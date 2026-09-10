package com.lifetrace.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lifetrace.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

data class UpdateStatus(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val available: AppUpdate? = null,
    val downloadedPath: String? = null,
    val message: String = "尚未检查更新",
)

class AppUpdater(private val context: Context) {
    suspend fun check(): AppUpdate = withContext(Dispatchers.IO) {
        val connection = (URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LifeTrace/${BuildConfig.VERSION_NAME}")
        }
        try {
            require(connection.responseCode in 200..299) { "更新服务器返回 HTTP ${connection.responseCode}" }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val url = json.getString("apkUrl")
            val parsedUrl = Uri.parse(url)
            val sha256 = json.getString("sha256").lowercase()
            require(parsedUrl.scheme == "https") { "更新包必须使用 HTTPS" }
            require(parsedUrl.host == "trace.wmy-cloud.cn") { "更新包必须来自 trace.wmy-cloud.cn" }
            require(sha256.matches(Regex("[0-9a-f]{64}"))) { "更新清单 SHA-256 格式无效" }
            AppUpdate(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = url,
                sha256 = sha256,
                notes = json.optString("notes").ifBlank { json.optString("releaseNotes") },
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(update: AppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val root = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        val target = File(root, "LifeTrace-${update.versionName}.apk")
        val partial = File(root, target.name + ".part")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LifeTrace/${BuildConfig.VERSION_NAME}")
        }
        try {
            require(connection.responseCode in 200..299) { "APK 下载返回 HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (total > 0L) onProgress(((copied * 100L) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            val actual = sha256(partial)
            require(actual.equals(update.sha256, ignoreCase = true)) { "APK 校验失败，已拒绝安装" }
            if (target.exists()) target.delete()
            require(partial.renameTo(target)) { "无法保存下载完成的 APK" }
            target
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    fun launchInstaller(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
