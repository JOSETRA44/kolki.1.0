package com.example.kolki.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: Long,
    val category: String,
    val originalCategory: String?,
    val amount: Double,
    val comment: String?,
    val date: Long,
    val createdAt: Long
)
