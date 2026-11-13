package com.emotionalleaves.studioagent.ui

import com.emotionalleaves.studioagent.domain.Texts
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
        val opps by dao.opps().collectAsState(emptyList())
        MonthView(events, nav, opps.filter { it.status in setOf("new", "later") && it.deadline > now() && it.fitScore >= 50 })
        androidx.compose.material3.TextButton(onClick = { nav.navigate("year") }) { Text("The year ahead") }
        // The week on paper, for the fridge or the studio wall: seen without opening the phone.
        androidx.compose.material3.TextButton(onClick = { scope.io {
            val today = LocalDate.now()
            val days = (0..6).map { k -> val d = today.plusDays(k.toLong())
                d to events.filter { !it.done && it.date > 0 && Engine.date(it.date) == d && it.type in setOf("studio", "task", "submit", "invite", "deadline") }.sortedBy { it.date }
                    .map { e -> (if (e.type == "studio") "Studio time" else e.title) + " (" + clock(e.date) + ")" } }
            val f = com.emotionalleaves.studioagent.work.Papers.weekSheet(ctx, st, days, if (st.monthGoalMonth == java.time.YearMonth.now().toString()) st.monthGoal else "")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
        } }) { Text("Print this week") }
        // For a calendar the app cannot reach (a work calendar, a shared one): every coming date in one file to import.
        androidx.compose.material3.TextButton(onClick = {
            val now0 = now()
            val coming = events.filter { !it.done && it.date > now0 && it.type in setOf("submit", "task", "studio", "invite", "watch") }
            val f = java.io.File(com.emotionalleaves.studioagent.media.ImageTools.exportDir(ctx), "Studio_Agent_dates.ics")
            f.writeText(com.emotionalleaves.studioagent.domain.Ics.file(coming.map { Triple("evt${it.id}", it.title.ifBlank { if (it.type == "studio") "Studio time" else it.type }, it.date to it.endDate) }))
            com.emotionalleaves.studioagent.sys.Share.files(ctx, listOf(f), "text/calendar")
        }) { Text("Add these dates to another calendar") }
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
                        // The phone's own clock face: nothing to type, nothing to get wrong.
                        TimeField("Starts at", Planner.timeOrNull(s.time)?.toString() ?: "17:00") { v -> val i = slots.indexOfFirst { it.id == s.id }; if (i >= 0) slots[i] = slots[i].copy(time = v) }
                        Pick("How long", listOf("20", "30", "40", "60", "90", "120", "180", "240").let { if (s.minutes in it) it else it + s.minutes }, s.minutes,
                            names = { m -> m.toIntOrNull()?.let { if (it >= 60 && it % 60 == 0) "${it / 60} hour" + (if (it > 60) "s" else "") else if (it > 60) "${it / 60} h ${it % 60} min" else "$it min" } ?: m }) { v ->
                            val i = slots.indexOfFirst { it.id == s.id }; if (i >= 0) slots[i] = slots[i].copy(minutes = v)
                        }
                        TextButton(onClick = { slots.removeAll { it.id == s.id } }) { Text("Remove this time") }
                    }
                }
            }
            // A new time lands on the first day without one: a weekend morning, or else an evening after work and rest.
            OutlinedButton(onClick = {
                counter++
                val used = slots.map { it.day }.toSet()
                val workDays = Planner.days(st.workDays)
                val day = (listOf(6, 7) + (1..5)).firstOrNull { it !in used } ?: 6
                val evening = Planner.time(st.workEnd, LocalTime.of(15, 30)).plusMinutes(st.bufferMin.toLong()).let { t -> if (t.minute in 1..29) t.withMinute(30) else if (t.minute > 30) t.plusHours(1).withMinute(0) else t }
                val time = if (day in workDays) evening.toString() else "10:00"
                slots.add(EditSlot(counter, day, time, if (day in workDays) "40" else "90"))
            }) { Text("Add a time") }
            val total = slots.sumOf { it.minutes.trim().toIntOrNull() ?: 0 }
            Note("Total: ${"%.1f".format(total / 60.0)} hours a week.")
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                // A time that cannot be read is left out rather than quietly moved to 5 pm.
                val parsed = slots.mapNotNull { e -> val t = Planner.timeOrNull(e.time) ?: return@mapNotNull null; e.minutes.trim().toIntOrNull()?.takeIf { it > 0 }?.let { TemplateSlot(e.day, t, it) } }
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
                    TextButton(onClick = {
                        // Undo puts it back in the calendar too, not only in the app.
                        scope.undoable("Skipped ${fmtDate(e.date)}. Nothing is marked missed.", { Cal.remove(ctx, e.calendarId); dao.del(e) },
                            { val cal = if (e.calendarId != 0L) Cal.add(ctx, e.title, e.date, e.endDate) else 0L; dao.put(e.copy(calendarId = cal)) })
                    }) { Text("Skip this one") }
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
        // The last eight weeks at a glance, against the hours you set: steady beats heroic.
        val hours = com.emotionalleaves.studioagent.domain.HoursByWeek.of(LocalDate.now(), sessions.filter { it.end > it.start }.map { Engine.date(it.start) to (it.end - it.start) / 3_600_000.0 })
        if (hours.any { it > 0 }) Panel {
            Text("The last eight weeks", fontWeight = FontWeight.SemiBold)
            val top = maxOf(hours.max(), st.weeklyHours.toDouble(), 1.0)
            val bar = MaterialTheme.colorScheme.primary; val goal = MaterialTheme.colorScheme.outline
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = hours.joinToString(", ") { "%.1f hours".format(it) } }) {
                val w = size.width / hours.size
                hours.forEachIndexed { i, h -> val bh = (h / top * size.height * 0.95).toFloat(); drawRect(bar, androidx.compose.ui.geometry.Offset(i * w + w * 0.2f, size.height - bh), androidx.compose.ui.geometry.Size(w * 0.6f, bh)) }
                if (st.weeklyHours > 0) { val y = (size.height - st.weeklyHours / top * size.height * 0.95).toFloat(); drawLine(goal, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 2f) }
            }
            Small("This week on the right." + (if (st.weeklyHours > 0) " The line is the ${st.weeklyHours} hours you set; a week under it still counts." else ""))
        }
        sessions.take(60).forEach { s ->
            Panel {
                Text(fmtDate(s.start) + ", ${((s.end - s.start) / 60000).coerceAtLeast(0)} min, ${mapOf("make" to "Make", "skill" to "Practice", "light" to "Light")[s.kind] ?: s.kind}" + name(s.artworkId).let { if (it.isNotBlank()) ", $it" else "" }, fontWeight = FontWeight.SemiBold)
                if (s.nextStep.isNotBlank()) Note("Next time: ${s.nextStep}")
                if (s.note.isNotBlank()) Note(s.note)
                if (s.photoPath.isNotBlank()) PathImage(s.photoPath, Modifier.fillMaxWidth(), 500)
                TextButton(onClick = { scope.remove("the session", { dao.del(s) }, { dao.put(s) }) }) { Text("Remove this session") }
            }
        }
        Expand("Log one you did away from the app") {
            DateField("Day", date.value) { date.value = it }
            Count("Minutes", minutes.value.toIntOrNull() ?: 30, 5, 600, 5, "min") { minutes.value = it.toString() }
            Choice("Kind", listOf("make", "practise", "light"), if (kind == "skill") "practise" else kind, names = { mapOf("make" to "Make", "practise" to "Practice", "light" to "Light")[it] ?: it }) { kind = if (it == "practise") "skill" else it }
            if (kind == "make" && active.isNotEmpty()) Choice("Piece (optional)", active.map { it.id.toString() }, workId.toString(), names = { i -> active.firstOrNull { it.id.toString() == i }?.let { it.title.ifBlank { "Untitled #${it.id}" } } ?: i }) { i -> workId = i.toLongOrNull() ?: 0L }
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

/** What a dated thing is, as a mark color and a word: making time, a deadline, or something for a show. */
private fun evtKind(type: String): Int = when (type) { "studio" -> 0; "deadline", "submit", "pack" -> 1; else -> 2 }  // shows and openings share a mark

/**
 * The month at a glance: every studio time, deadline and show step as a small mark on its day.
 * Tap a day to see what is on it; tap an item to open it.
 */
@Composable
fun MonthView(events: List<com.emotionalleaves.studioagent.data.Evt>, nav: NavController, calls: List<com.emotionalleaves.studioagent.data.Opp> = emptyList()) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var picked by remember { mutableStateOf(today) }
    val open = events.filter { !it.done && it.date > 0 }
    val byDay = open.groupBy { Engine.date(it.date) }
    // Calls you have not decided on yet, by the day they close: so a good one is not found too late.
    val callsByDay = calls.groupBy { Engine.date(it.deadline) }
    val markColors = listOf(cs.primary, cs.error, cs.tertiary, cs.outline)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("Earlier") }
            Text(month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + month.year,
                Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("Later") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) } }
        val first = month.dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        (0 until (first + days + 6) / 7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { col ->
                    val n = week * 7 + col - first + 1
                    if (n < 1 || n > days) Spacer(Modifier.weight(1f).height(44.dp))
                    else {
                        val d = month.withDayOfMonth(n)
                        val marks = (byDay[d].orEmpty().map { evtKind(it.type) } + (if (callsByDay[d].isNullOrEmpty()) emptyList() else listOf(3))).distinct().sorted()
                        val sel = d == picked
                        Column(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(onClickLabel = "Show this day") { picked = d }
                            .semantics(mergeDescendants = true) { contentDescription = d.toString() + if (marks.isEmpty()) "" else ", " + Texts.count(byDay[d].orEmpty().size, "item") },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("$n", style = MaterialTheme.typography.bodyMedium, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (d.isBefore(today)) cs.onSurfaceVariant else cs.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp).padding(top = 2.dp)) {
                                marks.forEach { k -> Box(Modifier.size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(markColors[k])) }
                            }
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Making time", "Deadlines", "Shows", "Calls closing").forEachIndexed { k, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(markColors[k])); Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        Hair()
        val items = byDay[picked].orEmpty().sortedBy { it.date }
        Text(fmtDate(Engine.millis(picked.atTime(12, 0))), style = MaterialTheme.typography.titleSmall)
        val closing = callsByDay[picked].orEmpty()
        if (items.isEmpty() && closing.isEmpty()) Small("Nothing on this day.")
        closing.forEach { o ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { nav.navigate("call/${o.id}") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(markColors[3]))
                Text("Call closes: " + o.title, Modifier.weight(1f))
            }
        }
        items.forEach { e ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(enabled = e.applId > 0 || e.artworkId > 0) { nav.navigate(if (e.artworkId > 0) "work/${e.artworkId}" else "appl/${e.applId}") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(markColors[evtKind(e.type)]))
                Text(e.title.ifBlank { if (e.type == "studio") "Studio time" else e.type }, Modifier.weight(1f))
                if (e.type == "studio") Text(clock(e.date), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
