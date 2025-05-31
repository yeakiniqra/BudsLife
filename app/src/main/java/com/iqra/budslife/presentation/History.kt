package com.iqra.budslife.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqra.budslife.data.BudsEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Define the color scheme based on #8BC34A (Light Green)
private val PrimaryColor = Color(0xFF8BC34A)
private val PrimaryDarkColor = Color(0xFF689F38)
private val PrimaryLightColor = Color(0xFFDCEDC8)
private val SecondaryColor = Color(0xFF4CAF50)
private val TextPrimaryColor = Color(0xFF212121)
private val TextSecondaryColor = Color(0xFF757575)
private val ChartColor = Color(0xFF8BC34A)
private val ChartGradientStart = Color(0xCC8BC34A)
private val ChartGradientEnd = Color(0x338BC34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage() {
    val viewModel: BluetoothViewModel = viewModel()
    val devices by viewModel.allDevices.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()

    var selectedDeviceIndex by remember { mutableIntStateOf(-1) }
    var timeRangeIndex by remember { mutableIntStateOf(1) } // 0: Day, 1: Week, 2: Month
    var showFilterDialog by remember { mutableStateOf(false) }

    // Generate sample history data (in a real app, you would get this from the repository)
    val batteryHistory = remember(selectedDeviceIndex, timeRangeIndex) {
        if (selectedDeviceIndex >= 0 && selectedDeviceIndex < devices.size) {
            generateBatteryHistoryData(timeRangeIndex)
        } else {
            emptyList()
        }
    }

    // Check if data is available
    val hasData = devices.isNotEmpty() && selectedDeviceIndex >= 0

    // Animation triggers
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showContent = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Battery History",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Filter",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(PrimaryLightColor.copy(alpha = 0.2f))
        ) {
            // Main content
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(initialOffsetY = { it / 2 }),
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

                            Spacer(modifier = Modifier.height(16.dp))

                            // Time range selector
                            TimeRangeSelector(
                                selectedIndex = timeRangeIndex,
                                onRangeSelected = { timeRangeIndex = it }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (hasData) {
                                // Battery chart
                                BatteryChart(
                                    history = batteryHistory,
                                    timeRange = when(timeRangeIndex) {
                                        0 -> "Last 24 Hours"
                                        1 -> "Last 7 Days"
                                        else -> "Last 30 Days"
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Battery history list
                                BatteryHistoryList(history = batteryHistory)
                            } else {
                                // No device selected
                                NoDeviceSelectedView()
                            }
                        }
                    }
                }
            }

            // Filter dialog
            if (showFilterDialog) {
                FilterDialog(
                    currentTimeRange = timeRangeIndex,
                    onTimeRangeSelected = {
                        timeRangeIndex = it
                        showFilterDialog = false
                    },
                    onDismiss = { showFilterDialog = false }
                )
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
                                    color = getBatteryColor(device.lastBatteryLevel)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeSelector(
    selectedIndex: Int,
    onRangeSelected: (Int) -> Unit
) {
    Column {
        Text(
            text = "Time Range",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedIndex == 0,
                onClick = { onRangeSelected(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = PrimaryColor,
                    activeContentColor = Color.White,
                    inactiveContainerColor = PrimaryLightColor.copy(alpha = 0.5f)
                )
            ) {
                Text("Day")
            }

            SegmentedButton(
                selected = selectedIndex == 1,
                onClick = { onRangeSelected(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = PrimaryColor,
                    activeContentColor = Color.White,
                    inactiveContainerColor = PrimaryLightColor.copy(alpha = 0.5f)
                )
            ) {
                Text("Week")
            }

            SegmentedButton(
                selected = selectedIndex == 2,
                onClick = { onRangeSelected(2) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = PrimaryColor,
                    activeContentColor = Color.White,
                    inactiveContainerColor = PrimaryLightColor.copy(alpha = 0.5f)
                )
            ) {
                Text("Month")
            }
        }
    }
}

@Composable
private fun BatteryChart(history: List<BatteryRecord>, timeRange: String) {
    if (history.isEmpty()) return

    // Animation
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(1000),
        label = "chartAnimation"
    )
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "ChartAnimation"
    )

    LaunchedEffect(history) {
        animationPlayed = false
        animationPlayed = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Battery History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = PrimaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timeRange,
                        fontSize = 12.sp,
                        color = TextSecondaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
                // Draw chart
                val minBattery = history.minOfOrNull { it.batteryLevel } ?: 0
                val maxBattery = history.maxOfOrNull { it.batteryLevel } ?: 100

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val stepX = width / (history.size - 1)

                    // Chart path
                    val path = Path()
                    val filledPath = Path()

                    // Draw chart lines
                    history.forEachIndexed { index, record ->
                        val x = index * stepX
                        val normalizedLevel = (record.batteryLevel - minBattery) /
                                ((maxBattery - minBattery).coerceAtLeast(1)).toFloat()
                        val y = height - (height * normalizedLevel * animationProgress)

                        if (index == 0) {
                            path.moveTo(x, y)
                            filledPath.moveTo(x, height)
                            filledPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            filledPath.lineTo(x, y)
                        }
                    }

                    // Close filled path
                    filledPath.lineTo(width, height)
                    filledPath.close()

                    // Draw gradient fill
                    drawPath(
                        path = filledPath,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(ChartGradientStart, ChartGradientEnd)
                        )
                    )

                    // Draw line
                    drawPath(
                        path = path,
                        color = ChartColor,
                        style = Stroke(width = 3f)
                    )

                    // Draw points
                    history.forEachIndexed { index, record ->
                        val x = index * stepX
                        val normalizedLevel = (record.batteryLevel - minBattery) /
                                ((maxBattery - minBattery).coerceAtLeast(1)).toFloat()
                        val y = height - (height * normalizedLevel * animationProgress)

                        drawCircle(
                            color = ChartColor,
                            radius = 4f,
                            center = Offset(x, y)
                        )

                        drawCircle(
                            color = Color.White,
                            radius = 2f,
                            center = Offset(x, y)
                        )
                    }
                }

                // Battery range text
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = "${maxBattery}%",
                        fontSize = 12.sp,
                        color = TextSecondaryColor
                    )

                    Spacer(modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f))

                    Text(
                        text = "${minBattery}%",
                        fontSize = 12.sp,
                        color = TextSecondaryColor
                    )
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimeLabel(history.first().timestamp, timeRange),
                    fontSize = 10.sp,
                    color = TextSecondaryColor
                )

                Text(
                    text = formatTimeLabel(history.last().timestamp, timeRange),
                    fontSize = 10.sp,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun BatteryHistoryList(history: List<BatteryRecord>) {
    Column {
        Text(
            text = "Battery Events",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryColor,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                items(history.filter { it.isKeyEvent }) { record ->
                    BatteryEventItem(record)

                    if (record != history.last { it.isKeyEvent }) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = PrimaryLightColor
                        )
                    }
                }

                if (history.none { it.isKeyEvent }) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No significant battery events recorded",
                                color = TextSecondaryColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryEventItem(record: BatteryRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Battery indicator
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(getBatteryColor(record.batteryLevel).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${record.batteryLevel}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = getBatteryColor(record.batteryLevel)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = getBatteryEventDescription(record),
                fontSize = 14.sp,
                color = TextPrimaryColor
            )

            Text(
                text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    .format(record.timestamp),
                fontSize = 12.sp,
                color = TextSecondaryColor
            )
        }
    }
}

@Composable
private fun FilterDialog(
    currentTimeRange: Int,
    onTimeRangeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Select Time Range",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                TimeRangeOption(
                    title = "Last 24 Hours",
                    description = "Battery data from the past day",
                    icon = Icons.Default.History,
                    isSelected = currentTimeRange == 0,
                    onClick = { onTimeRangeSelected(0) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TimeRangeOption(
                    title = "Last 7 Days",
                    description = "Weekly battery usage trends",
                    icon = Icons.Default.ShowChart,
                    isSelected = currentTimeRange == 1,
                    onClick = { onTimeRangeSelected(1) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TimeRangeOption(
                    title = "Last 30 Days",
                    description = "Monthly battery usage patterns",
                    icon = Icons.Default.CalendarMonth,
                    isSelected = currentTimeRange == 2,
                    onClick = { onTimeRangeSelected(2) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondaryColor)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = {
                        onTimeRangeSelected(currentTimeRange)
                    }) {
                        Text("Apply", color = PrimaryColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryLightColor.copy(alpha = 0.3f) else Color.Transparent
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, PrimaryColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { },
                modifier = Modifier.size(40.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isSelected) PrimaryColor.copy(alpha = 0.2f)
                    else PrimaryLightColor.copy(alpha = 0.2f),
                    contentColor = if (isSelected) PrimaryColor else TextSecondaryColor
                )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = TextPrimaryColor
                )

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = TextSecondaryColor
                )
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
            text = "Please grant Bluetooth permissions on the Home page to view battery history",
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
            text = "Connect your earbuds from the Home page to track battery history",
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
            imageVector = Icons.Default.DeleteSweep,
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

// Data class for battery records
data class BatteryRecord(
    val batteryLevel: Int,
    val timestamp: Date,
    val isKeyEvent: Boolean = false,
    val eventType: BatteryEventType = BatteryEventType.NORMAL
)

enum class BatteryEventType {
    LOW_BATTERY,
    CHARGING,
    FULLY_CHARGED,
    CONNECTED,
    DISCONNECTED,
    NORMAL
}

// Helper functions

// Generate sample battery history data
private fun generateBatteryHistoryData(timeRangeIndex: Int): List<BatteryRecord> {
    val calendar = Calendar.getInstance()
    val now = calendar.time
    val records = mutableListOf<BatteryRecord>()

    val points = when (timeRangeIndex) {
        0 -> 24  // 24 hours, hourly points
        1 -> 28  // 7 days, 4 points per day
        else -> 30 // 30 days, daily points
    }

    // Start from the past and move toward now
    val startLevel = (60..100).random()
    var currentLevel = startLevel

    for (i in points downTo 0) {
        calendar.time = now

        when (timeRangeIndex) {
            0 -> calendar.add(Calendar.HOUR, -i) // Past hours
            1 -> calendar.add(Calendar.HOUR, -i * 6) // Past 6-hour intervals
            else -> calendar.add(Calendar.DAY_OF_MONTH, -i) // Past days
        }

        // Randomly adjust battery level with some logic
        val change = (-8..5).random()
        currentLevel = (currentLevel + change).coerceIn(1, 100)

        // Determine if this is a key event
        val isKeyEvent = currentLevel <= 20 || currentLevel >= 90 || i % (points / 5) == 0

        // Determine event type
        val eventType = when {
            currentLevel <= 15 -> BatteryEventType.LOW_BATTERY
            currentLevel >= 95 -> BatteryEventType.FULLY_CHARGED
            i == 0 -> BatteryEventType.CONNECTED
            i == points -> BatteryEventType.DISCONNECTED
            else -> BatteryEventType.NORMAL
        }

        records.add(
            BatteryRecord(
                batteryLevel = currentLevel,
                timestamp = calendar.time,
                isKeyEvent = isKeyEvent,
                eventType = eventType
            )
        )
    }

    return records
}

private fun getBatteryEventDescription(record: BatteryRecord): String {
    return when (record.eventType) {
        BatteryEventType.LOW_BATTERY -> "Low Battery Alert"
        BatteryEventType.CHARGING -> "Charging"
        BatteryEventType.FULLY_CHARGED -> "Fully Charged"
        BatteryEventType.CONNECTED -> "Device Connected"
        BatteryEventType.DISCONNECTED -> "Device Disconnected"
        BatteryEventType.NORMAL -> {
            if (record.batteryLevel <= 20) "Battery Running Low"
            else "Battery Level Update"
        }
    }
}

private fun formatTimeLabel(date: Date, timeRange: String): String {
    return when (timeRange) {
        "Last 24 Hours" -> SimpleDateFormat("h a", Locale.getDefault()).format(date)
        "Last 7 Days" -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
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