package com.rasticpack.app.data.repo

import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.entities.VanDriverEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * دسترسی به جدول van_drivers — معادل بخش «تنظیمات — راننده وانت‌ها» (drivers-panel) در وب.
 * منطق دقیقاً معادل addDriver/saveDriverEdit/removeDriver در 4.html:
 *   - نام راننده اجباری و باید یکتا باشد (بدون حساسیت به بزرگ/کوچکی حروف و فاصله‌ی اضافه).
 *   - شماره تماس و پلاک اختیاری‌اند.
 *
 * ══ مرحله ۰.۲ — @Inject constructor ══
 * تنها تغییر این مرحله همین انوتیشن است — منطق داخل کلاس عیناً دست‌نخورده مانده.
 * چون `AppDatabase` از طریق `DatabaseModule` (core/di) به‌عنوان Singleton تأمین می‌شود،
 * Hilt می‌تواند این Repository را هم بدون هیچ سیم‌کشی دستی دیگری بسازد.
 */
class DriverRepository @Inject constructor(private val db: AppDatabase) {

    suspend fun getAll(): List<VanDriverEntity> = first(db.vanDriverDao().observeAll())

    fun observeAll(): Flow<List<VanDriverEntity>> = db.vanDriverDao().observeAll()

    private suspend fun nameTaken(name: String, excludeId: Int? = null): Boolean {
        val n = name.trim().lowercase()
        return getAll().any { it.id != excludeId && it.name.trim().lowercase() == n }
    }

    /** معادل addDriver در وب — بازمی‌گرداند: پیام خطا، یا null یعنی موفق */
    suspend fun add(name: String, phone: String, plate: String): String? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return "نام راننده را وارد کنید."
        if (nameTaken(trimmedName)) return "راننده‌ای با این نام قبلاً ثبت شده."
        db.vanDriverDao().insert(
            VanDriverEntity(name = trimmedName, phone = phone.trim(), plate = plate.trim())
        )
        return null
    }

    /** معادل saveDriverEdit در وب */
    suspend fun update(id: Int, name: String, phone: String, plate: String): String? {
        val existing = getAll().find { it.id == id } ?: return "راننده پیدا نشد."
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return "نام راننده را وارد کنید."
        if (nameTaken(trimmedName, excludeId = id)) return "راننده دیگری با این نام قبلاً ثبت شده."
        db.vanDriverDao().update(
            existing.copy(name = trimmedName, phone = phone.trim(), plate = plate.trim())
        )
        return null
    }

    /** معادل removeDriver در وب */
    suspend fun delete(id: Int) {
        val d = getAll().find { it.id == id } ?: return
        db.vanDriverDao().delete(d)
    }
}
