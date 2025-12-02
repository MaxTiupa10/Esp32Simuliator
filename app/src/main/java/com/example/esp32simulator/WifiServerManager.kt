package com.example.esp32simulator

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket

class WifiServerManager(
    private val context: Context,
    private val onLog: (String, LogType) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    // Порт, на якому ми слухаємо підключення (стандартний для простих тестів)
    private val PORT = 8080

    fun startServer() {
        val ip = getIpAddress()
        if (ip == null) {
            onLog("Wi-Fi: Немає IP. Перевірте підключення до мережі", LogType.ERROR)
            return
        }

        // Запускаємо сервер в окремому потоці (IO context), щоб не блокувати UI
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                withContext(Dispatchers.Main) {
                    onLog("Wi-Fi: Сервер запущено на $ip:$PORT", LogType.SUCCESS)
                }

                while (isActive) {
                    // Чекаємо на підключення клієнта (це блокуюча операція)
                    val socket = serverSocket?.accept()
                    socket?.let { handleClient(it) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onLog("Wi-Fi Помилка: ${e.message}", LogType.ERROR)
                    }
                }
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.Main) {
            onLog("Wi-Fi: Новий клієнт підключився", LogType.SUCCESS)
        }

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            var line: String?
            // Читаємо рядки, поки з'єднання активне
            while (reader.readLine().also { line = it } != null) {
                line?.let { msg ->
                    withContext(Dispatchers.Main) {
                        onLog("Wi-Fi RX <-- $msg", LogType.RX)

                        // Відправляємо емульовану відповідь (Echo)
                        // Тут можна додати логіку: if (msg == "STATUS") writer.println("OK")
                        CoroutineScope(Dispatchers.IO).launch {
                            writer.println("ESP32-Sim Received: $msg")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Клієнт розірвав з'єднання або помилка мережі
        } finally {
            withContext(Dispatchers.Main) {
                onLog("Wi-Fi: Клієнт відключився", LogType.ERROR)
            }
            try { socket.close() } catch (e: Exception) {}
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onLog("Wi-Fi: Сервер зупинено", LogType.INFO)
    }

    // Допоміжна функція для отримання локальної IP-адреси телефону
    fun getIpAddress(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork) ?: return null

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            // Шукаємо IPv4 адресу, яка не є локальною (127.0.0.1)
            if (address is Inet4Address && !address.isLoopbackAddress && address.hostAddress != "127.0.0.1") {
                return address.hostAddress
            }
        }
        return null
    }
}