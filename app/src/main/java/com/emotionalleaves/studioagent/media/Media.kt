package com.emotionalleaves.studioagent.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageTools {
    fun mediaDir(ctx: Context): File = File(ctx.filesDir, "media").apply { mkdirs() }
    fun exportDir(ctx: Context): File = File(ctx.filesDir, "exports").apply { mkdirs() }

    fun size(path: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return max(o.outWidth, 0) to max(o.outHeight, 0)
    }

    /** Decode no larger than needed, so big files do not exhaust memory. */
    fun decode(path: String, maxSide: Int): Bitmap? {
        if (path.isBlank() || !File(path).exists()) return null
        val (w, h) = size(path)
        if (w <= 0 || h <= 0) return null
        var sample = 1
        while (max(w, h) / (sample * 2) >= maxSide) sample *= 2
        val raw: Bitmap = (try { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) } catch (e: OutOfMemoryError) { null }) ?: return null
        val long = max(raw.width, raw.height)
        if (long <= maxSide) return raw
        val k = maxSide.toFloat() / long
        return Bitmap.createScaledBitmap(raw, (raw.width * k).roundToInt().coerceAtLeast(1), (raw.height * k).roundToInt().coerceAtLeast(1), true)
    }

    fun copyUri(ctx: Context, uri: Uri, dest: File): Boolean = try {
        ctx.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        dest.exists() && dest.length() > 0
    } catch (e: Exception) { false }

    fun saveJpeg(b: Bitmap, dest: File, quality: Int = 95) {
        dest.parentFile?.mkdirs()
        dest.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    fun jpegBytes(path: String, maxSide: Int = 1568, quality: Int = 85): ByteArray? {
        val b = decode(path, maxSide) ?: return null
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }

    /**
     * Straighten a photographed artwork. [corners] are 8 numbers from 0 to 1: top-left, top-right, bottom-right, bottom-left (x, y each).
     * [aspect] is width / height of the real work, or 0 to keep the shape of the marked corners.
     */
    fun straighten(src: Bitmap, corners: FloatArray, aspect: Double): Bitmap {
        val pts = FloatArray(8) { i -> corners[i] * (if (i % 2 == 0) src.width else src.height) }
        val top = hypot(pts[2] - pts[0], pts[3] - pts[1]); val bottom = hypot(pts[4] - pts[6], pts[5] - pts[7])
        val left = hypot(pts[6] - pts[0], pts[7] - pts[1]); val right = hypot(pts[4] - pts[2], pts[5] - pts[3])
        val outW = ((top + bottom) / 2f).roundToInt().coerceIn(16, 4000)
        val outH = (if (aspect > 0) outW / aspect else ((left + right) / 2f).toDouble()).roundToInt().coerceIn(16, 4000)
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix(); m.setPolyToPoly(pts, 0, dst, 0, 4)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Neutral white balance (grey-world) plus a gentle levels stretch. The original file is never touched. */
    fun autoFix(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        var sr = 0L; var sg = 0L; var sb = 0L
        val hist = IntArray(256)
        val step = max(1, px.size / 200_000)
        var n = 0
        var i = 0
        while (i < px.size) {
            val c = px[i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            sr += r; sg += g; sb += b; hist[(r * 3 + g * 6 + b) / 10]++; n++; i += step
        }
        if (n == 0) return src
        val ar = sr.toDouble() / n; val ag = sg.toDouble() / n; val ab = sb.toDouble() / n
        val grey = (ar + ag + ab) / 3
        // Only correct part of the way, so strongly coloured artworks are not washed out.
        val kr = 1 + (grey / max(ar, 1.0) - 1) * 0.5; val kg = 1 + (grey / max(ag, 1.0) - 1) * 0.5; val kb = 1 + (grey / max(ab, 1.0) - 1) * 0.5
        var lo = 0; var acc = 0
        while (lo < 255 && acc + hist[lo] < n * 0.005) { acc += hist[lo]; lo++ }
        var hi = 255; acc = 0
        while (hi > 0 && acc + hist[hi] < n * 0.005) { acc += hist[hi]; hi-- }
        if (hi - lo < 40) { lo = 0; hi = 255 }
        val scale = 255.0 / (hi - lo)
        val lr = IntArray(256) { (((it * kr) - lo) * scale).roundToInt().coerceIn(0, 255) }
        val lg = IntArray(256) { (((it * kg) - lo) * scale).roundToInt().coerceIn(0, 255) }
        val lb = IntArray(256) { (((it * kb) - lo) * scale).roundToInt().coerceIn(0, 255) }
        for (j in px.indices) {
            val c = px[j]
            px[j] = (c and -0x1000000) or (lr[(c shr 16) and 255] shl 16) or (lg[(c shr 8) and 255] shl 8) or lb[c and 255]
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Export a JPEG with the long side at [longSide] px and, if [maxBytes] > 0, under that file size. */
    fun export(path: String, longSide: Int, maxBytes: Long, dest: File): Boolean {
        val b = decode(path, longSide) ?: return false
        var q = 92
        while (true) {
            saveJpeg(b, dest, q)
            if (maxBytes <= 0 || dest.length() <= maxBytes || q <= 40) return true
            q -= 8
        }
    }

    /** Pull "1920" and "5 MB" style limits out of a call's image spec text. */
    fun parseSpecs(spec: String): Pair<Int, Long> {
        val s = spec.lowercase()
        val px = Regex("(\\d{3,5})\\s*(px|pixels)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 1920
        val mb = Regex("(\\d+(?:\\.\\d+)?)\\s*mb").find(s)?.groupValues?.get(1)?.toDoubleOrNull() ?: 5.0
        return px.coerceIn(600, 6000) to (mb * 1024 * 1024).toLong()
    }
}

/** Small page-flow PDF writer on top of Android's PdfDocument. US Letter by default. */
class Pdf(private val w: Int = 612, private val h: Int = 792, private val margin: Int = 54) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private var n = 0

    fun newPage() {
        page?.let { doc.finishPage(it) }
        n++
        page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, n).create())
        y = margin.toFloat()
    }

    private fun room(space: Float) { if (page == null || y + space > h - margin) newPage() }

    fun text(s: String, size: Float = 11f, bold: Boolean = false, gap: Float = 6f, grey: Boolean = false) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; color = if (grey) Color.DKGRAY else Color.BLACK
        }
        for (para in s.split("\n")) {
            val line = para.ifEmpty { " " }
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, tp, w - 2 * margin).setLineSpacing(2f, 1f).build()
            room(layout.height.toFloat())
            val c = page!!.canvas
            c.save(); c.translate(margin.toFloat(), y); layout.draw(c); c.restore()
            y += layout.height + gap
        }
    }

    fun space(px: Float) { y += px }

    fun image(path: String, maxH: Float): Boolean {
        val b = ImageTools.decode(path, 1600) ?: return false
        val availW = (w - 2 * margin).toFloat()
        val k = min(availW / b.width, maxH / b.height)
        val dw = b.width * k; val dh = b.height * k
        room(dh + 8)
        val left = margin + (availW - dw) / 2
        page!!.canvas.drawBitmap(b, null, RectF(left, y, left + dw, y + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        y += dh + 8
        return true
    }

    fun rule() {
        room(10f)
        page!!.canvas.drawLine(margin.toFloat(), y, (w - margin).toFloat(), y, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        y += 10
    }

    fun save(file: File): File {
        if (page == null) newPage()
        page?.let { doc.finishPage(it) }
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
