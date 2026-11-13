package com.emotionalleaves.studioagent.ui

import androidx.compose.ui.semantics.clearAndSetSemantics

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
import androidx.compose.ui.text.style.TextOverflow
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
    // Applications too: sending one or getting a result changes the next step at once.
    LaunchedEffect(works, docs, sessions, events, appls, tick) {
        withContext(Dispatchers.IO) {
            try { next = Engine.nextStep(); pendingCount = Engine.pending().size; guide = guideSteps(ctx) } catch (e: Exception) { }
            // The home-screen widget shows the same one step.
            next?.let { n -> if (n.title != st.widgetTitle || n.detail != st.widgetDetail) { st.widgetTitle = n.title; st.widgetDetail = n.detail; st.widgetRoute = n.route; com.emotionalleaves.studioagent.sys.NextWidget.refresh(ctx) } }
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
                TextButton(onClick = { G.crashFile.delete(); crashed = false }) { Text("Hide this note") }
            }
        }

        // After an update: what is new, once, in a few lines, so new features are found instead of hunted for.
        val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } } catch (e: Exception) { 0L } }
        var showNew by remember { mutableStateOf(st.onboarded && version > 0 && st.seenVersion < version) }
        // A brand-new install has nothing to compare against: it starts as seen.
        LaunchedEffect(version) { if (!st.onboarded && st.seenVersion != version) st.seenVersion = version }
        if (showNew) Panel(strong = true) {
            Text("New in this version", style = MaterialTheme.typography.titleSmall)
            whatsNew.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { st.seenVersion = version; showNew = false }, modifier = Modifier.offset(x = (-12).dp)) { Text("Got it") }
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
                            DropdownMenuItem(text = { Text(if (st.calmToday) "Show everything on Today" else "Calm Today: just this step") }, onClick = { st.calmToday = !st.calmToday; menu = false })
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
                if (pinText.value.isNotBlank()) Button(onClick = { st.pinnedStep = pinText.value.trim(); pinText.value = ""; pinning = false }) { Text("Pin it") }
                TextButton(onClick = { pinning = false }) { Text("Cancel") }
            }
        }

        if (st.onboarded) {
            if (st.sessionStart > 0) Button(onClick = { nav.navigate("session") }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) { Text("Back to your session") }
            else {
                // One tap: the app picks the piece and starts the clock. A tired day is the second button, not a question first.
                fun go(min: Int, tired: Boolean) {
                    buzz()
                    if (tired) { st.energy = "wiped"; st.energyDay = today.toString() }
                    scope.io { st.welcomedBack = now(); Engine.quickStart(min); if (st.dndInSession) Notifier.dnd(ctx, true); withContext(Dispatchers.Main) { nav.navigate("session") } }
                }
                Button(onClick = { go(st.defaultSessionMin, false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) { Text("Start now, ${st.defaultSessionMin} minutes") }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { go(5, true) }) { Text("Tired? Just 5 minutes") }
                    TextButton(onClick = { nav.navigate("session") }) { Text("Choose the piece") }
                }
            }
        }

        if (st.onboarded && !st.calmToday) {
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
                else events.firstOrNull { it.type == "studio" && it.date > now() }?.let { Small("Next studio time: ${fmtDate(it.date)}, ${clock(it.date)}") }
            }

            // The next seven days in one list: deadlines, show steps you got into, and good calls closing.
            val horizon = now() + 7 * com.emotionalleaves.studioagent.work.DAY
            val opps by dao.opps().collectAsState(emptyList())
            data class Ahead(val at: Long, val title: String, val detail: String, val route: String)
            val ahead = (events.filter { !it.done && it.date in now()..horizon && it.type in setOf("deadline", "task", "invite", "watch") }.map { e ->
                    Ahead(e.date, e.title.removePrefix("Deadline: "), when (e.type) { "deadline" -> "Deadline"; "invite" -> "Free and open to anyone; worth going"; "watch" -> "It came round about now last year; look for this year's call"; else -> opps.firstOrNull { it.id == e.oppId }?.title ?: "Your show" },
                        if (e.applId > 0) "appl/${e.applId}" else if (e.type == "invite") "outreach" else if (e.type == "watch" && opps.any { it.id == e.oppId }) "call/${e.oppId}" else "calls") } +
                opps.filter { it.status == "new" && it.fitScore >= 60 && it.deadline in now()..horizon }.map { o -> Ahead(o.deadline, o.title, "Call closes; you have not decided yet", "call/${o.id}") })
                .sortedBy { it.at }.take(5)
            if (ahead.isNotEmpty()) {
                Section("Next 7 days")
                // With reminders blocked, a deadline only reaches you if you open the app: say so once, where it matters.
                if (!Notifier.allowed(ctx)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reminders are off on this phone, so these will not reach you on their own.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { nav.navigate("settings/permissions") }) { Text("Turn on") }
                }
                ahead.forEach { a ->
                    val d = Engine.date(a.at)
                    val dayWord = when (d) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }
                    IconRow(Icons.Default.DateRange, a.title, "$dayWord. ${a.detail}") { nav.navigate(a.route) }
                }
            }
        }

        // Until the first show: the road there, as seven milestones with the next one named. It disappears once you are in a show.
        if (st.onboarded && !st.calmToday && appls.none { it.status == "accepted" }) {
            val contacts by dao.contacts().collectAsState(emptyList())
            val venues = contacts.filter { it.kind != "subscriber" }
            val stones = listOf(
                "Three pieces with pictures" to (works.count { it.imagePath.isNotBlank() } >= 3),
                "Your statement approved" to docs.any { it.type == "statement" && it.approved && it.text.isNotBlank() },
                "Five real places in People" to (venues.size >= 5),
                "Go and look at two of them" to (venues.count { it.notes.contains("Visited on") } + events.count { it.type == "opening" && it.done } >= 2),
                "Write to one" to venues.any { it.status in setOf("sent", "followed_up", "interested", "more", "not_now", "no") },
                "Send your first application" to appls.any { it.submittedAt > 0 },
                "Your first show" to false
            )
            val at = stones.indexOfFirst { !it.second }.coerceAtLeast(0)
            Section("The road to your first show")
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stones.forEachIndexed { i, (_, done) -> Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (done) MaterialTheme.colorScheme.primary else if (i == at) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHigh)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next: ${stones[at].first} (${at + 1} of ${stones.size}).", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Each milestone opens where it is done.
                val route = listOf("works", "materials", "nearby", "outreach", "outreach", "calls", "calls")[at]
                TextButton(onClick = { nav.navigate(route) }) { Text("Go") }
            }
        }

        // Proof of what got done, in plain counts. No streaks, nothing to break.
        val weekStart = Engine.millis(today.with(java.time.DayOfWeek.MONDAY).atStartOfDay())
        val weekSessions = sessions.filter { it.start >= weekStart && it.end > it.start }
        val wins = mutableListOf<String>()
        if (weekSessions.isNotEmpty()) wins += "${weekSessions.size} session" + (if (weekSessions.size > 1) "s" else "") + ", ${weekSessions.sumOf { (it.end - it.start) / 60000 }} minutes of making"
        works.count { it.createdAt >= weekStart && it.imagePath.isNotBlank() }.let { if (it > 0) wins += "$it piece" + (if (it > 1) "s" else "") + " with an image added" }
        appls.count { it.submittedAt >= weekStart }.let { if (it > 0) wins += "$it application" + (if (it > 1) "s" else "") + " sent" }
        if (wins.isNotEmpty() && !st.calmToday) {
            Section("Done this week")
            wins.forEach { Check(true, it) }
        }

        // Calm Today: only the step and Start. Everything else waits in the tabs.
        if (st.onboarded && st.calmToday) Small("Calm Today is on: just the next step. Everything else is in the tabs below.")
        if (st.onboarded && !st.calmToday) {
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
                    "Show" to "Every day it finds open calls and keeps the ones right for where you are. Say yes and it builds the application for your last look.").forEachIndexed { i, (t, d) ->
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
            Text((if (mins < 1) "Under a minute" else Texts.count(mins.toInt(), "minute")) + (works.firstOrNull { it.id == s.artworkId }?.let { " on " + it.title.ifBlank { "Untitled" } } ?: "") + ".", style = MaterialTheme.typography.headlineSmall)
            Text(if (mins < 10) "Short sessions count. You showed up." else "That counts toward your week.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The week so far, against the hours you set: progress, never a scold.
            val monday = Engine.millis(LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay())
            val weekMin = sessions.filter { it.start >= monday && it.end > it.start }.sumOf { (it.end - it.start) / 60000 }.toInt()
            if (st.weeklyHours > 0) Small("This week: " + (if (weekMin >= 60) "${weekMin / 60} h ${weekMin % 60} min" else "$weekMin min") + " of your ${st.weeklyHours} hours" + (if (weekMin >= st.weeklyHours * 60) ". The week is done; anything more is extra." else "."))
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
        var kind by rememberSaveable { mutableStateOf(if (st.energyDay == LocalDate.now().toString() && st.energy == "wiped") "light" else "make") }
        // Set once the pieces have loaded; a piece just started here stays chosen when the list grows.
        var workId by rememberSaveable { mutableStateOf(0L) }
        if (workId == 0L) (Engine.currentPiece(works) ?: active.firstOrNull())?.id?.let { workId = it }
        val work = works.firstOrNull { it.id == workId }
        Screen("Start a session", nav) {
            Section("What kind?")
            // Three kinds as rows with a line each, so the choice explains itself.
            Column {
                listOf("make" to ("Make" to "Work on a piece."), "skill" to ("Practice" to "A new medium or technique. Counts toward your week."),
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
                // Chosen by the piece itself, not its title, so two pieces with the same name stay apart.
                if (active.isNotEmpty()) Choice("Which piece?", active.map { it.id.toString() }, workId.toString(),
                    names = { idText -> active.firstOrNull { it.id.toString() == idText }?.let { w -> w.title.ifBlank { "Untitled #${w.id}" } + (if (w.status == "idea") " (idea)" else "") } ?: idText }) { picked ->
                    picked.toLongOrNull()?.let { workId = it }
                }
                TextButton(onClick = { scope.io { workId = dao.put(newWork()) } }) { Text("Start a new piece") }
            }
            // Practice without deciding what to practice: one exercise, and another if that one does not appeal.
            var n by rememberSaveable { mutableStateOf((java.time.LocalDate.now().toEpochDay() % practicePrompts.size).toInt()) }
            if (kind == "light") Panel {
                // A low-energy day still moves things: one of these, and stopping after it is a complete session.
                Small("Something small")
                Text(lightPrompts[n % lightPrompts.size], style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { n = (n + 1) % practicePrompts.size }, modifier = Modifier.offset(x = (-12).dp)) { Text("Another one") }
            }
            if (kind == "skill") {
                Panel {
                    Small("Today's exercise")
                    Text(practicePrompts[n], style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { n = (n + 1) % practicePrompts.size }, modifier = Modifier.offset(x = (-12).dp)) { Text("Another one") }
                }
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
                st.practiceText = when (kind) { "skill" -> practicePrompts[n]; "light" -> lightPrompts[n % lightPrompts.size]; else -> "" }
                // Working on an idea makes it a piece in progress.
                if (kind == "make" && work?.status == "idea") scope.io { dao.put(work.copy(status = "in_progress")) }
                if (st.dndInSession) Notifier.dnd(ctx, true)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start, " + length.replace(" min", " minutes")) }
            if (st.dndInSession && !Notifier.dndGranted(ctx)) Small("Want the phone silenced during sessions? Allow it in You, Settings, Hours and calm.")
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
            com.emotionalleaves.studioagent.media.Noise.stop()
            Notifier.sessionEnded(ctx)
            Engine.cancelGoal(ctx)
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
        var stopping by rememberSaveable { mutableStateOf(false) }
        val goal = st.sessionGoalMin
        LaunchedEffect(start, work?.title) { Notifier.session(ctx, start, work?.title?.ifBlank { "Untitled" } ?: "Light session") }
        // With the phone face down, one gentle notice says when the minutes are done. Pausing moves it; ending cancels it.
        LaunchedEffect(start, goal, paused) {
            if (paused || goal <= 0) Engine.cancelGoal(ctx)
            else Engine.scheduleGoal(ctx, goal * 60_000L - Engine.sessionElapsed(now()), goal)
        }
        Screen("In session", nav, bare = true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (paused) "Paused" else "In session", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }) { Icon(Icons.Default.Close, "Leave this screen; the timer keeps going", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val track = MaterialTheme.colorScheme.surfaceContainerHigh
            val fill = MaterialTheme.colorScheme.primary
            val part = if (goal > 0) (secs / (goal * 60f)).coerceIn(0f, 1f) else 0f
            // A ring that fills as time passes: the whole session visible at a glance.
            // A screen reader hears the time in words, not a ticking clock.
            val spoken = "${Texts.count((secs / 60).toInt(), "minute")} in" + (if (goal > 0 && secs < goal * 60L) ", ${Texts.count(Math.ceil((goal * 60L - secs) / 60.0).toInt(), "minute")} to go" else "")
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
                val shown by animateFloatAsState(part, tween(900), label = "ring")
                // Smaller when the phone is sideways, so the step under it stays in view.
                val ringSize = minOf(250, (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.55f).toInt()).dp
                Canvas(Modifier.size(ringSize)) {
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
            if (work == null && st.sessionKind in setOf("skill", "light") && st.practiceText.isNotBlank()) Text(st.practiceText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)

            if (work != null) {
                val steps = Texts.steps(work.steps)
                val open = steps.indexOfFirst { !it.first }
                if (open >= 0) WallLabel(onClick = null) {
                    Text("Now: ${steps[open].second}", style = MaterialTheme.typography.titleLarge)
                    Small("${steps.count { it.first }} of ${steps.size} small steps done")
                    TextButton(onClick = { scope.io { dao.put(work.copy(steps = Texts.stepsText(steps.mapIndexed { i, p -> if (i == open) true to p.second else p }))) } }, modifier = Modifier.offset(x = (-12).dp)) { Text("Done, show the next one") }
                } else if (steps.isNotEmpty() && work.status == "in_progress") WallLabel(onClick = null) {
                    // Every step ticked: the finish line decides, not another pass.
                    Text("Every small step is done.", style = MaterialTheme.typography.titleLarge)
                    Small(if (work.doneWhen.isNotBlank()) "Finished when: ${work.doneWhen}" else "If it looks done to you, it is done.")
                    TextButton(onClick = { scope.io { dao.put(work.copy(status = "finished")) } }, modifier = Modifier.offset(x = (-12).dp)) { Text("Call it finished") }
                } else if (work.nextStep.isNotBlank()) Small("Where you left off: ${work.nextStep}")
                if (work.doneWhen.isNotBlank() && (open >= 0 || steps.isEmpty())) Small("Finished when: ${work.doneWhen}")
            }
            if (Planner.isWindDown(LocalDateTime.now(), Planner.time(st.bedtime, LocalTime.of(22, 30)))) Panel(strong = true) {
                Text("Wind-down time. A good place to stop: write your next step so nothing is lost.")
            }
            if (!stopping) {
                Space(8)
                // One big way out, one quiet way to pause. Stacked, so long words never get squeezed.
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = { stopping = true }) { Text("I am stopping") }
                OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                    if (paused) { st.sessionPausedTotal = st.sessionPausedTotal + (now() - st.sessionPausedAt); st.sessionPausedAt = 0L; if (st.sessionNoise) com.emotionalleaves.studioagent.media.Noise.start() }
                    else { st.sessionPausedAt = now(); com.emotionalleaves.studioagent.media.Noise.stop() }
                }) { Text(if (paused) "Resume" else "Pause") }
                Text("Put the phone down. The timer keeps going.", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // A steady hum to settle into: on or off with one tap, remembered for next time.
                var hum by remember { mutableStateOf(com.emotionalleaves.studioagent.media.Noise.playing) }
                LaunchedEffect(Unit) { if (st.sessionNoise && !hum && !paused) { com.emotionalleaves.studioagent.media.Noise.start(); hum = true } }
                TextButton(onClick = {
                    if (hum) com.emotionalleaves.studioagent.media.Noise.stop() else com.emotionalleaves.studioagent.media.Noise.start()
                    hum = !hum; st.sessionNoise = hum
                }, modifier = Modifier.fillMaxWidth()) { Text(if (hum) "Turn the background hum off" else "Play a background hum (brown noise)") }
            } else {
                // Back here means "not yet", not "leave".
                BackHandler { stopping = false }
                H("Before you go")
                Field("Next time, start with…", nextStep, mic = true)
                // One line is all that is asked. A note or photo is there if wanted, folded away.
                Expand("Add a note or a progress photo", startOpen = note.value.isNotBlank() || photo.isNotBlank()) {
                    Field("Anything to note", note, lines = 2)
                    if (photo.isNotBlank()) PathImage(photo, Modifier.fillMaxWidth().height(160.dp))
                    OutlinedButton(onClick = { try { take.launch(Share.uri(ctx, photoFile)) } catch (e: Exception) { } }) { Text(if (photo.isBlank()) "Take a progress photo" else "Retake photo") }
                }
                Button(onClick = { buzz(); finish(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("End and save") }
                Row {
                    TextButton(onClick = { stopping = false }) { Text("Keep going") }
                    Spacer(Modifier.weight(1f))
                    // Throwing away real time takes a second tap; a stray one-minute start goes at once.
                    var sure by remember { mutableStateOf(false) }
                    val mins = (secs / 60).toInt()
                    DangerButton(onClick = { if (mins < 5 || sure) finish(false) else sure = true }) { Text(if (sure) "Tap again to discard $mins minutes" else "Discard this session") }
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
        if (loaded && items.isEmpty()) Empty("Nothing to approve. You are done here.", "Back to Today") { nav.popBackStack() }
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
    val many = remember { Shared.images }
    if (many.size > 1) {
        // Several pictures at once are almost always your own work: one button makes a piece from each.
        Screen("Shared with Studio Agent", nav) {
            Text("${Texts.count(many.size, "picture")} shared.", style = MaterialTheme.typography.titleMedium)
            Small("Each becomes a piece in Studio, titled from its file name, with its picture on it. You can change anything after.")
            Status(status)
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; status = "Adding ${Texts.count(many.size, "piece")}…"
                scope.io({ status = it; busy = false }) {
                    var ok = 0
                    many.forEachIndexed { i, u ->
                        val name = try { ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } } catch (e: Exception) { null } ?: "Untitled ${i + 1}"
                        try { Engine.importFile(ctx, u, name); ok++ } catch (e: Exception) { }
                    }
                    withContext(Dispatchers.Main) { nav.navigate("works") { popUpTo("home") } }
                }
            }) { Text("Add ${Texts.count(many.size, "piece")} to Studio") }
        }
        return
    }
    Screen("Shared with Studio Agent", nav) {
        if (image != null) {
            Note("A picture was shared.")
            Small("A photo of a flyer: tap Add as an open call. The words are read on your phone, and the name, deadline and fee are picked out. Anything it misses, you fill in after.")
        }
        Field("Text or link", text, lines = 4)
        Status(status)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; status = "Reading it…"
            scope.io({ status = it; busy = false }) {
                val t0 = text.value.trim()
                val isUrl = Texts.isLink(t0)
                val t = if (isUrl && !t0.startsWith("http")) "https://$t0" else t0
                // A shared picture is read like a flyer photo: the words are read on the phone first.
                if (image != null && !isUrl) {
                    val f = File(ctx.cacheDir, "shared.jpg")
                    if (ImageTools.copyUri(ctx, image, f)) {
                        val (id, _) = Engine.callFromPhoto(ctx, f.absolutePath, "Shared")
                        if (t.isNotBlank()) dao.oppsNow().firstOrNull { it.id == id }?.let { o -> dao.put(o.copy(rawText = (o.rawText + "\n" + t).trim())) }
                        withContext(Dispatchers.Main) { nav.navigate("call/$id") { popUpTo("home") } }
                        return@io
                    }
                }
                val bytes: ByteArray? = null
                val page = if (isUrl) (try { Fetch.pageText(t) } catch (e: Exception) { "" }) else ""
                val before = dao.oppsNow().maxOfOrNull { it.id } ?: 0L
                // Writing help reads it fully; if it is off or does not answer, the plain reader still picks out deadline, fee and link.
                val n = if (G.ai.ready) try { Engine.ingest((if (isUrl) "Link: $t\n$page" else t).ifBlank { "See the attached picture." }, "Shared", bytes) } catch (e: Exception) { -1 } else -1
                if (n == 1) {
                    // One call: open it, so the next step (yes, no, or fill a gap) is right there.
                    val id = dao.oppsNow().filter { it.id > before }.maxByOrNull { it.id }?.id
                    withContext(Dispatchers.Main) { if (id != null) nav.navigate("call/$id") { popUpTo("home") } else nav.navigate("calls") { popUpTo("home") } }
                }
                else if (n > 1) { status = "Added $n calls. They are in Calls."; busy = false }
                else {
                    val id = Engine.ingestPlain(if (isUrl) page.ifBlank { "Call from link" } + "\n" + t else t.ifBlank { "Shared call" }, "Shared")
                    withContext(Dispatchers.Main) { nav.navigate("call/$id") { popUpTo("home") } }
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
        // A picture of your own work becomes a piece, with the picture already on it.
        if (image != null) OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true
            scope.io({ status = it; busy = false }) {
                val id = Engine.importFile(ctx, image, text.value.trim().ifBlank { "Untitled" })
                withContext(Dispatchers.Main) { nav.navigate("work/$id") { popUpTo("home") } }
            }
        }) { Text("Add as a piece of my work") }
    }
}

/**
 * You: everything about you and the app, on one page. Each thing has exactly one row here,
 * and nothing on this page is reachable from another menu.
 */
@Composable
fun More(nav: NavController) {
    val ctx = LocalContext.current
    val tick by st.changed.collectAsState()
    var steps by remember { mutableStateOf<List<GuideStep>?>(null) }
    LaunchedEffect(tick) { steps = withContext(Dispatchers.IO) { try { guideSteps(ctx) } catch (e: Exception) { emptyList() } } }
    val next = steps?.firstOrNull { !it.done && !it.optional }
    Screen("You", nav, back = false) {
        // The one set-up step that matters next, with its own button; the full Guide is one tap behind it.
        if (next != null) Panel(strong = true) {
            Small("Next set-up step, ${steps!!.count { it.done && !it.optional } + 1} of ${steps!!.count { !it.optional }}")
            Text(next.title, style = MaterialTheme.typography.titleMedium)
            Text(next.why, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { nav.navigate(next.route) }) { Text(next.button) }
                TextButton(onClick = { nav.navigate("guide") }) { Text("All steps") }
            }
        }
        // The studio at a glance: four real counts, each opening the place it counts.
        val works by dao.artworks().collectAsState(emptyList())
        val appls by dao.appls().collectAsState(emptyList())
        val contacts by dao.contacts().collectAsState(emptyList())
        val docs by dao.docs().collectAsState(emptyList())
        val facts by dao.facts().collectAsState(emptyList())
        val ledger by dao.ledger().collectAsState(emptyList())
        val year = LocalDate.now().year
        val ready = works.count { Engine.available(it) }
        val sent = appls.count { it.submittedAt > 0 }; val inShows = appls.count { it.status == "accepted" }
        val venues = contacts.count { it.kind != "subscriber" }; val list = contacts.count { it.kind == "subscriber" }
        val sold = ledger.filter { it.kind == "sale" && Engine.date(it.date).year == year }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), "$ready", if (ready == 1) "piece ready to send" else "pieces ready to send") { nav.navigate("works") }
                Tile(Modifier.weight(1f), "$sent", "sent" + if (inShows > 0) ", $inShows got in" else "") { nav.navigate("calls") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), "$venues", (if (venues == 1) "venue" else "venues") + if (list > 0) ", $list on your list" else "") { nav.navigate("outreach") }
                Tile(Modifier.weight(1f), if (sold.isEmpty()) "0" else money(sold.sumOf { it.amount }), if (sold.isEmpty()) "sales in $year" else Texts.count(sold.size, "sale") + " in $year") { nav.navigate("records") }
            }
        }
        IconRow(Icons.Default.Search, "Find anything", "A piece, a call, a venue, a person, a document") { nav.navigate("search") }
        val statementOk = docs.any { it.type == "statement" && it.approved && it.text.isNotBlank() }
        IconRow(Icons.Default.Edit, "Statement, bio and CV", (if (statementOk) "Statement approved" else "Statement not approved yet") + ", " + Texts.count(facts.size, "CV line")) { nav.navigate("materials") }
        IconRow(Icons.Default.Person, "People and venues", if (venues == 0) "Find real places near you to show" else "Places to show, and emails to them") { nav.navigate("outreach") }
        IconRow(Icons.Default.AccountBox, "Records", "To-dos, money, sales, and proof of what was sent") { nav.navigate("records") }
        if (steps != null && next == null) IconRow(Icons.Default.CheckCircle, "Guide", "Set-up is done. The five levels, and where you are") { nav.navigate("guide") }
        IconRow(Icons.Default.Settings, "Settings", "Your details, hours, rules, writing help, email, backup") { nav.navigate("settings") }
        IconRow(Icons.Default.Info, "Help", "The tour, plain answers, about this app") { nav.navigate("basics") }
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
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // No studio times ahead and not paused: plan them now, instead of saying they come later.
    var planned by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { try { if (st.pausedUntil <= now() && dao.eventsNow().none { it.type == "studio" && it.date > now() }) Engine.replan(ctx) } catch (e: Exception) {} }
        planned = true
    }
    Screen("Next week", nav) {
        StageHead(1, "What you did", true)
        // The month so far, counted plainly: proof that it is moving, without streaks to break.
        val monthStart = Engine.millis(LocalDate.now().withDayOfMonth(1).atStartOfDay())
        val applsAll by dao.appls().collectAsState(emptyList())
        val monthLine = listOfNotNull(
            works.count { it.imagePath.isNotBlank() && it.createdAt >= monthStart }.takeIf { it > 0 }?.let { Texts.count(it, "piece") + " added with a picture" },
            applsAll.count { it.submittedAt >= monthStart }.takeIf { it > 0 }?.let { Texts.count(it, "application") + " sent" },
            sessions.filter { it.start >= monthStart && it.end > it.start }.let { l -> if (l.isEmpty()) null else "${l.sumOf { (it.end - it.start) / 60000 } / 60} hours of making" }
        )
        if (monthLine.isNotEmpty()) Small("This month: " + monthLine.joinToString(", ") + ".")
        Text(if (mine.isEmpty()) "No sessions logged this week. That is information, not a verdict. If the plan did not fit your week, the plan is what should change."
        else "${mine.size} session" + (if (mine.size > 1) "s" else "") + ", ${mine.sumOf { (it.end - it.start) / 60000 }} minutes. That happened, and it counts.")
        // The plan bends to your life: a planned time you never use, and a time you keep working anyway.
        val plan = Planner.parseTemplate(st.weekTemplate).ifEmpty { Planner.suggestedTemplate(Planner.days(st.workDays), Planner.time(st.workEnd, LocalTime.of(15, 30)), st.bufferMin, Planner.time(st.bedtime, LocalTime.of(22, 30)), st.weeklyHours) }
        val starts = sessions.filter { it.end > it.start }.map { java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
        var moved by remember { mutableStateOf(false) }
        Planner.betterSlot(plan, starts, LocalDate.now())?.let { (from, to) ->
            if (!moved) Panel(strong = true) {
                val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                val fmt = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                Text("You never used ${names[from.day - 1]} at ${from.time.format(fmt)}, but you often work on ${names[to.day - 1]}s around ${to.time.format(fmt)}.")
                Small("Moving the plan to match is not giving up; it is the plan learning your week.")
                Button(onClick = {
                    st.weekTemplate = Planner.formatTemplate(plan.map { if (it == from) to else it }); moved = true
                    scope.io { Engine.replan(ctx) }
                }) { Text("Move it to ${names[to.day - 1]}") }
            }
        }
        // A plan you keep missing is a plan that is too big: after four weeks, offer the size you actually work.
        if (st.weekTemplate.isBlank()) {
            val since = now() - 28 * com.emotionalleaves.studioagent.work.DAY
            val avg = sessions.filter { it.start >= since && it.end > it.start }.sumOf { (it.end - it.start) / 3_600_000.0 } / 4
            val firstEver = sessions.minOfOrNull { it.start } ?: now()
            val suggest = kotlin.math.max(1, kotlin.math.round(avg).toInt())
            var sized by remember { mutableStateOf(false) }
            if (!sized && firstEver < since && avg > 0 && suggest < st.weeklyHours) Panel {
                Text("The last four weeks averaged ${"%.1f".format(avg)} hours a week; the plan asks for ${st.weeklyHours}.")
                Small("A plan you can keep beats a bigger one. You can raise it again any time.")
                OutlinedButton(onClick = { st.weeklyHours = suggest; sized = true; scope.io { Engine.replan(ctx) } }) { Text("Plan for $suggest " + (if (suggest == 1) "hour" else "hours") + " a week") }
            }
        }
        StageHead(2, "One piece for next week", st.focusWork > 0)
        if (active.isEmpty()) Empty("Nothing in progress. Start a new piece from Studio when you are ready.", "Open Studio") { nav.navigate("works") }
        else {
            Choice("", active.map { it.id.toString() }, if (tick >= 0 && active.any { it.id == st.focusWork }) st.focusWork.toString() else "",
                names = { i -> active.firstOrNull { it.id.toString() == i }?.let { it.title.ifBlank { "Untitled #${it.id}" } } ?: i }) { i ->
                i.toLongOrNull()?.let { st.focusWork = it }
            }
            Small("Start now will open straight onto it. Other ideas can wait in Ideas.")
        }
        StageHead(3, "Your studio times", false)
        val coming = events.filter { it.type == "studio" && it.date > now() }.take(5)
        if (st.pausedUntil > now()) Small("Planning is paused until ${fmtDate(st.pausedUntil)}.")
        else if (coming.isEmpty()) Small(if (planned) "None fit the week as it is set. Change studio times below." else "Planning them now…")
        else coming.forEach { Check(false, fmtDate(it.date) + ", " + clock(it.date) + ", ${(it.endDate - it.date) / 60000} min", small = true) }
        OutlinedButton(onClick = { nav.navigate("week") }, modifier = Modifier.fillMaxWidth()) { Text("Change studio times") }
        if (pending > 0) OutlinedButton(onClick = { nav.navigate("admin") }, modifier = Modifier.fillMaxWidth()) { Text("Look at ${Texts.count(pending, "thing")} waiting for your yes") }
        Button(onClick = { st.lastReviewWeek = Engine.weekId(); nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done for the week") }
    }
}

/** One count on the You page: a big number, what it counts, and a tap to where it lives. */
@Composable
fun Tile(modifier: Modifier, number: String, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerLowest).clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }
        .semantics(mergeDescendants = true) { }.padding(14.dp).heightIn(min = 72.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(number, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
    }
}

/**
 * Short exercises for practice sessions, chosen for an artist moving from screens to paper and paint:
 * each is one sitting, needs little, and is fine to do badly.
 */
val practicePrompts = listOf(
    "Ten one-minute drawings of the same mug from ten angles. Speed over neatness.",
    "A value study: one object in pencil, using only three tones (light, middle, dark).",
    "Mix a chart of one color with white in five steps, then with its opposite in five steps.",
    "Draw your hand five times without lifting the pen.",
    "Copy one small part of a painting you love, as closely as you can, in 20 minutes.",
    "Cut five shapes from paper scraps and arrange them three different ways. Photograph each.",
    "Draw the view from a window in 15 minutes, then again in 5.",
    "Paint a small square of something in the kitchen at the color it really is, not the color you know it is.",
    "Make a page of textures with one pen: hatching, stippling, scribbles, dashes.",
    "Draw something in the dark corners of a room: only the shadows, not the outlines.",
    "Print one of your digital pieces small on plain paper and draw over it by hand.",
    "Ten thumbnails (thumbnail-sized sketches) of compositions for your next piece.",
    "Draw with your other hand for ten minutes. Notice what you see instead of what you know.",
    "A one-color painting: one paint plus water or white, nothing else.",
    "Fill a page with one shape repeated, changing it a little each time.",
    "Draw a coworker's tools, a pan, a knife, a ladle, from memory. Then from life, if you can.",
    "Make a small collage from a magazine page or packaging. Fifteen minutes, no undo.",
    "Trace the shadows of a plant on paper with a lamp, then fill them in.",
    "Draw the same object at three sizes: tiny, hand-sized, as big as the page.",
    "Look at one of your finished pieces for five minutes and write three things you would keep and one you would change."
)

/** Small things for a tired day: real progress, little energy, nothing to decide. */
val lightPrompts = listOf(
    "Photograph one finished piece for the catalog, by a window.",
    "Sort ten reference pictures into keep and delete.",
    "Write the next small step for the piece you are on.",
    "Tidy the desk so tomorrow starts with a clear surface.",
    "Look through your ideas and park one more.",
    "Sketch for fifteen minutes. Anything. Throw it away if you like.",
    "Measure one finished piece and type its size into the app.",
    "Read one open call in Calls and say Yes or No.",
    "Prime or prepare three surfaces for later.",
    "Put on music and look at your series side by side for ten minutes."
)

/** The few changes worth knowing about, shown once after an update. Kept short: the newest and most useful first. */
val whatsNew = listOf(
    "People: Find places near me shows real galleries, libraries, print labs and framers, and a walk past the ones you have not seen.",
    "Calls come from eight sites, including CaFÉ. Photograph a flyer in Calls to add one; calls open only to other states are held back.",
    "A venue said yes? Plan the show (or a one-day open studio): dated steps, flyer with a QR code, labels, an agreement, and a code to scan at the opening.",
    "Your week has a month calendar that learns when you really work, and a Next step widget for your home screen.",
    "Find anything, on You, finds a piece, a call, a person or a document by any word."
)
