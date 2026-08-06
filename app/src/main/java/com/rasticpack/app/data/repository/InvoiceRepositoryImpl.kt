package com.rasticpack.app.data.repository

import com.rasticpack.app.data.dao.InvoiceDao
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import com.rasticpack.app.domain.model.Invoice
import com.rasticpack.app.domain.model.InvoiceItem
import com.rasticpack.app.domain.model.InvoiceWithItemsModel
import com.rasticpack.app.domain.repository.InvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ══ مرحله ۳.۳ (نقشه معماری) — پیاده‌سازی واقعی InvoiceRepository با Room ══
 * فقط دسترسی خام به داده + Mapping بین Entity های Room (`InvoiceEntity`,
 * `InvoiceItemEntity`, `InvoiceWithItems`) و مدل‌های خالص domain (`Invoice`,
 * `InvoiceItem`, `InvoiceWithItemsModel`) است. منطق ثبت/ویرایش/حذف فاکتور
 * (که در وب در توابع submitCalc2Invoice/saveInvoiceEdit/deleteInvoice بود) در
 * UseCase های `domain/usecase/invoice/*` قرار می‌گیرد.
 *
 * فایل قدیمی `data/repo/InvoiceRepository.kt` عمداً حذف نشده — چون `Calc2ViewModel`
 * و `InvoicesViewModel`/`InvoicesScreen` هنوز مستقیماً به آن وابسته‌اند (منطق
 * سنگین ثبت/ویرایش فاکتور که به `CartonCalcResult` از تب محاسبه وابسته است، در
 * این زیرمرحله هنوز به UseCase منتقل نشده — طبق قانون «مرحله‌به‌مرحله و
 * قابل‌تست»، این کار به یک زیرمرحله‌ی بعدی مجزا موکول شده تا ریسک هر تغییر کم
 * بماند).
 */
class InvoiceRepositoryImpl @Inject constructor(
    private val dao: InvoiceDao
) : InvoiceRepository {

    override fun observeAllWithItems(): Flow<List<InvoiceWithItemsModel>> =
        dao.observeAllWithItems().map { list -> list.map { it.toDomain() } }

    override fun observeByCustomer(customerId: Int): Flow<List<InvoiceWithItemsModel>> =
        dao.observeByCustomer(customerId).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Int): InvoiceWithItemsModel? =
        dao.getById(id)?.toDomain()

    override suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    override suspend fun updateInvoice(invoice: Invoice) {
        dao.updateInvoice(invoice.toEntity())
    }

    override suspend fun updateItem(item: InvoiceItem) {
        dao.updateItem(item.toEntity())
    }

    override suspend fun deleteItem(itemId: Int) {
        dao.deleteItemById(itemId)
    }

    override suspend fun insertWithItems(invoice: Invoice, items: List<InvoiceItem>): Int {
        val invoiceId = dao.insertInvoiceWithItems(invoice.toEntity(), items.map { it.toEntity() })
        return invoiceId.toInt()
    }
}

private fun Invoice.toEntity(): InvoiceEntity = InvoiceEntity(
    id = id, customerId = customerId, customerName = customerName, dateIso = dateIso,
    status = status, totalSheets = totalSheets, sent = sent, sentToProduction = sentToProduction,
    paidAmount = paidAmount, editedAtIso = editedAtIso
)

private fun InvoiceItem.toEntity(): InvoiceItemEntity = InvoiceItemEntity(
    id = id, invoiceId = invoiceId, sheetId = sheetId, sw = sw, sh = sh, layer = layer, qty = qty,
    cartonName = cartonName, cartonLength = cartonLength, cartonWidth = cartonWidth,
    cartonHeight = cartonHeight, glue = glue, cartonQty = cartonQty, unitPrice = unitPrice,
    lineTotal = lineTotal, itemProfit = itemProfit, stockAfter = stockAfter, bundleSize = bundleSize
)

private fun InvoiceWithItems.toDomain(): InvoiceWithItemsModel =
    InvoiceWithItemsModel(invoice = invoice.toDomain(), items = items.map { it.toDomain() })

private fun InvoiceEntity.toDomain(): Invoice = Invoice(
    id = id, customerId = customerId, customerName = customerName, dateIso = dateIso,
    status = status, totalSheets = totalSheets, sent = sent, sentToProduction = sentToProduction,
    paidAmount = paidAmount, editedAtIso = editedAtIso
)

private fun InvoiceItemEntity.toDomain(): InvoiceItem = InvoiceItem(
    id = id, invoiceId = invoiceId, sheetId = sheetId, sw = sw, sh = sh, layer = layer, qty = qty,
    cartonName = cartonName, cartonLength = cartonLength, cartonWidth = cartonWidth,
    cartonHeight = cartonHeight, glue = glue, cartonQty = cartonQty, unitPrice = unitPrice,
    lineTotal = lineTotal, itemProfit = itemProfit, stockAfter = stockAfter, bundleSize = bundleSize
)
