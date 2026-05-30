package com.pastforward.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Share
import com.pastforward.data.repository.ImageRepository

@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    caption: String,
    styleKey: String? = null, // Add styleKey parameter
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Force handle the system back press gesture to exit the viewer
    BackHandler {
        onDismiss()
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var containerWidth by remember { mutableStateOf(0) }
    var containerHeight by remember { mutableStateOf(0) }

    val decodedBitmap = remember(imageUrl, styleKey) {
        if (imageUrl.startsWith("data:")) {
            val key = styleKey ?: imageUrl.hashCode().toString()
            val repo = ImageRepository.getInstance(context)
            repo.getFullResolutionBitmap(key, imageUrl)?.asImageBitmap()
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .onSizeChanged { size ->
                containerWidth = size.width
                containerHeight = size.height
            }
    ) {
        // Image display with gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(containerWidth, containerHeight, scale) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                val center = Offset(containerWidth / 2f, containerHeight / 2f)
                                val baseOffset = (center - tapOffset) * (2.5f - 1f)
                                
                                val maxOffsetX = maxOf(0f, (containerWidth * 2.5f - containerWidth) / 2f)
                                val maxOffsetY = maxOf(0f, (containerHeight * 2.5f - containerHeight) / 2f)
                                offset = Offset(
                                    x = baseOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                    y = baseOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                        }
                    )
                }
                .pointerInput(containerWidth, containerHeight, scale) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        
                        val adjustedOffset = if (newScale > 1f) {
                            (offset - centroid) * (newScale / oldScale) + centroid + pan
                        } else {
                            Offset.Zero
                        }

                        // Apply bounding logic using viewport width and height
                        val maxOffsetX = maxOf(0f, (containerWidth * newScale - containerWidth) / 2f)
                        val maxOffsetY = maxOf(0f, (containerHeight * newScale - containerHeight) / 2f)

                        offset = Offset(
                            x = adjustedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                            y = adjustedOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                        )
                        scale = newScale
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = caption,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    contentScale = ContentScale.Fit
                )
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = caption,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Top Glass Bar Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Explanatory Gesture Tip
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Pinch to Zoom • Double Tap",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share Action
                IconButton(
                    onClick = { shareImage(context, imageUrl, caption) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Image",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Close CTA
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Viewer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Elegant Bottom Caption HUD
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                .background(Color(0xFF111112).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = caption,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "A I  S T U D I O  P O R T R A I T",
                    color = Color(0xFFFACC15), // Elegant gold accent
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun shareImage(context: Context, imageUrl: String, caption: String) {
    try {
        val sharedDir = File(context.cacheDir, "shared_images").apply {
            if (!exists()) mkdirs()
        }
        val file = File(sharedDir, "temp_share.jpg")
        
        if (imageUrl.startsWith("data:")) {
            val cleanStr = if (imageUrl.contains(",")) imageUrl.substringAfter(",") else imageUrl
            val decodedBytes = Base64.decode(cleanStr, Base64.DEFAULT)
            FileOutputStream(file).use { output ->
                output.write(decodedBytes)
            }
        } else {
            val uri = Uri.parse(imageUrl)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "com.pastforward.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "Look at this reimagined portrait from Past Forward: $caption")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Portrait").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Failed to share image: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
