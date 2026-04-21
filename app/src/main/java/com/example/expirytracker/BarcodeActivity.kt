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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
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
class BarcodeActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var tvProductName: TextView
    private lateinit var tvBarcodeValue: TextView
    private lateinit var btnConfirm: Button
    private lateinit var btnRescan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutResult: LinearLayout
    private lateinit var imageCapture: ImageCapture

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var barcodeDetected = false
    private var detectedProductName = ""
    private var detectedBarcode = ""
    private var capturedBitmap: Bitmap? = null

    private val apiKey =
        "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"
    private val apiUrl =
        "https://openrouter.ai/api/v1/chat/completions"
    private val models = listOf(
        "google/gemma-3-4b-it:free",
        "nvidia/nemotron-nano-12b-v2-vl:free",
        "google/gemma-3-12b-it:free"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        const val RESULT_PRODUCT_NAME = "product_name"
        const val RESULT_BARCODE = "barcode_value"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private fun setupUI() {
        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            layoutParams = android.widget.FrameLayout
                .LayoutParams(
                    android.widget.FrameLayout.LayoutParams
                        .MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams
                        .MATCH_PARENT)
        }

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout
                .LayoutParams(
                    android.widget.FrameLayout.LayoutParams
                        .MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams
                        .MATCH_PARENT)
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(
                android.graphics.Color.parseColor("#CC000000"))
            setPadding(24, 56, 24, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        topBar.addView(TextView(this).apply {
            text = "← Back"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { finish() }
        })
        topBar.addView(TextView(this).apply {
            text = "  📊 Scan Product"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
        })

        // Center scan area
        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f)
        }

        val scanFrame = android.view.View(this).apply {
            val w = resources.displayMetrics.widthPixels - 100
            layoutParams = LinearLayout.LayoutParams(w, w / 2)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    cornerRadius = 16f
                    setStroke(4, android.graphics.Color
                        .parseColor("#4CAF50"))
                }
        }

        tvStatus = TextView(this).apply {
            text = "📷 Point at barcode — auto-detects"
            textSize = 14f
            setTextColor(android.graphics.Color
                .parseColor("#A5D6A7"))
            gravity = android.view.Gravity.CENTER
            setPadding(32, 20, 32, 8)
        }

        // Capture button for manual trigger
        val btnCapture = Button(this).apply {
            text = "📷 Capture & Identify"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#1565C0"))
                    cornerRadius = 48f
                }
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = android.view.Gravity.CENTER_HORIZONTAL
            p.setMargins(0, 16, 0, 0)
            layoutParams = p
            setOnClickListener { captureForAi() }
        }

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.gravity = android.view.Gravity.CENTER_HORIZONTAL
            p.setMargins(0, 16, 0, 0)
            layoutParams = p
        }

        centerLayout.addView(scanFrame)
        centerLayout.addView(tvStatus)
        centerLayout.addView(btnCapture)
        centerLayout.addView(progressBar)

        // Result panel
        layoutResult = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color
                .parseColor("#F8FFF8"))
            setPadding(32, 24, 32, 40)
            visibility = View.GONE
        }

        tvBarcodeValue = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color
                .parseColor("#888888"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        tvProductName = TextView(this).apply {
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color
                .parseColor("#1B5E20"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        btnRescan = Button(this).apply {
            text = "🔄 Rescan"
            textSize = 14f
            setTextColor(android.graphics.Color
                .parseColor("#2E7D32"))
            setBackgroundColor(
                android.graphics.Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { resetScan() }
        }

        btnConfirm = Button(this).apply {
            text = "✅ Use This Name"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable
                .GradientDrawable().apply {
                    setColor(android.graphics.Color
                        .parseColor("#2E7D32"))
                    cornerRadius = 32f
                }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            setOnClickListener { confirmProduct() }
        }

        btnRow.addView(btnRescan)
        btnRow.addView(btnConfirm)
        layoutResult.addView(tvBarcodeValue)
        layoutResult.addView(tvProductName)
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
                    it.setSurfaceProvider(
                        previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) {
                        imageProxy ->
                    if (!barcodeDetected)
                        detectBarcode(imageProxy)
                    else
                        imageProxy.close()
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture, imageAnalysis)

            } catch (e: Exception) {
                android.util.Log.e("Barcode",
                    "Camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Step 1: Auto-detect barcode value ─────────────────────────

    private fun detectBarcode(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close(); return }
        val image = InputImage.fromMediaImage(
            mediaImage, imageProxy.imageInfo.rotationDegrees)
        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { barcodes ->
                for (bc in barcodes) {
                    val value = bc.rawValue ?: continue
                    if (value.isNotEmpty() && !barcodeDetected) {
                        barcodeDetected = true
                        detectedBarcode = value
                        imageProxy.close()
                        runOnUiThread {
                            tvStatus.text =
                                "✅ Barcode: $value\n" +
                                        "📷 Capturing image..."
                            // Auto-capture for AI
                            captureForAi()
                        }
                        return@addOnSuccessListener
                    }
                }
                imageProxy.close()
            }
            .addOnFailureListener { imageProxy.close() }
    }

    // ── Step 2: Capture photo and send to AI ──────────────────────

    private fun captureForAi() {
        if (!::imageCapture.isInitialized) return
        tvStatus.text = "📷 Capturing..."
        progressBar.visibility = View.VISIBLE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(
                    imageProxy: ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(
                            imageProxy)
                        imageProxy.close()
                        if (bitmap != null) {
                            capturedBitmap = bitmap
                            tvStatus.text =
                                "🤖 AI identifying product..."
                            lifecycleScope.launch {
                                val name = withContext(
                                    Dispatchers.IO) {
                                    identifyWithAi(
                                        bitmap,
                                        detectedBarcode)
                                }
                                progressBar.visibility =
                                    View.GONE
                                if (name != null) {
                                    detectedProductName = name
                                    showResult(
                                        detectedBarcode, name)
                                } else {
                                    showNotFound(
                                        detectedBarcode)
                                }
                            }
                        } else {
                            progressBar.visibility = View.GONE
                            showNotFound(detectedBarcode)
                        }
                    } catch (e: Exception) {
                        imageProxy.close()
                        progressBar.visibility = View.GONE
                        showNotFound(detectedBarcode)
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "❌ Capture failed"
                }
            })
    }

    // ── Step 3: AI identifies product from image ──────────────────

    private fun identifyWithAi(
        bitmap: Bitmap,
        barcode: String
    ): String? {
        // Resize to 400px for fast upload
        val resized = resizeBitmap(bitmap, 400)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 60, out)
        val b64 = Base64.encodeToString(
            out.toByteArray(), Base64.NO_WRAP)

        val prompt = """
You are a product label reader. Look at this product package image.

Your ONLY job: Find and return the PRODUCT NAME printed on this package.

STRICT RULES:
1. Return ONLY the brand name + product name
2. Look for the LARGEST text on the package - that is usually the product name
3. For Indian products, read both Hindi and English text
4. Common Indian brands: Amul, Parle, Britannia, Nestle, 
   ITC, Dabur, Patanjali, MDH, Everest, Tata, Godrej,
   HUL, Marico, Emami, Colgate, Haldirams, Bikaji
5. IGNORE: weight, quantity, expiry date, batch number,
   ingredients, price, barcode number, address, 
   "net wt", "MRP", "FSSAI", license numbers
6. IGNORE: "Best Before", "Use By", "Mfg", "Exp"
7. Return MAXIMUM 5 words
8. If you see "Amul Butter 500g" → return "Amul Butter"
9. If you see "Maggi 2-Minute Noodles Masala" → 
   return "Maggi Masala Noodles"
10. If you truly cannot read ANY product name → 
    return exactly: UNKNOWN

${if (barcode.isNotEmpty())
            "Barcode detected: $barcode" else ""}

REPLY WITH PRODUCT NAME ONLY. NO EXPLANATION. NO PUNCTUATION.
""".trimIndent()

        for (model in models) {
            try {
                val reqJson = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url",
                                        JSONObject().apply {
                                            put("url",
                                                "data:image/jpeg" +
                                                        ";base64,$b64")
                                        })
                                })
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("max_tokens", 50)
                    put("temperature", 0.1)
                }

                val body = reqJson.toString()
                    .toRequestBody(
                        "application/json".toMediaType())
                val request = Request.Builder()
                    .url(apiUrl).post(body)
                    .addHeader("Authorization",
                        "Bearer $apiKey")
                    .addHeader("HTTP-Referer",
                        "https://savesmart.app")
                    .addHeader("X-Title", "SaveSmart")
                    .build()

                val resp = client.newCall(request).execute()
                val code = resp.code
                val respBody = resp.body?.string() ?: ""
                resp.close()

                android.util.Log.d("Barcode",
                    "AI $model: $code ${respBody.take(100)}")

                if (code == 200) {
                    val raw = JSONObject(respBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()

                    // Clean up AI response
                    val cleaned = raw
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                        .lines()
                        .firstOrNull { it.isNotEmpty() }
                        ?.trim() ?: ""

                    android.util.Log.d("Barcode",
                        "AI result: $cleaned")

                    // Validate — reject if AI is confused
                    // Validate — reject bad AI responses
                    if (cleaned.isNotEmpty() &&
                        cleaned.length > 2 &&
                        cleaned.length < 60 &&
                        cleaned.uppercase() != "UNKNOWN" &&
                        !cleaned.lowercase().contains("sorry") &&
                        !cleaned.lowercase().contains("cannot") &&
                        !cleaned.lowercase().contains("unable") &&
                        !cleaned.lowercase().contains("i don") &&
                        !cleaned.lowercase().contains("product name") &&
                        !cleaned.lowercase().contains("the product") &&
                        !cleaned.lowercase().contains("image shows") &&
                        !cleaned.lowercase().contains("package") &&
                        !cleaned.lowercase().contains("based on") &&
                        !cleaned.lowercase().contains("appears to") &&
                        !cleaned.lowercase().contains("label") &&
                        !cleaned.any { it.isDigit() } // no numbers in name
                    ) {
                        return cleaned
                    }
                } else if (code == 429) {
                    // Quota exceeded, try next model
                    continue
                }

            } catch (e: Exception) {
                android.util.Log.e("Barcode",
                    "AI error: ${e.message}")
                continue
            }
        }
        return null
    }

    private fun imageProxyToBitmap(
        imageProxy: ImageProxy): Bitmap? {
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

    private fun resizeBitmap(
        bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val ratio = w.toFloat() / h
        val nw: Int; val nh: Int
        if (w > h) { nw = maxSize; nh = (maxSize/ratio).toInt() }
        else { nh = maxSize; nw = (maxSize*ratio).toInt() }
        return try {
            Bitmap.createScaledBitmap(
                bitmap, nw.coerceAtLeast(1),
                nh.coerceAtLeast(1), true)
        } catch (e: Exception) { bitmap }
    }

    private fun showResult(barcode: String, name: String) {
        runOnUiThread {
            layoutResult.visibility = View.VISIBLE
            tvBarcodeValue.text =
                if (barcode.isNotEmpty())
                    "Barcode: $barcode" else ""
            tvProductName.text = "📦 $name"
            tvStatus.text = "✅ Product identified!"
            tvStatus.setTextColor(
                android.graphics.Color.parseColor("#A5D6A7"))
        }
    }

    private fun showNotFound(barcode: String) {
        runOnUiThread {
            layoutResult.visibility = View.VISIBLE
            detectedProductName = ""
            tvBarcodeValue.text =
                if (barcode.isNotEmpty())
                    "Barcode: $barcode" else ""
            tvProductName.text =
                "⚠️ Could not identify product\n" +
                        "Please enter name manually"
            tvProductName.textSize = 15f
            tvStatus.text = "Identification failed"
            tvStatus.setTextColor(
                android.graphics.Color.parseColor("#FFB74D"))
            btnConfirm.text = "✅ Enter Manually"
        }
    }

    private fun confirmProduct() {
        val intent = Intent()
        intent.putExtra(RESULT_PRODUCT_NAME,
            detectedProductName)
        intent.putExtra(RESULT_BARCODE, detectedBarcode)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun resetScan() {
        barcodeDetected = false
        detectedProductName = ""
        detectedBarcode = ""
        capturedBitmap = null
        layoutResult.visibility = View.GONE
        progressBar.visibility = View.GONE
        tvStatus.text = "📷 Point at barcode — auto-detects"
        tvStatus.setTextColor(
            android.graphics.Color.parseColor("#A5D6A7"))
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
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}