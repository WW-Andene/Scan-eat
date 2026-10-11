package fr.scanneat.data.local.prefs

import android.os.Build
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

    private fun baseSpecBuilder(): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        // StrongBox (a separate secure-element chip, where present) is strictly
        // stronger than the TEE-only default — opportunistic, not required.
        // setIsStrongBoxBacked() itself is API 28+ (this app's minSdk is 26),
        // so it's only ever called behind this version check - that guards the
        // hard NewApi lint error from calling a method that doesn't exist in the
        // API 26/27 framework jar at all. The runtime StrongBoxUnavailableException
        // a device can still throw even on API 28+ (declares the feature but
        // rejects this spec, or has no real StrongBox chip at all) is handled by
        // the try/catch below.
        //
        // Each attempt below calls baseSpecBuilder() to get its OWN fresh Builder:
        // KeyGenParameterSpec.Builder mutates itself in place and returns `this`
        // from every setter, so a single shared builder that had
        // .setIsStrongBoxBacked(true) called on it stays permanently flagged for
        // every later .build() call from that same instance - a previous version
        // of this fallback reused one builder for both the StrongBox attempt and
        // the "retry without StrongBox" attempt, so the retry silently rebuilt an
        // identical StrongBox-required spec and threw the exact same
        // StrongBoxUnavailableException, this time uncaught, crashing every save
        // on a device whose StrongBox chip rejects this spec (or has none at all).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(baseSpecBuilder().setIsStrongBoxBacked(true).build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                // Fall through to the non-StrongBox attempt below, fresh builder.
            }
        }
        keyGenerator.init(baseSpecBuilder().build())
        return keyGenerator.generateKey()
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
