package com.tools.maestro.core.error

import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Sealed class representing different error types in TOols.
 */
sealed class TOolsError(override val message: String? = null) : Exception(message) {
    data class NetworkError(val errorMessage: String) : TOolsError(errorMessage)
    data class PermissionError(val permission: String) : TOolsError("Permission denied: $permission")
    data class FileSystemError(val errorMessage: String) : TOolsError(errorMessage)
    data class DatabaseError(val errorMessage: String) : TOolsError(errorMessage)
    data class AIProviderError(val provider: String, val errorMessage: String) : TOolsError("$provider error: $errorMessage")
    data class BuildError(val errorMessage: String) : TOolsError(errorMessage)
    data class GitError(val errorMessage: String) : TOolsError(errorMessage)
    data class ValidationError(val errorMessage: String) : TOolsError(errorMessage)
    data class UnknownError(override val cause: Throwable) : TOolsError(cause.message)
}

/**
 * Centralized error handler with circuit breaker pattern.
 * Prevents cascading failures in external API calls.
 */
class ErrorHandler {

    /**
     * Circuit breaker for external API calls.
     * States: CLOSED (working), OPEN (failing), HALF_OPEN (testing recovery)
     */
    class CircuitBreaker(
        private val failureThreshold: Int = 5,
        private val timeoutMs: Long = 60000
    ) {
        private var failureCount = 0
        private var lastFailureTime = 0L
        private var state = CircuitState.CLOSED

        enum class CircuitState {
            CLOSED,    // Normal operation
            OPEN,      // Stop trying, fail fast
            HALF_OPEN  // Testing if service recovered
        }

        fun <T> execute(block: suspend () -> T): T {
            when (state) {
                CircuitState.CLOSED -> {
                    try {
                        val result = kotlinx.coroutines.runBlocking { block() }
                        failureCount = 0
                        return result
                    } catch (e: Exception) {
                        handleFailure()
                        throw e
                    }
                }
                CircuitState.OPEN -> {
                    if (System.currentTimeMillis() - lastFailureTime > timeoutMs) {
                        state = CircuitState.HALF_OPEN
                        return execute(block)
                    } else {
                        throw IOException("Circuit breaker OPEN - service unavailable")
                    }
                }
                CircuitState.HALF_OPEN -> {
                    try {
                        val result = kotlinx.coroutines.runBlocking { block() }
                        state = CircuitState.CLOSED
                        failureCount = 0
                        return result
                    } catch (e: Exception) {
                        handleFailure()
                        throw e
                    }
                }
            }
        }

        private fun handleFailure() {
            failureCount++
            lastFailureTime = System.currentTimeMillis()
            if (failureCount >= failureThreshold) {
                state = CircuitState.OPEN
                Timber.w("Circuit breaker OPEN after $failureCount failures")
            }
        }
    }

    /**
     * Retry logic with exponential backoff.
     */
    suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 100,
        maxDelayMs: Long = 10000,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var delay = initialDelayMs

        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Attempt $attempt failed, retrying in ${delay}ms")

                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(delay)
                    delay = (delay * 2).coerceAtMost(maxDelayMs)
                }
            }
        }

        throw lastException ?: IOException("All retry attempts failed")
    }

    /**
     * Convert throwable to appropriate TOolsError.
     */
    fun handleException(throwable: Throwable): TOolsError {
        return when (throwable) {
            is TOolsError -> throwable
            is SocketTimeoutException -> TOolsError.NetworkError("Request timeout")
            is IOException -> TOolsError.NetworkError(throwable.message ?: "Network error")
            is SecurityException -> TOolsError.PermissionError(throwable.message ?: "Unknown permission")
            else -> TOolsError.UnknownError(throwable)
        }
    }

    companion object {
        fun logError(error: TOolsError) {
            Timber.e(error, "TOolsError: ${error.javaClass.simpleName}")
        }
    }
}


