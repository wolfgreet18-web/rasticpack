package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * معادل هر آیتم آرایه‌ی `inventory` در نسخه‌ی وب (4.html) —
 * یک نوع ورق مشخص با ابعاد، لایه، فلوت، نوع کاغذ و موجودی.
 */
@Entity(tableName = "inventory_sheets")
data class InventorySheetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sw: Double,              // عرض ورق (سانتی‌متر)
    val sh: Double,              // طول ورق (سانتی‌متر)
    val layer: String = "3",     // "3" یا "5"
    val qty: Int = 0,            // موجودی (تعداد برگ)
    val flute: String = "C",     // "C" یا "E"
    val paperType: String = "2T" // "KT" یا "2T"
)
