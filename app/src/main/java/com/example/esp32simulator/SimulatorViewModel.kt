package com.example.esp32simulator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimulatorViewModel(application: Application) : AndroidViewModel(application) {

    // --- Стан даних (StateFlow) для UI ---

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

    // --- Менеджери ---

    private val flashlightManager = FlashlightManager(application.applicationContext)
    private val sensorManager = HardwareSensorManager(application.applicationContext)

    private val bleManager = BleServerManager(application.applicationContext) { msg, type ->
        addLog("[BLE] $msg", type)
        processCommand(msg)
    }

    private val wifiManager = WifiServerManager(application.applicationContext) { msg, type ->
        addLog("[Wi-Fi] $msg", type)
        processCommand(msg)
    }

    init {
        viewModelScope.launch {
            sensorManager.getLightSensorData().collect { lux ->
                _lightLevel.value = lux
            }
        }
    }

    // --- ГОЛОВНЕ ВИПРАВЛЕННЯ ТУТ ---
    private fun processCommand(command: String) {
        // Переводимо в верхній регістр, щоб не залежати від led_on чи LED_ON
        val rawCommand = command.uppercase()

        // Використовуємо contains(), щоб ігнорувати пробіли та Enter (\n)
        if (rawCommand.contains("LED_ON")) {
            _isLedOn.value = true
            addLog("✅ ACTION: Flashlight ON", LogType.SUCCESS)
            try {
                flashlightManager.setFlash(true)
            } catch (e: Exception) {
                addLog("Error: Camera permission?", LogType.ERROR)
            }
        }
        else if (rawCommand.contains("LED_OFF")) {
            _isLedOn.value = false
            addLog("✅ ACTION: Flashlight OFF", LogType.SUCCESS)
            try {
                flashlightManager.setFlash(false)
            } catch (e: Exception) {
                addLog("Error: Camera busy", LogType.ERROR)
            }
        }
    }
    // -------------------------------

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
                addLog("Error: No Wi-Fi Connection", LogType.ERROR)
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // Публічна функція для тестування з кнопок UI
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