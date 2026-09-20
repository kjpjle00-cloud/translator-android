package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class BasicPhraseStore {

    public static class Phrase {
        public String id;
        public String category;
        public String scenario;
        public String text;
        public boolean favorite;
        public boolean custom;

        public Phrase(String id, String category, String scenario, String text,
                      boolean favorite, boolean custom) {
            this.id = id;
            this.category = category;
            this.scenario = scenario;
            this.text = text;
            this.favorite = favorite;
            this.custom = custom;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("category", category);
            o.put("scenario", scenario);
            o.put("text", text);
            o.put("favorite", favorite);
            o.put("custom", custom);
            return o;
        }

        static Phrase fromJson(JSONObject o) throws JSONException {
            return new Phrase(
                    o.getString("id"),
                    o.getString("category"),
                    o.getString("scenario"),
                    o.getString("text"),
                    o.optBoolean("favorite", false),
                    o.optBoolean("custom", false)
            );
        }
    }

    private static final String PREFS = "daily_translator_phrase_store";
    private static final String KEY_DATA = "phrases_v1";

    private final SharedPreferences prefs;
    private final ArrayList<Phrase> phrases = new ArrayList<>();

    public BasicPhraseStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public List<Phrase> get(String category, String scenario, boolean favoritesOnly) {
        ArrayList<Phrase> out = new ArrayList<>();
        for (Phrase p : phrases) {
            if (!category.equals(p.category)) continue;
            if (!scenario.equals(p.scenario)) continue;
            if (favoritesOnly && !p.favorite) continue;
            out.add(p);
        }
        Collections.sort(out, Comparator.comparing((Phrase p) -> !p.favorite));
        return out;
    }

    public void add(String category, String scenario, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return;

        phrases.add(new Phrase(
                UUID.randomUUID().toString(),
                category,
                scenario,
                trimmed,
                false,
                true
        ));
        save();
    }

    public void update(Phrase phrase, String newText) {
        if (phrase == null) return;
        String trimmed = newText == null ? "" : newText.trim();
        if (trimmed.isEmpty()) return;
        phrase.text = trimmed;
        phrase.custom = true;
        save();
    }

    public void delete(Phrase phrase) {
        if (phrase == null) return;
        phrases.remove(phrase);
        save();
    }

    public void toggleFavorite(Phrase phrase) {
        if (phrase == null) return;
        phrase.favorite = !phrase.favorite;
        save();
    }

    private void load() {
        phrases.clear();
        String raw = prefs.getString(KEY_DATA, null);

        if (raw == null || raw.trim().isEmpty()) {
            seedDefaults();
            save();
            return;
        }

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                phrases.add(Phrase.fromJson(arr.getJSONObject(i)));
            }
            if (phrases.isEmpty()) {
                seedDefaults();
                save();
            }
        } catch (Exception e) {
            phrases.clear();
            seedDefaults();
            save();
        }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        for (Phrase p : phrases) {
            try {
                arr.put(p.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_DATA, arr.toString()).apply();
    }

    private void seedDefaults() {
        // 업무용 > 민원안내
        addDefault("업무용", "민원안내", "무슨 일로 방문하셨나요?");
        addDefault("업무용", "민원안내", "신분증을 보여주세요.");
        addDefault("업무용", "민원안내", "잠시만 기다려 주세요.");
        addDefault("업무용", "민원안내", "담당자를 연결해 드리겠습니다.");
        addDefault("업무용", "민원안내", "처리까지 시간이 조금 걸립니다.");
        addDefault("업무용", "민원안내", "필요한 서류가 있습니다.");
        addDefault("업무용", "민원안내", "연락 가능한 전화번호를 알려주세요.");
        addDefault("업무용", "민원안내", "다시 방문해 주세요.");

        // 업무용 > 서류작성
        addDefault("업무용", "서류작성", "이 신청서를 작성해 주세요.");
        addDefault("업무용", "서류작성", "성함을 적어 주세요.");
        addDefault("업무용", "서류작성", "생년월일을 적어 주세요.");
        addDefault("업무용", "서류작성", "주소를 적어 주세요.");
        addDefault("업무용", "서류작성", "여기에 서명해 주세요.");
        addDefault("업무용", "서류작성", "해당되는 항목에 체크해 주세요.");

        // 업무용 > 대기·호출
        addDefault("업무용", "대기·호출", "여기에서 잠시 기다려 주세요.");
        addDefault("업무용", "대기·호출", "순서가 되면 불러드리겠습니다.");
        addDefault("업무용", "대기·호출", "잠시 후 다시 안내해 드리겠습니다.");
        addDefault("업무용", "대기·호출", "담당자가 오고 있습니다.");

        // 업무용 > 연락·재방문
        addDefault("업무용", "연락·재방문", "연락 가능한 전화번호를 알려주세요.");
        addDefault("업무용", "연락·재방문", "확인 후 연락드리겠습니다.");
        addDefault("업무용", "연락·재방문", "이 날짜에 다시 방문해 주세요.");
        addDefault("업무용", "연락·재방문", "준비된 서류를 가지고 다시 와 주세요.");

        // 여행용 > 공항
        addDefault("여행용", "공항", "체크인은 어디에서 하나요?");
        addDefault("여행용", "공항", "이 짐을 부칠 수 있나요?");
        addDefault("여행용", "공항", "탑승구가 어디인가요?");
        addDefault("여행용", "공항", "비행기가 지연되었나요?");
        addDefault("여행용", "공항", "수하물 찾는 곳이 어디인가요?");
        addDefault("여행용", "공항", "환승하려면 어디로 가야 하나요?");

        // 여행용 > 숙소
        addDefault("여행용", "숙소", "예약했습니다.");
        addDefault("여행용", "숙소", "체크인하고 싶습니다.");
        addDefault("여행용", "숙소", "체크아웃은 몇 시인가요?");
        addDefault("여행용", "숙소", "와이파이 비밀번호를 알려주세요.");
        addDefault("여행용", "숙소", "수건을 더 받을 수 있을까요?");
        addDefault("여행용", "숙소", "짐을 잠시 맡아주실 수 있나요?");

        // 여행용 > 식당
        addDefault("여행용", "식당", "메뉴판을 보여주세요.");
        addDefault("여행용", "식당", "추천 메뉴가 무엇인가요?");
        addDefault("여행용", "식당", "이 메뉴는 맵지 않게 해주세요.");
        addDefault("여행용", "식당", "물을 주세요.");
        addDefault("여행용", "식당", "이것 하나 주세요.");
        addDefault("여행용", "식당", "계산해 주세요.");
        addDefault("여행용", "식당", "카드로 결제할 수 있나요?");
        addDefault("여행용", "식당", "포장해 주세요.");

        // 여행용 > 교통
        addDefault("여행용", "교통", "이 버스가 시내로 가나요?");
        addDefault("여행용", "교통", "기차역이 어디인가요?");
        addDefault("여행용", "교통", "표 한 장 주세요.");
        addDefault("여행용", "교통", "여기에서 내려 주세요.");
        addDefault("여행용", "교통", "목적지까지 얼마나 걸리나요?");
        addDefault("여행용", "교통", "택시를 불러 주세요.");

        // 여행용 > 쇼핑
        addDefault("여행용", "쇼핑", "이것은 얼마인가요?");
        addDefault("여행용", "쇼핑", "다른 색상이 있나요?");
        addDefault("여행용", "쇼핑", "더 큰 사이즈가 있나요?");
        addDefault("여행용", "쇼핑", "입어봐도 될까요?");
        addDefault("여행용", "쇼핑", "카드로 결제할게요.");
        addDefault("여행용", "쇼핑", "영수증을 주세요.");

        // 여행용 > 긴급상황
        addDefault("여행용", "긴급상황", "도와주세요.");
        addDefault("여행용", "긴급상황", "경찰을 불러주세요.");
        addDefault("여행용", "긴급상황", "구급차를 불러주세요.");
        addDefault("여행용", "긴급상황", "병원이 어디인가요?");
        addDefault("여행용", "긴급상황", "휴대전화를 잃어버렸습니다.");
        addDefault("여행용", "긴급상황", "여권을 잃어버렸습니다.");

        // 일상용 > 인사
        addDefault("일상용", "인사", "안녕하세요.");
        addDefault("일상용", "인사", "반갑습니다.");
        addDefault("일상용", "인사", "감사합니다.");
        addDefault("일상용", "인사", "괜찮습니다.");
        addDefault("일상용", "인사", "또 만나요.");

        // 일상용 > 소개
        addDefault("일상용", "소개", "제 이름은 무엇입니다.");
        addDefault("일상용", "소개", "저는 한국에서 왔습니다.");
        addDefault("일상용", "소개", "한국어를 사용합니다.");
        addDefault("일상용", "소개", "영어를 잘 못합니다.");
        addDefault("일상용", "소개", "천천히 말씀해 주세요.");

        // 일상용 > 약속·시간
        addDefault("일상용", "약속·시간", "지금 몇 시인가요?");
        addDefault("일상용", "약속·시간", "몇 시에 만날까요?");
        addDefault("일상용", "약속·시간", "조금 늦을 것 같습니다.");
        addDefault("일상용", "약속·시간", "내일 다시 만나요.");
        addDefault("일상용", "약속·시간", "이 시간 괜찮으세요?");

        // 일상용 > 식사
        addDefault("일상용", "식사", "식사하셨어요?");
        addDefault("일상용", "식사", "같이 식사할까요?");
        addDefault("일상용", "식사", "저는 이것을 먹고 싶어요.");
        addDefault("일상용", "식사", "정말 맛있어요.");
        addDefault("일상용", "식사", "배가 부릅니다.");

        // 일상용 > 길찾기
        addDefault("일상용", "길찾기", "여기가 어디인가요?");
        addDefault("일상용", "길찾기", "이곳에 어떻게 가나요?");
        addDefault("일상용", "길찾기", "걸어서 갈 수 있나요?");
        addDefault("일상용", "길찾기", "지도에서 보여주세요.");
        addDefault("일상용", "길찾기", "길을 잃었습니다.");

        // 일상용 > 부탁·대화
        addDefault("일상용", "부탁·대화", "잠시만 기다려 주세요.");
        addDefault("일상용", "부탁·대화", "다시 말씀해 주세요.");
        addDefault("일상용", "부탁·대화", "천천히 말씀해 주세요.");
        addDefault("일상용", "부탁·대화", "잘 이해하지 못했습니다.");
        addDefault("일상용", "부탁·대화", "이것을 도와주실 수 있나요?");
    }

    private void addDefault(String category, String scenario, String text) {
        phrases.add(new Phrase(
                "default-" + phrases.size(),
                category,
                scenario,
                text,
                false,
                false
        ));
    }
}
