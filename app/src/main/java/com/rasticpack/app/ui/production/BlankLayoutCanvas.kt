package com.rasticpack.app.ui.production

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/*
 * ══ زیرمرحله‌ی ۸.۲ — رسم گرافیکی بلانک کارتن ══
 * معادل ساده‌شده‌ی svgBlank(...) و svgSheetCutOnly(...) در 4.html (بخش TAB — مراحل تولید).
 * چون رسم مستقیم متن فارسی روی Canvas کامپوز راست‌به‌چپ نمی‌شود، برچسب‌های اندازه‌گیری
 * («طول=۴۰»، «عرض=۲۰» و...) به‌صورت جدول/لیست Composable جدا زیر تصویر نمایش داده می‌شوند —
 * همان اطلاعات، فقط جایگاه نمایش متفاوت با SVG وب که متن را مستقیم کنار خط می‌گذاشت.
 *
 * رنگ‌ها دقیقاً از CLR در وب:
 *   W (عرض) = #fed7aa   L (طول) = #bbf7d0   F (درپوش) = #fbcfe8   G (لپ چسب) = #fef08a
 *   fold (خط تا) = #2563eb   cut (خط برش) = #dc2626
 */

private val ColW = Color(0xFFFED7AA)
private val ColL = Color(0xFFBBF7D0)
private val ColF = Color(0xFFFBCFE8)
private val ColG = Color(0xFFFEF08A)
private val ColFold = Color(0xFF2563EB)
private val ColCut = Color(0xFFDC2626)
private val PaperBg = Color(0xFFFFFBF0)
private val PaperBorder = Color(0xFF374151)
private val WasteColor = Color(0xFF94A3B8)
private val CellFill = Color(0xFFFEF3C7)

/**
 * رسم بلانک تک‌کارتن (پنل‌های طول/عرض/طول/عرض + درپوش‌ها + لپ چسب) — معادل svgBlank در وب.
 * محور افقی = طول کل بازشده (blankLen)، محور عمودی = عرض کل بازشده (blankHt = H+W).
 */
@Composable
fun BlankSingleCanvas(
    r: BlankCalcResult,
    modifier: Modifier = Modifier
) {
    val aspect = (r.blankLen / r.blankHt).toFloat().let { if (it.isFinite() && it > 0f) it else 1f }
        .coerceIn(0.3f, 3.2f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(6.dp))
            .background(PaperBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
            val cw = size.width
            val ch = size.height
            val scX = cw / r.blankLen.toFloat()
            val scY = ch / r.blankHt.toFloat()

            fun px(c: Double) = (c * scX).toFloat()
            fun py(c: Double) = (c * scY).toFloat()

            val flapHpx = py(r.flapH)
            val v1x = px(r.v1); val v2x = px(r.v2); val v3x = px(r.v3); val v4x = px(r.v4)
            val gluePx = px(r.glue)

            drawRect(PaperBg, topLeft = Offset.Zero, size = Size(cw, ch))

            // پنل‌های اصلی: طول(L) عرض(W) طول(L) عرض(W) — بین دو خط درپوش (flapH .. flapH+H)
            val bodyTop = flapHpx
            val bodyBottom = ch - flapHpx
            val bodyH = (bodyBottom - bodyTop).coerceAtLeast(0f)
            drawRect(ColL, topLeft = Offset(0f, bodyTop), size = Size(v1x, bodyH))
            drawRect(ColW, topLeft = Offset(v1x, bodyTop), size = Size((v2x - v1x).coerceAtLeast(0f), bodyH))
            drawRect(ColL, topLeft = Offset(v2x, bodyTop), size = Size((v3x - v2x).coerceAtLeast(0f), bodyH))
            drawRect(ColW, topLeft = Offset(v3x, bodyTop), size = Size((v4x - v3x).coerceAtLeast(0f), bodyH))
            // لپ چسب
            drawRect(ColG, topLeft = Offset(v4x, 0f), size = Size(gluePx.coerceAtLeast(0f), ch))
            // درپوش‌های بالا/پایین
            drawRect(ColF, topLeft = Offset(0f, 0f), size = Size(v4x, flapHpx))
            drawRect(ColF, topLeft = Offset(0f, bodyBottom), size = Size(v4x, flapHpx))

            // کادر بیرونی
            drawRect(PaperBorder, topLeft = Offset.Zero, size = Size(cw, ch), style = Stroke(width = 2.5f))

            val dashFold = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
            val dashCut = PathEffect.dashPathEffect(floatArrayOf(7f, 4f))

            // خطوط تا عمودی (v1,v2,v3) — آبی
            listOf(v1x, v2x, v3x).forEach { x ->
                drawLine(ColFold, Offset(x, 0f), Offset(x, ch), strokeWidth = 2.2f, pathEffect = dashFold)
            }
            // خط برش لپ چسب (v4) — قرمز
            drawLine(ColCut, Offset(v4x, 0f), Offset(v4x, ch), strokeWidth = 2.2f, pathEffect = dashCut)
            // خطوط تا افقی بالا/پایین درپوش
            drawLine(ColCut, Offset(0f, bodyTop), Offset(v4x, bodyTop), strokeWidth = 2.2f, pathEffect = dashFold)
            drawLine(ColCut, Offset(0f, bodyBottom), Offset(v4x, bodyBottom), strokeWidth = 2.2f, pathEffect = dashFold)
        }
    }
}

/**
 * رسم چیدمان بلانک‌ها روی شیت خام (فقط خطوط برش، بدون جزئیات پنل‌ها) — معادل ساده‌شده‌ی
 * svgSheetCutOnly در وب. محور افقی = عرض شیت (sW)، محور عمودی = طول شیت (sL).
 */
@Composable
fun BlankSheetCutCanvas(
    r: BlankCalcResult,
    modifier: Modifier = Modifier
) {
    val aspect = (r.sW / r.sL).toFloat().let { if (it.isFinite() && it > 0f) it else 1f }
        .coerceIn(0.3f, 3.2f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(6.dp))
            .background(PaperBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
            val cw = size.width
            val ch = size.height
            drawRect(PaperBg, topLeft = Offset.Zero, size = Size(cw, ch))

            val nW = r.bestNW
            val nL = r.bestNL
            if (nL > 0 && nW > 0) {
                val cellWpx = (r.blankHt / r.sW).toFloat() * cw
                val cellLpx = (r.blankLen / r.sL).toFloat() * ch
                for (row in 0 until nL) {
                    for (col in 0 until nW) {
                        val x = col * cellWpx
                        val y = row * cellLpx
                        drawRect(CellFill, topLeft = Offset(x, y), size = Size(cellWpx, cellLpx))
                        drawRect(
                            ColCut,
                            topLeft = Offset(x, y),
                            size = Size(cellWpx, cellLpx),
                            style = Stroke(width = 1.6f)
                        )
                    }
                }
                val usedW = nW * cellWpx
                val usedL = nL * cellLpx
                if (usedW < cw) drawRect(WasteColor, topLeft = Offset(usedW, 0f), size = Size(cw - usedW, ch))
                if (usedL < ch) drawRect(WasteColor, topLeft = Offset(0f, usedL), size = Size(usedW, ch - usedL))
            }
            drawRect(PaperBorder, topLeft = Offset.Zero, size = Size(cw, ch), style = Stroke(width = 2.5f))
        }
    }
}
