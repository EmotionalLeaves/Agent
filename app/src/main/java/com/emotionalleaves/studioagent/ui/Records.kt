package com.emotionalleaves.studioagent.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.ai.Ai
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.data.Ledger
import com.emotionalleaves.studioagent.net.Mail
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import com.emotionalleaves.studioagent.domain.Scoring
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.sys.Backup
import com.emotionalleaves.studioagent.sys.Cal
import com.emotionalleaves.studioagent.sys.Notifier
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.system.exitProcess

@Composable
fun Records(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by dao.events().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val appls by dao.appls().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    var kind by remember { mutableStateOf("fee") }
    var amount by remember { mutableStateOf(0.0) }
    val note = remember { mutableStateOf("") }
    var saleWork by remember { mutableStateOf(0L) }
    var salePrice by remember { mutableStateOf(0.0) }
    var saleCommission by remember { mutableStateOf(0.0) }
    var status by remember { mutableStateOf("") }
    val todo = remember { mutableStateOf("") }
    val todoDate = remember { mutableStateOf("") }

    Screen("Records and logistics", nav) {
        Intro("records", "The paperwork drawer: what is left to do for shows you got into, your own to-dos, money in and out, and a report once a quarter. Nothing here needs checking daily.")
        val tasks = events.filter { (it.type == "task" || it.type == "todo") && !it.done }
        H("To do")
        Field("Add your own to-do", todo)
        Field("Date, if it has one (YYYY-MM-DD)", todoDate)
        Button(enabled = todo.value.isNotBlank(), onClick = {
            val e = Evt(type = "todo", title = todo.value.trim(), date = Ai.parseDate(todoDate.value)); todo.value = ""; todoDate.value = ""
            scope.io { dao.put(e) }
        }) { Text("Add") }
        if (tasks.isEmpty()) Note("Nothing open.")
        tasks.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = e.done, onCheckedChange = { c -> scope.io { dao.put(e.copy(done = c)) } })
                Column { Text(e.title); Note((opps.firstOrNull { it.id == e.oppId }?.title.orEmpty() + (if (e.date > 0) "  ·  " + fmtDate(e.date) else "")).trim()) }
            }
        }

        H("Money")
        val year = LocalDate.now().year
        val thisYear = ledger.filter { Engine.date(it.date).year == year }
        Panel {
            Text("Fees this month: ${money(Engine.feesThisMonth(ledger))} of ${money(st.feeBudget)} budget")
            Text("$year so far: out ${money(-thisYear.filter { it.amount < 0 }.sumOf { it.amount })}, in ${money(thisYear.filter { it.amount > 0 }.sumOf { it.amount })}")
        }
        Choice("Add an entry", listOf("fee", "shipping", "print", "framing", "other"), kind) { kind = it }
        Num("Amount, " + st.currency, amount) { amount = it }
        Field("What for", note)
        Button(enabled = amount > 0, onClick = { val l = Ledger(kind = kind, amount = -amount, date = now(), note = note.value.trim()); note.value = ""; scope.io { dao.put(l) } }) { Text("Add cost") }

        H("Record a sale")
        val sellable = works.filter { it.status != "sold" && it.status != "idea" && it.status != "in_progress" }
        if (sellable.isEmpty()) Note("No finished works to sell yet.")
        else {
            Choice("Which work", sellable.map { it.title.ifBlank { "Untitled #${it.id}" } }, sellable.firstOrNull { it.id == saleWork }?.title?.ifBlank { "Untitled #$saleWork" }.orEmpty()) { t ->
                sellable.firstOrNull { it.title.ifBlank { "Untitled #${it.id}" } == t }?.let { saleWork = it.id; if (salePrice == 0.0) salePrice = it.price }
            }
            key(saleWork) { Num("Sale price, " + st.currency, salePrice) { salePrice = it } }
            Num("Venue commission, %", saleCommission) { saleCommission = it }
            Button(enabled = saleWork != 0L && salePrice > 0, onClick = {
                val w = works.firstOrNull { it.id == saleWork }
                val price = salePrice; val pct = saleCommission; saleWork = 0L
                scope.io {
                    dao.put(Ledger(kind = "sale", amount = price, date = now(), note = w?.title.orEmpty(), artworkId = w?.id ?: 0))
                    if (pct > 0) dao.put(Ledger(kind = "commission", amount = -price * pct / 100, date = now(), note = "Commission on ${w?.title.orEmpty()}", artworkId = w?.id ?: 0))
                    if (w != null) dao.put(if (w.editionSize > 1) w.copy(editionUsed = w.editionUsed + 1) else w.copy(status = "sold", location = "Sold"))
                    status = "Sale recorded. You keep ${money(price * (1 - pct / 100))}."
                }
            }) { Text("Record sale") }
        }
        Status(status)

        H("Entries")
        ledger.take(40).forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${fmtDate(l.date)}  ${l.kind}  ${l.note}", Modifier.weight(1f))
                Text(money(l.amount))
                TextButton(onClick = { scope.io { dao.del(l) } }) { Text("×") }
            }
        }
        OutlinedButton(onClick = {
            scope.io({ status = it }) {
                val f = File(ImageTools.exportDir(ctx), "StudioAgent_ledger_$year.csv")
                f.writeText("date,kind,amount,note\n" + thisYear.joinToString("\n") { "${Engine.date(it.date)},${it.kind},${"%.2f".format(it.amount)},\"${it.note.replace("\"", "'")}\"" })
                withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "text/csv") }
            }
        }) { Text("Export $year as a spreadsheet file") }

        H("Quarterly report")
        // Results are only summarised once a quarter, never one by one.
        val today = LocalDate.now()
        val quarterStart = today.withMonth((today.monthValue - 1) / 3 * 3 + 1).withDayOfMonth(1)
        val cutoff = Engine.millis(quarterStart.atStartOfDay())
        val decided = appls.filter { (it.status == "accepted" || it.status == "declined") && it.submittedAt in 1 until cutoff }
        if (decided.isEmpty()) Note("Your first report appears at the start of a new quarter, once there are results from the one before. Until then there is nothing to analyse.")
        else Panel {
            val acc = decided.count { it.status == "accepted" }
            Text("Up to ${quarterStart.minusDays(1)}: $acc accepted of ${decided.size} decided", fontWeight = FontWeight.SemiBold)
            listOf(1, 2, 3).forEach { t ->
                val group = decided.filter { a -> opps.firstOrNull { it.id == a.oppId }?.let { Scoring.tier(it) } == t }
                if (group.isNotEmpty()) Text(listOf("", "Local, campus, online", "Regional", "National and up")[t] + ": ${group.count { it.status == "accepted" }} of ${group.size}")
            }
            val free = decided.filter { a -> (opps.firstOrNull { it.id == a.oppId }?.fee ?: 0.0) <= 0.0 }
            val paid = decided - free.toSet()
            if (free.isNotEmpty()) Text("Free calls: ${free.count { it.status == "accepted" }} of ${free.size}")
            if (paid.isNotEmpty()) Text("Paid calls: ${paid.count { it.status == "accepted" }} of ${paid.size}")
        }
        val learned = st.noReasonCounts()
        if (learned.isNotEmpty()) Note("The ranking has learned from your No answers: " + learned.entries.joinToString(", ") { "${it.key} ×${it.value}" })
    }
}

@Composable
private fun SText(label: String, initial: String, secret: Boolean = false, lines: Int = 1, onSet: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    Field(label, t, { t = it; onSet(it.trim()) }, lines = lines, secret = secret)
}

/** Chips where several can be on at once. */
@Composable
private fun Multi(label: String, options: List<String>, initial: List<String>, onChange: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    Column {
        Note(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = o in picked, onClick = { picked = if (o in picked) picked - o else picked + o; onChange(picked) }, label = { Text(o) })
            }
        }
    }
}

private val sectionNames = listOf(
    Triple("you", "You", "Name, contact details, what you make, goals"),
    Triple("hours", "Work hours and quiet time", "When the app must stay silent"),
    Triple("rules", "Your rules", "Level, gates, fee caps, what calls you see, what matters most"),
    Triple("writing", "Writing", "Standing notes for anything the app writes"),
    Triple("ai", "AI", "The key that lets the app write and read for you"),
    Triple("email", "Email", "Let the app send and read email itself"),
    Triple("github", "GitHub", "Backup and your portfolio page"),
    Triple("backup", "Backup file", "Save or restore everything as one file"),
    Triple("permissions", "Permissions", "Calendar, notifications, Do Not Disturb")
)

@Composable
fun SettingsScreen(nav: NavController, section: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by st.changed.collectAsState()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(st.aiProvider) }
    var dnd by remember { mutableStateOf(st.dndInSession) }
    var useCal by remember { mutableStateOf(st.useCalendar) }
    var showAll by remember { mutableStateOf(st.showAllLevels) }
    var gateOut by remember { mutableStateOf(st.gateOutreach) }
    var levelPick by remember { mutableStateOf(if (st.levelOverride in 0..4) st.levelOverride.toString() else "automatic") }
    var units by remember { mutableStateOf(st.units) }
    var weights by remember { mutableStateOf(st.weights.split(",").map { it.trim().toIntOrNull() ?: 2 }.let { l -> List(6) { l.getOrElse(it) { 2 } } }) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { status = "Permissions updated." }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.io({ status = it }) { Backup.export(ctx, st, uri); status = "Backup saved. Keys and passwords are not in it." }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.io({ status = it }) {
            G.db.close()
            Backup.restore(ctx, st, uri)
            status = "Restored. The app will close now; open it again."
            delay(1800)
            withContext(Dispatchers.Main) { (ctx as? Activity)?.finishAffinity(); exitProcess(0) }
        }
    }

    Screen(sectionNames.firstOrNull { it.first == section }?.second ?: "Settings", nav) {
        Status(status)
        when (section) {
            "" -> {
                Note("Nothing here has to be touched for the app to work. Open a section only when you want to change something. The Guide walks through the ones worth setting up.")
                sectionNames.forEach { (id, title, sub) ->
                    val state = when (id) {
                        "ai" -> if (G.ai.ready) "Set up" else "Not set up (optional)"
                        "email" -> if (st.mailReady) "Set up" else "Not set up (optional)"
                        "github" -> if (st.ghReady) "Set up" else "Not set up (optional)"
                        else -> ""
                    }
                    Panel(onClick = { nav.navigate("settings/$id") }) { Text(title, fontWeight = FontWeight.SemiBold); Note(sub + (if (tick >= 0 && state.isNotBlank()) "  ·  $state" else "")) }
                }
                Panel(onClick = { nav.navigate("week") }) { Text("Your week", fontWeight = FontWeight.SemiBold); Note("Studio times, pausing, session history") }
                Panel(onClick = { nav.navigate("guide") }) { Text("Guide and layout", fontWeight = FontWeight.SemiBold); Note("Setup steps, simple layout on or off") }
            }
            "you" -> {
                SText("First name", st.firstName) { st.firstName = it }
                SText("Last name", st.lastName) { st.lastName = it }
                SText("Email", st.email) { st.email = it }
                SText("Phone (only used to fill entry forms)", st.phone) { st.phone = it }
                SText("Website or portfolio page", st.website) { st.website = it }
                SText("City", st.city) { st.city = it }
                SText("State or region", st.region) { st.region = it }
                SText("Mediums", st.mediums) { st.mediums = it }
                Multi("How your work is shown (pick every one that applies)", listOf("print", "physical", "screen"), st.routes) { st.route = it.joinToString(",") }
                SText("Goals for the next 12 months", st.goals, lines = 2) { st.goals = it }
                Choice("Sizes in", listOf("in", "cm"), units) { units = it; st.units = it }
                SText("Currency symbol", st.currency) { if (it.isNotBlank()) st.currency = it }
                Num("Price rule: per square inch", st.pricePerSqIn) { st.pricePerSqIn = it }
            }
            "hours" -> {
                Note("The app sends nothing during work hours on work days, and nothing from one hour before bedtime until 7:00.")
                SText("Work days (1 = Monday ... 7 = Sunday)", st.workDays) { st.workDays = it }
                SText("Work starts (HH:MM)", st.workStart) { st.workStart = it }
                SText("Work ends (HH:MM)", st.workEnd) { st.workEnd = it }
                Whole("Rest after work before any studio time, minutes", st.bufferMin) { st.bufferMin = it }
                SText("Bedtime (HH:MM)", st.bedtime) { st.bedtime = it }
                Whole("Notifications a day, at most", st.notifCap) { st.notifCap = it }
                Toggle("Book studio time in my calendar", useCal) { useCal = it; st.useCalendar = it }
                Toggle("Silence my phone during sessions", dnd) { dnd = it; st.dndInSession = it }
                OutlinedButton(onClick = { nav.navigate("week") }) { Text("Change my studio times") }
            }
            "rules" -> {
                Note("The defaults follow the plan: start free and local, climb when a gate is met. Every one of them is yours to change.")
                H("Level")
                Choice("My level", listOf("automatic", "0", "1", "2", "3", "4"), levelPick) { levelPick = it; st.levelOverride = it.toIntOrNull() ?: -1 }
                Note("Automatic works it out from your catalog and CV. Pick a number if you already have a history the app does not know about.")
                Whole("Level 1 opens at this many documented works in one series", st.gateWorks) { if (it > 0) st.gateWorks = it }
                Whole("Level 2 opens at this many exhibitions", st.gateShows1) { if (it > 0) st.gateShows1 = it }
                Whole("Level 3 opens at this many (plus an award or invitation)", st.gateShows2) { if (it > 0) st.gateShows2 = it }
                H("Calls")
                Toggle("Show calls from every level, whatever mine is", showAll) { showAll = it; st.showAllLevels = it }
                Num("Fee cap per entry at Level 0", st.feeCap0) { st.feeCap0 = it }
                Num("Fee cap per entry at Level 1", st.feeCap1) { st.feeCap1 = it }
                Num("Fee cap per entry at Level 2", st.feeCap2) { st.feeCap2 = it }
                Num("Fee budget a month", st.feeBudget) { st.feeBudget = it }
                Note("A call with a fee is only shown when it is under your level's cap and this month's budget still covers it. Budget 0 means free calls only.")
                Multi("Kinds of call to show", Ai.callKinds, st.kinds) { st.callKinds = it.joinToString(",") }
                Whole("Calls on the weekly short list", st.shortlistSize) { if (it > 0) st.shortlistSize = it }
                SText("Blocked organisers (comma separated)", st.blocked) { st.blocked = it }
                H("What matters most when ranking")
                listOf("Theme fits my work", "Good for my CV", "Costs little", "Good odds", "Little effort", "Matches my goals").forEachIndexed { i, label ->
                    Choice(label, listOf("low", "normal", "high"), listOf("low", "normal", "high")[weights[i].coerceIn(1, 3) - 1]) { pick ->
                        weights = weights.toMutableList().also { it[i] = listOf("low", "normal", "high").indexOf(pick) + 1 }
                        st.weights = weights.joinToString(",")
                        scope.io { Engine.rescore() }
                    }
                }
                H("Outreach")
                Toggle("Hold back emails to venues above my level", gateOut) { gateOut = it; st.gateOutreach = it }
                Whole("Outreach emails a week, at most", st.outreachWeeklyCap) { st.outreachWeeklyCap = it }
            }
            "writing" -> {
                Note("These apply to everything the app writes for you: statements, bios, emails, answers.")
                SText("Standing notes (for example: never call it a journey; mention that I cook for a living)", st.writingNotes, lines = 3) { st.writingNotes = it }
                SText("Words or phrases never to use (comma separated)", st.extraJargon, lines = 2) { st.extraJargon = it }
                Whole("Revision passes before the app says a text is ready", st.revisionLimit) { st.revisionLimit = it.coerceAtLeast(1) }
            }
            "ai" -> {
                Note("Without a key the app still works; you type what it would have written. With one, it writes drafts and reads calls out of links, emails and flyers.")
                Choice("Provider", listOf("anthropic", "gemini", "custom"), provider) { provider = it; st.aiProvider = it }
                Note(when (provider) {
                    "anthropic" -> "Get a key at platform.claude.com, under Settings, API keys. It is billed separately from any chat subscription."
                    "gemini" -> "Get a key at aistudio.google.com. Change both model names below to Gemini models."
                    else -> "Any service that speaks the OpenAI chat format (OpenAI, OpenRouter, a server of your own). Set the address and both model names."
                })
                SText("API key", st.aiKey, secret = true) { st.aiKey = it }
                if (provider == "custom") SText("Service address", st.aiBaseUrl) { st.aiBaseUrl = it }
                SText("Model for writing", st.aiModelWrite) { st.aiModelWrite = it }
                SText("Model for reading and sorting", st.aiModelFast) { st.aiModelFast = it }
                Num("Stop at this much a month (estimate)", st.aiCapUsd) { st.aiCapUsd = it }
                Note("Spent this month, roughly: ${money(G.ai.spentThisMonth())}.")
                Button(enabled = !busy, onClick = {
                    busy = true; status = "Testing..."
                    scope.io({ status = "That did not work: $it"; busy = false }) {
                        G.ai.complete("Reply with the single word OK.", "Say OK.", fast = true, maxTokens = 20)
                        G.ai.complete("Reply with the single word OK.", "Say OK.", fast = false, maxTokens = 20)
                        status = "The key works with both models."; busy = false
                    }
                }) { Text("Test the key") }
            }
            "email" -> {
                Note("With this, the app sends approved emails itself and reads replies and results. Without it, the app opens your mail app with everything filled in and you press Send.")
                Note("Gmail: turn on 2-Step Verification, then create an app password at myaccount.google.com/apppasswords and paste it below. It is not your normal password.")
                SText("Email address", st.mailUser) { st.mailUser = it }
                SText("App password", st.mailPass, secret = true) { st.mailPass = it.replace(" ", "") }
                Expand("Not Gmail?") {
                    SText("Outgoing server", st.smtpHost) { st.smtpHost = it }
                    SText("Incoming server", st.imapHost) { st.imapHost = it }
                }
                Button(enabled = !busy, onClick = {
                    busy = true; status = "Sending a test..."
                    scope.io({ status = "That did not work: $it"; busy = false }) {
                        Mail.send(st.smtpHost, st.mailUser, st.mailPass, st.mailUser, "Studio Agent test", "If you can read this, Studio Agent can send email for you.")
                        Mail.recent(st.imapHost, st.mailUser, st.mailPass, now() - 60_000, 3)
                        status = "Sent, and the inbox can be read. Check your email for the test."; busy = false
                    }
                }) { Text("Send me a test email") }
            }
            "github" -> {
                Note("Create a token at github.com/settings/personal-access-tokens/new: Only select repositories, then Contents set to Read and write.")
                SText("Personal access token", st.ghToken, secret = true) { st.ghToken = it }
                SText("Backup repository (yourname/name, keep it private)", st.ghBackupRepo) { st.ghBackupRepo = it }
                SText("Portfolio page repository (yourname/name, public)", st.ghPagesRepo) { st.ghPagesRepo = it }
                Button(enabled = !busy, onClick = {
                    busy = true; status = "Backing up..."
                    scope.io({ status = "That did not work: $it"; busy = false }) {
                        val n = Engine.backupToGitHub(ctx)
                        status = if (n == 0) "Nothing was uploaded. Check the token and the backup repository name." else "Backed up $n files. It now repeats on its own every day."; busy = false
                    }
                }) { Text("Back up to GitHub now") }
                Note("The portfolio page is published from Materials. The first time, also open the public repository's Settings, Pages, and choose the main branch.")
            }
            "backup" -> {
                Note("One file with your whole catalog, images and settings. Keys and passwords are left out. Restoring replaces everything in the app with the file's contents.")
                OutlinedButton(onClick = { export.launch("studio-agent-backup-${LocalDate.now()}.zip") }) { Text("Save a backup") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Restore from a backup") }
            }
            "permissions" -> {
                Text("Notifications: " + (if (Notifier.allowed(ctx)) "on" else "off"))
                Text("Calendar: " + (if (Cal.granted(ctx)) "on" else "off"))
                Text("Do Not Disturb during sessions: " + (if (Notifier.dndGranted(ctx)) "on" else "off"))
                Note("All three are optional. Calendar lets the app book studio time around your day. Do Not Disturb silences the phone while a session runs.")
                OutlinedButton(onClick = {
                    val wanted = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
                    perms.launch(wanted.toTypedArray())
                }) { Text("Allow calendar and notifications") }
                OutlinedButton(onClick = { try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) { } }) { Text("Allow Do Not Disturb") }
                Note("Social media: there is none in this app. No feeds, no follower counts, no sign-in.")
            }
            else -> Note("That settings page does not exist.")
        }
    }
}
