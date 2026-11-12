package com.emotionalleaves.studioagent.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val pinText = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val appls by dao.appls().collectAsState(emptyList())
    val idea = remember { mutableStateOf("") }
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
    Screen("Studio Agent", nav, back = false) {
        if (crashed) Panel {
            Text("The app closed unexpectedly last time", fontWeight = FontWeight.SemiBold)
            Text("That was the app, not you. Nothing you saved is lost. Sending the details gets it fixed.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Share.text(ctx, G.problemReport()) }) { Text("Send the details") }
                TextButton(onClick = { G.crashFile.delete(); crashed = false }) { Text("Dismiss") }
            }
        }
        val n = next
        Panel(strong = true, onClick = { n?.let { if (it.route != "home") nav.navigate(it.route) } }) {
            Text("NEXT", style = MaterialTheme.typography.labelMedium)
            Text(n?.title ?: "One moment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (n != null) Text(n.detail)
        }
        if (st.onboarded) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (st.pinnedStep.isNotBlank()) TextButton(onClick = { st.pinnedStep = "" }) { Text("Done, clear my pin") }
            else {
                if (st.snoozeDay == today.toString()) TextButton(onClick = { st.snoozeDay = "" }) { Text("Bring today's step back") }
                else TextButton(onClick = { st.snoozeDay = today.toString() }) { Text("Not today") }
                TextButton(onClick = { pinning = !pinning }) { Text("Pin my own") }
            }
        }
        if (pinning) {
            Field("What do you want to see here instead?", pinText)
            Button(enabled = pinText.value.isNotBlank(), onClick = { st.pinnedStep = pinText.value.trim(); pinText.value = ""; pinning = false }) { Text("Pin it") }
        }

        val workEnd = Planner.time(st.workEnd, LocalTime.of(15, 30))
        val isWorkDay = today.dayOfWeek.value in Planner.days(st.workDays)
        if (st.onboarded && isWorkDay && LocalTime.now().isAfter(workEnd) && st.energyDay != today.toString()) {
            Panel {
                Text("How is your energy after work?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("wiped", "okay", "good").forEach { e ->
                        OutlinedButton(onClick = { st.energy = e; st.energyDay = today.toString() }) { Text(e) }
                    }
                }
            }
        }

        if (st.onboarded) {
            if (st.sessionStart > 0) Button(onClick = { nav.navigate("session") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Back to session") }
            else {
                // One tap: the app picks the piece and starts the clock. Nothing to decide first.
                Button(onClick = { scope.io { Engine.quickStart(st.defaultSessionMin); if (st.dndInSession) Notifier.dnd(ctx, true); withContext(Dispatchers.Main) { nav.navigate("session") } } }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Start now") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.io { Engine.quickStart(5); if (st.dndInSession) Notifier.dnd(ctx, true); withContext(Dispatchers.Main) { nav.navigate("session") } } }) { Text("Just 5 minutes") }
                    TextButton(onClick = { nav.navigate("session") }) { Text("Let me choose first") }
                }
            }
        }

        val todo = guide.filter { !it.done }
        val must = todo.firstOrNull { !it.optional } ?: todo.firstOrNull()
        if (!st.guideHidden && must != null) Panel(onClick = { nav.navigate("guide") }) {
            Text("Guide: ${guide.count { it.done }} of ${guide.size} done", fontWeight = FontWeight.SemiBold)
            Note("Next: ${must.title}. Tap for the exact steps.")
        }

        Expand("Progress and deadlines", startOpen = !st.simpleMode) {
            Panel(onClick = { nav.navigate("path") }) {
                Text("Level $level: ${Levels.names[level]}", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { gate.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Note(if (level < 4) "To reach Level ${level + 1}: ${gate.text}" else gate.text)
            }
            val hours = Engine.hoursThisWeek(sessions)
            Panel(onClick = { nav.navigate("week") }) {
                Text("This week: ${"%.1f".format(hours)} of ${st.weeklyHours} hours")
                LinearProgressIndicator(progress = { (hours / st.weeklyHours.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                // The week at a glance: a filled dot is a day you worked, a ring is a planned block still ahead.
                val monday = today.with(java.time.DayOfWeek.MONDAY)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0..6).forEach { i ->
                        val day = monday.plusDays(i.toLong())
                        val worked = sessions.any { it.end > it.start && Engine.date(it.start) == day }
                        val planned = events.any { it.type == "studio" && it.date > now() && Engine.date(it.date) == day }
                        Text("MTWTFSS"[i].toString() + "\n" + (if (worked) "●" else if (planned) "○" else "·"),
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                if (st.pausedUntil > now()) Note("Planning is paused until ${fmtDate(st.pausedUntil)}.")
                else events.firstOrNull { it.type == "studio" && it.date > now() }?.let { Note("Next studio block: ${fmtDate(it.date)}. Tap to change your week.") }
            }
            val deadlines = events.filter { it.type == "deadline" && !it.done && it.date > now() }.take(3)
            if (deadlines.isNotEmpty()) Panel {
                deadlines.forEach { e ->
                    val left = ((e.date - now()) / (30.0 * 86_400_000)).toFloat().coerceIn(0f, 1f)
                    Text(e.title.removePrefix("Deadline: "))
                    LinearProgressIndicator(progress = { left }, modifier = Modifier.fillMaxWidth())
                    Note(Planner.weekendsLeft(today, Engine.date(e.date)))
                }
            }
            if (st.feeBudget > 0 || G.ai.ready) Note(
                "Fees this month: ${money(Engine.feesThisMonth(ledger))} of ${money(st.feeBudget)}.  AI this month: about ${money(G.ai.spentThisMonth())} of ${money(st.aiCapUsd)}."
            )
        }

        // Proof of what got done, in plain counts. No streaks, nothing to break.
        val weekStart = Engine.millis(today.with(java.time.DayOfWeek.MONDAY).atStartOfDay())
        val weekSessions = sessions.filter { it.start >= weekStart && it.end > it.start }
        val wins = mutableListOf<String>()
        if (weekSessions.isNotEmpty()) wins += "${weekSessions.size} session" + (if (weekSessions.size > 1) "s" else "") + ", ${weekSessions.sumOf { (it.end - it.start) / 60000 }} minutes of making"
        works.count { it.createdAt >= weekStart && it.imagePath.isNotBlank() }.let { if (it > 0) wins += "$it piece" + (if (it > 1) "s" else "") + " with an image added" }
        appls.count { it.submittedAt >= weekStart }.let { if (it > 0) wins += "$it application" + (if (it > 1) "s" else "") + " sent" }
        if (wins.isNotEmpty()) Panel {
            Text("Done this week", fontWeight = FontWeight.SemiBold)
            wins.forEach { Text("✓ $it") }
        }

        if (st.onboarded) {
            Field("Park an idea so it stops circling", idea, mic = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = idea.value.isNotBlank(), onClick = { val t = idea.value.trim(); idea.value = ""; parked = true; scope.io { dao.put(Idea(text = t, createdAt = now())) } }) { Text("Park it") }
                if (pendingCount > 0) OutlinedButton(onClick = { nav.navigate("admin") }) { Text("Admin pass ($pendingCount)") }
            }
            if (parked) Small("Parked ✓. It is safe in Ideas; you can let it go for now.")
        }
    }
}

/** Shown once after a session ends: what was saved, in full, so there is no doubt and nothing to go back and check. */
@Composable
fun DoneScreen(nav: NavController) {
    val sessions by dao.sessions().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val s = sessions.firstOrNull()
    Screen("Session saved", nav, back = false) {
        Panel(strong = true) {
            Text("Saved ✓", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (s != null) {
                val mins = ((s.end - s.start) / 60000).coerceAtLeast(0)
                Text("$mins minutes" + (works.firstOrNull { it.id == s.artworkId }?.let { " on " + it.title.ifBlank { "Untitled" } } ?: "") + ".")
                if (s.nextStep.isNotBlank()) Text("Next time starts with: ${s.nextStep}")
                Text(if (mins < 10) "Short sessions count. You showed up." else "That counts toward your week.")
            }
        }
        Small("It is all stored. You can close the app.")
        Button(onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("OK") }
    }
}

@Composable
fun Onboarding(nav: NavController) {
    val first = remember { mutableStateOf(st.firstName) }
    val last = remember { mutableStateOf(st.lastName) }
    val email = remember { mutableStateOf(st.email) }
    val city = remember { mutableStateOf(st.city) }
    var shown by remember { mutableStateOf("not sure yet") }
    var hours by remember { mutableStateOf("decide for me") }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { nav.navigate("home") { popUpTo("home") { inclusive = true } } }
    Screen("Set up", nav) {
        Text("Only your first name is needed. You are not expected to know any of the rest yet: leave it on \"not sure\" and the app starts you with a safe choice that you can change later.")
        Field("First name", first)
        Field("Last name (optional)", last)
        Field("Email for your CV (optional)", email)
        Field("City (optional)", city)
        Choice("How will your work be shown?", listOf("not sure yet", "prints of my digital work", "physical work", "on screens"), shown) { shown = it }
        Small(when (shown) {
            "not sure yet" -> "That is fine. The app will assume prints of your digital work, because it is the quickest way to get something you already make onto a wall. Nothing is locked in."
            "prints of my digital work" -> "Your files, printed on archival paper and framed. Shows accept these all the time."
            "physical work" -> "Painting, drawing, printmaking or anything made by hand."
            else -> "Video, animation or interactive work shown on a monitor or projector. Fewer shows take it, but some do."
        })
        Choice("Hours of making a week", listOf("decide for me", "3", "4", "5", "6", "8"), hours) { hours = it }
        Small(if (hours == "decide for me") "The app starts at 4 hours: two short evenings and one longer weekend session. You can lower or raise it any time, or pause." else "About $hours hours, spread around your job.")
        Button(onClick = {
            st.firstName = first.value.trim(); st.lastName = last.value.trim(); st.email = email.value.trim(); st.city = city.value.trim()
            st.route = when (shown) { "physical work" -> "physical"; "on screens" -> "screen"; else -> "print" }
            st.weeklyHours = hours.toIntOrNull() ?: 4
            val wanted = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
            perms.launch(wanted.toTypedArray())
        }, enabled = first.value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        Small("Next, your phone asks two things: calendar (so studio time can be booked around your day) and notifications (at most ${st.notifCap} quiet ones a day). Say no to either and the app still works.")
        TextButton(onClick = { nav.navigate("basics") }) { Text("New to exhibiting? Plain answers to the basics") }
    }
}

@Composable
fun SessionScreen(nav: NavController) {
    val ctx = LocalContext.current
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
            Choice("What kind?", listOf("make", "skill", "light"), kind) { kind = it }
            Note(when (kind) {
                "make" -> "Work on a piece."
                "skill" -> "Practice in a new medium. Logged as learning, and it counts toward your week."
                else -> "Low effort: sort reference images, a 15-minute sketch, tidy the workspace."
            })
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
            Button(onClick = {
                st.sessionPausedAt = 0L; st.sessionPausedTotal = 0L
                st.sessionGoalMin = if (kind == "light") 15 else st.defaultSessionMin
                st.sessionArtwork = if (kind == "make") workId else 0L; st.sessionKind = kind; st.sessionStart = now()
                if (st.dndInSession) Notifier.dnd(ctx, true)
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Start") }
            if (st.dndInSession && !Notifier.dndGranted(ctx)) Note("To silence your phone during sessions, allow Do Not Disturb access in Settings (More, Settings, Permissions).")
        }
    } else {
        var nowMs by remember { mutableStateOf(now()) }
        LaunchedEffect(start) { while (true) { delay(1000); nowMs = now() } }
        val note = remember { mutableStateOf("") }
        val nextStep = remember { mutableStateOf("") }
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
        Screen("In session", nav) {
            val track = MaterialTheme.colorScheme.surfaceContainerHigh
            val fill = MaterialTheme.colorScheme.primary
            val part = if (goal > 0) (secs / (goal * 60f)).coerceIn(0f, 1f) else 0f
            // A ring that fills as time passes: the whole session visible at a glance.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp)) {
                    val w = 14.dp.toPx()
                    drawArc(track, -90f, 360f, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    if (part > 0f) drawArc(fill, -90f, 360f * part, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((if (secs >= 3600) "%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60) else "%d:%02d".format(secs / 60, secs % 60)), style = MaterialTheme.typography.displayMedium)
                    if (goal > 0) Small(if (secs >= goal * 60L) "$goal minutes done" else "${Math.ceil((goal * 60L - secs) / 60.0).toInt()} min to go")
                    if (paused) Small("paused")
                }
            }
            if (goal > 0 && secs >= goal * 60L) Panel(strong = true) { Text("That is your $goal minutes. Stopping now is a complete session. Keep going only if you want to.") }
            Text(work?.title?.ifBlank { "Untitled" } ?: (if (st.sessionKind == "skill") "Practice" else "Light session"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (work != null) {
                val steps = Texts.steps(work.steps)
                val open = steps.indexOfFirst { !it.first }
                if (open >= 0) Panel {
                    Text("Now: ${steps[open].second}", fontWeight = FontWeight.SemiBold)
                    Small("${steps.count { it.first }} of ${steps.size} small steps done")
                    OutlinedButton(onClick = { scope.io { dao.put(work.copy(steps = Texts.stepsText(steps.mapIndexed { i, p -> if (i == open) true to p.second else p }))) } }) { Text("Done, show the next one") }
                } else if (work.nextStep.isNotBlank()) Small("Where you left off: ${work.nextStep}")
                if (work.doneWhen.isNotBlank()) Small("Finished when: ${work.doneWhen}")
            }
            if (Planner.isWindDown(LocalDateTime.now(), Planner.time(st.bedtime, LocalTime.of(22, 30)))) Panel(strong = true) {
                Text("Wind-down time. A good place to stop: write your next step so nothing is lost.")
            }
            if (!stopping) {
                Note("Your phone is quiet. Put it down and work; the timer keeps going.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f).height(52.dp), onClick = {
                        if (paused) { st.sessionPausedTotal = st.sessionPausedTotal + (now() - st.sessionPausedAt); st.sessionPausedAt = 0L }
                        else st.sessionPausedAt = now()
                    }) { Text(if (paused) "Resume" else "Pause") }
                    Button(modifier = Modifier.weight(1f).height(52.dp), onClick = { stopping = true }) { Text("I am stopping") }
                }
            } else {
                H("Before you go")
                Field("Next time, start with...", nextStep, mic = true)
                Field("Anything to note (optional)", note, lines = 2)
                if (photo.isNotBlank()) PathImage(photo, Modifier.fillMaxWidth().height(160.dp))
                OutlinedButton(onClick = { try { take.launch(Share.uri(ctx, photoFile)) } catch (e: Exception) { } }) { Text(if (photo.isBlank()) "Progress photo (optional)" else "Retake photo") }
                Button(onClick = { finish(true) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("End and save") }
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
    Screen("Admin pass", nav) {
        Note("Everything that needs a yes from you, in one place. One tap each. When this list is empty you are done.")
        if (loaded && items.isEmpty()) Panel(strong = true) { Text("Nothing to approve. Done.") }
        items.forEach { p -> Panel(onClick = { nav.navigate(p.route) }) { Text(p.title, fontWeight = FontWeight.SemiBold); Note(p.detail) } }
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

@Composable
fun More(nav: NavController) {
    Screen("More", nav, back = false) {
        listOf(
            "Help" to listOf(
                Triple("Guide", "Every setup step, one at a time", "guide"),
                Triple("Plain answers", "What a call, a series, an edition and the rest mean", "basics"),
                Triple("Where this is going", "The five levels and what opens each one", "path")
            ),
            "Do" to listOf(
                Triple("Calls", "Open calls, ranked for you", "calls"),
                Triple("People", "Openings, venues, your email list", "outreach"),
                Triple("Materials", "Interview, statement, bio, CV, portfolio", "materials"),
                Triple("Ready-made emails", "What to say to a print lab, a gallery, a library", "letters"),
                Triple("Your week", "Studio times, pause, session history", "week"),
                Triple("Ideas", "Parked for later", "ideas"),
                Triple("Admin pass", "Everything waiting for your yes", "admin")
            ),
            "Look up" to listOf(
                Triple("Receipts", "Everything sent, and what the app did on its own", "receipts"),
                Triple("Records and logistics", "To-dos, fees, sales, reports", "records"),
                Triple("Call sources", "Pages and senders the app reads", "sources"),
                Triple("Settings", "You, your rules, AI, email, backup", "settings")
            )
        ).forEach { (group, items) ->
            H(group)
            items.forEach { (title, sub, route) -> Panel(onClick = { nav.navigate(route) }) { Text(title, fontWeight = FontWeight.SemiBold); Small(sub) } }
        }
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
        H("1. What you did")
        Text(if (mine.isEmpty()) "No sessions logged this week. That is information, not a verdict. If the plan did not fit your week, the plan is what should change."
        else "${mine.size} session" + (if (mine.size > 1) "s" else "") + ", ${mine.sumOf { (it.end - it.start) / 60000 }} minutes. That happened, and it counts.")
        H("2. One piece for next week")
        if (active.isEmpty()) Small("Nothing in progress. Start a new piece from Studio when you are ready.")
        else {
            Choice("", active.map { it.title.ifBlank { "Untitled #${it.id}" } }, active.firstOrNull { tick >= 0 && it.id == st.focusWork }?.let { it.title.ifBlank { "Untitled #${it.id}" } }.orEmpty()) { t ->
                active.firstOrNull { it.title.ifBlank { "Untitled #${it.id}" } == t }?.let { st.focusWork = it.id }
            }
            Small("Start now will open straight onto it. Other ideas can wait in Ideas.")
        }
        H("3. Your studio times")
        val coming = events.filter { it.type == "studio" && it.date > now() }.take(5)
        if (st.pausedUntil > now()) Small("Planning is paused until ${fmtDate(st.pausedUntil)}.")
        else if (coming.isEmpty()) Small("None planned yet. They are added with the next daily check.")
        else coming.forEach { Small("○ " + fmtDate(it.date) + ", ${(it.endDate - it.date) / 60000} min") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("week") }) { Text("Change them") }
            if (pending > 0) OutlinedButton(onClick = { nav.navigate("admin") }) { Text("$pending to approve") }
        }
        Button(onClick = { st.lastReviewWeek = Engine.weekId(); nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Done for the week") }
    }
}
