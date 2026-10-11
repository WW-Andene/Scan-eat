package fr.scanneat.data.repository.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPassphraseCipherTest {

    @Test
    fun `round trip with new V2 envelope`() {
        val enc = BackupPassphraseCipher.encrypt("""{"a":"é€"}""", "correct horse")
        assertTrue(enc.startsWith("SCANEAT-ENC-V2:"))
        assertTrue(BackupPassphraseCipher.isEncrypted(enc))
        assertEquals("""{"a":"é€"}""", BackupPassphraseCipher.decryptOrNull(enc, "correct horse"))
    }

    @Test
    fun `wrong passphrase and tampering are rejected`() {
        val enc = BackupPassphraseCipher.encrypt("secret", "pw1")
        assertNull(BackupPassphraseCipher.decryptOrNull(enc, "pw2"))
        val parts = enc.split(":")
        val body = Base64.getDecoder().decode(parts[2]).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(BackupPassphraseCipher.decryptOrNull("${parts[0]}:${parts[1]}:${Base64.getEncoder().encodeToString(body)}", "pw1"))
        assertNull(BackupPassphraseCipher.decryptOrNull("SCANEAT-ENC-V2:garbage", "pw1"))
        assertFalse(BackupPassphraseCipher.isEncrypted("{\"formatVersion\":1}"))
    }

    // Backward compatibility: a file written by the previous version (V1, 150 000 iterations).
    @Test
    fun `legacy V1 backups still decrypt`() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec("old pw".toCharArray(), salt, 150_000, 256)).encoded,
            "AES",
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val ct = cipher.doFinal("legacy".toByteArray())
        val v1 = "SCANEAT-ENC-V1:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(cipher.iv + ct)}"
        assertTrue(BackupPassphraseCipher.isEncrypted(v1))
        assertEquals("legacy", BackupPassphraseCipher.decryptOrNull(v1, "old pw"))
        assertNull(BackupPassphraseCipher.decryptOrNull(v1, "new pw"))
    }
}
