package com.rasticpack.app.domain.usecase.settings

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.Driver
import com.rasticpack.app.domain.repository.DriverRepository
import javax.inject.Inject

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) ══
 * معادل دقیق `addDriver()` در 4.html:
 *   - نام راننده اجباری است.
 *   - نام باید یکتا باشد (بدون حساسیت به بزرگ/کوچکی حروف و فاصله‌ی اضافه‌ی
 *     ابتدا/انتها — همان `trim().lowercase()` منطق وب/Repository فعلی).
 *   - شماره تماس و پلاک اختیاری‌اند.
 * اولین استفاده‌ی واقعی از `RasticResult`/`RasticError` (ساخته‌شده در مرحله ۰.۴).
 */
class AddDriverUseCase @Inject constructor(
    private val repo: DriverRepository
) {
    suspend operator fun invoke(name: String, phone: String, plate: String): RasticResult<Driver> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return RasticResult.failure(RasticError.DriverNameBlank)
        }
        val taken = repo.getAll().any { it.name.trim().equals(trimmedName, ignoreCase = true) }
        if (taken) {
            return RasticResult.failure(RasticError.DriverNameDuplicateOnAdd)
        }
        val driver = Driver(name = trimmedName, phone = phone.trim(), plate = plate.trim())
        repo.insert(driver)
        return RasticResult.success(driver)
    }
}
