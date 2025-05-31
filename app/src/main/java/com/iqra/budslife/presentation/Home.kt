package com.iqra.budslife.presentation

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Earbuds
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqra.budslife.data.BudsEntity
import com.iqra.budslife.domain.ConnectionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Define the color scheme based on #8BC34A (Light Green)
private val PrimaryColor = Color(0xFF8BC34A)
private val PrimaryDarkColor = Color(0xFF689F38)
private val PrimaryLightColor = Color(0xFFDCEDC8)
private val SecondaryColor = Color(0xFF4CAF50)
private val TextPrimaryColor = Color(0xFF212121)
private val TextSecondaryColor = Color(0xFF757575)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage() {
    val viewModel: BluetoothViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // State collectors
    val uiState by viewModel.uiState.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled
    val devices by viewModel.devicesSortedByBatteryLevel.collectAsState()
    val currentConnectionState by viewModel.connectionState.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()

    // Permission request launchers
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.startMonitoringService()
            viewModel.refreshPairedDevices()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Bluetooth permissions required to use this app")
            }
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.resetState()
            viewModel.refreshPairedDevices()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Bluetooth must be enabled to use this app")
            }
        }
    }

    // Handle permission requests on app start
    LaunchedEffect(permissionState) {
        when (permissionState) {
            PermissionState.Denied -> {
                // Request necessary permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    )
                } else {
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                        )
                    )
                }
            }
            PermissionState.Granted -> {
                if (!isBluetoothEnabled) {
                    // Show Bluetooth enable dialog
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBluetoothLauncher.launch(enableBtIntent)
                } else {
                    // Start service if permissions are granted and Bluetooth is on
                    viewModel.startMonitoringService()
                    viewModel.refreshPairedDevices()
                }
            }
            PermissionState.Unknown -> {
                // Check permissions
                viewModel.checkPermissions()
            }
        }
    }

    // Show error messages in snackbar
    LaunchedEffect(uiState) {
        if (uiState is UiState.Error) {
            scope.launch {
                snackbarHostState.showSnackbar((uiState as UiState.Error).message)
                viewModel.clearUiError()
            }
        }
    }

    // Main UI structure
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "BudsLife",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.refreshPairedDevices() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { /* Navigate to settings */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (permissionState == PermissionState.Granted && isBluetoothEnabled && uiState !is UiState.NoDevices) {
                FloatingActionButton(
                    onClick = { viewModel.refreshPairedDevices() },
                    containerColor = PrimaryColor,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh devices"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(PrimaryLightColor.copy(alpha = 0.2f))
        ) {
            when (uiState) {
                is UiState.Loading -> {
                    LoadingView()
                }
                is UiState.NeedsPermission -> {
                    PermissionRequiredView(
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                bluetoothPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    )
                                )
                            } else {
                                bluetoothPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH,
                                        Manifest.permission.BLUETOOTH_ADMIN
                                    )
                                )
                            }
                        }
                    )
                }
                is UiState.BluetoothDisabled -> {
                    BluetoothDisabledView(
                        onEnableBluetooth = {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }
                    )
                }
                is UiState.NoDevices -> {
                    NoDevicesView(
                        onRefresh = { viewModel.refreshPairedDevices() }
                    )
                }
                is UiState.Ready,
                is UiState.Connected,
                is UiState.Connecting,
                is UiState.Disconnected -> {
                    DevicesListView(
                        devices = devices,
                        currentDevice = currentDevice,
                        connectionState = currentConnectionState,
                        batteryLevel = batteryLevel,
                        onDeviceClick = { deviceAddress ->
                            viewModel.connectToDevice(deviceAddress)
                        },
                        onNotificationToggle = { deviceAddress, enabled ->
                            viewModel.toggleNotifications(deviceAddress, enabled)
                        },
                        onDisconnect = {
                            viewModel.disconnectDevice()
                        },
                        onUpdateThreshold = { deviceAddress, threshold ->
                            viewModel.updateDeviceThreshold(deviceAddress, threshold)
                        },
                        onDeleteDevice = { device ->
                            viewModel.deleteDevice(device)
                        }
                    )
                }
                is UiState.Error -> {
                    // Error is shown in snackbar, show ready screen
                    if (devices.isEmpty()) {
                        NoDevicesView(
                            onRefresh = { viewModel.refreshPairedDevices() }
                        )
                    } else {
                        DevicesListView(
                            devices = devices,
                            currentDevice = currentDevice,
                            connectionState = currentConnectionState,
                            batteryLevel = batteryLevel,
                            onDeviceClick = { deviceAddress ->
                                viewModel.connectToDevice(deviceAddress)
                            },
                            onNotificationToggle = { deviceAddress, enabled ->
                                viewModel.toggleNotifications(deviceAddress, enabled)
                            },
                            onDisconnect = {
                                viewModel.disconnectDevice()
                            },
                            onUpdateThreshold = { deviceAddress, threshold ->
                                viewModel.updateDeviceThreshold(deviceAddress, threshold)
                            },
                            onDeleteDevice = { device ->
                                viewModel.deleteDevice(device)
                            }
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
                text = "Loading...",
                color = TextPrimaryColor,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun PermissionRequiredView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BatteryStd,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Bluetooth Permissions Required",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "BudsLife needs Bluetooth permissions to monitor your earbuds battery levels",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            )
        ) {
            Text("Grant Permissions")
        }
    }
}

@Composable
private fun BluetoothDisabledView(onEnableBluetooth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Bluetooth is Disabled",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please enable Bluetooth to use BudsLife",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onEnableBluetooth,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            )
        ) {
            Text("Enable Bluetooth")
        }
    }
}

@Composable
private fun NoDevicesView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Earbuds,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = PrimaryColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Paired Earbuds Found",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pair your Bluetooth earbuds with your phone in Settings, then refresh",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

@Composable
private fun DevicesListView(
    devices: List<BudsEntity>,
    currentDevice: BudsEntity?,
    connectionState: ConnectionState,
    batteryLevel: Int?,
    onDeviceClick: (String) -> Unit,
    onNotificationToggle: (String, Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onUpdateThreshold: (String, Int) -> Unit,
    onDeleteDevice: (BudsEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Connected device card (if any)
            if (currentDevice != null) {
                ConnectedDeviceCard(
                    device = currentDevice,
                    connectionState = connectionState,
                    batteryLevel = batteryLevel,
                    onDisconnect = onDisconnect
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Text(
                text = "Available Buds",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(devices) { device ->
            // Skip already connected device
            if (currentDevice?.deviceAddress != device.deviceAddress) {
                DeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device.deviceAddress) },
                    onNotificationToggle = { enabled ->
                        onNotificationToggle(device.deviceAddress, enabled)
                    },
                    onUpdateThreshold = { threshold ->
                        onUpdateThreshold(device.deviceAddress, threshold)
                    },
                    onDelete = { onDeleteDevice(device) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCard(
    device: BudsEntity,
    onClick: () -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    onUpdateThreshold: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(device.batteryThreshold.toFloat()) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Device info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = device.deviceName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryColor
                    )
                    Text(
                        text = device.model ?: "Unknown Model",
                        fontSize = 14.sp,
                        color = TextSecondaryColor
                    )
                    Text(
                        text = "Last seen: ${formatDate(device.lastConnected)}",
                        fontSize = 12.sp,
                        color = TextSecondaryColor
                    )
                }

                BatteryIndicator(
                    batteryLevel = device.lastBatteryLevel,
                    isCharging = false,
                    size = 40.dp
                )
            }

            // Battery bar
            LinearProgressIndicator(
                progress = device.lastBatteryLevel / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(vertical = 8.dp),
                color = getBatteryColor(device.lastBatteryLevel),
                trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
                strokeCap = StrokeCap.Round
            )

            // Warning if battery is low
            if (device.lastBatteryLevel <= device.batteryThreshold) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Battery below threshold (${device.batteryThreshold}%)",
                        fontSize = 12.sp,
                        color = Color(0xFFFFA000)
                    )
                }
            }

            // Expandable section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    fontSize = 14.sp,
                    color = TextPrimaryColor
                )

                Switch(
                    checked = device.notificationsEnabled,
                    onCheckedChange = onNotificationToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryColor,
                        checkedTrackColor = PrimaryLightColor,
                        checkedBorderColor = PrimaryDarkColor
                    )
                )
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: BudsEntity,
    connectionState: ConnectionState,
    batteryLevel: Int?,
    onDisconnect: () -> Unit
) {
    val displayBatteryLevel = batteryLevel ?: device.lastBatteryLevel

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryColor.copy(alpha = 0.1f)
        ),
        border = BorderStroke(2.dp, PrimaryColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = device.deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkColor
                    )

                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "Connected"
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.DISCONNECTED -> "Disconnected"
                        },
                        fontSize = 14.sp,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            ConnectionState.CONNECTING -> Color(0xFFFFA000)
                            ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                        }
                    )
                }

                BatteryIndicator(
                    batteryLevel = displayBatteryLevel,
                    isCharging = false,
                    size = 50.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = displayBatteryLevel / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = getBatteryColor(displayBatteryLevel),
                trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$displayBatteryLevel%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryDarkColor
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun BatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(getBatteryColor(batteryLevel).copy(alpha = 0.2f))
            .border(2.dp, getBatteryColor(batteryLevel), CircleShape)
    ) {
        Text(
            text = "$batteryLevel%",
            fontSize = (size.value / 3).sp,
            fontWeight = FontWeight.Bold,
            color = getBatteryColor(batteryLevel)
        )
    }
}

private fun getBatteryColor(level: Int): Color {
    return when {
        level <= 20 -> Color(0xFFF44336) // Red
        level <= 40 -> Color(0xFFFF9800) // Orange
        level <= 60 -> Color(0xFFFFC107) // Yellow
        level <= 80 -> Color(0xFF8BC34A) // Light Green
        else -> Color(0xFF4CAF50) // Green
    }
}

private fun formatDate(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    val days = diff / (24 * 60 * 60 * 1000)

    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

@Preview
@Composable
private fun HomePagePreview() {
    MaterialTheme {
        HomePage()
    }
}