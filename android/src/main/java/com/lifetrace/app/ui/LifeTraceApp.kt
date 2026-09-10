package com.lifetrace.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lifetrace.app.BuildConfig
import com.lifetrace.app.data.DiaryEntry
import com.lifetrace.app.data.DurableStorageStatus
import com.lifetrace.app.data.UpdateStatus
import com.lifetrace.app.data.DiaryPhoto
import com.lifetrace.app.data.SaveDiaryRequest
import com.lifetrace.app.ui.theme.LifeTraceThemeMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private data class Destination(
    val label: String,
    val title: String,
)

private val destinations = listOf(
    Destination("日记", "我的日常"),
    Destination("地图", "足迹地图"),
    Destination("设置", "设置"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeTraceApp(
    themeMode: LifeTraceThemeMode,
    onThemeModeChange: (LifeTraceThemeMode) -> Unit,
    viewModel: LifeTraceViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val durableStatus by viewModel.durableStatus.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("home") }
    var selectedEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedEntry = entries.firstOrNull { it.id == selectedEntryId }

    val showError: (String) -> Unit = { message ->
        scope.launch { snackbar.showSnackbar(message) }
    }
    val context = LocalContext.current
    val durableAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshDurableStorageAccess(showError)
    }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.refreshDurableStorageAccess(showError)
        else showError("未授予存储权限，永久本地备份未启用")
    }
    val requestDurableAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            durableAccessLauncher.launch(appIntent)
        } else {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    BackHandler(enabled = route != "home") {
        route = if (route == "editor" && selectedEntryId != null) "detail" else "home"
    }

    when (route) {
        "editor" -> DiaryEditorScreen(
            entry = selectedEntry,
            onBack = {
                route = if (selectedEntryId != null) "detail" else "home"
            },
            onSave = { request, onFinished ->
                viewModel.save(
                    request = request,
                    onSuccess = { id ->
                        selectedEntryId = id
                        route = "detail"
                        onFinished()
                    },
                    onError = {
                        onFinished()
                        showError(it)
                    },
                )
            },
            snackbar = snackbar,
        )

        "detail" -> {
            if (selectedEntry == null) {
                LoadingScreen()
            } else {
                DiaryDetailScreen(
                    entry = selectedEntry,
                    durableBackupEnabled = durableStatus.enabled,
                    onBack = { route = "home" },
                    onEdit = { route = "editor" },
                    onDelete = {
                        viewModel.delete(
                            id = selectedEntry.id,
                            onSuccess = {
                                selectedEntryId = null
                                route = "home"
                            },
                            onError = showError,
                        )
                    },
                    snackbar = snackbar,
                )
            }
        }

        else -> Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(destinations[selectedTab].title) },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        label = { Text("日记") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                        label = { Text("地图") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = {
                            selectedEntryId = null
                            route = "editor"
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建日记")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when (selectedTab) {
                0 -> DiaryTimeline(
                    entries = entries,
                    contentPadding = padding,
                    onOpen = {
                        selectedEntryId = it.id
                        route = "detail"
                    },
                    onCreate = {
                        selectedEntryId = null
                        route = "editor"
                    },
                )
                1 -> DiaryMapScreen(
                    entries = entries,
                    contentPadding = padding,
                    onOpen = {
                        selectedEntryId = it.id
                        route = "detail"
                    },
                )
                else -> SettingsScreen(
                    entries = entries,
                    durableStatus = durableStatus,
                    updateStatus = updateStatus,
                    themeMode = themeMode,
                    contentPadding = padding,
                    onThemeModeChange = onThemeModeChange,
                    onEnableDurableStorage = requestDurableAccess,
                    onSyncDurableStorage = { viewModel.syncDurableBackup(showError) },
                    onCheckUpdates = viewModel::checkForUpdates,
                    onDownloadUpdate = viewModel::downloadAndInstallUpdate,
                    onInstallDownloaded = viewModel::installDownloadedUpdate,
                )
            }
        }
    }
}

@Composable
private fun DiaryTimeline(
    entries: List<DiaryEntry>,
    contentPadding: PaddingValues,
    onOpen: (DiaryEntry) -> Unit,
    onCreate: () -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "从今天开始记录",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "文字、照片、时间和位置都会安全地保存在这台手机上。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCreate) {
                    Text("写第一篇日记")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "共 " + entries.size + " 篇 · 数据保存在本机",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        items(entries, key = { it.id }) { entry ->
            DiaryCard(entry = entry, onClick = { onOpen(entry) })
        }
    }
}

@Composable
private fun DiaryCard(entry: DiaryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entry.photos.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { entry.photos.size })
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val photo = entry.photos[page]
                        AsyncImage(
                            model = File(photo.originalPath),
                            contentDescription = "日记照片 ${page + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (entry.photos.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${entry.photos.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (entry.photos.isEmpty()) 16.dp else 0.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = formatDateTime(entry.occurredAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (entry.body.isNotBlank()) {
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "图片日记",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.placeLabel.isNotBlank() || entry.latitude != null) {
                    Text(
                        text = entry.placeLabel.ifBlank { formatCoordinates(entry.latitude, entry.longitude) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.photos.size > 1) {
                    Text(
                        text = "左右滑动查看 ${entry.photos.size} 张照片",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryEditorScreen(
    entry: DiaryEntry?,
    onBack: () -> Unit,
    onSave: (SaveDiaryRequest, () -> Unit) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by rememberSaveable(entry?.id) { mutableStateOf(entry?.body.orEmpty()) }
    var occurredAt by rememberSaveable(entry?.id) {
        mutableLongStateOf(entry?.occurredAt ?: System.currentTimeMillis())
    }
    var latitude by rememberSaveable(entry?.id) { mutableStateOf(entry?.latitude) }
    var longitude by rememberSaveable(entry?.id) { mutableStateOf(entry?.longitude) }
    var placeLabel by rememberSaveable(entry?.id) { mutableStateOf(entry?.placeLabel.orEmpty()) }
    var retainedIds by remember(entry?.id) {
        mutableStateOf(entry?.photos?.map { it.id }.orEmpty())
    }
    var newUris by remember(entry?.id) { mutableStateOf(emptyList<String>()) }
    var locating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val available = (9 - retainedIds.size - newUris.size).coerceAtLeast(0)
        val accepted = uris
            .map(Uri::toString)
            .filterNot { it in newUris }
            .take(available)
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        newUris = newUris + accepted
        if (uris.size > available) {
            scope.launch { snackbar.showSnackbar("最多只能添加 9 张照片") }
        }
    }

    val onLocation: (Location?) -> Unit = { location ->
        locating = false
        if (location == null) {
            scope.launch { snackbar.showSnackbar("暂时无法获取位置，请稍后重试") }
        } else {
            latitude = location.latitude
            longitude = location.longitude
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locating = true
            requestSingleLocation(context, onLocation)
        } else {
            locating = false
            scope.launch { snackbar.showSnackbar("没有位置权限，仍可正常保存日记") }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (entry == null) "新建日记" else "编辑日记") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !saving) {
                        Text("取消")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !saving &&
                            (body.isNotBlank() || retainedIds.isNotEmpty() || newUris.isNotEmpty()),
                        onClick = {
                            saving = true
                            onSave(
                                SaveDiaryRequest(
                                    id = entry?.id,
                                    body = body,
                                    occurredAt = occurredAt,
                                    latitude = latitude,
                                    longitude = longitude,
                                    placeLabel = placeLabel,
                                    retainedPhotoIds = retainedIds,
                                    newPhotoUris = newUris,
                                ),
                            ) { saving = false }
                        },
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("保存")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 18.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= 20_000) body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    label = { Text("今天发生了什么？") },
                    placeholder = { Text("写下值得记住的事情……") },
                    supportingText = { Text(body.length.toString() + " / 20000") },
                )
            }

            item {
                SectionTitle("照片", retainedIds.size + newUris.size, 9)
                Spacer(Modifier.height(10.dp))
                EditorPhotoRow(
                    existing = entry?.photos.orEmpty().filter { it.id in retainedIds },
                    newUris = newUris,
                    onRemoveExisting = { id -> retainedIds = retainedIds - id },
                    onRemoveNew = { uri -> newUris = newUris - uri },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { photoPicker.launch(arrayOf("image/*")) },
                    enabled = retainedIds.size + newUris.size < 9,
                ) {
                    Text("选择图片")
                }
            }

            item {
                SectionTitle("时间")
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        showDateTimePicker(context, occurredAt) { occurredAt = it }
                    },
                ) {
                    Text(formatDateTime(occurredAt))
                }
            }

            item {
                SectionTitle("位置")
                Spacer(Modifier.height(8.dp))
                if (latitude != null && longitude != null) {
                    Text(
                        text = formatCoordinates(latitude, longitude),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = placeLabel,
                    onValueChange = { placeLabel = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地点名称（可选）") },
                    placeholder = { Text("例如：滨海湾、家、实验室") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (hasLocationPermission(context)) {
                                locating = true
                                requestSingleLocation(context, onLocation)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        enabled = !locating,
                    ) {
                        Text(if (locating) "定位中…" else "获取当前位置")
                    }
                    if (latitude != null) {
                        TextButton(
                            onClick = {
                                latitude = null
                                longitude = null
                            },
                        ) {
                            Text("清除坐标")
                        }
                    }
                }
                Text(
                    text = "拒绝位置权限也可以保存；内容优先保存在本机；建议在设置中启用 Documents/LifeTrace 永久备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int? = null, max: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (count != null && max != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString() + " / " + max,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditorPhotoRow(
    existing: List<DiaryPhoto>,
    newUris: List<String>,
    onRemoveExisting: (String) -> Unit,
    onRemoveNew: (String) -> Unit,
) {
    if (existing.isEmpty() && newUris.isEmpty()) {
        Text(
            text = "还没有照片，可添加 1～9 张。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(existing, key = { it.id }) { photo ->
            RemovablePhoto(
                model = File(photo.thumbnailPath),
                onRemove = { onRemoveExisting(photo.id) },
            )
        }
        items(newUris, key = { it }) { uri ->
            RemovablePhoto(
                model = Uri.parse(uri),
                onRemove = { onRemoveNew(uri) },
            )
        }
    }
}

@Composable
private fun RemovablePhoto(model: Any, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        AsyncImage(
            model = model,
            contentDescription = "已选择照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = "×",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onRemove)
                .padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryDetailScreen(
    entry: DiaryEntry,
    durableBackupEnabled: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这篇日记？") },
            text = {
                Text(
                    if (durableBackupEnabled) {
                        "本地内容会删除；永久备份副本会保留在 Documents/LifeTrace/Trash。"
                    } else {
                        "当前未启用永久备份，文字和照片会从本机删除。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("日记详情") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = onEdit) { Text("编辑") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 18.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = formatDateTime(entry.occurredAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (entry.body.isNotBlank()) {
                item {
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (entry.placeLabel.isNotBlank() || entry.latitude != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = entry.placeLabel.ifBlank { "记录位置" },
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (entry.latitude != null) {
                                Text(
                                    text = formatCoordinates(entry.latitude, entry.longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            if (entry.photos.isNotEmpty()) {
                item {
                    DiaryPhotoPager(entry.photos)
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除日记")
                }
            }
        }
    }
}

@Composable
private fun DiaryPhotoPager(photos: List<DiaryPhoto>) {
    val pagerState = rememberPagerState(pageCount = { photos.size })
    var fullScreenPhoto by remember { mutableStateOf<DiaryPhoto?>(null) }
    fullScreenPhoto?.let { photo ->
        FullScreenPhotoViewer(photo = photo, onDismiss = { fullScreenPhoto = null })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val photo = photos[page]
            AsyncImage(
                model = File(photo.originalPath),
                contentDescription = "日记照片 " + (page + 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { fullScreenPhoto = photo },
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = "点击图片查看大图" + if (photos.size > 1) " · 左右滑动切换" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (photos.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                photos.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = (pagerState.currentPage + 1).toString() + " / " + photos.size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FullScreenPhotoViewer(photo: DiaryPhoto, onDismiss: () -> Unit) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = File(photo.originalPath),
                contentDescription = "日记照片大图",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(photo.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale > 1f) offset + pan else Offset.Zero
                        }
                    },
                contentScale = ContentScale.Fit,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
            ) {
                Text("关闭", color = Color.White)
            }
        }
    }
}

@Composable
private fun MapMilestone(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "地图将在下一阶段接入",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "当前版本先保证离线日记可靠可用。日记中保存的坐标不会丢失，后续会在 MapLibre 地图上按日期显示。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    entries: List<DiaryEntry>,
    durableStatus: DurableStorageStatus,
    updateStatus: UpdateStatus,
    themeMode: LifeTraceThemeMode,
    contentPadding: PaddingValues,
    onThemeModeChange: (LifeTraceThemeMode) -> Unit,
    onEnableDurableStorage: () -> Unit,
    onSyncDurableStorage: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallDownloaded: () -> Unit,
) {
    val entryCount = entries.size
    val photoCount = entries.sumOf { it.photos.size }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection("外观") {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("主题", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(LifeTraceThemeMode.entries, key = { it.storedValue }) { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSection("数据") {
                SettingRow(
                    title = "本地记录",
                    summary = "$entryCount 篇日记 · $photoCount 张照片",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "永久备份",
                    summary = if (durableStatus.enabled) {
                        "Documents/LifeTrace · 卸载后保留"
                    } else {
                        "尚未启用 · 建议启用以防卸载丢失"
                    },
                    trailing = {
                        if (durableStatus.enabled) {
                            FilledTonalButton(
                                onClick = onSyncDurableStorage,
                                enabled = !durableStatus.syncing,
                            ) { Text(if (durableStatus.syncing) "同步中" else "同步") }
                        } else {
                            FilledTonalButton(onClick = onEnableDurableStorage) { Text("启用") }
                        }
                    },
                )
                Text(
                    text = durableStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                )
            }
        }

        item {
            SettingsSection("更新") {
                SettingRow(
                    title = "LifeTrace ${BuildConfig.VERSION_NAME}",
                    summary = "更新服务 · trace.wmy-cloud.cn",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        text = updateStatus.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (updateStatus.available != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (updateStatus.downloading) {
                        val progress = (updateStatus.progress ?: 0).coerceIn(0, 100) / 100f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    updateStatus.available?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            updateStatus.downloadedPath != null -> {
                                FilledTonalButton(onClick = onInstallDownloaded) { Text("安装已下载版本") }
                            }
                            updateStatus.available != null -> {
                                FilledTonalButton(
                                    onClick = onDownloadUpdate,
                                    enabled = !updateStatus.downloading,
                                ) { Text(if (updateStatus.downloading) "下载中…" else "下载并安装") }
                            }
                            else -> {
                                FilledTonalButton(
                                    onClick = onCheckUpdates,
                                    enabled = !updateStatus.checking,
                                ) { Text(if (updateStatus.checking) "检查中…" else "检查更新") }
                            }
                        }
                        if (updateStatus.available != null || updateStatus.downloadedPath != null) {
                            TextButton(onClick = onCheckUpdates, enabled = !updateStatus.downloading) {
                                Text("重新检查")
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSection("记录活跃度") {
                ActivityHeatmap(entries = entries, modifier = Modifier.padding(14.dp))
            }
        }

        item {
            SettingsSection("关于") {
                SettingRow(
                    title = "LifeTrace ${BuildConfig.VERSION_NAME}",
                    summary = "本地优先 · 矢量地图 · 稳定签名 · 应用内更新",
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatCoordinates(latitude: Double?, longitude: Double?): String {
    if (latitude == null || longitude == null) return ""
    return String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude)
}

private fun showDateTimePicker(
    context: Context,
    timestamp: Long,
    onSelected: (Long) -> Unit,
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(year, month, day, hour, minute, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    onSelected(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true,
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun requestSingleLocation(
    context: Context,
    onResult: (Location?) -> Unit,
) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).filter { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    val fallback = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }

    val provider = providers.firstOrNull()
    if (provider == null) {
        onResult(fallback)
        return
    }

    val cancellationSignal = CancellationSignal()
    LocationManagerCompat.getCurrentLocation(
        manager,
        provider,
        cancellationSignal,
        ContextCompat.getMainExecutor(context),
        Consumer { location -> onResult(location ?: fallback) },
    )
}
