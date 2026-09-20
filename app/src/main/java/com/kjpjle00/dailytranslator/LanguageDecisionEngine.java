package com.kjpjle00.dailytranslator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Java policy: every decision uses evidence from this utterance only. */
public final class LanguageDecisionEngine {
    public static final class TextEvidence {
        public final String language;
        public final float confidence;
        public TextEvidence(String language, float confidence) {
            this.language = code(language);
            this.confidence = confidence;
        }
    }

    public static final class Decision {
        public final String rawText;
        public final String text;
        public final String sourceCode;
        public final String reason;
        public boolean confirmed() { return sourceCode != null; }
        private Decision(String raw, String text, String source, String reason) {
            this.rawText = raw;
            this.text = text;
            this.sourceCode = source;
            this.reason = reason;
        }
    }

    /** Preserve ASR order. A previous language is deliberately not an input. */
    public static int firstCandidate(List<String> candidates) {
        if (candidates != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i) != null && !candidates.get(i).trim().isEmpty()) return i;
            }
        }
        return -1;
    }

    public static Decision decide(String raw, String first, String second,
            String confidentSpeechLanguage, boolean conflictingSpeechLanguages,
            boolean switchFailed, float recognitionConfidence, List<TextEvidence> textEvidence) {
        String text = raw == null ? "" : raw.trim();
        first = code(first);
        second = code(second);
        String speech = code(confidentSpeechLanguage);
        if (first == null || second == null || first.equals(second)) return hold(text, "invalid_pair");
        if (text.isEmpty()) return hold(text, "empty");
        if (switchFailed) return hold(text, "speech_model_or_switch_failed");
        if (conflictingSpeechLanguages) return hold(text, "conflicting_audio_languages");
        if (speech != null && !inPair(speech, first, second)) return hold(text, "audio_outside_pair");
        if (recognitionConfidence >= 0f && recognitionConfidence < 0.45f) {
            return hold(text, "low_recognition_confidence");
        }
        if (letterCount(text) == 0) return hold(text, "no_language_evidence");

        String script = dominantNativeLanguage(text, first, second);
        String textLanguage = confidentTextLanguage(textEvidence);
        if (speech != null) {
            // Exact known greetings are normalized only with independent audio evidence.
            String restored = restoreGreeting(text, speech);
            if (restored != null) return confirm(text, restored, speech, "audio_supported_greeting");
            if (script != null && !script.equals(speech)) return hold(text, "audio_script_conflict");
            if (textLanguage != null && !textLanguage.equals(speech)) {
                return hold(text, "audio_text_conflict");
            }
            // Do not send Hangul transliterations, or unrelated scripts, to a foreign translator.
            if (!scriptCompatible(text, speech)) return hold(text, "wrong_transcription_script");
            return confirm(text, text, speech, "current_audio_evidence");
        }

        // Text cannot repair audio transcribed by the wrong model. Known transliterations
        // are held unless this utterance supplied independent foreign-language evidence.
        if (restoreGreeting(text, first) != null || restoreGreeting(text, second) != null) {
            return hold(text, "phonetic_text_needs_audio_evidence");
        }
        if (textLanguage == null || !inPair(textLanguage, first, second)) {
            return hold(text, textLanguage == null ? "uncertain_text_language" : "text_outside_pair");
        }
        if (script != null && !script.equals(textLanguage)) return hold(text, "text_script_conflict");
        if (!scriptCompatible(text, textLanguage)) return hold(text, "wrong_transcription_script");
        // A short fragment or a person's name must not acquire the previous speaker's language.
        if (letterCount(text) < 4 && !isShortReply(text, textLanguage)) {
            return hold(text, "short_text_needs_audio_evidence");
        }
        return confirm(text, text, textLanguage, "current_text_evidence");
    }

    private static String confidentTextLanguage(List<TextEvidence> evidence) {
        TextEvidence best = null;
        float runnerUp = 0f;
        if (evidence != null) {
            for (TextEvidence item : evidence) {
                if (item == null || item.language == null || Float.isNaN(item.confidence)) continue;
                if (best == null || item.confidence > best.confidence) {
                    if (best != null) runnerUp = Math.max(runnerUp, best.confidence);
                    best = item;
                } else if (!item.language.equals(best.language)) {
                    runnerUp = Math.max(runnerUp, item.confidence);
                }
            }
        }
        return best != null && best.confidence >= 0.85f
                && best.confidence - runnerUp >= 0.20f ? best.language : null;
    }

    public static String code(String tag) {
        if (tag == null || tag.trim().isEmpty()) return null;
        String result = tag.trim().toLowerCase(Locale.ROOT).replace('_', '-').split("-", 2)[0];
        if ("und".equals(result)) return null;
        if ("fil".equals(result)) return "tl";
        if ("in".equals(result)) return "id";
        return result;
    }

    private static boolean inPair(String value, String first, String second) {
        return value != null && (value.equals(first) || value.equals(second));
    }

    private static Decision hold(String raw, String reason) {
        return new Decision(raw, raw, null, reason);
    }

    private static Decision confirm(String raw, String text, String code, String reason) {
        return new Decision(raw, text, code, reason);
    }

    private static int letterCount(String text) {
        return (int) text.codePoints().filter(Character::isLetter).count();
    }

    private static int count(String text, Character.UnicodeScript script) {
        return (int) text.codePoints().filter(c -> Character.isLetter(c)
                && Character.UnicodeScript.of(c) == script).count();
    }

    private static int nativeCount(String text, String code) {
        switch (code) {
            case "ko": return count(text, Character.UnicodeScript.HANGUL);
            case "zh": return count(text, Character.UnicodeScript.HAN);
            case "ja": return count(text, Character.UnicodeScript.HIRAGANA)
                    + count(text, Character.UnicodeScript.KATAKANA) + count(text, Character.UnicodeScript.HAN);
            case "th": return count(text, Character.UnicodeScript.THAI);
            case "ru": return count(text, Character.UnicodeScript.CYRILLIC);
            case "ar": return count(text, Character.UnicodeScript.ARABIC);
            case "hi": return count(text, Character.UnicodeScript.DEVANAGARI);
            default: return count(text, Character.UnicodeScript.LATIN);
        }
    }

    private static String dominantNativeLanguage(String text, String first, String second) {
        List<String> matches = new ArrayList<>();
        int letters = letterCount(text);
        for (String language : new String[]{first, second}) {
            if (isLatin(language)) continue; // A script is not a Latin-language identifier.
            if (nativeCount(text, language) * 100 >= letters * 60) matches.add(language);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean scriptCompatible(String text, String language) {
        int letters = letterCount(text);
        int nativeLetters = nativeCount(text, language);
        // Allow a minority of borrowed words, but not a wrong-model transliteration.
        if (nativeLetters == 0 || nativeLetters * 100 < letters * 60) return false;
        if ("zh".equals(language) && (count(text, Character.UnicodeScript.HIRAGANA)
                + count(text, Character.UnicodeScript.KATAKANA) > 0)) return false;
        return true;
    }

    private static boolean isLatin(String code) {
        return !("ko".equals(code) || "zh".equals(code) || "ja".equals(code)
                || "th".equals(code) || "ru".equals(code) || "ar".equals(code) || "hi".equals(code));
    }

    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{P}]+", "");
    }

    private static boolean isShortReply(String text, String language) {
        String value = key(text);
        if ("ko".equals(language)) return value.equals("네") || value.equals("아니요") || value.equals("예");
        if ("en".equals(language)) return value.equals("yes") || value.equals("no");
        if ("ja".equals(language)) return value.equals("はい") || value.equals("いいえ");
        return false;
    }

    private static String restoreGreeting(String text, String language) {
        if (language == null) return null;
        String value = key(text);
        switch (language) {
            case "ja":
                if (value.equals("곤니치와") || value.equals("곤니찌와") || value.equals("콘니치와")
                        || value.equals("콘니찌와") || value.equals("konnichiwa")) return "こんにちは";
                if (value.equals("사요나라") || value.equals("sayonara")) return "さようなら";
                if (value.equals("아리가토") || value.equals("아리가또") || value.equals("arigato")) return "ありがとう";
                if (value.equals("스미마센") || value.equals("sumimasen")) return "すみません";
                if (value.equals("오하요") || value.equals("ohayo")) return "おはよう";
                break;
            case "en":
                if (value.equals("헬로") || value.equals("헬로우")) return "Hello";
                if (value.equals("굿모닝")) return "Good morning";
                if (value.equals("땡큐") || value.equals("쌩큐")) return "Thank you";
                if (value.equals("굿나잇")) return "Good night";
                break;
            case "zh":
                if (value.equals("니하오") || value.equals("nihao")) return "你好";
                if (value.equals("셰셰") || value.equals("시에시에") || value.equals("xiexie")) return "谢谢";
                if (value.equals("짜이찌엔") || value.equals("짜이젠") || value.equals("zaijian")) return "再见";
                break;
            case "th":
                if (value.equals("사와디캅") || value.equals("sawadeekrap")) return "สวัสดีครับ";
                if (value.equals("사와디카") || value.equals("sawadeeka")) return "สวัสดีค่ะ";
                break;
            default: break;
        }
        return null;
    }
}
