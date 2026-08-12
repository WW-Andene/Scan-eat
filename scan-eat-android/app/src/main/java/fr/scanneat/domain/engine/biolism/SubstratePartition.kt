package fr.scanneat.domain.engine.biolism

// ─────────────────────────────────────────────────────────────────────────
// SUBSTRATE PARTITION — Frayn 1983, Cahill 1970 ("Starvation in Man"),
// Owen et al. 1967 (J Clin Invest 46:1589), Keys 1950
// 3-pool model: fat / carb / protein, protein fraction varies by phase.
//
// Pass-2 context/logic audit finding: this previously RAMPED UP to a peak
// at 96h (0.220) before declining — backwards relative to Cahill/Owen, who
// both document protein catabolism/gluconeogenesis as HIGHEST in the first
// ~24-48h (post-absorptive, before ketone adaptation matures) and then
// DECLINING as ketone bodies increasingly substitute for glucose as brain
// fuel, sparing protein. Owen's own urinary-nitrogen data: ~11-12 g N/day
// at day 1, falling to ~3-4 g N/day by day 5-6, and down to ~1 g N/day
// only after 5-6 weeks of adaptation. The peak is now placed at 24h (early
// post-absorptive/glycogen-depletion GNG surge) and declines monotonically
// from there, matching that shape; the previous tail (values rising again
// past 1440h) was also physiologically backwards and is now a flat floor
// instead. Magnitudes at each breakpoint are unchanged from the prior
// curve (same energy-contribution order); only the ordering/shape and the
// tail were corrected — retuning to exactly match Owen's N-excretion
// ratios is a separate, larger modeling decision this fix does not attempt.
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeSubstrates(npRQ: Double, ketoHours: Double): SubstrateResult {
    val kh = ketoHours.coerceAtLeast(0.0)

    val protFrac = when {
        kh <= 0.0    -> 0.170
        kh <= 24.0   -> 0.170 + 0.050 * (kh / 24.0)               // rises to the peak (0.220) as glycogen depletes and GNG surges
        kh <= 96.0   -> 0.220 - 0.040 * ((kh - 24.0) / 72.0)      // falls as ketone adaptation ramps up and spares protein (day 1 → day 4)
        kh <= 168.0  -> 0.180 - 0.030 * ((kh - 96.0) / 72.0)      // day 4 → day 7
        kh <= 504.0  -> 0.150 - 0.030 * ((kh - 168.0) / 336.0)    // day 7 → ~3 weeks
        kh <= 1440.0 -> 0.120 - 0.030 * ((kh - 504.0) / 936.0)    // ~3 weeks → 2 months
        else         -> 0.090                                     // long-term floor; Owen's extended-fast subjects continued to decline, never rose again
    }

    val npFrac   = 1.0 - protFrac
    val fatNP    = ((1.0 - npRQ) / 0.30).coerceIn(0.0, 1.0)
    val carbNP   = 1.0 - fatNP
    val fatFrac  = fatNP  * npFrac
    val carbFrac = carbNP * npFrac
    val rq       = fatFrac * 0.70 + carbFrac * 1.00 + protFrac * 0.81
    val oxycal   = fatFrac * 4.686 + carbFrac * 5.047 + protFrac * 4.485

    return SubstrateResult(
        fatFrac   = fatFrac,
        carbFrac  = carbFrac,
        protFrac  = protFrac,
        rq        = rq,
        npRq      = npRQ,
        oxycaloric = oxycal,
    )
}
