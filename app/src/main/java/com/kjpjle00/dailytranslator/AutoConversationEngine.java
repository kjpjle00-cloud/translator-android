package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    private int detectedConfidence =
            Build.VERSION.SDK_INT >= 34
                    ? SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN
                    : 0;
    private int languageSwitchResult =
            Build.VERSION.SDK_INT >= 34
                    ? SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED
                    : 0;

    private String preferredStartLanguageTag = null;
    private boolean modelDownloadRequested = false;

    private final Map<String, String> chineseAliases = new HashMap<>();
    private final Map<String, String> japaneseAliases = new HashMap<>();
    private final Map<String, String> thaiAliases = new HashMap<>();

    public AutoConversationEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        initAliases();
        createRecognizer();
    }

    public void setLanguagePair(AppLanguage first, AppLanguage second) {
        if (first != null) firstLanguage = first;
        if (second != null) secondLanguage = second;

        preferredStartLanguageTag = firstLanguage.speechTag;

        boolean wasActive = active;
        if (wasActive) {
            stop();
        }

        prepareSpeechModels();

        if (wasActive) {
            start();
        }
    }

    private void createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return;
        }

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

                if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                    if (listener != null) {
                        listener.onError("선택한 언어 음성모델 준비 중");
                    }
                    prepareSpeechModels();
                    scheduleRestart(900);
                    return;
                }

                if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
                    if (listener != null) {
                        listener.onError(
                                "현재 휴대폰 음성인식기가 "
                                        + firstLanguage.name
                                        + " / "
                                        + secondLanguage.name
                                        + " 조합을 지원하지 않습니다."
                        );
                    }
                    scheduleRestart(1200);
                    return;
                }

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

                if (list == null || list.isEmpty()) {
                    scheduleRestart(220);
                    return;
                }

                String selected = chooseBestCandidate(list, detectedLanguage);
                if (selected == null || selected.trim().isEmpty()) {
                    scheduleRestart(220);
                    return;
                }

                String languageTag = normalizeDetectedLanguage(
                        detectedLanguage,
                        selected
                );

                // 선택하지 않은 언어가 명확하게 감지된 경우 잘못 번역하지 않고 다시 듣기
                if (languageTag != null && !matchesPair(languageTag)) {
                    if (listener != null) {
                        listener.onError("선택한 두 언어가 아닌 음성으로 인식되어 다시 듣습니다.");
                    }
                    resetDetectionState();
                    processing = false;
                    scheduleRestart(220);
                    return;
                }

                selected = normalizePhoneticFallback(selected, languageTag);

                // 보정 결과로 언어를 다시 판정
                String inferredAfterFix = inferLanguageFromText(selected);
                if (inferredAfterFix != null) {
                    languageTag = inferredAfterFix;
                }

                // 한국어/중국어처럼 양쪽 모두 비라틴 문자인데 영어 문장으로 잡힌 경우 차단
                if (languageTag == null && isClearlyOutsidePair(selected)) {
                    if (listener != null) {
                        listener.onError("선택한 언어로 인식되지 않아 다시 듣습니다.");
                    }
                    resetDetectionState();
                    processing = false;
                    scheduleRestart(220);
                    return;
                }

                if (languageTag == null) {
                    languageTag = preferredStartLanguageTag != null
                            ? preferredStartLanguageTag
                            : firstLanguage.speechTag;
                }

                preferredStartLanguageTag = languageTag;
                processing = true;

                String finalLanguageTag = languageTag;
                String finalSelected = selected;

                resetDetectionState();

                if (listener != null) {
                    listener.onUtterance(finalSelected, finalLanguageTag);
                }
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
                if (Build.VERSION.SDK_INT < 34 || results == null) {
                    return;
                }

                String lang = results.getString(SpeechRecognizer.DETECTED_LANGUAGE);
                if (lang != null && !lang.trim().isEmpty()) {
                    detectedLanguage = lang;
                }

                detectedConfidence = results.getInt(
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN
                );

                languageSwitchResult = results.getInt(
                        SpeechRecognizer.LANGUAGE_SWITCH_RESULT,
                        SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED
                );

                if (languageSwitchResult
                        == SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL) {
                    if (listener != null) {
                        listener.onError("상대 언어 음성모델이 없어 준비 중입니다.");
                    }
                    prepareSpeechModels();
                }
            }
        });

        prepareSpeechModels();
    }

    public void prepareSpeechModels() {
        if (recognizer == null || Build.VERSION.SDK_INT < 33) {
            return;
        }

        // 언어 선택 때마다 재요청 가능. 이미 있으면 즉시 success가 온다.
        requestModel(firstLanguage);
        requestModel(secondLanguage);
        modelDownloadRequested = true;
    }

    private void requestModel(AppLanguage language) {
        if (recognizer == null || language == null || Build.VERSION.SDK_INT < 33) {
            return;
        }

        Intent modelIntent = buildSingleLanguageIntent(language);

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                recognizer.triggerModelDownload(
                        modelIntent,
                        context.getMainExecutor(),
                        new ModelDownloadListener() {
                            @Override
                            public void onProgress(int completedPercent) {
                                if (listener != null && completedPercent > 0) {
                                    listener.onError(
                                            language.name
                                                    + " 음성모델 준비 "
                                                    + completedPercent
                                                    + "%"
                                    );
                                }
                            }

                            @Override
                            public void onSuccess() {
                                if (listener != null) {
                                    listener.onIdleRetry();
                                }
                            }

                            @Override
                            public void onScheduled() {
                                if (listener != null) {
                                    listener.onError(
                                            language.name
                                                    + " 음성모델 다운로드가 예약되었습니다."
                                    );
                                }
                            }

                            @Override
                            public void onError(int error) {
                                // 서비스에 따라 다운로드 이벤트를 지원하지 않을 수 있으므로
                                // 자동대화를 중단하지 않고 실제 인식 시도는 계속한다.
                                if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                                        && listener != null) {
                                    listener.onError(
                                            language.name
                                                    + " 음성모델을 아직 사용할 수 없습니다."
                                    );
                                }
                            }
                        }
                );
            } else {
                recognizer.triggerModelDownload(modelIntent);
            }
        } catch (Exception ignored) {
            // 일부 제조사 RecognitionService는 모델 다운로드 API를
            // 완전히 구현하지 않았을 수 있다. 인식 자체는 계속 시도한다.
        }
    }

    private Intent buildSingleLanguageIntent(AppLanguage language) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechTag);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        return intent;
    }

    private Intent buildRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        String startTag = preferredStartLanguageTag != null
                ? preferredStartLanguageTag
                : firstLanguage.speechTag;

        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, startTag);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

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
        }

        return intent;
    }

    private String chooseBestCandidate(
            ArrayList<String> candidates,
            String languageTag
    ) {
        String expectedCode = codeForTag(languageTag);

        if (expectedCode != null) {
            String best = null;
            int bestScore = Integer.MIN_VALUE;

            for (String candidate : candidates) {
                if (candidate == null || candidate.trim().isEmpty()) continue;
                int score = scriptScore(candidate, expectedCode);
                if (score > bestScore) {
                    best = candidate.trim();
                    bestScore = score;
                }
            }

            if (best != null && bestScore > 0) {
                return best;
            }
        }

        // 감지 태그가 없거나 후보가 애매하면 선택한 두 언어 문자에 맞는 후보 우선
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String inferred = inferLanguageFromText(candidate);
            if (inferred != null) {
                return candidate.trim();
            }
        }

        return candidates.get(0).trim();
    }

    private String normalizeDetectedLanguage(
            String languageTag,
            String text
    ) {
        String detectedCode = codeForTag(languageTag);

        // 감지 신뢰도가 높으면 감지 태그를 우선한다.
        if (Build.VERSION.SDK_INT >= 34
                && detectedCode != null
                && detectedConfidence
                >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) {
            return languageTagForCode(detectedCode);
        }

        String inferred = inferLanguageFromText(text);
        if (inferred != null) {
            return inferred;
        }

        if (detectedCode != null) {
            return languageTagForCode(detectedCode);
        }

        return null;
    }

    private String normalizePhoneticFallback(
            String raw,
            String languageTag
    ) {
        if (raw == null) return "";

        String code = codeForTag(languageTag);
        String key = normalizeAliasKey(raw);

        if ("zh".equals(code)
                || pairContainsCode("zh")) {
            String fixed = chineseAliases.get(key);
            if (fixed != null) {
                return fixed;
            }
        }

        if ("ja".equals(code)
                || pairContainsCode("ja")) {
            String fixed = japaneseAliases.get(key);
            if (fixed != null) {
                return fixed;
            }
        }

        if ("th".equals(code)
                || pairContainsCode("th")) {
            String fixed = thaiAliases.get(key);
            if (fixed != null) {
                return fixed;
            }
        }

        return raw.trim();
    }

    private boolean matchesPair(String languageTag) {
        String code = codeForTag(languageTag);
        if (code == null) return false;

        return code.equals(firstLanguage.code)
                || code.equals(secondLanguage.code)
                || ("fil".equals(code)
                    && (firstLanguage.code.equals("tl")
                        || secondLanguage.code.equals("tl")));
    }

    private boolean pairContainsCode(String code) {
        return firstLanguage.code.equals(code)
                || secondLanguage.code.equals(code);
    }

    private String codeForTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return null;

        String lower = tag.toLowerCase(Locale.ROOT);

        if (lower.startsWith("zh")) return "zh";
        if (lower.startsWith("ja")) return "ja";
        if (lower.startsWith("ko")) return "ko";
        if (lower.startsWith("th")) return "th";
        if (lower.startsWith("ru")) return "ru";
        if (lower.startsWith("ar")) return "ar";
        if (lower.startsWith("hi")) return "hi";
        if (lower.startsWith("fil")) return "fil";

        int dash = lower.indexOf('-');
        return dash > 0 ? lower.substring(0, dash) : lower;
    }

    private String languageTagForCode(String code) {
        if (code == null) return null;

        if (firstLanguage.code.equals(code)) {
            return firstLanguage.speechTag;
        }
        if (secondLanguage.code.equals(code)) {
            return secondLanguage.speechTag;
        }

        if ("fil".equals(code)) {
            if (firstLanguage.code.equals("tl")) return firstLanguage.speechTag;
            if (secondLanguage.code.equals("tl")) return secondLanguage.speechTag;
        }

        return code;
    }

    private String inferLanguageFromText(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        int hangul = 0;
        int kana = 0;
        int han = 0;
        int thai = 0;
        int cyrillic = 0;
        int arabic = 0;
        int devanagari = 0;
        int latin = 0;
        int letters = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;

            if (c >= '\uAC00' && c <= '\uD7A3') {
                hangul++;
            } else if (c >= '\u3040' && c <= '\u30FF') {
                kana++;
            } else if (c >= '\u4E00' && c <= '\u9FFF') {
                han++;
            } else if (c >= '\u0E00' && c <= '\u0E7F') {
                thai++;
            } else if (c >= '\u0400' && c <= '\u04FF') {
                cyrillic++;
            } else if (c >= '\u0600' && c <= '\u06FF') {
                arabic++;
            } else if (c >= '\u0900' && c <= '\u097F') {
                devanagari++;
            } else {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                if (block == Character.UnicodeBlock.BASIC_LATIN
                        || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                        || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                        || block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
                    latin++;
                }
            }
        }

        if (letters == 0) return null;

        if (hangul > 0 && pairContainsCode("ko")) return languageTagForCode("ko");
        if (kana > 0 && pairContainsCode("ja")) return languageTagForCode("ja");
        if (thai > 0 && pairContainsCode("th")) return languageTagForCode("th");
        if (cyrillic > 0 && pairContainsCode("ru")) return languageTagForCode("ru");
        if (arabic > 0 && pairContainsCode("ar")) return languageTagForCode("ar");
        if (devanagari > 0 && pairContainsCode("hi")) return languageTagForCode("hi");

        if (han > 0) {
            if (pairContainsCode("zh")) return languageTagForCode("zh");
            if (pairContainsCode("ja")) return languageTagForCode("ja");
        }

        if (latin * 100 / letters >= 70) {
            boolean firstLatin = isLatinLanguage(firstLanguage.code);
            boolean secondLatin = isLatinLanguage(secondLanguage.code);

            if (firstLatin && !secondLatin) return firstLanguage.speechTag;
            if (!firstLatin && secondLatin) return secondLanguage.speechTag;
        }

        return null;
    }

    private int scriptScore(String value, String expectedCode) {
        if (value == null) return 0;

        int score = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if ("ko".equals(expectedCode)
                    && c >= '\uAC00' && c <= '\uD7A3') {
                score += 4;
            } else if ("ja".equals(expectedCode)
                    && ((c >= '\u3040' && c <= '\u30FF')
                    || (c >= '\u4E00' && c <= '\u9FFF'))) {
                score += 4;
            } else if ("zh".equals(expectedCode)
                    && c >= '\u4E00' && c <= '\u9FFF') {
                score += 4;
            } else if ("th".equals(expectedCode)
                    && c >= '\u0E00' && c <= '\u0E7F') {
                score += 4;
            } else if ("ru".equals(expectedCode)
                    && c >= '\u0400' && c <= '\u04FF') {
                score += 4;
            } else if ("ar".equals(expectedCode)
                    && c >= '\u0600' && c <= '\u06FF') {
                score += 4;
            } else if ("hi".equals(expectedCode)
                    && c >= '\u0900' && c <= '\u097F') {
                score += 4;
            } else if (isLatinLanguage(expectedCode)
                    && isLatinLetter(c)) {
                score += 2;
            }
        }

        return score;
    }

    private boolean isClearlyOutsidePair(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        int latinLetters = 0;
        int letters = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if (isLatinLetter(c)) latinLetters++;
        }

        if (letters == 0) return false;

        boolean latinDominant = latinLetters * 100 / letters >= 80;
        boolean pairHasLatin =
                isLatinLanguage(firstLanguage.code)
                        || isLatinLanguage(secondLanguage.code);

        // 예: 한국어/중국어 선택인데 "I'm from Korea"로 인식된 경우
        return latinDominant && !pairHasLatin;
    }

    private boolean isLatinLetter(char c) {
        if (!Character.isLetter(c)) return false;

        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.BASIC_LATIN
                || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                || block == Character.UnicodeBlock.LATIN_EXTENDED_B;
    }

    private boolean isLatinLanguage(String code) {
        return "en".equals(code)
                || "es".equals(code)
                || "fr".equals(code)
                || "de".equals(code)
                || "id".equals(code)
                || "tl".equals(code)
                || "vi".equals(code)
                || "pt".equals(code)
                || "it".equals(code)
                || "tr".equals(code);
    }

    private String normalizeAliasKey(String raw) {
        String value = raw.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace(".", "")
                .replace(",", "")
                .replace("?", "")
                .replace("!", "")
                .replace("-", "");

        return value.trim();
    }

    private void initAliases() {
        // 중국어: 한국어식/로마자식으로 잘못 받아쓴 대표 표현
        chineseAliases.put("니하오", "你好");
        chineseAliases.put("니하오마", "你好吗");
        chineseAliases.put("nihao", "你好");
        chineseAliases.put("nihaoma", "你好吗");
        chineseAliases.put("셰셰", "谢谢");
        chineseAliases.put("시에시에", "谢谢");
        chineseAliases.put("xiexie", "谢谢");
        chineseAliases.put("짜이찌엔", "再见");
        chineseAliases.put("짜이젠", "再见");
        chineseAliases.put("zaijian", "再见");
        chineseAliases.put("두이부치", "对不起");
        chineseAliases.put("duibuqi", "对不起");
        chineseAliases.put("부커치", "不客气");
        chineseAliases.put("뿌커치", "不客气");
        chineseAliases.put("bukeqi", "不客气");

        // 일본어
        japaneseAliases.put("곤니치와", "こんにちは");
        japaneseAliases.put("콘니치와", "こんにちは");
        japaneseAliases.put("konnichiwa", "こんにちは");
        japaneseAliases.put("오하요", "おはよう");
        japaneseAliases.put("ohayo", "おはよう");
        japaneseAliases.put("아리가토", "ありがとう");
        japaneseAliases.put("아리가또", "ありがとう");
        japaneseAliases.put("arigato", "ありがとう");
        japaneseAliases.put("스미마센", "すみません");
        japaneseAliases.put("sumimasen", "すみません");
        japaneseAliases.put("사요나라", "さようなら");
        japaneseAliases.put("sayonara", "さようなら");

        // 태국어 대표 인사
        thaiAliases.put("사와디캅", "สวัสดีครับ");
        thaiAliases.put("사와디카", "สวัสดีค่ะ");
        thaiAliases.put("sawadeekrap", "สวัสดีครับ");
        thaiAliases.put("sawadeeka", "สวัสดีค่ะ");
        thaiAliases.put("컵쿤캅", "ขอบคุณครับ");
        thaiAliases.put("컵쿤카", "ขอบคุณค่ะ");
    }

    private void resetDetectionState() {
        detectedLanguage = null;

        if (Build.VERSION.SDK_INT >= 34) {
            detectedConfidence =
                    SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN;
            languageSwitchResult =
                    SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED;
        } else {
            detectedConfidence = 0;
            languageSwitchResult = 0;
        }
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
        resetDetectionState();

        if (!modelDownloadRequested) {
            prepareSpeechModels();
        }

        startListeningNow();
    }

    public void stop() {
        active = false;
        processing = false;
        resetDetectionState();
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
