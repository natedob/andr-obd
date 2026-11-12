package com.natan.obd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natan.obd.R
import com.natan.obd.data.AppSettings
import com.natan.obd.ui.components.ScreenTitle
import com.natan.obd.ui.components.SurfaceCard

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))

        SurfaceCard {
            Text(stringResource(R.string.settings_vehicle), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_vehicle_sub), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The form is only composed once settings are loaded, so each field's initial text is the real value.
                NumberField(R.string.set_tank, loaded.tankLitres, decimal = true) { v -> viewModel.update { it.copy(tankLitres = v) } }
                NumberField(R.string.set_rpm, loaded.optimalRpm.toDouble(), decimal = false) { v -> viewModel.update { it.copy(optimalRpm = v.toInt()) } }
                NumberField(R.string.set_speed, loaded.optimalSpeed.toDouble(), decimal = false) { v -> viewModel.update { it.copy(optimalSpeed = v.toInt()) } }
                NumberField(R.string.set_load, loaded.optimalLoad, decimal = true) { v -> viewModel.update { it.copy(optimalLoad = v) } }
                NumberField(R.string.set_cons, loaded.optimalConsumption, decimal = true) { v -> viewModel.update { it.copy(optimalConsumption = v) } }
            }
        }

        SurfaceCard {
            Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(R.string.clear_history), color = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            stringResource(R.string.about, "2.0"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** Commits on every valid, positive edit; invalid text is simply flagged and not saved. */
@Composable
private fun NumberField(labelRes: Int, initial: Double, decimal: Boolean, onValid: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (decimal) initial.toString().removeSuffix(".0") else initial.toInt().toString()) }
    val parsed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v > 0 }?.let(onValid) },
        label = { Text(stringResource(labelRes)) },
        isError = parsed == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
