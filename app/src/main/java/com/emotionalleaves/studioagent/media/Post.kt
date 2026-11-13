package com.emotionalleaves.studioagent.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import kotlin.math.min

/**
 * A picture ready to post: the piece whole on a quiet field, in the tall 4:5 shape most feeds show
 * largest, nothing cropped off the work. Posting finished work regularly is how jurors and venues
 * who look you up find something there.
 */
object Post {
    const val W = 1080
    const val H = 1350

    fun draw(imagePath: String, credit: String = "", outW: Int = W, outH: Int = H): Bitmap? {
        val art = ImageTools.decode(imagePath, 2000) ?: return null
        return draw(art, credit, outW, outH)
    }

    fun draw(art: Bitmap, credit: String, outW: Int = W, outH: Int = H): Bitmap {
        val b = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFF4F3EF.toInt())
        // The work fills most of the frame with an even margin, so a whole piece is never cut off.
        val margin = outW * 0.08f
        val bottom = if (credit.isNotBlank()) outH * 0.07f else 0f
        val boxW = outW - 2 * margin; val boxH = outH - 2 * margin - bottom
        val s = min(boxW / art.width, boxH / art.height)
        val w = art.width * s; val h = art.height * s
        val left = (outW - w) / 2; val top = margin + (boxH - h) / 2
        // A soft shadow, as if it were leaning a little off the wall.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
        c.drawRect(RectF(left + 6, top + 10, left + w + 6, top + h + 10), shadow)
        c.drawBitmap(art, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (credit.isNotBlank()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B6A66.toInt(); textSize = outW * 0.024f; textAlign = Paint.Align.CENTER }
            c.drawText(credit.take(70), outW / 2f, outH - margin * 0.6f, p)
        }
        return b
    }

    /**
     * The making of a piece in one picture: up to nine progress photos in order, in a grid on the same quiet field,
     * each whole (never cropped), with the finished piece last when there is one.
     */
    fun process(arts: List<Bitmap>, credit: String, outW: Int = W, outH: Int = H): Bitmap {
        val b = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(0xFFF4F3EF.toInt())
        val list = if (arts.size > 9) (0 until 9).map { arts[it * (arts.size - 1) / 8] } else arts
        val cols = when { list.size <= 1 -> 1; list.size <= 4 -> 2; else -> 3 }
        val rows = (list.size + cols - 1) / cols
        val margin = outW * 0.05f; val gap = outW * 0.025f
        val bottom = if (credit.isNotBlank()) outH * 0.06f else 0f
        val cellW = (outW - 2 * margin - gap * (cols - 1)) / cols
        val cellH = (outH - 2 * margin - bottom - gap * (rows - 1)) / rows
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        list.forEachIndexed { i, art ->
            val col = i % cols; val row = i / cols
            val x0 = margin + col * (cellW + gap); val y0 = margin + row * (cellH + gap)
            val s = min(cellW / art.width, cellH / art.height)
            val w = art.width * s; val h = art.height * s
            val l = x0 + (cellW - w) / 2; val t = y0 + (cellH - h) / 2
            c.drawBitmap(art, null, RectF(l, t, l + w, t + h), paint)
        }
        if (credit.isNotBlank()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6B6A66.toInt(); textSize = outW * 0.024f; textAlign = Paint.Align.CENTER }
            c.drawText(credit.take(70), outW / 2f, outH - margin * 0.7f, p)
        }
        return b
    }

    fun render(imagePath: String, credit: String, dest: File): File? {
        val b = draw(imagePath, credit) ?: return null
        ImageTools.saveJpeg(b, dest, 92)
        return dest
    }

    /**
     * Words to go with it: the title and year, what it is, one line of your own from the notes if there is one,
     * and a few plain tags from the medium and your city. Short, so it reads on a phone.
     */
    fun caption(title: String, year: Int, medium: String, size: String, note: String, city: String, available: Boolean, inProgress: Boolean = false): String {
        val head = (if (inProgress) "In progress: " else "") + title.ifBlank { "Untitled" } + (if (year > 0 && !inProgress) " (" + year + ")" else "")
        val what = listOf(medium, size).filter { it.isNotBlank() }.joinToString(", ")
        val line = note.split(Regex("(?<=[.!?])\\s+")).firstOrNull { it.length in 12..220 }?.trim().orEmpty()
        val tags = tags(medium, city)
        return listOf(head, what, line, if (available && !inProgress) "Available. Message me if you would like to know more." else "", tags)
            .filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun tags(medium: String, city: String): String {
        val m = medium.lowercase()
        val kinds = listOf("oil" to "oilpainting", "acrylic" to "acrylicpainting", "watercolor" to "watercolor", "gouache" to "gouache", "charcoal" to "charcoaldrawing",
            "graphite" to "drawing", "pencil" to "drawing", "ink" to "inkdrawing", "pastel" to "pastel", "digital" to "digitalart", "photo" to "photography",
            "print" to "printmaking", "linocut" to "linocut", "screen" to "screenprint", "collage" to "collage", "clay" to "ceramics", "ceramic" to "ceramics",
            "textile" to "textileart", "fiber" to "fiberart", "wood" to "woodwork", "sculpture" to "sculpture")
            .filter { m.contains(it.first) }.map { it.second }.distinct().take(3)
        val place = city.substringBefore(",").filter { it.isLetter() }.lowercase()
        val all = kinds + listOfNotNull(if (place.length > 2) place + "artist" else null, if (place.length > 2) place + "art" else null) + listOf("contemporaryart", "artistsoninstagram")
        return all.distinct().take(7).joinToString(" ") { "#$it" }
    }
}
