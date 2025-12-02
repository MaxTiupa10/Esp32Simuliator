package com.example.esp32simulator

import java.util.UUID

object Constants {
    // UUID сервісу Nordic UART (стандарт де-факто для емуляції Serial через BLE)
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    // Характеристика для запису (телефон-клієнт пише сюди -> ми отримуємо дані)
    val RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    // Характеристика для читання/повідомлення (ми пишемо сюди -> клієнт отримує дані)
    val TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    // Стандартний дескриптор для ввімкнення нотифікацій
    val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}