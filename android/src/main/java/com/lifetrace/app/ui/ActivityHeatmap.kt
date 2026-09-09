package com.lifetrace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifetrace.app.data.DiaryEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private data class DayActivity(val date: LocalDate, val notes: Int, val photos: Int) {
    val score: Int = notes * 2 + photos
}

@Composable
fun ActivityHeatmap(entries: List<DiaryEntry>, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(entries) { LocalDate.now(zone) }
    val days = remember(entries, today) {
        val grouped = entries.groupBy {
            Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
        }
        val start = today.minusDays(364)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { date ->
                val records = grouped[date].orEmpty()
                DayActivity(date, records.size, records.sumOf { it.photos.size })
            }
            .toList()
    }
    val weeks = remember(days) { days.chunked(7) }
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("过去一年记录 " + entries.size + " 篇", style = MaterialTheme.typography.titleMedium)
        Text(
            "每篇日记记 2 点，每张照片记 1 点；颜色越深，记录越丰富。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { day ->
                        Spacer(
                            Modifier
                                .size(11.dp)
                                .background(heatColor(day.score), RoundedCornerShape(2.dp)),
                        )
                    }
                    repeat(7 - week.size) { Spacer(Modifier.size(11.dp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "少",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp),
            )
            listOf(0, 1, 3, 6, 10).forEach { score ->
                Spacer(Modifier.size(11.dp).background(heatColor(score), RoundedCornerShape(2.dp)))
            }
            Text(
                "多",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun heatColor(score: Int): Color = when {
    score <= 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
    score <= 2 -> Color(0xFFBFEBD9)
    score <= 5 -> Color(0xFF72D3AE)
    score <= 9 -> Color(0xFF28A77B)
    else -> Color(0xFF087858)
}
