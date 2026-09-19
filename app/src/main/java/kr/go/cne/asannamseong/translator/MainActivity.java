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
        webView.loadUrl(APP_URL + "?native=1.0-test1");
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
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
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
        public String appVersion() { return "1.0-test1b-realtime"; }
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
      },350);
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
      this.closed=false;
      this.ended=false;
      this.stopping=false;
      this.results=[];

      const session=this;

      this.__rtMaxTimer=setTimeout(()=>{
        if(session.closed)return;
        if(session.__rtFullText())session.stopForFinal();
        else{
          session.cancel();
          session.options.error('no-speech');
        }
      },30000);

      this.__rtLaunch=function(){
        if(session.closed || session.stopping)return;
        if(Date.now()-session.__rtStartedAt>=30000){
          session.stopForFinal();
          return;
        }

        const generation=++session.__rtGeneration;
        const r=session.recognition=new Ctor();
        r.lang=lang;
        r.continuous=false;
        r.interimResults=true;
        r.maxAlternatives=1;

        const current=()=>generation===session.__rtGeneration && !session.closed;

        r.onstart=()=>{
          if(!current())return;
          session.options.state('listening');
        };

        r.onspeechstart=()=>{
          if(!current() || session.stopping)return;
          session.options.state('listening');
        };

        r.onspeechend=()=>{
          if(!current() || session.stopping)return;
          if(session.__rtFullText())session.arm();
        };

        r.onresult=event=>{
          if(!current())return;
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

          // A restarted recognizer may time out while we are waiting to see
          // whether the speaker continues. If we already have text, finalize it.
          if((error==='no-speech' || error==='aborted') && session.__rtFullText()){
            session.stopForFinal();
            return;
          }

          session.cancel();
          session.options.error(error);
        };

        r.onend=()=>{
          if(!current())return;
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
      this.options.state('finalizing');

      if(this.__rtCurrent){
        this.__rtCommitted=joinSegments(this.__rtCommitted,this.__rtCurrent);
        this.__rtCurrent='';
        this.__rtShow();
      }

      this.finalTimer=setTimeout(()=>this.finish(),650);
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
        [String(sourceKey||''),String(targetKey||''),cleanText(text)].join('§');

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
    bar.style.cssText='position:fixed;z-index:99999;right:8px;top:8px;max-width:calc(100vw - 16px);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;background:#0f5c55;color:white;border:0;padding:8px 11px;border-radius:999px;font:700 11px system-ui;box-shadow:0 3px 14px #0003;cursor:pointer';
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
    document.body.appendChild(bar);
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
