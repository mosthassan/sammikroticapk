package com.example.domain.sync

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseCloudSyncBridge(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : CloudSyncBridge {

    override suspend fun pushEntity(
        orgId: String,
        collectionName: String,
        entityId: String,
        data: Map<String, Any?>
    ) {
        firestore.collection("orgs")
            .document(orgId)
            .collection(collectionName)
            .document(entityId)
            .set(data)
            .await()
    }

    override suspend fun pullEntitiesModifiedSince(
        orgId: String,
        collectionName: String,
        sinceTimestamp: Long
    ): List<Map<String, Any?>> {
        val querySnapshot = firestore.collection("orgs")
            .document(orgId)
            .collection(collectionName)
            .whereGreaterThan("updatedAt", sinceTimestamp)
            .get()
            .await()

        return querySnapshot.documents.mapNotNull { it.data }
    }
}
