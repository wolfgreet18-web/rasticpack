package com.rasticpack.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rasticpack.app.data.entities.VanDriverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VanDriverDao {
    @Query("SELECT * FROM van_drivers ORDER BY name")
    fun observeAll(): Flow<List<VanDriverEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(driver: VanDriverEntity): Long

    @Update
    suspend fun update(driver: VanDriverEntity)

    @Delete
    suspend fun delete(driver: VanDriverEntity)

    @Query("DELETE FROM van_drivers")
    suspend fun clear()
}
