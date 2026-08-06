package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InvoiceRepository
import javax.inject.Inject

/**
 * معادل دقیق markInvoiceSettled/unmarkInvoiceSettled در 4.html.
 * تسویه: کل مبلغ فاکتور (مجموع lineTotal آیتم‌ها) به‌عنوان paidAmount ثبت و
 * status روی "paid" تنظیم می‌شود.
 * لغو تسویه: فقط وقتی وضعیت فعلی "paid" باشد اثر دارد؛ به "draft" و
 * paidAmount=null برمی‌گردد (دقیقاً مثل وب — بدون بازگرداندن مقدار نیمه‌تسویه‌ی قبلی).
 */
class MarkInvoiceSettledUseCase @Inject constructor(
    private val invoiceRepository: InvoiceRepository
) {
    suspend fun settle(invoiceId: Int): RasticResult<Unit> {
        val iw = invoiceRepository.getById(invoiceId)
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)
        val total = iw.total
        invoiceRepository.updateInvoice(iw.invoice.copy(status = "paid", paidAmount = total))
        return RasticResult.Success(Unit)
    }

    suspend fun unsettle(invoiceId: Int): RasticResult<Unit> {
        val iw = invoiceRepository.getById(invoiceId)
            ?: return RasticResult.Failure(RasticError.InvoiceNotFound)
        if (iw.invoice.effectiveStatus != "paid") return RasticResult.Success(Unit)
        invoiceRepository.updateInvoice(iw.invoice.copy(status = "draft", paidAmount = null))
        return RasticResult.Success(Unit)
    }
}
