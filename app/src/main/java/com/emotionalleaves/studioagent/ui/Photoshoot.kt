package com.emotionalleaves.studioagent.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.sys.Share
import java.io.File

/**
 * Photograph every finished piece in one sitting: the next piece without a picture, one tap to the camera,
 * the photo color-corrected and checked, then straight on to the next. A daylight weekend morning's job.
 */
@Composable
fun PhotoshootScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val dao = G.dao
    val works by dao.artworks().collectAsState(emptyList())
    val todo = works.filter { it.imagePath.isBlank() && it.status in setOf("finished", "documented", "available", "in_progress") && it.format != "digital" }.sortedBy { if (it.status == "finished") 0 else 1 }
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    val next = todo.firstOrNull { it.id !in skipped }
    var done by remember { mutableStateOf(0) }
    var said by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(0L) }
    val dir = ImageTools.mediaDir(ctx)
    fun finish(id: Long) = scope.io({ said = it }) {
            val orig = File(dir, "work_${id}_orig.jpg"); val main = File(dir, "work_${id}_main.jpg")
            val (pw, ph) = ImageTools.size(orig.absolutePath)
            val b = ImageTools.decode(orig.absolutePath, 2400) ?: throw Exception("The photo could not be read. Try that one again.")
            ImageTools.saveJpeg(ImageTools.autoFix(b), main)
            dao.artworksNow().firstOrNull { it.id == id }?.let { w -> dao.put(w.copy(originalPath = orig.absolutePath, imagePath = main.absolutePath, pixelW = pw, pixelH = ph)) }
            val looks = ImageTools.quality(main.absolutePath)
            done++; said = if (looks.isEmpty()) "Looks good." else looks.first()
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok && pending > 0) finish(pending) }
    // Flat pieces can be scanned instead: edges found and squared on the phone.
    val scanned = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val uri = Scan.uri(r); val id = pending
        if (uri != null && id > 0) scope.io({ said = it }) {
            if (!ImageTools.copyUri(ctx, uri, File(dir, "work_${id}_orig.jpg"))) throw Exception("Could not copy the scan.")
            finish(id)
        }
    }
    Screen("Photograph your pieces", nav) {
        Intro("photoshoot", "Every piece without a picture, one after another: tap, photograph, and the next one comes up. Do it by a big window on a bright, overcast day, the piece flat on the wall and the phone square to it.")
        if (next == null) {
            Panel(strong = true) { Text(if (done > 0) "Done: ${Texts.count(done, "piece")} photographed." else "Every piece has a picture.", fontWeight = FontWeight.SemiBold); Small("Straighten any that are not square from the piece's page.") }
            return@Screen
        }
        Text("${Texts.count(todo.count { it.id !in skipped }, "piece")} to go" + (if (done > 0) ", $done done" else ""), style = MaterialTheme.typography.titleMedium)
        Panel(strong = true) {
            Text(next.title.ifBlank { "Untitled #${next.id}" }, style = MaterialTheme.typography.headlineSmall)
            Small(listOfNotNull(next.medium.ifBlank { null }, if (next.widthIn > 0) Texts.dims(next.widthIn, next.heightIn) else null, statusWords(next.status)).joinToString(", "))
            Button(onClick = {
                pending = next.id
                val f = File(dir, "work_${next.id}_orig.jpg")
                try { take.launch(Share.uri(ctx, f)) } catch (e: Exception) { said = "No camera app found." }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Photograph it") }
            OutlinedButton(onClick = { pending = next.id; Scan.start(ctx, { scanned.launch(it) }, { said = it }) }, modifier = Modifier.fillMaxWidth()) { Text("Scan it instead (flat pieces)") }
            TextButton(onClick = { skipped = skipped + next.id }) { Text("Skip this one for now") }
        }
        if (said.isNotBlank()) Note(said)
        Small("Fill the frame with the piece, edges just inside. No flash. Turn off any lamp that shines on the surface.")
    }
}
