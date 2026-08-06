package com.rasticpack.app.domain.model

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * معادل دقیق `CustomerEntity` (`data/entities/CustomerEntity.kt`) اما بدون هیچ
 * وابستگی به `androidx.room` — طبق قانون وابستگی یک‌طرفه (`domain` هرگز از `data`
 * import نمی‌کند). فیلدها عیناً همان فیلدهای هر آیتم آرایه‌ی `customers` در وب:
 * name (اجباری، یکتا)، company/address/phone (اختیاری)، و موقعیت مکانی اختیاری
 * (lat/lng یا لینک اشتراک‌گذاری خام نشان/گوگل‌مپ — معادل customer.locationLink).
 *
 * Mapping بین این مدل و `CustomerEntity` در `data/repository/CustomerRepositoryImpl.kt`
 * انجام می‌شود (مرحله ۳.۱) — نه اینجا، چون این پکیج اجازه‌ی دیدن Entity را ندارد.
 */
data class Customer(
    val id: Int = 0,
    val name: String,
    val company: String = "",
    val address: String = "",
    val phone: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val locationLink: String? = null
)
