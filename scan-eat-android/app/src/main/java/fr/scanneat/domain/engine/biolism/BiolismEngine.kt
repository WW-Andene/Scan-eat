package fr.scanneat.domain.engine.biolism

// ============================================================================
// BIOLISM ENGINE — pure Kotlin port of all compute functions from App.jsx
// All source citations preserved. No I/O, no Android dependencies — pure math.
//
// Namespace only — each compute function lives in its own file as an
// extension function on this object (RQCurve.kt, SubstratePartition.kt,
// OrganHeatDistribution.kt, MetabolicsCalculator.kt, HormoneEstimator.kt,
// WaterAndGlucoseEstimator.kt, KetoPhaseCalculator.kt), shared rounding
// helper in BiolismMathHelpers.kt. Kotlin resolves `BiolismEngine.foo(...)`
// identically whether foo is a true member or a same-package extension
// function, so every existing call site (BiolismEngine.computeMetabolics(),
// BiolismEngine.computeKetoRQ(), etc.) is unaffected. Was previously a
// single 619-line file with all of the above inline as object members.
// ============================================================================
object BiolismEngine
