package fr.scanneat.data.repository.backup

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ============================================================================
// PASSPHRASE-BASED BACKUP ENCRYPTION — optional, user-provided passphrase to
// encrypt the exported JSON file itself.
//
// Unlike SecureFieldCipher (Keystore-backed, device-bound, non-exportable),
// a backup file is explicitly meant to leave the device - restoring "on this
// device or another" is the whole point (see BackupRepository's own doc
// comment). A Keystore key can never work here since it can't be exported to
// the new device. Instead: a passphrase the user chooses and remembers,
// stretched via PBKDF2 into an AES-256 key, with a fresh random salt per
// export so the same passphrase never derives the same key twice.
//
// Encrypted file format (plain text, so a JSON parser sees it and fails
// cleanly rather than crashing on binary garbage):
//   SCANEAT-ENC-V1:<base64 salt>:<base64 iv+ciphertext+tag>
// The prefix lets importFromJson/peekMetadata auto-detect an encrypted file
// and prompt for a passphrase instead of failing with an opaque parse error;
// a plain (unencrypted) export - the default when no passphrase is set -
// still starts with "{" and parses exactly as before, so this is fully
// backward compatible with every existing backup file.
// ============================================================================

internal object BackupPassphraseCipher {
    private const val PREFIX = "SCANEAT-ENC-V1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private const val PBKDF2_ITERATIONS = 150_000
    private const val KEY_LENGTH_BITS = 256

    fun isEncrypted(content: String): Boolean = content.startsWith(PREFIX)

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Encrypts [plaintext] (the exported backup JSON) with [passphrase]. */
    fun encrypt(plaintext: String, passphrase: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val bodyB64 = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        return "$PREFIX$saltB64:$bodyB64"
    }

    /**
     * Decrypts a value produced by [encrypt]. Returns null on a wrong passphrase
     * (GCM's authentication tag fails to verify) or a malformed envelope - the
     * caller surfaces both identically as "wrong passphrase or corrupted file"
     * since there's no way to distinguish them from here.
     */
    fun decryptOrNull(content: String, passphrase: String): String? = runCatching {
        require(content.startsWith(PREFIX))
        val rest = content.removePrefix(PREFIX)
        val parts = rest.split(":", limit = 2)
        require(parts.size == 2)
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val combined = Base64.decode(parts[1], Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH_BYTES)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()
}
