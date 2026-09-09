package com.lifetrace.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.lifetrace.app.data.DiaryEntry
import java.io.File
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun DiaryMapScreen(
    entries: List<DiaryEntry>,
    contentPadding: PaddingValues,
    onOpen: (DiaryEntry) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var rangeDays by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val locatedEntries = remember(entries, rangeDays) {
        entries.filter { entry ->
            entry.latitude != null && entry.longitude != null &&
                (rangeDays == 0 || entry.occurredAt >= now - rangeDays * DAY_MILLIS)
        }
    }
    val trackAnalysis = remember(locatedEntries) { analyzeTrack(locatedEntries) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(locatedEntries.map { it.id to it.updatedAt }) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                map.clear()
                locatedEntries.forEach { entry ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(entry.latitude!!, entry.longitude!!))
                            .title(entry.placeLabel.ifBlank { formatMapDate(entry.occurredAt) })
                            .snippet(entry.body.ifBlank { "图片日记" }.take(80)),
                    )
                }
                if (trackAnalysis.orderedEntries.size >= 2) {
                    val points = trackAnalysis.orderedEntries.map { entry ->
                        Point.fromLngLat(entry.longitude!!, entry.latitude!!)
                    }
                    style.addSource(
                        GeoJsonSource(TRACK_SOURCE_ID, LineString.fromLngLats(points)),
                    )
                    style.addLayer(
                        LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
                            lineColor(TRACK_COLOR),
                            lineWidth(4f),
                            lineOpacity(0.72f),
                            lineCap(Property.LINE_CAP_ROUND),
                            lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                }
                when (locatedEntries.size) {
                    0 -> Unit
                    1 -> {
                        val entry = locatedEntries.first()
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(entry.latitude!!, entry.longitude!!),
                                16.0,
                            ),
                        )
                    }
                    else -> {
                        val bounds = LatLngBounds.Builder().apply {
                            locatedEntries.forEach {
                                include(LatLng(it.latitude!!, it.longitude!!))
                            }
                        }.build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 96))
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0 to "全部", 7 to "7 天", 30 to "30 天").forEach { (days, label) ->
                FilterChip(
                    selected = rangeDays == days,
                    onClick = { rangeDays = days },
                    label = { Text(label) },
                )
            }
        }

        if (trackAnalysis.suspiciousSegmentCount > 0) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, top = 66.dp, end = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
                ),
            ) {
                Text(
                    text = "检测到 ${trackAnalysis.suspiciousSegmentCount} 段疑似位置跳变，仅提示、不修改原始记录。",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        if (locatedEntries.isEmpty()) {
            Card(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Text("这个日期范围内还没有带坐标的日记", modifier = Modifier.padding(18.dp))
            }
        } else {
            LazyRow(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(locatedEntries, key = { it.id }) { entry ->
                    MapDiaryCard(entry) { onOpen(entry) }
                }
            }
        }
    }
}

@Composable
private fun MapDiaryCard(entry: DiaryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(320.dp)
            .height(112.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entry.photos.firstOrNull()?.let { photo ->
                AsyncImage(
                    model = File(photo.originalPath),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    entry.placeLabel.ifBlank { "已记录位置" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    formatMapDate(entry.occurredAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    entry.body.ifBlank { "图片日记" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun formatMapDate(timestamp: Long): String =
    java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))

private data class TrackAnalysis(
    val orderedEntries: List<DiaryEntry>,
    val suspiciousSegmentCount: Int,
)

private fun analyzeTrack(entries: List<DiaryEntry>): TrackAnalysis {
    val ordered = entries
        .filter { entry ->
            entry.latitude?.let { it in -90.0..90.0 } == true &&
                entry.longitude?.let { it in -180.0..180.0 } == true
        }
        .sortedBy { it.occurredAt }
    val suspicious = ordered.zipWithNext().count { (first, second) ->
        isSuspiciousJump(first, second)
    }
    return TrackAnalysis(orderedEntries = ordered, suspiciousSegmentCount = suspicious)
}

private fun isSuspiciousJump(first: DiaryEntry, second: DiaryEntry): Boolean {
    val elapsedHours = (second.occurredAt - first.occurredAt) / 3_600_000.0
    if (elapsedHours <= 0.0 || elapsedHours > 6.0) return false
    val distanceKm = haversineKm(
        first.latitude!!,
        first.longitude!!,
        second.latitude!!,
        second.longitude!!,
    )
    return distanceKm >= 50.0 && distanceKm / elapsedHours > 1_000.0
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 6_371.0 * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"
private const val DAY_MILLIS = 86_400_000L
private const val TRACK_SOURCE_ID = "lifetrace-diary-track-source"
private const val TRACK_LAYER_ID = "lifetrace-diary-track-layer"
private const val TRACK_COLOR = "#3157D5"
