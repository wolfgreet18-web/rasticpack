package com.rasticpack.app.domain.repository

import com.rasticpack.app.domain.model.Customer
import kotlinx.coroutines.flow.Flow

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * قرارداد خواندن/نوشتن خام مشتری‌ها — بدون هیچ منطق اعتبارسنجی (نام تکراری/خالی).
 * منطق اعتبارسنجی طبق قانون لایه‌بندی به `domain/usecase/customer/*` منتقل شده،
 * دقیقاً مثل الگوی `DriverRepository`/`AddDriverUseCase` در مرحله ۲.
 *
 * توجه: عملیات مرتبط با «فاکتورهای یک مشتری» (برای مودال 🧾 در تب مشتری‌ها) عمداً
 * در این اینترفیس نیست — چون `Invoice` هنوز به لایه‌ی domain منتقل نشده (این کار
 * مرحله‌ی جداگانه‌ی ۳.۳ نقشه است). تا رسیدن به آن مرحله، `CustomersViewModel` برای
 * همان یک قابلیت (مودال فاکتورها) هنوز از `data.repo.CustomerRepository` قدیمی
 * استفاده می‌کند — این یک نقض موقت و شناخته‌شده‌ی قانون لایه‌بندی است، نه اشتباه.
 */
interface CustomerRepository {
    fun observeAll(): Flow<List<Customer>>
    suspend fun getAll(): List<Customer>
    suspend fun findById(id: Int): Customer?

    /** معادل findCustomerByName در وب — مقایسه‌ی نام بدون حساسیت به بزرگ/کوچکی و فاصله‌ی اضافه */
    suspend fun findByName(name: String): Customer?

    suspend fun insert(customer: Customer): Customer
    suspend fun update(customer: Customer)
    suspend fun delete(id: Int)

    /**
     * وقتی نام مشتری تغییر می‌کند، باید `customerName` روی تمام فاکتورهای همان مشتری
     * هم به‌روز شود — معادل دقیق بخش «if(nameChanged)» در `saveCustomerEdit()` وب.
     * چون `Invoice` هنوز domain model ندارد، این متد همچنان مستقیم روی جدول فاکتورها
     * (از طریق پیاده‌سازی data) کار می‌کند؛ امضای آن در سطح domain عمداً خنثی
     * (بدون هیچ نوع وابسته به Room) نگه داشته شده است.
     */
    suspend fun renameCustomerOnInvoices(customerId: Int, newName: String)
}
