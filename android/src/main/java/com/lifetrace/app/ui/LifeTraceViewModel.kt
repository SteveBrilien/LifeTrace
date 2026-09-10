package com.lifetrace.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrace.app.BuildConfig
import com.lifetrace.app.LifeTraceApplication
import com.lifetrace.app.data.AppUpdater
import com.lifetrace.app.data.DiaryEntry
import com.lifetrace.app.data.SaveDiaryRequest
import com.lifetrace.app.data.UpdateStatus
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LifeTraceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LifeTraceApplication).repository
    private val updater = AppUpdater(application)
    private val _updateStatus = MutableStateFlow(UpdateStatus())

    val entries: StateFlow<List<DiaryEntry>> = repository.entries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val durableStatus = repository.durableStatus
    val updateStatus = _updateStatus.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshDurableStorageAccess() }
    }

    fun save(
        request: SaveDiaryRequest,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.save(request) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "保存失败，请重试") }
        }
    }

    fun refreshDurableStorageAccess(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.refreshDurableStorageAccess() }
                .onFailure { onError(it.message ?: "永久本地备份初始化失败") }
        }
    }

    fun syncDurableBackup(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.syncDurableBackup() }
                .onFailure { onError(it.message ?: "永久本地备份同步失败") }
        }
    }

    fun delete(
        id: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "删除失败，请重试") }
        }
    }

    fun checkForUpdates() {
        val current = _updateStatus.value
        if (current.checking || current.downloading) return
        _updateStatus.value = current.copy(checking = true, message = "正在检查更新…")
        viewModelScope.launch {
            runCatching { updater.check() }
                .onSuccess { update ->
                    _updateStatus.value = if (update.versionCode > BuildConfig.VERSION_CODE) {
                        UpdateStatus(available = update, message = "发现 LifeTrace ${update.versionName}")
                    } else {
                        UpdateStatus(message = "已是最新版本 ${BuildConfig.VERSION_NAME}")
                    }
                }
                .onFailure { error ->
                    _updateStatus.value = UpdateStatus(
                        message = "检查失败：${error.message ?: "网络错误"}",
                    )
                }
        }
    }

    fun downloadAndInstallUpdate() {
        val update = _updateStatus.value.available ?: return
        if (_updateStatus.value.downloading) return
        _updateStatus.value = _updateStatus.value.copy(
            downloading = true,
            progress = 0,
            message = "正在下载 ${update.versionName}…",
        )
        viewModelScope.launch {
            runCatching {
                updater.download(update) { progress ->
                    _updateStatus.value = _updateStatus.value.copy(
                        progress = progress,
                        message = "正在下载 ${update.versionName} · $progress%",
                    )
                }
            }.onSuccess { file ->
                val launched = updater.launchInstaller(file)
                _updateStatus.value = _updateStatus.value.copy(
                    downloading = false,
                    progress = 100,
                    downloadedPath = file.absolutePath,
                    message = if (launched) {
                        "已打开系统安装器"
                    } else {
                        "请允许“安装未知应用”，返回后点击安装"
                    },
                )
            }.onFailure { error ->
                _updateStatus.value = _updateStatus.value.copy(
                    downloading = false,
                    progress = null,
                    message = "更新失败：${error.message ?: "下载错误"}",
                )
            }
        }
    }

    fun installDownloadedUpdate() {
        val path = _updateStatus.value.downloadedPath ?: return
        val file = File(path)
        if (!file.isFile) {
            _updateStatus.value = _updateStatus.value.copy(
                downloadedPath = null,
                message = "已下载更新不存在，请重新下载",
            )
            return
        }
        val launched = updater.launchInstaller(file)
        _updateStatus.value = _updateStatus.value.copy(
            message = if (launched) "已打开系统安装器" else "请先允许“安装未知应用”",
        )
    }
}
