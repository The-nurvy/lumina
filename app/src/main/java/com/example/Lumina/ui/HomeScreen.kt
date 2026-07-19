package com.example.Lumina.ui

import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.Lumina.models.UIMode
import com.example.Lumina.viewmodel.VisualizerViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeScreen(
    viewModel: VisualizerViewModel,
    uiModeFlow: StateFlow<UIMode>,
    renderBandsFlow: StateFlow<com.example.Lumina.models.AudioBands>
) {
    val uiMode by uiModeFlow.collectAsState(UIMode.IDLE)

    MaterialTheme {
        Surface(color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                VisualizerScreen(fftBandsFlow = renderBandsFlow)

                when (uiMode) {
                    UIMode.IDLE -> IdleScreen(viewModel)
                    UIMode.HOSTING, UIMode.SEARCHING, UIMode.CONNECTED -> StatusScreen(uiMode, viewModel)
                }
            }
        }
    }
}

@Composable
private fun IdleScreen(viewModel: VisualizerViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Button(onClick = { viewModel.startHosting() }, modifier = Modifier.padding(8.dp)) {
            Text("HOST (Listen to Mic)")
        }
        Button(onClick = { viewModel.startJoining() }, modifier = Modifier.padding(8.dp)) {
            Text("JOIN (Sync to Host)")
        }
    }
}

@Composable
private fun StatusScreen(uiMode: UIMode, viewModel: VisualizerViewModel) {
    Text(
        "Status: $uiMode",
        color = Color.White,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(32.dp)
    )

    if (uiMode == UIMode.HOSTING) {
        IconButton(
            onClick = { viewModel.cycleVisualMode() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
                .size(64.dp)
        ) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Change Visuals", tint = Color.White)
        }
    }
}
