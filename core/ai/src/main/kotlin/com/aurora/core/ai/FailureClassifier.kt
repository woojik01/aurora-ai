package com.aurora.core.ai

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Failure classification for retry/fallback policy (PRD-03).
 * Only idempotent operations may be retried, and only with bounded backoff —
 * the classifier itself never triggers retries.
 */
object FailureClassifier {

    fun classify(error: Throwable?): FailureType = when (error) {
        null -> FailureType.UNKNOWN
        is kotlinx.coroutines.CancellationException -> FailureType.CANCELLED
        is java.util.concurrent.TimeoutException -> FailureType.TIMEOUT
        is SocketTimeoutException -> FailureType.TIMEOUT
        is UnknownHostException -> FailureType.NETWORK
        is IOException -> FailureType.NETWORK
        is SecurityException -> FailureType.AUTHENTICATION
        is ProviderException -> error.failureType
        else -> FailureType.UNKNOWN
    }
}

/** Provider-thrown exception carrying a classified failure type and no secrets. */
class ProviderException(
    val failureType: FailureType,
    message: String,
) : RuntimeException(message)
