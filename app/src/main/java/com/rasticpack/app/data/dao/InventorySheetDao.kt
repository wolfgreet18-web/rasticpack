package com.rasticpack.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rasticpack.app.data.entities.InventorySheetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventorySheetDao {
    @Query("SELECT * FROM inventory_sheets ORDER BY layer, paperType, flute, sh, sw")
    fun observeAll(): Flow<List<InventorySheetEntity>>

    @Query("SELECT * FROM inventory_sheets ORDER BY layer, paperType, flute, sh, sw")
    suspend fun getAll(): List<InventorySheetEntity>

    @Query("SELECT COUNT(*) FROM inventory_sheets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sheet: InventorySheetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sheets: List<InventorySheetEntity>)

    @Update
    suspend fun update(sheet: InventorySheetEntity)

    @Delete
    suspend fun delete(sheet: InventorySheetEntity)

    @Query("DELETE FROM inventory_sheets WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM inventory_sheets")
    suspend fun clear()
}
