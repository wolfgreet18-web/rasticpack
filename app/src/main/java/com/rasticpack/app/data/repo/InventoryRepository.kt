package com.rasticpack.app.data.repo

import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.engine.SheetItem
import javax.inject.Inject

/**
 * دسترسی به جدول inventory_sheets — معادل آرایه‌ی `inventory` در وب.
 * toSheetItem/فهرست کامل اینجا در اختیار CalculatorEngine.matchSheets قرار می‌گیرد.
 *
 * ══ مرحله ۰.۳ — @Inject constructor ══ (منطق داخل کلاس دست‌نخورده مانده)
 */
class InventoryRepository @Inject constructor(private val db: AppDatabase) {

    suspend fun getAll(): List<InventorySheetEntity> = db.inventoryDao().getAll()

    /** فهرست ابعاد یکتای ثبت‌شده در موجودی (بدون تکرار)، مرتب بر اساس sh سپس sw صعودی —
        معادل truckFreightPresetList در وب. */
    suspend fun getUniqueDims(): List<Pair<Double, Double>> {
        val seen = linkedSetOf<Pair<Double, Double>>()
        getAll().forEach { seen.add(it.sh to it.sw) }
        return seen.sortedWith(compareBy({ it.first }, { it.second }))
    }

    /** تبدیل ردیف‌های Room به مدل SheetItem که CalculatorEngine با آن کار می‌کند */
    suspend fun getAllAsSheetItems(): List<SheetItem> =
        getAll().map {
            SheetItem(
                id = it.id,
                sw = it.sw,
                sh = it.sh,
                layer = it.layer,
                qty = it.qty,
                flute = it.flute,
                paperType = it.paperType
            )
        }

    suspend fun getById(id: Int): InventorySheetEntity? = getAll().find { it.id == id }

    /** کم کردن موجودی یک ورق — معادل s.qty -= it.qty هنگام ثبت فاکتور در وب */
    suspend fun decreaseQty(sheetId: Int, amount: Int) {
        val sheet = getById(sheetId) ?: return
        val newQty = (sheet.qty - amount).coerceAtLeast(0)
        db.inventoryDao().update(sheet.copy(qty = newQty))
    }

    // ══ مرحله ۴ — تب «موجودی ورق» ══ معادل addInventoryRow/updateQty/updateDim/removeInventoryRow در وب

    fun observeAll() = db.inventoryDao().observeAll()

    /** افزودن ورق جدید — معادل دقیق addInventoryRow در وب (شامل چک تکراری‌بودن). */
    suspend fun addSheet(sh: Double, sw: Double, layer: String, qty: Int, flute: String, paperType: String): String? {
        if (sh <= 0 || sw <= 0) return "طول و عرض ورق را کامل وارد کنید."
        if (qty < 0) return "موجودی را وارد کنید."
        val dup = getAll().any {
            it.sw == sw && it.sh == sh && it.layer == layer && it.flute == flute && it.paperType == paperType
        }
        if (dup) return "ورق ${fmt(sh)}×${fmt(sw)} (${layerLabel(layer)} · فلوت $flute · $paperType) قبلاً ثبت شده."
        db.inventoryDao().insert(
            InventorySheetEntity(sw = sw, sh = sh, layer = layer, qty = qty, flute = flute, paperType = paperType)
        )
        return null
    }

    /** معادل updateQty در وب */
    suspend fun updateQty(id: Int, qty: Int) {
        val sheet = getById(id) ?: return
        db.inventoryDao().update(sheet.copy(qty = qty.coerceAtLeast(0)))
    }

    /** معادل updateDim در وب — تغییر طول یا عرض یک ورق، با چک تکراری‌نبودن ترکیب جدید. */
    suspend fun updateDim(id: Int, field: String, value: Double): String? {
        if (value <= 0) return null
        val sheet = getById(id) ?: return null
        val newSw = if (field == "sw") value else sheet.sw
        val newSh = if (field == "sh") value else sheet.sh
        val dup = getAll().any {
            it.id != id && it.sw == newSw && it.sh == newSh && it.layer == sheet.layer &&
                it.flute == sheet.flute && it.paperType == sheet.paperType
        }
        if (dup) return "ورقی با همین ابعاد در همین بخش وجود دارد."
        db.inventoryDao().update(sheet.copy(sw = newSw, sh = newSh))
        return null
    }

    /** معادل removeInventoryRow در وب */
    suspend fun delete(id: Int) = db.inventoryDao().deleteById(id)

    /** افزایش موجودی یک ورق — معادل sheet.qty+=r.qty در applyTruckFreightStock وب */
    suspend fun increaseQty(id: Int, amount: Int) {
        val sheet = getById(id) ?: return
        db.inventoryDao().update(sheet.copy(qty = sheet.qty + amount))
    }

    /**
     * پیدا کردن ورق با همین ابعاد+لایه+فلوت+کاغذ، یا ساختن ردیف جدید با موجودی صفر —
     * معادل دقیق findOrCreateInventorySheet در وب (استفاده در «افزودن ورق کرایه حمل به موجودی»).
     */
    suspend fun findOrCreateSheet(sh: Double, sw: Double, layer: String, flute: String, paperType: String): InventorySheetEntity {
        getAll().find { it.sw == sw && it.sh == sh && it.layer == layer && it.flute == flute && it.paperType == paperType }
            ?.let { return it }
        val newId = db.inventoryDao().insert(
            InventorySheetEntity(sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType)
        )
        return InventorySheetEntity(id = newId.toInt(), sw = sw, sh = sh, layer = layer, qty = 0, flute = flute, paperType = paperType)
    }

    companion object {
        fun layerLabel(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"
        private fun fmt(n: Double) = if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
    }
}
