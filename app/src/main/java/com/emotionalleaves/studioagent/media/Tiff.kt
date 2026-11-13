package com.emotionalleaves.studioagent.media

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An uncompressed 8-bit RGB TIFF, the file many print labs ask for: every pixel exactly as it is, with the print
 * resolution written in so the lab's software opens it at the intended size. Written a row at a time, so a large
 * picture never needs a second copy in memory.
 */
object Tiff {
    /** The 8-byte header and the directory of tags, for a picture [w] x [h] at [dpi]. Exposed for the tests. */
    fun header(w: Int, h: Int, dpi: Int): ByteArray {
        val tags = 13
        val ifdOffset = 8
        val ifdSize = 2 + tags * 12 + 4
        val bitsOffset = ifdOffset + ifdSize               // 3 shorts: 8, 8, 8
        val xResOffset = bitsOffset + 6                     // rational: dpi / 1
        val yResOffset = xResOffset + 8
        val softwareOffset = yResOffset + 8
        val software = "Studio Agent\u0000".toByteArray(Charsets.US_ASCII)
        val dataOffset = softwareOffset + software.size
        val b = ByteBuffer.allocate(dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        b.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(ifdOffset)
        b.putShort(tags.toShort())
        fun tag(id: Int, type: Int, count: Int, value: Int) { b.putShort(id.toShort()).putShort(type.toShort()).putInt(count).putInt(value) }
        fun short(id: Int, value: Int) { b.putShort(id.toShort()).putShort(3).putInt(1).putShort(value.toShort()).putShort(0) }
        tag(256, 4, 1, w)                      // ImageWidth
        tag(257, 4, 1, h)                      // ImageLength
        tag(258, 3, 3, bitsOffset)             // BitsPerSample
        short(259, 1)                          // Compression: none
        short(262, 2)                          // Photometric: RGB
        tag(273, 4, 1, dataOffset)             // StripOffsets
        short(277, 3)                          // SamplesPerPixel
        tag(278, 4, 1, h)                      // RowsPerStrip: one strip
        tag(279, 4, 1, w * h * 3)              // StripByteCounts
        tag(282, 5, 1, xResOffset)             // XResolution
        tag(283, 5, 1, yResOffset)             // YResolution
        short(296, 2)                          // ResolutionUnit: inch
        tag(305, 2, software.size, softwareOffset) // Software
        b.putInt(0)                            // no next directory
        b.putShort(8).putShort(8).putShort(8)
        b.putInt(dpi).putInt(1).putInt(dpi).putInt(1)
        b.put(software)
        return b.array()
    }

    fun write(bmp: Bitmap, dest: File, dpi: Int = 300): File {
        val w = bmp.width; val h = bmp.height
        BufferedOutputStream(FileOutputStream(dest), 1 shl 16).use { out ->
            out.write(header(w, h, dpi))
            val row = IntArray(w); val bytes = ByteArray(w * 3)
            for (y in 0 until h) {
                bmp.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 0 until w) { val c = row[x]; bytes[x * 3] = (c shr 16).toByte(); bytes[x * 3 + 1] = (c shr 8).toByte(); bytes[x * 3 + 2] = c.toByte() }
                out.write(bytes)
            }
        }
        return dest
    }
}
