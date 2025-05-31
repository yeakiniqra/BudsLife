package com.iqra.budslife.presentation

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqra.budslife.data.BudsEntity
import com.iqra.budslife.domain.BluetoothModel
import com.iqra.budslife.domain.ConnectionState
import com.iqra.budslife.domain.ErrorState
import com.iqra.budslife.service.BluetoothMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Permission states
sealed class PermissionState {
    object Unknown : PermissionState()
    object Granted : PermissionState()
    object Denied : PermissionState()
}

// UI states
sealed class UiState {
    object Loading : UiState()
    object Ready : UiState()
    object Connecting : UiState()
    object Connected : UiState()
    object Disconnected : UiState()
    object NeedsPermission : UiState()
    object BluetoothDisabled : UiState()
    object NoDevices : UiState()
    data class Error(val message: String) : UiState()
}

class BluetoothViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "BluetoothViewModel"
    private val context = application.applicationContext
    private val bluetoothModel = BluetoothModel(context)

    // UI States
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Permission states
    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Unknown)
    val permissionState = _permissionState.asStateFlow()

    // Bluetooth states
    val isBluetoothEnabled = mutableStateOf(isBluetoothOn())

    // Device states
    val connectionState = bluetoothModel.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val currentDevice = bluetoothModel.connectedDevice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val batteryLevel = bluetoothModel.batteryLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val errorState = bluetoothModel.errorState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // All devices with battery status
    val allDevices = bluetoothModel.getAllBuds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Devices that need charging
    val devicesNeedingCharge = bluetoothModel.getBudsThatNeedCharging()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined state for UI
    val devicesSortedByBatteryLevel = allDevices.map { devices ->
        devices.sortedByDescending { it.lastBatteryLevel }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkPermissions()

        // Monitor error state
        viewModelScope.launch {
            errorState.collect { error ->
                error?.let {
                    handleError(it)
                }
            }
        }

        // Monitor connection state
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> _uiState.value = UiState.Connected
                    ConnectionState.CONNECTING -> _uiState.value = UiState.Connecting
                    ConnectionState.DISCONNECTED -> {
                        // Only change to Disconnected if we were previously connected or connecting
                        if (_uiState.value is UiState.Connected || _uiState.value is UiState.Connecting) {
                            _uiState.value = UiState.Disconnected
                        }
                    }
                }
            }
        }

        // Update UI state based on devices and permissions
        viewModelScope.launch {
            combine(allDevices, permissionState) { devices, permissions ->
                Pair(devices, permissions)
            }.collect { (devices, permissions) ->
                when {
                    permissions != PermissionState.Granted -> _uiState.value = UiState.NeedsPermission
                    !isBluetoothEnabled.value -> _uiState.value = UiState.BluetoothDisabled
                    devices.isEmpty() -> _uiState.value = UiState.NoDevices
                    else -> {
                        if (_uiState.value !is UiState.Connected &&
                            _uiState.value !is UiState.Connecting) {
                            _uiState.value = UiState.Ready
                        }
                    }
                }
            }
        }
    }

    // Start background service
    fun startMonitoringService() {
        if (checkPermissions() && isBluetoothEnabled.value) {
            val serviceIntent = Intent(context, BluetoothMonitorService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    // Stop background service
    fun stopMonitoringService() {
        val serviceIntent = Intent(context, BluetoothMonitorService::class.java)
        context.stopService(serviceIntent)
    }

    // Connect to a device
    fun connectToDevice(deviceAddress: String) {
        if (!checkPermissions()) {
            _uiState.value = UiState.NeedsPermission
            return
        }

        if (!isBluetoothEnabled.value) {
            _uiState.value = UiState.BluetoothDisabled
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Connecting
            bluetoothModel.connectToDevice(deviceAddress)
        }
    }

    // Disconnect from current device
    fun disconnectDevice() {
        viewModelScope.launch {
            bluetoothModel.disconnectDevice()
        }
    }

    // Update device notification threshold
    fun updateDeviceThreshold(deviceAddress: String, threshold: Int) {
        if (threshold !in 1..99) {
            _uiState.value = UiState.Error("Threshold must be between 1% and 99%")
            return
        }

        viewModelScope.launch {
            bluetoothModel.updateDeviceThreshold(deviceAddress, threshold)
        }
    }

    // Toggle notifications for a device
    fun toggleNotifications(deviceAddress: String, enabled: Boolean) {
        viewModelScope.launch {
            bluetoothModel.toggleNotifications(deviceAddress, enabled)
        }
    }

    // Delete a device
    fun deleteDevice(device: BudsEntity) {
        viewModelScope.launch {
            bluetoothModel.deleteDevice(device)
        }
    }

    // Refresh paired devices
    fun refreshPairedDevices() {
        if (!checkPermissions()) {
            _uiState.value = UiState.NeedsPermission
            return
        }

        if (!isBluetoothEnabled.value) {
            _uiState.value = UiState.BluetoothDisabled
            return
        }

        viewModelScope.launch {
            bluetoothModel.scanAndStorePairedDevices()
        }
    }

    // Handle errors from the model
    private fun handleError(error: ErrorState) {
        when (error) {
            ErrorState.PERMISSION_ERROR -> {
                _permissionState.value = PermissionState.Denied
                _uiState.value = UiState.NeedsPermission
            }
            ErrorState.CONNECTION_ERROR -> {
                _uiState.value = UiState.Error("Failed to connect to device")
            }
            ErrorState.SERVICE_DISCOVERY_ERROR -> {
                _uiState.value = UiState.Error("Failed to discover device services")
            }
            ErrorState.SERVICE_NOT_FOUND -> {
                _uiState.value = UiState.Error("Battery service not found on device")
            }
            ErrorState.CHARACTERISTIC_NOT_FOUND -> {
                _uiState.value = UiState.Error("Battery characteristic not found on device")
            }
            ErrorState.READ_CHARACTERISTIC_ERROR -> {
                _uiState.value = UiState.Error("Failed to read battery level")
            }
            ErrorState.DEVICE_NOT_FOUND -> {
                _uiState.value = UiState.Error("Device not found or not paired")
            }
            ErrorState.GATT_NOT_INITIALIZED -> {
                _uiState.value = UiState.Error("Bluetooth connection not initialized")
            }
            ErrorState.INVALID_THRESHOLD -> {
                _uiState.value = UiState.Error("Invalid battery threshold value")
            }
            ErrorState.UNKNOWN_ERROR -> {
                _uiState.value = UiState.Error("Unknown error occurred")
            }
        }

        // Clear error after handling
        bluetoothModel.clearError()
    }

    // Check if Bluetooth is enabled
    private fun isBluetoothOn(): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            bluetoothAdapter?.isEnabled ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth state", e)
            false
        }
    }

    // Check required permissions
    fun checkPermissions(): Boolean {
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
        }

        _permissionState.value = if (hasPermissions) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }

        return hasPermissions
    }

    // Clear UI error state
    fun clearUiError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Ready
        }
    }

    // Reset to initial state
    fun resetState() {
        _uiState.value = UiState.Loading
        checkPermissions()
        isBluetoothEnabled.value = isBluetoothOn()
    }
}