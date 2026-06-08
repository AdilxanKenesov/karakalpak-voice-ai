package com.karakalpak.voiceai.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karakalpak.voiceai.R
import com.karakalpak.voiceai.ui.theme.TextSecondary

@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: VoiceViewModel = viewModel(factory = VoiceViewModel.factory(context))

    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val answer by viewModel.answer.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.start()
    }

    // On entering: request permission, then auto-start the listening loop.
    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Toasts from the ViewModel (e.g. TTS fell back to text-only).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                VoiceEvent.TtsUnavailable ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.tts_unavailable),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    // Pause the mic/loop when backgrounded; release fully on leaving the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.stop()
                Lifecycle.Event.ON_START -> if (hasPermission) viewModel.start()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: back only (hands-free, no other controls).
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            IconButton(onClick = onBack, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextSecondary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Robot(state = state, amplitude = amplitude)

        Spacer(Modifier.height(20.dp))

        Waveform(
            state = state,
            amplitude = amplitude,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp),
        )

        Spacer(Modifier.height(20.dp))

        if (!hasPermission) {
            Text(
                text = stringResource(R.string.voice_permission_needed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.action_retry))
            }
        } else if (state == VoiceState.Error) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::retry) {
                Text(stringResource(R.string.action_retry))
            }
        } else {
            // Status label.
            Text(
                text = stringResource(state.statusRes()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(12.dp))

            // Live captions: user transcript (muted) + AI answer (primary text).
            if (transcript.isNotBlank()) {
                Text(
                    text = stringResource(R.string.label_you, transcript),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(0.85f),
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1.3f))
    }
}

private fun VoiceState.statusRes(): Int = when (this) {
    VoiceState.Listening -> R.string.voice_listening
    VoiceState.Capturing -> R.string.voice_capturing
    VoiceState.Transcribing -> R.string.voice_transcribing
    VoiceState.Thinking -> R.string.voice_thinking
    VoiceState.Speaking -> R.string.voice_speaking
    VoiceState.Error -> R.string.action_retry // not shown (Error handled above)
}
