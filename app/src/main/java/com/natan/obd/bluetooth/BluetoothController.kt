package com.natan.obd.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.natan.obd.R
import com.natan.obd.obd.Elm327Client
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PairedDevice(val name: String, val address: String)

sealed interface LinkState {
    data object Disconnected : LinkState
    data class Connecting(val name: String) : LinkState
    data class Connected(val name: String, val address: String) : LinkState
    data class Failed(val message: String) : LinkState
}

/**
 * Owns the single Bluetooth socket to the OBD adapter. Replaces the old global socket holder and
 * the busy-wait on the main thread: [connect] is a suspend function that reports a real result.
 */
@Singleton
@SuppressLint("MissingPermission") // every entry point checks hasPermission() first
class BluetoothController @Inject constructor(@ApplicationContext private val context: Context) {

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var socket: BluetoothSocket? = null

    private val _state = MutableStateFlow<LinkState>(LinkState.Disconnected)
    val state: StateFlow<LinkState> = _state

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun pairedDevices(): List<PairedDevice> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty()
            .map { PairedDevice(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun connect(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        closeSocket()
        val adapter = adapter
        when {
            adapter == null -> return@withContext fail(R.string.err_no_bluetooth)
            !adapter.isEnabled -> return@withContext fail(R.string.err_bluetooth_off)
            !hasPermission() -> return@withContext fail(R.string.err_bt_permission)
        }
        adapter!!
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return@withContext fail(R.string.err_connect_failed)
        }
        val name = device.name ?: address
        _state.value = LinkState.Connecting(name)
        adapter.cancelDiscovery() // discovery slows connecting down considerably

        // Some cheap ELM327 clones only accept the insecure RFCOMM variant, so try both.
        for (insecure in listOf(false, true)) {
            var candidate: BluetoothSocket? = null
            try {
                candidate = if (insecure) device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                else device.createRfcommSocketToServiceRecord(SPP_UUID)
                candidate.connect()
                socket = candidate
                _state.value = LinkState.Connected(name, address)
                return@withContext Result.success(Unit)
            } catch (e: IOException) {
                runCatching { candidate?.close() }
            } catch (e: SecurityException) {
                runCatching { candidate?.close() }
                return@withContext fail(R.string.err_bt_permission)
            }
        }
        fail(R.string.err_connect_failed)
    }

    /** A fresh protocol client over the connected socket, or null when not connected. */
    fun openClient(): Elm327Client? =
        socket?.let { Elm327Client(it.inputStream, it.outputStream) }

    fun disconnect() {
        closeSocket()
        _state.value = LinkState.Disconnected
    }

    /** Called when reads fail mid-trip: the socket is dead, so release it and tell the UI. */
    fun markLost() {
        closeSocket()
        _state.value = LinkState.Failed(context.getString(R.string.err_link_lost))
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun fail(messageRes: Int): Result<Unit> {
        val message = context.getString(messageRes)
        _state.value = LinkState.Failed(message)
        return Result.failure(IOException(message))
    }

    private companion object {
        /** Standard Serial Port Profile UUID used by ELM327 adapters. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
