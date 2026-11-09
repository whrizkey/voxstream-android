package com.voxstream.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxstream.android.player.VoxStreamPlayerController
import com.voxstream.android.ui.theme.Emerald500
import com.voxstream.android.ui.theme.Indigo500
import com.voxstream.android.ui.theme.Slate400
import com.voxstream.android.ui.theme.Slate800
import com.voxstream.android.ui.theme.Slate900
import com.voxstream.android.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    controller: VoxStreamPlayerController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyStore = controller.secureKeyStore
    val usage = controller.usageTracker

    var apiKey by remember { mutableStateOf(keyStore.openAiApiKey) }
    var baseUrl by remember { mutableStateOf(keyStore.openAiBaseUrl) }
    var duckingDb by remember { mutableFloatStateOf(keyStore.duckingAttenuationDb) }
    var lookaheadSecs by remember { mutableIntStateOf(keyStore.lookaheadBufferSeconds) }
    var maxCost by remember { mutableFloatStateOf(keyStore.maxCostUsd) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VoxStream Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900
                )
            )
        },
        containerColor = Slate950,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Audio Ducking Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Audio Ducking Attenuation",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Level to attenuate original video audio while dubbed speech plays: ${duckingDb.toInt()} dB",
                        color = Slate400,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = duckingDb,
                        onValueChange = {
                            duckingDb = it
                            controller.updateDuckingDb(it)
                        },
                        valueRange = -24.0f..-6.0f,
                        steps = 17
                    )
                }
            }

            // Lookahead Window Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Rolling Lookahead Window",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Target lookahead buffer ahead of playhead: ${lookaheadSecs}s",
                        color = Slate400,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = lookaheadSecs.toFloat(),
                        onValueChange = {
                            lookaheadSecs = it.toInt()
                            keyStore.lookaheadBufferSeconds = it.toInt()
                        },
                        valueRange = 30f..120f,
                        steps = 5
                    )
                }
            }

            // Cloud API Configuration
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cloud Provider Configuration",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Optional API keys for OpenAI / Groq / Ollama endpoints.",
                        color = Slate400,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            keyStore.openAiApiKey = it
                        },
                        label = { Text("OpenAI API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            keyStore.openAiBaseUrl = it
                        },
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Cost Ceiling & Accounting
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Cost Protection & Accounting",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Max Spend Ceiling:", color = Slate400)
                        Text(text = "$${String.format("%.2f", maxCost)} USD", fontWeight = FontWeight.Bold, color = Indigo500)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Current Session Spend:", color = Slate400)
                        Text(
                            text = "$${String.format("%.4f", usage.getEstimatedCostUsd())} USD",
                            fontWeight = FontWeight.Bold,
                            color = Emerald500
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Audio Processed:", color = Slate400)
                        Text(text = "${usage.audioSeconds}s", color = Slate400)
                    }
                }
            }
        }
    }
}
