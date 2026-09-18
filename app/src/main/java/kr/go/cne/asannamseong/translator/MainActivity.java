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
        webView.loadUrl(APP_URL);
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setSpeechRate(0.94f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { }
                    @Override public void onError(String utteranceId) { handleTtsSynthesisError(utteranceId, "TTS_ERROR"); }
                    @Override public void onError(String utteranceId, int errorCode) { handleTtsSynthesisError(utteranceId, "TTS_ERROR_" + errorCode); }
                    @Override public void onDone(String utteranceId) { handleTtsFileReady(utteranceId); }
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions();
            jsRecognition(id, "error", "not-allowed");
            jsRecognition(id, "end", "");
            return;
        }
        stopNativeRecognition(true);
        activeRecognitionId = id;
        recognitionEnded = false;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            private boolean begun = false;
            @Override public void onReadyForSpeech(Bundle params) { jsRecognition(id, "start", ""); }
            @Override public void onBeginningOfSpeech() { begun = true; jsRecognition(id, "speechstart", ""); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { if (begun) jsRecognition(id, "speechend", ""); }
            @Override public void onError(int error) {
                if (!isCurrentRecognition(id)) return;
                jsRecognition(id, "error", mapRecognitionError(error));
                finishRecognition(id);
            }
            @Override public void onResults(Bundle results) {
                if (!isCurrentRecognition(id)) return;
                String text = firstResult(results);
                if (!text.isEmpty()) jsRecognition(id, "final", text);
                finishRecognition(id);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                if (!isCurrentRecognition(id)) return;
                String text = firstResult(partialResults);
                if (!text.isEmpty()) jsRecognition(id, "partial", text);
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { speechRecognizer.startListening(intent); }
        catch (Exception e) {
            jsRecognition(id, "error", "audio-capture");
            finishRecognition(id);
        }
    }

    private String firstResult(Bundle b) {
        if (b == null) return "";
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (list == null || list.isEmpty() || list.get(0) == null) ? "" : list.get(0).trim();
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
                String nativeId = "tts_" + UUID.randomUUID();
                File f = new File(getCacheDir(), nativeId + ".wav");
                PendingTts p = new PendingTts(jsId, f, target);
                synchronized (pendingTts) { pendingTts.put(nativeId, p); }
                Bundle params = new Bundle();
                int result = tts.synthesizeToFile(text, params, f, nativeId);
                if (result != TextToSpeech.SUCCESS) handleTtsSynthesisError(nativeId, "SYNTH_QUEUE_FAILED");
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
            main.post(() -> startNativeRecognition(id, lang));
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
        public String appVersion() { return "0.4-faster-speech"; }
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

  // Shorten Android's post-recognition wait; retain final-result safeguards.
  if(window.SpeechSession && window.SpeechSession.VERSION==='6.7'){
    window.SpeechSession.prototype.arm=function(){
      if(this.closed || this.stopping) return;
      this.clearSilence();
      this.deadline=Date.now()+1200;
      const tick=()=>{
        const remaining=this.deadline-Date.now();
        if(remaining<=0){ this.stopForFinal(); return; }
        this.options.state('waiting',Math.ceil(remaining/1000));
        this.silenceTimer=setTimeout(tick,Math.min(250,remaining));
      };
      tick();
    };
        window.SpeechSession.prototype.stopForFinal=function(){
      if(this.closed || this.stopping) return;
      this.stopping=true;
      this.clearSilence();
      this.options.state('finalizing');
      if(this.ended){ this.finish(); return; }
      this.finalTimer=setTimeout(()=>this.finish(),500);
      try{ this.recognition.stop(); }catch(e){ this.finish(); }
    };
    const hint=document.getElementById('workspaceHint');
    if(hint){
      const updateHint=()=>{
        const text=hint.textContent;
        const updated=text.replace('3초 후 자동 번역','1.2초 후 자동 번역');
        if(text!==updated) hint.textContent=updated;
      };
      new MutationObserver(updateHint).observe(hint,{childList:true,subtree:true,characterData:true});
      updateHint();
    }
  }
  const utterances={}; let utterSeq=0;
  function safe(fn,arg){ try{ if(typeof fn==='function') fn(arg); }catch(e){ console.warn(e); } }
if(window.AndroidAudio){
  if(typeof window.SpeechSynthesisUtterance!=='function'){
    window.SpeechSynthesisUtterance=function(text){
      this.text=String(text||'');
      this.lang='';
      this.rate=1;
      this.pitch=1;
      this.volume=1;
      this.onstart=null;
      this.onend=null;
      this.onerror=null;
    };
  }

  if(!window.speechSynthesis){
    window.speechSynthesis={};
  }

  window.speechSynthesis.speak=function(u){
    const id='u'+Date.now()+'_'+(++utterSeq);
    utterances[id]=u;
    AndroidAudio.speak(
      id,
      String((u&&u.text)||''),
      String((u&&u.lang)||'')
    );
  };

  window.speechSynthesis.cancel=function(){
    try{
      AndroidAudio.stopTts();
    }catch(e){}

    Object.keys(utterances).forEach(id=>{
      const u=utterances[id];
      safe(u&&u.onerror,{error:'canceled'});
      delete utterances[id];
    });
  };

  window.speechSynthesis.pause=function(){};
  window.speechSynthesis.resume=function(){};
  window.speechSynthesis.getVoices=function(){return [];};
}
  window.__nativeTtsEvent=function(id,type,detail){
    const u=utterances[id]; if(!u) return;
    if(type==='start') safe(u.onstart,{type:'start'});
    else if(type==='end'){ safe(u.onend,{type:'end'}); delete utterances[id]; }
    else if(type==='error'){ safe(u.onerror,{type:'error',error:detail||'native'}); delete utterances[id]; }
  };

  const recognizers={}; let recSeq=0;
  function NativeRecognition(){
    this.lang='ko-KR'; this.continuous=true; this.interimResults=false; this.maxAlternatives=1;
    this.onstart=this.onspeechstart=this.onspeechend=this.onresult=this.onerror=this.onend=null;
    this.__id=null;
  }
  NativeRecognition.prototype.start=function(){
    this.__id='r'+Date.now()+'_'+(++recSeq); recognizers[this.__id]=this;
    AndroidAudio.startRecognition(this.__id,String(this.lang||'ko-KR'));
  };
  NativeRecognition.prototype.stop=function(){ if(this.__id) AndroidAudio.stopRecognition(this.__id,false); };
  NativeRecognition.prototype.abort=function(){ if(this.__id) AndroidAudio.stopRecognition(this.__id,true); };
  window.SpeechRecognition=NativeRecognition;
  window.webkitSpeechRecognition=NativeRecognition;
  window.__nativeRecognitionEvent=function(id,type,payload){
    const r=recognizers[id]; if(!r) return;
    if(type==='start') safe(r.onstart,{type:'start'});
    else if(type==='speechstart') safe(r.onspeechstart,{type:'speechstart'});
    else if(type==='speechend') safe(r.onspeechend,{type:'speechend'});
    else if(type==='partial'||type==='final'){
      const alt={transcript:String(payload||''),confidence:1};
      const row=[alt]; row.isFinal=(type==='final');
      const results=[row];
      safe(r.onresult,{resultIndex:0,results:results});
    } else if(type==='error') safe(r.onerror,{error:String(payload||'unknown')});
    else if(type==='end'){ safe(r.onend,{type:'end'}); delete recognizers[id]; r.__id=null; }
  };

  function installStatus(){
    if(document.getElementById('nativeEarphoneStatus')) return;
    const bar=document.createElement('button'); bar.id='nativeEarphoneStatus'; bar.type='button';
    bar.style.cssText='position:fixed;z-index:99999;right:8px;top:max(60px,calc(env(safe-area-inset-top) + 52px));background:#0f5c55;color:white;border:0;padding:8px 11px;border-radius:999px;font:700 11px system-ui;box-shadow:0 3px 14px #0003;cursor:pointer';
    let st=''; let mode=true;
    try{st=AndroidAudio.audioStatus(); mode=!!AndroidAudio.earphoneMode();}catch(e){}
    const paint=()=>{ bar.textContent=(mode?'🎧 이어폰 분리 ON · ':'🔊 일반 출력 · ')+st; bar.style.background=mode?'#0f5c55':'#334155'; };
    bar.addEventListener('click',()=>{ mode=!mode; try{AndroidAudio.setEarphoneMode(mode);}catch(e){} paint(); });
    paint(); document.body.appendChild(bar);
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
