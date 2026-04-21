package com.example.expirytracker.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

class ProductRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.productDao()

    // Get current logged in user ID
    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    suspend fun insert(product: Product) {
        // Always attach current user ID when inserting
        val productWithUser = product.copy(userId = getCurrentUserId())
        dao.insert(productWithUser)
    }

    suspend fun delete(product: Product) {
        dao.deleteById(product.id, getCurrentUserId())
    }

    suspend fun getAllProducts(): List<Product> {
        return dao.getAllProducts(getCurrentUserId())
    }

    suspend fun getActiveProducts(): List<Product> {
        return dao.getActiveProducts(getCurrentUserId(), System.currentTimeMillis())
    }

    suspend fun getExpiredProducts(): List<Product> {
        return dao.getExpiredProducts(getCurrentUserId(), System.currentTimeMillis())
    }

    suspend fun getActiveCount(): Int {
        return dao.getActiveCount(getCurrentUserId(), System.currentTimeMillis())
    }

    suspend fun getExpiredCount(): Int {
        return dao.getExpiredCount(getCurrentUserId(), System.currentTimeMillis())
    }
}