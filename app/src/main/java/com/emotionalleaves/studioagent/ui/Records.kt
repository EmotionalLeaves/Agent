package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
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
import com.emotionalleaves.studioagent.domain.Texts
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
    var kind by rememberSaveable { mutableStateOf("fee") }
    var amount by remember { mutableStateOf(0.0) }
    val note = rememberSaveable { mutableStateOf("") }
    var saleWork by remember { mutableStateOf(0L) }
    var salePrice by remember { mutableStateOf(0.0) }
    var saleCommission by remember { mutableStateOf(0.0) }
    var cutFromShow by remember { mutableStateOf(0) }
    val buyer = rememberSaveable { mutableStateOf("") }
    var lastSale by remember { mutableStateOf<Triple<Long, Double, String>?>(null) }
    var status by remember { mutableStateOf("") }
    val todo = rememberSaveable { mutableStateOf("") }
    val todoDate = rememberSaveable { mutableStateOf("") }

    Screen("Records", nav) {
        Intro("records", "The paperwork drawer: what is left to do for shows you got into, your own to-dos, money in and out, a report once a quarter, and proof of everything sent. Nothing here needs checking daily.")
        val tasks = events.filter { (it.type == "task" || it.type == "todo") && !it.done }
        H("To do")
        if (tasks.isEmpty()) Empty("Nothing open. When you get into a show, its delivery and pick-up steps appear here by themselves.")
        if (tasks.isNotEmpty()) Panel {
            tasks.sortedBy { if (it.date > 0) it.date else Long.MAX_VALUE }.forEachIndexed { i, e ->
                if (i > 0) Hair()
                // Tap the whole line to tick it off.
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = e.done, role = androidx.compose.ui.semantics.Role.Checkbox) { c ->
                    // A ticked line leaves the list, so it can be brought back with Undo.
                    if (c) scope.undoable("Done: ${e.title}", { dao.put(e.copy(done = true)) }, { dao.put(e.copy(done = false)) }) else scope.io { dao.put(e.copy(done = false)) }
                }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mark(e.done)
                    Column(Modifier.weight(1f)) {
                        Text(e.title)
                        val sub = (opps.firstOrNull { it.id == e.oppId }?.title.orEmpty() + (if (e.date > 0) ", " + fmtDate(e.date) else "")).trim().trim(',').trim()
                        if (sub.isNotBlank()) Small(sub)
                    }
                }
            }
        }

        Expand("Add your own to-do") {
            Field("What needs doing", todo)
            DateField("Date, if it has one", todoDate.value) { todoDate.value = it }
            if (todo.value.isNotBlank()) Button(onClick = {
                val e = Evt(type = "todo", title = todo.value.trim(), date = Ai.parseDate(todoDate.value)); todo.value = ""; todoDate.value = ""
                scope.io { dao.put(e) }
            }) { Text("Add to-do") }
        }

        H("Money")
        val year = LocalDate.now().year
        val thisYear = ledger.filter { Engine.date(it.date).year == year }
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { Small("Spent in $year"); Text(money(-thisYear.filter { it.amount < 0 }.sumOf { it.amount }), style = MaterialTheme.typography.headlineSmall) }
                Column(Modifier.weight(1f)) { Small("Earned in $year"); Text(money(thisYear.filter { it.amount > 0 }.sumOf { it.amount }), style = MaterialTheme.typography.headlineSmall) }
            }
            val net = thisYear.sumOf { it.amount }
            if (thisYear.isNotEmpty()) Small((if (net >= 0) "Ahead by " else "Behind by ") + money(kotlin.math.abs(net)) + " this year.")
            Small("Entry fees this month ${money(Engine.feesThisMonth(ledger))}" + (if (st.feeBudget > 0) " of ${money(st.feeBudget)}" else ""))
        }
        Expand("Add a cost") {
            Choice("What kind", listOf("fee", "shipping", "print", "framing", "other"), kind, names = { mapOf("fee" to "Entry fee", "print" to "Printing")[it] ?: it.replaceFirstChar { c -> c.uppercase() } }) { kind = it }
            Num("Amount, " + st.currency, amount) { amount = it }
            Field("What for", note)
            if (amount > 0) Button(onClick = { val l = Ledger(kind = kind, amount = -amount, date = now(), note = note.value.trim()); note.value = ""; amount = 0.0; scope.io { dao.put(l) } }) { Text("Add cost") }
        }

        Expand("Record a sale") {
        val sellable = works.filter { it.status != "sold" && it.status != "idea" && it.status != "in_progress" }
        if (sellable.isEmpty()) Small("No finished works to sell yet.")
        else {
            Choice("Which work", sellable.map { it.id.toString() }, saleWork.toString(), names = { i -> sellable.firstOrNull { it.id.toString() == i }?.let { it.title.ifBlank { "Untitled #${it.id}" } } ?: i }) { i ->
                sellable.firstOrNull { it.id.toString() == i }?.let { w -> saleWork = w.id; if (salePrice == 0.0) salePrice = w.price
                    // Sold at a show it got into: that venue's cut, as the call stated it.
                    scope.io {
                        val shownAt = dao.applsNow().filter { it.status == "accepted" && w.id in Engine.workIds(it) }.maxByOrNull { it.submittedAt }
                        val cut = shownAt?.let { a -> dao.oppsNow().firstOrNull { it.id == a.oppId }?.commission } ?: 0.0
                        if (cut > 0 && saleCommission == 0.0) { saleCommission = cut; cutFromShow++ }
                    }
                }
            }
            key(saleWork) { Num("Sale price, " + st.currency, salePrice) { salePrice = it } }
            key(saleWork, cutFromShow) { Num("Venue commission, %", saleCommission) { saleCommission = it } }
            if (salePrice > 0) Small("You receive ${money(salePrice * (1 - saleCommission.coerceIn(0.0, 100.0) / 100))}" + (if (saleCommission > 0) " after the venue's ${"%.0f".format(saleCommission)}%." else "."))
            Field("Buyer's name, for the bill of sale", buyer)
            Button(enabled = saleWork != 0L && salePrice > 0, onClick = {
                val w = works.firstOrNull { it.id == saleWork }
                val price = salePrice; val pct = saleCommission; val who = buyer.value.trim(); saleWork = 0L; buyer.value = ""
                lastSale = w?.let { Triple(it.id, price, who) }
                scope.io {
                    dao.put(Ledger(kind = "sale", amount = price, date = now(), note = w?.title.orEmpty() + (if (who.isNotBlank()) ", to $who" else ""), artworkId = w?.id ?: 0))
                    if (pct > 0) dao.put(Ledger(kind = "commission", amount = -price * pct / 100, date = now(), note = "Commission on ${w?.title.orEmpty()}", artworkId = w?.id ?: 0))
                    if (w != null) dao.put(if (w.editionSize > 1) w.copy(editionUsed = w.editionUsed + 1).let { x -> if (x.editionUsed >= x.editionSize) x.copy(status = "sold", location = "Edition sold out") else x } else w.copy(status = "sold", location = "Sold"))
                    status = "Sale recorded. You keep ${money(price * (1 - pct / 100))}."
                }
            }) { Text("Record sale") }
        }
        Status(status)
        lastSale?.let { (id, price, who) ->
            OutlinedButton(onClick = {
                scope.io({ status = it }) {
                    val w = dao.artworksNow().firstOrNull { it.id == id } ?: return@io
                    val year = java.time.LocalDate.now().year
                    val n = ledger.count { it.kind == "sale" && java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneId.systemDefault()).year == year }.coerceAtLeast(1)
                    val f = com.emotionalleaves.studioagent.work.Papers.billOfSale(ctx, st, w, who, price, n)
                    withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
                }
            }) { Text("Make the bill of sale") }
            // A certificate usually goes to the buyer with the work.
            OutlinedButton(onClick = {
                scope.io({ status = it }) {
                    val w = dao.artworksNow().firstOrNull { it.id == id } ?: return@io
                    val f = com.emotionalleaves.studioagent.work.Papers.certificate(ctx, st, w, w.editionUsed.coerceAtLeast(1))
                    withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
                }
            }) { Text("Make its certificate of authenticity") }
        }

        }

        H("Entries")
        ledger.take(40).forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((mapOf("fee" to "Entry fee", "sale" to "Sale", "commission" to "Commission", "shipping" to "Shipping", "print" to "Printing", "framing" to "Framing")[l.kind] ?: l.kind.replaceFirstChar { it.uppercase() }) +
                        (if (l.note.isNotBlank()) ": ${l.note}" else ""))
                    Small(fmtDate(l.date))
                }
                Text(money(l.amount))
                RemoveButton("entry") { scope.remove("the ${l.kind} entry", { dao.del(l) }, { dao.put(l) }) }
            }
        }
        OutlinedButton(onClick = {
            scope.io({ status = it }) {
                val f = File(ImageTools.exportDir(ctx), com.emotionalleaves.studioagent.work.Papers.docName(st, "Income_and_costs_$year", "csv"))
                // Money in and money out in their own columns, oldest first, with totals: what a tax preparer asks for.
                val rows = thisYear.sortedBy { it.date }
                fun q(t: String) = "\"" + t.replace("\"", "\"\"") + "\""
                f.writeText("date,kind,description,money_in,money_out\n" + rows.joinToString("\n") {
                    "${Engine.date(it.date)},${it.kind},${q(it.note)},${if (it.amount > 0) "%.2f".format(it.amount) else ""},${if (it.amount < 0) "%.2f".format(-it.amount) else ""}"
                } + "\ntotal,,," + "%.2f".format(rows.filter { it.amount > 0 }.sumOf { it.amount }) + "," + "%.2f".format(-rows.filter { it.amount < 0 }.sumOf { it.amount }) + "\n")
                withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "text/csv") }
            }
        }) { Text("Export $year as a spreadsheet file") }

        H("Quarterly report")
        // Results are only summarised once a quarter, never one by one.
        val today = LocalDate.now()
        val quarterStart = today.withMonth((today.monthValue - 1) / 3 * 3 + 1).withDayOfMonth(1)
        val cutoff = Engine.millis(quarterStart.atStartOfDay())
        val decided = appls.filter { (it.status == "accepted" || it.status == "declined") && it.submittedAt in 1 until cutoff }
        if (decided.isEmpty()) Note("Your first report appears at the start of a new quarter, once there are results from the one before. Until then there is nothing to analyze.")
        else Panel {
            val acc = decided.count { it.status == "accepted" }
            Text("Up to ${fmtDate(Engine.millis(quarterStart.minusDays(1).atTime(12, 0)))}: accepted $acc of ${Texts.count(decided.size, "result")}", fontWeight = FontWeight.SemiBold)
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
        if (learned.isNotEmpty()) Note("The ranking has learned from your No answers: " + learned.entries.joinToString(", ") { "${mapOf("too far" to "too far", "fee" to "the fee", "wrong vibe" to "not your kind of show")[it.key] ?: it.key} (${it.value})" })
        ReceiptsBody()
    }
}

/** One page that answers "did it send?" and "what has the app been doing?" so neither needs checking twice. */
@Composable
fun Receipts(nav: NavController) = Records(nav)

@Composable
fun ReceiptsBody() {
    val appls by dao.appls().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val contacts by dao.contacts().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    run {
        Section("Sent, with proof")
        val sent = appls.filter { it.submittedAt > 0 }.map { Triple(it.submittedAt, "Application: " + (opps.firstOrNull { o -> o.id == it.oppId }?.title ?: "call"), it.proofText) } +
            contacts.filter { it.sentAt > 0 }.map { Triple(it.sentAt, "Email to " + it.venue.ifBlank { it.email }, if (it.status == "followed_up") "Follow-up sent too. Nothing more will go to them." else "") }
        if (sent.isEmpty()) Empty("Nothing sent yet. Each application and email you send is listed here with proof, so you never need to check twice.")
        // Grouped by month, newest first, so "did that go?" is one glance.
        sent.sortedByDescending { it.first }.groupBy { Engine.date(it.first).withDayOfMonth(1) }.forEach { (month, items) ->
            Small(month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + month.year)
            Panel {
                items.forEachIndexed { i, (at, what, proof) ->
                    if (i > 0) Hair()
                    Check(true, what, bold = true); Small(fmtDate(at) + ", " + clock(at)); if (proof.isNotBlank()) Small(proof)
                }
            }
        }
        Expand("What the app did on its own") {
            val lines = if (tick >= 0) st.activityLines() else emptyList()
            if (lines.isEmpty()) Empty("Nothing yet. The daily check runs once a day when the phone is online.")
            lines.take(40).forEach { (at, text) -> Small(fmtDate(at) + ", " + clock(at) + ": " + text) }
        }
    }
}



@Composable
private fun SText(label: String, initial: String, secret: Boolean = false, lines: Int = 1, onSet: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    Field(label, t, { t = it; onSet(it.trim()) }, lines = lines, secret = secret)
}

/** Chips where several can be on at once. */
@Composable
private fun Multi(label: String, options: List<String>, initial: List<String>, names: (String) -> String = { it.replaceFirstChar { c -> c.uppercase() } }, onChange: (List<String>) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    Column {
        Note(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = o in picked, onClick = { picked = if (o in picked) picked - o else picked + o; onChange(picked) }, label = { Text(names(o)) })
            }
        }
    }
}

val sectionNames = listOf(
    Triple("you", "About you", "Name, contact details, what you make, goals"),
    Triple("hours", "Hours and calm", "Work hours, quiet times, studio times, focus, phone permissions"),
    Triple("rules", "Your rules", "Level, gates, fee caps, what calls you see, what matters most"),
    Triple("ai", "Writing help", "Free AI that drafts and reads calls, and your standing notes"),
    Triple("email", "Email", "Let the app send and read email itself"),
    Triple("backup", "Backup and web page", "One backup file, or GitHub for backup and a portfolio page"),
    // Older addresses still open the page they now live on.
    Triple("calm", "Hours and calm", ""), Triple("permissions", "Hours and calm", ""),
    Triple("writing", "Writing help", ""), Triple("github", "Backup and web page", "")
)

/** Settings pages that were merged: each old section now shows inside its new page. */
private fun settingsParts(section: String): List<String> = when (section) {
    "hours", "calm", "permissions" -> listOf("hours", "calm", "permissions")
    "ai", "writing" -> listOf("ai", "writing")
    "backup", "github" -> listOf("backup", "github")
    else -> listOf(section)
}

private val partHeads = mapOf("calm" to "Focus and calm", "permissions" to "Phone permissions", "writing" to "Standing notes", "github" to "GitHub: off-phone backup and a portfolio page")

@Composable
fun SettingsScreen(nav: NavController, section: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by st.changed.collectAsState()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(st.dndInSession) }
    var useCal by remember { mutableStateOf(st.useCalendar) }
    var showAll by remember { mutableStateOf(st.showAllLevels) }
    var gateOut by remember { mutableStateOf(st.gateOutreach) }
    var levelPick by remember { mutableStateOf(if (st.levelOverride in 0..4) st.levelOverride.toString() else "automatic") }
    var units by remember { mutableStateOf(st.units) }
    var shortText by remember { mutableStateOf(st.shortText) }
    var keepAwake by remember { mutableStateOf(st.keepAwake) }
    var fitWords by remember { mutableStateOf(st.fitWords) }
    var lockApproved by remember { mutableStateOf(st.lockApproved) }
    var weights by remember { mutableStateOf(st.weights.split(",").map { it.trim().toIntOrNull() ?: 2 }.let { l -> List(6) { l.getOrElse(it) { 2 } } }) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { status = "Permissions updated." }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.io({ status = it }) { Backup.export(ctx, st, uri); status = "Backup saved. Keys and passwords are not in it." }
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Exception) { }
            st.backupFolder = uri.toString(); st.guideManual = st.guideManual + ",autobackup"
            status = "Folder chosen. Tap Back up there now to make the first one."
        }
    }
    // Restoring cannot be undone, so it is the one place the app asks first.
    var restoreFrom by remember { mutableStateOf<android.net.Uri?>(null) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreFrom = uri }
    fun doRestore(uri: android.net.Uri) {
        scope.io({ status = it }) {
            G.db.close()
            Backup.restore(ctx, st, uri)
            status = "Restored. The app will close now; open it again."
            delay(1800)
            withContext(Dispatchers.Main) { (ctx as? Activity)?.finishAffinity(); exitProcess(0) }
        }
    }

    Screen(sectionNames.firstOrNull { it.first == section }?.second ?: "Settings", nav) {
        Status(status)
        for (part in settingsParts(section)) {
        partHeads[part]?.let { Section(it) }
        when (part) {
            "" -> {
                Intro("settings", "Nothing here has to be touched for the app to work. Open a section only when you want to change something. The Guide walks through the ones worth setting up.")
                val icons = mapOf("you" to Icons.Default.Person, "hours" to Icons.Default.Notifications, "rules" to Icons.Default.Build, "calm" to Icons.Default.Favorite,
                    "writing" to Icons.Default.Edit, "ai" to Icons.Default.Star, "email" to Icons.Default.Email, "github" to Icons.Default.Share,
                    "backup" to Icons.Default.Refresh, "permissions" to Icons.Default.Lock)
                listOf("Everyday" to listOf("you", "hours", "rules"), "Extras, all optional" to listOf("ai", "email", "backup")).forEach { (group, ids) ->
                    Section(group)
                    Column {
                        ids.forEach { id ->
                            val (_, title, sub) = sectionNames.first { it.first == id }
                            val state = when (id) {
                                "ai" -> if (G.ai.ready) "On" else "Off"
                                "email" -> if (st.mailReady) "On" else "Off"
                                else -> ""
                            }
                            IconRow(icons.getValue(id), title, sub + (if (tick >= 0 && state.isNotBlank()) ". $state" else "")) { nav.navigate("settings/$id") }
                        }
                    }
                }
            }
            "you" -> {
                Small("All optional. Each is used only where it is needed: your CV, file names, entry forms.")
                Section("Name")
                SText("First name", st.firstName) { st.firstName = it }
                SText("Last name", st.lastName) { st.lastName = it }
                Section("Contact")
                SText("Your email (for your signature and entry forms)", st.email) { st.email = it }
                SText("Phone (optional: entry forms, your email signature, receipts)", st.phone) { st.phone = it }
                SText("Website or portfolio page", st.website) { st.website = it }
                SText("City", st.city) { st.city = it }
                SText("University or college you work or study at (optional: its own calls are kept for you)", st.school) { st.school = it }
                SText("State or region", st.region) { st.region = it }
                SText("Born (optional, for your CV, like \"b. 1998, Chicago\")", st.born) { st.born = it }
                Section("Your work")
                SText("Mediums", st.mediums) { st.mediums = it }
                Multi("How your work is shown (pick every one that applies)", listOf("print", "physical", "screen"), st.routes, names = { mapOf("print" to "Printed (digital work on paper)", "physical" to "Made by hand", "screen" to "On a screen")[it] ?: it }) { st.route = it.joinToString(",") }
                SText("Goals for the next 12 months", st.goals, lines = 2) { st.goals = it }
                Choice("Sizes in", listOf("in", "cm"), units, names = { if (it == "in") "Inches" else "Centimeters" }) { units = it; st.units = it }
                SText("Currency symbol", st.currency) { if (it.isNotBlank()) st.currency = it }
                Num("Price rule: per square inch", st.pricePerSqIn) { st.pricePerSqIn = it }
            }
            "hours" -> {
                Intro("hours", "The app sends nothing during work hours on work days, and nothing from one hour before bedtime until 7:00.")
                DaysField("Days you work", st.workDays) { st.workDays = it }
                TimeField("Work starts", st.workStart) { st.workStart = it }
                TimeField("Work ends", st.workEnd) { st.workEnd = it }
                Count("Rest after work before studio time", st.bufferMin, 0, 240, 15, "min") { st.bufferMin = it }
                TimeField("Bedtime", st.bedtime) { st.bedtime = it }
                Count("Notifications a day, at most", st.notifCap, 0, 10) { st.notifCap = it }
                Toggle("Book studio time in my calendar", useCal) { useCal = it; st.useCalendar = it }
                Toggle("Silence my phone during sessions", dnd) { dnd = it; st.dndInSession = it }
                Small("Your studio times themselves are on Today: tap Your week.")
            }
            "rules" -> {
                Intro("rules", "The defaults follow the plan: start free and local, climb when a gate is met. Every one of them is yours to change.")
                H("Level")
                Choice("My level", listOf("automatic", "0", "1", "2", "3", "4"), levelPick, names = { if (it == "automatic") "Worked out for me" else "Level $it" }) { levelPick = it; st.levelOverride = it.toIntOrNull() ?: -1 }
                Note("Automatic works it out from your catalog and CV. Pick a number if you already have a history the app does not know about.")
                Expand("When each level opens") {
                    Count("Level 1: finished works in one series", st.gateWorks, 1, 30) { st.gateWorks = it }
                    Count("Level 2: shows you have been in", st.gateShows1, 1, 30) { st.gateShows1 = it }
                    Count("Level 3: shows, plus an award or invitation", st.gateShows2, 1, 50) { st.gateShows2 = it }
                }
                H("Calls")
                Toggle("Show calls from every level, whatever mine is", showAll) { showAll = it; st.showAllLevels = it }
                Expand("Fee caps and monthly budget") {
                    Num("Fee cap per entry at Level 0", st.feeCap0) { st.feeCap0 = it }
                    Num("Fee cap per entry at Level 1", st.feeCap1) { st.feeCap1 = it }
                    Num("Fee cap per entry at Level 2", st.feeCap2) { st.feeCap2 = it }
                    Num("Fee budget a month", st.feeBudget) { st.feeBudget = it }
                    Note("A call with a fee is only shown when it is under your level's cap and this month's budget still covers it. Budget 0 means free calls only.")
                    Multi("Kinds of call to show", Ai.callKinds, st.kinds, names = { mapOf("fair" to "Art fairs", "exhibition" to "Exhibitions", "residency" to "Residencies", "grant" to "Grants", "publication" to "Publications", "commission" to "Commissions", "other" to "Other")[it] ?: it }) { st.callKinds = it.joinToString(",") }
                }
                Count("Calls on the weekly short list", st.shortlistSize, 1, 10) { st.shortlistSize = it }
                SText("Blocked organizers (comma separated)", st.blocked) { st.blocked = it }
                Expand("What matters most when ranking") {
                    listOf("Theme fits my work", "Good for my CV", "Costs little", "Good odds", "Little effort", "Matches my goals").forEachIndexed { i, label ->
                        Choice(label, listOf("low", "normal", "high"), listOf("low", "normal", "high")[weights[i].coerceIn(1, 3) - 1]) { pick ->
                            weights = weights.toMutableList().also { it[i] = listOf("low", "normal", "high").indexOf(pick) + 1 }
                            st.weights = weights.joinToString(",")
                            scope.io { Engine.rescore() }
                        }
                    }
                }
                H("Outreach")
                Toggle("Hold back emails to venues above my level", gateOut) { gateOut = it; st.gateOutreach = it }
                Count("Outreach emails a week, at most", st.outreachWeeklyCap, 0, 20) { st.outreachWeeklyCap = it }
            }
            "calm" -> {
                Text("Each of these is a default you can switch. None of them is a rule about you.")
                H("Starting")
                Count("Usual session length", st.defaultSessionMin, 5, 180, 5, "min") { st.defaultSessionMin = it }
                Small("Start now uses this. A shorter time is one tap away under Start now, for days when starting is the hard part.")
                Toggle("Keep the screen on during a session", keepAwake) { keepAwake = it; st.keepAwake = it }
                H("Less on screen")
                Toggle("Hide the small gray explanations", shortText) { shortText = it; st.shortText = it }
                Toggle("Show fit as words (Strong, Good, Fair), not a score", fitWords) { fitWords = it; st.fitWords = it }
                H("Finished means finished")
                Toggle("Lock texts once I approve them", lockApproved) { lockApproved = it; st.lockApproved = it }
                Count("Rewrites before a text is called ready", st.revisionLimit, 1, 5) { st.revisionLimit = it }
                Small("Sent applications lock on their own. Every editing screen shows the time it last saved, and Records lists everything that went out.")
            }
            "writing" -> {
                Intro("writing", "These apply to everything the app writes for you: statements, bios, emails, answers.")
                SText("For example: never call it a journey; mention that I cook for a living", st.writingNotes, lines = 3) { st.writingNotes = it }
                SText("Words or phrases never to use (comma separated)", st.extraJargon, lines = 2) { st.extraJargon = it }
                Count("Rewrites before a text is called ready", st.revisionLimit, 1, 5) { st.revisionLimit = it }
            }
            "ai" -> {
                Small("Writing help is on and free. It drafts statements and emails and reads calls for you, using a public service that needs no account. Only the text being written is sent, and no account is made in your name.")
                Toggle("Use the free writing help", st.freeAi) { st.freeAi = it }
                Small("Now writing: ${G.ai.who()}.")
                Button(enabled = !busy, onClick = {
                    busy = true; status = "Asking…"
                    scope.io({ status = "No answer: $it"; busy = false }) {
                        val a = G.ai.complete("Reply in five words or fewer.", "Say hello to an artist.", fast = true, maxTokens = 30)
                        status = "It answered: \"${a.take(80)}\""; busy = false
                    }
                }) { Text("Try it") }
                Expand("Faster and steadier: a free key of your own") {
                    Small("The free helper can be slow when it is busy. A Google key is free too and takes a minute: sign in, tap Create API key, copy it, come back. The app works out the rest from the key itself.")
                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { com.emotionalleaves.studioagent.sys.Share.open(ctx, "https://aistudio.google.com/apikey") }) { Text("Get a free key") }
                        // Back from the website with the key copied: one tap, no typing.
                        Button(onClick = {
                            val k = clip.getText()?.text?.trim().orEmpty()
                            if (k.length < 20 || k.contains(' ')) status = "Nothing that looks like a key is copied yet. On the key page, tap the copy button next to the key, then come back."
                            else { G.ai.useKey(k); status = "Key added: ${Ai.detect(k).name}. Tap Try it to check." }
                        }) { Text("Paste the key I copied") }
                    }
                    SText("Paste any AI key here (Google, Claude, OpenAI, Groq, OpenRouter)", st.aiKey, secret = true) { G.ai.useKey(it) }
                    if (st.aiKey.isNotBlank()) {
                        Small("Recognized as ${Ai.detect(st.aiKey).name}.")
                        Num("Stop at this much a month (estimate)", st.aiCapUsd) { st.aiCapUsd = it }
                        Small("Spent this month, roughly: ${money(G.ai.spentThisMonth())}.")
                        TextButton(onClick = { G.ai.useKey("") }) { Text("Remove the key and go back to the free helper") }
                    }
                }
            }
            "email" -> {
                Small("Optional. Without it, the app opens your mail app with everything filled in and you press Send. With it, approved emails go by themselves and replies are read for you.")
                Explain("Where do I get an app password?", "For Gmail: turn on 2-Step Verification, then create an app password at myaccount.google.com/apppasswords and paste it below. It is not your normal password, and it can be removed any time.")
                var addr by remember { mutableStateOf(st.mailUser) }
                SText("Email address", st.mailUser) { e ->
                    st.mailUser = e; addr = e
                    // The servers follow from the address for the common services, so there is nothing else to fill in.
                    Mail.serversFor(e)?.let { (out, inc) -> st.smtpHost = out; st.imapHost = inc }
                }
                if (Mail.noAppPasswords(addr)) Note("Outlook and Hotmail no longer let apps sign in with a password, so emails cannot go out from here. Nothing is lost: each application opens in your mail app, filled in, and you press Send.")
                else if (addr.contains('@') && Mail.serversFor(addr) == null) Note("The app does not know this mail service's servers. Fill them in under Servers, or leave email off and send from your mail app.")
                SText("App password", st.mailPass, secret = true) { st.mailPass = it.replace(" ", "") }
                Expand("Servers (filled in for Gmail, Yahoo, iCloud, AOL, Fastmail and Zoho)") {
                    SText("Outgoing server", st.smtpHost) { st.smtpHost = it }
                    SText("Incoming server", st.imapHost) { st.imapHost = it }
                }
                if (st.mailUser.isNotBlank() && st.mailPass.isNotBlank()) Button(enabled = !busy, onClick = {
                    busy = true; status = "Sending a test…"
                    scope.io({ status = "That did not work: $it"; busy = false }) {
                        Mail.send(st.smtpHost, st.mailUser, st.mailPass, st.mailUser, "Studio Agent test", "If you can read this, Studio Agent can send email for you.", fromName = st.fullName)
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
                    busy = true; status = "Backing up…"
                    scope.io({ status = "That did not work: $it"; busy = false }) {
                        val n = Engine.backupToGitHub(ctx)
                        status = if (n == 0) "Nothing was uploaded. Check the token and the backup repository name." else "Backed up ${Texts.count(n, "file")}. It now repeats on its own every day."; busy = false
                    }
                }) { Text("Back up to GitHub now") }
                Note("The portfolio page is published from You, Statement bio and CV. The first time, also open the public repository's Settings, Pages, and choose the main branch.")
            }
            "backup" -> {
                H("Automatic")
                Text(if (tick >= 0 && st.backupFolder.isBlank()) "Not on yet. Choose a folder once and the app saves a backup there every day by itself, keeping the newest five."
                else "On. " + (if (st.lastAutoBackup > 0) "Last backup ✓ ${fmtDate(st.lastAutoBackup)}." else "The first one is made with the next daily check, or now with the button below."))
                Small("Pick a folder that is copied off the phone if you can (for example one inside Google Drive's files), so a lost phone does not lose your catalog.")
                OutlinedButton(onClick = { folder.launch(null) }) { Text(if (st.backupFolder.isBlank()) "Choose a backup folder" else "Choose a different folder") }
                if (st.backupFolder.isNotBlank()) Button(enabled = !busy, onClick = {
                    busy = true; status = "Backing up…"
                    scope.io({ status = "That did not work: $it"; busy = false }) { val name = Backup.toFolder(ctx, st); st.log("Backup saved to your folder: $name"); status = "Saved $name ✓"; busy = false }
                }) { Text("Back up there now") }
                H("By hand")
                Note("One file with your whole catalog, images and settings. Keys and passwords are left out. Restoring replaces everything in the app with the file's contents.")
                OutlinedButton(onClick = { export.launch("studio-agent-backup-${LocalDate.now()}.zip") }) { Text("Save a backup") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) { Text("Restore from a backup") }
                restoreFrom?.let { uri ->
                    Panel(strong = true) {
                        Text("Replace everything in the app with this backup?", style = MaterialTheme.typography.titleSmall)
                        Small("What is in the app now is replaced and cannot be brought back. Save a backup first if you are not sure.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DangerButton(onClick = { restoreFrom = null; doRestore(uri) }) { Text("Replace everything") }
                            TextButton(onClick = { restoreFrom = null }) { Text("Cancel") }
                        }
                    }
                }
            }
            "permissions" -> {
                Small("All three are optional. Each one only makes something easier.")
                listOf(
                    Triple("Notifications", "At most a couple of quiet reminders a day, never during work.", Notifier.allowed(ctx)),
                    Triple("Calendar", "Lets the app book studio time around your day.", Cal.granted(ctx)),
                    Triple("Do Not Disturb", "Silences the phone while a session runs.", Notifier.dndGranted(ctx))
                ).forEach { (name, what, on) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.padding(top = 2.dp)) { Mark(on) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name + (if (on) ": on" else ": off"), style = MaterialTheme.typography.titleSmall)
                            Small(what)
                        }
                    }
                }
                if (!Notifier.allowed(ctx) || !Cal.granted(ctx)) OutlinedButton(onClick = {
                    val wanted = mutableListOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
                    perms.launch(wanted.toTypedArray())
                }) { Text("Allow calendar and notifications") }
                OutlinedButton(onClick = { try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) { } }) { Text("Allow Do Not Disturb") }
                Small("Social media: there is none in this app. No feeds, no follower counts, no sign-in.")
                H("Something not working?")
                Small("Version ${G.versionName()}. The report has no keys, passwords or artwork in it.")
                OutlinedButton(onClick = { Share.text(ctx, G.problemReport()) }) { Text("Share a problem report") }
            }
            else -> Note("That settings page does not exist.")
        }
        }
    }
}


val sectionIcons = mapOf("you" to Icons.Default.Person, "hours" to Icons.Default.Notifications, "rules" to Icons.Default.Build, "calm" to Icons.Default.Favorite,
    "writing" to Icons.Default.Edit, "ai" to Icons.Default.Star, "email" to Icons.Default.Email, "github" to Icons.Default.Share,
    "backup" to Icons.Default.Refresh, "permissions" to Icons.Default.Lock)
