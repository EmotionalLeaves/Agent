package com.emotionalleaves.studioagent.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.io.File
import kotlin.math.min

/**
 * The piece hanging on a plain gallery wall at its real size, framed and matted, with a bench for scale.
 * For deciding a print size, and for showing someone what it will look like before it is printed.
 */
object Mockup {
    /** Wall height in inches shown above the floor, so a 57-inch hang sits a little above the middle, as in a gallery. */
    private const val WALL_IN = 100.0

    fun render(imagePath: String, widthIn: Double, heightIn: Double, framed: Boolean, dest: File, outW: Int = 1600, outH: Int = 1100): File? {
        val b = draw(imagePath, widthIn, heightIn, framed, outW, outH) ?: return null
        ImageTools.saveJpeg(b, dest, 90)
        return dest
    }

    fun draw(imagePath: String, widthIn: Double, heightIn: Double, framed: Boolean, outW: Int = 1600, outH: Int = 1100): Bitmap? {
        if (widthIn <= 0 || heightIn <= 0) return null
        val art = ImageTools.decode(imagePath, 1600) ?: return null
        val b = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val floorY = outH * 0.84f
        val ppi = floorY / WALL_IN
        // Wall: a soft light falling from above; floor: pale wood.
        c.drawRect(0f, 0f, outW.toFloat(), floorY, Paint().apply { shader = LinearGradient(0f, 0f, 0f, floorY, 0xFFF7F6F2.toInt(), 0xFFE6E4DE.toInt(), Shader.TileMode.CLAMP) })
        c.drawRect(0f, floorY, outW.toFloat(), outH.toFloat(), Paint().apply { color = 0xFFC9B79C.toInt() })
        c.drawRect(0f, floorY, outW.toFloat(), floorY + 4f, Paint().apply { color = 0xFFF2F1EC.toInt() })
        // Gallery hang: the center of the work 57 inches above the floor.
        val mat = if (framed) 2.5 else 0.0; val frame = if (framed) 1.0 else 0.0
        val totalW = (widthIn + 2 * (mat + frame)) * ppi; val totalH = (heightIn + 2 * (mat + frame)) * ppi
        val scale = min(1.0, min(outW * 0.9 / totalW, floorY * 0.92 / totalH))
        val fw = (totalW * scale).toFloat(); val fh = (totalH * scale).toFloat()
        val cx = outW / 2f
        val cy = (floorY - 57 * ppi * scale).toFloat().coerceIn(fh / 2 + 20f, floorY - fh / 2 - 20f)
        val outer = RectF(cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2)
        // A soft shadow down and to the right, as from a ceiling light.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000; maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
        c.drawRect(RectF(outer.left + 8f, outer.top + 14f, outer.right + 8f, outer.bottom + 18f), shadow)
        val k = (ppi * scale).toFloat()
        var inner = outer
        if (framed) {
            c.drawRect(outer, Paint().apply { color = 0xFF2B2B2B.toInt() })
            inner = RectF(outer.left + (frame * k).toFloat(), outer.top + (frame * k).toFloat(), outer.right - (frame * k).toFloat(), outer.bottom - (frame * k).toFloat())
            c.drawRect(inner, Paint().apply { color = 0xFFFBFAF7.toInt() })
            inner = RectF(inner.left + (mat * k).toFloat(), inner.top + (mat * k).toFloat(), inner.right - (mat * k).toFloat(), inner.bottom - (mat * k).toFloat())
        }
        c.drawBitmap(art, null, inner, Paint(Paint.FILTER_BITMAP_FLAG))
        // A bench 48 inches wide and 18 high, so the size reads at a glance.
        val bw = (48 * k); val bh = (18 * k)
        val bench = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3530.toInt() }
        c.drawRect(RectF(cx - bw / 2, floorY - bh, cx + bw / 2, floorY - bh + 2 * k), bench)
        c.drawRect(RectF(cx - bw / 2 + 3 * k, floorY - bh, cx - bw / 2 + 4.5f * k, floorY), bench)
        c.drawRect(RectF(cx + bw / 2 - 4.5f * k, floorY - bh, cx + bw / 2 - 3 * k, floorY), bench)
        return b
    }
}
