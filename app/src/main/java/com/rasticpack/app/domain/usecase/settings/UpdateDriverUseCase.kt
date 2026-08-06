package com.rasticpack.app.domain.usecase.settings

import com.rasticpack.app.core.result.RasticError
import com.rasticpack.app.core.result.RasticResult
import com.rasticpack.app.domain.model.Driver
import com.rasticpack.app.domain.repository.DriverRepository
import javax.inject.Inject

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) ══
 * معادل دقیق `saveDriverEdit()` در 4.html — همان قوانین AddDriverUseCase (نام اجباری،
 * یکتا) با این تفاوت که راننده‌ی در حال ویرایش خودش از چک تکراری‌بودن مستثنا می‌شود
 * (`excludeId`) و پیام تکراری بودن نام کمی متفاوت است («راننده دیگری با این نام...»).
 */
class UpdateDriverUseCase @Inject constructor(
    private val repo: DriverRepository
) {
    suspend operator fun invoke(id: Int, name: String, phone: String, plate: String): RasticResult<Driver> {
        val existing = repo.findById(id) ?: return RasticResult.failure(RasticError.DriverNotFound)
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return RasticResult.failure(RasticError.DriverNameBlank)
        }
        val taken = repo.getAll().any { it.id != id && it.name.trim().equals(trimmedName, ignoreCase = true) }
        if (taken) {
            return RasticResult.failure(RasticError.DriverNameDuplicateOnEdit)
        }
        val updated = existing.copy(name = trimmedName, phone = phone.trim(), plate = plate.trim())
        repo.update(updated)
        return RasticResult.success(updated)
    }
}
