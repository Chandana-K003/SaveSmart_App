package com.example.expirytracker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expirytracker.adapter.ProductAdapter
import com.example.expirytracker.data.Product
import com.example.expirytracker.data.ProductRepository
import com.example.expirytracker.notification.NotificationScheduler
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {

    private lateinit var repository: ProductRepository
    private lateinit var adapter: ProductAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var prefs: android.content.SharedPreferences

    private var allProducts = listOf<Product>()
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        repository = ProductRepository(this)
        prefs = getSharedPreferences("savesmart_prefs",
            MODE_PRIVATE)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(
                    android.Manifest.permission
                        .POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(
                    android.Manifest.permission
                        .POST_NOTIFICATIONS), 200)
            }
        }

        setupViews()
        setupDrawer()
        loadProducts()
        runAutoDelete()
        updateStreak()
        uploadStreakToFirestore()

        // Schedule all notifications
        try {
            NotificationScheduler.scheduleAll(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity",
                "Scheduler: ${e.message}")
        }
    }

    private fun setupViews() {
        drawerLayout = findViewById(R.id.drawerLayout)

        // Hamburger button opens drawer
        findViewById<TextView>(R.id.btnMenu)
            .setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

        // User email
        val email = auth.currentUser?.email ?: ""
        findViewById<TextView>(R.id.tvUserEmail).text =
            if (email.isNotEmpty()) "👤 $email" else ""
        findViewById<TextView>(R.id.tvDrawerEmail).text =
            email

        // RecyclerView
        val recyclerView = findViewById<RecyclerView>(
            R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ProductAdapter(emptyList()) { product ->
            deleteProduct(product)
        }
        recyclerView.adapter = adapter

        // Filter buttons
        findViewById<Button>(R.id.btnAll)
            .setOnClickListener { setFilter("all") }
        findViewById<Button>(R.id.btnActive)
            .setOnClickListener { setFilter("active") }
        findViewById<Button>(R.id.btnExpired)
            .setOnClickListener { setFilter("expired") }

        // FAB
        findViewById<FloatingActionButton>(R.id.fabAdd)
            .setOnClickListener {
                startActivity(Intent(this,
                    AddItemActivity::class.java))
            }

        // Recipe button
        try {
            findViewById<Button>(R.id.btnRecipe)
                ?.setOnClickListener {
                    startActivity(Intent(this,
                        RecipeActivity::class.java))
                }
        } catch (e: Exception) { }
    }

    private fun setupDrawer() {
        try {
            findViewById<TextView>(R.id.navHome)
                ?.setOnClickListener {
                    drawerLayout.closeDrawer(
                        GravityCompat.START)
                }
            findViewById<TextView>(R.id.navRecipes)
                ?.setOnClickListener {
                    startActivity(Intent(this,
                        RecipeActivity::class.java))
                    drawerLayout.closeDrawer(
                        GravityCompat.START)
                }
            findViewById<TextView>(R.id.navSettings)
                ?.setOnClickListener {
                    startActivity(Intent(this,
                        SettingsActivity::class.java))
                    drawerLayout.closeDrawer(
                        GravityCompat.START)
                }
            // Friends
            try {
                findViewById<TextView>(R.id.navFriends)
                    ?.setOnClickListener {
                        startActivity(Intent(this,
                            FriendsActivity::class.java))
                        drawerLayout.closeDrawer(
                            GravityCompat.START)
                    }
            } catch (e: Exception) { }

            findViewById<TextView>(R.id.navLogout)
                ?.setOnClickListener {
                    auth.signOut()
                    startActivity(
                        Intent(this,
                            LoginActivity::class.java)
                            .apply {
                                flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity",
                "Drawer setup: ${e.message}")
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch {
            allProducts = withContext(Dispatchers.IO) {
                repository.getAllProducts()
            }
            applyFilter()
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        applyFilter()

        val green = android.graphics.Color
            .parseColor("#2E7D32")
        val lightGreen = android.graphics.Color
            .parseColor("#E8F5E9")
        val white = android.graphics.Color.WHITE

        listOf(
            R.id.btnAll to "all",
            R.id.btnActive to "active",
            R.id.btnExpired to "expired"
        ).forEach { (id, f) ->
            try {
                val btn = findViewById<Button>(id)
                if (f == filter) {
                    btn.backgroundTintList =
                        android.content.res.ColorStateList
                            .valueOf(green)
                    btn.setTextColor(white)
                } else {
                    btn.backgroundTintList =
                        android.content.res.ColorStateList
                            .valueOf(lightGreen)
                    btn.setTextColor(green)
                }
            } catch (e: Exception) { }
        }
    }

    private fun applyFilter() {
        val now = System.currentTimeMillis()
        val filtered = when (currentFilter) {
            "active" -> allProducts.filter {
                it.expiryDate >= now }
            "expired" -> allProducts.filter {
                it.expiryDate < now }
            else -> allProducts
        }
        adapter.updateList(filtered)
    }

    private fun deleteProduct(product: Product) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                StreakManager.recordProductUsed(
                    this@MainActivity, product.expiryDate)
                repository.delete(product)
                val prev = prefs.getInt(
                    "total_deleted", 0)
                prefs.edit()
                    .putInt("total_deleted", prev + 1)
                    .apply()
            }
            loadProducts()
            updateStreak()
        }
    }

    private fun runAutoDelete() {
        val enabled = prefs.getBoolean(
            "auto_delete_enabled", false)
        if (!enabled) return
        val days = prefs.getInt("auto_delete_days", 7)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val products = repository.getAllProducts()
                val now = System.currentTimeMillis()
                val cutoff = days * 24 * 60 * 60 * 1000L
                var deleted = 0
                products.forEach { p ->
                    if (now - p.expiryDate >= cutoff) {
                        repository.delete(p)
                        deleted++
                    }
                }
                if (deleted > 0) {
                    val prev = prefs.getInt(
                        "total_deleted", 0)
                    prefs.edit()
                        .putInt("total_deleted",
                            prev + deleted)
                        .apply()
                }
            }
            loadProducts()
        }
    }

    private fun updateStreak() {
        try {
            val points = StreakManager.getStreakPoints(this)
            val emoji = StreakManager.getStreakEmoji(points)
            findViewById<TextView>(R.id.tvStreakEmoji)
                ?.text = emoji
            findViewById<TextView>(R.id.tvStreakPoints)
                ?.text = points.toString()
        } catch (e: Exception) { }
    }

    private fun uploadStreakToFirestore() {
        val uid = com.google.firebase.auth.FirebaseAuth
            .getInstance().currentUser?.uid ?: return
        val pts = StreakManager.getStreakPoints(this)
        val email = com.google.firebase.auth.FirebaseAuth
            .getInstance().currentUser?.email ?: ""
        val name = email.substringBefore("@")
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val sb = StringBuilder()
        var hash = uid.hashCode().toLong()
        if (hash < 0) hash = -hash
        repeat(6) {
            sb.append(chars[(hash % chars.length).toInt()])
            hash /= chars.length
        }
        val code = sb.toString()

        lifecycleScope.launch {
            kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.google.firebase.firestore
                        .FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .set(mapOf(
                            "uid"          to uid,
                            "email"        to email,
                            "displayName"  to name,
                            "friendCode"   to code,
                            "streakPoints" to pts,
                            "updatedAt"    to
                                    System.currentTimeMillis()
                        )).await()
                } catch (e: Exception) { }
            }
        }
    }



    override fun onResume() {
        super.onResume()
        loadProducts()
        updateStreak()
        updateStreak()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}