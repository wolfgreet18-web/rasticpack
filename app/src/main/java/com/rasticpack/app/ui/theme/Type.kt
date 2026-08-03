package com.rasticpack.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rasticpack.app.R

/* ══ زیرمرحله ۱۱.۲ — فونت فارسی وزیرمتن واقعی (به‌جای فونت پیش‌فرض سیستم) ══
   معادل دقیق وب: font-family:'Vazirmatn',Tahoma,sans-serif در body و h1..h4/دکمه‌ها.
   فایل‌های .ttf واقعی (از مخزن رسمی rastikerdar/vazirmatn) در res/font/ قرار دارند.
   FontFamily زیر ۴ وزن را پوشش می‌دهد که در وب هم استفاده شده بودند:
   ۴۰۰ (Normal/بدنه‌ی متن)، ۵۰۰ (Medium/labelSmall)، ۷۰۰ (Bold/تیتر‌ها و دکمه‌ها)،
   ۹۰۰ (Black/تیترهای خیلی درشت).
   وزن ۶۰۰ (SemiBold) در وب هم بود اما چون فایل جدا برایش نداریم،
   از Bold (۷۰۰) نزدیک‌ترین معادل بصری استفاده می‌شود — تفاوت آن‌قدر کم است
   که در گوشی عملاً محسوس نیست. */
val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_black, FontWeight.Black)
)

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    titleLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelSmall = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
