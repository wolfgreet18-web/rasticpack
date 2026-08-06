package com.rasticpack.app.domain.usecase.inventory

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.InventorySheet
import com.rasticpack.app.domain.repository.InventorySheetRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * معادل دقیق `updateDim()` در 4.html — تغییر طول (`sh`) یا عرض (`sw`) یک ورق:
 *   - مقدار غیرمثبت نادیده گرفته می‌شود (بدون خطا؛ همان `if(!num||num<=0){...return}` وب
 *     که فقط رندر مجدد می‌کند بدون تغییر واقعی).
 *   - اگر ترکیب جدید (sh/sw + همان layer/flute/paperType) با یک ورق دیگر (غیر از خودش)
 *     تکراری باشد، خطا برمی‌گردد و چیزی عوض نمی‌شود.
 */
class UpdateSheetDimUseCase @Inject constructor(
    private val repo: InventorySheetRepository
) {
    suspend operator fun invoke(id: Int, field: String, value: Double): RasticResult<InventorySheet> {
        val existing = repo.getById(id) ?: return RasticResult.success(
            InventorySheet(id = id, sw = 0.0, sh = 0.0)
        )
        if (value <= 0) {
            return RasticResult.success(existing)
        }
        val newSw = if (field == "sw") value else existing.sw
        val newSh = if (field == "sh") value else existing.sh
        val dup = repo.getAll().any {
            it.id != id && it.sw == newSw && it.sh == newSh &&
                it.layer == existing.layer && it.flute == existing.flute && it.paperType == existing.paperType
        }
        if (dup) {
            return RasticResult.failure(RasticError.DuplicateSheetOnDimUpdate)
        }
        val updated = existing.copy(sw = newSw, sh = newSh)
        repo.update(updated)
        return RasticResult.success(updated)
    }
}
