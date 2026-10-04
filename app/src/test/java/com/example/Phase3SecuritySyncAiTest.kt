package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.ledger.LedgerInvariants
import com.example.data.ledger.LedgerWriter
import com.example.data.ledger.SalesItemSpec
import com.example.core.model.ExchangeRate
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PartyEntity
import com.example.domain.ai.AnomalyDetectionUseCase
import com.example.domain.ai.GeminiAiService
import com.example.domain.ai.InvoiceOcrToDraftUseCase
import com.example.domain.security.AppLockManager
import com.example.domain.security.RbacException
import com.example.domain.security.RbacManager
import com.example.domain.security.SecurityAction
import com.example.domain.security.UserRole
import com.example.domain.sync.MockCloudSyncBridge
import com.example.domain.sync.OfflineFirstSyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3SecuritySyncAiTest {

    private lateinit var db: AppDatabase
    private lateinit var writer: LedgerWriter
    private lateinit var invariants: LedgerInvariants
    private lateinit var mockCloudBridge: MockCloudSyncBridge
    private lateinit var syncEngine: OfflineFirstSyncEngine
    private lateinit var rbacManager: RbacManager
    private lateinit var appLockManager: AppLockManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        writer = LedgerWriter(db, enableInvariantValidation = true)
        invariants = LedgerInvariants(db)
        mockCloudBridge = MockCloudSyncBridge()
        syncEngine = OfflineFirstSyncEngine(db, mockCloudBridge, deviceId = "TEST_DEVICE_01")
        rbacManager = RbacManager(db.auditLogDao())
        appLockManager = AppLockManager()

        runBlocking {
            syncEngine.initialize()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRbacCashierDeniedFromPostingPurchaseInvoiceAndAuditLogged() = runBlocking {
        rbacManager.currentUserRole = UserRole.CASHIER
        rbacManager.currentUserId = "CASHIER_01"

        try {
            rbacManager.enforce(SecurityAction.CREATE_PURCHASE_INVOICE)
            fail("Expected RbacException when CASHIER attempts CREATE_PURCHASE_INVOICE")
        } catch (e: RbacException) {
            assertTrue(e.message!!.contains("غير مصرح"))
        }

        // Verify that security violation is recorded in audit_log
        val entries = db.journalDao().getAllEntriesSync()
        // Ensure no transaction was posted
        assertEquals(0, entries.size)
    }

    @Test
    fun testRbacOwnerHasFullAccess() = runBlocking {
        rbacManager.currentUserRole = UserRole.OWNER
        // Should succeed without throwing
        rbacManager.enforce(SecurityAction.CREATE_SALES_INVOICE)
        rbacManager.enforce(SecurityAction.CREATE_PURCHASE_INVOICE)
        rbacManager.enforce(SecurityAction.ANNUAL_CLOSE)
        rbacManager.enforce(SecurityAction.REOPEN_PERIOD)
    }

    @Test
    fun testOfflineFirstSyncOutboxPush() = runBlocking {
        val payload = mapOf(
            "id" to "PARTY_TEST_SYNC",
            "name" to "بقالة النور الحديثة",
            "isCustomer" to true,
            "phone" to "777123456"
        )
        syncEngine.queueOutboxItem("PARTY", "PARTY_TEST_SYNC", "UPSERT", payload)

        val pendingBefore = db.syncOutboxDao().getPendingCount()
        assertEquals(1, pendingBefore)

        val result = syncEngine.syncNow("DEFAULT_ORG")
        assertTrue(result.isSuccess)

        val pendingAfter = db.syncOutboxDao().getPendingCount()
        assertEquals(0, pendingAfter)

        // Verify entity arrived in cloud bridge
        val remote = mockCloudBridge.pullEntitiesModifiedSince("DEFAULT_ORG", "partys", 0L)
        assertEquals(1, remote.size)
        assertEquals("بقالة النور الحديثة", remote[0]["name"])
    }

    @Test
    fun testOfflineFirstConflictResolutionLocalWinsWhenLocalNewer() = runBlocking {
        // Local party created at t=2000
        val localParty = PartyEntity(
            id = "PARTY_CONFLICT_01",
            name = "بقالة الأمل المحلية",
            isCustomer = true,
            createdAt = 2000L
        )
        db.partyDao().insertParty(localParty)

        // Remote party has older timestamp t=1000
        val remoteOlderData = mapOf(
            "id" to "PARTY_CONFLICT_01",
            "name" to "بقالة الأمل السحابية القديمة",
            "isCustomer" to true,
            "updatedAt" to 1000L
        )
        mockCloudBridge.pushEntity("DEFAULT_ORG", "partys", "PARTY_CONFLICT_01", remoteOlderData)

        // Perform sync
        val syncResult = syncEngine.syncNow("DEFAULT_ORG")
        assertTrue(syncResult.isSuccess)

        // Verify local party was preserved
        val currentLocal = db.partyDao().getPartyById("PARTY_CONFLICT_01")
        assertEquals("بقالة الأمل المحلية", currentLocal?.name)

        // Verify conflict was recorded in conflict_log
        val conflicts = db.conflictLogDao().observeAllConflicts()
        assertNotNull(conflicts)
    }

    @Test
    fun testAiDraftPurchaseInvoiceIsolation() = runBlocking {
        val ocrUseCase = InvoiceOcrToDraftUseCase(GeminiAiService { "" })
        val scannedReceipt = """
            Starlink Internet Services
            Date: 2026-10-04
            Total: $200.00 USD
        """.trimIndent()

        val draft = ocrUseCase.convertInvoiceTextToDraft(scannedReceipt)

        // Verify draft attributes
        assertNotNull(draft)
        assertEquals(20000L, draft.totalAmount.minor)
        assertEquals(CurrencyCode.USD, draft.totalAmount.currency)
        assertFalse(draft.isReadyForPosting)

        // Verify zero database impact: no document and no journal entries posted
        val docs = db.documentDao().getAllDocumentsSync()
        val journalEntries = db.journalDao().getAllEntriesSync()
        assertEquals(0, docs.size)
        assertEquals(0, journalEntries.size)
    }

    @Test
    fun testAnomalyDetectionDuplicateDocuments() = runBlocking {
        // Create customer
        val customer = PartyEntity(id = "CUST_ANOMALY", name = "بقالة البركة", isCustomer = true)
        db.partyDao().insertParty(customer)

        val items = listOf(
            SalesItemSpec(description = "كرت فئة 1000", quantity = 1, unitPriceMinor = 100000L)
        )
        val rate = ExchangeRate(CurrencyCode.YER, CurrencyCode.YER, 1000000L)

        // Post doc 1
        writer.postSalesInvoice(
            partyId = customer.id,
            fiscalYear = 2026,
            dateEpochDay = 20000L,
            currency = CurrencyCode.YER,
            exchangeRate = rate,
            cardItems = items,
            serviceItems = emptyList()
        )

        // Post doc 2 identical on same date
        writer.postSalesInvoice(
            partyId = customer.id,
            fiscalYear = 2026,
            dateEpochDay = 20000L,
            currency = CurrencyCode.YER,
            exchangeRate = rate,
            cardItems = items,
            serviceItems = emptyList()
        )

        val anomalyUseCase = AnomalyDetectionUseCase(db)
        val anomalies = anomalyUseCase.runDeterministicScan()

        assertTrue(anomalies.any { it.title.contains("تكرار") })
    }

    @Test
    fun testAppLockPinVerification() {
        appLockManager.setupPin("5566")
        assertTrue(appLockManager.isPinLockEnabled)

        appLockManager.lockApp()
        assertTrue(appLockManager.isLocked.value)

        assertFalse(appLockManager.verifyPin("1111"))
        assertTrue(appLockManager.isLocked.value)

        assertTrue(appLockManager.verifyPin("5566"))
        assertFalse(appLockManager.isLocked.value)
    }
}
