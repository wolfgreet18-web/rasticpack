package com.rasticpack.app.domain.usecase.inventory

import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InventorySheetRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * معادل دقیق `removeInventoryRow()` در 4.html — حذف یک ورق از موجودی.
 * پاک‌کردن آستانه‌ی هشدار مرتبط (`sheetThresholds[id]`) هنوز در `PricingRepository`
 * انجام می‌شود (مسئولیت لایه‌ی ViewModel، دقیقاً مثل امروز)، نه اینجا — چون این
 * UseCase فقط مسئول خودِ جدول موجودی است.
 */
class RemoveInventorySheetUseCase @Inject constructor(
    private val repo: InventorySheetRepository
) {
    suspend operator fun invoke(id: Int): RasticResult<Unit> {
        repo.delete(id)
        return RasticResult.success(Unit)
    }
}
