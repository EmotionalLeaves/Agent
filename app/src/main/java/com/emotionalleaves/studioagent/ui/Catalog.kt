package com.emotionalleaves.studioagent.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.LaunchedEffect
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
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.domain.Planner
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
    Field(label, t, { t = it; onValue(Texts.number(it) ?: 0.0) }, number = true)
}

@Composable
fun Whole(label: String, value: Int, onValue: (Int) -> Unit) {
    var t by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
    Field(label, t, { t = it; onValue(Texts.number(it)?.toInt() ?: 0) }, number = true)
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
            importStatus = "Bringing in ${Texts.count(uris.size, "file")}…"
            scope.io({ importStatus = it }) {
                var ok = 0; val problems = mutableListOf<String>()
                uris.forEachIndexed { i, uri ->
                    val name = try {
                        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } catch (e: Exception) { null } ?: "Untitled ${i + 1}"
                    try { Engine.importFile(ctx, uri, name); ok++ } catch (e: Exception) { problems += "$name (" + plainError(e) + ")" }
                    importStatus = "Brought in $ok of ${uris.size}…"
                }
                importStatus = "Done. $ok " + (if (ok == 1) "piece" else "pieces") + " added, each with a title from its file name and a size, edition and price filled in from safe defaults. Open any piece to change them." +
                    (if (problems.isNotEmpty()) " Skipped: " + problems.joinToString("; ") else "")
            }
        }
    }
    Screen("Studio", nav, back = false) {
        Intro("studio", "Every piece you make lives here, with its image and details. Works are single pieces; Series are groups that belong together. Already have finished files? Add from my files picks several at once and each becomes a piece.")
        Choice("", listOf("works", "series", "ideas"), tab) { tab = it }
        if (tab == "ideas") IdeasBody(nav)
        else if (tab == "works") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // One tap makes one piece, however fast the second tap comes.
                var adding by remember { mutableStateOf(false) }
                Button(enabled = !adding, onClick = { adding = true; scope.io({ adding = false }) { val id = dao.put(newWork()); withContext(Dispatchers.Main) { adding = false; nav.navigate("work/$id") } } }, modifier = Modifier.weight(1f)) { Text("Add a piece") }
                OutlinedButton(onClick = { many.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("From my files", maxLines = 1) }
            }
            val unphotographed = works.count { it.imagePath.isBlank() && it.status in setOf("finished", "documented", "available") && it.format != "digital" }
            if (unphotographed >= 2) TextButton(onClick = { nav.navigate("photoshoot") }) { Text("Photograph ${Texts.count(unphotographed, "finished piece")} in one go") }
            Status(importStatus)
            // Where the work is right now: pieces out at shows, by place, so nothing is forgotten on a wall.
            val out = works.filter { it.status in setOf("shown", "pending") && it.location.isNotBlank() && it.location != "Studio" }.groupBy { it.location }
            if (out.isNotEmpty()) Panel {
                Text("Out of the studio", fontWeight = FontWeight.SemiBold)
                out.forEach { (place, list) -> Small("$place: " + list.joinToString(", ") { it.title.ifBlank { "Untitled" } }) }
            }
            // Finished pieces with pictures are one tap from going out: all of them at once.
            val almost = works.filter { it.status == "finished" && it.imagePath.isNotBlank() }
            if (almost.isNotEmpty()) Panel {
                Text(Texts.count(almost.size, "finished piece") + " " + (if (almost.size == 1) "has its" else "have their") + " picture" + (if (almost.size == 1) "" else "s") + " but " + (if (almost.size == 1) "is" else "are") + " not marked Ready to send yet.")
                OutlinedButton(onClick = { scope.io { almost.forEach { w -> dao.put(Engine.fillBlanks(w.copy(status = "documented")).first) }; importStatus = "Marked ${Texts.count(almost.size, "piece")} Ready to send. Empty details were filled in; open any piece to check them." } }) {
                    Text(if (almost.size == 1) "Mark it Ready to send" else "Mark all ${almost.size} Ready to send")
                }
            }
            if (works.isEmpty()) Empty("No pieces yet. Start one, or bring in work you have already made.")
            if (works.size > 5) {
                Field("Search title, tag, medium or series", search)
                Choice("", listOf("all") + workStatuses.filter { x -> works.any { it.status == x } }, show, names = { if (it == "all") "All" else statusWords(it) }) { show = it }
            }
            val q = search.value.trim().lowercase()
            // Long walls are drawn 40 at a time, so Studio opens quickly however much work there is.
            var limit by rememberSaveable { mutableStateOf(40) }
            // In the order you need: newest first, by title, by series, or what is ready to send first.
            var sortBy by rememberSaveable { mutableStateOf("newest") }
            if (works.size > 5) Choice("", listOf("newest", "title", "series", "ready"), sortBy, names = { mapOf("newest" to "Newest", "title" to "A to Z", "series" to "By series", "ready" to "Ready first")[it] ?: it }) { sortBy = it }
            val shownWorks = works.filter { w ->
                (show == "all" || w.status == show) && (q.isBlank() || listOf(w.title, w.tags, w.medium, w.status, series.firstOrNull { it.id == w.seriesId }?.name.orEmpty()).any { it.lowercase().contains(q) })
            }.let { list -> when (sortBy) {
                "title" -> list.sortedBy { it.title.lowercase() }
                "series" -> list.sortedWith(compareBy({ series.firstOrNull { s -> s.id == it.seriesId }?.name?.lowercase() ?: "~" }, { -it.createdAt }))
                "ready" -> list.sortedWith(compareBy({ if (Engine.available(it)) 0 else if (it.status == "in_progress") 1 else 2 }, { -it.createdAt }))
                else -> list
            } }
            if (works.isNotEmpty() && shownWorks.isEmpty()) Empty("Nothing matches that.", "Show everything") { search.value = ""; show = "all" }
            // A few pieces hang large; a growing body of work hangs three across, so more of it is seen at once.
            val across = if (shownWorks.size > 6) 3 else 2
            shownWorks.take(limit).chunked(across).forEach { row ->
                // Hung like a wall: picture, then a small label underneath with title, year and size.
                Row(horizontalArrangement = Arrangement.spacedBy(if (across == 3) 10.dp else 14.dp)) {
                    row.forEach { w -> Box(Modifier.weight(1f)) { WorkTile(w, series.firstOrNull { it.id == w.seriesId }?.name.orEmpty(), tick >= 0 && st.focusWork == w.id, compact = across == 3) { nav.navigate("work/${w.id}") } } }
                    repeat(across - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (shownWorks.size > limit) OutlinedButton(onClick = { limit += 40 }, modifier = Modifier.fillMaxWidth()) { Text("Show ${minOf(40, shownWorks.size - limit)} more") }
            if (works.size > 1) TextButton(onClick = {
                scope.io({ importStatus = it }) {
                    val f = File(ImageTools.exportDir(ctx), com.emotionalleaves.studioagent.work.Papers.docName(st, "Inventory", "csv"))
                    f.writeText(Papers.inventoryCsv(works, series.associate { it.id to it.name }))
                    withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "text/csv") }
                }
            }) { Text("Export my inventory as a spreadsheet file") }
            // The full record, with pictures and each piece's history of shows and sales.
            if (works.any { it.imagePath.isNotBlank() }) TextButton(onClick = {
                importStatus = "Making your catalogue…"
                scope.io({ importStatus = it }) {
                    val f = Papers.catalogue(ctx, st, works.filter { it.status != "idea" }.sortedWith(compareBy({ -it.year }, { it.title })), Engine.workHistory())
                    importStatus = ""
                    withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
                }
            }) { Text("Make my catalogue of works (PDF)") }
            if (works.any { it.price > 0 && it.status != "sold" }) TextButton(onClick = { scope.io({ importStatus = it }) {
                val f = Papers.valueList(ctx, st, works.sortedWith(compareBy({ it.location }, { it.title })))
                withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
            } }) { Text("For insurance: everything and its value (PDF)") }
            // Rarely needed at the start, so it waits at the bottom with the other occasional things.
            TextButton(onClick = { nav.navigate("commission") }) { Text("Someone wants a piece made for them") }

        } else {
            Note("A series is a group of works that belong together. Most calls and every solo show want one. Ten documented works in one series opens Level 1.")
            series.forEach { s -> key(s.id) {
                val done = works.count { it.seriesId == s.id && Engine.documented(it) }
                var notes by remember(s.id) { mutableStateOf(s.notes) }
                var name by remember(s.id) { mutableStateOf(s.name) }
                Panel {
                    Text(name.ifBlank { "Untitled series" }, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (done.toFloat() / s.targetCount.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Note("$done of ${s.targetCount} documented, ${works.count { it.seriesId == s.id }} started")
                    Small(Engine.forecast(s.id, s.targetCount, works, sessions))
                    // The series hung together at real sizes: does it hold as a group, and how much wall it needs.
                    val hung = works.filter { it.seriesId == s.id && it.imagePath.isNotBlank() && it.widthIn > 0 && it.heightIn > 0 }
                    if (hung.size > 1) {
                        var wallPic by remember(s.id) { mutableStateOf("") }
                        TextButton(onClick = { scope.io { wallPic = com.emotionalleaves.studioagent.media.Mockup.series(hung.map { Triple(it.imagePath, it.widthIn, it.heightIn) }, hung.any { it.framed || it.format == "print" },
                            File(ImageTools.exportDir(ctx), Papers.docName(st, "Series_wall_" + s.name.filter { it.isLetterOrDigit() }.take(20), "jpg")))?.absolutePath.orEmpty() } }) { Text("See the series hung together") }
                        if (wallPic.isNotBlank()) {
                            PathImage(wallPic, Modifier.fillMaxWidth().height(170.dp), 1400, description = "The series on one wall")
                            val inches = hung.sumOf { it.widthIn + 7 } + 6 * (hung.size - 1)
                            Small("About ${"%.0f".format(inches / 12)} feet of wall, framed, hung 6 inches apart.")
                            TextButton(onClick = { Share.files(ctx, listOf(File(wallPic)), "image/jpeg") }) { Text("Share this picture") }
                        }
                    }
                    // The pieces still to make, planned: one thing varied at a time, added as ideas in one tap.
                    val left = (s.targetCount - works.count { it.seriesId == s.id }).coerceIn(0, 8)
                    if (left > 0) Expand("Plan the next ${Texts.count(left, "piece")}") {
                        var plan by remember(s.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                        var thinking by remember { mutableStateOf(false) }
                        Small("A series holds one thing steady and changes one thing at a time. These are starting points: add the ones you would like to make.")
                        if (plan.isEmpty()) TextButton(enabled = !thinking, onClick = {
                            thinking = true
                            scope.io({ thinking = false; plan = Planner.seriesVariations(s.notes.ifBlank { s.name }, left) }) {
                                val mine = works.filter { it.seriesId == s.id }
                                val got = if (G.ai.ready) try { G.ai.seriesPieces(s.name, s.notes, mine.firstOrNull { it.medium.isNotBlank() }?.medium ?: st.mediums,
                                    mine.joinToString("; ") { it.title.ifBlank { "untitled" } + (if (it.notes.isNotBlank()) ": " + it.notes.take(80) else "") }, dao.ideasNow().filter { !it.done }.take(15).joinToString("; ") { it.text }, left) } catch (e: Exception) { emptyList() } else emptyList()
                                plan = got.ifEmpty { Planner.seriesVariations(s.notes.ifBlank { s.name }, left) }; thinking = false
                            }
                        }) { Text(if (thinking) "Planning…" else "Suggest the pieces") }
                        plan.forEach { (t, what) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(t, fontWeight = FontWeight.SemiBold); Small(what) }
                                TextButton(onClick = { plan = plan.filter { it.first != t }; scope.io { dao.put(Artwork(title = t, status = "idea", seriesId = s.id, notes = what, createdAt = now())) } }) { Text("Add") }
                            }
                        }
                        if (plan.size > 1) Button(onClick = { val all = plan; plan = emptyList(); scope.io { all.forEach { (t, what) -> dao.put(Artwork(title = t, status = "idea", seriesId = s.id, notes = what, createdAt = now())) } } }) { Text("Add all ${plan.size} as ideas") }
                        if (plan.isNotEmpty()) TextButton(onClick = { plan = emptyList() }) { Text("Different suggestions") }
                    }
                    // The series as one post: each piece in the feed shape, in order, with the series' own words.
                    val postable = works.filter { it.seriesId == s.id && it.imagePath.isNotBlank() && it.status !in setOf("idea", "in_progress") }
                    if (st.socialOn && postable.size >= 2) TextButton(onClick = { scope.io {
                        val dir = File(ImageTools.exportDir(ctx), "series_post").apply { deleteRecursively(); mkdirs() }
                        com.emotionalleaves.studioagent.media.Post.watermark = st.watermark
                        val files = postable.take(10).mapIndexedNotNull { i, w -> com.emotionalleaves.studioagent.media.Post.render(w.imagePath, st.fullName, File(dir, "${i + 1}_" + w.title.filter { it.isLetterOrDigit() }.take(20) + ".jpg")) }
                        val words = listOf(s.name, s.notes.trim(), postable.take(10).mapIndexed { i, w -> "${i + 1}. " + w.title.ifBlank { "Untitled" } + (if (w.year > 0) " (${w.year})" else "") }.joinToString("\n"),
                            com.emotionalleaves.studioagent.media.Post.tags(postable.first().medium, st.city)).filter { it.isNotBlank() }.joinToString("\n\n")
                        st.lastPosted = System.currentTimeMillis(); st.postedIds = (st.postedIds.split(",").filter { it.isNotBlank() } + "s${s.id}").takeLast(300).joinToString(",")
                        withContext(Dispatchers.Main) { Share.files(ctx, files, "image/jpeg", words) }
                    } }) { Text("Post the series (${minOf(10, postable.size)} pictures)") }
                    // The series as an online show in one file: no hosting, no account; send it or put it in Drive.
                    if (postable.size >= 2) TextButton(onClick = { scope.io {
                        val f = Papers.onlineShow(ctx, st, s.name, s.notes.ifBlank { Engine.pickStatement(150) }, postable)
                        withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "text/html", "${s.name}: new work by ${st.fullName}. Open the attached page in any browser.") }
                    } }) { Text("An online show of this series (one file)") }
                    Expand("Is this series ready to show?") {
                        val ready = works.filter { it.seriesId == s.id && Engine.documented(it) }
                        val hasStatement = docs.any { it.type == "statement" && it.seriesId == s.id && it.approved && it.text.isNotBlank() }
                        val checks = Readiness.series(ready, s.targetCount, hasStatement)
                        checks.forEach { (label, ok) -> Check(ok, label) }
                        Small(if (checks.all { it.second }) "Every check passes. It is ready; there is nothing left to perfect." else "When every line is ticked, it is ready. Nothing else is needed.")
                    }
                    // Renaming and the rest are rare, so they sit together behind one fold.
                    Expand("Change this series") {
                    Field("Series name", name, { name = it; if (it.isNotBlank()) scope.io { dao.put(s.copy(name = it.trim(), notes = notes)) } })
                    Field("What ties these works together? (used for the series statement)", notes, { notes = it; scope.io { dao.put(s.copy(notes = it, name = name.trim().ifBlank { s.name })) } }, lines = 2, mic = true)
                    // A blank box is the hardest part: a first try from the works' own titles and materials, to change into your words.
                    if (notes.isBlank() && G.ai.ready && works.any { it.seriesId == s.id }) TextButton(onClick = { scope.io {
                        val line = G.ai.seriesLine(name, works.filter { it.seriesId == s.id }.joinToString("; ") { w -> listOf(w.title, w.medium, w.year.takeIf { it > 0 }?.toString()).filterNotNull().filter { it.isNotBlank() }.joinToString(", ") }, Engine.pickStatement(150))
                        if (line.isNotBlank()) { notes = line; dao.put(s.copy(notes = line, name = name.trim().ifBlank { s.name })) }
                    } }) { Text("Suggest a first try") }
                    Whole("How many works you are aiming for", s.targetCount) { n -> if (n > 0) scope.io { dao.put(s.copy(targetCount = n, notes = notes, name = name.trim().ifBlank { s.name })) } }
                    DangerButton(onClick = { val members = works.filter { it.seriesId == s.id }; scope.remove("the series", { members.forEach { dao.put(it.copy(seriesId = 0)) }; dao.del(s) }, { dao.put(s); members.forEach { dao.put(it) } }) }) { Text("Delete this series (its works are kept)") }
                    }
                }
            } }
            Field("New series name", newSeries)
            if (newSeries.value.isNotBlank()) Button(onClick = { val n = newSeries.value.trim(); newSeries.value = ""; scope.io { dao.put(Series(name = n, createdAt = now())) } }) { Text("Add this series") }
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
        status = "Preparing the image…"
        scope.io({ status = it }) {
            val (pw, ph) = ImageTools.size(orig.absolutePath)
            var b: Bitmap = ImageTools.decode(orig.absolutePath, 2400) ?: throw Exception("That file could not be read as an image.")
            if (photo) b = ImageTools.autoFix(b)
            ImageTools.saveJpeg(b, main)
            val next = w.copy(originalPath = orig.absolutePath, imagePath = main.absolutePath, pixelW = pw, pixelH = ph)
            w = next; dao.put(next)
            // For a photo of physical work: what a juror would notice, said now while the work is still in front of you.
            val looks = if (photo) ImageTools.quality(main.absolutePath) else emptyList()
            status = (if (photo) "Photo added and color-corrected. Straighten it next if the edges are not square." else "File imported at $pw × $ph px.") +
                (if (looks.isNotEmpty()) " " + looks.joinToString(" ") else "") +
                // Juries usually ask for 1920 px or more on the long side; say so now, not on the day of a deadline.
                (if (maxOf(pw, ph) in 1..1599) " It is small for calls, which often want 1920 px on the long side. The original file or a closer photo will look better to a juror." else "") +
                // A third retake in a day that passes every check: said plainly that this one is good enough.
                run {
                    val k = "${w.id}:${LocalDate.now()}"
                    val n = (st.retakes.split(",").firstOrNull { it.startsWith("$k:") }?.substringAfterLast(":")?.toIntOrNull() ?: 0) + 1
                    st.retakes = (st.retakes.split(",").filter { it.isNotBlank() && it.substringBeforeLast(":").substringAfter(":") == LocalDate.now().toString() && !it.startsWith("$k:") } + "$k:$n").joinToString(",")
                    if (n >= 3 && looks.isEmpty() && maxOf(pw, ph) >= 1600) " It passed every check. This picture is good enough to send; a juror will not see what a fourth would change." else ""
                }
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) attached(true) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.io({ status = it }) {
            if (!ImageTools.copyUri(ctx, uri, orig)) throw Exception("Could not copy that file.")
            withContext(Dispatchers.Main) { attached(false) }
        }
    }

    // Scanning a flat piece: Google's scanner on the phone finds the four edges and squares the picture, so there is nothing to straighten.
    val scanned = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val uri = Scan.uri(r)
        if (uri != null) scope.io({ status = it }) {
            if (!ImageTools.copyUri(ctx, uri, orig)) throw Exception("Could not copy the scan.")
            withContext(Dispatchers.Main) { attached(true) }
        }
    }
    fun scan() = Scan.start(ctx, { scanned.launch(it) }, { status = it })

    val pickExtra = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.io({ status = it }) {
            val f = File(dir, "work_${id}_x_${now()}.jpg")
            if (!ImageTools.copyUri(ctx, uri, f)) throw Exception("Could not copy that file.")
            val next = w.copy(extraImages = (w.extraImages.split("|").filter { it.isNotBlank() } + f.absolutePath).joinToString("|"))
            w = next; dao.put(next)
        }
    }

    Screen(w.title.ifBlank { "Untitled" }, nav) {
        if (w.clientId > 0 || w.tags.split(",").any { it.trim() == "commission" }) CommissionPanel(nav, w)
        // Waiting a month: three ways to decide it, each fine, each one tap.
        val touched by dao.sessions().collectAsState(emptyList())
        val lastTouch = touched.filter { it.artworkId == w.id }.maxOfOrNull { it.start } ?: w.createdAt
        if (w.status == "in_progress" && w.createdAt in 1 until now() - 30 * 86_400_000L && lastTouch < now() - 30 * 86_400_000L && "w${w.id}" !in st.stuckTold.split(",")) Panel(strong = true) {
            Text("This one has waited a month", fontWeight = FontWeight.SemiBold)
            Small("Unfinished pieces weigh on you. Decide it, and it stops weighing. All three are fine.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { st.stuckTold = st.stuckTold + ",w${w.id}"; save(w.copy(nextStep = w.nextStep.ifBlank { "Look at it for five minutes and decide the next mark" })); nav.navigate("session") }) { Text("One small step") }
                TextButton(onClick = { st.stuckTold = st.stuckTold + ",w${w.id}"; save(w.copy(status = "finished")) }) { Text("It is finished") }
                TextButton(onClick = { st.stuckTold = st.stuckTold + ",w${w.id}"; save(w.copy(status = "idea")) }) { Text("Park it for later") }
            }
        }
        // The picture comes first, in a viewing room: lit on a deep wall in the app's color, its wall label beneath.
        val roomF = FloatArray(3); val pc = MaterialTheme.colorScheme.primary
        androidx.core.graphics.ColorUtils.colorToHSL(android.graphics.Color.rgb((pc.red * 255).toInt(), (pc.green * 255).toInt(), (pc.blue * 255).toInt()), roomF)
        val room = hsl(roomF[0], 0.22f, 0.13f)
        if (w.imagePath.isNotBlank()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(hsl(roomF[0], 0.18f, 0.30f), room), radius = 900f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PathImage(w.imagePath, Modifier.fillMaxWidth().height(340.dp), 1200, description = "Picture of " + w.title.ifBlank { "this piece" })
            // Its wall label, as it would hang in a show.
            Column {
                Text(w.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
                val facts = listOfNotNull(w.year.takeIf { it > 0 }?.toString(), w.medium.ifBlank { null }, if (w.widthIn > 0 && w.heightIn > 0) Texts.dims(w.widthIn, w.heightIn) else null)
                if (facts.isNotEmpty()) Text(facts.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f))
            }
        } else Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp))) {
            // No picture yet: the canvas, blocked in with color, waiting.
            ColorField(MaterialTheme.colorScheme.primary, Modifier.matchParentSize(), key = w.id, shift = ((w.id * 47) % 90 - 45).toFloat())
            Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(w.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
                Text("No picture yet. Photograph it, scan it, or bring in the file; everything else can come later.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (w.imagePath.isNotBlank()) { }
            else {
                // A piece started from a parked picture keeps that picture in view while it is being made.
                w.extraImages.split("|").firstOrNull { it.isNotBlank() && File(it).exists() }?.let { ref ->
                    PathImage(ref, Modifier.fillMaxWidth().height(180.dp), 700, description = "Your reference picture")
                    Small("Your reference. The piece's own picture goes here once it is made.")
                }
            }
            if (w.imagePath.isBlank()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { try { take.launch(Share.uri(ctx, orig)) } catch (e: Exception) { status = "No camera app found." } }, modifier = Modifier.fillMaxWidth()) { Text("Take a photo") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scan() }, modifier = Modifier.weight(1f)) { Text("Scan it", maxLines = 1) }
                    OutlinedButton(onClick = { pick.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Choose a file", maxLines = 1) }
                }
                Small("Scan finds the edges of a flat piece (paper, prints) and squares it for you.")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (w.imagePath.isBlank()) { } else {
                    // Fixing the picture is the common need; replacing it is rarer, so it sits behind one button.
                    TextButton(onClick = { nav.navigate("crop/$id") }) { Text("Straighten and crop") }
                    var replace by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { replace = true }) { Text("Replace the picture") }
                        DropdownMenu(expanded = replace, onDismissRequest = { replace = false }) {
                            DropdownMenuItem(text = { Text("Take a new photo") }, onClick = { replace = false; try { take.launch(Share.uri(ctx, orig)) } catch (e: Exception) { status = "No camera app found." } })
                            DropdownMenuItem(text = { Text("Scan it (flat pieces)") }, onClick = { replace = false; scan() })
                            DropdownMenuItem(text = { Text("Choose a file") }, onClick = { replace = false; pick.launch("image/*") })
                        }
                    }
                }
            }
        }
        // Flip it, take the color away, squint: see it as a stranger would.
        if (w.imagePath.isNotBlank() || w.status == "in_progress") TextButton(onClick = { nav.navigate("look/${w.id}") }) { Text("Look at it with fresh eyes") }
        // A series grows piece by piece: the next one starts with this one's size, medium and series already set.
        if (w.status !in setOf("idea")) TextButton(onClick = { scope.io {
            val next = Artwork(title = Texts.nextTitle(w.title), medium = w.medium, support = w.support, widthIn = w.widthIn, heightIn = w.heightIn, depthIn = w.depthIn, framed = w.framed,
                seriesId = w.seriesId, format = w.format, status = "idea", year = java.time.LocalDate.now().year, createdAt = now())
            val nid = dao.put(next)
            withContext(Dispatchers.Main) { nav.navigate("work/$nid") }
        } }) { Text("Start a new piece like this one") }
        // The piece at its real size on a gallery wall, framed, with a bench for scale.
        if (w.imagePath.isNotBlank() && w.widthIn > 0 && w.heightIn > 0) {
            var wall by remember(w.id) { mutableStateOf("") }
            var stamp by remember { mutableStateOf(0) }
            TextButton(onClick = { scope.io({ status = it }) {
                val f = com.emotionalleaves.studioagent.media.Mockup.render(w.imagePath, w.widthIn, w.heightIn, w.framed || w.format == "print", File(ImageTools.exportDir(ctx), Papers.docName(st, "On_the_wall_" + w.title.filter { it.isLetterOrDigit() }.take(24), "jpg")))
                wall = f?.absolutePath.orEmpty(); stamp++
            } }) { Text(if (wall.isBlank()) "See it on a wall at its real size" else "Make it again") }
            if (wall.isNotBlank()) key(stamp) {
                PathImage(wall, Modifier.fillMaxWidth().height(240.dp), 1400, description = "The piece on a gallery wall at ${Texts.dims(w.widthIn, w.heightIn)}")
                Small("At ${Texts.dims(w.widthIn, w.heightIn)}" + (if (w.framed || w.format == "print") ", framed with a mat" else "") + ". The bench is 4 feet wide. Change the size under Size, year, medium to compare.")
                TextButton(onClick = { Share.files(ctx, listOf(File(wall)), "image/jpeg") }) { Text("Share this picture") }
            }
        }
        Field("Title", w.title, { save(w.copy(title = it)) })
        // Naming is hard: five plain ideas from what you have written about the piece, one tap to use one.
        if (G.ai.ready && (w.title.isBlank() || w.title.startsWith("Untitled") || w.title == "New piece")) {
            var ideas by remember(w.id) { mutableStateOf<List<String>>(emptyList()) }
            TextButton(onClick = { scope.io({ status = it }) {
                val sname = series.firstOrNull { it.id == w.seriesId }?.name.orEmpty()
                ideas = G.ai.titles(w.notes + " " + w.tags, w.medium, sname, Engine.pickStatement(150))
            } }) { Text("Suggest titles") }
            if (ideas.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ideas.forEach { t -> androidx.compose.material3.SuggestionChip(onClick = { save(w.copy(title = t)); ideas = emptyList() }, label = { Text(t) }) } }
        }
        Explain("How do I photograph work well?", "Daylight from the side, no flash. Put the work flat against a wall and hold the phone parallel to it, level with its center. A sheet of plain gray card in one corner helps the app correct the color. Then tap Straighten and crop.")
        Status(status)
        if (w.imagePath.isNotBlank() && w.status in setOf("idea", "in_progress", "finished"))
            Button(onClick = {
                val (filledWork, filled) = Engine.fillBlanks(w.copy(status = "documented"))
                save(filledWork)
                status = if (filled.isEmpty()) "Ready to send. It can now go to calls." else "Ready to send. Filled in for you: " + filled.joinToString(", ") + ". Change any of it whenever you like; reopen the piece to see it in the boxes."
            }, modifier = Modifier.fillMaxWidth()) { Text("Mark it Ready to send") }
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
            if (steps.isEmpty()) {
                var thinking by remember { mutableStateOf(false) }
                OutlinedButton(enabled = !thinking, onClick = {
                    thinking = true
                    scope.io({ thinking = false }) {
                        val list = Engine.suggestSteps(w)
                        withContext(Dispatchers.Main) { save(w.copy(steps = Texts.stepsText(list.map { false to it }))); thinking = false }
                    }
                }) { Text(if (thinking) "Thinking of steps…" else "Suggest small steps") }
            }
            Field("Add a small step (for example: block in the big shapes)", newStep)
            if (newStep.value.isNotBlank()) OutlinedButton(onClick = { save(w.copy(steps = Texts.stepsText(steps + (false to newStep.value.trim())))); newStep.value = "" }) { Text("Add this step") }

            H("Finish line")
            Field("This piece is finished when…", w.doneWhen, { save(w.copy(doneWhen = it)) })
            Note("Decide it now, while you are calm. When the piece meets this, it is finished, even if part of you wants one more pass.")
            OutlinedButton(onClick = { save(w.copy(status = "finished")) }) { Text(if (w.doneWhen.isNotBlank()) "It meets my finish line: call it finished" else "Call it finished") }
        }

        H("Where it stands")
        // Four you set; the rest (out on a call, shown, sold) the app sets when they happen.
        val manual = listOf("idea", "in_progress", "finished", "documented")
        if (w.status in manual || w.status == "available") {
            // Choosing Ready to send here does the same as the button above: empty details are filled in for you.
            Choice("Status", manual, if (w.status == "available") "documented" else w.status, names = ::statusWords) {
                if (it == "documented" && w.status != "documented") { val (filled, _) = Engine.fillBlanks(w.copy(status = "documented")); save(filled) } else save(w.copy(status = it))
            }
            if (w.status in setOf("documented", "available") && w.imagePath.isBlank()) Small("It needs its picture before it can go to calls. Add one at the top of this page.")
        }
        else {
            Row { Tag(statusWords(w.status), strong = true) }
            // Which call it is out on, one tap away.
            val appl = remember(w.id, w.status) { mutableStateOf<com.emotionalleaves.studioagent.data.Appl?>(null) }
            LaunchedEffect(w.id, w.status) { appl.value = withContext(Dispatchers.IO) { dao.applsNow().lastOrNull { w.id in Engine.workIds(it) } } }
            appl.value?.let { a -> TextButton(onClick = { nav.navigate("appl/${a.id}") }) { Text("Open the application it is part of") } }
            // After a show the piece comes home; this lets it go out again.
            if (w.status == "shown") OutlinedButton(onClick = { save(w.copy(status = "available", location = "Studio")) }) { Text("It is back with me: send it out again") }
        }
        Explain("What do these mean?", "Idea: not started. In progress: still making it. Finished: done, not photographed yet. Ready to send: has its picture and details and can go to calls. Out on a call, Shown and Sold are set by the app when they happen.")
        // The rest only matters once a piece is heading out, so it stays folded until then.
        // This piece's own to-dos (from Empty your head, a commission, a sale), ticked off here.
        val pieceTasks by dao.events().collectAsState(emptyList())
        val mineOpen = pieceTasks.filter { it.artworkId == w.id && it.type == "task" && !it.done }
        if (mineOpen.isNotEmpty()) Panel {
            Text("To do for this piece", fontWeight = FontWeight.SemiBold)
            mineOpen.forEach { e -> key("t" + e.id) {
                Row(Modifier.fillMaxWidth().toggleable(false, role = androidx.compose.ui.semantics.Role.Checkbox) { scope.undoable("Ticked off: " + e.title, { dao.put(e.copy(done = true)) }, { dao.put(e) }) }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = false, onCheckedChange = null)
                    Column(Modifier.weight(1f)) { Text(e.title); if (e.date > 0) Small(fmtDate(e.date)) }
                }
            } }
        }
        Expand("Size, year, medium, format, series", startOpen = w.widthIn <= 0 && w.status in setOf("finished", "documented")) {
            Whole("Year", w.year) { save(w.copy(year = it)) }
            Field("Medium (blank uses the default for its format)", w.medium, { save(w.copy(medium = it)) })
            Choice("Format", listOf("print", "physical", "screen", "digital"), w.format, names = { mapOf("print" to "To be printed", "physical" to "Made by hand", "screen" to "On a screen", "digital" to "Digital file")[it] ?: it }) { save(w.copy(format = it)) }
            Explain("Not sure which format?", "To be printed: a digital file that will be printed and framed. Made by hand: paint, drawing, sculpture and the like. On a screen: shown on a monitor or projector. Digital file: no plan for showing it yet. If unsure, leave it on To be printed.")
            var sizeVersion by remember { mutableStateOf(0) }
            key(sizeVersion) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Dim("Width", w.widthIn) { save(w.copy(widthIn = it)) } }
                Column(Modifier.weight(1f)) { Dim("Height", w.heightIn) { save(w.copy(heightIn = it)) } }
                Column(Modifier.weight(1f)) { Dim("Depth", w.depthIn) { save(w.copy(depthIn = it)) } }
            } }
            // No size yet, but a squared-up picture of a flat piece: its shape usually matches a standard paper or canvas.
            if (w.widthIn <= 0 && w.format == "physical" && w.pixelW > 0) {
                val guesses = com.emotionalleaves.studioagent.domain.PaperSizes.guess(w.pixelW, w.pixelH)
                if (guesses.isNotEmpty()) {
                    Small("Its shape matches " + (if (guesses.size == 1) "a common size" else "these common sizes") + ". Tap the one it is on to fill in the size:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        guesses.forEach { g -> AssistChip(onClick = { save(w.copy(widthIn = g.first, heightIn = g.second)); sizeVersion++ }, label = {
                            Text(Texts.dims(g.first, g.second) + (com.emotionalleaves.studioagent.domain.PaperSizes.name(g)?.let { " ($it)" } ?: ""))
                        }) }
                    }
                }
            }
            if (w.format == "print" || w.format == "digital") {
                Panel { Text("Print check", fontWeight = FontWeight.SemiBold); Text(Texts.printVerdict(w.pixelW, w.pixelH, w.widthIn, w.heightIn)) }
                val size = Texts.suggestedPrintSize(w.pixelW, w.pixelH)
                if (size != null && (w.widthIn <= 0 || w.heightIn <= 0)) OutlinedButton(onClick = { save(w.copy(widthIn = size.first, heightIn = size.second)); sizeVersion++; status = "Size filled in." }) {
                    Text("I do not know the size: use ${Texts.dim(size.second)} \u00D7 ${Texts.dim(size.first)} in, the largest sharp print")
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
            // Framing without a framer: the standard frame that fits, and the mat opening to ask for.
            if (w.widthIn > 0 && w.heightIn > 0) Small(com.emotionalleaves.studioagent.domain.Frames.advice(w.widthIn, w.heightIn))
            // Not knowing where to get it printed is where most digital work stalls.
            if (w.printStage < 5) TextButton(onClick = { nav.navigate("nearby/labs") }) { Text("Find a print lab or framer near me") }
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
                    status = "Added to your records (You, Records). No need to add it again."
                }
            }) { Text("Add these costs to my records") }
        }

        // Before it leaves the studio: the physical checks for what it is, ticked once, and a label for its back.
        if (w.status in setOf("finished", "documented", "available", "pending", "shown") && w.format != "digital") {
            val tick by st.changed.collectAsState()
            val items = com.emotionalleaves.studioagent.domain.HangReady.items(w.format, w.framed)
            val bits = remember(tick, w.id) { com.emotionalleaves.studioagent.domain.HangReady.bits(st.hangChecks, w.id) }
            val done = items.indices.count { bits and (1 shl it) != 0 }
            Expand("Ready to hang ($done of ${items.size})") {
                items.forEachIndexed { i, label ->
                    Toggle(label, bits and (1 shl i) != 0) { on -> st.hangChecks = com.emotionalleaves.studioagent.domain.HangReady.set(st.hangChecks, w.id, i, on) }
                }
                OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.backLabels(ctx, st, listOf(w)); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Label for the back") }
            }
        }
        // Money on this piece, once any has moved: what it sold for, what went out, and what you kept.
        val pieceLedger by dao.ledger().collectAsState(emptyList())
        val moneyOn = pieceLedger.filter { it.artworkId == w.id }.map { it.kind to it.amount }
        if (moneyOn.any { it.first == "sale" }) Panel {
            Text("Money on this piece", fontWeight = FontWeight.SemiBold)
            com.emotionalleaves.studioagent.domain.PieceMoney.lines(moneyOn).forEach { (label, amt) ->
                Row { Text(label, Modifier.weight(1f)); Text(money(amt)) }
            }
            val kept = com.emotionalleaves.studioagent.domain.PieceMoney.kept(moneyOn)
            Row { Text("You kept", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(money(kept), fontWeight = FontWeight.SemiBold) }
        } else if (moneyOn.isNotEmpty()) {
            // Not sold yet, but money has gone into it: say so, and whether the price still covers it after a gallery's share.
            val spent = -moneyOn.filter { it.second < 0 }.sumOf { it.second }
            if (spent > 0) Small("Spent on this piece so far: ${money(spent)}." + when {
                w.price <= 0 -> ""
                w.price * 0.6 < spent -> " At ${money(w.price)}, a gallery's usual 40 percent would leave less than that. Worth a look at its price."
                else -> " At ${money(w.price)}, it covers that even after a gallery's usual 40 percent."
            })
        }
        Expand("Price, edition and where it is") {
        TextButton(onClick = { nav.navigate("pricing") }) { Text("Prices for all your work") }
        var priceVersion by remember { mutableStateOf(0) }
        // The box is redrawn only when the rule fills it, so typing in it is never interrupted.
        key(priceVersion) { Num("Price, " + st.currency, w.price) { save(w.copy(price = it)) } }
        if (w.widthIn > 0 && w.heightIn > 0) TextButton(onClick = { save(w.copy(price = Texts.price(w.widthIn, w.heightIn, st.pricePerSqIn))); priceVersion++; status = "Price set from your rule." }) {
            Text("Use my price rule: ${money(Texts.price(w.widthIn, w.heightIn, st.pricePerSqIn))}")
        }
        // Collectors and galleries notice when two pieces of the same size cost very different amounts.
        if (w.price > 0 && w.widthIn > 0 && w.heightIn > 0 && !w.nfs) {
            val area = w.widthIn * w.heightIn
            val alike = works.filter { it.id != w.id && it.price > 0 && !it.nfs && it.widthIn > 0 && it.heightIn > 0 && kotlin.math.abs(it.widthIn * it.heightIn - area) / area < 0.2 && it.medium.equals(w.medium, true) }
            if (alike.isNotEmpty()) {
                val typical = alike.map { it.price }.sorted()[alike.size / 2]
                if (kotlin.math.abs(w.price - typical) / typical > 0.25) Small("Your other ${w.medium.ifBlank { "pieces" }.lowercase()} about this size are around ${money(typical)}. Prices that match by size look considered to buyers and galleries.")
            }
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

        // How it was made: the progress photos from its sessions, in order, with the time they took.
        val made by dao.sessions().collectAsState(emptyList())
        val mine = made.filter { it.artworkId == w.id && it.end > it.start }
        val shots = mine.filter { it.photoPath.isNotBlank() && File(it.photoPath).exists() }.sortedBy { it.start }
        if (mine.isNotEmpty()) Expand("How it was made (${Texts.count(mine.size, "session")}, ${"%.1f".format(mine.sumOf { (it.end - it.start) / 3_600_000.0 })} hours)") {
            if (shots.isEmpty()) Small("Take a progress photo when you stop a session and it appears here, in order.")
            shots.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { x -> Column(Modifier.weight(1f)) { PathImage(x.photoPath, Modifier.fillMaxWidth().height(96.dp), 400, ContentScale.Crop); Small(fmtDate(x.start)) } }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } }
            mine.filter { it.note.isNotBlank() }.sortedBy { it.start }.takeLast(5).forEach { Small(fmtDate(it.start) + ": " + it.note) }
            // The making, in one picture to post: progress photos in order, and the finished piece last.
            if (st.socialOn && shots.size >= 2) TextButton(onClick = { scope.io({ status = it }) {
                val bms = (shots.map { it.photoPath } + listOfNotNull(w.imagePath.takeIf { p -> p.isNotBlank() })).mapNotNull { ImageTools.decode(it, 700) }
                val f = File(ImageTools.exportDir(ctx), Papers.docName(st, "Process_" + w.title.filter { it.isLetterOrDigit() }.take(24), "jpg"))
                ImageTools.saveJpeg(com.emotionalleaves.studioagent.media.Post.process(bms, st.fullName), f, 90)
                st.lastPosted = System.currentTimeMillis(); st.postedIds = (st.postedIds.split(",").filter { it.isNotBlank() } + "p${w.id}").takeLast(300).joinToString(",")
                val words = "How " + w.title.ifBlank { "this piece" } + " came together, " + Texts.count(mine.size, "session") + " from first marks to finished.\n\n" + com.emotionalleaves.studioagent.media.Post.tags(w.medium, st.city)
                withContext(Dispatchers.Main) { Share.post(ctx, f, words) }
            } }) { Text("Post how it was made") }
        }
        Expand("Pictures and papers") {
        if (st.socialOn) Toggle("Put a faint © and my name on pictures I post", st.watermark) { st.watermark = it }
        Note("More pictures of this piece: a detail, the back, the piece on a wall. Kept with it and backed up.")
        w.extraImages.split("|").filter { it.isNotBlank() }.let { extras ->
            extras.forEach { path ->
                PathImage(path, Modifier.fillMaxWidth().height(160.dp), 700)
                TextButton(onClick = { save(w.copy(extraImages = extras.filter { it != path }.joinToString("|"))) }) { Text("Remove this picture") }
            }
        }
        OutlinedButton(onClick = { pickExtra.launch("image/*") }) { Text("Add a picture") }
        // A description of the picture: web pages, Instagram and some entry forms ask for one, and screen readers read it.
        if (w.imagePath.isNotBlank()) {
            var alt by remember(w.id) { mutableStateOf(w.altText) }
            Field("Description of the picture (for screen readers and forms)", alt, { alt = it; save(w.copy(altText = it)) }, lines = 3, mic = true)
            if (alt.isBlank()) TextButton(onClick = { scope.io({ status = it }) { val t = Engine.altTextFor(w); withContext(Dispatchers.Main) { alt = t; save(w.copy(altText = t)) } } }) { Text("Write a first one for me") }
        }
        Section("Files to send")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = w.imagePath.isNotBlank(), onClick = {
                status = "Exporting…"
                scope.io({ status = it }) {
                    val out = ImageTools.exportDir(ctx)
                    val base = Texts.fileName(st.lastName, w.title, w.year, Papers.medium(w), w.widthIn, w.heightIn).removeSuffix(".jpg")
                    val jury = File(out, "$base.jpg"); val web = File(out, "${base}_web.jpg")
                    ImageTools.export(w.imagePath, 1920, 5L * 1024 * 1024, jury, st.fullName, Papers.caption(w))
                    ImageTools.export(w.imagePath, 1600, 2L * 1024 * 1024, web, st.fullName, Papers.caption(w))
                    val files = mutableListOf(jury, web)
                    if (w.originalPath.isNotBlank() && File(w.originalPath).exists()) files += File(w.originalPath).copyTo(File(out, "${base}_original.jpg"), overwrite = true)
                    status = "Exported: jury size (1920 px), web size (under 2 MB) and the untouched original."
                    withContext(Dispatchers.Main) { Share.files(ctx, files, "image/jpeg") }
                }
            }) { Text("Export pictures") }
            OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.certificate(ctx, st, w, (w.editionUsed + 1).coerceAtLeast(1)); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Certificate of authenticity") }
            OutlinedButton(onClick = { Share.text(ctx, Papers.printOrder(st, listOf(w))) }) { Text("Ask a print lab for a quote") }
            // Many labs ask for a TIFF: every pixel as it is, with the print size written in.
            if (w.format == "print" && w.imagePath.isNotBlank()) OutlinedButton(onClick = {
                status = "Making the TIFF…"
                scope.io({ status = it }) {
                    val src = w.originalPath.takeIf { it.isNotBlank() && File(it).exists() } ?: w.imagePath
                    val b = try { ImageTools.decode(src, 9000) } catch (e: OutOfMemoryError) { ImageTools.decode(src, 4500) } ?: throw Exception("The picture could not be opened.")
                    val dpi = if (w.widthIn > 0) (maxOf(b.width, b.height) / maxOf(w.widthIn, w.heightIn)).toInt().coerceIn(72, 1200) else 300
                    val f = com.emotionalleaves.studioagent.media.Tiff.write(b, File(ImageTools.exportDir(ctx), Papers.docName(st, w.title.filter { it.isLetterOrDigit() }.take(24).ifBlank { "Piece" }, "tif")), dpi)
                    status = "TIFF ready: ${b.width} × ${b.height} px at $dpi ppi" + (if (dpi < 240) ". Under 240 ppi may look soft at this size; ask the lab." else ".")
                    withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "image/tiff") }
                }
            }) { Text("A TIFF for the print lab") }
            if (st.socialOn) OutlinedButton(enabled = w.imagePath.isNotBlank(), onClick = {
                scope.io({ status = it }) {
                    com.emotionalleaves.studioagent.media.Post.watermark = st.watermark
                    val f = com.emotionalleaves.studioagent.media.Post.render(w.imagePath, st.fullName, File(ImageTools.exportDir(ctx), Papers.docName(st, "Post_" + w.title.filter { it.isLetterOrDigit() }.take(24), "jpg")))
                        ?: throw Exception("The picture of this piece could not be opened.")
                    val words = com.emotionalleaves.studioagent.media.Post.caption(w.title, w.year, Papers.medium(w), if (w.widthIn > 0) Texts.dims(w.widthIn, w.heightIn, w.depthIn) else "", w.notes, st.city,
                        available = !w.nfs && w.status != "sold" && w.status != "idea", inProgress = w.status == "in_progress")
                    st.lastPosted = System.currentTimeMillis(); if (w.id.toString() !in st.postedIds.split(",")) st.postedIds = (st.postedIds.split(",").filter { it.isNotBlank() } + w.id.toString()).takeLast(300).joinToString(",")
                    status = "The words are copied too: if the app shows only the picture, paste them in the caption."
                    withContext(Dispatchers.Main) { Share.post(ctx, f, words) }
                }
            }) { Text("Post it online") }
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
            Toggle("Also neutralize the color cast (for photos of physical work)", fix) { fix = it }
            TextButton(onClick = { pts[0] = Offset(0.06f, 0.06f); pts[1] = Offset(0.94f, 0.06f); pts[2] = Offset(0.94f, 0.94f); pts[3] = Offset(0.06f, 0.94f) }) { Text("Put the dots back to the start") }
            Status(status)
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                status = "Working…"
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
            }) { Text("Save the straightened picture") }
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
        val parkedN = ideas.count { !it.done }
        // The board's front: how many are waiting, on a field in your color, with the newest picture if there is one.
        Banner(if (parkedN == 0) "A place to park ideas" else "Parked, not promised", if (parkedN == 0) "Write it down, let it go, get back to work." else "Look through them when a piece is done and you want the next one.",
            key = 81L, image = ideas.firstOrNull { !it.done && it.photoPath.isNotBlank() }?.photoPath, height = 160, big = if (parkedN > 0) "$parkedN" else null, bigLabel = if (parkedN == 1) "idea" else "ideas")
        Field("New idea", text, lines = 2, mic = true)
        if (text.value.isNotBlank()) Button(onClick = { val t = text.value.trim(); text.value = ""; scope.io { dao.put(Idea(text = t, createdAt = now())) } }) { Text("Park it") }
        // A picture that sparked it (a screenshot, a photo of a wall, a color) parks the same way, with any words typed above.
        val ctx = LocalContext.current
        val pic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) { val t = text.value.trim(); text.value = ""
                scope.io { val f = File(ImageTools.mediaDir(ctx), "idea_${now()}.jpg"); if (ImageTools.copyUri(ctx, uri, f)) dao.put(Idea(text = t, photoPath = f.absolutePath, createdAt = now())) } }
        }
        TextButton(onClick = { pic.launch("image/*") }) { Text("Park a picture") }
        TextButton(onClick = { nav.navigate("dump") }) { Text("Lots on your mind? Empty your head") }
        val allOpen = ideas.filter { !it.done }
        // Ideas parked long ago: look through them now and then, keep the ones that still pull, let the rest go.
        val old = allOpen.filter { it.createdAt in 1 until now() - 120 * 86_400_000L }
        var reviewing by rememberSaveable { mutableStateOf(false) }
        if (old.size >= 3 && !reviewing) TextButton(onClick = { reviewing = true }) { Text("Look through ${Texts.count(old.size, "idea")} parked over four months ago") }
        if (reviewing) {
            Small("Keep the ones that still pull at you; let the rest go. Letting an idea go is not losing it: it was never a promise.")
            old.forEach { i -> Panel {
                Text(i.text.ifBlank { "(picture)" }, maxLines = 4); Small(fmtDate(i.createdAt))
                Row {
                    TextButton(onClick = { scope.io { dao.put(i.copy(createdAt = now())) } }) { Text("Keep") }
                    TextButton(onClick = { scope.io { dao.put(i.copy(done = true)) } }) { Text("Let it go") }
                }
            } }
            TextButton(onClick = { reviewing = false }) { Text("Done looking") }
        }
        val open = if (reviewing) allOpen.filter { it !in old } else allOpen
        if (open.isEmpty() && !reviewing) Empty("Nothing parked. When an idea keeps circling while you work, put it here and let it go.")
        // Too many to choose between: one picked for you, at random, to try or to skip. Choosing is often the hardest part.
        var picked by remember { mutableStateOf<Long?>(null) }
        if (open.size >= 3 && !reviewing) {
            val p = open.firstOrNull { it.id == picked }
            if (p == null) TextButton(onClick = { picked = open.random().id }) { Text("Can't choose? Pick one for me") }
            else Panel(strong = true) {
                Text("This one:", fontWeight = FontWeight.SemiBold)
                Text(p.text.ifBlank { "(a picture)" })
                Row { TextButton(onClick = { picked = open.filter { it.id != p.id }.random().id }) { Text("Another") }; TextButton(onClick = { picked = null }) { Text("Never mind") } }
                Small("Like it? \"Start as a piece\" is on its card below.")
            }
        }
        // A pinboard: each idea a small sheet with a pin, two across.
        open.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { i ->
                    // Each sheet hangs a little crooked, as paper on a board does, tinted a near neighbor of your color.
                    val tilt = ((i.id * 7919) % 5 - 2) * 0.6f
                    val tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + ((i.id * 31) % 4) * 0.08f)
                    Box(Modifier.weight(1f).graphicsLayer { rotationZ = tilt }) {
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).background(tint).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (i.photoPath.isNotBlank()) PathImage(i.photoPath, Modifier.fillMaxWidth().height(110.dp), 400, ContentScale.Crop)
                            Text(i.text.ifBlank { "(picture)" }, maxLines = 6)
                            Small(fmtDate(i.createdAt))
                            TextButton(onClick = {
                                scope.io {
                                    // A working title from the idea's first words; it can change any time.
                                    val working = i.text.trim().split(Regex("\\s+")).take(5).joinToString(" ").trimEnd('.', ',', ';', ':').replaceFirstChar { it.uppercase() }
                                    // The picture that sparked it comes along as a reference image.
                                    val id = dao.put(newWork().copy(title = working.ifBlank { "New piece" }, notes = i.text, nextStep = if (i.text.isNotBlank()) "Start: " + i.text.take(60) else "Look at the reference picture and make a first mark",
                                        extraImages = i.photoPath))
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
fun WorkTile(w: Artwork, seriesName: String, focus: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            // The whole picture, never cropped, on a white mat: artists need to see their edges.
            if (w.imagePath.isNotBlank() && File(w.imagePath).exists()) PathImage(w.imagePath, Modifier.fillMaxSize().padding(10.dp), 500, ContentScale.Fit)
            else {
                // No picture yet: a canvas blocked in with color, its title on it, waiting to be photographed.
                ColorField(cs.primary, Modifier.fillMaxSize(), key = w.id, shift = ((w.id * 47) % 90 - 45).toFloat())
                Text(w.title.ifBlank { "Untitled" }, Modifier.align(Alignment.BottomStart).padding(14.dp), color = androidx.compose.ui.graphics.Color.White,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, maxLines = 3)
            }
            // What state it is in, at a glance, on the picture itself.
            Text(statusWords(w.status), Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(if (w.status == "in_progress") cs.primary else cs.surface.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = if (w.status == "in_progress") cs.onPrimary else cs.onSurface)
            if (focus) TapeStrip(Modifier.align(Alignment.TopCenter).offset(y = (-6).dp), 2f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (w.imagePath.isNotBlank()) Text(w.title.ifBlank { "Untitled" }, style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val facts = listOfNotNull(w.year.takeIf { it > 0 }?.toString(), if (w.widthIn > 0 && w.heightIn > 0) Texts.dims(w.widthIn, w.heightIn) else null)
            if (facts.isNotEmpty() && !compact) Small(facts.joinToString(", "))
            if (seriesName.isNotBlank() && !compact) Text(seriesName, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 2)
        }
    }
}

fun statusWords(s: String): String = when (s) {
    "in_progress" -> "In progress"; "idea" -> "Idea"; "finished" -> "Finished"; "documented" -> "Ready to send"; "available" -> "Available"
    "pending" -> "Out on a call"; "shown" -> "Shown"; "sold" -> "Sold"; else -> s.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
