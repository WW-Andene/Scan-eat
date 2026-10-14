package fr.scanneat.data

import fr.scanneat.data.repository.profileshare.ProfileShareCodec
import fr.scanneat.domain.model.AllergenSeverity
import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

/** Roadmap N8. */
class ProfileShareCodecTest {
    @Test fun `round trip with the right code only`() {
        val p = Profile(name = "Léa", allergens = setOf("peanuts"), allergenSeverities = mapOf("peanuts" to AllergenSeverity.SEVERE))
        val code = ProfileShareCodec.newCode()
        val qr = ProfileShareCodec.seal(ProfileShareCodec.payloadOf(p), code)
        assertTrue(ProfileShareCodec.isShareQr(qr))
        assertNull(ProfileShareCodec.open(qr, "WRONGCOD"))
        val got = ProfileShareCodec.toProfile(ProfileShareCodec.open(qr, code.lowercase())!!, "id", "partagé le")
        assertEquals(setOf("peanuts"), got.allergens)
        assertEquals(AllergenSeverity.SEVERE, got.allergenSeverities["peanuts"])
        assertTrue(got.name.startsWith("Léa"))
    }
}
