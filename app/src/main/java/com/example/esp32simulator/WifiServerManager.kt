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

    // Змінна для відправки даних клієнту
    private var clientWriter: PrintWriter? = null

    private val PORT = 8080

    fun startServer() {
        val ip = getIpAddress()
        if (ip == null) {
            onLog("Wi-Fi: Немає IP. Перевірте підключення до мережі", LogType.ERROR)
            return
        }

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                withContext(Dispatchers.Main) {
                    onLog("Wi-Fi: Сервер запущено на $ip:$PORT", LogType.SUCCESS)
                }

                while (isActive) {
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
            // Ініціалізуємо writer і зберігаємо його в змінну класу
            clientWriter = PrintWriter(socket.getOutputStream(), true)

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { msg ->
                    withContext(Dispatchers.Main) {
                        onLog("Wi-Fi RX <-- $msg", LogType.RX)
                    }
                }
            }
        } catch (e: Exception) {
            // Клієнт відпав
        } finally {
            withContext(Dispatchers.Main) {
                onLog("Wi-Fi: Клієнт відключився", LogType.ERROR)
            }
            clientWriter = null
            try { socket.close() } catch (e: Exception) {}
        }
    }

    // Метод для відправки відповіді (викликається з ViewModel)
    fun sendResponse(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                clientWriter?.println(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun getIpAddress(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties: LinkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork) ?: return null
        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress && address.hostAddress != "127.0.0.1") {
                return address.hostAddress
            }
        }
        return null
    }
}