package com.example.bluetoothconnect

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class WatchService : Service() {

    private val TAG = "WatchService"
    private val NOTIFICATION_CHANNEL_ID = "WatchServiceChannel"
    private val NOTIFICATION_ID = 101 // Unique ID for your foreground notification

    private var connectionJob: Job? = null
    private var connectedDevice: BluetoothDevice? = null

    // --- Service Lifecycle Callbacks ---

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel() // Create channel for foreground notification
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        when (val action = intent?.action) {
            ACTION_CONNECT -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(EXTRA_BLUETOOTH_DEVICE)
                device?.let {
                    Log.d(TAG, "Received request to connect to device: ${it.address}")
                    connectedDevice = it
                    startForegroundService(it) // Start as foreground service
                    startConnectionManagement(it) // Start actual connection logic
                } ?: run {
                    Log.e(TAG, "No BluetoothDevice extra received with ACTION_CONNECT")
                    stopSelf() // Stop if no device is provided
                }
            }
            ACTION_DISCONNECT -> {
                Log.d(TAG, "Received request to disconnect")
                stopConnectionManagement()
                stopSelf() // Stop the service
            }
            else -> {
                Log.d(TAG, "Service started with unhandled action: $action")
                // If service is restarted by system after being killed, it will re-enter here.
                // You might want to re-establish connection if connectedDevice is not null.
                if (connectedDevice != null && connectionJob == null) {
                    Log.d(TAG, "Service restarted by system, attempting to re-establish connection.")
                    startConnectionManagement(connectedDevice!!)
                } else if (connectedDevice == null) {
                    Log.d(TAG, "Service restarted by system without device, stopping.")
                    stopSelf()
                }
            }
        }

        // START_STICKY: If the system kills the service, it recreates it but doesn't redeliver the last intent.
        // Good for long-running background processes.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Service onBind")
        return null // This service doesn't provide binding
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopConnectionManagement() // Clean up connection when service is destroyed
    }

    // --- Foreground Service Management ---

    @SuppressLint("MissingPermission", "NewApi") // Permissions are handled in the calling Activity
    private fun startForegroundService(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android O and above require notification channel
            val notification = createNotification(device.name ?: device.address)
            // For Android 14 (API 34) and above, you must have the FOREGROUND_SERVICE_CONNECTED_DEVICE permission
            // This is checked by the system when startForeground is called.
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            Log.d(TAG, "Started foreground service for device: ${device.address}")
        } else {
            // For older Android versions, startForeground without type
            val notification = createNotification(device.name ?: device.address)
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Started foreground service for device (old API): ${device.address}")
        }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Watch Service Channel",
                NotificationManager.IMPORTANCE_LOW // Use IMPORTANCE_LOW for less intrusive ongoing notifications
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(deviceName: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java) // Replace with your main activity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Device Connected: $deviceName")
            .setContentText("Maintaining connection to your companion device.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible
            .build()
    }

    // --- Connection Management (Placeholder Logic) ---

    @SuppressLint("MissingPermission") // Permissions handled in calling Activity
    private fun startConnectionManagement(device: BluetoothDevice) {
        connectionJob?.cancel() // Cancel any previous connection job
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting connection job for ${device.address}")
            // TODO: Implement your actual Bluetooth Classic or BLE connection logic here
            // This could involve:
            // - Creating a BluetoothSocket (Classic) or BluetoothGatt (BLE)
            // - Calling connect()
            // - Handling connection success/failure, data transfer
            // - Reconnection logic if connection drops

            try {
                // Simulate connection process
                Log.d(TAG, "Simulating connection to ${device.address}...")
                delay(5000) // Simulate connection delay
                Log.d(TAG, "Simulated connection established to ${device.address}.")

                // Simulate keeping connection alive and reading data
                while (isActive) { // Loop while coroutine is active (service is running)
                    // TODO: Implement reading/writing data
                    Log.d(TAG, "Maintaining connection and processing data for ${device.address}...")
                    delay(10000) // Simulate data processing interval
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection job failed or cancelled: ${e.message}")
            } finally {
                // TODO: Implement actual disconnection/cleanup
                Log.d(TAG, "Simulating disconnection from ${device.address}.")
            }
        }
    }

    private fun stopConnectionManagement() {
        connectionJob?.cancel() // Stop the coroutine job
        connectionJob = null
        Log.d(TAG, "Connection job stopped.")
        stopForeground(STOP_FOREGROUND_REMOVE) // Remove notification and stop service
    }

    // --- Companion Object for Service Actions ---

    companion object {
        const val ACTION_CONNECT = "com.example.yourapp.ACTION_CONNECT" // Adjust to your package
        const val ACTION_DISCONNECT = "com.example.yourapp.ACTION_DISCONNECT"
        const val EXTRA_BLUETOOTH_DEVICE = "bluetooth_device"

        /**
         * Starts the WatchService to connect and maintain connection with a Bluetooth device.
         * Call this from your Activity or other components.
         */
        fun startService(context: Context, device: BluetoothDevice) {
            val intent = Intent(context, WatchService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_BLUETOOTH_DEVICE, device)
            }
            // For Android O and above, use startForegroundService.
            // For older versions, context.startService() is sufficient.
            // Android 12+ requires BLUETOOTH_CONNECT permission here.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the WatchService and disconnects from the device.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, WatchService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent) // Or context.stopService(intent) directly
        }
    }
}
