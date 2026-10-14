package fr.scanneat.data.repository.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.scanneat.data.local.prefs.SecureFieldCipher
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Roadmap C12: the app is 100% local and `allowBackup` is off, so a lost or
 * reset phone lost everything unless the user had exported by hand. When
 * enabled, an encrypted backup (same format as the manual export, restorable
 * from Settings) is written every week into a folder the user picked (Storage
 * Access Framework - e.g. a cloud-synced folder or an SD card). The passphrase
 * is kept encrypted with the device Keystore so the worker can run unattended;
 * the user needs to remember it to restore on another phone.
 */
object AutoBackupSettings {
    private const val PREFS = "auto_backup"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_PASS = "passphrase_enc"
    private const val KEY_LAST_OK = "last_success_ms"
    private const val KEY_LAST_FAILED = "last_failed"
    const val WORK_NAME = "auto_backup_weekly"
    const val KEEP_FILES = 4
    const val FILE_PREFIX = "scaneat-auto-"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun treeUri(c: Context): Uri? = prefs(c).getString(KEY_TREE, null)?.let(Uri::parse)
    fun passphrase(c: Context): String? = prefs(c).getString(KEY_PASS, null)?.let { SecureFieldCipher.decryptOrNull(it) }
    fun isEnabled(c: Context) = treeUri(c) != null && passphrase(c) != null
    fun lastSuccessMs(c: Context): Long = prefs(c).getLong(KEY_LAST_OK, 0L)
    fun lastFailed(c: Context): Boolean = prefs(c).getBoolean(KEY_LAST_FAILED, false)

    fun enable(c: Context, tree: Uri, passphrase: String) {
        prefs(c).edit().putString(KEY_TREE, tree.toString()).putString(KEY_PASS, SecureFieldCipher.encrypt(passphrase)).apply()
        val wm = WorkManager.getInstance(c)
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
        wm.enqueueUniqueWork("$WORK_NAME-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    fun disable(c: Context) {
        prefs(c).edit().remove(KEY_TREE).remove(KEY_PASS).apply()
        WorkManager.getInstance(c).cancelUniqueWork(WORK_NAME)
    }

    internal fun recordResult(c: Context, ok: Boolean) {
        prefs(c).edit().apply { if (ok) putLong(KEY_LAST_OK, System.currentTimeMillis()) }.putBoolean(KEY_LAST_FAILED, !ok).apply()
    }

    /** Backup files to delete so only the [keep] most recent remain (names sort by date). */
    fun filesToPrune(names: List<String>, keep: Int = KEEP_FILES): List<String> =
        names.filter { it.startsWith(FILE_PREFIX) }.sortedDescending().drop(keep)
}

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val tree = AutoBackupSettings.treeUri(ctx) ?: return Result.success()
        val pass = AutoBackupSettings.passphrase(ctx) ?: return Result.success()
        return try {
            val json = backupRepository.exportToJson(pass)
            val resolver = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "${AutoBackupSettings.FILE_PREFIX}${LocalDate.now()}-${System.currentTimeMillis() % 100000}.json"
            val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: error("createDocument failed")
            resolver.openOutputStream(doc, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: error("no output stream")
            prune(tree)
            AutoBackupSettings.recordResult(ctx, true)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AutoBackupSettings.recordResult(ctx, false)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun prune(tree: Uri) {
        val resolver = applicationContext.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableMapOf<String, String>() // name -> documentId
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) found[c.getString(1)] = c.getString(0)
        }
        AutoBackupSettings.filesToPrune(found.keys.toList()).forEach { n ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, found.getValue(n))) }
        }
    }
}
