package com.iqra.budslife.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import com.iqra.budslife.data.BudsEntity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iqra.budslife.domain.BluetoothModel
import com.iqra.budslife.domain.ConnectionState
import com.iqra.budslife.domain.ErrorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class BluetoothMonitorService : Service() {
    private val TAG = "BluetoothMonitorService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()
    private lateinit var bluetoothModel: BluetoothModel

    // Track connected device monitoring jobs
    private val monitoringJobs = ConcurrentHashMap<String, Job>()

    // Constants
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "buds_life_channel"
        private const val CHANNEL_NAME = "Buds Life"
        private const val BATTERY_CHECK_INTERVAL = 30000L // 30 seconds
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        bluetoothModel = BluetoothModel(applicationContext)

        // Register for Bluetooth events
        registerBluetoothReceivers()

        // Create notification channel
        createNotificationChannel()

        // Create the notification
        val notification = createForegroundNotification("Monitoring your AirBuds")

        // Start as foreground service with proper type specification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Check permissions first
        if (hasRequiredPermissions()) {
            // Start scanning for paired buds
            startMonitoringPairedBuds()
        } else {
            Log.w(TAG, "Missing required Bluetooth permissions")
            stopSelf()
        }

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        // Unregister receivers
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        // Cancel all monitoring jobs
        monitoringJobs.values.forEach { it.cancel() }
        monitoringJobs.clear()

        // Cancel all coroutines
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // Local binder for activity connections
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothMonitorService = this@BluetoothMonitorService
    }

    // Permission handling
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Broadcast receiver for Bluetooth events
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                    }

                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        Log.d(TAG, "Device connected: ${safeGetDeviceName(it)}")
                        handleConnectedDevice(it)
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        Log.d(TAG, "Device disconnected: ${safeGetDeviceName(it)}")
                        cancelDeviceMonitoring(it.address)
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        Log.d(TAG, "Bluetooth turned off")
                        monitoringJobs.keys.toList().forEach { cancelDeviceMonitoring(it) }
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "Bluetooth turned on")
                        startMonitoringPairedBuds()
                    }
                }
            }
        }
    }

    // Safety wrapper for device name access
    private fun safeGetDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device"
        }
    }

    // Register for Bluetooth events
    private fun registerBluetoothReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    // Handle newly connected devices
    private fun handleConnectedDevice(device: BluetoothDevice) {
        // Skip if already monitoring
        if (monitoringJobs.containsKey(device.address)) {
            return
        }

        // Add to database if new
        bluetoothModel.addNewDevice(device)

        // Start monitoring battery
        startDeviceMonitoring(device.address)
    }

    // Start monitoring all paired buds
    private fun startMonitoringPairedBuds() {
        try {
            val pairedDevices = bluetoothModel.scanAndStorePairedDevices()
            serviceScope.launch {
                bluetoothModel.getAllBuds().collectLatest { budsList ->
                    budsList.forEach { buds ->
                        if (!monitoringJobs.containsKey(buds.deviceAddress)) {
                            startDeviceMonitoring(buds.deviceAddress)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied while fetching paired devices", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting device monitoring", e)
        }
    }

    // Start monitoring a specific device - FIXED VERSION
    private fun startDeviceMonitoring(deviceAddress: String) {
        if (monitoringJobs.containsKey(deviceAddress)) {
            return
        }

        monitoringJobs[deviceAddress] = serviceScope.launch {
            try {
                Log.d(TAG, "Starting to monitor device: $deviceAddress")

                // Connect to the device
                bluetoothModel.connectToDevice(deviceAddress)

                // Wait a bit for connection to establish
                delay(2000)

                // Use combine to merge battery level and device info flows
                combine(
                    bluetoothModel.batteryLevel,
                    bluetoothModel.connectedDevice
                ) { batteryLevel, connectedDevice ->
                    Pair(batteryLevel, connectedDevice)
                }.collectLatest { (batteryLevel, connectedDevice) ->

                    // Only process if we have valid data for this specific device
                    if (connectedDevice != null &&
                        connectedDevice.deviceAddress == deviceAddress &&
                        batteryLevel != null &&
                        batteryLevel > 0) {

                        Log.d(TAG, "Valid battery data for $deviceAddress: $batteryLevel%")

                        // Check if notification needs to be shown
                        if (connectedDevice.notificationsEnabled &&
                            batteryLevel <= connectedDevice.batteryThreshold) {

                            Log.d(TAG, "Battery low notification for ${connectedDevice.deviceName}: $batteryLevel%")
                            showLowBatteryNotification(
                                deviceName = connectedDevice.deviceName,
                                batteryLevel = batteryLevel,
                                deviceAddress = connectedDevice.deviceAddress
                            )
                        }
                    } else {
                        // If battery level is 0 or invalid, try to refresh
                        if (connectedDevice?.deviceAddress == deviceAddress) {
                            Log.d(TAG, "Invalid battery data for $deviceAddress, attempting refresh...")
                            delay(BATTERY_CHECK_INTERVAL)
                            bluetoothModel.refreshBatteryLevel(deviceAddress)
                        }
                    }
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied while monitoring device $deviceAddress", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring device $deviceAddress", e)
            }
        }
    }

    // Stop monitoring a device
    private fun cancelDeviceMonitoring(deviceAddress: String) {
        Log.d(TAG, "Canceling monitoring for device: $deviceAddress")
        monitoringJobs[deviceAddress]?.cancel()
        monitoringJobs.remove(deviceAddress)

        // Only disconnect if this was the currently connected device
        serviceScope.launch {
            bluetoothModel.connectedDevice.value?.let { connectedDevice ->
                if (connectedDevice.deviceAddress == deviceAddress) {
                    bluetoothModel.disconnectDevice()
                }
            }
        }
    }

    // Create notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications about AirBuds battery levels"
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Create the foreground notification
    private fun createForegroundNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BudsLife Active")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    // Show low battery notification
    private fun showLowBatteryNotification(deviceName: String, batteryLevel: Int, deviceAddress: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = deviceAddress.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Low")
            .setContentText("$deviceName battery is at $batteryLevel%")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    // Expose model for binding activities
    fun getBluetoothModel(): BluetoothModel = bluetoothModel
}