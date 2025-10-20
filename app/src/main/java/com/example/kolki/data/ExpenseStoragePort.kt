package com.example.kolki.data

import kotlinx.coroutines.flow.Flow

interface ExpenseStoragePort {
    val expenses: Flow<List<SimpleExpense>>
    val incomes: Flow<List<SimpleIncome>>

    fun insertExpense(expense: SimpleExpense)
    fun deleteExpense(expense: SimpleExpense)
    fun updateExpense(expense: SimpleExpense)

    fun insertIncome(income: SimpleIncome)

    fun clearAllData()
    fun reload()

    // Snapshots and helpers used by UI code
    fun getSnapshot(): List<SimpleExpense>
    fun getIncomeSnapshot(): List<SimpleIncome>
    fun getRemaining(): Double
}
