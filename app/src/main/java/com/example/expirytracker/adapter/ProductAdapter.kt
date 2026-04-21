package com.example.expirytracker.adapter

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expirytracker.DisposalTipHelper
import com.example.expirytracker.R
import com.example.expirytracker.data.Product
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProductAdapter(
    private var products: List<Product>,
    private val onDeleteClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ── ViewHolder ─────────────────────────────────────────────────
    // ✅ Removed 'inner' keyword — this was causing 'itemView' unresolved errors
    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView         = itemView.findViewById(R.id.tvProductName)
        val tvExpiry: TextView       = itemView.findViewById(R.id.tvExpiryDate)
        val tvCategory: TextView     = itemView.findViewById(R.id.tvCategory)
        val tvDaysLeft: TextView     = itemView.findViewById(R.id.tvDaysLeft)
        val tvDeleteBtn: TextView    = itemView.findViewById(R.id.tvDeleteBtn)
        val btnBuy: Button           = itemView.findViewById(R.id.btnBuy)

        // ✅ Disposal tip views — declared here only, NOT again in onBindViewHolder
        val btnDisposalTip: Button       = itemView.findViewById(R.id.btnDisposalTip)
        val disposalLayout: LinearLayout = itemView.findViewById(R.id.disposalLayout)
        val tvDisposalTip: TextView      = itemView.findViewById(R.id.tvDisposalTip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        val now = System.currentTimeMillis()

        // ✅ REMOVED the duplicate itemView.findViewById() block that was here
        // All views are accessed via holder.xxx

        holder.tvName.text     = product.name
        holder.tvCategory.text = product.category
        holder.tvExpiry.text   = "Expires: ${displayFormat.format(product.expiryDate)}"

        val diffMillis = product.expiryDate - now
        val daysLeft = TimeUnit.MILLISECONDS.toDays(diffMillis)

        // ── Reset disposal tip views for every item (important for RecyclerView reuse)
        holder.btnDisposalTip.visibility = View.GONE
        holder.disposalLayout.visibility = View.GONE
        holder.btnDisposalTip.text = "♻️ How to dispose"

        when {
            daysLeft < 0 -> {
                val daysAgo = kotlin.math.abs(daysLeft)
                holder.tvDaysLeft.text = "Expired ${daysAgo}d ago"
                holder.tvDaysLeft.setTextColor(Color.parseColor("#C62828"))
                holder.tvDaysLeft.setBackgroundResource(R.drawable.badge_expired)
                holder.itemView.alpha = 0.6f

                // ── AI Disposal Tip — only for expired items ──────────────
                holder.btnDisposalTip.visibility = View.VISIBLE

                var tipLoaded = false
                var tipVisible = false

                // Clear old click listener before setting new one
                holder.btnDisposalTip.setOnClickListener(null)

                holder.btnDisposalTip.setOnClickListener {
                    if (!tipLoaded) {
                        holder.disposalLayout.visibility = View.VISIBLE
                        holder.tvDisposalTip.text = "🌿 Fetching eco-disposal tip..."
                        tipVisible = true

                        CoroutineScope(Dispatchers.Main).launch {
                            val tip = DisposalTipHelper.getDisposalTip(
                                productName = product.name,
                                category = product.category ?: "food"
                            )
                            holder.tvDisposalTip.text = "♻️ $tip"
                            holder.btnDisposalTip.text = "▲ Hide tip"
                            tipLoaded = true
                        }
                    } else {
                        tipVisible = !tipVisible
                        holder.disposalLayout.visibility =
                            if (tipVisible) View.VISIBLE else View.GONE
                        holder.btnDisposalTip.text =
                            if (tipVisible) "▲ Hide tip" else "♻️ How to dispose"
                    }
                }
            }

            daysLeft == 0L -> {
                holder.tvDaysLeft.text = "Expires TODAY"
                holder.tvDaysLeft.setTextColor(Color.parseColor("#E65100"))
                holder.tvDaysLeft.setBackgroundResource(R.drawable.badge_warning)
                holder.itemView.alpha = 1.0f
            }

            daysLeft <= 3 -> {
                holder.tvDaysLeft.text = "In ${daysLeft}d"
                holder.tvDaysLeft.setTextColor(Color.parseColor("#E65100"))
                holder.tvDaysLeft.setBackgroundResource(R.drawable.badge_warning)
                holder.itemView.alpha = 1.0f
            }

            daysLeft <= 7 -> {
                holder.tvDaysLeft.text = "${daysLeft}d left"
                holder.tvDaysLeft.setTextColor(Color.parseColor("#F57F17"))
                holder.tvDaysLeft.setBackgroundResource(R.drawable.badge_soon)
                holder.itemView.alpha = 1.0f
            }

            daysLeft <= 30 -> {
                holder.tvDaysLeft.text = "${daysLeft}d left"
                holder.tvDaysLeft.setTextColor(Color.parseColor("#2E7D32"))
                holder.tvDaysLeft.setBackgroundResource(R.drawable.badge_good)
                holder.itemView.alpha = 1.0f
            }

            else -> {
                val monthsLeft = daysLeft / 30
                holder.tvDaysLeft.text = if (monthsLeft >= 2)
                    "${monthsLeft}mo left" else "${daysLeft}d left"
                holder.tvDaysLeft.setTextColor(Color.parseColor("#1B5E20"))
                holder.tvDaysLeft.setBackgroundResource(R.drawable.badge_good)
                holder.itemView.alpha = 1.0f
            }
        }

        // ── Buy Button ────────────────────────────────────────────────
        if (product.name.isNotEmpty()) {
            holder.btnBuy.visibility = View.VISIBLE
            holder.btnBuy.setOnClickListener {
                openGoogleShopping(
                    holder.itemView.context,
                    product.name,
                    product.category
                )
            }
        } else {
            holder.btnBuy.visibility = View.GONE
        }

        // ── Delete Button ─────────────────────────────────────────────
        holder.tvDeleteBtn.setOnClickListener {
            onDeleteClick(product)
        }
    }

    // ── Google Shopping ───────────────────────────────────────────────
    private fun openGoogleShopping(
        context: android.content.Context,
        productName: String,
        category: String
    ) {
        val searchQuery = buildSearchQuery(productName, category)
        val encoded = Uri.encode(searchQuery)
        val shoppingUrl = "https://www.google.com/search?q=$encoded&tbm=shop"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shoppingUrl))
        context.startActivity(intent)
    }

    private fun buildSearchQuery(name: String, category: String): String {
        return when (category) {
            "Medicine"           -> "buy $name medicine"
            "Personal Care"      -> "buy $name"
            "Dairy"              -> "buy $name dairy"
            "Snacks"             -> "buy $name snacks"
            "Beverages"          -> "buy $name"
            "Baby Products"      -> "buy $name baby"
            "Bakery & Biscuits"  -> "buy $name biscuits"
            "Chocolates & Sweets"-> "buy $name"
            "Frozen Foods"       -> "buy $name"
            else                 -> "buy $name"
        }
    }

    override fun getItemCount() = products.size

    fun updateList(newList: List<Product>) {
        products = newList
        notifyDataSetChanged()
    }
}