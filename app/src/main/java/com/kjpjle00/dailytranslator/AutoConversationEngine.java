package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Arrays;

public class AutoConversationEngine {

    public interface Listener {
        void onListening();
        void onPartial(String text, String languageTag);
        void onUtterance(String text, String languageTag);
        void onIdleRetry();
        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private AppLanguage firstLanguage = AppLanguage.KOREAN;
    private AppLanguage secondLanguage = AppLanguage.ENGLISH;

    private boolean active = false;
    private boolean processing = false;
    private boolean destroyed = false;
    private String detectedLanguage = null;

    public AutoConversationEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        createRecognizer();
    }

    public void setLanguagePair(AppLanguage first, AppLanguage second) {
        if (first != null) firstLanguage = first;
        if (second != null) secondLanguage = second;

        if (active) {
            stop();
            start();
        }
    }

    private void createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return;

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (listener != null) listener.onListening();
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                if (!active || destroyed || processing) return;

                if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    if (listener != null) listener.onIdleRetry();
                    scheduleRestart(250);
                    return;
                }

                if (listener != null) listener.onError(errorText(error));
                scheduleRestart(650);
            }

            @Override
            public void onResults(Bundle results) {
                if (!active || destroyed) return;

                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (list == null || list.isEmpty() || list.get(0).trim().isEmpty()) {
                    scheduleRestart(220);
                    return;
                }

                processing = true;
                String text = list.get(0).trim();
                String language = detectedLanguage;
                detectedLanguage = null;

                if (listener != null) listener.onUtterance(text, language);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (list != null && !list.isEmpty() && listener != null) {
                    listener.onPartial(list.get(0), detectedLanguage);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }

            @Override
            public void onLanguageDetection(Bundle results) {
                if (Build.VERSION.SDK_INT >= 34 && results != null) {
                    String lang = results.getString(SpeechRecognizer.DETECTED_LANGUAGE);
                    if (lang != null && !lang.trim().isEmpty()) {
                        detectedLanguage = lang;
                    }
                }
            }
        });
    }

    private Intent buildRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1800L
        );
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1100L
        );

        if (Build.VERSION.SDK_INT >= 33) {
            intent.putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY
            );
        }

        if (Build.VERSION.SDK_INT >= 34) {
            ArrayList<String> languages =
                    new ArrayList<>(Arrays.asList(
                            firstLanguage.speechTag,
                            secondLanguage.speechTag
                    ));

            intent.putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,
                    true
            );
            intent.putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    languages
            );
            intent.putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE
            );
            intent.putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    languages
            );
        } else {
            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    firstLanguage.speechTag
            );
        }

        return intent;
    }

    public boolean isAvailable() {
        return recognizer != null;
    }

    public boolean isActive() {
        return active;
    }

    public void start() {
        if (recognizer == null || destroyed) return;
        active = true;
        processing = false;
        detectedLanguage = null;
        startListeningNow();
    }

    public void stop() {
        active = false;
        processing = false;
        detectedLanguage = null;
        handler.removeCallbacksAndMessages(null);

        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    public void resumeAfterProcessing() {
        if (!active || destroyed) return;
        processing = false;
        scheduleRestart(140);
    }

    private void scheduleRestart(long delayMs) {
        if (!active || destroyed || processing) return;

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startListeningNow, delayMs);
    }

    private void startListeningNow() {
        if (!active || destroyed || processing || recognizer == null) return;

        try {
            recognizer.cancel();
        } catch (Exception ignored) {
        }

        try {
            recognizer.startListening(buildRecognizerIntent());
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("음성 인식을 다시 시작하는 중입니다.");
            }
            scheduleRestart(500);
        }
    }

    private String errorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "마이크 오류";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "음성 인식 네트워크 오류";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "마이크 권한 필요";
            case SpeechRecognizer.ERROR_SERVER:
                return "음성 인식 서버 오류";
            default:
                return "음성 인식 다시 시도";
        }
    }

    public void destroy() {
        destroyed = true;
        active = false;
        processing = false;
        handler.removeCallbacksAndMessages(null);

        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }
            recognizer.destroy();
            recognizer = null;
        }
    }
}
