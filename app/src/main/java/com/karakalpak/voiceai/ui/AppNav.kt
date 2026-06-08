package com.karakalpak.voiceai.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.karakalpak.voiceai.ui.chat.ChatScreen
import com.karakalpak.voiceai.ui.home.HomeScreen
import com.karakalpak.voiceai.ui.voice.VoiceScreen

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val VOICE = "voice"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenChat = { navController.navigate(Routes.CHAT) },
                    onOpenVoice = { navController.navigate(Routes.VOICE) },
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.VOICE) {
                VoiceScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
