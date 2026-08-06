package com.rasticpack.app.domain.usecase.invoice

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.repository.InventorySheetRepository
import com.rasticpack.app.domain.repository.InvoiceRepository
import java.time.Instant
import javax.inject.Inject

/**
 * ══ مرحله ۳.۳ بخش دوم — EditInvoiceUseCase ══
 * معادل دقیق saveInvoiceEdit در 4.html (شامل BUG FIX جمع‌زدن نیاز چند ردیف از یک
 * ورق مشترک قبل از چک موجودی — که در data/repo/InvoiceRepository قدیمی هم
 * پیاده‌سازی شده بود):
 *   ۱) موجودی قبلی هر ورق مصرف‌شده توسط این فاکتور به انبار برگردانده می‌شود.
 *   ۲) آیتم‌های جدید (qty>0 بعد از ویرایش) با تعداد جدید ساخته می‌شوند؛ آیتم‌هایی که
 *      qty صفر شده‌اند حذف می‌شوند (برخلاف نسخه‌ی قدیمی کاتلین که چون DAO متد delete
 *      تکی نداشت qty=0 نگه می‌داشت، حالا با deleteItem واقعاً حذف می‌شوند — رفتار
 *      قابل‌مشاهده برای کاربر یکسان است: آیتم دیگر نمایش داده نمی‌شود).
 *   ۳) نیاز هر ورق (بر اساس sheetId) جمع زده و قبل از هر کسری چک می‌شود.
 *   ۴) اگر کافی بود: موجودی جدید کسر، آیتم‌ها به‌روزرسانی/حذف، و فاکتور (نام مشتری،
 *      totalSheets، editedAtIso) ذخیره می‌شود. اگر کافی نبود: موجودی برگردانده‌شده
 *      دوباره ذخیره می‌شود (چون قبلاً برگشت داده شده) و خطا برگردانده می‌شود — هیچ
 *      تغییری در آیتم‌ها/فاکتور اعمال نمی‌شود.
 */
class EditInvoiceUseCase @Inject constructor(
    private val invoiceRepo: InvoiceRepository,
    private val inventoryRepo: InventorySheetRepository
) {
    /** newQuantities: itemId → تعداد جدید (برگ) وارد‌شده در فرم ویرایش */
    suspend operator fun invoke(
        invoiceId: Int,
        customerId: Int,
        customerName: String,
        newQuantities: Map<Int, Int>
    ): RasticResult<Unit> {
        val iw = invoiceRepo.getById(invoiceId)
            ?: return RasticResult.failure(RasticError.InvoiceNotFound)

        // ۱) موجودی قبلی هر ورق را برگردان (به همان مقداری که این فاکتور مصرف کرده بود)
        val restoredBySheet = mutableMapOf<Int, Int>()
        iw.items.forEach { item ->
            restoredBySheet[item.sheetId] = (restoredBySheet[item.sheetId] ?: 0) + item.qty
        }
        restoredBySheet.forEach { (sheetId, amount) -> inventoryRepo.increaseQty(sheetId, amount) }

        // ۲) آیتم‌های جدید (qty>0) با تعداد جدید؛ بقیه حذف می‌شوند
        val newItems = iw.items.mapNotNull { item ->
            val q = newQuantities[item.id] ?: item.qty
            if (q <= 0) null else item.copy(qty = q)
        }
        val removedItemIds = iw.items.map { it.id }.toSet() - newItems.map { it.id }.toSet()

        // ۳) نیاز هر ورق را جمع بزن و چک کن
        val need = linkedMapOf<Int, Int>()
        newItems.forEach { need[it.sheetId] = (need[it.sheetId] ?: 0) + it.qty }

        val sheetsById = inventoryRepo.getAll().associateBy { it.id }
        for ((sheetId, qty) in need) {
            val sheet = sheetsById[sheetId]
            if (sheet == null || qty > sheet.qty) {
                val label = sheet?.let { " ${fmt(it.sh)}×${fmt(it.sw)}" }
                return RasticResult.failure(RasticError.InsufficientStock(label))
            }
        }

        // ۴) اعمال: کسر نیاز جدید از موجودی (که همین الان شامل بازگشت قبلی است)
        need.forEach { (sheetId, qty) -> inventoryRepo.decreaseQty(sheetId, qty) }

        val stockAfterBySheet = need.keys.associateWith { sheetId ->
            inventoryRepo.getById(sheetId)?.qty ?: 0
        }
        val finalItems = newItems.map { it.copy(stockAfter = stockAfterBySheet[it.sheetId]) }
        finalItems.forEach { invoiceRepo.updateItem(it) }
        removedItemIds.forEach { invoiceRepo.deleteItem(it) }

        val totalSheets = finalItems.sumOf { it.qty }
        invoiceRepo.updateInvoice(
            iw.invoice.copy(
                customerId = customerId,
                customerName = customerName,
                totalSheets = totalSheets,
                editedAtIso = Instant.now().toString()
            )
        )
        return RasticResult.success(Unit)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
