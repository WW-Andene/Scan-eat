package fr.scanneat.data.local.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ============================================================================
// SECURE FIELD CIPHER — Keystore-backed AES-256/GCM for sensitive values
// stored in UserPreferences (the Groq/Cerebras API keys, and the user's
// allergens/health-conditions profile fields). Unencrypted, any of these sat
// in plaintext in the app's preferences file — exposed verbatim in any
// device backup, on rooted devices, or via adb backup on debuggable builds,
// and for allergens/conditions specifically, that's real medical data
// (diabetes, pregnancy, kidney disease, allergies), not just a credential.
//
// The AES key itself never leaves the AndroidKeyStore (non-exportable,
// StrongBox-backed where the device supports it, hardware-backed
// TEE-only otherwise) — only the IV + ciphertext is ever persisted to disk.
// One key alias, shared across all fields using this cipher: they're all
// equally sensitive app data protected by the same device-bound secret,
// so there's no benefit to per-field keys, only more Keystore bookkeeping.
// ============================================================================

internal object SecureFieldCipher {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "scanneat_api_key_v1" // unchanged from before the rename — re-keying would strand every already-encrypted value
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val specBuilder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        // StrongBox (a separate secure-element chip, where present) is strictly
        // stronger than the TEE-only default — opportunistic, not required:
        // most devices don't have one, and KeyGenParameterSpec throws
        // StrongBoxUnavailableException rather than silently downgrading, so
        // the fallback below is what actually lets this run everywhere.
        val spec = runCatching {
            specBuilder.setIsStrongBoxBacked(true).build()
        }.getOrElse { specBuilder.build() }
        return try {
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            // StrongBoxUnavailableException on devices that report the feature
            // but reject this particular spec — retry once without it.
            keyGenerator.init(specBuilder.build())
            keyGenerator.generateKey()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        try {
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Keystore invalidated the key (e.g. biometric enrollment changed,
            // or an OS security-patch reset it) — the ciphertext it protected
            // is unrecoverable either way, so drop the dead alias and mint a
            // fresh key rather than crashing every caller from here on.
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: UnrecoverableKeyException) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
        return generateKey()
    }

    /** Encrypts [plaintext], returning Base64(iv || ciphertext || GCM tag). */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value produced by [encrypt]. Returns null when [encoded]
     * wasn't produced by this cipher — the expected case right after this
     * feature ships, when a value stored before encryption existed is still
     * plain text (not valid Base64(iv+ciphertext), or GCM's authentication
     * tag fails to verify) — the caller falls back to treating it as legacy
     * plaintext and re-encrypts it in place.
     */
    fun decryptOrNull(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_LENGTH_BYTES) return null
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()
}
