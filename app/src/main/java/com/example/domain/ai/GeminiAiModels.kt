package com.example.domain.ai

import com.example.core.model.CurrencyCode
import com.example.core.model.Money

data class CitationItem(
    val title: String,
    val value: String,
    val sourceReport: String,
    val period: String
)

data class SmartFinancialSummary(
    val title: String,
    val executiveSummaryAr: String,
    val highlights: List<String>,
    val citations: List<CitationItem>,
    val timestamp: Long = System.currentTimeMillis()
)

data class DraftInvoiceItem(
    val description: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val totalMinor: Long,
    val accountCode: String = "5101"
)

data class DraftPurchaseInvoice(
    val suggestedVendorName: String,
    val vendorConfidence: Float, // 0.0 to 1.0
    val suggestedDateEpochDay: Long,
    val dateConfidence: Float,
    val totalAmount: Money,
    val totalConfidence: Float,
    val items: List<DraftInvoiceItem>,
    val isReadyForPosting: Boolean = false,
    val warnings: List<String> = emptyList()
)

enum class AnomalySeverity(val labelAr: String) {
    LOW("تنبيه منخفض"),
    MEDIUM("تنبيه متوسط"),
    HIGH("خطر / شذوذ مالي مرتفع")
}

data class FinancialAnomaly(
    val id: String,
    val title: String,
    val description: String,
    val severity: AnomalySeverity,
    val relatedEntityId: String? = null,
    val detectedAt: Long = System.currentTimeMillis()
)

data class DebtReminderDraft(
    val partyId: String,
    val partyName: String,
    val phone: String,
    val totalDue: Money,
    val messageText: String,
    val daysOverdue: Int
)
