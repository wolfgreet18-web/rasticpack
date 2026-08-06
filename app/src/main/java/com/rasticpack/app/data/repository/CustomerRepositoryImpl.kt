package com.rasticpack.app.data.repository

import com.rasticpack.app.data.dao.CustomerDao
import com.rasticpack.app.data.dao.InvoiceDao
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.domain.model.Customer
import com.rasticpack.app.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) — پیاده‌سازی واقعی CustomerRepository با Room ══
 * این کلاس جایگزین قدیمی `data/repo/CustomerRepository.kt` می‌شود (که خودش منطق
 * اعتبارسنجی را هم داشت). اینجا فقط دسترسی خام به داده + Mapping بین
 * `CustomerEntity` (Room) و `Customer` (domain) است — منطق اعتبارسنجی (نام
 * تکراری/خالی) به `domain/usecase/customer/*UseCase.kt` منتقل شده، طبق قانون
 * لایه‌بندی نقشه‌ی معماری (بخش ۱: presentation → domain ← data).
 *
 * فایل قدیمی `data/repo/CustomerRepository.kt` عمداً حذف نشده — چون طبق قوانین
 * پرامپت معرفی («هیچ فایل موجودی را به بهانه‌ی ساده‌سازی حذف نکن، مگر دستور
 * صریح باشد») حذف فایل نیاز به تأیید صریح دارد. علاوه‌بر این، تا رسیدن به مرحله
 * ۳.۳ (Invoice)، تعدادی از صفحات دیگر (فاکتورها، محاسبه کارتن، بکاپ) همچنان
 * مستقیماً به `data.repo.CustomerRepository` قدیمی وابسته‌اند — این وابستگی‌ها
 * عمداً در این مرحله دست‌نخورده می‌مانند.
 *
 * `InvoiceDao` هم اینجا تزریق می‌شود، فقط برای پیاده‌سازی `renameCustomerOnInvoices`
 * (معادل بخش «if(nameChanged)» در `saveCustomerEdit()` وب) — چون `Invoice` هنوز
 * domain model ندارد، این یک متد است که مستقیم با Entity کار می‌کند، نه یک
 * وابستگی کامل به لایه‌ی فاکتور.
 */
class CustomerRepositoryImpl @Inject constructor(
    private val dao: CustomerDao,
    private val invoiceDao: InvoiceDao
) : CustomerRepository {

    private fun CustomerEntity.toDomain() = Customer(
        id = id, name = name, company = company, address = address,
        phone = phone, lat = lat, lng = lng, locationLink = locationLink
    )

    private fun Customer.toEntity() = CustomerEntity(
        id = id, name = name, company = company, address = address,
        phone = phone, lat = lat, lng = lng, locationLink = locationLink
    )

    override fun observeAll(): Flow<List<Customer>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Customer> = dao.getAll().map { it.toDomain() }

    override suspend fun findById(id: Int): Customer? = dao.getById(id)?.toDomain()

    override suspend fun findByName(name: String): Customer? {
        val n = name.trim().lowercase()
        return getAll().find { it.name.trim().lowercase() == n }
    }

    override suspend fun insert(customer: Customer): Customer {
        val newId = dao.insert(customer.toEntity())
        return customer.copy(id = newId.toInt())
    }

    override suspend fun update(customer: Customer) {
        dao.update(customer.toEntity())
    }

    override suspend fun delete(id: Int) {
        val existing = dao.getById(id) ?: return
        dao.delete(existing)
    }

    override suspend fun renameCustomerOnInvoices(customerId: Int, newName: String) {
        val invoices = first(invoiceDao.observeByCustomer(customerId))
        invoices.forEach { iw ->
            invoiceDao.updateInvoice(iw.invoice.copy(customerName = newName))
        }
    }
}
