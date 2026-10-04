package com.example.domain.sync

interface CloudSyncBridge {
    suspend fun pushEntity(orgId: String, collectionName: String, entityId: String, data: Map<String, Any?>)
    suspend fun pullEntitiesModifiedSince(orgId: String, collectionName: String, sinceTimestamp: Long): List<Map<String, Any?>>
}

class MockCloudSyncBridge : CloudSyncBridge {
    // collection -> id -> document
    private val cloudStore = mutableMapOf<String, MutableMap<String, Map<String, Any?>>>()

    override suspend fun pushEntity(
        orgId: String,
        collectionName: String,
        entityId: String,
        data: Map<String, Any?>
    ) {
        val path = "orgs/$orgId/$collectionName"
        val collection = cloudStore.getOrPut(path) { mutableMapOf() }
        collection[entityId] = data
    }

    override suspend fun pullEntitiesModifiedSince(
        orgId: String,
        collectionName: String,
        sinceTimestamp: Long
    ): List<Map<String, Any?>> {
        val path = "orgs/$orgId/$collectionName"
        val collection = cloudStore[path] ?: return emptyList()
        return collection.values.filter { doc ->
            val updatedAt = (doc["updatedAt"] as? Number)?.toLong()
                ?: (doc["createdAt"] as? Number)?.toLong()
                ?: 0L
            updatedAt > sinceTimestamp
        }
    }

    fun clear() {
        cloudStore.clear()
    }
}
