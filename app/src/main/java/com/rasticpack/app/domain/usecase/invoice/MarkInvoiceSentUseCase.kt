package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InvoiceRepository
import javax.inject.Inject

/**
 * معادل دقیق markInvoiceSent/unmarkInvoiceSent در 4.html — فقط پرچم sent را
 * روی خودِ فاکتور تغییر می‌دهد؛ هیچ اثر دیگری روی موجودی یا آیتم‌ها ندارد.
 */
class MarkInvoiceSentUseCase @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Int, sent: Boolean): RasticResult<Unit> {
        val iw = invoiceRepository.getById(invoiceId)
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)
        invoiceRepository.updateInvoice(iw.invoice.copy(sent = sent))
        return RasticResult.Success(Unit)
    }
}
