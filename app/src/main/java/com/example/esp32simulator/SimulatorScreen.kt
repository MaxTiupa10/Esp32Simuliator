package com.example.esp32simulator

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

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
    val lightLevel by viewModel.lightLevel.collectAsState()
    val isLedOn by viewModel.isLedOn.collectAsState() // Стан світла

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("GPIO & Датчики") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
        Column(modifier = Modifier.padding(16.dp)) {

            // --- БЛОК LED (ЛІХТАРИК) ---
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("GPIO 2 (Flashlight)", style = MaterialTheme.typography.titleMedium)
                        Text(if (isLedOn) "State: ON" else "State: OFF", color = if(isLedOn) Color(0xFF2E7D32) else Color.Gray)
                    }
                    // Візуальний кружечок
                    Box(Modifier.size(40.dp).background(if (isLedOn) Color.Green else Color.Gray, CircleShape).border(2.dp, Color.DarkGray, CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- ТЕСТОВІ КНОПКИ ---
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.manualCommand("LED_ON") }, modifier = Modifier.weight(1f)) { Text("Test ON") }
                Button(onClick = { viewModel.manualCommand("LED_OFF") }, modifier = Modifier.weight(1f)) { Text("Test OFF") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- БЛОК LIGHT SENSOR ---
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Light Sensor", style = MaterialTheme.typography.titleMedium)
                    Text("${lightLevel.toInt()} lux", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { (lightLevel / 1000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: SimulatorViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Карта в розробці") }
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