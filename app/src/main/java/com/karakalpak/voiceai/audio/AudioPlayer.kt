package com.karakalpak.voiceai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Where playback is in its lifecycle. Exposed via [AudioPlayer.state]. */
enum class PlaybackState {
    IDLE,
    PREPARING,
    PLAYING,
    ERROR,
}

/**
 * Plays the WAV bytes produced by GeminiClient.synthesize().
 *
 * MediaPlayer can't read from a raw byte array, so the bytes are written to a temp
 * `.wav` in the cache dir and played from there; the file is deleted when playback
 * finishes, is stopped, or errors.
 *
 * Not thread-safe: drive it from a single call site. MediaPlayer setup and its
 * callbacks are kept on the main thread (callbacks need a Looper).
 */
class AudioPlayer(private val context: Context) {

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var deleteCurrentOnDone = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Writes [bytes] to a temp WAV and plays it. The temp file is deleted when playback
     * finishes. Use [play] with a [File] to play a persistent file you own.
     */
    suspend fun play(bytes: ByteArray) {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav").apply {
                writeBytes(bytes)
            }
        }
        play(file, deleteWhenDone = true)
    }

    /**
     * Plays an existing WAV [file]. Returns once playback has been kicked off (prepared
     * asynchronously); observe [state] for progress. Any in-progress playback is stopped
     * first. The file is left in place unless [deleteWhenDone] is true.
     */
    suspend fun play(file: File, deleteWhenDone: Boolean = false) {
        stop()

        Log.d(TAG, "play() file=${file.name} size=${file.length()} bytes")
        withContext(Dispatchers.Main) {
            _state.value = PlaybackState.PREPARING
            try {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setOnPreparedListener { prepared ->
                        // Grab audio focus, then start. Resume-after-start is guaranteed:
                        // the VAD loop only resumes after playback fully ends (see awaitPlaybackEnd).
                        val granted = requestAudioFocus()
                        prepared.start()
                        _state.value = PlaybackState.PLAYING
                        Log.d(TAG, "onPrepared -> start(); audioFocusGranted=$granted, playing ${file.length()} bytes")
                    }
                    setOnCompletionListener {
                        Log.d(TAG, "onCompletion -> playback finished")
                        cleanup(PlaybackState.IDLE)
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "onError what=$what extra=$extra")
                        cleanup(PlaybackState.ERROR)
                        true // handled; suppresses a follow-up onCompletion
                    }
                    setDataSource(file.absolutePath)
                    prepareAsync()
                }
                player = mp
                currentFile = file
                deleteCurrentOnDone = deleteWhenDone
                Log.d(TAG, "prepareAsync() called")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start playback", e)
                cleanup(PlaybackState.ERROR)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .build()
        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Stops playback (if any) and releases resources. Safe to call anytime. */
    fun stop() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
        }
        cleanup(PlaybackState.IDLE)
    }

    private fun cleanup(newState: PlaybackState) {
        player?.let { mp -> runCatching { mp.reset(); mp.release() } }
        player = null
        abandonAudioFocus()
        // Only delete temp files we created; persistent files (replay from history) stay.
        if (deleteCurrentOnDone) currentFile?.let { f -> runCatching { f.delete() } }
        currentFile = null
        deleteCurrentOnDone = false
        _state.value = newState
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
