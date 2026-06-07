package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDao {
    // VPN Configs
    @Query("SELECT * FROM vpn_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<VpnConfig>>

    @Query("SELECT * FROM vpn_configs WHERE isSelected = 1 LIMIT 1")
    fun getSelectedConfigFlow(): Flow<VpnConfig?>

    @Query("SELECT * FROM vpn_configs WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedConfig(): VpnConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: VpnConfig): Long

    @Query("UPDATE vpn_configs SET isSelected = 0 WHERE isSelected = 1")
    suspend fun deselectAllConfigs()

    @Transaction
    suspend fun selectConfig(configId: Long) {
        deselectAllConfigs()
        QuerySelectConfig(configId)
    }

    @Query("UPDATE vpn_configs SET isSelected = 1 WHERE id = :configId")
    suspend fun QuerySelectConfig(configId: Long)

    @Delete
    suspend fun deleteConfig(config: VpnConfig)

    @Query("DELETE FROM vpn_configs WHERE isSubscription = 1 AND subscriptionUrl = :url")
    suspend fun deleteConfigsForSubscription(url: String)

    @Query("UPDATE vpn_configs SET latency = :latency WHERE id = :configId")
    suspend fun updateLatency(configId: Long, latency: Int)

    // Data Usage
    @Query("SELECT * FROM data_usage ORDER BY timestamp DESC LIMIT 30")
    fun getDataUsageHistory(): Flow<List<DataUsageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageRecord(record: DataUsageRecord)

    // VPN Logs
    @Query("SELECT * FROM vpn_logs ORDER BY timestamp DESC LIMIT 100")
    fun getLatestLogs(): Flow<List<VpnLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VpnLog)

    @Query("DELETE FROM vpn_logs")
    suspend fun clearLogs()

    // App Settings
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}
