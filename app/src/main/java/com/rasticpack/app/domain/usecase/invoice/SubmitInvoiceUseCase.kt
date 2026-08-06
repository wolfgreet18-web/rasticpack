package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.Invoice
import com.rasticpack.app.domain.model.InvoiceItem
import com.rasticpack.app.domain.repository.InventorySheetRepository
import com.rasticpack.app.domain.repository.InvoiceRepository
import com.rasticpack.app.ui.calc.CartonCalcResult
import java.time.Instant
import javax.inject.Inject

/**
 * ══ مرحله ۳.۳ بخش دوم — SubmitInvoiceUseCase ══
 * معادل دقیق submitCalc2Invoice در 4.html و منطق موجود در data/repo/InvoiceRepository
 * (submitInvoiceFromCalc2 قدیمی) — با همان سه گام:
 *   ۱) از هر ردیف کارتن، بهترین ورق (results[i].sheets.firstOrNull()) و تعداد ورق لازم
 *      (best.sheetsNeed) گرفته می‌شود؛ اگر ورق مناسبی پیدا نشده باشد آن ردیف نادیده گرفته می‌شود.
 *   ۲) نیاز هر ورق (بر اساس sheetId) جمع زده می‌شود (چون چند ردیف کارتن می‌توانند از یک
 *      ورق مشترک استفاده کنند) و قبل از هر تغییری، کافی‌بودن موجودی هر ورق چک می‌شود.
 *   ۳) اگر همه‌چیز کافی بود: موجودی هر ورق کم می‌شود و فاکتور+آیتم‌ها درج می‌شوند.
 *
 * خروجی موفق: شناسه‌ی فاکتور تازه‌ساخته‌شده.
 * خطاها (دقیقاً معادل پیام‌های وب، از طریق RasticError موجود):
 *   - NoValidSheetsForInvoice  → «هیچ ورق معتبری برای صدور سند پیدا نشد.»
 *   - InsufficientStock(label) → «موجودی ورق{label} کافی نیست.»
 */
class SubmitInvoiceUseCase @Inject constructor(
    private val invoiceRepo: InvoiceRepository,
    private val inventoryRepo: InventorySheetRepository
) {
    suspend operator fun invoke(
        customerId: Int,
        customerName: String,
        results: List<CartonCalcResult>
    ): RasticResult<Int> {
        data class RawItem(
            val sheetId: Int, val sw: Double, val sh: Double, val layer: String, val qty: Int,
            val cartonName: String, val cartonLength: Double, val cartonWidth: Double,
            val cartonHeight: Double, val glue: Double, val cartonQty: Int,
            val unitPrice: Double, val lineTotal: Double, val itemProfit: Double
        )

        val rawItems = results.mapNotNull { r ->
            val best = r.sheets.firstOrNull() ?: return@mapNotNull null
            val qty = best.sheetsNeed ?: return@mapNotNull null
            if (qty <= 0) return@mapNotNull null
            RawItem(
                sheetId = best.sheet.id, sw = best.sheet.sw, sh = best.sheet.sh, layer = best.sheet.layer, qty = qty,
                cartonName = r.name, cartonLength = r.length, cartonWidth = r.width,
                cartonHeight = r.height, glue = r.glue, cartonQty = r.qty,
                unitPrice = r.finalPrice, lineTotal = r.totalPrice, itemProfit = r.totalProfit
            )
        }
        if (rawItems.isEmpty()) {
            return RasticResult.failure(RasticError.NoValidSheetsForInvoice)
        }

        // جمع نیاز هر ورق و بررسی کافی‌بودن موجودی
        val need = linkedMapOf<Int, Int>()
        rawItems.forEach { need[it.sheetId] = (need[it.sheetId] ?: 0) + it.qty }

        val sheetsById = inventoryRepo.getAll().associateBy { it.id }
        for ((sheetId, qty) in need) {
            val sheet = sheetsById[sheetId]
            if (sheet == null || qty > sheet.qty) {
                val label = sheet?.let { " ${fmt(it.sh)}×${fmt(it.sw)}" }
                return RasticResult.failure(RasticError.InsufficientStock(label))
            }
        }

        // کم کردن موجودی هر ورق (یک‌بار برای هر sheetId، به‌اندازه‌ی نیاز کل)
        need.forEach { (sheetId, qty) -> inventoryRepo.decreaseQty(sheetId, qty) }

        // stockAfter برای هر آیتم — موجودی باقی‌مانده‌ی همان ورق بعد از کسر کل نیازش
        val stockAfterBySheet = need.keys.associateWith { sheetId ->
            inventoryRepo.getById(sheetId)?.qty ?: 0
        }

        val totalSheets = rawItems.sumOf { it.qty }
        val invoice = Invoice(
            customerId = customerId,
            customerName = customerName,
            dateIso = Instant.now().toString(),
            status = "draft",
            totalSheets = totalSheets
        )
        val items = rawItems.map {
            InvoiceItem(
                invoiceId = 0, // با insertWithItems جایگزین می‌شود
                sheetId = it.sheetId, sw = it.sw, sh = it.sh, layer = it.layer, qty = it.qty,
                cartonName = it.cartonName, cartonLength = it.cartonLength, cartonWidth = it.cartonWidth,
                cartonHeight = it.cartonHeight, glue = it.glue, cartonQty = it.cartonQty,
                unitPrice = it.unitPrice, lineTotal = it.lineTotal, itemProfit = it.itemProfit,
                stockAfter = stockAfterBySheet[it.sheetId]
            )
        }

        val invoiceId = invoiceRepo.insertWithItems(invoice, items)
        return RasticResult.success(invoiceId)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
