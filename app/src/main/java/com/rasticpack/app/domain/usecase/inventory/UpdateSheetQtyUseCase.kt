package com.rasticpack.app.domain.usecase.inventory

import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InventorySheetRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * معادل دقیق `updateQty()` در 4.html — مقدار منفی به صفر محدود می‌شود
 * (`Math.max(0, parseInt(val)||0)`)، بدون هیچ چک تکراری/اعتبارسنجی دیگر.
 * اگر ورق پیدا نشود، بی‌صدا کاری نمی‌کند (دقیقاً معادل `if(item)` در وب).
 */
class UpdateSheetQtyUseCase @Inject constructor(
    private val repo: InventorySheetRepository
) {
    suspend operator fun invoke(id: Int, qty: Int): RasticResult<Unit> {
        val existing = repo.getById(id) ?: return RasticResult.success(Unit)
        repo.update(existing.copy(qty = qty.coerceAtLeast(0)))
        return RasticResult.success(Unit)
    }
}
