package com.rasticpack.app.domain.repository

import com.rasticpack.app.domain.model.Invoice
import com.rasticpack.app.domain.model.InvoiceItem
import com.rasticpack.app.domain.model.InvoiceWithItemsModel
import kotlinx.coroutines.flow.Flow

/**
 * قرارداد دامنه برای عملیات فاکتور — پیاده‌سازی واقعی (Room) این را در
 * data/repository/InvoiceRepositoryImpl.kt پیاده می‌کند.
 */
interface InvoiceRepository {
    fun observeAllWithItems(): Flow<List<InvoiceWithItemsModel>>
    fun observeByCustomer(customerId: Int): Flow<List<InvoiceWithItemsModel>>
    suspend fun getById(id: Int): InvoiceWithItemsModel?
    suspend fun deleteById(id: Int)

    /** ذخیره‌ی خام یک Invoice ویرایش‌شده — منطق تصمیم‌گیری (وضعیت جدید و...) در UseCase است. */
    suspend fun updateInvoice(invoice: Invoice)

    /** ذخیره‌ی خام یک InvoiceItem ویرایش‌شده (مثلاً bundleSize) */
    suspend fun updateItem(item: InvoiceItem)

    /** حذف خام یک آیتم فاکتور — معادل حالتی که در ویرایش، تعداد یک ردیف صفر می‌شود. */
    suspend fun deleteItem(itemId: Int)

    /**
     * درج یک فاکتور جدید همراه با آیتم‌هایش در یک تراکنش — معادل insertInvoiceWithItems
     * در Room DAO. UseCase مسئول منطق (چک موجودی، کسر موجودی و...) است؛ این متد فقط
     * درج خام را انجام می‌دهد و شناسه‌ی فاکتور تازه‌ساخته‌شده را برمی‌گرداند.
     */
    suspend fun insertWithItems(invoice: Invoice, items: List<InvoiceItem>): Int
}
