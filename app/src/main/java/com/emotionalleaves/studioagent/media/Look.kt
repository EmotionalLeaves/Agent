package com.emotionalleaves.studioagent.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The tricks painters use to see their own work fresh: flip it, take the color away, squint,
 * cut it down to two or three values, and pull out the colors actually used. All on the phone,
 * no account, nothing sent anywhere.
 */
object Look {
    fun flip(b: Bitmap): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { preScale(-1f, 1f) }, true)

    /** Lightness 0..255 of a color, as the eye weighs it. */
    fun lum(c: Int): Int = ((0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c))).roundToInt().coerceIn(0, 255)

    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    fun gray(b: Bitmap): Bitmap {
        val px = pixels(b); for (i in px.indices) { val l = lum(px[i]); px[i] = Color.rgb(l, l, l) }
        return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
    }

    /** Squinting: the detail goes and the big shapes stay. Down to a few dozen pixels and back up, smoothly. */
    fun squint(b: Bitmap): Bitmap {
        val small = max(1, min(b.width, b.height) / 28)
        val sw = max(1, b.width / small); val sh = max(1, b.height / small)
        val tiny = Bitmap.createScaledBitmap(b, sw, sh, true)
        return Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(tiny, sw * 4, sh * 4, true), b.width, b.height, true)
    }

    /**
     * The picture in [levels] values only (2 = black and white, 3 = dark, middle, light), split where the
     * picture's own values divide most evenly, so the result shows its real value plan.
     */
    fun values(b: Bitmap, levels: Int = 3): Bitmap {
        val px = pixels(squint2(b)); val ls = px.map { lum(it) }.sorted()
        val cuts = (1 until levels).map { ls[(ls.size * it / levels).coerceIn(0, ls.size - 1)] }
        val tones = when (levels) { 2 -> intArrayOf(30, 235); 3 -> intArrayOf(35, 140, 240); else -> IntArray(levels) { 20 + it * 220 / (levels - 1) } }
        for (i in px.indices) { val l = lum(px[i]); val k = cuts.count { l > it }; val t = tones[k]; px[i] = Color.rgb(t, t, t) }
        return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
    }

    /** A light blur so value groups come out as shapes, not speckle. */
    private fun squint2(b: Bitmap): Bitmap {
        val f = max(1, min(b.width, b.height) / 90)
        val sw = max(1, b.width / f); val sh = max(1, b.height / f)
        return Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, sw, sh, true), b.width, b.height, true)
    }

    /** Thirds lines over the picture, where the eye tends to land. */
    fun thirds(b: Bitmap): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); strokeWidth = max(2f, b.width / 300f) }
        val d = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt(); strokeWidth = p.strokeWidth * 2.5f }
        for (k in 1..2) { val x = b.width * k / 3f; val y = b.height * k / 3f
            c.drawLine(x, 0f, x, b.height.toFloat(), d); c.drawLine(0f, y, b.width.toFloat(), y, d)
            c.drawLine(x, 0f, x, b.height.toFloat(), p); c.drawLine(0f, y, b.width.toFloat(), y, p) }
        return out
    }

    /** The main colors used, most to least, by a simple grouping of a small copy of the picture. */
    fun palette(b: Bitmap, k: Int = 6): List<Pair<Int, Double>> {
        val s = Bitmap.createScaledBitmap(b, 64, max(1, 64 * b.height / max(1, b.width)), true)
        val px = pixels(s)
        // Start from colors spread through the picture's lightness, then settle (k-means, a few passes).
        val byL = px.sortedBy { lum(it) }
        var centers = (0 until k).map { byL[(byL.size * (2 * it + 1) / (2 * k)).coerceIn(0, byL.size - 1)] }.map { floatArrayOf(Color.red(it).toFloat(), Color.green(it).toFloat(), Color.blue(it).toFloat()) }
        val assign = IntArray(px.size)
        repeat(8) {
            for (i in px.indices) { val c = px[i]; var best = 0; var bd = Float.MAX_VALUE
                centers.forEachIndexed { j, m -> val dr = Color.red(c) - m[0]; val dg = Color.green(c) - m[1]; val db = Color.blue(c) - m[2]; val d = dr * dr + dg * dg + db * db; if (d < bd) { bd = d; best = j } }
                assign[i] = best }
            centers = centers.indices.map { j -> val mine = px.indices.filter { assign[it] == j }
                if (mine.isEmpty()) centers[j] else floatArrayOf(mine.map { Color.red(px[it]) }.average().toFloat(), mine.map { Color.green(px[it]) }.average().toFloat(), mine.map { Color.blue(px[it]) }.average().toFloat()) }
        }
        val counts = IntArray(k); assign.forEach { counts[it]++ }
        return centers.indices.filter { counts[it] > 0 }.map { Color.rgb(centers[it][0].roundToInt(), centers[it][1].roundToInt(), centers[it][2].roundToInt()) to counts[it].toDouble() / px.size }
            .sortedByDescending { it.second }
            // Two groups that came out nearly the same color are one color.
            .fold(mutableListOf<Pair<Int, Double>>()) { acc, x -> val near = acc.indexOfFirst { dist(it.first, x.first) < 18 }; if (near >= 0) acc[near] = acc[near].first to acc[near].second + x.second else acc += x; acc }
    }

    private fun dist(a: Int, b: Int): Double { val dr = Color.red(a) - Color.red(b); val dg = Color.green(a) - Color.green(b); val db = Color.blue(a) - Color.blue(b); return sqrt((dr * dr + dg * dg + db * db).toDouble()) }

    /** A plain name for a color, so "a warm mid brown" can be said out loud. */
    fun name(c: Int): String {
        val hsv = FloatArray(3); Color.colorToHSV(c, hsv); val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
        val light = when { v < 0.22f -> "very dark"; v < 0.45f -> "dark"; v < 0.7f -> "mid"; v < 0.88f -> "light"; else -> "very light" }
        if (s < 0.12f) return when { v < 0.15f -> "near black"; v > 0.93f -> "near white"; else -> "$light gray" }
        val hue = when { h < 15 || h >= 345 -> "red"; h < 40 -> if (v < 0.6f) "brown" else "orange"; h < 65 -> if (v < 0.55f) "olive" else "yellow"; h < 160 -> "green"; h < 195 -> "teal"; h < 255 -> "blue"; h < 290 -> "violet"; else -> "pink" }
        val muted = if (s < 0.35f) "grayed " else ""
        return "$light $muted$hue"
    }

    /** What the values say, in plain words: how much of the range is used, and whether the picture has a clear dark and light. */
    fun valueNotes(b: Bitmap): List<String> {
        val s = Bitmap.createScaledBitmap(b, 80, max(1, 80 * b.height / max(1, b.width)), true)
        val ls = pixels(s).map { lum(it) }.sorted()
        val dark = ls[ls.size * 3 / 100]; val light = ls[ls.size * 97 / 100]; val span = light - dark
        val mid = ls.count { it in 85..170 }.toDouble() / ls.size
        val out = mutableListOf<String>()
        out += when {
            span < 110 -> "The values sit close together: the darkest dark and the lightest light are not far apart. From across a room it may read as flat. A truly dark accent or a clean light near the part that matters most usually helps."
            span < 170 -> "A fair range of values, from a mid dark to a light. Pushing the darkest dark or the lightest light a little further, in one place, can give it more pull from a distance."
            else -> "A full range of values, from near black to near white. That carries well from across a room."
        }
        if (mid > 0.7) out += "Most of the picture is middle values. Look at the three-value view: if the shapes are hard to tell apart there, they will be hard to tell apart on a wall."
        val warm = pixels(s).count { val hsv = FloatArray(3); Color.colorToHSV(it, hsv); hsv[1] > 0.2f && (hsv[0] < 70 || hsv[0] > 330) }
        val cool = pixels(s).count { val hsv = FloatArray(3); Color.colorToHSV(it, hsv); hsv[1] > 0.2f && hsv[0] in 150f..280f }
        val total = s.width * s.height
        if (warm + cool > total / 5) out += when { warm > cool * 3 -> "Mostly warm color. A small cool note can make the warm sing."; cool > warm * 3 -> "Mostly cool color. A small warm note can make the cool sing."; else -> "A mix of warm and cool color." }
        return out
    }

    /** How far apart two values are, 0..255: for a quick check that the focal point has the strongest contrast. */
    fun contrastAt(b: Bitmap, cx: Float, cy: Float): Int {
        val r = max(2, min(b.width, b.height) / 12)
        val x0 = ((cx * b.width).toInt() - r).coerceIn(0, b.width - 1); val y0 = ((cy * b.height).toInt() - r).coerceIn(0, b.height - 1)
        val w = min(2 * r, b.width - x0); val h = min(2 * r, b.height - y0)
        val ls = IntArray(w * h).also { b.getPixels(it, 0, w, x0, y0, w, h) }.map { lum(it) }
        return abs((ls.maxOrNull() ?: 0) - (ls.minOrNull() ?: 0))
    }
}
