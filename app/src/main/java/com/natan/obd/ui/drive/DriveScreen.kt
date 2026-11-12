package com.natan.obd.ui.drive

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.natan.obd.R
import com.natan.obd.bluetooth.LinkState
import com.natan.obd.data.Driver
import com.natan.obd.trip.Hint
import com.natan.obd.trip.LiveData
import com.natan.obd.trip.TripState
import com.natan.obd.trip.Vitals
import com.natan.obd.ui.components.VitalTile
import com.natan.obd.ui.components.ArcGauge
import com.natan.obd.ui.components.ScreenTitle
import com.natan.obd.ui.components.StatTile
import com.natan.obd.ui.components.SurfaceCard
import com.natan.obd.ui.LocalUnits
import com.natan.obd.ui.Units
import com.natan.obd.ui.formatDecimal
import com.natan.obd.ui.formatDuration
import com.natan.obd.ui.theme.Amber
import com.natan.obd.ui.theme.Coral
import com.natan.obd.ui.theme.Mint
import com.natan.obd.ui.theme.Sky
import com.natan.obd.ui.theme.Teal
import com.natan.obd.ui.theme.scoreColor
import kotlinx.coroutines.launch

@Composable
fun DriveScreen(
    snackbar: SnackbarHostState,
    onTripSaved: (Long) -> Unit,
    viewModel: DriveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDriver by remember { mutableStateOf(false) }
    var driverToDelete by remember { mutableStateOf<Driver?>(null) }
    var showDevices by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DriveEvent.Message -> launch { snackbar.showSnackbar(event.text) }
                is DriveEvent.TripSaved -> onTripSaved(event.tripId)
            }
        }
    }

    // Starting needs Bluetooth permission (for the foreground-service type) and, on 13+, notifications.
    val startPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasBluetoothPermission(context)) viewModel.startTrip()
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.err_bt_permission)) }
    }
    val devicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showDevices = true
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.err_bt_permission)) }
    }

    val units = LocalUnits.current
    val running = state.trip as? TripState.Running
    val canStart = state.selectedDriver != null &&
        (state.settings.demoMode || state.link is LinkState.Connected)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(stringResource(R.string.nav_drive))

        DriverRow(
            drivers = state.drivers,
            selected = state.selectedDriver,
            enabled = running == null,
            onSelect = { viewModel.selectDriver(it.id) },
            onAdd = { showAddDriver = true },
            onDelete = { driverToDelete = it },
        )

        ConnectionCard(
            state = state,
            enabled = running == null,
            onDemoChange = viewModel::setDemoMode,
            onConnect = {
                if (hasBluetoothPermission(context)) showDevices = true
                else devicePermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            onDisconnect = viewModel::disconnect,
        )

        if (running != null && running.live.hasData) CoachingBanner(running.live)

        Dashboard(live = running?.live ?: LiveData(), units = units)

        if (running != null && running.live.hasFuelInfo) {
            FuelSection(running.live, state.settings.tankLitres, state.settings.optimalConsumption, units)
        }

        if (running != null && running.live.vitals.any) VitalsSection(running.live.vitals, units)

        if (running != null && !running.live.hasData) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.drive_warming_up), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val live = running?.live ?: LiveData()
            StatTile(stringResource(R.string.tile_distance), units.distance(live.distanceKm), Modifier.weight(1f))
            StatTile(stringResource(R.string.tile_time), formatDuration(live.elapsedSec), Modifier.weight(1f))
            StatTile(stringResource(R.string.tile_avg_speed), units.speed(live.avgSpeedKmh), Modifier.weight(1f))
        }

        if (running != null) {
            Button(
                onClick = viewModel::stopTrip,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.btn_stop))
            }
        } else {
            Button(
                onClick = { requestAndStart(context, viewModel, state.settings.recordRoute, startPermissions::launch) },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.btn_start))
            }
            if (!canStart) {
                Text(
                    stringResource(if (state.selectedDriver == null) R.string.hint_add_driver else R.string.hint_connect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAddDriver) {
        AddDriverDialog(onDismiss = { showAddDriver = false }, onAdd = { viewModel.addDriver(it); showAddDriver = false })
    }
    driverToDelete?.let { driver ->
        AlertDialog(
            onDismissRequest = { driverToDelete = null },
            title = { Text(stringResource(R.string.driver_delete_title, driver.name)) },
            text = { Text(stringResource(R.string.driver_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteDriver(driver); driverToDelete = null }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { driverToDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (showDevices) {
        DeviceSheet(
            devices = viewModel.pairedDevices(),
            onDismiss = { showDevices = false },
            onPick = { viewModel.connect(it.address); showDevices = false },
        )
    }
}

private fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun requestAndStart(context: Context, vm: DriveViewModel, recordRoute: Boolean, launch: (Array<String>) -> Unit) {
    val needed = buildList {
        if (recordRoute) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    if (needed.isEmpty()) vm.startTrip() else launch(needed.toTypedArray())
}

@Composable
private fun DriverRow(
    drivers: List<Driver>,
    selected: Driver?,
    enabled: Boolean,
    onSelect: (Driver) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Driver) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(drivers, key = { it.id }) { driver ->
            FilterChip(
                selected = driver.id == selected?.id,
                onClick = { onSelect(driver) },
                enabled = enabled,
                label = { Text(driver.name) },
                trailingIcon = if (driver.id == selected?.id && enabled) {
                    { Icon(Icons.Default.Close, stringResource(R.string.common_delete),
                        Modifier.size(18.dp).clickableNoRipple { onDelete(driver) }) }
                } else null,
            )
        }
        item {
            AssistChip(
                onClick = onAdd, enabled = enabled,
                label = { Text(stringResource(R.string.driver_add)) },
                leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    state: DriveUiState,
    enabled: Boolean,
    onDemoChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.conn_demo_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.conn_demo_sub), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.settings.demoMode, onCheckedChange = onDemoChange, enabled = enabled)
        }
        if (!state.settings.demoMode) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val link = state.link
                Icon(
                    if (link is LinkState.Connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    null,
                    tint = if (link is LinkState.Connected) Mint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    when (link) {
                        LinkState.Disconnected -> stringResource(R.string.conn_disconnected)
                        is LinkState.Connecting -> stringResource(R.string.conn_connecting, link.name)
                        is LinkState.Connected -> stringResource(R.string.conn_connected, link.name)
                        is LinkState.Failed -> link.message
                    },
                    Modifier.weight(1f),
                    color = if (link is LinkState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (link is LinkState.Connected) {
                    TextButton(onClick = onDisconnect, enabled = enabled) { Text(stringResource(R.string.conn_disconnect)) }
                } else {
                    OutlinedButton(
                        onClick = onConnect,
                        enabled = enabled && state.bluetoothSupported && link !is LinkState.Connecting,
                    ) { Text(stringResource(R.string.conn_connect)) }
                }
            }
        }
    }
}

@Composable
private fun CoachingBanner(live: LiveData) {
    val coaching = live.coaching
    val (textRes, color, icon) = if (live.speeding) {
        Triple(R.string.coach_speeding, Coral, Icons.Default.Warning)
    } else when (coaching.hint) {
        Hint.GOOD -> Triple(R.string.coach_good, Mint, Icons.Default.CheckCircle)
        Hint.SLOW_DOWN -> Triple(R.string.coach_slow, Amber, Icons.Default.Warning)
        Hint.SHIFT_UP -> Triple(R.string.coach_shift, Amber, Icons.Default.Warning)
        Hint.EASE_THROTTLE -> Triple(R.string.coach_throttle, Amber, Icons.Default.Warning)
        Hint.IDLING -> Triple(R.string.coach_idling, Sky, Icons.Default.Info)
        Hint.NONE -> Triple(R.string.coach_none, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Info)
    }
    SurfaceCard(padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(textRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.coach_score, coaching.ecoNow), style = MaterialTheme.typography.titleLarge, color = scoreColor(coaching.ecoNow))
        }
    }
}

@Composable
private fun Dashboard(live: LiveData, units: Units) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ArcGauge(
                value = units.speedValue(live.speedKmh.toDouble()).toFloat(), max = units.speedValue(220.0).toFloat(),
                label = stringResource(R.string.gauge_speed), unit = units.speedUnit,
                colors = if (live.speeding) listOf(Coral, Coral) else listOf(Teal, Sky, Amber),
                gaugeSize = 240.dp, big = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ArcGauge(
                    value = live.rpm.toFloat(), max = 8000f,
                    label = stringResource(R.string.gauge_rpm), unit = "rpm",
                    colors = listOf(Teal, Amber, Coral), gaugeSize = 140.dp,
                )
                ArcGauge(
                    value = live.loadPercent.toFloat(), max = 100f,
                    label = stringResource(R.string.gauge_load), unit = "%",
                    colors = listOf(Mint, Amber, Coral), gaugeSize = 140.dp,
                )
            }
        }
    }
}

@Composable
private fun FuelSection(live: LiveData, tankLitres: Double, targetL100: Double, units: Units) {
    // Consumption is coloured against the driver's own target: at or under is green, up to 30% over is amber.
    fun consColor(l100: Double) = when {
        l100 <= targetL100 -> Mint
        l100 <= targetL100 * 1.3 -> Amber
        else -> Coral
    }
    val tiles = buildList {
        live.tankPercent?.let {
            val litres = it / 100 * tankLitres
            add(Vital(R.string.fuel_tank, "${it.toInt()} % · ${units.volume(litres)}", (it / 100).toFloat(),
                if (it < 15) Coral else if (it < 30) Amber else Teal))
        }
        // Standing still there is no per-distance figure, so show litres per hour instead.
        live.instantL100?.let { add(Vital(R.string.fuel_now, units.consumption(it), (it / (targetL100 * 2)).toFloat(), consColor(it))) }
            ?: live.instantLph?.let { add(Vital(R.string.fuel_now, units.fuelRate(it), (it / 20).toFloat(), Sky)) }
        live.avgL100?.let { add(Vital(R.string.fuel_avg, units.consumption(it), (it / (targetL100 * 2)).toFloat(), consColor(it))) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.fuel_title).uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.forEach { t -> VitalTile(stringResource(t.label), t.text, t.fraction, t.color, Modifier.weight(1f)) }
            repeat(3 - tiles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun VitalsSection(v: Vitals, units: Units) {
    // Each entry is (label, text, 0..1 position in range, colour); only readings the car reports are shown.
    val tiles = buildList {
        v.coolantC?.let { add(Vital(R.string.vital_coolant, units.temperature(it), (it - 40) / 80f, coolantColor(it))) }
        v.oilC?.let { add(Vital(R.string.vital_oil, units.temperature(it), (it - 40) / 100f, coolantColor(it - 10))) }
        v.intakeC?.let { add(Vital(R.string.vital_intake, units.temperature(it), (it + 20) / 100f, Sky)) }
        v.throttlePercent?.let { add(Vital(R.string.vital_throttle, "${it.toInt()} %", it.toFloat() / 100f, Teal)) }
        v.manifoldKpa?.let { add(Vital(R.string.vital_map, "$it kPa", it / 255f, Teal)) }
        v.voltage?.let { add(Vital(R.string.vital_voltage, "${formatDecimal(it)} V", ((it - 10) / 5).toFloat(), voltageColor(it))) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.vitals_title).uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> VitalTile(stringResource(t.label), t.text, t.fraction, t.color, Modifier.weight(1f)) }
                // Pad short rows so tiles keep the same width.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class Vital(val label: Int, val text: String, val fraction: Float, val color: androidx.compose.ui.graphics.Color)

/** Cold engines are blue-ish, the normal band is green, and overheating turns amber then red. */
private fun coolantColor(c: Int) = when {
    c < 60 -> Sky
    c <= 100 -> Mint
    c <= 110 -> Amber
    else -> Coral
}

private fun voltageColor(v: Double) = when {
    v < 11.8 -> Coral
    v < 12.4 -> Amber
    else -> Mint
}

@Composable
private fun AddDriverDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.driver_add)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                label = { Text(stringResource(R.string.driver_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSheet(
    devices: List<com.natan.obd.bluetooth.PairedDevice>,
    onDismiss: () -> Unit,
    onPick: (com.natan.obd.bluetooth.PairedDevice) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.sheet_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text(stringResource(R.string.sheet_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            devices.forEach { device ->
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text(device.address) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier.clickableNoRipple { onPick(device) },
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                Text(stringResource(R.string.sheet_open_settings))
            }
        }
    }
}
