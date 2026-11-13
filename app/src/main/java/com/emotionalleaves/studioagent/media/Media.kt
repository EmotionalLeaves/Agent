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

/** QR codes: a phone camera pointed at one opens the link. Drawn here, nothing fetched. */
object Qr {
    fun bitmap(text: String, px: Int = 600): Bitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
        val b = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
        val row = IntArray(m.width)
        for (y in 0 until m.height) { for (x in 0 until m.width) row[x] = if (m.get(x, y)) Color.BLACK else Color.WHITE; b.setPixels(row, 0, m.width, 0, y, m.width, 1) }
        b
    } catch (e: Exception) { null }
}

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
    /**
     * What a juror would notice first about a photo of the work, in plain words: too dark, washed out, soft, or a glare spot.
     * Empty when it looks fine. Measured on a small copy, so it is quick.
     */
    fun quality(path: String): List<String> {
        val b = decode(path, 500) ?: return emptyList()
        val w = b.width; val h = b.height
        if (w < 8 || h < 8) return emptyList()
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(px.size) { val c = px[it]; ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 }
        val mean = g.average()
        val clipped = g.count { it >= 250 }.toDouble() / g.size
        // Sharpness: how strongly neighboring pixels differ (variance of the Laplacian).
        var sum = 0.0; var sum2 = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val l = (4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]).toDouble()
            sum += l; sum2 += l * l; n++
        }
        val lapVar = if (n > 0) sum2 / n - (sum / n) * (sum / n) else 0.0
        val out = mutableListOf<String>()
        if (mean < 60) out += "It looks dark. More daylight from the side, or the phone's exposure turned up, will show the work properly."
        if (mean > 225 && clipped > 0.35) out += "It looks washed out. Move out of direct sun, or turn the exposure down a little."
        else if (clipped in 0.02..0.35 && mean < 200) out += "There may be a glare spot. Turn the work or the light a little so the shine leaves the surface."
        if (lapVar < 40) out += "It may be soft or blurry. Hold the phone steady (elbows on something), tap the work to focus, and try again."
        return out
    }

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
    fun export(path: String, longSide: Int, maxBytes: Long, dest: File, artist: String = "", caption: String = ""): Boolean {
        var side = longSide
        var b = decode(path, side) ?: return false
        var q = 92
        while (true) {
            saveJpeg(b, dest, q)
            if (maxBytes <= 0 || dest.length() <= maxBytes) break
            if (q > 60) { q -= 8; continue }
            // Still too big at a decent quality: make it a little smaller rather than blotchy, never under 800 pixels.
            if (side <= 800) break
            side = (side * 0.85).toInt().coerceAtLeast(800); b = decode(path, side) ?: break; q = 85
        }
        credit(dest, artist, caption)
        return true
    }

    /** Writes the artist's name, a copyright line and the caption into the picture file itself, so the credit travels with it. */
    fun credit(file: File, artist: String, caption: String) {
        try {
            val ascii = { t: String -> java.text.Normalizer.normalize(t.replace('\u00D7', 'x'), java.text.Normalizer.Form.NFD).replace(Regex("[^\\x20-\\x7E]"), "") }
            val e = android.media.ExifInterface(file.absolutePath)
            // Many calls ask for 300 dpi. Pixels decide the quality, but the file says 300 so no one has to ask.
            e.setAttribute(android.media.ExifInterface.TAG_X_RESOLUTION, "300/1")
            e.setAttribute(android.media.ExifInterface.TAG_Y_RESOLUTION, "300/1")
            e.setAttribute(android.media.ExifInterface.TAG_RESOLUTION_UNIT, "2")
            if (artist.isNotBlank()) {
                e.setAttribute(android.media.ExifInterface.TAG_ARTIST, ascii(artist))
                e.setAttribute(android.media.ExifInterface.TAG_COPYRIGHT, "Copyright ${java.time.LocalDate.now().year} ${ascii(artist)}. All rights reserved.")
            }
            if (caption.isNotBlank()) e.setAttribute(android.media.ExifInterface.TAG_IMAGE_DESCRIPTION, ascii(caption).take(300))
            e.saveAttributes()
        } catch (x: Exception) { }
    }

    /** Pull "1920" and "5 MB" style limits out of a call's image spec text. */
    /**
     * Reads a call's image rules: "1,920 pixels on the longest side", "1500px", "under 3 MB", "500 KB", "2 megabytes".
     * Files are made a little under the limit, because some upload forms count a megabyte as a million bytes.
     */
    fun parseSpecs(spec: String): Pair<Int, Long> {
        val s = spec.lowercase().replace(Regex("(\\d),(\\d{3})"), "$1$2")
        val px = Regex("(\\d{3,5})\\s*(px|pixels?)\\b").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 1920
        val mb = Regex("(\\d+(?:\\.\\d+)?)\\s*(mb|megabytes?)\\b").find(s)?.groupValues?.get(1)?.toDoubleOrNull()
        val kb = Regex("(\\d+(?:\\.\\d+)?)\\s*(kb|kilobytes?)\\b").find(s)?.groupValues?.get(1)?.toDoubleOrNull()
        val bytes = when { mb != null -> mb * 1_000_000; kb != null -> kb * 1_000; else -> 5_000_000.0 } * 0.95
        return px.coerceIn(600, 6000) to bytes.toLong()
    }
}

/**
 * Writes the documents that go to galleries and jurors.
 * Titles in the app's display face, text in its reading face, a running footer with the artist's name,
 * the document's name and the page number, and helpers for the aligned columns real CVs and invoices use.
 */
class Pdf(ctx: Context? = null, private val owner: String = "", private val docTitle: String = "",
          private val w: Int = 612, private val h: Int = 792, private val margin: Int = 60) {
    enum class Face { DISPLAY, BODY, BOLD, ITALIC }

    companion object {
        /** When set (by tests), pages are drawn as pictures into this folder instead of a PDF, so the layout can be looked at. */
        var previewDir: File? = null
    }
    private val preview = previewDir
    /** A cover page carries no running footer. */
    var coverPage = false
    private val shots = mutableListOf<Bitmap>()
    private var shot: Canvas? = null

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private var n = 0
    private fun font(ctx: Context?, id: Int): Typeface? = try { ctx?.let { androidx.core.content.res.ResourcesCompat.getFont(it, id) } } catch (e: Exception) { null }
    private val display = font(ctx, com.emotionalleaves.studioagent.R.font.bricolage_semibold) ?: Typeface.DEFAULT_BOLD
    private val body = font(ctx, com.emotionalleaves.studioagent.R.font.atkinson_regular) ?: Typeface.DEFAULT
    private val bodyBold = font(ctx, com.emotionalleaves.studioagent.R.font.atkinson_bold) ?: Typeface.DEFAULT_BOLD
    private val bodyItalic = font(ctx, com.emotionalleaves.studioagent.R.font.atkinson_italic) ?: Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    private val ink = 0xFF1F2124.toInt()
    private val pencil = 0xFF5F6368.toInt()

    /** Usable width between the margins, in points. */
    val width: Int get() = w - 2 * margin
    val pageWidth: Int get() = w
    val pageHeight: Int get() = h
    val left: Float get() = margin.toFloat()

    /** Text set in the reading face's true italic, for titles of works. */
    fun em(s: CharSequence): CharSequence = android.text.SpannableString(s).apply { setSpan(FaceSpan(bodyItalic), 0, length, 0) }

    fun paint(size: Float, face: Face = Face.BODY, grey: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; color = if (grey) pencil else ink
        typeface = when (face) { Face.DISPLAY -> display; Face.BODY -> body; Face.BOLD -> bodyBold; Face.ITALIC -> bodyItalic }
    }

    /** Lays text out without drawing it. Italic spans in the text (titles of works) are kept. */
    fun layout(s: CharSequence, width: Int, size: Float, face: Face = Face.BODY, grey: Boolean = false, center: Boolean = false): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, paint(size, face, grey), width.coerceAtLeast(10))
            .setAlignment(if (center) android.text.Layout.Alignment.ALIGN_CENTER else android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(size * 0.25f, 1f).build()

    fun newPage() {
        finishPage()
        n++
        if (preview != null) {
            val b = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
            shots += b
            shot = Canvas(b).apply { drawColor(Color.WHITE); scale(2f, 2f) }
        } else page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, n).create())
        y = margin.toFloat()
    }

    private val open: Boolean get() = page != null || shot != null
    private fun cv(): Canvas = shot ?: cv()

    private fun finishPage() {
        if (!open) return
        val c = cv()
        if ((owner.isNotBlank() || docTitle.isNotBlank()) && !(n == 1 && coverPage)) {
            val tp = paint(8f, Face.BODY, true)
            val base = h - margin / 2f
            c.drawText(listOf(owner, docTitle).filter { it.isNotBlank() }.joinToString(", "), margin.toFloat(), base, tp)
            val num = "Page $n"
            c.drawText(num, w - margin - tp.measureText(num), base, tp)
        }
        page?.let { doc.finishPage(it) }
        page = null; shot = null
    }

    fun canvas(): Canvas { if (!open) newPage(); return cv() }

    private fun room(space: Float) { if (!open || y + space > h - margin) newPage() }

    /** Starts a new page first if [need] points will not fit, so a heading never sits alone at the foot of a page. */
    fun keep(need: Float) = room(need)

    fun text(s: CharSequence, size: Float = 11f, bold: Boolean = false, gap: Float = 6f, grey: Boolean = false,
             face: Face? = null, indent: Float = 0f, center: Boolean = false) {
        val f = face ?: if (bold) Face.BOLD else Face.BODY
        val str = s.toString()
        var start = 0
        while (start <= str.length) {
            val end = str.indexOf('\n', start).let { if (it < 0) str.length else it }
            val part: CharSequence = if (end > start) s.subSequence(start, end) else " "
            val l = layout(part, (width - indent).toInt(), size, f, grey, center)
            room(l.height.toFloat())
            val c = cv()
            c.save(); c.translate(margin + indent, y); l.draw(c); c.restore()
            y += l.height + gap
            start = end + 1
        }
    }

    /** A label in a fixed left column and its value beside it, with an optional amount on the right edge. */
    fun row(label: String, value: CharSequence, labelWidth: Float = 130f, size: Float = 11f, gap: Float = 6f, right: String = "") {
        val tp = paint(size)
        val rw = if (right.isBlank()) 0f else tp.measureText(right) + 18f
        val lv = layout(value, (width - labelWidth - rw).toInt(), size)
        val ll = layout(label, (labelWidth - 10).toInt(), size, Face.BODY, grey = true)
        val tall = max(lv.height, ll.height).toFloat()
        room(tall)
        val c = cv()
        c.save(); c.translate(margin.toFloat(), y); ll.draw(c); c.restore()
        c.save(); c.translate(margin + labelWidth, y); lv.draw(c); c.restore()
        if (right.isNotBlank()) c.drawText(right, w - margin - tp.measureText(right), y + lv.getLineBaseline(0), tp)
        y += tall + gap
    }

    /** A CV line: the year in its own column, the entry hanging beside it. */
    fun hang(year: String, entry: CharSequence, col: Float = 52f, size: Float = 10.5f, gap: Float = 5f, right: String = "") = row(year, entry, col, size, gap, right)

    /** A line with text on the left and an amount on the right edge. */
    fun amount(label: CharSequence, value: String, size: Float = 11f, face: Face = Face.BODY, gap: Float = 6f) {
        val tp = paint(size, face)
        val vw = tp.measureText(value)
        val l = layout(label, (width - vw - 24).toInt(), size, face)
        room(l.height.toFloat())
        val c = cv()
        c.save(); c.translate(margin.toFloat(), y); l.draw(c); c.restore()
        c.drawText(value, w - margin - vw, y + l.getLineBaseline(0), tp)
        y += l.height + gap
    }

    fun space(px: Float) { y += px }

    fun image(path: String, maxH: Float): Boolean {
        val b = ImageTools.decode(path, 1800) ?: return false
        val availW = width.toFloat()
        val k = min(availW / b.width, maxH / b.height)
        val dw = b.width * k; val dh = b.height * k
        room(dh + 10)
        val left = margin + (availW - dw) / 2
        cv().drawBitmap(b, null, RectF(left, y, left + dw, y + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        y += dh + 10
        return true
    }

    /** A square picture drawn at [size] points, centered, or at the left when [left] is set. */
    fun bitmap(b: Bitmap, size: Float, left: Boolean = false) {
        room(size + 8)
        val x = if (left) margin.toFloat() else margin + (width - size) / 2
        cv().drawBitmap(b, null, RectF(x, y, x + size, y + size), Paint())
        y += size + 8
    }

    fun rule(dark: Boolean = false) {
        room(12f)
        cv().drawLine(margin.toFloat(), y, (w - margin).toFloat(), y,
            Paint().apply { color = if (dark) ink else 0xFFD5D6D2.toInt(); strokeWidth = if (dark) 0.8f else 0.6f })
        y += 12
    }

    fun save(file: File): File {
        if (!open && n == 0) newPage()
        finishPage()
        file.parentFile?.mkdirs()
        if (preview != null) {
            preview.mkdirs()
            shots.forEachIndexed { i, b -> File(preview, "${file.nameWithoutExtension}_p${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            file.writeText("%PDF- preview of ${shots.size} pages")
        } else file.outputStream().use { doc.writeTo(it) }
        try { doc.close() } catch (e: Exception) { }
        return file
    }
}

/** Sets a whole typeface on a span of text, so italics use the real italic font instead of a slanted copy. */
class FaceSpan(private val face: Typeface) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = face }
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = face }
}
