package com.rasticpack.app.domain.usecase.inventory

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.InventorySheet
import com.rasticpack.app.domain.repository.InventorySheetRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۲ (نقشه معماری v2.7) ══
 * معادل دقیق `addInventoryRow()` در 4.html:
 *   - طول و عرض باید مثبت باشند.
 *   - موجودی (qty) منفی مجاز نیست.
 *   - ترکیب (sh, sw, layer, flute, paperType) باید یکتا باشد — دقیقاً همان چک
 *     `inventory.find(...)` در وب.
 */
class AddInventorySheetUseCase @Inject constructor(
    private val repo: InventorySheetRepository
) {
    suspend operator fun invoke(
        sh: Double,
        sw: Double,
        layer: String,
        qty: Int,
        flute: String,
        paperType: String
    ): RasticResult<InventorySheet> {
        if (sh <= 0 || sw <= 0) {
            return RasticResult.failure(RasticError.InvalidSheetDimensions)
        }
        if (qty < 0) {
            return RasticResult.failure(RasticError.InvalidStockQuantity)
        }
        val dup = repo.getAll().any {
            it.sw == sw && it.sh == sh && it.layer == layer && it.flute == flute && it.paperType == paperType
        }
        if (dup) {
            return RasticResult.failure(
                RasticError.DuplicateSheet(
                    sh = sh, sw = sw,
                    layerLabel = layerLabel(layer),
                    flute = flute, paperType = paperType
                )
            )
        }
        val inserted = repo.insert(
            InventorySheet(sw = sw, sh = sh, layer = layer, qty = qty, flute = flute, paperType = paperType)
        )
        return RasticResult.success(inserted)
    }

    private fun layerLabel(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"
}
