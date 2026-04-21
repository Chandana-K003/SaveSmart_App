package com.example.expirytracker.notification

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    // Schedule all 3 notification frequencies
    fun scheduleAll(context: Context) {
        scheduleSixHourly(context)
        scheduleDaily(context)
        scheduleWeekly(context)
    }

    // Every 6 hours — for items expiring in 1 day
    fun scheduleSixHourly(context: Context) {
        val data = workDataOf("worker_type" to "sixhour")
        val request =
            PeriodicWorkRequestBuilder<
                    ExpiryNotificationWorker>(
                6, TimeUnit.HOURS
            )
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.NOT_REQUIRED)
                        .build()
                )
                .addTag("notif_sixhour")
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "notif_sixhour",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    // Every 24 hours — for items expiring in 2-7 days
    fun scheduleDaily(context: Context) {
        val data = workDataOf("worker_type" to "daily")
        val request =
            PeriodicWorkRequestBuilder<
                    ExpiryNotificationWorker>(
                24, TimeUnit.HOURS
            )
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.NOT_REQUIRED)
                        .build()
                )
                .addTag("notif_daily")
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "notif_daily",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    // Every 7 days — for items expiring in 8-30 days
    fun scheduleWeekly(context: Context) {
        val data = workDataOf("worker_type" to "weekly")
        val request =
            PeriodicWorkRequestBuilder<
                    ExpiryNotificationWorker>(
                7, TimeUnit.DAYS
            )
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            NetworkType.NOT_REQUIRED)
                        .build()
                )
                .addTag("notif_weekly")
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "notif_weekly",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    // Run immediate test — triggers all checks now
    fun runImmediateCheck(context: Context) {
        listOf("sixhour", "daily", "weekly")
            .forEach { type ->
                val data = workDataOf(
                    "worker_type" to type)
                val req = OneTimeWorkRequestBuilder<
                        ExpiryNotificationWorker>()
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(context)
                    .enqueue(req)
            }
    }

    // For backward compatibility
    fun scheduleDailyCheck(context: Context) {
        scheduleAll(context)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context)
            .cancelAllWorkByTag("notif_sixhour")
        WorkManager.getInstance(context)
            .cancelAllWorkByTag("notif_daily")
        WorkManager.getInstance(context)
            .cancelAllWorkByTag("notif_weekly")
    }
}