/**
 * Voice dictation for Quick Add — Web Speech API.
 *
 * Wires the mic button to the browser's SpeechRecognition, feeds the
 * transcript through parseVoiceQuickAdd, then fills the Quick Add form
 * fields. On Firefox / unsupported browsers (Fix #18), the mic button is
 * repurposed as a "⌨️ type instead" affordance that focuses the name field,
 * rather than being hidden silently with no explanation.
 *
 * ADR-0004 feature-folder pattern. Deps are injected so we don't hard-
 * depend on app.js globals.
 *
 * Deps shape:
 *   { t, currentLang, parseVoiceQuickAdd }
 */

export function initVoiceDictate({ t, currentLang, parseVoiceQuickAdd, logEvent }) {
  const SpeechRecognitionImpl =
    globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
  const btn = document.getElementById('qa-voice-btn');

  // Fix #18 — when the browser doesn't support SpeechRecognition, don't
  // just hide the button silently. Replace it with a small hint that
  // directs the user to the typed name field, which is always available.
  if (!SpeechRecognitionImpl) {
    if (btn) {
      // Repurpose the button as a "type instead" affordance so the user
      // knows there IS a way to add food manually (the name input below).
      btn.removeAttribute('hidden');
      btn.setAttribute('aria-label', t('voiceNotSupported'));
      btn.setAttribute('title', t('voiceNotSupported'));
      btn.dataset.state = 'unsupported';

      // Replace the mic icon with a keyboard icon so it's visually distinct.
      const iconEl = btn.querySelector('.icon');
      if (iconEl) iconEl.textContent = '⌨️';
      const labelEl = btn.querySelector('.label');
      if (labelEl) labelEl.textContent = t('voiceNotSupported');

      // Clicking focuses the name field so the user's hands go straight
      // to where they need to type — one tap, not zero communication.
      btn.addEventListener('click', () => {
        const nameInput = document.getElementById('qa-name');
        if (nameInput) nameInput.focus();
      });
    }
    return;
  }

  if (!btn) return;
  btn.removeAttribute('hidden');
  let recognizer = null;
  // Expose deps object to nested handlers.
  const deps = { t, currentLang, parseVoiceQuickAdd, logEvent };

  btn.addEventListener('click', () => {
    // Toggle: if already listening, stop.
    if (recognizer) { try { recognizer.stop(); } catch { /* ignore */ } return; }
    try {
      const rec = new SpeechRecognitionImpl();
      rec.lang = currentLang() === 'en' ? 'en-US' : 'fr-FR';
      rec.interimResults = false;
      rec.continuous = false;
      rec.maxAlternatives = 1;

      // R28.2: safe label update — don't assume .label exists.
      const setLabel = (text) => {
        const el = btn.querySelector('.label');
        if (el) el.textContent = text;
      };
      rec.onstart = () => {
        btn.dataset.state = 'listening';
        setLabel(t('voiceListening'));
      };
      rec.onresult = (ev) => {
        const transcript = Array.from(ev.results)
          .map((r) => r[0]?.transcript || '')
          .join(' ')
          .trim();
        if (!transcript) return;
        // Parse the transcript into field candidates and only overwrite
        // fields the parser actually recognized, so a partial utterance
        // doesn't wipe values the user already typed.
        const parsed = parseVoiceQuickAdd(transcript);
        const setField = (id, v) => {
          const el = document.getElementById(id);
          if (el && v != null) el.value = String(v);
        };
        setField('qa-name',    parsed.name);
        setField('qa-kcal',    parsed.kcal);
        setField('qa-protein', parsed.protein_g);
        setField('qa-carbs',   parsed.carbs_g);
        setField('qa-fat',     parsed.fat_g);
        // Fix #11 — extended parser now catches sat-fat / sugars /
        // salt / fiber. Populate whatever the user actually uttered.
        setField('qa-satfat',  parsed.sat_fat_g);
        setField('qa-sugars',  parsed.sugars_g);
        setField('qa-salt',    parsed.salt_g);
        setField('qa-fiber',   parsed.fiber_g);
        // Meal slot override — only when the user clearly said a meal
        // word ("petit-déjeuner", "dinner", "snack"). Stays on the
        // time-of-day default otherwise.
        if (parsed.meal) setField('qa-meal', parsed.meal);
      };
      // R28.1: surface the actual error so the user knows why the
      // mic did nothing. `not-allowed` = permission denied, the
      // common case; `no-speech` = user didn't speak in time.
      // Previously silent — users saw the listening state flicker,
      // nothing happen, and assumed the feature was broken.
      rec.onerror = (ev) => {
        const status = document.getElementById('qa-ai-status');
        if (status) {
          const msg = ev?.error === 'not-allowed'
            ? t('voiceMicDenied')
            : ev?.error === 'no-speech'
              ? t('voiceNoSpeech')
              : t('voiceFailed');
          status.textContent = msg;
          status.dataset.state = 'warn';
          status.hidden = false;
        }
        // Fix #30 — capture voice-dictate failures in telemetry so
        // the user (via Settings → telemetry) can debug why voice
        // isn't working.
        if (deps.logEvent) {
          try { deps.logEvent('voice_error', ev?.error || 'unknown'); } catch { /* noop */ }
        }
      };
      rec.onend = () => {
        delete btn.dataset.state;
        setLabel(t('voiceDictate'));
        recognizer = null;
      };
      recognizer = rec;
      rec.start();
    } catch { recognizer = null; }
  });
}
