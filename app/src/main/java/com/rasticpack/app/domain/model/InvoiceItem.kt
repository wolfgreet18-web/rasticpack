package com.rasticpack.app.domain.model

/**
 * معادل خالص هر آیتم آرایه‌ی `inv.items[]` در نسخه‌ی وب (بدون هیچ import از Room) —
 * یک ردیف = یک نوع کارتن/ورق داخل یک فاکتور.
 */
data class InvoiceItem(
    val id: Int = 0,
    val invoiceId: Int,
    val sheetId: Int,
    val sw: Double,
    val sh: Double,
    val layer: String,
    val qty: Int,                    // تعداد برگ ورق مصرف‌شده
    val cartonName: String = "",
    val cartonLength: Double = 0.0,
    val cartonWidth: Double = 0.0,
    val cartonHeight: Double = 0.0,
    val glue: Double = 0.0,
    val cartonQty: Int = 0,          // تعداد عدد کارتن
    val unitPrice: Double? = null,
    val lineTotal: Double? = null,
    val itemProfit: Double? = null,
    val stockAfter: Int? = null,
    val bundleSize: Int? = null      // اندازه بسته‌ی تسمه‌بندی (۱۰/۱۵/۲۰) — اختیاری
)
