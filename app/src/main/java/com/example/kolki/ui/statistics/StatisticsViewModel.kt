package com.example.kolki.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.example.kolki.data.SimpleCategoryTotal
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.repository.ExpenseRepository
import java.util.*

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: ExpenseRepository
    
    private val _selectedPeriod = MutableLiveData<Period>(Period.MONTH)
    val selectedPeriod: LiveData<Period> = _selectedPeriod
    
    val totalAmount: LiveData<Double>
    val monthlyAmount: LiveData<Double>
    val categoryTotals: LiveData<List<SimpleCategoryTotal>>
    val recentExpenses: LiveData<List<SimpleExpense>>
    val remaining: LiveData<Double>
    
    init {
        val storage = SimpleExpenseStorage(application)
        repository = ExpenseRepository(storage)
        
        totalAmount = repository.getTotalExpenses().asLiveData()
        
        monthlyAmount = selectedPeriod.switchMap { period ->
            val (startDate, endDate) = getDateRange(period)
            repository.getTotalExpensesByDateRange(startDate, endDate).asLiveData()
        }
        
        categoryTotals = repository.getExpensesByCategory().asLiveData()
        remaining = repository.getRemaining().asLiveData()
        
        recentExpenses = selectedPeriod.switchMap { period ->
            val (startDate, endDate) = getDateRange(period)
            repository.getExpensesByDateRange(startDate, endDate).asLiveData()
        }
    }
    
    fun setPeriod(period: Period) {
        _selectedPeriod.value = period
    }
    
    private fun getDateRange(period: Period): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        when (period) {
            Period.WEEK -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
            }
            Period.MONTH -> {
                calendar.add(Calendar.MONTH, -1)
            }
            Period.YEAR -> {
                calendar.add(Calendar.YEAR, -1)
            }
        }
        
        val startDate = calendar.time
        return Pair(startDate, endDate)
    }
    
    enum class Period {
        WEEK, MONTH, YEAR
    }
}
