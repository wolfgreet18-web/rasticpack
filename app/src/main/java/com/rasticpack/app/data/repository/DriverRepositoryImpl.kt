package com.rasticpack.app.data.repository

import com.rasticpack.app.data.dao.VanDriverDao
import com.rasticpack.app.data.entities.VanDriverEntity
import com.rasticpack.app.domain.model.Driver
import com.rasticpack.app.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) — پیاده‌سازی واقعی DriverRepository با Room ══
 * این کلاس جایگزین قدیمی `data/repo/DriverRepository.kt` می‌شود (که خودش منطق
 * اعتبارسنجی را هم داشت). اینجا فقط دسترسی خام به داده + Mapping بین
 * `VanDriverEntity` (Room) و `Driver` (domain) است — منطق اعتبارسنجی (نام
 * تکراری/خالی) به `domain/usecase/settings/*DriverUseCase.kt` منتقل شده،
 * طبق قانون لایه‌بندی نقشه‌ی معماری (بخش ۱: presentation → domain ← data).
 *
 * فایل قدیمی `data/repo/DriverRepository.kt` عمداً حذف نشده — چون طبق قوانین
 * پرامپت معرفی («هیچ فایل موجودی را به بهانه‌ی ساده‌سازی حذف نکن، مگر دستور
 * صریح باشد») حذف فایل نیاز به تأیید صریح دارد. اما دیگر در مسیر جدید
 * (`DriversViewModel` → UseCase → این کلاس) استفاده نمی‌شود.
 */
class DriverRepositoryImpl @Inject constructor(
    private val dao: VanDriverDao
) : DriverRepository {

    private fun VanDriverEntity.toDomain() = Driver(id = id, name = name, phone = phone, plate = plate)
    private fun Driver.toEntity() = VanDriverEntity(id = id, name = name, phone = phone, plate = plate)

    override fun observeAll(): Flow<List<Driver>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Driver> = first(observeAll())

    override suspend fun findById(id: Int): Driver? = getAll().find { it.id == id }

    override suspend fun insert(driver: Driver) {
        dao.insert(driver.toEntity())
    }

    override suspend fun update(driver: Driver) {
        dao.update(driver.toEntity())
    }

    override suspend fun delete(id: Int) {
        val existing = getAll().find { it.id == id } ?: return
        dao.delete(existing.toEntity())
    }
}
