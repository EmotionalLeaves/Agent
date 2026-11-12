package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Idea
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.data.Sess
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.net.Fetch
import com.emotionalleaves.studioagent.sys.Notifier
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.NextStep
import com.emotionalleaves.studioagent.work.Pending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun Home(nav: NavController) {
    val ctx = LocalContext.current
    val buzz = rememberBuzz()
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val events by dao.events().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    var next by remember { mutableStateOf<NextStep?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    var guide by remember { mutableStateOf<List<GuideStep>>(emptyList()) }
    var pinning by remember { mutableStateOf(false) }
    val pinText = rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val appls by dao.appls().collectAsState(emptyList())
    val idea = rememberSaveable { mutableStateOf("") }
    var parked by remember { mutableStateOf(false) }
    LaunchedEffect(works, docs, sessions, events, tick) {
        withContext(Dispatchers.IO) {
            try { next = Engine.nextStep(); pendingCount = Engine.pending().size; guide = guideSteps(ctx) } catch (e: Exception) { }
        }
    }
    val rules = Engine.rules()
    val stats = Engine.statsOf(works, docs, facts)
    val level = Levels.level(stats, rules)
    val gate = Levels.gate(stats, rules)
    val today = LocalDate.now()

    var crashed by remember { mutableStateOf(G.crashFile.exists()) }
    var menu by remember { mutableStateOf(false) }
    val dayName = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    Screen(if (st.onboarded) dayName else "Studio Agent", nav, back = false) {
        if (crashed) Panel {
            Text("The app closed unexpectedly last time", style = MaterialTheme.typography.titleSmall)
            Text("Nothing you saved is lost. Sending the details gets it fixed.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Share.text(ctx, G.problemReport()) }) { Text("Send the details") }
                TextButton(onClick = { G.crashFile.delete(); crashed = false }) { Text("Dismiss") }
            }
        }

        // The one next step, pinned to the wall.
        val n = next
        WallLabel(onClick = { n?.let { if (it.route != "home") nav.navigate(it.route) } }) {
            Row(verticalAlignment = Alignment.Top) {
                Text(n?.title ?: "Finding your next step", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = if (n == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (st.onboarded) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.offset(x = 12.dp, y = (-10).dp)) { Icon(Icons.Default.MoreVert, "Change today's step", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (st.pinnedStep.isNotBlank()) DropdownMenuItem(text = { Text("Done, clear my pin") }, onClick = { st.pinnedStep = ""; menu = false })
                        else {
                            if (st.snoozeDay == today.toString()) DropdownMenuItem(text = { Text("Bring today's step back") }, onClick = { st.snoozeDay = ""; menu = false })
                            else DropdownMenuItem(text = { Text("Not today") }, onClick = { st.snoozeDay = today.toString(); menu = false })
                            DropdownMenuItem(text = { Text("Pin my own step") }, onClick = { pinning = true; menu = false })
                        }
                    }
                }
            }
            if (n != null && n.detail.isNotBlank()) Text(n.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!st.onboarded) TextButton(onClick = { st.setupDone = true; st.tourSeen = true; nav.navigate("home") { popUpTo("home") { inclusive = true } } }) { Text("Skip the tour and start") }
        if (pinning) {
            Field("What do you want to see here instead?", pinText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = pinText.value.isNotBlank(), onClick = { st.pinnedStep = pinText.value.trim(); pinText.value = ""; pinning = false }) { Text("Pin it") }
                TextButton(onClick = { pinning = false }) { Text("Cancel") }
            }
        }

        val workEnd = Planner.time(st.workEnd, LocalTime.of(15, 30))
        val isWorkDay = today.dayOfWeek.value in Planner.days(st.workDays)
        if (st.onboarded && isWorkDay && LocalTime.now().isAfter(workEnd) && st.energyDay != today.toString()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Energy after work?", style = MaterialTheme.typography.titleSmall)
                Choice("", listOf("wiped", "okay", "good"), "") { e -> st.energy = e; st.energyDay = today.toString() }
            }
        }

        if (st.onboarded) {
            if (st.sessionStart > 0) Button(onClick = { nav.navigate("session") }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) { Text("Back to your session") }
            else {
                // One tap: the app picks the piece and starts the clock. Nothing to decide first.
                Button(onClick = { buzz(); scope.io { Engine.quickStart(st.defaultSessionMin); if (st.dndInSession) Notifier.dnd(ctx, true); withContext(Dispatchers.Main) { nav.navigate("session") } } }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) { Text("Start now") }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { nav.navigate("session") }) { Text("Choose the piece or a shorter time") }
                }
            }
        }

        if (st.onboarded) {
            // The week as seven marks: filled for a day you made something, a ring for a block still ahead.
            val hours = Engine.hoursThisWeek(sessions)
            val cs = MaterialTheme.colorScheme
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).clickable { nav.navigate("week") }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your week", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("%.1f of %d hours".format(hours, st.weeklyHours), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open your week", Modifier.padding(start = 4.dp).size(20.dp), tint = cs.outline)
                }
                val monday = today.with(java.time.DayOfWeek.MONDAY)
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { i ->
                        val day = monday.plusDays(i.toLong())
                        val worked = sessions.any { it.end > it.start && Engine.date(it.start) == day }
                        val planned = events.any { it.type == "studio" && it.date > now() && Engine.date(it.date) == day }
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = day.dayOfWeek.toString().lowercase() + (if (worked) ", worked" else if (planned) ", planned" else "") },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MTWTFSS"[i].toString(), style = MaterialTheme.typography.labelMedium, fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (day == today) cs.onSurface else cs.onSurfaceVariant)
                            Box(Modifier.size(width = 26.dp, height = 34.dp).clip(RoundedCornerShape(3.dp)).let {
                                if (worked) it.background(cs.primary) else if (planned) it.border(2.dp, cs.primary, RoundedCornerShape(3.dp)) else it.background(cs.surfaceContainerHigh)
                            })
                        }
                    }
                }
                if (st.pausedUntil > now()) Small("Planning is paused until ${fmtDate(st.pausedUntil)}.")
                else events.firstOrNull { it.type == "studio" && it.date > now() }?.let { Small("Next studio time: ${fmtDate(it.date)}") }
            }

            val deadlines = events.filter { it.type == "deadline" && !it.done && it.date > now() }.take(3)
            if (deadlines.isNotEmpty()) {
                Section("Deadlines")
                deadlines.forEach { e ->
                    IconRow(Icons.Default.DateRange, e.title.removePrefix("Deadline: "), Planner.weekendsLeft(today, Engine.date(e.date)).replaceFirstChar { it.uppercase() }) {
                        e.applId.takeIf { it > 0 }?.let { nav.navigate("appl/$it") } ?: nav.navigate("calls")
                    }
                }
            }
        }

        // Proof of what got done, in plain counts. No streaks, nothing to break.
        val weekStart = Engine.millis(today.with(java.time.DayOfWeek.MONDAY).atStartOfDay())
        val weekSessions = sessions.filter { it.start >= weekStart && it.end > it.start }
        val wins = mutableListOf<String>()
        if (weekSessions.isNotEmpty()) wins += "${weekSessions.size} session" + (if (weekSessions.size > 1) "s" else "") + ", ${weekSessions.sumOf { (it.end - it.start) / 60000 }} minutes of making"
        works.count { it.createdAt >= weekStart && it.imagePath.isNotBlank() }.let { if (it > 0) wins += "$it piece" + (if (it > 1) "s" else "") + " with an image added" }
        appls.count { it.submittedAt >= weekStart }.let { if (it > 0) wins += "$it application" + (if (it > 1) "s" else "") + " sent" }
        if (wins.isNotEmpty()) {
            Section("Done this week")
            wins.forEach { Check(true, it) }
        }

        if (st.onboarded) {
            if (pendingCount > 0) Section("Waiting for you")
            if (pendingCount > 0) IconRow(Icons.Default.Done, "$pendingCount waiting for your yes", "One tap each") { nav.navigate("admin") }

            Space(4)
            Field("Park an idea so it stops circling", idea, mic = true)
            if (idea.value.isNotBlank()) Button(onClick = { buzz(); val t = idea.value.trim(); idea.value = ""; parked = true; scope.io { dao.put(Idea(text = t, createdAt = now())) } }) { Text("Park it") }
            if (parked) Check(true, "Parked. It waits in Studio, under Ideas.", small = true)
        } else {
            val todo = guide.filter { !it.done }
            Section("How it goes")
            Column {
                listOf("Look around" to "A two-minute tour. Nothing to fill in.",
                    "Make" to "Tap Start and the timer runs. The app keeps track of each piece and the next small step.",
                    "Show" to "Once a few pieces are done, it finds open calls that fit and builds each application for your yes.").forEachIndexed { i, (t, d) ->
                    Timeline(done = false, current = i == 0, last = i == 2) {
                        Text(t, style = MaterialTheme.typography.titleMedium)
                        Small(d)
                    }
                }
            }
        }
    }
}

/** Shown once after a session ends: what was saved, in full, so there is no doubt and nothing to go back and check. */
@Composable
fun DoneScreen(nav: NavController) {
    val sessions by dao.sessions().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val s = sessions.firstOrNull()
    Screen("Session saved", nav, bare = true) {
        Space(40)
        // The one moment of motion in the app: the word settles into place once.
        var shownIn by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shownIn = true }
        val lift by animateFloatAsState(if (shownIn) 0f else 1f, tween(520), label = "saved")
        Text("Saved.", Modifier.graphicsLayer { translationY = lift * 40f; alpha = 1f - lift }, style = MaterialTheme.typography.displayLarge)
        if (s != null) {
            val mins = ((s.end - s.start) / 60000).coerceAtLeast(0)
            Text("$mins minutes" + (works.firstOrNull { it.id == s.artworkId }?.let { " on " + it.title.ifBlank { "Untitled" } } ?: "") + ".", style = MaterialTheme.typography.headlineSmall)
            Text(if (mins < 10) "Short sessions count. You showed up." else "That counts toward your week.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.nextStep.isNotBlank()) WallLabel(onClick = null) {
                Small("Next time starts with")
                Text(s.nextStep, style = MaterialTheme.typography.titleLarge)
            }
        }
        Small("It is all stored. You can close the app.")
        Space(8)
        Button(onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Back to Today") }
    }
}

@Composable
fun SessionScreen(nav: NavController) {
    val ctx = LocalContext.current
    val buzz = rememberBuzz()
    val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    val start = remember(tick) { st.sessionStart }
    val active = works.filter { it.status == "in_progress" || it.status == "idea" }

    if (start == 0L) {
        var kind by remember { mutableStateOf(if (st.energyDay == LocalDate.now().toString() && st.energy == "wiped") "light" else "make") }
        var workId by remember(works.size) { mutableStateOf((Engine.currentPiece(works) ?: active.firstOrNull())?.id ?: 0L) }
        val work = works.firstOrNull { it.id == workId }
        Screen("Start a session", nav) {
            Section("What kind?")
            // Three kinds as rows with a line each, so the choice explains itself.
            Column {
                listOf("make" to ("Make" to "Work on a piece."), "skill" to ("Practise" to "A new medium or technique. Counts toward your week."),
                    "light" to ("Light" to "Low effort: sort references, a 15-minute sketch, tidy up.")).forEach { (k, tl) ->
                    val on = kind == k
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { kind = k }.semantics { selected = on }.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = on, onClick = null)
                        Column(Modifier.weight(1f)) { Text(tl.first, style = MaterialTheme.typography.titleSmall); Small(tl.second) }
                    }
                }
            }
            if (kind == "make") {
                if (active.isNotEmpty()) Choice("Which piece?", active.map { it.title.ifBlank { "Untitled #${it.id}" } }, work?.title?.ifBlank { "Untitled #${work.id}" }.orEmpty()) { picked ->
                    active.firstOrNull { it.title.ifBlank { "Untitled #${it.id}" } == picked }?.let { workId = it.id }
                }
                TextButton(onClick = { scope.io { workId = dao.put(newWork()) } }) { Text("Start a new piece") }
            }
            val left = work?.let { Engine.firstStep(it) }.orEmpty().ifBlank { sessions.firstOrNull { it.artworkId == workId && it.nextStep.isNotBlank() }?.nextStep.orEmpty() }
            Panel(strong = true) {
                Text("Two-minute start", fontWeight = FontWeight.SemiBold)
                Text(when {
                    kind == "light" -> "Pick one small thing. Stopping after it is fine."
                    left.isNotBlank() -> "Where you left off: $left"
                    work?.format == "physical" || kind == "skill" -> "Set out your materials. That is the whole first step."
                    else -> "Open the file. That is the whole first step."
                })
            }
            // How long: the usual, or something smaller for a hard day. Five minutes still counts.
            val usual = "${st.defaultSessionMin} min"
            var length by rememberSaveable { mutableStateOf(if (kind == "light") "15 min" else usual) }
            Section("How long?")
            Choice("", listOf("5 min", "15 min", usual).distinct(), length) { length = it }
            Button(onClick = {
                buzz()
                st.sessionPausedAt = 0L; st.sessionPausedTotal = 0L
                st.sessionGoalMin = length.substringBefore(" ").toIntOrNull() ?: st.defaultSessionMin
                st.sessionArtwork = if (kind == "make") workId else 0L; st.sessionKind = kind; st.sessionStart = now()
                if (st.dndInSession) Notifier.dnd(ctx, true)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start") }
            if (st.dndInSession && !Notifier.dndGranted(ctx)) Small("Want the phone silenced during sessions? Allow it in You, Permissions.")
        }
    } else {
        var nowMs by remember { mutableStateOf(now()) }
        LaunchedEffect(start) { while (true) { delay(1000); nowMs = now() } }
        val note = rememberSaveable { mutableStateOf("") }
        val nextStep = rememberSaveable { mutableStateOf("") }
        var photo by remember { mutableStateOf("") }
        val photoFile = remember(start) { File(ImageTools.mediaDir(ctx), "progress_$start.jpg") }
        val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photo = photoFile.absolutePath }
        val paused = st.sessionPausedAt > 0
        // Keep the screen on so the time bar is there at a glance. Switchable in Settings, Focus and calm.
        DisposableEffect(Unit) {
            val window = (ctx as? android.app.Activity)?.window
            if (st.keepAwake) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        val secs = (Engine.sessionElapsed(nowMs) / 1000).coerceAtLeast(0)
        val work = works.firstOrNull { it.id == st.sessionArtwork }
        fun finish(save: Boolean) {
            val end = start + Engine.sessionElapsed(now()); val kind = st.sessionKind; val artId = st.sessionArtwork
            st.sessionStart = 0L; st.sessionPausedAt = 0L; st.sessionPausedTotal = 0L
            Notifier.dnd(ctx, false)
            scope.io {
                if (save) {
                    dao.put(Sess(artworkId = artId, kind = kind, start = start, end = end, note = note.value.trim(), nextStep = nextStep.value.trim(), photoPath = photo))
                    val latest = dao.artworksNow().firstOrNull { it.id == artId }
                    if (latest != null && nextStep.value.isNotBlank()) dao.put(latest.copy(nextStep = nextStep.value.trim()))
                }
                withContext(Dispatchers.Main) { nav.navigate(if (save) "done" else "home") { popUpTo("home") { inclusive = !save } } }
            }
        }
        var stopping by remember { mutableStateOf(false) }
        val goal = st.sessionGoalMin
        Screen("In session", nav, bare = true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (paused) "Paused" else "In session", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }) { Icon(Icons.Default.Close, "Leave this screen; the timer keeps going", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val track = MaterialTheme.colorScheme.surfaceContainerHigh
            val fill = MaterialTheme.colorScheme.primary
            val part = if (goal > 0) (secs / (goal * 60f)).coerceIn(0f, 1f) else 0f
            // A ring that fills as time passes: the whole session visible at a glance.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                val shown by animateFloatAsState(part, tween(900), label = "ring")
                Canvas(Modifier.size(250.dp)) {
                    val w = 10.dp.toPx()
                    drawArc(track, -90f, 360f, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    if (shown > 0f) drawArc(fill, -90f, 360f * shown, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((if (secs >= 3600) "%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60) else "%d:%02d".format(secs / 60, secs % 60)), style = MaterialTheme.typography.displayMedium)
                    if (goal > 0) Small(if (secs >= goal * 60L) "$goal minutes done" else "${Math.ceil((goal * 60L - secs) / 60.0).toInt()} min to go")
                }
            }
            if (goal > 0 && secs >= goal * 60L) Panel(strong = true) { Text("That is your $goal minutes. Stopping now is a complete session. Keep going only if you want to.") }
            Text(work?.title?.ifBlank { "Untitled" } ?: (if (st.sessionKind == "skill") "Practice" else "Light session"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (work != null) {
                val steps = Texts.steps(work.steps)
                val open = steps.indexOfFirst { !it.first }
                if (open >= 0) WallLabel(onClick = null) {
                    Text("Now: ${steps[open].second}", style = MaterialTheme.typography.titleLarge)
                    Small("${steps.count { it.first }} of ${steps.size} small steps done")
                    TextButton(onClick = { scope.io { dao.put(work.copy(steps = Texts.stepsText(steps.mapIndexed { i, p -> if (i == open) true to p.second else p }))) } }, modifier = Modifier.offset(x = (-12).dp)) { Text("Done, show the next one") }
                } else if (work.nextStep.isNotBlank()) Small("Where you left off: ${work.nextStep}")
                if (work.doneWhen.isNotBlank()) Small("Finished when: ${work.doneWhen}")
            }
            if (Planner.isWindDown(LocalDateTime.now(), Planner.time(st.bedtime, LocalTime.of(22, 30)))) Panel(strong = true) {
                Text("Wind-down time. A good place to stop: write your next step so nothing is lost.")
            }
            if (!stopping) {
                Space(8)
                // One big way out, one quiet way to pause. Stacked, so long words never get squeezed.
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = { stopping = true }) { Text("I am stopping") }
                OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                    if (paused) { st.sessionPausedTotal = st.sessionPausedTotal + (now() - st.sessionPausedAt); st.sessionPausedAt = 0L }
                    else st.sessionPausedAt = now()
                }) { Text(if (paused) "Resume" else "Pause") }
                Text("Put the phone down. The timer keeps going.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                }
            } else {
                H("Before you go")
                Field("Next time, start with...", nextStep, mic = true)
                Field("Anything to note (optional)", note, lines = 2)
                if (photo.isNotBlank()) PathImage(photo, Modifier.fillMaxWidth().height(160.dp))
                OutlinedButton(onClick = { try { take.launch(Share.uri(ctx, photoFile)) } catch (e: Exception) { } }) { Text(if (photo.isBlank()) "Progress photo (optional)" else "Retake photo") }
                Button(onClick = { buzz(); finish(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("End and save") }
                Row {
                    TextButton(onClick = { stopping = false }) { Text("Keep going") }
                    TextButton(onClick = { finish(false) }) { Text("Discard this session") }
                }
            }
        }
    }
}

@Composable
fun Admin(nav: NavController) {
    var items by remember { mutableStateOf<List<Pending>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val docs by dao.docs().collectAsState(emptyList())
    val appls by dao.appls().collectAsState(emptyList())
    val contacts by dao.contacts().collectAsState(emptyList())
    LaunchedEffect(docs, appls, contacts) { withContext(Dispatchers.IO) { items = try { Engine.pending() } catch (e: Exception) { emptyList() }; loaded = true } }
    Screen("Waiting for your yes", nav) {
        Intro("admin", "Everything that needs a yes from you, in one place. One tap each. When this list is empty you are done.")
        if (loaded && items.isEmpty()) Empty("Nothing to approve. You are done here.", "Back to Home") { nav.popBackStack() }
        items.forEach { p -> IconRow(if (p.route.startsWith("doc")) Icons.Default.Edit else if (p.route.startsWith("appl")) Icons.AutoMirrored.Filled.Send else Icons.Default.Done, p.title, p.detail) { nav.navigate(p.route) } }
    }
}

@Composable
fun ShareScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember { mutableStateOf(Shared.text) }
    val image = remember { Shared.image }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Screen("Shared with Studio Agent", nav) {
        if (image != null) Note("A picture was shared (a flyer or poster).")
        Field("Text or link", text, lines = 4)
        Status(status)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; status = "Reading it..."
            scope.io({ status = it; busy = false }) {
                val t = text.value.trim()
                val isUrl = t.startsWith("http") && !t.contains(" ")
                var bytes: ByteArray? = null
                if (image != null) {
                    val f = File(ctx.cacheDir, "shared.jpg")
                    if (ImageTools.copyUri(ctx, image, f)) bytes = ImageTools.jpegBytes(f.absolutePath)
                }
                if (!G.ai.ready) {
                    // No AI key: read the page or text with plain pattern matching, then show it for a quick look.
                    val body = if (isUrl) (try { "Link: $t\n" + Fetch.pageText(t) } catch (e: Exception) { "Call from link\n$t" }) else t
                    val id = Engine.ingestPlain(if (isUrl) body.removePrefix("Link: $t\n").ifBlank { "Call from link" } + "\n" + t else body, "Shared")
                    withContext(Dispatchers.Main) { nav.navigate("call/$id") { popUpTo("home") } }
                } else {
                    val body = if (isUrl) "Link: $t\n" + Fetch.pageText(t) else t
                    val n = Engine.ingest(body.ifBlank { "See the attached picture." }, "Shared", bytes)
                    status = if (n > 0) "Added $n call" + (if (n > 1) "s" else "") + ". It is in Calls." else "No new call found in that."
                    busy = false
                }
            }
        }) { Text("Add as an open call") }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.io {
                var path = ""
                if (image != null) { val f = File(ImageTools.mediaDir(ctx), "idea_${now()}.jpg"); if (ImageTools.copyUri(ctx, image, f)) path = f.absolutePath }
                dao.put(Idea(text = text.value.trim(), photoPath = path, createdAt = now()))
                status = "Parked in Ideas."
            }
        }) { Text("Park as an idea") }
    }
}

/**
 * You: everything about you and the app, on one page. Each thing has exactly one row here,
 * and nothing on this page is reachable from another menu.
 */
@Composable
fun More(nav: NavController) {
    Screen("You", nav, back = false) {
        Section("Your words")
        IconRow(Icons.Default.Edit, "Statement, bio and CV", "Written from your own answers; nothing made up") { nav.navigate("materials") }
        Section("Progress")
        IconRow(Icons.Default.CheckCircle, "Guide", "Set-up steps, one at a time") { nav.navigate("guide") }
        IconRow(Icons.Default.Star, "Where this is going", "The five levels and the next small step") { nav.navigate("path") }
        Section("Records")
        IconRow(Icons.Default.AccountBox, "To-dos and money", "Delivery steps, costs, sales, reports") { nav.navigate("records") }
        IconRow(Icons.AutoMirrored.Filled.List, "Receipts", "Everything sent, with proof") { nav.navigate("receipts") }
        Section("Settings")
        sectionNames.forEach { (id, title, sub) -> IconRow(sectionIcons.getValue(id), title, sub) { nav.navigate("settings/$id") } }
        Section("Help")
        IconRow(Icons.Default.PlayArrow, "The tour", "How it all fits, in two minutes") { nav.navigate("tour") }
        IconRow(Icons.Default.Info, "Plain answers", "What the art-world words mean") { nav.navigate("basics") }
    }
}

data class Tile(val title: String, val sub: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun TileCard(t: Tile, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().heightIn(min = 118.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(t.icon, null, tint = cs.primary)
        Space(6)
        Text(t.title, style = MaterialTheme.typography.titleSmall)
        Text(t.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** A three-minute look at the week ahead, offered once at the weekend. One decision: which piece. */
@Composable
fun Review(nav: NavController) {
    val works by dao.artworks().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val events by dao.events().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    var pending by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { pending = withContext(Dispatchers.IO) { try { Engine.pending().size } catch (e: Exception) { 0 } } }
    val weekStart = Engine.millis(LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay())
    val mine = sessions.filter { it.start >= weekStart && it.end > it.start }
    val active = works.filter { it.status == "in_progress" }
    Screen("Next week", nav) {
        StageHead(1, "What you did", true)
        Text(if (mine.isEmpty()) "No sessions logged this week. That is information, not a verdict. If the plan did not fit your week, the plan is what should change."
        else "${mine.size} session" + (if (mine.size > 1) "s" else "") + ", ${mine.sumOf { (it.end - it.start) / 60000 }} minutes. That happened, and it counts.")
        StageHead(2, "One piece for next week", st.focusWork > 0)
        if (active.isEmpty()) Empty("Nothing in progress. Start a new piece from Studio when you are ready.", "Open Studio") { nav.navigate("works") }
        else {
            Choice("", active.map { it.title.ifBlank { "Untitled #${it.id}" } }, active.firstOrNull { tick >= 0 && it.id == st.focusWork }?.let { it.title.ifBlank { "Untitled #${it.id}" } }.orEmpty()) { t ->
                active.firstOrNull { it.title.ifBlank { "Untitled #${it.id}" } == t }?.let { st.focusWork = it.id }
            }
            Small("Start now will open straight onto it. Other ideas can wait in Ideas.")
        }
        StageHead(3, "Your studio times", false)
        val coming = events.filter { it.type == "studio" && it.date > now() }.take(5)
        if (st.pausedUntil > now()) Small("Planning is paused until ${fmtDate(st.pausedUntil)}.")
        else if (coming.isEmpty()) Small("None planned yet. They are added with the next daily check.")
        else coming.forEach { Check(false, fmtDate(it.date) + ", ${(it.endDate - it.date) / 60000} min", small = true) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("week") }) { Text("Change them") }
            if (pending > 0) OutlinedButton(onClick = { nav.navigate("admin") }) { Text("$pending to approve") }
        }
        Button(onClick = { st.lastReviewWeek = Engine.weekId(); nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done for the week") }
    }
}
