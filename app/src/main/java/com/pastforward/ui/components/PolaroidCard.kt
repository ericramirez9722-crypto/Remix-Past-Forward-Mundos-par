package com.pastforward.ui.components

import com.pastforward.data.repository.ImageRepository
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import coil.compose.AsyncImage

enum class ImageStatus {
    PENDING, DONE, ERROR
}

// Convert base64 data to ImageBitmap for ultra-fast, offline-capable in-memory rendering
fun base64ToImageBitmap(base64Str: String?): ImageBitmap? {
    if (base64Str == null) return null
    return try {
        val cleanStr = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
        val decodedBytes = Base64.decode(cleanStr, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// Build a hybrid ColorMatrix that interpolates dynamically between vintage sepia (progress=0) and realism (progress=1)
fun getChemicalDevelopmentMatrix(progress: Float): ColorMatrix {
    val sepia = floatArrayOf(
        0.393f + 0.607f * progress, 0.769f - 0.769f * progress, 0.189f - 0.189f * progress, 0f, 0f,
        0.349f - 0.349f * progress, 0.686f + 0.314f * progress, 0.168f - 0.168f * progress, 0f, 0f,
        0.272f - 0.272f * progress, 0.534f - 0.534f * progress, 0.131f + 0.869f * progress, 0f, 0f,
        0f,                          0f,                          0f,                          1f, 0f
    )
    return ColorMatrix(sepia)
}

@Composable
fun PolaroidCard(
    caption: String,
    status: ImageStatus,
    modifier: Modifier = Modifier,
    imageUrl: String? = null, // Can be base64 data or Uri string
    styleKey: String? = null,   // Specific style identifier for memory cache tracking
    error: String? = null,
    rotation: Float = 0f,
    onRetry: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    // Development animation states
    val haptic = LocalHapticFeedback.current
    var isImageLoaded by remember(imageUrl) { mutableStateOf(false) }
    var developmentProgress by remember(imageUrl) { mutableStateOf(0f) }

    var isPeeking by remember { mutableStateOf(false) }

    val peekElevation by animateDpAsState(
        targetValue = if (isPeeking) 22.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "peekElevation"
    )
    val peekRotationOffset by animateFloatAsState(
        targetValue = if (isPeeking) -5f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "peekRotationOffset"
    )
    val peekScale by animateFloatAsState(
        targetValue = if (isPeeking) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "peekScale"
    )

    LaunchedEffect(isPeeking) {
        if (isPeeking) {
            delay(1000) // Brief peek animation of 1 second
            isPeeking = false
        }
    }

    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val animatedDragOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dragX"
    )
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dragY"
    )

    val context = LocalContext.current

    val decodedBitmap = remember(imageUrl, styleKey) {
        if (imageUrl != null && imageUrl.startsWith("data:")) {
            val key = styleKey ?: imageUrl.hashCode().toString()
            val repo = ImageRepository.getInstance(context)
            repo.getThumbnailBitmap(key, imageUrl)?.asImageBitmap()
        } else null
    }

    // Shake-to-regenerate trigger using Android's Accelerometer Sensor API with full Lifecycle awareness
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    if (status != ImageStatus.PENDING && onRetry != null) {
        DisposableEffect(context, lifecycleOwner, onRetry) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            val sensorListener = object : SensorEventListener {
                private var lastX = 0f
                private var lastY = 0f
                private var lastZ = 0f
                private var lastTime = 0L
                private val SHAKE_THRESHOLD = 14f // Force threshold: higher values require stronger shakes
                private val SHAKE_COOLDOWN = 2000L // Prevent double triggers
                private var lastShakeTime = 0L

                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val now = System.currentTimeMillis()
                    if (now - lastTime > 100) {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]

                        if (lastTime != 0L) {
                            val dx = x - lastX
                            val dy = y - lastY
                            val dz = z - lastZ
                            val delta = sqrt(dx * dx + dy * dy + dz * dz)
                            
                            if (delta > SHAKE_THRESHOLD && now - lastShakeTime > SHAKE_COOLDOWN) {
                                lastShakeTime = now
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                android.widget.Toast.makeText(
                                    context, 
                                    "Shake detected! Reimagining portrait...", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onRetry()
                            }
                        }

                        lastX = x
                        lastY = y
                        lastZ = z
                        lastTime = now
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            var isRegistered = false

            fun registerSensor() {
                if (accelerometer != null && !isRegistered) {
                    sensorManager.registerListener(
                        sensorListener,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_UI
                    )
                    isRegistered = true
                }
            }

            fun unregisterSensor() {
                if (isRegistered) {
                    sensorManager.unregisterListener(sensorListener)
                    isRegistered = false
                }
            }

            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    registerSensor()
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    unregisterSensor()
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            // Register initially if we're currently resumed
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                registerSensor()
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                unregisterSensor()
            }
        }
    }

    // Trigger development animation when image becomes available
    LaunchedEffect(imageUrl, status, decodedBitmap, isImageLoaded) {
        if (status == ImageStatus.DONE && (decodedBitmap != null || (imageUrl != null && !imageUrl.startsWith("data:")))) {
            if (!isImageLoaded) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            isImageLoaded = true
            val triggeredMilestones = remember { mutableSetOf<Int>() }
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
            ) { value, _ ->
                developmentProgress = value
                
                // Fine-grained tactile ticks representing the chemical photo crystallization
                val percentage = (value * 100).toInt()
                
                // We define specific points for chemical reaction phase shifts:
                // 1. Initial fizzing/crystallization: light handle move ticks (fast pacing)
                // 2. Mid-stage detail solidification: steady rhythmic pulses
                // 3. Late-stage color binding: intense, slow-decaying feedback
                val chemicalMilestones = listOf(8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 95)
                for (milestone in chemicalMilestones) {
                    if (percentage >= milestone && milestone !in triggeredMilestones) {
                        triggeredMilestones.add(milestone)
                        // Alternate between ultra-light ticks and solid press feedbacks
                        if (milestone < 50) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            }
            // Final completion snap physical feedback: a double-haptic "clunk" representing the finished physical print
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(80)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            isImageLoaded = false
            developmentProgress = 0f
        }
    }

    Card(
        modifier = modifier
            .width(280.dp)
            .aspectRatio(3f / 4f)
            .graphicsLayer {
                scaleX = peekScale
                scaleY = peekScale
                rotationZ = rotation + peekRotationOffset
                translationX = animatedDragOffsetX
                translationY = animatedDragOffsetY
            }
            .pointerInput(status) {
                if (status != ImageStatus.PENDING) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y
                        },
                        onDragEnd = {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        }
                    )
                }
            }
            .shadow(
                elevation = peekElevation,
                shape = RoundedCornerShape(4.dp),
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black
            )
            .let { cardMod ->
                if (status == ImageStatus.DONE) {
                    cardMod.pointerInput(status, onCardClick) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPeeking = true
                            },
                            onTap = {
                                onCardClick?.invoke()
                            }
                        )
                    }
                } else {
                    cardMod
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)), // Nostalgic Polaroid clean off-white
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photo Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF111111)), // Dark camera inner well
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    ImageStatus.PENDING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFFACC15),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Reimagining...",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                    ImageStatus.ERROR -> {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error ?: "Failed to generate image.",
                                color = Color(0xFFEF4444),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            if (onRetry != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onRetry()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "Try Again",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    ImageStatus.DONE -> {
                        if (imageUrl != null) {
                            // Render base64 bitmap instantly if present, else fallback on Coil AsyncImage (for Uri)
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (decodedBitmap != null) {
                                    Image(
                                        bitmap = decodedBitmap,
                                        contentDescription = caption,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        // Dynamic sepia-adjust matrix blending up with development progress
                                        colorFilter = ColorFilter.colorMatrix(
                                            getChemicalDevelopmentMatrix(developmentProgress)
                                        )
                                    )
                                } else {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = caption,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        onSuccess = { isImageLoaded = true },
                                        colorFilter = ColorFilter.colorMatrix(
                                            getChemicalDevelopmentMatrix(developmentProgress)
                                        )
                                    )
                                }

                                // Photo Chemical Emulsion overlay (Fades out as image develops)
                                if (developmentProgress < 1f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color(0xFF3A322C).copy(alpha = (1f - developmentProgress) * 0.9f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Developing...",
                                            color = Color.White.copy(alpha = (1f - developmentProgress) * 0.6f),
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Interactive Quick-icons (Share/Download)
                                if (status == ImageStatus.DONE && developmentProgress >= 0.95f) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (onRetry != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .shadow(2.dp, RoundedCornerShape(14.dp))
                                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onRetry()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Regenerate",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Blank upload placeholder styled as cameras
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "✦",
                                    color = Color(0xFFE5E5E5),
                                    fontSize = 28.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ready",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Captions (Styled like vintage ink marker handwriting)
            Text(
                text = caption,
                color = Color.Black.copy(alpha = 0.85f),
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
