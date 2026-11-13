package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.domain.Foundations

/** Twelve weeks of foundations: the week you are on, open; the rest, one line each. Ticks happen by themselves after a practice session. */
@Composable
fun FoundationsScreen(nav: NavController) {
    val tick by st.changed.collectAsState()
    val stored = if (tick >= 0) st.foundationsDone else ""
    val done = Foundations.done(stored); val current = Foundations.current(stored)
    Screen("Foundations", nav) {
        Intro("foundations", "Twelve weeks of the basics every exhibiting artist leans on, sized for weeknights: three short exercises a week. A practice session picks the next one by itself and ticks it when you save the session. Doing them badly is the point.")
        Text(if (current == null) "All twelve weeks done. That is real groundwork." else "${done.size} of ${Foundations.weeks.sumOf { it.exercises.size }} exercises done. This week: ${current.skill}.", style = MaterialTheme.typography.bodyLarge)
        if (current != null) Button(onClick = { nav.navigate("session") }) { Text("Practice the next one") }
        StudyAMaster()
        Foundations.weeks.forEach { wk ->
            val all = wk.exercises.all { it.id in done }
            if (wk == current) Panel(strong = true) {
                Text("Week ${wk.n}: ${wk.skill}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(wk.why)
                wk.exercises.forEach { e -> Toggle(e.text + (if (e.text.contains("minute")) "" else " (${e.minutes} min)"), e.id in done) { on -> st.foundationsDone = Foundations.mark(st.foundationsDone, e.id, on) } }
                Small("To check it: open the drawing's photo in Fresh eyes and look at it ${wk.check.lowercase().let { if (it == "as it is") "as it is, next to what you painted from" else "with $it" }}.")
            } else Expand("Week ${wk.n}: ${wk.skill}" + (if (all) " (done)" else "")) {
                Text(wk.why)
                wk.exercises.forEach { e -> Toggle(e.text + (if (e.text.contains("minute")) "" else " (${e.minutes} min)"), e.id in done) { on -> st.foundationsDone = Foundations.mark(st.foundationsDone, e.id, on) } }
            }
        }
    }
}

/**
 * A public-domain work from a museum's open collection, in a medium near yours, to copy a small part of.
 * Copying a detail is how painters have always learned; it is practice, never for showing or selling.
 */
@Composable
fun StudyAMaster() {
    val ctx = androidx.compose.ui.platform.LocalContext.current; val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pieces by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.emotionalleaves.studioagent.net.Museum.Piece>>(emptyList()) }
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<com.emotionalleaves.studioagent.net.Museum.Piece, String>?>(null) }
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    fun next() {
        status = "Looking in the collection…"
        scope.io({ status = it }) {
            val list = pieces.ifEmpty {
                val term = com.emotionalleaves.studioagent.net.Museum.termFor(st.mediums)
                // A narrow medium can have few old works: drawings fill the list out.
                (com.emotionalleaves.studioagent.net.Museum.search(term) + (if (term != "drawing") com.emotionalleaves.studioagent.net.Museum.search("drawing").take(20) else emptyList())).distinctBy { it.id }.shuffled()
            }
            if (list.isEmpty()) { status = "Nothing came back just now. Try again later."; return@io }
            val p = list.first(); pieces = list.drop(1)
            // When the picture cannot be brought into the app, the work still shows, with its page to open in your browser.
            val path = try { com.emotionalleaves.studioagent.net.Museum.download(p, java.io.File(ctx.cacheDir, "museum")).absolutePath } catch (e: Exception) { "" }
            shown = p to path; status = ""
        }
    }
    Panel {
        Text("Study a master", fontWeight = FontWeight.SemiBold)
        Small("A public-domain work in a medium near yours, from the Art Institute of Chicago's open collection. Copy one small part of it, the size of your palm, in 20 minutes: it is how painters have always learned. For practice only, never to show.")
        shown?.let { (p, path) ->
            if (path.isNotBlank()) PathImage(path, androidx.compose.ui.Modifier.fillMaxWidth().heightIn(max = 360.dp), 900, description = p.title + ", " + p.artist)
            Text(p.title, fontWeight = FontWeight.SemiBold); Small(p.credit + (if (p.medium.isNotBlank()) " " + p.medium + "." else ""))
            androidx.compose.material3.TextButton(onClick = { com.emotionalleaves.studioagent.sys.Share.open(ctx, p.pageUrl) }) { Text(if (path.isBlank()) "See it on the museum's site" else "Larger, on the museum's site") }
        }
        if (status.isNotBlank()) Small(status)
        androidx.compose.material3.TextButton(onClick = { next() }) { Text(if (shown == null) "Show me one" else "Another") }
    }
}
