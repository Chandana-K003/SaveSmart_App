package com.example.expirytracker

import android.os.Bundle
import android.widget.*
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.expirytracker.data.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: ProductRepository
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            repository = ProductRepository(this)
            prefs = getSharedPreferences("savesmart_prefs",
                MODE_PRIVATE)
            setupUI()
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity",
                "CRASH: ${e.message}", e)
            Toast.makeText(this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(
                android.graphics.Color.parseColor("#F5F5F5"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 48)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(
                android.graphics.Color.parseColor("#2E7D32"))
            setPadding(32, 56, 32, 24)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val btnBack = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 24, 0)
            setOnClickListener { finish() }
        }
        val tvTitle = TextView(this).apply {
            text = "⚙️ Settings"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(btnBack)
        header.addView(tvTitle)

        // ── Stats Card ────────────────────────────────────────────
        val statsCard = makeCard()
        statsCard.addView(sectionTitle("📊 App Statistics"))

        val tvScanned = statRow("Products Scanned", "Loading...")
        val tvDeleted = statRow("Products Deleted", "Loading...")
        val tvExpiredBeforeUse = statRow(
            "Expired Before Use", "Loading...")
        val tvCurrentItems = statRow(
            "Current Items", "Loading...")
        val (used, saved, expired) = StreakManager.getStats(this)
        val streakPoints = StreakManager.getStreakPoints(this)
        statsCard.addView(statRow(
            "🔥 Streak Points", "$streakPoints pts"))
        statsCard.addView(statRow(
            "✅ Used Before Expiry", "$used items"))
        statsCard.addView(statRow(
            "💚 Saved From Alert", "$saved items"))

        statsCard.addView(tvScanned)
        statsCard.addView(tvDeleted)
        statsCard.addView(tvExpiredBeforeUse)
        statsCard.addView(tvCurrentItems)

        // Load stats
        lifecycleScope.launch {
            val products = withContext(Dispatchers.IO) {
                repository.getAllProducts()
            }
            val now = System.currentTimeMillis()
            val scanned = prefs.getInt("total_scanned", 0)
            val deleted = prefs.getInt("total_deleted", 0)
            val expiredBeforeUse = products.count {
                it.expiryDate < now }

            tvScanned.text = "📷 Products Scanned: $scanned"
            tvDeleted.text = "🗑️ Products Deleted: $deleted"
            tvExpiredBeforeUse.text =
                "⚠️ Expired Before Use: $expiredBeforeUse"
            tvCurrentItems.text =
                "📦 Current Items: ${products.size}"
        }

        // ── App Info Card ─────────────────────────────────────────
        val infoCard = makeCard()
        infoCard.addView(sectionTitle("ℹ️ App Details"))
        infoCard.addView(infoRow("App Name", "SaveSmart"))
        infoCard.addView(infoRow("Version", "1.0.0"))
        infoCard.addView(infoRow("Developer", "SaveSmart Team"))
        infoCard.addView(infoRow("Purpose",
            "Track product expiry dates"))

        // ── Auto Delete Card ──────────────────────────────────────
        val autoDeleteCard = makeCard()
        autoDeleteCard.addView(
            sectionTitle("🗑️ Auto Delete Expired Items"))

        val tvAutoDeleteDesc = TextView(this).apply {
            text = "Automatically delete expired items " +
                    "after a set number of days"
            textSize = 13f
            setTextColor(
                android.graphics.Color.parseColor("#666666"))
            setPadding(0, 0, 0, 16)
        }
        autoDeleteCard.addView(tvAutoDeleteDesc)

        // Toggle row
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(
                android.graphics.Color.parseColor("#F1F8E9"))
            setPadding(24, 20, 24, 20)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 16)
            layoutParams = p
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#F1F8E9"))
                    cornerRadius = 12f
                    setStroke(2, android.graphics.Color
                        .parseColor("#2E7D32"))
                }
        }
        val tvToggleLabel = TextView(this).apply {
            text = "Enable Auto Delete"
            textSize = 15f
            setTextColor(
                android.graphics.Color.parseColor("#212121"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val switchAutoDelete =
            androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = prefs.getBoolean(
                    "auto_delete_enabled", false)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit()
                        .putBoolean("auto_delete_enabled", isChecked)
                        .apply()
                }
            }
        toggleRow.addView(tvToggleLabel)
        toggleRow.addView(switchAutoDelete)
        autoDeleteCard.addView(toggleRow)

// Days selector row

        val daysBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 16)
            layoutParams = p
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#FFF8E1"))
                    cornerRadius = 12f
                    setStroke(2, android.graphics.Color
                        .parseColor("#F57F17"))
                }
        }

        val tvDaysTitle = TextView(this).apply {
            text = "⏱ Delete after how many days?"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                android.graphics.Color.parseColor("#E65100"))
            setPadding(0, 0, 0, 16)
        }

        var currentDays = prefs.getInt("auto_delete_days", 7)

// +/- row
        val daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val btnMinus = Button(this).apply {
            text = "−"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#E65100"))
                    cornerRadius = 48f
                }
            val p = LinearLayout.LayoutParams(120, 120)
            layoutParams = p
        }

        val tvDaysValue = TextView(this).apply {
            text = "$currentDays days"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                android.graphics.Color.parseColor("#212121"))
            gravity = android.view.Gravity.CENTER
            val p = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = p
        }

        val btnPlus = Button(this).apply {
            text = "+"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#2E7D32"))
                    cornerRadius = 48f
                }
            val p = LinearLayout.LayoutParams(120, 120)
            layoutParams = p
        }

// Button click logic
        btnMinus.setOnClickListener {
            if (currentDays > 1) {
                currentDays--
                tvDaysValue.text = "$currentDays days"
                prefs.edit()
                    .putInt("auto_delete_days", currentDays)
                    .apply()
            }
        }
        btnPlus.setOnClickListener {
            if (currentDays < 90) {
                currentDays++
                tvDaysValue.text = "$currentDays days"
                prefs.edit()
                    .putInt("auto_delete_days", currentDays)
                    .apply()
            }
        }

        val tvSuffix = TextView(this).apply {
            text = "after expiry"
            textSize = 13f
            setTextColor(
                android.graphics.Color.parseColor("#666666"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        daysRow.addView(btnMinus)
        daysRow.addView(tvDaysValue)
        daysRow.addView(btnPlus)
        daysBox.addView(tvDaysTitle)
        daysBox.addView(daysRow)
        daysBox.addView(tvSuffix)
        autoDeleteCard.addView(daysBox)

        // Manual trigger button
        val btnDeleteNow = Button(this).apply {
            text = "Delete All Expired Items Now"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#C62828"))
                    cornerRadius = 32f
                }
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 110)
            p.setMargins(0, 20, 0, 0)
            layoutParams = p
            setOnClickListener {
                deleteExpiredItems(
                    prefs.getInt("auto_delete_days", 7))
            }
        }
        autoDeleteCard.addView(btnDeleteNow)


        // ── Notification Card ─────────────────────────────────────
        val notifCard = makeCard()
        notifCard.addView(
            sectionTitle("🔔 Notifications"))

        val notifRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val tvNotifLabel = TextView(this).apply {
            text = "Expiry Reminders"
            textSize = 15f
            setTextColor(
                android.graphics.Color.parseColor("#212121"))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val switchNotif = androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = prefs.getBoolean(
                "notifications_enabled", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit()
                    .putBoolean("notifications_enabled",
                        isChecked)
                    .apply()
            }
        }
        notifRow.addView(tvNotifLabel)
        notifRow.addView(switchNotif)
        notifCard.addView(notifRow)


       // notifCard.addView(btnTestNotif)

        // Assemble
        root.addView(header)
        root.addView(statsCard)
        root.addView(infoCard)
        root.addView(autoDeleteCard)
        root.addView(notifCard)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun deleteExpiredItems(daysAfter: Int) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                val products = repository.getAllProducts()
                val now = System.currentTimeMillis()
                val cutoff = daysAfter * 24 * 60 * 60 * 1000L
                var deleted = 0
                products.forEach { p ->
                    if (now - p.expiryDate >= cutoff) {
                        repository.delete(p)
                        deleted++
                    }
                }
                val prev = prefs.getInt("total_deleted", 0)
                prefs.edit()
                    .putInt("total_deleted", prev + deleted)
                    .apply()
                deleted
            }
            Toast.makeText(this@SettingsActivity,
                "✅ Deleted $count expired items",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(48, 32, 48, 32)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(24, 20, 24, 0)
            layoutParams = p
            elevation = 3f
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 20f
                }
        }
    }

    private fun sectionTitle(title: String) = TextView(this).apply {
        text = title
        textSize = 16f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(
            android.graphics.Color.parseColor("#2E7D32"))
        setPadding(0, 0, 0, 20)
    }

    private fun statRow(label: String, value: String) =
        TextView(this).apply {
            text = "• $label: $value"
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#333333"))
            setPadding(0, 6, 0, 6)
        }

    private fun infoRow(label: String, value: String) =
        TextView(this).apply {
            text = "• $label: $value"
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#333333"))
            setPadding(0, 6, 0, 6)
        }
}