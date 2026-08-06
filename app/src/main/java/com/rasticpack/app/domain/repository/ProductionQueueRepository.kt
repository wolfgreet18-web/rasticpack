package com.rasticpack.app.domain.repository

import com.rasticpack.app.domain.model.ProductionQueueItem
import kotlinx.coroutines.flow.Flow

/**
 * ══ مرحله ۳.۴ (نقشه معماری v2.9) ══
 * قرارداد خواندن/نوشتن خام صف تولید — بدون منطق تصمیم‌گیری (جلوگیری از تکرار،
 * محدودیت ۳۰ موردی و...) که در `domain/usecase/production/SendToProductionUseCase`
 * قرار می‌گیرد، دقیقاً مطابق الگوی مراحل قبلی (Customer، InventorySheet، Invoice).
 */
interface ProductionQueueRepository {
    fun observeAll(): Flow<List<ProductionQueueItem>>

    /** فهرست sourceKey های موجود در صف — معادل getAllSourceKeys در DAO فعلی. */
    suspend fun getAllSourceKeys(): Set<String>

    /** اولین رکورد صف که sourceKey آن با "{invoiceId}-" شروع می‌شود — معادل findFirstByInvoicePrefix. */
    suspend fun findFirstByInvoicePrefix(invoiceId: Int): ProductionQueueItem?

    suspend fun insert(item: ProductionQueueItem): Int
    suspend fun deleteById(id: Int)

    /** معادل `if(productionQueue.length>30) productionQueue.length=30;` در وب. */
    suspend fun trimTo30()
}
