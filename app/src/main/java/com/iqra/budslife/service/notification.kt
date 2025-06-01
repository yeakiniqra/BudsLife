package com.iqra.budslife.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iqra.budslife.data.BluetoothRepo
import com.iqra.budslife.domain.BluetoothModel
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * NotificationHandler manages all notification-related functionality.
 * It uses WorkManager for reliable background processing and handles
 * battery threshold notifications.
 */
class NotificationHandler(private val context: Context) {
    private val TAG = "NotificationHandler"
    private val CHANNEL_ID = "buds_life_battery_channel"
    private val CHANNEL_NAME = "Battery Alerts"
    private val WORK_NAME = "BATTERY_CHECK_WORK"

    init {
        createNotificationChannel()
    }

    /**
     * Schedules periodic battery checks for all devices
     * @param intervalMinutes How often to check battery levels (in minutes)
     */
    fun scheduleBatteryChecks(intervalMinutes: Int = 15) {
        try {
            Log.d(TAG, "Scheduling battery checks every $intervalMinutes minutes")

            val batteryCheckRequest = PeriodicWorkRequestBuilder<BatteryCheckWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update if exists
                batteryCheckRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling battery checks", e)
        }
    }

    /**
     * Cancels scheduled battery checks
     */
    fun cancelBatteryChecks() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Battery checks canceled")
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling battery checks", e)
        }
    }

    /**
     * Creates the notification channel for battery alerts
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for low battery levels on your devices"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Shows a battery low notification for a specific device
     */
    fun showLowBatteryNotification(deviceName: String, batteryLevel: Int, deviceAddress: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = deviceAddress.hashCode()

            // Create intent to open app when notification is tapped
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE
            )

            // Build the notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Battery Low")
                .setContentText("$deviceName battery is at $batteryLevel%")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Battery notification shown for $deviceName: $batteryLevel%")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
}

/**
 * Worker that periodically checks battery levels and shows notifications
 * when they fall below the configured threshold
 */
class BatteryCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val TAG = "BatteryCheckWorker"
    private val repo = BluetoothRepo(applicationContext)
    private val notificationHandler = NotificationHandler(applicationContext)
    private val bluetoothModel = BluetoothModel(applicationContext)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Battery check worker running")

        try {
            // Get all devices that have notifications enabled
            val devices = repo.getAllBuds().first().filter { it.notificationsEnabled }

            // Check each device's battery level
            for (device in devices) {
                // Skip devices with invalid or unknown battery levels
                if (device.lastBatteryLevel <= 0) continue

                // Check threshold and notify if needed
                if (device.lastBatteryLevel <= device.batteryThreshold) {
                    Log.d(TAG, "Device ${device.deviceName} (${device.deviceAddress}) " +
                            "battery level ${device.lastBatteryLevel}% is below threshold ${device.batteryThreshold}%")

                    notificationHandler.showLowBatteryNotification(
                        deviceName = device.deviceName,
                        batteryLevel = device.lastBatteryLevel,
                        deviceAddress = device.deviceAddress
                    )
                } else {
                    Log.d(TAG, "Device ${device.deviceName} battery level ${device.lastBatteryLevel}% " +
                            "is above threshold ${device.batteryThreshold}%")
                }

                // Try to refresh the battery level for next check
                bluetoothModel.refreshBatteryLevel(device.deviceAddress)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery levels", e)
            return Result.retry()
        }
    }
}

/**
 * Utility class for handling zero/unavailable battery levels
 */
object BatteryDisplay {
    /**
     * Returns the display text for a battery level.
     * If battery level is 0 or null, returns "N/A".
     */
    fun getDisplayText(batteryLevel: Int?): String {
        return if (batteryLevel != null && batteryLevel > 0) {
            "$batteryLevel%"
        } else {
            "N/A"
        }
    }

    /**
     * Returns a safe battery level for UI display.
     * If battery level is 0 or null, returns 0 for proper UI rendering.
     */
    fun getSafeLevel(batteryLevel: Int?): Int {
        return batteryLevel?.takeIf { it > 0 } ?: 0
    }
}