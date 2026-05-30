package com.pastforward.lib

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.io.OutputStream

object AlbumUtils {
    private const val TAG = "AlbumUtils"

    // Reads Uri stream and converts it directly to a clean Base64 string for the API call
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed convert Uri to Base64: ${e.message}")
            null
        }
    }

    // Direct Base64 to Bitmap converter
    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val cleanStr = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
            val decodedBytes = Base64.decode(cleanStr, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Composites a gorgeous high-resolution 2x3 grid collage of the generated Polaroid cards.
     * Draws authentic white paper margins and hand-written style titles onto a single dark album canvas.
     */
    fun createAndSaveAlbum(
        context: Context,
        imageData: Map<String, String>, // Map of Style -> Base64 data Url
        title: String = "Past Forward Album"
    ): Result<Uri> {
        return try {
            // High-resolution canvas size: 1800 x 2400 (perfect aspect-ratio for standard photos)
            val albumWidth = 1800
            val albumHeight = 2400
            val bitmap = Bitmap.createBitmap(albumWidth, albumHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw a lux dark grid background matching the web app
            val bgPaint = Paint().apply {
                color = Color.parseColor("#0F0F11") // Slate pitch black
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, albumWidth.toFloat(), albumHeight.toFloat(), bgPaint)

            // Draw album header text
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 80f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
            }
            canvas.drawText("Past Forward", (albumWidth / 2).toFloat(), 150f, titlePaint)

            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#FACC15") // Warm golden yellow
                textSize = 40f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
            }
            canvas.drawText("M UNDOS  PARALELOS", (albumWidth / 2).toFloat(), 210f, subtitlePaint)

            // Layout 2x3 Grid of Polaroids
            // Row offsets: Y starts around 300, ends around 2300
            val columns = 2
            val rows = 3
            val gridPaddingX = 80f
            val gridPaddingY = 50f
            val gridTopY = 280f

            val cellWidth = (albumWidth - (gridPaddingX * 3)) / columns // 820 width
            val cellHeight = (albumHeight - gridTopY - (gridPaddingY * 4)) / rows // 620 height

            val styles = imageData.keys.toList()

            for (i in styles.indices) {
                val style = styles[i]
                val base64Data = imageData[style] ?: continue
                val sourceBitmap = base64ToBitmap(base64Data) ?: continue

                val row = i / columns
                val col = i % columns

                val left = gridPaddingX + col * (cellWidth + gridPaddingX)
                val topLoc = gridTopY + gridPaddingY + row * (cellHeight + gridPaddingY)
                val right = left + cellWidth
                val bottom = topLoc + cellHeight

                // Draw White Polaroid Card Structure on the canvas matrix
                val cardBounds = RectF(left, topLoc, right, bottom)
                val cardPaint = Paint().apply {
                    color = Color.parseColor("#F9FAFB")
                    isAntiAlias = true
                }
                // Custom slightly rounded card edges (10px)
                canvas.drawRoundRect(cardBounds, 12f, 12f, cardPaint)

                // Sub-shadow ambient edge tracing
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#E5E7EB")
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                canvas.drawRoundRect(cardBounds, 12f, 12f, borderPaint)

                // Render inner image well (leaving standard thick bottom margin of Polaroid)
                val imagePadding = 25f
                val imageBottomPadding = 120f
                val imgLeft = left + imagePadding
                val imgTop = topLoc + imagePadding
                val imgRight = right - imagePadding
                val imgBottom = bottom - imageBottomPadding

                val imageBounds = RectF(imgLeft, imgTop, imgRight, imgBottom)

                // Draw image cropped in place
                val saveCount = canvas.save()
                canvas.clipRect(imageBounds)
                
                // Draw decoded image bitmap matching Rect bounds
                val srcRect = android.graphics.Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
                canvas.drawBitmap(sourceBitmap, srcRect, imageBounds, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restoreToCount(saveCount)

                // Draw Handwritten label caption at the bottom margin
                val captionPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 34f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
                }
                
                val captionText = "$style Self-Portrait"
                val captionX = left + (cellWidth / 2)
                val captionY = bottom - 40f
                canvas.drawText(captionText, captionX, captionY, captionPaint)
            }

            // Save composite back to MediaStore
            saveBitmapToGallery(context, bitmap, "$title-${System.currentTimeMillis()}.jpg")

        } catch (e: Exception) {
            Log.e(TAG, "Collage aggregation failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Result<Uri> {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PastForward")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure(Exception("Failed to open MediaStore insert location."))

        return try {
            val stream: OutputStream = resolver.openOutputStream(uri)
                ?: throw Exception("Could not resolve directory stream.")
            
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            stream.flush()
            stream.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Result.success(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    }
}
