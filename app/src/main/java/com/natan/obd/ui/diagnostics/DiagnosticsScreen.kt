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
