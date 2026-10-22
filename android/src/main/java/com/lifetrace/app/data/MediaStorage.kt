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

data class MediaIsolationResult(
    val photos: List<DiaryPhotoEntity>,
    val migratedCount: Int,
)

class MediaStorage(private val context: Context) {
    private val mediaRoot = File(context.filesDir, "media")

    suspend fun importPhotos(entryId: String, uriStrings: List<String>): List<StoredPhoto> =
        withContext(Dispatchers.IO) {
            uriStrings.map { importOne(entryId, Uri.parse(it)) }
        }


    suspend fun importBackupPhotos(
        entryId: String,
        photos: List<DurableBackupPhoto>,
    ): List<DiaryPhotoEntity> = withContext(Dispatchers.IO) {
        photos.sortedBy { it.sortOrder }.mapIndexed { index, photo ->
            val extension = photo.sourceFile.extension.ifBlank { "jpg" }
            val directory = File(context.filesDir, "media/$entryId").apply { mkdirs() }
            val original = File(directory, photo.id + "." + extension)
            val thumbnail = File(directory, photo.id + "_thumb.jpg")
            photo.sourceFile.copyTo(original, overwrite = true)
            createThumbnail(original, thumbnail)
            DiaryPhotoEntity(
                id = photo.id,
                entryId = entryId,
                originalPath = original.absolutePath,
                thumbnailPath = thumbnail.absolutePath,
                mimeType = photo.mimeType,
                width = photo.width,
                height = photo.height,
                sortOrder = index,
            )
        }
    }

    suspend fun deleteFiles(photos: List<DiaryPhoto>) = withContext(Dispatchers.IO) {
        photos.forEach { photo ->
            if (isManagedPath(photo.originalPath)) File(photo.originalPath).delete()
            if (isManagedPath(photo.thumbnailPath)) File(photo.thumbnailPath).delete()
        }
    }

    suspend fun deleteEntryDirectory(entryId: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, "media/$entryId").deleteRecursively()
    }

    suspend fun ensurePrivateCopies(
        entryId: String,
        photos: List<DiaryPhotoEntity>,
    ): MediaIsolationResult = withContext(Dispatchers.IO) {
        var migrated = 0
        val isolated = photos.sortedBy { it.sortOrder }.map { photo ->
            val originalFile = File(photo.originalPath)
            val thumbnailFile = File(photo.thumbnailPath)
            if (
                isManagedPath(photo.originalPath) &&
                isManagedPath(photo.thumbnailPath) &&
                originalFile.isFile &&
                thumbnailFile.isFile
            ) {
                photo
            } else {
                val migratedPhoto = migratePhotoToPrivateStorage(entryId, photo)
                if (migratedPhoto != photo) migrated += 1
                migratedPhoto
            }
        }
        MediaIsolationResult(isolated, migrated)
    }

    private fun migratePhotoToPrivateStorage(
        entryId: String,
        photo: DiaryPhotoEntity,
    ): DiaryPhotoEntity {
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(photo.mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: File(photo.originalPath).extension.ifBlank { "jpg" }
        val directory = File(mediaRoot, entryId).apply { mkdirs() }
        val targetOriginal = File(directory, photo.id + "." + extension)
        val targetThumbnail = File(directory, photo.id + "_thumb.jpg")

        return runCatching {
            if (!sameFile(photo.originalPath, targetOriginal)) {
                copySource(photo.originalPath, targetOriginal)
            } else {
                require(targetOriginal.isFile) { "原图副本不存在" }
            }
            createThumbnail(targetOriginal, targetThumbnail)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(targetOriginal.absolutePath, bounds)
            photo.copy(
                originalPath = targetOriginal.absolutePath,
                thumbnailPath = targetThumbnail.absolutePath,
                width = bounds.outWidth.takeIf { it > 0 } ?: photo.width,
                height = bounds.outHeight.takeIf { it > 0 } ?: photo.height,
            )
        }.getOrElse {
            targetOriginal.takeIf { !sameFile(photo.originalPath, it) }?.delete()
            targetThumbnail.delete()
            photo
        }
    }

    private fun copySource(source: String, destination: File) {
        val uri = runCatching { Uri.parse(source) }.getOrNull()
        if (uri?.scheme == "content") {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取旧照片引用" }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            val sourceFile = File(source)
            require(sourceFile.isFile) { "旧照片文件不存在" }
            sourceFile.copyTo(destination, overwrite = true)
        }
    }

    private fun isManagedPath(path: String): Boolean {
        if (path.startsWith("content://")) return false
        return runCatching {
            val root = mediaRoot.canonicalFile
            val file = File(path).canonicalFile
            file.path == root.path || file.path.startsWith(root.path + File.separator)
        }.getOrDefault(false)
    }

    private fun sameFile(path: String, target: File): Boolean =
        runCatching { File(path).canonicalFile == target.canonicalFile }.getOrDefault(false)

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

            createThumbnail(original, thumbnail)

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


    private fun createThumbnail(original: File, thumbnail: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.absolutePath, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1920)
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
