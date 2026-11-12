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
import androidx.compose.material.icons.filled.Close
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
import com.natan.obd.trip.LiveData
import com.natan.obd.trip.TripState
import com.natan.obd.ui.components.ArcGauge
import com.natan.obd.ui.components.ScreenTitle
import com.natan.obd.ui.components.StatTile
import com.natan.obd.ui.components.SurfaceCard
import com.natan.obd.ui.formatDecimal
import com.natan.obd.ui.formatDuration
import com.natan.obd.ui.theme.Amber
import com.natan.obd.ui.theme.Coral
import com.natan.obd.ui.theme.Mint
import com.natan.obd.ui.theme.Sky
import com.natan.obd.ui.theme.Teal
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

        Dashboard(live = running?.live ?: LiveData(), running = running != null)

        if (running != null && !running.live.hasData) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.drive_warming_up), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val live = running?.live ?: LiveData()
            StatTile(stringResource(R.string.tile_distance), "${formatDecimal(live.distanceKm)} km", Modifier.weight(1f))
            StatTile(stringResource(R.string.tile_time), formatDuration(live.elapsedSec), Modifier.weight(1f))
            StatTile(stringResource(R.string.tile_avg_speed), "${live.avgSpeedKmh} km/h", Modifier.weight(1f))
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
                onClick = { requestAndStart(context, viewModel, startPermissions::launch) },
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

private fun requestAndStart(context: Context, vm: DriveViewModel, launch: (Array<String>) -> Unit) {
    val needed = buildList {
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
private fun Dashboard(live: LiveData, running: Boolean) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ArcGauge(
                value = live.speedKmh.toFloat(), max = 220f,
                label = stringResource(R.string.gauge_speed), unit = "km/h",
                colors = listOf(Teal, Sky, Amber), gaugeSize = 240.dp, big = true,
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
