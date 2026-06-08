package com.karakalpak.voiceai.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Raw Gemini REST surface. We return [Response] (not the bare body) so callers can
 * inspect the HTTP status — notably to retry the TTS preview model on HTTP 500.
 *
 * The API key is attached by an OkHttp interceptor as the `x-goog-api-key` header
 * (see GeminiClient), so it never appears in these method signatures.
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body body: GenerateContentRequest,
    ): Response<GenerateContentResponse>
}
