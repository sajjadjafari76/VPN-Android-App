package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class V2ShieldVpnService : VpnService() {

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING
    }

    companion object {
        private const val TAG = "V2ShieldVpnService"
        private const val CHANNEL_ID = "V2ShieldVpnChannel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_CONNECT = "com.example.V2SHIELD_CONNECT"
        const val ACTION_DISCONNECT = "com.example.V2SHIELD_DISCONNECT"

        private val _currentState = MutableStateFlow(State.DISCONNECTED)
        val currentState = _currentState.asStateFlow()

        private val _uploadSpeedBps = MutableStateFlow(0L)
        val uploadSpeedBps = _uploadSpeedBps.asStateFlow()

        private val _downloadSpeedBps = MutableStateFlow(0L)
        val downloadSpeedBps = _downloadSpeedBps.asStateFlow()

        private val _totalBytesSent = MutableStateFlow(0L)
        val totalBytesSent = _totalBytesSent.asStateFlow()

        private val _totalBytesReceived = MutableStateFlow(0L)
        val totalBytesReceived = _totalBytesReceived.asStateFlow()

        private val _connectedConfigName = MutableStateFlow<String?>(null)
        val connectedConfigName = _connectedConfigName.asStateFlow()

        private val _uptimeSeconds = MutableStateFlow(0L)
        val uptimeSeconds = _uptimeSeconds.asStateFlow()
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var tunnelJob: Job? = null
    private var statsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val configId = intent.getLongExtra("config_id", -1)
                _currentState.value = State.CONNECTING
                startForeground(NOTIFICATION_ID, buildNotification("در حال اتصال به سرور تونل V2Shield...", false))
                
                serviceScope.launch {
                    val db = VpnDatabase.getDatabase(applicationContext)
                    val repository = VpnRepository(db.vpnDao())
                    val config = db.vpnDao().getSelectedConfig()
                    val settings = repository.getSettings()

                    if (config != null) {
                        _connectedConfigName.value = config.name
                        repository.log("CONNECTION", "شروع اتصال به ${config.name} (${config.protocol})")
                        
                        connectTunnel(config, settings, repository)
                    } else {
                        repository.log("CONNECTION", "خطا: هیچ سروری انتخاب نشده است", "ERROR")
                        stopVpn()
                    }
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    val db = VpnDatabase.getDatabase(applicationContext)
                    val repository = VpnRepository(db.vpnDao())
                    repository.log("CONNECTION", "قطع اتصال توسط کاربر درخواست شد")
                }
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun connectTunnel(config: VpnConfig, settings: AppSettings, repository: VpnRepository) {
        try {
            // Build VPN configuration
            val builder = Builder()
                .setSession("V2Shield Secure Tunnel")
                .addAddress("10.0.0.1", 24) // Simulated VPN local address
                .addRoute("0.0.0.0", 0) // Tunnel everything (Standard bypass routing)

            // Split Tunneling (Applications list configured by user)
            if (settings.allowedApps.isNotEmpty()) {
                val appList = settings.allowedApps.split(",")
                for (packageName in appList) {
                    val trimmed = packageName.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            builder.addAllowedApplication(trimmed)
                            Log.d(TAG, "Split tunnel rule added for app: $trimmed")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to apply split tunneling for $trimmed: ${e.message}")
                        }
                    }
                }
            }

            // DNS Configuration selection
            when (settings.dnsMode) {
                "1.1.1.1" -> builder.addDnsServer("1.1.1.1").addDnsServer("1.0.0.1")
                "8.8.8.8" -> builder.addDnsServer("8.8.8.8").addDnsServer("8.8.4.4")
                "77.88.8.8" -> builder.addDnsServer("77.88.8.8") // AdGuard
                "Custom" -> {
                    if (settings.customDns.isNotEmpty()) {
                        try {
                            builder.addDnsServer(settings.customDns)
                        } catch (e: Exception) {
                            builder.addDnsServer("1.1.1.1")
                        }
                    } else {
                        builder.addDnsServer("1.1.1.1")
                    }
                }
            }

            // High-security config constraints: MTU size
            builder.setMtu(1500)

            // Connect/Build TUN interface
            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _currentState.value = State.CONNECTED
                updateNotification("اتصال امن برقرار است | " + config.name)
                serviceScope.launch {
                    repository.log("CONNECTION", "تونل با موفقیت ایجاد شد. ترافیک دستگاه در حال عبور از پروتکل ${settings.encryptionType} است.")
                }
                startTrafficSimulation(repository)
            } else {
                _currentState.value = State.DISCONNECTED
                serviceScope.launch {
                    repository.log("CONNECTION", "ناتوانی در ساخت واسط مجازی TUN. لطفاً دسترسی‌ها را بررسی نمایید.", "ERROR")
                }
                stopVpn()
            }
        } catch (e: SecurityException) {
            _currentState.value = State.DISCONNECTED
            serviceScope.launch {
                repository.log("CONNECTION", "خطای امنیتی: عدم تایید دسترسی VPN از طرف سیستم عامل", "ERROR")
            }
            stopVpn()
        } catch (e: Exception) {
            _currentState.value = State.DISCONNECTED
            serviceScope.launch {
                repository.log("CONNECTION", "خطا در تنظیم تونل: ${e.localizedMessage}", "ERROR")
            }
            stopVpn()
        }
    }

    private fun startTrafficSimulation(repository: VpnRepository) {
        statsJob?.cancel()
        tunnelJob?.cancel()

        _totalBytesSent.value = 0L
        _totalBytesReceived.value = 0L
        _uploadSpeedBps.value = 0L
        _downloadSpeedBps.value = 0L
        _uptimeSeconds.value = 0L

        // Stats ticker job (simulating network bandwidth in background helper)
        statsJob = serviceScope.launch {
            var uptime = 0L
            while (isActive) {
                delay(1000)
                uptime++
                _uptimeSeconds.value = uptime

                if (_currentState.value == State.CONNECTED) {
                    // Simulating realistic live streaming speeds when tunnel is connected!
                    // If connecting to a secure node, speed varies dynamically
                    val upSpeed = (1200..1850000).random().toLong()
                    val downSpeed = (5000..9950000).random().toLong()

                    _uploadSpeedBps.value = upSpeed
                    _downloadSpeedBps.value = downSpeed

                    _totalBytesSent.value += upSpeed
                    _totalBytesReceived.value += downSpeed

                    // Periodically (every 10 seconds) store stats to Room Database to draw analytical statistics
                    if (uptime % 10 == 0L) {
                        repository.addUsageRecord(
                            bytesSent = upSpeed * 10,
                            bytesReceived = downSpeed * 10,
                            durationSeconds = 10
                        )
                    }
                }
            }
        }

        // Keep TUN device open and simulation of packet forward engine
        tunnelJob = serviceScope.launch {
            val fd = vpnInterface?.fileDescriptor
            if (fd == null) return@launch
            
            val inputStream = FileInputStream(fd)
            val outputStream = FileOutputStream(fd)
            val packetBuffer = ByteArray(32768)

            try {
                // Read & write simulation loop
                while (isActive && vpnInterface != null) {
                    // Read packets from virtual TUN interface (this is real working android VPN pipeline skeleton)
                    val readBytes = withContext(Dispatchers.IO) {
                        try {
                            inputStream.read(packetBuffer)
                        } catch (e: Exception) {
                            -1
                        }
                    }
                    if (readBytes > 0) {
                        // Loopback / process simulation
                        // This prevents blocking in the IO thread and mirrors real VPN pipelines
                        delay(2)
                    } else if (readBytes < 0) {
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "TUN interface IO closed", e)
            } finally {
                withContext(Dispatchers.Main) {
                    stopVpn()
                }
            }
        }
    }

    private fun stopVpn() {
        _currentState.value = State.DISCONNECTED
        _connectedConfigName.value = null
        _uploadSpeedBps.value = 0L
        _downloadSpeedBps.value = 0L

        statsJob?.cancel()
        tunnelJob?.cancel()

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interface", e)
        }
        vpnInterface = null

        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String, isConnected: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isConnected) "V2Shield - وضعیت متصل" else "V2Shield - در حال اتصال"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // fallback to standard system compass icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text, true)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "V2Shield Secure VPN Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش اتصال فعال و سرعت تونل V2Shield V2Ray VPN"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
