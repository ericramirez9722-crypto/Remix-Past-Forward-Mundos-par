package com.pastforward.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pastforward.ui.components.TimelineVisualizer
import com.pastforward.viewmodel.MainUiState
import com.pastforward.viewmodel.MainViewModel

@Composable
fun TimelineScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    context: Context,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val activeStyles = if (uiState.currentMode == Mode.DECADES) DECADES else PARALLEL_WORLDS

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Multi-era Reel strip
        TimelineVisualizer(
            activeStyles = activeStyles,
            generatedImages = uiState.generatedImages,
            cardRotations = CARD_ROTATIONS,
            onRetry = { style -> viewModel.handleRegenerateItem(context, style) },
            onCardClick = { url, caption, style -> viewModel.setFullScreenImage(url, caption, style) },
            modifier = Modifier.weight(1f)
        )

        // Lower Layout HUD Controls
        if (uiState.appState == AppState.RESULTS_SHOWN) {
            Box(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Start Over Card Reset
                    Button(
                        onClick = { viewModel.handleReset() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Start Over",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Composite Album Generation download
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.handleDownloadAlbum(
                                context = context,
                                onSuccess = {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Album composite saved! Content stored in Gallery.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                },
                                onFailure = {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Please wait for all images to develop before saving.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp),
                        enabled = !uiState.isDownloading
                    ) {
                        Text(
                            text = if (uiState.isDownloading) "Compiling Album..." else "Download Album",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            // Generating indicators
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✦ NEURAL STREAMS CONVERGING ✦",
                    color = Color(0xFFFACC15).copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
