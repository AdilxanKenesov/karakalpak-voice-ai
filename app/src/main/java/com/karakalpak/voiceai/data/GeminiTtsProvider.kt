package com.karakalpak.voiceai.data

import android.util.Base64
import android.util.Log
import com.karakalpak.voiceai.audio.WavUtil
import kotlinx.coroutines.delay

/**
 * Gemini TTS implementation of [TtsProvider].
 *
 * The preview TTS models return RAW PCM (24 kHz / 16-bit / mono) as base64; we decode it
 * and wrap it in a WAV header. Every stage is logged so the audio path can be debugged.
 *
 * Quota is per-model per-day on the free tier (10/day), so a 429 on the primary model is
 * handled by failing over to a second TTS model with its own quota bucket. A 500 (the
 * preview models return these randomly) is retried on the same model.
 *
 * Throws on final failure — [GeminiClient] turns that into a [GeminiResult] for the UI.
 */
class GeminiTtsProvider(
    private val api: GeminiApi,
    private val models: List<String> = listOf(TTS_MODEL_PRIMARY, TTS_MODEL_FALLBACK),
    private val voiceName: String = VOICE_NAME,
) : TtsProvider {

    private sealed interface Attempt {
        data class Ok(val wav: ByteArray) : Attempt
        data object Quota : Attempt // HTTP 429: this model's daily quota is exhausted
        data class Failure(val reason: String) : Attempt
    }

    // Models that returned 429 this session — skipped on later turns so a failover costs
    // at most ONE extra request (once), keeping the steady state at 1 TTS request/turn.
    private val exhausted = mutableSetOf<String>()

    override suspend fun synthesize(text: String): ByteArray {
        Log.d(TAG, "TTS: calling (text=${text.length} chars, voice=$voiceName)")
        val candidates = models.filter { it !in exhausted }.ifEmpty {
            // All known-exhausted — clear and try again (a new day may have reset quotas).
            exhausted.clear()
            models
        }
        var lastReason = "unknown"
        for (model in candidates) {
            when (val attempt = trySynthesize(model, text)) {
                is Attempt.Ok -> return attempt.wav
                Attempt.Quota -> {
                    Log.w(TAG, "TTS: 429 quota exhausted on $model — skipping it and failing over")
                    exhausted.add(model)
                    lastReason = "429 quota on $model"
                }
                is Attempt.Failure -> {
                    Log.e(TAG, "TTS: failure on $model — ${attempt.reason}")
                    lastReason = attempt.reason
                }
            }
        }
        throw IllegalStateException("TTS failed on all models ($lastReason)")
    }

    private suspend fun trySynthesize(model: String, text: String): Attempt {
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = text)))),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(
                        prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = voiceName),
                    ),
                ),
            ),
        )

        repeat(MAX_RETRIES_500) { attempt ->
            val response = api.generateContent(model, request)
            val code = response.code()
            Log.d(TAG, "TTS: $model attempt ${attempt + 1} -> HTTP $code")

            if (response.isSuccessful) {
                val inline = response.body()
                    ?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.inlineData != null }
                    ?.inlineData
                if (inline?.data == null) {
                    Log.e(TAG, "TTS: 200 OK but response has NO inlineData audio")
                    return Attempt.Failure("no audio in response")
                }
                Log.d(TAG, "TTS: inlineData present, mimeType=${inline.mimeType}")
                val pcm = Base64.decode(inline.data, Base64.DEFAULT)
                val wav = WavUtil.pcmToWav(pcm)
                Log.d(TAG, "TTS: decoded PCM=${pcm.size} bytes -> WAV=${wav.size} bytes (model=$model)")
                return Attempt.Ok(wav)
            }

            val body = response.errorBody()?.string().orEmpty()
            when (code) {
                429 -> {
                    Log.w(TAG, "TTS: HTTP 429 RESOURCE_EXHAUSTED on $model (per-day quota)")
                    return Attempt.Quota
                }
                500 -> {
                    Log.w(TAG, "TTS: HTTP 500 on $model (attempt ${attempt + 1}/$MAX_RETRIES_500), retrying")
                    if (attempt < MAX_RETRIES_500 - 1) delay(RETRY_DELAY_MS * (attempt + 1))
                }
                else -> {
                    Log.e(TAG, "TTS: HTTP $code on $model: ${body.take(200)}")
                    return Attempt.Failure("HTTP $code")
                }
            }
        }
        return Attempt.Failure("HTTP 500 after $MAX_RETRIES_500 attempts on $model")
    }

    companion object {
        private const val TAG = "GeminiTts"

        // Model ids change — re-verify at https://ai.google.dev/gemini-api/docs/models
        // Each TTS model has its OWN free-tier daily quota (10/day), so the fallback
        // genuinely doubles available synthesis. 3.1 is primary: it's the newer model and
        // currently has quota; 2.5 is the fallback. Verified 2026-06-08.
        const val TTS_MODEL_PRIMARY = "gemini-3.1-flash-tts-preview"
        const val TTS_MODEL_FALLBACK = "gemini-2.5-flash-preview-tts"
        const val VOICE_NAME = "Kore"
        const val MAX_RETRIES_500 = 3
        private const val RETRY_DELAY_MS = 400L
    }
}
