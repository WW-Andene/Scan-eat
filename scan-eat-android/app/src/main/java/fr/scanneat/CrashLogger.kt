package fr.scanneat

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// CRASH LOGGER — no crash-reporting service exists anywhere in this app
// (confirmed: no Firebase/Crashlytics/Sentry dependency in libs.versions.toml).
// Adding one requires an external project + google-services.json this audit
// pass can't fabricate, so this is the safe, dependency-free floor: persist
// uncaught exceptions to a local file so a crash in the field leaves a trace
// a user can find (e.g. to attach to a bug report) instead of vanishing the
// moment the process dies. Always chains to the previous handler so the
// system's own crash dialog/process-death behavior is unaffected.
// ============================================================================

object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_ENTRIES = 5

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { appendCrash(appContext, thread, throwable) }
                .onFailure { Log.e("CrashLogger", "Failed to persist crash log", it) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun appendCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = formatWithoutMessages(throwable)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "=== $timestamp — thread ${thread.name} ===\n$stackTrace\n"

        val file = File(context.filesDir, FILE_NAME)
        val existingEntries = if (file.exists()) file.readText().split("=== ").filter { it.isNotBlank() } else emptyList()
        val newEntries = (existingEntries + entry.removePrefix("=== ")).takeLast(MAX_ENTRIES)
        file.writeText(newEntries.joinToString("") { "=== $it" })
    }

    /**
     * Renders the exception chain (class names + stack frames only) without
     * any exception .message. Several exception paths in this app build their
     * message from real user data - e.g. Moshi's JsonDataException on a
     * malformed backup-file field routinely embeds the offending value itself
     * (a food name, a weight, a note) - and this handler is a global catch-all
     * with no allowlist, so persisting messages risks writing that data to a
     * plaintext file for any future uncaught exception, not just known ones.
     */
    private fun formatWithoutMessages(throwable: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = throwable
        var isCause = false
        while (current != null) {
            sb.append(if (isCause) "Caused by: " else "").append(current.javaClass.name).append('\n')
            current.stackTrace.forEach { sb.append("\tat ").append(it).append('\n') }
            current = current.cause?.takeIf { it !== current }
            isCause = true
        }
        return sb.toString()
    }
}
