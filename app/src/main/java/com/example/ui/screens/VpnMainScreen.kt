package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.VpnViewModel
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.ConnectingAmber
import com.example.ui.theme.DisconnectedRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnMainScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // State flows
    val connectionState by viewModel.connectionState.collectAsState()
    val activeNodeName by viewModel.activeNodeName.collectAsState()
    val uploadSpeed by viewModel.uploadSpeedBps.collectAsState()
    val downloadSpeed by viewModel.downloadSpeedBps.collectAsState()
    val totalBytesSent by viewModel.totalBytesSent.collectAsState()
    val totalBytesReceived by viewModel.totalBytesReceived.collectAsState()
    val uptimeSeconds by viewModel.uptimeSeconds.collectAsState()

    val configs by viewModel.allConfigs.collectAsState()
    val selectedConfig by viewModel.selectedConfig.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val logs by viewModel.usageLogs.collectAsState()
    val usageHistory by viewModel.usageHistory.collectAsState()

    // Screen Tabs navigation state
    var selectedTab by remember { mutableStateOf(0) }

    // Enforce Right-to-Left (Persian standard) for cohesive localization
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "V2Shield VPN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val tabs = listOf(
                        Triple("داشبورد", Icons.Default.Home, 0),
                        Triple("سرورها", Icons.Default.List, 1),
                        Triple("اپلیکیشن‌ها", Icons.Default.PlayArrow, 2),
                        Triple("تحلیل", Icons.Default.Info, 3),
                        Triple("تنظیمات", Icons.Default.Settings, 4)
                    )

                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            icon = { Icon(icon, contentDescription = label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    }
                ) { currentTab ->
                    when (currentTab) {
                        0 -> DashboardTab(
                            connectionState = connectionState,
                            activeNodeName = activeNodeName ?: (selectedConfig?.name ?: "سروری انتخاب نشده"),
                            uploadSpeed = uploadSpeed,
                            downloadSpeed = downloadSpeed,
                            totalSent = totalBytesSent,
                            totalReceived = totalBytesReceived,
                            uptime = uptimeSeconds,
                            onToggleConnect = { viewModel.toggleVpnState(context) }
                        )
                        1 -> ServersAndConfigsTab(
                            configs = configs,
                            selectedId = selectedConfig?.id ?: -1L,
                            onSelect = { viewModel.selectVpnConfig(it) },
                            onDelete = { viewModel.deleteVpnConfig(it) },
                            onImportLink = { link, onDone, onErr ->
                                viewModel.importConfigLink(link, onDone, onErr)
                            },
                            onAddManual = { name, proto, addr, port, uuid, tls, sni ->
                                viewModel.addCustomVpnConfig(name, proto, addr, port, uuid, tls, sni)
                            }
                        )
                        2 -> SplitTunnelTab(
                            viewModel = viewModel
                        )
                        3 -> LogsAndAnalyticsTab(
                            usageHistory = usageHistory,
                            logs = logs,
                            onClearLogs = { viewModel.clearAllLogs() }
                        )
                        4 -> SettingsAndSecurityTab(
                            settings = settings ?: AppSettings(),
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onBackup = { callback -> viewModel.backupSettings(callback) },
                            onRestore = { str, onOk, onFail -> viewModel.restoreSettings(str, onOk, onFail) }
                        )
                    }
                }
            }
        }
    }
}

// FORMAT HELPER
fun formatSpeedBytes(bytes: Long, withSpeed: Boolean = true): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val suffix = if (withSpeed) " KB/s" else " KB"
    return when {
        mb >= 1.0 -> {
            val speedSuffix = if (withSpeed) " MB/s" else " MB"
            String.format("%.1f%s", mb, speedSuffix)
        }
        kb >= 1.0 -> String.format("%.1f%s", kb, suffix)
        else -> String.format("%d B/s", bytes)
    }
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

// --------------------------------------------------------
// TAB 1: DASHBOARD
// --------------------------------------------------------
@Composable
fun DashboardTab(
    connectionState: V2ShieldVpnService.State,
    activeNodeName: String,
    uploadSpeed: Long,
    downloadSpeed: Long,
    totalSent: Long,
    totalReceived: Long,
    uptime: Long,
    onToggleConnect: () -> Unit
) {
    var isPressingDial by remember { mutableStateOf(false) }

    // Dynamic animation scales for neon pulse
    val dialScale by animateFloatAsState(
        targetValue = if (isPressingDial) 0.93f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val waveBaseSpeed = when (connectionState) {
        V2ShieldVpnService.State.CONNECTED -> 2.5f
        V2ShieldVpnService.State.CONNECTING, V2ShieldVpnService.State.RECONNECTING -> 7.0f
        else -> 0f
    }

    // Dynamic infinite transition to fuel glowing wave lines
    val infiniteTransition = rememberInfiniteTransition()
    val wavePulseState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Connected Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "سرور متصل فعلی:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeNodeName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // Status Beacon dot
                val colorBeacon = when (connectionState) {
                    V2ShieldVpnService.State.CONNECTED -> ConnectedGreen
                    V2ShieldVpnService.State.CONNECTING, V2ShieldVpnService.State.RECONNECTING -> ConnectingAmber
                    V2ShieldVpnService.State.DISCONNECTED -> DisconnectedRed
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = colorBeacon)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionState) {
                            V2ShieldVpnService.State.CONNECTED -> "متصل"
                            V2ShieldVpnService.State.CONNECTING -> "در حال تلاش"
                            V2ShieldVpnService.State.RECONNECTING -> "مجدد..."
                            V2ShieldVpnService.State.DISCONNECTED -> "قطع"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorBeacon
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // GORGEOUS INTERACTIVE CANVAS DIAL TRIGGER
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer(scaleX = dialScale, scaleY = dialScale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressingDial = true
                            tryAwaitRelease()
                            isPressingDial = false
                            onToggleConnect()
                        }
                    )
                }
                .testTag("connection_dial")
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            // Neon glowing feedback outline
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2.3f
                val centerOffset = Offset(size.width / 2, size.height / 2)

                // Background track
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.1f),
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = 16.dp.toPx())
                )

                // High-fidelity active neon arc
                val activeColor = when (connectionState) {
                    V2ShieldVpnService.State.CONNECTED -> ConnectedGreen
                    V2ShieldVpnService.State.CONNECTING, V2ShieldVpnService.State.RECONNECTING -> ConnectingAmber
                    V2ShieldVpnService.State.DISCONNECTED -> primaryColor.copy(alpha = 0.2f)
                }

                // Breathing dynamic halo
                val breathingRadius = radius + if (connectionState == V2ShieldVpnService.State.CONNECTED) {
                    Math.sin(Math.toRadians(wavePulseState.toDouble())).toFloat() * 12f
                } else if (connectionState == V2ShieldVpnService.State.CONNECTING) {
                    Math.sin(Math.toRadians(wavePulseState.toDouble() * 3)).toFloat() * 18f
                } else {
                    0f
                }

                if (connectionState != V2ShieldVpnService.State.DISCONNECTED) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.15f),
                        radius = breathingRadius + 15f,
                        center = centerOffset
                    )
                }

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.1f),
                            activeColor,
                            activeColor.copy(alpha = 0.1f)
                        )
                    ),
                    startAngle = wavePulseState,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Central Ring Core
            Surface(
                modifier = Modifier
                    .size(175.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (connectionState == V2ShieldVpnService.State.CONNECTED) Icons.Default.Lock else Icons.Default.Refresh,
                        contentDescription = "Power",
                        tint = when (connectionState) {
                            V2ShieldVpnService.State.CONNECTED -> ConnectedGreen
                            V2ShieldVpnService.State.CONNECTING, V2ShieldVpnService.State.RECONNECTING -> ConnectingAmber
                            V2ShieldVpnService.State.DISCONNECTED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .rotate(if (connectionState == V2ShieldVpnService.State.CONNECTING) wavePulseState else 0f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (connectionState) {
                            V2ShieldVpnService.State.CONNECTED -> "قطع اتصال"
                            V2ShieldVpnService.State.CONNECTING -> "اتصال..."
                            V2ShieldVpnService.State.RECONNECTING -> "بازنشانی..."
                            V2ShieldVpnService.State.DISCONNECTED -> "بارگذاری تونل"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (connectionState == V2ShieldVpnService.State.CONNECTED) "زمان: " + formatSeconds(uptime) else "ضربه برای تغییر",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // REAL-TIME SPEEDOMETER & VOLUME METERS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speed Download
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info, // Arrow down substitute
                            contentDescription = "دانلود",
                            tint = ConnectedGreen,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(180f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سرعت دانلود", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatSpeedBytes(downloadSpeed),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ConnectedGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "حجم کل: " + formatSpeedBytes(totalReceived, false),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Speed Upload
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info, // Arrow Up
                            contentDescription = "آپلود",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سرعت آپلود", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatSpeedBytes(uploadSpeed),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "حجم کل: " + formatSpeedBytes(totalSent, false),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Encryption Indicator Widget
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "شیلد",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "امنیت ترافیک دستگاه شما اولویت ماست",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "تمام داده‌ها با پروتکل AES-256 رمزنگاری سراسری می‌شوند.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------
// TAB 2: SERVERS AND CONFIGS
// --------------------------------------------------------
@Composable
fun ServersAndConfigsTab(
    configs: List<VpnConfig>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onDelete: (VpnConfig) -> Unit,
    onImportLink: (String, () -> Unit, (String) -> Unit) -> Unit,
    onAddManual: (String, String, String, Int, String, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    var importText by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Quick Import Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "وارد کردن اشتراک یا کانکشن V2Ray",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "لینک خرید اشتراک، لینک کانفیگ vmess://، vless://، ss:// یا trojan:// را جایگذاری کنید:",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("vmess:// , https://...", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("import_field"),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Button(
                        onClick = {
                            if (importText.isEmpty()) {
                                Toast.makeText(context, "لطفاً ابتدا متنی وارد کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmitting = true
                            onImportLink(importText, {
                                isSubmitting = false
                                importText = ""
                                Toast.makeText(context, "با موفقیت بارگیری شد", Toast.LENGTH_SHORT).show()
                            }, { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            })
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("import_button"),
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("دریافت", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section Title & Add Manual Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "کانکشن‌های من",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(
                onClick = { showManualDialog = true },
                modifier = Modifier.testTag("add_manual_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "اضافه")
                Spacer(modifier = Modifier.width(4.dp))
                Text("تنظیم دستی نود", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List of configs
        if (configs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "تعطیل",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "هیچ سروری ثبت نشده است.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dedicated/Custom partition
                items(configs, key = { it.id }) { cfg ->
                    val isSelected = cfg.id == selectedId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(cfg.id) }
                            .testTag("vpn_config_${cfg.id}"),
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Flag icon simulated container
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when(cfg.countryCode) {
                                            "DE" -> "🇩🇪"
                                            "US" -> "🇺🇸"
                                            "FI" -> "🇫🇮"
                                            "GB" -> "🇬🇧"
                                            "AE" -> "🇦🇪"
                                            else -> "🌐"
                                        },
                                        fontSize = 18.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = cfg.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ) {
                                            Text(cfg.protocol, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${cfg.address}:${cfg.port}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Simulating high latency response (green vs yellow/red)
                                Text(
                                    text = if (cfg.isDedicated) "۲۴ میلی‌ثانیه" else "۴۵ میلی‌ثانیه",
                                    fontSize = 11.sp,
                                    color = ConnectedGreen,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                IconButton(
                                    onClick = { onDelete(cfg) },
                                    modifier = Modifier.testTag("delete_config_${cfg.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف نود",
                                        tint = DisconnectedRed.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Creation Setup Dialog Custom
    if (showManualDialog) {
        var name by remember { mutableStateOf("کانال فرانسه") }
        var protocol by remember { mutableStateOf("VLESS") } // VLESS VMESS TROJAN
        var address by remember { mutableStateOf("fr.v2shield.work") }
        var portString by remember { mutableStateOf("443") }
        var uuid by remember { mutableStateOf("f7f6da93-90be-4a6c-9c95-5dbd73e961fa") }
        var tls by remember { mutableStateOf(true) }
        var sni by remember { mutableStateOf("fr.v2shield.work") }

        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("ساخت نود جدید دستی V2Ray", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("نام سرور") })
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("پروتکل:", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        listOf("VLESS", "VMESS", "TROJAN").forEach { proto ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { protocol = proto }) {
                                RadioButton(selected = protocol == proto, onClick = { protocol = proto })
                                Text(proto, fontSize = 12.sp)
                            }
                        }
                    }

                    TextField(value = address, onValueChange = { address = it }, label = { Text("آدرس سرور (IP/Host)") })
                    TextField(value = portString, onValueChange = { portString = it }, label = { Text("پورت اتصال") })
                    TextField(value = uuid, onValueChange = { uuid = it }, label = { Text("شناسه کاربری (UUID / Password)") })
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tls, onCheckedChange = { tls = it })
                        Text("امنیت TLS فعال باشد", fontSize = 12.sp)
                    }

                    if (tls) {
                        TextField(value = sni, onValueChange = { sni = it }, label = { Text("آدرس SNI (اختیاری)") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val portInt = portString.toIntOrNull() ?: 443
                        onAddManual(name, protocol, address, portInt, uuid, tls, sni)
                        showManualDialog = false
                    },
                    modifier = Modifier.testTag("save_manual_node")
                ) {
                    Text("ذخیره نود")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("انصراف") }
            }
        )
    }
}

// --------------------------------------------------------
// TAB 3: SPLIT TUNNEL (MANAGED SPLIT)
// --------------------------------------------------------
@Composable
fun SplitTunnelTab(
    viewModel: VpnViewModel
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val installedApps by viewModel.installedApps.collectAsState()

    // Query installed app lists once loaded
    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context.packageManager)
    }

    val filteredList = remember(installedApps, searchQuery) {
        if (searchQuery.trim().isEmpty()) installedApps
        else installedApps.filter { it.name.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Explanatory Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "تفکیک اپلیکیشن‌های اندروید (Split Tunneling)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "در این قسمت می‌توانید برنامه‌هایی که نیاز دارید حتما از فیلتر عبور داده شوند (یا بالعکس) را تیک بزنید. مابقی برنامه‌ها بدون تونل‌گذاری متصل باقی خواهند ماند.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجوی نام اپلیکیشن...", fontSize = 12.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "سرچ") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search_field"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Applications List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("اپلیکیشنی پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredList, key = { it.packageName }) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                               )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = app.packageName,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = app.isAllowed,
                                onCheckedChange = { viewModel.toggleAppInSplitTunnel(app.packageName) },
                                modifier = Modifier.testTag("app_toggle_${app.packageName}"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------
// TAB 4: ANALYTICS & SYSTEM CONNECTIONS LOGS
// --------------------------------------------------------
@Composable
fun LogsAndAnalyticsTab(
    usageHistory: List<DataUsageRecord>,
    logs: List<VpnLog>,
    onClearLogs: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Chart, 1 = Realtime Terminal Routing Logs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sub Navigation Menu
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = activeSubTab == 0, onClick = { activeSubTab = 0 }) {
                Text("تحلیل ترافیک مصرفی", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = activeSubTab == 1, onClick = { activeSubTab = 1 }) {
                Text("گزارش ترافیک زنده و سیستم", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeSubTab == 0) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Total bytes calculations
                val sumReceived = usageHistory.sumOf { it.bytesReceived }
                val sumSent = usageHistory.sumOf { it.bytesSent }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "مجموع ترافیک مصرفی دوره (۳۰ ساعت گذشته)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("کل دریافت (دانلود)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatSpeedBytes(sumReceived, false), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ConnectedGreen)
                            }
                            Column {
                                Text("کل ارسال (آپلود)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatSpeedBytes(sumSent, false), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GORGEOUS SYSTEM GRAPH CANVAS (Bezier Graph)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("میزان تغییرات ترافیک در گذر زمان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        ) {
                            if (usageHistory.isEmpty()) {
                                Text("اطلاعات ترافیکی کافی نیست.", modifier = Modifier.align(Alignment.Center), fontSize = 12.sp)
                            } else {
                                val chartPoints = usageHistory.reversed().take(10)
                                val maxVal = (chartPoints.maxOfOrNull { it.bytesReceived + it.bytesSent } ?: 1L).coerceAtLeast(1L)
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val secondaryColor = ConnectedGreen

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height
                                    val stepX = width / (chartPoints.size - 1).coerceAtLeast(1)

                                    val points = chartPoints.mapIndexed { idx, value ->
                                        val x = idx * stepX
                                        val totalVolume = value.bytesReceived + value.bytesSent
                                        val y = height - (totalVolume.toFloat() / maxVal.toFloat() * (height - 30.dp.toPx())) - 15.dp.toPx()
                                        Offset(x, y)
                                    }

                                    // Draw background grids
                                    val lineCount = 4
                                    for (i in 0..lineCount) {
                                        val yG = (height / lineCount) * i
                                        drawLine(
                                            color = Color.Gray.copy(alpha = 0.1f),
                                            start = Offset(0f, yG),
                                            end = Offset(width, yG),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }

                                    // Draw path line
                                    val path = androidx.compose.ui.graphics.Path()
                                    if (points.isNotEmpty()) {
                                        path.moveTo(points[0].x, points[0].y)
                                        for (i in 1 until points.size) {
                                            // Quadratic curve simulation
                                            val prev = points[i - 1]
                                            val curr = points[i]
                                            path.quadraticTo(
                                                (prev.x + curr.x) / 2f, prev.y,
                                                curr.x, curr.y
                                            )
                                        }

                                        drawPath(
                                            path = path,
                                            color = primaryColor,
                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                        )

                                        // Draw points circle
                                        points.forEach { pt ->
                                            drawCircle(color = secondaryColor, radius = 5.dp.toPx(), center = pt)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Logs Terminal
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ترمینال رویدادها", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_btn")) {
                        Text("پاک‌کردن کنسول", fontSize = 12.sp, color = DisconnectedRed)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(Color.Black)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            "[V2Shield] منتظر دریافت بسته‌‌ها و سیگنال‌های تونل...",
                            fontFamily = FontFamily.Monospace,
                            color = ConnectedGreen.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logs) { log ->
                                val colorText = when (log.level) {
                                    "ERROR" -> DisconnectedRed
                                    "WARN" -> ConnectingAmber
                                    else -> Color.Green
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "[${log.tag}] ",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = log.message,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorText,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------
// TAB 5: SETTINGS & SECURITY
// --------------------------------------------------------
@Composable
fun SettingsAndSecurityTab(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onBackup: ((String) -> Unit) -> Unit,
    onRestore: (String, () -> Unit, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var securePinInput by remember { mutableStateOf("") }
    var restoreTextInput by remember { mutableStateOf("") }

    // Dialog flags
    var showBackupDialog by remember { mutableStateOf(false) }
    var generatedBackupStr by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showTotpDialog by remember { mutableStateOf(false) }

    // Simulated dynamic 2FA variables
    var currentTotpTimer by remember { mutableStateOf(30) }
    var simulatedTotpCode by remember { mutableStateOf("492 881") }

    LaunchedEffect(showTotpDialog) {
        if (showTotpDialog) {
            while (true) {
                delay(1000)
                if (currentTotpTimer > 1) {
                    currentTotpTimer--
                } else {
                    currentTotpTimer = 30
                    simulatedTotpCode = (100_000..999_999).random().toString().let {
                        "${it.substring(0,3)} ${it.substring(3)}"
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // VPN PERSONALIZATION & DNS CONFIG
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "تنظیمات فنی و شخصی‌سازی دی‌ان‌اس",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Kill Switch Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("قابلیت سوئیچ قطع کلی (Kill Switch)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("در صورت افت سیگنال اتصال، اتصال کل اینترنت گوشی موقتا بلاک می‌شود.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.enableKillSwitch,
                        onCheckedChange = { onUpdateSettings(settings.copy(enableKillSwitch = it)) },
                        modifier = Modifier.testTag("kill_switch_toggle")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // DNS Selector
                Column {
                    Text("آدرس دی‌ان‌اس (DNS Server)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("سرور دی‌ان‌اس برای حل باگ های فیلترینگ و روتینگ ایمن وب‌سایت ها:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    val dnsOptions = listOf(
                        "Cloudflare (1.1.1.1)" to "1.1.1.1",
                        "Google (8.8.8.8)" to "8.8.8.8",
                        "AdGuard Family" to "77.88.8.8",
                        "تنظیم دستی دی‌ان‌اس" to "Custom"
                    )

                    dnsOptions.forEach { (label, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (value == "Custom") {
                                        onUpdateSettings(settings.copy(dnsMode = "Custom"))
                                    } else {
                                        onUpdateSettings(settings.copy(dnsMode = value))
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            var isSelected = if (value == "Custom") settings.dnsMode == "Custom" else settings.dnsMode == value
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (value == "Custom") {
                                        onUpdateSettings(settings.copy(dnsMode = "Custom"))
                                    } else {
                                        onUpdateSettings(settings.copy(dnsMode = value))
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, fontSize = 12.sp)
                        }
                    }

                    if (settings.dnsMode == "Custom") {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = settings.customDns,
                            onValueChange = { onUpdateSettings(settings.copy(customDns = it)) },
                            placeholder = { Text("مثلاً: 1.1.1.3", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                }
            }
        }

        // TWO-FACTOR AUTHENTICATION (2FA) & LOCKS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "امنیت ورود و احراز هویت دو مرحله‌ای (2FA)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("فعالسازی سیستم عبور تصویری/کد عبور ورود", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("محافظت از اپلیکیشن در برابر دسترسی‌های غیرمجاز", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.enableBiometricOrPin,
                        onCheckedChange = { onUpdateSettings(settings.copy(enableBiometricOrPin = it)) },
                        modifier = Modifier.testTag("pin_code_toggle")
                    )
                }

                if (settings.enableBiometricOrPin) {
                    TextField(
                        value = settings.securePin,
                        onValueChange = { onUpdateSettings(settings.copy(securePin = it)) },
                        placeholder = { Text("کد ورود (۴ رقمی عددی)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("فعال‌سازی سیستم احراز هویت دو مرحله‌ای (2FA)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("همگام‌سازی توکن با برنامه‌های Google Authenticator", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.enable2FA,
                        onCheckedChange = {
                            val nextSec = if (it) "JBSWY3DPEHPK3PXP" else ""
                            onUpdateSettings(settings.copy(enable2FA = it, totpSecret = nextSec))
                        },
                        modifier = Modifier.testTag("totp_2fa_toggle")
                    )
                }

                if (settings.enable2FA) {
                    // Show View Code Button
                    Button(
                        onClick = { showTotpDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("view_2fa_btn")
                    ) {
                        Text("مشاهده کد تایید دو مرحله‌ای زنده", fontSize = 12.sp)
                    }
                }
            }
        }

        // CONF BACKUP & RESTORE / CLOUD SYNC
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "پشتیبان‌گیری از تنظیمات و همگام‌سازی ابری",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Cloud Sync Emulator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("همگام‌سازی ابری چند دستگاهی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("تغییرات شما به صورت خودکار با سرور مرکزی امن همگام می‌شود.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.isCloudSyncEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(isCloudSyncEnabled = it, cloudSyncEmail = if (it) "user@v2shield.pro" else "")) },
                        modifier = Modifier.testTag("cloud_sync_toggle")
                    )
                }

                if (settings.isCloudSyncEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ایمیل همگام‌سازی شده:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(settings.cloudSyncEmail, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text("پشتیبان‌گیری محلی آفلاین", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onBackup { backup ->
                                generatedBackupStr = backup
                                clipboardManager.setText(AnnotatedString(backup))
                                showBackupDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_export_btn")
                    ) {
                        Text("تهیه پشتیبان (Backup)", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_import_btn")
                    ) {
                        Text("بازیابی تنظیمات (Restore)", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // Backup Export Dialog View
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("کد بک‌آپ تولید شد", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "کد بک‌آپ پیکربندی‌های شما به شرح ذیل است. این کد با موفقیت در کلیپ‌بورد کپی شد:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = generatedBackupStr,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBackupDialog = false }) { Text("تایید") }
            }
        )
    }

    // Backup Restore Dialog Input View
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("بازیابی اطلاعات بک‌آپ", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "لطفاً کد کپی شده بک‌آپ قبلی را در کادر زیر جایگذاری کنید:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = restoreTextInput,
                        onValueChange = { restoreTextInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("restore_input_area"),
                        placeholder = { Text("Base64 Backup Token...", fontSize = 10.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreTextInput.trim().isEmpty()) return@Button
                        onRestore(restoreTextInput.trim(), {
                            showRestoreDialog = false
                            restoreTextInput = ""
                            Toast.makeText(context, "اطلاعات با موفقیت بازیابی شد.", Toast.LENGTH_SHORT).show()
                        }, {
                            Toast.makeText(context, "کد بک‌آپ معتبر نیست.", Toast.LENGTH_SHORT).show()
                        })
                    },
                    modifier = Modifier.testTag("restore_confirm_btn")
                ) {
                    Text("انجام بازیابی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("انصراف") }
            }
        )
    }

    // 2FA TOTP Token View Dialog
    if (showTotpDialog) {
        AlertDialog(
            onDismissRequest = { showTotpDialog = false },
            title = { Text("رمز امن دو مرحله‌ای فعال V2Shield", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "رمز ۶ رقمی احراز هویت متصل به نرم افزار Google Authenticator:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = simulatedTotpCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Linear Timer Progress Loader
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { currentTotpTimer / 30f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "مدت پس‌ماند اعتبار کد عبور: $currentTotpTimer ثانیه",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showTotpDialog = false }) { Text("بستن پنجره") }
            }
        )
    }
}
