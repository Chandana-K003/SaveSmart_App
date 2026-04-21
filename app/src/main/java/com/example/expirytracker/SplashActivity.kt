package com.example.expirytracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val imgLogo = findViewById<ImageView>(R.id.imgSplashLogo)
        val tvAppName = findViewById<TextView>(R.id.tvSplashAppName)
        val tvTagline = findViewById<TextView>(R.id.tvSplashTagline)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        val fadeInSlow = AnimationUtils.loadAnimation(this, R.anim.fade_in_slow)

        imgLogo.startAnimation(fadeIn)
        tvAppName.startAnimation(slideUp)
        tvTagline.startAnimation(fadeInSlow)

        Handler(Looper.getMainLooper()).postDelayed({
            // Check Firebase Auth directly — most reliable way
            val currentUser = FirebaseAuth.getInstance().currentUser

            if (currentUser != null) {
                // User is still logged in via Firebase
                // Update shared prefs to match Firebase state
                val sharedPref = getSharedPreferences(
                    "expiry_tracker_prefs", MODE_PRIVATE)
                sharedPref.edit()
                    .putBoolean("is_logged_in", true)
                    .putString("user_email", currentUser.email ?: "")
                    .putString("user_id", currentUser.uid)
                    .apply()
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // No user logged in — clear any stale prefs
                val sharedPref = getSharedPreferences(
                    "expiry_tracker_prefs", MODE_PRIVATE)
                sharedPref.edit()
                    .putBoolean("is_logged_in", false)
                    .putString("user_email", "")
                    .putString("user_id", "")
                    .apply()
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 3000)
    }
}