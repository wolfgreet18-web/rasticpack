package com.rasticpack.app.domain.usecase.customer

import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.CustomerRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * معادل دقیق `removeCustomer()` در 4.html — حذف بی‌سروصدا اگر مشتری وجود نداشته
 * باشد، بدون خطا (تأیید حذف خودش در وب یک `confirm()` سمت UI است، نه اینجا؛
 * همان‌طور که در `CustomersScreen` قبل از صدازدن این UseCase انجام می‌شود).
 * توجه: طبق `4.html`، حذف مشتری فاکتورهای او را پاک نمی‌کند (فقط خودِ رکورد
 * مشتری حذف می‌شود؛ `customerName` روی فاکتورها دست‌نخورده باقی می‌ماند) — این
 * UseCase عمداً هیچ کاری با فاکتورها ندارد.
 */
class DeleteCustomerUseCase @Inject constructor(
    private val repo: CustomerRepository
) {
    suspend operator fun invoke(id: Int): RasticResult<Unit> {
        repo.delete(id)
        return RasticResult.success(Unit)
    }
}
