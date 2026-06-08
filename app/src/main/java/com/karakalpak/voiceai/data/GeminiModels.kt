package com.karakalpak.voiceai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire models for the Gemini v1beta `:generateContent` endpoint.
 *
 * The JSON uses camelCase (e.g. `inlineData`, `mimeType`), so the Kotlin property
 * names match the field names directly — no @SerialName needed except where a
 * keyword/style clash would otherwise occur. Nulls are omitted on the wire
 * (see GeminiClient's Json config: explicitNulls = false), so optional fields are
 * simply left out of the request.
 */

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>,
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String, // base64
)

@Serializable
data class GenerationConfig(
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null,
    val temperature: Float? = null,
    val responseMimeType: String? = null,
    val responseSchema: Schema? = null,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

/** Controls model "thinking". budget=0 disables it so the whole token budget is the answer. */
@Serializable
data class ThinkingConfig(
    val thinkingBudget: Int,
)

/** Minimal OpenAPI-subset schema for structured (JSON) responses. */
@Serializable
data class Schema(
    val type: String, // "OBJECT", "STRING", ...
    val properties: Map<String, Schema>? = null,
    val required: List<String>? = null,
)

@Serializable
data class SpeechConfig(
    val voiceConfig: VoiceConfig,
)

@Serializable
data class VoiceConfig(
    val prebuiltVoiceConfig: PrebuiltVoiceConfig,
)

@Serializable
data class PrebuiltVoiceConfig(
    val voiceName: String,
)

// --- Response ---

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(
    val blockReason: String? = null,
)

/** Combined STT+chat result: the model's JSON `{transcript, answer}`. */
@Serializable
data class TranscriptAnswer(
    val transcript: String = "",
    val answer: String = "",
)

/** Gemini error envelope: `{ "error": { "code", "message", "status" } }`. */
@Serializable
data class GeminiErrorEnvelope(
    val error: GeminiErrorBody? = null,
)

@Serializable
data class GeminiErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)
