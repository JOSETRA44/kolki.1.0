package com.example.kolki.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object LegacyMigrationRunner {
    private const val PREFS = "kolki_prefs"
    private const val KEY_MIGRATED = "room_migrated_v1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun runIfNeeded(context: Context) {
        android.util.Log.d("LegacyMigration", "Runner start")
        if (!running.compareAndSet(false, true)) {
            android.util.Log.d("LegacyMigration", "Already running, skipping new invocation")
            return
        }
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val migratedFlag = prefs.getBoolean(KEY_MIGRATED, false)
                val db = ExpenseDatabase.getInstance(context)
                val legacy = SimpleExpenseStorage(context)
                val legacyExpenses = legacy.getSnapshot()
                val legacyIncomes = legacy.getIncomeSnapshot()

                val expenseDao = db.expenseDao()
                val incomeDao = db.incomeDao()
                val e = expenseDao.count()
                val i = incomeDao.count()
                android.util.Log.d(
                    "LegacyMigration",
                    "DB counts e=$e i=$i, flag=$migratedFlag, legacy sizes exp=${legacyExpenses.size} inc=${legacyIncomes.size}"
                )
                val dbEmpty = (e == 0 && i == 0)

                // If no legacy data exists, just mark migrated and return
                if (legacyExpenses.isEmpty() && legacyIncomes.isEmpty()) {
                    android.util.Log.d("LegacyMigration", "No legacy data. Marking migrated and exit.")
                    if (!migratedFlag) prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                    return@launch
                }

                // If DB already has data, ensure flag is true and skip migration
                if (!dbEmpty) {
                    android.util.Log.d("LegacyMigration", "DB not empty. Ensuring flag and skipping migration.")
                    if (!migratedFlag) prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                    return@launch
                }

                // DB is empty and legacy has data: migrate now (even if flag was true)
                try {
                    android.util.Log.d("LegacyMigration", "Migrating legacy -> Room (start)")
                    db.withTransaction {
                        legacyExpenses.forEach { eItem -> expenseDao.insert(eItem.toEntity()) }
                        legacyIncomes.forEach { iItem -> incomeDao.insert(iItem.toEntity()) }
                    }
                    prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                    android.util.Log.d("LegacyMigration", "Migration success. Flag set.")
                } catch (e: Exception) {
                    android.util.Log.e("LegacyMigration", "Migration failed: ${e.message}", e)
                }
            } finally {
                running.set(false)
            }
        }
    }
}

// Reuse mappers
private fun SimpleExpense.toEntity() = ExpenseEntity(
    id = id,
    category = category,
    originalCategory = originalCategory,
    amount = amount,
    comment = comment,
    date = date.time,
    createdAt = createdAt
)

private fun SimpleIncome.toEntity() = IncomeEntity(
    id = id,
    emitter = emitter,
    amount = amount,
    date = date.time,
    createdAt = createdAt
)
