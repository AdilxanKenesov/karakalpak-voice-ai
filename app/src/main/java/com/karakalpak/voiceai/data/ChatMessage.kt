package com.karakalpak.voiceai.data

/**
 * A message as shown in the UI and persisted in Room.
 *
 * Richer than [Message] (the bare chat-history type sent to Gemini): it carries a DB
 * [id], an optional [audioPath] to the saved TTS audio for replay-from-history, and a
 * [timestamp]. Convert to [Message] via [toGeminiMessage] when building chat context.
 */
data class ChatMessage(
    val id: Long = 0,
    val role: Role,
    val text: String,
    val audioPath: String? = null,
    val timestamp: Long,
)

fun ChatMessage.toGeminiMessage(): Message = Message(role = role, text = text)
