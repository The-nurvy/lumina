package io.lumina.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.lumina.app.ui.AdvancedVisualizerScreen
import io.lumina.app.ui.VisualizerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: VisualizerViewModel by viewModels()
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.addAll(listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES))
        }
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(perms.toTypedArray())
        }

        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    val uiMode by viewModel.uiMode.collectAsState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        AdvancedVisualizerScreen(fftBandsFlow = viewModel.renderBandsFlow)
                        if (uiMode == "IDLE") {
                            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { viewModel.startHosting() }, modifier = Modifier.padding(8.dp)) { Text("HOST (Listen to Mic)") }
                                Button(onClick = { viewModel.startJoining() }, modifier = Modifier.padding(8.dp)) { Text("JOIN (Sync to Host)") }
                            }
                        } else {
                            Text("Status: $uiMode", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(32.dp))
                        }
                    }
                }
            }
        }
    }
}
