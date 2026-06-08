package com.karakalpak.voiceai.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Energy-based voice-activity capture over [AudioRecord] (16 kHz / mono / 16-bit PCM).
 *
 * [captureUtterance] listens until it hears speech, buffers it, and returns once the
 * speaker has been silent for [endSilenceMs]. The result is a WAV (PCM + header) ready
 * for STT, or null if nothing usable was captured.
 *
 * The threshold adapts to the room: the first [calibrationMs] are used to estimate the
 * noise floor, and the speech threshold is set a few times above it. (A learned VAD such
 * as Silero via ONNX would be more robust — a worthwhile later upgrade.)
 *
 * The mic is opened and released entirely within [captureUtterance], which is what makes
 * the caller's half-duplex easy: nothing is recording while the AI is speaking.
 *
 * @param onAmplitude called per frame with a normalized 0..1 level (drives the waveform).
 * @param onSpeechStart called once when speech is first detected (Listening -> Capturing).
 */
class MicVad(
    private val onAmplitude: (Float) -> Unit,
    private val onSpeechStart: () -> Unit,
    private val sampleRate: Int = 16_000,
    private val calibrationMs: Int = 500,
    private val endSilenceMs: Int = 1_200,
) {

    @SuppressLint("MissingPermission") // caller guarantees RECORD_AUDIO before starting the loop
    suspend fun captureUtterance(): ByteArray? = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBuf")
            return@withContext null
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            CHANNEL,
            ENCODING,
            max(minBuf, FRAME_SAMPLES * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return@withContext null
        }

        val frame = ShortArray(FRAME_SAMPLES)
        val frameMs = FRAME_SAMPLES * 1000 / sampleRate
        val captured = ByteArrayOutputStream()

        try {
            record.startRecording()

            // --- Calibrate noise floor from the first calibrationMs ---
            var noiseSum = 0.0
            var noiseFrames = 0
            val calibrationFrames = max(1, calibrationMs / frameMs)
            while (noiseFrames < calibrationFrames && currentCoroutineContext().isActive) {
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) continue
                val level = rms(frame, n)
                onAmplitude(normalize(level))
                noiseSum += level
                noiseFrames++
            }
            val noiseFloor = if (noiseFrames > 0) noiseSum / noiseFrames else 200.0
            val threshold = max(noiseFloor * NOISE_MULTIPLIER, MIN_THRESHOLD)

            // --- Wait for speech, then buffer until trailing silence ---
            // Onset gating: require ONSET_FRAMES of sustained above-threshold audio before
            // committing to "speech", so single clicks/taps/coughs don't trigger a turn.
            var speaking = false
            var onsetFrames = 0
            var silenceMs = 0
            var speechMs = 0
            val pending = ByteArrayOutputStream() // tentative onset audio, kept if it commits
            while (currentCoroutineContext().isActive) {
                val n = record.read(frame, 0, frame.size)
                if (n <= 0) continue
                val level = rms(frame, n)
                onAmplitude(normalize(level))

                if (!speaking) {
                    if (level > threshold) {
                        onsetFrames++
                        appendLittleEndian(pending, frame, n)
                        if (onsetFrames >= ONSET_FRAMES) {
                            speaking = true
                            onSpeechStart()
                            captured.write(pending.toByteArray())
                            pending.reset()
                            speechMs += onsetFrames * frameMs
                        }
                    } else {
                        // Not sustained — it was noise. Discard the tentative buffer.
                        onsetFrames = 0
                        pending.reset()
                    }
                } else {
                    appendLittleEndian(captured, frame, n)
                    speechMs += frameMs
                    if (level < threshold) {
                        silenceMs += frameMs
                        if (silenceMs >= endSilenceMs) break // utterance ended
                    } else {
                        silenceMs = 0
                    }
                    if (speechMs >= MAX_UTTERANCE_MS) break // safety cap
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }

        val pcm = captured.toByteArray()
        if (pcm.size < MIN_PCM_BYTES) {
            null // too short to be real speech
        } else {
            WavUtil.pcmToWav(pcm, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
        }
    }

    private fun rms(frame: ShortArray, n: Int): Double {
        var sum = 0.0
        for (i in 0 until n) {
            val v = frame[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / n)
    }

    private fun normalize(level: Double): Float = (level / NORMALIZE_DIVISOR).coerceIn(0.0, 1.0).toFloat()

    private fun appendLittleEndian(out: ByteArrayOutputStream, frame: ShortArray, n: Int) {
        val bytes = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = frame[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        out.write(bytes)
    }

    companion object {
        private const val TAG = "MicVad"
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 1024 // ~64 ms at 16 kHz
        private const val NOISE_MULTIPLIER = 3.0
        private const val MIN_THRESHOLD = 500.0
        private const val NORMALIZE_DIVISOR = 8_000.0
        private const val ONSET_FRAMES = 3 // ~190 ms of sustained sound before it counts as speech
        private const val MAX_UTTERANCE_MS = 15_000
        private const val MIN_PCM_BYTES = 16_000 * 2 * 400 / 1000 // ignore utterances < 0.4 s
    }
}
