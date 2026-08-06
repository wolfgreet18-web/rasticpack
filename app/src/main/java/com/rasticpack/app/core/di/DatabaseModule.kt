package com.rasticpack.app.core.di

import android.content.Context
import com.rasticpack.app.data.AppDatabase
import com.rasticpack.app.data.dao.AppSettingsDao
import com.rasticpack.app.data.dao.CustomerDao
import com.rasticpack.app.data.dao.InventorySheetDao
import com.rasticpack.app.data.dao.InvoiceDao
import com.rasticpack.app.data.dao.ProductionQueueDao
import com.rasticpack.app.data.dao.VanDriverDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ══ مرحله ۰.۲ — تأمین AppDatabase و تمام DAO ها از طریق Hilt ══
 * این ماژول دقیقاً همان کاری را می‌کند که قبلاً `AppDatabase.getInstance(context)`
 * پراکنده در هر Composable/ViewModel انجام می‌داد — با این تفاوت که حالا فقط
 * *یک‌جا* تعریف شده و Hilt خودش تضمین می‌کند که یک نمونه‌ی Singleton واحد در
 * کل اپ استفاده شود (دقیقاً همان رفتار قبلی `@Volatile INSTANCE` در AppDatabase،
 * ولی بدون نیاز به نوشتن دستی این الگو در هرجا که به دیتابیس نیاز است).
 *
 * توجه: خودِ `AppDatabase.getInstance(context)` هنوز دست‌نخورده باقی می‌ماند
 * (برای سازگاری با نقاطی از کد که در مرحله ۰.۳ هنوز به Hilt منتقل نشده‌اند)؛
 * این ماژول فقط یک مسیر جایگزین و توصیه‌شده برای وابستگی‌های جدید/منتقل‌شده اضافه می‌کند.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun provideInventorySheetDao(db: AppDatabase): InventorySheetDao = db.inventoryDao()

    @Provides
    fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideInvoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()

    @Provides
    fun provideVanDriverDao(db: AppDatabase): VanDriverDao = db.vanDriverDao()

    @Provides
    fun provideProductionQueueDao(db: AppDatabase): ProductionQueueDao = db.productionQueueDao()

    @Provides
    fun provideAppSettingsDao(db: AppDatabase): AppSettingsDao = db.appSettingsDao()
}
