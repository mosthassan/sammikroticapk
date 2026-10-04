package com.example.domain.sync

import com.example.data.ledger.LedgerInvariants
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConflictLogEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class SyncEngineStatus(val labelAr: String) {
    IDLE("متزامن وجاهز"),
    SYNCING("جارٍ المزامنة السحابية..."),
    OFFLINE("العمل دون اتصال"),
    ERROR("خطأ في المزامنة")
}

data class SyncUiState(
    val status: SyncEngineStatus = SyncEngineStatus.IDLE,
    val pendingOutboxCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSyncTimestamp: Long = 0L,
    val errorMessage: String? = null,
    val isAutoSyncEnabled: Boolean = true
)

class OfflineFirstSyncEngine(
    private val db: AppDatabase,
    private val cloudBridge: CloudSyncBridge,
    private val deviceId: String = "DEV_" + UUID.randomUUID().toString().take(8)
) {
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val existing = db.syncStateDao().getSyncState()
        if (existing == null) {
            val initialState = SyncStateEntity(
                deviceId = deviceId,
                organizationId = "DEFAULT_ORG",
                lastSyncTimestamp = 0L,
                isAutoSyncEnabled = true,
                lastSyncStatus = "IDLE"
            )
            db.syncStateDao().insertIgnore(initialState)
        }
        refreshStatus()
    }

    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        val pendingCount = db.syncOutboxDao().getPendingCount()
        val syncState = db.syncStateDao().getSyncState()
        _uiState.value = _uiState.value.copy(
            pendingOutboxCount = pendingCount,
            lastSyncTimestamp = syncState?.lastSyncTimestamp ?: 0L,
            isAutoSyncEnabled = syncState?.isAutoSyncEnabled ?: true
        )
    }

    suspend fun queueOutboxItem(
        entityType: String,
        entityId: String,
        operation: String,
        payloadMap: Map<String, Any?>
    ) = withContext(Dispatchers.IO) {
        val json = JSONObject(payloadMap).toString()
        val item = SyncOutboxEntity(
            id = UUID.randomUUID().toString(),
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadJson = json,
            status = "PENDING"
        )
        db.syncOutboxDao().insert(item)
        refreshStatus()
    }

    suspend fun syncNow(orgId: String = "DEFAULT_ORG"): Result<Unit> = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(status = SyncEngineStatus.SYNCING, errorMessage = null)
        try {
            // 1. Push pending outbox
            pushOutbox(orgId)

            // 2. Pull remote updates
            pullRemote(orgId)

            // 3. Verify Ledger Invariants after sync
            val invariantCheck = LedgerInvariants(db).verifyAll(failFast = false)
            if (!invariantCheck.isValid) {
                val errorMsg = "فشل التحقق من الثوابت بعد المزامنة: " + invariantCheck.violations.firstOrNull()?.description
                _uiState.value = _uiState.value.copy(
                    status = SyncEngineStatus.ERROR,
                    errorMessage = errorMsg
                )
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            val now = System.currentTimeMillis()
            db.syncStateDao().updateSyncProgress(now, "SUCCESS", null)
            _uiState.value = _uiState.value.copy(
                status = SyncEngineStatus.IDLE,
                lastSyncTimestamp = now,
                pendingOutboxCount = 0,
                errorMessage = null
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "حدث خطأ غير متوقع أثناء المزامنة"
            db.syncStateDao().updateSyncProgress(
                _uiState.value.lastSyncTimestamp,
                "ERROR",
                errorMsg
            )
            _uiState.value = _uiState.value.copy(
                status = SyncEngineStatus.ERROR,
                errorMessage = errorMsg
            )
            Result.failure(e)
        }
    }

    private suspend fun pushOutbox(orgId: String) {
        val pending = db.syncOutboxDao().getPendingItems(50)
        for (item in pending) {
            try {
                db.syncOutboxDao().updateStatus(item.id, "SYNCING")
                val json = JSONObject(item.payloadJson)
                val map = mutableMapOf<String, Any?>()
                json.keys().forEach { key ->
                    map[key] = json.get(key)
                }
                map["updatedAt"] = System.currentTimeMillis()
                map["sourceDeviceId"] = deviceId

                cloudBridge.pushEntity(
                    orgId = orgId,
                    collectionName = item.entityType.lowercase() + "s",
                    entityId = item.entityId,
                    data = map
                )
                db.syncOutboxDao().updateStatus(item.id, "COMPLETED")
            } catch (e: Exception) {
                db.syncOutboxDao().markForRetry(item.id, e.message ?: "Network error")
                throw e
            }
        }
    }

    private suspend fun pullRemote(orgId: String) {
        val lastTimestamp = _uiState.value.lastSyncTimestamp

        // Pull parties
        val remoteParties = cloudBridge.pullEntitiesModifiedSince(orgId, "partys", lastTimestamp)
        for (data in remoteParties) {
            val id = data["id"] as? String ?: continue
            val remoteUpdatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            val localParty = db.partyDao().getPartyById(id)

            if (localParty == null) {
                // New party from cloud
                val name = data["name"] as? String ?: "طرف جديد"
                val phone = data["phone"] as? String ?: ""
                val isCustomer = data["isCustomer"] as? Boolean ?: true
                val isVendor = data["isVendor"] as? Boolean ?: false
                val isPartner = data["isPartner"] as? Boolean ?: false
                db.partyDao().insertParty(
                    PartyEntity(
                        id = id,
                        name = name,
                        phone = phone,
                        isCustomer = isCustomer,
                        isVendor = isVendor,
                        isPartner = isPartner,
                        createdAt = remoteUpdatedAt
                    )
                )
            } else {
                // Check conflict
                val localUpdatedAt = localParty.createdAt
                if (remoteUpdatedAt > localUpdatedAt) {
                    val name = data["name"] as? String ?: localParty.name
                    val phone = data["phone"] as? String ?: localParty.phone
                    db.partyDao().updateParty(
                        localParty.copy(name = name, phone = phone)
                    )
                } else if (remoteUpdatedAt < localUpdatedAt) {
                    // Conflict: Local is newer than remote, record conflict log
                    db.conflictLogDao().insert(
                        ConflictLogEntity(
                            id = UUID.randomUUID().toString(),
                            entityType = "PARTY",
                            entityId = id,
                            localUpdatedAt = localUpdatedAt,
                            remoteUpdatedAt = remoteUpdatedAt,
                            localJson = """{"name":"${localParty.name}"}""",
                            remoteJson = """{"name":"${data["name"]}"}""",
                            resolution = "LOCAL_WINS",
                            resolvedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}
