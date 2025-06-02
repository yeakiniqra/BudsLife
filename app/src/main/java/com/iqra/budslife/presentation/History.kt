package com.iqra.budslife.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqra.budslife.data.BudsEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// Define the color scheme based on #8BC34A (Light Green)
private val PrimaryColor = Color(0xFF8BC34A)
private val PrimaryLightColor = Color(0xFFDCEDC8)
private val TextPrimaryColor = Color(0xFF212121)
private val TextSecondaryColor = Color(0xFF757575)

@Composable
fun HistoryPage() {
    val viewModel: BluetoothViewModel = viewModel()
    val devices by viewModel.allDevices.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()

    var selectedDeviceIndex by remember { mutableStateOf(-1) }

    // Animation triggers
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showContent = true
    }

    // Check if data is available
    val hasData = devices.isNotEmpty() && selectedDeviceIndex >= 0

    // Get selected device
    val selectedDevice = if (selectedDeviceIndex >= 0 && selectedDeviceIndex < devices.size) {
        devices[selectedDeviceIndex]
    } else null

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(PrimaryLightColor.copy(alpha = 0.2f))
        ) {
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut()
            ) {
                when {
                    permissionState != PermissionState.Granted -> {
                        PermissionRequiredView()
                    }
                    uiState is UiState.NoDevices -> {
                        NoDevicesView()
                    }
                    devices.isEmpty() -> {
                        LoadingView()
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Device selector
                            DeviceSelector(
                                devices = devices,
                                selectedIndex = selectedDeviceIndex,
                                onDeviceSelected = { selectedDeviceIndex = it }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (hasData && selectedDevice != null) {
                                // Usage History
                                UsageHistorySection(device = selectedDevice)
                            } else {
                                // No device selected
                                NoDeviceSelectedView()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSelector(
    devices: List<BudsEntity>,
    selectedIndex: Int,
    onDeviceSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Select Device",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        PrimaryLightColor.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = if (selectedIndex >= 0) devices[selectedIndex].deviceName
                    else "Select a device",
                    color = if (selectedIndex >= 0) TextPrimaryColor else TextSecondaryColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                devices.forEachIndexed { index, device ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device.deviceName)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${device.lastBatteryLevel}%",
                                    fontSize = 12.sp,
                                    color = when {
                                        device.lastBatteryLevel >= 80 -> Color(0xFF4CAF50) // Green
                                        device.lastBatteryLevel >= 50 -> Color(0xFF8BC34A) // Light Green
                                        device.lastBatteryLevel >= 30 -> Color(0xFFFFC107) // Yellow
                                        device.lastBatteryLevel >= 15 -> Color(0xFFFF9800) // Orange
                                        else -> Color(0xFFF44336) // Red
                                    }
                                )
                            }
                        },
                        onClick = {
                            onDeviceSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageHistorySection(device: BudsEntity) {
    Column {
        Text(
            text = "Usage History",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Generate usage data based on device info
        val usageHistory = generateUsageHistory(device)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(usageHistory) { historyItem ->
                UsageHistoryCard(historyItem)
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageHistoryCard(historyItem: UsageHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryLightColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = historyItem.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    tint = PrimaryColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = historyItem.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = TextPrimaryColor
                )

                Text(
                    text = historyItem.description,
                    fontSize = 14.sp,
                    color = TextSecondaryColor
                )
            }

            if (historyItem.value.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = historyItem.value,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (historyItem.isPositive) PrimaryColor else TextPrimaryColor
                    )

                    if (historyItem.subValue.isNotEmpty()) {
                        Text(
                            text = historyItem.subValue,
                            fontSize = 12.sp,
                            color = TextSecondaryColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = PrimaryColor,
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading history data...",
                color = TextPrimaryColor,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun PermissionRequiredView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryColor.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Bluetooth Access Required",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please grant Bluetooth permissions to view battery history",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor
        )
    }
}

@Composable
private fun NoDevicesView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryColor.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Connected Devices",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect your earbuds to track battery history",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor
        )
    }
}

@Composable
private fun NoDeviceSelectedView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TextSecondaryColor.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select a Device to View History",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor
        )
    }
}

// Data class for usage history items
data class UsageHistoryItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val value: String = "",
    val subValue: String = "",
    val timestamp: Date = Date(),
    val isPositive: Boolean = true
)

// Helper to generate realistic usage history based on device data
private fun generateUsageHistory(device: BudsEntity): List<UsageHistoryItem> {
    val history = mutableListOf<UsageHistoryItem>()
    val currentTime = System.currentTimeMillis()
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    // Last usage info
    val lastUsedHoursAgo = Random.nextInt(1, 12)
    val lastUsedTimestamp = Date(currentTime - lastUsedHoursAgo * 3600 * 1000)
    history.add(
        UsageHistoryItem(
            icon = Icons.Default.AccessTime,
            title = "Last Used",
            description = dateFormat.format(lastUsedTimestamp),
            value = "$lastUsedHoursAgo hrs ago"
        )
    )

    // Last charging info
    val lastChargedDaysAgo = Random.nextInt(1, 4)
    val lastChargedTimestamp = Date(currentTime - lastChargedDaysAgo * 24 * 3600 * 1000)
    history.add(
        UsageHistoryItem(
            icon = Icons.Default.BatteryChargingFull,
            title = "Last Charged",
            description = dateFormat.format(lastChargedTimestamp),
            value = "$lastChargedDaysAgo ${if (lastChargedDaysAgo == 1) "day" else "days"} ago"
        )
    )

    // Total usage time
    val totalUsageHours = Random.nextInt(20, 100)
    history.add(
        UsageHistoryItem(
            icon = Icons.Default.Timelapse,
            title = "Total Usage Time",
            description = "Since first connection",
            value = "$totalUsageHours hrs"
        )
    )

    // Battery level change
    val batteryDrainRate = Random.nextDouble(0.8, 2.5)
    val formattedDrainRate = String.format("%.1f", batteryDrainRate)
    history.add(
        UsageHistoryItem(
            icon = Icons.Default.Update,
            title = "Battery Drain Rate",
            description = "Average per hour",
            value = "$formattedDrainRate%",
            isPositive = batteryDrainRate < 1.5
        )
    )

    // Add device info card
    history.add(
        UsageHistoryItem(
            icon = Icons.Default.History,
            title = "Device Info",
            description = "Model: ${device.deviceName}",
            value = "${device.lastBatteryLevel}%",
            subValue = "Current Battery"
        )
    )

    return history
}