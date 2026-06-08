package com.karakalpak.voiceai.data

import android.util.Base64
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.karakalpak.voiceai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Calls the Gemini REST API directly (no backend) for the three things the app
 * needs: speech-to-text, chat, and text-to-speech.
 *
 * All public functions return [GeminiResult] — exceptions never leak to the UI.
 *
 * TODO(release): the API key is read from BuildConfig and sent straight from the
 *  device. For a public release, proxy these calls through a backend that holds
 *  the key server-side.
 */
class GeminiClient(
    private val api: GeminiApi,
    private val ttsProvider: TtsProvider,
    /** Total :generateContent requests issued (counts the combined call + TTS). */
    val requestCount: AtomicInteger = AtomicInteger(0),
) {

    /**
     * Combined STT + chat in ONE generateContent call: sends the audio plus the prior
     * history and gets back JSON {transcript, answer}. This keeps a user turn at a single
     * request here (+1 for TTS = 2 total), instead of separate transcribe + chat calls.
     */
    suspend fun converse(
        audioBytes: ByteArray,
        mimeType: String,
        history: List<Message>,
    ): GeminiResult<TranscriptAnswer> {
        return try {
            val base64Audio = withContext(Dispatchers.IO) {
                Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            }
            val contents = buildList {
                history.forEach { msg ->
                    add(Content(role = msg.role.wire, parts = listOf(Part(text = msg.text))))
                }
                add(
                    Content(
                        role = Role.USER.wire,
                        parts = listOf(
                            Part(inlineData = InlineData(mimeType = mimeType, data = base64Audio)),
                            Part(text = AUDIO_TURN_HINT),
                        ),
                    ),
                )
            }
            val request = GenerateContentRequest(
                systemInstruction = Content(parts = listOf(Part(text = CONVERSE_SYSTEM_PROMPT))),
                contents = contents,
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    responseSchema = Schema(
                        type = "OBJECT",
                        properties = mapOf(
                            "transcript" to Schema("STRING"),
                            "answer" to Schema("STRING"),
                        ),
                        required = listOf("transcript", "answer"),
                    ),
                    maxOutputTokens = MAX_OUTPUT_TOKENS,
                    // Disable thinking — otherwise thinking tokens consume the budget and
                    // the answer gets truncated (finishReason MAX_TOKENS).
                    thinkingConfig = ThinkingConfig(thinkingBudget = 0),
                ),
            )
            val response = api.generateContent(CHAT_MODEL, request)
            if (!response.isSuccessful) {
                return GeminiResult.Error(errorMessage(response), httpCode = response.code())
            }
            val text = response.body()?.candidates?.firstOrNull()
                ?.content?.parts?.mapNotNull { it.text }?.joinToString("")?.trim()
                .orEmpty()
            if (text.isEmpty()) return GeminiResult.Error("Empty response from Gemini")
            val parsed = runCatching {
                ERROR_JSON.decodeFromString(TranscriptAnswer.serializer(), text)
            }.getOrNull() ?: return GeminiResult.Error("Unexpected response format")
            GeminiResult.Success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Request failed", cause = e)
        }
    }

    /** STT: send the recorded audio file and get back a Karakalpak transcript. */
    suspend fun transcribe(audio: File, mimeType: String): GeminiResult<String> {
        return try {
            val bytes = withContext(Dispatchers.IO) { audio.readBytes() }
            transcribe(bytes, mimeType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Failed to read audio", cause = e)
        }
    }

    /** STT from in-memory audio bytes (e.g. WAV captured by the VAD loop). */
    suspend fun transcribe(audioBytes: ByteArray, mimeType: String): GeminiResult<String> {
        return try {
            val base64Audio = withContext(Dispatchers.IO) {
                Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            }
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        role = Role.USER.wire,
                        parts = listOf(
                            Part(inlineData = InlineData(mimeType = mimeType, data = base64Audio)),
                            Part(text = TRANSCRIBE_INSTRUCTION),
                        ),
                    ),
                ),
            )
            requestText(CHAT_MODEL, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Failed to encode audio", cause = e)
        }
    }

    /** Chat: prepend the Karakalpak system prompt, send history, return the answer. */
    suspend fun chat(history: List<Message>): GeminiResult<String> {
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = SYSTEM_PROMPT))),
            contents = history.map { msg ->
                Content(role = msg.role.wire, parts = listOf(Part(text = msg.text)))
            },
            generationConfig = GenerationConfig(
                maxOutputTokens = MAX_OUTPUT_TOKENS,
                thinkingConfig = ThinkingConfig(thinkingBudget = 0),
            ),
        )
        return requestText(CHAT_MODEL, request)
    }

    /** TTS: returns playable WAV bytes (24 kHz / 16-bit / mono). */
    suspend fun synthesize(text: String): GeminiResult<ByteArray> {
        return try {
            GeminiResult.Success(ttsProvider.synthesize(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "TTS failed", cause = e)
        }
    }

    // --- internals ---

    private suspend fun requestText(
        model: String,
        request: GenerateContentRequest,
    ): GeminiResult<String> {
        return try {
            val response = api.generateContent(model, request)
            if (!response.isSuccessful) {
                return GeminiResult.Error(errorMessage(response), httpCode = response.code())
            }
            val body = response.body()
            body?.promptFeedback?.blockReason?.let { reason ->
                return GeminiResult.Error("Blocked by Gemini: $reason")
            }
            val text = body?.candidates?.firstOrNull()
                ?.content?.parts?.mapNotNull { it.text }
                ?.joinToString("")?.trim()
                .orEmpty()
            if (text.isEmpty()) {
                GeminiResult.Error("Empty response from Gemini")
            } else {
                GeminiResult.Success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GeminiResult.Error(e.message ?: "Network error", cause = e)
        }
    }

    private fun errorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        val parsed = runCatching {
            ERROR_JSON.decodeFromString(GeminiErrorEnvelope.serializer(), raw).error?.message
        }.getOrNull()
        return parsed ?: "HTTP ${response.code()}: ${raw.take(200).ifEmpty { "request failed" }}"
    }

    companion object {
        // Model ids change — re-verify at https://ai.google.dev/gemini-api/docs/models
        // flash-lite handles audio STT + JSON output in one call and is cheap/fast.
        // Verified current & working 2026-06-08.
        const val CHAT_MODEL = "gemini-2.5-flash-lite"
        private const val MAX_OUTPUT_TOKENS = 512

        private const val BASE_URL = "https://generativelanguage.googleapis.com/"

        /** Enforced answer style for the Karakalpak voice assistant. */
        const val SYSTEM_PROMPT =
            "You are a helpful voice assistant for Karakalpak speakers. Always answer ONLY " +
                "in Karakalpak (Qaraqalpaq), Latin script. Give a COMPLETE but CONCISE answer " +
                "of 2 to 3 short, natural sentences that sound good when read aloud — get to " +
                "the point and do not pad. Do NOT use lists, tables, code, or any markdown — " +
                "just plain spoken sentences. Only say \"bilmeymen\" if you are truly unsure; " +
                "otherwise answer directly and informatively."

        /** Combined STT+chat: transcribe the audio AND answer, returning JSON. */
        private const val CONVERSE_SYSTEM_PROMPT =
            "You receive an audio clip of the user speaking Karakalpak (Qaraqalpaq), which " +
                "is close to Kazakh. Return JSON with two fields. " +
                "\"transcript\": exactly what the user said, in Karakalpak Latin script. " +
                "\"answer\": your reply to the user. For the answer, write ONLY in Karakalpak " +
                "Latin script, a COMPLETE but CONCISE reply of 2 to 3 short, natural sentences " +
                "suitable to be read aloud — get to the point, do not pad, and avoid long " +
                "sentences. No lists, tables, code, or markdown. Only say \"bilmeymen\" if " +
                "truly unsure; otherwise answer directly. If the audio has no clear speech, " +
                "return an empty transcript and empty answer."

        private const val AUDIO_TURN_HINT = "(The user's spoken audio is attached above.)"

        private const val TRANSCRIBE_INSTRUCTION =
            "Transcribe the spoken audio above into text. The speaker is talking in " +
                "Karakalpak (Qaraqalpaq), which is close to Kazakh. Use Latin script. " +
                "Return ONLY the transcript text, with no extra words, labels, or quotes."

        private val ERROR_JSON = Json { ignoreUnknownKeys = true }

        /**
         * Builds a fully-wired client: OkHttp (with the api-key header + logging),
         * Retrofit with the kotlinx.serialization converter, and the Gemini TTS provider.
         */
        fun default(): GeminiClient {
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false // omit null fields from request bodies
                encodeDefaults = false
            }

            // Counts every request that actually leaves the device — used to assert
            // <=2 Gemini requests per user turn.
            val requestCount = AtomicInteger(0)

            val logging = HttpLoggingInterceptor().apply {
                // Headers only — never log bodies (audio/base64 would be huge).
                // Redact the api key so it never reaches logcat.
                redactHeader("x-goog-api-key")
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val okHttp = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requestCount.incrementAndGet()
                    val request = chain.request().newBuilder()
                        .header("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS) // TTS/STT can be slow
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(GeminiApi::class.java)
            return GeminiClient(
                api = api,
                ttsProvider = GeminiTtsProvider(api),
                requestCount = requestCount,
            )
        }
    }
}
