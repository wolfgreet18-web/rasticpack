package com.rasticpack.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rasticpack.app.data.dao.AppSettingsDao
import com.rasticpack.app.data.dao.CustomerDao
import com.rasticpack.app.data.dao.InventorySheetDao
import com.rasticpack.app.data.dao.InvoiceDao
import com.rasticpack.app.data.dao.ProductionQueueDao
import com.rasticpack.app.data.dao.VanDriverDao
import com.rasticpack.app.data.entities.AppSettingsEntity
import com.rasticpack.app.data.entities.CustomerEntity
import com.rasticpack.app.data.entities.InventorySheetEntity
import com.rasticpack.app.data.entities.InvoiceEntity
import com.rasticpack.app.data.entities.InvoiceItemEntity
import com.rasticpack.app.data.entities.ProductionQueueItemEntity
import com.rasticpack.app.data.entities.VanDriverEntity

/**
 * دیتابیس اصلی اپ — معادل STORAGE در نسخه‌ی وب (IndexedDB برای فاکتور/مشتری + window.storage
 * برای بقیه). اینجا همه چیز در یک دیتابیس SQLite واحد (از طریق Room) نگه داشته می‌شود —
 * چون در اندروید محدودیت ۵ مگابایتی که در وب باعث جداسازی شد اصلاً وجود ندارد.
 */
@Database(
    entities = [
        InventorySheetEntity::class,
        CustomerEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        VanDriverEntity::class,
        ProductionQueueItemEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventorySheetDao
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun vanDriverDao(): VanDriverDao
    abstract fun productionQueueDao(): ProductionQueueDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rasticpack.db"
                ).build().also { INSTANCE = it }
            }
    }
}
