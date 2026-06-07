package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VpnViewModel(private val repository: VpnRepository) : ViewModel() {

    // Global connection state from Service flows
    val connectionState = V2ShieldVpnService.currentState
    val activeNodeName = V2ShieldVpnService.connectedConfigName
    val uploadSpeedBps = V2ShieldVpnService.uploadSpeedBps
    val downloadSpeedBps = V2ShieldVpnService.downloadSpeedBps
    val totalBytesSent = V2ShieldVpnService.totalBytesSent
    val totalBytesReceived = V2ShieldVpnService.totalBytesReceived
    val uptimeSeconds = V2ShieldVpnService.uptimeSeconds

    // Local DB state
    val allConfigs = repository.allConfigs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val selectedConfig = repository.selectedConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val usageLogs = repository.latestLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val usageHistory = repository.dataUsageHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val appSettings = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    // Installed apps fetching for Split Tunneling
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    data class AppInfo(
        val name: String,
        val packageName: String,
        val isSystem: Boolean,
        val isAllowed: Boolean
    )

    init {
        viewModelScope.launch {
            repository.populateDefaultsIfEmpty()
        }
    }

    fun loadInstalledApps(packageManager: PackageManager) {
        viewModelScope.launch {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            val allowedSet = appSettings.value?.allowedApps?.split(",")?.toSet() ?: emptySet()
            
            val sortedList = apps.map { app ->
                val name = app.loadLabel(packageManager).toString()
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(
                    name = name,
                    packageName = app.packageName,
                    isSystem = isSystem,
                    isAllowed = allowedSet.contains(app.packageName)
                )
            }.filter { !it.isSystem || it.packageName == "com.google.android.youtube" || it.packageName == "com.android.chrome" } // Filter down to make the list intuitive and responsive
                .sortedBy { it.name }

            _installedApps.value = sortedList
        }
    }

    fun toggleAppInSplitTunnel(packageName: String) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings()
            val list = currentSettings.allowedApps.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (list.contains(packageName)) {
                list.remove(packageName)
            } else {
                list.add(packageName)
            }
            val updatedString = list.joinToString(",")
            val updatedSettings = currentSettings.copy(allowedApps = updatedString)
            repository.saveSettings(updatedSettings)
            repository.log("SYSTEM", "لیست تفکیک اپلیکیشن‌ها بروزرسانی شد.")

            // Refresh app list with updated allowState
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(isAllowed = !it.isAllowed) else it
            }
        }
    }

    fun selectVpnConfig(configId: Long) {
        viewModelScope.launch {
            repository.selectConfig(configId)
        }
    }

    fun addCustomVpnConfig(name: String, protocol: String, address: String, port: Int, uuid: String, tls: Boolean, sni: String) {
        viewModelScope.launch {
            val config = VpnConfig(
                name = name,
                protocol = protocol,
                address = address,
                port = port,
                uuid = uuid,
                tls = tls,
                sni = sni
            )
            repository.insertConfig(config)
        }
    }

    fun deleteVpnConfig(config: VpnConfig) {
        viewModelScope.launch {
            repository.deleteConfig(config)
        }
    }

    fun importConfigLink(link: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.importFromLink(link)
            if (success) {
                onSuccess()
            } else {
                onError("فرمت لینک نامعتبر است یا قادر به بارگیری نیست.")
            }
        }
    }

    fun updateSettings(updated: AppSettings) {
        viewModelScope.launch {
            repository.saveSettings(updated)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun toggleVpnState(context: Context) {
        viewModelScope.launch {
            val state = connectionState.value
            val selected = selectedConfig.value

            if (selected == null) {
                repository.log("SYSTEM", "خطا در برقراری اتصال: سروری انتخاب نشده است", "WARN")
                return@launch
            }

            if (state == V2ShieldVpnService.State.DISCONNECTED) {
                // Connect
                val intent = Intent(context, V2ShieldVpnService::class.java).apply {
                    action = V2ShieldVpnService.ACTION_CONNECT
                    putExtra("config_id", selected.id)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                // Disconnect
                val intent = Intent(context, V2ShieldVpnService::class.java).apply {
                    action = V2ShieldVpnService.ACTION_DISCONNECT
                }
                context.startService(intent)
            }
        }
    }

    fun backupSettings(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val backupStr = repository.createBackupString()
            onComplete(backupStr)
        }
    }

    fun restoreSettings(backupStr: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val success = repository.restoreBackupString(backupStr)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }
}

class VpnViewModelFactory(private val repository: VpnRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VpnViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VpnViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
