package com.rasticpack.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* توجه: فونت اصلی نسخه‌ی وب «وزیرمتن» (Vazirmatn) است. چون این مرحله فقط اسکلت پایه است
   و فایل فونت (.ttf) باینری است، فعلاً از فونت پیش‌فرض سیستم استفاده می‌کنیم — کاملاً فارسی
   را درست نشان می‌دهد، فقط ظاهرش با نسخه‌ی وب یکی نیست. در یکی از مراحل بعد، فایل
   Vazirmatn-Regular.ttf و Vazirmatn-Bold.ttf را در res/font/ قرار می‌دهیم و اینجا یک
   FontFamily با آن‌ها می‌سازیم — همه‌ی استایل‌های پایین بدون تغییر دیگری از آن استفاده
   خواهند کرد. */

val Typography = Typography(
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
