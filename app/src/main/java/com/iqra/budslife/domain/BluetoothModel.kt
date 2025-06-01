package com.iqra.budslife.domain

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import kotlinx.coroutines.delay
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.iqra.budslife.data.BluetoothRepo
import com.iqra.budslife.data.BudsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BluetoothModel(private val context: Context) {
    private val TAG = "BluetoothModel"
    private val repo = BluetoothRepo(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Add the alternative battery detector
    private val alternativeBatteryDetector = AlternativeBatteryDetector(context)

    // Connection states
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BudsEntity?>(null)
    val connectedDevice: StateFlow<BudsEntity?> = _connectedDevice.asStateFlow()

    private val _errorState = MutableStateFlow<ErrorState?>(null)
    val errorState: StateFlow<ErrorState?> = _errorState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    init {
        // Observe alternative battery detector
        coroutineScope.launch {
            alternativeBatteryDetector.batteryLevel.collect { batteryMap ->
                // Update battery levels for all detected devices
                batteryMap.forEach { (address, level) ->
                    updateBatteryLevel(address, level)
                }
            }
        }
    }

    // Modified connect method - skip GATT, use alternatives directly
    fun connectToDevice(address: String) {
        Log.d(TAG, "Attempting to connect to device (alternative methods): $address")

        try {
            val bluetoothDevices = repo.getPairedDevices()
            val device = bluetoothDevices.find { it.address == address }

            if (device == null) {
                Log.e(TAG, "Device not found in paired devices: $address")
                _errorState.value = ErrorState.DEVICE_NOT_FOUND
                return
            }

            _connectionState.value = ConnectionState.CONNECTING
            _errorState.value = null

            // Try alternative methods instead of GATT
            coroutineScope.launch {
                delay(500) // Small delay for stability

                val batteryResult = alternativeBatteryDetector.getBatteryLevelAllMethods(device)

                if (batteryResult != null && batteryResult.level != null) {
                    Log.d(TAG, "Successfully got battery level via alternatives: ${batteryResult.level}% (using ${batteryResult.method})")
                    mainHandler.post {
                        _connectionState.value = ConnectionState.CONNECTED
                        updateConnectedDeviceState(address)
                        updateBatteryLevel(address, batteryResult.level)
                    }
                } else {
                    Log.w(TAG, "Could not get battery level, using polling approach")
                    startBatteryPolling(device)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in connectToDevice", e)
            _errorState.value = ErrorState.CONNECTION_ERROR
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // Polling approach for devices that don't immediately report battery
    private fun startBatteryPolling(device: BluetoothDevice) {
        val pollingRunnable = object : Runnable {
            private var attempts = 0
            private val maxAttempts = 10

            override fun run() {
                if (attempts < maxAttempts) {
                    val batteryResult = alternativeBatteryDetector.getBatteryLevelAllMethods(device)

                    if (batteryResult != null && batteryResult.level != null) {
                        Log.d(TAG, "Got battery level via polling: ${batteryResult.level}% (${batteryResult.method})")
                        mainHandler.post {
                            _connectionState.value = ConnectionState.CONNECTED
                            updateConnectedDeviceState(device.address)
                            updateBatteryLevel(device.address, batteryResult.level)
                        }
                        return // Stop polling
                    }

                    attempts++
                    mainHandler.postDelayed(this, 2000) // Poll every 2 seconds
                } else {
                    Log.w(TAG, "Polling failed, using default battery level")
                    mainHandler.post {
                        _connectionState.value = ConnectionState.CONNECTED
                        updateConnectedDeviceState(device.address)
                        useDefaultBatteryLevel(device.address)
                    }
                }
            }
        }

        mainHandler.post(pollingRunnable)
    }

    // Simplified connection check - no GATT needed
    fun checkDeviceConnection(address: String): Boolean {
        return try {
            val bluetoothDevices = repo.getPairedDevices()
            val device = bluetoothDevices.find { it.address == address }

            if (device != null) {
                // Check if device is currently connected via any profile
                isDeviceConnected(device)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device connection", e)
            false
        }
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            if (!hasBluetoothPermissions()) return false

            // Check via reflection for connection state
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (e: Exception) {
            // Fallback: assume connected if we can get battery level
            alternativeBatteryDetector.getBatteryLevelAllMethods(device) != null
        }
    }

    // Batch update for multiple devices
    // Batch update for multiple devices
    fun updateAllDevicesBattery() {
        Log.d(TAG, "Updating battery levels for all paired devices")

        coroutineScope.launch {
            try {
                val pairedDevices = repo.getPairedDevices()

                pairedDevices.forEach { device ->
                    val batteryResult = alternativeBatteryDetector.getBatteryLevelAllMethods(device)

                    if (batteryResult != null && batteryResult.level != null) {
                        updateBatteryLevel(device.address, batteryResult.level)
                        Log.d(TAG, "Updated ${device.address}: ${batteryResult.level}% (${batteryResult.method})")
                    } else {
                        Log.d(TAG, "Could not get battery for ${device.address}")
                    }

                    delay(1000) // Space out requests
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating all devices battery", e)
            }
        }
    }

    // Simplified disconnect - no GATT to close
    fun disconnectDevice() {
        Log.d(TAG, "Disconnecting device")
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

    // Keep your existing helper methods
    private fun updateConnectedDeviceState(deviceAddress: String?) {
        deviceAddress?.let { address ->
            coroutineScope.launch {
                try {
                    repo.getBudsByAddress(address).collect { budsEntity ->
                        mainHandler.post {
                            _connectedDevice.value = budsEntity
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating connected device state", e)
                }
            }
        }
    }

    private fun updateBatteryLevel(deviceAddress: String?, batteryLevel: Int) {
        if (deviceAddress == null || batteryLevel !in 0..100) return

        mainHandler.post {
            _batteryLevel.value = batteryLevel
        }

        coroutineScope.launch {
            try {
                repo.updateBatteryLevel(deviceAddress, batteryLevel)
                Log.d(TAG, "Updated battery level in database: $deviceAddress -> $batteryLevel%")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating battery level in database", e)
            }
        }
    }

    suspend fun refreshBatteryLevel(deviceAddress: String) {
        try {
            if (!hasBluetoothPermissions()) {
                Log.w(TAG, "Missing Bluetooth permissions for refresh")
                return
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)

            if (device != null) {
                // Check permissions before accessing bondState
                val isBonded = try {
                    device.bondState == BluetoothDevice.BOND_BONDED
                } catch (se: SecurityException) {
                    Log.e(TAG, "Security exception checking bond state", se)
                    false
                }

                if (isBonded) {
                    // Use alternativeBatteryDetector instead of BatteryDetector
                    val batteryResult = alternativeBatteryDetector.getBatteryLevelAllMethods(device)

                    if (batteryResult != null && batteryResult.level != null && batteryResult.level > 0) {
                        _batteryLevel.value = batteryResult.level
                        // Use existing updateBatteryLevel method instead of updateBatteryInDatabase
                        updateBatteryLevel(deviceAddress, batteryResult.level)
                        Log.d(TAG, "Refreshed battery level: $deviceAddress -> ${batteryResult.level}% (${batteryResult.method})")
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "Security exception in refreshBatteryLevel", se)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing battery level for $deviceAddress", e)
        }
    }


    private fun useDefaultBatteryLevel(deviceAddress: String?) {
        Log.d(TAG, "Using default battery level (100%) for device: $deviceAddress")
        deviceAddress?.let { address ->
            updateBatteryLevel(address, 0)
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Cleanup method
    fun cleanup() {
        alternativeBatteryDetector.cleanup()
    }

    // Keep all your existing methods for device management
    fun getAllBuds(): Flow<List<BudsEntity>> = repo.getAllBuds()
    fun getBudsThatNeedCharging(): Flow<List<BudsEntity>> = repo.getBudsThatNeedCharging()
    fun updateDeviceThreshold(address: String, threshold: Int) {
        if (threshold in 1..99) {
            repo.updateBatteryThreshold(address, threshold)
        } else {
            _errorState.value = ErrorState.INVALID_THRESHOLD
        }
    }
    fun toggleNotifications(address: String, enabled: Boolean) = repo.setNotificationsEnabled(address, enabled)
    fun deleteDevice(buds: BudsEntity) = repo.deleteBuds(buds)
    fun addNewDevice(device: BluetoothDevice, initialBatteryLevel: Int = 100) = repo.addOrUpdateBuds(device, initialBatteryLevel)
    fun scanAndStorePairedDevices() {
        val devices = repo.getPairedDevices()
        devices.forEach { device -> repo.addOrUpdateBuds(device) }
    }
    fun clearError() { _errorState.value = null }
}

// Connection states
enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

// Error states
enum class ErrorState {
    PERMISSION_ERROR,
    CONNECTION_ERROR,
    SERVICE_DISCOVERY_ERROR,
    SERVICE_NOT_FOUND,
    CHARACTERISTIC_NOT_FOUND,
    READ_CHARACTERISTIC_ERROR,
    DEVICE_NOT_FOUND,
    GATT_NOT_INITIALIZED,
    INVALID_THRESHOLD,
    UNKNOWN_ERROR
}