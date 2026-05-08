package com.example.sensorstream.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sensorstream.data.model.ConnectionState
import com.example.sensorstream.data.model.SensorType
import com.example.sensorstream.ui.theme.*
import com.example.sensorstream.ui.viewmodel.SensorStreamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: SensorStreamViewModel) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val snapshot by viewModel.currentSnapshot.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val config by viewModel.streamConfig.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleStreaming() },
                containerColor = if (isStreaming) StatusError else StatusStreaming
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isStreaming) "Stop Streaming" else "Start Streaming"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Connection Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connected -> "Connected to ${config.targetIp}:${config.targetPort}"
                            is ConnectionState.Connecting -> "Connecting..."
                            is ConnectionState.Disconnected -> "Disconnected"
                            is ConnectionState.Error -> "Error: ${(connectionState as ConnectionState.Error).message}"
                        },
                        color = when (connectionState) {
                            is ConnectionState.Connected -> StatusStreaming
                            is ConnectionState.Error -> StatusError
                            else -> StatusIdle
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Sensors List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sortedSensors = snapshot.sensors.values.sortedBy { it.type.ordinal }
                items(sortedSensors) { reading ->
                    SensorCard(
                        name = reading.type.displayName,
                        values = floatArrayOf(reading.values.x, reading.values.y, reading.values.z),
                        unit = reading.type.unit
                    )
                }

                if (snapshot.gps != null) {
                    item {
                        SensorCard(
                            name = "GPS Location",
                            values = floatArrayOf(
                                snapshot.gps!!.latitude.toFloat(),
                                snapshot.gps!!.longitude.toFloat(),
                                snapshot.gps!!.altitude.toFloat()
                            ),
                            unit = "deg, deg, m"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(name: String, values: FloatArray, unit: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = CyanLight)
            Column(horizontalAlignment = Alignment.End) {
                if (values.size == 1) {
                    Text(String.format("%.3f %s", values[0], unit))
                } else {
                    Text(String.format("X: %7.3f", values.getOrElse(0) { 0f }))
                    Text(String.format("Y: %7.3f", values.getOrElse(1) { 0f }))
                    Text(String.format("Z: %7.3f", values.getOrElse(2) { 0f }))
                }
            }
        }
    }
}
