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

    public static Decision decide(
            String raw,
            String first,
            String second,
            String detectedSpeechLanguage,
            int speechConfidenceLevel,
            String initialLanguage,
            boolean conflictingSpeechLanguages,
            boolean switchFailed,
            float recognitionConfidence,
            List<TextEvidence> textEvidence
    ) {
        String text = raw == null ? "" : raw.trim();
        first = code(first);
        second = code(second);
        String speech = code(detectedSpeechLanguage);
        String initial = code(initialLanguage);

        if (first == null || second == null || first.equals(second)) {
            return hold(text, "invalid_pair");
        }
        if (text.isEmpty()) return hold(text, "empty");
        if (conflictingSpeechLanguages) return hold(text, "conflicting_audio_languages");
        if (speech != null && !inPair(speech, first, second)) {
            return hold(text, "audio_outside_pair");
        }
        if (letterCount(text) == 0) return hold(text, "no_language_evidence");

        // First recover very common foreign phrases that a wrong primary ASR model
        // can spell phonetically in Korean/Latin text.
        String restoredFirst = restorePhoneticText(text, first);
        String restoredSecond = restorePhoneticText(text, second);

        if (restoredFirst != null && restoredSecond == null) {
            return confirm(text, restoredFirst, first, "pair_selected_phonetic_phrase");
        }
        if (restoredSecond != null && restoredFirst == null) {
            return confirm(text, restoredSecond, second, "pair_selected_phonetic_phrase");
        }

        String script = dominantNativeLanguage(text, first, second);
        String textLanguage = confidentTextLanguage(textEvidence);

        boolean speechConfident = speech != null
                && speechConfidenceLevel >= 2;
        boolean speechWeak = speech != null
                && speechConfidenceLevel >= 1;

        if (speechConfident) {
            if (script != null && !script.equals(speech)) {
                // If a recognizer says "Chinese" but returns Hangul, do not relabel it
                // as Korean. That is usually wrong-model transcription.
                return hold(text, "confident_audio_script_conflict");
            }

            if (textLanguage != null
                    && !textLanguage.equals(speech)
                    && script == null) {
                return hold(text, "confident_audio_text_conflict");
            }

            if (script != null && script.equals(speech)) {
                return confirm(text, text, speech, "confident_audio_and_script");
            }

            if (isLatin(speech) && textLanguage != null && textLanguage.equals(speech)) {
                return confirm(text, text, speech, "confident_audio_and_text");
            }

            if (scriptCompatible(text, speech)) {
                return confirm(text, text, speech, "confident_audio_evidence");
            }
        }

        if (speechWeak) {
            // QUICK_RESPONSE can legitimately return level 1 (NOT_CONFIDENT).
            // Use it as a soft clue, especially when it agrees with the language
            // that the current recognizer was primed to hear.
            if (script != null && script.equals(speech)) {
                return confirm(text, text, speech, "weak_audio_matches_script");
            }

            if (textLanguage != null && textLanguage.equals(speech)) {
                return confirm(text, text, speech, "weak_audio_matches_text");
            }

            if (initial != null
                    && initial.equals(speech)
                    && script == null
                    && isLatin(speech)) {
                return confirm(text, text, speech, "weak_audio_matches_primary_latin");
            }

            if (script != null && !script.equals(speech)) {
                // Important: do NOT silently call this Korean just because a Chinese
                // utterance was rendered in Hangul. Hold instead of translating wrong.
                return hold(text, "weak_audio_script_conflict");
            }
        }

        if (script != null) {
            // Turn-aware protection for Korean + foreign-language pairs:
            // when the current recognizer was deliberately primed for the foreign
            // language but returned Hangul without any language callback, only accept
            // it as Korean when it looks like actual Korean rather than phonetic noise.
            if ("ko".equals(script)
                    && initial != null
                    && !"ko".equals(initial)
                    && inPair("ko", first, second)
                    && !looksLikeNativeKorean(text)) {
                return hold(text, "hangul_after_foreign_primary_is_ambiguous");
            }

            if (recognitionConfidence >= 0f && recognitionConfidence < 0.15f) {
                return hold(text, "very_low_recognition_confidence");
            }

            return confirm(
                    text,
                    text,
                    script,
                    switchFailed
                            ? "native_script_despite_switch_failure"
                            : "native_script_evidence"
            );
        }

        if (textLanguage == null || !inPair(textLanguage, first, second)) {
            if (switchFailed) return hold(text, "switch_failed_without_other_evidence");
            return hold(
                    text,
                    textLanguage == null
                            ? "uncertain_text_language"
                            : "text_outside_pair"
            );
        }

        if (recognitionConfidence >= 0f && recognitionConfidence < 0.20f) {
            return hold(text, "very_low_recognition_confidence");
        }

        if (isLatin(textLanguage)) {
            return confirm(
                    text,
                    text,
                    textLanguage,
                    switchFailed
                            ? "latin_text_evidence_despite_switch_failure"
                            : "latin_text_evidence"
            );
        }

        if (!scriptCompatible(text, textLanguage)) {
            return hold(text, "wrong_transcription_script");
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
        return best != null && best.confidence >= 0.70f
                && best.confidence - runnerUp >= 0.10f ? best.language : null;
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

    private static boolean looksLikeNativeKorean(String text) {
        String value = key(text);
        if (value.isEmpty()) return false;

        // Common Korean endings/particles and frequent standalone replies.
        String[] cues = {
                "안녕하세요", "감사합니다", "괜찮아요", "괜찮습니다",
                "주세요", "해요", "해줘", "합니다", "입니다", "있어요", "없어요",
                "예요", "이에요", "나요", "까요", "네요", "거예요",
                "맞아요", "아니요", "네", "예", "잠시만", "어디", "얼마",
                "뭐", "무엇", "왜", "오늘", "내일"
        };
        for (String cue : cues) {
            if (value.contains(key(cue))) return true;
        }

        // Longer Hangul text with ordinary Korean sentence endings is likely native Korean.
        return value.length() >= 6
                && (value.endsWith("요")
                || value.endsWith("다")
                || value.endsWith("죠")
                || value.endsWith("까"));
    }

    private static String restorePhoneticText(String text, String language) {
        if (language == null) return null;
        String value = key(text);

        switch (language) {
            case "ja":
                if (value.equals("곤니치와") || value.equals("곤니찌와")
                        || value.equals("콘니치와") || value.equals("콘니찌와")
                        || value.equals("konnichiwa")) return "こんにちは";
                if (value.equals("사요나라") || value.equals("sayonara")) return "さようなら";
                if (value.equals("아리가토") || value.equals("아리가또")
                        || value.equals("arigato")) return "ありがとう";
                if (value.equals("스미마센") || value.equals("sumimasen")) return "すみません";
                if (value.equals("오하요") || value.equals("ohayo")) return "おはよう";
                if (value.equals("오하요고자이마스")
                        || value.equals("ohayougozaimasu")) return "おはようございます";
                break;

            case "en":
                if (value.equals("헬로") || value.equals("헬로우")) return "Hello";
                if (value.equals("굿모닝")) return "Good morning";
                if (value.equals("땡큐") || value.equals("쌩큐")) return "Thank you";
                if (value.equals("굿나잇")) return "Good night";
                if (value.equals("하와유")) return "How are you";
                break;

            case "zh":
                if (value.equals("니하오") || value.equals("nihao")) return "你好";
                if (value.equals("니하오마") || value.equals("nihaoma")) return "你好吗";
                if (value.equals("셰셰") || value.equals("쎼쎼")
                        || value.equals("시에시에") || value.equals("xiexie")) return "谢谢";
                if (value.equals("짜이찌엔") || value.equals("짜이젠")
                        || value.equals("zaijian")) return "再见";
                if (value.equals("중궈") || value.equals("zhongguo")) return "中国";
                if (value.equals("중궈니하오")
                        || value.equals("zhongguonihao")) return "中国你好";
                if (value.equals("워아이니") || value.equals("woaini")) return "我爱你";
                if (value.equals("두이부치") || value.equals("duibuqi")) return "对不起";
                if (value.equals("부커치") || value.equals("뿌커치")
                        || value.equals("bukeqi")) return "不客气";
                if (value.equals("뚜어샤오첸")
                        || value.equals("duoshaoqian")) return "多少钱";
                break;

            case "th":
                if (value.equals("사와디캅")
                        || value.equals("sawadeekrap")) return "สวัสดีครับ";
                if (value.equals("사와디카")
                        || value.equals("sawadeeka")) return "สวัสดีค่ะ";
                if (value.equals("컵쿤캅")) return "ขอบคุณครับ";
                if (value.equals("컵쿤카")) return "ขอบคุณค่ะ";
                break;

            case "es":
                if (value.equals("올라") || value.equals("hola")) return "Hola";
                if (value.equals("그라시아스") || value.equals("gracias")) return "Gracias";
                break;

            default:
                break;
        }

        return null;
    }
}
