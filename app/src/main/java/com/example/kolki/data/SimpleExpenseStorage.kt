package com.example.kolki.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

// Simple data class without Room annotations
data class SimpleExpense(
    val id: Long = System.currentTimeMillis(),
    val category: String,
    // Nombre original ingresado/ reconocido (ej: "cena"), para mostrar en la lista
    val originalCategory: String? = null,
    val amount: Double,
    val comment: String? = null,
    val date: Date = Date(),
    val createdAt: Long = System.currentTimeMillis()
)

data class SimpleCategoryTotal(
    val category: String,
    val total: Double
)

data class SimpleIncome(
    val id: Long = System.currentTimeMillis(),
    val emitter: String,
    val amount: Double,
    val date: Date = Date(),
    val createdAt: Long = System.currentTimeMillis()
)

class SimpleExpenseStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("expenses_db", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _expenses = MutableStateFlow<List<SimpleExpense>>(loadExpenses())
    val expenses: Flow<List<SimpleExpense>> = _expenses.asStateFlow()

    private val _incomes = MutableStateFlow<List<SimpleIncome>>(loadIncomes())
    val incomes: Flow<List<SimpleIncome>> = _incomes.asStateFlow()
    
    private fun loadExpenses(): List<SimpleExpense> {
        val json = sharedPreferences.getString("expenses", "[]")
        val type = object : TypeToken<List<SimpleExpense>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveExpenses(expenses: List<SimpleExpense>) {
        val json = gson.toJson(expenses)
        sharedPreferences.edit().putString("expenses", json).apply()
        _expenses.value = expenses
    }

    private fun loadIncomes(): List<SimpleIncome> {
        val json = sharedPreferences.getString("incomes", "[]")
        val type = object : TypeToken<List<SimpleIncome>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveIncomes(incomes: List<SimpleIncome>) {
        val json = gson.toJson(incomes)
        sharedPreferences.edit().putString("incomes", json).apply()
        _incomes.value = incomes
    }
    
    fun insertExpense(expense: SimpleExpense) {
        val currentExpenses = _expenses.value.toMutableList()
        currentExpenses.add(0, expense) // Add at beginning for recent first
        saveExpenses(currentExpenses)
        android.util.Log.d("SimpleExpenseStorage", "Expense saved: ${expense.category} - ${expense.amount}")
    }
    
    fun deleteExpense(expense: SimpleExpense) {
        val currentExpenses = _expenses.value.toMutableList()
        currentExpenses.removeAll { it.id == expense.id }
        saveExpenses(currentExpenses)
    }

    fun insertIncome(income: SimpleIncome) {
        val current = _incomes.value.toMutableList()
        current.add(0, income)
        saveIncomes(current)
        android.util.Log.d("SimpleExpenseStorage", "Income saved: ${income.emitter} - ${income.amount}")
    }
    
    fun getTotalAmount(): Double {
        return _expenses.value.sumOf { it.amount }
    }

    fun getIncomeTotal(): Double {
        return _incomes.value.sumOf { it.amount }
    }

    fun getRemaining(): Double {
        return getIncomeTotal() - getTotalAmount()
    }
    
    fun getTotalByDateRange(startDate: Date, endDate: Date): Double {
        return _expenses.value
            .filter { it.date.after(startDate) && it.date.before(endDate) }
            .sumOf { it.amount }
    }
    
    fun getCategoryTotals(): List<SimpleCategoryTotal> {
        return _expenses.value
            .groupBy { it.category }
            .map { (category, expenses) ->
                SimpleCategoryTotal(category, expenses.sumOf { it.amount })
            }
            .sortedByDescending { it.total }
    }
    
    fun getAllCategories(): List<String> {
        return _expenses.value
            .map { it.category }
            .distinct()
            .sorted()
    }
    
    fun clearAllData() {
        saveExpenses(emptyList())
    }

    fun reload() {
        _expenses.value = loadExpenses()
        _incomes.value = loadIncomes()
    }

    fun getSnapshot(): List<SimpleExpense> = _expenses.value

    fun exportJson(): String {
        // Export current snapshot as JSON
        return gson.toJson(_expenses.value)
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        // Header
        sb.append("id,category,originalCategory,amount,comment,date,createdAt\n")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        for (e in _expenses.value) {
            val fields = listOf(
                e.id.toString(),
                e.category,
                (e.originalCategory ?: ""),
                e.amount.toString(),
                e.comment ?: "",
                dateFormat.format(e.date),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(e.createdAt))
            )
            sb.append(fields.joinToString(",") { csvEscape(it) }).append('\n')
        }
        return sb.toString()
    }

    // === Dedicated export methods ===
    fun exportExpensesJson(): String = gson.toJson(_expenses.value)

    fun exportExpensesCsv(): String = exportCsv()

    fun exportIncomesJson(): String = gson.toJson(_incomes.value)

    fun exportIncomesCsv(): String {
        val sb = StringBuilder()
        sb.append("id,emitter,amount,date,createdAt\n")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        for (i in _incomes.value) {
            val fields = listOf(
                i.id.toString(),
                i.emitter,
                i.amount.toString(),
                dateFormat.format(i.date),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(i.createdAt))
            )
            sb.append(fields.joinToString(",") { csvEscape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun csvEscape(s: String): String {
        var v = s
        val mustQuote = v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')
        if (v.contains('"')) v = v.replace("\"", "\"\"")
        return if (mustQuote) "\"$v\"" else v
    }
}
