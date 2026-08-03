package com.rasticpack.app.data.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * معادل یک آبجکت کامل فاکتور در وب (inv با inv.items همراهش) —
 * Room این دو جدول را خودش join می‌کند.
 */
data class InvoiceWithItems(
    @Embedded val invoice: InvoiceEntity,
    @Relation(parentColumn = "id", entityColumn = "invoiceId")
    val items: List<InvoiceItemEntity>
)
