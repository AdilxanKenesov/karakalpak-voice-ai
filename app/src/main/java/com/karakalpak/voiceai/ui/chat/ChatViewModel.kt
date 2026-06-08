package com.karakalpak.voiceai.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karakalpak.voiceai.audio.AudioFileStore
import com.karakalpak.voiceai.audio.AudioPlayer
import com.karakalpak.voiceai.data.ChatMessage
import com.karakalpak.voiceai.data.ConversationRepository
import com.karakalpak.voiceai.data.GeminiClient
import com.karakalpak.voiceai.data.GeminiResult
import com.karakalpak.voiceai.data.Role
import com.karakalpak.voiceai.data.db.AppDatabase
import com.karakalpak.voiceai.data.toGeminiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Text chat with the AI: typed input -> chat() -> answer. Persists the conversation with
 * Room (restored on open) and can speak any answer on demand (replay-from-history).
 */
class ChatViewModel(
    private val gemini: GeminiClient,
    private val player: AudioPlayer,
    private val repository: ConversationRepository,
    private val audioStore: AudioFileStore,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors = _errors.asSharedFlow()

    private var conversationId: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            repository.latestConversationId()?.let { last ->
                conversationId = last
                _messages.value = repository.messagesFor(last)
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            appendMessage(Role.USER, trimmed)
            when (val r = gemini.chat(chatContext())) {
                is GeminiResult.Success -> appendMessage(Role.MODEL, r.value)
                is GeminiResult.Error -> _errors.tryEmit(r.message)
            }
            _sending.value = false
        }
    }

    /** Speak an answer: from the saved file if present, else synthesize, save and play. */
    fun replay(message: ChatMessage) {
        viewModelScope.launch {
            val saved = message.audioPath?.let(::File)?.takeIf { it.exists() }
            if (saved != null) {
                player.play(saved)
                return@launch
            }
            when (val r = gemini.synthesize(message.text)) {
                is GeminiResult.Success -> {
                    val file = withContext(Dispatchers.IO) { audioStore.save(r.value, message.id) }
                    repository.updateAudioPath(message.id, file.absolutePath)
                    _messages.update { list ->
                        list.map { if (it.id == message.id) it.copy(audioPath = file.absolutePath) else it }
                    }
                    player.play(file)
                }
                is GeminiResult.Error -> _errors.tryEmit(r.message)
            }
        }
    }

    fun newChat() {
        player.stop()
        conversationId = System.currentTimeMillis()
        _messages.value = emptyList()
    }

    private suspend fun appendMessage(role: Role, text: String): ChatMessage {
        val stored = repository.insert(
            conversationId,
            ChatMessage(role = role, text = text, timestamp = System.currentTimeMillis()),
        )
        _messages.update { it + stored }
        return stored
    }

    private fun chatContext() =
        _messages.value.takeLast(MAX_CONTEXT_MESSAGES).map { it.toGeminiMessage() }

    override fun onCleared() {
        player.stop()
    }

    companion object {
        private const val MAX_CONTEXT_MESSAGES = 20

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                ChatViewModel(
                    gemini = GeminiClient.default(),
                    player = AudioPlayer(app),
                    repository = ConversationRepository(AppDatabase.get(app).messageDao()),
                    audioStore = AudioFileStore(app),
                )
            }
        }
    }
}
