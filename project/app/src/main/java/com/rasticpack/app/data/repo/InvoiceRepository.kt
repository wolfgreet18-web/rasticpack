package com.rasticpack.app.data.repo

import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.ui.calc.CartonCalcResult
import java.time.Instant

/**
 * ثبت فاکتور از نتایج تب «محاسبه کارتن» — معادل دقیق submitCalc2Invoice در 4.html:
 *  ۱) برای هر ردیف کارتن، بهترین ورق (results[i].sheets.firstOrNull()) و تعداد ورق لازم
 *     (best.sheetsNeed) گرفته می‌شود؛ اگر ورق مناسبی پیدا نشده باشد آن ردیف نادیده گرفته می‌شود.
 *  ۲) نیاز هر ورق (بر اساس sheetId) جمع زده می‌شود (چون چند ردیف کارتن می‌توانند از یک
 *     ورق مشترک استفاده کنند) و قبل از هر تغییری، کافی بودن موجودی هر ورق چک می‌شود —
 *     دقیقاً همان‌طور که در وب با آبجکت `need` انجام می‌شد.
 *  ۳) اگر همه چیز کافی بود: موجودی هر ورق کم می‌شود و فاکتور+آیتم‌ها در دیتابیس درج می‌شوند.
 */
class InvoiceRepository(private val db: AppDatabase) {

    sealed class SubmitResult {
        data class Success(val invoiceId: Int) : SubmitResult()
        data class Error(val message: String) : SubmitResult()
    }

    suspend fun submitInvoiceFromCalc2(
        customerId: Int,
        customerName: String,
        results: List<CartonCalcResult>
    ): SubmitResult {
        // ۱) ساخت لیست آیتم‌های خام از روی بهترین ورق هر ردیف — معادل items.flatMap در وب
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
            return SubmitResult.Error("هیچ ورق معتبری برای صدور سند پیدا نشد.")
        }

        // ۲) جمع نیاز هر ورق و بررسی کافی‌بودن موجودی — معادل حلقه‌ی need در وب
        val need = linkedMapOf<Int, Int>()
        rawItems.forEach { need[it.sheetId] = (need[it.sheetId] ?: 0) + it.qty }

        for ((sheetId, qty) in need) {
            val sheet = db.inventoryDao().getAll().find { it.id == sheetId }
            if (sheet == null || qty > sheet.qty) {
                val label = sheet?.let { " ${fmtDim(it.sh)}×${fmtDim(it.sw)}" } ?: ""
                return SubmitResult.Error("موجودی ورق$label کافی نیست.")
            }
        }

        // ۳) کم کردن موجودی هر ورق (یک‌بار برای هر sheetId، به‌اندازه‌ی نیاز کل)
        need.forEach { (sheetId, qty) ->
            val sheet = db.inventoryDao().getAll().find { it.id == sheetId }!!
            db.inventoryDao().update(sheet.copy(qty = sheet.qty - qty))
        }

        // stockAfter برای هر آیتم — موجودی باقی‌مانده‌ی همان ورق بعد از کسر کل نیازش
        val stockAfterBySheet = need.keys.associateWith { sheetId ->
            db.inventoryDao().getAll().find { it.id == sheetId }?.qty ?: 0
        }

        val totalSheets = rawItems.sumOf { it.qty }
        val invoice = InvoiceEntity(
            customerId = customerId,
            customerName = customerName,
            dateIso = Instant.now().toString(),
            status = "draft",
            totalSheets = totalSheets
        )
        val items = rawItems.map {
            InvoiceItemEntity(
                invoiceId = 0, // با insertInvoiceWithItems جایگزین می‌شود
                sheetId = it.sheetId, sw = it.sw, sh = it.sh, layer = it.layer, qty = it.qty,
                cartonName = it.cartonName, cartonLength = it.cartonLength, cartonWidth = it.cartonWidth,
                cartonHeight = it.cartonHeight, glue = it.glue, cartonQty = it.cartonQty,
                unitPrice = it.unitPrice, lineTotal = it.lineTotal, itemProfit = it.itemProfit,
                stockAfter = stockAfterBySheet[it.sheetId]
            )
        }

        val invoiceId = db.invoiceDao().insertInvoiceWithItems(invoice, items)
        return SubmitResult.Success(invoiceId.toInt())
    }

    private fun fmtDim(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    // ══ TAB — فاکتورها: بقیه‌ی عملیات (مرحله ۶) ══

    fun observeAllWithItems() = db.invoiceDao().observeAllWithItems()

    /** معادل invoiceStatusOf در وب — پیش‌فرض "paid" اگر ثبت نشده باشد (فاکتورهای قدیمی) */
    fun statusOf(invoice: InvoiceEntity): String = invoice.status.ifBlank { "paid" }

    /** معادل isInvoiceDebtor در وب: ارسال‌شده ولی هنوز کامل تسویه نشده */
    fun isDebtor(invoice: InvoiceEntity): Boolean = invoice.sent && statusOf(invoice) != "paid"

    fun invoiceTotal(items: List<InvoiceItemEntity>): Double = items.sumOf { it.lineTotal ?: 0.0 }
    fun invoiceRemaining(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Double =
        (invoiceTotal(items) - (invoice.paidAmount ?: 0.0)).coerceAtLeast(0.0)

    /** معادل markInvoiceSent/unmarkInvoiceSent در وب */
    suspend fun setSent(invoiceId: Int, sent: Boolean) {
        val invoice = db.invoiceDao().getById(invoiceId)?.invoice ?: return
        db.invoiceDao().updateInvoice(invoice.copy(sent = sent))
    }

    /** معادل markInvoiceSettled در وب — کل مبلغ به‌عنوان واریزی ثبت می‌شود */
    suspend fun markSettled(invoiceId: Int) {
        val iw = db.invoiceDao().getById(invoiceId) ?: return
        val total = invoiceTotal(iw.items)
        db.invoiceDao().updateInvoice(iw.invoice.copy(status = "paid", paidAmount = total))
    }

    /** معادل unmarkInvoiceSettled در وب — به پیش‌فاکتور برمی‌گردد */
    suspend fun unmarkSettled(invoiceId: Int) {
        val invoice = db.invoiceDao().getById(invoiceId)?.invoice ?: return
        if (statusOf(invoice) != "paid") return
        db.invoiceDao().updateInvoice(invoice.copy(status = "draft", paidAmount = null))
    }

    /** معادل submitInvoicePayment در وب — بر اساس مبلغ واردشده، وضعیت خودکار تعیین می‌شود */
    suspend fun submitPayment(invoiceId: Int, amount: Double?) {
        val iw = db.invoiceDao().getById(invoiceId) ?: return
        val total = invoiceTotal(iw.items)
        val newInvoice = when {
            amount == null || amount <= 0 -> iw.invoice.copy(status = "draft", paidAmount = null)
            amount >= total -> iw.invoice.copy(status = "paid", paidAmount = total)
            else -> iw.invoice.copy(status = "partial", paidAmount = amount)
        }
        db.invoiceDao().updateInvoice(newInvoice)
    }

    /**
     * معادل دقیق sendAllToProduction در وب.
     * منطق تکرارنشدن: فقط آیتم‌هایی که sourceKey‌شان همین الان در صف تولید نیست
     * (چون حذف شده یا هنوز اضافه نشده) دوباره اضافه می‌شوند؛ آیتم‌های موجود در صف
     * دست‌نخورده می‌مانند. sourceKey = "{invoiceId}-{itemIndex}" — دقیقاً مثل وب.
     * برمی‌گرداند: شناسه‌ی اولین رکورد صف تولید مربوط به این فاکتور (برای apply خودکار
     * به فرم محاسبه، معادل applyProductionItem(firstId) در انتهای تابع وب) — یا اگر
     * هیچ‌کدام تازه اضافه نشدند، اولین رکورد موجود مربوط به همین فاکتور در صف فعلی.
     */
    suspend fun sendAllToProduction(invoiceId: Int): Int? {
        val iw = db.invoiceDao().getById(invoiceId) ?: return null
        if (iw.items.none { it.qty > 0 }) return null

        val existingKeys = db.productionQueueDao().getAllSourceKeys().toHashSet()
        var firstNewId: Int? = null
        val nowIso = Instant.now().toString()

        iw.items.forEachIndexed { idx, it ->
            if (it.qty <= 0) return@forEachIndexed
            val sourceKey = "$invoiceId-$idx"
            if (existingKeys.contains(sourceKey)) return@forEachIndexed
            val record = com.rasticpack.app.data.entities.ProductionQueueItemEntity(
                sourceKey = sourceKey,
                name = it.cartonName.ifBlank { "کارتن" },
                length = it.cartonLength, width = it.cartonWidth, height = it.cartonHeight,
                glue = if (it.glue > 0) it.glue else 4.0,
                sh = it.sh, sw = it.sw, layer = it.layer,
                customerName = iw.invoice.customerName,
                sentAtIso = nowIso
            )
            val newId = db.productionQueueDao().insert(record).toInt()
            if (firstNewId == null) firstNewId = newId
        }
        // معادل if(productionQueue.length>30) productionQueue.length=30;
        db.productionQueueDao().trimTo30()

        db.invoiceDao().updateInvoice(iw.invoice.copy(sentToProduction = true))

        if (firstNewId != null) return firstNewId

        // چیزی تازه اضافه نشد (همه از قبل در صف بودند) — اولین رکورد موجود مربوط به
        // همین فاکتور را برای apply خودکار برگردان.
        return db.productionQueueDao().findFirstByInvoicePrefix(invoiceId)?.id
    }

    /** معادل bundleClick/setInvoiceBundleSize/clearInvoiceBundleSize در وب */
    suspend fun setBundleSize(invoiceId: Int, itemId: Int, size: Int?) {
        val iw = db.invoiceDao().getById(invoiceId) ?: return
        val item = iw.items.find { it.id == itemId } ?: return
        db.invoiceDao().updateItem(item.copy(bundleSize = size))
    }

    /** معادل saveInvoiceEdit در وب — تغییر تعداد آیتم‌ها؛ ابتدا موجودی قبلی برگردانده،
     * سپس نیاز جدید هر ورق جمع و چک، و در صورت کافی‌بودن کسر می‌شود. اگر کافی نبود، هیچ
     * تغییری اعمال نمی‌شود (موجودی به حالت قبل بازمی‌گردد). */
    suspend fun editInvoiceItemQuantities(invoiceId: Int, newQuantities: Map<Int, Int>): String? {
        val iw = db.invoiceDao().getById(invoiceId) ?: return "فاکتور پیدا نشد."
        // ۱) موجودی قبلی هر ورق را برگردان (به همان مقداری که این فاکتور مصرف کرده بود)
        val sheets = db.inventoryDao().getAll().associateBy { it.id }.toMutableMap()
        iw.items.forEach { item ->
            val cur = sheets[item.sheetId] ?: return@forEach
            val newQty = cur.qty + item.qty
            sheets[item.sheetId] = cur.copy(qty = newQty)
        }
        // ۲) آیتم‌های جدید (qty>0) با تعداد جدید
        val newItems = iw.items.mapNotNull { item ->
            val q = newQuantities[item.id] ?: item.qty
            if (q <= 0) null else item.copy(qty = q)
        }
        // ۳) نیاز هر ورق را جمع بزن و چک کن
        val need = linkedMapOf<Int, Int>()
        newItems.forEach { need[it.sheetId] = (need[it.sheetId] ?: 0) + it.qty }
        for ((sheetId, qty) in need) {
            val sheet = sheets[sheetId]
            if (sheet == null || qty > sheet.qty) {
                val label = sheet?.let { " ${fmtDim(it.sh)}×${fmtDim(it.sw)}" } ?: ""
                return "موجودی ورق$label کافی نیست."
            }
        }
        // ۴) اعمال: ابتدا موجودی‌های برگردانده‌شده را ذخیره کن، سپس نیاز جدید را کم کن
        sheets.values.forEach { db.inventoryDao().update(it) }
        need.forEach { (sheetId, qty) ->
            val s = db.inventoryDao().getAll().find { it.id == sheetId } ?: return@forEach
            db.inventoryDao().update(s.copy(qty = s.qty - qty))
        }
        val stockAfterBySheet = need.keys.associateWith { sheetId ->
            db.inventoryDao().getAll().find { it.id == sheetId }?.qty ?: 0
        }
        val finalItems = newItems.map { it.copy(stockAfter = stockAfterBySheet[it.sheetId]) }
        finalItems.forEach { db.invoiceDao().updateItem(it) }
        // حذف آیتم‌هایی که qty صفر شدند از نمایش — چون InvoiceDao متد delete تکی آیتم ندارد،
        // آیتم‌های حذف‌شده را qty=0 نگه می‌داریم (نمایش آن‌ها در UI فیلتر می‌شود)
        val totalSheets = finalItems.sumOf { it.qty }
        db.invoiceDao().updateInvoice(
            iw.invoice.copy(totalSheets = totalSheets, editedAtIso = Instant.now().toString())
        )
        return null
    }

    /** معادل deleteInvoice در وب — موجودی ورق‌ها به انبار بازمی‌گردد */
    suspend fun deleteInvoice(invoiceId: Int) {
        val iw = db.invoiceDao().getById(invoiceId) ?: return
        iw.items.forEach { item ->
            val sheet = db.inventoryDao().getAll().find { it.id == item.sheetId } ?: return@forEach
            db.inventoryDao().update(sheet.copy(qty = sheet.qty + item.qty))
        }
        db.invoiceDao().deleteById(invoiceId)
    }
}
