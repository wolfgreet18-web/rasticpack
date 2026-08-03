package com.rasticpack.app.data.repo

import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** دسترسی به جدول customers — معادل بخش «TAB — مشتری‌ها» در وب. */
class CustomerRepository(private val db: AppDatabase) {

    suspend fun getAll(): List<CustomerEntity> = db.customerDao().getAll()

    fun observeAll(): Flow<List<CustomerEntity>> = db.customerDao().observeAll()

    /** معادل findCustomerByName در وب — مقایسه‌ی نام بدون حساسیت به بزرگ/کوچکی و فاصله‌ی اضافه */
    suspend fun findByName(name: String): CustomerEntity? {
        val n = name.trim().lowercase()
        return getAll().find { it.name.trim().lowercase() == n }
    }

    private suspend fun nameTaken(name: String, excludeId: Int? = null): Boolean {
        val n = name.trim().lowercase()
        return getAll().any { it.id != excludeId && it.name.trim().lowercase() == n }
    }

    /** معادل addCustomer در وب — بازمی‌گرداند: پیام خطا، یا null یعنی موفق */
    suspend fun add(
        name: String,
        company: String,
        address: String,
        phone: String,
        lat: Double?,
        lng: Double?,
        locationLink: String?
    ): String? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return "نام مشتری را وارد کنید."
        if (nameTaken(trimmedName)) return "مشتری با این نام قبلاً ثبت شده."
        db.customerDao().insert(
            CustomerEntity(
                name = trimmedName, company = company.trim(), address = address.trim(),
                phone = phone.trim(), lat = lat, lng = lng, locationLink = locationLink
            )
        )
        return null
    }

    /** معادل saveCustomerEdit در وب — در صورت تغییر نام، customerName در فاکتورهای مرتبط هم به‌روز می‌شود */
    suspend fun update(
        id: Int,
        name: String,
        company: String,
        address: String,
        phone: String,
        lat: Double?,
        lng: Double?,
        locationLink: String?
    ): String? {
        val existing = db.customerDao().getById(id) ?: return "مشتری پیدا نشد."
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return "نام مشتری را وارد کنید."
        if (nameTaken(trimmedName, excludeId = id)) return "مشتری دیگری با این نام قبلاً ثبت شده."
        val nameChanged = existing.name != trimmedName
        db.customerDao().update(
            existing.copy(
                name = trimmedName, company = company.trim(), address = address.trim(),
                phone = phone.trim(), lat = lat, lng = lng, locationLink = locationLink
            )
        )
        if (nameChanged) {
            val invoices = getInvoicesForCustomerOnce(id)
            invoices.forEach { iw ->
                db.invoiceDao().updateInvoice(iw.invoice.copy(customerName = trimmedName))
            }
        }
        return null
    }

    suspend fun delete(id: Int) {
        val c = db.customerDao().getById(id) ?: return
        db.customerDao().delete(c)
    }

    fun observeInvoicesForCustomer(customerId: Int): Flow<List<InvoiceWithItems>> =
        db.invoiceDao().observeByCustomer(customerId)

    private suspend fun getInvoicesForCustomerOnce(customerId: Int): List<InvoiceWithItems> {
        return first(db.invoiceDao().observeByCustomer(customerId))
    }

    suspend fun invoiceCountFor(customerId: Int): Int =
        getInvoicesForCustomerOnce(customerId).size
}
