package com.example.kolki.ui.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.SimpleIncome
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.repository.ExpenseRepository
import kotlinx.coroutines.launch

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: ExpenseRepository
    private val storage: SimpleExpenseStorage
    
    val expenses: LiveData<List<SimpleExpense>>
    val categories: LiveData<List<String>>
    val remaining: LiveData<Double>
    
    init {
        storage = SimpleExpenseStorage(application)
        repository = ExpenseRepository(storage)
        expenses = repository.getAllExpenses().asLiveData()
        categories = repository.getAllCategories().asLiveData()
        remaining = repository.getRemaining().asLiveData()
    }
    
    fun insertExpense(expense: SimpleExpense) = viewModelScope.launch {
        repository.insertExpense(expense)
    }
    
    fun deleteExpense(expense: SimpleExpense) = viewModelScope.launch {
        repository.deleteExpense(expense)
    }

    fun insertIncome(income: SimpleIncome) = viewModelScope.launch {
        repository.insertIncome(income)
    }

    fun reload() {
        storage.reload()
    }
}
