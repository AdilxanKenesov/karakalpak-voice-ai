package com.karakalpak.voiceai.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.karakalpak.voiceai.ui.theme.AccentCyan
import com.karakalpak.voiceai.ui.theme.AccentViolet
import kotlin.math.sin

/**
 * The AI character: a glowing orb with a dark visor band and two cyan "eyes", framed by
 * expanding accent rings while Speaking. Everything is state-driven — calm when
 * Listening, energetic when Speaking, a processing sweep when Thinking.
 */
@Composable
fun Robot(
    state: VoiceState,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "robot")

    // Subtle vertical bob (a touch faster while speaking).
    val bobDuration = if (state == VoiceState.Speaking) 1100 else 2600
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(bobDuration), RepeatMode.Restart),
        label = "bob",
    )

    // Expanding rings progress (used only while Speaking).
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "ring",
    )

    // Eye glow pulse — calm when listening, bright/fast when speaking.
    val glow by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (state == VoiceState.Speaking) 500 else 1800),
            RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // Thinking sweep angle.
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "sweep",
    )

    Canvas(modifier = modifier.size(220.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f + sin(bob) * size.height * 0.02f)
        val orbRadius = size.minDimension * 0.30f

        // 1. Expanding pulse rings while speaking.
        if (state == VoiceState.Speaking) {
            drawPulseRings(center, orbRadius, ring)
        }

        // 2. Outer glow halo.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentViolet.copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = orbRadius * 2.1f,
            ),
            radius = orbRadius * 2.1f,
            center = center,
        )

        // 3. Orb body.
        val activeTint = if (state == VoiceState.Capturing) AccentCyan else AccentViolet
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    activeTint.copy(alpha = 0.95f),
                    AccentViolet.copy(alpha = 0.55f),
                    Color(0xFF141A2B),
                ),
                center = center.copy(y = center.y - orbRadius * 0.25f),
                radius = orbRadius * 1.3f,
            ),
            radius = orbRadius,
            center = center,
        )

        // 4. Dark visor band across the middle.
        val visorHeight = orbRadius * 0.62f
        val visorWidth = orbRadius * 1.7f
        drawRoundRect(
            color = Color(0xFF0A0E17),
            topLeft = Offset(center.x - visorWidth / 2f, center.y - visorHeight / 2f),
            size = Size(visorWidth, visorHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(visorHeight / 2f),
        )

        // 5. Two glowing cyan eyes on the visor.
        val eyeGlow = when (state) {
            VoiceState.Speaking -> glow
            VoiceState.Listening, VoiceState.Capturing -> 0.55f + glow * 0.35f
            VoiceState.Thinking, VoiceState.Transcribing -> 0.35f
            VoiceState.Error -> 0.5f
        }
        val eyeColor = if (state == VoiceState.Error) Color(0xFFFF6B6B) else AccentCyan
        val eyeOffsetX = orbRadius * 0.42f
        val eyeRadius = orbRadius * 0.16f
        listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            // glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(eyeColor.copy(alpha = eyeGlow), Color.Transparent),
                    center = Offset(center.x + dx, center.y),
                    radius = eyeRadius * 2.6f,
                ),
                radius = eyeRadius * 2.6f,
                center = Offset(center.x + dx, center.y),
            )
            // core
            drawCircle(
                color = eyeColor.copy(alpha = 0.7f + eyeGlow * 0.3f),
                radius = eyeRadius,
                center = Offset(center.x + dx, center.y),
            )
        }

        // 6. Thinking sweep around the orb.
        if (state == VoiceState.Thinking || state == VoiceState.Transcribing) {
            drawArc(
                color = AccentCyan.copy(alpha = 0.8f),
                startAngle = sweep,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - orbRadius * 1.35f, center.y - orbRadius * 1.35f),
                size = Size(orbRadius * 2.7f, orbRadius * 2.7f),
                style = Stroke(width = 5f),
            )
        }
    }
}

private fun DrawScope.drawPulseRings(center: Offset, orbRadius: Float, progress: Float) {
    // Two rings, phase-shifted, expanding and fading.
    listOf(progress, (progress + 0.5f) % 1f).forEach { p ->
        val radius = orbRadius * (1.1f + p * 1.4f)
        val alpha = (1f - p) * 0.5f
        drawCircle(
            color = AccentCyan.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(width = 3f),
        )
    }
}
