package com.example.domain.security

import com.example.data.local.dao.AuditLogDao
import com.example.data.local.entity.AuditLogEntity
import java.util.UUID

enum class UserRole(val titleAr: String) {
    OWNER("مالك المنشأة"),
    ACCOUNTANT("محاسب عام"),
    CASHIER("كاشير / موزع ميداني"),
    VIEWER("مشاهد (قراءة فقط)")
}

enum class SecurityAction(val descriptionAr: String) {
    CREATE_SALES_INVOICE("إنشاء فاتورة مبيعات"),
    CREATE_RECEIPT_VOUCHER("إنشاء سند قبض"),
    CREATE_PURCHASE_INVOICE("إنشاء فاتورة مشتريات"),
    CREATE_PAYMENT_VOUCHER("إنشاء سند صرف"),
    POST_MANUAL_JOURNAL("ترحيل قيد يومية يدوي"),
    VOID_DOCUMENT("إلغاء مستند مالي"),
    RUN_DEPRECIATION("تشغيل إهلاك الأصول"),
    MONTHLY_CLOSE("قفل الفترة الشهرية"),
    ANNUAL_CLOSE("الإقفال المالي السنوي"),
    REOPEN_PERIOD("إعادة فتح فترة مقفلة"),
    MANAGE_USERS("إدارة المستخدمين والصلاحيات"),
    MANAGE_ORGANIZATION("تعديل بيانات المنشأة والإعدادات"),
    RESTORE_BACKUP("استعادة نسخة احتياطية"),
    VIEW_REPORTS("عرض التقارير والقوائم المالية")
}

class RbacException(message: String) : SecurityException(message)

object RbacPolicy {
    fun isActionAllowed(role: UserRole, action: SecurityAction): Boolean {
        return when (role) {
            UserRole.OWNER -> true
            UserRole.ACCOUNTANT -> when (action) {
                SecurityAction.CREATE_SALES_INVOICE,
                SecurityAction.CREATE_RECEIPT_VOUCHER,
                SecurityAction.CREATE_PURCHASE_INVOICE,
                SecurityAction.CREATE_PAYMENT_VOUCHER,
                SecurityAction.POST_MANUAL_JOURNAL,
                SecurityAction.VOID_DOCUMENT,
                SecurityAction.RUN_DEPRECIATION,
                SecurityAction.MONTHLY_CLOSE,
                SecurityAction.VIEW_REPORTS -> true
                SecurityAction.ANNUAL_CLOSE,
                SecurityAction.REOPEN_PERIOD,
                SecurityAction.MANAGE_USERS,
                SecurityAction.MANAGE_ORGANIZATION,
                SecurityAction.RESTORE_BACKUP -> false
            }
            UserRole.CASHIER -> when (action) {
                SecurityAction.CREATE_SALES_INVOICE,
                SecurityAction.CREATE_RECEIPT_VOUCHER,
                SecurityAction.VIEW_REPORTS -> true
                else -> false
            }
            UserRole.VIEWER -> when (action) {
                SecurityAction.VIEW_REPORTS -> true
                else -> false
            }
        }
    }
}

class RbacManager(
    private val auditLogDao: AuditLogDao? = null
) {
    var currentUserRole: UserRole = UserRole.OWNER
    var currentUserId: String = "USER_DEFAULT"

    suspend fun enforce(action: SecurityAction) {
        if (!RbacPolicy.isActionAllowed(currentUserRole, action)) {
            val violationMessage = "محاولة غير مصرح بها: المستخدم ($currentUserId) بدور (${currentUserRole.titleAr}) حاول تنفيذ (${action.descriptionAr})"
            auditLogDao?.insertLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = "SECURITY_VIOLATION",
                    entityId = action.name,
                    action = "ACCESS_DENIED",
                    beforeJson = """{"userId":"$currentUserId","role":"${currentUserRole.name}"}""",
                    afterJson = """{"action":"${action.name}","allowed":false}""",
                    timestamp = System.currentTimeMillis()
                )
            )
            throw RbacException(violationMessage)
        }
    }
}
