package com.karakalpak.voiceai.data

/** A single turn in the conversation. */
data class Message(
    val role: Role,
    val text: String,
)

/** Gemini's two content roles. Maps to the JSON `role` field. */
enum class Role(val wire: String) {
    USER("user"),
    MODEL("model");

    companion object {
        fun fromWire(wire: String): Role = entries.firstOrNull { it.wire == wire } ?: USER
    }
}
