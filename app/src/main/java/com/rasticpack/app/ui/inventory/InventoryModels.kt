package com.rasticpack.app.ui.inventory

/**
 * یک ردیف ورودی در ماشین‌حساب «کرایه حمل بار ماشین» (حالت دستی) — معادل هر آیتم
 * آرایه‌ی truckFreightItems در وب. category دقیقاً همان سه دسته‌ی قیمتی KT/2T/E است
 * (هم‌راستا با PRICE_CATEGORIES در وب)؛ از روی آن paperType/flute استخراج می‌شود.
 */
data class FreightItemInput(
    val localId: Int,
    val sh: String = "",
    val sw: String = "",
    val qty: String = "",
    val layer: String = "3",
    val category: String = "KT",
    val presetOpen: Boolean = false
)

/** یک ردیف خام معتبر بعد از محاسبه — برای اعمال روی موجودی/قیمت.
    shareCost = سهم هزینه‌ی همین ردیف از کرایه‌ی کل (area×qty×freightPerM2) —
    برای رسم نمودار میله‌ای سهم هر ابعاد (renderTruckFreightAutoChart در وب). */
data class FreightRow(
    val sh: Double,
    val sw: Double,
    val qty: Int,
    val layer: String,
    val flute: String,
    val paperType: String,
    val shareCost: Double = 0.0
)

/** نتیجه‌ی گروه‌بندی‌شده بر اساس دسته‌ی قیمتی (لایه+KT/2T/E) — معادل byKey در renderTruckFreightResult وب */
data class FreightGroupResult(
    val priceKey: String,
    val layer: String,
    val category: String,
    val dimsLabel: String,
    val shareCost: Double,
    val oldPrice: Double,
    val newPrice: Double,
    val rows: List<FreightRow>
)

/** خروجی کامل محاسبه‌ی کرایه — معادل truckFreightLastCalc در وب */
data class FreightCalcResult(
    val freightPerM2: Double,
    val totalArea: Double,
    val groups: List<FreightGroupResult>
)


/**
 * ابعاد آماده‌ی «حالت اتوماتیک» کرایه حمل — معادل دقیق TRUCK_FREIGHT_AUTO_DIMS_LIST در وب.
 * برای هر لایه یکسان است.
 */
val TRUCK_FREIGHT_AUTO_DIMS_LIST = listOf(
    280.0 to 240.0, 280.0 to 220.0, 280.0 to 200.0, 280.0 to 180.0, 280.0 to 160.0, 280.0 to 140.0,
    240.0 to 220.0, 240.0 to 180.0,
    220.0 to 180.0, 220.0 to 160.0, 220.0 to 140.0
)

/** فرمت ساده‌ی عدد ابعاد برای کلید — بدون اعشار صفر (مثلاً 240 نه 240.0) */
private fun fmtDimKey(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** کلید یک ابعاد آماده در حالت اتوماتیک — معادل truckFreightAutoDimKey در وب: "{layer}-{sh}-{sw}" */
fun autoDimKey(layer: String, sh: Double, sw: Double): String =
    "$layer-${fmtDimKey(sh)}-${fmtDimKey(sw)}"

/** وضعیت هر ابعاد در حالت اتوماتیک — معادل ترکیب truckFreightAutoSelected/Category/Qty در وب */
data class AutoDimState(
    val selected: Boolean = false,
    val category: String? = null,
    val qty: String = ""
)
