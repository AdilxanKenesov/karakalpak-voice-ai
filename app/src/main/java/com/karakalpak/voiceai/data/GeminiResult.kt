package com.karakalpak.voiceai.data

/**
 * Result of a Gemini call. The UI gets one of these instead of a raw exception,
 * so every call site is forced to handle the failure path explicitly.
 */
sealed interface GeminiResult<out T> {
    data class Success<T>(val value: T) : GeminiResult<T>

    /**
     * @param message a short, user/log-friendly description.
     * @param httpCode the HTTP status if the failure came from an HTTP response.
     * @param cause the underlying throwable, if any (kept off the UI, useful for logs).
     */
    data class Error(
        val message: String,
        val httpCode: Int? = null,
        val cause: Throwable? = null,
    ) : GeminiResult<Nothing>
}
