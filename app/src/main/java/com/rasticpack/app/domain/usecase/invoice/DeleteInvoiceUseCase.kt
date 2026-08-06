package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InventorySheetRepository
import com.rasticpack.app.domain.repository.InvoiceRepository
import javax.inject.Inject

/**
 * معادل دقیق deleteInvoice در 4.html: موجودی هر ورق مصرف‌شده در فاکتور به‌اندازه‌ی
 * qty آن آیتم به انبار بازمی‌گردد، سپس خودِ فاکتور (و آیتم‌هایش، با CASCADE) حذف
 * می‌شود. اگر فاکتور پیدا نشود، خطای InvoiceNotFound برمی‌گردد.
 */
class DeleteInvoiceUseCase @Inject constructor(
    private val invoiceRepository: InvoiceRepository,
    private val inventoryRepository: InventorySheetRepository
) {
    suspend operator fun invoke(invoiceId: Int): RasticResult<Unit> {
        val iw = invoiceRepository.getById(invoiceId)
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)

        iw.items.forEach { item ->
            inventoryRepository.increaseQty(item.sheetId, item.qty)
        }
        invoiceRepository.deleteById(invoiceId)
        return RasticResult.Success(Unit)
    }
}
