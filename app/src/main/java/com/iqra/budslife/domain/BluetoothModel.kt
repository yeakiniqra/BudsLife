package com.iqra.budslife.domain

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
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

    // Connection states
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Currently connected device
    private val _connectedDevice = MutableStateFlow<BudsEntity?>(null)
    val connectedDevice: StateFlow<BudsEntity?> = _connectedDevice.asStateFlow()

    // Error states
    private val _errorState = MutableStateFlow<ErrorState?>(null)
    val errorState: StateFlow<ErrorState?> = _errorState.asStateFlow()

    // Battery level
    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    // Active GATT connection
    private var bluetoothGatt: BluetoothGatt? = null

    // Standard UUIDs for battery service and characteristic
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val BATTERY_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    // GATT callback to handle connection events and characteristic reads
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server.")
                    _connectionState.value = ConnectionState.CONNECTED

                    // Discover services after successful connection
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        _errorState.value = ErrorState.PERMISSION_ERROR
                        disconnectDevice()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server.")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    closeGatt()
                }
                else -> {
                    Log.d(TAG, "Connection state changed: $newState")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                readBatteryLevel()
            } else {
                Log.w(TAG, "Service discovery failed with status: $status")
                _errorState.value = ErrorState.SERVICE_DISCOVERY_ERROR
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleCharacteristicRead(gatt, characteristic, status, characteristic.value)
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(gatt, characteristic, status, value)
        }


        private fun handleCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
            value: ByteArray?
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_CHARACTERISTIC_UUID) {
                    val batteryLevel = value?.get(0)?.toInt() ?: 0
                    Log.d(TAG, "Battery Level: $batteryLevel%")
                    _batteryLevel.value = batteryLevel

                    // Update the database with the new battery level
                    gatt.device?.address?.let { address ->
                        repo.updateBatteryLevel(address, batteryLevel)

                        // Update the current device
                        coroutineScope.launch {
                            repo.getBudsByAddress(address).collect { buds ->
                                _connectedDevice.value = buds
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "Characteristic read failed with status: $status")
                _errorState.value = ErrorState.READ_CHARACTERISTIC_ERROR
            }
        }
    }

    // Helper method to check Bluetooth permissions
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // Get all saved devices
    fun getAllBuds(): Flow<List<BudsEntity>> {
        return repo.getAllBuds()
    }

    // Get devices that need charging
    fun getBudsThatNeedCharging(): Flow<List<BudsEntity>> {
        return repo.getBudsThatNeedCharging()
    }

    // Connect to a device and read its battery level
    fun connectToDevice(address: String) {
        try {
            val bluetoothDevices = repo.getPairedDevices()
            val device = bluetoothDevices.find { it.address == address }

            if (device == null) {
                _errorState.value = ErrorState.DEVICE_NOT_FOUND
                return
            }

            // Reset state
            _connectionState.value = ConnectionState.CONNECTING
            _errorState.value = null
            _batteryLevel.value = null

            // Connect to the device
            try {
                bluetoothGatt = device.connectGatt(
                    context,
                    false, // Don't auto-connect
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error when connecting to device", e)
                _errorState.value = ErrorState.PERMISSION_ERROR
                _connectionState.value = ConnectionState.DISCONNECTED
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device", e)
                _errorState.value = ErrorState.CONNECTION_ERROR
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectToDevice", e)
            _errorState.value = ErrorState.UNKNOWN_ERROR
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // Read the battery level characteristic
    private fun readBatteryLevel() {
        bluetoothGatt?.let { gatt ->
            try {
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                if (batteryService == null) {
                    Log.w(TAG, "Battery service not found")
                    _errorState.value = ErrorState.SERVICE_NOT_FOUND
                    return
                }

                val batteryCharacteristic = batteryService.getCharacteristic(BATTERY_CHARACTERISTIC_UUID)
                if (batteryCharacteristic == null) {
                    Log.w(TAG, "Battery characteristic not found")
                    _errorState.value = ErrorState.CHARACTERISTIC_NOT_FOUND
                    return
                }

                gatt.readCharacteristic(batteryCharacteristic)
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error when reading battery level", e)
                _errorState.value = ErrorState.PERMISSION_ERROR
            } catch (e: Exception) {
                Log.e(TAG, "Error reading battery level", e)
                _errorState.value = ErrorState.READ_CHARACTERISTIC_ERROR
            }
        } ?: run {
            _errorState.value = ErrorState.GATT_NOT_INITIALIZED
        }
    }

    // Disconnect from the device
    fun disconnectDevice() {
        if (hasBluetoothPermissions()) {
            try {
                bluetoothGatt?.disconnect()
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied when disconnecting device", e)
                _errorState.value = ErrorState.PERMISSION_ERROR
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting device", e)
            }
        } else {
            Log.e(TAG, "Missing Bluetooth permissions for disconnect")
            _errorState.value = ErrorState.PERMISSION_ERROR
        }

        _connectionState.value = ConnectionState.DISCONNECTED
    }



    // Close the GATT connection
    private fun closeGatt() {
        if (hasBluetoothPermissions()) {
            try {
                bluetoothGatt?.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied when closing GATT", e)
                _errorState.value = ErrorState.PERMISSION_ERROR
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT connection", e)
            }
        } else {
            Log.e(TAG, "Missing Bluetooth permissions for GATT close")
            _errorState.value = ErrorState.PERMISSION_ERROR
        }

        bluetoothGatt = null
    }


    // Update device threshold for notifications
    fun updateDeviceThreshold(address: String, threshold: Int) {
        if (threshold in 1..99) {
            repo.updateBatteryThreshold(address, threshold)
        } else {
            _errorState.value = ErrorState.INVALID_THRESHOLD
        }
    }

    // Toggle notifications for a device
    fun toggleNotifications(address: String, enabled: Boolean) {
        repo.setNotificationsEnabled(address, enabled)
    }

    // Delete a device from the database
    fun deleteDevice(buds: BudsEntity) {
        repo.deleteBuds(buds)
    }

    // Add a new device to the database
    fun addNewDevice(device: BluetoothDevice, initialBatteryLevel: Int = 0) {
        repo.addOrUpdateBuds(device, initialBatteryLevel)
    }

    // Scan for paired devices and store them
    fun scanAndStorePairedDevices() {
        val devices = repo.getPairedDevices()
        devices.forEach { device ->
            repo.addOrUpdateBuds(device)
        }
    }

    // Clear the error state
    fun clearError() {
        _errorState.value = null
    }
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