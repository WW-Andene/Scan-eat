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

/**
 * [passphrase] is required (and used to decrypt first) when [json] is actually
 * a BackupPassphraseCipher-encrypted envelope, not raw JSON - see that
 * object's own doc comment for the file format and why a Keystore key can't
 * be used here instead. A plain (unencrypted) export is unaffected: it
 * doesn't match the encrypted prefix, so this parses it exactly as before.
 */
internal fun BackupRepository.parseBundle(json: String, passphrase: String? = null): BackupBundle {
    val plainJson = if (BackupPassphraseCipher.isEncrypted(json)) {
        if (passphrase.isNullOrEmpty()) throw BackupImportError.PassphraseRequired
        BackupPassphraseCipher.decryptOrNull(json, passphrase) ?: throw BackupImportError.WrongPassphrase
    } else json
    val bundle = try {
        bundleAdapter.fromJson(plainJson)
    } catch (e: Exception) {
        throw BackupImportError.Malformed(e)
    } ?: throw BackupImportError.Malformed(IllegalArgumentException("empty JSON body"))
    if (bundle.formatVersion > BACKUP_FORMAT_VERSION) {
        throw BackupImportError.UnsupportedVersion(bundle.formatVersion, BACKUP_FORMAT_VERSION)
    }
    return bundle
}
