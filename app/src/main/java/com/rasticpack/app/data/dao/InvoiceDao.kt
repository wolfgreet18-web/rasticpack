package com.rasticpack.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {

    // ══ صفحه‌بندی (Pagination) — معادل PAGE_SIZE=50 در وب ══
    // فقط یک صفحه از فاکتورها هر بار خوانده می‌شود تا با هزاران رکورد هم اسکرول نرم بماند.
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY dateIso DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<InvoiceWithItems>

    @Transaction
    @Query("SELECT * FROM invoices ORDER BY dateIso DESC")
    fun observeAllWithItems(): Flow<List<InvoiceWithItems>>

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY dateIso DESC")
    fun observeByCustomer(customerId: Int): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: Int): InvoiceWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItemEntity>)

    @Transaction
    suspend fun insertInvoiceWithItems(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Long {
        val invoiceId = insertInvoice(invoice)
        insertItems(items.map { it.copy(invoiceId = invoiceId.toInt()) })
        return invoiceId
    }

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Update
    suspend fun updateItem(item: InvoiceItemEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM invoices")
    suspend fun clearInvoices()

    @Query("DELETE FROM invoice_items")
    suspend fun clearItems()
}
