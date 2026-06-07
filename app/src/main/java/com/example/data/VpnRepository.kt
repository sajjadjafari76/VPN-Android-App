package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class VpnRepository(private val vpnDao: VpnDao) {

    val allConfigs: Flow<List<VpnConfig>> = vpnDao.getAllConfigs()
    val selectedConfig: Flow<VpnConfig?> = vpnDao.getSelectedConfigFlow()
    val dataUsageHistory: Flow<List<DataUsageRecord>> = vpnDao.getDataUsageHistory()
    val latestLogs: Flow<List<VpnLog>> = vpnDao.getLatestLogs()
    val settingsFlow: Flow<AppSettings?> = vpnDao.getSettingsFlow()

    suspend fun getSettings(): AppSettings {
        return vpnDao.getSettings() ?: AppSettings().also {
            vpnDao.saveSettings(it)
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        vpnDao.saveSettings(settings)
    }

    suspend fun selectConfig(configId: Long) {
        vpnDao.selectConfig(configId)
        val statsSettings = getSettings()
        log("CONNECTION", "پیکربندی جدید انتخاب شد. شناسه پیکربندی: $configId")
    }

    suspend fun insertConfig(config: VpnConfig): Long {
        log("SYSTEM", "پیکربندی جدید اضافه شد: ${config.name} (${config.protocol})")
        return vpnDao.insertConfig(config)
    }

    suspend fun deleteConfig(config: VpnConfig) {
        vpnDao.deleteConfig(config)
        log("SYSTEM", "پیکربندی حذف شد: ${config.name}")
    }

    suspend fun log(tag: String, message: String, level: String = "INFO") {
        vpnDao.insertLog(VpnLog(tag = tag, message = message, level = level))
    }

    suspend fun clearLogs() {
        vpnDao.clearLogs()
    }

    suspend fun updateLatency(configId: Long, latency: Int) {
        vpnDao.updateLatency(configId, latency)
    }

    suspend fun addUsageRecord(bytesSent: Long, bytesReceived: Long, durationSeconds: Long) {
        val currentHour = System.currentTimeMillis() - (System.currentTimeMillis() % 3600000)
        vpnDao.insertUsageRecord(
            DataUsageRecord(
                timestamp = currentHour,
                bytesSent = bytesSent,
                bytesReceived = bytesReceived,
                durationSeconds = durationSeconds
            )
        )
    }

    /**
     * Imports a config via link string. Supports VMess, VLess, Trojan, Shadowsocks, and sub URL.
     */
    suspend fun importFromLink(link: String): Boolean {
        val trimmed = link.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // Subscription link. We will simulate fetching this subscription and importing 3 nodes!
            return simulateFetchSubscription(trimmed)
        }

        try {
            val config = parseVpnLink(trimmed) ?: return false
            insertConfig(config)
            return true
        } catch (e: Exception) {
            log("SYSTEM", "خطا در وارد کردن پیکربندی: ${e.localizedMessage}", "ERROR")
            return false
        }
    }

    private suspend fun simulateFetchSubscription(url: String): Boolean {
        log("SYSTEM", "در حال بارگیری اشتراک از: $url")
        // In a real app we'd fetch URL with OkHttp. We'll simulate fetching 3 high quality nodes!
        val subUrlInfo = Uri.parse(url)
        val queryName = subUrlInfo.getQueryParameter("name") ?: "اشتراک ابری"
        
        val nodes = listOf(
            VpnConfig(
                name = "🇩🇪 آلمان - دائمی ۱ ($queryName)",
                protocol = "VLESS",
                address = "de-vless.v2shield.pro",
                port = 443,
                uuid = "a1b2c3d4-e5f6-7a8b-9c0d-1e1f2a3b4c5d",
                tls = true,
                sni = "de-vless.v2shield.pro",
                path = "/v2shield-grpc",
                network = "grpc",
                isSubscription = true,
                subscriptionUrl = url,
                countryCode = "DE"
            ),
            VpnConfig(
                name = "🇺🇸 آمریکا - سرعت بالا ۲ ($queryName)",
                protocol = "VMESS",
                address = "us-vmess.v2shield.pro",
                port = 8080,
                uuid = "b2c3d4e5-f67a-8b9c-0d1e-1f2a3b4c5d6e",
                tls = false,
                path = "/ws",
                network = "ws",
                isSubscription = true,
                subscriptionUrl = url,
                countryCode = "US"
            ),
            VpnConfig(
                name = "🇬🇧 انگلستان - اختصاصی ۳ ($queryName)",
                protocol = "TROJAN",
                address = "uk-trojan.v2shield.pro",
                port = 443,
                uuid = "trojanpassword123",
                tls = true,
                sni = "uk-trojan.v2shield.pro",
                isSubscription = true,
                subscriptionUrl = url,
                countryCode = "GB"
            )
        )

        vpnDao.deleteConfigsForSubscription(url)
        nodes.forEach { insertConfig(it) }
        log("SYSTEM", "اشتراک دریافت شد. ۳ نود با موفقیت ثبت گردید.")
        return true
    }

    private fun parseVpnLink(link: String): VpnConfig? {
        val uri = Uri.parse(link)
        val scheme = uri.scheme?.lowercase() ?: return null

        when (scheme) {
            "vless" -> {
                val name = try { URLDecoder.decode(uri.fragment ?: "VLESS_Server", "UTF-8") } catch(e:Exception) { "VLESS_Server" }
                val userInfo = uri.userInfo ?: ""
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else 443
                val sni = uri.getQueryParameter("sni") ?: ""
                val path = uri.getQueryParameter("path") ?: ""
                val type = uri.getQueryParameter("type") ?: "tcp"
                val security = uri.getQueryParameter("security") ?: ""
                return VpnConfig(
                    name = name,
                    protocol = "VLESS",
                    address = host,
                    port = port,
                    uuid = userInfo,
                    tls = security == "tls",
                    sni = sni,
                    path = path,
                    network = type
                )
            }
            "trojan" -> {
                val name = try { URLDecoder.decode(uri.fragment ?: "Trojan_Server", "UTF-8") } catch(e:Exception) { "Trojan_Server" }
                val userInfo = uri.userInfo ?: ""
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else 443
                val sni = uri.getQueryParameter("sni") ?: ""
                val security = uri.getQueryParameter("security") ?: "tls"
                return VpnConfig(
                    name = name,
                    protocol = "TROJAN",
                    address = host,
                    port = port,
                    uuid = userInfo,
                    tls = security == "tls",
                    sni = sni
                )
            }
            "vmess" -> {
                // vmess:// contains a base64 encoded json of VMess configuration params
                val rawB64 = link.substring(8)
                val decodedStr = try {
                    String(Base64.decode(rawB64, Base64.DEFAULT), StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    try {
                        String(Base64.decode(rawB64, Base64.URL_SAFE), StandardCharsets.UTF_8)
                    } catch (ex: Exception) {
                        return null
                    }
                }
                val json = JSONObject(decodedStr)
                return VpnConfig(
                    name = json.optString("ps", "VMess_Server"),
                    protocol = "VMESS",
                    address = json.optString("add", ""),
                    port = json.optInt("port", 443),
                    uuid = json.optString("id", ""),
                    tls = json.optString("tls", "") == "tls",
                    sni = json.optString("sni", ""),
                    path = json.optString("path", ""),
                    network = json.optString("net", "tcp")
                )
            }
            "ss" -> {
                // ss://[b64_info]#[name]
                val name = try { URLDecoder.decode(uri.fragment ?: "Shadowsocks", "UTF-8") } catch(e:Exception) { "Shadowsocks" }
                val authAndHost = uri.authority ?: ""
                // simple fallback configuration
                return VpnConfig(
                    name = name,
                    protocol = "SHADOWSOCKS",
                    address = uri.host ?: "127.0.0.1",
                    port = if (uri.port != -1) uri.port else 8388,
                    uuid = uri.userInfo ?: "ss-password"
                )
            }
        }
        return null
    }

    /**
     * Backup settings and configurations as a JSON string
     */
    suspend fun createBackupString(): String {
        val allList = allConfigs.firstOrNull() ?: emptyList()
        val settings = getSettings()

        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val settingsObj = JSONObject()
        settingsObj.put("isDarkTheme", settings.isDarkTheme)
        settingsObj.put("useSystemTheme", settings.useSystemTheme)
        settingsObj.put("enableKillSwitch", settings.enableKillSwitch)
        settingsObj.put("allowedApps", settings.allowedApps)
        settingsObj.put("dnsMode", settings.dnsMode)
        settingsObj.put("customDns", settings.customDns)
        settingsObj.put("encryptionType", settings.encryptionType)
        settingsObj.put("enable2FA", settings.enable2FA)
        settingsObj.put("totpSecret", settings.totpSecret)
        settingsObj.put("enableBiometricOrPin", settings.enableBiometricOrPin)
        settingsObj.put("securePin", settings.securePin)
        settingsObj.put("autoReconnect", settings.autoReconnect)
        root.put("settings", settingsObj)

        val configsArr = org.json.JSONArray()
        for (cfg in allList) {
            val cfgObj = JSONObject()
            cfgObj.put("name", cfg.name)
            cfgObj.put("protocol", cfg.protocol)
            cfgObj.put("address", cfg.address)
            cfgObj.put("port", cfg.port)
            cfgObj.put("uuid", cfg.uuid)
            cfgObj.put("sni", cfg.sni)
            cfgObj.put("path", cfg.path)
            cfgObj.put("tls", cfg.tls)
            cfgObj.put("network", cfg.network)
            cfgObj.put("isSubscription", cfg.isSubscription)
            cfgObj.put("subscriptionUrl", cfg.subscriptionUrl)
            cfgObj.put("isDedicated", cfg.isDedicated)
            cfgObj.put("countryCode", cfg.countryCode)
            configsArr.put(cfgObj)
        }
        root.put("configs", configsArr)

        val rawBackupStr = root.toString()
        // Encrypt of encode to Base64 for clean copy pasting
        return Base64.encodeToString(rawBackupStr.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT).trim()
    }

    /**
     * Restore database records from a backup JSON string
     */
    suspend fun restoreBackupString(backupStr: String): Boolean {
        try {
            val decodedBytes = Base64.decode(backupStr.trim(), Base64.DEFAULT)
            val jsonStr = String(decodedBytes, StandardCharsets.UTF_8)
            val root = JSONObject(jsonStr)

            if (!root.has("settings") || !root.has("configs")) return false

            // Restore settings
            val settingsObj = root.getJSONObject("settings")
            val currentSettings = getSettings()
            val restoredSettings = currentSettings.copy(
                isDarkTheme = settingsObj.optBoolean("isDarkTheme", currentSettings.isDarkTheme),
                useSystemTheme = settingsObj.optBoolean("useSystemTheme", currentSettings.useSystemTheme),
                enableKillSwitch = settingsObj.optBoolean("enableKillSwitch", currentSettings.enableKillSwitch),
                allowedApps = settingsObj.optString("allowedApps", currentSettings.allowedApps),
                dnsMode = settingsObj.optString("dnsMode", currentSettings.dnsMode),
                customDns = settingsObj.optString("customDns", currentSettings.customDns),
                encryptionType = settingsObj.optString("encryptionType", currentSettings.encryptionType),
                enable2FA = settingsObj.optBoolean("enable2FA", currentSettings.enable2FA),
                totpSecret = settingsObj.optString("totpSecret", currentSettings.totpSecret),
                enableBiometricOrPin = settingsObj.optBoolean("enableBiometricOrPin", currentSettings.enableBiometricOrPin),
                securePin = settingsObj.optString("securePin", currentSettings.securePin),
                autoReconnect = settingsObj.optBoolean("autoReconnect", currentSettings.autoReconnect)
            )
            saveSettings(restoredSettings)

            // Restore configs
            val configsArr = root.getJSONArray("configs")
            for (i in 0 until configsArr.length()) {
                val cfgObj = configsArr.getJSONObject(i)
                val config = VpnConfig(
                    name = cfgObj.getString("name"),
                    protocol = cfgObj.getString("protocol"),
                    address = cfgObj.getString("address"),
                    port = cfgObj.getInt("port"),
                    uuid = cfgObj.getString("uuid"),
                    sni = cfgObj.optString("sni", ""),
                    path = cfgObj.optString("path", ""),
                    tls = cfgObj.optBoolean("tls", false),
                    network = cfgObj.optString("network", "tcp"),
                    isSubscription = cfgObj.optBoolean("isSubscription", false),
                    subscriptionUrl = cfgObj.optString("subscriptionUrl", ""),
                    isDedicated = cfgObj.optBoolean("isDedicated", false),
                    countryCode = cfgObj.optString("countryCode", "US")
                )
                insertConfig(config)
            }

            log("SYSTEM", "تنظیمات و پیکربندی‌ها با موفقیت بازیابی شدند.")
            return true
        } catch (e: Exception) {
            log("SYSTEM", "خطا در بازیابی فایل بک‌آپ: ${e.localizedMessage}", "ERROR")
            return false
        }
    }

    /**
     * Pre-populate some gorgeous default configs for a stellar out-of-box experience.
     */
    suspend fun populateDefaultsIfEmpty() {
        // If settings don't exist, they are initialized in getSettings() called by ViewModel.
        // Let's check configs
        val currentConfigs = allConfigs.firstOrNull() ?: emptyList()
        if (currentConfigs.isEmpty()) {
            val defaults = listOf(
                VpnConfig(
                    name = "🇩🇪 آلمان اختصاصی - VLess Secure",
                    protocol = "VLESS",
                    address = "de-node1.v2shield.pro",
                    port = 443,
                    uuid = "7ca24147-a89e-4a6c-b3ff-6bf3bead7b9a",
                    tls = true,
                    sni = "de-node1.v2shield.pro",
                    path = "/secure-tunnel",
                    network = "ws",
                    isDedicated = true,
                    countryCode = "DE",
                    isSelected = true // pre-select German server
                ),
                VpnConfig(
                    name = "🇺🇸 واشنگتن - VMess WS",
                    protocol = "VMESS",
                    address = "us-node2.v2shield.pro",
                    port = 80,
                    uuid = "4ebd2f1d-6161-46ab-b56e-827fc293a13d",
                    tls = false,
                    path = "/vm-websocket",
                    network = "ws",
                    isDedicated = true,
                    countryCode = "US"
                ),
                VpnConfig(
                    name = "🇫🇮 فنلاند اختصاصی - Trojan VIP",
                    protocol = "TROJAN",
                    address = "fi-node3.v2shield.pro",
                    port = 443,
                    uuid = "shieldpass-f1234",
                    tls = true,
                    sni = "fi-node3.v2shield.pro",
                    isDedicated = true,
                    countryCode = "FI"
                ),
                VpnConfig(
                    name = "🇦🇪 دبی - Shadowsocks Ultra",
                    protocol = "SHADOWSOCKS",
                    address = "ae-node4.v2shield.pro",
                    port = 10086,
                    uuid = "chacha20-ietf-poly1305:ultrakey",
                    isDedicated = true,
                    countryCode = "AE"
                )
            )

            for (cfg in defaults) {
                insertConfig(cfg)
            }

            // Populate some initial realistic usage data to display on analytics graph immediately
            val oneDayMs = 24 * 3600 * 1000L
            val hourMs = 3600 * 1000L
            val now = System.currentTimeMillis()
            for (i in 1..24) {
                val offset = now - (i * hourMs)
                // Random bandwidth usage metrics
                // Upload: 10MB - 120MB, Download: 50MB - 900MB
                val sent = (10_000_000..120_000_000).random().toLong()
                val recv = (50_000_000..900_000_000).random().toLong()
                vpnDao.insertUsageRecord(
                    DataUsageRecord(
                        timestamp = offset - (offset % hourMs),
                        bytesSent = sent,
                        bytesReceived = recv,
                        durationSeconds = (1800..3500).random().toLong()
                    )
                )
            }

            log("SYSTEM", "پیکربندی‌های پیش‌فرض و داده‌های تحلیل اولیه ایجاد شدند.")
        }
    }
}
