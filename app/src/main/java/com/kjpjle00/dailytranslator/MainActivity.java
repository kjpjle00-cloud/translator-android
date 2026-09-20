package com.kjpjle00.dailytranslator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final int RED = Color.rgb(191, 61, 61);
    private static final int REQ_MIC = 2201;

    private final String[] categories = {"업무용", "여행용", "일상용"};

    private final Map<String, String> englishLoanwordFix = new HashMap<>();

    private BasicPhraseStore phraseStore;
    private TranslationEngine translationEngine;
    private AutoConversationEngine autoConversationEngine;
    private SharedPreferences prefs;

    private LinearLayout categoryRow;
    private LinearLayout scenarioRow;
    private LinearLayout phraseList;
    private LinearLayout phraseBody;
    private TextView phraseTitle;
    private TextView phraseToggle;
    private TextView favoriteFilterButton;

    private TextView myLanguageButton;
    private TextView otherLanguageButton;

    private TextView autoBadge;
    private TextView autoStatus;
    private TextView autoButton;
    private TextView micButton;

    private TextView mySourceLabel;
    private TextView mySourceText;
    private TextView myTranslatedText;
    private TextView otherSourceLabel;
    private TextView otherSourceText;
    private TextView otherTranslatedText;

    private String selectedCategory = "여행용";
    private String selectedScenario = "식당";
    private boolean favoritesOnly = false;
    private boolean phraseExpanded = false;
    private boolean autoStartAfterPermission = false;

    private AppLanguage myLanguage = AppLanguage.KOREAN;
    private AppLanguage otherLanguage = AppLanguage.ENGLISH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initLoanwordFixes();

        prefs = getSharedPreferences("daily_translator_settings", MODE_PRIVATE);
        myLanguage = AppLanguage.byCode(
                prefs.getString("my_language", AppLanguage.KOREAN.code)
        );
        otherLanguage = AppLanguage.byCode(
                prefs.getString("other_language", AppLanguage.ENGLISH.code)
        );

        if (myLanguage.code.equals(otherLanguage.code)) {
            otherLanguage = AppLanguage.ENGLISH;
            if (myLanguage.code.equals(AppLanguage.ENGLISH.code)) {
                otherLanguage = AppLanguage.KOREAN;
            }
        }

        phraseStore = new BasicPhraseStore(this);
        translationEngine = new TranslationEngine(this);
        translationEngine.preparePair(myLanguage, otherLanguage);

        autoConversationEngine = new AutoConversationEngine(
                this,
                new AutoConversationEngine.Listener() {
                    @Override
                    public void onListening() {
                        runOnUiThread(() -> {
                            if (autoStatus != null) {
                                autoStatus.setText(
                                        "듣는 중 · "
                                                + myLanguage.name
                                                + " / "
                                                + otherLanguage.name
                                );
                            }
                        });
                    }

                    @Override
                    public void onPartial(String text, String languageTag) {
                        runOnUiThread(() -> {
                            if (autoStatus != null && text != null && !text.trim().isEmpty()) {
                                autoStatus.setText("듣는 중 · " + text);
                            }
                        });
                    }

                    @Override
                    public void onUtterance(String text, String languageTag) {
                        handleAutoUtterance(text, languageTag);
                    }

                    @Override
                    public void onIdleRetry() {
                        runOnUiThread(() -> {
                            if (autoStatus != null && autoConversationEngine.isActive()) {
                                autoStatus.setText("자동대화 유지 중 · 말씀하세요");
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (autoStatus != null) {
                                if (!autoConversationEngine.isActive()) updateAutoUi();
                                autoStatus.setText((autoConversationEngine.isActive()
                                        ? "자동대화 유지 중 · " : "자동대화 중지 · ") + message);
                            }
                        });
                    }
                }
        );
        autoConversationEngine.setLanguagePair(myLanguage, otherLanguage);

        translationEngine.setPlaybackListener(new TranslationEngine.PlaybackListener() {
            @Override public void onRequestStarted() {
                autoConversationEngine.pauseForPlayback();
            }
            @Override public void onPlaybackPrepared(String text) {
                autoConversationEngine.suppressPlaybackEcho(text);
            }
            @Override public void onRequestFinished() {
                autoConversationEngine.resumeAfterProcessing();
            }
        });

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
        page.addView(buildPhrasePanel());
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

        refreshCategoryTabs();
        refreshScenarios();
        refreshPhrases();
        updatePhraseVisibility();
        updateLanguageUi();
        updateAutoUi();
    }

    private void initLoanwordFixes() {
        englishLoanwordFix.put("굿모닝", "Good morning");
        englishLoanwordFix.put("굿 모닝", "Good morning");
        englishLoanwordFix.put("헬로", "Hello");
        englishLoanwordFix.put("헬로우", "Hello");
        englishLoanwordFix.put("하이", "Hi");
        englishLoanwordFix.put("땡큐", "Thank you");
        englishLoanwordFix.put("쌩큐", "Thank you");
        englishLoanwordFix.put("쏘리", "Sorry");
        englishLoanwordFix.put("쏘리요", "Sorry");
        englishLoanwordFix.put("굿나잇", "Good night");
        englishLoanwordFix.put("굿 나잇", "Good night");
        englishLoanwordFix.put("굿이브닝", "Good evening");
        englishLoanwordFix.put("굿 이브닝", "Good evening");
        englishLoanwordFix.put("바이", "Bye");
        englishLoanwordFix.put("바이바이", "Bye bye");
        englishLoanwordFix.put("익스큐즈미", "Excuse me");
        englishLoanwordFix.put("익스큐즈 미", "Excuse me");
        englishLoanwordFix.put("오케이", "Okay");
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
        TextView sub = text("다국어 선택 · 빠른 번역 · 지속 자동대화", 11, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(space(1));
        titleBox.addView(sub);
        row.addView(titleBox,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView settings = pill("⚙ 설정", 12, NAVY, WHITE, BORDER, 12);
        settings.setPadding(dp(10), dp(8), dp(10), dp(8));
        settings.setOnClickListener(v -> showSettingsDialog());
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
            TextView tab = text(labels[i], 14, NAVY, true);
            tab.setGravity(Gravity.CENTER);
            tab.setTag(categories[i]);
            final String category = categories[i];
            tab.setOnClickListener(v -> {
                selectedCategory = category;
                selectedScenario = defaultScenario(category);
                favoritesOnly = false;
                refreshCategoryTabs();
                refreshScenarios();
                refreshPhrases();
            });
            wrap.addView(tab, new LinearLayout.LayoutParams(0, dp(43), 1f));
        }
        return wrap;
    }

    private void refreshCategoryTabs() {
        if (categoryRow == null) return;
        for (int i = 0; i < categoryRow.getChildCount(); i++) {
            View v = categoryRow.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            TextView t = (TextView) v;
            boolean active = selectedCategory.equals(String.valueOf(t.getTag()));
            t.setTextColor(active ? WHITE : NAVY);
            t.setBackground(round(
                    active ? TEAL : Color.TRANSPARENT,
                    12,
                    Color.TRANSPARENT,
                    0
            ));
        }
    }

    private View buildScenarioChips() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        scenarioRow = hbox();
        scenarioRow.setPadding(0, dp(1), dp(4), dp(1));
        hsv.addView(scenarioRow);
        return hsv;
    }

    private void refreshScenarios() {
        if (scenarioRow == null) return;
        scenarioRow.removeAllViews();

        String[] scenarios = scenariosFor(selectedCategory);
        for (String scenario : scenarios) {
            boolean active = scenario.equals(selectedScenario);
            TextView chip = pill(
                    scenarioIcon(scenario) + " " + scenario,
                    12,
                    active ? WHITE : NAVY,
                    active ? TEAL : WHITE,
                    BORDER,
                    18
            );
            chip.setPadding(dp(12), dp(8), dp(12), dp(8));
            chip.setTag(scenario);

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            p.setMargins(0, 0, dp(6), 0);
            scenarioRow.addView(chip, p);

            chip.setOnClickListener(v -> {
                selectedScenario = String.valueOf(v.getTag());
                favoritesOnly = false;
                refreshScenarios();
                refreshPhrases();
            });
        }
    }

    private View buildPhrasePanel() {
        LinearLayout card = vbox();
        card.setPadding(dp(11), dp(10), dp(11), dp(10));
        card.setBackground(round(WHITE, 16, BORDER, 1));

        LinearLayout header = hbox();
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = vbox();
        TextView title = text("기본 멘트", 17, TEXT, true);
        phraseTitle = text("", 10, MUTED, false);
        titleBox.addView(title);
        titleBox.addView(space(2));
        titleBox.addView(phraseTitle);
        header.addView(titleBox,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        phraseToggle = pill("펼치기 ▼", 11, TEAL_DARK, PALE_TEAL,
                Color.rgb(194, 231, 231), 14);
        phraseToggle.setGravity(Gravity.CENTER);
        phraseToggle.setPadding(dp(10), dp(7), dp(10), dp(7));
        header.addView(phraseToggle);

        View.OnClickListener toggle = v -> {
            phraseExpanded = !phraseExpanded;
            updatePhraseVisibility();
            if (phraseExpanded) warmCurrentPhrases();
        };
        header.setOnClickListener(toggle);
        phraseToggle.setOnClickListener(toggle);

        card.addView(header);

        phraseBody = vbox();
        phraseBody.addView(space(8));

        LinearLayout tools = hbox();

        TextView add = pill("+ 내 문구", 11, WHITE, TEAL, TEAL, 14);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(10), dp(7), dp(10), dp(7));
        add.setOnClickListener(v -> showPhraseEditor(null));
        tools.addView(add);

        favoriteFilterButton = pill("☆ 즐겨찾기", 11, NAVY, WHITE, BORDER, 14);
        favoriteFilterButton.setGravity(Gravity.CENTER);
        favoriteFilterButton.setPadding(dp(10), dp(7), dp(10), dp(7));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fp.setMargins(dp(6), 0, 0, 0);
        tools.addView(favoriteFilterButton, fp);

        favoriteFilterButton.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            refreshPhrases();
        });

        phraseBody.addView(tools);
        phraseBody.addView(space(8));

        phraseList = vbox();
        phraseBody.addView(phraseList);
        card.addView(phraseBody);

        return card;
    }

    private void updatePhraseVisibility() {
        if (phraseBody == null || phraseToggle == null) return;
        phraseBody.setVisibility(phraseExpanded ? View.VISIBLE : View.GONE);
        phraseToggle.setText(phraseExpanded ? "접기 ▲" : "펼치기 ▼");
    }

    private void refreshPhrases() {
        if (phraseList == null) return;

        phraseTitle.setText(selectedCategory + "  ›  " + selectedScenario);
        favoriteFilterButton.setText(favoritesOnly ? "★ 전체보기" : "☆ 즐겨찾기");
        favoriteFilterButton.setTextColor(favoritesOnly ? TEAL_DARK : NAVY);
        favoriteFilterButton.setBackground(round(
                favoritesOnly ? PALE_TEAL : WHITE,
                14,
                favoritesOnly ? Color.rgb(194, 231, 231) : BORDER,
                1
        ));

        phraseList.removeAllViews();
        List<BasicPhraseStore.Phrase> items =
                phraseStore.get(selectedCategory, selectedScenario, favoritesOnly);

        if (items.isEmpty()) {
            TextView empty = text(
                    favoritesOnly
                            ? "이 상황에 즐겨찾기한 문구가 없습니다."
                            : "등록된 문구가 없습니다. ‘+ 내 문구’로 추가해 주세요.",
                    12,
                    MUTED,
                    false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(16), dp(8), dp(16));
            phraseList.addView(empty);
            return;
        }

        int count = 0;
        for (BasicPhraseStore.Phrase phrase : items) {
            phraseList.addView(buildPhraseRow(phrase));
            count++;
            if (count < items.size()) phraseList.addView(space(6));
        }

        if (phraseExpanded) warmCurrentPhrases();
    }

    private View buildPhraseRow(BasicPhraseStore.Phrase phrase) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(7), dp(7), dp(7));
        row.setBackground(round(
                phrase.custom ? Color.rgb(255, 253, 242) : Color.rgb(250, 253, 255),
                13,
                BORDER,
                1
        ));

        TextView star = text(phrase.favorite ? "★" : "☆", 20,
                phrase.favorite ? Color.rgb(230, 160, 26) : MUTED, true);
        star.setGravity(Gravity.CENTER);
        star.setOnClickListener(v -> {
            phraseStore.toggleFavorite(phrase);
            refreshPhrases();
        });
        row.addView(star, lp(dp(34), dp(36)));

        LinearLayout center = vbox();
        center.setPadding(dp(4), 0, dp(4), 0);

        TextView phraseText = text(phrase.text, 12, TEXT, true);
        TextView type = text(
                phrase.custom ? "내 문구" : "기본 문구",
                9,
                phrase.custom ? Color.rgb(155, 118, 27) : MUTED,
                false
        );

        center.addView(phraseText);
        center.addView(space(2));
        center.addView(type);
        row.addView(center,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView speaker = pill("🔊", 16, NAVY, PALE_BLUE,
                Color.rgb(203, 229, 249), 15);
        speaker.setGravity(Gravity.CENTER);
        row.addView(speaker, lp(dp(40), dp(38)));
        speaker.setOnClickListener(v -> playPhraseImmediately(phrase, speaker));

        TextView edit = pill("수정", 9, NAVY, WHITE, BORDER, 11);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(6), dp(5), dp(6), dp(5));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        ep.setMargins(dp(4), 0, 0, 0);
        row.addView(edit, ep);
        edit.setOnClickListener(v -> showPhraseEditor(phrase));

        TextView delete = pill("삭제", 9, RED, WHITE,
                Color.rgb(241, 210, 210), 11);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(6), dp(5), dp(6), dp(5));
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dpv.setMargins(dp(4), 0, 0, 0);
        row.addView(delete, dpv);
        delete.setOnClickListener(v -> confirmDelete(phrase));

        center.setOnClickListener(v -> playPhraseImmediately(phrase, speaker));
        return row;
    }

    private void playPhraseImmediately(BasicPhraseStore.Phrase phrase, TextView button) {
        if (phrase == null || phrase.text == null || phrase.text.trim().isEmpty()) return;
        if (translationEngine.isBusy()) {
            Toast.makeText(this, "현재 번역 음성이 끝난 뒤 다시 눌러 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        button.setEnabled(false);
        button.setText("…");

        translationEngine.translateAndSpeak(
                phrase.text,
                AppLanguage.KOREAN,
                otherLanguage,
                true,
                new TranslationEngine.Callback() {
                    @Override
                    public void onSuccess(String translatedText) {
                        runOnUiThread(() -> {
                            button.setText("🔊");
                            button.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            button.setText("🔊");
                            button.setEnabled(true);
                            Toast.makeText(
                                    MainActivity.this,
                                    "번역 실패: " + message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onSpeechComplete() {
                    }

                    @Override
                    public void onCancelled() {
                        runOnUiThread(() -> {
                            button.setText("🔊");
                            button.setEnabled(true);
                        });
                    }
                }
        );
    }

    private void warmCurrentPhrases() {
        List<BasicPhraseStore.Phrase> items =
                phraseStore.get(selectedCategory, selectedScenario, favoritesOnly);

        ArrayList<String> texts = new ArrayList<>();
        for (BasicPhraseStore.Phrase p : items) {
            if (p.text != null && !p.text.trim().isEmpty()) {
                texts.add(p.text);
            }
        }

        translationEngine.prewarm(
                texts,
                AppLanguage.KOREAN,
                otherLanguage
        );
    }

    private void showPhraseEditor(BasicPhraseStore.Phrase phrase) {
        final EditText input = new EditText(this);
        input.setText(phrase == null ? "" : phrase.text);
        input.setHint("사용할 문구를 입력하세요.");
        input.setTextSize(16);
        input.setTextColor(TEXT);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(round(Color.rgb(250, 253, 255), 12, BORDER, 1));

        LinearLayout holder = vbox();
        holder.setPadding(dp(18), dp(8), dp(18), 0);
        holder.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(phrase == null
                        ? selectedCategory + " · " + selectedScenario + " 문구 추가"
                        : "문구 수정")
                .setView(holder)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) {
                    Toast.makeText(this, "문구를 입력해 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (phrase == null) {
                    phraseStore.add(selectedCategory, selectedScenario, value);
                } else {
                    phraseStore.update(phrase, value);
                }

                refreshPhrases();
                dialog.dismiss();
            });

            input.requestFocus();
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 200);
        });

        dialog.show();
    }

    private void confirmDelete(BasicPhraseStore.Phrase phrase) {
        new AlertDialog.Builder(this)
                .setTitle("문구 삭제")
                .setMessage("이 문구를 삭제할까요?\n\n" + phrase.text)
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> {
                    phraseStore.delete(phrase);
                    refreshPhrases();
                })
                .show();
    }

    private View buildLanguageCard() {
        LinearLayout card = hbox();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(round(WHITE, 16, BORDER, 1));

        myLanguageButton = languageBox("내 언어", myLanguage.name);
        otherLanguageButton = languageBox("상대 언어", otherLanguage.name);

        myLanguageButton.setOnClickListener(v -> showLanguagePicker(true));
        otherLanguageButton.setOnClickListener(v -> showLanguagePicker(false));

        TextView swap = text("⇄", 22, BLUE, true);
        swap.setGravity(Gravity.CENTER);
        swap.setBackground(round(PALE_BLUE, 21, PALE_BLUE, 0));
        swap.setOnClickListener(v -> {
            AppLanguage temp = myLanguage;
            myLanguage = otherLanguage;
            otherLanguage = temp;
            onLanguageChanged();
        });

        card.addView(myLanguageButton,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams sp = lp(dp(42), dp(42));
        sp.setMargins(dp(6), 0, dp(6), 0);
        card.addView(swap, sp);

        card.addView(otherLanguageButton,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private TextView languageBox(String label, String language) {
        TextView box = text(label + "\n" + language + "  ⌄", 13, TEXT, true);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(round(Color.rgb(252, 254, 255), 12, BORDER, 1));
        return box;
    }

    private void showLanguagePicker(boolean forMine) {
        String[] names = new String[AppLanguage.ALL.size()];
        for (int i = 0; i < AppLanguage.ALL.size(); i++) {
            names[i] = AppLanguage.ALL.get(i).name;
        }

        AppLanguage current = forMine ? myLanguage : otherLanguage;

        new AlertDialog.Builder(this)
                .setTitle(forMine ? "내 언어 선택" : "상대 언어 선택")
                .setSingleChoiceItems(
                        names,
                        AppLanguage.indexOf(current),
                        (dialog, which) -> {
                            AppLanguage chosen = AppLanguage.ALL.get(which);

                            if (forMine) {
                                if (chosen.code.equals(otherLanguage.code)) {
                                    Toast.makeText(
                                            this,
                                            "내 언어와 상대 언어는 다르게 선택해 주세요.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    return;
                                }
                                myLanguage = chosen;
                            } else {
                                if (chosen.code.equals(myLanguage.code)) {
                                    Toast.makeText(
                                            this,
                                            "내 언어와 상대 언어는 다르게 선택해 주세요.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    return;
                                }
                                otherLanguage = chosen;
                            }

                            dialog.dismiss();
                            onLanguageChanged();
                        }
                )
                .setNegativeButton("취소", null)
                .show();
    }

    private void onLanguageChanged() {
        prefs.edit()
                .putString("my_language", myLanguage.code)
                .putString("other_language", otherLanguage.code)
                .apply();

        translationEngine.stopSpeaking();
        translationEngine.preparePair(myLanguage, otherLanguage);
        autoConversationEngine.setLanguagePair(myLanguage, otherLanguage);

        updateLanguageUi();
        refreshPhrases();

        if (autoConversationEngine.isActive()) {
            updateAutoUi();
        }
    }

    private void updateLanguageUi() {
        if (myLanguageButton != null) {
            myLanguageButton.setText("내 언어\n" + myLanguage.name + "  ⌄");
        }
        if (otherLanguageButton != null) {
            otherLanguageButton.setText("상대 언어\n" + otherLanguage.name + "  ⌄");
        }

        if (mySourceLabel != null) {
            mySourceLabel.setText(
                    "● 내 말 · "
                            + myLanguage.name
                            + " → "
                            + otherLanguage.name
            );
        }
        if (otherSourceLabel != null) {
            otherSourceLabel.setText(
                    "● 상대방 말 · "
                            + otherLanguage.name
                            + " → "
                            + myLanguage.name
            );
        }
    }

    private View buildAudioRouteCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(11), dp(9), dp(11), dp(9));
        card.setBackground(round(WHITE, 15, BORDER, 1));

        TextView title = text("음성 출력", 13, TEXT, true);
        card.addView(title);
        card.addView(space(6));

        LinearLayout row = hbox();

        TextView speaker = pill(
                "🔊 내 말 번역음",
                11,
                NAVY,
                PALE_BLUE,
                Color.rgb(203, 229, 249),
                14
        );
        speaker.setGravity(Gravity.CENTER);
        speaker.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView earphone = pill(
                "🎧 상대방 번역음",
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
        autoBadge = pill(
                "○ 대기",
                10,
                MUTED,
                Color.rgb(246, 248, 250),
                BORDER,
                15
        );
        autoBadge.setPadding(dp(8), dp(6), dp(8), dp(6));

        top.addView(title);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        badgeLp.setMargins(dp(9), 0, 0, 0);
        top.addView(autoBadge, badgeLp);
        card.addView(top);

        TextView guide = text(
                "선택한 두 언어만 판정합니다. 애매하거나 말이 겹치면 번역하지 않고 다시 듣습니다.",
                10,
                MUTED,
                false
        );
        guide.setPadding(0, dp(4), 0, 0);
        card.addView(guide);

        card.addView(space(8));
        card.addView(buildFlowRow());
        card.addView(space(9));

        micButton = text("🎙", 29, WHITE, true);
        micButton.setGravity(Gravity.CENTER);
        micButton.setBackground(round(TEAL, 39, TEAL_DARK, 1));
        micButton.setOnClickListener(v -> toggleAutoConversation());

        LinearLayout micHolder = hbox();
        micHolder.setGravity(Gravity.CENTER);
        micHolder.addView(micButton, lp(dp(78), dp(78)));
        card.addView(micHolder);

        card.addView(space(6));

        autoStatus = text("자동대화 대기", 16, TEAL_DARK, true);
        autoStatus.setGravity(Gravity.CENTER);
        card.addView(autoStatus);

        TextView note = text(
                "약 1.8초 침묵 후 처리 · 재생이 끝나면 자동으로 다시 듣습니다.",
                9,
                Color.rgb(119, 139, 151),
                false
        );
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(3), dp(5), dp(3), 0);
        card.addView(note);

        card.addView(space(8));

        autoButton = text("▶ 자동대화 시작", 14, WHITE, true);
        autoButton.setGravity(Gravity.CENTER);
        autoButton.setPadding(dp(12), dp(11), dp(12), dp(11));
        autoButton.setBackground(round(TEAL, 20, TEAL_DARK, 1));
        autoButton.setOnClickListener(v -> toggleAutoConversation());
        card.addView(autoButton);

        return card;
    }

    private View buildFlowRow() {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);

        String[][] steps = {
                {"🎙", "자동 듣기"},
                {"◎", "언어 감지"},
                {"▤", "즉시 번역"},
                {"↻", "다시 듣기"}
        };

        for (int i = 0; i < steps.length; i++) {
            LinearLayout step = vbox();
            step.setGravity(Gravity.CENTER);

            TextView icon = text(steps[i][0], 15, TEAL_DARK, true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(round(PALE_TEAL, 18, PALE_TEAL, 0));
            step.addView(icon, lp(dp(36), dp(36)));
            step.addView(space(3));

            TextView label = text(steps[i][1], 9, TEXT, true);
            label.setGravity(Gravity.CENTER);
            step.addView(label);

            row.addView(step,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (i < steps.length - 1) {
                TextView arrow = text("→", 13, MUTED, false);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, lp(dp(14), dp(36)));
            }
        }
        return row;
    }

    private void toggleAutoConversation() {
        if (autoConversationEngine.isActive()) {
            autoConversationEngine.stop();
            translationEngine.stopSpeaking();
            updateAutoUi();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            autoStartAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }

        startAutoConversation();
    }

    private void startAutoConversation() {
        if (!autoConversationEngine.isAvailable()) {
            Toast.makeText(
                    this,
                    "이 기기에서 음성 인식 서비스를 사용할 수 없습니다.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        translationEngine.preparePair(myLanguage, otherLanguage);
        autoConversationEngine.setLanguagePair(myLanguage, otherLanguage);
        autoConversationEngine.start();
        updateAutoUi();
    }

    private void updateAutoUi() {
        if (autoBadge == null || autoStatus == null || autoButton == null) return;

        boolean active = autoConversationEngine != null && autoConversationEngine.isActive();

        if (active) {
            autoBadge.setText("● 자동 듣기 ON");
            autoBadge.setTextColor(GREEN);
            autoBadge.setBackground(round(
                    Color.rgb(237, 250, 240),
                    15,
                    Color.TRANSPARENT,
                    0
            ));
            autoStatus.setText(
                    "자동대화 유지 중 · "
                            + myLanguage.name
                            + " / "
                            + otherLanguage.name
            );
            autoButton.setText("■ 자동대화 종료");
            micButton.setAlpha(1f);
        } else {
            autoBadge.setText("○ 대기");
            autoBadge.setTextColor(MUTED);
            autoBadge.setBackground(round(
                    Color.rgb(246, 248, 250),
                    15,
                    BORDER,
                    1
            ));
            autoStatus.setText("자동대화 대기");
            autoButton.setText("▶ 자동대화 시작");
            micButton.setAlpha(0.75f);
        }
    }

    private void handleAutoUtterance(String rawText, String detectedTag) {
        if (rawText == null || rawText.trim().isEmpty()) {
            autoConversationEngine.resumeAfterProcessing();
            return;
        }

        RecognitionDecision decision = decisionFromDetectedTag(rawText, detectedTag);
        if (decision == null) {
            runOnUiThread(() ->
                    autoStatus.setText("언어 판단 불확실 · 다시 말씀해 주세요.")
            );
            autoConversationEngine.resumeAfterProcessing();
            return;
        }

        AppLanguage source = decision.fromMyLanguage ? myLanguage : otherLanguage;
        AppLanguage target = decision.fromMyLanguage ? otherLanguage : myLanguage;
        String sourceText = decision.text;

        runOnUiThread(() -> {
            autoStatus.setText(
                    source.name + " 감지 · 번역 중"
            );

            if (decision.fromMyLanguage) {
                mySourceText.setText(sourceText);
                myTranslatedText.setText("번역 중...");
            } else {
                otherSourceText.setText(sourceText);
                otherTranslatedText.setText("번역 중...");
            }
        });

        translationEngine.translateAndSpeak(
                sourceText,
                source,
                target,
                true,
                new TranslationEngine.Callback() {
                    @Override
                    public void onSuccess(String translatedText) {
                        runOnUiThread(() -> {
                            if (decision.fromMyLanguage) {
                                myTranslatedText.setText(translatedText);
                            } else {
                                otherTranslatedText.setText(translatedText);
                            }
                            autoStatus.setText("음성 재생 중 · " + target.name);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (decision.fromMyLanguage) {
                                myTranslatedText.setText("번역 실패: " + message);
                            } else {
                                otherTranslatedText.setText("번역 실패: " + message);
                            }
                            autoStatus.setText("자동대화 유지 중 · 다시 듣기");
                        });
                    }

                    @Override
                    public void onSpeechComplete() {
                        runOnUiThread(() -> {
                            if (autoConversationEngine.isActive()) {
                                autoStatus.setText("자동대화 유지 중 · 말씀하세요");
                            }
                        });
                    }
                }
        );
    }

    private RecognitionDecision decisionFromDetectedTag(
            String rawText,
            String detectedTag
    ) {
        if (rawText == null || rawText.trim().isEmpty()
                || detectedTag == null || detectedTag.trim().isEmpty()) {
            return null;
        }

        String detected = detectedTag.toLowerCase(Locale.ROOT);
        String myCode = myLanguage.code.toLowerCase(Locale.ROOT);
        String otherCode = otherLanguage.code.toLowerCase(Locale.ROOT);

        boolean mine = detected.startsWith(myCode);
        boolean other = detected.startsWith(otherCode);

        if (myLanguage.code.equals("tl") && detected.startsWith("fil")) {
            mine = true;
        }
        if (otherLanguage.code.equals("tl") && detected.startsWith("fil")) {
            other = true;
        }

        if (mine == other) {
            return null;
        }

        return new RecognitionDecision(rawText.trim(), mine);
    }

    private RecognitionDecision decideRecognition(String rawText, String detectedTag) {
        String textValue = rawText.trim();

        // 영어 음성을 한국어 발음으로 잘못 받아 적는 흔한 경우 보정
        if (otherLanguage.code.equals("en")
                && myLanguage.code.equals("ko")) {
            String fixed = englishLoanwordFix.get(textValue);
            if (fixed != null) {
                return new RecognitionDecision(fixed, false);
            }
        }

        if (myLanguage.code.equals("en")
                && otherLanguage.code.equals("ko")) {
            String fixed = englishLoanwordFix.get(textValue);
            if (fixed != null) {
                return new RecognitionDecision(fixed, true);
            }
        }

        // 문자 형태가 명확한 경우 음성인식기의 잘못된 언어 태그보다 우선
        String scriptCode = detectScriptLanguage(textValue);
        if (scriptCode != null) {
            if (scriptCode.equals(myLanguage.code)) {
                return new RecognitionDecision(textValue, true);
            }
            if (scriptCode.equals(otherLanguage.code)) {
                return new RecognitionDecision(textValue, false);
            }
        }

        // 한쪽이 비라틴 문자, 다른 쪽이 라틴 문자면 라틴 여부로 보완
        if (isLatinDominant(textValue)) {
            boolean myLatin = isLatinLanguage(myLanguage.code);
            boolean otherLatin = isLatinLanguage(otherLanguage.code);

            if (myLatin && !otherLatin) {
                return new RecognitionDecision(textValue, true);
            }
            if (!myLatin && otherLatin) {
                return new RecognitionDecision(textValue, false);
            }
        }

        // Android가 감지한 언어 태그를 선택한 두 언어와만 비교
        if (detectedTag != null && !detectedTag.trim().isEmpty()) {
            String detectedCode = detectedTag.toLowerCase(Locale.ROOT);

            if (detectedCode.startsWith(myLanguage.code.toLowerCase(Locale.ROOT))) {
                return new RecognitionDecision(textValue, true);
            }
            if (detectedCode.startsWith(otherLanguage.code.toLowerCase(Locale.ROOT))) {
                return new RecognitionDecision(textValue, false);
            }

            // 필리핀 음성 인식에서 fil/tl 표기 차이 허용
            if (myLanguage.code.equals("tl") && detectedCode.startsWith("fil")) {
                return new RecognitionDecision(textValue, true);
            }
            if (otherLanguage.code.equals("tl") && detectedCode.startsWith("fil")) {
                return new RecognitionDecision(textValue, false);
            }
        }

        // 마지막 보조: 한글이 포함돼 있고 한쪽이 한국어라면 한국어
        if (containsHangul(textValue)) {
            if (myLanguage.code.equals("ko")) {
                return new RecognitionDecision(textValue, true);
            }
            if (otherLanguage.code.equals("ko")) {
                return new RecognitionDecision(textValue, false);
            }
        }

        // 판단 불가 시 내 언어로 처리
        return new RecognitionDecision(textValue, true);
    }

    private String detectScriptLanguage(String value) {
        int hangul = 0;
        int kana = 0;
        int han = 0;
        int thai = 0;
        int cyrillic = 0;
        int arabic = 0;
        int devanagari = 0;
        int letters = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;

            if (c >= '\uAC00' && c <= '\uD7A3') {
                hangul++;
            } else if ((c >= '\u3040' && c <= '\u30FF')) {
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
            }
        }

        if (letters == 0) return null;

        if (hangul > 0 && hangul * 2 >= letters) return "ko";
        if (kana > 0) return "ja";
        if (thai > 0) return "th";
        if (cyrillic > 0) return "ru";
        if (arabic > 0) return "ar";
        if (devanagari > 0) return "hi";

        if (han > 0) {
            if (myLanguage.code.equals("ja") || otherLanguage.code.equals("ja")) {
                if (kana > 0) return "ja";
            }
            if (myLanguage.code.equals("zh") || otherLanguage.code.equals("zh")) {
                return "zh";
            }
        }

        return null;
    }

    private boolean containsHangul(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') return true;
        }
        return false;
    }

    private boolean isLatinDominant(String value) {
        int latin = 0;
        int letters = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;

            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.BASIC_LATIN
                    || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                    || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                    || block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
                latin++;
            }
        }

        return letters > 0 && latin * 100 / letters >= 70;
    }

    private boolean isLatinLanguage(String code) {
        return code.equals("en")
                || code.equals("es")
                || code.equals("fr")
                || code.equals("de")
                || code.equals("id")
                || code.equals("tl")
                || code.equals("vi")
                || code.equals("pt")
                || code.equals("it")
                || code.equals("tr");
    }

    private View buildMySpeechCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(PALE_BLUE, 16, Color.rgb(203, 229, 249), 1));

        mySourceLabel = text("", 12, BLUE, true);
        card.addView(mySourceLabel);
        card.addView(space(6));

        mySourceText = text("자동대화를 시작하고 말해보세요.", 14, TEXT, true);
        card.addView(mySourceText);
        card.addView(space(5));

        myTranslatedText = text("번역 결과가 여기에 표시됩니다.", 12,
                Color.rgb(52, 87, 119), false);
        card.addView(myTranslatedText);
        return card;
    }

    private View buildOtherSpeechCard() {
        LinearLayout card = vbox();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(PALE_GREEN, 16, Color.rgb(205, 236, 213), 1));

        otherSourceLabel = text("", 12, GREEN, true);
        card.addView(otherSourceLabel);
        card.addView(space(6));

        otherSourceText = text("상대방이 말하면 자동으로 인식합니다.", 14, TEXT, true);
        card.addView(otherSourceText);
        card.addView(space(5));

        otherTranslatedText = text("번역 결과가 여기에 표시됩니다.", 12,
                Color.rgb(52, 87, 119), false);
        card.addView(otherTranslatedText);
        return card;
    }

    private void showSettingsDialog() {
        boolean micGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        String[] items = {
                "내 언어 변경 · " + myLanguage.name,
                "상대 언어 변경 · " + otherLanguage.name,
                "마이크 권한 · " + (micGranted ? "허용됨" : "확인 필요"),
                "음성모델 다시 준비",
                "Android 앱 권한 설정 열기"
        };

        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showLanguagePicker(true);
                            break;
                        case 1:
                            showLanguagePicker(false);
                            break;
                        case 2:
                            checkOrRequestMicrophonePermission();
                            break;
                        case 3:
                            if (autoConversationEngine != null) {
                                autoConversationEngine.prepareSpeechModels();
                            }
                            Toast.makeText(
                                    this,
                                    "선택한 언어의 음성모델 준비를 요청했습니다. 자동대화는 모델 확인과 관계없이 계속 들을 수 있습니다.",
                                    Toast.LENGTH_LONG
                            ).show();
                            break;
                        case 4:
                            openAndroidAppSettings();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void checkOrRequestMicrophonePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "마이크 권한이 허용되어 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQ_MIC
        );
    }

    private void openAndroidAppSettings() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Android 설정에서 일상번역기의 마이크 권한을 확인해 주세요.",
                    Toast.LENGTH_LONG
            ).show();
        }
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
                item.setBackground(round(PALE_TEAL, 14, Color.TRANSPARENT, 0));
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

            if ("설정".equals(items[i][1])) {
                item.setClickable(true);
                item.setOnClickListener(v -> showSettingsDialog());
            }

            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(0, dp(52), 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            row.addView(item, p);
        }

        return row;
    }

    private String[] scenariosFor(String category) {
        if ("업무용".equals(category)) {
            return new String[]{"민원안내", "서류작성", "대기·호출", "연락·재방문"};
        }
        if ("일상용".equals(category)) {
            return new String[]{"인사", "소개", "약속·시간", "식사", "길찾기", "부탁·대화"};
        }
        return new String[]{"공항", "숙소", "식당", "교통", "쇼핑", "긴급상황"};
    }

    private String defaultScenario(String category) {
        if ("업무용".equals(category)) return "민원안내";
        if ("일상용".equals(category)) return "인사";
        return "식당";
    }

    private String scenarioIcon(String scenario) {
        switch (scenario) {
            case "민원안내": return "☑";
            case "서류작성": return "▤";
            case "대기·호출": return "◷";
            case "연락·재방문": return "☎";
            case "공항": return "✈";
            case "숙소": return "▰";
            case "식당": return "🍴";
            case "교통": return "▣";
            case "쇼핑": return "▢";
            case "긴급상황": return "⚠";
            case "인사": return "☺";
            case "소개": return "●";
            case "약속·시간": return "◷";
            case "식사": return "🍴";
            case "길찾기": return "⌖";
            case "부탁·대화": return "☏";
            default: return "•";
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_MIC) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if (granted && autoStartAfterPermission) {
                autoStartAfterPermission = false;
                startAutoConversation();
            } else {
                autoStartAfterPermission = false;
                Toast.makeText(
                        this,
                        "자동대화를 사용하려면 마이크 권한이 필요합니다.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (autoConversationEngine != null) {
            autoConversationEngine.destroy();
        }
        if (translationEngine != null) {
            translationEngine.close();
        }
        super.onDestroy();
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

    private static class RecognitionDecision {
        final String text;
        final boolean fromMyLanguage;

        RecognitionDecision(String text, boolean fromMyLanguage) {
            this.text = text;
            this.fromMyLanguage = fromMyLanguage;
        }
    }
}
