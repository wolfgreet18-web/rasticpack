package com.rasticpack.app.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * ══ مرحله ۰.۲ — زیرساخت DispatcherProvider ══
 * چرا این وجود دارد: به‌جای صدازدن مستقیم `Dispatchers.IO`/`Dispatchers.Default` در وسط
 * هر Repository/UseCase (که تست‌کردن را سخت می‌کند چون در تست‌های JVM یک Dispatcher
 * قابل‌کنترل لازم است، نه دیسپچرهای واقعی اندروید)، همه‌جا این اینترفیس تزریق می‌شود.
 * در تست‌ها (فاز الف/تست) یک پیاده‌سازی fake با `TestDispatcher`/`UnconfinedTestDispatcher`
 * جایگزین `DefaultDispatcherProvider` می‌شود — بدون نیاز به تغییر کد Repository/UseCase.
 *
 * این همان زیرساختی است که مرحله ۱۶ (عملیات سنگین خارج از Main Thread، مثل موتور
 * چیدمان/پرت یا ساخت PDF فاکتور) و تمام تست‌های UseCase آینده به آن وابسته‌اند.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

/** پیاده‌سازی واقعی — همان Dispatchers واقعی کوروتین اندروید/کاتلین. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}
