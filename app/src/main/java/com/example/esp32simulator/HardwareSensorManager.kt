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

    // Потік даних з датчика освітлення (LDR)
    fun getLightSensorData(): Flow<Float> {
        return callbackFlow {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    // event.values[0] - це рівень освітлення в люксах
                    event?.values?.firstOrNull()?.let { lux ->
                        trySend(lux)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (sensor != null) {
                // SENSOR_DELAY_UI підходить для оновлення інтерфейсу
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                trySend(-1f) // -1 означає, що датчика немає
            }

            // Коли потік закривається (перестаємо слухати), відключаємо датчик
            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
    }
}