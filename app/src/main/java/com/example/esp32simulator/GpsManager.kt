package com.example.esp32simulator

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GpsManager(context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Ми ставимо SuppressLint, бо дозволи перевіряються в MainActivity перед запуском
    @SuppressLint("MissingPermission")
    fun getGpsData(): Flow<GpsData> {
        return callbackFlow {
            // Створюємо слухача подій GPS
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    // Коли телефон отримує нові координати -> відправляємо їх у Flow
                    trySend(
                        GpsData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude,
                            speed = location.speed
                        )
                    )
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Перевіряємо, які провайдери доступні (GPS точніший, Network швидший)
            val providers = locationManager.getProviders(true)
            var isRegistered = false

            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                // Оновлення кожну 1 секунду (1000мс) або кожен 1 метр
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
                isRegistered = true
            } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, listener)
                isRegistered = true
            }

            if (!isRegistered) {
                // Якщо нічого не увімкнено, відправляємо нульові дані, щоб UI не завис
                trySend(GpsData())
            }

            // Цей блок спрацює, коли ми закриємо екран або зупинимо програму
            awaitClose {
                if (isRegistered) {
                    locationManager.removeUpdates(listener)
                }
            }
        }
    }
}