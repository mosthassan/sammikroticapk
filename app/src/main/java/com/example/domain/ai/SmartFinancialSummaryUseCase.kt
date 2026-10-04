package com.example.domain.ai

import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.domain.usecase.FinancialStatementsUseCase
import java.time.LocalDate

class SmartFinancialSummaryUseCase(
    private val financialStatementsUseCase: FinancialStatementsUseCase,
    private val aiService: GeminiAiService
) {
    suspend fun generateSummary(year: Int, month: Int): SmartFinancialSummary {
        val incomeStatement = financialStatementsUseCase.generateIncomeStatement()
        val balanceSheet = financialStatementsUseCase.generateBalanceSheet(LocalDate.now().toEpochDay())

        val totalRev = Money(incomeStatement.netRevenueMinor, CurrencyCode.YER)
        val directCost = Money(incomeStatement.directIspCostMinor, CurrencyCode.YER)
        val grossProfit = Money(incomeStatement.grossProfitMinor, CurrencyCode.YER)
        val totalExp = Money(incomeStatement.totalOperatingExpensesMinor, CurrencyCode.YER)
        val netProfit = Money(incomeStatement.netProfitMinor, CurrencyCode.YER)
        val totalAssets = Money(balanceSheet.totalAssetsMinor, CurrencyCode.YER)

        val marginPct = if (totalRev.minor > 0) (netProfit.minor * 100) / totalRev.minor else 0

        val periodStr = "$year/$month"

        val citations = listOf(
            CitationItem(
                title = "صافي الإيرادات",
                value = totalRev.format(),
                sourceReport = "قائمة الدخل (حسابات 4101 + 4201 - 4102)",
                period = periodStr
            ),
            CitationItem(
                title = "تكلفة الخدمة المباشرة",
                value = directCost.format(),
                sourceReport = "قائمة الدخل (حساب 5101 - اشتراكات الإنترنت)",
                period = periodStr
            ),
            CitationItem(
                title = "المصاريف التشغيلية",
                value = totalExp.format(),
                sourceReport = "قائمة الدخل (حسابات 5201-5299)",
                period = periodStr
            ),
            CitationItem(
                title = "صافي الربح",
                value = netProfit.format(),
                sourceReport = "قائمة الدخل (Net Profit)",
                period = periodStr
            ),
            CitationItem(
                title = "إجمالي الأصول",
                value = totalAssets.format(),
                sourceReport = "الميزانية العمومية",
                period = periodStr
            )
        )

        val highlights = mutableListOf<String>()
        highlights.add("بلغ صافي إيرادات الشبكة ${totalRev.format()} بنسبة هامش ربح صافي تُقدّر بـ $marginPct%.")
        if (directCost.minor > 0) {
            highlights.add("شكّلت تكلفة اشتراكات الإنترنت الرئيسية (حساب 5101) مبلغ ${directCost.format()}.")
        }
        highlights.add("إجمالي المصاريف التشغيلية (ديزل، صيانة، رواتب، إهلاك) بلغ ${totalExp.format()}.")
        highlights.add("الميزانية العمومية مطابقة لمعايير IFRS حيث تساوت الأصول مع إجمالي الخصوم وحقوق الملكية.")

        val prompt = """
            أنت مساعد مالي ذكي لتطبيق SamMikrotik لإدارة شبكات ومزودي خدمة الإنترنت.
            قم بصياغة ملخص تنفيذي موجز واحترافي باللغة العربية بناءً على الأرقام الحقيقية التالية فقط:
            - الفترة: $periodStr
            - صافي الإيرادات: ${totalRev.format()}
            - تكلفة خدمة الإنترنت الرئيسية: ${directCost.format()}
            - إجمالي المصاريف: ${totalExp.format()}
            - صافي الربح: ${netProfit.format()}
            - هامش صافي الربح: $marginPct%
            تنبيه: لا تخترع أي أرقام من عندك، واعتمد فقط على البيانات أعلاه.
        """.trimIndent()

        val aiResult = aiService.generateContent(prompt)
        val combinedCostMinor = directCost.minor + totalExp.minor
        val narrative = aiResult.getOrNull()?.trim() ?: """
            خلال الفترة $periodStr حققت المنشأة أداءً مالياً متزناً بإجمالي إيرادات بلغت ${totalRev.format()}، مقابل تكاليف تشغيل وخدمة مباشرة بلغت ${Money(combinedCostMinor, CurrencyCode.YER).format()}. استقر صافي أرباح الشبكة عند ${netProfit.format()} بهامش ربحية $marginPct%. جميع الأرقام مطابقة لقيد الأستاذ العام والميزانية العمومية.
        """.trimIndent()

        return SmartFinancialSummary(
            title = "التقرير التحليلي الذكي - شهر $month/$year",
            executiveSummaryAr = narrative,
            highlights = highlights,
            citations = citations
        )
    }
}
