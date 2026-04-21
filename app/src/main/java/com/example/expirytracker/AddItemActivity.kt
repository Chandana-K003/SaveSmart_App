package com.example.expirytracker

import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.example.expirytracker.data.Product
import com.example.expirytracker.data.ProductRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import android.content.Intent
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog

@androidx.camera.core.ExperimentalGetImage
class AddItemActivity : AppCompatActivity() {

    private lateinit var repository: ProductRepository
    private lateinit var cameraPreview: PreviewView
    private lateinit var tvOcrResult: TextView
    private lateinit var tvDateInfo: TextView
    private lateinit var etProductName: TextInputEditText
    private lateinit var etExpiryDate: TextInputEditText
    private lateinit var etCategory: TextInputEditText
    private lateinit var btnCapture: Button
    private lateinit var btnSave: Button

    private var imageCapture: ImageCapture? = null
    private var selectedExpiryDate: Long = 0
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS)
    private val displayFormat = SimpleDateFormat(
        "dd/MM/yyyy", Locale.getDefault())

    // ─── DATA CLASSES ─────────────────────────────────────────────────────────

    data class SpatialDate(
        val timestamp: Long,
        val text: String,
        val centerX: Float,
        val centerY: Float,
        val label: String,
        val isExpiry: Boolean,
        val isLabeled: Boolean
    )

    data class BestBeforeDuration(
        val value: Int,
        val unit: String
    )

    // ─── BRAND → CATEGORY MAP ─────────────────────────────────────────────────

    private val brandCategoryMap = linkedMapOf(
        "mother dairy" to "Dairy", "kwality dairy" to "Dairy",
        "heritage milk" to "Dairy", "amul" to "Dairy",
        "nestle milk" to "Dairy", "milma" to "Dairy",
        "nandini" to "Dairy", "parag" to "Dairy",
        "gowardhan" to "Dairy", "hatsun" to "Dairy",
        "arokya" to "Dairy", "aavin" to "Dairy", "verka" to "Dairy",
        "hide & seek" to "Bakery & Biscuits",
        "good day" to "Bakery & Biscuits",
        "marie gold" to "Bakery & Biscuits",
        "milk bikis" to "Bakery & Biscuits",
        "britannia" to "Bakery & Biscuits",
        "sunfeast" to "Bakery & Biscuits",
        "mcvities" to "Bakery & Biscuits",
        "oreo" to "Bakery & Biscuits",
        "bourbon" to "Bakery & Biscuits",
        "50-50" to "Bakery & Biscuits",
        "krackjack" to "Bakery & Biscuits",
        "monaco" to "Bakery & Biscuits",
        "nutrichoice" to "Bakery & Biscuits",
        "digestive" to "Bakery & Biscuits",
        "little hearts" to "Bakery & Biscuits",
        "parle" to "Bakery & Biscuits",
        "tiger" to "Bakery & Biscuits",
        "uncle chipps" to "Snacks", "yellow diamond" to "Snacks",
        "too yumm" to "Snacks", "act ii" to "Snacks",
        "lay's" to "Snacks", "lays" to "Snacks",
        "kurkure" to "Snacks", "bingo" to "Snacks",
        "pringles" to "Snacks", "haldiram" to "Snacks",
        "bikaji" to "Snacks", "bikanervala" to "Snacks",
        "balaji" to "Snacks", "peppy" to "Snacks",
        "cornitos" to "Snacks",
        "dairy milk" to "Chocolates & Sweets",
        "kit kat" to "Chocolates & Sweets",
        "kitkat" to "Chocolates & Sweets",
        "5 star" to "Chocolates & Sweets",
        "milkybar" to "Chocolates & Sweets",
        "cadbury" to "Chocolates & Sweets",
        "munch" to "Chocolates & Sweets",
        "gems" to "Chocolates & Sweets",
        "eclairs" to "Chocolates & Sweets",
        "ferrero" to "Chocolates & Sweets",
        "toblerone" to "Chocolates & Sweets",
        "lindt" to "Chocolates & Sweets",
        "snickers" to "Chocolates & Sweets",
        "twix" to "Chocolates & Sweets",
        "bounty" to "Chocolates & Sweets",
        "temptations" to "Chocolates & Sweets",
        "silk" to "Chocolates & Sweets",
        "coca cola" to "Beverages", "thums up" to "Beverages",
        "red bull" to "Beverages", "real juice" to "Beverages",
        "minute maid" to "Beverages", "tata tea" to "Beverages",
        "taj mahal" to "Beverages", "red label" to "Beverages",
        "coke" to "Beverages", "pepsi" to "Beverages",
        "sprite" to "Beverages", "fanta" to "Beverages",
        "limca" to "Beverages", "maaza" to "Beverages",
        "frooti" to "Beverages", "slice" to "Beverages",
        "tropicana" to "Beverages", "monster" to "Beverages",
        "sting" to "Beverages", "horlicks" to "Beverages",
        "bournvita" to "Beverages", "complan" to "Beverages",
        "boost" to "Beverages", "nescafe" to "Beverages",
        "bru" to "Beverages", "lipton" to "Beverages",
        "milo" to "Beverages", "ovaltine" to "Beverages",
        "top ramen" to "Noodles & Pasta",
        "wai wai" to "Noodles & Pasta",
        "knorr soupy" to "Noodles & Pasta",
        "ching's" to "Noodles & Pasta",
        "maggi" to "Noodles & Pasta",
        "yippee" to "Noodles & Pasta",
        "nature fresh" to "Cooking Oil",
        "patanjali oil" to "Cooking Oil",
        "fortune" to "Cooking Oil", "saffola" to "Cooking Oil",
        "sundrop" to "Cooking Oil", "dhara" to "Cooking Oil",
        "gemini" to "Cooking Oil", "postman" to "Cooking Oil",
        "mtr masala" to "Spices & Condiments",
        "everest" to "Spices & Condiments",
        "mdh" to "Spices & Condiments",
        "catch" to "Spices & Condiments",
        "badshah" to "Spices & Condiments",
        "kissan" to "Spices & Condiments",
        "maggi sauce" to "Spices & Condiments",
        "heinz" to "Spices & Condiments",
        "del monte" to "Spices & Condiments",
        "india gate" to "Grains & Pulses",
        "tata sampann" to "Grains & Pulses",
        "golden temple" to "Grains & Pulses",
        "aashirvaad" to "Grains & Pulses",
        "pillsbury" to "Grains & Pulses",
        "annapurna" to "Grains & Pulses",
        "rajdhani" to "Grains & Pulses",
        "kohinoor" to "Grains & Pulses",
        "daawat" to "Grains & Pulses",
        "clinic plus" to "Personal Care",
        "fair lovely" to "Personal Care",
        "glow lovely" to "Personal Care",
        "oral-b" to "Personal Care",
        "head & shoulders" to "Personal Care",
        "dove" to "Personal Care", "lux" to "Personal Care",
        "lifebuoy" to "Personal Care", "dettol" to "Personal Care",
        "savlon" to "Personal Care", "colgate" to "Personal Care",
        "pepsodent" to "Personal Care",
        "closeup" to "Personal Care",
        "sensodyne" to "Personal Care",
        "pantene" to "Personal Care", "sunsilk" to "Personal Care",
        "garnier" to "Personal Care", "loreal" to "Personal Care",
        "himalaya" to "Personal Care", "dabur" to "Personal Care",
        "patanjali" to "Personal Care", "nivea" to "Personal Care",
        "vaseline" to "Personal Care", "ponds" to "Personal Care",
        "lakme" to "Personal Care",
        "pudin hara" to "Medicine", "dolo" to "Medicine",
        "crocin" to "Medicine", "disprin" to "Medicine",
        "combiflam" to "Medicine", "ibugesic" to "Medicine",
        "gelusil" to "Medicine", "digene" to "Medicine",
        "vicks" to "Medicine", "zandu" to "Medicine",
        "hamdard" to "Medicine", "moov" to "Medicine",
        "eno" to "Medicine",
        "nan pro" to "Baby Products",
        "cerelac" to "Baby Products",
        "nestum" to "Baby Products",
        "farex" to "Baby Products",
        "lactogen" to "Baby Products",
        "similac" to "Baby Products",
        "enfamil" to "Baby Products",
        "royal canin" to "Pet Food",
        "pedigree" to "Pet Food", "whiskas" to "Pet Food",
        "drools" to "Pet Food", "purina" to "Pet Food",
        "kwality walls" to "Frozen Foods",
        "vadilal" to "Frozen Foods",
        "havmor" to "Frozen Foods",
        "mother dairy ice cream" to "Frozen Foods"
    )

    // ─── KEYWORD LISTS ────────────────────────────────────────────────────────

    private val expiryKeywords = listOf(
        "exp", "expiry", "expiry date", "expires", "expiration",
        "expiration date", "exp date", "exp.", "exp:",
        "best before", "best by", "bb", "bbd", "bbf", "bb:",
        "use by", "use before", "useby", "use-by",
        "sell by", "sell before", "sellby",
        "best before end", "bbe",
        "consume by", "consume before",
        "enjoy by", "discard after", "discard by"
    )

    private val mfgKeywords = listOf(
        "mfg", "mfg.", "mfg:", "mfd", "mfd.", "mfd:",
        "manufactured", "manufactured on", "manufactured date",
        "manufacturing date", "date of manufacture",
        "production date", "prod date", "prod.",
        "packed on", "packing date", "packed date",
        "packaged on", "bottled on", "dom", "made on", "made date"
    )

    private val bestBeforeDurationKeywords = listOf(
        "best before", "best by", "use within", "consume within",
        "use before", "good for", "shelf life", "valid for",
        "bb", "bbd", "bbf"
    )

    private val monthMap = mapOf(
        "january" to 1, "february" to 2, "march" to 3,
        "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9,
        "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10,
        "nov" to 11, "dec" to 12,
        "jan." to 1, "feb." to 2, "mar." to 3, "apr." to 4,
        "jun." to 6, "jul." to 7, "aug." to 8,
        "sep." to 9, "oct." to 10, "nov." to 11, "dec." to 12,
        "j4n" to 1, "f3b" to 2, "m4r" to 3, "4pr" to 4,
        "m4y" to 5, "jun3" to 6, "jul1" to 7, "4ug" to 8,
        "s3p" to 9, "0ct" to 10, "n0v" to 11, "d3c" to 12,
        "jän" to 1, "mär" to 3, "okt" to 10, "dez" to 12
    )

    // ─── LIFECYCLE ────────────────────────────────────────────────────────────
    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val name = result.data?.getStringExtra(
                ProductCaptureActivity.RESULT_PRODUCT_NAME) ?: ""
            if (name.isNotEmpty()) {
                etProductName.setText(name)
                Toast.makeText(this,
                    "✅ Product name filled! Now scan expiry date.",
                    Toast.LENGTH_SHORT).show()
            }
        }
        // Always restart camera and reset UI
        // so expiry scanning works after returning
        tvOcrResult.text = ""
        tvOcrResult.visibility = View.GONE
        tvDateInfo.text = "📷 Point camera at expiry date and tap Scan"
        tvDateInfo.setTextColor(
            android.graphics.Color.parseColor("#1565C0"))
        tvDateInfo.visibility = View.VISIBLE
        btnCapture.isEnabled = true
        btnCapture.text = "📷 Scan"
        startCamera()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_item)

        repository = ProductRepository(this)
        cameraPreview = findViewById(R.id.cameraPreview)
        tvOcrResult = findViewById(R.id.tvOcrResult)
        tvDateInfo = findViewById(R.id.tvDateInfo)
        etProductName = findViewById(R.id.etProductName)
        findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btnScanBarcode).setOnClickListener {
            barcodeLauncher.launch(
                Intent(this, ProductCaptureActivity::class.java))
        }
        etExpiryDate = findViewById(R.id.etExpiryDate)
        etCategory = findViewById(R.id.etCategory)

        val categories = arrayOf(
            "🍎 Food", "🥦 Vegetables", "🍇 Fruits",
            "🥛 Dairy & Eggs", "🥩 Meat & Seafood", "🍿 Snacks",
            "🥤 Beverages", "💊 Medicine", "💄 Cosmetics & Personal Care",
            "🍱 Leftovers", "🌾 Grains & Pulses", "🧂 Condiments & Spices",
            "🧹 Household & Maintenance", "🧊 Frozen Foods",
            "🍳 Cooking Oil", "🍞 Bakery & Biscuits", "🍜 Noodles & Pasta",
            "👶 Baby Products", "🐾 Pet Supplies", "📦 General"
        )

        etCategory.setOnClickListener {
            android.app.AlertDialog.Builder(this@AddItemActivity)
                .setTitle("Select Category")
                .setItems(categories) { dialog, which ->
                    etCategory.setText(categories[which])
                    dialog.dismiss()
                }
                .create()
                .show()
        }
        btnCapture = findViewById(R.id.btnCapture)
        btnSave = findViewById(R.id.btnSave)

        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA), 100)
        }

        etExpiryDate.setOnClickListener { showDatePicker() }
        btnCapture.setOnClickListener { captureAndRecognize() }
        btnSave.setOnClickListener { saveProduct() }
    }


    // ─── DATE PICKER ──────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (selectedExpiryDate > 0) cal.timeInMillis = selectedExpiryDate
        DatePickerDialog(this, { _, year, month, day ->
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            selectedExpiryDate = cal.timeInMillis
            etExpiryDate.setText(displayFormat.format(cal.time))
            tvDateInfo.text = "✅ Date selected manually"
            tvDateInfo.setTextColor(
                android.graphics.Color.parseColor("#2E7D32"))
            tvDateInfo.visibility = View.VISIBLE
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ─── CAMERA ───────────────────────────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture!!)
            } catch (e: Exception) {
                Toast.makeText(this,
                    "Camera failed to start: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ─── STEP 1: CAPTURE & DIRECT OCR ────────────────────────────────────────

    private fun captureAndRecognize() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, "Camera not ready",
                Toast.LENGTH_SHORT).show()
            return
        }

        // Clear previous results
        tvOcrResult.text = ""
        tvOcrResult.visibility = View.GONE

        btnCapture.isEnabled = false
        btnCapture.text = "Scanning..."
        tvDateInfo.text = "🔍 Step 1: Reading text..."
        tvDateInfo.setTextColor(
            android.graphics.Color.parseColor("#1565C0"))
        tvDateInfo.visibility = View.VISIBLE

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val originalBitmap = imageProxyToBitmap(imageProxy)
                        imageProxy.close()

                        if (originalBitmap == null) {
                            runOnUiThread {
                                resetScanButton()
                                Toast.makeText(this@AddItemActivity,
                                    "Could not process image. Try again.",
                                    Toast.LENGTH_SHORT).show()
                            }
                            return
                        }

                        val originalImage = InputImage.fromBitmap(
                            originalBitmap, 0)
                        textRecognizer.process(originalImage)
                            .addOnSuccessListener { visionText ->
                                val ocrText = visionText.text
                                if (ocrText.isNotEmpty()) {
                                    tvOcrResult.text =
                                        "Scanned:\n$ocrText"
                                    tvOcrResult.visibility = View.VISIBLE
                                }

                                val cleaned = preprocessOcrText(ocrText)
                                val lowerOcr = ocrText.lowercase()
                                val hasMfgKw = mfgKeywords.any {
                                    lowerOcr.contains(it) }
                                val hasExpKw = expiryKeywords.any {
                                    lowerOcr.contains(it) }

                                // Count meaningful words (length >= 3)
                                val meaningfulWordCount = ocrText
                                    .trim()
                                    .split(Regex("\\s+"))
                                    .count { it.length >= 3 }
                                val ocrTooWeak = meaningfulWordCount < 5

                                val dateFound = tryExtractDateFromText(
                                    cleaned, source = "Direct OCR")

                                when {
                                    dateFound -> {
                                        // ✅ Strong expiry found directly
                                        resetScanButton()
                                        extractNameAndCategory(cleaned)
                                    }
                                    ocrText.isNotEmpty() && (
                                            hasDateLikePatterns(ocrText) ||
                                                    hasMfgKw) -> {
                                        // Date/MFG patterns found but
                                        // parser missed → AI directly
                                        tvDateInfo.text =
                                            "🤖 AI extracting date..."
                                        tvDateInfo.setTextColor(
                                            android.graphics.Color
                                                .parseColor("#1565C0"))
                                        tryWithAi(originalBitmap, ocrText)
                                    }
                                    ocrTooWeak || ocrText.isEmpty() -> {
                                        // OCR got almost nothing →
                                        // enhance then AI
                                        tvDateInfo.text =
                                            "🔍 Enhancing image..."
                                        tvDateInfo.setTextColor(
                                            android.graphics.Color
                                                .parseColor("#E65100"))
                                        tryWithImageEnhancement(
                                            originalBitmap, ocrText)
                                    }
                                    else -> {
                                        // Got text but no date patterns
                                        // → enhance
                                        tvDateInfo.text =
                                            "🔍 Step 2: Enhancing image..."
                                        tvDateInfo.setTextColor(
                                            android.graphics.Color
                                                .parseColor("#E65100"))
                                        tryWithImageEnhancement(
                                            originalBitmap, ocrText)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                tvDateInfo.text =
                                    "🔍 Step 2: Enhancing image..."
                                tvDateInfo.setTextColor(
                                    android.graphics.Color
                                        .parseColor("#E65100"))
                                tryWithImageEnhancement(
                                    originalBitmap, "")
                            }

                    } catch (e: Exception) {
                        try { imageProxy.close() }
                        catch (ignored: Exception) { }
                        runOnUiThread {
                            resetScanButton()
                            Toast.makeText(this@AddItemActivity,
                                "Capture error. Please try again.",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    runOnUiThread {
                        resetScanButton()
                        tvDateInfo.visibility = View.GONE
                        Toast.makeText(this@AddItemActivity,
                            "Could not capture. Try again.",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    // ─── STEP 2: IMAGE ENHANCEMENT + OCR ─────────────────────────────────────

    private fun tryWithImageEnhancement(
        originalBitmap: android.graphics.Bitmap,
        originalOcrText: String
    ) {
        Thread {
            try {
                val enhancedImages = ImageEnhancer.enhanceForOcr(
                    originalBitmap, this@AddItemActivity)
                    .drop(1)
                    .take(3)

                if (enhancedImages.isEmpty()) {
                    runOnUiThread {
                        tryWithAi(originalBitmap, originalOcrText)
                    }
                    return@Thread
                }

                var bestEnhancedText = originalOcrText
                var processedCount = 0
                val total = enhancedImages.size

                for (bitmap in enhancedImages) {
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                processedCount++
                                if (visionText.text.length >
                                    bestEnhancedText.length) {
                                    bestEnhancedText = visionText.text
                                }
                                if (processedCount == total) {
                                    runOnUiThread {
                                        onEnhancedOcrComplete(
                                            bestEnhancedText,
                                            originalBitmap,
                                            originalOcrText)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                processedCount++
                                if (processedCount == total) {
                                    runOnUiThread {
                                        onEnhancedOcrComplete(
                                            bestEnhancedText,
                                            originalBitmap,
                                            originalOcrText)
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        processedCount++
                        if (processedCount == total) {
                            runOnUiThread {
                                onEnhancedOcrComplete(
                                    bestEnhancedText,
                                    originalBitmap,
                                    originalOcrText)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tryWithAi(originalBitmap, originalOcrText)
                }
            }
        }.start()
    }

    // ─── AFTER ENHANCED OCR DONE ──────────────────────────────────────────────

    private fun onEnhancedOcrComplete(
        bestText: String,
        originalBitmap: android.graphics.Bitmap,
        originalOcrText: String
    ) {
        if (bestText.isNotEmpty() && bestText != originalOcrText) {
            tvOcrResult.text = "Scanned (enhanced):\n$bestText"
            tvOcrResult.visibility = View.VISIBLE
        }

        val cleaned = preprocessOcrText(bestText)
        val lowerText = bestText.lowercase()

        val hasMfgOnly = mfgKeywords.any { lowerText.contains(it) } &&
                !expiryKeywords.any { lowerText.contains(it) }

        // Count meaningful words in enhanced result
        val enhancedWordCount = bestText.trim()
            .split(Regex("\\s+"))
            .count { it.length >= 3 }
        val enhancedTooWeak = enhancedWordCount < 5

        val dateFound = tryExtractDateFromText(
            cleaned, source = "Enhanced OCR")

        if (dateFound && !hasMfgOnly) {
            // ✅ Strong expiry date found after enhancement
            resetScanButton()
            extractNameAndCategory(cleaned)
        } else {
            // No strong expiry date found → AI
            tvDateInfo.text = if (enhancedTooWeak)
                "🤖 AI analyzing (image unclear)..."
            else
                "🤖 Step 3: AI analyzing..."
            tvDateInfo.setTextColor(
                android.graphics.Color.parseColor("#1565C0"))
            tryWithAi(originalBitmap, bestText)
        }
    }

    // ─── STEP 3: AI EXTRACTION ────────────────────────────────────────────────

    private fun tryWithAi(
        originalBitmap: android.graphics.Bitmap,
        ocrText: String
    ) {
        if (!AiDateExtractor.isAiAvailable() || !isInternetAvailable()) {
            showManualDatePrompt(showAiMessage = false)
            resetScanButton()
            val cleaned = preprocessOcrText(ocrText)
            extractNameAndCategory(cleaned)
            return
        }

        tvDateInfo.text = "🤖 AI analyzing image..."
        tvDateInfo.setTextColor(
            android.graphics.Color.parseColor("#1565C0"))
        tvDateInfo.visibility = View.VISIBLE

        lifecycleScope.launch {
            val aiResult = AiDateExtractor.extractDateWithAi(
                originalBitmap, ocrText)
            handleAiResult(aiResult, ocrText)
            resetScanButton()
        }
    }

    // ─── HANDLE AI RESULT ─────────────────────────────────────────────────────

    private fun handleAiResult(aiResult: AiDateResult, ocrText: String) {
        when (aiResult.reasoning) {
            "NO_API_KEY" -> showManualDatePrompt(showAiMessage = false)
            "NO_INTERNET" -> {
                tvDateInfo.text =
                    "⚠️ No internet — please select date manually"
                tvDateInfo.setTextColor(
                    android.graphics.Color.parseColor("#E65100"))
                tvDateInfo.visibility = View.VISIBLE
            }
            "QUOTA_EXCEEDED", "INVALID_API_KEY" -> {
                showManualDatePrompt(showAiMessage = false)
            }
            "TIMEOUT" -> {
                tvDateInfo.text =
                    "⚠️ Scan timed out — please select date manually"
                tvDateInfo.setTextColor(
                    android.graphics.Color.parseColor("#E65100"))
                tvDateInfo.visibility = View.VISIBLE
            }
            else -> {
                if (aiResult.success && aiResult.confidence >= 60) {
                    val dateTs = tryAllDatePatterns(aiResult.extractedDate)
                    if (dateTs != null) {
                        selectedExpiryDate = dateTs
                        etExpiryDate.setText(
                            displayFormat.format(selectedExpiryDate))
                        tvDateInfo.text =
                            "🤖 AI: ${aiResult.extractedDate} " +
                                    "(${aiResult.confidence}% confident)"
                        tvDateInfo.setTextColor(
                            android.graphics.Color.parseColor("#1565C0"))
                        tvDateInfo.visibility = View.VISIBLE
                        tvOcrResult.append(
                            "\n\n🤖 ${aiResult.reasoning}")
                    } else {
                        showManualDatePrompt(showAiMessage = false)
                    }
                } else {
                    showManualDatePrompt(showAiMessage = false)
                }
            }
        }

        val cleaned = preprocessOcrText(ocrText)
        extractNameAndCategory(cleaned)
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private fun resetScanButton() {
        btnCapture.isEnabled = true
        btnCapture.text = "📷 Scan"
    }

    private fun extractNameAndCategory(cleaned: String) {
        val name = extractProductName(cleaned)
        if (name.isNotEmpty() && etProductName.text.isNullOrEmpty()) {
            etProductName.setText(name)
        }
        if (etCategory.text.isNullOrEmpty()) {
            etCategory.setText(extractCategory(
                etProductName.text.toString(), cleaned))
        }
    }

    private fun showManualDatePrompt(showAiMessage: Boolean = true) {
        val message = if (showAiMessage)
            "🤖 AI could not read date — please select manually"
        else
            "⚠️ Date not detected — tap date field to select manually"
        tvDateInfo.text = message
        tvDateInfo.setTextColor(
            android.graphics.Color.parseColor("#C62828"))
        tvDateInfo.visibility = View.VISIBLE
        Toast.makeText(this,
            "Could not read date — please select manually",
            Toast.LENGTH_LONG).show()
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getSystemService(
                android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val network = cm.activeNetworkInfo
            network != null && network.isConnected
        } catch (e: Exception) { false }
    }

    // ─── HAS DATE-LIKE PATTERNS ───────────────────────────────────────────────

    private fun hasDateLikePatterns(text: String): Boolean {
        val patterns = listOf(
            Regex("\\d{1,2}/\\d{2,4}"),
            Regex("\\d{1,2}-\\d{1,2}-\\d{2,4}"),
            Regex("\\d{2}\\.\\d{2}"),
            Regex("20\\d{2}"),
            Regex("(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)",
                RegexOption.IGNORE_CASE),
            Regex("(exp|best before|use by|bb|mfg|mfd)",
                RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(text) }
    }

    // ─── SAFE BITMAP CONVERSION ───────────────────────────────────────────────

    private fun imageProxyToBitmap(
        imageProxy: ImageProxy): android.graphics.Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size)
        } catch (e1: Exception) {
            try { imageProxy.toBitmap() }
            catch (e2: Exception) { null }
        }
    }

    // ─── TRY EXTRACT DATE FROM TEXT ───────────────────────────────────────────

    private fun tryExtractDateFromText(
        cleaned: String,
        source: String = ""
    ): Boolean {
        val calculatedExpiry = tryCalculateExpiryFromDuration(cleaned)
        if (calculatedExpiry != null) {
            selectedExpiryDate = calculatedExpiry.first
            etExpiryDate.setText(displayFormat.format(selectedExpiryDate))
            tvDateInfo.text = "✅ ${calculatedExpiry.second}"
            tvDateInfo.setTextColor(
                android.graphics.Color.parseColor("#2E7D32"))
            tvDateInfo.visibility = View.VISIBLE
            return true
        }

        val dateResult = extractExpiryDateSmart(cleaned)
        if (dateResult != null) {
            // Reject weak/MFG-only results — let AI handle them
            val isWeakResult =
                dateResult.second.contains("MFG only") ||
                        dateResult.second.contains("⚠️") ||
                        dateResult.second.contains("Auto-detected")

            val lowerCleaned = cleaned.lowercase()
            val hasExpiryKw = expiryKeywords.any {
                lowerCleaned.contains(it) }
            val hasMfgKw = mfgKeywords.any {
                lowerCleaned.contains(it) }

            // MFG keyword present but no expiry keyword → unreliable
            if (isWeakResult || (hasMfgKw && !hasExpiryKw)) {
                return false
            }

            selectedExpiryDate = dateResult.first
            etExpiryDate.setText(displayFormat.format(selectedExpiryDate))
            val label = if (source.isNotEmpty())
                "✅ [$source] ${dateResult.second}"
            else "✅ ${dateResult.second}"
            tvDateInfo.text = label
            tvDateInfo.setTextColor(
                android.graphics.Color.parseColor("#2E7D32"))
            tvDateInfo.visibility = View.VISIBLE
            return true
        }
        return false
    }

    // ─── BEST BEFORE DURATION ─────────────────────────────────────────────────

    private fun tryCalculateExpiryFromDuration(
        text: String): Pair<Long, String>? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val duration = extractBestBeforeDuration(text) ?: return null

        var mfgDate: Long? = null
        for (i in lines.indices) {
            val lower = lines[i].lowercase()
            if (mfgKeywords.any { lower.contains(it) }) {
                val cands = mutableListOf(lines[i])
                if (i + 1 < lines.size) cands.add(lines[i + 1])
                if (i + 1 < lines.size) cands.add(
                    "${lines[i]} ${lines[i + 1]}")
                for (c in cands) {
                    val d = tryAllDatePatterns(c)
                    if (d != null) { mfgDate = d; break }
                }
                if (mfgDate != null) break
            }
        }

        if (mfgDate == null) {
            for (line in lines) {
                val lower = line.lowercase()
                val hasExpiryKw = expiryKeywords
                    .filter { it !in bestBeforeDurationKeywords }
                    .any { lower.contains(it) }
                if (!hasExpiryKw) {
                    val d = tryAllDatePatterns(line)
                    if (d != null) { mfgDate = d; break }
                }
            }
        }

        if (mfgDate == null) return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = mfgDate
        val mfgFmt = displayFormat.format(mfgDate)

        return when (duration.unit) {
            "months" -> {
                cal.add(Calendar.MONTH, duration.value)
                Pair(cal.timeInMillis,
                    "Calculated: MFG($mfgFmt) + ${duration.value}" +
                            " months = ${displayFormat.format(cal.timeInMillis)}")
            }
            "years" -> {
                cal.add(Calendar.YEAR, duration.value)
                Pair(cal.timeInMillis,
                    "Calculated: MFG($mfgFmt) + ${duration.value}" +
                            " years = ${displayFormat.format(cal.timeInMillis)}")
            }
            "days" -> {
                cal.add(Calendar.DAY_OF_MONTH, duration.value)
                Pair(cal.timeInMillis,
                    "Calculated: MFG($mfgFmt) + ${duration.value}" +
                            " days = ${displayFormat.format(cal.timeInMillis)}")
            }
            "weeks" -> {
                cal.add(Calendar.WEEK_OF_YEAR, duration.value)
                Pair(cal.timeInMillis,
                    "Calculated: MFG($mfgFmt) + ${duration.value}" +
                            " weeks = ${displayFormat.format(cal.timeInMillis)}")
            }
            else -> null
        }
    }

    private fun extractBestBeforeDuration(
        text: String): BestBeforeDuration? {
        val lower = text.lowercase()
        val patterns = listOf(
            Regex("""(best before|best by|use within|consume within|use before|good for|valid for|shelf life|bb|bbf|bbd)\s*:?\s*(\d+)\s*(month|months|year|years|day|days|week|weeks)""",
                RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*(month|months|year|years|day|days|week|weeks)\s*(from|of|after)\s*(manufacture|manufacturing|production|mfg|mfd|packing|packed)""",
                RegexOption.IGNORE_CASE),
            Regex("""shelf\s*life\s*:?\s*(\d+)\s*(month|months|year|years|day|days)""",
                RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*(month|months|year|years|day|days|week|weeks)""",
                RegexOption.IGNORE_CASE)
        )
        for ((idx, pattern) in patterns.withIndex()) {
            val match = pattern.find(lower) ?: continue
            val groups = match.groupValues
            val numStr = groups.firstOrNull {
                it.matches(Regex("\\d+")) } ?: continue
            val num = numStr.toIntOrNull() ?: continue
            val unitStr = groups.firstOrNull {
                it.contains("month") || it.contains("year") ||
                        it.contains("day") || it.contains("week")
            } ?: continue
            val unit = when {
                unitStr.contains("month") -> "months"
                unitStr.contains("year") -> "years"
                unitStr.contains("day") -> "days"
                unitStr.contains("week") -> "weeks"
                else -> continue
            }
            val valid = when (unit) {
                "months" -> num in 1..120
                "years" -> num in 1..20
                "days" -> num in 1..1000
                "weeks" -> num in 1..200
                else -> false
            }
            if (valid) {
                if (idx == 3 && !bestBeforeDurationKeywords
                        .any { lower.contains(it) }) continue
                return BestBeforeDuration(num, unit)
            }
        }
        return null
    }

    // ─── SPATIAL DATE EXTRACTION ──────────────────────────────────────────────

    private fun extractExpiryDateSpatial(
        visionText: Text): Pair<Long, String>? {
        val spatialDates = mutableListOf<SpatialDate>()

        for (block in visionText.textBlocks) {
            val blockBox = block.boundingBox ?: continue
            val bCX = (blockBox.left + blockBox.right) / 2f
            val bCY = (blockBox.top + blockBox.bottom) / 2f
            val bLower = block.text.lowercase()
            val bCleaned = preprocessOcrText(block.text)
            val bExpKw = expiryKeywords.firstOrNull {
                bLower.contains(it) }
            val bMfgKw = mfgKeywords.firstOrNull {
                bLower.contains(it) }

            val bDate = tryAllDatePatterns(bCleaned)
            if (bDate != null && block.lines.size <= 1) {
                spatialDates.add(SpatialDate(
                    bDate, block.text, bCX, bCY,
                    when {
                        bExpKw != null -> "EXP: ${bExpKw.uppercase()}"
                        bMfgKw != null -> "MFG: ${bMfgKw.uppercase()}"
                        else -> "Unlabeled"
                    },
                    bExpKw != null,
                    bExpKw != null || bMfgKw != null))
                continue
            }

            for (line in block.lines) {
                val lineBox = line.boundingBox ?: continue
                val lCX = (lineBox.left + lineBox.right) / 2f
                val lCY = (lineBox.top + lineBox.bottom) / 2f
                val lLower = line.text.lowercase()
                val lCleaned = preprocessOcrText(line.text)
                val lExpKw = expiryKeywords.firstOrNull {
                    lLower.contains(it) }
                val lMfgKw = mfgKeywords.firstOrNull {
                    lLower.contains(it) }
                val ctxExp = bExpKw != null || lExpKw != null
                val ctxMfg = bMfgKw != null || lMfgKw != null

                var elemDatesFound = false
                for (element in line.elements) {
                    val eBox = element.boundingBox ?: continue
                    val eCX = (eBox.left + eBox.right) / 2f
                    val eCY = (eBox.top + eBox.bottom) / 2f
                    val eDate = tryAllDatePatterns(
                        preprocessOcrText(element.text))

                    if (eDate != null) {
                        elemDatesFound = true
                        var nearExpiry = false
                        var nearMfg = false
                        var nearDist = Float.MAX_VALUE

                        for (kw in line.elements) {
                            val kwLow = kw.text.lowercase()
                            val kwBox = kw.boundingBox ?: continue
                            val kwCX = (kwBox.left + kwBox.right) / 2f
                            val dist = kotlin.math.abs(kwCX - eCX)
                            if (expiryKeywords.any {
                                    kwLow.contains(it) } &&
                                dist < nearDist) {
                                nearDist = dist
                                nearExpiry = true
                                nearMfg = false
                            } else if (mfgKeywords.any {
                                    kwLow.contains(it) } &&
                                dist < nearDist) {
                                nearDist = dist
                                nearMfg = true
                                nearExpiry = false
                            }
                        }

                        val eIdx = line.elements.indexOf(element)
                        if (eIdx > 0) {
                            val prevLow =
                                line.elements[eIdx - 1].text.lowercase()
                            if (expiryKeywords.any {
                                    prevLow.contains(it) }) {
                                nearExpiry = true; nearMfg = false
                            } else if (mfgKeywords.any {
                                    prevLow.contains(it) }) {
                                nearMfg = true; nearExpiry = false
                            }
                        }

                        spatialDates.add(SpatialDate(
                            eDate, element.text, eCX, eCY,
                            when {
                                nearExpiry -> "EXP (nearest)"
                                nearMfg -> "MFG (nearest)"
                                ctxExp -> "EXP (ctx)"
                                ctxMfg -> "MFG (ctx)"
                                else -> "Unlabeled"
                            },
                            nearExpiry || (ctxExp && !nearMfg),
                            nearExpiry || nearMfg || ctxExp || ctxMfg))
                    }
                }

                if (!elemDatesFound) {
                    val lDate = tryAllDatePatterns(lCleaned)
                    if (lDate != null) {
                        spatialDates.add(SpatialDate(
                            lDate, line.text, lCX, lCY,
                            when {
                                ctxExp -> "EXP"
                                ctxMfg -> "MFG"
                                else -> "Unlabeled"
                            },
                            ctxExp, ctxExp || ctxMfg))
                    }
                }
            }
        }

        if (spatialDates.isEmpty()) return null

        val labeledExpiry = spatialDates.filter { it.isExpiry }
        if (labeledExpiry.isNotEmpty()) {
            val best = labeledExpiry.maxByOrNull { it.timestamp }!!
            return Pair(best.timestamp,
                "Expiry (${best.label}): " +
                        displayFormat.format(best.timestamp))
        }

        if (spatialDates.size >= 2) {
            return pickExpiryBySpatialPosition(
                spatialDates,
                spatialDates.filter { !it.isExpiry && it.isLabeled })
        }

        val single = spatialDates[0]
        return if (!single.isLabeled || single.isExpiry)
            Pair(single.timestamp,
                "Date: ${displayFormat.format(single.timestamp)}")
        else
            Pair(single.timestamp,
                "⚠️ Only MFG — verify: " +
                        displayFormat.format(single.timestamp))
    }

    // ─── SPATIAL POSITION RULES ───────────────────────────────────────────────

    private fun pickExpiryBySpatialPosition(
        allDates: List<SpatialDate>,
        mfgDates: List<SpatialDate>
    ): Pair<Long, String>? {
        val mfgTs = mfgDates.map { it.timestamp }.toSet()
        val candidates = allDates.filter {
            it.timestamp !in mfgTs || it.isExpiry }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return Pair(
            candidates[0].timestamp,
            "Date: ${displayFormat.format(candidates[0].timestamp)}")

        val labeledExpiry = candidates.filter { it.isExpiry }
        if (labeledExpiry.isNotEmpty()) {
            val best = labeledExpiry.maxByOrNull { it.timestamp }!!
            return Pair(best.timestamp,
                "✅ EXP keyword: " +
                        displayFormat.format(best.timestamp))
        }

        val byY = candidates.sortedBy { it.centerY }
        for (i in 0 until byY.size - 1) {
            val a = byY[i]; val b = byY[i + 1]
            if (kotlin.math.abs(a.centerY - b.centerY) < 25f) {
                val left = if (a.centerX < b.centerX) a else b
                val right = if (a.centerX < b.centerX) b else a
                return if (left.timestamp >= right.timestamp)
                    Pair(left.timestamp,
                        "✅ Expiry (left): " +
                                displayFormat.format(left.timestamp))
                else
                    Pair(right.timestamp,
                        "✅ Expiry (later): " +
                                displayFormat.format(right.timestamp))
            }
        }

        val byYDesc = candidates.sortedByDescending { it.centerY }
        val bottom = byYDesc[0]
        val second = byYDesc.getOrNull(1)
        if (second != null && bottom.centerY - second.centerY > 20f)
            return Pair(bottom.timestamp,
                "✅ Expiry (bottom): " +
                        displayFormat.format(bottom.timestamp))

        val latest = candidates.maxByOrNull { it.timestamp }!!
        return Pair(latest.timestamp,
            "✅ Expiry (latest): " +
                    displayFormat.format(latest.timestamp))
    }

    // ─── OCR PREPROCESSING ───────────────────────────────────────────────────

    private fun preprocessOcrText(text: String): String {
        return text
            .replace(Regex("(?<=[0-9])O(?=[0-9])"), "0")
            .replace(Regex("(?<=[0-9])o(?=[0-9])"), "0")
            .replace(Regex("(?<=[0-9])I(?=[0-9])"), "1")
            .replace(Regex("(?<=[0-9])l(?=[0-9])"), "1")
            .replace(Regex("(?<=[0-9])S(?=[0-9])"), "5")
            .replace(Regex("(?<=[0-9])B(?=[0-9])"), "8")
            .replace(Regex("(?<=[0-9])Z(?=[0-9])"), "2")
            .replace(Regex("(?<=[0-9])G(?=[0-9])"), "6")
            .replace(Regex("(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)"),
                "$1$2$3$4")
            .replace(Regex("(\\d)\\s+(\\d)\\s+(\\d)"), "$1$2$3")
            .replace(Regex("(\\d)\\s+(\\d)"), "$1$2")
            .replace(Regex("[,;](?=\\d)"), "/")
            .replace(Regex("\\s*/\\s*"), "/")
            .replace(Regex("\\s*-\\s*"), "-")
            .replace(Regex("\\s*\\.\\s*"), ".")
            .replace(Regex("(\\d)\\.(\\d)"), "$1/$2")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ─── TEXT FALLBACK ────────────────────────────────────────────────────────

    private fun extractExpiryDateSmart(text: String): Pair<Long, String>? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        data class LabeledDate(
            val ts: Long, val label: String, val isExpiry: Boolean)
        val found = mutableListOf<LabeledDate>()

        for (i in lines.indices) {
            val line = lines[i]; val lower = line.lowercase()
            val expKw = expiryKeywords.firstOrNull {
                lower.contains(it) }
            if (expKw != null) {
                val cands = mutableListOf(line)
                if (i + 1 < lines.size) cands.add(lines[i + 1])
                if (i + 1 < lines.size) cands.add(
                    "$line ${lines[i + 1]}")
                for (c in cands) {
                    val d = tryAllDatePatterns(c)
                    if (d != null) {
                        found.add(LabeledDate(
                            d, "EXP: ${expKw.uppercase()}", true))
                        break
                    }
                }
            }
            val mfgKw = mfgKeywords.firstOrNull { lower.contains(it) }
            if (mfgKw != null) {
                val cands = mutableListOf(line)
                if (i + 1 < lines.size) cands.add(lines[i + 1])
                if (i + 1 < lines.size) cands.add(
                    "$line ${lines[i + 1]}")
                for (c in cands) {
                    val d = tryAllDatePatterns(c)
                    if (d != null) {
                        found.add(LabeledDate(
                            d, "MFG: ${mfgKw.uppercase()}", false))
                        break
                    }
                }
            }
        }

        val expiry = found.filter { it.isExpiry }
        if (expiry.isNotEmpty()) {
            val best = expiry.maxByOrNull { it.ts }!!
            return Pair(best.ts, best.label)
        }

        val mfgTs = found.filter { !it.isExpiry }.map { it.ts }
        for (line in lines) {
            val lower = line.lowercase()
            if (!(expiryKeywords + mfgKeywords).any {
                    lower.contains(it) }) {
                val d = tryAllDatePatterns(line)
                if (d != null && !mfgTs.contains(d))
                    return Pair(d,
                        "Auto-detected: ${displayFormat.format(d)}")
            }
        }

        val any = found.maxByOrNull { it.ts }
        return if (any != null) Pair(any.ts,
            "⚠️ MFG only — verify: ${displayFormat.format(any.ts)}")
        else null
    }

    // ─── DATE PATTERNS ────────────────────────────────────────────────────────

    fun tryAllDatePatterns(text: String): Long? =
        tryNumericPatterns(text)
            ?: tryMonthNamePatterns(text)
            ?: tryFuzzyPatterns(text)

    private fun tryNumericPatterns(text: String): Long? {
        val patterns = listOf(
            Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})"),
            Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2})"),
            Regex("(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})"),
            Regex("(\\d{1,2})[/\\-](\\d{4})"),
            Regex("(\\d{4})(\\d{2})(\\d{2})"),
            Regex("(\\d{2})(\\d{2})(\\d{4})"),
            Regex("(\\d{1,2})\\s+[/\\-.]\\s*(\\d{1,2})\\s+[/\\-.]\\s*(\\d{4})"),
            Regex("(\\d{1,2})\\s+[/\\-.]\\s*(\\d{1,2})\\s+[/\\-.]\\s*(\\d{2})"),
            Regex("(\\d{2})\\s+(\\d{2})\\s+(\\d{4})"),
            Regex("(\\d{2})\\s+(\\d{2})\\s+(\\d{2})")
        )
        val formats = listOf(
            "dd/MM/yyyy", "d/M/yyyy", "MM/dd/yyyy",
            "dd-MM-yyyy", "yyyy-MM-dd", "dd.MM.yyyy",
            "dd/MM/yy", "MM/yyyy", "yyyyMMdd", "ddMMyyyy"
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val raw = match.value
                .replace(Regex("\\s+"), "")
                .replace("-", "/")
                .replace(".", "/")
            for (fmt in formats) {
                tryParseWithFormat(raw, fmt)?.let { return it }
            }
        }
        return null
    }

    private fun tryMonthNamePatterns(text: String): Long? {
        val mp = monthMap.keys.joinToString("|") { Regex.escape(it) }
        val patterns = listOf(
            Regex("(\\d{1,2})[\\s/\\-.]?($mp)[\\s/\\-.]?(\\d{4})",
                RegexOption.IGNORE_CASE),
            Regex("($mp)[\\s/\\-.]?(\\d{1,2})[,\\s/\\-.]+(\\d{4})",
                RegexOption.IGNORE_CASE),
            Regex("($mp)[\\s/\\-.]?(\\d{4})",
                RegexOption.IGNORE_CASE),
            Regex("(\\d{4})[\\s/\\-.]($mp)[\\s/\\-.]?(\\d{1,2})",
                RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val result = parseWithMonthMap(match.value)
            if (result != null) return result
        }
        return null
    }

    private fun parseWithMonthMap(text: String): Long? {
        val parts = text.split(Regex("[\\s/\\-.,]+"))
        var day = 0; var month = -1; var year = -1

        for (part in parts) {
            val lower = part.lowercase().trim()
            val mn = monthMap[lower]
            if (mn != null) { month = mn; continue }
            if (part.matches(Regex("20\\d{2}")) ||
                part.matches(Regex("19\\d{2}"))) {
                year = part.toInt(); continue
            }
            if (part.matches(Regex("\\d{2}")) && year == -1) {
                val yr = part.toInt()
                year = if (yr > 50) 1900 + yr else 2000 + yr
                continue
            }
            if (part.matches(Regex("\\d{1,2}")) && day == 0) {
                val d = part.toInt()
                if (d in 1..31) day = d
            }
        }

        if (month == -1 || year == -1 ||
            year < 2000 || year > 2100) return null
        return try {
            val cal = Calendar.getInstance()
            cal.set(year, month - 1,
                if (day == 0) 1 else day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (day == 0) cal.set(Calendar.DAY_OF_MONTH,
                cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.timeInMillis
        } catch (e: Exception) { null }
    }

    private fun tryFuzzyPatterns(text: String): Long? {
        val noSpaces = text.replace(Regex("(\\d)\\s+(\\d)"), "$1$2")
        if (noSpaces != text)
            tryNumericPatterns(noSpaces)?.let { return it }

        val stripped = text.replace(Regex("[A-Za-z:]+"), " ").trim()
        tryNumericPatterns(stripped)?.let { return it }

        val embeddedPatterns = listOf(
            Regex("(?<![\\d])(\\d{2})/(\\d{2})(?![\\d])"),
            Regex("(?<![\\d])(\\d{2})/(\\d{4})(?![\\d])"),
            Regex("(?<![\\d])(\\d{1,2})-(\\d{2})(?![\\d])"),
            Regex("(?<![\\d])(\\d{1,2})-(\\d{4})(?![\\d])")
        )
        for (pattern in embeddedPatterns) {
            val match = pattern.find(text) ?: continue
            val raw = match.value.replace("-", "/")
            tryParseWithFormat(raw, "MM/yy")?.let { return it }
            tryParseWithFormat(raw, "MM/yyyy")?.let { return it }
            tryParseWithFormat(raw, "dd/yy")?.let { return it }
            tryParseWithFormat(raw, "dd/MM/yy")?.let { return it }
        }

        val segments = Regex("\\d[\\d/\\-.]+\\d").findAll(text)
        for (seg in segments) {
            val v = seg.value
                .replace("-", "/").replace(".", "/")
            for (fmt in listOf(
                "dd/MM/yyyy", "MM/dd/yyyy", "dd/MM/yy",
                "MM/yy", "MM/yyyy", "yyyy/MM/dd")) {
                tryParseWithFormat(v, fmt)?.let { return it }
            }
        }
        return null
    }

    private fun tryParseWithFormat(
        dateStr: String, format: String): Long? {
        return try {
            val sdf = SimpleDateFormat(format, Locale.ENGLISH)
            sdf.isLenient = false
            val date = sdf.parse(dateStr) ?: return null
            val cal = Calendar.getInstance()
            cal.time = date
            var yr = cal.get(Calendar.YEAR)
            if (yr < 100) yr = if (yr > 50) 1900 + yr else 2000 + yr
            cal.set(Calendar.YEAR, yr)
            if (!format.contains("d") && !format.contains("D"))
                cal.set(Calendar.DAY_OF_MONTH,
                    cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val y = cal.get(Calendar.YEAR)
            if (y < 2000 || y > 2100) null else cal.timeInMillis
        } catch (e: Exception) { null }
    }

    // ─── PRODUCT NAME EXTRACTION ──────────────────────────────────────────────

    private fun extractProductName(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""

        val fullLower = text.lowercase()
        val sortedBrands = brandCategoryMap.keys
            .sortedByDescending { it.length }

        for (brand in sortedBrands) {
            if (fullLower.contains(brand)) {
                val brandLine = lines.firstOrNull {
                    it.lowercase().contains(brand) } ?: continue
                val brandIdx = lines.indexOf(brandLine)
                val variant = findVariantLine(lines, brandIdx)
                return if (variant != null) {
                    val combined =
                        if (brandIdx < lines.indexOf(variant))
                            "${brandLine.trim()} ${variant.trim()}"
                        else "${variant.trim()} ${brandLine.trim()}"
                    combined
                        .replace(Regex("[^a-zA-Z0-9\\s&'®™.-]"), " ")
                        .replace(Regex("\\s+"), " ").trim().take(50)
                } else {
                    brandLine
                        .replace(Regex("[^a-zA-Z0-9\\s&'®™.-]"), " ")
                        .replace(Regex("\\s+"), " ").trim()
                }
            }
        }

        val datePattern = Regex(
            "\\d{1,2}[/\\-.\\s]\\d{1,2}[/\\-.\\s]\\d{2,4}")
        val numberOnly = Regex("^[\\d\\s./:%-]+$")
        val barcode = Regex("^\\d{8,}$")
        val weightLine = Regex(
            "^\\s*\\d+\\s*(g|kg|ml|l|oz|lb|gm|mg)\\s*$",
            RegexOption.IGNORE_CASE)
        val licPat = Regex(
            "(fssai|lic|iso|reg|gst|cin|ean|upc|rs\\.|mrp)" +
                    "\\s*[:\\-#]?\\s*[\\d.]",
            RegexOption.IGNORE_CASE)
        val addrPat = Regex(
            "\\d+[,\\s]+[a-zA-Z]+[,\\s]+" +
                    "(street|road|ave|nagar|colony|dist|phase|sector)",
            RegexOption.IGNORE_CASE)

        val hardSkip = listOf(
            "ingredients", "nutrition", "nutritional", "serving size",
            "calories", "energy", "protein", "carbohydrate", "total fat",
            "saturated", "trans fat", "cholesterol", "sodium",
            "dietary fiber", "vitamin", "calcium", "iron",
            "allergy", "allergen", "contains", "may contain",
            "manufactured by", "manufactured for", "marketed by",
            "distributed by", "imported by", "packed by",
            "customer care", "helpline", "toll free",
            "www.", "http", ".com", ".in", ".net",
            "store in", "keep in", "refrigerate",
            "fssai", "lic no", "batch no", "lot no",
            "per 100", "per serving", "daily value",
            "tel:", "ph:", "email:", "address:",
            "best before", "use by", "expiry", "exp ",
            "mfg", "mfd", "sell by", "packed on",
            "net wt", "net weight", "mrp", "price",
            "inclusive", "taxes", "country of origin",
            "not for sale", "warning", "keep dry",
            "keep cool", "avoid"
        )

        val nameIndicators = listOf(
            "milk", "butter", "ghee", "oil", "cream", "cheese", "curd",
            "yogurt", "paneer", "bread", "bun", "roti", "atta", "rice",
            "dal", "flour", "sugar", "salt", "tea", "coffee", "juice",
            "drink", "water", "chips", "snack", "biscuit", "cookie",
            "chocolate", "candy", "sauce", "ketchup", "pickle", "jam",
            "noodle", "pasta", "cereal", "oats", "soup", "soap",
            "shampoo", "lotion", "toothpaste", "gel", "tablet",
            "capsule", "syrup", "spray", "powder"
        )

        data class ScoredLine(
            val text: String, val score: Int, val index: Int)
        val scoredLines = mutableListOf<ScoredLine>()

        for ((index, line) in lines.withIndex()) {
            val lower = line.lowercase()
            if (datePattern.containsMatchIn(line)) continue
            if (numberOnly.matches(line)) continue
            if (barcode.matches(line.replace(" ", ""))) continue
            if (weightLine.matches(line)) continue
            if (licPat.containsMatchIn(line)) continue
            if (addrPat.containsMatchIn(line)) continue
            if (line.length < 2) continue
            if (hardSkip.any { lower.contains(it) }) continue

            val digitRatio = line.count { it.isDigit() }.toFloat() /
                    line.length.coerceAtLeast(1)
            if (digitRatio > 0.45f) continue
            val letterRatio = line.count { it.isLetter() }.toFloat() /
                    line.length.coerceAtLeast(1)
            if (letterRatio < 0.4f) continue

            var score = 0
            score += when (index) {
                0 -> 55; 1 -> 42; 2 -> 28; 3 -> 18; 4 -> 10
                else -> maxOf(0, 7 - index)
            }

            val upperCount = line.count { it.isUpperCase() }
            val lowerCount = line.count { it.isLowerCase() }
            val totalLetters = (upperCount + lowerCount).coerceAtLeast(1)
            val upperRatio = upperCount.toFloat() / totalLetters

            when {
                upperRatio > 0.85f && totalLetters >= 2 -> score += 35
                line.split(" ").filter { it.isNotEmpty() }
                    .all { it[0].isUpperCase() } -> score += 22
                upperRatio > 0.5f -> score += 14
                line[0].isUpperCase() -> score += 7
            }

            score += when (line.length) {
                in 2..6 -> 25; in 7..15 -> 30
                in 16..28 -> 20; in 29..45 -> 10; else -> -18
            }
            val wordCount = line.trim().split("\\s+".toRegex()).size
            score += when (wordCount) {
                1 -> 20; 2 -> 26; 3 -> 22; 4 -> 16
                5 -> 10; 6 -> 5
                else -> -10 * (wordCount - 6)
            }
            if (nameIndicators.any { lower.contains(it) }) score += 18
            if (line.contains("®") || line.contains("™") ||
                line.contains("©")) score += 30

            val weightInLine = Regex(
                "\\d+\\s*(g|kg|ml|l|oz|lb|gm|mg)\\b",
                RegexOption.IGNORE_CASE)
            if (weightInLine.containsMatchIn(line)) score -= 22
            if (line.contains(Regex("\\d+\\s*%"))) score -= 15
            if (line.contains(",") && line.length > 25) score -= 14

            val specialChars = line.count {
                !it.isLetterOrDigit() && it != ' ' && it != '-' &&
                        it != '&' && it != '\'' && it != '.' &&
                        it != '®' && it != '™'
            }
            if (specialChars > 2) score -= specialChars * 3
            if (score > 0) scoredLines.add(
                ScoredLine(line, score, index))
        }

        if (scoredLines.isEmpty()) return ""
        val sorted = scoredLines.sortedByDescending { it.score }

        if (sorted.size >= 2) {
            val first = sorted[0]; val second = sorted[1]
            val scoreDiff = first.score - second.score
            val indexDiff = kotlin.math.abs(first.index - second.index)
            if (scoreDiff < 22 && indexDiff <= 1 &&
                first.text.length <= 22 &&
                second.text.length <= 22 &&
                (first.text.length + second.text.length) <= 44) {
                val combined = if (first.index < second.index)
                    "${first.text} ${second.text}"
                else "${second.text} ${first.text}"
                if (combined.length <= 50) {
                    return combined
                        .replace(Regex("[^a-zA-Z0-9\\s&'®™.-]"), " ")
                        .replace(Regex("\\s+"), " ").trim()
                }
            }
        }

        val candidate = sorted[0].text
            .replace(Regex("[^a-zA-Z0-9\\s&'®™.-]"), " ")
            .replace(Regex("\\s+"), " ").trim()
            .split("\\s+".toRegex()).take(7).joinToString(" ")

// Reject if it looks like batch/date/code text
        val candidateLower = candidate.lowercase()
        val isBatchOrCode =
            // Too many numbers mixed with letters = batch code
            candidate.count { it.isDigit() }.toFloat() /
                    candidate.length.coerceAtLeast(1) > 0.30f ||
                    // Contains batch-like patterns
                    candidateLower.containsAny(
                        "bn ", "bn:", "nedt", "exfi", "expiru",
                        "mfd", "mfg", "batch", "lot no", "serial",
                        "0824", "0825", "0826", "0123", "0124", "0125") ||
                    // Mostly uppercase single chars = noise
                    candidate.split(" ").count {
                        it.length == 1 && it[0].isLetter() } >= 3 ||
                    // Less than 3 actual letters in a row anywhere
                    !Regex("[a-zA-Z]{3,}").containsMatchIn(candidate)

        return if (isBatchOrCode) "" else candidate
    }

    private fun findVariantLine(
        lines: List<String>, brandIdx: Int): String? {
        val variantKw = listOf(
            "milk", "butter", "ghee", "cream", "cheese", "curd",
            "full fat", "toned", "double toned", "skimmed",
            "classic", "original", "masala", "cream & onion",
            "salted", "sweet", "chocolate", "vanilla", "strawberry",
            "mango", "lite", "light", "diet", "zero", "extra",
            "regular", "special", "premium", "gold", "silver",
            "noodles", "pasta", "soup", "flavour", "flavor",
            "dark", "white", "roasted", "caramel", "crunchy",
            "smooth", "creamy", "spicy", "tangy", "natural", "organic"
        )
        for (idx in listOf(brandIdx - 1, brandIdx + 1, brandIdx + 2)) {
            if (idx < 0 || idx >= lines.size ||
                idx == brandIdx) continue
            val line = lines[idx]; val lower = line.lowercase()
            if (variantKw.any { lower.contains(it) } &&
                line.length < 35 &&
                line.count { it.isLetter() }.toFloat() /
                line.length.coerceAtLeast(1) > 0.5f &&
                !line.contains(Regex(
                    "\\d{1,2}[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})")))
                return line
        }
        return null
    }

    // ─── CATEGORY EXTRACTION ──────────────────────────────────────────────────

    private fun extractCategory(
        productName: String, fullText: String): String {
        val nameLower = productName.lowercase()
        val fullLower = fullText.lowercase()

        val sortedBrands = brandCategoryMap.keys
            .sortedByDescending { it.length }
        for (brand in sortedBrands) {
            if (nameLower.contains(brand) ||
                fullLower.contains(brand)) {
                return brandCategoryMap[brand]!!
            }
        }

        val catScores = mutableMapOf<String, Int>()
        fun add(cat: String, pts: Int) {
            catScores[cat] = (catScores[cat] ?: 0) + pts }

        listOf(nameLower to 3, fullLower to 1).forEach { (src, w) ->
            if (src.containsAny("toned milk", "full cream milk",
                    "skimmed milk", "double toned",
                    "cow milk", "buffalo milk"))
                add("Dairy", 30 * w)
            if (src.containsAny("milk", "paneer", "curd", "dahi",
                    "lassi", "buttermilk", "khoya"))
                add("Dairy", 20 * w)
            if (src.containsAny("butter", "ghee", "cheese",
                    "cream", "yogurt", "whey"))
                add("Dairy", 15 * w)
            if (src.containsAny("glucose biscuit", "marie biscuit",
                    "cream biscuit", "digestive biscuit"))
                add("Bakery & Biscuits", 30 * w)
            if (src.containsAny("biscuit", "cookie", "cracker",
                    "wafer", "bread", "bun", "cake", "muffin", "rusk"))
                add("Bakery & Biscuits", 20 * w)
            if (src.containsAny("roti", "naan", "paratha", "chapati"))
                add("Bakery & Biscuits", 15 * w)
            if (src.containsAny("cold drink", "soft drink",
                    "energy drink", "ready to drink"))
                add("Beverages", 30 * w)
            if (src.containsAny("juice", "drink", "beverage", "soda",
                    "cola", "water", "lemonade", "tea", "coffee", "chai"))
                add("Beverages", 15 * w)
            if (src.containsAny("potato chips", "corn chips",
                    "puffed snack"))
                add("Snacks", 30 * w)
            if (src.containsAny("chips", "crisps", "popcorn", "nachos",
                    "namkeen", "mixture", "bhujia", "chivda", "sev"))
                add("Snacks", 20 * w)
            if (src.containsAny("milk chocolate", "dark chocolate",
                    "white chocolate", "chocolate bar"))
                add("Chocolates & Sweets", 30 * w)
            if (src.containsAny("chocolate", "toffee", "candy",
                    "caramel", "fudge", "nougat"))
                add("Chocolates & Sweets", 20 * w)
            if (src.containsAny("sweet", "mithai", "barfi", "ladoo",
                    "gulab jamun", "halwa"))
                add("Chocolates & Sweets", 15 * w)
            if (src.containsAny("instant noodles", "cup noodles"))
                add("Noodles & Pasta", 30 * w)
            if (src.containsAny("noodles", "pasta", "spaghetti",
                    "macaroni", "vermicelli", "ramen"))
                add("Noodles & Pasta", 20 * w)
            if (src.containsAny("refined sunflower oil",
                    "extra virgin olive oil", "mustard oil"))
                add("Cooking Oil", 30 * w)
            if (src.containsAny("cooking oil", "vegetable oil",
                    "olive oil", "sunflower oil", "coconut oil"))
                add("Cooking Oil", 20 * w)
            if (src.containsAny("vanaspati", "dalda", "hydrogenated"))
                add("Cooking Oil", 15 * w)
            if (src.containsAny("garam masala", "chaat masala",
                    "biryani masala", "curry powder"))
                add("Spices & Condiments", 30 * w)
            if (src.containsAny("masala", "spice", "chilli",
                    "turmeric", "cumin", "coriander", "pepper"))
                add("Spices & Condiments", 20 * w)
            if (src.containsAny("ketchup", "pickle", "chutney",
                    "mayo", "mustard", "vinegar", "sauce"))
                add("Spices & Condiments", 15 * w)
            if (src.containsAny("whole wheat atta", "basmati rice",
                    "sona masoori"))
                add("Grains & Pulses", 30 * w)
            if (src.containsAny("atta", "wheat flour", "maida",
                    "besan", "sooji", "semolina", "ragi"))
                add("Grains & Pulses", 20 * w)
            if (src.containsAny("rice", "dal", "lentil", "chana",
                    "moong", "toor", "urad", "rajma", "oats",
                    "muesli", "cornflakes"))
                add("Grains & Pulses", 15 * w)
            if (src.containsAny("chicken", "mutton", "beef", "pork",
                    "fish", "prawn", "shrimp", "seafood", "egg"))
                add("Meat & Fish", 20 * w)
            if (src.containsAny("ice cream", "frozen yogurt",
                    "gelato", "kulfi", "popsicle"))
                add("Frozen Foods", 30 * w)
            if (src.containsAny("frozen", "freeze", "ready to cook"))
                add("Frozen Foods", 15 * w)
            if (src.containsAny("anti dandruff shampoo",
                    "antibacterial soap", "face wash gel"))
                add("Personal Care", 30 * w)
            if (src.containsAny("shampoo", "conditioner", "hair oil",
                    "face wash", "moisturizer", "sunscreen",
                    "body lotion", "body wash"))
                add("Personal Care", 20 * w)
            if (src.containsAny("soap", "toothpaste", "toothbrush",
                    "mouthwash", "deodorant", "talcum"))
                add("Personal Care", 15 * w)
            if (src.containsAny("500mg", "250mg", "100mg",
                    "film coated", "oral solution"))
                add("Medicine", 30 * w)
            if (src.containsAny("tablet", "capsule", "syrup",
                    "drops", "ointment", "antibiotic", "antacid"))
                add("Medicine", 20 * w)
            if (src.containsAny("medicine", "drug", "pharma",
                    "supplement", "probiotic", "vitamin"))
                add("Medicine", 15 * w)
            if (src.containsAny("baby formula", "infant formula",
                    "stage 1", "stage 2"))
                add("Baby Products", 30 * w)
            if (src.containsAny("baby", "infant", "toddler",
                    "diaper", "nappy", "baby food"))
                add("Baby Products", 20 * w)
            if (src.containsAny("dog food", "cat food",
                    "puppy food", "kitten food"))
                add("Pet Food", 30 * w)
            if (src.containsAny("pet", "dog treat", "cat treat",
                    "bird seed", "aquarium"))
                add("Pet Food", 15 * w)
        }

        if (catScores.isEmpty()) return "General"
        val best = catScores.maxByOrNull { it.value }!!
        return if (best.value >= 10) best.key else "General"
    }

    private fun String.containsAny(vararg kw: String): Boolean =
        kw.any { this.contains(it) }

    // ─── SAVE ─────────────────────────────────────────────────────────────────

    private fun saveProduct() {
        val name = etProductName.text.toString().trim()
        val category = etCategory.text.toString().trim()
            .ifEmpty { "General" }

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter product name",
                Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedExpiryDate == 0L) {
            Toast.makeText(this, "Please select expiry date",
                Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val isExpired = selectedExpiryDate < now

        lifecycleScope.launch {
            try {
                val product = Product(
                    name = name,
                    expiryDate = selectedExpiryDate,
                    category = category,
                    isExpired = isExpired
                )
                repository.insert(product)
                Toast.makeText(this@AddItemActivity,
                    "✅ $name saved!",
                    Toast.LENGTH_SHORT).show()
                val prefs = getSharedPreferences(
                    "savesmart_prefs", MODE_PRIVATE)
                val prev = prefs.getInt("total_scanned", 0)
                prefs.edit().putInt("total_scanned", prev + 1).apply()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddItemActivity,
                    "Error saving: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }


    // ─── PERMISSIONS ──────────────────────────────────────────────────────────

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
        } else {
            Toast.makeText(this,
                "Camera permission required",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}