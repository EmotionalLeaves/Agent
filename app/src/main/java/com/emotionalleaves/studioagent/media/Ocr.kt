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
 * No internet, no account and no key: the reading model ships inside the app.
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
}
