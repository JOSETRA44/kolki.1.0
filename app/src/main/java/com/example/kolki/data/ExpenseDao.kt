package com.example.kolki.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getBetween(start: Long, end: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExpenseEntity)

    @Update
    suspend fun update(entity: ExpenseEntity)

    @Delete
    suspend fun delete(entity: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY date DESC, createdAt DESC")
    fun getAll(): Flow<List<IncomeEntity>>

    @Query("SELECT COUNT(*) FROM incomes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IncomeEntity)

    @Delete
    suspend fun delete(entity: IncomeEntity)

    @Query("DELETE FROM incomes")
    suspend fun clearAll()
}

