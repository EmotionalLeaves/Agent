package com.emotionalleaves.studioagent.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.media.Look
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The views a painter uses to see a picture fresh, in the order they are usually tried. */
val lookViews = listOf("As it is", "Flipped", "No color", "Squint", "Three values", "Two values", "Thirds")

/**
 * Fresh eyes on a piece: flipped, without color, squinted, cut to a few values, with thirds lines,
 * and the colors it really uses. For a piece in progress, the newest progress photo is used.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookScreen(nav: NavController, id: Long) {
    val dao = G.dao; val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(null)
    val w = works?.firstOrNull { it.id == id }
    if (w == null) { Screen("Fresh eyes", nav) { Loading() }; return }
    val sessions by dao.sessions().collectAsState(emptyList())
    val progress = sessions.filter { it.artworkId == id && it.photoPath.isNotBlank() && File(it.photoPath).exists() }.maxByOrNull { it.start }
    var useProgress by rememberSaveable(id) { mutableStateOf(w.status == "in_progress" || w.imagePath.isBlank()) }
    val path = if (useProgress && progress != null) progress.photoPath else w.imagePath.ifBlank { progress?.photoPath.orEmpty() }
    var view by rememberSaveable { mutableStateOf("As it is") }
    // The photo before this one, to compare in the same view: what changed, and whether it got better.
    val earlier = sessions.filter { it.artworkId == id && it.photoPath.isNotBlank() && it.photoPath != path && File(it.photoPath).exists() }.maxByOrNull { it.start }
    var compare by rememberSaveable(id) { mutableStateOf(false) }
    val before by produceState<Bitmap?>(null, earlier?.photoPath, view, compare) {
        val p = earlier?.photoPath
        value = if (!compare || p == null) null else withContext(Dispatchers.Default) { ImageTools.decode(p, 900)?.let { b -> when (view) { "Flipped" -> Look.flip(b); "No color" -> Look.gray(b); "Squint" -> Look.squint(b); "Three values" -> Look.values(b, 3); "Two values" -> Look.values(b, 2); "Thirds" -> Look.thirds(b); else -> b } } }
    }
    val base by produceState<Bitmap?>(null, path) { value = withContext(Dispatchers.IO) { if (path.isBlank()) null else ImageTools.decode(path, 1200) } }
    val shown by produceState<Bitmap?>(null, base, view) {
        val b = base; value = if (b == null) null else withContext(Dispatchers.Default) {
            when (view) { "Flipped" -> Look.flip(b); "No color" -> Look.gray(b); "Squint" -> Look.squint(b); "Three values" -> Look.values(b, 3); "Two values" -> Look.values(b, 2); "Thirds" -> Look.thirds(b); else -> b }
        }
    }
    val palette by produceState<List<Pair<Int, Double>>>(emptyList(), base) { val b = base; value = if (b == null) emptyList() else withContext(Dispatchers.Default) { Look.palette(b) } }
    val notes by produceState<List<String>>(emptyList(), base) { val b = base; value = if (b == null) emptyList() else withContext(Dispatchers.Default) { Look.valueNotes(b) } }
    var saw by rememberSaveable(id) { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    Screen("Fresh eyes", nav) {
        Intro("look", "The checks painters use to see their own work again: flip it to see it as a stranger would, take the color away to see the values, squint to see the big shapes. Nothing is changed in your picture.")
        if (path.isBlank()) { Note("Add a photo of the piece first (or take a progress photo when you stop a session), and it can be looked at here."); return@Screen }
        if (progress != null && w.imagePath.isNotBlank()) Choice("", listOf("Newest progress photo", "Finished photo"), if (useProgress) "Newest progress photo" else "Finished photo") { useProgress = it.startsWith("Newest") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lookViews.forEach { v -> FilterChip(selected = v == view, onClick = { view = v }, label = { Text(v) }) }
        }
        if (earlier != null) androidx.compose.material3.TextButton(onClick = { compare = !compare }) { Text(if (compare) "Show this one alone" else "Compare with the photo before") }
        val bb = before
        if (compare && bb != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) { val i0 = remember(bb) { bb.asImageBitmap() }; Image(i0, "The photo before, ${view.lowercase()}", Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit); Small("Before" + (earlier?.let { ", " + fmtDate(it.start) } ?: "")) }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) { shown?.let { b -> val i1 = remember(b) { b.asImageBitmap() }; Image(i1, "Now, ${view.lowercase()}", Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit) }; Small("Now") }
        } else shown?.let { b -> val img = remember(b) { b.asImageBitmap() }
            Image(img, "${w.title.ifBlank { "The piece" }}, ${view.lowercase()}", Modifier.fillMaxWidth().heightIn(max = 520.dp), contentScale = ContentScale.Fit)
        } ?: Small("Opening the picture…")
        Small(when (view) {
            "Flipped" -> "Seen backward, mistakes in drawing and balance jump out: a lean, a heavy side, an eye too high."
            "No color" -> "Only the values. If it still reads clearly, the color is free to do anything."
            "Squint" -> "The big shapes only. Is there one place the eye goes first?"
            "Three values" -> "Dark, middle, light, split where your picture divides most evenly. Strong pictures usually have a simple, clear plan here."
            "Two values" -> "Just dark and light. The shape this makes is what people see from across a room."
            "Thirds" -> "Where the lines cross is where the eye tends to rest. A center of interest near one often feels settled; dead center can feel still, which may be what you want."
            else -> "Tap a view above to look again."
        })
        if (notes.isNotEmpty()) { Section("What the values say"); notes.forEach { Text(it) } }
        // Three questions to look again with: from a teacher-like helper when it is on, otherwise from what the picture shows.
        if (notes.isNotEmpty()) {
            val asked by produceState<List<String>>(emptyList(), notes, path) {
                value = Look.questions(notes)
                if (G.ai.ready) try {
                    val measured = notes.joinToString(" ") + " Main colors: " + palette.take(4).joinToString(", ") { Look.name(it.first) + " " + (it.second * 100).toInt() + "%" } + "."
                    G.ai.teacherQuestions(w.title, w.medium, measured, w.notes).takeIf { it.size >= 2 }?.let { value = it }
                } catch (e: Exception) { }
            }
            Section("Questions to look again with")
            asked.forEach { Text("• $it") }
        }
        if (palette.isNotEmpty()) {
            Section("The colors it uses")
            palette.forEach { (c, share) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Color(c)))
                    Text(Look.name(c) + ", about " + (share * 100).toInt().coerceAtLeast(1) + "%")
                }
            }
            Small("A picture with one color clearly in charge, and a little of its opposite, tends to hold together.")
        }
        Section("What you noticed")
        Field("One thing to change next time", saw, { saw = it }, lines = 2, mic = true)
        if (saw.isNotBlank()) TextButton(onClick = {
            val text = saw.trim()
            scope.io({ status = it }) {
                val now = dao.artworksNow().firstOrNull { it.id == id } ?: return@io
                // Kept in the notes, and shown as the next step when a session on this piece starts.
                dao.put(now.copy(notes = listOf(now.notes, "Fresh eyes, " + java.time.LocalDate.now() + ": " + text).filter { it.isNotBlank() }.joinToString("\n"), nextStep = text))
                status = "Kept with the piece, and it is the next step for the next session."; saw = ""
            }
        }) { Text("Keep it for the next session") }
        if (status.isNotBlank()) Note(status)
    }
}
