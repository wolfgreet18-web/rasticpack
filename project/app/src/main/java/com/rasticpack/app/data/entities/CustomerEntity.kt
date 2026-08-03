package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * معادل هر آیتم آرایه‌ی `customers` در نسخه‌ی وب — نام، شرکت، آدرس، تلفن، و موقعیت مکانی
 * اختیاری (lat/lng یا لینک اشتراک‌گذاری خام نشان/گوگل‌مپ).
 */
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val company: String = "",
    val address: String = "",
    val phone: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val locationLink: String? = null
)
