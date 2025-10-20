// This file is intentionally empty - replaced by SimpleExpenseStorage

package com.example.kolki.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExpenseEntity::class, IncomeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao

    companion object {
        @Volatile private var INSTANCE: ExpenseDatabase? = null

        fun getInstance(context: Context): ExpenseDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ExpenseDatabase::class.java,
                "kolki.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
