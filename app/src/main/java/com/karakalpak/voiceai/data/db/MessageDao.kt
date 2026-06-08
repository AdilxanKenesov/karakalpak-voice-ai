package com.karakalpak.voiceai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    /** Messages of one conversation, oldest first. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    suspend fun messagesFor(conversationId: Long): List<MessageEntity>

    /** The conversation containing the most recent message, or null if the DB is empty. */
    @Query("SELECT conversationId FROM messages ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun latestConversationId(): Long?

    @Query("UPDATE messages SET audioPath = :audioPath WHERE id = :id")
    suspend fun updateAudioPath(id: Long, audioPath: String?)
}
