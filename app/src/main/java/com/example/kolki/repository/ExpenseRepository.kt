package com.example.kolki.repository

import com.example.kolki.data.SimpleCategoryTotal
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.data.SimpleIncome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.util.Date

class ExpenseRepository(private val storage: SimpleExpenseStorage) {
    
    fun getAllExpenses(): Flow<List<SimpleExpense>> = storage.expenses
    
    fun getExpensesByDateRange(startDate: Date, endDate: Date): Flow<List<SimpleExpense>> = 
        storage.expenses.map { expenses ->
            expenses.filter { it.date.after(startDate) && it.date.before(endDate) }
        }
    
    fun getExpensesByCategory(category: String): Flow<List<SimpleExpense>> = 
        storage.expenses.map { expenses ->
            expenses.filter { it.category.contains(category, ignoreCase = true) }
        }
    
    fun getTotalExpenses(): Flow<Double> = 
        storage.expenses.map { expenses -> expenses.sumOf { it.amount } }
    
    fun getTotalIncomes(): Flow<Double> = 
        storage.incomes.map { incomes -> incomes.sumOf { it.amount } }
    
    fun getRemaining(): Flow<Double> =
        storage.incomes.map { incomes: List<SimpleIncome> -> incomes.sumOf { it.amount } }
            .combine(storage.expenses.map { expenses: List<SimpleExpense> -> expenses.sumOf { it.amount } }) { incomesTotal: Double, expensesTotal: Double ->
                incomesTotal - expensesTotal
            }
    
    fun getTotalExpensesByDateRange(startDate: Date, endDate: Date): Flow<Double> = 
        storage.expenses.map { expenses ->
            expenses.filter { it.date.after(startDate) && it.date.before(endDate) }
                .sumOf { it.amount }
        }
    
    fun getExpensesByCategory(): Flow<List<SimpleCategoryTotal>> = 
        storage.expenses.map { expenses ->
            expenses.groupBy { it.category }
                .map { (category, expenseList) ->
                    SimpleCategoryTotal(category, expenseList.sumOf { it.amount })
                }
                .sortedByDescending { it.total }
        }
    
    fun getAllCategories(): Flow<List<String>> = 
        storage.expenses.map { expenses ->
            expenses.map { it.category }.distinct().sorted()
        }
    
    fun insertExpense(expense: SimpleExpense) = storage.insertExpense(expense)
    
    fun insertIncome(income: SimpleIncome) = storage.insertIncome(income)
    
    fun deleteExpense(expense: SimpleExpense) = storage.deleteExpense(expense)
    
    fun clearAllData() = storage.clearAllData()
}
