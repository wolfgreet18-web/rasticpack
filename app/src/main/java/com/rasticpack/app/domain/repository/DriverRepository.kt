package com.rasticpack.app.domain.repository

import com.rasticpack.app.domain.model.Driver
import kotlinx.coroutines.flow.Flow

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) — اولین اینترفیس Repository در domain ══
 * قرارداد خالص برای دسترسی به راننده‌های وانت — معادل دقیق متدهای
 * `data/repo/DriverRepository.kt` فعلی، اما بدون هیچ جزئیات Room/SQL.
 * پیاده‌سازی واقعی (`data/repository/DriverRepositoryImpl.kt`) این اینترفیس را
 * implement می‌کند و مسئول Map‌کردن `VanDriverEntity` ↔ `Driver` است.
 *
 * توجه: خودِ اعتبارسنجی (نام خالی/تکراری) اینجا نیست — آن منطق در UseCase ها
 * (`domain/usecase/settings/*DriverUseCase.kt`) قرار دارد، طبق لایه‌بندی نقشه:
 * Repository فقط دسترسی خام به داده است، UseCase قوانین کسب‌وکار را اعمال می‌کند.
 */
interface DriverRepository {
    fun observeAll(): Flow<List<Driver>>
    suspend fun getAll(): List<Driver>
    suspend fun findById(id: Int): Driver?
    suspend fun insert(driver: Driver)
    suspend fun update(driver: Driver)
    suspend fun delete(id: Int)
}
