package com.example.expirytracker

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiTestActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var btnTest: Button

    private val API_KEY =
        "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"
    private val BASE = "https://openrouter.ai/api/v1/chat/completions"
    private val MODELS_URL = "https://openrouter.ai/api/v1/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        btnTest = Button(this).apply {
            text = "RUN API TESTS"
            setOnClickListener { runTests() }
        }
        tvLog = TextView(this).apply {
            text = "Press button to run tests...\n"
            textSize = 12f
            setTextIsSelectable(true)
        }
        layout.addView(btnTest)
        layout.addView(tvLog)
        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLog.append("$msg\n")
            android.util.Log.d("ApiTest", msg)
        }
    }

    private fun runTests() {
        btnTest.isEnabled = false
        tvLog.text = "Starting...\n\n"
        lifecycleScope.launch {
            testInternet()
            fetchAndTestFreeModels()
            log("\n=== DONE ===")
            btnTest.isEnabled = true
        }
    }

    private suspend fun testInternet() = withContext(Dispatchers.IO) {
        log("=== TEST 1: Internet ===")
        try {
            val req = Request.Builder()
                .url("https://www.google.com").head().build()
            val start = System.currentTimeMillis()
            val resp = client.newCall(req).execute()
            val ms = System.currentTimeMillis() - start
            log("OK: ${resp.code} in ${ms}ms")
            resp.close()
        } catch (e: Exception) {
            log("FAIL: ${e.message}")
        }
    }

    // Fetch models list from OpenRouter then test free vision ones
    private suspend fun fetchAndTestFreeModels() =
        withContext(Dispatchers.IO) {
            log("\n=== TEST 2: Fetching free models from OpenRouter ===")
            try {
                val req = Request.Builder()
                    .url(MODELS_URL)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .build()
                val resp = client.newCall(req).execute()
                val code = resp.code
                val body = resp.body?.string() ?: ""
                resp.close()

                if (code != 200) {
                    log("FAIL fetching models: HTTP $code")
                    log(body.take(100))
                    return@withContext
                }

                // Parse free models that support vision
                val root = org.json.JSONObject(body)
                val data = root.getJSONArray("data")
                val freeVisionModels = mutableListOf<String>()

                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val id = m.optString("id", "")
                    if (!id.contains(":free")) continue

                    // Check if supports vision/image input
                    val arch = m.optJSONObject("architecture")
                    val inputModalities = arch
                        ?.optJSONArray("input_modalities")
                    var hasVision = false
                    if (inputModalities != null) {
                        for (j in 0 until inputModalities.length()) {
                            if (inputModalities.getString(j)
                                    .contains("image")) {
                                hasVision = true
                                break
                            }
                        }
                    }
                    if (hasVision) freeVisionModels.add(id)
                }

                log("Found ${freeVisionModels.size} free vision models:")
                freeVisionModels.forEach { log("  $it") }

                if (freeVisionModels.isEmpty()) {
                    log("\nNo free vision models found!")
                    log("Trying known models anyway...")
                    // Try these known models as fallback
                    val fallback = listOf(
                        "google/gemini-2.0-flash-thinking-exp:free",
                        "google/gemini-2.5-pro-exp-03-25:free",
                        "mistralai/mistral-small-3.1-24b-instruct:free",
                        "moonshotai/moonlight-16b-a3b-instruct:free"
                    )
                    fallback.forEach { testModel(it) }
                } else {
                    // Test first 3 free vision models found
                    freeVisionModels.take(3).forEach { testModel(it) }
                }

            } catch (e: Exception) {
                log("Error: ${e.message}")
            }
        }

    private fun testModel(model: String) {
        log("\n--- Testing: $model ---")
        try {
            val json = JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Say: hello")
                            })
                        })
                    })
                })
                put("max_tokens", 10)
            }

            val body = json.toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(BASE).post(body)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("HTTP-Referer", "https://savesmart.app")
                .addHeader("X-Title", "SaveSmart")
                .build()

            val start = System.currentTimeMillis()
            val resp = client.newCall(req).execute()
            val ms = System.currentTimeMillis() - start
            val code = resp.code
            val respBody = resp.body?.string() ?: ""
            resp.close()

            if (code == 200) {
                val text = JSONObject(respBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                log("WORKS (${ms}ms): $text")
                log(">>> USE THIS MODEL: $model <<<")
                // Test with image too
                testWithImage(model)
            } else {
                log("FAIL HTTP $code: ${respBody.take(80)}")
            }
        } catch (e: Exception) {
            log("ERROR: ${e.message?.take(60)}")
        }
    }

    private fun testWithImage(model: String) {
        try {
            val bmp = android.graphics.Bitmap.createBitmap(
                150, 60, android.graphics.Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK; textSize = 18f
            }
            canvas.drawText("EXP: 09/2026", 5f, 35f, paint)
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
            val b64 = android.util.Base64.encodeToString(
                out.toByteArray(), android.util.Base64.NO_WRAP)

            val json = JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$b64")
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "What expiry date is shown?")
                            })
                        })
                    })
                })
                put("max_tokens", 20)
            }
            val body = json.toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(BASE).post(body)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("HTTP-Referer", "https://savesmart.app")
                .build()
            val start = System.currentTimeMillis()
            val resp = client.newCall(req).execute()
            val ms = System.currentTimeMillis() - start
            val code = resp.code
            val respBody = resp.body?.string() ?: ""
            resp.close()

            if (code == 200) {
                val text = JSONObject(respBody)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                log("IMAGE WORKS (${ms}ms): $text")
                log(">>> CONFIRMED: $model works with images <<<")
                log(">>> UPDATE AiDateExtractor MODELS list with this <<<")
            } else {
                log("IMAGE FAIL HTTP $code: ${respBody.take(80)}")
            }
        } catch (e: Exception) {
            log("IMAGE ERROR: ${e.message?.take(60)}")
        }
    }
}