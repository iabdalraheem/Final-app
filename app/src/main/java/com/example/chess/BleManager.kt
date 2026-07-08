package com.example.chess

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class BleManager(private val context: Context) {
    private val TAG = "BleManager"

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _receivedMessage = MutableStateFlow<String>("")
    val receivedMessage: StateFlow<String> = _receivedMessage

    private val _autoConnectEnabled = MutableStateFlow(true)
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled

    fun setAutoConnectEnabled(enabled: Boolean) {
        _autoConnectEnabled.value = enabled
    }

    // Nordic UART Service (NUS) UUIDs
    private val RX_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    var onMessageReceived: ((String) -> Unit)? = null

    // Callback when scan results are found
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name
            if (name != null) {
                val currentList = _scannedDevices.value
                if (!currentList.any { it.address == device.address }) {
                    _scannedDevices.value = currentList + device
                    
                    // Auto-connect to SmartChessboard if enabled
                    if (name == "SmartChessboard" && _autoConnectEnabled.value) {
                        Log.i(TAG, "SmartChessboard found! Initiating auto-connection...")
                        connect(device)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        if (_isScanning.value) return

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        // Stop scanning after 10 seconds to save power
        handler.postDelayed({
            stopScanning()
        }, 10000)

        try {
            adapter.bluetoothLeScanner?.startScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!_isScanning.value) return
        _isScanning.value = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScanning()
        _connectionState.value = BleConnectionState.CONNECTING
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")
                _connectionState.value = BleConnectionState.CONNECTED
                // Discover services
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                _connectionState.value = BleConnectionState.DISCONNECTED
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully.")
                enableNotifications(gatt)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value
            val stringValue = String(value).trim()
            Log.i(TAG, "Notification received: $stringValue")
            _receivedMessage.value = stringValue
            handler.post {
                onMessageReceived?.invoke(stringValue)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val stringValue = String(value).trim()
            Log.i(TAG, "Notification received: $stringValue")
            _receivedMessage.value = stringValue
            handler.post {
                onMessageReceived?.invoke(stringValue)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        // Try to enable notifications for Nordic UART Service (NUS) TX characteristic first
        val nusService = gatt.getService(RX_SERVICE_UUID)
        if (nusService != null) {
            val txChar = nusService.getCharacteristic(TX_CHAR_UUID)
            if (txChar != null) {
                gatt.setCharacteristicNotification(txChar, true)
                val descriptor = txChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                if (descriptor != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.i(TAG, "Enabled notifications for NUS TX characteristic")
                    return
                }
            }
        }

        // Fallback: search all services and characteristics to find any with NOTIFY property
        Log.i(TAG, "NUS service not found, scanning all services for characteristics with NOTIFY...")
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                val properties = characteristic.properties
                if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                    if (descriptor != null) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                        Log.i(TAG, "Enabled notifications for characteristic: ${characteristic.uuid}")
                        return
                    }
                }
            }
        }
    }
}
