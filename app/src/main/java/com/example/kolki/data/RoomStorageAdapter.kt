package com.example.kolki.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RoomStorageAdapter(context: Context) : ExpenseStoragePort {
    private val db = ExpenseDatabase.getInstance(context)
    private val expenseDao = db.expenseDao()
    private val incomeDao = db.incomeDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _expenses = MutableStateFlow<List<SimpleExpense>>(emptyList())
    override val expenses: Flow<List<SimpleExpense>> = _expenses.asStateFlow()

    private val _incomes = MutableStateFlow<List<SimpleIncome>>(emptyList())
    override val incomes: Flow<List<SimpleIncome>> = _incomes.asStateFlow()

    init {
        scope.launch {
            expenseDao.getAll().collectLatest { list ->
                _expenses.value = list.map { it.toModel() }
            }
        }
        scope.launch {
            incomeDao.getAll().collectLatest { list ->
                _incomes.value = list.map { it.toModel() }
            }
        }
    }

    override fun insertExpense(expense: SimpleExpense) {
        scope.launch {
            expenseDao.insert(expense.toEntity())
        }
    }

    override fun deleteExpense(expense: SimpleExpense) {
        scope.launch { expenseDao.delete(expense.toEntity()) }
    }

    override fun updateExpense(expense: SimpleExpense) {
        scope.launch { expenseDao.update(expense.toEntity()) }
    }

    override fun insertIncome(income: SimpleIncome) {
        scope.launch { incomeDao.insert(income.toEntity()) }
    }

    override fun clearAllData() {
        scope.launch {
            expenseDao.clearAll()
            incomeDao.clearAll()
        }
    }

    override fun reload() {
        // Room is reactive; nothing to do. Kept for API compatibility.
    }

    override fun getSnapshot(): List<SimpleExpense> = _expenses.value

    override fun getIncomeSnapshot(): List<SimpleIncome> = _incomes.value

    override fun getRemaining(): Double = _incomes.value.sumOf { it.amount } - _expenses.value.sumOf { it.amount }
}

private fun ExpenseEntity.toModel() = SimpleExpense(
    id = id,
    category = category,
    originalCategory = originalCategory,
    amount = amount,
    comment = comment,
    date = java.util.Date(date),
    createdAt = createdAt
)

private fun SimpleExpense.toEntity() = ExpenseEntity(
    id = id,
    category = category,
    originalCategory = originalCategory,
    amount = amount,
    comment = comment,
    date = date.time,
    createdAt = createdAt
)

private fun IncomeEntity.toModel() = SimpleIncome(
    id = id,
    emitter = emitter,
    amount = amount,
    date = java.util.Date(date),
    createdAt = createdAt
)

private fun SimpleIncome.toEntity() = IncomeEntity(
    id = id,
    emitter = emitter,
    amount = amount,
    date = date.time,
    createdAt = createdAt
)
