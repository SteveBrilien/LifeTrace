package com.lifetrace.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DurableStorageStatus(
    val enabled: Boolean,
    val rootPath: String,
    val syncing: Boolean = false,
    val message: String,
)

data class DurableBackupPhoto(
    val id: String,
    val sourceFile: File,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
)

data class DurableBackupEntry(
    val id: String,
    val body: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val placeLabel: String,
    val photos: List<DurableBackupPhoto>,
)

class DurableBackupStore(private val context: Context) {
    private val rootDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        ROOT_FOLDER,
    )
    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<DurableStorageStatus> = _status

    fun refreshAccess(): Boolean {
        val enabled = hasDirectFileAccess()
        _status.value = currentStatus()
        return enabled
    }

    suspend fun mirrorAll(entries: List<DiaryEntry>) = withContext(Dispatchers.IO) {
        if (!hasDirectFileAccess()) {
            _status.value = currentStatus()
            return@withContext
        }
        update(true, "正在同步永久本地备份…")
        runCatching {
            val entriesRoot = entriesRoot()
            val activeIds = entries.map { it.id }.toSet()
            entriesRoot.listFiles().orEmpty()
                .filter { it.isDirectory && it.name !in activeIds }
                .forEach { it.deleteRecursively() }
            entries.forEach { writeEntry(it, File(entriesRoot, it.id)) }
        }.onSuccess {
            update(false, "已同步 ${entries.size} 篇到 Documents/LifeTrace")
        }.onFailure {
            update(false, "永久本地备份失败：${it.message ?: it.javaClass.simpleName}")
            throw it
        }
    }

    suspend fun backupEntry(entry: DiaryEntry) = withContext(Dispatchers.IO) {
        if (!hasDirectFileAccess()) return@withContext
        update(true, "正在写入永久本地备份…")
        runCatching { writeEntry(entry, File(entriesRoot(), entry.id)) }
            .onSuccess { update(false, "已写入 Documents/LifeTrace") }
            .onFailure { update(false, "永久本地备份失败：${it.message ?: it.javaClass.simpleName}") }
    }

    suspend fun archiveDeleted(entry: DiaryEntry) = withContext(Dispatchers.IO) {
        if (!hasDirectFileAccess()) return@withContext
        update(true, "正在归档删除内容…")
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            writeEntry(entry, File(trashRoot(), "$stamp-${entry.id}"))
            File(entriesRoot(), entry.id).deleteRecursively()
        }.onSuccess {
            update(false, "删除内容已保存在 Documents/LifeTrace/Trash")
        }.onFailure {
            update(false, "本地归档失败：${it.message ?: it.javaClass.simpleName}")
            throw it
        }
    }

    suspend fun readEntries(): List<DurableBackupEntry> = withContext(Dispatchers.IO) {
        if (!hasDirectFileAccess()) return@withContext emptyList()
        val discovered = discoverBackupRoots()
            .asSequence()
            .map { File(it, ENTRIES_FOLDER) }
            .filter { it.isDirectory }
            .flatMap { entriesDirectory ->
                entriesDirectory.listFiles().orEmpty().asSequence()
                    .filter { it.isDirectory && File(it, ENTRY_FILE).isFile }
            }
            .mapNotNull { directory -> runCatching { readEntry(directory) }.getOrNull() }
            .distinctBy { it.id }
            .sortedByDescending { it.occurredAt }
            .toList()
        discovered
    }

    private fun discoverBackupRoots(): List<File> {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return buildList {
            add(rootDirectory)
            add(File(downloads, ROOT_FOLDER))
            listOf(documents, downloads).forEach { base ->
                base.listFiles().orEmpty()
                    .filter { it.isDirectory }
                    .forEach { child ->
                        val nested = File(child, ROOT_FOLDER)
                        if (File(nested, ENTRIES_FOLDER).isDirectory) add(nested)
                    }
            }
        }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    }

    fun reportRestore(count: Int) {
        _status.value = currentStatus().copy(
            message = if (count > 0) {
                "已从 Documents/LifeTrace 自动恢复 $count 篇日记"
            } else {
                "永久本地备份已启用"
            },
        )
    }

    private fun hasDirectFileAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun currentStatus(): DurableStorageStatus {
        val enabled = hasDirectFileAccess()
        return DurableStorageStatus(
            enabled = enabled,
            rootPath = rootDirectory.absolutePath,
            message = if (enabled) {
                "已启用；更新/卸载不会删除 Documents/LifeTrace"
            } else {
                "未启用；授予文件访问权限后会自动同步并在重装后恢复"
            },
        )
    }

    private fun entriesRoot(): File = File(rootDirectory, ENTRIES_FOLDER).apply { mkdirs() }
    private fun trashRoot(): File = File(rootDirectory, TRASH_FOLDER).apply { mkdirs() }

    private fun writeEntry(entry: DiaryEntry, destination: File) {
        rootDirectory.mkdirs()
        val temp = File(destination.parentFile, ".${destination.name}.tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        val photosDirectory = File(temp, PHOTOS_FOLDER).apply { mkdirs() }
        val photosJson = JSONArray()

        entry.photos.sortedBy { it.sortOrder }.forEachIndexed { index, photo ->
            val source = File(photo.originalPath)
            if (!source.isFile) return@forEachIndexed
            val extension = source.extension.ifBlank { "jpg" }
            val fileName = "%02d_%s.%s".format(index + 1, photo.id, extension)
            source.copyTo(File(photosDirectory, fileName), overwrite = true)
            photosJson.put(JSONObject().apply {
                put("id", photo.id)
                put("fileName", fileName)
                put("mimeType", photo.mimeType)
                put("width", photo.width)
                put("height", photo.height)
                put("sortOrder", index)
            })
        }

        File(temp, ENTRY_FILE).writeText(JSONObject().apply {
            put("schemaVersion", 2)
            put("id", entry.id)
            put("body", entry.body)
            put("occurredAt", entry.occurredAt)
            put("createdAt", entry.createdAt)
            put("updatedAt", entry.updatedAt)
            put("latitude", entry.latitude ?: JSONObject.NULL)
            put("longitude", entry.longitude ?: JSONObject.NULL)
            put("placeLabel", entry.placeLabel)
            put("photos", photosJson)
        }.toString(2))

        destination.deleteRecursively()
        if (!temp.renameTo(destination)) {
            temp.copyRecursively(destination, overwrite = true)
            temp.deleteRecursively()
        }
    }

    private fun readEntry(directory: File): DurableBackupEntry {
        val json = JSONObject(File(directory, ENTRY_FILE).readText())
        val photosDirectory = File(directory, PHOTOS_FOLDER)
        val photoArray = json.optJSONArray("photos") ?: JSONArray()
        val photos = buildList {
            for (index in 0 until photoArray.length()) {
                val item = photoArray.getJSONObject(index)
                val fileName = item.optString("fileName")
                val source = File(photosDirectory, fileName)
                if (!source.isFile) continue
                add(
                    DurableBackupPhoto(
                        id = item.getString("id"),
                        sourceFile = source,
                        mimeType = item.optString("mimeType", "image/jpeg"),
                        width = item.optInt("width", 0),
                        height = item.optInt("height", 0),
                        sortOrder = item.optInt("sortOrder", index),
                    ),
                )
            }
        }
        return DurableBackupEntry(
            id = json.getString("id"),
            body = json.optString("body"),
            occurredAt = json.getLong("occurredAt"),
            createdAt = json.optLong("createdAt", json.getLong("occurredAt")),
            updatedAt = json.optLong("updatedAt", json.getLong("occurredAt")),
            latitude = json.optNullableDouble("latitude"),
            longitude = json.optNullableDouble("longitude"),
            placeLabel = json.optString("placeLabel"),
            photos = photos.sortedBy { it.sortOrder },
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun update(syncing: Boolean, message: String) {
        _status.value = currentStatus().copy(syncing = syncing, message = message)
    }

    private companion object {
        const val ROOT_FOLDER = "LifeTrace"
        const val ENTRIES_FOLDER = "Entries"
        const val TRASH_FOLDER = "Trash"
        const val PHOTOS_FOLDER = "photos"
        const val ENTRY_FILE = "entry.json"
    }
}
