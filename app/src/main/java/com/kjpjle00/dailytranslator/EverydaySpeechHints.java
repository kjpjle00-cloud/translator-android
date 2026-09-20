package com.kjpjle00.dailytranslator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Context-aware helper for everyday speech recognition.
 * It never translates or rewrites the final sentence; it only chooses among
 * SpeechRecognizer candidates and supplies optional recognition hints.
 */
public final class EverydaySpeechHints {
    public static final class Selection {
        public final int index;
        public final String text;
        public final double score;
        public final String reason;
        Selection(int index, String text, double score, String reason) {
            this.index=index; this.text=text; this.score=score; this.reason=reason;
        }
    }

    private EverydaySpeechHints() {}

    public static Selection selectBestCandidate(
            List<String> candidates, float[] confidences,
            String detectedSpeechCode, int speechConfidenceLevel,
            String initialCode, String firstCode, String secondCode,
            String category, String scenario, List<String> userHints) {
        if (candidates == null || candidates.isEmpty())
            return new Selection(-1, "", Double.NEGATIVE_INFINITY, "no_candidates");

        List<String> phrases = scoringPhrases(category, scenario, userHints);
        List<String> keywords = contextKeywords(category, scenario);
        int bestIndex=-1; double bestScore=Double.NEGATIVE_INFINITY; String bestReason="asr_rank";
        int limit=Math.min(candidates.size(), 8);

        for (int i=0;i<limit;i++) {
            String raw=candidates.get(i);
            if (raw==null || raw.trim().isEmpty()) continue;
            String text=raw.trim();
            double score=Math.max(0.0, 2.20-(i*0.34));
            String reason="asr_rank";

            if (confidences!=null && i<confidences.length && confidences[i]>=0f) {
                score += Math.min(1.0, confidences[i])*3.6;
                reason += "+confidence";
            }

            String speech=LanguageDecisionEngine.code(detectedSpeechCode);
            String initial=LanguageDecisionEngine.code(initialCode);
            if (speech!=null) {
                int script=scriptEvidence(text,speech);
                if (script>0) { score += speechConfidenceLevel>=2 ? 3.2 : 1.4; reason += "+speech_script"; }
                else if (script<0) { score -= speechConfidenceLevel>=2 ? 3.8 : 1.2; reason += "-speech_script"; }
            } else if (initial!=null && scriptEvidence(text,initial)>0) {
                score += 0.20; // soft turn hint only
            }

            if (containsHangul(text) && pairHasKorean(firstCode,secondCode)) {
                double c=contextScore(text,phrases,keywords);
                score += c;
                if (c>0) reason += "+context";
            }

            if (score>bestScore) { bestIndex=i; bestScore=score; bestReason=reason; }
        }

        if (bestIndex<0) return new Selection(-1,"",Double.NEGATIVE_INFINITY,"no_nonblank_candidate");
        return new Selection(bestIndex,candidates.get(bestIndex).trim(),bestScore,bestReason);
    }

    public static ArrayList<String> biasStrings(String category, String scenario, List<String> userHints) {
        LinkedHashSet<String> out=new LinkedHashSet<>();

        // User-created/current phrases come first so the limit never drops them.
        if (userHints!=null) {
            for (String hint:userHints) {
                if (hint==null) continue;
                String v=hint.trim();
                if (!v.isEmpty() && v.length()<=60) out.add(v);
                if (out.size()>=20) break;
            }
        }

        for (String phrase:builtInPhrases(category,scenario)) {
            if (phrase==null) continue;
            String p=phrase.trim();
            if (!p.isEmpty() && p.length()<=40) out.add(p);
            if (out.size()>=40) break;
        }
        for (String keyword:contextKeywords(category,scenario)) {
            if (keyword!=null && !keyword.trim().isEmpty()) out.add(keyword.trim());
            if (out.size()>=48) break;
        }
        return new ArrayList<>(out);
    }

    private static List<String> scoringPhrases(String category,String scenario,List<String> userHints) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if (userHints!=null) for (String s:userHints) if (s!=null && !s.trim().isEmpty()) out.add(s.trim());
        out.addAll(builtInPhrases(category,scenario));
        return new ArrayList<>(out);
    }

    private static List<String> builtInPhrases(String category,String scenario) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        add(out,"잠깐만요","잠깐만","다시 말해 주세요","다시 말해줘","천천히 말해 주세요","천천히 말해줘",
                "이거 뭐예요","이거 뭐야","어디 가요","어디 가","뭐 해요","뭐 해","괜찮아요","괜찮아",
                "맞아요","맞아","아니요","아니","잘 모르겠어요","몰라","몇 시예요","몇 시야","얼마예요","얼마야");

        if ("업무용".equals(category)) {
            add(out,"무슨 일이세요","무슨 일로 오셨어요","뭐 때문에 오셨어요","어디로 가면 돼요","뭐가 필요해요",
                    "서류 뭐 내요","뭐 가져와야 해요","여기 쓰면 돼요","어디다 써요","사인 어디 해요","담당자 있어요","확인해 볼게요");
            if ("서류작성".equals(scenario)) add(out,"이름 쓰면 돼요","주소 쓰면 돼요","여기 사인하면 돼요","어디에 체크해요");
            if ("대기·호출".equals(scenario)) add(out,"여기서 기다리면 돼요","얼마나 기다려요","언제 불러요","조금만 기다려 주세요");
            if ("연락·재방문".equals(scenario)) add(out,"전화번호 알려 주세요","언제 다시 와요","다시 오면 돼요","확인하고 연락드릴게요");
        } else if ("여행용".equals(category)) {
            if ("식당".equals(scenario)) add(out,"이거 하나 주세요","이거 주세요","이거 얼마예요","뭐가 맛있어요","뭐가 제일 맛있어요",
                    "안 맵게 해주세요","덜 맵게 해주세요","물 좀 주세요","카드 돼요","카드 되나요","포장 돼요","포장해 주세요","계산할게요","계산해 주세요");
            else if ("숙소".equals(scenario)) add(out,"예약했어요","체크인할게요","체크아웃 몇 시예요","방이 어디예요","와이파이 뭐예요","와이파이 비밀번호 뭐예요","수건 좀 주세요","짐 맡길 수 있어요");
            else if ("공항".equals(scenario)) add(out,"체크인 어디서 해요","탑승구 어디예요","몇 번 게이트예요","짐 어디서 찾아요","이 짐 부칠 수 있어요","비행기 늦어요","환승 어디로 가요");
            else if ("교통".equals(scenario)) add(out,"이거 어디 가요","이 버스 맞아요","여기서 내려요","어디서 내려요","얼마나 걸려요","표 한 장 주세요","택시 불러 주세요");
            else if ("쇼핑".equals(scenario)) add(out,"이거 얼마예요","좀 싸게 해주세요","다른 색 있어요","큰 거 있어요","작은 거 있어요","입어봐도 돼요","카드 돼요","영수증 주세요");
            else if ("긴급상황".equals(scenario)) add(out,"도와주세요","경찰 불러 주세요","구급차 불러 주세요","병원 어디예요","휴대폰 잃어버렸어요","여권 잃어버렸어요");
        } else if ("일상용".equals(category)) {
            if ("인사".equals(scenario)) add(out,"안녕","잘 지냈어요","잘 지냈어","반가워요","반가워","고마워요","고마워","또 봐요","또 봐","잘 가요","잘 가");
            else if ("소개".equals(scenario)) add(out,"이름이 뭐예요","이름 뭐예요","어디서 왔어요","저 한국에서 왔어요","한국 사람이에요","한국말만 해요","영어 잘 못해요");
            else if ("약속·시간".equals(scenario)) add(out,"몇 시에 봐요","몇 시에 만나요","언제 만나요","조금 늦어요","좀 늦을 것 같아요","내일 봐요","나중에 봐요","지금 괜찮아요");
            else if ("식사".equals(scenario)) add(out,"밥 먹었어요","밥 먹었어","뭐 먹을래요","뭐 먹을래","같이 먹어요","같이 먹자","맛있어요","맛있다","배불러요","배불러");
            else if ("길찾기".equals(scenario)) add(out,"여기 어디예요","여기 어디야","어떻게 가요","어디로 가요","걸어가도 돼요","길을 잃었어요","지도 보여 주세요");
            else if ("부탁·대화".equals(scenario)) add(out,"잠깐만요","잠깐만","다시 말해 주세요","다시 말해줘","천천히 말해 주세요","천천히 말해줘","도와주세요","좀 도와주세요","무슨 말이에요","잘 모르겠어요");
        }
        return new ArrayList<>(out);
    }

    private static List<String> contextKeywords(String category,String scenario) {
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if ("업무용".equals(category)) add(out,"서류","신분증","신청","작성","이름","주소","전화번호","담당자","확인","대기","방문","서명");
        else if ("여행용".equals(category)) {
            if ("식당".equals(scenario)) add(out,"메뉴","주문","계산","카드","포장","맵게","물","맛","추천");
            else if ("숙소".equals(scenario)) add(out,"예약","체크인","체크아웃","방","객실","수건","짐","와이파이","비밀번호");
            else if ("공항".equals(scenario)) add(out,"체크인","탑승구","게이트","수하물","짐","환승","비행기","지연");
            else if ("교통".equals(scenario)) add(out,"버스","택시","기차","역","표","내려","목적지","걸려");
            else if ("쇼핑".equals(scenario)) add(out,"얼마","가격","색","사이즈","카드","영수증","입어","큰","작은");
            else add(out,"도와","경찰","구급차","병원","잃어버렸","여권","휴대폰");
        } else add(out,"안녕","고마워","시간","몇 시","내일","오늘","밥","먹","어디","길","잠깐","다시","천천히","도와");
        return new ArrayList<>(out);
    }

    private static double contextScore(String candidate,List<String> phrases,List<String> keywords) {
        String c=key(candidate); if (c.isEmpty()) return 0.0;
        double phraseScore=0.0;
        for (String phrase:phrases) {
            String p=key(phrase); if (p.isEmpty()) continue;
            if (c.equals(p)) phraseScore=Math.max(phraseScore,2.80);
            else if (c.length()>=4 && p.length()>=4 && (c.contains(p)||p.contains(c))) phraseScore=Math.max(phraseScore,1.45);
            else {
                double d=bigramDice(c,p);
                if (d>=0.82) phraseScore=Math.max(phraseScore,1.70);
                else if (d>=0.68) phraseScore=Math.max(phraseScore,0.90);
            }
        }
        double keywordScore=0.0;
        for (String k:keywords) if (candidate.contains(k)) { keywordScore+=0.32; if (keywordScore>=1.28) break; }
        double colloquial=0.0;
        for (String m:new String[]{"이거","저거","그거","뭐","어디","얼마","몇 시","돼요","되나요","해요","할게요","주세요","좀","잠깐","괜찮아","맞아","아니","몰라","먹었어","갈까","할까"})
            if (candidate.contains(m)) { colloquial+=0.14; if (colloquial>=0.56) break; }
        return Math.min(3.25,phraseScore+keywordScore+colloquial);
    }

    private static int scriptEvidence(String text,String code) {
        code=LanguageDecisionEngine.code(code); if (code==null||text==null||text.isEmpty()) return 0;
        int letters=0,target=0,otherNative=0;
        for (int cp:text.codePoints().toArray()) {
            if (!Character.isLetter(cp)) continue; letters++;
            Character.UnicodeScript s=Character.UnicodeScript.of(cp);
            if ("ko".equals(code)&&s==Character.UnicodeScript.HANGUL) target++;
            else if ("zh".equals(code)&&s==Character.UnicodeScript.HAN) target++;
            else if ("ja".equals(code)&&(s==Character.UnicodeScript.HIRAGANA||s==Character.UnicodeScript.KATAKANA||s==Character.UnicodeScript.HAN)) target++;
            else if ("th".equals(code)&&s==Character.UnicodeScript.THAI) target++;
            else if ("ru".equals(code)&&s==Character.UnicodeScript.CYRILLIC) target++;
            else if ("ar".equals(code)&&s==Character.UnicodeScript.ARABIC) target++;
            else if ("hi".equals(code)&&s==Character.UnicodeScript.DEVANAGARI) target++;
            else if (isLatinLanguage(code)&&s==Character.UnicodeScript.LATIN) target++;
            else if (s==Character.UnicodeScript.HANGUL||s==Character.UnicodeScript.HAN||s==Character.UnicodeScript.HIRAGANA||s==Character.UnicodeScript.KATAKANA||s==Character.UnicodeScript.THAI||s==Character.UnicodeScript.CYRILLIC||s==Character.UnicodeScript.ARABIC||s==Character.UnicodeScript.DEVANAGARI) otherNative++;
        }
        if (letters==0) return 0;
        if (target*100>=letters*60) return 1;
        if (otherNative*100>=letters*60) return -1;
        return 0;
    }

    private static boolean pairHasKorean(String a,String b) { return "ko".equals(LanguageDecisionEngine.code(a))||"ko".equals(LanguageDecisionEngine.code(b)); }
    private static boolean containsHangul(String text) { return text!=null && text.codePoints().anyMatch(cp->Character.UnicodeScript.of(cp)==Character.UnicodeScript.HANGUL); }
    private static boolean isLatinLanguage(String code) { return !("ko".equals(code)||"zh".equals(code)||"ja".equals(code)||"th".equals(code)||"ru".equals(code)||"ar".equals(code)||"hi".equals(code)); }
    private static String key(String v) { return v==null?"":Normalizer.normalize(v,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}]+",""); }
    private static void add(Set<String> target,String... values) { target.addAll(Arrays.asList(values)); }
    private static List<String> bigrams(String v) { ArrayList<String> o=new ArrayList<>(); for(int i=0;i<v.length()-1;i++)o.add(v.substring(i,i+2)); return o; }
    private static double bigramDice(String a,String b) {
        if (a.equals(b)) return 1.0; if (a.length()<2||b.length()<2) return 0.0;
        List<String> aa=bigrams(a),bb=bigrams(b); boolean[] used=new boolean[bb.size()]; int m=0;
        for(String x:aa) for(int i=0;i<bb.size();i++) if(!used[i]&&x.equals(bb.get(i))){used[i]=true;m++;break;}
        return (2.0*m)/(aa.size()+bb.size());
    }
}
