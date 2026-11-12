package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Idea
import com.emotionalleaves.studioagent.data.Series
import com.emotionalleaves.studioagent.data.Ledger
import com.emotionalleaves.studioagent.domain.Readiness
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.Papers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

val workStatuses = listOf("idea", "in_progress", "finished", "documented", "available", "pending", "shown", "sold")

fun newWork(): Artwork = Artwork(
    status = "in_progress", year = LocalDate.now().year, createdAt = now(),
    format = when (st.routes.firstOrNull()) { "physical" -> "physical"; "screen" -> "screen"; else -> "print" }
)

@Composable
fun Num(label: String, value: Double, onValue: (Double) -> Unit) {
    var t by remember { mutableStateOf(if (value == 0.0) "" else Texts.dim(value)) }
    Field(label, t, { t = it; onValue(it.toDoubleOrNull() ?: 0.0) }, number = true)
}

@Composable
fun Whole(label: String, value: Int, onValue: (Int) -> Unit) {
    var t by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
    Field(label, t, { t = it; onValue(it.trim().toIntOrNull() ?: 0) }, number = true)
}

@Composable
fun Works(nav: NavController) {
    val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(emptyList())
    val series by dao.series().collectAsState(emptyList())
    var tab by rememberSaveable { mutableStateOf("works") }
    val newSeries = rememberSaveable { mutableStateOf("") }
    val sessions by dao.sessions().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    val search = rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf("all") }
    val ctx = LocalContext.current
    var importStatus by remember { mutableStateOf("") }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            importStatus = "Bringing in ${uris.size} files..."
            scope.io({ importStatus = it }) {
                var ok = 0; val problems = mutableListOf<String>()
                uris.forEachIndexed { i, uri ->
                    val name = try {
                        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } catch (e: Exception) { null } ?: "Untitled ${i + 1}"
                    try { Engine.importFile(ctx, uri, name); ok++ } catch (e: Exception) { problems += e.message.orEmpty() }
                    importStatus = "Brought in $ok of ${uris.size}..."
                }
                importStatus = "Done. $ok pieces added, each with a title from its file name and a size, edition and price filled in from safe defaults. Open any piece to change them." +
                    (if (problems.isNotEmpty()) " Skipped: " + problems.joinToString("; ") else "")
            }
        }
    }
    Screen("Studio", nav, back = false) {
        Intro("studio", "Every piece you make lives here, with its image and details. Works are single pieces; Series are groups that belong together. Already have finished files? Bring in files picks several at once and each becomes a piece.")
        Choice("", listOf("works", "series", "ideas"), tab) { tab = it }
        if (tab == "ideas") IdeasBody(nav)
        else if (tab == "works") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { scope.io { val id = dao.put(newWork()); withContext(Dispatchers.Main) { nav.navigate("work/$id") } } }, modifier = Modifier.weight(1f)) { Text("Add a piece") }
                OutlinedButton(onClick = { many.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Bring in files") }
            }
            Status(importStatus)
            if (works.isEmpty()) Empty("No pieces yet. Start one, or bring in work you have already made.")
            if (works.size > 5) {
                Field("Search title, tag, medium or series", search)
                Choice("", listOf("all") + workStatuses.filter { x -> works.any { it.status == x } }, show, names = { if (it == "all") "All" else statusWords(it) }) { show = it }
            }
            val q = search.value.trim().lowercase()
            works.filter { w ->
                (show == "all" || w.status == show) && (q.isBlank() || listOf(w.title, w.tags, w.medium, w.status, series.firstOrNull { it.id == w.seriesId }?.name.orEmpty()).any { it.lowercase().contains(q) })
            }.chunked(2).forEach { pair ->
                // Hung like a wall: picture, then a small label underneath with title, year and size.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { w -> Box(Modifier.weight(1f)) { WorkTile(w, series.firstOrNull { it.id == w.seriesId }?.name.orEmpty(), tick >= 0 && st.focusWork == w.id) { nav.navigate("work/${w.id}") } } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (works.size > 1) TextButton(onClick = {
                scope.io({ importStatus = it }) {
                    val f = File(ImageTools.exportDir(ctx), "StudioAgent_inventory.csv")
                    f.writeText(Papers.inventoryCsv(works, series.associate { it.id to it.name }))
                    withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "text/csv") }
                }
            }) { Text("Export my inventory as a spreadsheet file") }

        } else {
            Note("A series is a group of works that belong together. Most calls and every solo show want one. Ten documented works in one series opens Level 1.")
            series.forEach { s -> key(s.id) {
                val done = works.count { it.seriesId == s.id && Engine.documented(it) }
                var notes by remember(s.id) { mutableStateOf(s.notes) }
                var name by remember(s.id) { mutableStateOf(s.name) }
                Panel {
                    Field("Series name", name, { name = it; if (it.isNotBlank()) scope.io { dao.put(s.copy(name = it.trim(), notes = notes)) } })
                    LinearProgressIndicator(progress = { (done.toFloat() / s.targetCount.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Note("$done of ${s.targetCount} documented, ${works.count { it.seriesId == s.id }} started")
                    Small(Engine.forecast(s.id, s.targetCount, works, sessions))
                    Expand("Is this series ready to show?") {
                        val ready = works.filter { it.seriesId == s.id && Engine.documented(it) }
                        val hasStatement = docs.any { it.type == "statement" && it.seriesId == s.id && it.approved && it.text.isNotBlank() }
                        val checks = Readiness.series(ready, s.targetCount, hasStatement)
                        checks.forEach { (label, ok) -> Check(ok, label) }
                        Small(if (checks.all { it.second }) "Every check passes. It is ready; there is nothing left to perfect." else "When every line is ticked, it is ready. Nothing else is needed.")
                    }
                    Field("What ties these works together? (used for the series statement)", notes, { notes = it; scope.io { dao.put(s.copy(notes = it, name = name.trim().ifBlank { s.name })) } }, lines = 2, mic = true)
                    Whole("How many works you are aiming for", s.targetCount) { n -> if (n > 0) scope.io { dao.put(s.copy(targetCount = n, notes = notes, name = name.trim().ifBlank { s.name })) } }
                    DangerButton(onClick = { val members = works.filter { it.seriesId == s.id }; scope.remove("the series", { members.forEach { dao.put(it.copy(seriesId = 0)) }; dao.del(s) }, { dao.put(s); members.forEach { dao.put(it) } }) }) { Text("Delete this series (its works are kept)") }
                }
            } }
            Field("New series name", newSeries)
            Button(enabled = newSeries.value.isNotBlank(), onClick = { val n = newSeries.value.trim(); newSeries.value = ""; scope.io { dao.put(Series(name = n, createdAt = now())) } }) { Text("Add series") }
        }
    }
}

@Composable
fun WorkDetail(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(emptyList())
    val series by dao.series().collectAsState(emptyList())
    val loaded = works.firstOrNull { it.id == id }
    if (loaded == null) { Screen("Piece", nav) { Loading() }; return }
    var w by remember(id) { mutableStateOf(loaded) }
    var status by remember { mutableStateOf("") }
    fun save(x: Artwork) { w = x; scope.io { dao.put(x); markSaved() } }
    val newStep = rememberSaveable { mutableStateOf("") }

    val dir = ImageTools.mediaDir(ctx)
    val orig = File(dir, "work_${id}_orig.jpg")
    val main = File(dir, "work_${id}_main.jpg")

    /** A new photo or file: keep the original untouched and make a working copy for screens and juries. */
    fun attached(photo: Boolean) {
        status = "Preparing the image..."
        scope.io({ status = it }) {
            val (pw, ph) = ImageTools.size(orig.absolutePath)
            var b: Bitmap = ImageTools.decode(orig.absolutePath, 2400) ?: throw Exception("That file could not be read as an image.")
            if (photo) b = ImageTools.autoFix(b)
            ImageTools.saveJpeg(b, main)
            val next = w.copy(originalPath = orig.absolutePath, imagePath = main.absolutePath, pixelW = pw, pixelH = ph)
            w = next; dao.put(next)
            status = if (photo) "Photo added and colour-corrected. Straighten it next if the edges are not square." else "File imported at $pw x $ph px."
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) attached(true) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.io({ status = it }) {
            if (!ImageTools.copyUri(ctx, uri, orig)) throw Exception("Could not copy that file.")
            withContext(Dispatchers.Main) { attached(false) }
        }
    }

    val pickExtra = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.io({ status = it }) {
            val f = File(dir, "work_${id}_x_${now()}.jpg")
            if (!ImageTools.copyUri(ctx, uri, f)) throw Exception("Could not copy that file.")
            val next = w.copy(extraImages = (w.extraImages.split("|").filter { it.isNotBlank() } + f.absolutePath).joinToString("|"))
            w = next; dao.put(next)
        }
    }

    Screen(w.title.ifBlank { "Untitled" }, nav) {
        // The picture comes first, on a white sheet, the way it will be seen.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(if (w.imagePath.isNotBlank()) 12.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (w.imagePath.isNotBlank()) {
                PathImage(w.imagePath, Modifier.fillMaxWidth().height(300.dp), 1000, description = "Picture of " + w.title.ifBlank { "this piece" })
                // Its wall label, as it would hang in a show.
                Column(Modifier.padding(top = 4.dp)) {
                    Text(w.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                    val facts = listOfNotNull(w.year.takeIf { it > 0 }?.toString(), w.medium.ifBlank { null }, if (w.widthIn > 0 && w.heightIn > 0) Texts.dims(w.widthIn, w.heightIn) else null)
                    if (facts.isNotEmpty()) Small(facts.joinToString(", "))
                }
            }
            else {
                Text("No picture yet", style = MaterialTheme.typography.titleLarge)
                Small("Photograph the work, or bring in the file. Everything else can come later.")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (w.imagePath.isBlank()) {
                    Button(onClick = { try { take.launch(Share.uri(ctx, orig)) } catch (e: Exception) { status = "No camera app found." } }) { Text("Take a photo") }
                    OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Bring in a file") }
                } else {
                    TextButton(onClick = { try { take.launch(Share.uri(ctx, orig)) } catch (e: Exception) { status = "No camera app found." } }) { Text("Take it again") }
                    TextButton(onClick = { pick.launch("image/*") }) { Text("Use another file") }
                    TextButton(onClick = { nav.navigate("crop/$id") }) { Text("Straighten and crop") }
                }
            }
        }
        Field("Title", w.title, { save(w.copy(title = it)) })
        Explain("How do I photograph work well?", "Daylight from the side, no flash. Put the work flat against a wall and hold the phone parallel to it, level with its centre. A sheet of plain grey card in one corner helps the app correct the colour. Then tap Straighten and crop.")
        Status(status)
        if (w.imagePath.isNotBlank() && (w.status == "in_progress" || w.status == "finished"))
            Button(onClick = {
                val (filledWork, filled) = Engine.fillBlanks(w.copy(status = "documented"))
                save(filledWork)
                status = if (filled.isEmpty()) "Documented. It can now go to calls." else "Documented. Filled in for you: " + filled.joinToString(", ") + ". Change any of it whenever you like; reopen the piece to see it in the boxes."
            }, modifier = Modifier.fillMaxWidth()) { Text("Finished and documented") }
        SavedLine()

        if (w.status == "in_progress") {
            var focus by remember { mutableStateOf(st.focusWork == id) }
            if (focus) Small("This is your one piece this week. Start now opens straight onto it.")
            OutlinedButton(onClick = { focus = !focus; st.focusWork = if (focus) id else 0L }) { Text(if (focus) "Stop making this the week's piece" else "Make this the week's piece") }
        }
        if (w.status == "in_progress" || w.status == "idea") {
            H("Small steps")
            Small("Small enough to start without thinking. A session shows only the next one.")
            val steps = Texts.steps(w.steps)
            steps.forEachIndexed { i, (done, text) ->
                val firstOpen = steps.indexOfFirst { !it.first }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The same round marks as everywhere else; the next step gets the dot.
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).toggleable(value = done, role = androidx.compose.ui.semantics.Role.Checkbox) { c -> save(w.copy(steps = Texts.stepsText(steps.mapIndexed { j, p -> if (j == i) c to p.second else p }))) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Mark(done, current = i == firstOpen)
                        Text(text, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                    RemoveButton("step: $text") { save(w.copy(steps = Texts.stepsText(steps.filterIndexed { j, _ -> j != i }))) }
                }
            }
            Field("Add a small step (for example: block in the big shapes)", newStep)
            OutlinedButton(enabled = newStep.value.isNotBlank(), onClick = { save(w.copy(steps = Texts.stepsText(steps + (false to newStep.value.trim())))); newStep.value = "" }) { Text("Add step") }

            H("Finish line")
            Field("This piece is finished when...", w.doneWhen, { save(w.copy(doneWhen = it)) })
            Note("Decide it now, while you are calm. When the piece meets this, it is finished, even if part of you wants one more pass.")
            OutlinedButton(onClick = { save(w.copy(status = "finished")) }) { Text(if (w.doneWhen.isNotBlank()) "It meets my finish line: call it finished" else "Call it finished") }
        }

        H("Where it stands")
        Choice("Status", workStatuses, w.status, names = ::statusWords) { save(w.copy(status = it)) }
        Explain("What do these mean?", "In progress: still making it. Finished: done, not photographed yet. Ready to send or available: has its image and can be sent to calls. Out on a call: sent to a call, waiting. Shown and sold: the app sets these for you.")
        Field("Next step (shown when you start a session)", w.nextStep, { save(w.copy(nextStep = it)) })
        // The rest only matters once a piece is heading out, so it stays folded until then.
        Expand("Size, year, medium, series", startOpen = w.widthIn <= 0 && w.status in setOf("finished", "documented")) {
            Whole("Year", w.year) { save(w.copy(year = it)) }
            Field("Medium (blank uses the default for its format)", w.medium, { save(w.copy(medium = it)) })
            Choice("Format", listOf("print", "physical", "screen", "digital"), w.format) { save(w.copy(format = it)) }
            Explain("Not sure which format?", "Print: a digital file that will be printed and framed. Physical: made by hand. Screen: shown on a monitor. Digital: a file with no plan yet. If unsure, leave it on print.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Dim("Width", w.widthIn) { save(w.copy(widthIn = it)) } }
                Column(Modifier.weight(1f)) { Dim("Height", w.heightIn) { save(w.copy(heightIn = it)) } }
                Column(Modifier.weight(1f)) { Dim("Depth", w.depthIn) { save(w.copy(depthIn = it)) } }
            }
            if (w.format == "print" || w.format == "digital") {
                Panel { Text("Print check", fontWeight = FontWeight.SemiBold); Text(Texts.printVerdict(w.pixelW, w.pixelH, w.widthIn, w.heightIn)) }
                val size = Texts.suggestedPrintSize(w.pixelW, w.pixelH)
                if (size != null && (w.widthIn <= 0 || w.heightIn <= 0)) OutlinedButton(onClick = { save(w.copy(widthIn = size.first, heightIn = size.second)); status = "Size filled in. Reopen this piece to see it in the boxes." }) {
                    Text("I do not know the size: use ${Texts.dim(size.second)} x ${Texts.dim(size.first)} in, the largest sharp print")
                }
            }
            Toggle("Framed", w.framed) { save(w.copy(framed = it)) }
            if (series.isNotEmpty()) Choice("Series", series.map { it.name }, series.firstOrNull { it.id == w.seriesId }?.name.orEmpty()) { n -> series.firstOrNull { it.name == n }?.let { save(w.copy(seriesId = it.id)) } }
            else Note("No series yet. Add one under Studio, Series.")
            Field("Tags (your own labels, comma separated)", w.tags, { save(w.copy(tags = it)) })
        }

        if (w.format == "print") Expand("Getting it printed (step ${w.printStage.coerceIn(0, 5)} of 5 done)") {
            Small("You do not need to know how any of this works. Do the highlighted step; ignore the rest.")
            Readiness.printSteps.forEachIndexed { i, (name, how) ->
                Check(i < w.printStage, name, current = i == w.printStage, bold = i == w.printStage)
                if (i == w.printStage) Text(how)
            }
            if (w.printStage < 5) Button(onClick = { save(w.copy(printStage = w.printStage + 1)) }) { Text("Done: " + Readiness.printSteps[w.printStage.coerceIn(0, 4)].first.lowercase()) }
            else Small("Printed and framed. This piece can hang.")
            if (w.printStage > 0) TextButton(onClick = { save(w.copy(printStage = w.printStage - 1)) }) { Text("Go back a step") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Num("Print cost, " + st.currency, w.printCost) { save(w.copy(printCost = it)) } }
                Column(Modifier.weight(1f)) { Num("Frame cost, " + st.currency, w.frameCost) { save(w.copy(frameCost = it)) } }
            }
            if (w.printCost > 0 || w.frameCost > 0) OutlinedButton(onClick = {
                val title = w.title; val pc = w.printCost; val fc = w.frameCost
                scope.io {
                    if (pc > 0) dao.put(Ledger(kind = "print", amount = -pc, date = now(), note = "Print: $title", artworkId = id))
                    if (fc > 0) dao.put(Ledger(kind = "framing", amount = -fc, date = now(), note = "Frame: $title", artworkId = id))
                    status = "Added to your records (You, To-dos and money). No need to add it again."
                }
            }) { Text("Add these costs to my records") }
        }

        Expand("More pictures of this piece") {
            Note("A detail, the back, the piece on a wall. Kept with the piece and backed up.")
            val extras = w.extraImages.split("|").filter { it.isNotBlank() }
            extras.forEach { path ->
                PathImage(path, Modifier.fillMaxWidth().height(160.dp), 700)
                TextButton(onClick = { save(w.copy(extraImages = extras.filter { it != path }.joinToString("|"))) }) { Text("Remove this picture") }
            }
            OutlinedButton(onClick = { pickExtra.launch("image/*") }) { Text("Add a picture") }
        }

        Expand("Price and edition") {
        Num("Price, " + st.currency, w.price) { save(w.copy(price = it)) }
        if (w.widthIn > 0 && w.heightIn > 0) TextButton(onClick = { save(w.copy(price = Texts.price(w.widthIn, w.heightIn, st.pricePerSqIn))); status = "Price set from your rule. Reopen this piece to see it in the box." }) {
            Text("Use my price rule: ${money(Texts.price(w.widthIn, w.heightIn, st.pricePerSqIn))}")
        }
        Toggle("Not for sale", w.nfs) { save(w.copy(nfs = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Whole("Edition size", w.editionSize) { save(w.copy(editionSize = it)) } }
            Column(Modifier.weight(1f)) { Whole("Prints made", w.editionUsed) { save(w.copy(editionUsed = it)) } }
        }
        Explain("What is an edition, and what number should I pick?", "The number of prints you promise to ever make of this image. Each is numbered, like 3/10. You cannot raise it later. If unsure, 10 is a common, safe choice. Leave it empty for one-off physical work.")
        Explain("I have no idea what to charge", "Nobody does at first. Tap \"Use my price rule\" above and the app sets a consistent price from the size. You can also switch on Not for sale and decide later; shows accept that.")
        Field("Where it is now", w.location, { save(w.copy(location = it)) })
        Field("Notes", w.notes, { save(w.copy(notes = it)) }, lines = 2)
        }

        Expand("Files and papers") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = w.imagePath.isNotBlank(), onClick = {
                status = "Exporting..."
                scope.io({ status = it }) {
                    val out = ImageTools.exportDir(ctx)
                    val base = Texts.fileName(st.lastName, w.title, w.year, Papers.medium(w), w.widthIn, w.heightIn).removeSuffix(".jpg")
                    val jury = File(out, "$base.jpg"); val web = File(out, "${base}_web.jpg")
                    ImageTools.export(w.imagePath, 1920, 5L * 1024 * 1024, jury)
                    ImageTools.export(w.imagePath, 1600, 2L * 1024 * 1024, web)
                    val files = mutableListOf(jury, web)
                    if (w.originalPath.isNotBlank() && File(w.originalPath).exists()) files += File(w.originalPath).copyTo(File(out, "${base}_original.jpg"), overwrite = true)
                    status = "Exported: jury size (1920 px), web size (under 2 MB) and the untouched original."
                    withContext(Dispatchers.Main) { Share.files(ctx, files, "image/jpeg") }
                }
            }) { Text("Export images") }
            OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.certificate(ctx, st, w, (w.editionUsed + 1).coerceAtLeast(1)); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Certificate") }
            OutlinedButton(onClick = { Share.text(ctx, Papers.printOrder(st, listOf(w))) }) { Text("Print order") }
        }
        TextButton(onClick = {
            scope.io { val copy = dao.put(w.copy(id = 0, title = (w.title + " (copy)").trim(), imagePath = "", originalPath = "", detailPath = "", extraImages = "", pixelW = 0, pixelH = 0, status = "in_progress", steps = "", nextStep = "", createdAt = now())); withContext(Dispatchers.Main) { nav.navigate("work/$copy") } }
        }) { Text("Make another like this (same details, new image)") }
        DangerButton(onClick = { scope.remove(w.title.ifBlank { "the piece" }, { dao.del(w) }, { dao.put(w) }); nav.popBackStack() }) { Text("Delete this piece") }
        }
    }
}

@Composable
fun CropScreen(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(emptyList())
    val w = works.firstOrNull { it.id == id }
    if (w == null) { Screen("Straighten", nav) { Loading() }; return }
    val src = w.originalPath.ifBlank { w.imagePath }
    var tried by remember { mutableStateOf(false) }
    val preview by produceState<Bitmap?>(null, src) { value = withContext(Dispatchers.IO) { try { ImageTools.decode(src, 1200) } catch (e: Exception) { null } }; tried = true }
    val pts = remember { mutableStateListOf(Offset(0.06f, 0.06f), Offset(0.94f, 0.06f), Offset(0.94f, 0.94f), Offset(0.06f, 0.94f)) }
    var active by remember { mutableStateOf(-1) }
    var fix by remember { mutableStateOf(w.format == "physical") }
    var status by remember { mutableStateOf("") }
    Screen("Straighten and crop", nav) {
        Small("Drag each dot onto a corner of the artwork. The result is squared up to those corners.")
        val bmp = preview
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            val line = MaterialTheme.colorScheme.primary
            BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp)) {
                val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
                val k = min(bw / bmp.width, bh / bmp.height)
                val dw = bmp.width * k; val dh = bmp.height * k
                val ox = (bw - dw) / 2; val oy = (bh - dh) / 2
                Canvas(
                    Modifier.fillMaxSize().pointerInput(bmp, bw, bh) {
                        detectDragGestures(
                            onDragStart = { p -> active = pts.indices.minByOrNull { i -> (Offset(ox + pts[i].x * dw, oy + pts[i].y * dh) - p).getDistance() } ?: -1 },
                            onDragEnd = { active = -1 }, onDragCancel = { active = -1 },
                            onDrag = { change, drag ->
                                change.consume()
                                val i = active
                                if (i >= 0) { val q = pts[i]; pts[i] = Offset((q.x + drag.x / dw).coerceIn(0f, 1f), (q.y + drag.y / dh).coerceIn(0f, 1f)) }
                            }
                        )
                    }
                ) {
                    drawImage(img, dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
                    for (i in 0..3) {
                        val a = pts[i]; val b = pts[(i + 1) % 4]
                        drawLine(line, Offset(ox + a.x * dw, oy + a.y * dh), Offset(ox + b.x * dw, oy + b.y * dh), strokeWidth = 4f)
                    }
                    pts.forEach { p ->
                        val c = Offset(ox + p.x * dw, oy + p.y * dh)
                        drawCircle(line, 24f, c); drawCircle(Color.White, 9f, c)
                    }
                }
            }
            Toggle("Also neutralise the colour cast (for photos of physical work)", fix) { fix = it }
            Status(status)
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                status = "Working..."
                scope.io({ status = it }) {
                    val big = ImageTools.decode(src, 2400) ?: throw Exception("Could not open the image.")
                    val corners = FloatArray(8) { i -> if (i % 2 == 0) pts[i / 2].x else pts[i / 2].y }
                    val aspect = if (w.widthIn > 0 && w.heightIn > 0) w.widthIn / w.heightIn else 0.0
                    var out = ImageTools.straighten(big, corners, aspect)
                    if (fix) out = ImageTools.autoFix(out)
                    val main = File(ImageTools.mediaDir(ctx), "work_${id}_main.jpg")
                    ImageTools.saveJpeg(out, main)
                    dao.put(w.copy(imagePath = main.absolutePath))
                    withContext(Dispatchers.Main) { nav.popBackStack() }
                }
            }) { Text("Apply") }
            Note("Your original file is kept untouched, so you can redo this any time.")
        } else if (tried) Empty("This picture could not be opened. It may have been moved or deleted. Add it again from the piece's page.", "Back to the piece") { nav.popBackStack() }
        else Note("Loading the image")
    }
}

@Composable
fun Ideas(nav: NavController) {
    Screen("Ideas", nav) { IdeasBody(nav) }
}

/** The pinboard of parked ideas. Lives in Studio, under Ideas. */
@Composable
fun IdeasBody(nav: NavController) {
    val scope = rememberCoroutineScope()
    val ideas by dao.ideas().collectAsState(emptyList())
    val text = rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Field("New idea", text, lines = 2, mic = true)
        if (text.value.isNotBlank()) Button(onClick = { val t = text.value.trim(); text.value = ""; scope.io { dao.put(Idea(text = t, createdAt = now())) } }) { Text("Park it") }
        val open = ideas.filter { !it.done }
        if (open.isEmpty()) Empty("Nothing parked. When an idea keeps circling while you work, put it here and let it go.")
        // A pinboard: each idea a small sheet with a pin, two across.
        open.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { i ->
                    Box(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (i.photoPath.isNotBlank()) PathImage(i.photoPath, Modifier.fillMaxWidth().height(110.dp), 400, ContentScale.Crop)
                            Text(i.text.ifBlank { "(picture)" }, maxLines = 6)
                            Small(fmtDate(i.createdAt))
                            TextButton(onClick = {
                                scope.io {
                                    val id = dao.put(newWork().copy(notes = i.text, nextStep = "Start: " + i.text.take(60)))
                                    dao.put(i.copy(done = true))
                                    withContext(Dispatchers.Main) { nav.navigate("work/$id") }
                                }
                            }, modifier = Modifier.offset(x = (-12).dp)) { Text("Start as a piece") }
                        }
                        Box(Modifier.align(Alignment.TopCenter).size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.secondary))
                        Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp)) { RemoveButton("idea") { scope.remove("the idea", { dao.del(i) }, { dao.put(i) }) } }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}


/** One piece on the wall: the picture (or its initial on a blank sheet) and a wall label. */
@Composable
fun WorkTile(w: Artwork, seriesName: String, focus: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            // The whole picture, never cropped, on a white mat: artists need to see their edges.
            if (w.imagePath.isNotBlank() && File(w.imagePath).exists()) PathImage(w.imagePath, Modifier.fillMaxSize().padding(10.dp), 500, ContentScale.Fit)
            else Text(w.title.trim().take(1).uppercase().ifBlank { "?" }, style = MaterialTheme.typography.displayLarge, color = cs.outlineVariant)
            if (focus) TapeStrip(Modifier.align(Alignment.TopCenter).offset(y = (-6).dp), 2f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(w.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val facts = listOfNotNull(w.year.takeIf { it > 0 }?.toString(), if (w.widthIn > 0 && w.heightIn > 0) Texts.dims(w.widthIn, w.heightIn) else null)
            if (facts.isNotEmpty()) Small(facts.joinToString(", "))
            Text(statusWords(w.status) + (if (seriesName.isNotBlank()) ", $seriesName" else ""), style = MaterialTheme.typography.labelMedium,
                color = if (w.status == "in_progress") cs.primary else cs.onSurfaceVariant, maxLines = 2)
        }
    }
}

fun statusWords(s: String): String = when (s) {
    "in_progress" -> "In progress"; "idea" -> "Idea"; "finished" -> "Finished"; "documented" -> "Ready to send"; "available" -> "Available"
    "pending" -> "Out on a call"; "shown" -> "Shown"; "sold" -> "Sold"; else -> s.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
