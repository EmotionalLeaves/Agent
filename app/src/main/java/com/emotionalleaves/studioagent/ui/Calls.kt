package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.layout.offset
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
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

fun whereLine(o: Opp): String = listOf(o.organiser, if (o.online) "Online" else o.city).filter { it.isNotBlank() }.joinToString(", ")

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
        // Opening Calls re-ranks with your latest details (level, city, rules), so the list is never stale.
        LaunchedEffect(Unit) { st.lastCallsLook = now(); try { withContext(Dispatchers.IO) { Engine.rescore() } } catch (e: Exception) { } }
        Choice("", listOf("this week", "all", "applied"), tab) { tab = it }
        if (kindsPresent.size > 1 && tab != "applied") Choice("", listOf("every kind") + kindsPresent, kindFilter, names = { mapOf("every kind" to "Every kind", "fair" to "Art fairs", "exhibition" to "Exhibitions", "residency" to "Residencies", "grant" to "Grants", "publication" to "Publications", "commission" to "Commissions", "other" to "Other")[it] ?: it }) { kindFilter = it }
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
                    val held = opps.count { it.status == "new" && it.deadline > now() }
                    Empty(if (held > 0) "Nothing fits your level this week. ${Texts.count(held, "open call")} " + (if (held == 1) "is" else "are") + " under All, with the reason each is held back."
                        else "No calls waiting. The app looks once a day on its own. When you see one anywhere, tap Share and choose Studio Agent, or paste it below.",
                        if (held > 0) "Show all" else "") { tab = "all" }
                    var looking by remember { mutableStateOf(false) }
                    OutlinedButton(enabled = !looking, onClick = {
                        looking = true; status = "Looking at the listing sites. This takes a minute or two…"
                        scope.io({ status = it; looking = false }) {
                            val n = Engine.scanSources(now()); Engine.rescore()
                            val on = dao.sourcesNow().count { it.kind == "url" && it.enabled }
                            status = if (on == 0) "Every listing site is switched off. Turn one on under All, Where calls come from." else if (n == 0) "Looked. Nothing new right now; the app checks again tomorrow." else "Found $n new " + (if (n == 1) "call" else "calls") + ". Ones that fit your level are above; the rest are under All."
                            looking = false
                        }
                    }) { Text(if (looking) "Looking…" else "Look for calls now") }
                }
                if (list.isNotEmpty()) Text(if (list.size == 1) "One call fits you this week. Yes or No takes a minute." else "${list.size} calls fit you this week, best fit first. Yes or No on each takes a minute.", style = MaterialTheme.typography.bodyMedium)
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
                                Tag(if (o.fee > 0) money(o.fee) else if (Texts.feeStated(o)) "Free" else "Fee?")
                                if (o.kind != "exhibition" && o.kind.isNotBlank()) Tag(o.kind.replaceFirstChar { it.uppercase() })
                            }
                            o.fitReasons.lines().filter { it.isNotBlank() && !it.startsWith("Free to enter") && !it.startsWith("Fee ") && !it.startsWith("Fee not stated") }.take(2).forEach { Small(it) }
                            Flags(o)
                            if (saidNo == o.id) {
                                // "Not this year" is one of the answers to No, so the card needs only two buttons.
                                Choice("Why not? (optional, it teaches the ranking)", listOf("not this year", "too far", "fee", "wrong vibe", "no reason"), "", names = { mapOf("not this year" to "Not this year", "too far" to "Too far", "fee" to "The fee", "wrong vibe" to "Not my kind of show", "no reason" to "No reason")[it] ?: it }) { r ->
                                    saidNo = 0L
                                    // A No can be taken back for 30 seconds, like everything else.
                                    if (r == "not this year") scope.undoable("Not this year: ${o.title}", { dao.put(o.copy(status = "later")) }, { dao.put(o) })
                                    else {
                                        if (r != "no reason") st.addNoReason(r)
                                        scope.undoable("Said no to ${o.title}", { dao.put(o.copy(status = "no", noReason = r)); Engine.rescore() }, { dao.put(o); Engine.rescore() })
                                    }
                                }
                            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { scope.io({ status = it }) { val id = Engine.createApplication(ctx, o); withContext(Dispatchers.Main) { nav.navigate("appl/$id") } } }) { Text("Yes, apply") }
                                OutlinedButton(onClick = { saidNo = o.id }) { Text("No") }
                            }
                        }
                    }
                }
                // So you can tell the app is working without opening anything.
                val looked = maxOf(st.lastScan, st.lastDaily)
                if (looked > 0) Small("The app last looked for new calls on ${fmtDate(looked)}, at ${clock(looked)}.")
                H("Add a call")
                Field("Paste a link or the call text", paste, lines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (paste.value.isNotBlank()) Button(onClick = {
                        val t0 = paste.value.trim(); paste.value = ""; status = "Reading it…"
                        scope.io({ status = it }) {
                            val isUrl = Texts.isLink(t0)
                            val t = if (isUrl && !t0.startsWith("http")) "https://$t0" else t0
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
                FlyerButtons(nav) { status = it }
            }
            "all" -> {
                Small("Everything found, including calls your rules hold back, and why.")
                IconRow(Icons.Default.Notifications, "Where calls come from", "Email alerts and pages the app checks each day") { nav.navigate("sources") }
                var allLimit by rememberSaveable { mutableStateOf(50) }
                // Open calls first, the soonest deadline at the top; closed ones sink to the bottom.
                // With dozens of calls from eight sites: find one by a word (a city, a medium, a venue, "photography").
                val find = rememberSaveable { mutableStateOf("") }
                if (opps.size > 12) Field("Find a call (a word, a city, a medium)", find)
                val words = find.value.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
                val everything = opps.filter { it.status in setOf("new", "later", "no", "expired") && kindOk(it) &&
                        words.all { w -> callWords(it).contains(w) } }
                    .sortedWith(compareBy<Opp>({ it.status == "expired" || (it.deadline in 1 until now()) }, { if (it.deadline > 0) it.deadline else Long.MAX_VALUE }))
                if (everything.size > allLimit) Small("Showing the first $allLimit of ${everything.size}.")
                // Grouped by the month they close, so the year of calls reads like a calendar.
                var lastMonth = ""
                everything.take(allLimit).forEach { o ->
                    val month = when { o.status == "expired" || o.deadline in 1 until now() -> "Closed"; o.deadline <= 0 -> "No deadline given"
                        else -> Engine.date(o.deadline).let { d -> d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + (if (d.year != LocalDate.now().year) " ${d.year}" else "") } }
                    if (month != lastMonth) { Section(month + " (" + everything.take(allLimit).count { x -> (when { x.status == "expired" || x.deadline in 1 until now() -> "Closed"; x.deadline <= 0 -> "No deadline given"; else -> Engine.date(x.deadline).let { d -> d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + (if (d.year != LocalDate.now().year) " ${d.year}" else "") } }) == month } + ")"); lastMonth = month }
                    val why = Scoring.ineligible(o, p, now())
                    Panel(onClick = { nav.navigate("call/${o.id}") }) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall)
                        Small(listOf(o.kind.replaceFirstChar { it.uppercase() }, whereLine(o), if (o.deadline > 0) fmtDate(o.deadline) else "", if (o.fee > 0) money(o.fee) else "free").filter { it.isNotBlank() }.joinToString(", "))
                        Small(when { o.status == "later" -> "You said not this year"; o.status == "no" -> "You said no"; o.status == "expired" -> "Deadline passed"; why.isEmpty() -> "Eligible, " + fitLabel(o.fitScore).lowercase(); else -> "Held back: " + why.joinToString("; ") })
                        Flags(o)
                    }
                }
                if (everything.size > allLimit) OutlinedButton(onClick = { allLimit += 50 }, modifier = Modifier.fillMaxWidth()) { Text("Show ${minOf(50, everything.size - allLimit)} more") }
            }
            else -> {
                if (appls.isEmpty()) Empty("Nothing applied to yet. Say Yes to a call and the app builds the application.")
                // Ones still being prepared come first, nearest deadline at the top; finished ones follow, newest first.
                val oppOf = opps.associateBy { it.id }
                val evts by dao.events().collectAsState(emptyList())
                // Every entry on one track, from chosen to the work coming home, grouped by what it needs from you.
                fun open(a: com.emotionalleaves.studioagent.data.Appl) = evts.filter { it.applId == a.id && it.type == "task" && !it.done }
                fun stage(a: com.emotionalleaves.studioagent.data.Appl): Int = when (a.status) {
                    "submitted", "sending" -> 1; "declined" -> 2
                    "accepted" -> { val t = evts.filter { it.applId == a.id && it.type == "task" }
                        when { t.any { it.done && it.title.startsWith("Pick up", true) } || (t.isNotEmpty() && t.all { it.done }) -> 4
                            t.any { it.done && (it.title.startsWith("Deliver", true) || it.title.contains("ship", true)) } -> 3; else -> 2 } }
                    else -> 0
                }
                val toFinish = appls.filter { it.status !in setOf("submitted", "sending", "accepted", "declined") }
                    .sortedBy { oppOf[it.oppId]?.deadline?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
                val waiting = appls.filter { it.status in setOf("submitted", "sending") }.sortedByDescending { it.submittedAt }
                val shows = appls.filter { it.status == "accepted" && stage(it) < 4 }.sortedBy { open(it).minOfOrNull { e -> if (e.date > 0) e.date else Long.MAX_VALUE } ?: Long.MAX_VALUE }
                val done = appls.filter { it.status == "declined" || (it.status == "accepted" && stage(it) >= 4) }.sortedByDescending { it.submittedAt }
                Text(listOfNotNull(
                    toFinish.size.takeIf { it > 0 }?.let { "${Texts.count(it, "application")} to finish" },
                    waiting.size.takeIf { it > 0 }?.let { "$it waiting to hear" },
                    shows.size.takeIf { it > 0 }?.let { Texts.count(it, "show") + " you got into" }
                ).joinToString(", ").replaceFirstChar { it.uppercase() }.let { if (it.isBlank()) "Nothing in progress." else "$it." }, style = MaterialTheme.typography.titleMedium)
                @Composable fun entry(a: com.emotionalleaves.studioagent.data.Appl) {
                    val o = oppOf[a.oppId]
                    Panel(onClick = { nav.navigate("appl/${a.id}") }) {
                        Text(o?.title ?: "Application", style = MaterialTheme.typography.titleSmall)
                        StageTrack(stage(a), a.status == "declined")
                        Small(when (a.status) {
                            "submitted" -> "Submitted ${fmtDate(a.submittedAt)}. " +
                                // When the call said results come: knowing the date ends the checking.
                                (o?.let { resultsDay(it) }?.let { "Results expected about ${fmtDate(Engine.millis(it.atTime(12, 0)))}. " } ?: "") +
                                (if (st.mailReady) "The result email is picked up for you." else "When you hear back, open it and tap Accepted or Not this time.")
                            // Accepted: the one thing to do next, with its day, so the show does not slip.
                            "accepted" -> open(a).sortedBy { if (it.date > 0) it.date else Long.MAX_VALUE }.firstOrNull()
                                ?.let { "Next: " + it.title + (if (it.date > 0) ", " + fmtDate(it.date) else "") } ?: "Everything on its list is done."
                            "declined" -> "Not this time. The works are free for the next call."
                            "sending" -> "Sending shortly"
                            else -> "Being prepared" + (o?.deadline?.takeIf { it > 0 }?.let { ". Deadline ${fmtDate(it)}, " + Planner.weekendsLeft(LocalDate.now(), Engine.date(it)) } ?: "")
                        })
                    }
                }
                if (shows.isNotEmpty()) { Section("Shows you got into"); shows.forEach { entry(it) } }
                if (toFinish.isNotEmpty()) { Section("To finish"); toFinish.forEach { entry(it) } }
                if (waiting.isNotEmpty()) { Section("Waiting to hear"); waiting.forEach { entry(it) } }
                if (done.isNotEmpty()) Expand("Finished (${done.size})") { done.forEach { entry(it) } }
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
            if (o.sourceName == "Flyer photo") Small("Read from a flyer photo. Check the deadline and fee against the flyer itself.")
            else if (o.sourceName.isNotBlank() && o.sourceName !in setOf("Pasted", "Shared", "probe", "By hand")) Small("Found on ${o.sourceName}" + (if (o.createdAt > 1_000_000_000_000L) ", ${fmtDate(o.createdAt)}" else ""))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Small("Deadline")
                    Text(if (o.deadline > 0) fmtDate(o.deadline) else "Not set", style = MaterialTheme.typography.titleLarge)
                    if (o.deadline > 0) Text(Planner.weekendsLeft(LocalDate.now(), Engine.date(o.deadline)), style = MaterialTheme.typography.bodySmall,
                        color = if (Engine.date(o.deadline).isBefore(LocalDate.now().plusDays(15))) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Small("Entry fee")
                    Text(if (o.fee > 0) money(o.fee) else if (Texts.feeStated(o)) "Free" else "Not stated", style = MaterialTheme.typography.titleLarge)
                }
            }
            Hair()
            Row { Tag(fitLabel(o.fitScore), strong = o.fitScore >= 60) }
            o.fitReasons.lines().filter { it.isNotBlank() }.forEach { Text(it) }
            if (why.isNotEmpty()) Text("Held back by your rules: " + why.joinToString("; "), color = MaterialTheme.colorScheme.secondary)
            Flags(o)
            // The signs of the usual schemes, read from the call's own words (paid features, awards that cost, urgency).
            remember(o.id, o.rawText) { com.emotionalleaves.studioagent.domain.Offers.check(o.rawText).filter { !it.what.startsWith("The fee buys the place") } }
                .takeIf { it.isNotEmpty() }?.let { signs -> signs.take(2).forEach { Text("Worth checking: " + it.what, color = MaterialTheme.colorScheme.secondary) } }
            // What this kind of call is, and what usually decides it.
            callKindTip(o.kind).takeIf { it.isNotBlank() }?.let { Small(it) }
            // Words in this call that someone starting out may not know, explained right here.
            remember(o.rawText) { val t = o.rawText.lowercase(); glossary.filter { (term, _) -> term.lowercase().split(" / ", " (").map { it.trim(')', ' ') }.any { k -> k.length > 3 && Regex("\\b" + Regex.escape(k) + "\\b").containsMatchIn(t) } }.take(3) }
                .forEach { (term, meaning) -> Small("$term: $meaning") }
            // Who chooses, and what they have chosen before: ten minutes of looking says what to send.
            if (o.jurors.isNotBlank() || o.title.length > 4) Row {
                if (o.jurors.isNotBlank()) TextButton(onClick = { Share.open(ctx, "https://www.google.com/search?q=" + java.net.URLEncoder.encode(o.jurors.substringBefore(",").trim() + " artist curator", "UTF-8")) }, modifier = Modifier.offset(x = (-12).dp)) { Text("Look up the juror") }
                TextButton(onClick = { Share.open(ctx, "https://www.google.com/search?q=" + java.net.URLEncoder.encode(o.title.replace(Regex("\\b(19|20)\\d\\d\\b"), "").trim() + " " + o.organiser + " past exhibition", "UTF-8")) }) { Text("See past years") }
            }
            if (o.jurors.isNotBlank()) Small("Look at the kind of work the juror shows or makes. Send the pieces of yours that would sit well beside it.")
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
            else if (o.status in setOf("yes", "applied", "accepted", "declined")) Button(onClick = { scope.io { val aid = Engine.createApplication(ctx, o); withContext(Dispatchers.Main) { nav.navigate("appl/$aid") } } }) { Text("Open the application") }
            if (o.url.isNotBlank()) OutlinedButton(onClick = { Share.open(ctx, o.url) }) { Text("Open the call's page") }
            // No deadline or fee known but an address is: a short polite question, ready in the mail app.
            if ((o.deadline <= 0 || !Texts.feeStated(o)) && o.status in setOf("new", "later", "yes") && Texts.looksLikeEmail(o.email)) OutlinedButton(onClick = {
                val l = letters.first { it.title == "Ask a question about a call" }
                val place = o.venue.ifBlank { o.organiser }.ifBlank { o.title }
                Share.email(ctx, o.email, fillLetter(l.subject, "", place), fillLetter(l.body, "", place))
            }) { Text("Ask them by email") }
            if (o.url.isNotBlank()) OutlinedButton(onClick = {
                status = "Reading the page…"
                scope.io({ status = it }) {
                    val page = Fetch.pageText(o.url)
                    val found = if (G.ai.ready) try { G.ai.extractCalls("Link: ${o.url}\n" + page, o.sourceName).firstOrNull() } catch (e: Exception) { null } else null
                    // Only empty boxes are filled, so nothing you typed is overwritten. A date the helper reads as past gives way to the plain reader's.
                    val r = com.emotionalleaves.studioagent.domain.CallReader.read(page, LocalDate.now(), java.time.ZoneId.systemDefault(), o.sourceName, now())
                    val aiDate = found?.deadline?.takeIf { it > now() } ?: 0L
                    val merged = Engine.scored(o.copy(
                        deadline = if (o.deadline > 0) o.deadline else aiDate.takeIf { it > 0 } ?: r.deadline,
                        fee = if (o.fee > 0) o.fee else (found?.fee?.takeIf { it > 0 } ?: r.fee),
                        title = if (placeholderTitle(o.title)) (found?.title ?: r.title).ifBlank { o.title } else o.title,
                        organiser = o.organiser.ifBlank { found?.organiser.orEmpty() }, venue = o.venue.ifBlank { found?.venue.orEmpty() }, city = o.city.ifBlank { found?.city.orEmpty() },
                        email = o.email.ifBlank { found?.email?.takeIf { it.isNotBlank() } ?: r.email }, maxWorks = if (o.maxWorks > 0) o.maxWorks else (found?.maxWorks?.takeIf { it > 0 } ?: r.maxWorks),
                        statementLimit = if (o.statementLimit > 0) o.statementLimit else (found?.statementLimit?.takeIf { it > 0 } ?: r.statementLimit),
                        imageSpecs = o.imageSpecs.ifBlank { found?.imageSpecs.orEmpty() }, theme = o.theme.ifBlank { found?.theme.orEmpty() },
                        guaranteed = o.guaranteed || r.guaranteed || found?.guaranteed == true, rightsGrab = o.rightsGrab || r.rightsGrab || found?.rightsGrab == true,
                        rawText = o.rawText.ifBlank { page.take(6000) }
                    ), p)
                    o = merged; dao.put(merged)
                    deadlineText = if (merged.deadline > 0) Engine.date(merged.deadline).toString() else ""
                    status = "Filled in from the page. Check the deadline and fee against the page yourself."
                }
            }) { Text("Read the page for me") }
        }
        // "Can I send prints?" "Is it only for Texas?" Answered from the call's own words, quoted.
        if (G.ai.ready && o.rawText.isNotBlank()) {
            val q = rememberSaveable(id) { mutableStateOf("") }
            var answer by remember(id) { mutableStateOf("") }
            var asking by remember { mutableStateOf(false) }
            Field("Ask about this call (for example: can I send prints?)", q)
            if (q.value.isNotBlank()) TextButton(enabled = !asking, onClick = {
                asking = true; answer = "Reading the call…"
                scope.io({ answer = it; asking = false }) { answer = G.ai.askCall(o.rawText, q.value.trim()); asking = false }
            }) { Text("Ask") }
            if (answer.isNotBlank()) Panel { Text(answer) }
        }
        // When the call does not say: a short, polite question to the organizer, written for you.
        if (Texts.looksLikeEmail(o.email)) TextButton(onClick = {
            val body = "Hello,\n\nI am interested in applying to ${o.title}. Could you tell me " + (if (o.deadline <= 0) "the deadline, " else "") + (if (!Texts.feeStated(o)) "whether there is an entry fee, " else "") + "and [your question]?\n\nThank you,\n\n" + com.emotionalleaves.studioagent.work.Papers.signature(st)
            Share.email(ctx, o.email, "Question about ${o.title}", body)
        }) { Text("Email the organizer a question") }
        Expand("Details", startOpen = placeholderTitle(o.title) || o.deadline <= 0) {
        Field("Title", o.title, { save(o.copy(title = it)) })
        Choice("Kind of call", Ai.callKinds, o.kind, names = { mapOf("fair" to "Art fair", "commission" to "Commission (a work made for them)")[it] ?: it.replaceFirstChar { c -> c.uppercase() } }) { save(o.copy(kind = it)) }
        DateField("Deadline", deadlineText) { deadlineText = it; val ms = Ai.parseDate(it)
            if (ms > 0 && ms != o.deadline) { val x = o.copy(deadline = ms); save(x)
                // Already applying: the reminders move with the new date.
                scope.io { dao.applsNow().firstOrNull { it.oppId == x.id && it.status !in setOf("submitted", "accepted", "declined", "withdrawn") }?.let { Engine.scheduleDeadline(ctx, x, it.id) } }
            } }
        Num("Entry fee, " + st.currency, o.fee) { save(o.copy(fee = it)) }
        Choice("Who can enter", listOf("campus", "local", "online", "regional", "national", "international"), o.scope, names = { mapOf("campus" to "My campus", "local" to "My city", "online" to "Online", "regional" to "My region", "national" to "The whole country", "international" to "Anywhere")[it] ?: it }) { save(o.copy(scope = it)) }
        Field("Link to the call's page", o.url, { save(o.copy(url = it.trim())) })
        SavedLine()

        Field("Organizer", o.organiser, { save(o.copy(organiser = it)) })
        Field("Venue", o.venue, { save(o.copy(venue = it)) })
        Field("City", o.city, { save(o.copy(city = it)) })
        Toggle("Online only", o.online) { save(o.copy(online = it)) }
        Field("Prizes", o.prizes, { save(o.copy(prizes = it)) })
        Field("Theme", o.theme, { save(o.copy(theme = it)) }, lines = 2)
        Field("Jurors", o.jurors, { save(o.copy(jurors = it)) })
        Field("Mediums accepted", o.mediums, { save(o.copy(mediums = it)) })
        // How many works, asked once (it was asked twice before).
        Count("Fewest works", o.minWorks, 0, 20) { save(o.copy(minWorks = it)) }
        Count("Most works", o.maxWorks, 0, 20) { save(o.copy(maxWorks = it)) }
        Field("Image rules (for example: JPEG, 1920 px, under 5 MB)", o.imageSpecs, { save(o.copy(imageSpecs = it)) })
        Whole("Statement word limit", o.statementLimit) { save(o.copy(statementLimit = it)) }
        Num("Their cut of sales, %", o.commission) { save(o.copy(commission = it)) }
        Field("Send entries to (email, if entries go by email)", o.email, { save(o.copy(email = it.trim())) })
        if (o.email.isNotBlank() && !Texts.looksLikeEmail(o.email)) Small("That does not look like a full email address yet (name@place.org).")
        Toggle("Accepts prints of digital work", o.acceptsPrints) { save(o.copy(acceptsPrints = it)) }
        Toggle("Accepts screen-based work", o.acceptsNewMedia) { save(o.copy(acceptsNewMedia = it)) }
        Toggle("Paying the fee guarantees a place", o.guaranteed) { save(o.copy(guaranteed = it)) }
        Toggle("Terms take broad rights to my images", o.rightsGrab) { save(o.copy(rightsGrab = it)) }
        }
        FlowRow {
            if (o.organiser.isNotBlank()) TextButton(onClick = {
                val before = st.blocked
                scope.undoable("Blocked ${o.organiser}. Their calls will be held back.", { st.blocked = (before.split(",").map { it.trim() }.filter { it.isNotBlank() } + o.organiser).distinct().joinToString(", "); Engine.rescore() }, { st.blocked = before; Engine.rescore() })
            }) { Text("Block ${o.organiser}") }
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
                val own = o.sourceName == "Your own show"
                Text(when { own -> "Your own show"; loaded.status == "accepted" -> "Accepted"; loaded.status == "declined" -> "Not this time"; else -> "Submitted. Done." }, fontWeight = FontWeight.SemiBold)
                Text(if (own) (if (o.exhibitionDates.isNotBlank()) o.exhibitionDates + "." else "") else "Sent ${fmtDate(loaded.submittedAt)}.")
                if (loaded.proofText.isNotBlank()) Note(loaded.proofText)
                if (loaded.status == "submitted") Small("This application is locked. There is nothing left to check: the result comes to you" + (if (st.mailReady) " automatically." else " by email, and you record it below."))
                if (loaded.status == "declined") Note("The works are free again and will be offered to the next call that fits.")
            }
            if (loaded.proofPath.isNotBlank()) PathImage(loaded.proofPath, Modifier.fillMaxWidth())
            chosen.forEach { Note(Papers.caption(it)) }
            // A no is one juror's view on one day. The same pieces go straight to the next call that fits.
            if (loaded.status == "declined") {
                val next by produceState<Opp?>(null, loaded.id) { value = Engine.nextCallFor(loaded) }
                next?.let { n -> Panel(strong = true) {
                    Text("Try these pieces again", fontWeight = FontWeight.SemiBold)
                    Text(n.title + ", closes " + fmtDate(n.deadline) + (if (n.fee > 0) ", fee " + money(n.fee) else ", free to enter") + ".")
                    Small("Most work is turned down many times before it is chosen. Sending it on right away is what working artists do.")
                    Button(onClick = { scope.io { val aid = Engine.createApplication(ctx, n, Engine.workIds(loaded)); withContext(Dispatchers.Main) { nav.navigate("appl/$aid") } } }) { Text("Start the entry with the same pieces") }
                } }
            }
            // A show planned before the pieces were ready: choose them here when they are.
            if (loaded.status == "accepted" && o.sourceName == "Your own show") {
                val more = works.filter { Engine.available(it) && it.id !in Engine.workIds(loaded) }
                if (more.isNotEmpty()) {
                    Small(if (chosen.isEmpty()) "Choose the pieces for this show:" else "Add a piece to the show:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        more.forEach { w -> FilterChip(selected = false, onClick = { scope.io {
                            dao.put(loaded.copy(workIds = (Engine.workIds(loaded) + w.id).joinToString(","))); dao.put(w.copy(status = "shown", location = o.venue.ifBlank { w.location }))
                        } }, label = { Text(w.title.ifBlank { "Untitled #${w.id}" }) }) }
                    }
                }
            }
            Status(status)
            if (loaded.status == "submitted") {
                H("Result")
                Field("Paste the result email (optional: builds your checklist)", resultText, lines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { busy = true; status = "Setting up your checklist…"; scope.io({ status = it; busy = false }) { Engine.recordResult(ctx, loaded, true, resultText.value); status = ""; busy = false } }) { Text("Accepted") }
                    OutlinedButton(enabled = !busy, onClick = { scope.io { Engine.recordResult(ctx, loaded, false, resultText.value) } }) { Text("Not this time") }
                }
            }
            if (loaded.status == "accepted" && o.sourceName != "Your own show") {
                // Far away, or the call says to ship: the box, what it will cost to send, and the steps on your week.
                val far = Regex("\\bship", RegexOption.IGNORE_CASE).containsMatchIn(o.rawText) || (o.city.isNotBlank() && st.city.isNotBlank() && !o.city.substringBefore(",").trim().equals(st.city.substringBefore(",").trim(), true))
                if (far && chosen.isNotEmpty()) Expand("Shipping it") {
                    chosen.forEach { w ->
                        if (w.widthIn > 0 && w.heightIn > 0) {
                            val framed = w.framed || w.format == "print"
                            val b = com.emotionalleaves.studioagent.domain.Shipping.box(w.widthIn, w.heightIn, w.depthIn, framed)
                            val lb = com.emotionalleaves.studioagent.domain.Shipping.weightLb(w.widthIn, w.heightIn, framed, w.support.contains("canvas", true))
                            Text(w.title.ifBlank { "Untitled" }, fontWeight = FontWeight.SemiBold)
                            Small("Box about ${b.first} x ${b.second} x ${b.third} in, about ${"%.0f".format(lb)} lb packed. Carriers charge for about ${com.emotionalleaves.studioagent.domain.Shipping.billableLb(b, lb)} lb, by size. Insure it at its price" + (if (w.price > 0) " (${money(w.price)})" else "") + ".")
                        } else Small("${w.title.ifBlank { "Untitled" }}: add its size to work out the box.")
                    }
                    Small("Ground shipping takes about a week; ship ten days before it is due. A pack-and-ship store will box it for you if you would rather not.")
                    var shipSaid by remember(id) { mutableStateOf("") }
                    if (shipSaid.isBlank()) TextButton(onClick = { scope.io({ shipSaid = it }) { val n = Engine.planShipping(loaded); shipSaid = if (n == 0) "The shipping steps are already on your week." else "${Texts.count(n, "shipping step")} added to your week, counted back from delivery." } }) { Text("Put the shipping steps on my week") }
                    else Small(shipSaid)
                }
            }
            if (loaded.status == "accepted") {
                // On the night: the phone becomes the sign-up sheet.
                OutlinedButton(onClick = { nav.navigate("opening") }) { Text("At the opening: a code to scan") }
                // Before the opening: what to say, in your own words, for a glance on the way in.
                var talk by remember(id) { mutableStateOf("") }
                if (G.ai.ready && loaded.statement.isNotBlank()) {
                    TextButton(onClick = { talk = "Writing…"; scope.io({ talk = it }) { talk = G.ai.talkingPoints(loaded.statement, Engine.factsText(Engine.workIds(loaded)), Engine.voice()) } }) { Text(if (talk.isBlank()) "What to say at the opening" else "Write it again") }
                    if (talk.isNotBlank()) Panel { Text(talk); Small("Glance at it on the way in. Saying less is fine; most people ask one question and listen.") }
                }
                // Photos of the work on the wall: the record of the show, for your portfolio and your next proposal.
                val showDir = ImageTools.mediaDir(ctx)
                var shots by remember(id) { mutableStateOf(showDir.listFiles { f -> f.name.startsWith("show_${id}_") }?.sortedBy { it.name }.orEmpty()) }
                var shotFile by remember { mutableStateOf<java.io.File?>(null) }
                val takeShot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                    shotFile?.let { f -> if (ok && f.exists()) shots = shots + f else f.delete() }
                }
                H("Photos of the show")
                if (shots.isEmpty()) Small("Once it is up, photograph the work on the wall, a wide view of the room, and the label. Installation shots are what venues ask for next time.")
                shots.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { f -> PathImage(f.absolutePath, Modifier.weight(1f).height(140.dp), 600, androidx.compose.ui.layout.ContentScale.Crop) }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val f = java.io.File(showDir, "show_${id}_${now()}.jpg"); shotFile = f; try { takeShot.launch(Share.uri(ctx, f)) } catch (e: Exception) { status = "No camera app found." } }) { Text("Take a photo") }
                    if (shots.isNotEmpty()) TextButton(onClick = { Share.files(ctx, shots, "image/jpeg") }) { Text("Share them") }
                }
                // The show as one post: the room and the work in a grid, and a thank-you to everyone who came.
                if (shots.size >= 2) TextButton(onClick = { scope.io({ status = it }) {
                    val bms = shots.takeLast(9).mapNotNull { ImageTools.decode(it.absolutePath, 700) }
                    val f = java.io.File(ImageTools.exportDir(ctx), Papers.docName(st, "Show_post_$id", "jpg"))
                    ImageTools.saveJpeg(com.emotionalleaves.studioagent.media.Post.process(bms, st.fullName), f, 90)
                    val place = o.venue.ifBlank { o.organiser }
                    val words = "\"${o.title}\"" + (if (place.isNotBlank()) " at $place" else "") + (if (o.exhibitionDates.isNotBlank()) ", ${o.exhibitionDates}" else "") + ". Thank you to everyone who came" + (if (place.isNotBlank()) ", and to $place for having the work." else ".") +
                        "\n\n" + com.emotionalleaves.studioagent.media.Post.tags(chosen.firstOrNull()?.medium.orEmpty(), st.city)
                    st.lastPosted = System.currentTimeMillis()
                    withContext(Dispatchers.Main) { Share.post(ctx, f, words) }
                } }) { Text("Post the show") }
                H("Checklist")
                events.filter { it.applId == id && it.type == "task" }.sortedBy { if (it.date > 0) it.date else Long.MAX_VALUE }.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = e.done, onCheckedChange = { c -> scope.io {
                            dao.put(e.copy(done = c))
                            // Unsold work picked up: it is free for the next call.
                            if (c && e.title.startsWith("Pick up", ignoreCase = true)) { Engine.releaseShown(id); status = "Picked up. The unsold pieces can go out to the next call." }
                            if (c && e.title.startsWith("Go to the opening", ignoreCase = true)) Engine.openingHappened(id)
                        } })
                        Column(Modifier.weight(1f)) {
                            Text(e.title); if (e.date > 0) Note(fmtDate(e.date))
                            // Each step that has a ready-made part opens it: the email, the list note, the labels, the flyer.
                            if (!e.done) {
                                val place = o.venue.ifBlank { o.organiser }.ifBlank { o.title }
                                fun letter(name: String) { letters.firstOrNull { it.title == name }?.let { l -> Share.email(ctx, o.email.ifBlank { loaded.emailTo }, fillLetter(l.subject, "", place), fillLetter(l.body, "", place)) } }
                                val t = e.title.lowercase()
                                val act: Pair<String, () -> Unit>? = when {
                                    t.startsWith("confirm") -> "Write the yes" to { letter("Say yes after being accepted") }
                                    t.startsWith("thank") -> "Write the thank-you" to { letter("Thank you after a show") }
                                    t.contains("email list") -> "Write the note" to { nav.navigate("update") }
                                    t.contains("listings") -> "Write the announcement" to { nav.navigate("announce/$id") }
                                    t.contains("label") -> "Make the labels" to { scope.io({ status = it }) { val f = Papers.labels(ctx, st, chosen); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }
                                    t.contains("flyer") -> "Make the flyer" to { scope.io({ status = it }) { val f = Papers.flyer(ctx, st, o.title, listOf(o.venue, o.city).filter { it.isNotBlank() }.joinToString(", "), o.exhibitionDates, "", chosen.firstOrNull()); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }
                                    t.contains("prints and frames") || t.startsWith("order print") -> "Find a lab" to { nav.navigate("nearby/labs") }
                                    t.contains("hanging kit") -> "See the list" to { nav.navigate("basics") }
                                    t.contains("deliver") || t.contains("ask about delivery") -> "Ask about delivery" to { letter("Ask about delivery details") }
                                    else -> null
                                }
                                act?.let { (label, go) -> TextButton(onClick = go, modifier = Modifier.offset(x = (-12).dp)) { Text(label) } }
                                // A drive to deliver or collect work is a cost: logged by the mile in one tap when the map knows the town.
                                if (Regex("deliver|pick up|collect", RegexOption.IGNORE_CASE).containsMatchIn(e.title)) {
                                    val mi = remember(o.city) { Engine.milesTo()[o.city.substringBefore(",").trim().lowercase()] }
                                    if (mi != null && mi > 0) TextButton(onClick = {
                                        val l = com.emotionalleaves.studioagent.data.Ledger(kind = "travel", amount = -Math.round(mi * 2 * st.mileRate * 100) / 100.0, date = now(), note = "${mi * 2} miles: ${e.title.lowercase()}, ${o.title}", oppId = o.id)
                                        scope.io { dao.put(l) }; status = "Trip added to Records: ${mi * 2} miles there and back."
                                    }, modifier = Modifier.offset(x = (-12).dp)) { Text("Log the trip, ${mi * 2} mi") }
                                }
                            }
                        }
                        // A step with no day yet: give it one from the venue's email, so it reaches Today when due.
                        if (!e.done) TextButton(onClick = {
                            val d = if (e.date > 0) Engine.date(e.date) else LocalDate.now().plusDays(7)
                            android.app.DatePickerDialog(ctx, { _, y, m, day -> scope.io { dao.put(e.copy(date = Engine.millis(LocalDate.of(y, m + 1, day).atTime(18, 0)))) } }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        }) { Text(if (e.date > 0) "Change day" else "Set a day") }
                    }
                }
                // Your own show, with friends: a group show is how most artists get their first walls.
                val contactsAll by dao.contacts().collectAsState(emptyList())
                val ledgerAll by dao.ledger().collectAsState(emptyList())
                val guests = contactsAll.filter { it.kind == "artist" && it.notes.contains("Show:$id;") }
                val guestPieces = guests.flatMap { g -> com.emotionalleaves.studioagent.domain.GroupShow.pieces(g.submissionNotes).map { it.copy(id = -(g.id * 100 + it.title.hashCode().toLong().mod(97L)), tags = "guest:" + g.name) } }
                // The opening of your own show: a short list, so nothing is remembered at the last minute.
                if (o.sourceName == "Your own show") Expand("The opening night") {
                    listOf("Ask the venue what is allowed: drinks, food, music, how late" to "A week before",
                        "Water and something simple to drink; cups and napkins" to "Most openings spend 30 to 60 on this",
                        "Something small to eat that does not need plates" to "Optional",
                        "The sign-up sheet and pens, by the door" to "Printed from People, Your email list",
                        "Price list, red dot stickers, a way to take payment" to "Ask the venue if they take it for you",
                        "A friend to greet people while you talk" to "It frees you to talk about the work",
                        "Photos of the room full of people" to "For your records and your next proposal")
                        .forEach { (what, note) -> Text("• $what"); Small(note) }
                    if (guests.isNotEmpty()) Small("With ${guests.size + 1} of you, costs are split; record them in Records for this show.")
                }
                if (o.sourceName == "Your own show") Expand(if (guests.isEmpty()) "Show with other artists" else "Showing with " + guests.joinToString(", ") { it.name.ifBlank { it.email } }) {
                    Small("Two to four artists share the work of a show and bring twice the visitors. Add them, send the invitation, and paste their list of pieces when it comes back.")
                    guests.forEach { g -> key(g.id) {
                        Panel {
                            Text(g.name.ifBlank { g.email }, fontWeight = FontWeight.SemiBold)
                            var list by remember(g.id) { mutableStateOf(g.submissionNotes) }
                            Field("Their pieces, one per line: Title, medium, 9 x 12 in, $250", list, { list = it; scope.io { dao.put(g.copy(submissionNotes = it)) } }, lines = 4)
                            val n = com.emotionalleaves.studioagent.domain.GroupShow.pieces(list).size
                            if (n > 0) Small(Texts.count(n, "piece") + " read.")
                            Row {
                                if (g.email.isNotBlank()) TextButton(onClick = { Share.email(ctx, g.email, "Join a group show" + (if (o.title.isNotBlank()) ": ${o.title}" else ""), com.emotionalleaves.studioagent.domain.GroupShow.invite(st.firstName, o.title, o.venue, o.exhibitionDates, guests.size + 1)) }) { Text("Send the invitation") }
                                TextButton(onClick = { scope.remove("${g.name} from the show", { dao.del(g) }, { dao.put(g) }) }) { Text("Remove") }
                            }
                        }
                    } }
                    val gName = rememberSaveable { mutableStateOf("") }; val gMail = rememberSaveable { mutableStateOf("") }
                    Field("Another artist's name", gName); Field("Their email", gMail)
                    if (gName.value.isNotBlank()) TextButton(onClick = {
                        val c = com.emotionalleaves.studioagent.data.Contact(name = gName.value.trim(), email = gMail.value.trim(), kind = "artist", status = "showing", notes = "Show:$id; showing with you in ${o.title}.", createdAt = now())
                        gName.value = ""; gMail.value = ""; scope.io { dao.put(c) }
                    }) { Text("Add to the show") }
                    val costs = -ledgerAll.filter { it.oppId == o.id && it.amount < 0 }.sumOf { it.amount }
                    if (guests.isNotEmpty() && costs > 0) Note("Costs so far ${money(costs)}: ${money(com.emotionalleaves.studioagent.domain.GroupShow.share(costs, guests.size + 1))} each, split ${guests.size + 1} ways.")
                    else if (guests.isNotEmpty()) Small("Costs you add in Records for this show are split evenly here.")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.labels(ctx, st, chosen + guestPieces) { w -> w.tags.removePrefix("guest:").takeIf { w.tags.startsWith("guest:") }.orEmpty() }; withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text(if (guestPieces.isEmpty()) "Wall labels" else "Wall labels for everyone") }
                    // For a library, cafe or community space: what was agreed by email, on one page to sign.
                    if (o.sourceName == "Your own show") OutlinedButton(onClick = { scope.io({ status = it }) {
                        val f = Papers.agreement(ctx, st, o.venue, chosen, o.exhibitionDates, o.commission)
                        withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Agreement to sign") }
                    // A flyer with the first chosen piece: for a show of your own, or to pin up at work.
                    OutlinedButton(onClick = { scope.io({ status = it }) {
                        val opening = events.firstOrNull { it.applId == id && it.title.startsWith("Go to the opening", true) && it.date > 0 }?.let { fmtDate(it.date) + ", " + clock(it.date) }.orEmpty()
                        val f = Papers.flyer(ctx, st, o.title, listOf(o.venue, o.city).filter { it.isNotBlank() }.joinToString(", "), o.exhibitionDates, opening, chosen.firstOrNull())
                        withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Flyer") }
                    OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.imageList(ctx, st, chosen, true); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Price list") }
                    OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.backLabels(ctx, st, chosen); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Back labels") }
                    OutlinedButton(onClick = { Share.text(ctx, Papers.printOrder(st, chosen)) }) { Text("Print and framing order") }
                }
                if (o.commission > 0) Note("Commission ${"%.0f".format(o.commission)}%: " + chosen.filter { it.price > 0 && !it.nfs }.joinToString("; ") { "${it.title}: you receive ${money(it.price * (1 - o.commission / 100))}" })
                Note("Shipping: box about 4 in larger than the work on every side, corner protectors, glassine over the face, never tape on the work. Take condition photos before it leaves.")
            }
            if (loaded.status == "submitted") Expand("Something really has to change") {
                Note("Only for a real reason, such as the organizer asking you to resend. Reopening unlocks the pack and frees the works.")
                TextButton(onClick = { scope.io { Engine.reopen(loaded) } }) { Text("Reopen this application") }
            }
        }
        return
    }

    var a by remember(id) { mutableStateOf(loaded) }
    // Opened: the "ready for your look" step has done its job.
    LaunchedEffect(id) { if (st.autopilotAppl == id) st.autopilotAppl = 0 }
    fun save(x: com.emotionalleaves.studioagent.data.Appl) { a = x; scope.io { dao.put(x); markSaved() } }
    val checks = Engine.checklist(a, o, works, feeLeft, now())
    val allGreen = checks.all { it.second }
    val chosenIds = Engine.workIds(a)
    // An address added to the call later reaches its application by itself.
    LaunchedEffect(o.email) { if (a.submittedAt == 0L && a.emailTo.isBlank() && o.email.isNotBlank()) save(a.copy(emailTo = o.email, method = "email")) }
    val files = remember(a.packDir, status) { Engine.packFiles(a) }
    val newQuestion = rememberSaveable { mutableStateOf("") }

    Screen(o.title, nav) {
        Note((if (o.deadline > 0) "Deadline ${fmtDate(o.deadline)} (${Planner.weekendsLeft(LocalDate.now(), Engine.date(o.deadline))}). " else "") + "The app aims to send 3 days early.")
        // The entry in small pieces: five 15-minute evenings, so it is never one big job the night before.
        if (o.deadline > now() + 4 * 86_400_000L && events.none { it.applId == id && it.title.startsWith("Entry, 15 minutes: ") }) {
            var split by remember(id) { mutableStateOf("") }
            if (split.isBlank()) TextButton(onClick = { scope.io({ split = it }) { val n = Engine.planEntrySteps(a, o); split = "${Texts.count(n, "15-minute step")} on your week, one an evening." } }) { Text("Split it into 15-minute evenings") } else Small(split)
        }
        StageHead(1, "Choose the works", chosenIds.isNotEmpty())
        val ready = works.filter { Engine.available(it) || it.id in chosenIds }
        // Short of pieces: the making, planned back from the deadline, a Saturday for each.
        val short = Engine.piecesShort(a, o, works)
        if (short > 0 && o.deadline > now()) {
            var planned by remember(a.id) { mutableStateOf("") }
            Panel(strong = true) {
                Text("This call needs ${Texts.count(short, "more piece")} than you have ready", fontWeight = FontWeight.SemiBold)
                Small("Time to make " + (if (short == 1) "it" else "them") + ": " + Planner.weekendsLeft(LocalDate.now(), Engine.date(o.deadline - 3 * 86_400_000L)) + ", sending three days early.")
                if (planned.isBlank()) TextButton(onClick = { scope.io({ planned = it }) { planned = Engine.planMaking(a, o) } }) { Text("Plan the making") }
                else { Text(planned); Small("Each one is on your week, on its Saturday.") }
            }
        }
        if (ready.isEmpty()) Empty("No pieces are ready to send yet. A piece is ready once it has a picture and you tap Mark it Ready to send on it in Studio.", "Open Studio") { nav.navigate("works") }
        else if (o.maxWorks > 0 && chosenIds.isEmpty()) Small("This call takes up to ${Texts.count(o.maxWorks, "work")}. Tap to choose.")
        // Chosen by their pictures, the way a juror will see them: tap to add or take out; the number is the order they go in.
        fun toggle(w: com.emotionalleaves.studioagent.data.Artwork) {
            val next = if (w.id in chosenIds) chosenIds - w.id else chosenIds + w.id
            // The covering email lists the works, so it follows the choice, unless you have rewritten it yourself.
            val byId = works.associateBy { it.id }
            val auto = Engine.applicationEmail(o, chosenIds.mapNotNull { byId[it] }, a.statement)
            val body = if (a.emailBody.isBlank() || a.emailBody == auto) Engine.applicationEmail(o, next.mapNotNull { byId[it] }, a.statement) else a.emailBody
            save(a.copy(workIds = next.joinToString(","), packDir = "", emailBody = body))
        }
        works.filter { Engine.available(it) || it.id in chosenIds }.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { w ->
                    val pos = chosenIds.indexOf(w.id)
                    Column(Modifier.weight(1f).clickable(role = androidx.compose.ui.semantics.Role.Checkbox, onClickLabel = if (pos >= 0) "Take out" else "Choose") { toggle(w) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                            .border(if (pos >= 0) 3.dp else 1.dp, if (pos >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            if (w.imagePath.isNotBlank()) PathImage(w.imagePath, Modifier.fillMaxSize().padding(6.dp), 300, androidx.compose.ui.layout.ContentScale.Fit, w.title)
                            if (pos >= 0) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Text("${pos + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(w.title.ifBlank { "Untitled #${w.id}" }, style = MaterialTheme.typography.labelMedium, maxLines = 2, fontWeight = if (pos >= 0) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // The pictures are most of the decision: a dark, washed-out or soft one is named now, while there is time to reshoot.
        var looks by remember(a.workIds) { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(a.workIds) {
            looks = withContext(Dispatchers.IO) { works.filter { it.id in chosenIds && it.format == "physical" }.mapNotNull { w ->
                ImageTools.quality(w.imagePath).firstOrNull()?.let { "${w.title.ifBlank { "Untitled" }}: " + it.substringBefore(".").lowercase() + "." } } }
        }
        if (looks.isNotEmpty()) Panel { Text("Before a juror sees them", fontWeight = FontWeight.SemiBold); looks.forEach { Small(it) }; Small("Optional, but a better photo often changes the result more than anything else.") }
        // Jurors see the works one at a time, in order; the order is part of the application.
        if (chosenIds.size > 1) OutlinedButton(onClick = { nav.navigate("jury/${a.id}") }, modifier = Modifier.fillMaxWidth()) { Text("See them as a juror will, and set the order") }
        StageHead(2, "Statement and fit", a.statement.isNotBlank() && a.themeText.isNotBlank())
        // Over this call's word limit: cut down from your own words, nothing added.
        if (o.statementLimit > 0 && Texts.words(a.statement) > o.statementLimit) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${Texts.words(a.statement)} words; this call allows ${o.statementLimit}.", Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = {
                busy = true; status = "Shortening…"
                scope.io({ status = it; busy = false }) {
                    val short = if (G.ai.ready) try { G.ai.shorten(a.statement, o.statementLimit) } catch (e: Exception) { "" } else ""
                    val fit = (if (short.isNotBlank() && Texts.words(short) <= o.statementLimit) short else Texts.trimToWords(a.statement, o.statementLimit))
                    val x = a.copy(statement = fit, packDir = ""); a = x; dao.put(x); status = "Shortened to ${Texts.words(fit)} words. Read it once."; busy = false
                }
            }) { Text("Shorten to fit") }
        }
        Field((if (o.statementLimit > 0) "Limit ${o.statementLimit} words. " else "") + "Edit only if this call needs it", a.statement, { t ->
            val chosen = works.filter { it.id in chosenIds }.sortedBy { chosenIds.indexOf(it.id) }
            val auto = Engine.applicationEmail(o, chosen, a.statement)
            save(a.copy(statement = t, packDir = "", emailBody = if (a.emailBody == auto) Engine.applicationEmail(o, chosen, t) else a.emailBody))
        }, lines = 5)
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
            // Several blank answers: drafted in one go, each from your own facts, for you to read and fix.
            val blanks = qa.count { it.second.isBlank() }
            if (G.ai.ready && blanks > 1) Button(enabled = !busy, onClick = {
                busy = true; status = "Drafting ${Texts.count(blanks, "answer")}…"
                scope.io({ status = it; busy = false }) {
                    var cur = qa
                    cur.forEachIndexed { i, (q, ans) ->
                        if (ans.isBlank()) {
                            status = "Drafting answer ${i + 1} of ${cur.size}…"
                            val t = try { G.ai.write("an answer to this application question: $q", 120, Engine.factsText(chosenIds), Engine.voice(), "The call: ${o.title}. Theme: ${o.theme}") } catch (e: Exception) { "" }
                            cur = cur.mapIndexed { j, p -> if (j == i) p.first to t else p }
                        }
                    }
                    val x = a.copy(answers = Engine.answersJson(cur), packDir = ""); a = x; dao.put(x)
                    status = "Drafted. Read each one and change anything that is not quite true or not you."; busy = false
                }
            }) { Text("Draft all ${blanks} answers") }
            qa.forEachIndexed { i, (q, ans) ->
                key(i, q) {
                    Field(q, ans, { v -> save(a.copy(answers = Engine.answersJson(qa.mapIndexed { j, p -> if (j == i) p.first to v else p }), packDir = "")) }, lines = 3)
                    // A budget question: add lines (what, how much) and the answer is written as a list with its total.
                    if (q.contains("budget", ignoreCase = true)) Expand("Build the budget") {
                        val item = rememberSaveable(i) { mutableStateOf("") }
                        var cost by rememberSaveable(i) { mutableStateOf(0.0) }
                        Field("What it pays for (for example: printing 10 works at 16 × 20 in)", item)
                        key(ans) { Num("How much, " + st.currency, cost) { cost = it } }
                        if (item.value.isNotBlank() && cost > 0) OutlinedButton(onClick = {
                            val lines = ans.lines().filter { it.startsWith("- ") }.toMutableList()
                            lines += "- " + item.value.trim() + ": " + money(cost)
                            val total = lines.sumOf { l -> Regex("[0-9][0-9,]*(\\.[0-9]+)?").findAll(l.substringAfterLast(": ")).lastOrNull()?.value?.replace(",", "")?.toDoubleOrNull() ?: 0.0 }
                            val text = lines.joinToString("\n") + "\nTotal: " + money(total)
                            item.value = ""; cost = 0.0
                            save(a.copy(answers = Engine.answersJson(qa.mapIndexed { j, p -> if (j == i) p.first to text else p }), packDir = ""))
                        }) { Text("Add this line") }
                    }
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
        // Before the checks: how this call's jury will read the words, without changing a word.
        var notes by remember(id) { mutableStateOf<List<String>>(emptyList()) }
        if (G.ai.ready && a.statement.isNotBlank()) {
            TextButton(enabled = !busy, onClick = {
                busy = true; status = "Reading it like this call's juror…"
                scope.io({ status = it; busy = false }) {
                    val words = "Call: ${o.title}. Theme: ${o.theme.ifBlank { "none stated" }}.\n\nStatement:\n${a.statement}" +
                        (if (a.themeText.isNotBlank()) "\n\nWhy it fits:\n${a.themeText}" else "") + qa.joinToString("") { "\n\nQ: ${it.first}\nA: ${it.second}" }
                    notes = G.ai.jurorNotes("application to this call", words); status = ""; busy = false
                }
            }) { Text("Read it like this call's juror") }
            if (notes.isNotEmpty()) Panel { Text("A juror's notes", fontWeight = FontWeight.SemiBold); notes.forEach { Text("• $it") }; Small("Change only what you agree with.") }
        }

        StageHead(3, "Checks before sending", allGreen)
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
            files.forEach { Note("${it.name}  (${Texts.fileSize(it.length())})") }
            Small("${Texts.count(files.size, "file")}, ${Texts.fileSize(files.sumOf { it.length() })} in all.")
            OutlinedButton(onClick = { Share.files(ctx, files) }) { Text("Share or save the files") }
        }

        StageHead(5, "Send it", a.submittedAt > 0)
        Choice("This call takes entries by", listOf("email", "web"), a.method, names = { if (it == "email") "Email" else "A form on their site" }) { save(a.copy(method = it)) }
        if (o.fee > 0) {
            if (a.feePaid > 0) Note("Fee of ${money(a.feePaid)} logged.")
            else OutlinedButton(onClick = {
                scope.io { dao.put(Ledger(kind = "fee", amount = -o.fee, date = now(), note = o.title, oppId = o.id)) }
                save(a.copy(feePaid = o.fee))
            }) { Text("I paid the ${money(o.fee)} fee") }
            Note("The app never pays for you. You pay on the organizer's page; it keeps the record.")
        }
        if (a.method == "email") {
            // The call may have gained its address after the application was started.
            LaunchedEffect(o.email) { if (a.emailTo.isBlank() && o.email.isNotBlank()) save(a.copy(emailTo = o.email)) }
            Field("To (their email address)", a.emailTo, { save(a.copy(emailTo = it.trim())) })
            if (a.emailTo.isNotBlank() && !Texts.looksLikeEmail(a.emailTo)) Small("That does not look like a full email address yet (name@place.org).")
            else if (a.emailTo.isBlank()) Small("Their address is usually on the call's page, near \"how to apply\".")
            Field("Subject", a.emailSubject, { save(a.copy(emailSubject = it)) })
            Field("The covering email", a.emailBody, { save(a.copy(emailBody = it)) }, lines = 6)
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
            if (o.url.isBlank()) Small("Add the link to the call's page first: open the call, then Details.")
            Button(enabled = o.url.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = { nav.navigate("web/${a.id}") }) { Text("Fill in the entry form") }
            OutlinedButton(onClick = { buzz(); scope.io { Engine.markSubmitted(a, "Submitted on the organizer's site on ${fmtDate(now())}.") } }) { Text("I submitted it") }
        }
        if (!allGreen) Note("Sending unlocks when every check above passes.")
        // Changing your mind is allowed: the application goes, its reminders go, the call is marked no. Undo brings it all back.
        if (a.submittedAt == 0L && a.status != "sending") {
            Space(8)
            TextButton(onClick = {
                val keepA = a; val keepO = o
                scope.undoable("Not applying to ${o.title}", {
                    val evts = dao.eventsNow().filter { it.applId == keepA.id && !it.done }
                    evts.forEach { e -> if (e.calendarId > 0) try { com.emotionalleaves.studioagent.sys.Cal.remove(ctx, e.calendarId) } catch (x: Exception) { }; dao.del(e) }
                    dao.del(keepA); dao.put(keepO.copy(status = "no"))
                    withContext(Dispatchers.Main) { nav.popBackStack() }
                }, { dao.put(keepA); dao.put(keepO); Engine.scheduleDeadline(ctx, keepO, keepA.id) })
            }) { Text("Not applying after all") }
        }
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
 var piece=['title','medium','materials','dimension','size','width','height','depth','edition','year','price','value'];
 function words(t){return t.toLowerCase().replace(/[^a-z ]/g,' ').split(/\s+/).filter(function(x){return x.length>3;});}
 function answer(k){var best='',score=0; (d.answers||[]).forEach(function(qa){var q=words(qa[0]); var hit=q.filter(function(w){return k.indexOf(w)>=0;}).length;
  if(q.length&&hit/q.length>score&&hit>=2){score=hit/q.length; best=qa[1];}}); return score>=0.5?best:'';}
 var n=0; var els=document.querySelectorAll('input[type=text],input[type=email],input[type=tel],input[type=url],input[type=number],input:not([type]),textarea');
 els.forEach(function(el){ if(el.value){return;} var k=key(el); var v='';
  for(var i=0;i<person.length&&!v;i++){ if(k.indexOf(person[i][0])>=0){ v=person[i][1]; } }
  if(!v && el.tagName==='TEXTAREA'){ v=answer(k); }
  if(!v){ for(var j=0;j<piece.length&&!v;j++){ if(k.indexOf(piece[j])>=0){ var w=work(k);
   v={title:w.title,medium:w.medium,materials:w.medium,dimension:w.dims,size:w.dims,width:w.width,height:w.height,depth:w.depth,edition:w.edition,year:w.year,price:w.price,value:w.price}[piece[j]]; } } }
  if(!v && (k.indexOf('state')>=0||k.indexOf('province')>=0)){ v=d.region; }
  if(!v && k.indexOf('country')>=0){ v=d.country; }
  if(!v && k.indexOf('name')>=0 && k.indexOf('title')<0){ v=d.name; }
  if(v){ setv(el,v); n++; } });
 document.querySelectorAll('select').forEach(function(el){ if(el.selectedIndex>0){return;} var k=key(el); var want='';
  if(k.indexOf('state')>=0||k.indexOf('province')>=0||k.indexOf('region')>=0){want=d.region;}
  if(!want){return;} var w=want.toLowerCase();
  for(var i=0;i<el.options.length;i++){var o=el.options[i]; if(o.text.toLowerCase()===w||o.value.toLowerCase()===w){el.selectedIndex=i; el.dispatchEvent(new Event('change',{bubbles:true})); n++; break;}} });
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
    var status by remember { mutableStateOf("Sign in if the site asks; it stays signed in here. Then tap Fill in. When you have pressed the site's own submit button, tap I sent it.") }

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
                                    .put("width", w.widthIn.takeIf { it > 0 }?.let { "%.4g".format(it).trimEnd('0').trimEnd('.') }.orEmpty())
                                    .put("height", w.heightIn.takeIf { it > 0 }?.let { "%.4g".format(it).trimEnd('0').trimEnd('.') }.orEmpty())
                                    .put("depth", w.depthIn.takeIf { it > 0 }?.let { "%.4g".format(it).trimEnd('0').trimEnd('.') }.orEmpty())
                                    .put("edition", if (w.editionSize > 1) "Edition of ${w.editionSize}" else "Original")
                                    .put("year", w.year.takeIf { it > 0 }?.toString().orEmpty()).put("price", if (w.nfs) "NFS" else w.price.takeIf { it > 0 }?.let { "%.0f".format(it) }.orEmpty()))
                            }
                            val d = JSONObject().put("first", st.firstName).put("last", st.lastName).put("name", st.fullName).put("email", st.email)
                                .put("phone", st.phone).put("website", st.website).put("city", st.city).put("region", st.region).put("statement", a.statement).put("bio", bio)
                                .put("works", list).put("country", if (st.currency == "$") "United States" else "")
                                // The call's own questions and your answers: a long box whose label matches a question gets its answer.
                                .put("answers", org.json.JSONArray(Engine.answers(a).map { org.json.JSONArray().put(it.first).put(it.second) }))
                            web?.evaluateJavascript(FILL_JS + "(" + d.toString() + ")") { r ->
                                val n = r?.trim('"')?.toIntOrNull() ?: 0
                                status = if (n == 0) "No boxes on this page matched. If the form is on another page, open it and tap Fill in again, or tap Copy lines and paste by hand."
                                    else "Filled ${Texts.count(n, "box", "boxes")}. Read each one, then press the site's own submit button."
                            }
                        }
                    }) { Text("Fill in") }
                    TextButton(onClick = { copying = !copying }) { Text(if (copying) "Hide lines" else "Copy lines") }
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
                    }) { Text("I sent it") }
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
                        Button(onClick = { cb.onReceiveValue(picked.map { Share.uri(ctx, File(it)) }.toTypedArray()); picked.clear(); chooser = null }) { Text("Attach to the form") }
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
        Intro("sources", "Eight big listing sites are already here and are read once a day. You can add more: a listing page, or the sender of email alerts you get.")
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
        Field(if (kind == "email") "Sender address or domain" else "Page address", value)
        Field("Name (optional)", name)
        // Appears once there is something to add, like Park it. The name, if left out, is the site's own.
        if (value.value.isNotBlank()) Button(onClick = {
            val raw = value.value.trim()
            // "artcall.org/calls" works as typed: the web address gets its https:// added.
            val url = if (kind == "url" && !raw.startsWith("http")) "https://$raw" else raw
            val named = name.value.trim().ifBlank { (if (kind == "email") raw.substringAfter('@') else (try { java.net.URI(url).host.orEmpty() } catch (e: Exception) { raw })).removePrefix("www.") }
            val s = Source(name = named, kind = kind, url = url); name.value = ""; value.value = ""
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
    // Text first, the button under it, so large text never squeezes the sentence into a thin column.
    Column {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { nav.navigate("settings/rules") }, modifier = Modifier.offset(x = (-12).dp)) { Text("Change my rules") }
    }
}

/**
 * A flyer on a notice board or a poster at a show: photograph it and the call is read from the picture.
 * When the picture cannot be read, the call is still made, with the photo's date, ready to fill in.
 */
@Composable
fun FlyerButtons(nav: NavController, onStatus: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shot = remember { File(ctx.cacheDir, "flyer.jpg") }
    var busy by remember { mutableStateOf(false) }
    fun read(path: String) {
        busy = true; onStatus("Reading the flyer…")
        scope.io({ onStatus(it); busy = false }) {
            val (id, read) = Engine.callFromPhoto(ctx, path, "Flyer photo")
            onStatus(if (read) "" else "No words could be read from that picture. Type the name and deadline from the flyer. If it happens with every photo, the phone's text reader may still be downloading; try again later."); busy = false
            withContext(Dispatchers.Main) { nav.navigate("call/$id") }
        }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) read(shot.absolutePath) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { val f = File(ctx.cacheDir, "flyer_picked.jpg"); if (ImageTools.copyUri(ctx, uri, f)) read(f.absolutePath) }
    }
    // Wraps onto two lines with large text instead of squeezing.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { try { take.launch(com.emotionalleaves.studioagent.sys.Share.uri(ctx, shot)) } catch (e: Exception) { onStatus("The camera did not open. Try A saved photo instead.") } }) { Text("Photo of a flyer") }
        TextButton(enabled = !busy, onClick = { pick.launch("image/*") }) { Text("A saved photo") }
    }
}

/** A stand-in title the app gave a call it could not name yet; it gives way to a real one. */
fun placeholderTitle(t: String) = t.isBlank() || t in setOf("New call", "Call from link", "Shared call", "Call from a flyer photo")

/** Where an entry is, as five short bars: chosen, sent, heard, shown, home. A "not this time" stops at heard, in gray. */
@Composable
fun StageTrack(stage: Int, stopped: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val names = listOf("Chosen", "Sent", "Heard back", "Shown", "Home again")
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = "Stage: " + names[stage.coerceIn(0, 4)] }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..4).forEach { i ->
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(when {
                    stopped && i <= stage -> cs.outline
                    stopped -> cs.surfaceContainerHigh
                    i <= stage -> cs.primary
                    else -> cs.surfaceContainerHigh
                }))
            }
        }
        Text(names[stage.coerceIn(0, 4)], style = MaterialTheme.typography.labelMedium, color = if (stopped) cs.onSurfaceVariant else cs.primary, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * The works as a juror meets them: one at a time, full screen, with only the label beside them.
 * Order matters (open strong, close strong), so each can be moved earlier or later right here.
 */
@Composable
fun JuryView(nav: NavController, id: Long) {
    val scope = rememberCoroutineScope()
    val appls by dao.appls().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val a = appls.firstOrNull { it.id == id }
    if (a == null) { Screen("As a juror sees it", nav) { Loading() }; return }
    val o = opps.firstOrNull { it.id == a.oppId }
    val ids = Engine.workIds(a)
    val chosen = ids.mapNotNull { i -> works.firstOrNull { it.id == i } }
    val pager = androidx.compose.foundation.pager.rememberPagerState { chosen.size }
    fun move(from: Int, to: Int) {
        if (to !in ids.indices) return
        val next = ids.toMutableList().also { val x = it.removeAt(from); it.add(to, x) }
        val byId = works.associateBy { it.id }
        val auto = Engine.applicationEmail(o ?: return, ids.mapNotNull { byId[it] }, a.statement)
        val body = if (a.emailBody.isBlank() || a.emailBody == auto) Engine.applicationEmail(o, next.mapNotNull { byId[it] }, a.statement) else a.emailBody
        // The pack is numbered in this order, so it is made again.
        scope.io { dao.put(a.copy(workIds = next.joinToString(","), packDir = "", emailBody = body)) }
        scope.launch { pager.scrollToPage(to) }
    }
    Screen("As a juror sees it", nav) {
        Small("Jurors often look at each image for only a few seconds. Put the strongest first, and end on one that stays with them.")
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().height(420.dp)) { i ->
            val w = chosen[i]
            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)).background(Color(0xFF1C1C1C)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (w.imagePath.isNotBlank()) PathImage(w.imagePath, Modifier.fillMaxWidth().weight(1f), 1400, androidx.compose.ui.layout.ContentScale.Fit, "Image ${i + 1}: " + w.title)
                Text("${i + 1}. " + Papers.caption(w), color = Color(0xFFEDEDED), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val i = pager.currentPage.coerceIn(0, (chosen.size - 1).coerceAtLeast(0))
        Text("Image ${i + 1} of ${chosen.size}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = i > 0, onClick = { move(i, i - 1) }) { Text("Move earlier") }
            OutlinedButton(enabled = i < chosen.lastIndex, onClick = { move(i, i + 1) }) { Text("Move later") }
        }
        if (i > 0) TextButton(onClick = { move(i, 0) }) { Text("Make this one first") }
        Small("The image list, file names and covering email all follow this order.")
    }
}

/** Everything a call says about itself, in one lowercase string to search. */
fun callWords(o: Opp): String = listOf(o.title, o.organiser, o.venue, o.city, o.theme, o.mediums, o.eligibility, o.kind, o.sourceName, o.rawText.take(3000)).joinToString(" ").lowercase()

/** One line on what each kind of call is and what usually decides it. */
fun callKindTip(kind: String): String = when (kind) {
    "exhibition" -> "A show: jurors look at the pictures first, so the images and how well they belong together decide most of it."
    "residency" -> "Time and space to work somewhere. They want a clear plan for the time there and why that place."
    "grant" -> "Money for a project. They want what you will make, a simple budget, and a realistic timeline."
    "fair" -> "You rent a booth and sell. Count the booth fee against what you could sell before saying yes."
    "publication" -> "Your work in print or online. Good pictures of the work matter most."
    "commission" -> "A piece made for a place or a brief. They want your idea for it and similar work you have made."
    else -> ""
}

/** The day a call said it would announce results, when its own words say ("notification: March 1", "artists notified by"). */
fun resultsDay(o: Opp): LocalDate? = if (o.rawText.isBlank()) null else
    com.emotionalleaves.studioagent.domain.CallReader.dateAfter(o.rawText, "notif|results (will be|are) (announced|sent|emailed)|accepted artists (will be|are)|selections? (will be )?announced|jury(ing)? results", LocalDate.now().minusMonths(6))
        ?.takeIf { o.deadline <= 0 || !it.isBefore(Engine.date(o.deadline)) }
