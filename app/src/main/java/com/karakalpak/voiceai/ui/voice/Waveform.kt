package com.karakalpak.voiceai.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.karakalpak.voiceai.ui.theme.AccentCyan
import com.karakalpak.voiceai.ui.theme.AccentViolet
import com.karakalpak.voiceai.ui.theme.TextMuted
import kotlin.math.abs
import kotlin.math.sin

/**
 * A small symmetric bar waveform.
 * - Capturing: bars driven by the live mic [amplitude] (cyan).
 * - Speaking: bars animate as a moving wave (violet) to suggest the AI talking.
 * - Otherwise: a calm flat line of muted bars.
 */
@Composable
fun Waveform(
    state: VoiceState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 21,
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "phase",
    )

    val color = when (state) {
        VoiceState.Capturing -> AccentCyan
        VoiceState.Speaking -> AccentViolet
        else -> TextMuted
    }

    Canvas(modifier = modifier) {
        val mid = size.height / 2f
        val gap = size.width / barCount
        val barWidth = gap * 0.45f
        val maxBar = size.height * 0.46f

        for (i in 0 until barCount) {
            val centerBias = 1f - abs(i - (barCount - 1) / 2f) / ((barCount - 1) / 2f) // 0..1, peak in middle
            val level = when (state) {
                VoiceState.Capturing -> amplitude * (0.4f + centerBias)
                VoiceState.Speaking -> {
                    val s = (sin(phase + i * 0.6f) + 1f) / 2f
                    s * (0.35f + centerBias * 0.65f)
                }
                else -> 0.05f
            }.coerceIn(0.04f, 1f)

            val barHeight = maxBar * level
            val x = gap * i + gap / 2f
            drawLine(
                color = color,
                start = Offset(x, mid - barHeight),
                end = Offset(x, mid + barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
