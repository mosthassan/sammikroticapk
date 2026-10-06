package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import com.example.core.ledger.AccountConstants
import com.example.core.model.CurrencyCode
import com.example.core.model.ExchangeRate
import com.example.core.model.Money
import com.example.data.ledger.InvariantCheckResult
import com.example.data.ledger.InvoiceAllocationSpec
import com.example.data.ledger.PaymentVoucherType
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.ledger.SalesItemSpec
import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountBalanceRow
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.AllocationEntity
import com.example.data.local.entity.AssetEntity
import com.example.data.local.entity.CardPackageEntity
import com.example.data.local.entity.CurrencyRateEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentItemEntity
import com.example.data.local.entity.FiscalPeriodEntity
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.StockMovementEntity
import com.example.data.local.entity.TreasuryAccountEntity
import com.example.data.repository.AccountingRepository
import com.example.domain.usecase.AgingReport
import com.example.domain.usecase.BackupRestoreUseCase
import com.example.domain.usecase.BalanceSheetReport
import com.example.domain.usecase.BatchImportUseCase
import com.example.domain.usecase.FinancialStatementsUseCase
import com.example.domain.usecase.ImportBatchReport
import com.example.domain.usecase.IncomeStatementReport
import com.example.domain.usecase.StatementOfAccountReport
import com.example.domain.usecase.StatementOfAccountUseCase
import com.example.data.local.entity.NetworkDeviceEntity
import com.example.data.local.entity.NetworkSubnetSettingsEntity
import com.example.domain.network.NetworkProtectionValidator
import com.example.domain.network.IpValidationResult
import com.example.util.JsonBackupHelper
import com.example.util.SalesDraftManager
import com.example.core.model.UuidUtils
import com.example.BuildConfig
import com.example.domain.ai.AccountingAssistantUseCase
import com.example.domain.ai.AnomalyDetectionUseCase
import com.example.domain.ai.AssistantAnswer
import com.example.domain.ai.DebtReminderDraft
import com.example.domain.ai.DebtReminderDraftUseCase
import com.example.domain.ai.DraftPurchaseInvoice
import com.example.domain.ai.FinancialAnomaly
import com.example.domain.ai.GeminiAiService
import com.example.domain.ai.InvoiceOcrToDraftUseCase
import com.example.domain.ai.SmartFinancialSummary
import com.example.domain.ai.SmartFinancialSummaryUseCase
import com.example.domain.security.AppLockManager
import com.example.domain.security.RbacManager
import com.example.domain.security.SecurityAction
import com.example.domain.security.UserRole
import com.example.domain.sync.FirebaseCloudSyncBridge
import com.example.domain.sync.OfflineFirstSyncEngine
import com.example.domain.sync.SyncUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardSummary(
    val totalSalesPeriodMinor: Long = 0L,
    val totalReceiptsPeriodMinor: Long = 0L,
    val totalExpensesPeriodMinor: Long = 0L,
    val netCashFlowMinor: Long = 0L,
    val totalReceivablesMinor: Long = 0L,
    val totalPayablesMinor: Long = 0L,
    val treasuryBalancesByCurrency: Map<String, Long> = emptyMap(),
    val openInvoiceCount: Int = 0,
    val isBalanced: Boolean = true
)

enum class PeriodFilter(val title: String) {
    TODAY("اليوم"),
    THIS_WEEK("هذا الأسبوع"),
    THIS_MONTH("هذا الشهر"),
    ALL_TIME("الكل")
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val db = AppDatabase.getInstance(application)
    val repository = AccountingRepository(db)
    val writer = repository.ledgerWriter
    val invariants = repository.invariants

    val statementsUseCase = FinancialStatementsUseCase(db)
    val statementOfAccountUseCase = StatementOfAccountUseCase(db)
    val backupRestoreUseCase = BackupRestoreUseCase(db)
    val batchImportUseCase = BatchImportUseCase(db, writer)

    // Phase 3: Security, Cloud Sync & AI
    val rbacManager = RbacManager(db.auditLogDao())
    val appLockManager = AppLockManager()
    val syncEngine = OfflineFirstSyncEngine(db, FirebaseCloudSyncBridge())
    val geminiService = GeminiAiService { BuildConfig.GEMINI_API_KEY }
    val smartSummaryUseCase = SmartFinancialSummaryUseCase(statementsUseCase, geminiService)
    val accountingAssistantUseCase = AccountingAssistantUseCase(db, statementsUseCase, statementOfAccountUseCase, geminiService)
    val invoiceOcrUseCase = InvoiceOcrToDraftUseCase(geminiService)
    val anomalyDetectionUseCase = AnomalyDetectionUseCase(db)
    val debtReminderDraftUseCase = DebtReminderDraftUseCase(db)

    val syncState = syncEngine.uiState
    val currentRole = MutableStateFlow(UserRole.OWNER)
    val smartSummary = MutableStateFlow<SmartFinancialSummary?>(null)
    val detectedAnomalies = MutableStateFlow<List<FinancialAnomaly>>(emptyList())
    val debtReminders = MutableStateFlow<List<DebtReminderDraft>>(emptyList())
    val assistantHistory = MutableStateFlow<List<AssistantAnswer>>(emptyList())
    val draftOcrInvoice = MutableStateFlow<DraftPurchaseInvoice?>(null)

    // Base Flows
    val allParties: StateFlow<List<PartyEntity>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPackages: StateFlow<List<CardPackageEntity>> = repository.allPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTreasuries: StateFlow<List<TreasuryAccountEntity>> = repository.allTreasuries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAllocations: StateFlow<List<AllocationEntity>> = db.allocationDao().getAllActiveAllocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAssets: StateFlow<List<AssetEntity>> = repository.allAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPeriods: StateFlow<List<FiscalPeriodEntity>> = db.fiscalPeriodDao().getAllPeriodsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRates: StateFlow<List<CurrencyRateEntity>> = db.currencyRateDao().getAllRatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockMovements: StateFlow<List<StockMovementEntity>> = db.cardPackageDao().getAllStockMovementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trialBalance: StateFlow<List<AccountBalanceRow>> = repository.trialBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDevices: StateFlow<List<NetworkDeviceEntity>> = db.networkDeviceDao().getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subnetSettings: StateFlow<NetworkSubnetSettingsEntity?> = db.networkSubnetSettingsDao().getSubnetSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Dashboard State
    val selectedPeriodFilter = MutableStateFlow(PeriodFilter.THIS_MONTH)

    private val _dashboardSummary = MutableStateFlow(DashboardSummary())
    val dashboardSummary: StateFlow<DashboardSummary> = _dashboardSummary.asStateFlow()

    // Generated Reports State
    private val _incomeStatement = MutableStateFlow<IncomeStatementReport?>(null)
    val incomeStatement: StateFlow<IncomeStatementReport?> = _incomeStatement.asStateFlow()

    private val _balanceSheet = MutableStateFlow<BalanceSheetReport?>(null)
    val balanceSheet: StateFlow<BalanceSheetReport?> = _balanceSheet.asStateFlow()

    private val _agingReport = MutableStateFlow<AgingReport?>(null)
    val agingReport: StateFlow<AgingReport?> = _agingReport.asStateFlow()

    private val _currentPartyStatement = MutableStateFlow<StatementOfAccountReport?>(null)
    val currentPartyStatement: StateFlow<StatementOfAccountReport?> = _currentPartyStatement.asStateFlow()

    private val _invariantResult = MutableStateFlow<InvariantCheckResult?>(null)
    val invariantResult: StateFlow<InvariantCheckResult?> = _invariantResult.asStateFlow()

    // UI Feedback Messages
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        refreshDashboard()
        runInvariantCheck()

        // Restore persistent sales invoice draft (survives app switching, WhatsApp, & process death)
        try {
            val savedDraft = SalesDraftManager.loadDraft(getApplication())
            if (savedDraft != null && savedDraft.items.isNotEmpty()) {
                draftSalesPartyId = savedDraft.partyId
                draftSalesCustomerSearch = savedDraft.customerSearch
                draftSalesNotes = savedDraft.notes
                draftSalesPaymentType = savedDraft.paymentType
                draftSalesPaidMinor = savedDraft.paidMinor
                draftSalesItems.clear()
                draftSalesItems.addAll(savedDraft.items)
                showCreateSalesInvoiceDialog = savedDraft.isOpen
            }
        } catch (e: Exception) {
            // Ignore error on startup
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            val todayEpoch = System.currentTimeMillis() / 86400000L
            val filter = selectedPeriodFilter.value

            val startEpoch: Long? = when (filter) {
                PeriodFilter.TODAY -> todayEpoch
                PeriodFilter.THIS_WEEK -> todayEpoch - 7
                PeriodFilter.THIS_MONTH -> todayEpoch - 30
                PeriodFilter.ALL_TIME -> null
            }

            val income = statementsUseCase.generateIncomeStatement(startEpoch, todayEpoch)
            val receivables = repository.getNetBalanceForAccount(AccountConstants.ACCOUNTS_RECEIVABLE)
            val payables = -repository.getNetBalanceForAccount(AccountConstants.ACCOUNTS_PAYABLE)

            // Treasury balances
            val treasuries = db.treasuryDao().getAllTreasuriesSync()
            val trMap = mutableMapOf<String, Long>()
            treasuries.forEach { tr ->
                val bal = repository.getTreasuryBalance(tr.id)
                trMap[tr.currency] = (trMap[tr.currency] ?: 0L) + bal
            }

            // Invariant check
            val invRes = repository.runInvariantCheck()
            _invariantResult.value = invRes

            _dashboardSummary.value = DashboardSummary(
                totalSalesPeriodMinor = income.netRevenueMinor,
                totalReceiptsPeriodMinor = 0L, // Derived
                totalExpensesPeriodMinor = income.directIspCostMinor + income.totalOperatingExpensesMinor,
                netCashFlowMinor = income.netProfitMinor,
                totalReceivablesMinor = receivables,
                totalPayablesMinor = payables,
                treasuryBalancesByCurrency = trMap,
                openInvoiceCount = 0,
                isBalanced = invRes.isValid
            )
        }
    }

    fun setPeriodFilter(filter: PeriodFilter) {
        selectedPeriodFilter.value = filter
        refreshDashboard()
    }

    fun runInvariantCheck() {
        viewModelScope.launch {
            val result = repository.runInvariantCheck()
            _invariantResult.value = result
        }
    }

    // --- Action Methods ---

    fun postQuickSale(
        partyId: String,
        packageId: String?,
        description: String,
        quantity: Int,
        unitPriceMinor: Long,
        cashPaidMinor: Long,
        treasuryId: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postQuickSale(
                    partyId = partyId,
                    packageId = packageId,
                    description = description,
                    quantity = quantity,
                    unitPriceMinor = unitPriceMinor,
                    cashPaidMinor = cashPaidMinor,
                    treasuryId = treasuryId,
                    fiscalYear = 2026,
                    dateEpochDay = today
                )
                _userMessage.emit("تم تسجيل البيع السريع وترحيله بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("خطأ في البيع السريع: ${e.message}")
            }
        }
    }

    fun postSalesInvoice(
        partyId: String,
        cardItems: List<SalesItemSpec>,
        serviceItems: List<SalesItemSpec>,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postSalesInvoice(
                    partyId = partyId,
                    fiscalYear = 2026,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    cardItems = cardItems,
                    serviceItems = serviceItems,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل فاتورة المبيعات بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إصدار الفاتورة: ${e.message}")
            }
        }
    }

    fun postCustomerReceipt(
        partyId: String,
        treasuryId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        allocations: List<InvoiceAllocationSpec>,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postCustomerReceipt(
                    partyId = partyId,
                    treasuryId = treasuryId,
                    fiscalYear = 2026,
                    dateEpochDay = today,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    allocations = allocations,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل سند القبض وتخصيصه بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل سند القبض: ${e.message}")
            }
        }
    }

    fun postPaymentVoucher(
        recipientPartyId: String,
        treasuryId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        paymentType: PaymentVoucherType,
        customExpenseCode: String? = null,
        invoiceAllocations: List<InvoiceAllocationSpec> = emptyList(),
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postPaymentVoucher(
                    recipientPartyId = recipientPartyId,
                    treasuryId = treasuryId,
                    fiscalYear = 2026,
                    dateEpochDay = today,
                    amountOrigMinor = amountOrigMinor,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    paymentType = paymentType,
                    customExpenseCode = customExpenseCode,
                    invoiceAllocations = invoiceAllocations,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل سند الصرف بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل سند الصرف: ${e.message}")
            }
        }
    }

    fun postPurchaseInvoice(
        vendorPartyId: String,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        items: List<PurchaseItemSpec>,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postPurchaseInvoice(
                    vendorPartyId = vendorPartyId,
                    fiscalYear = 2026,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    items = items,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل فاتورة المشتريات وتسجيل الأصول إن وُجدت")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسجيل فاتورة المشتريات: ${e.message}")
            }
        }
    }

    fun postCreditNote(
        partyId: String,
        amountOrigMinor: Long,
        currency: CurrencyCode,
        exchangeRate: ExchangeRate,
        returnPackageId: String?,
        returnQty: Int,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postCreditNote(
                    partyId = partyId,
                    fiscalYear = 2026,
                    dateEpochDay = today,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    amountOrigMinor = amountOrigMinor,
                    returnPackageId = returnPackageId,
                    returnQty = returnQty,
                    notes = notes
                )
                _userMessage.emit("تم ترحيل الإشعار الدائن وإعادة الكميات إن وُجدت")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل ترحيل الإشعار الدائن: ${e.message}")
            }
        }
    }

    fun postTreasuryTransfer(
        sourceTreasuryId: String,
        sourceAmountOrigMinor: Long,
        sourceCurrency: CurrencyCode,
        sourceRate: ExchangeRate,
        destTreasuryId: String,
        destAmountOrigMinor: Long,
        destCurrency: CurrencyCode,
        destRate: ExchangeRate,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.postTreasuryTransfer(
                    sourceTreasuryId = sourceTreasuryId,
                    sourceAmountOrigMinor = sourceAmountOrigMinor,
                    sourceCurrency = sourceCurrency,
                    sourceRate = sourceRate,
                    destTreasuryId = destTreasuryId,
                    destAmountOrigMinor = destAmountOrigMinor,
                    destCurrency = destCurrency,
                    destRate = destRate,
                    fiscalYear = 2026,
                    dateEpochDay = today,
                    notes = notes
                )
                _userMessage.emit("تم التحويل بين الخزائن وتسجيل فروق الصرف بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل التحويل: ${e.message}")
            }
        }
    }

    fun runDepreciation(assetId: String, year: Int, month: Int) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                val success = writer.runMonthlyDepreciation(assetId, year, month, today)
                if (success) {
                    _userMessage.emit("تم ترحيل قيد الإهلاك الشهري للأصل بنجاح")
                } else {
                    _userMessage.emit("تنبيه: تم إهلاك هذا الأصل مسبقاً لهذه الفترة أو تم استبعاده بالكامل")
                }
                refreshDashboard()
            } catch (e: Exception) {
                _userMessage.emit("فشل الإهلاك: ${e.message}")
            }
        }
    }

    fun disposeAsset(
        assetId: String,
        salvageProceedsMinor: Long,
        treasuryId: String?,
        notes: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.disposeAsset(
                    assetId = assetId,
                    disposalDateEpochDay = today,
                    salvageProceedsMinor = salvageProceedsMinor,
                    treasuryId = treasuryId,
                    notes = notes
                )
                _userMessage.emit("تم استبعاد الأصل وترحيل قيود التخريد والأرباح/الخسائر الرأسمالية بنجاح")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل استبعاد الأصل: ${e.message}")
            }
        }
    }

    fun voidDocument(docId: String, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                val success = writer.voidDocument(docId, today, reason)
                if (success) {
                    _userMessage.emit("تم إلغاء المستند وإدراج قيد عكسي تعويضي بنجاح")
                    refreshDashboard()
                    onSuccess()
                } else {
                    _userMessage.emit("المستند ملغي مسبقاً")
                }
            } catch (e: Exception) {
                _userMessage.emit("فشل الإلغاء: ${e.message}")
            }
        }
    }

    fun receiveCardStock(packageId: String, quantity: Int, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.receiveCardStock(packageId, quantity, today, notes)
                _userMessage.emit("تم تسجيل استلام دفعة الكروت وزيادة الرصيد")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل استلام الكروت: ${e.message}")
            }
        }
    }

    fun adjustCardStock(packageId: String, adjustmentQty: Int, reason: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                writer.adjustCardStock(packageId, adjustmentQty, today, reason)
                _userMessage.emit("تم تسجيل تسوية رصيد الكروت")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسوية الرصيد: ${e.message}")
            }
        }
    }

    fun reconcileTreasuryCash(treasuryId: String, actualCountMinor: Long, notes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val today = System.currentTimeMillis() / 86400000L
                val doc = writer.reconcileTreasuryCash(treasuryId, actualCountMinor, 2026, today, notes)
                if (doc != null) {
                    _userMessage.emit("تم تسجيل قيد تسوية فارق جرد الصندوق بنجاح")
                } else {
                    _userMessage.emit("الرصيد الفعلي مطابق تماماً للرصيد الدفتري، لا يلزم قيد")
                }
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تسوية جرد الخزينة: ${e.message}")
            }
        }
    }

    fun executeYearEndClosing(fiscalYear: Int, memo: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val closingDate = 20365L // End of year
                writer.executeYearEndClosing(fiscalYear, closingDate, memo)
                _userMessage.emit("تم الإقفال السنوي وترحيل الأرباح والخسائر لحساب الأرباح المرحّلة 3301")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل الإقفال السنوي: ${e.message}")
            }
        }
    }

    fun setPeriodClosed(year: Int, month: Int, isClosed: Boolean) {
        viewModelScope.launch {
            try {
                db.fiscalPeriodDao().setPeriodClosed(year, month, isClosed, if (isClosed) System.currentTimeMillis() else null)
                _userMessage.emit("تم ${if (isClosed) "قفل" else "فتح"} الفترة المالية بنجاح")
            } catch (e: Exception) {
                _userMessage.emit("فشل تعديل حالة الفترة: ${e.message}")
            }
        }
    }

    fun loadStatementOfAccount(partyId: String, controlAccountCode: String, startEpoch: Long?, endEpoch: Long?) {
        viewModelScope.launch {
            try {
                val report = statementOfAccountUseCase.generateStatement(partyId, controlAccountCode, startEpoch, endEpoch)
                _currentPartyStatement.value = report
            } catch (e: Exception) {
                _userMessage.emit("فشل توليد كشف الحساب: ${e.message}")
            }
        }
    }

    fun loadIncomeStatement(startEpoch: Long?, endEpoch: Long?) {
        viewModelScope.launch {
            val rep = statementsUseCase.generateIncomeStatement(startEpoch, endEpoch)
            _incomeStatement.value = rep
        }
    }

    fun loadBalanceSheet(asOfDateEpoch: Long) {
        viewModelScope.launch {
            val rep = statementsUseCase.generateBalanceSheet(asOfDateEpoch)
            _balanceSheet.value = rep
        }
    }

    fun loadAgingReport(asOfDateEpoch: Long) {
        viewModelScope.launch {
            val rep = statementsUseCase.generateAgingReport(asOfDateEpoch)
            _agingReport.value = rep
        }
    }

    fun insertParty(party: PartyEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertParty(party)
                _userMessage.emit("تمت إضافة الطرف بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إضافة الطرف: ${e.message}")
            }
        }
    }

    fun setPartyActive(partyId: String, isActive: Boolean) {
        viewModelScope.launch {
            db.partyDao().setPartyActive(partyId, isActive)
            _userMessage.emit(if (isActive) "تم تنشيط الطرف" else "تمت أرشفة الطرف")
        }
    }

    fun insertPackage(pkg: CardPackageEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertPackage(pkg)
                _userMessage.emit("تم حفظ الباقة بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل حفظ الباقة: ${e.message}")
            }
        }
    }

    fun setPackageActive(packageId: String, isActive: Boolean) {
        viewModelScope.launch {
            db.cardPackageDao().setPackageActive(packageId, isActive)
            _userMessage.emit(if (isActive) "تم تنشيط الباقة" else "تمت أرشفة الباقة")
        }
    }

    fun insertTreasury(treasury: TreasuryAccountEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.insertTreasury(treasury)
                _userMessage.emit("تمت إضافة الخزينة بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إضافة الخزينة: ${e.message}")
            }
        }
    }

    fun exportBackup(onExported: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = backupRestoreUseCase.exportDatabaseToJson()
                onExported(json)
                _userMessage.emit("تم تصدير النسخة الاحتياطية بنجاح")
            } catch (e: Exception) {
                _userMessage.emit("فشل تصدير النسخة: ${e.message}")
            }
        }
    }

    fun restoreBackup(json: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = backupRestoreUseCase.restoreDatabaseFromJson(json)
                if (res.isSuccess) {
                    _userMessage.emit(res.getOrNull() ?: "تمت الاستعادة بنجاح")
                    refreshDashboard()
                    onComplete()
                } else {
                    _userMessage.emit("فشل استعادة النسخة: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _userMessage.emit("خطأ أثناء الاستعادة: ${e.message}")
            }
        }
    }

    fun importInvoicesBatch(jsonString: String, onReport: (ImportBatchReport) -> Unit) {
        viewModelScope.launch {
            try {
                val report = batchImportUseCase.importInvoicesBatch(jsonString)
                onReport(report)
                _userMessage.emit("اكتمل الاستيراد: نجح ${report.successCount}، تخطي ${report.duplicateSkippedCount}، أخطاء ${report.errorCount}")
                refreshDashboard()
            } catch (e: Exception) {
                _userMessage.emit("فشل استيراد الدفعة: ${e.message}")
            }
        }
    }

    fun switchRole(role: UserRole) {
        currentRole.value = role
        rbacManager.currentUserRole = role
        viewModelScope.launch {
            _userMessage.emit("تم تبديل الدور إلى: ${role.titleAr}")
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            val res = syncEngine.syncNow()
            if (res.isSuccess) {
                _userMessage.emit("تمت المزامنة السحابية بنجاح")
                refreshDashboard()
            } else {
                _userMessage.emit("خطأ في المزامنة: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun loadSmartSummary(year: Int, month: Int) {
        viewModelScope.launch {
            try {
                val s = smartSummaryUseCase.generateSummary(year, month)
                smartSummary.value = s
            } catch (e: Exception) {
                _userMessage.emit("تعذر توليد الملخص الذكي: ${e.message}")
            }
        }
    }

    fun askAssistant(question: String) {
        viewModelScope.launch {
            try {
                val ans = accountingAssistantUseCase.answerQuestion(question)
                assistantHistory.value = assistantHistory.value + ans
            } catch (e: Exception) {
                _userMessage.emit("تعذر الإجابة عن الاستفسار: ${e.message}")
            }
        }
    }

    fun scanInvoiceText(text: String) {
        viewModelScope.launch {
            try {
                val draft = invoiceOcrUseCase.convertInvoiceTextToDraft(text)
                draftOcrInvoice.value = draft
                _userMessage.emit("تم استخراج مسودة الفاتورة بنجاح، بانتظار الاعتماد.")
            } catch (e: Exception) {
                _userMessage.emit("فشل تحليل نص الفاتورة: ${e.message}")
            }
        }
    }

    fun approveAndPostDraftInvoice(draft: DraftPurchaseInvoice, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Enforce RBAC
                rbacManager.enforce(SecurityAction.CREATE_PURCHASE_INVOICE)

                // Match or create vendor
                var vendor = db.partyDao().getAllPartiesSync().find { it.name == draft.suggestedVendorName }
                if (vendor == null) {
                    val vendorId = "VND_" + java.util.UUID.randomUUID().toString().take(8)
                    db.partyDao().insertParty(
                        PartyEntity(
                            id = vendorId,
                            name = draft.suggestedVendorName,
                            isVendor = true,
                            isCustomer = false
                        )
                    )
                    vendor = db.partyDao().getPartyById(vendorId)
                }

                val itemSpecs = draft.items.map { item ->
                    PurchaseItemSpec(
                        description = item.description,
                        accountCode = item.accountCode,
                        quantity = item.quantity,
                        unitPriceMinor = item.unitPriceMinor,
                        isAsset = false
                    )
                }

                val rateMicros = when (draft.totalAmount.currency) {
                    CurrencyCode.USD -> 530000000L
                    CurrencyCode.SAR -> 140000000L
                    CurrencyCode.YER -> 1000000L
                }

                val doc = writer.postPurchaseInvoice(
                    vendorPartyId = vendor!!.id,
                    fiscalYear = 2026,
                    dateEpochDay = draft.suggestedDateEpochDay,
                    currency = draft.totalAmount.currency,
                    exchangeRate = ExchangeRate(draft.totalAmount.currency, CurrencyCode.YER, rateMicros),
                    items = itemSpecs,
                    notes = "فاتورة معتمدة ومرحلة من مسودة الذكاء الاصطناعي OCR"
                )

                // Audit log
                db.auditLogDao().insertLog(
                    com.example.data.local.entity.AuditLogEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        entityType = "PURCHASE_INVOICE",
                        entityId = doc.id,
                        action = "AI_DRAFT_APPROVED_AND_POSTED",
                        beforeJson = """{"vendorConfidence":${draft.vendorConfidence},"totalConfidence":${draft.totalConfidence}}""",
                        afterJson = """{"docNumber":${doc.docNumber},"status":"POSTED"}""",
                        timestamp = System.currentTimeMillis()
                    )
                )

                draftOcrInvoice.value = null
                _userMessage.emit("تم ترحيل الفاتورة #${doc.docNumber} بنجاح إلى دفتر الأستاذ!")
                refreshDashboard()
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل ترحيل الفاتورة: ${e.message}")
            }
        }
    }

    fun scanAnomalies() {
        viewModelScope.launch {
            try {
                val list = anomalyDetectionUseCase.runDeterministicScan()
                detectedAnomalies.value = list
            } catch (e: Exception) {
                _userMessage.emit("فشل فحص الشذوذ المالي: ${e.message}")
            }
        }
    }

    fun loadDebtReminders() {
        viewModelScope.launch {
            try {
                val list = debtReminderDraftUseCase.generateReminders()
                debtReminders.value = list
            } catch (e: Exception) {
                _userMessage.emit("تعذر تجهيز تذكيرات الديون: ${e.message}")
            }
        }
    }

    fun togglePinLock() {
        if (appLockManager.isPinLockEnabled) {
            appLockManager.disablePin()
            viewModelScope.launch { _userMessage.emit("تم إلغاء قفل PIN") }
        } else {
            appLockManager.setupPin("1234")
            viewModelScope.launch { _userMessage.emit("تم تفعيل قفل PIN (الرمز: 1234)") }
        }
    }

    // ==========================================
    // Network Devices & Protection Operations
    // ==========================================

    fun validateIp(targetIp: String, currentDeviceId: String?): IpValidationResult {
        return NetworkProtectionValidator.validateDeviceIp(
            targetIp = targetIp,
            currentDeviceId = currentDeviceId,
            existingDevices = allDevices.value,
            settings = subnetSettings.value
        )
    }

    fun saveNetworkDevice(device: NetworkDeviceEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val existing = db.networkDeviceDao().getDeviceById(device.id)
                if (existing != null) {
                    db.networkDeviceDao().updateDevice(device)
                    _userMessage.emit("تم تحديث بيانات جهاز الشبكة (${device.name}) بنجاح")
                } else {
                    db.networkDeviceDao().insertDevice(device)
                    _userMessage.emit("تمت إضافة جهاز الشبكة (${device.name}) بنجاح")
                }
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("خطأ في حفظ الجهاز: ${e.message}")
            }
        }
    }

    fun deleteNetworkDevice(deviceId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                db.networkDeviceDao().deleteDeviceById(deviceId)
                _userMessage.emit("تم حذف الجهاز بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("خطأ في حذف الجهاز: ${e.message}")
            }
        }
    }

    fun updateSubnetSettings(settings: NetworkSubnetSettingsEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                db.networkSubnetSettingsDao().update(settings)
                _userMessage.emit("تم تحديث إعدادات رنجات وحماية الشبكة بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("خطأ في تحديث إعدادات الشبكة: ${e.message}")
            }
        }
    }

    // ==========================================
    // JSON Bulk Export & Import Operations
    // ==========================================

    fun importCustomersFromJson(jsonStr: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = JsonBackupHelper.parseCustomersFromJson(jsonStr)
            result.fold(
                onSuccess = { parties ->
                    var count = 0
                    parties.forEach { p ->
                        db.partyDao().insertParty(p)
                        count++
                    }
                    _userMessage.emit("تم استيراد $count عميل/طرف بنجاح من ملف JSON")
                    onSuccess(count)
                },
                onFailure = { err ->
                    val msg = err.message ?: "خطأ في قراءة بيانات JSON"
                    _userMessage.emit("فشل الاستيراد: $msg")
                    onError(msg)
                }
            )
        }
    }

    fun importDevicesFromJson(jsonStr: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = JsonBackupHelper.parseDevicesFromJson(jsonStr)
            result.fold(
                onSuccess = { devices ->
                    var count = 0
                    devices.forEach { dev ->
                        val res = db.networkDeviceDao().insertDevice(dev)
                        if (res > 0L) count++
                    }
                    _userMessage.emit("تم استيراد $count جهاز شبكة بنجاح من ملف JSON")
                    onSuccess(count)
                },
                onFailure = { err ->
                    val msg = err.message ?: "خطأ في قراءة بيانات JSON للأجهزة"
                    _userMessage.emit("فشل استيراد الأجهزة: $msg")
                    onError(msg)
                }
            )
        }
    }

    fun importPurchasesFromJson(jsonStr: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = JsonBackupHelper.parsePurchasesFromJson(jsonStr)
            result.fold(
                onSuccess = { invoices ->
                    var count = 0
                    invoices.forEach { inv ->
                        try {
                            var vendor = allParties.value.firstOrNull { it.name.trim().equals(inv.supplierName.trim(), ignoreCase = true) }
                            if (vendor == null) {
                                val newVendor = PartyEntity(
                                    id = UuidUtils.newTimeOrderedId(),
                                    name = inv.supplierName,
                                    isVendor = true,
                                    isActive = true
                                )
                                db.partyDao().insertParty(newVendor)
                                vendor = newVendor
                            }

                            val currency = CurrencyCode.fromString(inv.currency)
                            val rate = if (currency == CurrencyCode.FUNCTIONAL) ExchangeRate.parity(CurrencyCode.FUNCTIONAL) else ExchangeRate(currency, CurrencyCode.FUNCTIONAL, 530_000_000L)

                            val specs = inv.items.map { itm ->
                                val code = if (itm.isFixedAsset) AccountConstants.FIXED_ASSETS_NETWORK else AccountConstants.OPERATING_EXPENSES
                                PurchaseItemSpec(
                                    description = itm.description,
                                    accountCode = code,
                                    quantity = itm.quantity,
                                    unitPriceMinor = itm.unitPriceMinor,
                                    isAsset = itm.isFixedAsset,
                                    usefulLifeMonths = itm.usefulLifeMonths
                                )
                            }

                            postPurchaseInvoice(
                                vendorPartyId = vendor.id,
                                currency = currency,
                                exchangeRate = rate,
                                items = specs,
                                notes = "استيراد JSON: ${inv.notes}".trim()
                            ) {}
                            count++
                        } catch (e: Exception) {
                            // Continue
                        }
                    }
                    _userMessage.emit("تم استيراد وترحيل $count فاتورة مشتريات بنجاح من ملف JSON")
                    onSuccess(count)
                },
                onFailure = { err ->
                    val msg = err.message ?: "خطأ في قراءة بيانات JSON للمشتريات"
                    _userMessage.emit("فشل استيراد المشتريات: $msg")
                    onError(msg)
                }
            )
        }
    }

    // ==========================================
    // PERSISTENT ACTIVE DRAFTS (SURVIVE APP SWITCHING & WHATSAPP)
    // ==========================================

    // Sales Invoice Draft (Image 1)
    var showCreateSalesInvoiceDialog by mutableStateOf(false)
    var draftSalesPartyId by mutableStateOf<String?>(null)
    var draftSalesCustomerSearch by mutableStateOf("")
    val draftSalesItems = mutableStateListOf<SalesItemDraftState>()
    var draftSalesNotes by mutableStateOf("")
    var draftSalesPaymentType by mutableStateOf("CREDIT") // "CREDIT", "CASH"
    var draftSalesPaidMinor by mutableStateOf(0L)

    fun persistCurrentSalesDraft() {
        try {
            SalesDraftManager.saveDraft(
                context = getApplication(),
                isOpen = showCreateSalesInvoiceDialog,
                partyId = draftSalesPartyId,
                customerSearch = draftSalesCustomerSearch,
                items = draftSalesItems.toList(),
                notes = draftSalesNotes,
                paymentType = draftSalesPaymentType,
                paidMinor = draftSalesPaidMinor
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    fun openNewSalesInvoiceDraft(initialPartyId: String? = null, defaultPackage: CardPackageEntity? = null) {
        if (draftSalesPartyId == null && initialPartyId != null) {
            draftSalesPartyId = initialPartyId
        }
        if (draftSalesItems.isEmpty()) {
            draftSalesItems.add(
                SalesItemDraftState(
                    packageId = defaultPackage?.id,
                    packageName = defaultPackage?.name ?: "باقة كروت",
                    quantity = 10,
                    unitPriceMinor = defaultPackage?.wholesalePriceMinor ?: 10000L,
                    retailPriceMinor = defaultPackage?.retailPriceMinor ?: 20000L
                )
            )
        }
        showCreateSalesInvoiceDialog = true
        persistCurrentSalesDraft()
    }

    fun dismissSalesInvoiceDialog() {
        showCreateSalesInvoiceDialog = false
        persistCurrentSalesDraft()
    }

    fun clearSalesInvoiceDraft() {
        showCreateSalesInvoiceDialog = false
        draftSalesPartyId = null
        draftSalesCustomerSearch = ""
        draftSalesItems.clear()
        draftSalesNotes = ""
        draftSalesPaymentType = "CREDIT"
        draftSalesPaidMinor = 0L
        SalesDraftManager.clearDraft(getApplication())
    }

    fun cloneSalesInvoiceToDraft(doc: DocumentEntity) {
        viewModelScope.launch {
            try {
                val items = db.documentItemDao().getItemsForDocument(doc.id)
                draftSalesPartyId = doc.partyId
                draftSalesCustomerSearch = ""
                draftSalesNotes = "مستنسخة من فاتورة #${doc.docNumber}"
                draftSalesPaymentType = "CREDIT"
                draftSalesPaidMinor = 0L
                draftSalesItems.clear()

                if (items.isNotEmpty()) {
                    items.forEach { itm ->
                        draftSalesItems.add(
                            SalesItemDraftState(
                                packageId = itm.packageId,
                                packageName = itm.description,
                                quantity = itm.quantity,
                                unitPriceMinor = itm.unitPriceMinor,
                                retailPriceMinor = itm.unitPriceMinor * 2
                            )
                        )
                    }
                } else {
                    draftSalesItems.add(
                        SalesItemDraftState(
                            packageId = null,
                            packageName = "كروت إنترنت",
                            quantity = 10,
                            unitPriceMinor = 10000L,
                            retailPriceMinor = 20000L
                        )
                    )
                }
                showCreateSalesInvoiceDialog = true
                persistCurrentSalesDraft()
                _userMessage.emit("تم استنساخ الفاتورة #${doc.docNumber} بنجاح إلى مسودة التحرير")
            } catch (e: Exception) {
                _userMessage.emit("فشل استنساخ الفاتورة: ${e.message}")
            }
        }
    }

    // Manual Card Stock Addition Draft (Image 3)
    var showAddManualCardsDialog by mutableStateOf(false)
    var draftAddCardsPackageId by mutableStateOf<String?>(null)
    var draftAddCardsCategoryName by mutableStateOf("")
    var draftAddCardsQuantity by mutableStateOf(100)
    var draftAddCardsRetailPriceMinor by mutableStateOf(100000L)
    var draftAddCardsWholesalePriceMinor by mutableStateOf(90000L)
    var draftAddCardsNotes by mutableStateOf("")

    fun openAddManualCardsDraft(defaultPackage: CardPackageEntity? = null) {
        if (draftAddCardsPackageId == null && defaultPackage != null) {
            draftAddCardsPackageId = defaultPackage.id
            draftAddCardsCategoryName = defaultPackage.name
            draftAddCardsRetailPriceMinor = defaultPackage.retailPriceMinor
            draftAddCardsWholesalePriceMinor = defaultPackage.wholesalePriceMinor
        }
        showAddManualCardsDialog = true
    }

    fun clearAddManualCardsDraft() {
        showAddManualCardsDialog = false
        draftAddCardsPackageId = null
        draftAddCardsCategoryName = ""
        draftAddCardsQuantity = 100
        draftAddCardsRetailPriceMinor = 100000L
        draftAddCardsWholesalePriceMinor = 90000L
        draftAddCardsNotes = ""
    }
}

data class SalesItemDraftState(
    val id: String = UuidUtils.newTimeOrderedId(),
    var packageId: String? = null,
    var packageName: String = "",
    var quantity: Int = 10,
    var unitPriceMinor: Long = 0L,
    var retailPriceMinor: Long = 0L,
    var isService: Boolean = false
) {
    val lineTotalMinor: Long get() = quantity * unitPriceMinor
}
