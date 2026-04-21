package com.example.expirytracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expirytracker.DisposalTipActivity
import com.example.expirytracker.MainActivity
import com.example.expirytracker.RecipeActivity
import com.example.expirytracker.data.ProductRepository
import java.util.concurrent.TimeUnit
@androidx.camera.core.ExperimentalGetImage
class ExpiryNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "expiry_channel"
        const val CHANNEL_NAME = "Expiry Alerts"
    }

    // Food/cooking categories
    private val cookingCategories = setOf(
        "Dairy", "Grains & Pulses", "Meat & Fish",
        "Cooking Oil", "Spices & Condiments",
        "Beverages", "Frozen Foods", "Bakery & Biscuits",
        "Noodles & Pasta", "Snacks", "General",
        "Vegetables", "Fruits", "Grocery"
    )

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()
            val repo = ProductRepository(applicationContext)
            val products = repo.getAllProducts()
            val now = System.currentTimeMillis()

            val workerType = inputData.getString(
                "worker_type") ?: "daily"

            when (workerType) {

                // Every 6 hours — expiring in 1 day
                "sixhour" -> {
                    val critical = products.filter {
                        val d = TimeUnit.MILLISECONDS
                            .toDays(it.expiryDate - now)
                        d in 0..1 && !it.isExpired
                    }
                    critical.forEachIndexed { i, product ->
                        val daysLeft = TimeUnit.MILLISECONDS
                            .toDays(product.expiryDate - now)
                        val when_ = if (daysLeft == 0L)
                            "TODAY" else "TOMORROW"
                        val isCooking = cookingCategories
                            .contains(product.category)
                        sendRichNotif(
                            id = 100 + i,
                            title = "🚨 ${product.name}" +
                                    " expires $when_!",
                            message = "Don't let it go" +
                                    " to waste — use it now!",
                            productName = product.name,
                            productCategory = product.category,
                            productId = product.id,
                            expiryDate = product.expiryDate,
                            showDelete = true,
                            showRecipe = isCooking,
                            showBuy = true,
                            showDispose = false, // not expired yet
                            urgency = "critical"
                        )
                    }
                }

                // Every day — expiring in 2-7 days + expired
                "daily" -> {
                    val week = products.filter {
                        val d = TimeUnit.MILLISECONDS
                            .toDays(it.expiryDate - now)
                        d in 2..7 && !it.isExpired
                    }
                    week.forEachIndexed { i, product ->
                        val daysLeft = TimeUnit.MILLISECONDS
                            .toDays(product.expiryDate - now)
                        val isCooking = cookingCategories
                            .contains(product.category)
                        sendRichNotif(
                            id = 200 + i,
                            title = "⚠️ ${product.name}" +
                                    " expires in ${daysLeft}d",
                            message = "Use it before it" +
                                    " expires on " +
                                    formatDate(product.expiryDate),
                            productName = product.name,
                            productCategory = product.category,
                            productId = product.id,
                            expiryDate = product.expiryDate,
                            showDelete = true,
                            showRecipe = isCooking,
                            showBuy = true,
                            showDispose = false, // not expired yet
                            urgency = "warning"
                        )
                    }

                    // ── Expired items — each gets its OWN notification
                    //    with a personal "♻️ How to Dispose" button
                    val expired = products.filter { it.isExpired }

                    if (expired.isNotEmpty()) {
                        // Individual notification per expired item
                        // so each has its own dispose button
                        expired.forEachIndexed { i, product ->
                            sendRichNotif(
                                id = 50 + i,
                                title = "❌ Expired: ${product.name}",
                                message = "${product.name} has expired." +
                                        " Tap ♻️ to dispose sustainably.",
                                productName = product.name,
                                productCategory = product.category,
                                productId = product.id,
                                expiryDate = product.expiryDate,
                                showDelete = true,
                                showRecipe = false,
                                showBuy = true,
                                showDispose = true, // ✅ show dispose button
                                urgency = "expired"
                            )
                        }
                    }
                }

                // Every week — expiring in 8-30 days
                "weekly" -> {
                    val monthly = products.filter {
                        val d = TimeUnit.MILLISECONDS
                            .toDays(it.expiryDate - now)
                        d in 8..30 && !it.isExpired
                    }
                    if (monthly.isNotEmpty()) {
                        val names = monthly.take(3)
                            .joinToString(", ") { it.name }
                        val extra = if (monthly.size > 3)
                            " +${monthly.size - 3} more"
                        else ""
                        val hasCooking = monthly.any {
                            cookingCategories
                                .contains(it.category)
                        }
                        sendRichNotif(
                            id = 300,
                            title = "📅 ${monthly.size}" +
                                    " item(s) expire this month",
                            message = "$names$extra — plan" +
                                    " ahead!",
                            productName = names,
                            productCategory = "General",
                            productId = -1,
                            expiryDate = 0,
                            showDelete = false,
                            showRecipe = hasCooking,
                            showBuy = false,
                            showDispose = false, // not expired yet
                            urgency = "info"
                        )
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("NotifWorker",
                "Error: ${e.message}")
            Result.failure()
        }
    }

    private fun sendRichNotif(
        id: Int,
        title: String,
        message: String,
        productName: String,
        productCategory: String,       // ✅ new param for dispose intent
        productId: Int,
        expiryDate: Long,
        showDelete: Boolean,
        showRecipe: Boolean,
        showBuy: Boolean,
        showDispose: Boolean,          // ✅ new param
        urgency: String
    ) {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU) {
            if (applicationContext.checkSelfPermission(
                    android.Manifest.permission
                        .POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager
                    .PERMISSION_GRANTED) return
        }

        // Tap notification body → open MainActivity
        val mainIntent = Intent(applicationContext,
            MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPI = PendingIntent.getActivity(
            applicationContext, id * 10, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE)

        val priority = when (urgency) {
            "critical" -> NotificationCompat.PRIORITY_MAX
            "warning", "expired" ->
                NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val color = when (urgency) {
            "critical" -> android.graphics.Color.RED
            "warning"  -> android.graphics.Color
                .parseColor("#FF6F00")
            "expired"  -> android.graphics.Color
                .parseColor("#B71C1C")
            else       -> android.graphics.Color
                .parseColor("#2E7D32")
        }

        val builder = NotificationCompat
            .Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(
                android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(message))
            .setPriority(priority)
            .setColor(color)
            .setAutoCancel(true)
            .setContentIntent(mainPI)
            .setVibrate(longArrayOf(0, 400, 200, 400))

        // ── Action 1: ✅ Used / Delete ────────────────────
        if (showDelete && productId != -1) {
            val delIntent = Intent(applicationContext,
                NotificationActionReceiver::class.java
            ).apply {
                action = NotificationActionReceiver
                    .ACTION_MARK_USED
                putExtra("product_id", productId)
                putExtra("notif_id", id)
            }
            val delPI = PendingIntent.getBroadcast(
                applicationContext, id * 10 + 1,
                delIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                android.R.drawable.ic_menu_delete,
                "✅ Used — Delete",
                delPI)
        }

        // ── Action 2: 🍳 Get Recipe (food only) ───────────
        if (showRecipe) {
            val recipeIntent = Intent(applicationContext,
                RecipeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val recipePI = PendingIntent.getActivity(
                applicationContext, id * 10 + 2,
                recipeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                android.R.drawable.ic_menu_agenda,
                "🍳 Get Recipe",
                recipePI)
        }

        // ── Action 3: 🛒 Buy Again ─────────────────────────
        if (showBuy) {
            val query = android.net.Uri.encode(
                "buy $productName online")
            val shopUri = android.net.Uri.parse(
                "https://www.google.com/search" +
                        "?q=$query&tbm=shop")
            val buyIntent = Intent(Intent.ACTION_VIEW,
                shopUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val buyPI = PendingIntent.getActivity(
                applicationContext, id * 10 + 3,
                buyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                android.R.drawable.ic_menu_add,
                "🛒 Buy Again",
                buyPI)
        }

        // ── Action 4: ♻️ How to Dispose (expired only) ────
        if (showDispose) {
            val disposeIntent = Intent(
                applicationContext,
                DisposalTipActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("PRODUCT_NAME", productName)
                putExtra("PRODUCT_CATEGORY", productCategory)
                putExtra("NOTIFICATION_ID", id)
            }
            val disposePI = PendingIntent.getActivity(
                applicationContext, id * 10 + 4,
                disposeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(
                android.R.drawable.ic_menu_info_details,
                "♻️ How to Dispose",
                disposePI)
        }

        try {
            NotificationManagerCompat
                .from(applicationContext)
                .notify(id, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("NotifWorker",
                "SecurityException: ${e.message}")
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat(
            "dd MMM", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Alerts for products expiring soon"
                enableVibration(true)
                enableLights(true)
            }
            (applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE)
                    as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}