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
import com.emotionalleaves.studioagent.data.Ledger
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

    Screen("Records and logistics", nav) {
        val tasks = events.filter { it.type == "task" && !it.done }
        H("To do for accepted shows")
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
        Num("Amount, $", amount) { amount = it }
        Field("What for", note)
        Button(enabled = amount > 0, onClick = { val l = Ledger(kind = kind, amount = -amount, date = now(), note = note.value.trim()); note.value = ""; scope.io { dao.put(l) } }) { Text("Add cost") }

        H("Record a sale")
        val sellable = works.filter { it.status != "sold" && it.status != "idea" && it.status != "in_progress" }
        if (sellable.isEmpty()) Note("No finished works to sell yet.")
        else {
            Choice("Which work", sellable.map { it.title.ifBlank { "Untitled #${it.id}" } }, sellable.firstOrNull { it.id == saleWork }?.title?.ifBlank { "Untitled #$saleWork" }.orEmpty()) { t ->
                sellable.firstOrNull { it.title.ifBlank { "Untitled #${it.id}" } == t }?.let { saleWork = it.id; if (salePrice == 0.0) salePrice = it.price }
            }
            key(saleWork) { Num("Sale price, $", salePrice) { salePrice = it } }
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
private fun SText(label: String, initial: String, secret: Boolean = false, onSet: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    Field(label, t, { t = it; onSet(it.trim()) }, secret = secret)
}

@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(st.aiProvider) }
    var route by remember { mutableStateOf(st.route) }
    var dnd by remember { mutableStateOf(st.dndInSession) }
    var useCal by remember { mutableStateOf(st.useCalendar) }
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

    Screen("Settings", nav) {
        Status(status)
        H("You")
        SText("First name", st.firstName) { st.firstName = it }
        SText("Last name", st.lastName) { st.lastName = it }
        SText("Email", st.email) { st.email = it }
        SText("Phone (only used to fill entry forms)", st.phone) { st.phone = it }
        SText("Website or portfolio page", st.website) { st.website = it }
        SText("City", st.city) { st.city = it }
        SText("State or region", st.region) { st.region = it }
        SText("Mediums", st.mediums) { st.mediums = it }
        Choice("Shown first as", listOf("print", "physical", "screen"), route) { route = it; st.route = it }
        SText("Goals for the next 12 months", st.goals) { st.goals = it }
        SText("Blocked organisers (comma separated)", st.blocked) { st.blocked = it }
        Num("Price rule: $ per square inch", st.pricePerSqIn) { st.pricePerSqIn = it }

        H("Your week")
        SText("Work days (1 = Monday ... 7 = Sunday)", st.workDays) { st.workDays = it }
        SText("Work starts (HH:MM)", st.workStart) { st.workStart = it }
        SText("Work ends (HH:MM)", st.workEnd) { st.workEnd = it }
        Whole("Recovery time after work, minutes", st.bufferMin) { st.bufferMin = it }
        SText("Bedtime (HH:MM). Wind-down starts an hour before", st.bedtime) { st.bedtime = it }
        Whole("Hours of making a week", st.weeklyHours) { st.weeklyHours = it.coerceAtLeast(1) }
        Toggle("Book studio time in my calendar", useCal) { useCal = it; st.useCalendar = it }
        Toggle("Silence my phone during sessions", dnd) { dnd = it; st.dndInSession = it }
        OutlinedButton(onClick = { scope.io({ status = it }) { Engine.planWeek(ctx); status = "This week's studio blocks are planned." } }) { Text("Plan this week now") }

        H("Limits (loosen or tighten any of them)")
        Whole("Notifications a day, at most", st.notifCap) { st.notifCap = it }
        Whole("Revision passes before the app says it is ready", st.revisionLimit) { st.revisionLimit = it.coerceAtLeast(1) }
        Whole("Outreach emails a week, at most", st.outreachWeeklyCap) { st.outreachWeeklyCap = it }
        Num("Entry fee budget a month, $", st.feeBudget) { st.feeBudget = it }
        Note("At Level 0 only free calls are shown whatever the budget. From Level 1 the per-entry cap is $25, from Level 2 $40.")

        H("AI")
        Choice("Provider", listOf("anthropic", "gemini"), provider) { provider = it; st.aiProvider = it }
        SText("API key", st.aiKey, secret = true) { st.aiKey = it }
        SText("Model for writing", st.aiModelWrite) { st.aiModelWrite = it }
        SText("Model for reading and sorting", st.aiModelFast) { st.aiModelFast = it }
        Num("Monthly spend cap, $ (estimate)", st.aiCapUsd) { st.aiCapUsd = it }
        Note("Spent this month, roughly: ${money(G.ai.spentThisMonth())}. Without a key the app still works; you type what it would have written. If you pick Gemini, change both model names to Gemini models.")

        H("Email")
        SText("Email address", st.mailUser) { st.mailUser = it }
        SText("App password (not your normal password)", st.mailPass, secret = true) { st.mailPass = it }
        SText("Outgoing server", st.smtpHost) { st.smtpHost = it }
        SText("Incoming server", st.imapHost) { st.imapHost = it }
        Note("With these, the app sends approved emails itself and reads replies and results. Gmail app passwords need 2-Step Verification turned on in your Google account. Without them, the app opens your mail app with everything filled in instead.")

        H("GitHub (backup and portfolio page)")
        SText("Personal access token", st.ghToken, secret = true) { st.ghToken = it }
        SText("Backup repo (owner/name, keep it private)", st.ghBackupRepo) { st.ghBackupRepo = it }
        SText("Portfolio page repo (owner/name, public)", st.ghPagesRepo) { st.ghPagesRepo = it }
        OutlinedButton(onClick = { status = "Backing up..."; scope.io({ status = it }) { val n = Engine.backupToGitHub(ctx); status = if (n == 0) "Nothing to back up, or GitHub backup is not set up." else "Backed up $n files." } }) { Text("Back up to GitHub now") }

        H("Backup file")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch("studio-agent-backup-${LocalDate.now()}.zip") }) { Text("Save a backup") }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Restore from a backup") }
        }
        Note("Restoring replaces everything in the app with the backup's contents.")

        H("Permissions")
        Note("Notifications: " + (if (Notifier.allowed(ctx)) "on" else "off") + ".  Calendar: " + (if (Cal.granted(ctx)) "on" else "off") + ".  Do Not Disturb: " + (if (Notifier.dndGranted(ctx)) "on" else "off") + ".")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val wanted = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
                perms.launch(wanted.toTypedArray())
            }) { Text("Calendar and notifications") }
            OutlinedButton(onClick = { try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) { } }) { Text("Do Not Disturb access") }
        }
        Note("Social media: there is none in this app. No feeds, no follower counts, no sign-in. Your portfolio page, CV, email list and showing up in person do that job.")
    }
}
