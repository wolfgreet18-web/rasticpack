package com.rasticpack.app.ui.theme

import androidx.compose.ui.graphics.Color

// همون رنگ‌های :root در فایل HTML اصلی (4.html) — تا اپ اندروید دقیقاً هم‌رنگ نسخه‌ی وب باشد.
val Red800 = Color(0xFF991B1B)
val Red700 = Color(0xFFB91C1C)
val Red600 = Color(0xFFDC2626)
val Red100 = Color(0xFFFEE2E2)
val Red50 = Color(0xFFFEF2F2)

val Gold = Color(0xFFD97706)
val GoldLight = Color(0xFFFEF3C7)

val SurfaceMain = Color(0xFFFFFFFF)
val SurfaceAlt = Color(0xFFFAF8F4)
val SurfaceDeep = Color(0xFFF3EFE7)
val BorderColor = Color(0xFFE6E0D4)

val TextPrimary = Color(0xFF1C1917)
val TextSecondary = Color(0xFF57534E)
val TextMuted = Color(0xFFA8A29E)

val Green = Color(0xFF16A34A)
val GreenDark = Color(0xFF15803D)
val GreenBg = Color(0xFFF0FDF4)

val Blue = Color(0xFF2563EB)
val BlueDark = Color(0xFF1D4ED8)
val BlueBg = Color(0xFFEFF6FF)

// ══ رنگ‌های اختصاصی فیلدهای ورودی تب «محاسبه کارتن» — معادل دقیق c2-length/width/height/qty/glue در 4.html ══
val FieldLengthBg = Color(0xFF2563EB)   // L — آبی
val FieldLengthBorder = Color(0xFF1D4ED8)
val FieldWidthBg = Color(0xFFDC2626)    // W — قرمز
val FieldWidthBorder = Color(0xFFB91C1C)
val FieldHeightBg = Color(0xFF16A34A)   // H — سبز
val FieldHeightBorder = Color(0xFF15803D)
val FieldQtyBg = Color(0xFF1C1917)      // N — سیاه
val FieldQtyBorder = Color(0xFF000000)
val FieldGlueBg = Color(0xFFEAB308)     // F — زرد
val FieldGlueBorder = Color(0xFFCA8A04)

// رنگ‌های استاتوس‌باکس (stat-green/yellow/red/blue) — معادل [class*="stat-"] در وب
val StatGreenBorder = Green
val StatYellowBg = GoldLight
val StatYellowBorder = Gold
val StatRedBg = Red50
val StatRedBorder = Red600
val StatBlueBorder = Blue
