package com.natan.obd.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natan.obd.R
import com.natan.obd.data.Trip
import com.natan.obd.ui.components.LineChart
import com.natan.obd.ui.components.ScoreRing
import com.natan.obd.ui.components.StatTile
import com.natan.obd.ui.components.SurfaceCard
import com.natan.obd.ui.formatDecimal
import com.natan.obd.ui.formatDuration
import com.natan.obd.ui.theme.Amber
import com.natan.obd.ui.theme.Coral
import com.natan.obd.ui.theme.Sky
import com.natan.obd.ui.theme.Teal
import com.natan.obd.ui.theme.scoreColor
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(onBack: () -> Unit, viewModel: TripDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val trip = state.trip

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = {
                    if (trip != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.common_delete))
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth())
            trip == null -> Text(stringResource(R.string.trip_missing), Modifier.padding(padding).padding(24.dp))
            else -> Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(trip, state.driverName)
                Breakdown(trip)
                Stats(trip)
                if (state.samples.size >= 2) {
                    ChartCard(stringResource(R.string.chart_speed), state.samples.map { it.speed.toFloat() }, Teal, 220f)
                    ChartCard(stringResource(R.string.chart_rpm), state.samples.map { it.rpm.toFloat() }, Amber, 8000f)
                    ChartCard(stringResource(R.string.chart_load), state.samples.map { it.load.toFloat() }, Sky, 100f)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trip_delete_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun Header(trip: Trip, driverName: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreRing(trip.score, ringSize = 190.dp)
        Text(stringResource(R.string.score_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$driverName · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.startedAt)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Breakdown(trip: Trip) {
    SurfaceCard {
        Text(stringResource(R.string.breakdown), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BreakdownBar(stringResource(R.string.bd_speed), trip.speedScore)
            BreakdownBar(stringResource(R.string.bd_rpm), trip.rpmScore)
            BreakdownBar(stringResource(R.string.bd_load), trip.loadScore)
            BreakdownBar(stringResource(R.string.bd_efficiency), trip.efficiencyScore)
        }
    }
}

@Composable
private fun BreakdownBar(label: String, score: Int?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(score?.let { "$it" } ?: stringResource(R.string.not_available),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (score ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp),
            color = score?.let(::scoreColor) ?: Color.Transparent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun Stats(trip: Trip) {
    val na = stringResource(R.string.not_available)
    val rows = listOf(
        listOf(
            stringResource(R.string.tile_distance) to "${formatDecimal(trip.distanceKm)} km",
            stringResource(R.string.tile_time) to formatDuration(trip.durationSec),
        ),
        listOf(
            stringResource(R.string.tile_avg_speed) to "${trip.avgSpeed} km/h",
            stringResource(R.string.stat_max_speed) to "${trip.maxSpeed} km/h",
        ),
        listOf(
            stringResource(R.string.stat_avg_rpm) to "${trip.avgRpm}",
            stringResource(R.string.stat_avg_load) to "${formatDecimal(trip.avgLoad, 0)} %",
        ),
        listOf(
            stringResource(R.string.stat_fuel) to (trip.fuelLitres?.let { "${formatDecimal(it, 2)} L" } ?: na),
            stringResource(R.string.stat_consumption) to (trip.consumptionL100?.let { "${formatDecimal(it)} L/100 km" } ?: na),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, values: List<Float>, color: Color, max: Float) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
        LineChart(values, color, maxValue = maxOf(max.coerceAtMost((values.maxOrNull() ?: 1f) * 1.15f), 1f))
    }
}
