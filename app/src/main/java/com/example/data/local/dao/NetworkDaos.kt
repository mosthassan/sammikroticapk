package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.NetworkDeviceEntity
import com.example.data.local.entity.NetworkSubnetSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDeviceDao {
    @Query("SELECT * FROM network_devices ORDER BY createdAt DESC")
    fun getAllDevices(): Flow<List<NetworkDeviceEntity>>

    @Query("SELECT * FROM network_devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): NetworkDeviceEntity?

    @Query("SELECT * FROM network_devices WHERE ipAddress = :ip LIMIT 1")
    suspend fun getDeviceByIp(ip: String): NetworkDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevice(device: NetworkDeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevices(devices: List<NetworkDeviceEntity>): List<Long>

    @Update
    suspend fun updateDevice(device: NetworkDeviceEntity)

    @Query("DELETE FROM network_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: String)

    @Delete
    suspend fun deleteDevice(device: NetworkDeviceEntity)

    @Query("SELECT COUNT(*) FROM network_devices")
    suspend fun getDeviceCount(): Int
}

@Dao
interface NetworkSubnetSettingsDao {
    @Query("SELECT * FROM network_subnet_settings WHERE id = 'GLOBAL_SUBNET_SETTINGS' LIMIT 1")
    fun getSubnetSettings(): Flow<NetworkSubnetSettingsEntity?>

    @Query("SELECT * FROM network_subnet_settings WHERE id = 'GLOBAL_SUBNET_SETTINGS' LIMIT 1")
    suspend fun getSubnetSettingsDirect(): NetworkSubnetSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(settings: NetworkSubnetSettingsEntity): Long

    @Update
    suspend fun update(settings: NetworkSubnetSettingsEntity)
}
