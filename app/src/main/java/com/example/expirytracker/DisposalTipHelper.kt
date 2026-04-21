package com.example.expirytracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
object DisposalTipHelper {

    private const val API_KEY = BuildConfig.GROQ_API_KEY
    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"

    suspend fun getDisposalTip(productName: String, category: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    The product "$productName" (category: $category) has expired.
                    In 2-3 short sentences, give practical eco-friendly disposal advice.
                    Cover composting, recycling the packaging, or safe disposal.
                    Be specific to this product. Keep it simple and actionable.
                """.trimIndent()

                val requestBody = JSONObject().apply {
                    put("model", "llama-3.1-8b-instant")  // Free model
                    put("max_tokens", 120)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $API_KEY")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 15000
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    // Parse OpenAI-compatible response
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                } else {
                    getOfflineFallbackTip(category)
                }
            } catch (e: Exception) {
                getOfflineFallbackTip(category)
            }
        }
    }

    private fun getOfflineFallbackTip(category: String): String {
        return when (category.lowercase()) {
            "dairy", "milk", "cheese", "yogurt" ->
                "Pour liquids down the drain with running water. Solid dairy can go in compost; otherwise wrap tightly before binning."
            "meat", "fish", "poultry" ->
                "Wrap tightly in newspaper or a sealed bag before general waste. Check if your council collects food waste for green energy."
            "fruits", "vegetables" ->
                "Add to a home compost bin or food waste caddy — ideal for composting and green energy generation."
            "bread", "bakery", "grains" ->
                "Add to compost or crumble outdoors for birds if not mouldy. Avoid landfill — bread produces methane when sealed."
            "medicine", "supplements" ->
                "Return to your local pharmacy — most accept expired medications for safe incineration. Never bin or flush."
            "beverages", "drinks" ->
                "Pour liquids down the sink. Rinse and recycle the bottle or carton at kerbside or bottle banks."
            else ->
                "Separate recyclable packaging from food waste. Food contents go in compost or council food waste bin."
        }
    }
}