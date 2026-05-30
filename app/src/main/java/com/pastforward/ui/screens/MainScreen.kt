package com.pastforward.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pastforward.ui.components.ImageStatus
import com.pastforward.ui.components.PolaroidCard
import com.pastforward.ui.components.PinchToZoomOverlay
import com.pastforward.viewmodel.MainViewModel

enum class AppState {
    IDLE, IMAGE_UPLOADED, GENERATING, RESULTS_SHOWN
}

enum class Mode {
    DECADES, PARALLEL_WORLDS
}

val DECADES = listOf("1950s", "1960s", "1970s", "1980s", "1990s", "2000s")
val PARALLEL_WORLDS = listOf("Cyberpunk", "Fantasy", "Noir", "Steampunk", "Space Opera", "Post-Apocalyptic")

data class GeneratedImage(
    val status: ImageStatus,
    val url: String? = null,
    val error: String? = null
)

// Preloaded aesthetic rotations for results desk layout
val CARD_ROTATIONS = listOf(-6f, 4f, -2f, 8f, -4f, 5f)

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // Initialize our singleton ImageRepository caching in Phase 3
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val uiState by viewModel.uiState.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setImageUri(uri)
        }
    }

    // Scaffolding UI in full pitch-black editorial grid layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pure pitch black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top branding column in Phase 2 styling
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Past Forward",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )
            Text(
                text = "M UNDOS  PARALELOS",
                color = Color(0xFFFACC15), // Warm golden Accent
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            // State UI rendering flows via MVVM StateFlow observation (Phase 4)
            when (uiState.appState) {
                AppState.IDLE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(40.dp))
                        Box(
                            modifier = Modifier.clickable { imagePickerLauncher.launch("image/*") }
                        ) {
                            PolaroidCard(
                                caption = "Click to select a photo",
                                status = ImageStatus.DONE,
                                rotation = -4f
                            )
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                        Text(
                            text = "Tap the Polaroid to import your photo and begin a journey across time and space.",
                            color = Color(0xFF737373),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(280.dp)
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
                AppState.IMAGE_UPLOADED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        PolaroidCard(
                            caption = "Your Photo",
                            status = ImageStatus.DONE,
                            imageUrl = uiState.imageUri?.toString(),
                            rotation = 3f,
                            onCardClick = {
                                uiState.imageUri?.toString()?.let { url ->
                                    viewModel.setFullScreenImage(url, "Your Photo")
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(30.dp))

                        // Modes Selector segments
                        Box(
                            modifier = Modifier
                                .shadow(4.dp, RoundedCornerShape(24.dp))
                                .background(Color(0xFF171717), RoundedCornerShape(24.dp)) // Deep gray well
                                .padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.wrapContentSize(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val selectedColor = Color(0xFFFACC15)
                                val unselectedColor = Color(0xFF737373)

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (uiState.currentMode == Mode.DECADES) selectedColor else Color.Transparent)
                                        .clickable { viewModel.setMode(Mode.DECADES) }
                                        .padding(horizontal = 22.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Decades",
                                        color = if (uiState.currentMode == Mode.DECADES) Color.Black else unselectedColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (uiState.currentMode == Mode.PARALLEL_WORLDS) selectedColor else Color.Transparent)
                                        .clickable { viewModel.setMode(Mode.PARALLEL_WORLDS) }
                                        .padding(horizontal = 22.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Parallel Worlds",
                                        color = if (uiState.currentMode == Mode.PARALLEL_WORLDS) Color.Black else unselectedColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        // Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.handleReset() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = "Different Photo",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.handleGenerateClick(context) {
                                        Toast.makeText(
                                            context,
                                            "Failed to read image. Please load a different photo.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = "Reimagine ✦",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
                AppState.GENERATING, AppState.RESULTS_SHOWN -> {
                    // Staggered lists/grids of Polaroids represented inside modular TimelineScreen
                    TimelineScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        context = context,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Full screen viewer overlay (Pinch to Zoom Overlay)
        uiState.activeFullScreenImage?.let { imageUrl ->
            PinchToZoomOverlay(
                imageUrl = imageUrl,
                caption = uiState.activeFullScreenCaption ?: "AI Portrait",
                styleKey = uiState.activeFullScreenStyle,
                onDismiss = {
                    viewModel.setFullScreenImage(null, null, null)
                }
            )
        }
    }
}
