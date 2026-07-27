package fr.scanneat.data.repository.backup

import fr.scanneat.data.local.db.medication.MedicationEntity
import fr.scanneat.data.local.db.medication.MedicationLogEntity
import fr.scanneat.data.local.prefs.SecureFieldCipher

// ============================================================================
// MEDICATION FIELD ENCRYPTION FOR BACKUP — extracted verbatim out of
// BackupRepository.kt, the cohesive "plaintext <-> Keystore-encrypted" concern
// for the two entities whose fields are encrypted at rest. Pure extension
// functions on MedicationEntity/MedicationLogEntity - no repository state
// needed (SecureFieldCipher is a static/top-level facility), so this is a
// plain internal top-level move, only ever called from BackupRepository
// (same package).
// ============================================================================

// MedicationEntity.name/dosage/scheduleNote and MedicationLogEntity.
// medicationName are Keystore-encrypted at rest (MedicationRepository) -
// that encryption key is device-bound and non-exportable, so a raw
// ciphertext value written into a backup file would be permanently
// undecryptable after restoring on another device or a reinstall (a new
// Keystore key). The backup file must carry these fields as plaintext
// (matching how profile.allergens/healthConditions already reach this
// bundle already-decrypted, via UserPreferences.profile) and re-encrypt
// them going back in on import, exactly mirroring the live save/load path.
internal fun MedicationEntity.decryptedForBackup() = copy(
    name = SecureFieldCipher.decryptOrNull(name) ?: name,
    dosage = SecureFieldCipher.decryptOrNull(dosage) ?: dosage,
    scheduleNote = SecureFieldCipher.decryptOrNull(scheduleNote) ?: scheduleNote,
)

internal fun MedicationEntity.encryptedFromBackup() = copy(
    name = SecureFieldCipher.encrypt(name),
    dosage = SecureFieldCipher.encrypt(dosage),
    scheduleNote = SecureFieldCipher.encrypt(scheduleNote),
)

internal fun MedicationLogEntity.decryptedForBackup() = copy(
    medicationName = SecureFieldCipher.decryptOrNull(medicationName) ?: medicationName,
)

internal fun MedicationLogEntity.encryptedFromBackup() = copy(
    medicationName = SecureFieldCipher.encrypt(medicationName),
)
