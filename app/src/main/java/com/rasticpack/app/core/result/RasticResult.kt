package com.rasticpack.app.core.result

/**
 * ══ مرحله ۰.۴ — الگوی یکدست مدیریت خطا (فقط قرارداد؛ هنوز جایی استفاده نمی‌شود) ══
 *
 * از فاز الف به بعد، هر UseCase به‌جای پرتاب Exception یا برگرداندن `String?`/یک
 * sealed class محلیِ مخصوص خودش (مثل `InvoiceRepository.SubmitResult` فعلی)،
 * دقیقاً یکی از این دو حالت را برمی‌گرداند:
 *   - `Success(data)` — عملیات موفق بود، `data` نتیجه‌ی واقعی است.
 *   - `Failure(error)` — عملیات ناموفق بود؛ `error` یکی از زیرکلاس‌های [RasticError] است
 *     و متن فارسی نمایشی آن از طریق `error.toUserMessage()` گرفته می‌شود.
 *
 * توابع کمکی (`map`, `onSuccess`, `onFailure`, `fold`) برای زنجیره‌کردن تمیز در
 * ViewModel ها هستند — دقیقاً همان الگویی که برای UseCase های فاز الف لازم است،
 * بدون نیاز به هر بار نوشتن `when` دستی.
 */
sealed class RasticResult<out T> {
    data class Success<out T>(val data: T) : RasticResult<T>()
    data class Failure(val error: RasticError) : RasticResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    /** داده در صورت موفقیت، یا null در صورت شکست — برای موارد ساده که فقط داده لازم است. */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    /** پیام خطا در صورت شکست، یا null در صورت موفقیت. */
    fun errorOrNull(): RasticError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    /** تبدیل داده‌ی موفق به یک نوع دیگر، بدون دست‌زدن به حالت شکست. */
    inline fun <R> map(transform: (T) -> R): RasticResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): RasticResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (RasticError) -> Unit): RasticResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /** تبدیل هر دو حالت به یک نوع مشترک — برای مواقعی که UI باید یک مقدار واحد بسازد. */
    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (RasticError) -> R): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }

    companion object {
        fun <T> success(data: T): RasticResult<T> = Success(data)
        fun failure(error: RasticError): RasticResult<Nothing> = Failure(error)
    }
}
