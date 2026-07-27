package fr.scanneat.data.repository.scan

import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with jitter, replacing the old fixed `400L * (attempt + 1)`
 * linear delay used by every retry loop in this file (fetchOffProduct's lookup,
 * scoreViaServer, identifyViaServer) - same rough magnitude (~400ms then ~800ms
 * across this file's 3-attempt budget) but avoids concurrent clients retrying in
 * lockstep against a momentarily-overloaded OFF/server endpoint.
 *
 * Shared by ScanRepository (persist/cache paths), ScanServerClient, and
 * ScanOffLookup - kept in its own file since all three need the identical
 * backoff shape and none of them should diverge from it independently.
 */
internal fun backoffDelayMs(attempt: Int, baseDelayMs: Long = 400L, jitterMs: Long = 200L): Long =
    (baseDelayMs * 2.0.pow(attempt)).toLong() + Random.nextLong(0, jitterMs)
