package com.karakalpak.voiceai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.karakalpak.voiceai.ui.AppNav
import com.karakalpak.voiceai.ui.theme.KarakalpakVoiceAITheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acceptance check: confirm the key flowed from local.properties -> BuildConfig.
        // Log ONLY the length, never the value.
        Log.d(TAG, "GEMINI_API_KEY length = ${BuildConfig.GEMINI_API_KEY.length}")

        enableEdgeToEdge()
        setContent {
            KarakalpakVoiceAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav()
                }
            }
        }
    }

    companion object {
        const val TAG = "KarakalpakVoiceAI"
    }
}
