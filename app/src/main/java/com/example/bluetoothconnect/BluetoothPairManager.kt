package com.example.bluetoothconnect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BluetoothPairManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
    }

    interface UnpairCallback {
        fun onSuccess(device: BluetoothDevice)
        fun onFailure(device: BluetoothDevice, error: String)
    }

    @SuppressLint("MissingPermission")
    fun unpairDevice(device: BluetoothDevice, callback: UnpairCallback) {
        if (!hasBluetoothPermission()) {
            callback.onFailure(device, "缺少蓝牙权限")
            return
        }

        try {
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as Boolean
            if (result) {
                // 需要监听实际配对状态变化
                registerUnpairReceiver(device, callback)
            } else {
                callback.onFailure(device, "系统调用失败")
            }
        } catch (e: NoSuchMethodException) {
            callback.onFailure(device, "不支持的Android版本")
        } catch (e: Exception) {
            callback.onFailure(device, "反射调用异常: ${e.message}")
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private var unpairReceiver: BroadcastReceiver? = null

    private fun registerUnpairReceiver(device: BluetoothDevice, callback: UnpairCallback) {
        unpairReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondDevice = intent.getParcelableExtra<BluetoothDevice>(
                            BluetoothDevice.EXTRA_DEVICE
                        )
                        if (bondDevice?.address == device.address) {
                            val state = intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothDevice.ERROR
                            )
                            when (state) {
                                BluetoothDevice.BOND_NONE -> {
                                    callback.onSuccess(device)
                                    unregisterReceiver()
                                }

                                BluetoothDevice.BOND_BONDING -> {
                                    // 正在取消配对中
                                }

                                BluetoothDevice.BOND_BONDED -> {
                                    callback.onFailure(device, "取消配对失败")
                                    unregisterReceiver()
                                }
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(unpairReceiver, filter)
    }

    private fun unregisterReceiver() {
        unpairReceiver?.let {
            context.unregisterReceiver(it)
            unpairReceiver = null
        }
    }
}