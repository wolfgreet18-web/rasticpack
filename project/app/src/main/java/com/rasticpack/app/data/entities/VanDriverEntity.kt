package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** معادل هر آیتم آرایه‌ی `vanDrivers` در نسخه‌ی وب. */
@Entity(tableName = "van_drivers")
data class VanDriverEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val plate: String = ""
)
