package fr.scanneat.domain.engine.biolism

import kotlin.math.exp
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────
// ORGAN HEAT REDISTRIBUTION — Elia 1992 baseline, Cahill 2006 adaptation
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeOrganPcts(ketoHours: Double, adaptBlend: Double): List<OrganPct> {
    val kh = ketoHours.coerceAtLeast(0.0)
    val t  = min(1.0, kh / 72.0)

    // Acute targets (72h)
    val brainAcute   = 18.0 - t * 5.4   // → 12.6
    val liverAcute   = 26.0 + t * 4.0   // → 30.0
    val muscleAcute  = 22.0 + t * 0.6   // → 22.6
    val kidneysAcute =  9.0 + t * 0.4   // → 9.4
    val heartAcute   =  9.0 + t * 0.4   // → 9.4

    // Long-term adapted targets 21d+ (Cahill 2006)
    var brainAdapt   = 11.0
    var liverAdapt   = 32.0
    var muscleAdapt  = 23.0
    val kidneysAdapt =  9.8
    val heartAdapt   =  9.6

    // Extended starvation 60d+ — muscle rises as lean mass catabolises (Keys 1950)
    if (kh > 1440.0) {
        val ex = kh - 1440.0
        val catFrac = min(0.4, 1.0 - exp(-ex / 720.0))
        muscleAdapt = 23.0 + catFrac * 4.0
        brainAdapt  = 11.0 + catFrac * 1.5
        liverAdapt  = 32.0 - catFrac * 2.0
    }

    val blend   = adaptBlend.coerceIn(0.0, 1.0)
    val brain   = brainAcute   + blend * (brainAdapt   - brainAcute)
    val liver   = liverAcute   + blend * (liverAdapt   - liverAcute)
    val muscle  = muscleAcute  + blend * (muscleAdapt  - muscleAcute)
    val kidneys = kidneysAcute + blend * (kidneysAdapt - kidneysAcute)
    val heart   = heartAcute   + blend * (heartAdapt   - heartAcute)
    val residual = 100.0 - brain - liver - muscle - kidneys - heart

    return listOf(
        OrganPct("Liver",           liver.roundTo(1),   "Gold"),
        OrganPct("Skeletal Muscle", muscle.roundTo(1),  "Teal"),
        OrganPct("Brain",           brain.roundTo(1),   "Violet"),
        OrganPct("Residual",        residual.roundTo(1),"IconInactive"),
        OrganPct("Kidneys",         kidneys.roundTo(1), "Teal"),
        OrganPct("Heart",           heart.roundTo(1),   "Gold"),
    )
}
