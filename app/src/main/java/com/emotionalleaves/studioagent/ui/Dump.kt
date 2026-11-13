package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.emotionalleaves.studioagent.domain.Texts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.data.Idea
import com.emotionalleaves.studioagent.domain.Dump
import com.emotionalleaves.studioagent.work.Engine
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Empty your head: type (or say) everything at once, one line each. The app sorts it into jobs with their
 * days, things to buy, and ideas, and puts each away where it belongs. Tap a line's label to move it.
 */
@Composable
fun DumpScreen(nav: NavController) {
    val dao = G.dao; val scope = rememberCoroutineScope()
    val text = rememberSaveable { mutableStateOf("") }
    val moved = remember { mutableStateListOf<Pair<Int, Dump.Kind>>() }
    var putAway by rememberSaveable { mutableStateOf("") }
    val works by dao.artworks().collectAsState(emptyList())
    // A job that names one of your pieces belongs to that piece, so it shows up on its page and in its sessions.
    fun pieceFor(t: String) = works.filter { it.status != "idea" && it.title.length >= 4 }.firstOrNull { t.contains(it.title, ignoreCase = true) }
    val lines = Dump.sort(text.value).mapIndexed { i, l -> moved.lastOrNull { it.first == i }?.let { l.copy(kind = it.second) } ?: l }
    Screen("Empty your head", nav) {
        Intro("dump", "Everything that is buzzing around, one line each, in any order. Nothing needs deciding: the app sorts it into jobs (with their day when you name one), things to buy, and ideas, and puts each away.")
        Field("One thing per line", text, lines = 8, mic = true)
        if (putAway.isNotBlank() && lines.isEmpty()) Panel {
            Text("Put away: $putAway.", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Row {
                androidx.compose.material3.TextButton(onClick = { nav.navigate("records") }) { Text("To-dos") }
                androidx.compose.material3.TextButton(onClick = { nav.navigate("supplies") }) { Text("Supplies") }
                androidx.compose.material3.TextButton(onClick = { nav.navigate("ideas") }) { Text("Ideas") }
            }
        }
        if (lines.isEmpty() && putAway.isBlank()) Small("For example:\nemail the library about the wall\nbuy titanium white\nfinish the heron by Friday\na fox in the snow, all blue")
        if (lines.isNotEmpty()) {
            Panel {
                lines.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {
                            val next = Dump.Kind.values()[(l.kind.ordinal + 1) % Dump.Kind.values().size]
                            moved.add(i to next)
                        }, label = { Text(l.kind.label) })
                        Text(l.text + (l.day?.takeIf { l.kind == Dump.Kind.TASK }?.let { "  (" + dayName(it) + ")" } ?: "") + (if (l.kind != Dump.Kind.SUPPLY) pieceFor(l.text)?.let { "  for " + it.title } ?: "" else ""), Modifier.weight(1f))
                    }
                }
            }
            Small("Jobs without a day go on the list for the next couple of days. Tap a label to move a line.")
            Button(onClick = {
                val keep = lines.toList(); val now = System.currentTimeMillis()
                val before = st.supplies
                val evts = mutableListOf<Long>(); val ideas = mutableListOf<Long>(); val touched = mutableListOf<com.emotionalleaves.studioagent.data.Artwork>()
                scope.undoable("Put away ${keep.size} ${if (keep.size == 1) "thing" else "things"}", {
                    keep.forEach { l ->
                        when (l.kind) {
                            Dump.Kind.TASK -> { val d = l.day ?: LocalDate.now().plusDays(2); evts += dao.put(Evt(type = "task", title = l.text, date = Engine.millis(d.atTime(18, 0)), artworkId = pieceFor(l.text)?.id ?: 0)) }
                            // An idea about one of your pieces goes into that piece's notes, where you will see it while working on it.
                            Dump.Kind.IDEA -> { val p0 = pieceFor(l.text)
                                if (p0 != null) { val cur = dao.artworksNow().firstOrNull { it.id == p0.id } ?: p0; if (touched.none { it.id == cur.id }) touched += cur
                                    dao.put(cur.copy(notes = (cur.notes.trim() + "\nIdea: " + l.text).trim())) }
                                else ideas += dao.put(Idea(text = l.text, createdAt = now)) }
                            Dump.Kind.SUPPLY -> {
                                val have = supplies(st.supplies)
                                st.supplies = suppliesText(if (have.any { it.first.equals(l.text, true) }) have.map { if (it.first.equals(l.text, true)) it.first to true else it } else have + (l.text to true))
                            }
                        }
                    }
                }, {
                    dao.eventsNow().filter { e: Evt -> e.id in evts }.forEach { e: Evt -> dao.del(e) }
                    dao.ideasNow().filter { i: Idea -> i.id in ideas }.forEach { i: Idea -> dao.del(i) }
                    touched.forEach { dao.put(it) }
                    st.supplies = before
                })
                // Where each kind went, so nothing feels lost.
                putAway = listOfNotNull(
                    keep.count { it.kind == Dump.Kind.TASK }.takeIf { it > 0 }?.let { Texts.count(it, "to-do") + " in Records" },
                    keep.count { it.kind == Dump.Kind.SUPPLY }.takeIf { it > 0 }?.let { Texts.count(it, "thing") + " to buy in Supplies" },
                    keep.count { it.kind == Dump.Kind.IDEA }.takeIf { it > 0 }?.let { Texts.count(it, "idea") + " in Ideas" }).joinToString(", ")
                text.value = ""; moved.clear()
            }, Modifier.fillMaxWidth()) { Text("Put them away") }
        }
    }
}

private fun dayName(d: LocalDate): String = when (d) {
    LocalDate.now() -> "today"
    LocalDate.now().plusDays(1) -> "tomorrow"
    else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
}
