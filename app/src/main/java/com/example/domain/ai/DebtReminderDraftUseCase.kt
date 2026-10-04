package com.example.domain.ai

import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase

class DebtReminderDraftUseCase(
    private val db: AppDatabase
) {
    suspend fun generateReminders(): List<DebtReminderDraft> {
        val reminders = mutableListOf<DebtReminderDraft>()
        val parties = db.partyDao().getAllPartiesSync().filter { it.isCustomer && it.id != "WALK_IN_CASH" }
        val org = db.organizationDao().getOrganizationSync()
        val orgName = org?.name ?: "إدارة الشبكة"
        val partyBalances = db.journalDao().getPartyBalancesForControlAccount("1201")

        for (party in parties) {
            val balanceRow = partyBalances.find { it.partyId == party.id }
            val netBalanceMinor = balanceRow?.netBalanceMinor ?: 0L
            if (netBalanceMinor > 0) {
                val due = Money(netBalanceMinor, CurrencyCode.YER)
                val message = """
السلام عليكم ورحمة الله وبركاته،
الأخوة الأعزاء في: ${party.name} المحترمين،
تحية طيبة وبعد،،
نود تذكيركم بمطابقة الرصيد المالي المستحق لحساب توزيع كروت الإنترنت لدى (${orgName}).

إجمالي الرصيد المستحق: ${due.format()}
شاكرين لكم حسن تعاونكم الدائم وسدادكم في الموعد المحدد.

مع فائق الاحترام والتقدير،
${orgName}
                """.trimIndent()

                reminders.add(
                    DebtReminderDraft(
                        partyId = party.id,
                        partyName = party.name,
                        phone = party.phone,
                        totalDue = due,
                        messageText = message,
                        daysOverdue = 15 // Standard follow-up
                    )
                )
            }
        }
        return reminders
    }
}
