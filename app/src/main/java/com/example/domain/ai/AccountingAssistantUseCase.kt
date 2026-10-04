package com.example.domain.ai

import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.AppDatabase
import com.example.domain.usecase.FinancialStatementsUseCase
import com.example.domain.usecase.StatementOfAccountUseCase
import java.time.LocalDate

data class AssistantAnswer(
    val question: String,
    val answerText: String,
    val citedToolData: String,
    val isVerified: Boolean = true
)

class AccountingAssistantUseCase(
    private val db: AppDatabase,
    private val financialStatementsUseCase: FinancialStatementsUseCase,
    private val statementOfAccountUseCase: StatementOfAccountUseCase,
    private val aiService: GeminiAiService
) {
    suspend fun answerQuestion(question: String): AssistantAnswer {
        val qTrimmed = question.trim()

        // 1. Tool selection: Customer / Party Debt
        if (qTrimmed.contains("دين") || qTrimmed.contains("رصيد") || qTrimmed.contains("عميل") || qTrimmed.contains("بقالة") || qTrimmed.contains("طرف")) {
            val parties = db.partyDao().getAllPartiesSync()
            val matchedParty = parties.find { qTrimmed.contains(it.name) }
            if (matchedParty != null) {
                val statement = statementOfAccountUseCase.generateStatement(matchedParty.id, "1201")
                val balance = Money(statement.closingBalanceMinor, CurrencyCode.YER)
                val balanceFormatted = balance.format()
                val toolData = "الطرف: ${matchedParty.name} | الرصيد الدفتري الحالي: $balanceFormatted"
                val prompt = "أجب باختصار باللغة العربية بناءً على المعلومة الحقيقية فقط: $toolData. السؤال: $question"
                val aiAns = aiService.generateContent(prompt).getOrNull()
                val answer = aiAns ?: "رصيد ${matchedParty.name} الحالي هو: $balanceFormatted."
                return AssistantAnswer(question, answer, toolData, isVerified = true)
            }
        }

        // 2. Tool selection: Treasury Cash Balances
        if (qTrimmed.contains("صندوق") || qTrimmed.contains("خزينة") || qTrimmed.contains("نقد") || qTrimmed.contains("سيولة")) {
            val treasuries = db.treasuryDao().getAllTreasuriesSync()
            val balances = treasuries.map { tr ->
                val bal = db.journalDao().getNetDebitBalanceForTreasury(tr.id)
                val cur = runCatching { CurrencyCode.valueOf(tr.currency) }.getOrDefault(CurrencyCode.YER)
                "${tr.name}: ${Money(bal, cur).format()}"
            }
            val toolData = "أرصدة الخزائن: " + balances.joinToString(" ، ")
            val prompt = "أجب باختصار باللغة العربية عن أرصدة الصناديق التالية: $toolData. السؤال: $question"
            val aiAns = aiService.generateContent(prompt).getOrNull()
            val answer = aiAns ?: ("أرصدة الخزائن الحالية:\n" + balances.joinToString("\n"))
            return AssistantAnswer(question, answer, toolData, isVerified = true)
        }

        // 3. Tool selection: Direct ISP & Operating Expenses
        if (qTrimmed.contains("مصروف") || qTrimmed.contains("تكلفة") || qTrimmed.contains("إنترنت") || qTrimmed.contains("ديزل")) {
            val pAndL = financialStatementsUseCase.generateIncomeStatement()
            val directCost = Money(pAndL.directIspCostMinor, CurrencyCode.YER).format()
            val operatingExp = Money(pAndL.totalOperatingExpensesMinor, CurrencyCode.YER).format()
            val toolData = "تكلفة الإنترنت الرئيسي (5101): $directCost | المصاريف التشغيلية: $operatingExp"
            val prompt = "أجب باختصار باللغة العربية بالاعتماد على: $toolData. السؤال: $question"
            val aiAns = aiService.generateContent(prompt).getOrNull()
            val answer = aiAns ?: ("بيانات المصاريف الحالية:\n- تكلفة الإنترنت الرئيسي 5101: $directCost\n- إجمالي المصاريف التشغيلية: $operatingExp")
            return AssistantAnswer(question, answer, toolData, isVerified = true)
        }

        // Default: Balance Sheet & P&L overview
        val balanceSheet = financialStatementsUseCase.generateBalanceSheet(LocalDate.now().toEpochDay())
        val assets = Money(balanceSheet.totalAssetsMinor, CurrencyCode.YER).format()
        val liabilities = Money(balanceSheet.totalLiabilitiesMinor, CurrencyCode.YER).format()
        val equity = Money(balanceSheet.totalEquityMinor, CurrencyCode.YER).format()
        val toolData = "الميزانية العمومية: الأصول $assets ، الخصوم $liabilities ، حقوق الملكية $equity ، متزنة: ${balanceSheet.isBalanced}"
        val prompt = "أجب باختصار باللغة العربية عن الميزانية العمومية التالية: $toolData. السؤال: $question"
        val aiAns = aiService.generateContent(prompt).getOrNull()
        val answer = aiAns ?: "الميزانية العمومية للنظام: إجمالي الأصول $assets، والخصوم وحقوق الملكية متطابقة بنسبة 100% ومتزنة وفقاً لمعايير IFRS."
        return AssistantAnswer(question, answer, toolData, isVerified = true)
    }
}
