/* A single button-authorized utterance. No automatic microphone restart. */
class SpeechSession {
  constructor(options) {
    this.options = options;
    this.closed = false;
    this.ended = false;
    this.stopping = false;
    this.results = [];
    this.deadline = 0;
  }
  start(Ctor, lang) {
    const r = this.recognition = new Ctor();
    r.lang = lang;
    r.continuous = true;
    // On Android request final results only. Keep PC live transcription.
    r.interimResults = !this.options.finalOnly;
    r.maxAlternatives = 1;
    // Do not download or probe language packs on the microphone-start path.
    r.onstart = () => { if (!this.closed) this.options.state('listening'); };
    r.onspeechstart = () => {
      if (this.closed || this.stopping) return;
      this.clearSilence();
      this.options.state('listening');
    };
    r.onspeechend = () => { if (this.results.length) this.arm(); };
    r.onresult = event => {
      if (this.closed) return;
      const before = JSON.stringify(this.results);
      // resultIndex identifies replacement slots, not text to append.
      // Keep unchanged final slots; replace interim slots and remove retracted ones.
      const start = Number.isInteger(event.resultIndex) ? event.resultIndex : 0;
      for (let i = 0; i < event.results.length; i++) {
        if (i < start && this.results[i]) continue;
        const row = event.results[i];
        this.results[i] = {
          text: (row[0]?.transcript || '').trim(), final: !!row.isFinal
        };
      }
      this.results.length = event.results.length;
      this.options.text(this.results.map(x => x.text).filter(Boolean).join(' '));
      // A replay of the same result must not extend the silence deadline.
      if (!this.stopping && (before !== JSON.stringify(this.results) || !this.deadline)) this.arm();
    };
    r.onerror = event => {
      if (this.closed) return;
      this.cancel();
      this.options.error(event.error);
    };
    r.onend = () => {
      if (this.closed) return;
      this.ended = true;
      if (this.stopping) this.finish();
      else if (!this.results.length) {
        this.cancel(); this.options.error('no-speech');
      } else if (!this.deadline) this.arm();
    };
    try { r.start(); } catch (error) { this.cancel(); this.options.error(error.name); }
  }
  arm() {
    if (this.closed || this.stopping) return;
    this.clearSilence();
    this.deadline = Date.now() + 3000;
    const tick = () => {
      const remaining = this.deadline - Date.now();
      if (remaining <= 0) { this.stopForFinal(); return; }
      this.options.state('waiting', Math.ceil(remaining / 1000));
      this.silenceTimer = setTimeout(tick, Math.min(250, remaining));
    };
    tick();
  }
  clearSilence() {
    clearTimeout(this.silenceTimer); this.deadline = 0;
  }
  stopForFinal() {
    if (this.closed || this.stopping) return;
    this.stopping = true;
    this.clearSilence();
    this.options.state('finalizing');
    if (this.ended) { this.finish(); return; }
    // stop() may asynchronously deliver a corrected final transcript before onend.
    this.finalTimer = setTimeout(() => this.finish(), 1200);
    try { this.recognition.stop(); } catch { this.finish(); }
  }
  finish() {
    if (this.closed) return;
    const text = this.results.map(x => x.text).filter(Boolean).join(' ');
    const complete = !!text && this.results.every(x => !x.text || x.final);
    this.cancel();
    if (!complete) this.options.review(text, 'unfinished');
    else if (SpeechSession.needsReview(this.results)) this.options.review(text, 'repetition');
    else this.options.done(text);
  }
  static needsReview(results) {
    const text = results.map(x => x.text).filter(Boolean).join(' ').normalize('NFC');
    // Recognition may itself return "내가내가내가 말한말한말한" in one slot.
    // Flag, but NEVER silently delete possibly intentional words or numbers.
    if (/([\p{L}\p{M}]{2,20})(?:\s*\1){2,}/u.test(text)) return true;
    const normalize = value => value.normalize('NFC').replace(/[\s\p{P}]/gu, '');
    // Also catch repeated multiword phrases even if spacing changes:
    // "그건 번역기 그건 번역기 그건 번역기".
    if (/([\p{L}\p{M}]{2,40})\1{2,}/u.test(normalize(text))) return true;
    const chunks = results.map(x => normalize(x.text)).filter(Boolean);
    for (let i = 1; i < chunks.length; i++) {
      const previous = chunks[i - 1], next = chunks[i];
      // Longer overlapping segments could be a cumulative engine result.
      // Ask for confirmation instead of guessing which occurrence was intended.
      for (let size = Math.min(previous.length, next.length); size >= 4; size--) {
        if (previous.slice(-size) === next.slice(0, size)) return true;
      }
    }
    return false;
  }
  cancel() {
    this.closed = true;
    this.clearSilence(); clearTimeout(this.finalTimer);
    if (this.recognition) {
      this.recognition.onresult = this.recognition.onend = this.recognition.onerror = null;
      this.recognition.onstart = this.recognition.onspeechstart = this.recognition.onspeechend = null;
      try { this.recognition.abort(); } catch {}
    }
  }
}
window.SpeechSession = SpeechSession;
SpeechSession.VERSION = '6.7';
