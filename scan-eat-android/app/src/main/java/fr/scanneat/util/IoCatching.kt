package fr.scanneat.util

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never swallows cancellation - a bare `runCatching`
 * wrapping a whole suspend function body also catches CancellationException,
 * so work the caller already navigated away from would be reported back as a
 * caught "failure" (Result.failure) instead of the coroutine actually
 * stopping, letting a caller's stale success/failure handling run anyway.
 */
inline fun <T> ioCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
