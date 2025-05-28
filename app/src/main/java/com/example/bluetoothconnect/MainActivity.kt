package com.example.bluetoothconnect

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.Executor
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class MainActivity : AppCompatActivity() {

    private var bluetoothManger: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var audioManager: AudioManager
    private lateinit var deviceListView: ListView
    private lateinit var pairListView: ListView
    private lateinit var connectListView: ListView
    private lateinit var tvBluetoothStatus: TextView
    private lateinit var tvPairTitle: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var deviceList: ArrayList<String>
    private lateinit var pairList: ArrayList<String>
    private lateinit var connectList: ArrayList<String>
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var pairAdapter: ArrayAdapter<String>
    private lateinit var connectAdapter: ArrayAdapter<String>
    private lateinit var btnPairDevice: Button
    private var selectedDevice: BluetoothDevice? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var pairedDevices = mutableListOf<BluetoothDevice>()
    private var connectDevices = mutableListOf<BluetoothDevice>()

    private val requestBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "蓝牙已启用", Toast.LENGTH_SHORT).show()
            checkPermissionsAndStartDiscovery()
        } else {
            Toast.makeText(this, "蓝牙未启用，功能受限", Toast.LENGTH_SHORT).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 检查是否有权限访问蓝牙设备信息
                    if (hasBluetoothPermissions()) {
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            val deviceName = try {
                                it.name ?: "未知设备"
                            } catch (e: SecurityException) {
                                "未知设备(无权限)"
                            }
                            val deviceHardwareAddress = it.address // MAC地址
                            val deviceInfo = if (it.bondState == BluetoothDevice.BOND_BONDED) {
                                "$deviceName - $deviceHardwareAddress 已配对"
                            } else {
                                "$deviceName - $deviceHardwareAddress"
                            }
                            if (!deviceList.contains(deviceInfo) && device.name != null) {
                                deviceList.add(deviceInfo)
                                discoveredDevices.add(it)
                                deviceAdapter.notifyDataSetChanged()
                            }
                        }
                    } else {
                        Toast.makeText(context, "需要蓝牙权限才能获取设备信息", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Toast.makeText(context, "设备搜索完成", Toast.LENGTH_SHORT).show()
                }

                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    Toast.makeText(context, "设备搜索完成", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private val connectionReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission", "NewApi")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    device?.let {
                        Log.d("Bluetooth", "已连接到设备: ${it.name}")
                    }
                    updateConnectList()
                    stopScan()
                    bluetoothAdapter?.cancelDiscovery()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    device?.let {
                        Log.d("Bluetooth", "设备已断开: ${it.name}")
                    }
                    updateConnectList()
                }
            }
        }
    }

    // Pin配对广播
    private val pairingPinReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
//                    abortBroadcast()//此方法重要，不能去掉
//                    val device =
//                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
//                    if (device != null) {
//                        val mPasskey = intent.getIntExtra(
//                            BluetoothDevice.EXTRA_PAIRING_KEY,
//                            BluetoothDevice.ERROR
//                        )
//                        if (hasBluetoothPermissions()) {
//                            device.setPairingConfirmation(true)//关键方法
//                        }
//                    }
                }
            }
        }
    }

    // 配对状态广播
    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission", "NewApi")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val mPass = intent.getIntExtra(
                            BluetoothDevice.EXTRA_PAIRING_KEY,
                            BluetoothDevice.ERROR
                        )
                        Log.i("mPass", "$mPass")
                        val type =
                            intent.getIntExtra(
                                BluetoothDevice.EXTRA_PAIRING_VARIANT,
                                BluetoothDevice.ERROR
                            )
                        if (type == BluetoothDevice.PAIRING_VARIANT_PIN) {
//                            abortBroadcast()
//                            val pinBytes = "$mPass".toByteArray(Charsets.UTF_8)
//                            device.setPin(pinBytes)
//                            device.setPairingConfirmation(true)
                        } else if (type == 3) {
//                            abortBroadcast() // 关键：终止系统弹窗
//                            try {
//                                device.setPairingConfirmation(true)
//                            } catch (e: Exception) {
//                                e.printStackTrace()
//                            }
                        }
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val previousState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val currentState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    device?.let {
                        when (currentState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Toast.makeText(context, "${it.name} 配对成功", Toast.LENGTH_SHORT)
                                    .show()
                                updatePairList()
                                stopScan()
                            }

                            BluetoothDevice.BOND_NONE -> {
                                if (previousState == BluetoothDevice.BOND_BONDING) {
                                    Toast.makeText(
                                        context,
                                        "${it.name} 配对失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // 获取配对失败原因（通过反射）
                                    val failureReason = try {
                                        intent.getIntExtra(
                                            "android.bluetooth.device.extra.REASON",
                                            -1
                                        )
                                    } catch (e: Exception) {
                                        -1
                                    }

                                    if (failureReason != -1) {
                                        val reasonText = when (failureReason) {
                                            1 -> "认证失败"
                                            2 -> "远程设备拒绝"
                                            3 -> "重复请求"
                                            4 -> "PIN码错误"
                                            5 -> "自动配对失败"
                                            6 -> "认证超时"
                                            else -> "未知原因 ($failureReason)"
                                        }
                                        Log.e("Bluetooth", "失败原因: $reasonText")
                                        Toast.makeText(
                                            context,
                                            "失败原因: $reasonText",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {

                                    }
                                } else {

                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    // 蓝牙状态广播
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    updateBluetoothStatus(state)
                }
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                Log.i("Bluetooth Volume", "Volume changed: $newVolume")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestBluetoothPermissions()
        val btnDiscoverDevices: Button = findViewById(R.id.btn_discover_devices)
        deviceListView = findViewById(R.id.device_list)
        pairListView = findViewById(R.id.pair_list)
        connectListView = findViewById(R.id.connect_list)
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        tvPairTitle = findViewById(R.id.tv_pair_title)
        btnEnableBluetooth = findViewById(R.id.btnEnableBluetooth)
        btnPairDevice = findViewById(R.id.btn_pair_device)
        btnPairDevice.setOnClickListener { pairSelectedDevice() }

        deviceList = ArrayList()
        pairList = ArrayList()
        connectList = ArrayList()
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        deviceListView.adapter = deviceAdapter

        pairAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pairList)
        pairListView.adapter = pairAdapter

        connectAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, connectList)
        connectListView.adapter = connectAdapter


        deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            selectedDevice = discoveredDevices[position]
            selectedDevice?.let {
                btnPairDevice.text =
                    if (it.bondState == BluetoothDevice.BOND_BONDED) "取消配对" else "配对设备"
                btnPairDevice.visibility = VISIBLE
            }
        }

        registerReceivers()
        val pairingManager = BluetoothPairManager(this)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        pairListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            pairedDevices[position].let { device ->
                pairingManager.unpairDevice(device, object : BluetoothPairManager.UnpairCallback {
                    override fun onSuccess(device: BluetoothDevice) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "已取消 ${device.name} 的配对",
                                Toast.LENGTH_SHORT
                            ).show()
                            updatePairList()
                        }
                    }

                    override fun onFailure(device: BluetoothDevice, error: String) {
                    }
                })
            }
        }

        connectListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            disconnectBleDevice(connectDevices[position].address)
        }

        bluetoothManger = ContextCompat.getSystemService(this, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManger?.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "该设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnEnableBluetooth.setOnClickListener {
            if (!bluetoothAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetooth.launch(enableBtIntent)
            } else {
                Toast.makeText(this, "蓝牙已启用", Toast.LENGTH_SHORT).show()
            }
        }

        btnDiscoverDevices.setOnClickListener {
            if (bluetoothAdapter!!.isEnabled) {
                checkPermissionsAndStartDiscovery()
            } else {
                Toast.makeText(this, "请先启用蓝牙", Toast.LENGTH_SHORT).show()
            }
        }
        updateBluetoothStatus(bluetoothAdapter?.state ?: BluetoothAdapter.ERROR)

        if (hasBluetoothPermissions()) {
            updatePairList()
            updateConnectList()
        }
    }

    private fun updateBluetoothStatus(state: Int) {
        var statusText = ""
        when (state) {
            BluetoothAdapter.STATE_ON -> {
                statusText = "蓝牙状态: 已开启"
                btnEnableBluetooth.visibility = GONE
                Log.i("Bluetooth", "STATE_ON_StartDiscovery")
//                startDiscovery()
//                scanByCDM()
            }

            BluetoothAdapter.STATE_OFF -> {
                statusText = "蓝牙状态: 已关闭"
                btnEnableBluetooth.visibility = VISIBLE
                // 清除旧设备列表
                deviceList.clear()
                discoveredDevices.clear()
                deviceAdapter.notifyDataSetChanged()
            }

            BluetoothAdapter.STATE_TURNING_ON -> {
                statusText = "蓝牙状态: 正在开启"
            }

            BluetoothAdapter.STATE_TURNING_OFF -> {
                statusText = "蓝牙状态: 正在关闭"
            }
        }

        runOnUiThread {
            tvBluetoothStatus.text = statusText
            // 根据状态更新按钮状态
            findViewById<Button>(R.id.btn_discover_devices).isEnabled =
                state == BluetoothAdapter.STATE_ON
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermissions()) {
            updatePairList()
            updateConnectList()
            // 更新当前状态
            bluetoothAdapter?.let {
                updateBluetoothStatus(it.state)
            }
        } else {
            requestBluetoothPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        safeCancelDiscovery()
    }

    @SuppressLint("NewApi")
    override fun onDestroy() {
        super.onDestroy()
        unRegisterReceivers()
        stopScan() // 停止关联请求
        // macAddress?.let { stopObservePresence(it) } // 停止观察
    }

    private fun registerReceivers() {
        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)
        // 注册蓝牙状态广播接收器
        val enableFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, enableFilter)

        val pairFilter = IntentFilter()
        pairFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        pairFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(pairingReceiver, pairFilter)

        // 注册配对请求接收器
        val pFilter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        registerReceiver(pairingPinReceiver, pFilter)

        val connectionFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(connectionReceiver, connectionFilter)


        val voiceFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeReceiver, voiceFilter); // 注册接收器

        // 在Activity/Fragment中调用

    }

    private fun unRegisterReceivers() {
        try {
            unregisterReceiver(pairingReceiver)
            unregisterReceiver(pairingPinReceiver)
            unregisterReceiver(receiver)
            unregisterReceiver(bluetoothStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    /**
     * 安全地取消蓝牙设备发现
     */
    private fun safeCancelDiscovery() {
        if (!hasBluetoothPermissions()) {
            Log.w("Bluetooth", "无蓝牙权限，无法取消发现")
            requestBluetoothPermissions()
            return
        }
        try {
            val isDiscovering = bluetoothAdapter!!.isDiscovering
            if (isDiscovering) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e("Bluetooth", "取消发现失败: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBluetoothPermissions()
            }
        }
    }

    /**
     * 检查并请求必要的权限后开始设备发现
     */
    private fun checkPermissionsAndStartDiscovery() {
        when {
            !bluetoothAdapter!!.isEnabled -> {
                Toast.makeText(this, "请先启用蓝牙", Toast.LENGTH_SHORT).show()
            }

            hasBluetoothPermissions() -> {
                Log.i("Bluetooth", "checkPermissionsAndStartDiscovery")
                // startDiscovery()
                scanByCDM()
            }

            else -> {
                requestBluetoothPermissions()
            }
        }
    }

    /**
     * 检查是否拥有蓝牙权限
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * 检查单个权限是否已授予
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求必要的蓝牙权限
     */
    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }


    /**
     * 安全地开始设备发现
     */
    private fun startDiscovery() {
        deviceList.clear()
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()

        safeCancelDiscovery()
        if (!hasBluetoothPermissions()) {
            Toast.makeText(this, "无蓝牙权限", Toast.LENGTH_SHORT).show()
            requestBluetoothPermissions()
            return
        }
        try {
            if (bluetoothAdapter!!.startDiscovery()) {
                Toast.makeText(this, "开始搜索蓝牙设备...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "搜索启动失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "搜索失败: 权限不足", Toast.LENGTH_SHORT).show()
            Log.e("Bluetooth", "开始发现失败: ${e.message}")
        }
    }

    // 配对设备
    @SuppressLint("MissingPermission")
    private fun pairSelectedDevice() {
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        selectedDevice?.let { device ->
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                try {
                    val success = device.createBond()
                    if (!success) {
                        Toast.makeText(this, "配对请求发送失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(this, "配对失败: 权限不足", Toast.LENGTH_SHORT).show()
                }
            } else if (device.bondState == BluetoothDevice.BOND_BONDED) {
                updatePairList()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updatePairList() {
        if (getPairedDevices() != null) {
            pairedDevices = getPairedDevices()!!
            pairList.clear()
            // 添加已配对设备
            pairedDevices.forEach { device ->
                pairList.add("${device.name ?: "未知设备"} - ${device.address} (已配对)")
            }
            tvPairTitle.text = "已配对列表${pairList.size}"
            pairAdapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateConnectList() {
        connectDevices = getConnectedDevices()
        connectList.clear()
        // 添加已配对设备
        connectDevices.forEach { device ->
            connectList.add("${device.name ?: "未知设备"} - ${device.address} (已连接)")
        }
        connectAdapter.notifyDataSetChanged()
    }

    /**
     * 获取已配对设备列表
     * @return
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): MutableList<BluetoothDevice>? {
        return if (hasBluetoothPermissions()) {
            bluetoothAdapter?.bondedDevices?.toMutableList()
        } else {
            mutableListOf()
        }
    }

    /**
     * 获取已连接设备列表
     * @return
     */
    @SuppressLint("MissingPermission")
    fun getConnectedDevices(): MutableList<BluetoothDevice> {
        val result: MutableSet<BluetoothDevice> = HashSet()
        val deviceSet: MutableSet<BluetoothDevice> = HashSet()

        //获取BLE的设备, profile只能是GATT或者GATT_SERVER
        val gattDevices: List<BluetoothDevice>? =
            bluetoothManger!!.getConnectedDevices(BluetoothProfile.GATT)
        if (!gattDevices.isNullOrEmpty()) {
            deviceSet.addAll(gattDevices)
        }
        //获取经典已配对的设备
        pairedDevices = getPairedDevices()!!
        if (pairedDevices.size > 0) {
            deviceSet.addAll(pairedDevices.toMutableSet())
        }
        for (dev in deviceSet) {
            val type: String = when (dev.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典"
                BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "双模"
                else -> "未知"
            }
            var connect = "设备未连接"
            if (isConnected(dev.address)) {
                result.add(dev)
                connect = "设备已连接"
            }
            Log.d(
                "zbh",
                connect + ", address = " + dev.address + "(" + type + "), name --> " + dev.name
            )
        }
        return result.toMutableList()
    }

    //TODO 根据mac地址判断是否已连接(这里参数可以直接用BluetoothDevice对象)
    private fun isConnected(macAddress: String?): Boolean {
        if (!BluetoothAdapter.checkBluetoothAddress(macAddress)) {
            return false
        }
        val device = bluetoothAdapter!!.getRemoteDevice(macAddress)
        val isConnectedMethod: Method?
        var isConnected: Boolean
        try {
            isConnectedMethod = BluetoothDevice::class.java.getDeclaredMethod("isConnected")
            isConnectedMethod.isAccessible = true
            isConnected = (isConnectedMethod.invoke(device) as? Boolean)!!
        } catch (e: NoSuchMethodException) {
            isConnected = false
        } catch (e: IllegalAccessException) {
            isConnected = false
        } catch (e: InvocationTargetException) {
            isConnected = false
        }
        return isConnected
    }


    @SuppressLint("MissingPermission")
    fun disconnectBleDevice(mac: String) {
        gattConnections[mac]?.let { gatt ->
            // 步骤1：触发断开操作
            gatt.disconnect()

            // 步骤2：延迟关闭资源（必须等待断开完成）
            Handler(Looper.getMainLooper()).postDelayed({
                gatt.close() // 关键！防止内存泄漏
                gattConnections.remove(mac)
                Log.i("Bluetooth", "已释放 $mac 资源")
            }, 300) // 延迟 300ms 确保断开完成
        }
    }


    /**
     * 调节音量
     */
    fun setBluetoothVolume(target: Int = DEFAULT_BLUETOOTH_VOLUME) {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.i("Bluetooth Volume", "Current Volume: $currentVolume, Max Volume: $maxVolume")
        val validRange = 0..maxVolume
        when {
            target !in validRange -> Log.e("Bluetooth", "Invalid volume: $target (Max: $maxVolume)")
            else -> {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    target,
                    AudioManager.FLAG_SHOW_UI
                )
                Log.i("Bluetooth", "Successfully set volume to: $target")
            }
        }
    }


    @SuppressLint("MissingPermission")
    private val cdmLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // 处理结果（不再需要 requestCode 判断）
//        if (result.resultCode == Activity.RESULT_OK) {
//            val deviceToPair: BluetoothDevice? =
//                result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
//            deviceToPair?.createBond()
//        } else {
//            Log.e("CDM", "用户取消设备选择")
//        }
    }


    private val companionDeviceManager: CompanionDeviceManager by lazy {
        getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }

    // 添加成员变量保存关联ID
    private var mAssociationId: Int? = null
    private var mObservedMac: String? = null


    // 添加停止扫描的方法
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun stopScan() {
//        mAssociationId?.let { id ->
//            companionDeviceManager.disassociate(id)
//            mAssociationId = null
//        }
//        companionDeviceManager.stopObservingDevicePresence("41:42:F2:B6:A3:D4")
    }


    // 实现 CompanionDeviceService
    @SuppressLint("NewApi")
    class MyDeviceService : CompanionDeviceService() {
        override fun onDeviceAppeared(associationInfo: AssociationInfo) {
            Log.d(
                "Presence",
                "设备进入范围: ${associationInfo.associatedDevice?.bluetoothDevice?.address}"
            )
        }

        override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
            Log.d("Presence", "设备离开范围: ")
        }
    }


    // 在类顶部添加成员变量
    private val gattConnections = mutableMapOf<String, BluetoothGatt?>()

    // 自定义Gatt回调
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("Bluetooth", "BLE已连接 ${gatt.device.address}")
                    gatt.discoverServices() // 开始发现服务
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w("Bluetooth", "BLE断开 ${gatt.device.address}")
                    gatt.close()
                    gattConnections.remove(gatt.device.address)
                }
            }
        }
    }


    @SuppressLint("NewApi")
    private fun scanByCDM() {

        val uuid = ParcelUuid(UUID.fromString("00002299-0000-1000-8000-00805f9b34fb"))
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("AMOI"))
//            .addServiceUuid(ParcelUuid(UUID(0x123abcL, -1L)), null)
            .build()

        val bleFilter: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setServiceUuid(uuid).build())
            .build()

        val pairingRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .addDeviceFilter(bleFilter)
//            .setSingleDevice(true)
            .build()

        companionDeviceManager.associate(
            pairingRequest,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    cdmLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    Log.i("Bluetooth", "Successfully set volume to:")
                }

                @SuppressLint("MissingPermission", "NewApi")
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    mAssociationId = associationInfo.id
                    val device =
                        if (associationInfo.associatedDevice?.bluetoothDevice != null) associationInfo.associatedDevice?.bluetoothDevice
                        else associationInfo.associatedDevice?.bleDevice?.device

                    device?.let { btDevice ->
                        // 1. 先处理传统蓝牙配对（如果必要）
                        if (btDevice.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                            if (btDevice.bondState == BluetoothDevice.BOND_NONE) {
                                btDevice.createBond()
                            }
                        }

                        // 2. 启动设备存在监听
                        mObservedMac = associationInfo.deviceMacAddress.toString()
                        mObservedMac?.let { mac ->
                            companionDeviceManager.startObservingDevicePresence(mac)
                            Log.i("Bluetooth", "开始监听设备存在: $mac")
                        }

                        // 3. 启动BLE连接（关键新增部分）
                        // 使用 TRANSPORT_LE 明确指定BLE传输
                        if (btDevice.type == BluetoothDevice.DEVICE_TYPE_LE) {
                            val gatt = btDevice.connectGatt(
                                this@MainActivity,
                                false,
                                gattCallback,
                                BluetoothDevice.TRANSPORT_LE
                            )
                            gattConnections[btDevice.address] = gatt
                        }
                    }
                }

                override fun onFailure(error: CharSequence?) {
                }
            },
            null
        )

    }


    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
        private const val DEFAULT_BLUETOOTH_VOLUME = 5
    }
}