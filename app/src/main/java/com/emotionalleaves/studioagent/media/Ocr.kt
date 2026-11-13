package com.emotionalleaves.studioagent.media

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads the printed words in a photo (a flyer, a poster, a wall text) on the phone itself.
 * No account and no key. The reading model is part of Google Play services; it is fetched once, ahead of first use, by [warmUp].
 */
object Ocr {
    /** The words in the picture, top to bottom, or "" when none can be read. Never throws. */
    suspend fun read(ctx: Context, path: String): String = try {
        val image = InputImage.fromFilePath(ctx, Uri.fromFile(File(path)))
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspendCancellableCoroutine { cont ->
                client.process(image)
                    .addOnSuccessListener { r -> if (cont.isActive) cont.resume(r.textBlocks.sortedBy { it.boundingBox?.top ?: 0 }.joinToString("\n") { it.text }) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("") }
            }
        } finally { client.close() }
    } catch (e: Throwable) { "" }

    /** Asks Play services to fetch the reader now, so the first flyer photo is read straight away. Quiet if anything is missing. */
    suspend fun warmUp() {
        if (android.os.Build.FINGERPRINT == "robolectric") return
        try {
            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888), 0)
            try { suspendCancellableCoroutine<Unit> { cont -> client.process(image).addOnCompleteListener { if (cont.isActive) cont.resume(Unit) } } } finally { client.close() }
        } catch (e: Throwable) { }
    }
}
