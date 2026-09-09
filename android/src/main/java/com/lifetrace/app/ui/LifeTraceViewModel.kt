package com.lifetrace.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetrace.app.LifeTraceApplication
import com.lifetrace.app.data.DiaryEntry
import com.lifetrace.app.data.SaveDiaryRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LifeTraceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LifeTraceApplication).repository

    val entries: StateFlow<List<DiaryEntry>> = repository.entries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val cloudStatus = repository.cloudStatus

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

    fun configureCloudMirror(
        uri: Uri,
        folderName: String?,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.configureCloudMirror(uri, folderName) }
                .onFailure { onError(it.message ?: "云盘目录连接失败") }
        }
    }

    fun disconnectCloudMirror() = repository.disconnectCloudMirror()

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
}
