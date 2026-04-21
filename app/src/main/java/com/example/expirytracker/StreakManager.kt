package com.example.expirytracker

import android.content.Context

object StreakManager {

    private const val PREFS = "savesmart_prefs"
    private const val KEY_USED_BEFORE_EXPIRY = "streak_used_before_expiry"
    private const val KEY_SAVED_FROM_ALERT = "streak_saved_from_alert"
    private const val KEY_EXPIRED_BEFORE_USE = "streak_expired_before_use"

    // Called when user deletes a product manually
    fun recordProductUsed(context: Context, expiryDate: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val sevenDays = 7 * 24 * 60 * 60 * 1000L

        if (expiryDate > now) {
            // Product deleted before expiry = used before expiry
            val prev = prefs.getInt(KEY_USED_BEFORE_EXPIRY, 0)
            prefs.edit().putInt(KEY_USED_BEFORE_EXPIRY, prev + 1).apply()

            // If it was near expiry (≤7 days), also count as saved from alert
            if ((expiryDate - now) <= sevenDays) {
                val prevSaved = prefs.getInt(KEY_SAVED_FROM_ALERT, 0)
                prefs.edit().putInt(KEY_SAVED_FROM_ALERT, prevSaved + 1).apply()
            }
        } else {
            // Product deleted after expiry = expired before use
            val prev = prefs.getInt(KEY_EXPIRED_BEFORE_USE, 0)
            prefs.edit().putInt(KEY_EXPIRED_BEFORE_USE, prev + 1).apply()
        }
    }

    // Calculate streak score
    fun getStreakPoints(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val usedBeforeExpiry = prefs.getInt(KEY_USED_BEFORE_EXPIRY, 0)
        val savedFromAlert = prefs.getInt(KEY_SAVED_FROM_ALERT, 0)
        val expiredBeforeUse = prefs.getInt(KEY_EXPIRED_BEFORE_USE, 0)

        // Formula: (used*2) + (saved*1) - (expired*2)
        val points = (usedBeforeExpiry * 2) +
                (savedFromAlert * 1) -
                (expiredBeforeUse * 2)

        return points // Can be negative if many expired
    }

    fun getStats(context: Context): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            prefs.getInt(KEY_USED_BEFORE_EXPIRY, 0),
            prefs.getInt(KEY_SAVED_FROM_ALERT, 0),
            prefs.getInt(KEY_EXPIRED_BEFORE_USE, 0)
        )
    }

    // Streak emoji based on points
    fun getStreakEmoji(points: Int): String {
        return when {
            points >= 50 -> "🔥🔥🔥"
            points >= 30 -> "🔥🔥"
            points >= 10 -> "🔥"
            points >= 0  -> "✨"
            else         -> "❄️"
        }
    }

    // Streak label based on points
    fun getStreakLabel(points: Int): String {
        return when {
            points >= 50 -> "Legend"
            points >= 30 -> "Expert"
            points >= 20 -> "Pro"
            points >= 10 -> "Good"
            points >= 0  -> "Starter"
            else         -> "Try harder!"
        }
    }
}