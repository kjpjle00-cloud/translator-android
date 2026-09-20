package com.kjpjle00.dailytranslator;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AppLanguage {

    public final String name;
    public final String code;
    public final String speechTag;
    public final Locale ttsLocale;

    public AppLanguage(String name, String code, String speechTag) {
        this.name = name;
        this.code = code;
        this.speechTag = speechTag;
        this.ttsLocale = Locale.forLanguageTag(speechTag);
    }

    public static final AppLanguage KOREAN =
            new AppLanguage("한국어", "ko", "ko-KR");
    public static final AppLanguage ENGLISH =
            new AppLanguage("영어", "en", "en-US");
    public static final AppLanguage JAPANESE =
            new AppLanguage("일본어", "ja", "ja-JP");
    public static final AppLanguage CHINESE =
            new AppLanguage("중국어(간체)", "zh", "zh-CN");
    public static final AppLanguage SPANISH =
            new AppLanguage("스페인어", "es", "es-ES");
    public static final AppLanguage THAI =
            new AppLanguage("태국어", "th", "th-TH");
    public static final AppLanguage TAGALOG =
            new AppLanguage("타갈로그어", "tl", "fil-PH");
    public static final AppLanguage VIETNAMESE =
            new AppLanguage("베트남어", "vi", "vi-VN");
    public static final AppLanguage FRENCH =
            new AppLanguage("프랑스어", "fr", "fr-FR");
    public static final AppLanguage GERMAN =
            new AppLanguage("독일어", "de", "de-DE");
    public static final AppLanguage RUSSIAN =
            new AppLanguage("러시아어", "ru", "ru-RU");
    public static final AppLanguage INDONESIAN =
            new AppLanguage("인도네시아어", "id", "id-ID");
    public static final AppLanguage ARABIC =
            new AppLanguage("아랍어", "ar", "ar-SA");
    public static final AppLanguage HINDI =
            new AppLanguage("힌디어", "hi", "hi-IN");
    public static final AppLanguage PORTUGUESE =
            new AppLanguage("포르투갈어", "pt", "pt-BR");
    public static final AppLanguage ITALIAN =
            new AppLanguage("이탈리아어", "it", "it-IT");
    public static final AppLanguage TURKISH =
            new AppLanguage("터키어", "tr", "tr-TR");

    public static final List<AppLanguage> ALL = Arrays.asList(
            KOREAN,
            ENGLISH,
            JAPANESE,
            CHINESE,
            SPANISH,
            THAI,
            TAGALOG,
            VIETNAMESE,
            FRENCH,
            GERMAN,
            RUSSIAN,
            INDONESIAN,
            ARABIC,
            HINDI,
            PORTUGUESE,
            ITALIAN,
            TURKISH
    );

    public static AppLanguage byCode(String code) {
        if (code != null) {
            for (AppLanguage language : ALL) {
                if (language.code.equalsIgnoreCase(code)) {
                    return language;
                }
            }
        }
        return ENGLISH;
    }

    public static int indexOf(AppLanguage target) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).code.equals(target.code)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
