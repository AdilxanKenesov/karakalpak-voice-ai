package com.karakalpak.voiceai.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Push-to-talk recorder. Captures mic audio to an AAC `.m4a` file in the app cache
 * dir — a compact container that Gemini STT accepts as `audio/mp4`.
 *
 * 16 kHz mono is plenty for speech and keeps the upload small. If STT accuracy on
 * Karakalpak turns out poor, swap this for AudioRecord -> 16 kHz mono PCM WAV
 * (the [WavUtil] header helper already exists) without changing the public API.
 *
 * Not thread-safe: drive it from a single call site (start on press, stop on release).
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    /** MIME type of the produced file, for the Gemini STT request. */
    val mimeType: String get() = "audio/mp4"

    /**
     * Starts recording into a fresh cache file and returns it.
     * @throws IllegalStateException / java.io.IOException if the recorder can't start.
     */
    fun startRecording(): File {
        // Defensive: release any recorder left over from an aborted session.
        release()

        val file = File(context.cacheDir, "ptt_${System.currentTimeMillis()}.m4a")
        val rec = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        outputFile = file
        Log.d(TAG, "Recording started -> ${file.name}")
        return file
    }

    /**
     * Stops recording and returns the finished file, or `null` if nothing usable was
     * captured (e.g. the press was too short — [MediaRecorder.stop] then throws).
     */
    fun stopRecording(): File? {
        val rec = recorder ?: return null
        val file = outputFile
        return try {
            rec.stop()
            Log.d(TAG, "Recording stopped -> ${file?.name} (${file?.length()} bytes)")
            file?.takeIf { it.length() > 0 }
        } catch (e: RuntimeException) {
            // stop() throws when the recording was too short to produce valid output.
            Log.w(TAG, "stop() failed (recording too short?), discarding file", e)
            file?.delete()
            null
        } finally {
            release()
        }
    }

    /** Releases the recorder without producing a file. Safe to call repeatedly. */
    fun release() {
        recorder?.let {
            runCatching { it.release() }
        }
        recorder = null
        outputFile = null
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
