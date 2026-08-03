package com.rasticpack.app.ui.calc

import com.rasticpack.app.engine.CartonDimsSuggestion
import com.rasticpack.app.engine.SheetMatchResult
import com.rasticpack.app.engine.TwoPieceSuggestion
import com.rasticpack.app.engine.ZeroWasteSheetSuggestion

/**
 * یک ردیف ورودی کارتن در تب «محاسبه کارتن» — معادل هر آیتم calc2RowIds/calc2RowTemplate در وب.
 * مقادیر متنی نگه داشته می‌شوند (نه Double) چون فیلد ورودی خالی باید بتواند خالی بماند؛
 * تبدیل به عدد فقط هنگام محاسبه انجام می‌شود (دقیقاً مثل getNum در وب).
 */
data class CartonRowInput(
    val localId: Int,
    val length: String = "",
    val width: String = "",
    val height: String = "",
    val qty: String = "",
    val glue: String = "",
    val layer: String = "3",       // "3" یا "5"
    val flute: String = "C",       // "C" یا "E"
    val paperType: String = "2T",  // "KT" یا "2T"
    val error: String? = null
)

/** دو ردیف نمونه‌ی پیش‌فرض — معادل calc2SampleDefaults در 4.html (فقط برای ردیف اول هنگام باز شدن صفحه) */
fun defaultCartonRows(): List<CartonRowInput> = listOf(
    CartonRowInput(localId = 1, length = "60", width = "40", height = "40", qty = "100", glue = "4", layer = "3"),
    CartonRowInput(localId = 2, length = "30", width = "20", height = "30", qty = "100", glue = "2", layer = "3")
)

/**
 * نتیجه‌ی محاسبه‌شده‌ی یک ردیف کارتن — معادل هر آیتم آرایه‌ی `results` در runCalc2 (وب).
 * فرمول‌ها عیناً از 4.html کپی/معادل‌سازی شده‌اند.
 */
data class CartonCalcResult(
    val row: CartonRowInput,
    val name: String,
    val length: Double,
    val width: Double,
    val height: Double,
    val qty: Int,
    val glue: Double,
    val totalLength: Double,      // bh — طول بازشده = (L+W)*2 + glue
    val totalWidth: Double,       // bw — عرض بازشده = W+H
    val areaM2: Double,
    val basePrice: Double,
    val wasteCostPerUnit: Double,
    val wasteCostTotal: Double,
    val basePriceWithWaste: Double,
    val finalPrice: Double,       // قیمت فروش نهایی هر کارتن (با سود)
    val profitPerUnit: Double,
    val totalProfit: Double,
    val totalPrice: Double,
    val sheets: List<SheetMatchResult>, // بهترین ورق‌ها (اینجا فقط ۱ مورد نگه می‌داریم — .slice(0,1) در وب)
    var scrapWastePricePerCarton: Double = 0.0
)

/** خلاصه‌ی مجموع کل ردیف‌ها — برای نمایش «مبلغ کل / سود کل / ضایعات کل» */
data class Calc2Totals(
    val totalPrice: Double,
    val totalProfit: Double,
    val totalWaste: Double
)

/**
 * پیشنهادهای شیت/کارتن برای یک ردیف — معادل خروجی renderCalc2SuggestSheet در وب
 * (بخش «📐 پیشنهاد شیت»، بدون بخش برش دوتکه که در زیرمرحله‌ی ۳.۴ اضافه می‌شود).
 */
data class Calc2SuggestBundle(
    val zeroWasteOptions: List<ZeroWasteSheetSuggestion>,
    val existingClose: List<SheetMatchResult>,
    val cartonSuggestions: List<CartonDimsSuggestion>,
    val twoPiece: TwoPieceSuggestion
)
