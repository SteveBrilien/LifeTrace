package com.lifetrace.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredPhoto(
    val id: String,
    val originalPath: String,
    val thumbnailPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

class MediaStorage(private val context: Context) {
    suspend fun importPhotos(entryId: String, uriStrings: List<String>): List<StoredPhoto> =
        withContext(Dispatchers.IO) {
            uriStrings.map { importOne(entryId, Uri.parse(it)) }
        }

    suspend fun deleteFiles(photos: List<DiaryPhoto>) = withContext(Dispatchers.IO) {
        photos.forEach { photo ->
            File(photo.originalPath).delete()
            File(photo.thumbnailPath).delete()
        }
    }

    suspend fun deleteEntryDirectory(entryId: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, "media/$entryId").deleteRecursively()
    }

    private fun importOne(entryId: String, uri: Uri): StoredPhoto {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "media/$entryId").apply { mkdirs() }
        val original = File(directory, id + "." + extension)
        val thumbnail = File(directory, id + "_thumb.jpg")

        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选图片" }
                original.outputStream().use { output -> input.copyTo(output) }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.absolutePath, bounds)
            val width = bounds.outWidth.coerceAtLeast(0)
            val height = bounds.outHeight.coerceAtLeast(0)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(width, height, 1920)
            }
            val bitmap = BitmapFactory.decodeFile(original.absolutePath, decodeOptions)
            if (bitmap != null) {
                val maxSide = maxOf(bitmap.width, bitmap.height)
                val scale = if (maxSide > 1440) 1440f / maxSide else 1f
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    bitmap
                }
                FileOutputStream(thumbnail).use {
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            } else {
                original.copyTo(thumbnail, overwrite = true)
            }

            return StoredPhoto(
                id = id,
                originalPath = original.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
                mimeType = mimeType,
                width = width,
                height = height,
            )
        } catch (error: Throwable) {
            original.delete()
            thumbnail.delete()
            throw error
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, requested: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= requested || height / (sample * 2) >= requested) {
            sample *= 2
        }
        return sample
    }
}
