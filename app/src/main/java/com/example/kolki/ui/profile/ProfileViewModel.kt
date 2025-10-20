package com.example.kolki.ui.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.ExpenseStoragePort
import com.example.kolki.data.RoomStorageAdapter
import com.example.kolki.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: ExpenseRepository
    
    val expenses: LiveData<List<SimpleExpense>>
    
    init {
        val storage: ExpenseStoragePort = RoomStorageAdapter(application)
        repository = ExpenseRepository(storage)
        expenses = repository.getAllExpenses().asLiveData()
    }
    
    fun exportDataToCsv(context: Context): String? {
        return try {
            val expenses = expenses.value ?: return null
            
            val fileName = "kolki_expenses_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)
            
            FileWriter(file).use { writer ->
                writer.append("Fecha,Categoría,Monto,Comentario\n")
                
                expenses.forEach { expense ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    writer.append("${dateFormat.format(expense.date)},")
                    writer.append("${expense.category},")
                    writer.append("${expense.amount},")
                    writer.append("${expense.comment ?: ""}\n")
                }
            }
            
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    fun clearAllData() = viewModelScope.launch {
        repository.clearAllData()
    }
}
