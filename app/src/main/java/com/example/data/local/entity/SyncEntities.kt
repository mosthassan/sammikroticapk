package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["status"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["createdAt"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String, // UUID
    val entityType: String, // PARTY, PACKAGE, TREASURY, DOCUMENT, JOURNAL_ENTRY, JOURNAL_LINE, ALLOCATION, ASSET
    val entityId: String,
    val operation: String, // UPSERT, DELETE
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "PENDING", // PENDING, SYNCING, FAILED, COMPLETED
    val lastError: String? = null
)

@Entity(
    tableName = "conflict_log",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["resolvedAt"])
    ]
)
data class ConflictLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val localJson: String,
    val remoteJson: String,
    val resolution: String, // REMOTE_WINS, LOCAL_WINS
    val resolvedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: String = "GLOBAL_SYNC_STATE",
    val deviceId: String,
    val organizationId: String = "DEFAULT_ORG",
    val lastSyncTimestamp: Long = 0L,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncStatus: String = "IDLE", // IDLE, SYNCING, SUCCESS, ERROR
    val lastErrorMessage: String? = null
)
