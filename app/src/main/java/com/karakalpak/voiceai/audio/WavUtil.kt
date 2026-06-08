package com.karakalpak.voiceai.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gemini TTS returns RAW PCM (no container): signed 16-bit little-endian, mono.
 * To play it (MediaPlayer / a file) we wrap it in a minimal 44-byte WAV header.
 */
object WavUtil {

    const val SAMPLE_RATE = 24_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    /** Prepends a 44-byte WAV/RIFF header to raw little-endian PCM samples. */
    fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put('R'.code.toByte()).put('I'.code.toByte())
            .put('F'.code.toByte()).put('F'.code.toByte())
        header.putInt(chunkSize)
        header.put('W'.code.toByte()).put('A'.code.toByte())
            .put('V'.code.toByte()).put('E'.code.toByte())

        // "fmt " sub-chunk
        header.put('f'.code.toByte()).put('m'.code.toByte())
            .put('t'.code.toByte()).put(' '.code.toByte())
        header.putInt(16)                       // PCM fmt chunk size
        header.putShort(1)                      // audioFormat = 1 (PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        // "data" sub-chunk
        header.put('d'.code.toByte()).put('a'.code.toByte())
            .put('t'.code.toByte()).put('a'.code.toByte())
        header.putInt(dataSize)

        return ByteArrayOutputStream(44 + dataSize).apply {
            write(header.array())
            write(pcm)
        }.toByteArray()
    }
}
