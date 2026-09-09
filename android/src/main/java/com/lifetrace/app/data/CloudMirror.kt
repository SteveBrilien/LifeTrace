package com.lifetrace.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

data class CloudMirrorStatus(
    val enabled: Boolean,
    val folderName: String?,
    val syncing: Boolean = false,
    val message: String = if (enabled) "等待同步" else "尚未选择外部目录",
)

class CloudMirror(private val context: Context) {
    private val preferences = context.getSharedPreferences("cloud_mirror", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(loadStatus())
    val status: StateFlow<CloudMirrorStatus> = _status

    val isEnabled: Boolean get() = preferences.getString(KEY_TREE_URI, null) != null

    suspend fun configure(treeUri: Uri, folderName: String?) = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        preferences.edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putString(KEY_FOLDER_NAME, folderName ?: "外部目录")
            .apply()
        _status.value = CloudMirrorStatus(true, folderName, message = "目录已连接，正在同步现有日记")
    }

    fun disconnect() {
        preferences.edit().clear().apply()
        _status.value = CloudMirrorStatus(false, null)
    }

    suspend fun mirrorAll(entries: List<DiaryEntry>) = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext
        update(true, "正在同步 " + entries.size + " 篇日记…")
        runCatching { entries.forEach { writeEntry(it, false) } }
            .onSuccess { update(false, "已同步 " + entries.size + " 篇日记") }
            .onFailure {
                update(false, "同步失败：" + it.readableMessage())
                throw it
            }
    }

    suspend fun backupEntry(entry: DiaryEntry) = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext
        update(true, "正在同步刚保存的日记…")
        runCatching { writeEntry(entry, false) }
            .onSuccess { update(false, "最新日记已同步") }
            .onFailure { update(false, "同步失败：" + it.readableMessage()) }
    }

    suspend fun archiveDeleted(entry: DiaryEntry) = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext
        update(true, "正在把删除内容移入外部目录 Trash…")
        runCatching {
            writeEntry(entry, true)
            entriesRoot().findFile(entry.id)?.delete()
        }.onSuccess {
            update(false, "已移入外部目录 Trash")
        }.onFailure {
            update(false, "外部目录归档失败：" + it.readableMessage())
            throw IllegalStateException("外部目录归档失败，本地日记未删除", it)
        }
    }

    private fun writeEntry(entry: DiaryEntry, trash: Boolean) {
        val parent = if (trash) trashRoot() else entriesRoot()
        val directoryName = if (trash) {
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + "-" + entry.id
        } else entry.id
        val directory = parent.findFile(directoryName)?.also {
            if (!it.isDirectory) it.delete()
        } ?: parent.createDirectory(directoryName)
        requireNotNull(directory) { "无法创建日记目录" }

        directory.findFile("entry.json")?.delete()
        val jsonFile = directory.createFile("application/json", "entry.json")
        requireNotNull(jsonFile) { "无法创建 entry.json" }
        context.contentResolver.openOutputStream(jsonFile.uri, "w").use { output ->
            requireNotNull(output) { "无法写入 entry.json" }
            output.write(entry.toJson().toString(2).toByteArray(Charsets.UTF_8))
        }

        directory.findFile("photos")?.delete()
        val photos = directory.createDirectory("photos")
        requireNotNull(photos) { "无法创建照片目录" }
        entry.photos.forEachIndexed { index, photo ->
            val source = File(photo.originalPath)
            if (!source.isFile) return@forEachIndexed
            val target = photos.createFile(
                photo.mimeType.ifBlank { "image/jpeg" },
                "%02d_%s.%s".format(index + 1, photo.id, source.extension.ifBlank { "jpg" }),
            )
            requireNotNull(target) { "无法创建外部目录照片" }
            context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                requireNotNull(output) { "无法写入外部目录照片" }
                source.inputStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private fun root(): DocumentFile {
        val value = preferences.getString(KEY_TREE_URI, null) ?: error("尚未选择外部目录")
        val selected = DocumentFile.fromTreeUri(context, Uri.parse(value))
        requireNotNull(selected) { "外部目录授权已经失效，请重新选择目录" }
        require(selected.canWrite()) { "外部目录当前不可写，请重新授权" }
        return selected.findFile(ROOT_FOLDER)?.takeIf { it.isDirectory }
            ?: requireNotNull(selected.createDirectory(ROOT_FOLDER)) { "无法创建 LifeTrace 目录" }
    }

    private fun entriesRoot(): DocumentFile =
        root().findFile(ENTRIES_FOLDER)?.takeIf { it.isDirectory }
            ?: requireNotNull(root().createDirectory(ENTRIES_FOLDER))

    private fun trashRoot(): DocumentFile =
        root().findFile(TRASH_FOLDER)?.takeIf { it.isDirectory }
            ?: requireNotNull(root().createDirectory(TRASH_FOLDER))

    private fun DiaryEntry.toJson() = JSONObject().apply {
        put("schemaVersion", 1)
        put("id", id)
        put("body", body)
        put("occurredAt", occurredAt)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("latitude", latitude ?: JSONObject.NULL)
        put("longitude", longitude ?: JSONObject.NULL)
        put("placeLabel", placeLabel)
        put("photos", JSONArray().apply {
            photos.forEach { photo ->
                put(JSONObject().apply {
                    put("id", photo.id)
                    put("mimeType", photo.mimeType)
                    put("width", photo.width)
                    put("height", photo.height)
                    put("sortOrder", photo.sortOrder)
                })
            }
        })
    }

    private fun update(syncing: Boolean, message: String) {
        _status.value = _status.value.copy(syncing = syncing, message = message)
    }

    private fun loadStatus(): CloudMirrorStatus {
        val uri = preferences.getString(KEY_TREE_URI, null)
        return CloudMirrorStatus(
            enabled = uri != null,
            folderName = preferences.getString(KEY_FOLDER_NAME, null),
        )
    }

    private fun Throwable.readableMessage(): String = message ?: javaClass.simpleName

    private companion object {
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_FOLDER_NAME = "folder_name"
        const val ROOT_FOLDER = "LifeTrace"
        const val ENTRIES_FOLDER = "Entries"
        const val TRASH_FOLDER = "Trash"
    }
}
