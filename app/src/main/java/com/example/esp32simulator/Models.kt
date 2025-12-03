package com.example.esp32simulator

// Тип повідомлення для логу
enum class LogType {
    INFO,    // Звичайна інформація
    RX,      // Отримані дані (Receive)
    TX,      // Відправлені дані (Transmit)
    SUCCESS, // Успішний старт/підключення
    ERROR    // Помилки
}

// Модель одного рядка в консолі
data class LogMessage(
    val time: String,
    val message: String,
    val type: LogType
)

// --- ДОДАЙТЕ ЦЕЙ КЛАС ТУДИ Ж ---
// Дані для Акселерометра та Гіроскопа
data class Sensor3DData(val x: Float, val y: Float, val z: Float)


data class GpsData(
    val latitude: Double = 0.0,   // Широта
    val longitude: Double = 0.0,  // Довгота
    val altitude: Double = 0.0,   // Висота
    val speed: Float = 0.0f       // Швидкість
)