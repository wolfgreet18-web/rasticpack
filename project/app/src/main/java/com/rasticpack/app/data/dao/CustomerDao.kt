package com.rasticpack.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rasticpack.app.data.entities.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR company LIKE '%' || :query || '%' ORDER BY name")
    fun search(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Int): CustomerEntity?

    @Query("SELECT * FROM customers ORDER BY id LIMIT 1")
    suspend fun getFirstOrNull(): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM customers")
    suspend fun clear()
}
