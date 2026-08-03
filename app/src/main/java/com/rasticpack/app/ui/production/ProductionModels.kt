package com.rasticpack.app.ui.production

/**
 * محاسبات عددی بلانک کارتن — معادل بخش‌های مشترک محاسباتی ابتدای calculate() در وب
 * (قبل از رسم SVG): girth, blankLen, blankHt, flapH, v1..v4, areaM2.
 * رسم گرافیکی خود بلانک (svgBlank/svgBlankArzi/...) به مرحله‌ی ۸.۲ موکول شده؛ این مدل
 * فقط اعداد را آماده می‌کند تا در همین مرحله به‌صورت جدول/متن قابل نمایش باشد.
 */
data class BlankCalcResult(
    val sL: Double, val sW: Double,
    val L: Double, val W: Double, val H: Double,
    val glue: Double,
    val girth: Double,      // 2*(L+W)
    val blankLen: Double,   // girth + glue  — طول کل بازشده
    val blankHt: Double,    // H + W — عرض کل بازشده
    val flapH: Double,      // W/2
    val v1: Double, val v2: Double, val v3: Double, val v4: Double,
    val areaM2: Double,
    val bestNL: Int,        // تعداد ردیف روی شیت خام (بر اساس blankLen)
    val bestNW: Int,        // تعداد ستون روی شیت خام (بر اساس blankHt)
    val bestCount: Int,
    val fits: Boolean
)

object ProductionCalc {
    /** معادل دقیق محاسبات ابتدای calculate() در وب (قبل از رسم SVG) */
    fun compute(sL: Double, sW: Double, L: Double, W: Double, H: Double, glueRaw: Double?): BlankCalcResult {
        val glue = glueRaw ?: 3.5
        val girth = 2 * (L + W)
        val blankLen = girth + glue
        val blankHt = H + W
        val flapH = W / 2
        val areaCm2 = blankLen * blankHt
        val areaM2 = Math.round((areaCm2 / 10000.0) * 10000) / 10000.0
        val v1 = L
        val v2 = L + W
        val v3 = 2 * L + W
        val v4 = girth
        val bestNL = if (blankLen > 0) (sL / blankLen).toInt() else 0
        val bestNW = if (blankHt > 0) (sW / blankHt).toInt() else 0
        val bestCount = bestNL * bestNW
        return BlankCalcResult(
            sL = sL, sW = sW, L = L, W = W, H = H, glue = glue,
            girth = girth, blankLen = blankLen, blankHt = blankHt, flapH = flapH,
            v1 = v1, v2 = v2, v3 = v3, v4 = v4,
            areaM2 = areaM2, bestNL = bestNL, bestNW = bestNW, bestCount = bestCount,
            fits = bestCount > 0
        )
    }
}
