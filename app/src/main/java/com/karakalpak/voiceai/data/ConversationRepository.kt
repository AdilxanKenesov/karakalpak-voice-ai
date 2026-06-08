package com.karakalpak.voiceai.data

import com.karakalpak.voiceai.data.db.MessageDao
import com.karakalpak.voiceai.data.db.MessageEntity

/**
 * Persistence boundary for the conversation. The ViewModel talks to this; it never
 * touches Room types directly. Maps between [MessageEntity] (storage) and [ChatMessage]
 * (domain/UI).
 */
class ConversationRepository(private val dao: MessageDao) {

    /** Conversation id of the most recent message, or null if nothing has been saved. */
    suspend fun latestConversationId(): Long? = dao.latestConversationId()

    suspend fun messagesFor(conversationId: Long): List<ChatMessage> =
        dao.messagesFor(conversationId).map { it.toChatMessage() }

    /** Inserts a message and returns it with its assigned DB id. */
    suspend fun insert(conversationId: Long, message: ChatMessage): ChatMessage {
        val id = dao.insert(message.toEntity(conversationId))
        return message.copy(id = id)
    }

    suspend fun updateAudioPath(id: Long, audioPath: String?) =
        dao.updateAudioPath(id, audioPath)
}

private fun MessageEntity.toChatMessage(): ChatMessage = ChatMessage(
    id = id,
    role = Role.fromWire(role),
    text = text,
    audioPath = audioPath,
    timestamp = timestamp,
)

private fun ChatMessage.toEntity(conversationId: Long): MessageEntity = MessageEntity(
    id = id, // 0 -> Room autogenerates
    conversationId = conversationId,
    role = role.wire,
    text = text,
    audioPath = audioPath,
    timestamp = timestamp,
)
