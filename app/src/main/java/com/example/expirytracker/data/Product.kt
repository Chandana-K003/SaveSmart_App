package com.example.expirytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val name: String,
    val expiryDate: Long,
    val category: String,
    val isExpired: Boolean = false
)