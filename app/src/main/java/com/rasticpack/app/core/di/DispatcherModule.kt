package com.rasticpack.app.core.di

import com.rasticpack.app.core.dispatcher.DefaultDispatcherProvider
import com.rasticpack.app.core.dispatcher.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ══ مرحله ۰.۲ — تأمین DispatcherProvider از طریق Hilt ══
 * در سراسر اپ، هرجا نیاز به اجرای کار سنگین خارج از Main Thread هست (طبق مرحله ۱۶ نقشه)،
 * این نمونه‌ی واحد (Singleton) با @Inject تزریق می‌شود؛ در تست‌ها این ماژول با یک
 * پیاده‌سازی fake جایگزین می‌شود (بدون تغییر کدی که از آن استفاده می‌کند).
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
