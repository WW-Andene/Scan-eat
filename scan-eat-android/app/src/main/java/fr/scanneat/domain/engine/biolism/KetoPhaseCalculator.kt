package fr.scanneat.domain.engine.biolism

import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────
// KETO PHASE INFO
// ─────────────────────────────────────────────────────────────────────────
// `lang` defaults to "fr" and isn't yet threaded from user prefs at the call
// site (TrackerViewModel) - same partial-wiring state as the other lang-aware
// scoring functions (DietChecker/PersonalScoreEngine/AllergenDetector) whose
// callers also don't source the live app language yet.
fun BiolismEngine.ketoPhaseInfo(ketoHours: Double, ketoAdapted: Boolean, lang: String = "en"): KetoPhaseInfo {
    val en = lang == "en"
    return when {
        ketoHours < 8 -> KetoPhaseInfo(
            KetoPhase.GLYCOGEN_DEPLETION, if (en) "Glycogen Depletion" else "Déplétion glycogénique",
            if (en) "Liver glycogen depleting · glucose still primary fuel · RQ easing" else "Glycogène hépatique en baisse · glucose encore carburant principal · QR en baisse",
            (ketoHours / 8.0) * 100, "Gold", "< 0.2 mM")
        ketoHours < 24 -> KetoPhaseInfo(
            KetoPhase.TRANSITION, if (en) "Transition" else "Transition",
            if (en) "Ketone bodies rising · lipolysis accelerating · RQ dropping" else "Corps cétoniques en hausse · lipolyse en accélération · QR en baisse",
            ((ketoHours - 8) / 16.0) * 100, "Warm", "0.2–0.5 mM")
        // Pass-2 context/logic audit finding: protein catabolism/GNG peak
        // right around glycogen depletion (~24h), per Cahill 1970/Owen 1967
        // (see SubstratePartition.kt's protFrac comment for the full
        // sourcing) - not later in DEEP_KETOSIS as this copy previously
        // claimed. Moved the "peak" language here, to the start of this
        // phase, and reworded DEEP_KETOSIS below to reflect the decline/
        // protein-sparing that's actually underway by 72-168h.
        ketoHours < 72 -> KetoPhaseInfo(
            KetoPhase.KETOSIS_ONSET, if (en) "Ketosis Onset" else "Début de cétose",
            if (en) "Blood ketones 0.5–1.5 mM · fat is primary fuel · protein catabolism near its peak, starting to decline as ketone adaptation progresses" else "Cétones sanguines 0,5–1,5 mM · les graisses deviennent le carburant principal · catabolisme protéique proche de son pic, en début de baisse avec l'adaptation cétonique",
            ((ketoHours - 24) / 48.0) * 100, "Teal", "0.5–1.5 mM")
        ketoHours < 168 -> KetoPhaseInfo(
            KetoPhase.DEEP_KETOSIS, if (en) "Deep Ketosis" else "Cétose profonde",
            if (en) "Blood ketones 1.5–3.0 mM · protein catabolism declining · ketone adaptation sparing protein" else "Cétones sanguines 1,5–3,0 mM · catabolisme protéique en baisse · adaptation cétonique épargnant les protéines",
            ((ketoHours - 72) / 96.0) * 100, "Violet", "1.5–3.0 mM")
        ketoHours < 504 -> KetoPhaseInfo(
            KetoPhase.PROLONGED_FAST, if (en) "Prolonged Fast" else "Jeûne prolongé",
            if (en) "Protein sparing activating · brain ketone uptake rising" else "Épargne protéique activée · captation cérébrale des cétones en hausse",
            ((ketoHours - 168) / 336.0) * 100, "Violet", "2.0–5.0 mM")
        ketoHours < 1440 -> KetoPhaseInfo(
            KetoPhase.KETO_ADAPTED, if (en) (if (ketoAdapted) "Fully Adapted" else "Keto-Adapted") else (if (ketoAdapted) "Totalement adapté" else "Adapté à la cétose"),
            if (en) "Brain ~70% ketone-fuelled · protein sparing near-maximal · RQ floor 0.710" else "Cerveau ~70% alimenté par les cétones · épargne protéique quasi maximale · QR plancher 0,710",
            min(100.0, ((ketoHours - 504) / 936.0) * 100), "Gold", "3.0–6.0 mM")
        // "rising again" removed - no Cahill/Owen source supports protein
        // catabolism rebounding this late; Keys 1950 (Minnesota Starvation
        // Experiment) documents accelerating tissue wasting as fat stores
        // become critically low, which is a fat-depletion/mortality-risk
        // finding, not evidence of protein catabolism itself reversing
        // course and climbing back up.
        else -> KetoPhaseInfo(
            KetoPhase.EXTENDED_STARVATION, if (en) "Extended Starvation" else "Jeûne prolongé extrême",
            if (en) "Fat stores critically depleted · protein sparing can no longer fully compensate (Keys 1950)" else "Réserves de graisse critiquement épuisées · l'épargne protéique ne peut plus compenser entièrement (Keys 1950)",
            min(100.0, ((ketoHours - 1440) / 1440.0) * 100), "Severe", "1.0–3.0 mM ↓")
    }
}
