package fr.scanneat.util

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-requested: read a fresh scan result's score aloud (hands-busy - driving,
 * cooking - or low-vision use), alongside (not replacing) TalkBack's own
 * generic content-description reading, which only fires if TalkBack itself is
 * on and doesn't summarize "score + grade" as one deliberate sentence the way
 * this does. Gated behind UserPreferences.voiceScoreAnnounce (opt-in, off by
 * default) - see ScanScreen's own call site for where this fires.
 *
 * A single TextToSpeech instance for the app's lifetime (Hilt singleton) -
 * TextToSpeech's own init callback is asynchronous and it's a genuinely heavy
 * object to construct (binds to the system TTS service), so this is created
 * once and reused rather than per-scan.
 */
@Singleton
class ScoreSpeechAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    // TextToSpeech(context, listener) is asynchronous - init() is safe to call
    // more than once (e.g. once per scan) since it's cheap once `tts` already
    // exists, but the very first call has to wait for onInit before speak()
    // can do anything (speak() below silently no-ops until `ready` flips).
    private fun ensureInit() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    /** [lang] is the app's own in-app language ("fr"/"en", UserPreferences.language),
     *  not the device locale - same reasoning every other lang-aware string in
     *  this app already follows (see LocalizedStrings.kt). Silently does nothing
     *  if the system has no TTS engine available or hasn't finished initializing
     *  yet - this is a convenience announcement, never something a flow depends on. */
    fun speak(text: String, lang: String) {
        ensureInit()
        val engine = tts ?: return
        if (!ready) return
        engine.language = if (lang == "en") Locale.US else Locale.FRENCH
        engine.stop()   // don't queue behind a previous scan's still-playing announcement
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scan_score_announce")
    }
}
