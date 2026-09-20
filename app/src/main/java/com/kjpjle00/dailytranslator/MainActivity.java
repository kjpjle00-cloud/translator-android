package com.kjpjle00.dailytranslator;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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

        final LinearLayout page = vbox();
        page.setBackgroundColor(Color.rgb(248, 252, 254));

        final int padLeft = dp(12);
        final int padTop = dp(8);
        final int padRight = dp(12);
        final int padBottom = dp(12);
        page.setPadding(padLeft, padTop, padRight, padBottom);

        // Android 15/16 edge-to-edge 환경에서도 상태표시줄/내비게이션바와 겹치지 않게 처리
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(
                    padLeft,
                    padTop + topInset,
                    padRight,
                    padBottom + bottomInset
            );
            return insets;
        });

        page.addView(buildHeader());
        page.addView(space(9));
        page.addView(buildCategoryTabs());
        page.addView(space(7));
        page.addView(buildScenarioChips());
        page.addView(space(8));
        page.addView(buildLanguageCard());
        page.addView(space(7));
        page.addView(buildAudioRouteCard());
        page.addView(space(9));
        page.addView(buildAutoConversationCard());
        page.addView(space(9));
        page.addView(buildMySpeechCard());
        page.addView(space(8));
        page.addView(buildOtherSpeechCard());
        page.addView(space(9));
        page.addView(buildProgressButton());
        page.addView(space(8));
        page.addView(buildManualButtons());
        page.addView(space(11));
        page.addView(buildBottomNav());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.rgb(248, 252, 254));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scroll);
        page.requestApplyInsets();
    }

    private View buildHeader() {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("A↔가", 15, WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(TEAL, 14, TEAL, 0));
        row.addView(logo, lp(dp(50), dp(42)));

        LinearLayout titleBox = vbox();
        titleBox.setPadding(dp(9), 0, 0, 0);
        TextView title = text("일상번역기", 24, NAVY, true);
        TextView sub = text("업무 · 여행 · 일상  |  지속세션형 자동대화", 11, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(space(1));
        titleBox.addView(sub);
        row.addView(titleBox,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = pill("⚙ 설정", 12, NAVY, WHITE, BORDER, 12);
        settings.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.addView(settings);
        return row;
    }

    private View buildCategoryTabs() {
        LinearLayout wrap = hbox();
        wrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        wrap.setBackground(round(WHITE, 16, BORDER, 1));
        categoryRow = wrap;

        String[] labels = {"▣ 업무용", "✈ 여행용", "⌂ 일상용"};
        for (int i = 0; i < labels.length; i++) {
            TextView tab = text(labels[i], 14, i == 1 ? WHITE : NAVY, true);
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(round(
                    i == 1 ? TEAL : Color.TRANSPARENT,
                    12,
                    Color.TRANSPARENT,
                    0
            ));
            final TextView current = tab;
            tab.setOnClickListener(v -> selectCategory(current));
            wrap.addView(tab, new LinearLayout.LayoutParams(0, dp(43), 1f));
        }
        return wrap;
    }

    private View buildScenarioChips() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);

        scenarioRow = hbox();
        scenarioRow.setPadding(0, dp(1), dp(4), dp(1));

        String[] items = {
                "✈ 공항", "▰ 숙소", "🍴 식당", "▣ 교통", "▢ 쇼핑", "⚠ 긴급상황"
        };

        for (String item : items) {
            boolean selected = item.contains("식당");
            TextView chip = pill(
                    item,
                    12,
                    selected ? WHITE : NAVY,
                    selected ? TEAL : WHITE,
                    BORDER,
                    18
            );
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            p.setMargins(0, 0, dp(6), 0);
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
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(round(WHITE, 16, BORDER, 1));

        LinearLayout mine = languageBox("내 언어", "한국어");
        LinearLayout other = languageBox("상대 언어", "영어");

        TextView swap = text("⇄", 22, BLUE, true);
        swap.setGravity(Gravity.CENTER);
        swap.setBackground(round(PALE_BLUE, 21, PALE_BLUE, 0));
        swap.setOnClickListener(v -> {
            TextView a = (TextView) mine.getChildAt(1);
            TextView b = (TextView) other.getChildAt(1);
            CharSequence tmp = a.getText();
            a.setText(b.getText());
            b.setText(tmp);
        });

        card.addView(mine,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams sp = lp(dp(42), dp(42));
        sp.setMargins(dp(6), 0, dp(6), 0);
        card.addView(swap, sp);

        card.addView(other,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private LinearLayout languageBox(String label, String language) {
        LinearLayout box = vbox();
        box.setPadding(dp(10), dp(7), dp(10), dp(7));
        box.setBackground(round(Color.rgb(252, 254, 255), 12, BORDER, 1));

        TextView l = text(label, 10, MUTED, false);
        TextView v = text(language + "  ⌄", 16, TEXT, true);

        box.addView(l);
        box.addView(space(2));
        box.addView(v);
        return box;
    }

    private View buildAudioRouteCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        card.setBackground(round(WHITE, 15, BORDER, 1));

        LinearLayout titleRow = hbox();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("음성 출력", 13, TEXT, true);
        TextView hint = text("  자동대화 재생 위치", 10, MUTED, false);
        titleRow.addView(title);
        titleRow.addView(hint);
        card.addView(titleRow);
        card.addView(space(7));

        LinearLayout row = hbox();

        TextView speaker = pill(
                "🔊 외국어 번역음 · 스피커",
                11,
                NAVY,
                PALE_BLUE,
                Color.rgb(203, 229, 249),
                14
        );
        speaker.setGravity(Gravity.CENTER);
        speaker.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView earphone = pill(
                "🎧 한국어 번역음 · 이어폰",
                11,
                TEAL_DARK,
                PALE_TEAL,
                Color.rgb(194, 231, 231),
                14
        );
        earphone.setGravity(Gravity.CENTER);
        earphone.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout.LayoutParams p1 =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMargins(0, 0, dp(4), 0);

        LinearLayout.LayoutParams p2 =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.setMargins(dp(4), 0, 0, 0);

        row.addView(speaker, p1);
        row.addView(earphone, p2);
        card.addView(row);

        return card;
    }

    private View buildAutoConversationCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(12), dp(11), dp(12), dp(12));
        card.setBackground(round(WHITE, 18, BORDER, 1));

        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("◎ 자동대화", 18, TEXT, true);
        TextView badge = pill(
                "● 자동 인식 ON",
                10,
                GREEN,
                Color.rgb(237, 250, 240),
                Color.TRANSPARENT,
                15
        );
        badge.setPadding(dp(8), dp(6), dp(8), dp(6));

        top.addView(title);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        badgeLp.setMargins(dp(9), 0, 0, 0);
        top.addView(badge, badgeLp);
        card.addView(top);

        TextView guide = text(
                "종료할 때까지 계속 듣고 자동으로 번역·재생합니다.",
                10,
                MUTED,
                false
        );
        guide.setPadding(0, dp(4), 0, 0);
        card.addView(guide);

        card.addView(space(9));
        card.addView(buildFlowRow());
        card.addView(space(10));

        TextView mic = text("🎙", 32, WHITE, true);
        mic.setGravity(Gravity.CENTER);
        mic.setBackground(round(TEAL, 43, TEAL_DARK, 1));

        LinearLayout micHolder = hbox();
        micHolder.setGravity(Gravity.CENTER);
        micHolder.addView(mic, lp(dp(86), dp(86)));
        card.addView(micHolder);

        card.addView(space(7));

        TextView status = text("자동대화 세션 유지 중", 17, TEAL_DARK, true);
        status.setGravity(Gravity.CENTER);
        card.addView(status);

        TextView detail = text(
                "같은 사람이 끊었다가 다시 말해도 계속 처리합니다.",
                11,
                MUTED,
                false
        );
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(dp(3), dp(3), dp(3), 0);
        card.addView(detail);

        TextView audio = text(
                "🔊 외국어는 스피커  ·  🎧 한국어는 이어폰",
                11,
                NAVY,
                true
        );
        audio.setGravity(Gravity.CENTER);
        audio.setPadding(dp(3), dp(6), dp(3), 0);
        card.addView(audio);

        TextView note = text(
                "1.8초 침묵 = 발화 조각 확정  |  세션 종료·강제 화자 전환 아님",
                9,
                Color.rgb(119, 139, 151),
                false
        );
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(3), dp(5), dp(3), 0);
        card.addView(note);

        return card;
    }

    private View buildFlowRow() {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);

        String[][] steps = {
                {"🎙", "듣기"},
                {"▤", "번역"},
                {"🎙", "계속 듣기"},
                {"🔊/🎧", "재생"}
        };

        for (int i = 0; i < steps.length; i++) {
            LinearLayout step = vbox();
            step.setGravity(Gravity.CENTER);

            TextView icon = text(
                    steps[i][0],
                    i == 3 ? 13 : 16,
                    i == 3 ? BLUE : TEAL_DARK,
                    true
            );
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(round(PALE_TEAL, 19, PALE_TEAL, 0));
            step.addView(icon, lp(dp(38), dp(38)));

            step.addView(space(3));

            TextView label = text(steps[i][1], 9, TEXT, true);
            label.setGravity(Gravity.CENTER);
            step.addView(label);

            row.addView(step,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (i < steps.length - 1) {
                TextView arrow = text("→", 14, MUTED, false);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, lp(dp(16), dp(38)));
            }
        }
        return row;
    }

    private View buildMySpeechCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(PALE_BLUE, 16, Color.rgb(203, 229, 249), 1));

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView who = text("● 내 말  ·  한국어", 12, BLUE, true);
        TextView time = text("09:41", 10, MUTED, false);

        head.addView(who,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(time);
        card.addView(head);

        card.addView(space(6));

        TextView source = text("이 메뉴는 맵지 않게 해주세요.", 15, TEXT, true);
        card.addView(source);

        card.addView(space(5));

        TextView translated = text(
                "Please make this dish not spicy.",
                13,
                Color.rgb(52, 87, 119),
                false
        );
        card.addView(translated);

        card.addView(space(7));

        TextView speaker = pill(
                "🔊 스피커로 듣기",
                11,
                NAVY,
                WHITE,
                BORDER,
                15
        );
        speaker.setGravity(Gravity.CENTER);
        speaker.setPadding(dp(10), dp(7), dp(10), dp(7));
        card.addView(speaker);

        return card;
    }

    private View buildOtherSpeechCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(PALE_GREEN, 16, Color.rgb(205, 236, 213), 1));

        LinearLayout head = hbox();
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView who = text("● 상대방 말  ·  영어", 12, GREEN, true);
        TextView time = text("09:42", 10, MUTED, false);

        head.addView(who,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(time);
        card.addView(head);

        card.addView(space(6));

        TextView source = text(
                "Would you like anything to drink?",
                15,
                TEXT,
                true
        );
        card.addView(source);

        card.addView(space(5));

        TextView translated = text(
                "마실 것은 무엇으로 드릴까요?",
                13,
                Color.rgb(52, 87, 119),
                false
        );
        card.addView(translated);

        card.addView(space(7));

        TextView earphone = pill(
                "🎧 이어폰으로 듣기",
                11,
                TEAL_DARK,
                WHITE,
                Color.rgb(194, 231, 231),
                15
        );
        earphone.setGravity(Gravity.CENTER);
        earphone.setPadding(dp(10), dp(7), dp(10), dp(7));
        card.addView(earphone);

        return card;
    }

    private View buildProgressButton() {
        TextView button = text("■  자동대화 종료", 15, WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(round(TEAL, 21, TEAL_DARK, 1));
        return button;
    }

    private View buildManualButtons() {
        LinearLayout row = hbox();

        TextView left = pill(
                "🎙 내가 말하기",
                12,
                NAVY,
                WHITE,
                BORDER,
                19
        );
        TextView right = pill(
                "🎧 이어폰 듣기",
                12,
                NAVY,
                WHITE,
                BORDER,
                19
        );

        left.setGravity(Gravity.CENTER);
        right.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams p1 =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        p1.setMargins(0, 0, dp(4), 0);

        LinearLayout.LayoutParams p2 =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        p2.setMargins(dp(4), 0, 0, 0);

        row.addView(left, p1);
        row.addView(right, p2);
        return row;
    }

    private View buildBottomNav() {
        LinearLayout row = hbox();
        row.setPadding(dp(6), dp(7), dp(6), dp(4));
        row.setBackground(round(WHITE, 17, BORDER, 1));

        String[][] items = {
                {"●", "대화"},
                {"☆", "즐겨찾기"},
                {"◷", "기록"},
                {"⚙", "설정"}
        };

        for (int i = 0; i < items.length; i++) {
            LinearLayout item = vbox();
            item.setGravity(Gravity.CENTER);

            if (i == 0) {
                item.setBackground(round(
                        PALE_TEAL,
                        14,
                        Color.TRANSPARENT,
                        0
                ));
            }

            TextView icon = text(
                    items[i][0],
                    17,
                    i == 0 ? TEAL_DARK : Color.rgb(75, 103, 139),
                    true
            );
            icon.setGravity(Gravity.CENTER);

            TextView label = text(
                    items[i][1],
                    10,
                    i == 0 ? TEAL_DARK : MUTED,
                    i == 0
            );
            label.setGravity(Gravity.CENTER);

            item.addView(icon);
            item.addView(space(2));
            item.addView(label);

            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(0, dp(52), 1f);
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
            t.setBackground(round(
                    active ? TEAL : Color.TRANSPARENT,
                    12,
                    Color.TRANSPARENT,
                    0
            ));
        }
    }

    private void selectScenario(TextView chosen) {
        for (int i = 0; i < scenarioRow.getChildCount(); i++) {
            View v = scenarioRow.getChildAt(i);
            if (!(v instanceof TextView)) continue;

            TextView t = (TextView) v;
            boolean active = t == chosen;
            t.setTextColor(active ? WHITE : NAVY);
            t.setBackground(round(
                    active ? TEAL : WHITE,
                    18,
                    BORDER,
                    1
            ));
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
        t.setTypeface(Typeface.create(
                "sans",
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        t.setIncludeFontPadding(false);
        return t;
    }

    private TextView pill(
            String value,
            float sp,
            int textColor,
            int bgColor,
            int strokeColor,
            int radiusDp
    ) {
        TextView t = text(value, sp, textColor, true);
        t.setBackground(round(
                bgColor,
                radiusDp,
                strokeColor,
                strokeColor == Color.TRANSPARENT ? 0 : 1
        ));
        return t;
    }

    private GradientDrawable round(
            int color,
            int radiusDp,
            int strokeColor,
            int strokeDp
    ) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(strokeDp), strokeColor);
        }
        return g;
    }

    private Space space(int valueDp) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(valueDp)));
        return s;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
