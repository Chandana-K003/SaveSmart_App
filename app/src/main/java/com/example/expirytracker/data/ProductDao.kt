package com.example.expirytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product)

    @Query("SELECT * FROM products WHERE userId = :userId ORDER BY expiryDate ASC")
    suspend fun getAllProducts(userId: String): List<Product>

    @Query("SELECT * FROM products WHERE userId = :userId AND expiryDate >= :now ORDER BY expiryDate ASC")
    suspend fun getActiveProducts(userId: String, now: Long): List<Product>

    @Query("SELECT * FROM products WHERE userId = :userId AND expiryDate < :now ORDER BY expiryDate ASC")
    suspend fun getExpiredProducts(userId: String, now: Long): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND expiryDate >= :now")
    suspend fun getActiveCount(userId: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND expiryDate < :now")
    suspend fun getExpiredCount(userId: String, now: Long): Int

    @Query("DELETE FROM products WHERE id = :productId AND userId = :userId")
    suspend fun deleteById(productId: Int, userId: String)
}