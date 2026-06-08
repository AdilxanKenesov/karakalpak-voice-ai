package com.karakalpak.voiceai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted message row. [conversationId] groups messages into a conversation so we can
 * restore the last one on startup and start a fresh one for "new chat".
 *
 * [role] stores [com.karakalpak.voiceai.data.Role.wire] ("user"/"model"). [audioPath]
 * points to the saved TTS WAV for assistant messages (null for user messages), enabling
 * replay from history without re-synthesizing.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val text: String,
    val audioPath: String?,
    val timestamp: Long,
)
