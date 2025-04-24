package com.example.bluetoothconnect

import android.Manifest
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
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private var bluetoothManger: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var deviceListView: ListView
    private lateinit var pairListView: ListView
    private lateinit var tvBluetoothStatus: TextView
    private lateinit var tvPairTitle: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var deviceList: ArrayList<String>
    private lateinit var pairList: ArrayList<String>
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var pairAdapter: ArrayAdapter<String>
    private lateinit var btnPairDevice: Button
    private var selectedDevice: BluetoothDevice? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var pairedDevices = mutableListOf<BluetoothDevice>()

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


    // Pin配对广播
    private val pairingPinReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
//                    handlePairingRequest(intent)
                }
            }
        }
    }

    // 配对状态广播接收器
    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
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
                                // 配对成功后取消注册PIN接收器
                                try {
                                    unregisterReceiver(pairingPinReceiver)
                                } catch (e: IllegalArgumentException) {
                                    // 接收器未注册
                                }
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

    private fun updateBluetoothStatus(state: Int) {
        var statusText = ""
        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                statusText = "蓝牙状态: 已关闭"
                btnEnableBluetooth.visibility = VISIBLE
                // 清除旧设备列表
                deviceList.clear()
                discoveredDevices.clear()
                deviceAdapter.notifyDataSetChanged()
            }

            BluetoothAdapter.STATE_ON -> {
                statusText = "蓝牙状态: 已开启"
                btnEnableBluetooth.visibility = GONE
                Log.i("Bluetooth", "STATE_ON_StartDiscovery")
                startDiscovery()
            }
        }

        runOnUiThread {
            tvBluetoothStatus.text = statusText
            // 根据状态更新按钮状态
            findViewById<Button>(R.id.btn_discover_devices).isEnabled =
                state == BluetoothAdapter.STATE_ON
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestBluetoothPermissions()
        deviceListView = findViewById(R.id.device_list)
        pairListView = findViewById(R.id.pair_list)
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        tvPairTitle = findViewById(R.id.tv_pair_title)
        btnEnableBluetooth = findViewById(R.id.btnEnableBluetooth)
        btnPairDevice = findViewById(R.id.btn_pair_device)
        btnPairDevice.setOnClickListener { pairSelectedDevice() }
        val btnDiscoverDevices: Button = findViewById(R.id.btn_discover_devices)

        deviceList = ArrayList()
        pairList = ArrayList()
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        deviceListView.adapter = deviceAdapter

        pairAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pairList)
        pairListView.adapter = pairAdapter
        // 设置列表点击监听
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
                        runOnUiThread {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("操作失败")
                                .setMessage("无法取消配对: $error")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                })
            }
        }

        bluetoothManger = ContextCompat.getSystemService(this, BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManger?.adapter
        // 检查蓝牙支持
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "该设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 检查设备是否支持蓝牙
        btnEnableBluetooth.setOnClickListener {
            if (!bluetoothAdapter!!.isEnabled) {
                // 请求用户打开蓝牙
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

        // 初始状态更新
        updateBluetoothStatus(bluetoothAdapter?.state ?: BluetoothAdapter.ERROR)

        if (hasBluetoothRequiredPermissions()) {
            updatePairList()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothRequiredPermissions()) {
            updatePairList()
            // 更新当前状态
            bluetoothAdapter?.let {
                updateBluetoothStatus(it.state)
            }
        } else {
            requestBluetoothPermissions()
        }
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

        val pairFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(pairingReceiver, pairFilter)
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


    override fun onPause() {
        super.onPause()
        safeCancelDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        unRegisterReceivers()
    }

    /**
     * 安全地取消蓝牙设备发现
     */
    private fun safeCancelDiscovery() {
        if (!hasBluetoothScanPermission()) {
            Log.w("Bluetooth", "无BLUETOOTH_SCAN权限，无法取消发现")
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
            // 首先检查蓝牙是否启用
            !bluetoothAdapter!!.isEnabled -> {
                Toast.makeText(this, "请先启用蓝牙", Toast.LENGTH_SHORT).show()
            }
            // 检查是否已有全部所需权限
            hasBluetoothRequiredPermissions() -> {
                Log.i("Bluetooth", "checkPermissionsAndStartDiscovery")
                startDiscovery()
            }
            // 需要请求权限
            else -> {
                requestBluetoothPermissions()
            }
        }
    }

    /**
     * 检查是否拥有所有必需的权限
     */
    private fun hasBluetoothRequiredPermissions(): Boolean {
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
        // 检查是否有发现权限
        if (!hasBluetoothScanPermission()) {
            Toast.makeText(this, "无蓝牙扫描权限", Toast.LENGTH_SHORT).show()
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

    /**
     * 检查是否有蓝牙扫描权限
     */
    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            true
        }
    }

    /**
     * 检查是否有蓝牙连接权限
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    // 配对设备
    @SuppressLint("MissingPermission")
    private fun pairSelectedDevice() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissions()
            return
        }
        selectedDevice?.let { device ->
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                try {
                    // 注册配对请求接收器
                    registerReceiver(
                        pairingPinReceiver,
                        IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
                    )
                    // 开始配对
                    if (!device.createBond()) {
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

    // 广播接收器，用于接收发现的设备
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 检查是否有权限访问蓝牙设备信息
                    if (hasBluetoothRequiredPermissions()) {
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

    // 获取已配对设备列表
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): MutableList<BluetoothDevice>? {
        return if (hasBluetoothRequiredPermissions()) {
            bluetoothAdapter?.bondedDevices?.toMutableList()
        } else {
            mutableListOf()
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    }
}