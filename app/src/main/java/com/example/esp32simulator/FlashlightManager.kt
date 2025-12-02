package com.example.esp32simulator

import android.content.Context
import android.hardware.camera2.CameraManager

class FlashlightManager(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    init {
        try {
            // Шукаємо ID задньої камери (зазвичай це "0"), яка має спалах
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                // Нам потрібна задня камера зі спалахом
                facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK && hasFlash
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Функція вмикання/вимикання
    fun setFlash(state: Boolean) {
        try {
            cameraId?.let { id ->
                // true = увімкнути, false = вимкнути
                cameraManager.setTorchMode(id, state)
            }
        } catch (e: Exception) {
            // Може виникнути, якщо камера зайнята іншим додатком
            e.printStackTrace()
        }
    }
}