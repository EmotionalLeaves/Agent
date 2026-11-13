package com.emotionalleaves.studioagent.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Scanning a flat piece with the phone's own scanner (Google Play services, offline): it finds the four edges and
 * squares the picture up. Basic mode only, so no "document" filters change the colors of the work.
 */
object Scan {
    fun start(ctx: Context, onReady: (IntentSenderRequest) -> Unit, onFail: (String) -> Unit) {
        val act = generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }.firstOrNull { it is Activity } as? Activity
        if (act == null) { onFail("The scanner could not open here. Take a photo instead, then straighten it."); return }
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build()
        try {
            GmsDocumentScanning.getClient(opts).getStartScanIntent(act)
                .addOnSuccessListener { sender -> onReady(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { onFail("The scanner is not available on this phone yet (it comes with Google Play services). Take a photo instead, then straighten it.") }
        } catch (e: Exception) { onFail("The scanner could not open. Take a photo instead, then straighten it.") }
    }

    fun uri(r: ActivityResult): Uri? =
        if (r.resultCode == Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.firstOrNull()?.imageUri else null
}
