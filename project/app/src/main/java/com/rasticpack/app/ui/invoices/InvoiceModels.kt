package com.rasticpack.app.ui.invoices

import com.rasticpack.app.data.entities.InvoiceItemEntity

/** معادل INVOICE_STATUS_INFO در وب */
data class InvoiceStatusInfo(val label: String)
val INVOICE_STATUS_INFO = mapOf(
    "draft" to InvoiceStatusInfo("پیش‌فاکتور"),
    "partial" to InvoiceStatusInfo("نیمه تسویه"),
    "paid" to InvoiceStatusInfo("تسویه‌شده")
)

/** معادل BUNDLE_SIZE_OPTIONS در وب */
val BUNDLE_SIZE_OPTIONS = listOf(15, 10, 20)

data class BundleCalc(val size: Int, val full: Int, val rem: Int, val totalBundles: Int)

/** معادل دقیق bundleCalc در وب */
fun bundleCalc(qty: Int, size: Int?): BundleCalc? {
    if (size == null || size <= 0 || qty <= 0) return null
    val full = qty / size
    val rem = qty % size
    val totalBundles = if (rem > 0) full + 1 else full
    return BundleCalc(size, full, rem, totalBundles)
}

/** معادل bundleSummaryText در وب */
fun bundleSummaryText(item: InvoiceItemEntity): String {
    val size = item.bundleSize ?: return ""
    val qty = item.cartonQty
    val c = bundleCalc(qty, size) ?: return ""
    return if (c.rem > 0) "${c.totalBundles} بسته (${c.full}×${c.size} + ${c.rem} عدد تکی)"
    else "${c.totalBundles} بسته ${c.size}تایی"
}

/** معادل invoiceTotalBundles در وب */
fun invoiceTotalBundles(items: List<InvoiceItemEntity>): Int? {
    var total = 0
    var any = false
    items.forEach { it ->
        val size = it.bundleSize ?: return@forEach
        val c = bundleCalc(it.cartonQty, size) ?: return@forEach
        total += c.totalBundles
        any = true
    }
    return if (any) total else null
}

fun fmtDimShort(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

fun layerLabelInv(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"

fun fmtNumInv(n: Double): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(n))
