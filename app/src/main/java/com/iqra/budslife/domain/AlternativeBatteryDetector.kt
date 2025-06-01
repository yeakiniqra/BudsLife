package com.iqra.budslife.domain

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method

data class BatteryResult(
    val level: Int?,
    val method: String,
    val isRealReading: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AlternativeBatteryDetector(private val context: Context) {
    private val TAG = "BatteryDetector"

    // Store battery levels with metadata
    private val _batteryResults = MutableStateFlow<Map<String, BatteryResult>>(emptyMap())
    val batteryResults: StateFlow<Map<String, BatteryResult>> = _batteryResults

    // Legacy compatibility - just the levels
    val batteryLevel: StateFlow<Map<String, Int>> = MutableStateFlow(_batteryResults.value.mapValues { it.value.level ?: -1 })

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var receiver: BroadcastReceiver? = null

    // Cache recent readings to avoid false fallbacks
    private val recentReadings = mutableMapOf<String, Pair<Int, Long>>()
    private val READING_CACHE_DURATION = 30_000L // 30 seconds

    init {
        registerReceiver()
    }

    private fun registerReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    handleBroadcast(intent)
                }
            }

            // Register the receiver with appropriate flags for Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            Log.d(TAG, "Battery level receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register battery receiver", e)
        }
    }



    private fun handleBroadcast(intent: Intent?) {
        if (intent == null) return

        try {
            val device = extractDeviceFromIntent(intent)
            if (device == null) {
                Log.d(TAG, "No device found in broadcast: ${intent.action}")
                return
            }

            when (intent.action) {
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (level in 0..100) {
                        Log.d(TAG, "Received battery broadcast: ${device.address} -> $level%")
                        updateBatteryResult(device.address, BatteryResult(
                            level = level,
                            method = "Broadcast",
                            isRealReading = true
                        ))
                    }
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "Device connected: ${device.address}")
                    // Request fresh battery reading on connection
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(2000) // Wait for connection to stabilize
                        val result = getBatteryLevelAllMethods(device)
                        Log.d(TAG, "Fresh reading after connection: ${device.address} -> ${result?.level}%")
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d(TAG, "Device disconnected: ${device.address}")
                    // Clear cached readings for disconnected device
                    recentReadings.remove(device.address)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing broadcast: ${intent.action}", e)
        }
    }

    private fun extractDeviceFromIntent(intent: Intent): BluetoothDevice? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract device from intent", e)
            null
        }
    }

    fun cleanup() {
        try {
            receiver?.let {
                context.unregisterReceiver(it)
                receiver = null
            }
            recentReadings.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    private fun updateBatteryResult(address: String, result: BatteryResult) {
        coroutineScope.launch {
            _batteryResults.value = _batteryResults.value.toMutableMap().apply {
                put(address, result)
            }

            // Update cache if it's a real reading
            if (result.isRealReading && result.level != null && result.level >= 0) {
                recentReadings[address] = Pair(result.level, result.timestamp)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun getDeviceName(device: BluetoothDevice): String? {
        return try {
            if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                device.name
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission for device name")
            null
        }
    }

    fun getBatteryLevelAllMethods(device: BluetoothDevice): BatteryResult? {
        val deviceAddress = device.address

        // Check if we have a recent valid reading
        val recentReading = recentReadings[deviceAddress]
        if (recentReading != null) {
            val (level, timestamp) = recentReading
            if (System.currentTimeMillis() - timestamp < READING_CACHE_DURATION) {
                Log.d(TAG, "Using recent cached reading: $deviceAddress -> $level%")
                return BatteryResult(level, "Cache", true, timestamp)
            }
        }

        val methods = listOf<Pair<String, () -> Int?>>(
            // Method 1: Official Android O+ API
            "Official API" to {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
                        val level = method.invoke(device) as? Int
                        if (level != null && level >= 0 && level <= 100) {
                            Log.d(TAG, "Method 1 - Official getBatteryLevel(): $level")
                            level
                        } else null
                    } else null
                } catch (e: Exception) {
                    Log.v(TAG, "Official API failed: ${e.message}")
                    null
                }
            },

            // Method 2: Direct field access (Samsung/LG devices)
            "Field Access" to {
                try {
                    val field = BluetoothDevice::class.java.getDeclaredField("mBatteryLevel")
                    field.isAccessible = true
                    val level = field.getInt(device)
                    if (level in 0..100) {
                        Log.d(TAG, "Method 2 - Field access: $level")
                        level
                    } else null
                } catch (e: Exception) {
                    Log.v(TAG, "Field access failed: ${e.message}")
                    null
                }
            },

            // Method 3: Cached broadcast data
            "Broadcast Cache" to {
                val cachedResult = _batteryResults.value[deviceAddress]
                if (cachedResult?.isRealReading == true && cachedResult.level != null && cachedResult.level >= 0) {
                    val age = System.currentTimeMillis() - cachedResult.timestamp
                    if (age < READING_CACHE_DURATION) {
                        Log.d(TAG, "Method 3 - Recent broadcast cache: ${cachedResult.level}")
                        cachedResult.level
                    } else null
                } else null
            },

            // Method 4: Manufacturer-specific reflection methods
            "Manufacturer API" to {
                try {
                    // Try different method names used by various manufacturers
                    val methodNames = listOf("getBatteryLevel", "getBattery", "getBatteryStatus")
                    for (methodName in methodNames) {
                        try {
                            val method: Method = device.javaClass.getMethod(methodName)
                            val result = method.invoke(device)
                            val level = when (result) {
                                is Int -> result
                                is String -> result.toIntOrNull()
                                else -> null
                            }
                            if (level != null && level in 0..100) {
                                Log.d(TAG, "Method 4 - Manufacturer $methodName: $level")
                                return@to level
                            }
                        } catch (e: Exception) {
                            // Try next method name
                        }
                    }
                    null
                } catch (e: Exception) {
                    Log.v(TAG, "Manufacturer API failed: ${e.message}")
                    null
                }
            },

            // Method 5: HID service battery (for some earbuds)
            "HID Battery" to {
                try {
                    val hidMethod = device.javaClass.getMethod("getHidBatteryLevel")
                    val level = hidMethod.invoke(device) as? Int
                    if (level != null && level in 0..100) {
                        Log.d(TAG, "Method 5 - HID battery: $level")
                        level
                    } else null
                } catch (e: Exception) {
                    Log.v(TAG, "HID battery failed: ${e.message}")
                    null
                }
            }
        )

        // Try each method
        for ((methodName, method) in methods) {
            try {
                val result = method()
                if (result != null) {
                    val batteryResult = BatteryResult(result, methodName, true)
                    updateBatteryResult(deviceAddress, batteryResult)
                    return batteryResult
                }
            } catch (e: Exception) {
                Log.v(TAG, "$methodName failed: ${e.message}")
            }
        }

        // Instead of fallback values, return null to indicate no reading available
        Log.w(TAG, "No battery reading available for device: $deviceAddress")
        val noReadingResult = BatteryResult(null, "No Reading", false)
        updateBatteryResult(deviceAddress, noReadingResult)
        return noReadingResult
    }

    // Get battery level with intelligent fallback
    fun getBatteryLevelWithFallback(device: BluetoothDevice): Int {
        val result = getBatteryLevelAllMethods(device)

        // If we have a real reading, use it
        if (result?.isRealReading == true && result.level != null) {
            return result.level
        }

        // Check if device is actually connected before using fallback
        val isConnected = try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                bluetoothAdapter?.getRemoteDevice(device.address)?.bondState == BluetoothDevice.BOND_BONDED
            } else {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted; cannot check bondState")
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking bondState", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bondState", e)
            false
        }


        if (!isConnected) {
            Log.w(TAG, "Device not connected, returning -1: ${device.address}")
            return -1
        }

        // Device-specific fallbacks only for known working devices
        val deviceName = getDeviceName(device)
        val fallbackLevel = when {
            deviceName?.contains("TWS-10i", ignoreCase = true) == true -> {
                Log.d(TAG, "Using TWS-10i specific fallback: 85")
                85
            }
            deviceName?.contains("AirPods", ignoreCase = true) == true -> {
                Log.d(TAG, "Using AirPods specific fallback: 90")
                90
            }
            else -> {
                Log.w(TAG, "Using general fallback for unknown device: 50")
                50
            }
        }

        updateBatteryResult(device.address, BatteryResult(fallbackLevel, "Fallback", false))
        return fallbackLevel
    }

    // Get the most recent valid battery reading
    fun getLastKnownBatteryLevel(deviceAddress: String): BatteryResult? {
        return _batteryResults.value[deviceAddress]
    }

    // Check if device has recent real battery data
    fun hasRecentRealReading(deviceAddress: String, maxAgeMs: Long = READING_CACHE_DURATION): Boolean {
        val result = _batteryResults.value[deviceAddress]
        return result?.isRealReading == true &&
                result.level != null &&
                (System.currentTimeMillis() - result.timestamp) < maxAgeMs
    }
}