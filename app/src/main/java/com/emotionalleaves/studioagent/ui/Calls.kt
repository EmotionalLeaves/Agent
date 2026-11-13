package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.ai.Ai
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Ledger
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.data.Source
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Profile
import com.emotionalleaves.studioagent.domain.Scoring
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.net.Fetch
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.Papers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

fun profileOf(works: List<Artwork>, docs: List<Doc>, facts: List<Fact>, ledger: List<Ledger>): Profile = Engine.profileOf(works, docs, facts, ledger)

@Composable
fun Flags(o: Opp) {
    Scoring.trustFlags(o).forEach { Text("Worth knowing: " + it.replaceFirstChar { c -> c.lowercase() }, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
}

fun whereLine(o: Opp): String = listOf(o.organiser, if (o.online) "online" else o.city).filter { it.isNotBlank() }.joinToString(", ")

@Composable
fun Calls(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val opps by dao.opps().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val appls by dao.appls().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    var tab by rememberSaveable { mutableStateOf("this week") }
    var status by remember { mutableStateOf("") }
    var saidNo by remember { mutableStateOf(0L) }
    val paste = rememberSaveable { mutableStateOf("") }
    val p = remember(works, docs, facts, ledger, tick) { profileOf(works, docs, facts, ledger) }
    val today = LocalDate.now()
    var kindFilter by remember { mutableStateOf("every kind") }
    val kindsPresent = opps.map { it.kind }.distinct().filter { it.isNotBlank() }
    fun kindOk(o: Opp) = kindFilter == "every kind" || o.kind == kindFilter

    Screen("Calls", nav, back = false) {
        Intro("calls", "Open calls you could apply to. The app checks each one against your level, budget and work, and shows the few worth a look. You only answer Yes or No.")
        Choice("", listOf("this week", "all", "applied"), tab) { tab = it }
        if (kindsPresent.size > 1 && tab != "applied") Choice("", listOf("every kind") + kindsPresent, kindFilter) { kindFilter = it }
        Status(status)
        when (tab) {
            "this week" -> {
                val list = Engine.shortlist(opps.filter { kindOk(it) }, p, now(), Engine.stretchOk(now()), st.shortlistSize)
                val cap = Levels.feeCap(p.level, p.rules)
                LevelLine(nav, if (p.rules.showAllLevels) "Showing calls from every level (you turned level limits off in You, Settings, Your rules)."
                else "Level ${p.level}: " + (if (cap <= 0.0) "free calls only. " else if (cap == Double.MAX_VALUE) "no fee cap. " else "fees up to ${money(cap)}. ") + when (p.level) {
                    0 -> "Online or on your doorstep. The priority right now is making the series."
                    1 -> "Local, campus and community calls."
                    2 -> "Regional juried shows."
                    else -> "Competitive calls, judged case by case."
                })
                if (list.isEmpty()) {
                    Empty("No calls waiting. The app looks once a day on its own. When you see one anywhere, tap Share and choose Studio Agent, or paste it below.")
                    var looking by remember { mutableStateOf(false) }
                    OutlinedButton(enabled = !looking, onClick = {
                        looking = true; status = "Looking at the listing sites. This takes a minute or two…"
                        scope.io({ status = it; looking = false }) {
                            val n = Engine.scanSources(now()); Engine.rescore()
                            status = if (n == 0) "Looked. Nothing new right now; the app checks again tomorrow." else "Found $n new " + (if (n == 1) "call" else "calls") + ". Ones that fit your level are above; the rest are under All."
                            looking = false
                        }
                    }) { Text(if (looking) "Looking…" else "Look for calls now") }
                }
                list.forEach { o ->
                    key(o.id) {
                        Panel {
                            Row(Modifier.fillMaxWidth().clickable { nav.navigate("call/${o.id}") }, verticalAlignment = Alignment.CenterVertically) {
                                Text(o.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Full details", tint = MaterialTheme.colorScheme.outline)
                            }
                            if (whereLine(o).isNotBlank()) Small(whereLine(o))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tag(fitLabel(o.fitScore), strong = true)
                                if (o.deadline > 0) Tag(Planner.weekendsLeft(today, Engine.date(o.deadline)))
                                Tag(if (o.fee > 0) money(o.fee) else "free")
                                if (o.kind != "exhibition" && o.kind.isNotBlank()) Tag(o.kind)
                            }
                            o.fitReasons.lines().filter { it.isNotBlank() && !it.startsWith("Free to enter") && !it.startsWith("Fee ") }.take(2).forEach { Small(it) }
                            Flags(o)
                            if (saidNo == o.id) {
                                // "Not this year" is one of the answers to No, so the card needs only two buttons.
                                Choice("Why not? (optional, it teaches the ranking)", listOf("not this year", "too far", "fee", "wrong vibe", "no reason"), "") { r ->
                                    saidNo = 0L
                                    if (r == "not this year") scope.io { dao.put(o.copy(status = "later")) }
                                    else {
                                        if (r != "no reason") st.addNoReason(r)
                                        scope.io { dao.put(o.copy(status = "no", noReason = r)); Engine.rescore() }
                                    }
                                }
                            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { scope.io({ status = it }) { val id = Engine.createApplication(ctx, o); withContext(Dispatchers.Main) { nav.navigate("appl/$id") } } }) { Text("Yes, apply") }
                                OutlinedButton(onClick = { saidNo = o.id }) { Text("No") }
                            }
                        }
                    }
                }
                H("Add a call")
                Field("Paste a link or the call text", paste, lines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (paste.value.isNotBlank()) Button(onClick = {
                        val t = paste.value.trim(); paste.value = ""; status = "Reading it…"
                        scope.io({ status = it }) {
                            val isUrl = t.startsWith("http") && !t.contains(" ")
                            val page = if (isUrl) (try { Fetch.pageText(t) } catch (e: Exception) { "" }) else ""
                            val n = if (G.ai.ready) try { Engine.ingest(if (isUrl) "Link: $t\n$page" else t, "Pasted") } catch (e: Exception) { -1 } else -1
                            if (n > 0) status = "Added $n. It is at the top of All."
                            else {
                                val id = Engine.ingestPlain(if (isUrl) page.ifBlank { "Call from link" } + "\n" + t else t, "Pasted")
                                status = "Saved. The app picked out what it could (deadline, fee, link). Check those and fill in the rest."
                                withContext(Dispatchers.Main) { nav.navigate("call/$id") }
                            }
                        }
                    }) { Text("Add this call") }
                    OutlinedButton(onClick = { scope.io { val id = dao.put(Opp(title = "New call", sourceName = "By hand", createdAt = now())); withContext(Dispatchers.Main) { nav.navigate("call/$id") } } }) { Text("By hand") }
                }
            }
            "all" -> {
                Small("Everything found, including calls your rules hold back, and why.")
                IconRow(Icons.Default.Notifications, "Where calls come from", "Email alerts and pages the app checks each day") { nav.navigate("sources") }
                var allLimit by rememberSaveable { mutableStateOf(50) }
                val everything = opps.filter { it.status in setOf("new", "later", "no", "expired") && kindOk(it) }
                if (everything.size > allLimit) Small("Showing the first $allLimit of ${everything.size}.")
                everything.take(allLimit).forEach { o ->
                    val why = Scoring.ineligible(o, p, now())
                    Panel(onClick = { nav.navigate("call/${o.id}") }) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall)
                        Small(listOf(o.kind.replaceFirstChar { it.uppercase() }, whereLine(o), if (o.deadline > 0) fmtDate(o.deadline) else "", if (o.fee > 0) money(o.fee) else "free").filter { it.isNotBlank() }.joinToString(", "))
                        Small(when { o.status == "later" -> "You said not this year"; o.status == "no" -> "You said no"; o.status == "expired" -> "Deadline passed"; why.isEmpty() -> "Eligible, " + fitLabel(o.fitScore).lowercase(); else -> "Held back: " + why.joinToString("; ") })
                        Flags(o)
                    }
                }
                if (everything.size > allLimit) OutlinedButton(onClick = { allLimit += 50 }, modifier = Modifier.fillMaxWidth()) { Text("Show more") }
            }
            else -> {
                if (appls.isEmpty()) Empty("Nothing applied to yet. Say Yes to a call and the app builds the application.")
                appls.forEach { a ->
                    val o = opps.firstOrNull { it.id == a.oppId }
                    Panel(onClick = { nav.navigate("appl/${a.id}") }) {
                        Text(o?.title ?: "Application", style = MaterialTheme.typography.titleSmall)
                        Small(when (a.status) {
                            "submitted" -> "Submitted ${fmtDate(a.submittedAt)}. Done. The result will come to you."
                            "accepted" -> "Accepted"
                            "declined" -> "Not this time"
                            "sending" -> "Sending shortly"
                            else -> "Pack in progress"
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun CallDetail(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val opps by dao.opps().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val loaded = opps.firstOrNull { it.id == id }
    if (loaded == null) { Screen("Call", nav) { Loading() }; return }
    var o by remember(id) { mutableStateOf(loaded) }
    var status by remember { mutableStateOf("") }
    var deadlineText by remember { mutableStateOf(if (loaded.deadline > 0) Engine.date(loaded.deadline).toString() else "") }
    val p = profileOf(works, docs, facts, ledger)
    fun save(x: Opp) { val s = Engine.scored(x.copy(dedupeKey = Texts.dedupeKey(x.organiser, x.deadline, x.title)), p); o = s; scope.io { dao.put(s); markSaved() } }
    val why = Scoring.ineligible(o, p, now())

    Screen(o.title.ifBlank { "Call" }, nav) {
        // The facts that decide it, at a glance: when, what it costs, how well it fits.
        Panel {
            if (whereLine(o).isNotBlank()) Small(whereLine(o))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Small("Deadline")
                    Text(if (o.deadline > 0) fmtDate(o.deadline) else "Not set", style = MaterialTheme.typography.titleLarge)
                    if (o.deadline > 0) Text(Planner.weekendsLeft(LocalDate.now(), Engine.date(o.deadline)), style = MaterialTheme.typography.bodySmall,
                        color = if (Engine.date(o.deadline).isBefore(LocalDate.now().plusDays(15))) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Small("Entry fee")
                    Text(if (o.fee <= 0) "Free" else money(o.fee), style = MaterialTheme.typography.titleLarge)
                }
            }
            Hair()
            Row { Tag(fitLabel(o.fitScore), strong = o.fitScore >= 60) }
            o.fitReasons.lines().filter { it.isNotBlank() }.forEach { Text(it) }
            if (why.isNotEmpty()) Text("Held back by your rules: " + why.joinToString("; "), color = MaterialTheme.colorScheme.secondary)
            Flags(o)
            // Call pages are long and full of jargon: the writing help says what it is and what they want, in three plain lines.
            var plain by remember(o.id) { mutableStateOf(Engine.plainWordsCached(o)) }
            LaunchedEffect(o.id) { if (plain.isEmpty()) plain = try { Engine.plainWords(o) } catch (e: Exception) { emptyList() } }
            if (plain.isNotEmpty()) {
                Hair()
                Small("In plain words")
                plain.forEach { Text(it) }
            }
        }
        Status(status)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (o.status in setOf("new", "later", "no")) Button(onClick = { scope.io({ status = it }) { val aid = Engine.createApplication(ctx, o); withContext(Dispatchers.Main) { nav.navigate("appl/$aid") } } }) { Text("Yes, apply") }
            if (o.url.isNotBlank()) OutlinedButton(onClick = { Share.open(ctx, o.url) }) { Text("Open link") }
            if (o.url.isNotBlank()) OutlinedButton(onClick = {
                status = "Reading the page…"
                scope.io({ status = it }) {
                    val page = Fetch.pageText(o.url)
                    val found = if (G.ai.ready) try { G.ai.extractCalls("Link: ${o.url}\n" + page, o.sourceName).firstOrNull() } catch (e: Exception) { null } else null
                    if (found != null) {
                        val merged = Engine.scored(found.copy(id = o.id, url = o.url.ifBlank { found.url }, status = o.status, createdAt = o.createdAt), p)
                        o = merged; dao.put(merged)
                    } else {
                        // No AI: the plain reader fills only what is still empty (deadline, fee, title).
                        val r = com.emotionalleaves.studioagent.domain.CallReader.read(page, LocalDate.now(), java.time.ZoneId.systemDefault(), o.sourceName, now())
                        val merged = Engine.scored(o.copy(
                            deadline = if (o.deadline > 0) o.deadline else r.deadline,
                            fee = if (o.fee > 0) o.fee else r.fee,
                            title = if (o.title.isBlank() || o.title == "New call") r.title.ifBlank { o.title } else o.title,
                            rawText = o.rawText.ifBlank { page.take(4000) }
                        ), p)
                        o = merged; dao.put(merged)
                        deadlineText = if (merged.deadline > 0) Engine.date(merged.deadline).toString() else ""
                    }
                    status = "Filled in from the page. Check the deadline and fee against the page yourself."
                }
            }) { Text("Read the page for me") }
        }
        Expand("Details", startOpen = o.title.isBlank() || o.title == "New call" || o.deadline <= 0) {
        Field("Title", o.title, { save(o.copy(title = it)) })
        Choice("Kind of call", Ai.callKinds, o.kind) { save(o.copy(kind = it)) }
        Field("Deadline (YYYY-MM-DD)", deadlineText, { deadlineText = it; val ms = Ai.parseDate(it); if (ms > 0 || it.isBlank()) save(o.copy(deadline = ms)) })
        Num("Entry fee, " + st.currency, o.fee) { save(o.copy(fee = it)) }
        Choice("Reach", listOf("campus", "local", "online", "regional", "national", "international"), o.scope) { save(o.copy(scope = it)) }
        Field("Link", o.url, { save(o.copy(url = it.trim())) })
        SavedLine()

        Field("Organiser", o.organiser, { save(o.copy(organiser = it)) })
        Field("Venue", o.venue, { save(o.copy(venue = it)) })
        Field("City", o.city, { save(o.copy(city = it)) })
        Toggle("Online only", o.online) { save(o.copy(online = it)) }
        Field("Prizes", o.prizes, { save(o.copy(prizes = it)) })
        Field("Theme", o.theme, { save(o.copy(theme = it)) }, lines = 2)
        Field("Jurors", o.jurors, { save(o.copy(jurors = it)) })
        Field("Mediums accepted", o.mediums, { save(o.copy(mediums = it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Count("Fewest works", o.minWorks, 0, 20) { save(o.copy(minWorks = it)) }; Count("Most works", o.maxWorks, 0, 20) { save(o.copy(maxWorks = it)) } }
        }
        Field("Image rules (for example: JPEG, 1920 px, under 5 MB)", o.imageSpecs, { save(o.copy(imageSpecs = it)) })
        Whole("Statement word limit", o.statementLimit) { save(o.copy(statementLimit = it)) }
        Field("Send entries to (email, if entries go by email)", o.email, { save(o.copy(email = it.trim())) })
        Toggle("Accepts prints of digital work", o.acceptsPrints) { save(o.copy(acceptsPrints = it)) }
        Toggle("Accepts screen-based work", o.acceptsNewMedia) { save(o.copy(acceptsNewMedia = it)) }
        Toggle("Paying the fee guarantees a place", o.guaranteed) { save(o.copy(guaranteed = it)) }
        Toggle("Terms take broad rights to my images", o.rightsGrab) { save(o.copy(rightsGrab = it)) }
        }
        Row {
            if (o.organiser.isNotBlank()) TextButton(onClick = { st.blocked = (st.blocked.split(",").map { it.trim() }.filter { it.isNotBlank() } + o.organiser).distinct().joinToString(", "); status = "Blocked. Their calls will be held back." }) { Text("Block this organiser") }
            DangerButton(onClick = { scope.remove(o.title.ifBlank { "the call" }, { dao.del(o) }, { dao.put(o) }); nav.popBackStack() }) { Text("Delete this call") }
        }
    }
}

@Composable
fun ApplScreen(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val buzz = rememberBuzz()
    val scope = rememberCoroutineScope()
    val appls by dao.appls().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val events by dao.events().collectAsState(emptyList())
    val loaded = appls.firstOrNull { it.id == id }
    val o = opps.firstOrNull { it.id == loaded?.oppId }
    if (loaded == null || o == null) { Screen("Application", nav) { Loading() }; return }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val resultText = rememberSaveable { mutableStateOf("") }
    val feeLeft = Engine.feeLeft(ledger)

    if (loaded.status in setOf("submitted", "accepted", "declined")) {
        val chosen = works.filter { it.id in Engine.workIds(loaded) }
        Screen(o.title, nav) {
            Panel {
                Text(when (loaded.status) { "accepted" -> "Accepted"; "declined" -> "Not this time"; else -> "Submitted. Done." }, fontWeight = FontWeight.SemiBold)
                Text("Sent ${fmtDate(loaded.submittedAt)}.")
                if (loaded.proofText.isNotBlank()) Note(loaded.proofText)
                if (loaded.status == "submitted") Small("This application is locked. There is nothing left to check: the result comes to you" + (if (st.mailReady) " automatically." else " by email, and you record it below."))
                if (loaded.status == "declined") Note("The works are free again and will be offered to the next call that fits.")
            }
            if (loaded.proofPath.isNotBlank()) PathImage(loaded.proofPath, Modifier.fillMaxWidth())
            chosen.forEach { Note(Papers.caption(it)) }
            Status(status)
            if (loaded.status == "submitted") {
                H("Result")
                Field("Paste the result email (optional: builds your checklist)", resultText, lines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { busy = true; status = "Setting up your checklist…"; scope.io({ status = it; busy = false }) { Engine.recordResult(ctx, loaded, true, resultText.value); status = ""; busy = false } }) { Text("Accepted") }
                    OutlinedButton(enabled = !busy, onClick = { scope.io { Engine.recordResult(ctx, loaded, false, resultText.value) } }) { Text("Not this time") }
                }
            }
            if (loaded.status == "accepted") {
                H("Checklist")
                events.filter { it.applId == id && it.type == "task" }.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = e.done, onCheckedChange = { c -> scope.io { dao.put(e.copy(done = c)) } })
                        Column { Text(e.title); if (e.date > 0) Note(fmtDate(e.date)) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.labels(ctx, st, chosen); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Wall labels") }
                    OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.imageList(ctx, st, chosen, true); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Price list") }
                    OutlinedButton(onClick = { Share.text(ctx, Papers.printOrder(st, chosen)) }) { Text("Print and framing order") }
                }
                if (o.commission > 0) Note("Commission ${"%.0f".format(o.commission)}%: " + chosen.filter { it.price > 0 && !it.nfs }.joinToString("; ") { "${it.title}: you receive ${money(it.price * (1 - o.commission / 100))}" })
                Note("Shipping: box about 4 in larger than the work on every side, corner protectors, glassine over the face, never tape on the work. Take condition photos before it leaves.")
            }
            if (loaded.status == "submitted") Expand("Something really has to change") {
                Note("Only for a real reason, such as the organiser asking you to resend. Reopening unlocks the pack and frees the works.")
                TextButton(onClick = { scope.io { Engine.reopen(loaded) } }) { Text("Reopen this application") }
            }
        }
        return
    }

    var a by remember(id) { mutableStateOf(loaded) }
    fun save(x: com.emotionalleaves.studioagent.data.Appl) { a = x; scope.io { dao.put(x); markSaved() } }
    val checks = Engine.checklist(a, o, works, feeLeft, now())
    val allGreen = checks.all { it.second }
    val chosenIds = Engine.workIds(a)
    val files = remember(a.packDir, status) { Engine.packFiles(a) }
    val newQuestion = rememberSaveable { mutableStateOf("") }

    Screen(o.title, nav) {
        Note((if (o.deadline > 0) "Deadline ${fmtDate(o.deadline)} (${Planner.weekendsLeft(LocalDate.now(), Engine.date(o.deadline))}). " else "") + "The app aims to send 3 days early.")
        StageHead(1, "Choose the works", chosenIds.isNotEmpty())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            works.filter { Engine.available(it) || it.id in chosenIds }.forEach { w ->
                FilterChip(selected = w.id in chosenIds, onClick = {
                    val next = if (w.id in chosenIds) chosenIds - w.id else chosenIds + w.id
                    save(a.copy(workIds = next.joinToString(","), packDir = ""))
                }, label = { Text(w.title.ifBlank { "Untitled #${w.id}" }) })
            }
        }
        StageHead(2, "Statement and fit", a.statement.isNotBlank() && a.themeText.isNotBlank())
        Field((if (o.statementLimit > 0) "Limit ${o.statementLimit} words. " else "") + "Edit only if this call needs it", a.statement, { save(a.copy(statement = it, packDir = "")) }, lines = 5)
        Small("${Texts.words(a.statement)} words" + (if (o.statementLimit > 0) " of ${o.statementLimit} allowed" else ""))
        Field("How these works fit this call (2 to 3 sentences)", a.themeText, { save(a.copy(themeText = it, packDir = "")) }, lines = 3)
        val starterFit = { Starter.fit(works.filter { it.id in chosenIds }.map { it.title.ifBlank { "Untitled" } }, o, a.statement) }
        if (!G.ai.ready && a.themeText.isBlank()) TextButton(onClick = { save(a.copy(themeText = starterFit(), packDir = "")) }) { Text("Start it for me") }
        if (G.ai.ready && a.revisions < st.revisionLimit + 1) TextButton(enabled = !busy, onClick = {
            busy = true; status = "Writing…"
            scope.io({ status = it; busy = false }) {
                val t = try { G.ai.themeParagraph(o, Engine.factsText(chosenIds), Engine.voice()) } catch (e: Exception) {
                    if (a.themeText.isNotBlank()) throw e
                    status = "The writing help did not answer, so here is a starter to finish."; starterFit()
                }
                val x = a.copy(themeText = t, revisions = a.revisions + 1, packDir = ""); a = x; dao.put(x); if (status == "Writing…") status = ""; busy = false
            }
        }) { Text(if (a.themeText.isBlank()) "Write the fit paragraph for me" else "Rewrite it") }

        val qa = Engine.answers(a)
        Expand("Questions this call asks (" + qa.size + ")", startOpen = qa.isNotEmpty()) {
            Note("If the entry form has its own questions, add each one here. The answers go into the pack and are ready to paste.")
            qa.forEachIndexed { i, (q, ans) ->
                key(i, q) {
                    Field(q, ans, { v -> save(a.copy(answers = Engine.answersJson(qa.mapIndexed { j, p -> if (j == i) p.first to v else p }), packDir = "")) }, lines = 3)
                    Row {
                        if (G.ai.ready) TextButton(enabled = !busy, onClick = {
                            busy = true; status = "Writing…"
                            scope.io({ status = it; busy = false }) {
                                val t = try { G.ai.write("an answer to this application question: $q", 120, Engine.factsText(chosenIds), Engine.voice(), "The call: ${o.title}. Theme: ${o.theme}") } catch (e: Exception) { if (ans.isNotBlank()) throw e; Starter.answer(a.statement) }
                                val x = a.copy(answers = Engine.answersJson(qa.mapIndexed { j, p -> if (j == i) p.first to t else p }), packDir = ""); a = x; dao.put(x); status = ""; busy = false
                            }
                        }) { Text("Draft an answer") }
                        else if (ans.isBlank()) TextButton(onClick = { save(a.copy(answers = Engine.answersJson(qa.mapIndexed { j, p -> if (j == i) p.first to Starter.answer(a.statement) else p }), packDir = "")) }) { Text("Start from my statement") }
                        TextButton(onClick = { save(a.copy(answers = Engine.answersJson(qa.filterIndexed { j, _ -> j != i }), packDir = "")) }) { Text("Remove this question") }
                    }
                }
            }
            Field("Add a question", newQuestion)
            if (newQuestion.value.isNotBlank()) OutlinedButton(onClick = {
                val q = newQuestion.value.trim(); newQuestion.value = ""
                scope.io({ status = it }) {
                    // If you have answered this before, start from that answer instead of a blank box.
                    val prior = Engine.priorAnswer(q, dao.applsNow(), a.id)
                    val x = a.copy(answers = Engine.answersJson(qa + (q to prior)), packDir = ""); a = x; dao.put(x); markSaved()
                    if (prior.isNotBlank()) status = "You answered a question like this before, so that answer is filled in. Check it still fits this call."
                }
            }) { Text("Add question") }
        }

        StageHead(3, "Check", allGreen)
        Panel(strong = allGreen) {
            Text(if (allGreen) "Every check passes. This is ready." else "Ready when these are all true", fontWeight = FontWeight.SemiBold)
            checks.forEach { (label, ok) -> Check(ok, label) }
        }
        Status(status)
        SavedLine()

        StageHead(4, "The pack", files.isNotEmpty())
        // Once every check passes the files are made by themselves: nothing to press.
        LaunchedEffect(allGreen, a.packDir) {
            if (allGreen && files.isEmpty() && chosenIds.isNotEmpty() && !busy) {
                busy = true; status = "Every check passes, so the pack is being made…"
                try { val dir = Engine.buildPack(ctx, a, o); a = a.copy(packDir = dir.absolutePath); status = "Pack made." } catch (e: Exception) { status = plainError(e) }
                busy = false
            }
        }
        if (files.isEmpty() && !allGreen) Small("The pictures, CV, image list and statement are made here as soon as every check above passes.")
        if (files.isNotEmpty()) {
            files.forEach { Note("${it.name}  (${it.length() / 1024} KB)") }
            OutlinedButton(onClick = { Share.files(ctx, files) }) { Text("Share or save the files") }
        }

        StageHead(5, "Send it", a.submittedAt > 0)
        Choice("This call takes entries by", listOf("email", "web"), a.method) { save(a.copy(method = it)) }
        if (o.fee > 0) {
            if (a.feePaid > 0) Note("Fee of ${money(a.feePaid)} logged.")
            else OutlinedButton(onClick = {
                scope.io { dao.put(Ledger(kind = "fee", amount = -o.fee, date = now(), note = o.title, oppId = o.id)) }
                save(a.copy(feePaid = o.fee))
            }) { Text("I paid the ${money(o.fee)} fee") }
            Note("The app never pays for you. You pay on the organiser's page; it keeps the record.")
        }
        if (a.method == "email") {
            Field("To", a.emailTo, { save(a.copy(emailTo = it.trim())) })
            Field("Subject", a.emailSubject, { save(a.copy(emailSubject = it)) })
            Field("Email", a.emailBody, { save(a.copy(emailBody = it)) }, lines = 6)
            if (a.status == "sending") Panel {
                Text("Approved. It sends in about 10 minutes.")
                OutlinedButton(onClick = { Engine.cancelSend(ctx, a.id); save(a.copy(status = "ready")) }) { Text("Undo") }
            } else if (st.mailReady) Button(enabled = allGreen && files.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                save(a.copy(status = "sending")); Engine.queueSend(ctx, a.id)
            }) { Text("Approve and send") }
            else {
                Button(enabled = allGreen && files.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = { Share.email(ctx, a.emailTo, a.emailSubject, a.emailBody, files) }) { Text("Open in my mail app, filled in") }
                OutlinedButton(onClick = { buzz(); scope.io { Engine.markSubmitted(a, "Sent from your mail app to ${a.emailTo} on ${fmtDate(now())}.") } }) { Text("I sent it") }
                Note("Add your email and an app password in You, Settings, Email and the app sends it for you instead.")
            }
        } else {
            Button(enabled = o.url.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = { nav.navigate("web/${a.id}") }) { Text("Fill in the entry form") }
            OutlinedButton(onClick = { buzz(); scope.io { Engine.markSubmitted(a, "Submitted on the organiser's site on ${fmtDate(now())}.") } }) { Text("I submitted it") }
        }
        if (!allGreen) Note("Sending unlocks when every check above passes.")
    }
}

/**
 * Fills empty boxes on an entry form by what their labels say. Work boxes numbered 2, 3... ("Title of work 2") get that work's details.
 * It never presses submit and never changes a box you already filled.
 */
private const val FILL_JS = """
(function(d){
 function key(el){var s=(el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||'');
  if(el.labels&&el.labels.length){s+=' '+el.labels[0].innerText;} return s.toLowerCase();}
 function setv(el,v){var p=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
  var set=Object.getOwnPropertyDescriptor(p,'value').set; set.call(el,v);
  el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true}));}
 function work(k){var m=k.replace(/(19|20)\d\d/g,'').match(/(\d{1,2})/); var i=m?parseInt(m[1],10)-1:0;
  return d.works[(i>=0&&i<d.works.length)?i:0]||{};}
 var person=[['first',d.first],['last',d.last],['surname',d.last],['family name',d.last],['email',d.email],['phone',d.phone],['mobile',d.phone],
  ['website',d.website],['portfolio',d.website],['url',d.website],['city',d.city],['statement',d.statement],['bio',d.bio]];
 var piece=['title','medium','materials','dimension','size','year','price','value'];
 var n=0; var els=document.querySelectorAll('input[type=text],input[type=email],input[type=tel],input[type=url],input[type=number],input:not([type]),textarea');
 els.forEach(function(el){ if(el.value){return;} var k=key(el); var v='';
  for(var i=0;i<person.length&&!v;i++){ if(k.indexOf(person[i][0])>=0){ v=person[i][1]; } }
  if(!v){ for(var j=0;j<piece.length&&!v;j++){ if(k.indexOf(piece[j])>=0){ var w=work(k);
   v={title:w.title,medium:w.medium,materials:w.medium,dimension:w.dims,size:w.dims,year:w.year,price:w.price,value:w.price}[piece[j]]; } } }
  if(!v && k.indexOf('name')>=0 && k.indexOf('title')<0){ v=d.name; }
  if(v){ setv(el,v); n++; } });
 return n;
})
"""

@Composable
fun WebFill(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val appls by dao.appls().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val a = appls.firstOrNull { it.id == id }
    val o = opps.firstOrNull { it.id == a?.oppId }
    var web by remember { mutableStateOf<WebView?>(null) }
    var chooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var copying by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var status by remember { mutableStateOf("Sign in if the site asks; it stays signed in here. Then tap Fill.") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Entry form", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        if (a != null) {
                            val bio = docs.filter { it.type == "bio" && it.text.isNotBlank() }.maxByOrNull { if (it.approved) 1 else 0 }?.text.orEmpty()
                            val ids = Engine.workIds(a)
                            val list = org.json.JSONArray()
                            works.filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }.forEach { w ->
                                list.put(JSONObject().put("title", w.title).put("medium", Papers.medium(w)).put("dims", Texts.dims(w.widthIn, w.heightIn, w.depthIn))
                                    .put("year", w.year.takeIf { it > 0 }?.toString().orEmpty()).put("price", if (w.nfs) "NFS" else w.price.takeIf { it > 0 }?.let { "%.0f".format(it) }.orEmpty()))
                            }
                            val d = JSONObject().put("first", st.firstName).put("last", st.lastName).put("name", st.fullName).put("email", st.email)
                                .put("phone", st.phone).put("website", st.website).put("city", st.city).put("statement", a.statement).put("bio", bio)
                                .put("works", list)
                            web?.evaluateJavascript(FILL_JS + "(" + d.toString() + ")") { r -> status = "Filled ${r ?: "0"} fields. Read each one, then press the site's own submit button." }
                        }
                    }) { Text("Fill") }
                    TextButton(onClick = { copying = !copying }) { Text(if (copying) "Hide" else "Copy") }
                    TextButton(onClick = {
                        val w = web
                        if (a != null && w != null) scope.io({ status = it }) {
                            val f = File(ImageTools.mediaDir(ctx), "proof_${a.id}.jpg")
                            val shot = withContext(Dispatchers.Main) {
                                val b = Bitmap.createBitmap(w.width.coerceAtLeast(1), w.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                w.draw(Canvas(b)); b
                            }
                            ImageTools.saveJpeg(shot, f, 85)
                            val proof = withContext(Dispatchers.Main) { "Submitted on ${w.title.orEmpty()} (${w.url.orEmpty()}) on ${fmtDate(now())}." }
                            Engine.markSubmitted(a, proof, f.absolutePath)
                            withContext(Dispatchers.Main) { nav.navigate("appl/${a.id}") { popUpTo("calls") } }
                        }
                    }) { Text("Submitted") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            if (copying && a != null) {
                // When Fill misses a box: tap a line to copy it, then long-press the box on the form and paste.
                val chosen = works.filter { it.id in Engine.workIds(a) }
                val bio = docs.filter { it.type == "bio" && it.text.isNotBlank() }.maxByOrNull { if (it.approved) 1 else 0 }?.text.orEmpty()
                val items = listOf("Name" to st.fullName, "Email" to st.email, "Phone" to st.phone, "Website" to st.website, "City" to st.city,
                    "Statement" to a.statement, "How the work fits" to a.themeText, "Bio" to bio) +
                    chosen.flatMap { w -> listOf("Title" to w.title, "Details of ${w.title}" to Papers.caption(w), "Price of ${w.title}" to Papers.priceLine(w)) } +
                    Engine.answers(a).map { it.first to it.second }
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    items.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                        Text("$label: " + value.take(70) + (if (value.length > 70) "..." else ""), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(value)); status = "Copied: $label. Long-press the box on the form and choose Paste." }.padding(vertical = 8.dp))
                    }
                }
            }
            val cb = chooser
            if (cb != null && a != null) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text("The form is asking for a file. Pick from your pack:", fontWeight = FontWeight.SemiBold)
                    Engine.packFiles(a).forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = f.absolutePath in picked, onCheckedChange = { c -> if (c) picked.add(f.absolutePath) else picked.remove(f.absolutePath) })
                            Text(f.name)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { cb.onReceiveValue(picked.map { Share.uri(ctx, File(it)) }.toTypedArray()); picked.clear(); chooser = null }) { Text("Attach") }
                        TextButton(onClick = { cb.onReceiveValue(null); picked.clear(); chooser = null }) { Text("Cancel") }
                    }
                }
            }
            if (o != null && o.url.isNotBlank()) {
                val url = if (o.url.startsWith("http")) o.url else "https://${o.url}"
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: WebChromeClient.FileChooserParams?): Boolean {
                                    chooser?.onReceiveValue(null)
                                    chooser = callback
                                    return true
                                }
                            }
                            loadUrl(url)
                            web = this
                        }
                    }
                )
            } else Box(Modifier.padding(20.dp)) { Empty("This call has no link yet, so there is no form to fill. Add the link on the call's page.", "Go to the call") { nav.popBackStack() } }
        }
    }
}

@Composable
fun Sources(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by dao.sources().collectAsState(emptyList())
    val name = rememberSaveable { mutableStateOf("") }
    val value = rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("email") }
    var status by remember { mutableStateOf(if (st.lastDaily > 0) "Last daily check ${fmtDate(st.lastDaily)}: ${st.lastDailyStatus}" else "") }
    var busy by remember { mutableStateOf(false) }
    Screen("Where calls come from", nav) {
        Intro("sources", "Four big listing sites are already here and are read once a day. You can add more: a listing page, or the sender of email alerts you get.")
        LaunchedEffect(Unit) { Engine.seedSources() }
        if (sources.isEmpty()) Empty("No sources yet. Calls you share or paste still arrive without one.")
        Explain("What can I add here?", "Email source: the sender's address or domain (for example callforentry.org). Needs email set up in You, Settings, Email. Page source: only add a listing page whose terms allow automated reading; the app reads it once a day. Pages that build their list with scripts will not work.")
        // One row per source: the switch is the only control. Removing one sits behind the details.
        sources.forEach { s ->
            val where = if (s.kind == "email") "Email alerts from ${s.url}" else (try { java.net.URI(s.url).host.removePrefix("www.") } catch (e: Exception) { s.url })
            Toggle(s.name, s.enabled) { on -> scope.io { dao.put(s.copy(enabled = on)) } }
            Small(where + (if (s.lastStatus.isNotBlank()) ". ${fmtDate(s.lastFetched)}: ${s.lastStatus}" else ""))
            Hair()
        }
        if (sources.isNotEmpty()) Expand("Remove a source") {
            Small("Turning a source off keeps it for later. Removing it can be undone for a few seconds.")
            sources.forEach { s -> TextButton(onClick = { scope.remove("the source", { dao.del(s) }, { dao.put(s) }) }) { Text("Remove ${s.name}") } }
        }
        H("Add a source")
        Choice("Kind", listOf("email", "url"), kind, names = { if (it == "email") "Email alerts" else "Listing page" }) { kind = it }
        Field("Name", name)
        Field(if (kind == "email") "Sender address or domain" else "Page address", value)
        // Appears once there is something to add, like Park it.
        if (name.value.isNotBlank() && value.value.isNotBlank()) Button(onClick = {
            val s = Source(name = name.value.trim(), kind = kind, url = value.value.trim()); name.value = ""; value.value = ""
            scope.io { dao.put(s) }
        }) { Text("Add source") }
        Status(status)
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; status = "Running the daily check…"
            scope.io({ status = it; busy = false }) { status = Engine.runDaily(ctx); busy = false }
        }) { Text("Run the daily check now") }
        Note("Listing pages are read once a day: the app opens each new call on the page and picks out its title, deadline and fee. Sites that ask apps to stay out are left alone.")
    }
}


/** A numbered stage header for work that really is done in order. The number becomes a tick once the stage is done. */
@Composable
fun StageHead(n: Int, title: String, done: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 16.dp).semantics(mergeDescendants = true) { heading(); stateDescription = if (done) "done" else "to do" },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) cs.primary else cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = cs.onPrimary)
            else Text("$n", style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}


/** What your level lets through, in one line, with the way to change it right beside it. */
@Composable
fun LevelLine(nav: NavController, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { nav.navigate("settings/rules") }) { Text("Change") }
    }
}
