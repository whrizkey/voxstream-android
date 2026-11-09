package com.voxstream.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.voxstream.android.player.VoxStreamPlayerController
import com.voxstream.android.ui.player.PlayerScreen
import com.voxstream.android.ui.settings.SettingsScreen
import com.voxstream.android.ui.theme.VoxStreamTheme

enum class Screen {
    PLAYER,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var controller: VoxStreamPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        controller = VoxStreamPlayerController(
            context = applicationContext,
            scope = lifecycleScope
        )

        // Handle incoming intent (e.g. Open with... video)
        intent?.data?.let { uri ->
            controller.openMedia(uri)
        }

        setContent {
            VoxStreamTheme {
                var currentScreen by remember { mutableStateOf(Screen.PLAYER) }

                when (currentScreen) {
                    Screen.PLAYER -> {
                        PlayerScreen(
                            controller = controller,
                            onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                        )
                    }
                    Screen.SETTINGS -> {
                        BackHandler {
                            currentScreen = Screen.PLAYER
                        }
                        SettingsScreen(
                            controller = controller,
                            onNavigateBack = { currentScreen = Screen.PLAYER }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.release()
    }
}
