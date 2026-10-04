package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ConflictLogEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SyncOutboxEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SyncOutboxEntity>)

    @Update
    suspend fun update(item: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingItems(limit: Int = 50): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("UPDATE sync_outbox SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE sync_outbox SET status = 'PENDING', retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markForRetry(id: String, error: String)

    @Query("DELETE FROM sync_outbox WHERE status = 'COMPLETED' AND createdAt < :beforeTimestamp")
    suspend fun cleanupCompleted(beforeTimestamp: Long)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt DESC LIMIT 100")
    fun observeRecentOutbox(): Flow<List<SyncOutboxEntity>>
}

@Dao
interface ConflictLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conflict: ConflictLogEntity)

    @Query("SELECT * FROM conflict_log ORDER BY resolvedAt DESC")
    fun observeAllConflicts(): Flow<List<ConflictLogEntity>>

    @Query("SELECT COUNT(*) FROM conflict_log")
    fun observeConflictCount(): Flow<Int>
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(state: SyncStateEntity): Long

    @Update
    suspend fun update(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE id = 'GLOBAL_SYNC_STATE' LIMIT 1")
    suspend fun getSyncState(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = 'GLOBAL_SYNC_STATE' LIMIT 1")
    fun observeSyncState(): Flow<SyncStateEntity?>

    @Query("UPDATE sync_state SET lastSyncTimestamp = :timestamp, lastSyncStatus = :status, lastErrorMessage = :error WHERE id = 'GLOBAL_SYNC_STATE'")
    suspend fun updateSyncProgress(timestamp: Long, status: String, error: String? = null)
}
