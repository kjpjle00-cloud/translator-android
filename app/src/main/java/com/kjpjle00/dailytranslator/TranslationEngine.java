package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Locale;

public class TranslationEngine {

    public interface Callback {
        void onSuccess(String translatedText);
        void onError(String message);
    }

    private final Translator koreanEnglishTranslator;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech = null;

    public TranslationEngine(Context context) {
        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.KOREAN)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build();

        koreanEnglishTranslator = Translation.getClient(options);

        tts = new TextToSpeech(
                context.getApplicationContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        int result = tts.setLanguage(Locale.US);
                        ttsReady =
                                result != TextToSpeech.LANG_MISSING_DATA
                                && result != TextToSpeech.LANG_NOT_SUPPORTED;

                        if (ttsReady && pendingSpeech != null) {
                            String textToSpeak = pendingSpeech;
                            pendingSpeech = null;
                            speakEnglish(textToSpeak);
                        }
                    }
                }
        );
    }

    public void translateKoreanToEnglish(String text, Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError("번역할 문장이 없습니다.");
            return;
        }

        DownloadConditions conditions =
                new DownloadConditions.Builder().build();

        koreanEnglishTranslator
                .downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused ->
                        koreanEnglishTranslator
                                .translate(text.trim())
                                .addOnSuccessListener(translatedText -> {
                                    speakEnglish(translatedText);
                                    callback.onSuccess(translatedText);
                                })
                                .addOnFailureListener(e ->
                                        callback.onError(
                                                e.getMessage() == null
                                                        ? "번역 처리 오류"
                                                        : e.getMessage()
                                        )
                                )
                )
                .addOnFailureListener(e ->
                        callback.onError(
                                e.getMessage() == null
                                        ? "번역 모델 다운로드 오류"
                                        : e.getMessage()
                        )
                );
    }

    private void speakEnglish(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (!ttsReady || tts == null) {
            pendingSpeech = text;
            return;
        }

        tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "daily-translator-en"
        );
    }

    public void close() {
        koreanEnglishTranslator.close();

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        ttsReady = false;
        pendingSpeech = null;
    }
}
