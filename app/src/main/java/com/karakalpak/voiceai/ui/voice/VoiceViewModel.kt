package com.karakalpak.voiceai.ui.voice

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karakalpak.voiceai.audio.AudioPlayer
import com.karakalpak.voiceai.audio.MicVad
import com.karakalpak.voiceai.audio.PlaybackState
import com.karakalpak.voiceai.data.GeminiClient
import com.karakalpak.voiceai.data.GeminiResult
import com.karakalpak.voiceai.data.Message
import com.karakalpak.voiceai.data.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** State of the hands-free voice session. Drives the robot animation + status label. */
enum class VoiceState {
    Listening, // calm orb, waiting for speech
    Capturing, // user is speaking; waveform reacts
    Transcribing,
    Thinking,
    Speaking, // AI is talking; pulse rings + mouth waveform
    Error,
}

/** One-shot UI events (transient toasts). */
sealed interface VoiceEvent {
    data object TtsUnavailable : VoiceEvent
}

/**
 * Hands-free voice loop. On [start] it continuously: listens (VAD) -> transcribes ->
 * chats -> speaks, then returns to listening — no push-to-talk.
 *
 * Half-duplex is structural: [MicVad] opens and releases the mic entirely within one
 * capture, and we only capture while Listening/Capturing. While Transcribing/Thinking/
 * Speaking the mic is closed, so the AI can never transcribe its own TTS output.
 */
class VoiceViewModel(
    private val gemini: GeminiClient,
    private val player: AudioPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceState.Listening)
    val state = _state.asStateFlow()

    /** Normalized mic level (0..1) for the live waveform. */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    /** Last recognized user utterance (small caption). */
    private val _transcript = MutableStateFlow("")
    val transcript = _transcript.asStateFlow()

    /** Latest assistant answer (main live caption). */
    private val _answer = MutableStateFlow("")
    val answer = _answer.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage = _errorMessage.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val history = mutableListOf<Message>()
    private var sessionJob: Job? = null

    private val vad = MicVad(
        onAmplitude = { _amplitude.value = it },
        onSpeechStart = { _state.value = VoiceState.Capturing },
    )

    /** Starts (or resumes) the continuous listening loop. No-op if already running. */
    fun start() {
        if (sessionJob?.isActive == true) return
        _errorMessage.value = ""
        sessionJob = viewModelScope.launch { sessionLoop() }
    }

    /** Stops the loop and the mic/player (leaving the screen or going to background). */
    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        player.stop()
        _amplitude.value = 0f
    }

    /** Resume after an error. */
    fun retry() {
        _errorMessage.value = ""
        start()
    }

    private suspend fun sessionLoop() {
        while (currentCoroutineContext().isActive) {
            _state.value = VoiceState.Listening
            _amplitude.value = 0f

            // The mic is open ONLY inside captureUtterance — while we transcribe, think,
            // and speak below, it is closed. That is the half-duplex guarantee: the AI
            // can never hear (or transcribe) its own TTS.
            val wav = try {
                vad.captureUtterance()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.e(TAG, "VAD capture failed", e)
                null
            }
            _amplitude.value = 0f
            if (wav == null) continue // noise / utterance < 0.4 s — keep listening, no request

            // Count requests for THIS turn — must stay <= 2 (1 combined STT+chat, 1 TTS).
            val requestsBefore = gemini.requestCount.get()

            // --- One call: transcribe + answer (JSON {transcript, answer}) ---
            _state.value = VoiceState.Thinking
            val turn = when (val r = gemini.converse(wav, "audio/wav", history.takeLast(MAX_CONTEXT))) {
                is GeminiResult.Success -> r.value
                is GeminiResult.Error -> return failAndStop("Could not get an answer: ${r.message}")
            }
            if (turn.transcript.isBlank() || turn.answer.isBlank()) {
                logRequestCount(requestsBefore) // typically 1: a false trigger, no TTS
                cooldown()
                continue
            }
            Log.d(TAG, "Transcript: ${turn.transcript}")
            Log.d(TAG, "Answer: ${turn.answer}")
            _transcript.value = turn.transcript
            _answer.value = turn.answer
            history.add(Message(Role.USER, turn.transcript))
            history.add(Message(Role.MODEL, turn.answer))

            // --- Speak (non-fatal: fall back to text-only on failure) ---
            _state.value = VoiceState.Speaking
            Log.d(TAG, "TTS: calling synthesize() for answer (${turn.answer.length} chars)")
            when (val r = gemini.synthesize(turn.answer)) {
                is GeminiResult.Success -> {
                    player.play(r.value)
                    awaitPlaybackEnd()
                }
                is GeminiResult.Error -> {
                    Log.w(TAG, "TTS failed, text-only: ${r.message}")
                    _events.tryEmit(VoiceEvent.TtsUnavailable)
                }
            }

            logRequestCount(requestsBefore)
            cooldown() // brief pause so room echo / TTS tail doesn't re-trigger the VAD
        }
    }

    private fun logRequestCount(before: Int) {
        val used = gemini.requestCount.get() - before
        Log.d(TAG, "Gemini requests this turn = $used (target <= 2)")
        if (used > 2) Log.w(TAG, "Too many Gemini requests this turn: $used")
    }

    private suspend fun cooldown() {
        _state.value = VoiceState.Listening
        _amplitude.value = 0f
        kotlinx.coroutines.delay(COOLDOWN_MS)
    }

    private suspend fun awaitPlaybackEnd() {
        var sawActive = false
        player.state.takeWhile { st ->
            if (st == PlaybackState.PREPARING || st == PlaybackState.PLAYING) sawActive = true
            !(sawActive && (st == PlaybackState.IDLE || st == PlaybackState.ERROR))
        }.collect { /* drain */ }
    }

    private fun failAndStop(message: String) {
        Log.e(TAG, message)
        _errorMessage.value = message
        _state.value = VoiceState.Error
        sessionJob = null // loop is ending; allow retry() to start fresh
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val TAG = "VoiceViewModel"
        private const val MAX_CONTEXT = 20 // ~10 turns of context
        private const val COOLDOWN_MS = 1_000L // pause after a turn before listening again

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                VoiceViewModel(
                    gemini = GeminiClient.default(),
                    player = AudioPlayer(app),
                )
            }
        }
    }
}
