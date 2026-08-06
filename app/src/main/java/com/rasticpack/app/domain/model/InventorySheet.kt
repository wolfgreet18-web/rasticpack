package com.rasticpack.app.domain.model

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * معادل دقیق `InventorySheetEntity` (`data/entities/InventorySheetEntity.kt`) اما بدون
 * هیچ وابستگی به `androidx.room` — طبق قانون وابستگی یک‌طرفه (`domain` هرگز از `data`
 * import نمی‌کند). فیلدها عیناً همان فیلدهای هر آیتم آرایه‌ی `inventory` در وب:
 * ابعاد (sh/sw)، لایه (۳/۵)، فلوت (C/E)، نوع کاغذ (KT/2T)، و موجودی (qty).
 *
 * Mapping بین این مدل و `InventorySheetEntity` در `data/repository/InventorySheetRepositoryImpl.kt`
 * انجام می‌شود (مرحله ۳.۲) — نه اینجا.
 */
data class InventorySheet(
    val id: Int = 0,
    val sw: Double,
    val sh: Double,
    val layer: String = "3",
    val qty: Int = 0,
    val flute: String = "C",
    val paperType: String = "2T"
)
