package com.pastforward

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.metrics.performance.JankStats
import com.pastforward.ui.screens.MainScreen
import com.pastforward.ui.theme.PastForwardTheme

class MainActivity : ComponentActivity() {
    private lateinit var jankStats: JankStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize JankStats to track and log UI frame drops (jank) in real-time
        jankStats = JankStats.createAndTrack(window) { frameData ->
            if (frameData.isJank) {
                val durationMs = frameData.frameDurationUiNanos / 1_000_000f
                val statesStr = frameData.states.joinToString { "${it.key}:${it.value}" }
                Log.w(
                    "JankStats",
                    "⚠️ UI Frame Drop (Jank) Detected! Duration: %.2f ms | States: [%s]".format(durationMs, statesStr)
                )
            }
        }

        setContent {
            PastForwardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        jankStats.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        jankStats.isTrackingEnabled = false
    }
}

