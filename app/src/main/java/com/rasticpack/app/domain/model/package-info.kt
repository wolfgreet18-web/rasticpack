/**
 * ══ مرحله ۱ — اسکلت لایه‌ی domain ══
 * این پکیج فقط مدل‌های خالص کاتلین (data class) نگه می‌دارد — معادل هر Entity فعلی
 * در `data/entities/`، اما بدون هیچ import از `androidx.room` یا هر وابستگی دیگری
 * به لایه‌ی data. مدل اول (`Driver.kt`، معادل `VanDriverEntity`) در مرحله ۲ اضافه می‌شود.
 *
 * قانون وابستگی یک‌طرفه (بخش ۱ نقشه‌ی معماری): این پکیج هرگز از `data.*` یا `ui.*`
 * import نمی‌کند. جزئیات کامل در `docs/ARCHITECTURE_RULES.md`.
 *
 * این فایل صرفاً برای ثبت پوشه‌ی خالی در Git است (Git پوشه‌ی خالی را ردیابی نمی‌کند)؛
 * هیچ کد اجراشونده‌ای اینجا نیست.
 */
package com.rasticpack.app.domain.model
