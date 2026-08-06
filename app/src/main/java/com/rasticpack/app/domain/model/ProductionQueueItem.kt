package com.rasticpack.app.domain.model

/**
 * ══ مرحله ۳.۴ (نقشه معماری v2.9) ══
 * معادل خالص هر آیتم آرایه‌ی `productionQueue` در نسخه‌ی وب (بدون هیچ import از Room) —
 * یک کارتن که از یک فاکتور به تب «مراحل تولید» ارسال شده.
 * sourceKey = "{invoiceId}-{itemIndex}" — برای جلوگیری از تکرار هنگام ارسال دوباره‌ی
 * همان فاکتور، دقیقاً معادل منطق sendAllToProduction در 4.html.
 */
data class ProductionQueueItem(
    val id: Int = 0,
    val sourceKey: String,
    val name: String,
    val length: Double,
    val width: Double,
    val height: Double,
    val glue: Double,
    val sh: Double,
    val sw: Double,
    val layer: String,
    val customerName: String = "",
    val sentAtIso: String
)
