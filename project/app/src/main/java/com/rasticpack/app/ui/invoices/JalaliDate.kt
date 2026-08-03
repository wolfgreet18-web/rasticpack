package com.rasticpack.app.ui.invoices

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * معادل دقیق gregorianToJalali/jalaliToGregorian/getPersianMonthInfo در 4.html —
 * تبدیل تاریخ میلادی↔شمسی بدون هیچ کتابخانه‌ی خارجی، دقیقاً همان الگوریتم وب.
 */
object JalaliDate {
    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    fun toFaDigits(n: Any): String = n.toString().map { c -> if (c.isDigit()) FA_DIGITS[c - '0'] else c }.joinToString("")

    /** خروجی: Triple(jy, jm, jd) */
    fun gregorianToJalali(gy0: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy: Int
        var gy = gy0
        if (gy > 1600) { jy = 979; gy -= 1600 } else { jy = 0; gy -= 621 }
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) - 80 + gd + gDm[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm: Int; val jd: Int
        if (days < 186) { jm = 1 + days / 31; jd = 1 + (days % 31) } else { jm = 7 + (days - 186) / 30; jd = 1 + ((days - 186) % 30) }
        return Triple(jy, jm, jd)
    }

    /** خروجی: Triple(gy, gm, gd) */
    fun jalaliToGregorian(jy0: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var gy: Int
        var jy = jy0
        if (jy > 979) { gy = 1600; jy -= 979 } else { gy = 621 }
        var days = (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + 78 + jd +
            (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
        gy += 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * ((days - 1) / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { gy += (days - 1) / 365; days = (days - 1) % 365 }
        var gd = days + 1
        val salA = intArrayOf(0, 31, if ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > salA[gm]) { gd -= salA[gm]; gm++ }
        return Triple(gy, gm, gd)
    }

    fun jalaliMonthLength(jy: Int, jm: Int): Int {
        if (jm <= 6) return 31
        if (jm <= 11) return 30
        val isLeap = ((jy % 33) % 4) == 1
        return if (isLeap) 30 else 29
    }

    /** sortVal = jy*100+jm — برای مقایسه/مرتب‌سازی ماه‌ها؛ label = "فروردین ۱۴۰۴" */
    data class MonthInfo(val key: String, val sortVal: Int, val label: String)

    fun monthInfoFor(isoDateTime: String): MonthInfo {
        val zdt = isoToZoned(isoDateTime)
        val (jy, jm, _) = gregorianToJalali(zdt.year, zdt.monthValue, zdt.dayOfMonth)
        return MonthInfo(
            key = "$jy-" + jm.toString().padStart(2, '0'),
            sortVal = jy * 100 + jm,
            label = MONTH_NAMES[jm - 1] + " " + toFaDigits(jy)
        )
    }

    fun addMonthsToSortVal(sortVal: Int, delta: Int): Int {
        var jy = sortVal / 100
        var jm = sortVal % 100 + delta
        while (jm > 12) { jm -= 12; jy += 1 }
        while (jm < 1) { jm += 12; jy -= 1 }
        return jy * 100 + jm
    }

    fun isoToZoned(iso: String): ZonedDateTime =
        Instant.parse(iso).atZone(ZoneId.systemDefault())

    /** تاریخ+ساعت به فرمت ساده‌ی نمایشی — چون کتابخانه‌ی تقویم فارسی کامل اینجا نداریم،
     * تاریخ میلادی معادل + ساعت نشان داده می‌شود (نمایش شمسی کامل در چرخ تاریخ انجام می‌شود). */
    fun formatDateTimeShort(iso: String): String {
        return try {
            val zdt = isoToZoned(iso)
            val (jy, jm, jd) = gregorianToJalali(zdt.year, zdt.monthValue, zdt.dayOfMonth)
            val hh = zdt.hour.toString().padStart(2, '0')
            val mm = zdt.minute.toString().padStart(2, '0')
            "${toFaDigits(jd)} ${MONTH_NAMES[jm - 1]} ${toFaDigits(jy)} — ${toFaDigits(hh)}:${toFaDigits(mm)}"
        } catch (e: Exception) {
            iso
        }
    }

    fun dateOnlyKey(iso: String): String = try { isoToZoned(iso).toLocalDate().toString() } catch (e: Exception) { iso.take(10) }
}
