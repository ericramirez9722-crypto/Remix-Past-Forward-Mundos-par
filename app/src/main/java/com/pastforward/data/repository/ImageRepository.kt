package com.pastforward.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.pastforward.data.network.GeminiService
import com.pastforward.lib.AlbumUtils
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.util.Collections

class ImageRepository private constructor(private val context: Context) {

    // Selective dynamic caches separating light timeline thumbnails from heavy full-res overlays
    private val thumbnailCache = Collections.synchronizedMap(mutableMapOf<String, Bitmap>())
    private val fullResolutionCache = Collections.synchronizedMap(mutableMapOf<String, Bitmap>())

    init {
        // Configure Coil's ImageLoader with optimal disk and memory caching in Phase 3
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use up to 25% of the application's available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100 MB maximum disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }

    suspend fun generateStyledImage(
        base64Image: String,
        prompt: String,
        style: String
    ): Result<String> {
        return GeminiService.generateStyledImage(base64Image, prompt, style)
    }

    fun base64ToBitmap(base64Str: String): Bitmap? {
        return AlbumUtils.base64ToBitmap(base64Str)
    }

    /**
     * Decode and retrieve a downsampled thumbnail of the Base64 image.
     * Keeps memory footprint ultra-low (typically downsampled to 1/16th or 1/64th of the original's memory size).
     */
    fun getThumbnailBitmap(key: String, base64Str: String): Bitmap? {
        val existing = thumbnailCache[key]
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        
        // Downsample: standard cards are small (~240x320dp on device), so target 300x400 max
        val bitmap = decodeBase64ToBitmap(base64Str, reqWidth = 300, reqHeight = 400)
        if (bitmap != null) {
            thumbnailCache[key] = bitmap
        }
        return bitmap
    }

    /**
     * Decode and retrieve the full-resolution bitmap of the Base64 image on demand.
     * This is only kept in memory while the user is actively viewing this styled card in full-screen.
     */
    fun getFullResolutionBitmap(key: String, base64Str: String): Bitmap? {
        val existing = fullResolutionCache[key]
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        
        val bitmap = decodeBase64ToBitmap(base64Str, reqWidth = 0, reqHeight = 0) // decode at full scale
        if (bitmap != null) {
            fullResolutionCache[key] = bitmap
        }
        return bitmap
    }

    /**
     * Selectively recycle and release the heavy native heap memory of a specific full-resolution card.
     */
    fun releaseFullResolution(key: String) {
        val bitmap = fullResolutionCache.remove(key)
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    /**
     * Selectively releases all full-resolution bitmap memory from the caches, except for the specified active key.
     * When activeKey is null, it flushes ALL active full-resolution bitmaps directly into the garbage collector.
     */
    fun releaseAllFullResolutionsExcept(activeKey: String?) {
        synchronized(fullResolutionCache) {
            val iterator = fullResolutionCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key != activeKey) {
                    val bitmap = entry.value
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Clean up and recycle all bitmaps in caches, fully freeing memory.
     */
    fun clearAllCaches() {
        synchronized(thumbnailCache) {
            thumbnailCache.values.forEach { if (!it.isRecycled) it.recycle() }
            thumbnailCache.clear()
        }
        synchronized(fullResolutionCache) {
            fullResolutionCache.values.forEach { if (!it.isRecycled) it.recycle() }
            fullResolutionCache.clear()
        }
    }

    private fun decodeBase64ToBitmap(base64Str: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val cleanStr = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val decodedBytes = Base64.decode(cleanStr, Base64.DEFAULT)
            
            if (reqWidth > 0 && reqHeight > 0) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
            } else {
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        @Volatile
        private var INSTANCE: ImageRepository? = null

        fun getInstance(context: Context): ImageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
