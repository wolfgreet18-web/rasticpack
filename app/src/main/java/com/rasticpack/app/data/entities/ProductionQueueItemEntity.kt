package com.rasticpack.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * معادل هر آیتم آرایه‌ی `productionQueue` در نسخه‌ی وب — یک کارتن که از یک فاکتور به
 * تب «مراحل تولید» ارسال شده. sourceKey (= invoiceId-itemIndex) برای جلوگیری از تکرار
 * هنگام ارسال دوباره‌ی همان فاکتور استفاده می‌شود — دقیقاً مثل منطق sendAllToProduction در وب.
 */
@Entity(tableName = "production_queue")
data class ProductionQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceKey: String,
    val name: String,
    val length: Double,
    val width: Double,
    val height: Double,
    val glue: Double,
    val sh: Double,
    val sw: Double,
    val layer: String,
    val customerName: String = "",
    val sentAtIso: String
)
