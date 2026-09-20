package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TranslationEngine {

    public interface Callback {
        void onSuccess(String translatedText);
        void onError(String message);
        void onSpeechComplete();
    }

    private final Context context;
    private final Map<String, Translator> translators = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> translationCaches = new ConcurrentHashMap<>();
    private final Map<String, Boolean> preparedPairs = new ConcurrentHashMap<>();

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Runnable speechCompleteCallback;
    private String pendingSpeech;
    private Locale pendingLocale;

    public TranslationEngine(Context context) {
        this.context = context.getApplicationContext();

        tts = new TextToSpeech(
                this.context,
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
                                        finishSpeechCallback();
                                    }

                                    @Override
                                    public void onError(String utteranceId) {
                                        finishSpeechCallback();
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

    public void preparePair(AppLanguage source, AppLanguage target) {
        if (source == null || target == null || source.code.equals(target.code)) return;

        prepareOneDirection(source, target);
        prepareOneDirection(target, source);
    }

    private void prepareOneDirection(AppLanguage source, AppLanguage target) {
        String pair = pairKey(source.code, target.code);
        if (Boolean.TRUE.equals(preparedPairs.get(pair))) return;

        preparedPairs.put(pair, true);
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        getTranslator(source.code, target.code)
                .downloadModelIfNeeded(conditions)
                .addOnFailureListener(e -> preparedPairs.remove(pair));
    }

    public void prewarm(
            List<String> texts,
            AppLanguage source,
            AppLanguage target
    ) {
        if (texts == null || texts.isEmpty() || source == null || target == null) return;
        if (source.code.equals(target.code)) return;

        preparePair(source, target);

        for (String text : texts) {
            if (text == null || text.trim().isEmpty()) continue;
            String value = text.trim();

            Map<String, String> cache = getCache(source.code, target.code);
            if (cache.containsKey(value)) continue;

            getTranslator(source.code, target.code)
                    .translate(value)
                    .addOnSuccessListener(result -> cache.put(value, result));
        }
    }

    public void translateAndSpeak(
            String text,
            AppLanguage source,
            AppLanguage target,
            boolean shouldSpeak,
            Callback callback
    ) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError("번역할 문장이 없습니다.");
            return;
        }
        if (source == null || target == null) {
            callback.onError("언어를 선택해 주세요.");
            return;
        }

        String value = text.trim();

        if (source.code.equals(target.code)) {
            callback.onSuccess(value);
            if (shouldSpeak) {
                speak(value, target.ttsLocale, callback::onSpeechComplete);
            } else {
                callback.onSpeechComplete();
            }
            return;
        }

        preparePair(source, target);

        Map<String, String> cache = getCache(source.code, target.code);
        String cached = cache.get(value);

        if (cached != null) {
            callback.onSuccess(cached);
            if (shouldSpeak) {
                speak(cached, target.ttsLocale, callback::onSpeechComplete);
            } else {
                callback.onSpeechComplete();
            }
            return;
        }

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        Translator translator = getTranslator(source.code, target.code);

        translator
                .downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused ->
                        translator
                                .translate(value)
                                .addOnSuccessListener(result -> {
                                    cache.put(value, result);
                                    callback.onSuccess(result);

                                    if (shouldSpeak) {
                                        speak(
                                                result,
                                                target.ttsLocale,
                                                callback::onSpeechComplete
                                        );
                                    } else {
                                        callback.onSpeechComplete();
                                    }
                                })
                                .addOnFailureListener(e ->
                                        callback.onError(errorText(e, "번역 처리 오류"))
                                )
                )
                .addOnFailureListener(e ->
                        callback.onError(errorText(e, "번역 모델 준비 오류"))
                );
    }

    private Translator getTranslator(String sourceCode, String targetCode) {
        String key = pairKey(sourceCode, targetCode);

        Translator existing = translators.get(key);
        if (existing != null) return existing;

        Translator created = Translation.getClient(
                new TranslatorOptions.Builder()
                        .setSourceLanguage(sourceCode)
                        .setTargetLanguage(targetCode)
                        .build()
        );

        translators.put(key, created);
        return created;
    }

    private Map<String, String> getCache(String sourceCode, String targetCode) {
        String key = pairKey(sourceCode, targetCode);
        Map<String, String> cache = translationCaches.get(key);

        if (cache == null) {
            cache = new ConcurrentHashMap<>();
            translationCaches.put(key, cache);
        }

        return cache;
    }

    private String pairKey(String sourceCode, String targetCode) {
        return sourceCode + ">" + targetCode;
    }

    private String errorText(Exception e, String fallback) {
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
            finishSpeechCallback();
            return;
        }

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "daily-translator-" + System.nanoTime()
        );
    }

    private synchronized void finishSpeechCallback() {
        Runnable cb = speechCompleteCallback;
        speechCompleteCallback = null;
        if (cb != null) cb.run();
    }

    public void stopSpeaking() {
        if (tts != null) tts.stop();
        pendingSpeech = null;
        pendingLocale = null;
        finishSpeechCallback();
    }

    public void close() {
        for (Translator translator : translators.values()) {
            translator.close();
        }
        translators.clear();
        translationCaches.clear();
        preparedPairs.clear();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        pendingSpeech = null;
        pendingLocale = null;
        speechCompleteCallback = null;
        ttsReady = false;
    }
}
