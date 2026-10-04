package com.example.domain.ai

import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase
import java.util.UUID

class AnomalyDetectionUseCase(
    private val db: AppDatabase
) {
    suspend fun runDeterministicScan(): List<FinancialAnomaly> {
        val anomalies = mutableListOf<FinancialAnomaly>()

        // 1. Check duplicate documents (same party, same amount, within 2 days)
        val allDocs = db.documentDao().getAllDocumentsSync()
        for (i in allDocs.indices) {
            val d1 = allDocs[i]
            for (j in i + 1 until allDocs.size) {
                val d2 = allDocs[j]
                if (d1.partyId == d2.partyId &&
                    d1.totalMinor == d2.totalMinor &&
                    d1.type == d2.type &&
                    kotlin.math.abs(d1.dateEpochDay - d2.dateEpochDay) <= 2 &&
                    d1.status == "POSTED" && d2.status == "POSTED"
                ) {
                    val party = db.partyDao().getPartyById(d1.partyId)
                    anomalies.add(
                        FinancialAnomaly(
                            id = UUID.randomUUID().toString(),
                            title = "اشتباه في تكرار مستند مالي",
                            description = "المستند #${d1.docNumber} والمستند #${d2.docNumber} يحملان نفس المبلغ والطرف (${party?.name ?: d1.partyId}) في تواريخ متقاربة جداً.",
                            severity = AnomalySeverity.HIGH,
                            relatedEntityId = d1.id
                        )
                    )
                }
            }
        }

        // 2. Check customer exceeding credit limit
        val parties = db.partyDao().getAllPartiesSync()
        val customerBalances = db.journalDao().getPartyBalancesForControlAccount("1201")
        for (party in parties) {
            if (party.creditLimitMinor > 0) {
                val balanceRow = customerBalances.find { it.partyId == party.id }
                val netDebitMinor = balanceRow?.netBalanceMinor ?: 0L
                if (netDebitMinor > party.creditLimitMinor) {
                    val excess = netDebitMinor - party.creditLimitMinor
                    anomalies.add(
                        FinancialAnomaly(
                            id = UUID.randomUUID().toString(),
                            title = "تجاوز الحد الائتماني للعميل",
                            description = "العميل (${party.name}) تجاوز حده الائتماني بمقدار ${Money(excess, CurrencyCode.YER).format()}.",
                            severity = AnomalySeverity.MEDIUM,
                            relatedEntityId = party.id
                        )
                    )
                }
            }
        }

        // 3. Check for unusual negative stock packages
        val packages = db.cardPackageDao().getAllPackagesSync()
        for (pkg in packages) {
            val balance = db.cardPackageDao().getStockBalance(pkg.id)
            if (balance < 0) {
                anomalies.add(
                    FinancialAnomaly(
                        id = UUID.randomUUID().toString(),
                        title = "رصيد مخزون سالب في الباقات",
                        description = "الباقة (${pkg.name}) رصيدها المخزني سالب ($balance كرت). يرجى إجراء تسوية جردية (ADJUST).",
                        severity = AnomalySeverity.HIGH,
                        relatedEntityId = pkg.id
                    )
                )
            }
        }

        // 4. Check for high expense spike
        val directIspCostMinor = db.journalDao().getNetDebitBalanceForAccount("5101")
        if (directIspCostMinor > 50000000L) { // > 500,000 YER threshold alert
            anomalies.add(
                FinancialAnomaly(
                    id = UUID.randomUUID().toString(),
                    title = "ارتفاع في تكلفة الخدمة المباشرة",
                    description = "إجمالي تكلفة الإنترنت الرئيسي 5101 وصل إلى ${Money(directIspCostMinor, CurrencyCode.YER).format()}، يرجى مطابقة الفواتير مع موفري النطاق.",
                    severity = AnomalySeverity.LOW,
                    relatedEntityId = "5101"
                )
            )
        }

        return anomalies
    }
}
