package kr.go.cne.asannamseong.translator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://translator-942.pages.dev/";
    private static final int REQ_PERMS = 42;

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private MediaPlayer player;
    private SpeechRecognizer speechRecognizer;
    private String activeRecognitionId;
    private boolean recognitionEnded = true;
    private boolean earphoneMode = true;
    private AudioDeviceCallback audioDeviceCallback;

    private final Map<String, PendingTts> pendingTts = new HashMap<>();

    static class PendingTts {
        final String jsId;
        final File file;
        final String target;
        PendingTts(String jsId, File file, String target) {
            this.jsId = jsId;
            this.file = file;
            this.target = target;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        requestNeededPermissions();
        initTts();
        registerAudioDeviceWatcher();
        initWebView();
    }

    private void registerAudioDeviceWatcher() {
        if (Build.VERSION.SDK_INT >= 23) {
            audioDeviceCallback = new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) { updateNativeStatus(); }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) { updateNativeStatus(); }
            };
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, main);
        }
    }

    private void initWebView() {
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new NativeBridge(), "AndroidAudio");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!request.getOrigin().toString().startsWith(APP_URL)) {
                        request.deny();
                        return;
                    }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestNeededPermissions();
                        request.deny();
                        return;
                    }
                    ArrayList<String> allowed = new ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) allowed.add(r);
                    }
                    if (allowed.isEmpty()) request.deny();
                    else request.grant(allowed.toArray(new String[0]));
                });
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(APP_URL) || url.startsWith("https://script.google.com/") || url.startsWith("https://script.googleusercontent.com/")) {
                    return false;
                }
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.startsWith(APP_URL)) injectNativeBridgeJs();
            }
        });
        webView.loadUrl(APP_URL + "?native=1.0-test2b1-auto28");
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setSpeechRate(0.94f);
               tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
    @Override
    public void onStart(String utteranceId) {
        if (utteranceId != null && utteranceId.startsWith("direct_")) {
            String jsId = utteranceId.substring(7);
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(jsId) + ",'start','')");
        }
    }

    @Override
    public void onError(String utteranceId) {
        if (utteranceId != null && utteranceId.startsWith("direct_")) {
            String jsId = utteranceId.substring(7);
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(jsId) + ",'error','TTS_ERROR')");
        } else {
            handleTtsSynthesisError(utteranceId, "TTS_ERROR");
        }
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        if (utteranceId != null && utteranceId.startsWith("direct_")) {
            String jsId = utteranceId.substring(7);
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(jsId) + ",'error','TTS_ERROR_" + errorCode + "')");
        } else {
            handleTtsSynthesisError(utteranceId, "TTS_ERROR_" + errorCode);
        }
    }

    @Override
    public void onDone(String utteranceId) {
        if (utteranceId != null && utteranceId.startsWith("direct_")) {
            String jsId = utteranceId.substring(7);
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(jsId) + ",'end','')");
        } else {
            handleTtsFileReady(utteranceId);
        }
    }
});
            }
        });
    }

    private void requestNeededPermissions() {
        ArrayList<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ_PERMS);
    }

    private void injectNativeBridgeJs() {
        webView.evaluateJavascript(NATIVE_PATCH_JS, null);
    }

    private void js(String code) {
        main.post(() -> {
            if (webView != null) webView.evaluateJavascript(code, null);
        });
    }

    private String q(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    private void handleTtsSynthesisError(String nativeId, String error) {
        PendingTts p;
        synchronized (pendingTts) { p = pendingTts.remove(nativeId); }
        if (p != null) {
            p.file.delete();
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'error'," + q(error) + ")");
        }
    }

    private void handleTtsFileReady(String nativeId) {
        PendingTts p;
        synchronized (pendingTts) { p = pendingTts.remove(nativeId); }
        if (p == null) return;
        main.post(() -> playTtsFile(p));
    }

    private void playTtsFile(PendingTts p) {
        stopPlayer();
        AudioDeviceInfo device = "earphone".equals(p.target) ? findPrivateOutput() : findSpeakerOutput();
        if (device == null) {
            String msg = "earphone".equals(p.target)
                    ? "이어폰이 연결되어 있지 않아 한국어를 스피커로 재생하지 않았습니다."
                    : "휴대폰 스피커를 찾지 못했습니다.";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            p.file.delete();
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'error'," + q("earphone".equals(p.target) ? "NO_EARPHONE" : "NO_SPEAKER") + ")");
            updateNativeStatus();
            return;
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(p.file.getAbsolutePath());
            player.prepare();
            boolean routed = player.setPreferredDevice(device);
            if (!routed) {
                if ("earphone".equals(p.target)) {
                    Toast.makeText(this, "이어폰 출력 지정에 실패해 한국어를 재생하지 않았습니다.", Toast.LENGTH_LONG).show();
                    player.release(); player = null; p.file.delete();
                    js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'error','ROUTE_FAILED')");
                    return;
                }
            }
            player.setOnCompletionListener(mp -> {
                try { mp.release(); } catch (Exception ignored) {}
                if (player == mp) player = null;
                p.file.delete();
                js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'end','')");
            });
            player.setOnErrorListener((mp, what, extra) -> {
                try { mp.release(); } catch (Exception ignored) {}
                if (player == mp) player = null;
                p.file.delete();
                js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'error','PLAYBACK_ERROR')");
                return true;
            });
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'start','')");
            player.start();
            updateNativeStatus();
        } catch (Exception e) {
            p.file.delete();
            js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(p.jsId) + ",'error'," + q(e.getClass().getSimpleName()) + ")");
        }
    }

    private AudioDeviceInfo findSpeakerOutput() {
        for (AudioDeviceInfo d : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return d;
        }
        return null;
    }

    private AudioDeviceInfo findPrivateOutput() {
        AudioDeviceInfo fallback = null;
        for (AudioDeviceInfo d : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                t == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                t == AudioDeviceInfo.TYPE_USB_HEADSET ||
                t == AudioDeviceInfo.TYPE_HEARING_AID ||
                (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET)) return d;
                fallback = d;
            }
        }
        return fallback;
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void startNativeRecognition(String id, String lang) {
        startNativeRecognition(id, lang, "auto");
    }

    private void startNativeRecognition(String id, String lang, String context) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions();
            jsRecognition(id, "error", "not-allowed");
            jsRecognition(id, "end", "");
            return;
        }

        final String recognitionContext = normalizeRecognitionContext(context);
        stopNativeRecognition(true);
        activeRecognitionId = id;
        recognitionEnded = false;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            private boolean begun = false;

            @Override public void onReadyForSpeech(Bundle params) {
                jsRecognition(id, "start", "");
            }

            @Override public void onBeginningOfSpeech() {
                begun = true;
                jsRecognition(id, "speechstart", "");
            }

            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }

            @Override public void onEndOfSpeech() {
                if (begun) jsRecognition(id, "speechend", "");
            }

            @Override public void onError(int error) {
                if (!isCurrentRecognition(id)) return;
                jsRecognition(id, "error", mapRecognitionError(error));
                finishRecognition(id);
            }

            @Override public void onResults(Bundle results) {
                if (!isCurrentRecognition(id)) return;
                String text = bestRecognitionResult(results, lang, recognitionContext);
                if (!text.isEmpty()) jsRecognition(id, "final", text);
                finishRecognition(id);
            }

            @Override public void onPartialResults(Bundle partialResults) {
                if (!isCurrentRecognition(id)) return;
                String text = bestRecognitionResult(partialResults, lang, recognitionContext);
                if (!text.isEmpty()) jsRecognition(id, "partial", text);
            }

            @Override public void onEvent(int eventType, Bundle params) { }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        // TEST2A: ask Android for multiple hypotheses instead of accepting only one.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        // Keep the proven TEST1C turn timing.
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);

        // Android 13+ can bias recognition toward words that fit the current situation.
        // Unsupported recognizers are allowed to ignore these hints.
        if (Build.VERSION.SDK_INT >= 33) {
            ArrayList<String> hints = recognitionHints(lang, recognitionContext);
            if (!hints.isEmpty()) {
                intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, hints);
            }
        }

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            jsRecognition(id, "error", "audio-capture");
            finishRecognition(id);
        }
    }

    private String normalizeRecognitionContext(String context) {
        String c = context == null ? "auto" : context.trim().toLowerCase(Locale.ROOT);
        if ("work".equals(c) || "travel".equals(c) || "daily".equals(c) || "auto".equals(c)) return c;
        return "auto";
    }

    private String bestRecognitionResult(Bundle b, String lang, String context) {
        if (b == null) return "";
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return "";

        float[] confidence = b.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        String best = "";
        double bestScore = -999.0;

        for (int i = 0; i < list.size() && i < 5; i++) {
            String candidate = list.get(i) == null ? "" : list.get(i).trim();
            if (candidate.isEmpty()) continue;

            double score;
            if (confidence != null && i < confidence.length && confidence[i] >= 0f) {
                score = confidence[i];
            } else {
                // Android already orders hypotheses by likelihood.
                // Preserve that ordering when confidence is unavailable.
                score = 1.0 - (i * 0.08);
            }

            score += contextMatchBoost(candidate, lang, context);

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private double contextMatchBoost(String text, String lang, String context) {
        if (text == null || text.isEmpty()) return 0.0;
        String haystack = text.toLowerCase(Locale.ROOT);
        ArrayList<String> hints = recognitionHints(lang, context);
        double boost = 0.0;

        for (String hint : hints) {
            if (hint == null || hint.length() < 2) continue;
            if (haystack.contains(hint.toLowerCase(Locale.ROOT))) {
                boost += 0.025;
                if (boost >= 0.10) return 0.10;
            }
        }
        return boost;
    }

    private ArrayList<String> recognitionHints(String lang, String context) {
        ArrayList<String> out = new ArrayList<>();
        String l = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        String c = normalizeRecognitionContext(context);

        if (l.startsWith("ko")) {
            addHints(out,
                "안녕하세요", "감사합니다", "괜찮아요", "잠깐만요", "다시 말해 주세요",
                "어디예요", "얼마예요", "카드 돼요", "이거", "그거", "주세요",
                "맞아요", "아니요", "몰라요", "어떻게", "언제", "어디");

            if ("auto".equals(c) || "work".equals(c)) {
                addHints(out,
                    "학생", "학년", "반", "담임", "보호자", "재학증명서", "전학",
                    "방과후", "급식", "결석", "서류", "신청", "발급", "제출", "서명");
            }

            if ("auto".equals(c) || "travel".equals(c)) {
                addHints(out,
                    "공항", "여권", "탑승구", "수하물", "체크인", "체크아웃",
                    "호텔", "예약", "식당", "메뉴", "맵지 않게", "알레르기",
                    "계산", "택시", "기차", "버스", "화장실");
            }

            if ("auto".equals(c) || "daily".equals(c)) {
                addHints(out,
                    "뭐 해요", "어디 가요", "밥 먹었어요", "좋아요", "싫어요",
                    "잠깐만", "다시 한번", "괜찮습니다", "필요해요", "필요 없어요");
            }
        } else if (l.startsWith("en")) {
            addHints(out,
                "hello", "thank you", "please", "excuse me", "sorry",
                "where is", "how much", "card", "cash", "bathroom",
                "airport", "hotel", "reservation", "check in", "check out",
                "restaurant", "menu", "not spicy", "allergy", "taxi",
                "train", "bus", "school", "student", "document", "signature");
        }

        return out;
    }

    private void addHints(ArrayList<String> target, String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty() && !target.contains(value)) target.add(value);
        }
    }

    private boolean isCurrentRecognition(String id) {
        return !recognitionEnded && id != null && id.equals(activeRecognitionId);
    }

    private void finishRecognition(String id) {
        if (!isCurrentRecognition(id)) return;
        recognitionEnded = true;
        jsRecognition(id, "end", "");
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        activeRecognitionId = null;
    }

    private void stopNativeRecognition(boolean abort) {
        if (speechRecognizer != null) {
            try { if (abort) speechRecognizer.cancel(); else speechRecognizer.stopListening(); } catch (Exception ignored) {}
            if (abort && activeRecognitionId != null) finishRecognition(activeRecognitionId);
        }
    }

    private String mapRecognitionError(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_AUDIO: return "audio-capture";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "not-allowed";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "network";
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "no-speech";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "aborted";
            default: return "unknown";
        }
    }

    private void jsRecognition(String id, String type, String payload) {
        js("window.__nativeRecognitionEvent && window.__nativeRecognitionEvent(" + q(id) + "," + q(type) + "," + q(payload) + ")");
    }

    private void updateNativeStatus() {
        AudioDeviceInfo privateDevice = findPrivateOutput();
        String label = privateDevice == null ? "이어폰 미연결" : "이어폰 연결됨";
        js("window.__nativeAudioStatus && window.__nativeAudioStatus(" + q(label) + "," + (earphoneMode ? "true" : "false") + ")");
    }

    public class NativeBridge {
        @JavascriptInterface
        public void speak(String jsId, String text, String lang) {
            main.post(() -> {
                if (!ttsReady || text == null || text.trim().isEmpty()) {
                    js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(jsId) + ",'error','TTS_NOT_READY')");
                    return;
                }
                boolean korean = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("ko");
                String target = (earphoneMode && korean) ? "earphone" : "speaker";
                Locale locale = Locale.forLanguageTag((lang == null || lang.isEmpty()) ? "ko-KR" : lang);
                int set = tts.setLanguage(locale);
                if (set == TextToSpeech.LANG_MISSING_DATA || set == TextToSpeech.LANG_NOT_SUPPORTED) {
                    js("window.__nativeTtsEvent && window.__nativeTtsEvent(" + q(jsId) + ",'error','LANG_NOT_SUPPORTED')");
                    return;
                }
                stopPlayer();
                tts.stop();

                // Both routes use the proven synthesize-to-file pipeline.
                // MediaPlayer then forces foreign-language audio to the phone speaker
                // and Korean audio to the private earphone route when split mode is on.
                String nativeId = "tts_" + UUID.randomUUID();
                File f = new File(getCacheDir(), nativeId + ".wav");
                PendingTts p = new PendingTts(jsId, f, target);
                synchronized (pendingTts) { pendingTts.put(nativeId, p); }

                Bundle params = new Bundle();
                int result = tts.synthesizeToFile(text, params, f, nativeId);

                if (result != TextToSpeech.SUCCESS) {
                    handleTtsSynthesisError(nativeId, "SYNTH_QUEUE_FAILED");
                }
            });
        }

        @JavascriptInterface
        public void stopTts() {
            main.post(() -> {
                if (tts != null) tts.stop();
                stopPlayer();
            });
        }

        @JavascriptInterface
        public void startRecognition(String id, String lang) {
            main.post(() -> startNativeRecognition(id, lang, "auto"));
        }

        @JavascriptInterface
        public void startRecognitionWithContext(String id, String lang, String context) {
            main.post(() -> startNativeRecognition(id, lang, context));
        }

        @JavascriptInterface
        public void stopRecognition(String id, boolean abort) {
            main.post(() -> {
                if (id != null && id.equals(activeRecognitionId)) stopNativeRecognition(abort);
            });
        }

        @JavascriptInterface
        public String audioStatus() {
            AudioDeviceInfo d = findPrivateOutput();
            return d == null ? "이어폰 미연결" : "이어폰 연결됨";
        }

        @JavascriptInterface
        public boolean earphoneMode() { return earphoneMode; }

        @JavascriptInterface
        public void setEarphoneMode(boolean enabled) {
            earphoneMode = enabled;
            updateNativeStatus();
        }

        @JavascriptInterface
        public String appVersion() { return "1.0-test2b1-auto28"; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNativeStatus();
    }

    @Override
    protected void onDestroy() {
        stopNativeRecognition(true);
        stopPlayer();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (audioDeviceCallback != null && Build.VERSION.SDK_INT >= 23) {
            try { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback); } catch (Exception ignored) {}
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private static final String NATIVE_PATCH_JS = """
(function(){
  if(window.__namseongNativePatched) return;
  window.__namseongNativePatched=true;

  // TEST2B1 connection recovery.
  // The translation server itself is confirmed healthy. A stale but valid-looking
  // Apps Script URL saved in localStorage can override the built-in working URL.
  // Pin this test build to the confirmed v6.0 server, then refresh the page badge.
  try{
    const nativeServerUrl='https://script.google.com/macros/s/AKfycbzXS4sGTdMz0ZXCJx0fEYAs7xLcZi-iT-dYEERbi4it2N5j4ZLR6JAIU0_dwdM9ZeKT/exec';
    const savedServer=(localStorage.getItem('ans_gas_url')||'').trim();
    if(savedServer!==nativeServerUrl){
      localStorage.setItem('ans_gas_url',nativeServerUrl);
    }
    setTimeout(()=>{
      try{
        if(typeof syncServerInputs==='function')syncServerInputs();
        if(typeof testServerEverywhere==='function')testServerEverywhere();
        if(typeof checkTranslatorStatus==='function')checkTranslatorStatus();
      }catch(e){console.warn('native server recovery retry',e);}
    },300);
  }catch(e){
    console.warn('native server recovery',e);
  }

  // Native app update refresh:
  // clear only web resource/service-worker caches once per native build.
  // localStorage (school name, favorites, custom phrases, settings) is preserved.
  try{
    const nativeVersion=(window.AndroidAudio && typeof AndroidAudio.appVersion==='function')
      ? String(AndroidAudio.appVersion()||'') : '';
    const markerKey='ans_native_cache_build';
    const already=localStorage.getItem(markerKey)||'';
    const urlNow=new URL(location.href);
    if(nativeVersion && already!==nativeVersion && !urlNow.searchParams.has('native_refresh')){
      const jobs=[];
      if(navigator.serviceWorker && navigator.serviceWorker.getRegistrations){
        jobs.push(
          navigator.serviceWorker.getRegistrations()
            .then(list=>Promise.all(list.map(reg=>reg.unregister())))
        );
      }
      if(window.caches && caches.keys){
        jobs.push(
          caches.keys().then(keys=>Promise.all(
            keys.filter(k=>k.startsWith('ans-translator-')).map(k=>caches.delete(k))
          ))
        );
      }
      Promise.allSettled(jobs).finally(()=>{
        try{ localStorage.setItem(markerKey,nativeVersion); }catch(e){}
        const u=new URL(location.href);
        u.searchParams.set('native',nativeVersion);
        u.searchParams.set('native_refresh','1');
        location.replace(u.toString());
      });
      return;
    }
  }catch(e){
    console.warn('native cache refresh',e);
  }

  // v1.0 realtime conversation engine.
  // Keeps listening across Android recognition segment boundaries and only
  // finalizes after real silence. The proven v0.9 TTS/output routing is untouched.
  if(window.SpeechSession && window.SpeechSession.VERSION==='6.7'){
    const SS=window.SpeechSession;
    const nativeAutoIsActive=()=>{
      try{
        return typeof conversationMode!=='undefined' && conversationMode==='auto'
          && typeof autoConversationActive!=='undefined' && autoConversationActive;
      }catch(e){ return false; }
    };
    if(typeof SS.needsReview==='function' && !SS.__nativeAuto23NeedsReview){
      SS.__nativeAuto23NeedsReview=SS.needsReview;
      SS.needsReview=function(results){
        return nativeAutoIsActive()?false:SS.__nativeAuto23NeedsReview.call(this,results);
      };
    }

    const cleanText=value=>String(value||'').replace(/\\s+/g,' ').trim();
    const comparableToken=value=>cleanText(value).replace(/[.,!?…~"'“”‘’(){}:;]+$/g,'').toLowerCase();

    const joinSegments=(base,piece)=>{
      base=cleanText(base); piece=cleanText(piece);
      if(!base)return piece;
      if(!piece)return base;
      if(base===piece)return base;
      if(piece.startsWith(base))return piece;

      const a=base.split(/\\s+/), b=piece.split(/\\s+/);
      const limit=Math.min(a.length,b.length,8);
      for(let size=limit;size>=1;size--){
        const left=a.slice(a.length-size).map(comparableToken);
        const right=b.slice(0,size).map(comparableToken);
        if(left.join('¦')!==right.join('¦'))continue;
        const chars=left.join('').length;
        const safeSingle=size===1 && a.length>1 && b.length>1 && chars>=2;
        if(size>=2 || chars>=4 || safeSingle){
          return cleanText(a.concat(b.slice(size)).join(' '));
        }
      }

      // Handle a cumulative recognizer result without blindly deleting intentional repeats.
      const compactBase=base.replace(/\\s+/g,'');
      const compactPiece=piece.replace(/\\s+/g,'');
      if(compactPiece.startsWith(compactBase) && compactBase.length>=4)return piece;
      if(compactBase.endsWith(compactPiece) && compactPiece.length>=4)return base;

      return cleanText(base+' '+piece);
    };

    SS.prototype.clearSilence=function(){
      clearTimeout(this.silenceTimer);
      this.silenceTimer=null;
      this.deadline=0;
    };

    SS.prototype.__rtFullText=function(){
      return joinSegments(this.__rtCommitted||'',this.__rtCurrent||'');
    };

    SS.prototype.__rtShow=function(){
      const full=this.__rtFullText();
      this.results=full?[{text:full,final:!this.__rtCurrent}]:[];
      this.options.text(full);
      if(full)this.__rtSchedulePrime(full);
      return full;
    };

    SS.prototype.__rtSchedulePrime=function(full){
      clearTimeout(this.__rtPrimeTimer);
      if(this.closed || this.stopping || !full || full.length<4)return;
      if((this.__rtPrimeCount||0)>=2)return;
      const snapshot=cleanText(full);
      this.__rtPrimeTimer=setTimeout(()=>{
        if(this.closed || this.stopping)return;
        const latest=cleanText(this.__rtFullText());
        if(latest!==snapshot || this.__rtLastPrimed===snapshot)return;
        this.__rtLastPrimed=snapshot;
        this.__rtPrimeCount=(this.__rtPrimeCount||0)+1;
        try{
          if(typeof window.__primeRealtimeTranslation==='function'){
            window.__primeRealtimeTranslation(snapshot,this.side);
          }
        }catch(e){ console.warn('realtime prime',e); }
      },220);
    };

    SS.prototype.arm=function(){
      if(this.closed || this.stopping)return;
      this.clearSilence();
      this.deadline=Date.now()+1800;
      const tick=()=>{
        if(this.closed || this.stopping)return;
        const remaining=this.deadline-Date.now();
        if(remaining<=0){ this.stopForFinal(); return; }
        this.options.state('waiting',Math.max(1,Math.ceil(remaining/1000)));
        this.silenceTimer=setTimeout(tick,Math.min(150,remaining));
      };
      tick();
    };

    SS.prototype.start=function(Ctor,lang){
      this.__rtCtor=Ctor;
      this.__rtLang=lang;
      this.__rtCommitted='';
      this.__rtCurrent='';
      this.__rtPrimeCount=0;
      this.__rtLastPrimed='';
      this.__rtGeneration=0;
      this.__rtStartedAt=Date.now();
      this.__rtNoSpeechTimer=null;
      this.closed=false;
      this.ended=false;
      this.stopping=false;
      this.results=[];

      const session=this;

      this.__rtMaxTimer=null;
      if(!nativeAutoIsActive()){
        this.__rtMaxTimer=setTimeout(()=>{
          if(session.closed)return;
          if(session.__rtFullText())session.stopForFinal();
          else{
            session.cancel();
            session.options.error('no-speech');
          }
        },30000);
      }

      this.__rtQueueSameTurn=function(delay=90,abortCurrent=false){
        if(session.closed || session.stopping)return;
        clearTimeout(session.__rtRestartTimer);
        clearTimeout(session.__rtNoSpeechTimer);
        session.__rtNoSpeechTimer=null;
        if(abortCurrent && session.recognition){
          const old=session.recognition;
          session.__rtGeneration=(session.__rtGeneration||0)+1;
          session.recognition=null;
          try{
            old.onresult=null;old.onend=null;old.onerror=null;old.onstart=null;
            old.onspeechstart=null;old.onspeechend=null;
            old.abort();
          }catch(e){}
        }
        session.ended=false;
        session.__rtRestartTimer=setTimeout(()=>{
          if(!session.closed && !session.stopping)session.__rtLaunch();
        },delay);
      };

      this.__rtLaunch=function(){
        if(session.closed || session.stopping)return;
        if(!nativeAutoIsActive() && Date.now()-session.__rtStartedAt>=30000){
          session.stopForFinal();
          return;
        }

        const generation=++session.__rtGeneration;
        const r=session.recognition=new Ctor();
        r.lang=lang;
        r.continuous=false;
        r.interimResults=true;
        r.maxAlternatives=5;

        const current=()=>generation===session.__rtGeneration && !session.closed;

        r.onstart=()=>{
          if(!current())return;
          session.options.state('listening');
          clearTimeout(session.__rtNoSpeechTimer);
          if(nativeAutoIsActive()){
            session.__rtNoSpeechTimer=setTimeout(()=>{
              if(!current() || session.stopping || session.__rtFullText())return;
              session.__rtQueueSameTurn(90,true);
            },1800);
          }
        };

        r.onspeechstart=()=>{
          if(!current() || session.stopping)return;
          clearTimeout(session.__rtNoSpeechTimer);
          session.__rtNoSpeechTimer=null;
          session.options.state('listening');
        };

        r.onspeechend=()=>{
          if(!current() || session.stopping)return;
          if(session.__rtFullText())session.arm();
        };

        r.onresult=event=>{
          if(!current())return;
          clearTimeout(session.__rtNoSpeechTimer);
          session.__rtNoSpeechTimer=null;
          const rows=event.results||[];
          let latest='';
          let isFinal=false;
          for(let i=0;i<rows.length;i++){
            const row=rows[i];
            const text=cleanText(row?.[0]?.transcript||'');
            if(!text)continue;
            latest=text;
            isFinal=!!row.isFinal;
          }
          if(!latest)return;

          session.__rtCurrent=latest;
          session.__rtShow();
          session.arm();

          if(isFinal){
            session.__rtCommitted=joinSegments(session.__rtCommitted,latest);
            session.__rtCurrent='';
            session.__rtShow();
            session.arm();
          }
        };

        r.onerror=event=>{
          if(!current())return;
          const error=String(event?.error||'unknown');
          if(session.stopping){
            session.finish();
            return;
          }

          clearTimeout(session.__rtNoSpeechTimer);
          session.__rtNoSpeechTimer=null;

          if((error==='no-speech' || error==='aborted') && session.__rtFullText()){
            session.stopForFinal();
            return;
          }
          if((error==='no-speech' || error==='aborted') && nativeAutoIsActive()){
            session.__rtQueueSameTurn(90,false);
            return;
          }

          session.cancel();
          session.options.error(error);
        };

        r.onend=()=>{
          if(!current())return;
          clearTimeout(session.__rtNoSpeechTimer);
          session.__rtNoSpeechTimer=null;
          session.ended=true;

          // Preserve the best partial result if Android ended the segment before
          // delivering an explicit final result.
          if(session.__rtCurrent){
            session.__rtCommitted=joinSegments(session.__rtCommitted,session.__rtCurrent);
            session.__rtCurrent='';
            session.__rtShow();
          }

          if(session.stopping){
            session.finish();
            return;
          }

          if(!session.__rtFullText()){
            if(nativeAutoIsActive()){
              session.__rtQueueSameTurn(90,false);
              return;
            }
            session.cancel();
            session.options.error('no-speech');
            return;
          }

          session.ended=false;
          clearTimeout(session.__rtRestartTimer);
          session.__rtRestartTimer=setTimeout(()=>{
            if(!session.closed && !session.stopping)session.__rtLaunch();
          },70);
        };

        try{
          r.start();
        }catch(error){
          session.cancel();
          session.options.error(error?.name||'audio-capture');
        }
      };

      this.__rtLaunch();
    };

    SS.prototype.stopForFinal=function(){
      if(this.closed || this.stopping)return;
      this.stopping=true;
      this.clearSilence();
      clearTimeout(this.__rtRestartTimer);
      clearTimeout(this.__rtPrimeTimer);
      clearTimeout(this.__rtNoSpeechTimer);
      this.__rtNoSpeechTimer=null;
      this.options.state('finalizing');

      if(this.__rtCurrent){
        this.__rtCommitted=joinSegments(this.__rtCommitted,this.__rtCurrent);
        this.__rtCurrent='';
        this.__rtShow();
      }

      this.finalTimer=setTimeout(()=>this.finish(),350);
      try{
        if(this.recognition)this.recognition.stop();
        else this.finish();
      }catch(e){
        this.finish();
      }
    };

    SS.prototype.finish=function(){
      if(this.closed)return;
      const text=cleanText(this.__rtFullText());
      const result=text?[{text,final:true}]:[];
      this.cancel();

      if(!text){
        this.options.error('no-speech');
        return;
      }
      if(SS.needsReview(result))this.options.review(text,'repetition');
      else this.options.done(text);
    };

    SS.prototype.cancel=function(){
      if(this.closed)return;
      this.closed=true;
      this.clearSilence();
      clearTimeout(this.finalTimer);
      clearTimeout(this.__rtRestartTimer);
      clearTimeout(this.__rtPrimeTimer);
      clearTimeout(this.__rtNoSpeechTimer);
      clearTimeout(this.__rtMaxTimer);
      this.__rtGeneration=(this.__rtGeneration||0)+1;
      if(this.recognition){
        this.recognition.onresult=null;
        this.recognition.onend=null;
        this.recognition.onerror=null;
        this.recognition.onstart=null;
        this.recognition.onspeechstart=null;
        this.recognition.onspeechend=null;
        try{ this.recognition.abort(); }catch(e){}
      }
      this.recognition=null;
    };

    // Update the existing helper text without touching the page design.
    const hint=document.getElementById('workspaceHint');
    if(hint){
      const updateHint=()=>{
        const text=hint.textContent||'';
        const updated=text
          .replace('3초 후 자동 번역','실시간 연결 · 약 1.8초 후 번역')
          .replace('1.2초 후 자동 번역','실시간 연결 · 약 1.8초 후 번역');
        if(text!==updated)hint.textContent=updated;
      };
      new MutationObserver(updateHint).observe(hint,{childList:true,subtree:true,characterData:true});
      updateHint();
    }
  }

  // Speculative translation cache:
  // after a transcript stays unchanged for 350 ms, prepare at most two
  // translations during the turn. Final translation reuses an exact cache hit.
  try{
    if(!window.__namseongRealtimeTranslateWrapped && typeof translateText==='function'){
      window.__namseongRealtimeTranslateWrapped=true;
      const originalTranslate=translateText;
      const rtCache=new Map();

      const cacheKey=(text,sourceKey,targetKey)=>
        [String(sourceKey||''),String(targetKey||''),String(text||'').trim()].join('§');

      const cachedTranslate=async(text,sourceKey,targetKey)=>{
        const key=cacheKey(text,sourceKey,targetKey);
        const now=Date.now();
        const found=rtCache.get(key);
        if(found && now-found.time<8000)return found.promise;

        const promise=Promise.resolve(originalTranslate(text,sourceKey,targetKey))
          .catch(error=>{ rtCache.delete(key); throw error; });

        rtCache.set(key,{time:now,promise});
        if(rtCache.size>12){
          const oldest=[...rtCache.entries()].sort((a,b)=>a[1].time-b[1].time)[0];
          if(oldest)rtCache.delete(oldest[0]);
        }
        return promise;
      };

      try{ translateText=cachedTranslate; }catch(e){}
      try{ window.translateText=cachedTranslate; }catch(e){}

      window.__primeRealtimeTranslation=(text,side)=>{
        try{
          if(!text || typeof currentLang==='undefined')return;
          const staff=side==='staff';
          const sourceKey=staff?'ko':currentLang;
          const targetKey=staff?currentLang:'ko';
          cachedTranslate(text,sourceKey,targetKey).catch(()=>{});
        }catch(e){}
      };
    }
  }catch(e){
    console.warn('realtime translation cache',e);
  }

  const utterances={}; let utterSeq=0;
  function safe(fn,arg){ try{ if(typeof fn==='function') fn(arg); }catch(e){ console.warn(e); } }

  function NativeUtterance(text){
    this.text=String(text||'');
    this.lang='';
    this.rate=1;
    this.pitch=1;
    this.volume=1;
    this.onstart=null;
    this.onend=null;
    this.onerror=null;
  }

  if(typeof window.SpeechSynthesisUtterance!=='function'){
    try{
      Object.defineProperty(window,'SpeechSynthesisUtterance',{
        value:NativeUtterance,writable:true,configurable:true
      });
    }catch(e){
      try{ window.SpeechSynthesisUtterance=NativeUtterance; }catch(ignore){}
    }
  }

  let synth=null;
  try{ synth=window.speechSynthesis; }catch(e){}
  if(!synth){
    synth={};
    try{
      Object.defineProperty(window,'speechSynthesis',{
        value:synth,writable:true,configurable:true
      });
    }catch(e){
      try{ window.speechSynthesis=synth; }catch(ignore){}
    }
  }
  try{ synth=window.speechSynthesis||synth; }catch(e){}

  if(synth){
    synth.speak=function(u){
      const id='u'+Date.now()+'_'+(++utterSeq);
      utterances[id]=u;
      if(!window.AndroidAudio || typeof AndroidAudio.speak!=='function'){
        safe(u&&u.onerror,{type:'error',error:'ANDROID_AUDIO_NOT_READY'});
        delete utterances[id];
        return;
      }
      AndroidAudio.speak(
        id,
        String((u&&u.text)||''),
        String((u&&u.lang)||'')
      );
    };

    synth.cancel=function(){
      try{
        if(window.AndroidAudio && typeof AndroidAudio.stopTts==='function'){
          AndroidAudio.stopTts();
        }
      }catch(e){}
      Object.keys(utterances).forEach(id=>{
        const u=utterances[id];
        safe(u&&u.onerror,{error:'canceled'});
        delete utterances[id];
      });
    };
    synth.pause=function(){};
    synth.resume=function(){};
    synth.getVoices=function(){return [];};
  }

  window.__nativeTtsEvent=function(id,type,detail){
    const u=utterances[id]; if(!u) return;
    if(type==='start') safe(u.onstart,{type:'start'});
    else if(type==='end'){ safe(u.onend,{type:'end'}); delete utterances[id]; }
    else if(type==='error'){ safe(u.onerror,{type:'error',error:detail||'native'}); delete utterances[id]; }
  };

  const recognizers={}; let recSeq=0;

  const validRecognitionContexts=new Set(['auto','work','travel','daily']);
  const normalizeRecognitionContext=value=>{
    const v=String(value||'auto').toLowerCase();
    return validRecognitionContexts.has(v)?v:'auto';
  };
  window.__recognitionContext=normalizeRecognitionContext(
    localStorage.getItem('ans_recognition_context')||'auto'
  );
  window.setRecognitionContext=function(value){
    const v=normalizeRecognitionContext(value);
    window.__recognitionContext=v;
    try{ localStorage.setItem('ans_recognition_context',v); }catch(e){}
    return v;
  };
  window.getRecognitionContext=function(){
    return normalizeRecognitionContext(window.__recognitionContext);
  };

  // AUTO27: 목표 시안과 같은 단일 자동통역 화면. 기존 웹 UI는 기능만 백그라운드에서 사용한다.
  const SCENARIO_MODE_KEY='ans_usage_mode_v2';
  const SCENARIO_DETAIL_KEY='ans_usage_detail_v2';
  const SCENARIO_DEFS={
    work:{label:'업무용',icon:'💼',details:['학교·교육','행정·민원','회사·사무','병원·의료','계약·서류','방문·전화응대']},
    travel:{label:'여행용',icon:'✈️',details:['공항','숙소','식당','교통','쇼핑','긴급상황']},
    daily:{label:'일상용',icon:'🏠',details:['인사·소개','가족·친구','약속·시간','음식·생활','쇼핑','길찾기','자유대화']}
  };

  function safeScenarioMode(value){
    const v=String(value||'work').toLowerCase();
    return SCENARIO_DEFS[v]?v:'work';
  }

  function installTargetStyles(){
    if(document.getElementById('nativeTargetStyle'))return;
    const style=document.createElement('style');
    style.id='nativeTargetStyle';
    style.textContent=`
      html,body{background:#f5fafb!important}
      body.native-target-active{margin:0!important;padding:0!important;overflow-x:hidden!important}
      body.native-target-active>header.top,
      body.native-target-active>main,
      body.native-target-active>.speech-dock,
      body.native-target-active>#bottomNav,
      body.native-target-active>.appversion{display:none!important}
      #nativeEarphoneStatus{display:none!important}
      #nativeTargetApp{display:block;min-height:100vh;background:linear-gradient(180deg,#ffffff 0,#f5fafb 100%);color:#18384b;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;padding-bottom:78px;box-sizing:border-box}
      #nativeTargetApp *{box-sizing:border-box}
      .nt-header{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:12px 14px 8px;background:#fff}
      .nt-brand{display:flex;align-items:center;gap:10px;min-width:0}
      .nt-logo{width:39px;height:39px;border-radius:12px;background:linear-gradient(135deg,#3c8ef7,#7b6ee8);color:#fff;display:grid;place-items:center;font-weight:900;font-size:18px;box-shadow:0 5px 14px rgba(73,106,205,.22)}
      .nt-title{font-size:20px;line-height:1.05;font-weight:900;color:#17354d;margin:0}
      .nt-subtitle{font-size:10px;color:#788d99;margin-top:4px;font-weight:700;white-space:nowrap}
      .nt-head-actions{display:flex;align-items:center;gap:6px}
      .nt-icon-btn{border:1px solid #dbe8ed;background:#fff;color:#37586d;border-radius:10px;min-width:34px;height:34px;padding:0 8px;font-size:12px;font-weight:800;box-shadow:0 2px 8px rgba(23,53,77,.04)}
      .nt-body{padding:0 12px 12px;max-width:760px;margin:0 auto}
      .nt-tabs{display:grid;grid-template-columns:repeat(3,1fr);gap:4px;background:#f5f8fa;border:1px solid #e4edf1;border-radius:12px;padding:4px;margin-top:4px}
      .nt-tab{border:0;background:transparent;border-radius:9px;padding:9px 4px;color:#547083;font-size:13px;font-weight:900;white-space:nowrap}
      .nt-tab.active{background:linear-gradient(135deg,#20a9ad,#1699a4);color:#fff;box-shadow:0 4px 12px rgba(22,153,164,.22)}
      .nt-details{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;padding:8px 1px 7px}
      .nt-details::-webkit-scrollbar{display:none}
      .nt-detail{flex:0 0 auto;border:1px solid #dfe9ee;background:#fff;color:#567285;border-radius:999px;padding:7px 10px;font-size:11px;font-weight:850}
      .nt-detail.active{border-color:#63c7ca;background:#e8f8f8;color:#16888e}
      .nt-language{display:grid;grid-template-columns:1fr 38px 1fr;gap:7px;align-items:end;background:#fff;border:1px solid #e0eaee;border-radius:12px;padding:9px 10px;box-shadow:0 3px 12px rgba(32,70,89,.04)}
      .nt-lang-block{min-width:0}
      .nt-lang-label{font-size:10px;color:#748a98;font-weight:850;margin-bottom:4px}
      .nt-lang-value,.nt-lang-select{width:100%;height:34px;border:1px solid #e0e9ed;border-radius:9px;background:#f9fbfc;color:#27485d;padding:0 10px;font-size:13px;font-weight:900}
      .nt-lang-value{display:flex;align-items:center}
      .nt-lang-select{appearance:auto}
      .nt-swap{width:34px;height:34px;border:0;border-radius:50%;background:#eaf7f7;color:#17979d;font-size:18px;font-weight:900;margin-bottom:1px}
      .nt-auto{margin-top:8px;background:#fff;border:1px solid #e0ebef;border-radius:14px;padding:11px;box-shadow:0 5px 18px rgba(34,70,91,.05)}
      .nt-auto-head{display:flex;align-items:center;justify-content:space-between;gap:8px}
      .nt-auto-title{font-size:16px;font-weight:950;color:#18384b}
      .nt-on{border-radius:999px;background:#e5f8ea;color:#25a356;padding:5px 8px;font-size:10px;font-weight:900;white-space:nowrap}
      .nt-auto-copy{font-size:9px;color:#8799a4;margin-top:3px}
      .nt-steps{display:grid;grid-template-columns:repeat(4,1fr);gap:2px;margin:10px 0 5px}
      .nt-step{text-align:center;position:relative;color:#8ca0aa}
      .nt-step:not(:last-child):after{content:"";position:absolute;top:16px;right:-12%;width:24%;height:1px;background:#dbe8ec}
      .nt-step-icon{width:30px;height:30px;border-radius:50%;margin:0 auto 4px;background:#eef5f7;display:grid;place-items:center;font-size:14px;border:1px solid #e1ebee}
      .nt-step-label{font-size:8.5px;font-weight:800;line-height:1.25}
      .nt-step.active{color:#178f95}
      .nt-step.active .nt-step-icon{background:#dff7f6;border-color:#74cfd0;color:#11888d;box-shadow:0 0 0 4px rgba(31,169,173,.08)}
      .nt-divider{height:1px;background:#e5eef1;margin:8px 0}
      .nt-mic-row{display:flex;align-items:center;justify-content:center;gap:16px;margin:7px 0 3px}
      .nt-wave{color:#86d9db;font-size:18px;letter-spacing:2px;opacity:.8}
      .nt-mic{width:64px;height:64px;border:8px solid #d9f4f4;border-radius:50%;background:linear-gradient(135deg,#27b6bb,#138e9a);color:#fff;display:grid;place-items:center;font-size:28px;box-shadow:0 7px 18px rgba(22,151,160,.25)}
      .nt-mic.idle{background:linear-gradient(135deg,#7fa9b1,#668f98)}
      .nt-status{text-align:center;font-size:12.5px;color:#178c92;font-weight:950;margin-top:6px}
      .nt-status-sub{text-align:center;font-size:9px;color:#94a5ad;margin-top:2px}
      .nt-chat{position:relative;border-radius:12px;padding:10px 42px 10px 10px;margin-top:7px;border:1px solid}
      .nt-chat.me{background:linear-gradient(180deg,#eff8ff,#e9f5ff);border-color:#cfe6f5}
      .nt-chat.them{background:linear-gradient(180deg,#f0fbf3,#eaf8ee);border-color:#cdebd5}
      .nt-chat-head{display:flex;align-items:center;gap:6px;margin-bottom:5px}
      .nt-avatar{width:23px;height:23px;border-radius:50%;display:grid;place-items:center;font-size:12px;font-weight:900}
      .nt-chat.me .nt-avatar{background:#cdeaff;color:#1478b8}
      .nt-chat.them .nt-avatar{background:#d2f2da;color:#2a9a4d}
      .nt-chat-name{font-size:11px;font-weight:950;color:#26485d}
      .nt-chat-lang{font-size:8.5px;color:#8398a4;font-weight:800}
      .nt-time{margin-left:auto;font-size:8px;color:#90a1aa}
      .nt-source{font-size:13px;font-weight:900;color:#18384b;line-height:1.35;min-height:18px}
      .nt-chat-line{height:1px;background:rgba(105,145,164,.18);margin:6px 0}
      .nt-result{font-size:10.5px;color:#5c7381;line-height:1.35;min-height:15px}
      .nt-sound{position:absolute;right:9px;bottom:11px;width:28px;height:28px;border:1px solid #d5e4e9;background:#fff;border-radius:9px;color:#527184;font-size:14px}
      .nt-progress{width:100%;border:0;border-radius:11px;background:linear-gradient(135deg,#18a5aa,#118f9a);color:#fff;font-size:13px;font-weight:950;padding:10px 12px;margin-top:9px;box-shadow:0 5px 14px rgba(17,143,154,.22)}
      .nt-progress.stop{background:linear-gradient(135deg,#bd5953,#a7443e)}
      .nt-manual{display:grid;grid-template-columns:1fr 1fr;gap:7px;margin-top:7px}
      .nt-manual-btn{border:1px solid #d9e5ea;background:#fff;color:#496879;border-radius:10px;padding:9px 5px;font-size:11.5px;font-weight:900}
      .nt-bottom{position:fixed;left:0;right:0;bottom:0;height:66px;background:rgba(255,255,255,.97);border-top:1px solid #e3ecef;display:grid;grid-template-columns:repeat(4,1fr);z-index:50;padding-bottom:max(4px,env(safe-area-inset-bottom));box-shadow:0 -4px 16px rgba(28,61,79,.05)}
      .nt-nav{border:0;background:transparent;color:#6f8591;font-size:9px;font-weight:850;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px}
      .nt-nav .ico{font-size:17px}
      .nt-nav.active{color:#15939a;background:#eefafb}
      .nt-float-message{position:fixed;left:50%;bottom:82px;transform:translateX(-50%);z-index:80;background:#173f4d;color:#fff;border-radius:999px;padding:8px 12px;font-size:11px;font-weight:800;box-shadow:0 5px 18px #0002;opacity:0;pointer-events:none;transition:opacity .2s}
      .nt-float-message.show{opacity:1}
      @media(min-width:700px){
        .nt-body{max-width:520px}
        .nt-header{max-width:520px;margin:0 auto}
        .nt-bottom{left:50%;right:auto;width:520px;transform:translateX(-50%);border-left:1px solid #e3ecef;border-right:1px solid #e3ecef}
      }
    `;
    document.head.appendChild(style);
  }

  function installTargetUI(){
    if(document.getElementById('nativeTargetApp'))return true;
    if(typeof setConversationMode!=='function' || !document.getElementById('conversationLanguage'))return false;
    installTargetStyles();

    const root=document.createElement('div');
    root.id='nativeTargetApp';
    root.innerHTML=`
      <header class="nt-header">
        <div class="nt-brand">
          <div class="nt-logo">A↔</div>
          <div><h1 class="nt-title">실시간 통역</h1><div class="nt-subtitle">업무 · 여행 · 일상 자동대화</div></div>
        </div>
        <div class="nt-head-actions">
          <button id="ntAudioBtn" class="nt-icon-btn" type="button" title="이어폰 분리 출력">🎧</button>
          <button id="ntSettingsBtn" class="nt-icon-btn" type="button">⚙ 설정</button>
        </div>
      </header>

      <main class="nt-body">
        <div class="nt-tabs">
          <button class="nt-tab" data-mode="work" type="button">💼 업무용</button>
          <button class="nt-tab" data-mode="travel" type="button">✈️ 여행용</button>
          <button class="nt-tab" data-mode="daily" type="button">⌂ 일상용</button>
        </div>
        <div id="ntDetailRow" class="nt-details"></div>

        <section class="nt-language">
          <div class="nt-lang-block"><div class="nt-lang-label">내 언어</div><div class="nt-lang-value">한국어</div></div>
          <button class="nt-swap" type="button" aria-label="언어 방향">⇄</button>
          <label class="nt-lang-block"><div class="nt-lang-label">상대 언어</div><select id="ntLanguageSelect" class="nt-lang-select"></select></label>
        </section>

        <section class="nt-auto">
          <div class="nt-auto-head">
            <div><div class="nt-auto-title">🤖 자동대화</div><div class="nt-auto-copy">자연스럽고 끊김 없는 대화를 제공합니다.</div></div>
            <div id="ntAutoBadge" class="nt-on">● 자동 인식 대기</div>
          </div>

          <div class="nt-steps">
            <div id="ntStep0" class="nt-step"><div class="nt-step-icon">🎙</div><div class="nt-step-label">내 말을 듣는 중</div></div>
            <div id="ntStep1" class="nt-step"><div class="nt-step-icon">▤</div><div class="nt-step-label">번역 중</div></div>
            <div id="ntStep2" class="nt-step"><div class="nt-step-icon">🎙</div><div class="nt-step-label">상대방 말을 듣는 중</div></div>
            <div id="ntStep3" class="nt-step"><div class="nt-step-icon">🔊</div><div class="nt-step-label">한국어로 들려주는 중</div></div>
          </div>

          <div class="nt-divider"></div>
          <div class="nt-mic-row"><div class="nt-wave">▮▯▮</div><button id="ntMicBtn" class="nt-mic idle" type="button">🎙</button><div class="nt-wave">▮▯▮</div></div>
          <div id="ntStatus" class="nt-status">자동대화를 시작해 주세요.</div>
          <div class="nt-status-sub">버튼 없이 자동으로 대화가 이어집니다.</div>
        </section>

        <article class="nt-chat me">
          <div class="nt-chat-head"><span class="nt-avatar">👤</span><span class="nt-chat-name">내 말</span><span class="nt-chat-lang">한국어</span><span id="ntMyTime" class="nt-time"></span></div>
          <div id="ntMySource" class="nt-source">말씀을 기다리고 있습니다.</div>
          <div class="nt-chat-line"></div>
          <div id="ntMyResult" class="nt-result">번역 결과가 여기에 표시됩니다.</div>
          <button id="ntMySound" class="nt-sound" type="button">🔊</button>
        </article>

        <article class="nt-chat them">
          <div class="nt-chat-head"><span class="nt-avatar">👤</span><span class="nt-chat-name">상대방 말</span><span id="ntTheirLang" class="nt-chat-lang">외국어</span><span id="ntTheirTime" class="nt-time"></span></div>
          <div id="ntTheirSource" class="nt-source">상대방 말씀을 기다리고 있습니다.</div>
          <div class="nt-chat-line"></div>
          <div id="ntTheirResult" class="nt-result">한국어 번역이 여기에 표시됩니다.</div>
          <button id="ntTheirSound" class="nt-sound" type="button">🎧</button>
        </article>

        <button id="ntAutoBar" class="nt-progress" type="button">▶ 자동대화 시작</button>
        <div class="nt-manual">
          <button id="ntStaffManual" class="nt-manual-btn" type="button">🎙 내가 말하기</button>
          <button id="ntVisitorManual" class="nt-manual-btn" type="button">🎧 수동 듣기</button>
        </div>
      </main>

      <nav class="nt-bottom">
        <button class="nt-nav active" type="button"><span class="ico">💬</span><span>대화</span></button>
        <button id="ntFavorites" class="nt-nav" type="button"><span class="ico">☆</span><span>즐겨찾기</span></button>
        <button id="ntHistory" class="nt-nav" type="button"><span class="ico">◷</span><span>기록</span></button>
        <button id="ntBottomSettings" class="nt-nav" type="button"><span class="ico">⚙</span><span>설정</span></button>
      </nav>
      <div id="ntToast" class="nt-float-message"></div>
    `;
    document.body.appendChild(root);
    document.body.classList.add('native-target-active');
    document.body.dataset.page='conversation';

    let mode=safeScenarioMode(localStorage.getItem(SCENARIO_MODE_KEY)||window.getRecognitionContext()||'work');
    let detail=String(localStorage.getItem(SCENARIO_DETAIL_KEY)||'');
    let lastStaffSource='',lastStaffResult='',lastVisitorSource='',lastVisitorResult='';
    let staffTime='',visitorTime='';

    const originalLanguage=document.getElementById('conversationLanguage');
    const targetLanguage=document.getElementById('ntLanguageSelect');

    function toast(text){
      const t=document.getElementById('ntToast');
      if(!t)return;
      t.textContent=text;t.classList.add('show');
      clearTimeout(t.__timer);t.__timer=setTimeout(()=>t.classList.remove('show'),1500);
    }

    function useful(value){
      const v=String(value||'').trim();
      if(!v)return '';
      if(/듣는 중|새 말씀을 듣고 있습니다|번역 중|번역문이 여기에 표시됩니다|자동대화를 시작하면|문장 확인 후 번역합니다/.test(v))return '';
      return v;
    }

    function currentClock(){
      try{return new Date().toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit'});}catch(e){return '';}
    }

    function syncLanguageOptions(){
      const value=originalLanguage.value;
      const html=[...originalLanguage.options].map(o=>`<option value="${String(o.value).replaceAll('"','&quot;')}">${o.textContent}</option>`).join('');
      if(targetLanguage.innerHTML!==html)targetLanguage.innerHTML=html;
      targetLanguage.value=value;
      const selected=originalLanguage.options[originalLanguage.selectedIndex];
      const foreign=selected?selected.textContent:'외국어';
      document.getElementById('ntTheirLang').textContent=foreign;
    }

    function autoRunning(){
      try{return conversationMode==='auto'&&autoConversationActive;}catch(e){}
      const b=document.getElementById('autoConversationBtn');
      return !!(b&&(b.classList.contains('active')||/종료/.test(b.textContent||'')));
    }

    function getStage(){
      if(!autoRunning())return -1;
      let turn='staff';
      try{turn=autoConversationTurn||'staff';}catch(e){}
      const st=String(document.getElementById('staffSpeechStatus')?.textContent||'');
      const vt=String(document.getElementById('speechStatus')?.textContent||'');
      if(turn==='visitor'){
        if(/번역 중|읽는 중|번역 완료/.test(vt))return 3;
        return 2;
      }
      if(/번역 중|읽는 중|번역 완료/.test(st))return 1;
      return 0;
    }

    function syncStage(){
      const running=autoRunning();
      const stage=getStage();
      for(let i=0;i<4;i++)document.getElementById('ntStep'+i)?.classList.toggle('active',i===stage);
      const badge=document.getElementById('ntAutoBadge');
      const mic=document.getElementById('ntMicBtn');
      const bar=document.getElementById('ntAutoBar');
      if(badge)badge.textContent=running?'● 자동 인식 ON':'● 자동 인식 대기';
      mic?.classList.toggle('idle',!running);
      if(bar){
        bar.textContent=running?'▮▮  자동대화 진행 중':'▶ 자동대화 시작';
        bar.classList.toggle('stop',running);
      }
      const status=document.getElementById('ntStatus');
      if(!status)return;
      const messages=[
        '내 말을 자동으로 듣고 있습니다.',
        '내 말을 번역해 상대방에게 들려주고 있습니다.',
        '상대방 말을 자동으로 듣고 있습니다.',
        '상대방 말을 한국어로 들려주고 있습니다.'
      ];
      status.textContent=stage>=0?messages[stage]:'자동대화를 시작해 주세요.';
    }

    function syncMessages(){
      const ss=useful(document.getElementById('staffHeardText')?.textContent);
      const sr=useful(document.getElementById('staffForeignResult')?.textContent);
      const vs=useful(document.getElementById('heardText')?.textContent);
      const vr=useful(document.getElementById('koreanResult')?.textContent);
      if(ss && ss!==lastStaffSource){lastStaffSource=ss;staffTime=currentClock();}
      if(sr && sr!==lastStaffResult){lastStaffResult=sr;if(!staffTime)staffTime=currentClock();}
      if(vs && vs!==lastVisitorSource){lastVisitorSource=vs;visitorTime=currentClock();}
      if(vr && vr!==lastVisitorResult){lastVisitorResult=vr;if(!visitorTime)visitorTime=currentClock();}
      document.getElementById('ntMySource').textContent=ss||lastStaffSource||'말씀을 기다리고 있습니다.';
      document.getElementById('ntMyResult').textContent=sr||lastStaffResult||'번역 결과가 여기에 표시됩니다.';
      document.getElementById('ntTheirSource').textContent=vs||lastVisitorSource||'상대방 말씀을 기다리고 있습니다.';
      document.getElementById('ntTheirResult').textContent=vr||lastVisitorResult||'한국어 번역이 여기에 표시됩니다.';
      document.getElementById('ntMyTime').textContent=staffTime;
      document.getElementById('ntTheirTime').textContent=visitorTime;
      syncLanguageOptions();
    }

    function syncAudio(){
      const source=document.getElementById('nativeEarphoneStatus');
      const button=document.getElementById('ntAudioBtn');
      if(!button)return;
      const text=String(source?.textContent||'');
      button.textContent=/분리 ON/.test(text)?'🎧':'🔊';
      button.title=text||'이어폰 출력';
    }

    function syncAll(){
      document.body.classList.add('native-target-active');
      document.body.dataset.page='conversation';
      syncStage();syncMessages();syncAudio();
    }

    function renderDetails(){
      const row=document.getElementById('ntDetailRow');
      const def=SCENARIO_DEFS[mode];
      if(!def.details.includes(detail))detail=def.details[0];
      row.replaceChildren();
      def.details.forEach(name=>{
        const b=document.createElement('button');
        b.className='nt-detail'+(name===detail?' active':'');
        b.type='button';b.textContent=name;
        b.onclick=()=>{
          detail=name;try{localStorage.setItem(SCENARIO_DETAIL_KEY,detail);}catch(e){}
          renderDetails();
        };
        row.appendChild(b);
      });
    }

    function setMode(next){
      mode=safeScenarioMode(next);
      try{localStorage.setItem(SCENARIO_MODE_KEY,mode);}catch(e){}
      try{window.setRecognitionContext(mode);}catch(e){}
      root.querySelectorAll('.nt-tab').forEach(b=>b.classList.toggle('active',b.dataset.mode===mode));
      if(!SCENARIO_DEFS[mode].details.includes(detail))detail=SCENARIO_DEFS[mode].details[0];
      try{localStorage.setItem(SCENARIO_DETAIL_KEY,detail);}catch(e){}
      renderDetails();
    }

    function toggleAuto(){
      try{window.setRecognitionContext(mode);}catch(e){}
      try{
        if(autoRunning()){
          if(typeof stopAutoConversation==='function')stopAutoConversation('자동대화를 종료했습니다.');
          else document.getElementById('autoConversationBtn')?.click();
        }else{
          if(typeof setConversationMode==='function')setConversationMode('auto');
          if(typeof startAutoConversation==='function')startAutoConversation();
          else document.getElementById('autoConversationBtn')?.click();
        }
      }catch(e){console.error('auto27 toggle',e);}
      setTimeout(syncAll,60);
    }

    root.querySelectorAll('.nt-tab').forEach(b=>b.onclick=()=>setMode(b.dataset.mode));
    targetLanguage.onchange=()=>{
      originalLanguage.value=targetLanguage.value;
      originalLanguage.dispatchEvent(new Event('change',{bubbles:true}));
      setTimeout(syncAll,30);
    };
    document.getElementById('ntSettingsBtn').onclick=()=>document.getElementById('settingsBtn')?.click();
    document.getElementById('ntBottomSettings').onclick=()=>document.getElementById('settingsBtn')?.click();
    document.getElementById('ntAudioBtn').onclick=()=>document.getElementById('nativeEarphoneStatus')?.click();
    document.getElementById('ntMicBtn').onclick=toggleAuto;
    document.getElementById('ntAutoBar').onclick=toggleAuto;
    document.getElementById('ntMySound').onclick=()=>document.getElementById('staffSpeakResultBtn')?.click();
    document.getElementById('ntTheirSound').onclick=()=>document.getElementById('speakKoreanBtn')?.click();

    document.getElementById('ntStaffManual').onclick=()=>{
      try{cancelActiveSpeech();setTimeout(()=>beginSpeech('staff'),80);}catch(e){document.getElementById('staffMicBtn')?.click();}
    };
    document.getElementById('ntVisitorManual').onclick=()=>{
      try{cancelActiveSpeech();setTimeout(()=>beginSpeech('visitor'),80);}catch(e){document.getElementById('micBtn')?.click();}
    };

    document.getElementById('ntFavorites').onclick=()=>{
      document.body.classList.remove('native-target-active');
      root.style.display='none';
      try{openWorkspacePage('favorites');}catch(e){document.querySelector('[data-page-nav="favorites"]')?.click();}
    };

    document.getElementById('ntHistory').onclick=()=>toast('기록 화면은 다음 단계에서 연결합니다.');

    document.querySelector('[data-page-nav="conversation"]')?.addEventListener('click',()=>{
      setTimeout(()=>{root.style.display='block';document.body.classList.add('native-target-active');syncAll();},40);
    });

    if(typeof scheduleAutoTurn==='function'&&!window.__auto27FastTurn){
      const originalSchedule=scheduleAutoTurn;window.__auto27FastTurn=true;
      scheduleAutoTurn=function(side,delay){
        const n=Number(delay);
        return originalSchedule(side,Math.min(Number.isFinite(n)&&n>0?n:360,360));
      };
    }

    ['autoConversationBtn','dockStatus','staffSpeechStatus','speechStatus','staffHeardText','staffForeignResult','heardText','koreanResult'].forEach(id=>{
      const el=document.getElementById(id);
      if(el)new MutationObserver(syncAll).observe(el,{attributes:true,childList:true,subtree:true,characterData:true});
    });
    new MutationObserver(syncAudio).observe(document.body,{childList:true,subtree:true});

    originalLanguage.addEventListener('change',()=>setTimeout(syncAll,20));
    if(typeof setConversationMode==='function')setConversationMode('auto');
    setMode(mode);
    syncLanguageOptions();
    syncAll();
    return true;
  }

  let auto27UiAttempts=0;
  function ensureAuto27UI(){
    try{
      if(installTargetUI())return;
    }catch(e){
      console.error('AUTO27 UI install',e);
    }
    if(++auto27UiAttempts<50)setTimeout(ensureAuto27UI,200);
  }
  setTimeout(ensureAuto27UI,0);

  function NativeRecognition(){
    this.lang='ko-KR'; this.continuous=true; this.interimResults=false; this.maxAlternatives=5;
    this.onstart=this.onspeechstart=this.onspeechend=this.onresult=this.onerror=this.onend=null;
    this.__id=null;
    this.__heard=false;
    this.__autoSide='';
    this.__autoSilenceTimer=null;
    this.__autoSilenceHandled=false;
  }

  function clearAutoSilenceTimer(r){
    if(!r)return;
    clearTimeout(r.__autoSilenceTimer);
    r.__autoSilenceTimer=null;
  }

  function handleAutoSilence(r){
    if(!r || r.__autoSilenceHandled || r.__heard)return false;
    let running=false;
    try{running=conversationMode==='auto'&&autoConversationActive;}catch(e){}
    if(!running)return false;

    r.__autoSilenceHandled=true;
    clearAutoSilenceTimer(r);
    const silentSide=r.__autoSide==='visitor'?'visitor':'staff';
    const nextSide=silentSide==='visitor'?'staff':'staff';

    // Close only the current microphone session. Keep automatic conversation ON.
    try{cancelActiveSpeech();}catch(e){
      try{if(r.__id)AndroidAudio.stopRecognition(r.__id,true);}catch(ignore){}
    }

    const dock=document.getElementById('dockStatus');
    if(dock){
      dock.textContent=silentSide==='visitor'
        ? '상대방 응답 없음 · 직원 말씀을 다시 듣습니다'
        : '직원 말씀을 계속 기다립니다';
    }

    setTimeout(()=>{
      try{
        if(conversationMode==='auto'&&autoConversationActive){
          scheduleAutoTurn(nextSide,0);
        }
      }catch(e){}
    },120);
    return true;
  }

  NativeRecognition.prototype.start=function(){
    this.__id='r'+Date.now()+'_'+(++recSeq); recognizers[this.__id]=this;
    this.__heard=false;
    this.__autoSilenceHandled=false;
    clearAutoSilenceTimer(this);
    try{
      this.__autoSide=(conversationMode==='auto'&&autoConversationActive)?String(autoConversationTurn||'staff'):'';
    }catch(e){this.__autoSide='';}
    const context=normalizeRecognitionContext(window.__recognitionContext);
    try{
      AndroidAudio.startRecognitionWithContext(
        this.__id,
        String(this.lang||'ko-KR'),
        context
      );
    }catch(e){
      AndroidAudio.startRecognition(this.__id,String(this.lang||'ko-KR'));
    }
  };
  NativeRecognition.prototype.stop=function(){ clearAutoSilenceTimer(this); if(this.__id) AndroidAudio.stopRecognition(this.__id,false); };
  NativeRecognition.prototype.abort=function(){ clearAutoSilenceTimer(this); if(this.__id) AndroidAudio.stopRecognition(this.__id,true); };
  window.SpeechRecognition=NativeRecognition;
  window.webkitSpeechRecognition=NativeRecognition;
  window.__nativeRecognitionEvent=function(id,type,payload){
    const r=recognizers[id]; if(!r) return;
    if(type==='start'){
      safe(r.onstart,{type:'start'});
      clearAutoSilenceTimer(r);
      let running=false;
      try{running=conversationMode==='auto'&&autoConversationActive;}catch(e){}
      if(running){
        const dock=document.getElementById('dockStatus');
        if(dock && r.__autoSide==='visitor')dock.textContent='상대방 말씀을 기다립니다 · 무응답이면 직원 차례로 돌아갑니다';
        r.__autoSilenceTimer=setTimeout(()=>handleAutoSilence(r),1800);
      }
    }
    else if(type==='speechstart'){
      r.__heard=true; clearAutoSilenceTimer(r);
      safe(r.onspeechstart,{type:'speechstart'});
    }
    else if(type==='speechend') safe(r.onspeechend,{type:'speechend'});
    else if(type==='partial'||type==='final'){
      r.__heard=true; clearAutoSilenceTimer(r);
      const alt={transcript:String(payload||''),confidence:1};
      const row=[alt]; row.isFinal=(type==='final');
      const results=[row];
      safe(r.onresult,{resultIndex:0,results:results});
    } else if(type==='error'){
      const err=String(payload||'unknown');
      clearAutoSilenceTimer(r);
      if((err==='no-speech'||err==='aborted')&&!r.__heard&&handleAutoSilence(r))return;
      safe(r.onerror,{error:err});
    }
    else if(type==='end'){
      clearAutoSilenceTimer(r);
      if(!r.__heard && handleAutoSilence(r))return;
      safe(r.onend,{type:'end'}); delete recognizers[id]; r.__id=null;
    }
  };

  function installStatus(){
    if(document.getElementById('nativeEarphoneStatus')) return;
    const bar=document.createElement('button'); bar.id='nativeEarphoneStatus'; bar.type='button';
    bar.style.cssText='position:static;display:block;margin:0 auto 8px;max-width:calc(100vw - 16px);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;background:#0f5c55;color:white;border:0;padding:8px 11px;border-radius:999px;font:700 11px system-ui;box-shadow:0 3px 14px #0003;cursor:pointer';
    let st=''; let mode=true;
    try{st=AndroidAudio.audioStatus(); mode=!!AndroidAudio.earphoneMode();}catch(e){}

    const place=()=>{
      try{
        const header=document.querySelector('.top');
        const bottom=header ? header.getBoundingClientRect().bottom : 72;
        bar.style.top=Math.max(8,Math.ceil(bottom+8))+'px';
      }catch(e){
        bar.style.top='88px';
      }
    };

    const paint=()=>{
      bar.textContent=(mode?'🎧 분리 ON · ':'🔊 일반 출력 · ')+st;
      bar.style.background=mode?'#0f5c55':'#334155';
    };

    bar.addEventListener('click',()=>{
      mode=!mode;
      try{AndroidAudio.setEarphoneMode(mode);}catch(e){}
      paint();
    });

    paint();
    (document.querySelector('.top')||document.body).appendChild(bar);
    place();
    window.addEventListener('resize',place);
    window.addEventListener('orientationchange',()=>setTimeout(place,120));
  }
  window.__nativeAudioStatus=function(st,mode){
    const b=document.getElementById('nativeEarphoneStatus'); if(!b) return;
    b.textContent=(mode?'🎧 이어폰 분리 ON · ':'🔊 일반 출력 · ')+st;
    b.style.background=mode?'#0f5c55':'#334155';
  };
  installStatus();
})();
""";
}
