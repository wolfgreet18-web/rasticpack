package com.rasticpack.app.domain.model

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) — اولین مدل خالص domain ══
 * معادل دقیق `VanDriverEntity` (`data/entities/VanDriverEntity.kt`) اما بدون هیچ
 * وابستگی به `androidx.room` — طبق قانون وابستگی یک‌طرفه (`domain` هرگز از `data`
 * import نمی‌کند). فیلدها عیناً همان سه فیلد نسخه‌ی وب (`vanDrivers` array):
 * name (اجباری، یکتا)، phone و plate (هر دو اختیاری).
 *
 * Mapping بین این مدل و `VanDriverEntity` در `data/repository/DriverRepositoryImpl.kt`
 * انجام می‌شود (مرحله ۲) — نه اینجا، چون این پکیج اجازه‌ی دیدن Entity را ندارد.
 */
data class Driver(
    val id: Int = 0,
    val name: String,
    val phone: String = "",
    val plate: String = ""
)
