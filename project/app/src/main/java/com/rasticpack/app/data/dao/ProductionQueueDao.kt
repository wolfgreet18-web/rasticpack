package com.rasticpack.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rasticpack.app.data.entities.ProductionQueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductionQueueDao {
    @Query("SELECT * FROM production_queue ORDER BY id DESC")
    fun observeAll(): Flow<List<ProductionQueueItemEntity>>

    @Query("SELECT sourceKey FROM production_queue")
    suspend fun getAllSourceKeys(): List<String>

    // پیدا کردن اولین رکورد صف تولید که sourceKey آن با "{invoiceId}-" شروع می‌شود —
    // برای apply خودکار وقتی فاکتور دوباره ارسال می‌شود ولی همه‌ی آیتم‌هایش از قبل
    // در صف بودند (پس چیز تازه‌ای درج نشد).
    @Query("SELECT * FROM production_queue WHERE sourceKey LIKE :prefix || '-%' ORDER BY id ASC LIMIT 1")
    suspend fun findFirstByInvoicePrefix(prefix: Int): ProductionQueueItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProductionQueueItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductionQueueItemEntity>)

    @Delete
    suspend fun delete(item: ProductionQueueItemEntity)

    @Query("DELETE FROM production_queue WHERE id = :id")
    suspend fun deleteById(id: Int)

    // معادل `if(productionQueue.length>30) productionQueue.length=30;` در وب —
    // فقط ۳۰ مورد اخیر نگه داشته می‌شود.
    @Query("DELETE FROM production_queue WHERE id NOT IN (SELECT id FROM production_queue ORDER BY id DESC LIMIT 30)")
    suspend fun trimTo30()

    @Query("DELETE FROM production_queue")
    suspend fun clear()
}
