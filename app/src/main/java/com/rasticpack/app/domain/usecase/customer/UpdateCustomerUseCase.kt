package com.rasticpack.app.domain.usecase.customer

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.Customer
import com.rasticpack.app.domain.repository.CustomerRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * معادل دقیق `saveCustomerEdit()` در 4.html — همان قوانین AddCustomerUseCase (نام
 * اجباری، یکتا) با این تفاوت که مشتری در حال ویرایش خودش از چک تکراری‌بودن مستثنا
 * می‌شود (`excludeId`). اگر نام واقعاً تغییر کرده باشد، `renameCustomerOnInvoices`
 * صدا زده می‌شود تا `customerName` روی فاکتورهای مرتبط هم به‌روز شود — دقیقاً معادل
 * بخش «if(nameChanged){ invoices.forEach(...) }» در وب.
 */
class UpdateCustomerUseCase @Inject constructor(
    private val repo: CustomerRepository
) {
    suspend operator fun invoke(
        id: Int,
        name: String,
        company: String,
        address: String,
        phone: String,
        lat: Double?,
        lng: Double?,
        locationLink: String?
    ): RasticResult<Customer> {
        val existing = repo.findById(id) ?: return RasticResult.failure(RasticError.CustomerNotFound())
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return RasticResult.failure(RasticError.CustomerNameBlank)
        }
        val taken = repo.getAll().any { it.id != id && it.name.trim().equals(trimmedName, ignoreCase = true) }
        if (taken) {
            return RasticResult.failure(RasticError.CustomerNameDuplicate)
        }
        val nameChanged = existing.name != trimmedName
        val updated = existing.copy(
            name = trimmedName,
            company = company.trim(),
            address = address.trim(),
            phone = phone.trim(),
            lat = lat,
            lng = lng,
            locationLink = locationLink
        )
        repo.update(updated)
        if (nameChanged) {
            repo.renameCustomerOnInvoices(id, trimmedName)
        }
        return RasticResult.success(updated)
    }
}
