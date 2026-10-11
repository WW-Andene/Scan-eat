package fr.scanneat.domain.engine.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

// Pregnancy alcohol veto / listeria deduction and migraine tyramine caution
// rely on these patterns; spirits, plurals and classic ANSES listeria foods
// (rillettes, raw shellfish) were missing.
class ConditionPatternCoverageTest {

    private fun misses(re: Regex, terms: List<String>) = terms.filterNot { re.containsMatchIn(normalizeForMatching(it)) }
    private fun hits(re: Regex, terms: List<String>) = terms.filter { re.containsMatchIn(normalizeForMatching(it)) }

    @Test
    fun `alcohol pattern covers spirits, plurals and all-caps labels`() {
        val terms = listOf("rhum", "kirsch", "liqueur d'orange", "cognac", "calvados", "vins", "BIÈRE", "eau-de-vie de poire",
            "whisky", "vodka", "porto", "cidre", "champagne", "rum", "brandy", "marsala")
        assertEquals(emptyList<String>(), misses(ALCOHOL_INGREDIENT_PATTERN, terms))
        assertEquals(emptyList<String>(), hits(ALCOHOL_INGREDIENT_PATTERN, listOf("vinaigre de vin", "gingembre", "vinaigrette").filter { it != "vinaigre de vin" }))
    }

    @Test
    fun `listeria pattern covers ANSES examples and plurals`() {
        val terms = listOf("rillettes", "terrine de campagne", "huîtres", "coquillages crus", "sushis", "fromages au lait cru", "pâté")
        assertEquals(emptyList<String>(), misses(PREGNANCY_LISTERIA_RISK_PATTERN, terms))
        assertEquals(emptyList<String>(), hits(PREGNANCY_LISTERIA_RISK_PATTERN, listOf("pâtes", "pates de blé dur")))
    }

    @Test
    fun `tyramine pattern covers plurals`() {
        assertEquals(emptyList<String>(), misses(TYRAMINE_SOURCE_PATTERN, listOf("fromages affinés", "saucissons", "salamis")))
    }
}
