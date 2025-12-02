package com.example.esp32simulator

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import java.nio.charset.StandardCharsets
import java.util.UUID

@SuppressLint("MissingPermission")
class BleServerManager(
    private val context: Context,
    private val onLog: (String, LogType) -> Unit
) {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }

    private var bluetoothGattServer: BluetoothGattServer? = null
    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    // === CALLBACKS ===

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                registeredDevices.add(device)
                onLog("Клієнт підключився: ${device.address}", LogType.SUCCESS)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
                onLog("Клієнт відключився", LogType.ERROR)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == Constants.RX_CHAR_UUID) {
                val message = String(value, StandardCharsets.UTF_8)
                onLog("RX <-- $message", LogType.RX)

                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == Constants.TX_CHAR_UUID) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "OK".toByteArray())
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            onLog("BLE Сервер запущено. Дані розділено (UUID / Ім'я)", LogType.SUCCESS)
        }

        override fun onStartFailure(errorCode: Int) {
            val errorMsg = when(errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Дані завеликі (спробуйте скоротити ім'я)"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Не підтримується"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Внутрішня помилка"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Забагато реклами в ефірі"
                else -> "Код помилки $errorCode"
            }
            onLog("Помилка реклами: $errorMsg", LogType.ERROR)
        }
    }

    // === PUBLIC FUNCTIONS ===

    fun startServer(): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onLog("Bluetooth вимкнено!", LogType.ERROR)
            return false
        }

        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onLog("Помилка: Цей телефон не підтримує BLE Peripheral Mode", LogType.ERROR)
            return false
        }

        // --- НАЛАШТУВАННЯ GATT ---
        val service = BluetoothGattService(Constants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val rxChar = BluetoothGattCharacteristic(
            Constants.RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val txChar = BluetoothGattCharacteristic(
            Constants.TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Додаємо дескриптор для повідомлень (Notifications)
        txChar.addDescriptor(BluetoothGattDescriptor(
            Constants.CLIENT_CONFIG_DESCRIPTOR,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        ))

        service.addCharacteristic(rxChar)
        service.addCharacteristic(txChar)

        bluetoothGattServer?.close()
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        bluetoothGattServer?.addService(service)

        // --- ВИПРАВЛЕНА ЧАСТИНА: РЕКЛАМА ---

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Пакет 1: Основний (Advertise Data)
        // Тут ми відправляємо ТІЛЬКИ UUID, щоб влізти в ліміт 31 байт
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // ВАЖЛИВО: Вимикаємо ім'я тут
            .addServiceUuid(ParcelUuid(Constants.SERVICE_UUID))
            .build()

        // Пакет 2: Відповідь на сканування (Scan Response)
        // Ім'я відправляємо тут. Цей пакет надсилається, коли телефон хтось знаходить.
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Вмикаємо ім'я тут
            .build()

        advertiser.stopAdvertising(advertiseCallback)

        // Використовуємо метод з 4 аргументами: settings, data, scanResponse, callback
        advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)

        return true
    }

    fun stopServer() {
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        advertiser?.stopAdvertising(advertiseCallback)

        bluetoothGattServer?.close()
        bluetoothGattServer = null
        onLog("Сервер зупинено", LogType.INFO)
    }
}