package com.rasticpack.app.domain.usecase.customer

import com.rasticpack.app.ui.customers.LocationParsing
import com.rasticpack.app.ui.customers.ParsedCoords
import javax.inject.Inject

/**
 * ══ مرحله ۳.۱ (نقشه معماری v2.6) ══
 * معادل دقیق `parseCoordsFromText()` در 4.html — استخراج مختصات (lat,lng) از
 * لینک/متن اشتراک‌گذاری نشان یا گوگل‌مپ.
 *
 * توجه معماری: منطق واقعی همچنان در `ui/customers/LocationParsing.kt` است (که خودش
 * از قبل کاتلین خالص و بدون وابستگی به Room/Compose بود — فقط در پکیج اشتباهی
 * (`ui`) جا مانده بود). طبق قانون «هیچ فایل موجودی را بدون دستور صریح جابه‌جا/حذف
 * نکن»، آن فایل عمداً جابه‌جا نشده؛ این UseCase فقط یک لایه‌ی نازک domain روی آن
 * می‌کشد تا UseCase های بعدی (مثلاً در فاز محاسبه) بتوانند بدون import مستقیم از
 * پکیج `ui`، از این قابلیت استفاده کنند — و تا زمانی‌که `LocationParsing` به یک
 * پکیج خنثی (مثلاً `domain/util` یا `core`) منتقل شود، این پوشش جای آن را پر می‌کند.
 */
class ParseLocationFromTextUseCase @Inject constructor() {
    operator fun invoke(rawText: String?): ParsedCoords? = LocationParsing.parse(rawText)
}
