package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Existing ML Kit/TTS pipeline with request-scoped cancellation and completion. */
public class TranslationEngine {
    public interface Callback {
        void onSuccess(String translatedText);
        void onError(String message);
        void onSpeechComplete();
        default void onCancelled() { }
    }

    public interface PlaybackListener {
        void onRequestStarted();
        void onPlaybackPrepared(String text);
        void onRequestFinished();
    }

    private static final class Operation {
        final String id;
        final Callback callback;
        final Locale locale;
        final boolean shouldSpeak;
        String pendingText;
        Runnable timeout;
        Operation(long id, Callback cb, Locale locale, boolean shouldSpeak) {
            this.id = "daily-translator-" + id;
            this.callback = cb;
            this.locale = locale;
            this.shouldSpeak = shouldSpeak;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Translator> translators = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> translationCaches = new ConcurrentHashMap<>();
    private final Map<String, Boolean> preparedPairs = new ConcurrentHashMap<>();
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsFailed;
    private boolean closed;
    private long sequence;
    private Operation current;
    private PlaybackListener playbackListener;

    public TranslationEngine(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), status -> handler.post(() -> {
            if (closed) return;
            if (status != TextToSpeech.SUCCESS || tts == null) {
                ttsFailed = true;
                if (current != null) fail(current, "음성 출력 초기화에 실패했습니다.");
                return;
            }
            ttsReady = true;
            tts.setSpeechRate(1.08f);
            tts.setPitch(1.0f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { onMain(() -> finishId(id, null)); }
                @Override public void onError(String id) { onMain(() -> finishId(id, "음성 재생에 실패했습니다.")); }
                @Override public void onStop(String id, boolean interrupted) { onMain(() -> finishId(id, null)); }
            });
            if (current != null && current.pendingText != null) speak(current, current.pendingText);
        }));
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run(); else handler.post(action);
    }
    public void setPlaybackListener(PlaybackListener listener) { onMain(() -> playbackListener = listener); }
    public boolean isBusy() { return current != null; }
    private boolean owns(Operation operation) { return !closed && current == operation; }

    public void preparePair(AppLanguage source, AppLanguage target) {
        if (closed || source == null || target == null || source.code.equals(target.code)) return;
        prepareOneDirection(source, target);
        prepareOneDirection(target, source);
    }

    private void prepareOneDirection(AppLanguage source, AppLanguage target) {
        String pair = pairKey(source.code, target.code);
        if (Boolean.TRUE.equals(preparedPairs.get(pair))) return;
        preparedPairs.put(pair, true);
        getTranslator(source.code, target.code).downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnFailureListener(e -> preparedPairs.remove(pair));
    }

    public void prewarm(List<String> texts, AppLanguage source, AppLanguage target) {
        if (closed || texts == null || source == null || target == null || source.code.equals(target.code)) return;
        Translator translator = getTranslator(source.code, target.code);
        Map<String, String> cache = getCache(source.code, target.code);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build()).addOnSuccessListener(unused -> {
            if (closed) return;
            for (String raw : texts) {
                if (raw == null || raw.trim().isEmpty() || cache.containsKey(raw.trim())) continue;
                String value = raw.trim();
                translator.translate(value).addOnSuccessListener(result -> {
                    if (!closed) cache.put(value, result);
                });
            }
        });
    }

    public void translateAndSpeak(String text, AppLanguage source, AppLanguage target,
            boolean shouldSpeak, Callback callback) {
        onMain(() -> begin(text, source, target, shouldSpeak, callback));
    }

    private void begin(String text, AppLanguage source, AppLanguage target, boolean shouldSpeak, Callback callback) {
        if (closed) return;
        if (current != null) { callback.onError("현재 번역 음성이 끝난 뒤 다시 눌러 주세요."); return; }
        if (text == null || text.trim().isEmpty() || source == null || target == null) {
            callback.onError("번역할 문장과 언어를 확인해 주세요."); return;
        }
        Operation operation = new Operation(++sequence, callback, target.ttsLocale, shouldSpeak);
        current = operation;
        if (playbackListener != null) playbackListener.onRequestStarted();
        // Download/translation can stall; leave no permanently busy session behind.
        armTimeout(operation, 60000L, "번역 준비가 지연되었습니다. 모델 다운로드를 확인해 주세요.");
        String value = text.trim();
        if (source.code.equals(target.code)) { translated(operation, value); return; }
        Map<String, String> cache = getCache(source.code, target.code);
        String cached = cache.get(value);
        if (cached != null) { translated(operation, cached); return; }
        Translator translator = getTranslator(source.code, target.code);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(unused -> onMain(() -> {
                    if (!owns(operation)) return;
                    translator.translate(value)
                            .addOnSuccessListener(result -> onMain(() -> {
                                if (!owns(operation)) return;
                                cache.put(value, result);
                                translated(operation, result);
                            }))
                            .addOnFailureListener(e -> onMain(() -> fail(operation, "번역 처리 오류")));
                }))
                .addOnFailureListener(e -> onMain(() -> fail(operation, "번역 모델 준비 오류")));
    }

    private void translated(Operation operation, String result) {
        if (!owns(operation)) return;
        operation.callback.onSuccess(result);
        // A caller may stop/change language while handling onSuccess.
        if (!owns(operation)) return;
        if (!operation.shouldSpeak) { complete(operation); return; }
        if (playbackListener != null) playbackListener.onPlaybackPrepared(result);
        speak(operation, result);
    }

    private void speak(Operation operation, String text) {
        if (!owns(operation)) return;
        if (ttsFailed) { fail(operation, "음성 출력 초기화에 실패했습니다."); return; }
        if (!ttsReady || tts == null) {
            operation.pendingText = text;
            armTimeout(operation, 10000L, "음성 출력을 준비하지 못했습니다.");
            return;
        }
        operation.pendingText = null;
        int languageResult = tts.setLanguage(operation.locale);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail(operation, "선택한 언어의 음성 출력 데이터가 없거나 지원되지 않습니다."); return;
        }
        armTimeout(operation, Math.max(30000L, Math.min(180000L, text.length() * 500L)), "음성 재생이 지연되어 다시 듣습니다.");
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, operation.id) == TextToSpeech.ERROR) {
            fail(operation, "음성 재생 요청에 실패했습니다.");
        }
    }

    private void armTimeout(Operation operation, long delay, String message) {
        if (operation.timeout != null) handler.removeCallbacks(operation.timeout);
        operation.timeout = () -> fail(operation, message);
        handler.postDelayed(operation.timeout, delay);
    }

    private void finishId(String id, String error) {
        if (current == null || !current.id.equals(id)) return;
        if (error == null) complete(current); else fail(current, error);
    }

    private void release(Operation operation) {
        current = null;
        if (operation.timeout != null) handler.removeCallbacks(operation.timeout);
        if (playbackListener != null) playbackListener.onRequestFinished();
    }

    private void complete(Operation operation) {
        if (!owns(operation)) return;
        release(operation);
        operation.callback.onSpeechComplete();
    }

    private void fail(Operation operation, String message) {
        if (!owns(operation)) return;
        // Invalidate before stop triggers onStop.
        release(operation);
        if (tts != null) tts.stop();
        operation.callback.onError(message);
    }

    public void stopSpeaking() {
        onMain(() -> {
            Operation operation = current;
            if (operation != null) release(operation);
            if (tts != null) tts.stop();
            if (operation != null) operation.callback.onCancelled();
        });
    }

    private Translator getTranslator(String source, String target) {
        String key = pairKey(source, target);
        Translator translator = translators.get(key);
        if (translator == null) {
            translator = Translation.getClient(new TranslatorOptions.Builder()
                    .setSourceLanguage(source).setTargetLanguage(target).build());
            translators.put(key, translator);
        }
        return translator;
    }
    private Map<String, String> getCache(String source, String target) {
        String key = pairKey(source, target);
        Map<String, String> cache = translationCaches.get(key);
        if (cache == null) { cache = new ConcurrentHashMap<>(); translationCaches.put(key, cache); }
        return cache;
    }
    private String pairKey(String source, String target) { return source + ">" + target; }

    public void close() {
        onMain(() -> {
            closed = true;
            Operation operation = current;
            current = null;
            if (operation != null && operation.timeout != null) handler.removeCallbacks(operation.timeout);
            for (Translator translator : translators.values()) translator.close();
            translators.clear();
            translationCaches.clear();
            preparedPairs.clear();
            if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
            playbackListener = null;
        });
    }
}
