package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InvoiceRepository
import javax.inject.Inject

/**
 * معادل دقیق setInvoiceBundleSize/clearInvoiceBundleSize در 4.html — مقدار bundleSize
 * را دقیقاً به همان مقداری که فراخوان می‌دهد تنظیم می‌کند (size=null یعنی پاک‌شدن).
 * تصمیم toggle (اگر همان اندازه از قبل انتخاب بود، پاک شود) در سطح UI/ViewModel گرفته
 * می‌شود — دقیقاً معادل bundleClick در وب که خودش تصمیم می‌گیرد کدام تابع (set/clear)
 * را صدا بزند. itemId شناسه‌ی InvoiceItem است.
 */
class SetInvoiceBundleSizeUseCase @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Int, itemId: Int, size: Int?): RasticResult<Unit> {
        val iw = invoiceRepository.getById(invoiceId)
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)
        val item = iw.items.find { it.id == itemId }
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)
        invoiceRepository.updateItem(item.copy(bundleSize = size))
        return RasticResult.Success(Unit)
    }
}
