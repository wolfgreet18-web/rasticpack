package com.rasticpack.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * ══ مرحله ۰.۱ — نصب زیرساخت Hilt ══
 * این کلاس فقط نقطه‌ی ورود Hilt به اپ است (طبق مستندات Hilt، هر اپی که از Hilt
 * استفاده می‌کند باید دقیقاً یک Application class با @HiltAndroidApp داشته باشد).
 *
 * عمداً در این مرحله هیچ منطق دیگری اینجا نوشته نمی‌شود — نه seed داده،
 * نه بازکردن دیتابیس، نه هیچ‌چیز دیگر. طبق قانون مرحله ۰.۱، این مرحله فقط
 * زیرساخت را نصب می‌کند تا کامپایل/اجرا با خیال راحت تست شود؛ ماژول‌های Hilت
 * (DatabaseModule, RepositoryModule, ...) و وصل‌کردن واقعی ViewModel ها در
 * مراحل بعدی (۰.۲ به بعد) اضافه می‌شوند.
 */
@HiltAndroidApp
class RasticApp : Application()
