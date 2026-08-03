package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * معادل هر آیتم آرایه‌ی `inv.items[]` در نسخه‌ی وب (یک ردیف = یک نوع کارتن/ورق داخل
 * یک فاکتور). با حذف فاکتور، آیتم‌هایش هم با CASCADE پاک می‌شوند.
 */
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("invoiceId"), Index("sheetId")]
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val sheetId: Int,
    val sw: Double,
    val sh: Double,
    val layer: String,
    val qty: Int,                    // تعداد برگ ورق مصرف‌شده
    val cartonName: String = "",
    val cartonLength: Double = 0.0,
    val cartonWidth: Double = 0.0,
    val cartonHeight: Double = 0.0,
    val glue: Double = 0.0,
    val cartonQty: Int = 0,          // تعداد عدد کارتن
    val unitPrice: Double? = null,
    val lineTotal: Double? = null,
    val itemProfit: Double? = null,
    val stockAfter: Int? = null,
    val bundleSize: Int? = null      // اندازه بسته‌ی تسمه‌بندی (۱۰/۱۵/۲۰) — اختیاری
)
