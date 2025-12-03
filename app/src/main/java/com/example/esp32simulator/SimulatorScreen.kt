package com.example.esp32simulator

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MainScreen(viewModel: SimulatorViewModel = viewModel()) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { padding ->
        NavHost(navController, startDestination = "connection", modifier = Modifier.padding(padding)) {
            composable("connection") { ConnectionScreen(viewModel) }
            composable("simulation") { SimulationScreen(viewModel) }
            composable("map") { MapScreen(viewModel) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem("connection", "Зв'язок", Icons.Default.Home),
        BottomNavItem("simulation", "Симуляція", Icons.Default.Build),
        BottomNavItem("map", "Карта", Icons.Default.Place)
    )
    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) { launchSingleTop = true }
                    }
                }
            )
        }
    }
}

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: SimulatorViewModel) {
    val logs by viewModel.logs.collectAsState()
    val isBleRunning by viewModel.isBleRunning.collectAsState()
    val isWifiRunning by viewModel.isWifiRunning.collectAsState()
    val wifiIp by viewModel.wifiIpAddress.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("ESP32 Підключення") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
        Column(modifier = Modifier.padding(16.dp)) {
            StatusCard("Bluetooth LE", isBleRunning, if (isBleRunning) "Active" else "Off", { viewModel.toggleBle() }, Color(0xFFE3F2FD), Color(0xFFFFEBEE))
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard("Wi-Fi Server", isWifiRunning, if (isWifiRunning) "IP: $wifiIp" else "Off", { viewModel.toggleWifi() }, Color(0xFFFFF3E0), Color(0xFFFFEBEE))
            Spacer(modifier = Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Logs:", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.clearLogs() }, modifier = Modifier.height(36.dp)) { Text("Clear") }
            }
            Card(Modifier.fillMaxSize().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF212121))) {
                LazyColumn(Modifier.padding(8.dp), reverseLayout = true) { items(logs) { LogItemRow(it) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(viewModel: SimulatorViewModel) {
    // Підписка на дані
    val lightLevel by viewModel.lightLevel.collectAsState()
    val isLedOn by viewModel.isLedOn.collectAsState()

    // Нові підписки
    val rgbColor by viewModel.rgbColor.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val accel by viewModel.accelData.collectAsState()
    val gyro by viewModel.gyroData.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("GPIO & Sensors") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        )

        LazyColumn(modifier = Modifier.padding(16.dp)) {

            // 1. Блок LED та RGB
            item {
                Text("Виходи (Outputs)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Картка Ліхтарика
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Built-in LED", fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.size(40.dp)
                                    .background(if (isLedOn) Color.Green else Color.Gray, CircleShape)
                                    .border(2.dp, Color.DarkGray, CircleShape)
                            )
                        }
                    }

                    // Картка RGB (NeoPixel)
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RGB (WS2812)", fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            // Візуалізація кольору
                            Box(
                                Modifier.size(40.dp)
                                    .background(rgbColor, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            // Текстовий код кольору
                            Text(
                                text = "R:${(rgbColor.red*255).toInt()} G:${(rgbColor.green*255).toInt()} B:${(rgbColor.blue*255).toInt()}",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // 2. Блок Температури (Повзунок)
            item {
                Text("Датчики (Inputs)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("DHT22 (Temperature)", fontWeight = FontWeight.Bold)
                            Text("${"%.1f".format(temperature)}°C", fontWeight = FontWeight.Bold, color = calculateTempColor(temperature))
                        }

                        Slider(
                            value = temperature,
                            onValueChange = { viewModel.updateTemperature(it) },
                            valueRange = -10f..60f,
                            steps = 69,
                            colors = SliderDefaults.colors(
                                thumbColor = calculateTempColor(temperature),
                                activeTrackColor = calculateTempColor(temperature)
                            )
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 3. Блок IMU (Акселерометр та Гіроскоп)
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("MPU6050 (IMU)", fontWeight = FontWeight.Bold)
                        Divider(Modifier.padding(vertical = 8.dp))

                        SensorValueRow("Accel X", accel.x)
                        SensorValueRow("Accel Y", accel.y)
                        SensorValueRow("Accel Z", accel.z)

                        Divider(Modifier.padding(vertical = 8.dp))

                        SensorValueRow("Gyro X", gyro.x)
                        SensorValueRow("Gyro Y", gyro.y)
                        SensorValueRow("Gyro Z", gyro.z)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 4. Блок Світла (BH1750)
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("BH1750 (Light)", fontWeight = FontWeight.Bold)
                            Text("${lightLevel.toInt()} lx")
                        }
                        LinearProgressIndicator(
                            progress = { (lightLevel / 1000f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Компонент для відображення рядка значень сенсора
@Composable
fun SensorValueRow(label: String, value: Float) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text("%.2f".format(value), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

// Допоміжна функція для кольору температури (Синій -> Червоний)
fun calculateTempColor(temp: Float): Color {
    // Нормалізуємо значення від -10 до 50
    val fraction = ((temp + 10) / 60f).coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.lerp(
        Color(0xFF2196F3), // Синій (холодно)
        Color(0xFFF44336), // Червоний (гаряче)
        fraction
    )
}

@Composable
fun MapScreen(viewModel: SimulatorViewModel) {
    val myGps by viewModel.gpsData.collectAsState()
    val clientGps by viewModel.clientGpsData.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Ініціалізація OSMdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    Column(Modifier.fillMaxSize()) {
        // Інфо-панель зверху
        Card(
            Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(8.dp)) {
                Text("ESP32 (Me): ${"%.4f".format(myGps.latitude)}, ${"%.4f".format(myGps.longitude)}")
                if (clientGps != null) {
                    Text("Client: ${"%.4f".format(clientGps!!.latitude)}, ${"%.4f".format(clientGps!!.longitude)}", color = Color.Blue)

                    // Розрахунок відстані
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        myGps.latitude, myGps.longitude,
                        clientGps!!.latitude, clientGps!!.longitude,
                        results
                    )
                    Text("Distance: ${results[0].toInt()} meters", fontWeight = FontWeight.Bold)
                } else {
                    Text("Client location unknown (Send CLIENT_GPS:lat,lon)", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        // Сама мапа
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // 1. Маркер ESP32 (Червоний/Стандартний)
                val myPoint = GeoPoint(myGps.latitude, myGps.longitude)
                val myMarker = Marker(mapView)
                myMarker.position = myPoint
                myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                myMarker.title = "ESP32 Simulator (Me)"
                mapView.overlays.add(myMarker)

                // Центруємо мапу на нас (опціонально)
                if (myGps.latitude != 0.0) {
                    mapView.controller.setCenter(myPoint)
                }

                // 2. Маркер Клієнта (Синій)
                if (clientGps != null) {
                    val clientPoint = GeoPoint(clientGps!!.latitude, clientGps!!.longitude)
                    val clientMarker = Marker(mapView)
                    clientMarker.position = clientPoint
                    clientMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    clientMarker.title = "Connected Client"
                    clientMarker.icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_myplaces)
                    mapView.overlays.add(clientMarker)
                }

                mapView.invalidate() // Оновити мапу
            }
        )
    }
}

@Composable
fun StatusCard(title: String, isRunning: Boolean, details: String, onToggle: () -> Unit, activeColor: Color, inactiveColor: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isRunning) activeColor else inactiveColor), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(details, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            Switch(checked = isRunning, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
fun LogItemRow(log: LogMessage) {
    val color = when (log.type) { LogType.RX -> Color(0xFF64B5F6); LogType.TX -> Color(0xFFFFB74D); LogType.SUCCESS -> Color.Green; LogType.ERROR -> Color(0xFFE57373); else -> Color.White }
    Text("> [${log.time}] ${log.message}", color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
}