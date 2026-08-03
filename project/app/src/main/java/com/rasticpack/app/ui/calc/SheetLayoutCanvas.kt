package com.rasticpack.app.ui.calc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rasticpack.app.engine.CalculatorEngine
import com.rasticpack.app.engine.Grain
import com.rasticpack.app.engine.LayoutResult
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * ══ رسم گرافیکی چیدمان کارتن روی ورق ══
 * معادل دقیق توابع drawSheetCanvas و drawDirectionArrow در 4.html (بخش CORE ENGINE):
 *   - پس‌زمینه‌ی ورق با رنگ #f4f4f5
 *   - خط‌های موازی کم‌رنگ (grain texture) در جهت گوشت — افقی برای grain=horizontal، عمودی برای grain=vertical
 *   - جدول کارتن‌های چیده‌شده: سبز (#16a34a) اگر جهت گوشت صحیح باشد، قرمز (#dc2626) اگر نه
 *   - خط‌های سفید نیمه‌شفاف بین کارتن‌ها
 *   - ناحیه‌ی پرت (کارتن‌های چیده‌نشده) با رنگ خاکستری #94a3b8
 *   - کادر بیرونی #64748b
 *   - پیکان دوسر جهت گوشت وسط ورق
 * نسبت‌ها/رنگ‌ها/منطق عیناً از جاوااسکریپت کپی شده — هیچ عددی دلبخواهی تغییر نکرده.
 */

private val SheetBg = Color(0xFFF4F4F5)
private val GrainLineColor = Color(0x0F000000) // rgba(0,0,0,.06) تقریبی برای دید بهتر روی موبایل
private val CorrectColor = Color(0xFF16A34A)
private val WrongColor = Color(0xFFDC2626)
private val WasteColor = Color(0xFF94A3B8)
private val OuterBorderColor = Color(0xFF64748B)
private val CellBorderColor = Color(0xB3FFFFFF) // rgba(255,255,255,.7)

/**
 * رسم چیدمان یک کارتن bw×bh روی یک ورق sw×sh، با پیکان جهت گوشت.
 * معادل دقیق drawSheetCanvas(canvas, sw, sh, bw, bh, grain, layout) در وب.
 *
 * @param sw عرض ورق (سانتی‌متر) — محور افقی
 * @param sh طول ورق (سانتی‌متر) — محور عمودی
 * @param bw عرض کارتن بازشده (سانتی‌متر)
 * @param bh طول کارتن بازشده (سانتی‌متر)
 * @param grain جهت گوشت
 * @param layout نتیجه‌ی از‌پیش‌محاسبه‌شده‌ی calcLayout (برای جلوگیری از محاسبه‌ی تکراری)
 */
@Composable
fun SheetLayoutCanvas(
    sw: Double,
    sh: Double,
    bw: Double,
    bh: Double,
    grain: Grain,
    layout: LayoutResult,
    modifier: Modifier = Modifier
) {
    val isCorrect = CalculatorEngine.isGrainCorrect(bw, bh, grain)
    val cellColor = if (isCorrect) CorrectColor else WrongColor

    // نسبت طول‌به‌عرض واقعی ورق حفظ می‌شود — دقیقاً مثل وب که canvas.width/height را متناسب با sw/sh تنظیم می‌کند.
    val aspect = (sw / sh).toFloat().let { if (it.isFinite() && it > 0f) it else 1f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(6.dp))
            .background(SheetBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
            val cw = size.width
            val ch = size.height

            // ── بافت گوشت (grain texture) — خط‌های موازی کم‌رنگ ──
            val stepPx = (6f * (min(cw, ch) / 300f)).coerceAtLeast(4f)
            if (grain == Grain.HORIZONTAL) {
                var y = 0f
                while (y <= ch) {
                    drawLine(GrainLineColor, Offset(0f, y), Offset(cw, y), strokeWidth = 1f)
                    y += stepPx
                }
            } else {
                var x = 0f
                while (x <= cw) {
                    drawLine(GrainLineColor, Offset(x, 0f), Offset(x, ch), strokeWidth = 1f)
                    x += stepPx
                }
            }

            // ── جدول کارتن‌های چیده‌شده ──
            val bwPx = (bw / sw).toFloat() * cw
            val bhPx = (bh / sh).toFloat() * ch
            if (layout.rows > 0 && layout.cols > 0 && bwPx > 0f && bhPx > 0f) {
                for (row in 0 until layout.rows) {
                    for (col in 0 until layout.cols) {
                        val x = col * bwPx
                        val y = row * bhPx
                        drawRect(cellColor, topLeft = Offset(x, y), size = Size(bwPx, bhPx))
                        drawRect(
                            CellBorderColor,
                            topLeft = Offset(x, y),
                            size = Size(bwPx, bhPx),
                            style = Stroke(width = 1f)
                        )
                    }
                }
            }

            // ── ناحیه‌ی پرت (خاکستری) — سمت راست و پایین ناحیه‌ی استفاده‌شده ──
            val usedW = layout.cols * bwPx
            val usedH = layout.rows * bhPx
            if (usedW < cw) {
                drawRect(WasteColor, topLeft = Offset(usedW, 0f), size = Size(cw - usedW, ch))
            }
            if (usedH < ch) {
                drawRect(WasteColor, topLeft = Offset(0f, usedH), size = Size(usedW, ch - usedH))
            }

            // ── کادر بیرونی ──
            drawRect(
                OuterBorderColor,
                topLeft = Offset(0f, 0f),
                size = Size(cw, ch),
                style = Stroke(width = 1.5f)
            )

            // ── پیکان دوسر جهت گوشت وسط ورق ──
            val margin = min(cw, ch) * 0.15f
            if (grain == Grain.HORIZONTAL) {
                drawDirectionArrow(Offset(margin, ch / 2f), Offset(cw - margin, ch / 2f))
            } else {
                drawDirectionArrow(Offset(cw / 2f, margin), Offset(cw / 2f, ch - margin))
            }
        }
    }
}

/**
 * پیکان دوسر (با هاله‌ی سفید زیر خط سیاه، برای دید بهتر روی هر رنگ پس‌زمینه) —
 * معادل دقیق drawDirectionArrow(ctx,x1,y1,x2,y2) در وب: خط + سرپیکان در هر دو انتها،
 * ابتدا با خط سفید ضخیم‌تر (هاله) سپس خط سیاه نازک‌تر روی آن.
 */
private fun DrawScope.drawDirectionArrow(p1: Offset, p2: Offset) {
    val hl = 12f
    val angle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())

    fun headPath(px: Float, py: Float, ang: Double): Path {
        val a1 = ang - Math.PI / 6
        val a2 = ang + Math.PI / 6
        return Path().apply {
            moveTo(px, py)
            lineTo(px - (hl * cos(a1)).toFloat(), py - (hl * sin(a1)).toFloat())
            lineTo(px - (hl * cos(a2)).toFloat(), py - (hl * sin(a2)).toFloat())
            close()
        }
    }

    // هاله‌ی سفید (خط ضخیم‌تر) سپس خط سیاه نازک‌تر — دقیقاً همان دو پاس در وب
    val passes = listOf(
        6f to Color(0xEBFFFFFF), // rgba(255,255,255,.92)
        2.5f to Color(0xFF18181B)
    )
    for ((lw, color) in passes) {
        drawLine(color, p1, p2, strokeWidth = lw, cap = StrokeCap.Round)
        drawPath(headPath(p2.x, p2.y, angle), color)
        drawPath(headPath(p1.x, p1.y, angle + Math.PI), color)
    }
}

/**
 * برچسب کوچک روی گوشه‌ی تصویر — معادل .grain-badge در وب («↔ جهت گوشت: افقی» / «↕ جهت گوشت: عمودی»).
 * چون Canvas کامپوز متن فارسی راست‌به‌چپ را به‌سادگی جابه‌جا نمی‌کند، این برچسب به‌صورت
 * یک Composable معمولی (نه رسم روی Canvas) روی گوشه‌ی بالا-راست تصویر قرار می‌گیرد — دقیقاً
 * مثل موقعیت absolute (top:10px;right:10px) در CSS وب.
 */
@Composable
fun GrainBadgeLabel(grain: Grain): String =
    if (grain == Grain.HORIZONTAL) "↔ جهت گوشت: افقی" else "↕ جهت گوشت: عمودی"
