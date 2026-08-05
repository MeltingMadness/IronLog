package com.ironlog.app.domain.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "IronLog"

/**
 * Wraps a suspend block in try-catch and returns a Result.
 * Logs any exceptions before returning them as Result.failure.
 */
suspend fun <T> safeCall(tag: String = TAG, block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Exception) {
        AppLogger.e(tag, "Fehler: ${e.message}", e)
        Result.failure(e)
    }

/**
 * Extension for Flow that catches exceptions and logs them.
 * Emits nothing on error (the flow continues without crashing).
 */
fun <T> Flow<T>.catchAndLog(tag: String = TAG): Flow<T> =
    catch { e ->
        AppLogger.e(tag, "Flow-Fehler: ${e.message}", e)
    }
