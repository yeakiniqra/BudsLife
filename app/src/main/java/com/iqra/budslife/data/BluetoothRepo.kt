package com.iqra.budslife.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

class BluetoothRepo(private val context: Context) {
    private val budsDao = BudsDatabase.getDatabase(context).budsDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    private fun hasBluetoothPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Get all saved buds from the database
    fun getAllBuds(): Flow<List<BudsEntity>> {
        return budsDao.getAllBuds()
    }

    // Get a specific bud by its Bluetooth MAC address
    fun getBudsByAddress(address: String): Flow<BudsEntity?> {
        return budsDao.getBudsByAddress(address)
    }

    // Add a new bud to the database or update an existing one
    fun addOrUpdateBuds(device: BluetoothDevice, batteryLevel: Int = 0) {
        coroutineScope.launch {
            val deviceName = if (hasBluetoothPermissions()) {
                try {
                    device.name ?: "Unknown Device"
                } catch (e: SecurityException) {
                    "Unknown Device"
                }
            } else {
                "Unknown Device"
            }

            val budsEntity = BudsEntity(
                deviceAddress = device.address,
                deviceName = deviceName,
                lastBatteryLevel = batteryLevel,
                lastConnected = Date()
            )
            budsDao.insertBuds(budsEntity)
        }
    }

    // Update device details with manufacturer and model info if available
    fun updateDeviceDetails(address: String, model: String?, manufacturer: String?) {
        coroutineScope.launch {
            budsDao.updateModelAndManufacturer(address, model, manufacturer)
        }
    }

    // Update the battery level for a bud
    fun updateBatteryLevel(address: String, batteryLevel: Int) {
        coroutineScope.launch {
            budsDao.updateBatteryLevel(address, batteryLevel)
        }
    }

    // Update the battery threshold for notifications
    fun updateBatteryThreshold(address: String, threshold: Int) {
        coroutineScope.launch {
            budsDao.updateBatteryThreshold(address, threshold)
        }
    }

    // Enable or disable notifications for a bud
    fun setNotificationsEnabled(address: String, enabled: Boolean) {
        coroutineScope.launch {
            budsDao.updateNotificationsEnabled(address, enabled)
        }
    }

    // Delete a bud from the database
    fun deleteBuds(buds: BudsEntity) {
        coroutineScope.launch {
            budsDao.deleteBuds(buds)
        }
    }

    // Get buds that need to be charged (below threshold)
    fun getBudsThatNeedCharging(): Flow<List<BudsEntity>> {
        return budsDao.getAllBuds().map { budsList ->
            budsList.filter {
                it.notificationsEnabled && it.lastBatteryLevel <= it.batteryThreshold
            }
        }
    }

    // Get a list of paired Bluetooth devices
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            return emptyList()
        }

        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    // Check if a device is currently connected
    fun isDeviceConnected(address: String): Boolean {
        if (!hasBluetoothPermissions()) {
            return false
        }

        return try {
            bluetoothAdapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
        } catch (e: SecurityException) {
            false
        }
    }
}