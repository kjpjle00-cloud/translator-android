package com.kjpjle00.dailytranslator;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent session; independent recognition evidence for every utterance. */
public class AutoConversationEngine {
    public interface Listener {
        void onListening();
        void onPartial(String text, String languageTag);
        void onUtterance(String text, String languageTag);
        void onIdleRetry();
        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
    private final Runnable restart = this::startListeningNow;
    private final Set<String> downloading = new HashSet<>();
    private final Set<String> missingModels = new HashSet<>();
    private AppLanguage firstLanguage = AppLanguage.KOREAN;
    private AppLanguage secondLanguage = AppLanguage.ENGLISH;
    private SpeechRecognizer modelRecognizer;
    private Request current;
    private long generation;
    private long sequence;
    private long lastModelRequest = -30000L;
    private boolean active;
    private boolean processing;
    private boolean destroyed;
    private boolean playbackPaused;
    // A failed CURRENT detection can request one retry. This is not a speaker lock.
    private String retryLanguage;
    // Soft turn hint only: after A -> B translation, the next recognizer starts in B,
    // while Android language detection/switch still allows both selected languages.
    private String nextInitialLanguageTag;
    private String playbackText;
    private long playbackEndedAt = -10000L;

    // v0.11: recognition context only; does not alter translation/turn state.
    private String recognitionCategory = "여행용";
    private String recognitionScenario = "식당";
    private List<String> recognitionUserHints = Collections.emptyList();

    private static final class Request {
        final long generation;
        final long id;
        SpeechRecognizer recognizer;
        String speechCode;
        int speechConfidence = SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN;
        String initialCode;
        boolean conflictingSpeech;
        boolean switchFailed;
        boolean finalReceived;
        Runnable languageTimeout;
        Request(long generation, long id) { this.generation = generation; this.id = id; }
    }

    public AutoConversationEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else handler.post(action);
    }

    public void setLanguagePair(AppLanguage first, AppLanguage second) {
        onMain(() -> {
            boolean resume = active;
            stopInternal();
            if (first != null) firstLanguage = first;
            if (second != null) secondLanguage = second;
            playbackText = null;
            nextInitialLanguageTag = firstLanguage.speechTag;
            lastModelRequest = -30000L;
            downloading.clear();
            releaseModelRecognizer();
            if (resume) startInternal();
        });
    }

    public void setRecognitionContext(String category, String scenario, List<String> userHints) {
        onMain(() -> {
            recognitionCategory = category == null ? "" : category.trim();
            recognitionScenario = scenario == null ? "" : scenario.trim();
            ArrayList<String> copy = new ArrayList<>();
            if (userHints != null) {
                for (String hint : userHints) {
                    if (hint == null) continue;
                    String value = hint.trim();
                    if (value.isEmpty()) continue;
                    copy.add(value);
                    if (copy.size() >= 24) break;
                }
            }
            recognitionUserHints = copy;
        });
    }

    public boolean isAvailable() { return SpeechRecognizer.isRecognitionAvailable(context); }
    public boolean isActive() { return active; }
    public void start() { onMain(this::startInternal); }

    private void startInternal() {
        if (destroyed || active || !isAvailable()) return;
        generation++;
        active = true;
        processing = false;
        retryLanguage = null;
        nextInitialLanguageTag = firstLanguage.speechTag;
        prepareSpeechModels();
        startListeningNow();
    }

    public void stop() { onMain(this::stopInternal); }
    private void stopInternal() {
        generation++;
        active = false;
        processing = false;
        retryLanguage = null;
        downloading.clear();
        missingModels.clear();
        lastModelRequest = -30000L;
        releaseModelRecognizer();
        handler.removeCallbacks(restart);
        retireRequest();
    }

    /** Call before every automatic or phrase translation/playback. */
    public void pauseForPlayback() {
        onMain(() -> {
            playbackPaused = true;
            handler.removeCallbacks(restart);
            retireRequest();
        });
    }

    public void suppressPlaybackEcho(String text) {
        onMain(() -> playbackText = normalizeEcho(text));
    }

    public void resumeAfterProcessing() {
        onMain(() -> {
            playbackEndedAt = SystemClock.elapsedRealtime();
            playbackPaused = false;
            processing = false;
            scheduleRestart(520);
        });
    }

    private void scheduleRestart(long delay) {
        handler.removeCallbacks(restart);
        if (active && !destroyed && !processing && !playbackPaused) handler.postDelayed(restart, delay);
    }

    private boolean owns(Request request) {
        return active && !destroyed && !playbackPaused && current == request
                && request.generation == generation;
    }

    private boolean acceptsAudio(Request request) {
        return owns(request) && !request.finalReceived && !processing;
    }

    private void startListeningNow() {
        if (!active || destroyed || processing || playbackPaused) return;

        // v0.9: on-device model 상태는 "준비 힌트"일 뿐 듣기 시작의 전제조건이 아니다.
        // 기본 SpeechRecognizer는 네트워크/기기 서비스를 사용할 수 있으므로,
        // missingModels 때문에 마이크 자체를 막으면 자동대화가 영구 대기 상태에 빠질 수 있다.
        if (!missingModels.isEmpty()) {
            prepareSpeechModels();
        }

        retireRequest();
        Request request = new Request(generation, ++sequence);
        current = request;
        try {
            request.recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            request.recognizer.setRecognitionListener(listenerFor(request));

            String initial;
            if (retryLanguage != null) {
                initial = retryLanguage;
            } else if (nextInitialLanguageTag != null) {
                initial = nextInitialLanguageTag;
            } else {
                initial = firstLanguage.speechTag;
            }

            retryLanguage = null;
            request.initialCode = LanguageDecisionEngine.code(initial);
            request.recognizer.startListening(recognizerIntent(initial));
        } catch (Exception e) {
            if (owns(request)) {
                retireRequest();
                notifyError("음성 인식을 다시 준비합니다.");
                scheduleRestart(1000);
            }
        }
    }

    private RecognitionListener listenerFor(Request request) {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (acceptsAudio(request) && listener != null) listener.onListening();
            }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float value) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int type, Bundle params) { }

            @Override public void onPartialResults(Bundle results) {
                if (!acceptsAudio(request) || results == null) return;
                ArrayList<String> candidates = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                int index = LanguageDecisionEngine.firstCandidate(candidates);
                if (index >= 0 && listener != null) listener.onPartial(candidates.get(index), request.speechCode);
            }

            @Override public void onLanguageDetection(Bundle results) {
                if (!acceptsAudio(request) || results == null || Build.VERSION.SDK_INT < 34) return;

                String code = LanguageDecisionEngine.code(
                        results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                );
                int confidence = results.getInt(
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN
                );
                int switched = results.getInt(
                        SpeechRecognizer.LANGUAGE_SWITCH_RESULT,
                        SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED
                );

                if (switched == SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SUCCEEDED) {
                    request.switchFailed = false;
                } else if (switched == SpeechRecognizer.LANGUAGE_SWITCH_RESULT_FAILED
                        || switched == SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL) {
                    request.switchFailed = true;
                    if (switched == SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SKIPPED_NO_MODEL
                            && tagFor(code) != null) {
                        missingModels.add(code);
                    }
                    prepareSpeechModels();
                }

                // v0.10:
                // QUICK_RESPONSE itself may switch from NOT_CONFIDENT(level 1).
                // Do not throw that evidence away. Keep the strongest observation.
                if (code != null
                        && tagFor(code) != null
                        && confidence
                        >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_NOT_CONFIDENT) {

                    if (request.speechCode != null
                            && !request.speechCode.equals(code)
                            && request.speechConfidence
                            >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT
                            && confidence
                            >= SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) {
                        request.conflictingSpeech = true;
                    }

                    if (request.speechCode == null
                            || code.equals(request.speechCode)
                            || confidence >= request.speechConfidence) {
                        request.speechCode = code;
                        request.speechConfidence = Math.max(
                                request.speechConfidence,
                                confidence
                        );
                    }
                }
            }

            @Override public void onResults(Bundle results) {
                if (!acceptsAudio(request)) return;
                request.finalReceived = true;
                processing = true;
                closeRecognizer(request);
                ArrayList<String> candidates = results == null ? null
                        : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float[] scores = results == null ? null
                        : results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

                EverydaySpeechHints.Selection selected =
                        EverydaySpeechHints.selectBestCandidate(
                                candidates, scores, request.speechCode,
                                request.speechConfidence, request.initialCode,
                                firstLanguage.code, secondLanguage.code,
                                recognitionCategory, recognitionScenario,
                                recognitionUserHints
                        );

                int index = selected.index;
                if (index < 0) {
                    retry(request, "인식한 말이 없습니다. 다시 말씀해 주세요.");
                    return;
                }
                String raw = selected.text;
                float score = scores != null && index < scores.length ? scores[index] : -1f;
                Log.d("DailyASR", "request=" + request.id
                        + " candidateIndex=" + index
                        + " candidates=" + (candidates == null ? 0 : candidates.size())
                        + " reason=" + selected.reason); // No transcripts in logs.
                if (isPlaybackEcho(raw)) { retry(request, "번역음의 잔향을 제외하고 다시 듣습니다."); return; }
                request.languageTimeout = () -> decide(request, raw, score, new ArrayList<>());
                handler.postDelayed(request.languageTimeout, 2500);
                languageIdentifier.identifyPossibleLanguages(raw)
                        .addOnSuccessListener(found -> onMain(() -> {
                            List<LanguageDecisionEngine.TextEvidence> evidence = new ArrayList<>();
                            for (IdentifiedLanguage language : found) evidence.add(
                                    new LanguageDecisionEngine.TextEvidence(language.getLanguageTag(), language.getConfidence()));
                            decide(request, raw, score, evidence);
                        }))
                        .addOnFailureListener(e -> onMain(() -> decide(request, raw, score, new ArrayList<>())));
            }

            @Override public void onError(int error) {
                if (!acceptsAudio(request)) return;
                retireRequest();
                if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                    if (request.speechCode != null) missingModels.add(request.speechCode);
                    prepareSpeechModels();
                    notifyError("음성인식 언어 모델이 필요합니다. 다운로드 후 다시 말씀해 주세요.");
                    scheduleRestart(2500);
                } else if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) {
                    notifyError("현재 음성인식 서비스가 선택한 언어를 지원하지 않습니다.");
                    scheduleRestart(5000);
                } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stopInternal();
                    notifyError("마이크 권한을 확인하고 자동대화를 다시 시작해 주세요.");
                } else {
                    if (listener != null) listener.onIdleRetry();
                    scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1000 : 450);
                }
            }
        };
    }

    private void decide(Request request, String raw, float score,
            List<LanguageDecisionEngine.TextEvidence> evidence) {
        if (!owns(request) || !processing) return;
        if (request.languageTimeout != null) handler.removeCallbacks(request.languageTimeout);

        // v0.9: 모델 다운로드 상태 때문에 이미 얻은 음성인식 결과를 버리지 않는다.
        LanguageDecisionEngine.Decision decision = LanguageDecisionEngine.decide(
                raw,
                firstLanguage.code,
                secondLanguage.code,
                request.speechCode,
                request.speechConfidence,
                request.initialCode,
                request.conflictingSpeech,
                request.switchFailed,
                score,
                evidence
        );
        Log.d("DailyLanguage", "request=" + request.id + " reason=" + decision.reason
                + " source=" + decision.sourceCode); // No transcripts/recordings in logs.
        if (!decision.confirmed()) {
            if (!request.conflictingSpeech && request.speechCode != null) retryLanguage = tagFor(request.speechCode);
            retry(request, request.switchFailed
                    ? "음성 언어 전환을 완료하지 못했습니다. 모델 준비 후 다시 말씀해 주세요."
                    : "인식한 말: “" + raw + "” · 언어가 불확실하여 다시 듣습니다.");
            return;
        }
        String tag = tagFor(decision.sourceCode);

        // v0.10 soft-turn priming:
        // Korean -> Chinese playback means the next recognizer starts in Chinese.
        // Chinese -> Korean playback means the next recognizer starts in Korean.
        // This is NOT a forced turn: both languages remain enabled for auto switch.
        if (firstLanguage.code.equals(decision.sourceCode)) {
            nextInitialLanguageTag = secondLanguage.speechTag;
        } else if (secondLanguage.code.equals(decision.sourceCode)) {
            nextInitialLanguageTag = firstLanguage.speechTag;
        }
        retryLanguage = null;

        retireRequest();
        // processing stays true until translation/playback completion.
        if (tag != null && listener != null) listener.onUtterance(decision.text, tag);
        else { processing = false; scheduleRestart(400); }
    }

    private void retry(Request request, String message) {
        if (!owns(request)) return;
        retireRequest();
        processing = false;
        notifyError(message);
        scheduleRestart(600);
    }

    private String tagFor(String code) {
        code = LanguageDecisionEngine.code(code);
        if (firstLanguage.code.equals(code)) return firstLanguage.speechTag;
        if (secondLanguage.code.equals(code)) return secondLanguage.speechTag;
        return null;
    }

    private void notifyError(String message) { if (listener != null) listener.onError(message); }

    private void retireRequest() {
        Request previous = current;
        current = null; // Invalidate BEFORE cancel/destroy can deliver callbacks.
        if (previous != null) {
            if (previous.languageTimeout != null) handler.removeCallbacks(previous.languageTimeout);
            closeRecognizer(previous);
        }
    }

    private void closeRecognizer(Request request) {
        SpeechRecognizer recognizer = request.recognizer;
        request.recognizer = null;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            recognizer.destroy();
        }
    }

    private Intent recognizerIntent(String initialLanguage) {
        Intent intent = singleLanguageIntent(initialLanguage);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L);
        if (Build.VERSION.SDK_INT >= 33) {
            ArrayList<String> biases = recognitionBiases();
            if (!biases.isEmpty()) {
                intent.putStringArrayListExtra(
                        RecognizerIntent.EXTRA_BIASING_STRINGS,
                        biases
                );
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            ArrayList<String> pair = new ArrayList<>(
                    Arrays.asList(firstLanguage.speechTag, secondLanguage.speechTag)
            );
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            intent.putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    pair
            );
            intent.putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE
            );
            intent.putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    pair
            );

            if (Build.VERSION.SDK_INT >= 35) {
                intent.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_SWITCH_INITIAL_ACTIVE_DURATION_TIME_MILLIS,
                        12000
                );
                intent.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_SWITCH_MAX_SWITCHES,
                        2
                );
            }
        }
        return intent;
    }

    private Intent singleLanguageIntent(String tag) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 7);
        return intent;
    }

    private ArrayList<String> recognitionBiases() {
        ArrayList<String> result = new ArrayList<>();

        for (AppLanguage language : new AppLanguage[]{firstLanguage, secondLanguage}) {
            if (language == null) continue;

            switch (language.code) {
                case "ko":
                    result.add("안녕하세요");
                    result.add("감사합니다");
                    result.add("잠시만요");
                    result.add("괜찮아요");
                    break;
                case "zh":
                    result.add("你好");
                    result.add("谢谢");
                    result.add("再见");
                    result.add("中国");
                    result.add("多少钱");
                    result.add("对不起");
                    break;
                case "ja":
                    result.add("こんにちは");
                    result.add("ありがとう");
                    result.add("すみません");
                    result.add("さようなら");
                    break;
                case "en":
                    result.add("Hello");
                    result.add("Good morning");
                    result.add("Thank you");
                    result.add("Excuse me");
                    break;
                case "th":
                    result.add("สวัสดีครับ");
                    result.add("สวัสดีค่ะ");
                    result.add("ขอบคุณ");
                    break;
                case "es":
                    result.add("Hola");
                    result.add("Gracias");
                    result.add("Buenos días");
                    break;
                case "vi":
                    result.add("Xin chào");
                    result.add("Cảm ơn");
                    break;
                case "tl":
                    result.add("Kumusta");
                    result.add("Salamat");
                    break;
                default:
                    break;
            }
        }

        if ("ko".equals(firstLanguage.code) || "ko".equals(secondLanguage.code)) {
            for (String hint : EverydaySpeechHints.biasStrings(
                    recognitionCategory, recognitionScenario, recognitionUserHints)) {
                if (!result.contains(hint)) result.add(hint);
                if (result.size() >= 50) break;
            }
        }

        return result;
    }

    public void prepareSpeechModels() {
        onMain(() -> {
            if (destroyed || Build.VERSION.SDK_INT < 33 || !isAvailable()) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastModelRequest < 30000) return;
            lastModelRequest = now;
            final long epoch = generation;
            try {
                if (modelRecognizer == null) modelRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                modelRecognizer.checkRecognitionSupport(recognizerIntent(firstLanguage.speechTag),
                        context.getMainExecutor(), new RecognitionSupportCallback() {
                            @Override public void onSupportResult(RecognitionSupport support) {
                                if (destroyed || epoch != generation) return;
                                for (AppLanguage language : new AppLanguage[]{firstLanguage, secondLanguage}) {
                                    if (containsLanguage(support.getInstalledOnDeviceLanguages(), language.code)) {
                                        missingModels.remove(language.code);
                                    } else {
                                        if (containsLanguage(support.getPendingOnDeviceLanguages(), language.code)
                                                || containsLanguage(support.getSupportedOnDeviceLanguages(), language.code)) {
                                            missingModels.add(language.code);
                                        }
                                        requestModel(language, epoch);
                                    }
                                }
                                // v0.9: 모델 확인 콜백이 늦게 와도 현재 듣기를 취소하지 않는다.
                                // 필요한 모델은 백그라운드에서 요청하되 자동대화는 계속 유지한다.
                            }
                            @Override public void onError(int error) {
                                if (destroyed || epoch != generation) return;
                                // Unknown is not supported. Still request the models where possible.
                                requestModel(firstLanguage, epoch);
                                requestModel(secondLanguage, epoch);
                            }
                        });
            } catch (Exception e) {
                notifyError("음성모델 준비 상태를 확인할 수 없습니다. 인식 결과를 검증하며 듣습니다.");
            }
        });
    }

    private boolean containsLanguage(List<String> tags, String code) {
        for (String tag : tags) if (code.equals(LanguageDecisionEngine.code(tag))) return true;
        return false;
    }

    private void requestModel(AppLanguage language, long epoch) {
        if (modelRecognizer == null || !downloading.add(language.code)) return;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                modelRecognizer.triggerModelDownload(singleLanguageIntent(language.speechTag),
                        context.getMainExecutor(), new ModelDownloadListener() {
                            @Override public void onProgress(int progress) { }
                            @Override public void onSuccess() {
                                if (destroyed || epoch != generation) return;
                                downloading.remove(language.code);
                                missingModels.remove(language.code);
                                if (missingModels.isEmpty() && current == null) scheduleRestart(0);
                            }
                            @Override public void onScheduled() {
                                if (destroyed || epoch != generation) return;
                                downloading.remove(language.code);
                                notifyError(language.name + " 음성모델 다운로드가 예약되어 있습니다.");
                            }
                            @Override public void onError(int error) {
                                if (destroyed || epoch != generation) return;
                                downloading.remove(language.code);
                                notifyError(language.name + " 음성모델 준비를 확인해 주세요.");
                            }
                        });
            } else {
                modelRecognizer.triggerModelDownload(singleLanguageIntent(language.speechTag));
                downloading.remove(language.code);
            }
        } catch (Exception e) { downloading.remove(language.code); }
    }

    private String normalizeEcho(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s\\p{P}]+", "");
    }

    private boolean isPlaybackEcho(String text) {
        // Short exact guard; never erase a sentence merely containing old translated words.
        return playbackText != null && playbackText.length() >= 3
                && SystemClock.elapsedRealtime() - playbackEndedAt < 1200L
                && playbackText.equals(normalizeEcho(text));
    }

    private void releaseModelRecognizer() {
        if (modelRecognizer != null) { modelRecognizer.destroy(); modelRecognizer = null; }
    }

    public void destroy() {
        onMain(() -> {
            stopInternal();
            destroyed = true;
            releaseModelRecognizer();
            languageIdentifier.close();
        });
    }
}
