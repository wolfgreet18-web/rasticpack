package com.rasticpack.app.domain.model

/**
 * معادل خالص هر آیتم آرایه‌ی `invoices` در نسخه‌ی وب (بدون هیچ import از Room).
 * status: "draft" (پیش‌فاکتور) ، "partial" (نیمه‌تسویه) ، "paid" (تسویه‌شده)
 *   — دقیقاً معادل invoiceStatusOf(inv) در وب که پیش‌فرض "paid" است اگر ثبت نشده باشد.
 * sent: معادل inv.sent — «ارسال شد» را نشان می‌دهد.
 */
data class Invoice(
    val id: Int = 0,
    val customerId: Int,
    val customerName: String,
    val dateIso: String,
    val status: String = "draft",
    val totalSheets: Int = 0,
    val sent: Boolean = false,
    val sentToProduction: Boolean = false,
    val paidAmount: Double? = null,
    val editedAtIso: String? = null
) {
    /** معادل invoiceStatusOf در وب — پیش‌فرض "paid" اگر ثبت نشده باشد (فاکتورهای قدیمی) */
    val effectiveStatus: String get() = status.ifBlank { "paid" }
}

/** معادل کامل یک فاکتور با آیتم‌هایش — همراستا با InvoiceWithItems سطح داده */
data class InvoiceWithItemsModel(
    val invoice: Invoice,
    val items: List<InvoiceItem>
) {
    /** معادل calcInvoiceTurnover در وب */
    val total: Double get() = items.sumOf { it.lineTotal ?: 0.0 }

    /** معادل invoiceRemaining در وب */
    val remaining: Double get() = (total - (invoice.paidAmount ?: 0.0)).coerceAtLeast(0.0)

    /** معادل isInvoiceDebtor در وب: ارسال‌شده ولی هنوز کامل تسویه نشده */
    val isDebtor: Boolean get() = invoice.sent && invoice.effectiveStatus != "paid"
}
