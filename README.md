# 아산남성초 통역기 Android 통합판

기존 웹 통역기 `https://translator-942.pages.dev/`를 Android 앱 안에서 사용하면서 출력 장치를 분리하는 시험판입니다.

- 직원 한국어 → 외국어: 휴대폰 스피커
- 민원인 외국어 → 한국어: 이어폰
- 기존 웹 통역기 UI/민원문장/즐겨찾기/내 문구/수동·자동대화 기능 사용

## APK 빌드
이 저장소에 파일을 올리면 GitHub Actions의 `Build Android APK`가 자동 실행됩니다.
성공 후 Actions > 실행 항목 > Artifacts > `NamseongTranslator-debug`를 내려받아 압축을 풀고 `app-debug.apk`를 스마트폰에 설치합니다.
