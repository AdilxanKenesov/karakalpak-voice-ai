package com.karakalpak.voiceai.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karakalpak.voiceai.R
import com.karakalpak.voiceai.ui.theme.AccentCyan
import com.karakalpak.voiceai.ui.theme.AccentViolet
import com.karakalpak.voiceai.ui.theme.CardBorder

@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        // Brand: small logo mark + name + subtitle.
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark()
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_brand),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.home_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(24.dp))

        MenuCard(
            icon = Icons.Filled.ChatBubbleOutline,
            title = stringResource(R.string.card_chat_title),
            subtitle = stringResource(R.string.card_chat_subtitle),
            accent = false,
            onClick = onOpenChat,
        )

        Spacer(Modifier.height(16.dp))

        MenuCard(
            icon = Icons.Filled.Mic,
            title = stringResource(R.string.card_voice_title),
            subtitle = stringResource(R.string.card_voice_subtitle),
            accent = true,
            onClick = onOpenVoice,
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LogoMark() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(AccentViolet, AccentCyan)),
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val border = if (accent) {
        BorderStroke(1.5.dp, Brush.linearGradient(listOf(AccentViolet, AccentCyan)))
    } else {
        BorderStroke(1.dp, CardBorder)
    }
    val iconTint = if (accent) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (accent) {
                            AccentViolet.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    ),
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            }

            Spacer(Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
