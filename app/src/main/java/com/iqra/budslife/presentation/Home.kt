package com.iqra.budslife.presentation

import com.iqra.budslife.presentation.formatDate
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqra.budslife.data.BudsEntity
import com.iqra.budslife.domain.ConnectionState
import kotlinx.coroutines.launch

// Define the color scheme based on #8BC34A (Light Green)
private val PrimaryColor = Color(0xFF8BC34A)
private val PrimaryDarkColor = Color(0xFF689F38)
private val PrimaryLightColor = Color(0xFFDCEDC8)
private val SecondaryColor = Color(0xFF4CAF50)
private val AccentColor = Color(0xFFFF9800) // Orange for warnings
private val AccentLightColor = Color(0xFFFFCC80) // Light Orange
private val TextPrimaryColor = Color(0xFF212121)
private val TextSecondaryColor = Color(0xFF757575)

fun getBatteryGradient(level: Int): List<Color> {
    return when {
        level >= 75 -> listOf(Color(0xFFA5D6A7), Color(0xFF388E3C)) // Green shades
        level >= 50 -> listOf(Color(0xFFFFF59D), Color(0xFFFFC107)) // Yellow shades
        level >= 25 -> listOf(Color(0xFFFFCC80), Color(0xFFFF9800)) // Orange shades
        else -> listOf(Color(0xFFEF9A9A), Color(0xFFD32F2F))         // Red shades
    }
}


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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (permissionState == PermissionState.Granted && isBluetoothEnabled && uiState !is UiState.NoDevices) {
                AnimatedRefreshFAB(
                    onClick = { viewModel.refreshPairedDevices() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PrimaryLightColor.copy(alpha = 0.1f),
                            Color.White
                        )
                    )
                )
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
private fun AnimatedRefreshFAB(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_scale"
    )

    FloatingActionButton(
        onClick = onClick,
        containerColor = PrimaryColor,
        contentColor = Color.White,
        modifier = Modifier.scale(scale)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh devices"
        )
    }
}

@Composable
private fun LoadingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "loading_rotation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotation),
                tint = PrimaryColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Searching for your buds...",
                color = TextPrimaryColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PermissionRequiredView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn()
        ) {
            Icon(
                imageVector = Icons.Default.BatteryStd,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = PrimaryColor
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Bluetooth Access Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "BudsLife needs Bluetooth permissions to monitor your earbuds battery levels and keep you informed",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Grant Permissions",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BluetoothDisabledView(onEnableBluetooth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn()
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = AccentColor
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Bluetooth is Disabled",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Please enable Bluetooth to start monitoring your earbuds",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onEnableBluetooth,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "Enable Bluetooth",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NoDevicesView(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn()
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = PrimaryColor
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Earbuds Found",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Pair your Bluetooth earbuds with your phone in Settings, then refresh to detect them",
            textAlign = TextAlign.Center,
            color = TextSecondaryColor,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Scan for Devices",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Connected device card (if any) - only show if connection state is CONNECTED
            if (currentDevice != null && connectionState == ConnectionState.CONNECTED) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    ConnectedDeviceCard(
                        device = currentDevice,
                        connectionState = connectionState,
                        batteryLevel = if (batteryLevel != null && batteryLevel > 0) batteryLevel else currentDevice.lastBatteryLevel,
                        onDisconnect = onDisconnect
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Text(
                text = "Available Devices",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(devices) { device ->
            // For all devices, including the connected one (but with different appearance)
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                // Check if this device is the currently connected one
                val isConnected = currentDevice?.deviceAddress == device.deviceAddress &&
                        connectionState == ConnectionState.CONNECTED

                EnhancedDeviceCard(
                    device = device,
                    isConnected = isConnected,
                    onClick = { onDeviceClick(device.deviceAddress) },
                    onNotificationToggle = { enabled ->
                        onNotificationToggle(device.deviceAddress, enabled)
                    },
                    onUpdateThreshold = { _, _ ->
                        onUpdateThreshold(device.deviceAddress, device.batteryThreshold)
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


@Composable
fun ConnectedDeviceCard(
    device: BudsEntity,
    connectionState: ConnectionState,
    batteryLevel: Int,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use a simpler approach for determining battery color
    val batteryColor = when {
        batteryLevel >= 50 -> Color(0xFF4CAF50) // Green for good battery
        batteryLevel >= 20 -> Color(0xFFFFA000) // Orange/Amber for medium battery
        else -> Color(0xFFF44336) // Red for low battery
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E3A8A) // Connected state color
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with device name and connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = device.deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Connected",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Simple connection status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.Green, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Battery level and disconnect button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery level display with simplified approach
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = batteryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$batteryLevel%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                }

                // Disconnect button
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Text("Disconnect", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedDeviceCard(
    device: BudsEntity,
    isConnected: Boolean = false,
    onClick: () -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    onUpdateThreshold: (String, Int) -> Unit,
    onDelete: () -> Unit
) {
    // Ensure we're displaying actual battery level (never show 0% unless it's truly 0%)
    val displayBatteryLevel = if (device.lastBatteryLevel > 0) device.lastBatteryLevel else 100
    val isLowBattery = displayBatteryLevel <= device.batteryThreshold

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnected -> Color(0xFF1E3A8A).copy(alpha = 0.8f) // Connected device blue
                isLowBattery -> Color(0xFFFFF3E0) // Low battery warning
                else -> Color.White // Normal card
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = when {
                isConnected -> 8.dp
                isLowBattery -> 6.dp
                else -> 4.dp
            }
        ),
        shape = RoundedCornerShape(20.dp),
        border = when {
            isConnected -> BorderStroke(2.dp, PrimaryColor)
            isLowBattery -> BorderStroke(1.dp, AccentColor.copy(alpha = 0.3f))
            else -> null
        },
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Device name and connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color.White else TextPrimaryColor
                    )

                    Text(
                        text = if (isConnected) "Connected" else "Last connected: ${formatDate(device.lastConnected)}",
                        fontSize = 14.sp,
                        color = if (isConnected) Color.White.copy(alpha = 0.8f) else TextSecondaryColor
                    )
                }

                // Battery indicator with connection status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Connection indicator
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Battery Percentage
                    Text(
                        text = "${displayBatteryLevel}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isConnected -> Color.White
                            displayBatteryLevel >= 50 -> Color(0xFF4CAF50)
                            displayBatteryLevel >= 20 -> Color(0xFFFFA000)
                            else -> Color(0xFFF44336)
                        }
                    )

                    // Battery icon
                    Icon(
                        imageVector = when {
                            displayBatteryLevel >= 80 -> Icons.Default.BatteryFull
                            displayBatteryLevel >= 50 -> Icons.Default.Battery6Bar
                            displayBatteryLevel >= 20 -> Icons.Default.Battery3Bar
                            else -> Icons.Default.BatteryAlert
                        },
                        contentDescription = "Battery level",
                        tint = when {
                            isConnected -> Color.White
                            displayBatteryLevel >= 50 -> Color(0xFF4CAF50)
                            displayBatteryLevel >= 20 -> Color(0xFFFFA000)
                            else -> Color(0xFFF44336)
                        }
                    )
                }
            }

            // Warning if battery is low and device is not the connected one
            if (isLowBattery && !isConnected) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentLightColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Battery below threshold (${device.batteryThreshold}%)",
                        color = TextPrimaryColor,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notifications Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    fontSize = 16.sp,
                    color = if (isConnected) Color.White else TextPrimaryColor,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = device.notificationsEnabled,
                    onCheckedChange = onNotificationToggle,
                    thumbContent = {
                        Icon(
                            imageVector = if (device.notificationsEnabled)
                                Icons.Default.Notifications
                            else
                                Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (isConnected) Color.White else PrimaryColor,
                        checkedTrackColor = if (isConnected) PrimaryColor else PrimaryLightColor,
                        checkedIconColor = if (isConnected) PrimaryColor else Color.White,
                        uncheckedThumbColor = if (isConnected) Color.White.copy(alpha = 0.8f) else Color.White,
                        uncheckedTrackColor = if (isConnected) Color.White.copy(alpha = 0.2f) else Color.LightGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Battery Threshold
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Battery Threshold",
                    fontSize = 16.sp,
                    color = if (isConnected) Color.White else TextPrimaryColor,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = {
                        onUpdateThreshold(device.deviceAddress, device.batteryThreshold)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color.White else PrimaryColor,
                        contentColor = if (isConnected) PrimaryColor else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(120.dp)
                ) {
                    Text(
                        text = "${device.batteryThreshold}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row - Connect/Disconnect/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connect/Disconnect button based on state
                if (isConnected) {
                    Button(
                        onClick = onClick, // This will trigger onDisconnect through the parent
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Disconnect",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color.White else PrimaryColor,
                            contentColor = if (isConnected) PrimaryColor else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Connect",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete button
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) Color.White.copy(alpha = 0.2f) else Color(0xFFEF5350).copy(alpha = 0.1f),
                        contentColor = if (isConnected) Color.White else Color(0xFFEF5350)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}