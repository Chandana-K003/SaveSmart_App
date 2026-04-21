package com.example.expirytracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageEnhancer {

    fun enhanceForOcr(bitmap: Bitmap, context: Context): List<Bitmap> {
        val results = mutableListOf<Bitmap>()

        // Always add original first
        results.add(bitmap)

        // Only scale to 2x max to avoid OOM
        val scaled = safeScale(bitmap, 2.0f) ?: bitmap

        safeAdd(results) { enhanceNormal(bitmap) }
        safeAdd(results) { enhanceDotMatrix(scaled, dilateRadius = 2) }
        safeAdd(results) { enhanceHighContrast(scaled) }
        safeAdd(results) { enhanceDotMatrixAggressive(scaled) }

        return results
    }

    private fun safeAdd(list: MutableList<Bitmap>, block: () -> Bitmap) {
        try {
            val b = block()
            if (!b.isRecycled) list.add(b)
        } catch (e: Exception) { }
    }

    private fun safeScale(bitmap: Bitmap, scale: Float): Bitmap? {
        return try {
            val w = (bitmap.width * scale).toInt().coerceIn(1, 4096)
            val h = (bitmap.height * scale).toInt().coerceIn(1, 4096)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (e: Exception) { null }
    }

    fun enhanceNormal(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val contrast = ColorMatrix(floatArrayOf(
            2.5f, 0f, 0f, 0f, -100f,
            0f, 2.5f, 0f, 0f, -100f,
            0f, 0f, 2.5f, 0f, -100f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrast)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun enhanceDotMatrix(bitmap: Bitmap, dilateRadius: Int = 2): Bitmap {
        val gray = toGrayscale(bitmap)
        val contrasted = applyContrast(gray, 4.0f, -180f)
        val thresholded = applyOtsuThreshold(contrasted)
        return dilateBitmap(thresholded, dilateRadius)
    }

    fun enhanceDotMatrixAggressive(bitmap: Bitmap): Bitmap {
        val gray = toGrayscale(bitmap)
        val brightened = applyContrast(gray, 1.5f, 60f)
        val contrasted = applyContrast(brightened, 5.0f, -220f)
        val thresholded = applyOtsuThreshold(contrasted)
        val dilated = dilateBitmap(thresholded, 3)
        return dilateBitmap(dilated, 2)
    }

    fun enhanceHighContrast(bitmap: Bitmap): Bitmap {
        val gray = toGrayscale(bitmap)
        val contrasted = applyContrast(gray, 4.0f, -200f)
        return applyOtsuThreshold(contrasted)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(
            bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyContrast(
        bitmap: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyOtsuThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        for (pixel in pixels) {
            val gray = (Color.red(pixel) * 0.299 +
                    Color.green(pixel) * 0.587 +
                    Color.blue(pixel) * 0.114).toInt().coerceIn(0, 255)
            histogram[gray]++
        }

        val total = pixels.size
        var sum = 0.0
        for (i in 0..255) sum += i * histogram[i]

        var sumB = 0.0
        var wB = 0
        var maxVariance = 0.0
        var threshold = 128

        for (i in 0..255) {
            wB += histogram[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * histogram[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val variance = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val gray = (Color.red(pixels[i]) * 0.299 +
                    Color.green(pixels[i]) * 0.587 +
                    Color.blue(pixels[i]) * 0.114).toInt()
            outPixels[i] = if (gray < threshold) Color.BLACK else Color.WHITE
        }
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun dilateBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var hasBlack = false
                outer@ for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        if (pixels[ny * width + nx] == Color.BLACK) {
                            hasBlack = true
                            break@outer
                        }
                    }
                }
                resultPixels[y * width + x] =
                    if (hasBlack) Color.BLACK else Color.WHITE
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }
}