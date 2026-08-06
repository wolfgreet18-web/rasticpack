package com.rasticpack.app.domain.repository

import com.rasticpack.app.domain.model.InventorySheet
import kotlinx.coroutines.flow.Flow

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * قرارداد خواندن/نوشتن خام موجودی ورق — بدون هیچ منطق اعتبارسنجی (ابعاد نامعتبر/
 * تکراری‌بودن). منطق اعتبارسنجی طبق قانون لایه‌بندی به `domain/usecase/inventory/*`
 * منتقل شده، دقیقاً مثل الگوی `CustomerRepository`/`AddCustomerUseCase` در مرحله ۳.۱.
 *
 * توجه: منطق «کرایه حمل» و «قیمت‌گذاری» (که در وب هم جدا از خودِ آرایه‌ی `inventory`
 * است — `sheetPrices`/`sheetPriceBreakdown` مربوط به `PricingRepository` می‌شوند)
 * عمداً در این اینترفیس نیست. `findOrCreateSheet` اینجا نگه داشته شده چون مستقیماً
 * روی خودِ جدول موجودی کار می‌کند (معادل `findOrCreateInventorySheet` در وب) و هم
 * مسیر «کرایه حمل ▸ افزودن ورق به موجودی» به آن وابسته است.
 */
interface InventorySheetRepository {
    fun observeAll(): Flow<List<InventorySheet>>
    suspend fun getAll(): List<InventorySheet>
    suspend fun getById(id: Int): InventorySheet?

    /** فهرست ابعاد یکتای ثبت‌شده در موجودی (بدون تکرار)، مرتب بر اساس sh سپس sw صعودی —
        معادل truckFreightPresetList در وب. */
    suspend fun getUniqueDims(): List<Pair<Double, Double>>

    suspend fun insert(sheet: InventorySheet): InventorySheet
    suspend fun update(sheet: InventorySheet)
    suspend fun delete(id: Int)

    /** کم کردن موجودی یک ورق — معادل s.qty -= it.qty هنگام ثبت فاکتور در وب */
    suspend fun decreaseQty(sheetId: Int, amount: Int)

    /** افزایش موجودی یک ورق — معادل sheet.qty+=r.qty در applyTruckFreightStock وب */
    suspend fun increaseQty(id: Int, amount: Int)

    /**
     * پیدا کردن ورق با همین ابعاد+لایه+فلوت+کاغذ، یا ساختن ردیف جدید با موجودی صفر —
     * معادل دقیق findOrCreateInventorySheet در وب.
     */
    suspend fun findOrCreateSheet(sh: Double, sw: Double, layer: String, flute: String, paperType: String): InventorySheet
}
