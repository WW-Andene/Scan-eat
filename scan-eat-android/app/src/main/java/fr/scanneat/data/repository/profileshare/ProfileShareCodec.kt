package fr.scanneat.data.repository.profileshare

import fr.scanneat.data.repository.backup.BackupPassphraseCipher
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.AllergenSeverity
import fr.scanneat.domain.model.Profile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/** What a carer receives (Roadmap N8): allergens, their severity and the diet - nothing else. */
@Serializable
data class SharedProfilePayload(
    val v: Int = 1,
    val name: String,
    val allergens: List<String>,
    val severities: Map<String, String> = emptyMap(),
    val diet: String,
    val sharedOn: String,
)

/**
 * Roadmap N8: share a profile's allergens/diet through a QR code, without an
 * account or a server. The JSON is encrypted with the same PBKDF2 + AES-GCM
 * cipher as backups, keyed by a one-off 8-character code shown next to the QR
 * (both are needed: a photo of the QR alone reveals nothing).
 */
object ProfileShareCodec {
    const val PREFIX = "SCANEAT-PROFILE:1:"
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O/1/I/L to misread
    private val json = Json { ignoreUnknownKeys = true }

    fun newCode(random: SecureRandom = SecureRandom()): String = (1..8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    fun payloadOf(profile: Profile, today: java.time.LocalDate = java.time.LocalDate.now()) = SharedProfilePayload(
        name = profile.name, allergens = profile.allergens.sorted(),
        severities = profile.allergenSeverities.filterKeys { it in profile.allergens }.mapValues { it.value.name },
        diet = profile.diet.key, sharedOn = today.toString(),
    )

    fun seal(payload: SharedProfilePayload, code: String): String =
        PREFIX + BackupPassphraseCipher.encrypt(json.encodeToString(SharedProfilePayload.serializer(), payload), normalize(code))

    fun isShareQr(raw: String) = raw.startsWith(PREFIX)

    /** Null when the code is wrong or the QR is not a Scan'eat profile. */
    fun open(raw: String, code: String): SharedProfilePayload? {
        if (!isShareQr(raw)) return null
        val plain = BackupPassphraseCipher.decryptOrNull(raw.removePrefix(PREFIX), normalize(code)) ?: return null
        return runCatching { json.decodeFromString(SharedProfilePayload.serializer(), plain) }.getOrNull()
    }

    /** New local profile from a received payload; unknown allergen/diet keys are dropped. */
    fun toProfile(p: SharedProfilePayload, id: String, receivedLabel: String): Profile = Profile(
        id = id,
        name = "${p.name.ifBlank { "?" }} ($receivedLabel ${p.sharedOn})",
        allergens = p.allergens.toSet(),
        allergenSeverities = p.severities.mapNotNull { (k, v) -> AllergenSeverity.entries.firstOrNull { it.name == v }?.let { k to it } }.toMap(),
        diet = DietKey.entries.firstOrNull { it.key == p.diet } ?: DietKey.NONE,
        sharedReadOnly = true,
    )

    private fun normalize(code: String) = code.trim().uppercase().replace(" ", "")
}
