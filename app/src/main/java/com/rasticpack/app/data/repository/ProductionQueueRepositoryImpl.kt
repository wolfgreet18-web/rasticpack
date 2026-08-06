package com.rasticpack.app.data.repository

import com.rasticpack.app.data.dao.ProductionQueueDao
import com.rasticpack.app.data.entities.ProductionQueueItemEntity
import com.rasticpack.app.domain.model.ProductionQueueItem
import com.rasticpack.app.domain.repository.ProductionQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ══ مرحله ۳.۴ (نقشه معماری v2.9) — پیاده‌سازی واقعی ProductionQueueRepository با Room ══
 * فقط دسترسی خام به داده + Mapping بین `ProductionQueueItemEntity` و مدل خالص domain
 * `ProductionQueueItem` است. منطق «جلوگیری از تکرار» (که در وب در sendAllToProduction
 * بود) در `domain/usecase/production/SendToProductionUseCase` قرار دارد.
 *
 * فایل قدیمی `data/repo/InvoiceRepository.sendAllToProduction` عمداً حذف نشده —
 * `InvoicesViewModel.sendToProduction` هنوز به آن وابسته است تا در همین زیرمرحله
 * به SendToProductionUseCase سوییچ شود.
 */
class ProductionQueueRepositoryImpl @Inject constructor(
    private val dao: ProductionQueueDao
) : ProductionQueueRepository {

    override fun observeAll(): Flow<List<ProductionQueueItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllSourceKeys(): Set<String> = dao.getAllSourceKeys().toHashSet()

    override suspend fun findFirstByInvoicePrefix(invoiceId: Int): ProductionQueueItem? =
        dao.findFirstByInvoicePrefix(invoiceId)?.toDomain()

    override suspend fun insert(item: ProductionQueueItem): Int =
        dao.insert(item.toEntity()).toInt()

    override suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    override suspend fun trimTo30() {
        dao.trimTo30()
    }
}

private fun ProductionQueueItem.toEntity(): ProductionQueueItemEntity = ProductionQueueItemEntity(
    id = id, sourceKey = sourceKey, name = name, length = length, width = width, height = height,
    glue = glue, sh = sh, sw = sw, layer = layer, customerName = customerName, sentAtIso = sentAtIso
)

private fun ProductionQueueItemEntity.toDomain(): ProductionQueueItem = ProductionQueueItem(
    id = id, sourceKey = sourceKey, name = name, length = length, width = width, height = height,
    glue = glue, sh = sh, sw = sw, layer = layer, customerName = customerName, sentAtIso = sentAtIso
)
