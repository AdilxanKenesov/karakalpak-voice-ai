package com.karakalpak.voiceai.data

/**
 * Text-to-speech abstraction. Kept deliberately thin so the implementation can be
 * swapped without touching the rest of the app.
 *
 * Karakalpak is NOT officially supported by Gemini TTS, so [GeminiTtsProvider]
 * pronunciation is best-effort. To improve it later, drop in a different provider
 * (e.g. an Azure kk-KZ Kazakh voice) — only the implementation changes.
 *
 * @return playable WAV bytes (header + PCM).
 * @throws Exception on failure. The higher-level [GeminiClient.synthesize] catches
 *  these and converts them into [GeminiResult] for the UI.
 */
interface TtsProvider {
    suspend fun synthesize(text: String): ByteArray
}
