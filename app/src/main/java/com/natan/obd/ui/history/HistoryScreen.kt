package com.natan.obd.ui.history

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.natan.obd.share.Sharing
import com.natan.obd.ui.LocalUnits
import com.natan.obd.ui.Units
import com.natan.obd.ui.components.LineChart
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natan.obd.R
import com.natan.obd.data.DriverSummary
import com.natan.obd.data.Trip
import com.natan.obd.ui.components.EmptyState
import com.natan.obd.ui.components.ScreenTitle
import com.natan.obd.ui.components.SurfaceCard
import com.natan.obd.ui.formatDuration
import com.natan.obd.ui.theme.scoreColor
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(onOpenTrip: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val units = LocalUnits.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(stringResource(R.string.nav_history), Modifier.weight(1f))
                IconButton(onClick = {
                    scope.launch {
                        val file = viewModel.exportCsv()
                        if (file == null) Toast.makeText(context, R.string.export_empty, Toast.LENGTH_SHORT).show()
                        else Sharing.shareFile(context, file, "text/csv", context.getString(R.string.export_title))
                    }
                }) { Icon(Icons.Default.FileDownload, stringResource(R.string.export_csv)) }
            }
        }

        // The last 20 trips, oldest to newest, so the line reads left to right.
        val trend = state.trips.take(20).reversed().map { it.score.toFloat() }
        if (trend.size >= 2) {
            item {
                SurfaceCard {
                    Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                    LineChart(trend, MaterialTheme.colorScheme.primary, maxValue = 100f)
                }
            }
        }

        if (state.summaries.any { it.tripCount > 0 }) {
            item { SectionLabel(stringResource(R.string.leaderboard)) }
            items(state.summaries.filter { it.tripCount > 0 }, key = { "s${it.id}" }) { LeaderboardRow(it, state.summaries.indexOf(it) + 1, units) }
        }

        item {
            SectionLabel(stringResource(R.string.trips))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                item {
                    FilterChip(state.filterDriverId == null, { viewModel.setFilter(null) }, { Text(stringResource(R.string.filter_all)) })
                }
                items(state.summaries, key = { "f${it.id}" }) {
                    FilterChip(state.filterDriverId == it.id, { viewModel.setFilter(it.id) }, { Text(it.name) })
                }
            }
        }

        if (state.trips.isEmpty()) {
            item { EmptyState(stringResource(R.string.history_empty), Modifier.fillMaxWidth()) }
        }
        items(state.trips, key = { it.id }) { trip ->
            TripRow(trip, state.driverNames[trip.driverId].orEmpty(), units, onClick = { onOpenTrip(trip.id) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScoreBadge(score: Int, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(scoreColor(score).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(score.toString(), color = scoreColor(score), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LeaderboardRow(summary: DriverSummary, rank: Int, units: Units) {
    SurfaceCard(padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$rank", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.trip_count, summary.tripCount, summary.tripCount) +
                        " · ${units.distance(summary.totalKm ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ScoreBadge(summary.avgScore?.toInt() ?: 0)
        }
    }
}

@Composable
private fun TripRow(trip: Trip, driverName: String, units: Units, onClick: () -> Unit) {
    SurfaceCard(Modifier.clickable(onClick = onClick), padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(trip.score)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.startedAt)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "$driverName · ${units.distance(trip.distanceKm)} · ${formatDuration(trip.durationSec)}" +
                        (trip.consumptionL100?.let { " · ${units.consumption(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
