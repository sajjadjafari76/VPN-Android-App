package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_configs")
data class VpnConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String, // VMESS, VLESS, TROJAN, SHADOWSOCKS
    val address: String,
    val port: Int,
    val uuid: String,
    val security: String = "auto",
    val network: String = "tcp",
    val sni: String = "",
    val path: String = "",
    val tls: Boolean = false,
    val isSubscription: Boolean = false,
    val subscriptionUrl: String = "",
    val isDedicated: Boolean = false,
    val countryCode: String = "US",
    val latency: Int = -1,
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "data_usage")
data class DataUsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // Start epoch of hour/day
    val bytesSent: Long,
    val bytesReceived: Long,
    val durationSeconds: Long
)

@Entity(tableName = "vpn_logs")
data class VpnLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String, // SYSTEM, ROUTER, CONNECTION
    val message: String,
    val level: String = "INFO" // INFO, WARN, ERROR
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val isDarkTheme: Boolean = true,
    val useSystemTheme: Boolean = false,
    val enableKillSwitch: Boolean = false,
    val allowedApps: String = "", // Comma-separated package names
    val dnsMode: String = "1.1.1.1", // Cloudflare, Google, AdGuard, Custom
    val customDns: String = "",
    val encryptionType: String = "AES-256-GCM", // ChaCha20-Poly1305, AES-128-GCM
    val enableBiometricOrPin: Boolean = false,
    val securePin: String = "",
    val enable2FA: Boolean = false,
    val totpSecret: String = "",
    val isCloudSyncEnabled: Boolean = false,
    val cloudSyncEmail: String = "",
    val autoReconnect: Boolean = true
)
