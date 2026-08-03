package com.rasticpack.app.ui.stats

import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.InvoiceWithItems
import com.rasticpack.app.ui.invoices.JalaliDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * معادل بخش «TAB — آمار سود» در 4.html (computeStatsData/getStatsBuckets/computeStatsLeaders).
 * همه‌ی محاسبات این فایل خالص (pure) هستند — بدون وابستگی به Compose یا دیتابیس.
 */

/** معادل calcInvoiceProfit/calcInvoiceTurnover در وب */
fun invoiceProfit(items: List<InvoiceItemEntity>): Double = items.sumOf { it.itemProfit ?: 0.0 }
fun invoiceTurnover(items: List<InvoiceItemEntity>): Double = items.sumOf { it.lineTotal ?: 0.0 }

enum class StatsPeriod { DAY, WEEK, MONTH, YEAR }

data class StatsBucket(val start: ZonedDateTime, val end: ZonedDateTime, val label: String)

data class StatsPoint(val label: String, val turnover: Double, val profit: Double, val count: Int)

private val FA_WEEKDAY_SHORT = listOf("ی", "د", "س", "چ", "پ", "ج", "ش") // getDay(): 0=یکشنبه
private val FA_MONTH_SHORT = listOf(
    "فرو", "ارد", "خرد", "تیر", "مرد", "شهر", "مهر", "آبا", "آذر", "دی", "بهم", "اسف"
)

/** روز هفته به سبک جاوااسکریپت Date#getDay(): 0=یکشنبه...6=شنبه.
 * java.time DayOfWeek: 1=دوشنبه...7=یکشنبه — تبدیل لازم است. */
private fun jsWeekday(z: ZonedDateTime): Int = z.dayOfWeek.value % 7 // MONDAY(1)->1 ... SUNDAY(7)->0

object StatsEngine {
    private val zone = ZoneId.systemDefault()
    private fun now() = ZonedDateTime.now(zone)

    /** معادل getStatsBuckets در وب */
    fun buckets(period: StatsPeriod): List<StatsBucket> {
        val n = now()
        return when (period) {
            StatsPeriod.DAY -> (0 until 24).map { h ->
                val start = n.toLocalDate().atStartOfDay(zone).plusHours(h.toLong())
                val end = start.plusHours(1)
                StatsBucket(start, end, JalaliDate.toFaDigits(h))
            }
            StatsPeriod.WEEK -> (6 downTo 0).map { i ->
                val start = n.toLocalDate().atStartOfDay(zone).minusDays(i.toLong())
                val end = start.plusDays(1)
                StatsBucket(start, end, FA_WEEKDAY_SHORT[jsWeekday(start)])
            }
            StatsPeriod.MONTH -> (4 downTo 0).map { i ->
                val end = n.toLocalDate().atStartOfDay(zone).minusDays((i * 7).toLong()).plusDays(1)
                val start = end.minusDays(7)
                StatsBucket(start, end, "هفته " + (5 - i))
            }
            StatsPeriod.YEAR -> (11 downTo 0).map { i ->
                val start = n.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).minusMonths(i.toLong())
                val end = start.plusMonths(1)
                StatsBucket(start, end, FA_MONTH_SHORT[start.monthValue - 1])
            }
        }
    }

    /** معادل getStatsRange در وب */
    fun range(period: StatsPeriod): Pair<ZonedDateTime, ZonedDateTime> {
        val b = buckets(period)
        return b.first().start to b.last().end
    }

    /** معادل computeStatsData در وب */
    fun computeData(period: StatsPeriod, invoices: List<InvoiceWithItems>): List<StatsPoint> {
        val bs = buckets(period)
        return bs.map { b ->
            var turnover = 0.0; var profit = 0.0; var count = 0
            invoices.forEach { iw ->
                val t = try { Instant.parse(iw.invoice.dateIso).atZone(zone) } catch (e: Exception) { return@forEach }
                if (!t.isBefore(b.start) && t.isBefore(b.end)) {
                    turnover += invoiceTurnover(iw.items)
                    profit += invoiceProfit(iw.items)
                    count += 1
                }
            }
            StatsPoint(b.label, turnover, profit, count)
        }
    }

    data class LeaderCarton(val label: String, val qty: Int)
    data class LeaderBuyer(val name: String, val turnover: Double)
    data class LeaderProfitCustomer(val name: String, val profit: Double)
    data class Leaders(
        val topCarton: LeaderCarton?,
        val topBuyer: LeaderBuyer?,
        val topProfitCustomer: LeaderProfitCustomer?
    )

    /** معادل computeStatsLeaders در وب */
    fun computeLeaders(period: StatsPeriod, invoices: List<InvoiceWithItems>): Leaders {
        val (start, end) = range(period)
        val cartonMap = linkedMapOf<String, LeaderCarton>()
        val buyerMap = linkedMapOf<Any, LeaderBuyer>()
        val profitMap = linkedMapOf<Any, LeaderProfitCustomer>()

        invoices.forEach { iw ->
            val t = try { Instant.parse(iw.invoice.dateIso).atZone(zone) } catch (e: Exception) { return@forEach }
            if (t.isBefore(start) || !t.isBefore(end)) return@forEach
            val custKey: Any = iw.invoice.customerId
            val custName = iw.invoice.customerName

            iw.items.forEach { it ->
                val qty = it.cartonQty
                if (qty > 0) {
                    val dims = if (it.cartonLength > 0)
                        "${fmtDimShort(it.cartonLength)}×${fmtDimShort(it.cartonWidth)}×${fmtDimShort(it.cartonHeight)}"
                    else ""
                    val key = (it.cartonName.ifBlank { "کارتن" }) + "|" + dims + "|" + it.layer
                    val label = (it.cartonName.ifBlank { "کارتن" }) +
                        (if (dims.isNotBlank()) " $dims" else "") +
                        (" · " + layerLabelStats(it.layer))
                    val cur = cartonMap[key] ?: LeaderCarton(label, 0)
                    cartonMap[key] = cur.copy(qty = cur.qty + qty)
                }
                val turn = it.lineTotal ?: 0.0
                if (turn != 0.0) {
                    val cur = buyerMap[custKey] ?: LeaderBuyer(custName, 0.0)
                    buyerMap[custKey] = cur.copy(turnover = cur.turnover + turn)
                }
                val prof = it.itemProfit ?: 0.0
                if (prof != 0.0) {
                    val cur = profitMap[custKey] ?: LeaderProfitCustomer(custName, 0.0)
                    profitMap[custKey] = cur.copy(profit = cur.profit + prof)
                }
            }
        }

        val topCarton = cartonMap.values.maxByOrNull { it.qty }
        val topBuyer = buyerMap.values.maxByOrNull { it.turnover }
        val topProfitCustomer = profitMap.values.maxByOrNull { it.profit }
        return Leaders(topCarton, topBuyer, topProfitCustomer)
    }

    private fun fmtDimShort(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    private fun layerLabelStats(layer: String) = if (layer == "5") "پنج‌لایه" else "سه‌لایه"

    /** معادل formatShort در وب — اعداد بزرگ را به «میلیون/میلیارد/هزار» کوتاه می‌کند */
    fun formatShort(n: Double): String {
        val r = Math.round(n)
        val abs = Math.abs(r)
        val fmt = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US)
        return when {
            abs >= 1_000_000_000L -> trimZero(r / 1_000_000_000.0) + " میلیارد"
            abs >= 1_000_000L -> trimZero(r / 1_000_000.0) + " میلیون"
            abs >= 1_000L -> (r / 1000L).toString() + " هزار"
            else -> fmt.format(r)
        }
    }

    private fun trimZero(d: Double): String {
        val s = String.format(java.util.Locale.US, "%.1f", d)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    /** معادل periodLabel در renderStatsCharts */
    fun periodLabel(period: StatsPeriod): String = when (period) {
        StatsPeriod.DAY -> "امروز"
        StatsPeriod.WEEK -> "۷ روز اخیر"
        StatsPeriod.MONTH -> "۳۵ روز اخیر"
        StatsPeriod.YEAR -> "۱۲ ماه اخیر"
    }
}

fun fmtNumStats(n: Double): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(Math.round(n))
