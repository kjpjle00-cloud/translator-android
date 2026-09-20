package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranslationEngine {

    public interface Callback {
        void onSuccess(String translatedText);
        void onError(String message);
        void onSpeechComplete();
    }

    private final Translator koToEn;
    private final Translator enToKo;
    private final Map<String, String> koEnCache = new HashMap<>();
    private final Map<String, String> enKoCache = new HashMap<>();
    private final ExecutorService warmExecutor = Executors.newSingleThreadExecutor();

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean modelsRequested = false;

    private Runnable speechCompleteCallback;
    private String pendingSpeech;
    private Locale pendingLocale;

    public TranslationEngine(Context context) {
        koToEn = Translation.getClient(
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.KOREAN)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build()
        );

        enToKo = Translation.getClient(
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.KOREAN)
                        .build()
        );

        tts = new TextToSpeech(
                context.getApplicationContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        ttsReady = true;
                        tts.setSpeechRate(1.08f);
                        tts.setPitch(1.0f);

                        tts.setOnUtteranceProgressListener(
                                new UtteranceProgressListener() {
                                    @Override
                                    public void onStart(String utteranceId) {
                                    }

                                    @Override
                                    public void onDone(String utteranceId) {
                                        Runnable cb = speechCompleteCallback;
                                        speechCompleteCallback = null;
                                        if (cb != null) cb.run();
                                    }

                                    @Override
                                    public void onError(String utteranceId) {
                                        Runnable cb = speechCompleteCallback;
                                        speechCompleteCallback = null;
                                        if (cb != null) cb.run();
                                    }
                                }
                        );

                        if (pendingSpeech != null) {
                            String text = pendingSpeech;
                            Locale locale = pendingLocale == null ? Locale.US : pendingLocale;
                            pendingSpeech = null;
                            pendingLocale = null;
                            speak(text, locale, speechCompleteCallback);
                        }
                    }
                }
        );
    }

    public synchronized void preload() {
        if (modelsRequested) return;
        modelsRequested = true;

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        koToEn.downloadModelIfNeeded(conditions);
        enToKo.downloadModelIfNeeded(conditions);
    }

    public void prewarmKoreanToEnglish(List<String> texts) {
        if (texts == null || texts.isEmpty()) return;
        preload();

        warmExecutor.execute(() -> {
            for (String text : texts) {
                if (text == null) continue;
                String key = text.trim();
                if (key.isEmpty()) continue;

                synchronized (koEnCache) {
                    if (koEnCache.containsKey(key)) continue;
                }

                try {
                    String translated = Tasks.await(koToEn.translate(key));
                    synchronized (koEnCache) {
                        koEnCache.put(key, translated);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void translateKoreanToEnglish(
            String text,
            boolean speak,
            Callback callback
    ) {
        translate(
                text,
                koToEn,
                koEnCache,
                Locale.US,
                speak,
                callback
        );
    }

    public void translateEnglishToKorean(
            String text,
            boolean speak,
            Callback callback
    ) {
        translate(
                text,
                enToKo,
                enKoCache,
                Locale.KOREA,
                speak,
                callback
        );
    }

    private void translate(
            String text,
            Translator translator,
            Map<String, String> cache,
            Locale outputLocale,
            boolean shouldSpeak,
            Callback callback
    ) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError("번역할 문장이 없습니다.");
            return;
        }

        preload();
        String source = text.trim();

        String cached;
        synchronized (cache) {
            cached = cache.get(source);
        }

        if (cached != null) {
            callback.onSuccess(cached);
            if (shouldSpeak) {
                speak(cached, outputLocale, callback::onSpeechComplete);
            } else {
                callback.onSpeechComplete();
            }
            return;
        }

        DownloadConditions conditions = new DownloadConditions.Builder().build();

        translator
                .downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused ->
                        translator
                                .translate(source)
                                .addOnSuccessListener(translatedText -> {
                                    synchronized (cache) {
                                        cache.put(source, translatedText);
                                    }
                                    callback.onSuccess(translatedText);

                                    if (shouldSpeak) {
                                        speak(
                                                translatedText,
                                                outputLocale,
                                                callback::onSpeechComplete
                                        );
                                    } else {
                                        callback.onSpeechComplete();
                                    }
                                })
                                .addOnFailureListener(e ->
                                        callback.onError(errorMessage(e, "번역 처리 오류"))
                                )
                )
                .addOnFailureListener(e ->
                        callback.onError(errorMessage(e, "번역 모델 준비 오류"))
                );
    }

    private String errorMessage(Exception e, String fallback) {
        return e.getMessage() == null ? fallback : e.getMessage();
    }

    private synchronized void speak(
            String text,
            Locale locale,
            Runnable completeCallback
    ) {
        if (text == null || text.trim().isEmpty()) {
            if (completeCallback != null) completeCallback.run();
            return;
        }

        speechCompleteCallback = completeCallback;

        if (!ttsReady || tts == null) {
            pendingSpeech = text;
            pendingLocale = locale;
            return;
        }

        int languageResult = tts.setLanguage(locale);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA
                || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Runnable cb = speechCompleteCallback;
            speechCompleteCallback = null;
            if (cb != null) cb.run();
            return;
        }

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "daily-translator-" + System.nanoTime()
        );
    }

    public void stopSpeaking() {
        if (tts != null) {
            tts.stop();
        }
        Runnable cb = speechCompleteCallback;
        speechCompleteCallback = null;
        pendingSpeech = null;
        pendingLocale = null;
        if (cb != null) cb.run();
    }

    public void close() {
        koToEn.close();
        enToKo.close();
        warmExecutor.shutdownNow();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        speechCompleteCallback = null;
        pendingSpeech = null;
        pendingLocale = null;
        ttsReady = false;
    }
}
