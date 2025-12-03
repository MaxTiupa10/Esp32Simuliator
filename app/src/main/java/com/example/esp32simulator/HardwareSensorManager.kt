package com.example.esp32simulator

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow



class HardwareSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Існуючий метод для світла
    fun getLightSensorData(): Flow<Float> {
        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.values?.firstOrNull()?.let { trySend(it) }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (sensor != null) sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            else trySend(0f)
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }

    // НОВИЙ метод для 3D датчиків (Акселерометр та Гіроскоп)
    fun get3DSensorData(sensorType: Int): Flow<Sensor3DData> {
        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null && event.values.size >= 3) {
                        // Використовуємо Sensor3DData з файлу Models.kt
                        trySend(Sensor3DData(event.values[0], event.values[1], event.values[2]))
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (sensor != null) {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }

            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }
}