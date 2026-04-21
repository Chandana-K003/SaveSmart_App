package com.example.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvToggle: TextView
    private lateinit var tvTitle: TextView
    private lateinit var progressBar: View

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvToggle = findViewById(R.id.tvToggle)
        tvTitle = findViewById(R.id.tvTitle)
        progressBar = findViewById(R.id.progressBar)

        progressBar.visibility = View.GONE

        updateUI()

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Please enter email"
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "Please enter password"
                etPassword.requestFocus()
                return@setOnClickListener
            }
            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            if (isLoginMode) loginUser(email, password)
            else registerUser(email, password)
        }

        btnRegister.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }

        tvToggle.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }
    }

    private fun updateUI() {
        if (isLoginMode) {
            tvTitle.text = "Welcome Back 👋"
            btnLogin.text = "Login"
            btnRegister.text = "Create Account"
            tvToggle.text = "Don't have an account? Register"
        } else {
            tvTitle.text = "Create Account 🛒"
            btnLogin.text = "Register"
            btnRegister.text = "Back to Login"
            tvToggle.text = "Already have an account? Login"
        }
    }

    private fun loginUser(email: String, password: String) {
        showLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    saveLoginState(true)
                    Toast.makeText(this,
                        "Welcome back! 🎉", Toast.LENGTH_SHORT).show()
                    goToMain()
                } else {
                    val msg = task.exception?.message ?: "Login failed"
                    Toast.makeText(this,
                        "Login failed: $msg", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun registerUser(email: String, password: String) {
        showLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    saveLoginState(true)
                    Toast.makeText(this,
                        "Account created successfully! 🎉",
                        Toast.LENGTH_SHORT).show()
                    goToMain()
                } else {
                    val msg = task.exception?.message ?: "Registration failed"
                    Toast.makeText(this,
                        "Registration failed: $msg", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveLoginState(isLoggedIn: Boolean) {
        val sharedPref = getSharedPreferences(
            "expiry_tracker_prefs", MODE_PRIVATE)
        sharedPref.edit()
            .putBoolean("is_logged_in", isLoggedIn)
            .putString("user_email", auth.currentUser?.email ?: "")
            .putString("user_id", auth.currentUser?.uid ?: "")
            .apply()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !show
        btnRegister.isEnabled = !show
        etEmail.isEnabled = !show
        etPassword.isEnabled = !show
    }

    override fun onBackPressed() {
        // Prevent going back to splash
        finishAffinity()
    }
}