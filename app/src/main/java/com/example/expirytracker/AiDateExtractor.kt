package com.example.expirytracker

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AiDateResult(
    val success: Boolean,
    val extractedDate: String,
    val confidence: Int,
    val reasoning: String
)

object AiDateExtractor {

    private const val API_KEY = "YOUR_GEMINI_API_KEY_HERE"
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-1.5-flash:generateContent?key=$API_KEY"

    fun isAiAvailable(): Boolean =
        API_KEY.isNotEmpty() &&
                API_KEY != "YOUR_GEMINI_API_KEY_HERE"

    suspend fun extractDateWithAi(
        bitmap: Bitmap,
        ocrText: String
    ): AiDateResult = withContext(Dispatchers.IO) {

        if (!isAiAvailable()) {
            return@withContext AiDateResult(
                false, "", 0, "NO_API_KEY")
        }

        val result = withTimeoutOrNull(20_000L) {
            try {
                // ── Resize + compress image ──────────────────────
                val resized = resizeBitmap(bitmap, 480)
                val outputStream = ByteArrayOutputStream()
                resized.compress(
                    Bitmap.CompressFormat.JPEG, 70, outputStream)
                val base64Image = Base64.encodeToString(
                    outputStream.toByteArray(), Base64.NO_WRAP)

                val prompt = buildStrictPrompt(ocrText)

                val requestBody = buildRequest(base64Image, prompt)

                // ── HTTP call ────────────────────────────────────
                val url = URL(API_URL)
                val conn = url.openConnection()
                        as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty(
                        "Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 18000
                }

                conn.outputStream.use { os ->
                    os.write(requestBody
                        .toString().toByteArray())
                }

                if (conn.responseCode != 200) {
                    val err = conn.errorStream
                        ?.bufferedReader()?.readText() ?: ""
                    android.util.Log.e("AiDateExtractor",
                        "HTTP ${conn.responseCode}: $err")
                    return@withTimeoutOrNull AiDateResult(
                        false, "", 0,
                        "HTTP_${conn.responseCode}")
                }

                val response = conn.inputStream
                    .bufferedReader().readText()

                parseResponse(response)

            } catch (e: java.net.UnknownHostException) {
                AiDateResult(false, "", 0, "NO_INTERNET")
            } catch (e: java.net.SocketTimeoutException) {
                AiDateResult(false, "", 0, "TIMEOUT")
            } catch (e: Exception) {
                android.util.Log.e("AiDateExtractor",
                    "Exception: ${e.message}")
                AiDateResult(false, "", 0,
                    "Error: ${e.message?.take(50)}")
            }
        }

        result ?: AiDateResult(false, "", 0, "TIMEOUT")
    }

    // ── STRICT PROMPT ────────────────────────────────────────────
    private fun buildStrictPrompt(ocrText: String): String {
        return """
You are a strict expiry date extractor for product packaging.

OCR raw text from the package (may have errors):
---
$ocrText
---

YOUR ONLY JOB: Find the EXPIRY DATE. Not MFD. Not batch. Not weight.

════════════════════════════════════════
RULE 1 — EXPIRY LABELS (only accept dates next to these):
  EXP / EXPIRY / EXPIRY DATE
  USE BY / USE BEFORE / USE-BY
  BEST BEFORE / BEST BY / BB / BBD
  SELL BY / SELL BEFORE
  Valid examples from OCR:
    "EXP 06/2026"        → expiry_date = "06/2026"
    "Best Before 12/25"  → expiry_date = "12/2025"
    "Use By 15 Mar 2026" → expiry_date = "03/2026"

════════════════════════════════════════
RULE 2 — MFD LABELS (these are MANUFACTURING dates — NEVER return these):
  MFD / MFG / MAN / MANU / MANUFACTURED
  PACKED ON / PKD / DOM / DATE OF MFG
  PRODUCTION DATE / PROD DATE / LOT
  IMPORTANT: Even if expiry label is missing,
  DO NOT return MFD date as expiry date.
  Example from OCR: "MFD 01/2025 EXP 01/2027"
    → CORRECT: expiry_date = "01/2027"
    → WRONG: expiry_date = "01/2025"  ← never do this

════════════════════════════════════════
RULE 3 — LOOK AT POSITION ON PACK:
  On Indian products, expiry date is usually:
  - Printed AFTER the MFD date on the same line
  - On the TOP or BOTTOM edge of the pack
  - The LATER date is almost always the expiry date
  - If two dates exist and one is clearly older,
    the NEWER date is the expiry date

════════════════════════════════════════
RULE 4 — REJECT NON-DATE TEXT:
  Do NOT extract:
  - Batch numbers (e.g., "Batch: A1234B")
  - Weight or volume (e.g., "200g", "500ml")
  - Price (e.g., "MRP 45")
  - Phone numbers or barcodes
  - License numbers (e.g., "FSSAI 123456")
  - Any number that is NOT a recognizable date
  If you are not 80%+ sure it is an expiry date → return found=false

════════════════════════════════════════
RULE 5 — DATE FORMAT RULES:
  Always return in format: MM/YYYY or DD/MM/YYYY
  Examples:
    "06/26"     → "06/2026"   (year fix)
    "6/2026"    → "06/2026"   (pad month)
    "5 Jun 26"  → "06/2026"
    "Jan-2027"  → "01/2027"
  The year must be between 2024 and 2035.
  If year is outside this range → likely wrong, return found=false

════════════════════════════════════════
RULE 6 — WHEN EXPIRY IS MISSING/DAMAGED:
  ONLY calculate expiry from MFD if ALL of these are true:
    a) You can clearly see a MFD/MFG label and date
    b) You can see a "Best Before X months" statement
    c) You are 90%+ confident in both values
  Calculation: MFD date + stated shelf life = expiry
  If any doubt → return found=false

════════════════════════════════════════

Respond ONLY with this exact JSON — no other text, no markdown:
{
  "found": true,
  "expiry_date": "MM/YYYY",
  "confidence": 90,
  "label_seen": "EXP",
  "reasoning": "Saw EXP label followed by date. MFD date was XX/XXXX which was ignored."
}

If no valid expiry date found:
{
  "found": false,
  "expiry_date": "",
  "confidence": 0,
  "label_seen": "",
  "reasoning": "Only found MFD date / No expiry label visible / Non-date text"
}
""".trimIndent()
    }

    // ── BUILD REQUEST ────────────────────────────────────────────
    private fun buildRequest(
        base64Image: String,
        prompt: String
    ): JSONObject {
        return JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        // Image first
                        put(JSONObject().apply {
                            put("inline_data",
                                JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                        })
                        // Then prompt
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                // Low temperature = more deterministic,
                // less hallucination
                put("temperature", 0.05)
                put("maxOutputTokens", 150)
                put("topP", 0.8)
                put("topK", 10)
            })
        }
    }

    // ── PARSE RESPONSE ───────────────────────────────────────────
    private fun parseResponse(response: String): AiDateResult {
        return try {
            val root = JSONObject(response)
            val rawText = root
                .optJSONArray("candidates")
                ?.getJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.getJSONObject(0)
                ?.optString("text", "") ?: ""

            android.util.Log.d("AiDateExtractor",
                "AI raw response: $rawText")

            // Strip markdown fences
            val cleaned = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Extract JSON block
            val start = cleaned.indexOf('{')
            val end   = cleaned.lastIndexOf('}')
            if (start == -1 || end == -1) {
                return AiDateResult(
                    false, "", 0, "No JSON in response")
            }

            val json = JSONObject(
                cleaned.substring(start, end + 1))

            val found      = json.optBoolean("found", false)
            val date       = json.optString(
                "expiry_date", "").trim()
            val confidence = json.optInt("confidence", 0)
            val reasoning  = json.optString("reasoning", "")
            val labelSeen  = json.optString("label_seen", "")

            android.util.Log.d("AiDateExtractor",
                "Parsed → found=$found date=$date " +
                        "conf=$confidence label=$labelSeen")

            // ── Post-parse safety checks ─────────────────────
            // Reject if confidence too low
            if (!found || confidence < 70) {
                return AiDateResult(false, "", 0,
                    "Low confidence: $reasoning")
            }

            // Reject if date is empty
            if (date.isEmpty()) {
                return AiDateResult(false, "", 0,
                    "Empty date returned")
            }

            // Reject if year looks wrong
            val yearMatch = Regex(
                "\\d{4}").find(date)?.value?.toIntOrNull()
            if (yearMatch != null &&
                (yearMatch < 2024 || yearMatch > 2035)) {
                android.util.Log.w("AiDateExtractor",
                    "Rejected out-of-range year: $yearMatch")
                return AiDateResult(false, "", 0,
                    "Year $yearMatch out of valid range")
            }

            AiDateResult(true, date, confidence, reasoning)

        } catch (e: Exception) {
            android.util.Log.e("AiDateExtractor",
                "Parse error: ${e.message}")
            AiDateResult(false, "", 0,
                "Parse error: ${e.message?.take(40)}")
        }
    }

    // ── RESIZE BITMAP ────────────────────────────────────────────
    private fun resizeBitmap(
        bitmap: Bitmap, maxSize: Int
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val ratio = w.toFloat() / h.toFloat()
        val newW: Int
        val newH: Int
        if (w > h) {
            newW = maxSize
            newH = (maxSize / ratio).toInt()
        } else {
            newH = maxSize
            newW = (maxSize * ratio).toInt()
        }
        return try {
            Bitmap.createScaledBitmap(
                bitmap,
                newW.coerceAtLeast(1),
                newH.coerceAtLeast(1),
                true)
        } catch (e: Exception) { bitmap }
    }
}