package com.example.esp32simulator

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import java.nio.charset.StandardCharsets
import java.util.UUID

@SuppressLint("MissingPermission")
class BleServerManager(
    private val context: Context,
    private val onLog: (String, LogType) -> Unit,
    private val onCommandReceived: (String) -> Unit
) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    private var bluetoothGattServer: BluetoothGattServer? = null
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    // === GATT CALLBACKS ===
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                registeredDevices.add(device)
                onLog("BLE: Клієнт підключився", LogType.SUCCESS)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
                onLog("BLE: Клієнт відключився", LogType.ERROR)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == Constants.RX_CHAR_UUID) {
                val message = String(value, StandardCharsets.UTF_8)

                // 1. Показуємо користувачеві, що прийшло
                onLog("RX <-- $message", LogType.RX)

                // 2. Передаємо на виконання (без зайвих текстів)
                onCommandReceived(message)

                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        // Інші обов'язкові методи
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == Constants.TX_CHAR_UUID) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "OK".toByteArray())
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            if (responseNeeded) bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    // === ADVERTISE CALLBACK ===
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            onLog("BLE Advertising Started", LogType.SUCCESS)
        }
        override fun onStartFailure(errorCode: Int) {
            onLog("BLE Advertise Failed: $errorCode", LogType.ERROR)
        }
    }

    // === METHODS ===
    fun startServer(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onLog("Bluetooth is OFF", LogType.ERROR)
            return false
        }
        val advertiser = bluetoothAdapter!!.bluetoothLeAdvertiser ?: return false

        // Config GATT
        val service = BluetoothGattService(Constants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val rxChar = BluetoothGattCharacteristic(Constants.RX_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE)
        val txChar = BluetoothGattCharacteristic(Constants.TX_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ)
        txChar.addDescriptor(BluetoothGattDescriptor(Constants.CLIENT_CONFIG_DESCRIPTOR, BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ))

        service.addCharacteristic(rxChar)
        service.addCharacteristic(txChar)

        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        bluetoothGattServer?.addService(service)

        // Config Advertising
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(Constants.SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()

        advertiser.stopAdvertising(advertiseCallback)
        advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        return true
    }

    fun sendData(message: String) {
        val gattServer = bluetoothGattServer ?: return

        // 1. Знаходимо нашу TX характеристику
        val service = gattServer.getService(Constants.SERVICE_UUID) ?: return
        val txChar = service.getCharacteristic(Constants.TX_CHAR_UUID) ?: return

        // 2. Записуємо в неї дані
        txChar.setValue(message.toByteArray(StandardCharsets.UTF_8))

        // 3. Відправляємо повідомлення всім підключеним пристроям
        for (device in registeredDevices) {
            try {
                // true = confirm (надійно), false = без підтвердження (швидко)
                gattServer.notifyCharacteristicChanged(device, txChar, false)
            } catch (e: Exception) {
                onLog("BLE Send Error: ${e.message}", LogType.ERROR)
            }
        }
    }
    fun stopServer() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        onLog("BLE Stopped", LogType.INFO)
    }
}