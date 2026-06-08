package com.karakalpak.voiceai.audio

import android.content.Context
import java.io.File

/**
 * Persists TTS audio to app-internal storage (filesDir, not cache) so assistant
 * answers can be replayed from history across app restarts.
 */
class AudioFileStore(context: Context) {

    private val dir = File(context.filesDir, "tts_audio").apply { mkdirs() }

    /** Writes [bytes] to a stable per-message file and returns it. */
    fun save(bytes: ByteArray, messageId: Long): File {
        val file = File(dir, "msg_$messageId.wav")
        file.writeBytes(bytes)
        return file
    }
}
