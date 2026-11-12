package com.natan.obd.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natan.obd.R
import com.natan.obd.diag.Dtc
import com.natan.obd.diag.DtcReport
import com.natan.obd.obd.FuelMethod
import com.natan.obd.obd.SupportedPids
import com.natan.obd.ui.components.ScreenTitle
import com.natan.obd.ui.components.SurfaceCard
import com.natan.obd.ui.theme.Coral
import com.natan.obd.ui.theme.Mint

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val busy = state.phase == DiagPhase.Busy

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.diag_title))

        SurfaceCard {
            Text(stringResource(R.string.diag_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = viewModel::scan,
                enabled = state.canScan && !state.tripRunning && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.diag_scan)) }
            when {
                state.tripRunning -> Hint(R.string.diag_trip_running)
                !state.canScan -> Hint(R.string.diag_needs_connection)
            }
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.diag_scanning), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SurfaceCard {
            Text(stringResource(R.string.sup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.sup_intro), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = viewModel::checkSupport,
                enabled = state.canScan && !state.tripRunning && !busy && state.support != SupportPhase.Busy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.sup_check)) }
        }
        when (val support = state.support) {
            SupportPhase.Busy -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.diag_scanning), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is SupportPhase.Done -> SupportReport(support.pids, support.diesel)
            is SupportPhase.Failed -> Text(support.message, color = MaterialTheme.colorScheme.error)
            SupportPhase.Idle -> Unit
        }

        when (val phase = state.phase) {
            is DiagPhase.Done -> {
                if (phase.justCleared) Text(stringResource(R.string.diag_cleared), color = Mint)
                Report(phase.report)
                if (phase.report.stored.isNotEmpty() || phase.report.milOn) {
                    OutlinedButton(onClick = { confirmClear = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.diag_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            is DiagPhase.Failed -> Text(phase.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.diag_clear_title)) },
            text = { Text(stringResource(R.string.diag_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clear() }) {
                    Text(stringResource(R.string.diag_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private class DashboardReading(val labelRes: Int, val pid: Int)

private val DASHBOARD_READINGS = listOf(
    DashboardReading(R.string.sup_rpm, 0x0C),
    DashboardReading(R.string.sup_speed, 0x0D),
    DashboardReading(R.string.sup_load, 0x04),
    DashboardReading(R.string.vital_coolant, 0x05),
    DashboardReading(R.string.vital_oil, 0x5C),
    DashboardReading(R.string.vital_intake, 0x0F),
    DashboardReading(R.string.vital_throttle, 0x11),
    DashboardReading(R.string.vital_map, 0x0B),
    DashboardReading(R.string.vital_voltage, 0x42),
    DashboardReading(R.string.fuel_tank, 0x2F),
)

/** Readings the app can use today; everything else is listed so you can see what else the car offers. */
private val USED_PIDS = DASHBOARD_READINGS.map { it.pid }.toSet() + setOf(0x10, 0x24, 0x34, 0x5E)

@Composable
private fun SupportReport(pids: Set<Int>, diesel: Boolean) {
    SurfaceCard {
        Text(stringResource(R.string.sup_dashboard), style = MaterialTheme.typography.titleMedium)
        DASHBOARD_READINGS.forEach { reading ->
            val ok = reading.pid in pids
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.Close, null,
                    tint = if (ok) Mint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(reading.labelRes),
                    color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val method = SupportedPids.fuelMethod(pids, diesel)
    SurfaceCard {
        Text(stringResource(R.string.sup_fuel_method), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when (method) {
                    FuelMethod.DIRECT_RATE -> R.string.fuel_method_direct
                    FuelMethod.MAF_ESTIMATE -> R.string.fuel_method_maf
                    FuelMethod.MAF_LAMBDA_ESTIMATE -> R.string.fuel_method_maf_lambda
                    FuelMethod.TANK_GAUGE_ONLY -> R.string.fuel_method_tank
                    FuelMethod.NONE -> R.string.fuel_method_none
                },
            ),
            modifier = Modifier.padding(top = 8.dp),
            color = if (method == FuelMethod.NONE || method == FuelMethod.TANK_GAUGE_ONLY) Coral else Mint,
        )
        Text(
            stringResource(if (diesel) R.string.fuel_method_diesel_hint else R.string.fuel_method_petrol_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    var expanded by remember { mutableStateOf(false) }
    val listed = pids.filterNot(SupportedPids::isRangeMarker).sorted()
    SurfaceCard {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.sup_all, listed.size))
        }
        if (expanded) {
            listed.forEach { pid ->
                Row(Modifier.padding(top = 8.dp)) {
                    Text("%02X".format(pid), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(SupportedPids.name(pid) ?: "-", style = MaterialTheme.typography.bodyMedium)
                        if (pid in USED_PIDS) {
                            Text(stringResource(R.string.sup_used), style = MaterialTheme.typography.bodySmall, color = Mint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(textRes: Int) {
    Text(
        stringResource(textRes), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Report(report: DtcReport) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (report.milOn) Icons.Default.Warning else Icons.Default.CheckCircle, null,
                tint = if (report.milOn) Coral else Mint,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(if (report.milOn) R.string.diag_mil_on else R.string.diag_mil_off),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    CodeSection(stringResource(R.string.diag_stored), null, report.stored, showEmpty = true)
    if (report.pending.isNotEmpty()) CodeSection(stringResource(R.string.diag_pending), stringResource(R.string.diag_pending_sub), report.pending, showEmpty = false)
}

@Composable
private fun CodeSection(title: String, subtitle: String?, codes: List<Dtc>, showEmpty: Boolean) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (codes.isEmpty() && showEmpty) {
            Text(stringResource(R.string.diag_none), color = Mint, modifier = Modifier.padding(top = 8.dp))
        }
        codes.forEach { dtc ->
            Column(Modifier.padding(top = 12.dp)) {
                Text(dtc.code, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(
                    dtc.description ?: stringResource(R.string.diag_unknown),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
