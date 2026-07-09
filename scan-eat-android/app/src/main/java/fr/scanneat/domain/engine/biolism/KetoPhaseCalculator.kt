package fr.scanneat.domain.engine.biolism

import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────
// KETO PHASE INFO
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.ketoPhaseInfo(ketoHours: Double, ketoAdapted: Boolean): KetoPhaseInfo {
    return when {
        ketoHours < 8 -> KetoPhaseInfo(
            KetoPhase.GLYCOGEN_DEPLETION, "Glycogen Depletion",
            "Liver glycogen depleting · glucose still primary fuel · RQ easing",
            (ketoHours / 8.0) * 100, "Gold", "< 0.2 mM")
        ketoHours < 24 -> KetoPhaseInfo(
            KetoPhase.TRANSITION, "Transition",
            "Ketone bodies rising · lipolysis accelerating · RQ dropping",
            ((ketoHours - 8) / 16.0) * 100, "Warm", "0.2–0.5 mM")
        ketoHours < 72 -> KetoPhaseInfo(
            KetoPhase.KETOSIS_ONSET, "Ketosis Onset",
            "Blood ketones 0.5–1.5 mM · fat is primary fuel",
            ((ketoHours - 24) / 48.0) * 100, "Teal", "0.5–1.5 mM")
        ketoHours < 168 -> KetoPhaseInfo(
            KetoPhase.DEEP_KETOSIS, "Deep Ketosis",
            "Blood ketones 1.5–3.0 mM · peak protein catabolism · GNG maximised",
            ((ketoHours - 72) / 96.0) * 100, "Violet", "1.5–3.0 mM")
        ketoHours < 504 -> KetoPhaseInfo(
            KetoPhase.PROLONGED_FAST, "Prolonged Fast",
            "Protein sparing activating · brain ketone uptake rising",
            ((ketoHours - 168) / 336.0) * 100, "Violet", "2.0–5.0 mM")
        ketoHours < 1440 -> KetoPhaseInfo(
            KetoPhase.KETO_ADAPTED, if (ketoAdapted) "Fully Adapted" else "Keto-Adapted",
            "Brain ~70% ketone-fuelled · protein sparing near-maximal · RQ floor 0.710",
            min(100.0, ((ketoHours - 504) / 936.0) * 100), "Gold", "3.0–6.0 mM")
        else -> KetoPhaseInfo(
            KetoPhase.EXTENDED_STARVATION, "Extended Starvation",
            "Fat stores depleting · protein catabolism rising again (Keys 1950)",
            min(100.0, ((ketoHours - 1440) / 1440.0) * 100), "Severe", "1.0–3.0 mM ↓")
    }
}
