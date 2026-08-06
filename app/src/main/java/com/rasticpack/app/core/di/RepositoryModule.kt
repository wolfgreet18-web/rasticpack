package com.rasticpack.app.core.di

import com.rasticpack.app.data.repository.CustomerRepositoryImpl
import com.rasticpack.app.data.repository.DriverRepositoryImpl
import com.rasticpack.app.data.repository.InventorySheetRepositoryImpl
import com.rasticpack.app.data.repository.InvoiceRepositoryImpl
import com.rasticpack.app.data.repository.ProductionQueueRepositoryImpl
import com.rasticpack.app.domain.repository.CustomerRepository
import com.rasticpack.app.domain.repository.DriverRepository
import com.rasticpack.app.domain.repository.InventorySheetRepository
import com.rasticpack.app.domain.repository.InvoiceRepository
import com.rasticpack.app.domain.repository.ProductionQueueRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ══ مرحله ۲ (نقشه معماری v2.5) — اولین @Binds برای اینترفیس‌های domain.repository ══
 * وقتی یک UseCase (مثل `AddDriverUseCase`) `domain.repository.DriverRepository` را
 * می‌خواهد، Hilt باید بداند کدام پیاده‌سازی واقعی را بسازد — این ماژول همان اتصال
 * (interface ↔ implementation) است. برخلاف `DatabaseModule` (که با `@Provides` یک
 * شیء واقعی می‌سازد)، اینجا از `@Binds` استفاده می‌شود چون فقط باید به Hilt بگوییم
 * «هرجا DriverRepository خواستند، DriverRepositoryImpl بده» — بدون منطق ساخت اضافه.
 *
 * با اضافه‌شدن هر Repository جدید به لایه‌ی domain (طبق مرحله ۳ نقشه: Customer،
 * InventorySheet، Invoice، ProductionQueueItem، AppSettings)، یک `@Binds` مشابه
 * همین‌جا اضافه می‌شود.
 *
 * ══ مرحله ۳.۱ — افزودن @Binds برای Customer ══
 * ══ مرحله ۳.۲ — افزودن @Binds برای InventorySheet ══
 * ══ مرحله ۳.۳ — افزودن @Binds برای Invoice ══
 * ══ مرحله ۳.۴ — افزودن @Binds برای ProductionQueueItem ══
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDriverRepository(impl: DriverRepositoryImpl): DriverRepository

    @Binds
    @Singleton
    abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository

    @Binds
    @Singleton
    abstract fun bindInventorySheetRepository(impl: InventorySheetRepositoryImpl): InventorySheetRepository

    @Binds
    @Singleton
    abstract fun bindInvoiceRepository(impl: InvoiceRepositoryImpl): InvoiceRepository

    @Binds
    @Singleton
    abstract fun bindProductionQueueRepository(impl: ProductionQueueRepositoryImpl): ProductionQueueRepository
}
