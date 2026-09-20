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

    private static final long LANGUAGE_LOCK_MS = 8000L;
    private static final long ECHO_BLOCK_MS = 5000L;
    private static final long AFTER_TTS_RESTART_MS = 520L;

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

    private String lockedLanguageTag = null;
    private long lockedLanguageAt = 0L;
    private boolean modelDownloadRequested = false;

    private String lastPlaybackText = null;
    private long lastPlaybackAt = 0L;

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

        lockedLanguageTag = firstLanguage.speechTag;
        lockedLanguageAt = 0L;

        boolean wasActive = active;
        if (wasActive) stop();

        prepareSpeechModels();

        if (wasActive) start();
    }

    public void suppressPlaybackEcho(String text) {
        if (text == null || text.trim().isEmpty()) return;
        lastPlaybackText = normalizeEcho(text);
        lastPlaybackAt = System.currentTimeMillis();
    }

    private void createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return;

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (listener != null) listener.onListening();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

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
                    scheduleRestart(260);
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
                    scheduleRestart(240);
                    return;
                }

                String expectedTag = strongDetectedTag();
                if (expectedTag == null && lockIsFresh()) {
                    expectedTag = lockedLanguageTag;
                }

                String selected = chooseBestCandidate(list, expectedTag);
                if (selected == null || selected.trim().isEmpty()) {
                    rejectAmbiguous("말을 다시 말씀해 주세요.");
                    return;
                }

                // 대표적인 한글/로마자 음차를 먼저 실제 문자로 복원한다.
                selected = normalizePhoneticFallback(selected, detectedLanguage);

                if (isRecentPlaybackEcho(selected)) {
                    resetDetectionState();
                    processing = false;
                    if (listener != null) {
                        listener.onError("방금 재생한 번역음은 무시했습니다.");
                    }
                    scheduleRestart(320);
                    return;
                }

                if (looksMixedBetweenSelectedLanguages(selected)) {
                    rejectAmbiguous("말이 겹쳐 언어를 구분하지 못했습니다. 다시 말씀해 주세요.");
                    return;
                }

                String languageTag = decideLanguageStrict(selected);

                if (languageTag == null) {
                    rejectAmbiguous("언어 판단이 불확실합니다. 다시 말씀해 주세요.");
                    return;
                }

                lockedLanguageTag = languageTag;
                lockedLanguageAt = System.currentTimeMillis();

                processing = true;
                String finalSelected = selected;
                String finalLanguageTag = languageTag;

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

            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onLanguageDetection(Bundle results) {
                if (Build.VERSION.SDK_INT < 34 || results == null) return;

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

    private String strongDetectedTag() {
        if (detectedLanguage == null || !matchesPair(detectedLanguage)) {
            return null;
        }

        if (Build.VERSION.SDK_INT < 34) {
            return null;
        }

        if (detectedConfidence
                >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) {
            return languageTagForCode(codeForTag(detectedLanguage));
        }

        return null;
    }

    private String decideLanguageStrict(String text) {
        String scriptTag = inferLanguageFromText(text);
        String strongTag = strongDetectedTag();

        // 문자와 Android 감지가 둘 다 같은 언어면 가장 확실하다.
        if (scriptTag != null && strongTag != null
                && sameLanguage(scriptTag, strongTag)) {
            return scriptTag;
        }

        // 한글/한자/가나/태국/키릴 등 문자가 명확하면 문자 판정을 우선한다.
        if (scriptTag != null && isStrongScriptForPair(text, scriptTag)) {
            return scriptTag;
        }

        // Android가 높은 신뢰도로 선택한 두 언어 중 하나를 감지했다면 전환 허용.
        if (strongTag != null) {
            return strongTag;
        }

        // 직전 화자가 계속 말하는 경우: 잠깐의 애매한 짧은 발화는 직전 언어를 유지한다.
        if (lockIsFresh() && textCompatibleWithLockedLanguage(text)) {
            return lockedLanguageTag;
        }

        // 선택한 두 언어 중 한쪽만 라틴 문자 언어라면 라틴 여부로 제한적으로 판정.
        String latinTag = inferUniqueLatinSide(text);
        if (latinTag != null) {
            return latinTag;
        }

        // 여기서 임의로 '내 언어'라고 찍지 않는다.
        return null;
    }

    private boolean textCompatibleWithLockedLanguage(String text) {
        if (lockedLanguageTag == null) return false;

        String lockedCode = codeForTag(lockedLanguageTag);
        String scriptTag = inferLanguageFromText(text);

        if (scriptTag != null) {
            return sameLanguage(scriptTag, lockedLanguageTag);
        }

        if (isLatinDominant(text)) {
            return isLatinLanguage(lockedCode);
        }

        // 짧은 숫자/고유명사 등 문자만으로 판정이 어려운 경우 같은 화자 연속발화로 인정.
        return text.trim().length() <= 12;
    }

    private String inferUniqueLatinSide(String text) {
        if (!isLatinDominant(text)) return null;

        boolean firstLatin = isLatinLanguage(firstLanguage.code);
        boolean secondLatin = isLatinLanguage(secondLanguage.code);

        if (firstLatin && !secondLatin) return firstLanguage.speechTag;
        if (!firstLatin && secondLatin) return secondLanguage.speechTag;

        return null;
    }

    private boolean isStrongScriptForPair(String text, String tag) {
        String code = codeForTag(tag);
        if (code == null) return false;

        if ("ko".equals(code)) return countHangul(text) >= 1;
        if ("zh".equals(code)) return countHan(text) >= 1;
        if ("ja".equals(code)) return countKana(text) >= 1;
        if ("th".equals(code)) return countThai(text) >= 1;
        if ("ru".equals(code)) return countCyrillic(text) >= 1;
        if ("ar".equals(code)) return countArabic(text) >= 1;
        if ("hi".equals(code)) return countDevanagari(text) >= 1;

        return false;
    }

    private boolean looksMixedBetweenSelectedLanguages(String text) {
        String first = firstLanguage.code;
        String second = secondLanguage.code;

        int firstCount = scriptCountForCode(text, first);
        int secondCount = scriptCountForCode(text, second);

        // 라틴-라틴 조합은 문자만으로 혼합 여부를 판정할 수 없다.
        if (isLatinLanguage(first) && isLatinLanguage(second)) {
            return false;
        }

        // 한쪽이 라틴 언어인 경우 라틴 문자도 계산.
        if (isLatinLanguage(first)) firstCount = countLatin(text);
        if (isLatinLanguage(second)) secondCount = countLatin(text);

        return firstCount >= 2 && secondCount >= 2;
    }

    private int scriptCountForCode(String text, String code) {
        if ("ko".equals(code)) return countHangul(text);
        if ("zh".equals(code)) return countHan(text);
        if ("ja".equals(code)) return countKana(text);
        if ("th".equals(code)) return countThai(text);
        if ("ru".equals(code)) return countCyrillic(text);
        if ("ar".equals(code)) return countArabic(text);
        if ("hi".equals(code)) return countDevanagari(text);
        if (isLatinLanguage(code)) return countLatin(text);
        return 0;
    }

    private void rejectAmbiguous(String message) {
        resetDetectionState();
        processing = false;
        if (listener != null) listener.onError(message);
        scheduleRestart(320);
    }

    private boolean lockIsFresh() {
        return lockedLanguageTag != null
                && System.currentTimeMillis() - lockedLanguageAt <= LANGUAGE_LOCK_MS;
    }

    private boolean sameLanguage(String aTag, String bTag) {
        String a = codeForTag(aTag);
        String b = codeForTag(bTag);
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        return ("tl".equals(a) && "fil".equals(b))
                || ("fil".equals(a) && "tl".equals(b));
    }

    private boolean isRecentPlaybackEcho(String text) {
        if (lastPlaybackText == null) return false;
        if (System.currentTimeMillis() - lastPlaybackAt > ECHO_BLOCK_MS) return false;

        String now = normalizeEcho(text);
        if (now.length() < 3 || lastPlaybackText.length() < 3) return false;

        return now.equals(lastPlaybackText)
                || (now.length() >= 6 && lastPlaybackText.contains(now))
                || (lastPlaybackText.length() >= 6 && now.contains(lastPlaybackText));
    }

    private String normalizeEcho(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "")
                .trim();
    }

    public void prepareSpeechModels() {
        if (recognizer == null || Build.VERSION.SDK_INT < 33) return;

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
                                            language.name + " 음성모델 준비 " + completedPercent + "%"
                                    );
                                }
                            }

                            @Override
                            public void onSuccess() {
                                if (listener != null) listener.onIdleRetry();
                            }

                            @Override
                            public void onScheduled() {
                                if (listener != null) {
                                    listener.onError(
                                            language.name + " 음성모델 다운로드가 예약되었습니다."
                                    );
                                }
                            }

                            @Override
                            public void onError(int error) {
                                if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                                        && listener != null) {
                                    listener.onError(
                                            language.name + " 음성모델을 아직 사용할 수 없습니다."
                                    );
                                }
                            }
                        }
                );
            } else {
                recognizer.triggerModelDownload(modelIntent);
            }
        } catch (Exception ignored) {
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

        String startTag = lockIsFresh()
                ? lockedLanguageTag
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
            ArrayList<String> languages = new ArrayList<>(
                    Arrays.asList(firstLanguage.speechTag, secondLanguage.speechTag)
            );

            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
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
            String expectedTag
    ) {
        String expectedCode = codeForTag(expectedTag);

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

        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) continue;
            String inferred = inferLanguageFromText(candidate);
            if (inferred != null) return candidate.trim();
        }

        return candidates.get(0) == null ? null : candidates.get(0).trim();
    }

    private String normalizePhoneticFallback(String raw, String languageTag) {
        if (raw == null) return "";

        String code = codeForTag(languageTag);
        String key = normalizeAliasKey(raw);

        if ("zh".equals(code) || pairContainsCode("zh")) {
            String fixed = chineseAliases.get(key);
            if (fixed != null) return fixed;
        }

        if ("ja".equals(code) || pairContainsCode("ja")) {
            String fixed = japaneseAliases.get(key);
            if (fixed != null) return fixed;
        }

        if ("th".equals(code) || pairContainsCode("th")) {
            String fixed = thaiAliases.get(key);
            if (fixed != null) return fixed;
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

        if (firstLanguage.code.equals(code)) return firstLanguage.speechTag;
        if (secondLanguage.code.equals(code)) return secondLanguage.speechTag;

        if ("fil".equals(code)) {
            if (firstLanguage.code.equals("tl")) return firstLanguage.speechTag;
            if (secondLanguage.code.equals("tl")) return secondLanguage.speechTag;
        }

        return code;
    }

    private String inferLanguageFromText(String value) {
        if (value == null || value.trim().isEmpty()) return null;

        if (countHangul(value) > 0 && pairContainsCode("ko")) {
            return languageTagForCode("ko");
        }
        if (countKana(value) > 0 && pairContainsCode("ja")) {
            return languageTagForCode("ja");
        }
        if (countHan(value) > 0) {
            if (pairContainsCode("zh")) return languageTagForCode("zh");
            if (pairContainsCode("ja")) return languageTagForCode("ja");
        }
        if (countThai(value) > 0 && pairContainsCode("th")) {
            return languageTagForCode("th");
        }
        if (countCyrillic(value) > 0 && pairContainsCode("ru")) {
            return languageTagForCode("ru");
        }
        if (countArabic(value) > 0 && pairContainsCode("ar")) {
            return languageTagForCode("ar");
        }
        if (countDevanagari(value) > 0 && pairContainsCode("hi")) {
            return languageTagForCode("hi");
        }

        return inferUniqueLatinSide(value);
    }

    private int scriptScore(String value, String expectedCode) {
        if (value == null || expectedCode == null) return 0;
        return scriptCountForCode(value, expectedCode) * 4;
    }

    private int countHangul(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') n++;
        }
        return n;
    }

    private int countHan(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') n++;
        }
        return n;
    }

    private int countKana(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u3040' && c <= '\u30FF') n++;
        }
        return n;
    }

    private int countThai(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0E00' && c <= '\u0E7F') n++;
        }
        return n;
    }

    private int countCyrillic(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') n++;
        }
        return n;
    }

    private int countArabic(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0600' && c <= '\u06FF') n++;
        }
        return n;
    }

    private int countDevanagari(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0900' && c <= '\u097F') n++;
        }
        return n;
    }

    private int countLatin(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (isLatinLetter(s.charAt(i))) n++;
        }
        return n;
    }

    private boolean isLatinDominant(String s) {
        int latin = countLatin(s);
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) letters++;
        }
        return letters > 0 && latin * 100 / letters >= 70;
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
        return raw.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace(".", "")
                .replace(",", "")
                .replace("?", "")
                .replace("!", "")
                .replace("-", "")
                .trim();
    }

    private void initAliases() {
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

        if (!modelDownloadRequested) prepareSpeechModels();

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
        scheduleRestart(AFTER_TTS_RESTART_MS);
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
