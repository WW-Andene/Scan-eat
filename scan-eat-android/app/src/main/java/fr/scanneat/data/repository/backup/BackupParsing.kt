package fr.scanneat.data.repository.backup

import fr.scanneat.data.backup.BACKUP_FORMAT_VERSION
import fr.scanneat.data.backup.BackupBundle
import fr.scanneat.data.backup.BackupImportError

// ============================================================================
// BACKUP JSON PARSING/VALIDATION — extracted verbatim out of
// BackupRepository.kt, the cohesive "decode + version-check a backup file"
// concern shared by importFromJson() and peekMetadata(). Extension function
// on BackupRepository since it needs [BackupRepository.bundleAdapter], widened
// from private to internal for this file to reach - only called internally
// (same package), no external caller changes.
// ============================================================================

internal fun BackupRepository.parseBundle(json: String): BackupBundle {
    val bundle = try {
        bundleAdapter.fromJson(json)
    } catch (e: Exception) {
        throw BackupImportError.Malformed(e)
    } ?: throw BackupImportError.Malformed(IllegalArgumentException("empty JSON body"))
    if (bundle.formatVersion > BACKUP_FORMAT_VERSION) {
        throw BackupImportError.UnsupportedVersion(bundle.formatVersion, BACKUP_FORMAT_VERSION)
    }
    return bundle
}
