package com.example.expirytracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@androidx.camera.core.ExperimentalGetImage
class ProductCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var tvProductName: TextView
    private lateinit var tvAiNote: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnRescan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutResult: LinearLayout
    private lateinit var imageCapture: ImageCapture

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS)
    private var detectedProductName = ""

    private val apiKey =
        "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"
    private val apiUrl =
        "https://openrouter.ai/api/v1/chat/completions"

    // Vision models — ordered by reliability for product reading
    private val visionModels = listOf(
        "google/gemma-3-12b-it:free",
        "nvidia/nemotron-nano-12b-v2-vl:free",
        "google/gemma-3-4b-it:free"
    )

    private val aiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    companion object {
        const val RESULT_PRODUCT_NAME = "product_name"
    }

    private val skipWords = setOf(
        "best before", "use by", "use before", "expiry",
        "mfg", "mfd", "manufactured", "batch", "lot no",
        "net wt", "net weight", "net vol", "nett wt",
        "mrp", "price inclusive", "rs.", "rupees",
        "ingredients", "nutrition facts", "nutritional",
        "fssai", "lic no", "license no",
        "customer care", "helpline", "toll free",
        "www.", "http", "@gmail", "@yahoo",
        "manufactured by", "marketed by", "packed by",
        "imported by", "distributed by",
        "store below", "keep in cool", "refrigerate",
        "per 100g", "per serving", "daily value",
        "protein", "carbohydrate", "energy kcal", "calories",
        "sodium", "allergen",
        "country of origin", "made in india",
        "barcode", "ean code"
    )

    private val brandKeywords = setOf(
        "amul", "parle", "britannia", "nestle", "maggi",
        "tata", "dabur", "patanjali", "mdh", "everest",
        "itc", "godrej", "marico", "colgate", "dettol",
        "lifebuoy", "dove", "lux", "pepsodent", "closeup",
        "haldiram", "bikaji", "mother dairy", "nandini",
        "aashirvaad", "fortune", "saffola", "sunfeast",
        "good day", "bourbon", "oreo", "kellogg", "quaker",
        "heinz", "kissan", "mtr", "catch", "badshah",
        "kohinoor", "daawat", "india gate", "kurkure",
        "lays", "lay's", "bingo", "pringles",
        "coca cola", "pepsi", "sprite", "fanta", "thums up",
        "frooti", "maaza", "tropicana", "nescafe", "bru",
        "horlicks", "bournvita", "complan", "boost", "milo",
        "cadbury", "dairy milk", "kitkat", "snickers",
        "himalaya", "nivea", "vaseline", "ponds", "lakme",
        "garnier", "loreal", "pantene", "savlon", "band aid",
        "bio-oil", "bio oil", "volini", "moov", "vicks",
        "zandu", "hamdard", "surf", "ariel", "tide", "rin",
        "nirma", "vim", "harpic", "lizol", "domex",
        "whisper", "pampers", "johnson", "cetaphil",
        "neutrogena", "olay", "fair lovely", "fair glow"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private fun setupUI() {
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(
                android.graphics.Color.parseColor("#CC000000"))
            setPadding(24, 56, 24, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "\u2190"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 16, 0)
            setOnClickListener { finish() }
        })
        topBar.addView(TextView(this).apply {
            text = "\ud83d\udce6 Identify Product"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(TextView(this).apply {
            text = "\ud83e\udd16 AI Vision"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#A5D6A7"))
        })

        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val screenW = resources.displayMetrics.widthPixels
        val frameW = screenW - 80
        val frameH = (frameW * 0.65f).toInt()

        val scanFrame = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(frameW, frameH)
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#22FFFFFF"))
                    cornerRadius = 20f
                    setStroke(3,
                        android.graphics.Color.parseColor("#4CAF50"))
                }
        }

        val tvGuide = TextView(this).apply {
            text = "Point at the FRONT of the package\nwhere the product name is visible"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(32, 16, 32, 0)
        }

        // Tip for transparent bottles
        val tvTip = TextView(this).apply {
            text = "\ud83d\udca1 Tip: For clear bottles, ensure\ngood lighting & hold steady"
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#FFCC80"))
            gravity = android.view.Gravity.CENTER
            setPadding(32, 6, 32, 0)
        }

        val tvPipeline = TextView(this).apply {
            text = "OCR \u2192 AI Vision \u2192 Verified name"
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#A5D6A7"))
            gravity = android.view.Gravity.CENTER
            setPadding(32, 2, 32, 0)
        }

        tvStatus = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#A5D6A7"))
            gravity = android.view.Gravity.CENTER
            setPadding(32, 8, 32, 0)
        }

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = android.view.Gravity.CENTER_HORIZONTAL
            p.setMargins(0, 12, 0, 0)
            layoutParams = p
        }

        btnCapture = Button(this).apply {
            text = "\ud83d\udcf7  Capture & Identify"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#2E7D32"))
                    cornerRadius = 56f
                }
            val p = LinearLayout.LayoutParams(frameW, 130)
            p.gravity = android.view.Gravity.CENTER_HORIZONTAL
            p.setMargins(0, 20, 0, 0)
            layoutParams = p
            setOnClickListener { captureAndIdentify() }
        }

        centerLayout.addView(scanFrame)
        centerLayout.addView(tvGuide)
        centerLayout.addView(tvTip)
        centerLayout.addView(tvPipeline)
        centerLayout.addView(tvStatus)
        centerLayout.addView(progressBar)
        centerLayout.addView(btnCapture)

        // Result panel
        layoutResult = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#F1F8E9"))
            setPadding(32, 20, 32, 32)
            visibility = View.GONE
        }

        val tvResultLabel = TextView(this).apply {
            text = "\ud83d\udce6 Product Name:"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#558B2F"))
            gravity = android.view.Gravity.CENTER
        }

        tvProductName = TextView(this).apply {
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1B5E20"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 6, 0, 4)
        }

        tvAiNote = TextView(this).apply {
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#E65100"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 2, 0, 6)
            visibility = View.GONE
        }

        val tvPickLabel = TextView(this).apply {
            text = "Not right? Tap an alternative:"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#558B2F"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 4)
            visibility = View.GONE
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        btnRescan = Button(this).apply {
            text = "\ud83d\udd04 Retake"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#558B2F"))
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#C8E6C9"))
                    cornerRadius = 32f
                }
            val p = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            p.setMargins(0, 0, 8, 0)
            p.height = 100
            layoutParams = p
            setOnClickListener { resetCapture() }
        }

        btnConfirm = Button(this).apply {
            text = "\u2705 Use This Name"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#2E7D32"))
                    cornerRadius = 32f
                }
            val p = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            p.height = 100
            layoutParams = p
            setOnClickListener { confirmProduct() }
        }

        btnRow.addView(btnRescan)
        btnRow.addView(btnConfirm)

        // Child order: 0=label 1=name 2=aiNote 3=pickLabel 4=btnRow
        layoutResult.addView(tvResultLabel)
        layoutResult.addView(tvProductName)
        layoutResult.addView(tvAiNote)
        layoutResult.addView(tvPickLabel)
        layoutResult.addView(btnRow)

        overlay.addView(topBar)
        overlay.addView(centerLayout)
        overlay.addView(layoutResult)
        root.addView(previewView)
        root.addView(overlay)
        setContentView(root)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture)
            } catch (e: Exception) {
                android.util.Log.e("ProductCapture",
                    "Camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Main pipeline ────────────────────────────────────────────────────

    private fun captureAndIdentify() {
        if (!::imageCapture.isInitialized) return

        btnCapture.isEnabled = false
        btnCapture.text = "\u23f3 Identifying..."
        tvStatus.text = "\ud83d\udcf7 Step 1/3: Capturing..."
        tvStatus.setTextColor(
            android.graphics.Color.parseColor("#A5D6A7"))
        progressBar.visibility = View.VISIBLE
        layoutResult.visibility = View.GONE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(imageProxy)
                        imageProxy.close()

                        if (bitmap == null) {
                            resetButton()
                            progressBar.visibility = View.GONE
                            tvStatus.text =
                                "\u274c Capture failed \u2014 try again"
                            return
                        }

                        tvStatus.text =
                            "\ud83d\udd0d Step 2/3: Reading text (OCR)..."

                        val mlImage = InputImage.fromBitmap(bitmap, 0)
                        textRecognizer.process(mlImage)
                            .addOnSuccessListener { visionText ->
                                val ocrText = visionText.text
                                val ocrCandidates =
                                    extractOcrCandidates(ocrText)

                                android.util.Log.d("ProductCapture",
                                    "OCR raw: $ocrText")
                                android.util.Log.d("ProductCapture",
                                    "OCR candidates: $ocrCandidates")

                                tvStatus.text =
                                    "\ud83e\udd16 Step 3/3: AI reading image..."

                                lifecycleScope.launch {
                                    val aiResult =
                                        withContext(Dispatchers.IO) {
                                            sendToAiVision(
                                                bitmap,
                                                ocrText,
                                                ocrCandidates)
                                        }
                                    progressBar.visibility = View.GONE
                                    resetButton()
                                    showResult(
                                        aiResult.first,
                                        ocrCandidates,
                                        aiResult.second)
                                }
                            }
                            .addOnFailureListener {
                                // OCR failed, still try AI with image only
                                tvStatus.text =
                                    "\ud83e\udd16 Step 3/3: AI reading image..."
                                lifecycleScope.launch {
                                    val aiResult =
                                        withContext(Dispatchers.IO) {
                                            sendToAiVision(
                                                bitmap, "", emptyList())
                                        }
                                    progressBar.visibility = View.GONE
                                    resetButton()
                                    showResult(
                                        aiResult.first,
                                        emptyList(),
                                        aiResult.second)
                                }
                            }

                    } catch (e: Exception) {
                        imageProxy.close()
                        progressBar.visibility = View.GONE
                        resetButton()
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    progressBar.visibility = View.GONE
                    resetButton()
                    tvStatus.text = "\u274c Capture failed"
                }
            })
    }

    // ── AI Vision: sends image + OCR to AI ───────────────────────────────

    private fun sendToAiVision(
        bitmap: Bitmap,
        ocrText: String,
        ocrCandidates: List<String>
    ): Pair<String, String> {

        // Higher resolution for transparent/tricky bottles
        val resized = resizeBitmap(bitmap, 512)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        android.util.Log.d("ProductCapture",
            "Image sent to AI: ${out.size() / 1024}KB")

        val ocrContext = when {
            ocrCandidates.isNotEmpty() ->
                "OCR detected these text candidates from the package: " +
                        "${ocrCandidates.joinToString(", ")}\n" +
                        "Full OCR text: \"${ocrText.take(300)}\"\n\n"
            ocrText.isNotBlank() ->
                "OCR full text (low confidence): " +
                        "\"${ocrText.take(300)}\"\n\n"
            else ->
                "Note: OCR found no readable text — " +
                        "please read the product name directly from the image.\n\n"
        }

        // Very direct prompt — no formatting tricks AI might misread
        val prompt = "This is a product package image.\n\n" +
                ocrContext +
                "What is the PRODUCT NAME shown on this package?\n\n" +
                "Instructions:\n" +
                "- Read the main brand name and product type from the image\n" +
                "- For transparent/clear bottles, read the text printed on the label\n" +
                "- Return ONLY the product name, 1-6 words maximum\n" +
                "- Do NOT include: weight (60ml, 500g), price, date, MRP\n" +
                "- Do NOT include: 'null', 'N/A', 'unknown', explanations\n" +
                "- Examples of CORRECT answers:\n" +
                "    Bio-Oil Skincare Oil\n" +
                "    Amul Butter\n" +
                "    Parle-G Biscuits\n" +
                "    Maggi Masala Noodles\n" +
                "    Tata Salt\n" +
                "    Colgate Strong Teeth\n\n" +
                "Reply format: PRODUCTNAME|NOTE\n" +
                "Where NOTE is empty, or a short reason if you corrected the OCR\n" +
                "Examples:\n" +
                "Bio-Oil Skincare Oil|\n" +
                "Amul Butter|\n" +
                "Maggi Noodles|OCR had garbled text\n\n" +
                "Your answer:"

        for (model in visionModels) {
            try {
                val contentArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$b64")
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                }

                val reqJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", contentArray)
                        })
                    })
                    put("max_tokens", 60)
                    put("temperature", 0.1)
                }

                val body = reqJson.toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(apiUrl).post(body)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://savesmart.app")
                    .addHeader("X-Title", "SaveSmart")
                    .build()

                val start = System.currentTimeMillis()
                val resp = aiClient.newCall(request).execute()
                val code = resp.code
                val respBody = resp.body?.string() ?: ""
                resp.close()
                val ms = System.currentTimeMillis() - start

                android.util.Log.d("ProductCapture",
                    "AI $model: ${ms}ms code=$code")

                if (code == 429) { continue }
                if (code != 200) {
                    android.util.Log.w("ProductCapture",
                        "AI error: ${respBody.take(300)}")
                    continue
                }

                val raw = JSONObject(respBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                android.util.Log.d("ProductCapture", "AI raw: '$raw'")

                // Clean markdown/code blocks
                val cleaned = raw
                    .removePrefix("```").removeSuffix("```")
                    .removePrefix("**").removeSuffix("**")
                    .trim()
                    .lines()
                    .firstOrNull { it.isNotEmpty() }
                    ?.trim() ?: ""

                // Parse PRODUCTNAME|NOTE
                val pipeIdx = cleaned.indexOf("|")
                val productName: String
                val note: String
                if (pipeIdx >= 0) {
                    productName = cleaned.substring(0, pipeIdx)
                        .trim()
                        .removePrefix("\"")
                        .removeSuffix("\"")
                        .trim()
                    note = cleaned.substring(pipeIdx + 1).trim()
                } else {
                    productName = cleaned
                        .removePrefix("\"")
                        .removeSuffix("\"")
                        .trim()
                    note = ""
                }

                // ── Strict validation — reject bad AI responses ──────────
                val lower = productName.lowercase()
                val isBadResponse = productName.isEmpty() ||
                        productName.length < 2 ||
                        productName.length > 80 ||
                        !productName.any { it.isLetter() } ||
                        lower == "null" ||
                        lower == "n/a" ||
                        lower == "unknown" ||
                        lower == "none" ||
                        lower == "product" ||
                        lower.contains("sorry") ||
                        lower.contains("cannot identify") ||
                        lower.contains("unable to") ||
                        lower.contains("i cannot") ||
                        lower.contains("i can't") ||
                        lower.contains("no text") ||
                        lower.contains("not visible") ||
                        lower.contains("not clear") ||
                        lower.contains("cannot read") ||
                        lower.contains("productname") ||
                        lower.contains("your answer") ||
                        lower.contains("instructions") ||
                        lower.startsWith("the product is") ||
                        lower.startsWith("based on") ||
                        lower.startsWith("looking at") ||
                        lower.startsWith("the image shows") ||
                        lower.contains("| note") ||
                        lower.contains("bio-oil skincare oil|") // edge case

                if (isBadResponse) {
                    android.util.Log.w("ProductCapture",
                        "Rejected AI response: '$productName'")
                    continue
                }

                android.util.Log.d("ProductCapture",
                    "AI verified: '$productName' note='$note'")
                return Pair(productName, note)

            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.w("ProductCapture",
                    "AI timeout: $model")
                continue
            } catch (e: Exception) {
                android.util.Log.e("ProductCapture",
                    "AI error: ${e.message}")
                continue
            }
        }

        // All AI models failed — use best OCR candidate
        val fallback = ocrCandidates.firstOrNull() ?: ""
        return Pair(fallback,
            if (fallback.isEmpty()) "" else "AI unavailable, using OCR result")
    }

    // ── OCR scoring ───────────────────────────────────────────────────────

    private fun extractOcrCandidates(ocrText: String): List<String> {
        if (ocrText.isBlank()) return emptyList()

        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.length >= 2 }

        data class ScoredLine(val text: String, val score: Int)
        val scored = mutableListOf<ScoredLine>()

        for ((idx, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (skipWords.any { lower.contains(it) }) continue

            val digitRatio =
                line.count { it.isDigit() }.toFloat() / line.length
            if (digitRatio > 0.5f) continue
            if (line.length < 2 || line.length > 50) continue

            val specialCount = line.count {
                !it.isLetterOrDigit() && it != ' ' &&
                        it != '-' && it != '&' && it != '.' &&
                        it != '\'' && it != '/'
            }
            if (specialCount > 3) continue

            var score = 0
            score += when (idx) {
                0 -> 40; 1 -> 32; 2 -> 22
                3 -> 14; 4 -> 8
                else -> maxOf(0, 5 - idx)
            }

            if (brandKeywords.any { lower.contains(it) }) score += 60

            val letterCount =
                line.count { it.isLetter() }.coerceAtLeast(1)
            val upperRatio =
                line.count { it.isUpperCase() }.toFloat() / letterCount
            when {
                upperRatio > 0.7f -> score += 30
                line.split(" ").filter { it.isNotEmpty() }
                    .all { it.first().isUpperCase() } -> score += 20
                else -> score += 5
            }

            score += when (line.length) {
                in 3..8 -> 20; in 9..20 -> 28
                in 21..35 -> 15; else -> 5
            }

            val wordCount =
                line.split(" ").filter { it.isNotEmpty() }.size
            score += when (wordCount) {
                1 -> 18; 2 -> 28; 3 -> 22
                4 -> 14; 5 -> 8; else -> 0
            }

            if (line.contains("\u00ae") || line.contains("\u2122"))
                score += 20

            if (score > 15) scored.add(ScoredLine(line, score))
        }

        return scored
            .sortedByDescending { it.score }
            .take(4)
            .map { it.text }
    }

    // ── Show result ───────────────────────────────────────────────────────

    private fun showResult(
        aiName: String,
        ocrCandidates: List<String>,
        aiNote: String
    ) {
        if (aiName.isEmpty()) {
            tvStatus.text =
                "\u26a0\ufe0f Could not identify \u2014 try again " +
                        "with better lighting"
            tvStatus.setTextColor(
                android.graphics.Color.parseColor("#FFB74D"))
            return
        }

        detectedProductName = aiName
        layoutResult.visibility = View.VISIBLE
        tvProductName.text = aiName
        tvStatus.text = "\u2705 Product identified!"
        tvStatus.setTextColor(
            android.graphics.Color.parseColor("#A5D6A7"))

        if (aiNote.isNotEmpty()) {
            tvAiNote.visibility = View.VISIBLE
            tvAiNote.text = "\ud83e\udd16 $aiNote"
        } else {
            tvAiNote.visibility = View.GONE
        }

        val tvPickLabel = layoutResult.getChildAt(3) as? TextView

        if (layoutResult.childCount > 5) layoutResult.removeViewAt(4)

        val alternatives = ocrCandidates
            .filter { it != aiName && it.isNotEmpty() }

        if (alternatives.isNotEmpty()) {
            tvPickLabel?.visibility = View.VISIBLE

            val chipScroll = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, 4, 0, 12)
            }

            val chipRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            chipScroll.addView(chipRow)

            alternatives.forEach { alt ->
                chipRow.addView(TextView(this).apply {
                    text = alt
                    textSize = 12f
                    setTextColor(
                        android.graphics.Color.parseColor("#1B5E20"))
                    background =
                        android.graphics.drawable
                            .GradientDrawable().apply {
                                setColor(android.graphics.Color
                                    .parseColor("#C8E6C9"))
                                cornerRadius = 32f
                            }
                    setPadding(20, 10, 20, 10)
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    p.setMargins(0, 0, 8, 0)
                    layoutParams = p
                    setOnClickListener {
                        detectedProductName = alt
                        tvProductName.text = alt
                        tvAiNote.visibility = View.VISIBLE
                        tvAiNote.text = "\ud83d\udcd6 Using your selection"
                        for (i in 0 until chipRow.childCount) {
                            val c = chipRow.getChildAt(i) as? TextView
                            val sel = c?.text == alt
                            c?.setTextColor(
                                if (sel) android.graphics.Color.WHITE
                                else android.graphics.Color
                                    .parseColor("#1B5E20"))
                            (c?.background as?
                                    android.graphics.drawable
                                    .GradientDrawable)
                                ?.setColor(
                                    if (sel) android.graphics.Color
                                        .parseColor("#2E7D32")
                                    else android.graphics.Color
                                        .parseColor("#C8E6C9"))
                        }
                    }
                })
            }

            layoutResult.addView(chipScroll, 4)
        } else {
            tvPickLabel?.visibility = View.GONE
        }
    }

    private fun resetButton() {
        btnCapture.isEnabled = true
        btnCapture.text = "\ud83d\udcf7  Capture & Identify"
    }

    private fun resetCapture() {
        detectedProductName = ""
        layoutResult.visibility = View.GONE
        tvStatus.text = ""
        tvAiNote.visibility = View.GONE
        if (layoutResult.childCount > 5) layoutResult.removeViewAt(4)
    }

    private fun confirmProduct() {
        val intent = Intent()
        intent.putExtra(RESULT_PRODUCT_NAME, detectedProductName)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory
                .decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            try { imageProxy.toBitmap() }
            catch (e2: Exception) { null }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val ratio = w.toFloat() / h
        val nw: Int; val nh: Int
        if (w > h) { nw = maxSize; nh = (maxSize / ratio).toInt() }
        else { nh = maxSize; nw = (maxSize * ratio).toInt() }
        return try {
            Bitmap.createScaledBitmap(
                bitmap, nw.coerceAtLeast(1),
                nh.coerceAtLeast(1), true)
        } catch (e: Exception) { bitmap }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults)
        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}