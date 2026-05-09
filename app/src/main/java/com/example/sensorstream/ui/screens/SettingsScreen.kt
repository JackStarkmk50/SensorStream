package com.example.sensorstream.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sensorstream.data.model.SensorType
import com.example.sensorstream.ui.viewmodel.SensorStreamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SensorStreamViewModel) {
    val config by viewModel.streamConfig.collectAsState()
    val scrollState = rememberScrollState()

    var ipAddress by remember { mutableStateOf(config.targetIp) }
    var port by remember { mutableStateOf(config.targetPort.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Network Configuration", style = MaterialTheme.typography.titleLarge)
            
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = config.protocol.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Protocol") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    com.example.sensorstream.data.model.StreamProtocol.values().forEach { protocol ->
                        DropdownMenuItem(
                            text = { Text(protocol.displayName) },
                            onClick = {
                                viewModel.updateConfig(config.copy(protocol = protocol))
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { 
                    ipAddress = it
                    viewModel.updateConfig(config.copy(targetIp = it))
                },
                label = { Text("Target IP or URL (e.g. ws://ngrok.io)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = port,
                onValueChange = { 
                    port = it
                    it.toIntOrNull()?.let { p ->
                        viewModel.updateConfig(config.copy(targetPort = p))
                    }
                },
                label = { Text("Target Port") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text("Active Sensors", style = MaterialTheme.typography.titleLarge)

            // Dynamic list of sensors
            SensorType.values().forEach { sensorType ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(sensorType.displayName)
                    Switch(
                        checked = config.enabledSensors.contains(sensorType),
                        onCheckedChange = { isChecked ->
                            val newSensors = config.enabledSensors.toMutableSet()
                            if (isChecked) newSensors.add(sensorType) else newSensors.remove(sensorType)
                            viewModel.updateConfig(config.copy(enabledSensors = newSensors))
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GPS / Location")
                Switch(
                    checked = config.gpsEnabled,
                    onCheckedChange = { isChecked ->
                        viewModel.updateConfig(config.copy(gpsEnabled = isChecked))
                    }
                )
            }
        }
    }
}
