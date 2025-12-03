package com.example.esp32simulator

import android.app.Application
import android.hardware.Sensor
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimulatorViewModel(application: Application) : AndroidViewModel(application) {

    // --- Стан даних ---
    private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _isBleRunning = MutableStateFlow(false)
    val isBleRunning = _isBleRunning.asStateFlow()

    private val _isWifiRunning = MutableStateFlow(false)
    val isWifiRunning = _isWifiRunning.asStateFlow()

    private val _wifiIpAddress = MutableStateFlow<String?>(null)
    val wifiIpAddress = _wifiIpAddress.asStateFlow()

    private val _lightLevel = MutableStateFlow(0f)
    val lightLevel = _lightLevel.asStateFlow()

    private val _isLedOn = MutableStateFlow(false)
    val isLedOn = _isLedOn.asStateFlow()

    private val _rgbColor = MutableStateFlow(Color.Black)
    val rgbColor = _rgbColor.asStateFlow()

    private val _accelData = MutableStateFlow(Sensor3DData(0f, 0f, 0f))
    val accelData = _accelData.asStateFlow()

    private val _gyroData = MutableStateFlow(Sensor3DData(0f, 0f, 0f))
    val gyroData = _gyroData.asStateFlow()

    private val _temperature = MutableStateFlow(24.0f)
    val temperature = _temperature.asStateFlow()

    // --- GPS Дані (Свої та Клієнта) ---
    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData = _gpsData.asStateFlow()

    private val _clientGpsData = MutableStateFlow<GpsData?>(null)
    val clientGpsData = _clientGpsData.asStateFlow()


    // --- Менеджери ---
    private val flashlightManager = FlashlightManager(application.applicationContext)
    private val sensorManager = HardwareSensorManager(application.applicationContext)
    private val gpsManager = GpsManager(application.applicationContext) // <-- Додано GPS Manager

    // BLE Manager
    private val bleManager = BleServerManager(
        context = application.applicationContext,
        onLog = { msg, type -> addLog("[BLE] $msg", type) },
        onCommandReceived = { cmd -> processCommand(cmd) }
    )

    // Wi-Fi Manager
    private val wifiManager = WifiServerManager(application.applicationContext) { msg, type ->
        addLog(msg, type)
        // Фільтруємо логи, передаємо тільки RX
        if (type == LogType.RX) {
            val cleanMsg = msg.replace("Wi-Fi RX <--", "").trim()
            processCommand(cleanMsg)
        }
    }

    init {
        // Збір даних з усіх датчиків
        viewModelScope.launch {
            launch { sensorManager.getLightSensorData().collect { _lightLevel.value = it } }
            launch { sensorManager.get3DSensorData(Sensor.TYPE_ACCELEROMETER).collect { _accelData.value = it } }
            launch { sensorManager.get3DSensorData(Sensor.TYPE_GYROSCOPE).collect { _gyroData.value = it } }
            // Запуск збору GPS
            launch { gpsManager.getGpsData().collect { _gpsData.value = it } }
        }
    }

    // --- ОБРОБКА КОМАНД ---
    private fun processCommand(command: String) {
        val cleanCmd = command.trim().uppercase()

        if (cleanCmd.isEmpty()) return

        when {
            // ЛІХТАРИК
            cleanCmd.contains("LED_ON") -> {
                _isLedOn.value = true
                addLog("✅ Flashlight ON", LogType.SUCCESS)
                try { flashlightManager.setFlash(true) } catch (e: Exception) {}
            }
            cleanCmd.contains("LED_OFF") -> {
                _isLedOn.value = false
                addLog("✅ Flashlight OFF", LogType.SUCCESS)
                try { flashlightManager.setFlash(false) } catch (e: Exception) {}
            }

            // RGB (Універсальний парсер)
            // Реагує на "RGB" або наявність двокрапки, якщо це не GPS координати
            (cleanCmd.contains("RGB") || cleanCmd.contains(":")) && !cleanCmd.contains("GPS") -> {
                try {
                    val numbersOnly = cleanCmd
                        .replace(Regex("[^0-9 ]"), " ") // Залишаємо тільки цифри
                        .trim()

                    val parts = numbersOnly.split(Regex("\\s+"))
                        .mapNotNull { it.toIntOrNull() }

                    if (parts.size >= 3) {
                        val r = parts[0].coerceIn(0, 255)
                        val g = parts[1].coerceIn(0, 255)
                        val b = parts[2].coerceIn(0, 255)

                        _rgbColor.value = Color(r / 255f, g / 255f, b / 255f)
                        addLog("🎨 OK: $r, $g, $b", LogType.SUCCESS)
                    }
                } catch (e: Exception) {
                    addLog("RGB Error: ${e.message}", LogType.ERROR)
                }
            }

            // ПРИЙОМ GPS ВІД КЛІЄНТА
            cleanCmd.contains("CLIENT_GPS") -> {
                try {
                    val numbersOnly = cleanCmd.replace("CLIENT_GPS", "").replace(":", " ").replace(",", " ").trim()
                    val parts = numbersOnly.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }

                    if (parts.size >= 2) {
                        val lat = parts[0]
                        val lon = parts[1]
                        _clientGpsData.value = GpsData(latitude = lat, longitude = lon)
                        addLog("📍 Client at: $lat, $lon", LogType.SUCCESS)
                    }
                } catch (e: Exception) {
                    addLog("Client GPS Parse Error", LogType.ERROR)
                }
            }

            // ВІДПРАВКА ДАНИХ (GET_SENSORS або GET_GPS)
            cleanCmd.contains("GET_GPS") -> sendGpsData() // Спеціальна команда для координат
            cleanCmd.contains("GET") || cleanCmd.contains("SENSOR") -> sendSensorData()

            else -> {
                addLog("❓ UNKNOWN: '$cleanCmd'", LogType.INFO)
            }
        }
    }

    // --- ФУНКЦІЇ КЕРУВАННЯ ---

    fun toggleBle() {
        if (_isBleRunning.value) {
            bleManager.stopServer()
            _isBleRunning.value = false
        } else {
            if (bleManager.startServer()) _isBleRunning.value = true
        }
    }

    fun toggleWifi() {
        if (_isWifiRunning.value) {
            wifiManager.stopServer()
            _isWifiRunning.value = false
            _wifiIpAddress.value = null
        } else {
            val ip = wifiManager.getIpAddress()
            if (ip != null) {
                wifiManager.startServer()
                _isWifiRunning.value = true
                _wifiIpAddress.value = ip
            } else {
                addLog("Error: No Wi-Fi", LogType.ERROR)
            }
        }
    }

    fun updateTemperature(newTemp: Float) {
        _temperature.value = newTemp
    }

    // --- ВІДПРАВКА ДАНИХ КЛІЄНТУ ---

    // Звичайна телеметрія
    private fun sendSensorData() {
        val acc = _accelData.value
        val gyro = _gyroData.value
        val temp = _temperature.value
        val lux = _lightLevel.value.toInt()

        val response = """
            {
              "temp": %.1f,
              "light": %d,
              "acc": [%.2f, %.2f, %.2f],
              "gyro": [%.2f, %.2f, %.2f]
            }
        """.trimIndent().format(Locale.US, temp, lux, acc.x, acc.y, acc.z, gyro.x, gyro.y, gyro.z)

        sendResponseToClients(response)
        addLog("TX --> SENSORS JSON", LogType.TX)
    }

    // Відправка тільки координат
    private fun sendGpsData() {
        val gps = _gpsData.value
        val response = """
            {
              "lat": %.6f,
              "lon": %.6f,
              "alt": %.1f,
              "speed": %.1f
            }
        """.trimIndent().format(Locale.US, gps.latitude, gps.longitude, gps.altitude, gps.speed)

        sendResponseToClients(response)
        addLog("TX --> GPS JSON", LogType.TX)
    }

    // Універсальна функція для відправки по всіх каналах
    private fun sendResponseToClients(response: String) {
        if (_isWifiRunning.value) wifiManager.sendResponse(response)
        if (_isBleRunning.value) bleManager.sendData(response)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun manualCommand(cmd: String) {
        processCommand(cmd)
    }

    private fun addLog(text: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = LogMessage(time, text, type)
        _logs.value = listOf(newLog) + _logs.value
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopServer()
        wifiManager.stopServer()
        flashlightManager.setFlash(false)
    }
}