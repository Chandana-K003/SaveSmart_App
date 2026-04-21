package com.example.expirytracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.expirytracker.data.ProductRepository
import com.example.expirytracker.StreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_USED =
            "com.example.expirytracker.ACTION_MARK_USED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val productId = intent.getIntExtra(
            "product_id", -1)
        val notifId = intent.getIntExtra(
            "notif_id", -1)

        // Dismiss the notification
        if (notifId != -1) {
            NotificationManagerCompat.from(context)
                .cancel(notifId)
        }

        if (productId == -1) return

        // Delete product from DB on IO thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ProductRepository(context)
                val all = repo.getAllProducts()
                val product = all.find { it.id == productId }
                if (product != null) {
                    StreakManager.recordProductUsed(
                        context, product.expiryDate)
                    repo.delete(product)
                    // Show toast on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            context,
                            "✅ ${product.name} marked as used!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotifReceiver",
                    "Error: ${e.message}")
            }
        }
    }
}