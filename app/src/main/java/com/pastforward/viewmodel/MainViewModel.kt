package com.pastforward.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pastforward.data.repository.ImageRepository
import com.pastforward.ui.components.ImageStatus
import com.pastforward.lib.AlbumUtils
import com.pastforward.ui.screens.AppState
import com.pastforward.ui.screens.Mode
import com.pastforward.ui.screens.GeneratedImage
import com.pastforward.ui.screens.DECADES
import com.pastforward.ui.screens.PARALLEL_WORLDS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.os.PowerManager
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver

data class MainUiState(
    val imageUri: Uri? = null,
    val appState: AppState = AppState.IDLE,
    val currentMode: Mode = Mode.DECADES,
    val generatedImages: Map<String, GeneratedImage> = emptyMap(),
    val isDownloading: Boolean = false,
    val activeFullScreenImage: String? = null,
    val activeFullScreenCaption: String? = null,
    val activeFullScreenStyle: String? = null,
    val isLowPowerMode: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var imageRepository: ImageRepository? = null
    private var generateJob: Job? = null
    private val regenerateJobs = mutableMapOf<String, Job>()
    private var powerSaveReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (imageRepository == null) {
            imageRepository = ImageRepository.getInstance(app)
        }
        registerPowerSaveReceiver(app)
    }

    private fun registerPowerSaveReceiver(context: Context) {
        if (powerSaveReceiver != null) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val initialPowerSave = powerManager?.isPowerSaveMode == true
        _uiState.update { it.copy(isLowPowerMode = initialPowerSave) }

        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isPowerSave = powerManager?.isPowerSaveMode == true
                val action = intent?.action
                val isBatteryLow = action == Intent.ACTION_BATTERY_LOW
                val isBatteryOk = action == Intent.ACTION_BATTERY_OKAY
                
                val isLowPower = if (isBatteryLow) {
                    true
                } else if (isBatteryOk) {
                    isPowerSave
                } else {
                    isPowerSave
                }

                _uiState.update { state ->
                    state.copy(isLowPowerMode = isLowPower)
                }
            }
        }

        try {
            context.registerReceiver(receiver, filter)
            powerSaveReceiver = receiver
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun setImageUri(uri: Uri?) {
        _uiState.update { state ->
            state.copy(
                imageUri = uri,
                appState = if (uri != null) AppState.IMAGE_UPLOADED else AppState.IDLE,
                generatedImages = emptyMap()
            )
        }
    }

    fun setMode(mode: Mode) {
        _uiState.update { state ->
            state.copy(currentMode = mode)
        }
    }

    fun setFullScreenImage(url: String?, caption: String?, style: String? = null) {
        _uiState.update { state ->
            state.copy(
                activeFullScreenImage = url,
                activeFullScreenCaption = caption,
                activeFullScreenStyle = style
            )
        }
        // Memory Optimization: selectively release full-resolution bitmap memory for inactive cards
        imageRepository?.releaseAllFullResolutionsExcept(style)
    }

    fun handleGenerateClick(context: Context, onFailedToRead: () -> Unit) {
        val currentState = _uiState.value
        val currentUri = currentState.imageUri ?: return
        val currentMode = currentState.currentMode
        val items = if (currentMode == Mode.DECADES) DECADES else PARALLEL_WORLDS

        // Cancel previous full generation and single-card developments
        generateJob?.cancel()
        regenerateJobs.values.forEach { it.cancel() }
        regenerateJobs.clear()

        _uiState.update { state ->
            val initialImages = items.associateWith { GeneratedImage(ImageStatus.PENDING) }
            state.copy(
                appState = AppState.GENERATING,
                generatedImages = initialImages
            )
        }

        generateJob = viewModelScope.launch {
            val base64 = AlbumUtils.uriToBase64(context, currentUri)
            if (base64 == null) {
                _uiState.update { state -> state.copy(appState = AppState.IMAGE_UPLOADED) }
                onFailedToRead()
                return@launch
            }

            for (i in items.indices) {
                val style = items[i]

                // Keep parity with 6-second rate limit safety delay to avoid 429 quota exhaustion
                if (i > 0) {
                    delay(6000L)
                }

                val prompt = "Reimagine the person in this photo in the style of the $style. " +
                        "This includes clothing, hairstyle, photo quality, and the overall aesthetic of that " +
                        "${if (currentMode == Mode.DECADES) "decade" else "world"}. " +
                        "The output must be a photorealistic image showing the person clearly."

                val repo = imageRepository ?: ImageRepository.getInstance(context)
                val responseResult = repo.generateStyledImage(base64, prompt, style)
                
                _uiState.update { state ->
                    val updatedMap = state.generatedImages.toMutableMap()
                    responseResult.onSuccess { base64Url ->
                        updatedMap[style] = GeneratedImage(ImageStatus.DONE, base64Url)
                    }.onFailure { error ->
                        updatedMap[style] = GeneratedImage(ImageStatus.ERROR, null, error.message)
                    }
                    state.copy(generatedImages = updatedMap)
                }
            }
            _uiState.update { state -> state.copy(appState = AppState.RESULTS_SHOWN) }
        }
    }

    fun handleRegenerateItem(context: Context, style: String) {
        val currentState = _uiState.value
        val currentUri = currentState.imageUri ?: return
        val currentMode = currentState.currentMode

        // Cancel previous in-flight recreation for this specific card
        regenerateJobs[style]?.cancel()

        _uiState.update { state ->
            val updatedMap = state.generatedImages.toMutableMap()
            updatedMap[style] = GeneratedImage(ImageStatus.PENDING)
            state.copy(generatedImages = updatedMap)
        }

        val job = viewModelScope.launch {
            // Keep parity with 3-second guard delay for manual refresh requests
            delay(3000L)

            val base64 = AlbumUtils.uriToBase64(context, currentUri) ?: return@launch
            val prompt = "Reimagine the person in this photo in the style of the $style. " +
                    "This includes clothing, hairstyle, photo quality, and the overall aesthetic of that " +
                    "${if (currentMode == Mode.DECADES) "decade" else "world"}. " +
                    "The output must be a photorealistic image showing the person clearly."

            val repo = imageRepository ?: ImageRepository.getInstance(context)
            val responseResult = repo.generateStyledImage(base64, prompt, style)

            _uiState.update { state ->
                val updatedMap = state.generatedImages.toMutableMap()
                responseResult.onSuccess { base64Url ->
                    updatedMap[style] = GeneratedImage(ImageStatus.DONE, base64Url)
                }.onFailure { error ->
                    updatedMap[style] = GeneratedImage(ImageStatus.ERROR, null, error.message)
                }
                state.copy(generatedImages = updatedMap)
            }
        }
        regenerateJobs[style] = job
    }

    fun handleDownloadAlbum(context: Context, onSuccess: () -> Unit, onFailure: () -> Unit) {
        val currentState = _uiState.value
        val items = if (currentState.currentMode == Mode.DECADES) DECADES else PARALLEL_WORLDS
        val completedImages = currentState.generatedImages
            .filter { it.value.status == ImageStatus.DONE && it.value.url != null }
            .mapValues { it.value.url!! }

        if (completedImages.size < items.size) {
            onFailure()
            return
        }

        _uiState.update { state -> state.copy(isDownloading = true) }

        viewModelScope.launch {
            val titleStr = if (currentState.currentMode == Mode.DECADES) "Time-Travel-Album" else "Multiverse-Album"
            val saveResult = AlbumUtils.createAndSaveAlbum(context, completedImages, titleStr)
            
            saveResult.onSuccess {
                onSuccess()
            }.onFailure {
                onFailure()
            }
            
            _uiState.update { state -> state.copy(isDownloading = false) }
        }
    }

    fun handleReset() {
        // Cancel all active coroutines on reset
        generateJob?.cancel()
        regenerateJobs.values.forEach { it.cancel() }
        regenerateJobs.clear()
        
        // Memory Optimization: Clear and recycle all bitmaps on reset
        imageRepository?.clearAllCaches()
        
        _uiState.value = MainUiState()
    }

    override fun onCleared() {
        super.onCleared()
        powerSaveReceiver?.let { receiver ->
            try {
                appContext?.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
        powerSaveReceiver = null
        appContext = null
    }
}
