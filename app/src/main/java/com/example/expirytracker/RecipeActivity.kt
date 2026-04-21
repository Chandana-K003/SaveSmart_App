package com.example.expirytracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expirytracker.data.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Recipe(
    val name: String,
    val description: String,
    val ingredients: List<String>,
    val usesExpiring: List<String>
)

class RecipeActivity : AppCompatActivity() {

    private lateinit var repository: ProductRepository
    private lateinit var tvStatus: TextView
    private lateinit var tvExpiringItems: TextView
    private lateinit var btnGetRecipes: Button
    private lateinit var rvRecipes: RecyclerView
    private lateinit var layoutRecipeDetail: LinearLayout
    private lateinit var tvRecipeName: TextView
    private lateinit var tvRecipeDesc: TextView
    private lateinit var tvIngredients: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar

    private val apiKey =
        "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"
    private val apiUrl =
        "https://openrouter.ai/api/v1/chat/completions"
    private val model = "google/gemma-3-4b-it:free"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var recipes = listOf<Recipe>()
    private var expiringProducts = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ProductRepository(this)
        setupUI()
        loadExpiringItems()
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(48, 40, 48, 40)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(32, 24, 32, 0)
            layoutParams = p
            elevation = 4f
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 24f
                }
        }
    }

    private fun setupUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(
                android.graphics.Color.parseColor("#F8FFF8"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 48)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                android.graphics.Color.parseColor("#2E7D32"))
            setPadding(48, 64, 48, 40)
        }
        header.addView(TextView(this).apply {
            text = "🍳 Recipe Engine"
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Recipes using your near-expiry items"
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#A5D6A7"))
            setPadding(0, 8, 0, 0)
        })

        // Expiring items card
        val cardExpiring = makeCard()
        cardExpiring.addView(TextView(this).apply {
            text = "⚠️ Near-Expiry Items (within 30 days)"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                android.graphics.Color.parseColor("#E65100"))
        })
        tvExpiringItems = TextView(this).apply {
            text = "Loading..."
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#555555"))
            setPadding(0, 16, 0, 0)
        }
        cardExpiring.addView(tvExpiringItems)

        // Get Recipes button
        btnGetRecipes = Button(this).apply {
            text = "🤖 Suggest Recipes with AI"
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#2E7D32"))
                    cornerRadius = 48f
                }
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140)
            p.setMargins(32, 24, 32, 0)
            layoutParams = p
            setOnClickListener { fetchRecipes() }
        }

        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = android.view.Gravity.CENTER_HORIZONTAL
            p.setMargins(0, 24, 0, 0)
            layoutParams = p
        }

        // Status text
        tvStatus = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#1565C0"))
            gravity = android.view.Gravity.CENTER
            setPadding(48, 16, 48, 0)
        }

        // Recipe list
        rvRecipes = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@RecipeActivity)
            visibility = View.GONE
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(32, 16, 32, 0)
            layoutParams = p
        }

        // Recipe detail card
        layoutRecipeDetail = makeCard().apply {
            visibility = View.GONE
        }

        btnBack = Button(this).apply {
            text = "← Back to Recipes"
            textSize = 13f
            setTextColor(
                android.graphics.Color.parseColor("#2E7D32"))
            setBackgroundColor(
                android.graphics.Color.TRANSPARENT)
            setOnClickListener {
                layoutRecipeDetail.visibility = View.GONE
                rvRecipes.visibility = View.VISIBLE
            }
        }
        tvRecipeName = TextView(this).apply {
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                android.graphics.Color.parseColor("#1B5E20"))
            setPadding(0, 8, 0, 8)
        }
        tvRecipeDesc = TextView(this).apply {
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#555555"))
            setPadding(0, 0, 0, 20)
        }
        val tvIngTitle = TextView(this).apply {
            text = "📋 Ingredients"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                android.graphics.Color.parseColor("#2E7D32"))
            setPadding(0, 0, 0, 8)
        }
        tvIngredients = TextView(this).apply {
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#333333"))
            setPadding(0, 0, 0, 20)
        }
        val tvInstTitle = TextView(this).apply {
            text = "👨‍🍳 Preparation Instructions"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                android.graphics.Color.parseColor("#2E7D32"))
            setPadding(0, 0, 0, 8)
        }
        tvInstructions = TextView(this).apply {
            textSize = 14f
            setTextColor(
                android.graphics.Color.parseColor("#333333"))
        }

        layoutRecipeDetail.addView(btnBack)
        layoutRecipeDetail.addView(tvRecipeName)
        layoutRecipeDetail.addView(tvRecipeDesc)
        layoutRecipeDetail.addView(tvIngTitle)
        layoutRecipeDetail.addView(tvIngredients)
        layoutRecipeDetail.addView(tvInstTitle)
        layoutRecipeDetail.addView(tvInstructions)

        root.addView(header)
        root.addView(cardExpiring)
        root.addView(btnGetRecipes)
        root.addView(progressBar)
        root.addView(tvStatus)
        root.addView(rvRecipes)
        root.addView(layoutRecipeDetail)
        scroll.addView(root)
        setContentView(scroll)
    }

    // ── Load near-expiry items ────────────────────────────────────

    private fun loadExpiringItems() {
        lifecycleScope.launch {
            val products = withContext(Dispatchers.IO) {
                repository.getAllProducts()
            }
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 24 * 60 * 60 * 1000

            val expiring = products.filter { p ->
                val diff = p.expiryDate - now
                diff in 0..thirtyDays
            }

            expiringProducts = expiring
                .map { it.name }
                .filter { it.isNotEmpty() }
                .distinct()

            if (expiringProducts.isEmpty()) {
                tvExpiringItems.text =
                    "No near-expiry items found.\n" +
                            "Add products to get recipe suggestions."
                btnGetRecipes.isEnabled = false
                btnGetRecipes.alpha = 0.5f
            } else {
                tvExpiringItems.text = expiringProducts
                    .joinToString("\n") { "• $it" }
            }
        }
    }

    // ── Fetch recipes ─────────────────────────────────────────────

    private fun fetchRecipes() {
        if (expiringProducts.isEmpty()) return
        btnGetRecipes.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "🤖 AI is thinking of recipes..."
        rvRecipes.visibility = View.GONE
        layoutRecipeDetail.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                callAiForRecipes(expiringProducts)
            }
            progressBar.visibility = View.GONE
            btnGetRecipes.isEnabled = true

            if (result != null && result.isNotEmpty()) {
                recipes = result
                tvStatus.text =
                    "✅ Found ${result.size} recipes!"
                showRecipeList(result)
            } else {
                tvStatus.text =
                    "❌ Could not get recipes. Try again."
            }
        }
    }

    // ── AI: Recipe list ───────────────────────────────────────────

    private fun callAiForRecipes(
        items: List<String>
    ): List<Recipe>? {
        return try {
            val itemsList = items.joinToString(", ")
            val prompt = """
You are a helpful recipe assistant.
The user has these items expiring soon: $itemsList
Suggest exactly 5 recipes using one or more of these items.
Respond ONLY with this JSON (no other text):
{
  "recipes": [
    {
      "name": "Recipe Name",
      "description": "One sentence description",
      "ingredients": ["item1", "item2", "item3"],
      "uses_expiring": ["expiring item used"]
    }
  ]
}
""".trimIndent()

            val reqBody = buildRequest(prompt, 1200)
            val request = Request.Builder()
                .url(apiUrl).post(reqBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer",
                    "https://savesmart.app")
                .addHeader("X-Title", "SaveSmart")
                .build()

            val resp = client.newCall(request).execute()
            val respCode = resp.code
            val respBody = resp.body?.string() ?: ""
            resp.close()

            android.util.Log.d("Recipe",
                "Recipes code:$respCode")
            if (respCode != 200) {
                android.util.Log.e("Recipe",
                    "Error: $respBody")
                return null
            }

            val raw = extractContent(respBody)
            val cleaned = raw
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```").trim()

            val s = cleaned.indexOf('{')
            val e = cleaned.lastIndexOf('}')
            if (s == -1 || e == -1) return null

            val parsed = JSONObject(cleaned.substring(s, e + 1))
            val arr = parsed.getJSONArray("recipes")
            val result = mutableListOf<Recipe>()

            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val ing = r.getJSONArray("ingredients")
                val exp = r.getJSONArray("uses_expiring")
                result.add(Recipe(
                    name = r.optString("name", "Recipe ${i+1}"),
                    description = r.optString("description", ""),
                    ingredients = (0 until ing.length())
                        .map { ing.getString(it) },
                    usesExpiring = (0 until exp.length())
                        .map { exp.getString(it) }
                ))
            }
            result

        } catch (ex: Exception) {
            android.util.Log.e("Recipe", "${ex.message}")
            null
        }
    }

    // ── AI: Instructions ──────────────────────────────────────────

    private fun callAiForInstructions(
        recipe: Recipe
    ): String? {
        return try {
            val prompt = """
Give step-by-step cooking instructions for: ${recipe.name}
Ingredients: ${recipe.ingredients.joinToString(", ")}
Give 5-6 numbered steps. Be brief and clear.
""".trimIndent()

            val reqBody = buildRequest(prompt, 400)
            val request = Request.Builder()
                .url(apiUrl).post(reqBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer",
                    "https://savesmart.app")
                .addHeader("X-Title", "SaveSmart")
                .build()

            val resp = client.newCall(request).execute()
            val respCode = resp.code
            val respBody = resp.body?.string() ?: ""
            resp.close()

            android.util.Log.d("Recipe",
                "Instructions code:$respCode")
            if (respCode != 200) {
                android.util.Log.e("Recipe",
                    "Instructions error: $respBody")
                return null
            }

            extractContent(respBody)

        } catch (ex: Exception) {
            android.util.Log.e("Recipe",
                "Instructions: ${ex.message}")
            null
        }
    }

    // ── Build request body ────────────────────────────────────────

    private fun buildRequest(
        prompt: String,
        maxTokens: Int
    ) = JSONObject().apply {
        put("model", model)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                })
            })
        })
        put("max_tokens", maxTokens)
        put("temperature", 0.5)
    }.toString().toRequestBody(
        "application/json".toMediaType())

    // ── Extract content from OpenRouter response ──────────────────

    private fun extractContent(respBody: String): String {
        return JSONObject(respBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content").trim()
    }

    // ── Show recipe list ──────────────────────────────────────────

    private fun showRecipeList(list: List<Recipe>) {
        rvRecipes.visibility = View.VISIBLE
        rvRecipes.adapter = RecipeListAdapter(list) { recipe ->
            showRecipeDetail(recipe)
        }
    }

    // ── Show recipe detail ────────────────────────────────────────

    private fun showRecipeDetail(recipe: Recipe) {
        rvRecipes.visibility = View.GONE
        layoutRecipeDetail.visibility = View.VISIBLE

        tvRecipeName.text = "🍽️ ${recipe.name}"
        tvRecipeDesc.text = recipe.description

        val ingText = StringBuilder()
        recipe.ingredients.forEach { ing ->
            val isExpiring = recipe.usesExpiring.any {
                ing.lowercase().contains(it.lowercase()) ||
                        it.lowercase().contains(ing.lowercase())
            }
            if (isExpiring)
                ingText.appendLine("⚠️ $ing  ← use soon!")
            else
                ingText.appendLine("• $ing")
        }
        tvIngredients.text = ingText.toString().trim()
        tvInstructions.text = "⏳ Loading instructions..."

        // Remove old YouTube button if exists
        val existingYt = layoutRecipeDetail
            .findViewWithTag<Button>("btnYoutube")
        if (existingYt != null)
            layoutRecipeDetail.removeView(existingYt)

        // Add YouTube button
        val btnYoutube = Button(this).apply {
            tag = "btnYoutube"
            text = "▶ Watch on YouTube"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#FF0000"))
                    cornerRadius = 48f
                }
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 120)
            p.setMargins(0, 24, 0, 0)
            layoutParams = p
            setOnClickListener {
                val query = Uri.encode("${recipe.name} recipe")
                val ytUrl =
                    "https://www.youtube.com/results" +
                            "?search_query=$query"
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(ytUrl))
                intent.setPackage(
                    "com.google.android.youtube")
                try {
                    startActivity(intent)
                } catch (ex: Exception) {
                    intent.setPackage(null)
                    startActivity(intent)
                }
            }
        }
        layoutRecipeDetail.addView(btnYoutube)

        // Load instructions
        lifecycleScope.launch {
            val instructions = withContext(Dispatchers.IO) {
                callAiForInstructions(recipe)
            }
            tvInstructions.text = instructions
                ?: "Could not load instructions. Try again."
        }
    }
}

// ── RecyclerView Adapter ──────────────────────────────────────────

class RecipeListAdapter(
    private val recipes: List<Recipe>,
    private val onClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeListAdapter.VH>() {

    class VH(val card: LinearLayout) :
        RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VH {
        val ctx = parent.context
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 24)
            layoutParams = p
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 24f
                    setStroke(2, android.graphics.Color
                        .parseColor("#E8F5E9"))
                }
            elevation = 3f
            isClickable = true
            isFocusable = true
        }
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = recipes[position]
        val ctx = holder.card.context
        holder.card.removeAllViews()

        holder.card.addView(TextView(ctx).apply {
            text = "🍽️ ${r.name}"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color
                .parseColor("#1B5E20"))
        })
        holder.card.addView(TextView(ctx).apply {
            text = r.description
            textSize = 13f
            setTextColor(android.graphics.Color
                .parseColor("#666666"))
            setPadding(0, 8, 0, 12)
        })
        holder.card.addView(TextView(ctx).apply {
            text = "⚠️ Uses: ${
                r.usesExpiring.joinToString(", ")}"
            textSize = 12f
            setTextColor(android.graphics.Color
                .parseColor("#E65100"))
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#FFF3E0"))
                    cornerRadius = 32f
                }
            setPadding(24, 10, 24, 10)
        })
        holder.card.addView(TextView(ctx).apply {
            text = "View Recipe →"
            textSize = 13f
            setTextColor(android.graphics.Color
                .parseColor("#2E7D32"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 0)
        })
        holder.card.setOnClickListener { onClick(r) }
    }

    override fun getItemCount() = recipes.size
}