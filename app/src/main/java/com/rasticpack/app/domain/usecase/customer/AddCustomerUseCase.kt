package com.rasticpack.app.domain.usecase.customer

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.Customer
import com.rasticpack.app.domain.repository.CustomerRepository
import javax.inject.Inject

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * معادل دقیق `addCustomer()` در 4.html:
 *   - نام مشتری اجباری است.
 *   - نام باید یکتا باشد (بدون حساسیت به بزرگ/کوچکی حروف و فاصله‌ی اضافه‌ی
 *     ابتدا/انتها — همان `trim().toLowerCase()` منطق وب/Repository فعلی).
 *   - شرکت/آدرس/تلفن/موقعیت مکانی اختیاری‌اند.
 */
class AddCustomerUseCase @Inject constructor(
    private val repo: CustomerRepository
) {
    suspend operator fun invoke(
        name: String,
        company: String,
        address: String,
        phone: String,
        lat: Double?,
        lng: Double?,
        locationLink: String?
    ): RasticResult<Customer> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return RasticResult.failure(RasticError.CustomerNameBlank)
        }
        val taken = repo.getAll().any { it.name.trim().equals(trimmedName, ignoreCase = true) }
        if (taken) {
            return RasticResult.failure(RasticError.CustomerNameDuplicate)
        }
        val customer = Customer(
            name = trimmedName,
            company = company.trim(),
            address = address.trim(),
            phone = phone.trim(),
            lat = lat,
            lng = lng,
            locationLink = locationLink
        )
        val inserted = repo.insert(customer)
        return RasticResult.success(inserted)
    }
}
