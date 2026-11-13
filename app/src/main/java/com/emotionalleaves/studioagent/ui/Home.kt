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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.produceState
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
    // Calm: for today only, or for the quiet days after "It is all too much".
    val quietDays = tick >= 0 && st.calmUntil > System.currentTimeMillis()
    val calm = st.calmToday || quietDays
    // Cards you have chosen not to see on Today (Settings, Hours and calm).
    fun shows(card: String) = card !in st.todayHidden.split(",")
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

        // The easel: today's one step, painted over your own work, with Start right under it.
        val n = next
        LaunchedEffect(works.size) { withContext(Dispatchers.IO) { try { Engine.refreshSeed() } catch (e: Exception) { } } }
        fun go(min: Int, tired: Boolean) {
            buzz()
            if (tired) { st.energy = "wiped"; st.energyDay = today.toString() }
            scope.io { st.welcomedBack = now(); Engine.quickStart(min); if (st.dndInSession) Notifier.dnd(ctx, true); withContext(Dispatchers.Main) { nav.navigate("session") } }
        }
        Easel(
            title = n?.title ?: "Finding your next step", detail = n?.detail.orEmpty(), piece = Engine.heroPiece(works),
            dateLine = "Your next step",
            minutes = st.defaultSessionMin, inSession = st.sessionStart > 0,
            onOpen = { n?.let { if (it.route != "home") nav.navigate(it.route) } },
            onStart = { if (st.onboarded) go(st.defaultSessionMin, false) else nav.navigate("tour") },
            onTired = { if (st.onboarded) go(5, true) else nav.navigate("tour") },
            onSession = { nav.navigate("session") },
            menu = {
            if (st.onboarded) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.offset(x = 12.dp, y = (-10).dp)) { Icon(Icons.Default.MoreVert, "Change today's step", tint = androidx.compose.ui.graphics.Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (st.pinnedStep.isNotBlank()) DropdownMenuItem(text = { Text("Done, clear my pin") }, onClick = { st.pinnedStep = ""; menu = false })
                        else {
                            if (st.snoozeDay == today.toString()) DropdownMenuItem(text = { Text("Bring today's step back") }, onClick = { st.snoozeDay = ""; menu = false })
                            else DropdownMenuItem(text = { Text("Not today") }, onClick = { st.snoozeRun = if (st.snoozeDay == today.minusDays(1).toString()) st.snoozeRun + 1 else 1; st.snoozeDay = today.toString(); menu = false })
                            DropdownMenuItem(text = { Text("Empty my head first") }, onClick = { menu = false; nav.navigate("dump") })
                            n?.let { step -> DropdownMenuItem(text = { Text("Remind me this evening") }, onClick = { menu = false
                                // Said back plainly, so there is no need to check that it worked; Undo cancels it.
                                scope.undoable("Reminder set for " + Engine.laterTime(), { Engine.remindLater(ctx, step.title, step.route) }, { Engine.cancelLater(ctx) }) }) }
                            // When it is all too much: three quiet days, the step shrinks to five minutes, everything else waits.
                            DropdownMenuItem(text = { Text("It is all too much") }, onClick = { st.calmUntil = System.currentTimeMillis() + 3 * 86_400_000L; menu = false })
                            DropdownMenuItem(text = { Text("Pin my own step") }, onClick = { pinning = true; menu = false })
                            DropdownMenuItem(text = { Text(if (calm) "Show everything on Today" else "Calm Today: just this step") }, onClick = { if (calm) { st.calmToday = false; st.calmUntil = 0 } else st.calmToday = true; menu = false })
                        }
                    }
                }
            }
        )
        if (st.onboarded && st.sessionStart == 0L) TextButton(onClick = { nav.navigate("session") }, modifier = Modifier.offset(x = (-12).dp)) { Text("Choose the piece or the kind of session") }

        // After an update: what is new, once, in a few lines, so new features are found instead of hunted for.
        val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } } catch (e: Exception) { 0L } }
        var showNew by remember { mutableStateOf(st.onboarded && version > 0 && st.seenVersion < version) }
        // A brand-new install has nothing to compare against: it starts as seen.
        LaunchedEffect(version) { if (!st.onboarded && st.seenVersion != version) st.seenVersion = version }
        // One line, so today's step stays at the top of the screen; the list opens only when asked.
        var openNew by remember { mutableStateOf(false) }
        if (showNew) Panel(strong = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The line itself opens the list, so two short buttons never squeeze it at large text sizes.
                Text("New in this version: " + Texts.count(whatsNew.size, "thing") + if (openNew) "" else " (show)", Modifier.weight(1f).clickable(onClickLabel = if (openNew) "Hide what is new" else "Show what is new") { openNew = !openNew }.padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { st.seenVersion = version; showNew = false }) { Text("Got it") }
            }
            if (openNew) whatsNew.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        // On a break at work: small jobs that fit in five minutes on the phone, so the evening is for making.
        val atWork = remember { val n0 = java.time.LocalDateTime.now(); n0.dayOfWeek.value in Engine.workDaysNow() &&
            !n0.toLocalTime().isBefore(com.emotionalleaves.studioagent.domain.Planner.time(st.workStart, java.time.LocalTime.of(7, 0))) && n0.toLocalTime().isBefore(com.emotionalleaves.studioagent.domain.Planner.time(st.workEnd, java.time.LocalTime.of(15, 30))) }
        if (st.onboarded && atWork && !calm && shows("jobs")) {
            val opps0 by dao.opps().collectAsState(emptyList())
            val toDecide = opps0.count { it.status == "new" && it.fitScore >= 60 && it.deadline > now() }
            val jobs = listOfNotNull(
                if (toDecide > 0) Triple("Say yes or no to ${Texts.count(toDecide, "call")}", "One at a time, about a minute each", "decide") else null,
                if (pendingCount > 0) Triple("Approve ${Texts.count(pendingCount, "email")}", "One tap each", "admin") else null,
                Triple("Park an idea you had today", "A line, or a photo", "ideas"),
                Triple("Read a call's page in plain words", "Then decide tonight", "calls")
            ).take(3)
            Panel {
                Text("On a break? Five-minute jobs", style = MaterialTheme.typography.titleSmall)
                // Each job wears what it is about; a tick would read as already done.
                jobs.forEach { (t, d, r) -> IconRow(when { r.startsWith("call") || r == "decide" -> Icons.Default.Search; r == "admin" || r.startsWith("outreach") || r.startsWith("letters") || r.startsWith("contact") -> Icons.Default.Email; r.startsWith("idea") || r == "dump" -> Icons.Default.Edit; r.startsWith("work") || r == "photoshoot" -> Icons.Default.Star; else -> Icons.AutoMirrored.Filled.KeyboardArrowRight }, t, d) { nav.navigate(r) } }
            }
        }
        // What the app did while you were away, in a few lines: the work done for you, seen without hunting.
        val tickA by st.changed.collectAsState()
        val fresh = remember(tickA) { st.activityLines().filter { it.first > st.activitySeen && !it.second.startsWith("Daily check finished") && !it.second.startsWith("Checked your inbox") } }
        if (st.onboarded && fresh.isNotEmpty() && !calm && shows("fresh")) Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("While you were away", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { st.activitySeen = fresh.maxOf { it.first } }) { Text("Got it") }
            }
            fresh.take(3).forEach { Small("• " + it.second) }
            if (fresh.size > 3) Small("and ${fresh.size - 3} more, in Records.")
        }
        // A first, on the day it happens: a sentence to let it land, then "Lovely" puts it away.
        // Read from the database itself, so a screen still loading never looks like a studio with nothing in it.
        val reachedNow by produceState<List<com.emotionalleaves.studioagent.domain.Firsts.First>?>(null, works.size, sessions.size, appls.size, ledger.size, facts.size, tick) {
            value = withContext(Dispatchers.IO) { try { Engine.firstsReached() } catch (e: Exception) { null } }
        }
        val reached = reachedNow
        if (st.onboarded && !calm && reached != null) {
            // The first time this runs, firsts from before are taken as seen, so nothing old is celebrated.
            if (!st.firstsInit) { st.firstsSeen = com.emotionalleaves.studioagent.domain.Firsts.markAll(reached, "before"); st.firstsInit = true; st.firstsVer = 3 }
            // Firsts added in a later version: whatever was already reached before then is taken as seen too.
            if (st.firstsVer < 2) { reached.filter { it.key in setOf("opening", "venue") }.forEach { st.firstsSeen = com.emotionalleaves.studioagent.domain.Firsts.mark(st.firstsSeen, it.key, "before") }; st.firstsVer = 2 }
            if (st.firstsVer < 3) { reached.filter { it.key in setOf("basics", "body") }.forEach { st.firstsSeen = com.emotionalleaves.studioagent.domain.Firsts.mark(st.firstsSeen, it.key, "before") }; st.firstsVer = 3 }
            val todayS = LocalDate.now().toString()
            com.emotionalleaves.studioagent.domain.Firsts.toShow(reached, st.firstsSeen, todayS)?.let { f ->
                LaunchedEffect(f.key) { st.firstsSeen = com.emotionalleaves.studioagent.domain.Firsts.mark(st.firstsSeen, f.key, todayS) }
                Panel(strong = true) {
                    Text(f.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(f.line)
                    Row {
                        TextButton(onClick = { st.firstsSeen = com.emotionalleaves.studioagent.domain.Firsts.mark(st.firstsSeen, f.key, "done") }) { Text("Lovely") }
                        if (st.buddy.isNotBlank()) TextButton(onClick = { Share.text(ctx, f.title + "!") }) { Text("Tell ${st.buddy}") }
                    }
                }
            }
        }
        // Back after a week or more away: nothing is late from here, and the week is planned again from today.
        if (st.onboarded && st.awayDays >= 7) Panel(strong = true) {
            Text("Welcome back", fontWeight = FontWeight.SemiBold)
            Text("It has been ${st.awayDays} days. Nothing is lost, and nothing is overdue from here: what went by has gone by, and any to-dos that slipped are spread over the coming week. One step today is plenty.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { st.awayDays = 0; scope.io { Engine.spreadOverdue(); Engine.replan(ctx) } }) { Text("Thanks, plan my week from today") }
        }
        // The day after quiet days end: a soft way back in, not a pile of everything that waited.
        if (st.onboarded && !calm && st.calmUntil in (System.currentTimeMillis() - 86_400_000L)..System.currentTimeMillis())
            Small("Welcome back. Just the one step below today; everything else will find its place over the next few days.")
        // Three "Not today"s in a row is a signal, not a failing: offer the quiet days, once, and let it go either way.
        if (st.onboarded && !calm && st.snoozeRun >= 3 && st.snoozeDay != today.toString()) Panel(strong = true) {
            Text("Three days of \"Not today\"", fontWeight = FontWeight.SemiBold)
            Text("That is allowed. Would three quiet days help: one five-minute step, no notifications, nothing else asked of you?", style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { st.calmUntil = System.currentTimeMillis() + 3 * 86_400_000L; st.snoozeRun = 0 }) { Text("Yes, quiet days") }
                TextButton(onClick = { st.snoozeRun = 0 }) { Text("No, I'm all right") }
            }
        }
        if (!st.onboarded) TextButton(onClick = { st.setupDone = true; st.tourSeen = true; nav.navigate("home") { popUpTo("home") { inclusive = true } } }) { Text("Skip the tour and start") }
        if (pinning) {
            Field("What do you want to see here instead?", pinText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pinText.value.isNotBlank()) Button(onClick = { st.pinnedStep = pinText.value.trim(); pinText.value = ""; pinning = false }) { Text("Pin it") }
                TextButton(onClick = { pinning = false }) { Text("Cancel") }
            }
        }

        if (st.onboarded && !calm && shows("week")) {
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

            // From your own wall: one finished piece a day, a quiet reminder of what the work is for.
            val wall = works.filter { it.imagePath.isNotBlank() && it.status !in setOf("idea", "in_progress") }
            if (wall.size >= 3 && shows("wall")) wall[(LocalDate.now().toEpochDay() % wall.size).toInt()].let { w ->
                Column(Modifier.fillMaxWidth().clickable { nav.navigate("work/${w.id}") }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PathImage(w.imagePath, Modifier.fillMaxWidth().height(180.dp), 700, description = "From your wall: " + w.title)
                    Small("From your wall: " + w.title.ifBlank { "Untitled" } + (if (w.year > 0) ", ${w.year}" else ""))
                }
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
        if (st.onboarded && !calm && shows("road") && appls.none { it.status == "accepted" }) {
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
        // The quieter work counts too: emails to venues, and ideas parked so they stop circling.
        val contactsWk by dao.contacts().collectAsState(emptyList())
        val ideasWk by dao.ideas().collectAsState(emptyList())
        contactsWk.count { it.sentAt >= weekStart }.let { if (it > 0) wins += Texts.count(it, "email") + " to venues sent" }
        ideasWk.count { it.createdAt >= weekStart }.let { if (it > 0) wins += Texts.count(it, "idea") + " parked" }
        if (wins.isNotEmpty() && !calm && shows("wins")) {
            Section("Done this week")
            wins.forEach { Check(true, it) }
            if (st.buddy.isNotBlank() && today.dayOfWeek.value >= 5) TextButton(onClick = { Share.text(ctx, "My art this week: " + wins.joinToString("; ") + ". How was yours?") }, modifier = Modifier.offset(x = (-12).dp)) { Text("Tell ${st.buddy} about your week") }
        }

        // Calm Today: only the step and Start. Everything else waits in the tabs.
        if (st.onboarded && quietDays) {
            Small("Quiet days until " + Engine.date(st.calmUntil).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + ": one small step, no notifications, nothing else asked of you.")
            TextButton(onClick = { st.calmUntil = 0; st.calmToday = false }) { Text("I'm ready for more") }
        } else if (st.onboarded && calm) Small("Calm Today is on: just the next step. Everything else is in the tabs below.")
        if (st.onboarded && !calm) {
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
    val scope = rememberCoroutineScope()
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
            if (st.buddy.isNotBlank()) { val ctx = LocalContext.current; TextButton(onClick = { Share.text(ctx, "Done: " + (if (mins < 1) "a few minutes" else Texts.count(mins.toInt(), "minute")) + " on my art. How did yours go?") }, modifier = Modifier.offset(x = (-12).dp)) { Text("Tell ${st.buddy} it's done") } }
            // The week so far, against the hours you set: progress, never a scold.
            val monday = Engine.millis(LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay())
            val weekMin = sessions.filter { it.start >= monday && it.end > it.start }.sumOf { (it.end - it.start) / 60000 }.toInt()
            if (st.weeklyHours > 0) Small("This week: " + (if (weekMin >= 60) "${weekMin / 60} h ${weekMin % 60} min" else "$weekMin min") + " of your ${st.weeklyHours} hours" + (if (weekMin >= st.weeklyHours * 60) ". The week is done; anything more is extra." else "."))
            // Far more sessions than your pieces usually take: it may already be done. Your call, asked once.
            works.firstOrNull { it.id == s.artworkId && it.status == "in_progress" }?.let { w ->
                val made = sessions.filter { it.end > it.start && it.artworkId > 0 }
                val finished = works.filter { it.status !in setOf("idea", "in_progress") }.map { f -> made.count { it.artworkId == f.id } }.filter { it > 0 }
                val here = made.count { it.artworkId == w.id }
                val usual = com.emotionalleaves.studioagent.domain.GoodEnough.usual(finished)
                var asked by remember(w.id) { mutableStateOf(false) }
                if (!asked && com.emotionalleaves.studioagent.domain.GoodEnough.overdue(here, usual)) Panel {
                    Text("Is it done?", fontWeight = FontWeight.SemiBold)
                    Text("${Texts.count(here, "session")} on this one; your pieces usually take about $usual." + (if (w.doneWhen.isNotBlank()) " Your finish line: ${w.doneWhen}" else " More work can make a finished piece worse."))
                    Row {
                        TextButton(onClick = { asked = true; scope.io { dao.put(w.copy(status = "finished")) } }) { Text("Yes, it is finished") }
                        TextButton(onClick = { asked = true }) { Text("Not yet") }
                    }
                }
            }
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
    val events by dao.events().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    val start = remember(tick) { st.sessionStart }
    val active = works.filter { it.status == "in_progress" || it.status == "idea" }

    if (start == 0L) {
        // Today's step can ask for practice ("session?practice"); a wiped day is always light.
        var kind by rememberSaveable { mutableStateOf(if (st.energyDay == LocalDate.now().toString() && st.energy == "wiped") "light" else if (st.suggestPractice == LocalDate.now().toString()) "skill" else "make") }
        // Set once the pieces have loaded; a piece just started here stays chosen when the list grows.
        var workId by rememberSaveable { mutableStateOf(0L) }
        if (workId == 0L) (Engine.currentPiece(works) ?: active.firstOrNull())?.id?.let { workId = it }
        val work = works.firstOrNull { it.id == workId }
        Screen("Start a session", nav) {
            Section("What kind?")
            // Three kinds as rows with a line each, so the choice explains itself.
            Column {
                listOf("make" to ("Make" to "Work on a piece."), "skill" to ("Practice" to "The next foundations exercise, or any exercise. Counts toward your week."),
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
            // Practice follows the twelve weeks of foundations, one exercise after another; "something else" is always there.
            val foundation = remember(st.foundationsDone) { com.emotionalleaves.studioagent.domain.Foundations.next(st.foundationsDone) }
            val week = remember(st.foundationsDone) { com.emotionalleaves.studioagent.domain.Foundations.current(st.foundationsDone) }
            var useFoundation by rememberSaveable { mutableStateOf(true) }
            if (kind == "skill") {
                Panel {
                    if (useFoundation && foundation != null && week != null) {
                        Small("Foundations, week ${week.n} of ${com.emotionalleaves.studioagent.domain.Foundations.weeks.size}: ${week.skill}")
                        Text(foundation.text, style = MaterialTheme.typography.titleMedium)
                        Small(week.why)
                        TextButton(onClick = { useFoundation = false }, modifier = Modifier.offset(x = (-12).dp)) { Text("Something else instead") }
                    } else {
                        Small("Today's exercise")
                        Text(practicePrompts[n], style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { n = (n + 1) % practicePrompts.size }, modifier = Modifier.offset(x = (-12).dp)) { Text("Another one") }
                    }
                }
            }
            val left = work?.let { Engine.firstStep(it) }.orEmpty().ifBlank { sessions.firstOrNull { it.artworkId == workId && it.nextStep.isNotBlank() }?.nextStep.orEmpty() }
            supplies(st.supplies).filter { it.second }.takeIf { it.isNotEmpty() }?.let { low -> Small("Running low: " + low.joinToString(", ") { it.first } + ". Supplies has the shopping list.") }
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
            // Straight off a shift: a short reset makes the switch from work to studio easier than pushing straight in.
            val offShift = remember {
                val n0 = java.time.LocalDateTime.now(); val end = com.emotionalleaves.studioagent.domain.Planner.time(st.workEnd, java.time.LocalTime.of(15, 30))
                n0.dayOfWeek.value in Engine.workDaysNow() &&
                    !n0.toLocalTime().isBefore(end) && n0.toLocalTime().isBefore(end.plusMinutes(150))
            }
            var resetSaid by remember { mutableStateOf("") }
            if (offShift) Panel {
                Text("Straight from work?", fontWeight = FontWeight.SemiBold)
                Text("Ten minutes to switch first: wash up and change, eat something, a glass of water. Then set out the one thing you will touch first.", style = MaterialTheme.typography.bodyMedium)
                if (resetSaid.isBlank()) TextButton(onClick = { Engine.remindIn(ctx, 20, "Reset done? Studio time", "session"); resetSaid = "A nudge in 20 minutes." }, modifier = Modifier.offset(x = (-12).dp)) { Text("Nudge me in 20 minutes") }
                else Small(resetSaid)
            }
            Section("How long?")
            Choice("", listOf("5 min", "15 min", usual).distinct(), length) { length = it }
            Button(onClick = {
                buzz()
                st.sessionPausedAt = 0L; st.sessionPausedTotal = 0L
                st.sessionGoalMin = length.substringBefore(" ").toIntOrNull() ?: st.defaultSessionMin
                st.sessionArtwork = if (kind == "make") workId else 0L; st.sessionKind = kind; st.sessionStart = now()
                val fromCourse = kind == "skill" && useFoundation && foundation != null
                st.practiceText = when (kind) { "skill" -> if (fromCourse) foundation!!.text else practicePrompts[n]; "light" -> lightPrompts[n % lightPrompts.size]; else -> "" }
                st.practiceId = if (fromCourse) foundation!!.id else ""
                // Working on an idea makes it a piece in progress.
                if (kind == "make" && work?.status == "idea") scope.io { dao.put(work.copy(status = "in_progress")) }
                if (st.dndInSession) Notifier.dnd(ctx, true)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start, " + length.replace(" min", " minutes")) }
            if (st.dndInSession && !Notifier.dndGranted(ctx)) Small("Want the phone silenced during sessions? Allow it in You, Settings, Hours and calm.")
            if (st.focusApp.isNotBlank()) TextButton(onClick = {
                val pkg = st.focusApp.substringBefore("|")
                ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { try { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } }
            }, modifier = Modifier.offset(x = (-12).dp)) { Text("Open " + st.focusApp.substringAfter("|")) }
            if (st.playlist.isNotBlank()) TextButton(onClick = { Share.open(ctx, st.playlist.trim()) }, modifier = Modifier.offset(x = (-12).dp)) { Text("Put on your studio playlist") }
            if (st.buddy.isNotBlank()) TextButton(onClick = { Share.text(ctx, "Starting ${length.replace(" min", " minutes")} on my art now. Want to work on something of yours at the same time? I'll text you when I'm done.") }, modifier = Modifier.offset(x = (-12).dp)) { Text("Tell ${st.buddy} you're starting") }
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
            // A foundations exercise practiced and saved is done; the next one comes up next time.
            if (save && kind == "skill" && st.practiceId.isNotBlank()) { st.foundationsDone = com.emotionalleaves.studioagent.domain.Foundations.mark(st.foundationsDone, st.practiceId); st.practiceId = "" }
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
                // Anything else on this piece's list, so it is not forgotten while the piece is in front of you.
                events.filter { it.artworkId == work.id && it.type == "task" && !it.done }.take(2).forEach { Small("Also for this piece: " + it.title) }
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
                // Every 45 minutes, for a couple of minutes: step back. Good for a back that stood all shift, and for the work.
                val mins = (secs / 60).toInt()
                if (!paused && mins >= 45 && mins % 45 < 3) Panel(strong = true) {
                    Text("Step back", fontWeight = FontWeight.SemiBold)
                    Text("Stand up and look at the piece from across the room for a minute. Your back gets a rest, and you will see it fresh.", style = MaterialTheme.typography.bodyMedium)
                }
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
        // Four zeros on day one read as a report card. Until something is counted, one calm line instead.
        if (ready == 0 && sent == 0 && venues == 0 && list == 0 && sold.isEmpty()) Panel(onClick = { nav.navigate("works") }) {
            Text("Nothing to count yet", fontWeight = FontWeight.SemiBold)
            Small("Pieces ready to send, applications, venues and sales appear here as they happen. The first one is a piece in Studio.")
        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), "$ready", if (ready == 1) "piece ready to send" else "pieces ready to send") { nav.navigate("works") }
                val sentYear = appls.count { it.submittedAt > 0 && Engine.date(it.submittedAt).year == year }
                Tile(Modifier.weight(1f), "$sentYear", com.emotionalleaves.studioagent.domain.Attempts.tile(sentYear, st.yearSends) + if (inShows > 0) ", $inShows got in" else "") { nav.navigate("calls") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f), "$venues", (if (venues == 1) "venue" else "venues") + if (list > 0) ", $list on your list" else "") { nav.navigate("outreach") }
                Tile(Modifier.weight(1f), if (sold.isEmpty()) "0" else money(sold.sumOf { it.amount }), if (sold.isEmpty()) "sales in $year" else Texts.count(sold.size, "sale") + " in $year") { nav.navigate("records") }
            }
        }
        IconRow(Icons.Default.Search, "Find anything", "A piece, a call, a person, or ask a question") { nav.navigate("search") }
        IconRow(Icons.Default.Menu, "Everything the app can do", "Every tool, grouped by what you want to get done") { nav.navigate("tools") }
        val statementOk = docs.any { it.type == "statement" && it.approved && it.text.isNotBlank() }
        IconRow(Icons.Default.Edit, "Statement, bio and CV", (if (statementOk) "Statement approved" else "Statement not approved yet") + ", " + Texts.count(facts.size, "CV line")) { nav.navigate("materials") }
        IconRow(Icons.Default.Search, "When someone looks you up", "What jurors and venues find online") { nav.navigate("findable") }
        IconRow(Icons.Default.Person, "People and venues", if (venues == 0) "Find real places near you to show" else "Places to show, and emails to them") { nav.navigate("outreach") }
        com.emotionalleaves.studioagent.domain.Foundations.current(st.foundationsDone).let { wk -> IconRow(Icons.Default.Star, "Foundations", if (wk == null) "All weeks done" else "Week ${wk.n} of ${com.emotionalleaves.studioagent.domain.Foundations.weeks.size}: ${wk.skill}") { nav.navigate("foundations") } }
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
            sessions.filter { it.start >= monthStart && it.end > it.start }.let { l -> if (l.isEmpty()) null else l.sumOf { (it.end - it.start) / 60000 }.toInt().let { m -> if (m < 60) Texts.count(m, "minute") + " of making" else "${"%.1f".format(m / 60.0).removeSuffix(".0")} hours of making" } }
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
            // Several in progress and none chosen: one picked for you, the one waited on longest, so the choice is not the obstacle.
            if (active.size >= 3 && active.none { it.id == st.focusWork }) TextButton(onClick = {
                st.focusWork = active.minByOrNull { w -> sessions.filter { it.artworkId == w.id }.maxOfOrNull { it.start } ?: w.createdAt }!!.id
            }, modifier = Modifier.offset(x = (-12).dp)) { Text("Can't choose? Pick for me") }
            Small("Start now will open straight onto it. Other ideas can wait in Ideas.")
        }
        StageHead(3, "Your studio times", false)
        val coming = events.filter { it.type == "studio" && it.date > now() }.take(5)
        if (st.pausedUntil > now()) Small("Planning is paused until ${fmtDate(st.pausedUntil)}.")
        else if (coming.isEmpty()) Small(if (planned) "None fit the week as it is set. Change studio times below." else "Planning them now…")
        else coming.forEach { Check(false, fmtDate(it.date) + ", " + clock(it.date) + ", ${(it.endDate - it.date) / 60000} min", small = true) }
        OutlinedButton(onClick = { nav.navigate("week") }, modifier = Modifier.fillMaxWidth()) { Text("Change studio times") }
        OutlinedButton(onClick = { nav.navigate("dump") }, modifier = Modifier.fillMaxWidth()) { Text("Empty your head before the week") }
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
    "Look at one of your finished pieces for five minutes and write three things you would keep and one you would change.",
    "Draw an onion, a pepper and a lemon in a row. Make the lemon the brightest thing on the page.",
    "Draw steam or smoke: only the edges where it meets the background.",
    "Five two-minute drawings of a sink full of dishes. Only the big shapes.",
    "Draw a shiny pot and its reflections: what is really in the shine, not what you expect.",
    "Fill a page with ellipses: cups, bowls, lids, plates, seen from above and from the side.",
    "Paint or color a small square of the light at the time you usually work. Then one in daylight, next weekend.",
    "Draw a corner of your room in only straight lines, then the same corner in only curves.",
    "Choose one color you never use and make a small piece where it is the star.",
    "Draw the same object lit from the left, from above, and from behind.",
    "Make a quick study in four values with a marker set or a pencil, from a photo of your street.",
    "Draw your own shoes on your feet, from where you sit. Foreshortening is just drawing what is there.",
    "Make three tiny pieces, the size of a stamp, of one idea. Pick the strongest and draw it bigger."
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
    "Put on music and look at your series side by side for ten minutes.",
    "Wash your brushes and set out tomorrow's paper. Done counts.",
    "Write titles for two untitled pieces: a place, a time, an object.",
    "Empty your head into the app, then close it.",
    "Find one artist whose work you love and look at ten of their pieces. Nothing else.",
    "Sharpen your pencils and test one new pen on scrap paper.",
    "Take a progress photo of the piece you are on, and nothing else."
)

/** The few changes worth knowing about, shown once after an update. Kept short: the newest and most useful first. */
val whatsNew = listOf(
    "Today, your way: switch off any card you do not want (your week, five-minute jobs, the road to a first show, the daily piece, done this week).",
    "Which notifications: switch off any kind; results and bedtime always come through. Light or dark, whatever the phone says.",
    "Empty your head: a job or idea that names a piece goes to that piece; its page shows its own to-dos, and sessions remind you of them.",
    "Ask more on the phone: what's on this week, when your next studio time is. Find anything shows example questions to try.",
    "Market day: the next market and its packing evening go on your to-dos when you close the day.",
    "Help lists the first questions a beginner needs first again; public art calls explained.",
    "Your day job, in your words, for the letters where it helps."
)
