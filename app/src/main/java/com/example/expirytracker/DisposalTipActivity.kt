package com.example.expirytracker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DisposalTipActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disposal_tip)

        val productName = intent.getStringExtra("PRODUCT_NAME") ?: "Unknown Product"
        val category    = intent.getStringExtra("PRODUCT_CATEGORY") ?: "food"

        val tvProductName: TextView = findViewById(R.id.tvDisposalProductName)
        val tvTipContent: TextView  = findViewById(R.id.tvDisposalTipContent)
        val progressBar: ProgressBar = findViewById(R.id.disposalProgressBar)
        val btnClose: Button        = findViewById(R.id.btnCloseDisposal)
        val btnRetry: Button        = findViewById(R.id.btnRetryDisposal)

        // Set product name in title
        tvProductName.text = "♻️ How to Dispose: $productName"

        // Auto-fetch tip on open
        fetchTip(productName, category, tvTipContent, progressBar, btnRetry)

        btnRetry.setOnClickListener {
            fetchTip(productName, category, tvTipContent, progressBar, btnRetry)
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun fetchTip(
        productName: String,
        category: String,
        tvTipContent: TextView,
        progressBar: ProgressBar,
        btnRetry: Button
    ) {
        progressBar.visibility = View.VISIBLE
        tvTipContent.text = ""
        btnRetry.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            val tip = DisposalTipHelper.getDisposalTip(
                productName = productName,
                category = category
            )

            progressBar.visibility = View.GONE

            if (tip.startsWith("⚠️") || tip.isEmpty()) {
                // Show retry if something went wrong
                tvTipContent.text = "Could not fetch tip. Please check your connection."
                btnRetry.visibility = View.VISIBLE
            } else {
                tvTipContent.text = tip
                btnRetry.visibility = View.GONE
            }
        }
    }
}