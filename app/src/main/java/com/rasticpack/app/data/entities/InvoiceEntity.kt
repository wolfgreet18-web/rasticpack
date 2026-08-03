package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * معادل هر آیتم آرایه‌ی `invoices` در نسخه‌ی وب (بدون آیتم‌های آن — آیتم‌ها در جدول
 * جداگانه‌ی InvoiceItemEntity هستند، چون یک فاکتور می‌تواند چند نوع کارتن/ورق داشته باشد).
 *
 * status: "draft" (پیش‌فاکتور) ، "partial" (نیمه‌تسویه) ، "paid" (تسویه‌شده)
 *   — دقیقاً معادل invoiceStatusOf(inv) در وب که پیش‌فرض "paid" است اگر ثبت نشده باشد.
 * sent: معادل inv.sent — «ارسال شد» را نشان می‌دهد.
 * isInvoiceDebtor در وب از ترکیب sent && status!=='paid' محاسبه می‌شود — اینجا هم همین‌طور
 * در لایه‌ی Repository/UI محاسبه می‌شود، نه این‌که به‌عنوان فیلد جدا ذخیره شود.
 */
@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val customerName: String,
    val dateIso: String,             // معادل inv.date (ISO 8601)
    val status: String = "draft",    // "draft" | "partial" | "paid"
    val totalSheets: Int = 0,
    val sent: Boolean = false,
    val sentToProduction: Boolean = false,
    val paidAmount: Double? = null,
    val editedAtIso: String? = null
)
