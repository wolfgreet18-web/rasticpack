package com.rasticpack.app.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/*
 * ══ موتور محاسبه (CORE ENGINE) ══
 * معادل دقیق بخش «CORE ENGINE» در نسخه‌ی وب (4.html):
 *   isGrainCorrect  ▸  calcLayout  ▸  matchSheets
 * تمام فرمول‌ها عیناً از جاوااسکریپت نسخه‌ی وب کپی/معادل‌سازی شده‌اند — هیچ عدد،
 * درصد، یا منطقی دلبخواهی تغییر نکرده است. اگر جایی نیاز به تغییر بود، ابتدا باید
 * دقیقاً همان تغییر در 4.html هم اعمال شود.
 */

/** یک ورق موجود در انبار — معادل هر آیتم آرایه‌ی `inventory` در نسخه‌ی وب. */
data class SheetItem(
    val id: Int,
    val sw: Double,                 // عرض ورق (سانتی‌متر)
    val sh: Double,                 // طول ورق (سانتی‌متر)
    val layer: String = "3",        // "3" یا "5"
    val qty: Int = 0,               // موجودی (تعداد برگ)
    val flute: String = "C",        // "C" یا "E"
    val paperType: String = "2T"    // "KT" یا "2T"
)

/** جهت گوشت (فلوت) کارتن روی ورق — دقیقاً دو حالت مثل نسخه‌ی وب. */
enum class Grain(val jsValue: String) {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical")
}

/** نتیجه‌ی خام چیدمان یک کارتن bw×bh روی یک ورق sw×sh — معادل خروجی calcLayout در وب. */
data class LayoutResult(
    val cols: Int,
    val rows: Int,
    val total: Int,          // تعداد کل کارتن قابل چیدمان روی این ورق
    val correct: Int,        // تعداد با جهت گوشت صحیح
    val wrong: Int,          // تعداد با جهت گوشت ناصحیح (total - correct)
    val wastePercent: Double // درصد پرت این یک ورق (۰ تا ۱۰۰)
)

/**
 * پیشنهاد ابعاد شیت خام برای پرت صفر — معادل هر آیتم خروجی computeZeroWasteSheetSuggestions در وب.
 */
data class ZeroWasteSheetSuggestion(
    val sw: Double,
    val sh: Double,
    val cols: Int,
    val rows: Int,
    val count: Int,
    val areaM2: Double,
    val wastePercent: Double
)

/**
 * پیشنهاد ابعاد کارتن نزدیک از روی موجودی — معادل هر آیتم خروجی suggestCartonDimsFromInventory در وب.
 */
data class CartonDimsSuggestion(
    val sheetId: Int,
    val sheetSh: Double,
    val sheetSw: Double,
    val sheetQty: Int,
    val length: Double,   // L پیشنهادی
    val width: Double,    // W پیشنهادی
    val height: Double,   // H (ثابت می‌ماند)
    val bw: Double,
    val bh: Double,
    val cols: Int,
    val rows: Int,
    val count: Int,
    val diff: Double
)

/**
 * پیشنهاد برش دو تکه — معادل خروجی computeTwoPieceSuggestion در وب.
 * singleBest می‌تواند null باشد (یعنی هیچ ورق موجودی برای بلانک یک‌تکه کافی نیست)؛
 * در آن صورت costPerCartonSingle/savingsPerCarton/totalSavings هم null می‌مانند
 * چون مقایسه‌ای با یک‌تکه ممکن نیست — عیناً همان رفتار وب.
 */
data class TwoPieceSuggestion(
    val pieceLen: Double,
    val pieceHt: Double,
    val piecesNeeded: Int,
    val best: SheetMatchResult?,
    val singleBest: SheetMatchResult?,
    val costPerCartonSingle: Double?,
    val costPerCartonTwo: Double?,
    val savingsPerCarton: Double?,
    val totalSavings: Double?
)

/** نتیجه‌ی تطبیق یک ورق موجود در انبار با نیاز کارتن — معادل هر آیتم خروجی matchSheets در وب. */
data class SheetMatchResult(
    val sheet: SheetItem,
    val layout: LayoutResult,
    val perSheet: Int,          // تعداد کارتن در هر ورق (= layout.total)
    val sheetsNeed: Int?,       // تعداد ورق لازم برای برآوردن کل نیاز؛ null معادل Infinity در جاوااسکریپت (یعنی perSheet=0)
    val canFulfill: Boolean,    // آیا موجودی این ورق برای کل نیاز کافی است
    val maxBoxes: Int,          // حداکثر کارتن قابل تولید با کل موجودی این ورق
    val shortage: Int,          // کسری (اگر موجودی کافی نباشد)
    val usedSheets: Int,        // تعداد ورقی که واقعاً استفاده می‌شود
    val wastePercent: Double,   // درصد پرت با احتساب usedSheets/usedBoxes (گرد‌شده به یک رقم اعشار — عیناً مثل وب)
    val wasteArea: Double,      // پرت هر ورق (سانتی‌متر مربع)
    val totalWasteArea: Double, // پرت کل usedSheets ورق (سانتی‌متر مربع)
    val score: Double           // امتیاز رتبه‌بندی — بیشتر = بهتر
)

object CalculatorEngine {

    /**
     * آیا جهت گوشت (فلوت) روی این چیدمان صحیح است؟
     * معادل دقیق: isGrainCorrect(bw,bh,grain) = grain==='horizontal' ? bw>=bh : bh>=bw
     */
    fun isGrainCorrect(bw: Double, bh: Double, grain: Grain): Boolean =
        if (grain == Grain.HORIZONTAL) bw >= bh else bh >= bw

    /**
     * چیدمان یک کارتن bw×bh روی یک ورق sw×sh (بدون در نظر گرفتن موجودی/نیاز).
     * معادل دقیق تابع calcLayout در 4.html.
     */
    fun calcLayout(sw: Double, sh: Double, bw: Double, bh: Double, grain: Grain): LayoutResult {
        val cols = floor(sw / bw).toInt()
        val rows = floor(sh / bh).toInt()
        val total = cols * rows
        val sheetArea = sw * sh
        val correct = if (isGrainCorrect(bw, bh, grain)) total else 0
        val waste = if (sheetArea > 0) ((sheetArea - total * bw * bh) / sheetArea) * 100 else 0.0
        return LayoutResult(
            cols = cols,
            rows = rows,
            total = total,
            correct = correct,
            wrong = total - correct,
            wastePercent = waste
        )
    }

    /**
     * بین چند ورق موجود در انبار، بهترین‌ها برای برآوردن نیاز `need` عدد کارتن bw×bh را
     * پیدا و بر اساس امتیاز (score) از بهترین به بدترین مرتب می‌کند.
     * معادل دقیق تابع matchSheets در 4.html.
     *
     * فیلترهای layer/flute/paperType دقیقاً مثل وب اختیاری‌اند (null = بدون فیلتر).
     */
    fun matchSheets(
        inventory: List<SheetItem>,
        bw: Double,
        bh: Double,
        need: Int,
        grain: Grain,
        layerFilter: String? = null,
        fluteFilter: String? = null,
        paperFilter: String? = null
    ): List<SheetMatchResult> {
        var usable = inventory.filter { it.sw >= bw && it.sh >= bh && it.qty > 0 }
        if (layerFilter != null) usable = usable.filter { it.layer == layerFilter }
        if (fluteFilter != null) usable = usable.filter { it.flute == fluteFilter }
        if (paperFilter != null) usable = usable.filter { it.paperType == paperFilter }
        if (usable.isEmpty()) return emptyList()

        return usable.map { s ->
            val layout = calcLayout(s.sw, s.sh, bw, bh, grain)
            val perSheet = layout.total
            // perSheet<=0 یعنی اصلاً روی این ورق جا نمی‌شود ← نیاز به ورق بی‌نهایت (Infinity در وب) → اینجا null
            val sheetsNeedRaw: Int? = if (perSheet > 0) ceil(need.toDouble() / perSheet).toInt() else null
            val canFulfill = sheetsNeedRaw != null && sheetsNeedRaw <= s.qty
            val maxBoxes = perSheet * s.qty
            val usedSheets = if (sheetsNeedRaw != null) min(sheetsNeedRaw, s.qty) else 0
            val usedBoxes = min(need, maxBoxes)
            val waste = if (usedSheets > 0)
                ((usedSheets * s.sw * s.sh - usedBoxes * bw * bh) / (usedSheets * s.sw * s.sh)) * 100
            else
                100.0
            val wasteArea = max(0.0, s.sw * s.sh - perSheet * bw * bh)
            val totalWasteArea = max(0.0, usedSheets * s.sw * s.sh - usedBoxes * bw * bh)
            val score = (if (layout.correct > 0) 1000.0 else 0.0) +
                (if (canFulfill) 500.0 else 0.0) +
                (100.0 - min(waste, 100.0)) +
                perSheet * 2.0

            SheetMatchResult(
                sheet = s,
                layout = layout,
                perSheet = perSheet,
                sheetsNeed = sheetsNeedRaw,
                canFulfill = canFulfill,
                maxBoxes = maxBoxes,
                shortage = max(0, need - maxBoxes),
                usedSheets = usedSheets,
                wastePercent = round(waste * 10) / 10.0,
                wasteArea = round(wasteArea),
                totalWasteArea = round(totalWasteArea),
                score = score
            )
        }.sortedByDescending { it.score }
    }

    /**
     * ابعاد بازشده‌ی کارتن (طول×عرض ورقی که باید بریده شود) از روی ابعاد نهایی کارتن.
     * معادل دقیق فرمول داخل runCalc2 در تب «محاسبه کارتن» (bw = W+H ، bh = 2(L+W)+چسب).
     * این فرمول در چند تب دیگر هم (پیشنهاد شیت، برش دوتکه، تولید) استفاده می‌شود، برای
     * همین به‌جای تکرار در هر تب، اینجا در موتور مشترک نگه داشته می‌شود.
     *
     * خروجی: Pair(bw=عرض بازشده, bh=طول بازشده) — دقیقاً همان ترتیبی که matchSheets(bw,bh,...) در وب می‌گیرد.
     */
    fun expandedCartonDims(length: Double, width: Double, height: Double, glue: Double): Pair<Double, Double> {
        val totalLength = (length + width) * 2 + glue   // bh
        val totalWidth = width + height                  // bw
        return totalWidth to totalLength
    }

    /**
     * پیشنهاد ابعاد شیت خام برای صفر کردن پرت.
     * معادل دقیق computeZeroWasteSheetSuggestions در 4.html: برای کارتن bw×bh، بین چیدمان‌های
     * ممکن (۱ تا maxCols/maxRows) دنبال ابعاد شیت رند (مضرب ۱۰ سانت) می‌گردد که کمترین پرت را بدهد.
     */
    fun computeZeroWasteSheetSuggestions(
        bw: Double,
        bh: Double,
        maxW: Double = 220.0,
        maxH: Double = 280.0,
        maxCols: Int = 12,
        maxRows: Int = 12
    ): List<ZeroWasteSheetSuggestion> {
        val step = 10.0
        val seen = HashSet<String>()
        val candidates = mutableListOf<ZeroWasteSheetSuggestion>()

        for (cols in 1..maxCols) {
            for (rows in 1..maxRows) {
                val rawSw = cols * bw
                val rawSh = rows * bh
                if (rawSw < 20 || rawSh < 20) continue
                val sw = ceil(rawSw / step) * step
                val sh = ceil(rawSh / step) * step
                val fits = (sw <= maxW && sh <= maxH) || (sw <= maxH && sh <= maxW)
                if (!fits) continue

                val actualCols = floor(sw / bw).toInt()
                val actualRows = floor(sh / bh).toInt()
                if (actualCols < 1 || actualRows < 1) continue

                val count = actualCols * actualRows
                val areaTotal = sw * sh
                val areaUsed = count * bw * bh
                val waste = if (areaTotal > 0) round(((areaTotal - areaUsed) / areaTotal) * 1000) / 10.0 else 0.0

                val key = "${sw}x${sh}"
                if (seen.contains(key)) continue
                seen.add(key)

                candidates.add(
                    ZeroWasteSheetSuggestion(
                        sw = sw, sh = sh,
                        cols = actualCols, rows = actualRows, count = count,
                        areaM2 = round((areaTotal / 10000.0) * 100) / 100.0,
                        wastePercent = waste
                    )
                )
            }
        }

        return candidates.sortedWith(
            compareBy<ZeroWasteSheetSuggestion> { it.wastePercent }
                .thenByDescending { it.count }
        ).take(6)
    }

    /** بهترین گزینه‌ها از بین شیت‌های موجود در انبار (همان لایه) — کمترین درصد پرت.
     *  معادل دقیق findClosestExistingSheets در وب. */
    fun findClosestExistingSheets(
        inventory: List<SheetItem>,
        bw: Double,
        bh: Double,
        layer: String,
        limit: Int = 3
    ): List<SheetMatchResult> {
        val items = inventory.filter { it.layer == layer }
        val scored = items.map { s ->
            val layout = calcLayout(s.sw, s.sh, bw, bh, Grain.HORIZONTAL)
            SheetMatchResult(
                sheet = s, layout = layout, perSheet = layout.total,
                sheetsNeed = null, canFulfill = false, maxBoxes = 0, shortage = 0,
                usedSheets = 0, wastePercent = layout.wastePercent, wasteArea = 0.0,
                totalWasteArea = 0.0, score = 0.0
            )
        }.filter { it.layout.total > 0 }
        return scored.sortedBy { it.wastePercent }.take(limit)
    }

    /**
     * پیشنهاد ابعاد کارتن نزدیک به درخواست مشتری که روی شیت‌های موجود در انبار پرت صفر بدهد.
     * معادل دقیق suggestCartonDimsFromInventory در 4.html.
     */
    fun suggestCartonDimsFromInventory(
        inventory: List<SheetItem>,
        origL: Double,
        origW: Double,
        origH: Double,
        glue: Double,
        layer: String,
        flute: String,
        paperType: String
    ): List<CartonDimsSuggestion> {
        val bwOrig = origW + origH
        val bhOrig = 2 * (origL + origW) + glue
        val maxW = 220.0
        val maxH = 280.0

        val sheets = inventory.filter {
            it.layer == layer &&
                it.flute == flute &&
                it.paperType == paperType &&
                it.qty > 0 &&
                ((it.sw <= maxW && it.sh <= maxH) || (it.sw <= maxH && it.sh <= maxW))
        }
        if (sheets.isEmpty()) return emptyList()

        data class DivOpt(val n: Int, val value: Double)

        fun divisorsNear(total: Double, target: Double, tol: Double): List<DivOpt> {
            val found = mutableListOf<DivOpt>()
            val maxN = floor(total / 5).toInt()
            for (n in 1..maxN) {
                val v = total / n
                if (kotlin.math.abs(v - target) <= tol) found.add(DivOpt(n, round(v * 10) / 10.0))
            }
            return found
        }

        fun search(tol: Double): List<CartonDimsSuggestion> {
            val out = mutableListOf<CartonDimsSuggestion>()
            sheets.forEach { sheet ->
                val wOpts = divisorsNear(sheet.sw, bwOrig, tol)
                val hOpts = divisorsNear(sheet.sh, bhOrig, tol)
                wOpts.forEach { wo ->
                    hOpts.forEach { ho ->
                        val newBw = wo.value
                        val newBh = ho.value
                        val newW = newBw - origH
                        val newL = (newBh - glue) / 2 - newW
                        if (newW <= 0 || newL <= 0) return@forEach
                        val diff = kotlin.math.abs(newL - origL) + kotlin.math.abs(newW - origW)
                        out.add(
                            CartonDimsSuggestion(
                                sheetId = sheet.id, sheetSh = sheet.sh, sheetSw = sheet.sw, sheetQty = sheet.qty,
                                length = round(newL * 10) / 10.0, width = round(newW * 10) / 10.0, height = origH,
                                bw = newBw, bh = newBh, cols = wo.n, rows = ho.n, count = wo.n * ho.n,
                                diff = round(diff * 10) / 10.0
                            )
                        )
                    }
                }
            }
            return out
        }

        var results: List<CartonDimsSuggestion> = emptyList()
        for (tol in listOf(3.0, 6.0, 10.0, 15.0, 20.0, 30.0)) {
            results = search(tol)
            if (results.isNotEmpty()) break
        }

        val seen = HashSet<String>()
        val uniq = results.filter {
            val key = "${it.sheetId}|${it.length}|${it.width}"
            if (seen.contains(key)) false else { seen.add(key); true }
        }
        return uniq.sortedWith(
            compareBy<CartonDimsSuggestion> { it.diff }.thenByDescending { it.count }
        ).take(5)
    }

    /**
     * پیشنهاد برش دو تکه — معادل دقیق computeTwoPieceSuggestion در 4.html.
     *
     * منطق: کارتن‌سازها گاهی بلانک را دقیقاً روی خط تای روبه‌روی لپ چسب اصلی (نقطه‌ی
     * v2 = L+W ، وسط دور کارتن) به دو تکه‌ی هم‌اندازه می‌برند: طول هر تکه = (L+W)+لب‌چسب
     * اضافه، عرض = H+W (بدون تغییر). این تابع بررسی می‌کند آیا چیدمان این تکه‌های
     * کوچک‌تر روی ورق‌های موجود پرت کمتری از بلانک یک‌تکه می‌دهد یا خیر.
     *
     * BUG FIX (هم‌راستا با نسخه‌ی وب): محاسبه‌ی هزینه/گزینه‌ی دوتکه مستقل از singleBest
     * است — یعنی حتی وقتی هیچ ورقی برای بلانک یک‌تکه کافی نیست (تنها سناریویی که برش
     * دوتکه واقعاً لازم می‌شود)، این تابع همچنان نتیجه برمی‌گرداند. singleBest فقط برای
     * مقایسه (صرفه‌جویی/هزینه‌ی اضافی نسبت به یک‌تکه) استفاده می‌شود.
     *
     * basePriceWithWaste: هزینه‌ی مواد هر کارتن با روش یک‌تکه (شامل سهم پرت) — همان
     * مقداری که در runCalc2/CartonCalcResult از قبل محاسبه شده؛ اگر singleBest=null باشد
     * این پارامتر بی‌اهمیت است (نادیده گرفته می‌شود).
     */
    fun computeTwoPieceSuggestion(
        inventory: List<SheetItem>,
        length: Double,
        width: Double,
        height: Double,
        glue: Double,
        qty: Int,
        layer: String,
        flute: String,
        paperType: String,
        pricePerM2: Double,
        singleBest: SheetMatchResult?,
        basePriceWithWaste: Double
    ): TwoPieceSuggestion {
        val grain = Grain.HORIZONTAL
        val pieceLen = length + width + glue      // (L+W) + لب چسب اضافه برای اتصال مجدد
        val pieceHt = width + height               // H+W — عرض بلانک بدون تغییر
        val piecesNeeded = qty * 2                  // هر کارتن به ۲ تکه نیاز دارد

        val matches = matchSheets(inventory, pieceHt, pieceLen, piecesNeeded, grain, layer, flute, paperType)
        val best = matches.firstOrNull()

        var costPerCartonSingle: Double? = null
        var costPerCartonTwo: Double? = null
        var savingsPerCarton: Double? = null
        var totalSavings: Double? = null

        if (best != null && pricePerM2 > 0.0) {
            val pieceAreaM2 = (pieceLen * pieceHt) / 10000.0
            val pieceBasePrice = pieceAreaM2 * pricePerM2 // هزینه خام هر تکه بدون پرت
            val pieceWasteCostPerUnit = if (best.perSheet > 0)
                ((best.wasteArea / 10000.0) * pricePerM2) / best.perSheet
            else 0.0
            costPerCartonTwo = 2 * (pieceBasePrice + pieceWasteCostPerUnit) // ۲ تکه به‌ازای هر کارتن — همیشه قابل محاسبه
            if (singleBest != null) {
                costPerCartonSingle = basePriceWithWaste
                savingsPerCarton = costPerCartonSingle - costPerCartonTwo
                totalSavings = savingsPerCarton * qty
            }
        }

        return TwoPieceSuggestion(
            pieceLen = round(pieceLen * 10) / 10.0,
            pieceHt = round(pieceHt * 10) / 10.0,
            piecesNeeded = piecesNeeded,
            best = best,
            singleBest = singleBest,
            costPerCartonSingle = costPerCartonSingle,
            costPerCartonTwo = costPerCartonTwo,
            savingsPerCarton = savingsPerCarton,
            totalSavings = totalSavings
        )
    }
}

