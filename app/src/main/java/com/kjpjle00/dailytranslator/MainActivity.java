package com.kjpjle00.dailytranslator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int NAVY = Color.rgb(16, 54, 92);
    private static final int TEXT = Color.rgb(24, 54, 78);
    private static final int MUTED = Color.rgb(105, 126, 143);
    private static final int TEAL = Color.rgb(12, 162, 165);
    private static final int TEAL_DARK = Color.rgb(7, 139, 143);
    private static final int BLUE = Color.rgb(32, 126, 224);
    private static final int PALE_BLUE = Color.rgb(239, 248, 255);
    private static final int PALE_GREEN = Color.rgb(239, 252, 244);
    private static final int PALE_TEAL = Color.rgb(236, 249, 249);
    private static final int BORDER = Color.rgb(220, 232, 239);
    private static final int GREEN = Color.rgb(39, 173, 83);
    private static final int WHITE = Color.WHITE;

    private LinearLayout categoryRow;
    private LinearLayout scenarioRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout page = vbox();
        page.setBackgroundColor(Color.rgb(248, 252, 254));
        page.setPadding(dp(16), dp(12), dp(16), dp(18));

        page.addView(buildHeader());
        page.addView(space(14));
        page.addView(buildCategoryTabs());
        page.addView(space(10));
        page.addView(buildScenarioChips());
        page.addView(space(12));
        page.addView(buildLanguageCard());
        page.addView(space(14));
        page.addView(buildAutoConversationCard());
        page.addView(space(12));
        page.addView(buildMySpeechCard());
        page.addView(space(10));
        page.addView(buildOtherSpeechCard());
        page.addView(space(12));
        page.addView(buildProgressButton());
        page.addView(space(10));
        page.addView(buildManualButtons());
        page.addView(space(16));
        page.addView(buildBottomNav());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scroll);
    }

    private View buildHeader() {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("A↔가", 17, WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(TEAL, 16, TEAL, 0));
        row.addView(logo, lp(dp(58), dp(48)));

        LinearLayout titleBox = vbox();
        titleBox.setPadding(dp(12), 0, 0, 0);
        TextView title = text("일상번역기", 29, NAVY, true);
        TextView sub = text("업무 · 여행 · 일상 지속세션형 자동대화", 13, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(space(2));
        titleBox.addView(sub);
        row.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = pill("⚙ 설정", 14, NAVY, WHITE, BORDER, 14);
        settings.setPadding(dp(13), dp(10), dp(13), dp(10));
        row.addView(settings);
        return row;
    }

    private View buildCategoryTabs() {
        LinearLayout wrap = hbox();
        wrap.setPadding(dp(6), dp(6), dp(6), dp(6));
        wrap.setBackground(round(WHITE, 18, BORDER, 1));
        categoryRow = wrap;

        String[] labels = {"▣ 업무용", "✈ 여행용", "⌂ 일상용"};
        for (int i = 0; i < labels.length; i++) {
            TextView tab = text(labels[i], 16, i == 1 ? WHITE : NAVY, true);
            tab.setGravity(Gravity.CENTER);
            tab.setTag(i == 1);
            tab.setBackground(round(i == 1 ? TEAL : Color.TRANSPARENT, 14, Color.TRANSPARENT, 0));
            final TextView current = tab;
            tab.setOnClickListener(v -> selectCategory(current));
            wrap.addView(tab, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        return wrap;
    }

    private View buildScenarioChips() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        scenarioRow = hbox();
        scenarioRow.setPadding(0, dp(2), dp(4), dp(2));
        String[] items = {"✈ 공항", "▰ 숙소", "🍴 식당", "▣ 교통", "▢ 쇼핑", "⚠ 긴급상황"};
        for (String item : items) {
            boolean selected = item.contains("식당");
            TextView chip = pill(item, 14, selected ? WHITE : NAVY,
                    selected ? TEAL : WHITE, BORDER, 22);
            chip.setTag(selected);
            chip.setPadding(dp(15), dp(10), dp(15), dp(10));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, dp(8), 0);
            scenarioRow.addView(chip, p);
            final TextView current = chip;
            chip.setOnClickListener(v -> selectScenario(current));
        }
        hsv.addView(scenarioRow);
        return hsv;
    }

    private View buildLanguageCard() {
        LinearLayout card = hbox();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(round(WHITE, 18, BORDER, 1));

        LinearLayout mine = languageBox("내 언어", "한국어");
        LinearLayout other = languageBox("상대 언어", "영어");

        TextView swap = text("⇄", 25, BLUE, true);
        swap.setGravity(Gravity.CENTER);
        swap.setBackground(round(PALE_BLUE, 24, PALE_BLUE, 0));
        swap.setOnClickListener(v -> {
            TextView a = (TextView) mine.getChildAt(1);
            TextView b = (TextView) other.getChildAt(1);
            CharSequence tmp = a.getText();
            a.setText(b.getText());
            b.setText(tmp);
        });

        card.addView(mine, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sp = lp(dp(48), dp(48));
        sp.setMargins(dp(8), 0, dp(8), 0);
        card.addView(swap, sp);
        card.addView(other, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private LinearLayout languageBox(String label, String language) {
        LinearLayout box = vbox();
        box.setPadding(dp(12), dp(9), dp(12), dp(9));
        box.setBackground(round(Color.rgb(252, 254, 255), 14, BORDER, 1));
        TextView l = text(label, 12, MUTED, false);
        TextView v = text(language + "   ⌄", 18, TEXT, true);
        box.addView(l);
        box.addView(space(3));
        box.addView(v);
        return box;
    }

    private View buildAutoConversationCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(15), dp(15), dp(15), dp(16));
        card.setBackground(round(WHITE, 20, BORDER, 1));

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView robot = text("◎", 22, BLUE, true);
        TextView title = text(" 자동대화", 21, TEXT, true);
        TextView badge = pill("● 자동 인식 ON", 12, GREEN, Color.rgb(237, 250, 240), Color.TRANSPARENT, 18);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));

        top.addView(robot);
        top.addView(title);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(dp(10), 0, 0, 0);
        top.addView(badge, badgeLp);
        card.addView(top);

        TextView guide = text("자연스럽고 끊김 없는 대화를 제공합니다.", 12, MUTED, false);
        guide.setGravity(Gravity.END);
        card.addView(space(4));
        card.addView(guide);

        card.addView(space(14));
        card.addView(buildFlowRow());

        card.addView(space(16));

        TextView mic = text("●\n🎙", 34, WHITE, true);
        mic.setGravity(Gravity.CENTER);
        mic.setLineSpacing(0, 0.82f);
        mic.setBackground(round(TEAL, 62, TEAL_DARK, 1));
        LinearLayout micHolder = hbox();
        micHolder.setGravity(Gravity.CENTER);
        micHolder.addView(mic, lp(dp(124), dp(124)));
        card.addView(micHolder);

        card.addView(space(10));
        TextView status = text("자동대화 세션 유지 중", 21, TEAL_DARK, true);
        status.setGravity(Gravity.CENTER);
        card.addView(status);

        TextView detail = text(
                "종료하기 전까지 계속 듣고, 같은 사람이 끊었다가 이어서 말해도 자동으로 처리합니다.",
                13, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(dp(4), dp(3), dp(4), 0);
        card.addView(detail);

        TextView note = text("1.8초 침묵은 ‘발화 조각 확정’ 기준이며 세션 종료나 강제 화자 전환이 아닙니다.",
                11, Color.rgb(119, 139, 151), false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(4), dp(7), dp(4), 0);
        card.addView(note);

        return card;
    }

    private View buildFlowRow() {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);

        String[][] steps = {
                {"🎙", "듣는 중"},
                {"▤", "번역 중"},
                {"🎙", "계속 듣기"},
                {"🔊", "음성 재생"}
        };

        for (int i = 0; i < steps.length; i++) {
            LinearLayout step = vbox();
            step.setGravity(Gravity.CENTER);
            TextView icon = text(steps[i][0], 19, i == 3 ? BLUE : TEAL_DARK, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(round(PALE_TEAL, 24, PALE_TEAL, 0));
            step.addView(icon, lp(dp(44), dp(44)));
            step.addView(space(5));
            TextView label = text(steps[i][1], 11, TEXT, true);
            label.setGravity(Gravity.CENTER);
            step.addView(label);
            row.addView(step, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (i < steps.length - 1) {
                TextView arrow = text("→", 18, MUTED, false);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, lp(dp(22), dp(44)));
            }
        }
        return row;
    }

    private View buildMySpeechCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(PALE_BLUE, 18, Color.rgb(203, 229, 249), 1));

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView who = text("●  내 말   한국어", 14, BLUE, true);
        TextView time = text("오전 09:41", 12, MUTED, false);
        head.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(time);
        card.addView(head);

        card.addView(space(8));
        TextView source = text("이 메뉴는 맵지 않게 해주세요.", 18, TEXT, true);
        card.addView(source);
        card.addView(space(6));

        LinearLayout resultRow = hbox();
        resultRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView translated = text("Please make this dish not spicy.", 16, Color.rgb(52, 87, 119), false);
        resultRow.addView(translated, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView speaker = pill("🔊", 18, NAVY, WHITE, BORDER, 18);
        speaker.setGravity(Gravity.CENTER);
        resultRow.addView(speaker, lp(dp(48), dp(44)));
        card.addView(resultRow);
        return card;
    }

    private View buildOtherSpeechCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(PALE_GREEN, 18, Color.rgb(205, 236, 213), 1));

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView who = text("●  상대방 말   영어", 14, GREEN, true);
        TextView time = text("오전 09:42", 12, MUTED, false);
        head.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(time);
        card.addView(head);

        card.addView(space(8));
        TextView source = text("Would you like anything to drink?", 18, TEXT, true);
        card.addView(source);
        card.addView(space(6));

        LinearLayout resultRow = hbox();
        resultRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView translated = text("마실 것은 무엇으로 드릴까요?", 16, Color.rgb(52, 87, 119), false);
        resultRow.addView(translated, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView earphone = pill("🎧", 18, NAVY, WHITE, BORDER, 18);
        earphone.setGravity(Gravity.CENTER);
        resultRow.addView(earphone, lp(dp(48), dp(44)));
        card.addView(resultRow);
        return card;
    }

    private View buildProgressButton() {
        TextView button = text("▮▮  자동대화 진행 중     ›", 18, WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(16), dp(16), dp(16));
        button.setBackground(round(TEAL, 25, TEAL_DARK, 1));
        return button;
    }

    private View buildManualButtons() {
        LinearLayout row = hbox();
        TextView left = pill("🎙  내가 말하기", 15, NAVY, WHITE, BORDER, 23);
        TextView right = pill("🎧  수동 듣기", 15, NAVY, WHITE, BORDER, 23);
        left.setGravity(Gravity.CENTER);
        right.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p2.setMargins(dp(6), 0, 0, 0);
        row.addView(left, p1);
        row.addView(right, p2);
        return row;
    }

    private View buildBottomNav() {
        LinearLayout row = hbox();
        row.setPadding(dp(8), dp(10), dp(8), dp(4));
        row.setBackground(round(WHITE, 20, BORDER, 1));

        String[][] items = {
                {"●", "대화"},
                {"☆", "즐겨찾기"},
                {"◷", "기록"},
                {"⚙", "설정"}
        };

        for (int i = 0; i < items.length; i++) {
            LinearLayout item = vbox();
            item.setGravity(Gravity.CENTER);
            if (i == 0) item.setBackground(round(PALE_TEAL, 16, Color.TRANSPARENT, 0));

            TextView icon = text(items[i][0], 22, i == 0 ? TEAL_DARK : Color.rgb(75, 103, 139), true);
            icon.setGravity(Gravity.CENTER);
            TextView label = text(items[i][1], 12, i == 0 ? TEAL_DARK : MUTED, i == 0);
            label.setGravity(Gravity.CENTER);

            item.addView(icon);
            item.addView(space(4));
            item.addView(label);

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(64), 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            row.addView(item, p);
        }
        return row;
    }

    private void selectCategory(TextView chosen) {
        for (int i = 0; i < categoryRow.getChildCount(); i++) {
            View v = categoryRow.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            TextView t = (TextView) v;
            boolean active = t == chosen;
            t.setTextColor(active ? WHITE : NAVY);
            t.setBackground(round(active ? TEAL : Color.TRANSPARENT, 14, Color.TRANSPARENT, 0));
        }
    }

    private void selectScenario(TextView chosen) {
        for (int i = 0; i < scenarioRow.getChildCount(); i++) {
            View v = scenarioRow.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            TextView t = (TextView) v;
            boolean active = t == chosen;
            t.setTextColor(active ? WHITE : NAVY);
            t.setBackground(round(active ? TEAL : WHITE, 22, BORDER, 1));
        }
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView pill(String value, float sp, int textColor, int bgColor, int strokeColor, int radiusDp) {
        TextView t = text(value, sp, textColor, true);
        t.setBackground(round(bgColor, radiusDp, strokeColor, strokeColor == Color.TRANSPARENT ? 0 : 1));
        return t;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private Space space(int dp) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return s;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
