package fr.scanneat

// ============================================================================
// FEATURE FLAGS — a single seam for gating a risky change (a scoring-engine
// revision, an AI-provider behavior change, a new screen) to a subset of
// installs or killing it in the field, without waiting on a Play Store
// release. Nothing in the app currently has this lever - every change ships
// atomically to 100% of users the instant a release rolls out.
//
// Deliberately compiled-in constants for now, not a server round-trip or a
// new dependency - the point is the seam. Swapping this object's body for a
// DataStore-backed read (mirroring UserPreferences) or a remote-fetched value
// later requires no changes at any call site.
// ============================================================================
object FeatureFlags {
    /** Placeholder gate for the next scoring-engine revision's staged rollout. */
    val NEW_SCORING_ENGINE_V2: Boolean = false
}
