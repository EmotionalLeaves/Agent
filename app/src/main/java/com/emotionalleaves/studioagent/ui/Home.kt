package com.emotionalleaves.studioagent.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val events by dao.events().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    var next by remember { mutableStateOf<NextStep?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    LaunchedEffect(works, docs, sessions, events, tick) {
        withContext(Dispatchers.IO) {
            try { next = Engine.nextStep(); pendingCount = Engine.pending().size } catch (e: Exception) { }
        }
    }
    val stats = Engine.statsOf(works, docs, facts)
    val level = Levels.level(stats)
    val gate = Levels.gate(stats)
    val today = LocalDate.now()

    Screen("Studio Agent", nav, back = false) {
        val n = next
        Panel(strong = true, onClick = { n?.let { nav.navigate(it.route) } }) {
            Text("NEXT", style = MaterialTheme.typography.labelMedium)
            Text(n?.title ?: "One moment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (n != null) Text(n.detail)
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

        if (st.onboarded) Button(onClick = { nav.navigate("session") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (st.sessionStart > 0) "Back to session" else "Start session")
        }

        Panel {
            Text("Level $level: ${Levels.names[level]}", fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { gate.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Note(if (level < 4) "To reach Level ${level + 1}: ${gate.text}" else gate.text)
        }

        val hours = Engine.hoursThisWeek(sessions)
        Panel {
            Text("This week: ${"%.1f".format(hours)} of ${st.weeklyHours} hours")
            LinearProgressIndicator(progress = { (hours / st.weeklyHours.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            events.firstOrNull { it.type == "studio" && it.date > now() }?.let { Note("Next studio block: ${fmtDate(it.date)}") }
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("ideas") }) { Text("Park an idea") }
            if (pendingCount > 0) OutlinedButton(onClick = { nav.navigate("admin") }) { Text("Admin pass ($pendingCount)") }
        }
        if (st.feeBudget > 0 || G.ai.ready) Note(
            "Fees this month: ${money(Engine.feesThisMonth(ledger))} of ${money(st.feeBudget)}.  AI this month: about ${money(G.ai.spentThisMonth())} of ${money(st.aiCapUsd)}."
        )
    }
}

@Composable
fun Onboarding(nav: NavController) {
    val first = remember { mutableStateOf(st.firstName) }
    val last = remember { mutableStateOf(st.lastName) }
    val email = remember { mutableStateOf(st.email) }
    val city = remember { mutableStateOf(st.city) }
    var route by remember { mutableStateOf(st.route) }
    var hours by remember { mutableStateOf(st.weeklyHours.toString()) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { nav.navigate("home") { popUpTo("home") { inclusive = true } } }
    Screen("Set up", nav) {
        Note("Four answers. Everything else has a sensible default you can change later in Settings.")
        Field("First name", first)
        Field("Last name", last)
        Field("Email (shown on your CV and portfolio)", email)
        Field("City", city)
        Choice("How will your work be shown first?", listOf("print", "physical", "screen"), route) { route = it }
        Note(when (route) {
            "print" -> "Archival prints of your digital work. The fastest way onto a wall."
            "physical" -> "Painting, drawing, printmaking or another hands-on medium."
            else -> "Video, animation or interactive work shown on screens."
        })
        Choice("Hours of making a week", listOf("3", "4", "5", "6", "8"), hours) { hours = it }
        Note("Your week is set to Monday to Friday, 7:00 to 3:30. The app stays silent in those hours.")
        Button(onClick = {
            st.firstName = first.value.trim(); st.lastName = last.value.trim(); st.email = email.value.trim(); st.city = city.value.trim()
            st.route = route; st.weeklyHours = hours.toIntOrNull() ?: 4
            val wanted = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
            perms.launch(wanted.toTypedArray())
        }, enabled = first.value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Done") }
        Note("Next it asks to use your calendar (to book studio time around your day) and to send at most ${st.notifCap} quiet notifications a day. Both are optional.")
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
        var workId by remember(works.size) { mutableStateOf(active.firstOrNull()?.id ?: 0L) }
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
                TextButton(onClick = { scope.io { workId = dao.put(Artwork(status = "in_progress", format = if (st.route == "screen") "screen" else if (st.route == "physical") "physical" else "print", year = LocalDate.now().year, createdAt = now())) } }) { Text("Start a new piece") }
            }
            val left = work?.nextStep.orEmpty().ifBlank { sessions.firstOrNull { it.artworkId == workId && it.nextStep.isNotBlank() }?.nextStep.orEmpty() }
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
        val secs = ((nowMs - start) / 1000).coerceAtLeast(0)
        val work = works.firstOrNull { it.id == st.sessionArtwork }
        fun finish(save: Boolean) {
            val end = now(); val kind = st.sessionKind; val artId = st.sessionArtwork
            st.sessionStart = 0L
            Notifier.dnd(ctx, false)
            scope.io {
                if (save) {
                    dao.put(Sess(artworkId = artId, kind = kind, start = start, end = end, note = note.value.trim(), nextStep = nextStep.value.trim(), photoPath = photo))
                    if (work != null && nextStep.value.isNotBlank()) dao.put(work.copy(nextStep = nextStep.value.trim()))
                }
            }
            nav.navigate("home") { popUpTo("home") { inclusive = true } }
        }
        Screen("In session", nav) {
            Text("%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60), style = MaterialTheme.typography.displayMedium)
            Note((work?.title?.ifBlank { "Untitled" } ?: st.sessionKind) + ". Your phone is quiet. Close this screen and work; the timer keeps going.")
            if (Planner.isWindDown(LocalDateTime.now(), Planner.time(st.bedtime, LocalTime.of(22, 30)))) Panel(strong = true) {
                Text("Wind-down time. A good place to stop: write your next step below so nothing is lost.")
            }
            H("When you stop")
            Field("Next time, start with...", nextStep, mic = true)
            Field("Anything to note (optional)", note, lines = 2)
            if (photo.isNotBlank()) PathImage(photo, Modifier.fillMaxWidth().height(160.dp))
            OutlinedButton(onClick = { try { take.launch(Share.uri(ctx, photoFile)) } catch (e: Exception) { } }) { Text(if (photo.isBlank()) "Progress photo" else "Retake photo") }
            Button(onClick = { finish(true) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("End and save") }
            TextButton(onClick = { finish(false) }) { Text("Discard this session") }
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
                    val id = dao.put(Opp(title = if (isUrl) "Call from link" else t.lines().firstOrNull().orEmpty().take(60).ifBlank { "Shared call" }, url = if (isUrl) t else "", rawText = t, sourceName = "Shared", createdAt = now()))
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
            Triple("Materials", "Interview, statement, bio, CV, portfolio", "materials"),
            Triple("Admin pass", "Everything waiting for your yes", "admin"),
            Triple("Records and logistics", "Checklists, fees, sales, reports", "records"),
            Triple("Ideas", "Parked for later", "ideas"),
            Triple("Call sources", "Pages and senders the app reads", "sources"),
            Triple("Settings", "Your week, AI, email, backup", "settings")
        ).forEach { (title, sub, route) -> Panel(onClick = { nav.navigate(route) }) { Text(title, fontWeight = FontWeight.SemiBold); Note(sub) } }
    }
}
