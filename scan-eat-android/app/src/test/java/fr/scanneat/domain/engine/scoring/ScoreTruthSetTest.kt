package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.engine.nutrition.mapOffProduct
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Score-engine audit S15: veracity check against real Open Food Facts records
 * (see ScoreTruthSet.kt). Before the S1 rewrite of mapCategory, 167/284 of
 * these products were scored against the wrong category's thresholds.
 */
class ScoreTruthSetTest {

    @Test
    fun everyTruthProductIsScoredAgainstAnAcceptableCategory() {
        val wrong = SCORE_TRUTH_SET.mapNotNull { t ->
            // A record with no product name is rejected upstream (mapOffProduct = null).
            val p = mapOffProduct(t.off) ?: return@mapNotNull null
            if (p.category in t.acceptable) null else "${t.offCategory} / ${p.name}: ${p.category} ∉ ${t.acceptable}"
        }
        assertTrue("Mis-categorised:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun everyTruthProductGetsAScoreInRange() {
        for (t in SCORE_TRUTH_SET) {
            val p = mapOffProduct(t.off) ?: continue
            val a = scoreProduct(p, "fr")
            assertTrue("${p.name}: ${a.score}", a.score in 0..100)
        }
    }
}
