package com.example.kolki.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.example.kolki.data.SimpleCategoryTotal
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.ExpenseStoragePort
import com.example.kolki.data.RoomStorageAdapter
import com.example.kolki.repository.ExpenseRepository
import java.util.*

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: ExpenseRepository
    
    private val _selectedPeriod = MutableLiveData<Period>(Period.MONTH)
    val selectedPeriod: LiveData<Period> = _selectedPeriod
    private val _customRange: MutableLiveData<Pair<Date, Date>?> = MutableLiveData(null)
    val customRange: LiveData<Pair<Date, Date>?> = _customRange
    
    val totalAmount: LiveData<Double>
    val monthlyAmount: LiveData<Double>
    val categoryTotals: LiveData<List<SimpleCategoryTotal>>
    val recentExpenses: LiveData<List<SimpleExpense>>
    val remaining: LiveData<Double>
    
    init {
        val storage: ExpenseStoragePort = RoomStorageAdapter(application)
        repository = ExpenseRepository(storage)
        
        totalAmount = repository.getTotalExpenses().asLiveData()
        
        monthlyAmount = selectedPeriod.switchMap { period ->
            when (period) {
                Period.WEEK, Period.MONTH -> {
                    val (startDate, endDate) = getDateRange(period)
                    repository.getTotalExpensesByDateRange(startDate, endDate).asLiveData()
                }
                Period.RANGE -> {
                    customRange.switchMap { range ->
                        if (range == null) {
                            // Si no hay rango aún, devuelve 0
                            androidx.lifecycle.MutableLiveData(0.0)
                        } else {
                            repository.getTotalExpensesByDateRange(range.first, range.second).asLiveData()
                        }
                    }
                }
            }
        }
        
        // Category totals por período o rango
        categoryTotals = selectedPeriod.switchMap { period ->
            when (period) {
                Period.WEEK, Period.MONTH -> {
                    val (startDate, endDate) = getDateRange(period)
                    repository.getExpensesByCategoryBetween(startDate, endDate).asLiveData()
                }
                Period.RANGE -> {
                    customRange.switchMap { range ->
                        if (range == null) {
                            // vacío hasta elegir fechas
                            androidx.lifecycle.MutableLiveData(emptyList())
                        } else {
                            repository.getExpensesByCategoryBetween(range.first, range.second).asLiveData()
                        }
                    }
                }
            }
        }
        remaining = repository.getRemaining().asLiveData()
        
        recentExpenses = selectedPeriod.switchMap { period ->
            when (period) {
                Period.WEEK, Period.MONTH -> {
                    val (startDate, endDate) = getDateRange(period)
                    repository.getExpensesByDateRange(startDate, endDate).asLiveData()
                }
                Period.RANGE -> {
                    customRange.switchMap { range ->
                        if (range == null) {
                            androidx.lifecycle.MutableLiveData(emptyList())
                        } else {
                            repository.getExpensesByDateRange(range.first, range.second).asLiveData()
                        }
                    }
                }
            }
        }
    }
    
    fun setPeriod(period: Period) { _selectedPeriod.value = period }

    fun setCustomRange(start: Date, end: Date) {
        _customRange.value = start to end
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
            Period.RANGE -> {
                // Fallback 30 días si aún no se eligió rango
                calendar.add(Calendar.DAY_OF_YEAR, -30)
            }
        }
        
        val startDate = calendar.time
        return Pair(startDate, endDate)
    }
    
    enum class Period {
        WEEK, MONTH, RANGE
    }
}
