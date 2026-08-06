/**
 * ══ مرحله ۱ — اسکلت لایه‌ی domain ══
 * این پکیج قلب منطق دامنه است: هر عملیات کسب‌وکار (معادل یک تابع در `4.html`، مثل
 * addDriver/submitCalc2Invoice/matchSheets) دقیقاً یک کلاس UseCase می‌شود که با
 * `@Inject constructor` ساخته می‌شود و یک `RasticResult<T>` (از `core.result`،
 * ساخته‌شده در مرحله ۰.۴) برمی‌گرداند — نه Exception و نه `String?` خام.
 *
 * زیرپکیج‌های آینده (طبق ساختار پوشه‌ی هدف در نقشه‌ی معماری):
 *   usecase/invoice/   usecase/calc/   usecase/inventory/   usecase/customer/   usecase/settings/
 * اولین UseCase های واقعی (AddDriverUseCase, UpdateDriverUseCase, DeleteDriverUseCase،
 * هرکدام همراه با Unit Test خودشان) در مرحله ۲ در `usecase/settings/` اضافه می‌شوند.
 *
 * قانون وابستگی یک‌طرفه: این پکیج فقط از `domain.model`, `domain.repository`, و
 * `core.*` استفاده می‌کند — هرگز مستقیماً از `data.*` (پیاده‌سازی واقعی Repository)
 * یا `androidx.room` وارد نمی‌شود؛ فقط اینترفیس `domain.repository` را می‌شناسد.
 *
 * این فایل صرفاً برای ثبت پوشه‌ی خالی در Git است؛ هیچ کد اجراشونده‌ای اینجا نیست.
 */
package com.rasticpack.app.domain.usecase
