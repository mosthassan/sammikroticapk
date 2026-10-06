package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.PartyEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WhatsAppShareHelper {

    const val NETWORK_BRAND_NAME = "SamMikrotik"

    fun sendWhatsApp(context: Context, phone: String?, messageText: String) {
        try {
            val cleanPhone = cleanPhoneNumber(phone)
            val intent = if (!cleanPhone.isNullOrBlank()) {
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(messageText)}"
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    setPackage("com.whatsapp")
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to chooser if WhatsApp package specific intent fails
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "مشاركة الإشعار عبر..."))
            } catch (ex: Exception) {
                Toast.makeText(context, "تعذر فتح تطبيق واتساب أو تطبيق المشاركة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cleanPhoneNumber(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        var digits = phone.filter { it.isDigit() }
        if (digits.startsWith("00")) {
            digits = digits.substring(2)
        }
        // If local Yemeni 9-digit starting with 7
        if (digits.length == 9 && digits.startsWith("7")) {
            digits = "967$digits"
        }
        return digits
    }

    fun formatSalesInvoiceMessage(
        invoice: DocumentEntity,
        customerName: String,
        customerPhone: String?,
        paidMinor: Long,
        totalCustomerDebtMinor: Long?,
        itemsSummary: String
    ): String {
        val dateStr = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale("ar")).format(Date(invoice.createdAt))
        val totalMoney = Money(invoice.totalMinor, CurrencyCode.fromString(invoice.currency)).format()
        val paidMoney = Money(paidMinor, CurrencyCode.fromString(invoice.currency)).format()
        val remainingMinor = (invoice.totalMinor - paidMinor).coerceAtLeast(0L)
        val remainingMoney = Money(remainingMinor, CurrencyCode.fromString(invoice.currency)).format()

        val builder = StringBuilder()
        builder.appendLine("🧾 *فاتورة مبيعات رسمية - $NETWORK_BRAND_NAME*")
        builder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        builder.appendLine("📄 *رقم الفاتورة:* #${invoice.docNumber}")
        builder.appendLine("📅 *التاريخ:* $dateStr")
        builder.appendLine("👤 *العميل / المحل:* $customerName")
        if (!customerPhone.isNullOrBlank()) {
            builder.appendLine("📱 *الهاتف:* $customerPhone")
        }
        builder.appendLine("─────────────────────────")
        if (itemsSummary.isNotBlank()) {
            builder.appendLine("📦 *الأصناف والبنود:* $itemsSummary")
        }
        builder.appendLine("💰 *إجمالي الفاتورة:* $totalMoney")
        builder.appendLine("💵 *المدفوع / الواصل:* $paidMoney")
        builder.appendLine("⏳ *المتبقي من الفاتورة:* $remainingMoney")
        if (totalCustomerDebtMinor != null && totalCustomerDebtMinor > 0L) {
            val debtFormatted = Money(totalCustomerDebtMinor, CurrencyCode.YER).format()
            builder.appendLine("💳 *إجمالي الرصيد المستحق بذمتكم حتى الآن:* $debtFormatted")
        }
        if (invoice.notes.isNotBlank()) {
            builder.appendLine("📝 *ملاحظات:* ${invoice.notes}")
        }
        builder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        builder.appendLine("📡 *إدارة شبكة $NETWORK_BRAND_NAME للإنترنت عالي السرعة*")
        builder.append("✨ نسعد دائماً بخدمتكم والتعامل معكم ✨")
        return builder.toString()
    }

    fun formatCustomerStatementMessage(
        customer: PartyEntity,
        currentDebtMinor: Long,
        activeInvoicesCount: Int
    ): String {
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date())
        val debtMoney = Money(currentDebtMinor, CurrencyCode.YER).format()

        val builder = StringBuilder()
        builder.appendLine("📋 *كشف حساب ومطابقة رصيد - $NETWORK_BRAND_NAME*")
        builder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        builder.appendLine("🏪 *العميل / المحل:* ${customer.name}")
        if (customer.phone.isNotBlank()) {
            builder.appendLine("📱 *الهاتف:* ${customer.phone}")
        }
        builder.appendLine("📅 *تاريخ المطابقة:* $dateStr")
        builder.appendLine("─────────────────────────")
        if (currentDebtMinor <= 0L) {
            builder.appendLine("✅ *الحالة:* الحساب خالص بالكامل (لا توجد مديونية مستحقة)")
        } else {
            builder.appendLine("💳 *الرصيد المستحق بذمتكم للشبكة:* $debtMoney")
            builder.appendLine("📄 *عدد الفواتير غير المسددة:* $activeInvoicesCount فاتورة")
        }
        builder.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        builder.appendLine("📡 *إدارة شبكة $NETWORK_BRAND_NAME للإنترنت*")
        builder.append("✨ شكراً لحسن تعاونكم ودقة سدادكم ✨")
        return builder.toString()
    }
}
