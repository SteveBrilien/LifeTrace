package com.lifetrace.app.data

import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DiaryPhoto(
    val id: String,
    val originalPath: String,
    val thumbnailPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
)

data class DiaryEntry(
    val id: String,
    val body: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeLabel: String,
    val photos: List<DiaryPhoto>,
)

data class SaveDiaryRequest(
    val id: String?,
    val body: String,
    val occurredAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeLabel: String,
    val retainedPhotoIds: List<String>,
    val newPhotoUris: List<String>,
)

class DiaryRepository(
    private val dao: DiaryDao,
    private val mediaStorage: MediaStorage,
    private val cloudMirror: CloudMirror,
) {
    val entries: Flow<List<DiaryEntry>> = dao.observeAll().map { rows ->
        rows.map { it.toModel() }
    }
    val cloudStatus = cloudMirror.status

    suspend fun save(request: SaveDiaryRequest): String {
        val now = System.currentTimeMillis()
        val old = request.id?.let { dao.getById(it) }
        val entryId = request.id ?: UUID.randomUUID().toString()
        val retainedIds = request.retainedPhotoIds.toSet()
        val retained = old?.photos
            ?.filter { it.id in retainedIds }
            ?.sortedBy { it.sortOrder }
            .orEmpty()
        val availableSlots = (9 - retained.size).coerceAtLeast(0)
        val imported = mediaStorage.importPhotos(
            entryId,
            request.newPhotoUris.take(availableSlots),
        )

        val allPhotos = buildList {
            retained.forEach { photo ->
                add(photo.copy(sortOrder = size))
            }
            imported.forEach { photo ->
                add(
                    DiaryPhotoEntity(
                        id = photo.id,
                        entryId = entryId,
                        originalPath = photo.originalPath,
                        thumbnailPath = photo.thumbnailPath,
                        mimeType = photo.mimeType,
                        width = photo.width,
                        height = photo.height,
                        sortOrder = size,
                    ),
                )
            }
        }

        val entity = DiaryEntryEntity(
            id = entryId,
            body = request.body.trim(),
            occurredAt = request.occurredAt,
            createdAt = old?.entry?.createdAt ?: now,
            updatedAt = now,
            latitude = request.latitude,
            longitude = request.longitude,
            placeLabel = request.placeLabel.trim(),
        )

        try {
            dao.replaceEntry(entity, allPhotos)
        } catch (error: Throwable) {
            imported.forEach {
                mediaStorage.deleteFiles(
                    listOf(
                        DiaryPhoto(
                            id = it.id,
                            originalPath = it.originalPath,
                            thumbnailPath = it.thumbnailPath,
                            mimeType = it.mimeType,
                            width = it.width,
                            height = it.height,
                            sortOrder = 0,
                        ),
                    ),
                )
            }
            throw error
        }

        val removed = old?.photos
            ?.filterNot { photo -> allPhotos.any { it.id == photo.id } }
            ?.map { it.toModel() }
            .orEmpty()
        mediaStorage.deleteFiles(removed)
        dao.getById(entryId)?.toModel()?.let { cloudMirror.backupEntry(it) }
        return entryId
    }

    suspend fun delete(id: String) {
        dao.getById(id)?.toModel()?.let { cloudMirror.archiveDeleted(it) }
        dao.deleteEntry(id)
        mediaStorage.deleteEntryDirectory(id)
    }

    suspend fun configureCloudMirror(treeUri: Uri, folderName: String?) {
        cloudMirror.configure(treeUri, folderName)
        cloudMirror.mirrorAll(dao.getAll().map { it.toModel() })
    }

    fun disconnectCloudMirror() = cloudMirror.disconnect()

    private fun DiaryWithPhotos.toModel() = DiaryEntry(
        id = entry.id,
        body = entry.body,
        occurredAt = entry.occurredAt,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
        latitude = entry.latitude,
        longitude = entry.longitude,
        placeLabel = entry.placeLabel,
        photos = photos.sortedBy { it.sortOrder }.map { it.toModel() },
    )

    private fun DiaryPhotoEntity.toModel() = DiaryPhoto(
        id = id,
        originalPath = originalPath,
        thumbnailPath = thumbnailPath,
        mimeType = mimeType,
        width = width,
        height = height,
        sortOrder = sortOrder,
    )
}
