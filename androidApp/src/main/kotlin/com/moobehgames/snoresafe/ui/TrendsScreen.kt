package com.moobehgames.snoresafe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.session.SessionSummary
import com.moobehgames.snoresafe.core.trends.TrendPeriod
import com.moobehgames.snoresafe.core.trends.Trends
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrendsScreen(
    sessions: List<SessionSummary>,
    onOpenNight: (String) -> Unit,
    onShareReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = remember { TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }
    val today = remember { Trends.nightDay(System.currentTimeMillis() + 12 * 60 * 60 * 1000L, offset) }
    var periodIndex by rememberSaveable { mutableIntStateOf(1) }
    val period = TrendPeriod.entries[periodIndex]
    val comparison = remember(sessions, period) { Trends.compare(sessions, period, today, offset) }
    val points = remember(sessions, period) { Trends.nightPoints(sessions, offset, period.days?.let { today - it + 1 }) }

    androidx.compose.material3.Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.trends_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendPeriod.entries.forEachIndexed { i, p ->
                FilterChip(selected = i == periodIndex, onClick = { periodIndex = i }, label = { Text(periodLabel(p)) })
            }
        }

        val current = comparison.current
        if (current == null) {
            Text(stringResource(R.string.no_trend_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.avg_score), style = MaterialTheme.typography.titleSmall)
                    Text(
                        current.averageScore.roundToInt().toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = scoreColor(current.averageScore.roundToInt()),
                        fontWeight = FontWeight.Bold,
                    )
                    comparison.scoreChange?.let { change ->
                        val n = abs(change).roundToInt()
                        Text(
                            when {
                                n == 0 -> stringResource(R.string.change_same)
                                change < 0 -> stringResource(R.string.change_down, n.toString())
                                else -> stringResource(R.string.change_up, n.toString())
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(stringResource(R.string.avg_snoring), stringResource(R.string.percent_value, current.averageSnoringPercent.roundToInt()), Modifier.weight(1f))
                StatCard(stringResource(R.string.avg_loud_epic), stringResource(R.string.percent_value, current.averageLoudOrEpicPercent.roundToInt()), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(stringResource(R.string.avg_time_in_bed), formatDuration(current.averageTimeInBedMs), Modifier.weight(1f))
                StatCard(stringResource(R.string.nights_count), current.nights.toString(), Modifier.weight(1f))
            }

            Text(stringResource(R.string.score_history), style = MaterialTheme.typography.titleMedium)
            ScoreChart(points.map { it.score })
        }

        Text(stringResource(R.string.calendar), style = MaterialTheme.typography.titleMedium)
        CalendarMonth(sessions, offset, onOpenNight)

        if (sessions.isNotEmpty()) {
            TextButton(onClick = onShareReport) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(stringResource(R.string.share_trend_report), Modifier.padding(start = 8.dp))
            }
        }
    }
    }
}

@Composable
private fun periodLabel(p: TrendPeriod) = stringResource(
    when (p) {
        TrendPeriod.WEEK -> R.string.period_week
        TrendPeriod.MONTH -> R.string.period_month
        TrendPeriod.QUARTER -> R.string.period_quarter
        TrendPeriod.YEAR -> R.string.period_year
        TrendPeriod.ALL -> R.string.period_all
    },
)

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScoreChart(scores: List<Int>) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = stringResource(R.string.score_history)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        drawRect(track, size = size)
        if (scores.isEmpty()) return@Canvas
        val max = maxOf(40, scores.max()).toFloat()
        val bw = size.width / scores.size
        scores.forEachIndexed { i, s ->
            val h = size.height * s / max
            drawRect(
                scoreColor(s),
                topLeft = Offset(i * bw + bw * 0.15f, size.height - h),
                size = Size(maxOf(bw * 0.7f, 1f), maxOf(h, 2f)),
            )
        }
    }
}

/** Month grid; each night is coloured by its score. Tap a night to open it. */
@Composable
private fun CalendarMonth(sessions: List<SessionSummary>, offset: Long, onOpenNight: (String) -> Unit) {
    var monthShift by rememberSaveable { mutableIntStateOf(0) }
    val cal = remember(monthShift) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            add(Calendar.MONTH, monthShift)
        }
    }
    val byDay = remember(sessions) { Trends.nightPoints(sessions, offset).associateBy { it.nightDay } }
    val monthName = remember(cal) { "${DateFormatSymbols.getInstance().months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}" }
    val firstDow = cal.firstDayOfWeek
    val lead = (cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstNightDay = Trends.nightDay(cal.timeInMillis, offset)
    val weekdays = remember(firstDow) {
        val names = DateFormatSymbols.getInstance().shortWeekdays
        (0 until 7).map { names[(firstDow - 1 + it) % 7 + 1].take(2) }
    }

    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthShift-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
                }
                Text(monthName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { monthShift++ }, enabled = monthShift < 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
                }
            }
            Row { weekdays.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            val cells = lead + daysInMonth
            for (week in 0 until (cells + 6) / 7) {
                Row {
                    for (dow in 0 until 7) {
                        val dayOfMonth = week * 7 + dow - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayOfMonth in 1..daysInMonth) {
                                val point = byDay[firstNightDay + dayOfMonth - 1]
                                val bg = point?.let { scoreColor(it.score).copy(alpha = 0.85f) } ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                Box(
                                    Modifier.fillMaxSize().background(bg, RoundedCornerShape(6.dp))
                                        .let { m -> if (point != null) m.clickable { onOpenNight(point.sessionId) } else m },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
