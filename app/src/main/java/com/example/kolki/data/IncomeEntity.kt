package com.example.kolki.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey val id: Long,
    val emitter: String,
    val amount: Double,
    val date: Long,
    val createdAt: Long
)
