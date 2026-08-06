package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InvoiceRepository
import javax.inject.Inject

/**
 * معادل دقیق submitInvoicePayment در 4.html — بر اساس مبلغ واردشده، وضعیت
 * فاکتور خودکار تعیین می‌شود:
 *   amount خالی/صفر/منفی → status="draft", paidAmount=null
 *   amount >= مبلغ کل      → status="paid",  paidAmount=مبلغ کل
 *   در غیر این صورت        → status="partial", paidAmount=amount
 */
class SubmitInvoicePaymentUseCase @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) {
    suspend operator fun invoke(invoiceId: Int, amount: Double?): RasticResult<Unit> {
        val iw = invoiceRepository.getById(invoiceId)
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)
        val total = iw.total
        val updated = when {
            amount == null || amount <= 0 -> iw.invoice.copy(status = "draft", paidAmount = null)
            amount >= total -> iw.invoice.copy(status = "paid", paidAmount = total)
            else -> iw.invoice.copy(status = "partial", paidAmount = amount)
        }
        invoiceRepository.updateInvoice(updated)
        return RasticResult.Success(Unit)
    }
}
