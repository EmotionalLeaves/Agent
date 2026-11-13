package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
    val lines = Dump.sort(text.value).mapIndexed { i, l -> moved.lastOrNull { it.first == i }?.let { l.copy(kind = it.second) } ?: l }
    Screen("Empty your head", nav) {
        Intro("dump", "Everything that is buzzing around, one line each, in any order. Nothing needs deciding: the app sorts it into jobs (with their day when you name one), things to buy, and ideas, and puts each away.")
        Field("One thing per line", text, lines = 8, mic = true)
        if (lines.isNotEmpty()) {
            Panel {
                lines.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {
                            val next = Dump.Kind.values()[(l.kind.ordinal + 1) % Dump.Kind.values().size]
                            moved.add(i to next)
                        }, label = { Text(l.kind.label) })
                        Text(l.text + (l.day?.takeIf { l.kind == Dump.Kind.TASK }?.let { "  (" + dayName(it) + ")" } ?: ""), Modifier.weight(1f))
                    }
                }
            }
            Small("Jobs without a day go on the list for the next couple of days. Tap a label to move a line.")
            Button(onClick = {
                val keep = lines.toList(); val now = System.currentTimeMillis()
                val before = st.supplies
                val added = mutableListOf<Any>()
                scope.undoable("Put away ${keep.size} ${if (keep.size == 1) "thing" else "things"}", {
                    keep.forEach { l ->
                        when (l.kind) {
                            Dump.Kind.TASK -> { val d = l.day ?: LocalDate.now().plusDays(2); added += dao.put(Evt(type = "task", title = l.text, date = Engine.millis(d.atTime(18, 0)))) to "evt" }
                            Dump.Kind.IDEA -> added += dao.put(Idea(text = l.text, createdAt = now)) to "idea"
                            Dump.Kind.SUPPLY -> {
                                val have = supplies(st.supplies)
                                st.supplies = suppliesText(if (have.any { it.first.equals(l.text, true) }) have.map { if (it.first.equals(l.text, true)) it.first to true else it } else have + (l.text to true))
                            }
                        }
                    }
                }, {
                    added.forEach { a -> val (id, kind) = a as Pair<*, *>
                        if (kind == "evt") dao.eventsNow().firstOrNull { it.id == id }?.let { dao.del(it) } else dao.ideasNow().firstOrNull { it.id == id }?.let { dao.del(it) } }
                    st.supplies = before
                })
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
