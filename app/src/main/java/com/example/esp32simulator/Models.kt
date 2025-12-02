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