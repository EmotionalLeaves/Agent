package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.data.Sess
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.TemplateSlot
import com.emotionalleaves.studioagent.sys.Cal
import com.emotionalleaves.studioagent.work.DAY
import com.emotionalleaves.studioagent.work.Engine
import java.time.LocalDate
import java.time.LocalTime

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** A slot while it is being edited: everything is text until you save. */
private data class EditSlot(val id: Int, val day: Int, val time: String, val minutes: String)

private fun suggested(): List<TemplateSlot> = Planner.suggestedTemplate(
    Planner.days(st.workDays), Planner.time(st.workEnd, LocalTime.of(15, 30)), st.bufferMin, Planner.time(st.bedtime, LocalTime.of(22, 30)), st.weeklyHours
)

@Composable
fun WeekScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by dao.events().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    val slots = remember {
        mutableStateListOf<EditSlot>().apply { Planner.parseTemplate(st.weekTemplate).forEachIndexed { i, s -> add(EditSlot(i, s.day, s.time.toString(), s.minutes.toString())) } }
    }
    var custom by remember { mutableStateOf(st.weekTemplate.isNotBlank()) }
    var counter by remember { mutableStateOf(100) }
    var status by remember { mutableStateOf("") }
    fun replan(message: String) { status = "Re-planning…"; scope.io({ status = it }) { Engine.replan(ctx); status = message } }

    Screen("Your week", nav) {
        Intro("week", "These are the times the app books for making work. They are a plan, not a test: skip one and nothing is marked missed. Change anything here to fit your real life.")
        Status(status)
        H("Studio times")
        if (!custom) {
            Small("The suggested week: two short evenings after work, the rest at the weekend.")
            Panel {
                suggested().forEachIndexed { i, s ->
                    if (i > 0) Hair()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayNames[s.day - 1], Modifier.width(56.dp), style = MaterialTheme.typography.titleMedium)
                        Text(s.time.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)), Modifier.weight(1f))
                        Text("${s.minutes} min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = {
                slots.clear(); suggested().forEachIndexed { i, s -> slots.add(EditSlot(i, s.day, s.time.toString(), s.minutes.toString())) }
                custom = true
            }) { Text("Change studio times") }
        } else {
            slots.forEach { s ->
                key(s.id) {
                    Panel {
                        Choice("", dayNames, dayNames[(s.day - 1).coerceIn(0, 6)]) { d -> val i = slots.indexOfFirst { it.id == s.id }; if (i >= 0) slots[i] = slots[i].copy(day = dayNames.indexOf(d) + 1) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Field("Start (HH:MM)", s.time, { v -> val i = slots.indexOfFirst { it.id == s.id }; if (i >= 0) slots[i] = slots[i].copy(time = v) }) }
                            Column(Modifier.weight(1f)) { Field("Minutes", s.minutes, { v -> val i = slots.indexOfFirst { it.id == s.id }; if (i >= 0) slots[i] = slots[i].copy(minutes = v) }, number = true) }
                        }
                        TextButton(onClick = { slots.removeAll { it.id == s.id } }) { Text("Remove this time") }
                    }
                }
            }
            OutlinedButton(onClick = { counter++; slots.add(EditSlot(counter, 6, "10:00", "60")) }) { Text("Add a time") }
            val total = slots.sumOf { it.minutes.trim().toIntOrNull() ?: 0 }
            Note("Total: ${"%.1f".format(total / 60.0)} hours a week.")
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val parsed = slots.mapNotNull { e -> e.minutes.trim().toIntOrNull()?.takeIf { it > 0 }?.let { TemplateSlot(e.day, Planner.time(e.time.trim(), LocalTime.of(17, 0)), it) } }
                st.weekTemplate = Planner.formatTemplate(parsed)
                if (parsed.isNotEmpty()) st.weeklyHours = ((parsed.sumOf { it.minutes } + 30) / 60).coerceAtLeast(1)
                custom = parsed.isNotEmpty()
                replan("Saved. The coming week is re-planned" + (if (st.useCalendar && Cal.granted(ctx)) " and your calendar is updated." else "."))
            }) { Text("Save and re-plan") }
            TextButton(onClick = { st.weekTemplate = ""; slots.clear(); custom = false; replan("Back on the suggested week.") }) { Text("Go back to the suggested week") }
        }

        H("Pause")
        if (tick >= 0 && st.pausedUntil > now()) {
            Panel { Text("Paused until ${fmtDate(st.pausedUntil)}. No blocks, no reminders, nothing counted as missed.") }
            Button(onClick = { st.pausedUntil = 0L; replan("Welcome back. The week is planned again.") }) { Text("Resume now") }
        } else {
            Note("Busy week, low week, away: pause and the app stops planning and reminding. You can still start a session whenever you like.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "1 week", 14 to "2 weeks", 30 to "A month").forEach { (days, label) ->
                    OutlinedButton(onClick = { st.pausedUntil = now() + days * DAY; replan("Paused.") }) { Text(label) }
                }
            }
        }

        val coming = events.filter { it.type == "studio" && it.date > now() }.take(8)
        if (coming.isNotEmpty()) {
            H("Coming up")
            coming.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtDate(e.date) + ", " + clock(e.date) + ", ${(e.endDate - e.date) / 60000} min", Modifier.weight(1f))
                    TextButton(onClick = { scope.remove("the studio time", { Cal.remove(ctx, e.calendarId); dao.del(e) }, { dao.put(e) }) }) { Text("Skip this one") }
                }
            }
        }
        IconRow(Icons.AutoMirrored.Filled.List, "Session history", "See, fix or remove past sessions, or log one you forgot") { nav.navigate("sessions") }
    }
}

@Composable
fun SessionsScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val sessions by dao.sessions().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val date = remember { mutableStateOf(LocalDate.now().toString()) }
    val minutes = rememberSaveable { mutableStateOf("30") }
    val note = rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("make") }
    var workId by remember { mutableStateOf(0L) }
    val active = works.filter { it.status != "sold" }.take(12)
    fun name(id: Long) = works.firstOrNull { it.id == id }?.let { it.title.ifBlank { "Untitled #${it.id}" } }.orEmpty()
    Screen("Session history", nav) {
        if (sessions.isEmpty()) Empty("No sessions yet. Each one you finish is listed here.")
        sessions.take(60).forEach { s ->
            Panel {
                Text(fmtDate(s.start) + ", ${((s.end - s.start) / 60000).coerceAtLeast(0)} min, ${mapOf("make" to "Make", "skill" to "Practise", "light" to "Light")[s.kind] ?: s.kind}" + name(s.artworkId).let { if (it.isNotBlank()) ", $it" else "" }, fontWeight = FontWeight.SemiBold)
                if (s.nextStep.isNotBlank()) Note("Next time: ${s.nextStep}")
                if (s.note.isNotBlank()) Note(s.note)
                if (s.photoPath.isNotBlank()) PathImage(s.photoPath, Modifier.fillMaxWidth(), 500)
                TextButton(onClick = { scope.remove("the session", { dao.del(s) }, { dao.put(s) }) }) { Text("Remove this session") }
            }
        }
        Expand("Log one you did away from the app") {
            DateField("Day", date.value) { date.value = it }
            Count("Minutes", minutes.value.toIntOrNull() ?: 30, 5, 600, 5, "min") { minutes.value = it.toString() }
            Choice("Kind", listOf("make", "practise", "light"), if (kind == "skill") "practise" else kind, names = { mapOf("make" to "Make", "practise" to "Practise", "light" to "Light")[it] ?: it }) { kind = if (it == "practise") "skill" else it }
            if (kind == "make" && active.isNotEmpty()) Choice("Piece (optional)", active.map { it.title.ifBlank { "Untitled #${it.id}" } }, name(workId)) { t -> workId = active.firstOrNull { it.title.ifBlank { "Untitled #${it.id}" } == t }?.id ?: 0L }
            Field("Note (optional)", note)
            Button(enabled = (minutes.value.trim().toIntOrNull() ?: 0) > 0, onClick = {
                val day = try { LocalDate.parse(date.value.trim()) } catch (e: Exception) { LocalDate.now() }
                val start = Engine.millis(day.atTime(12, 0)); val mins = minutes.value.trim().toIntOrNull() ?: 0
                val s = Sess(artworkId = if (kind == "make") workId else 0L, kind = kind, start = start, end = start + mins * 60_000L, note = note.value.trim())
                note.value = ""
                scope.io { dao.put(s) }
            }) { Text("Log this session") }
        }
    }
}
