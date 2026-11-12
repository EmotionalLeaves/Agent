package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
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
import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.net.Fetch
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.DAY
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.Papers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val contactKinds = listOf("campus", "community", "artist_run", "juror", "nonprofit", "university", "curator", "commercial")

fun statusWords(c: Contact): String = when (c.status) {
    "research" -> "Researched, no email yet"
    "drafted" -> "Draft waiting for your yes"
    "approved" -> "Approved, waiting to send"
    "sent" -> "Sent ${fmtDate(c.sentAt)}"
    "followed_up" -> "Followed up. Revisit ${fmtDate(c.revisitAt)}"
    "interested" -> "Interested"
    "more" -> "Asked for more"
    "not_now" -> "Not now"
    "no" -> "No"
    else -> c.status
}

@Composable
fun Outreach(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by dao.contacts().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val level = Engine.levelOf(works, docs, facts)
    val url = rememberSaveable { mutableStateOf("") }
    val events by dao.events().collectAsState(emptyList())
    val openingPlace = rememberSaveable { mutableStateOf("") }
    val openingNote = rememberSaveable { mutableStateOf("") }
    val subName = rememberSaveable { mutableStateOf("") }
    val subEmail = rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Screen("People", nav, back = false) {
        Intro("people", "Venues and people worth knowing, and what has passed between you. The app drafts short, personal emails and remembers to follow up once. Nothing is sent without your yes.")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Small("At level $level")
            Text(when (level) {
                0 -> "No emails yet. Just go to one opening a month and look at the work."
                1 -> "Campus galleries, the art department, libraries, cafés and community art centres. No cold emails to commercial galleries."
                2 -> "Artist-run spaces, plus jurors and organisers of shows that accepted you."
                else -> "Curators, university and nonprofit galleries, then commercial galleries whose programme fits your work."
            }, style = MaterialTheme.typography.titleMedium)
        }
        val monthStart = Engine.millis(java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay())
        val went = events.filter { it.type == "opening" && it.date >= monthStart }
        Panel {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${went.size}", style = MaterialTheme.typography.displayMedium, color = if (went.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text("of 1 opening this month", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
            }
            Small("An opening is the free first evening of a show. Going and just looking is how people slowly come to know your face.")
            Explain("How do I find one, and what do I do there?", "Look at your university art gallery's page or events calendar, your library's site, or any local gallery's mailing list. Openings are free and usually on a weekday evening. Walk round, look at the work, and leave when you like; ten minutes counts. Nobody expects you to talk to anyone. If you do talk to someone, note their name here afterwards.")
            Expand("I went to one") {
                Field("Where", openingPlace)
                Field("Anyone you met, or anything you noticed (optional)", openingNote)
                if (openingPlace.value.isNotBlank()) Button(onClick = {
                    val place = openingPlace.value.trim(); val note = openingNote.value.trim(); openingPlace.value = ""; openingNote.value = ""
                    scope.io {
                        dao.put(Evt(type = "opening", title = place + (if (note.isNotBlank()) ": $note" else ""), date = now(), done = true))
                        val known = dao.contactsNow().firstOrNull { it.venue.equals(place, ignoreCase = true) }
                        val line = "Went to an opening on ${fmtDate(now())}." + (if (note.isNotBlank()) " $note" else "")
                        if (known != null) dao.put(known.copy(notes = (known.notes + "\n" + line).trim())) else dao.put(Contact(venue = place, kind = "community", notes = line, createdAt = now()))
                    }
                }) { Text("Log it") }
            }
            events.filter { it.type == "opening" }.sortedByDescending { it.date }.take(3).forEach { Check(true, fmtDate(it.date) + ": " + it.title, small = true) }
        }
        if (contacts.none { it.kind != "subscriber" }) {
            Panel {
                Text("Do not know where to start?", fontWeight = FontWeight.SemiBold)
                Small("The app can add four places almost every campus and town has. Each comes with what to find out. Rename them to the real ones as you go.")
                Button(onClick = { scope.io { Engine.starterContacts(now()).forEach { dao.put(it) } } }) { Text("Give me a starter list") }
            }
        }
        H("Add a venue or person")
        Field("Their website", url)
        Status(status)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!G.ai.ready && url.value.isNotBlank()) Button(enabled = !busy, onClick = {
                busy = true; status = "Reading their site..."
                scope.io({ status = it; busy = false }) {
                    val u = url.value.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    val id = dao.put(Starter.venue(u, try { Fetch.pageText(u) } catch (e: Exception) { "" }, now()))
                    status = ""; busy = false
                    withContext(Dispatchers.Main) { url.value = ""; nav.navigate("contact/$id") }
                }
            }) { Text("Look it up") }
            if (G.ai.ready && url.value.isNotBlank()) Button(enabled = !busy, onClick = {
                busy = true; status = "Reading their site..."
                scope.io({ status = it; busy = false }) {
                    val u = url.value.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    val page = try { Fetch.pageText(u) } catch (e: Exception) { "" }
                    val j = try { G.ai.venueResearch(page) } catch (e: Exception) { null }
                    val id = if (j == null) dao.put(Starter.venue(u, page, now())) else dao.put(Contact(
                        venue = j.optString("venue"), name = j.optString("contact_name"), email = j.optString("email"), website = u,
                        kind = j.optString("kind").takeIf { it in contactKinds } ?: "community", acceptsSubmissions = j.optBoolean("accepts_submissions", true),
                        submissionNotes = j.optString("submission_notes"), notes = j.optString("notes"), createdAt = now()
                    ))
                    status = ""; busy = false
                    withContext(Dispatchers.Main) { url.value = ""; nav.navigate("contact/$id") }
                }
            }) { Text("Look it up") }
            OutlinedButton(onClick = { scope.io { val id = dao.put(Contact(website = url.value.trim(), createdAt = now())); withContext(Dispatchers.Main) { nav.navigate("contact/$id") } } }) { Text("Add by hand") }
        }

        val people = contacts.filter { it.kind != "subscriber" }
        if (people.isNotEmpty()) H("Venues and people")
        people.forEach { c ->
            Panel(onClick = { nav.navigate("contact/${c.id}") }) {
                Text(c.venue.ifBlank { c.name.ifBlank { "New contact" } }, fontWeight = FontWeight.SemiBold)
                Small(c.kind.replace('_', ' ').replaceFirstChar { it.uppercase() } + ", " + statusWords(c).replaceFirstChar { it.lowercase() })
                if (!Levels.outreachAllowed(level, c.kind, Engine.rules())) Note("Emails to this kind of venue open at Level ${Levels.outreachNeeds(c.kind)}.")
            }
        }

        H("Ready-made emails")
        IconRow(Icons.Default.Email, "Print lab, gallery, library", "Short emails you can send as they are") { nav.navigate("letters") }

        H("Your email list")
        val subs = contacts.filter { it.kind == "subscriber" }
        Note("${subs.size} people who asked to hear about your shows. Collected in person, never bought, never from social media.")
        Field("Name", subName)
        Field("Email", subEmail)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = subEmail.value.contains("@"), onClick = {
                val c = Contact(name = subName.value.trim(), email = subEmail.value.trim(), kind = "subscriber", status = "subscriber", createdAt = now())
                subName.value = ""; subEmail.value = ""; scope.io { dao.put(c) }
            }) { Text("Add to list") }
            if (subs.isNotEmpty()) OutlinedButton(onClick = { Share.text(ctx, subs.joinToString(", ") { it.email }) }) { Text("Copy all addresses") }
        }
    }
}

@Composable
fun ContactScreen(nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by dao.contacts().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val series by dao.series().collectAsState(emptyList())
    val loaded = contacts.firstOrNull { it.id == id }
    if (loaded == null) { Screen("Contact", nav) { Loading() }; return }
    var c by remember(id) { mutableStateOf(loaded) }
    // Background sends and replies change the row; pick those up without fighting your typing.
    LaunchedEffect(loaded.status, loaded.lastReply) {
        c = c.copy(status = loaded.status, sentAt = loaded.sentAt, followUpAt = loaded.followUpAt, revisitAt = loaded.revisitAt, lastReply = loaded.lastReply)
    }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val reply = rememberSaveable { mutableStateOf("") }
    fun save(x: Contact) { c = x; scope.io { dao.put(x); markSaved() } }
    val level = Engine.levelOf(works, docs, facts)
    val allowed = Levels.outreachAllowed(level, c.kind, Engine.rules())
    val limit = st.revisionLimit

    Screen(c.venue.ifBlank { c.name.ifBlank { "Contact" } }, nav) {
        Row { Tag(statusWords(c), strong = c.status in setOf("research", "drafted")) }
        Field("Venue", c.venue, { save(c.copy(venue = it)) })
        Field("Person", c.name, { save(c.copy(name = it)) })
        Field("Email", c.email, { save(c.copy(email = it.trim())) })
        Field("Website", c.website, { save(c.copy(website = it.trim())) })
        Choice("Kind", contactKinds, c.kind) { save(c.copy(kind = it)) }
        Choice("How realistic right now", listOf("now", "stretch", "long"), c.tier) { save(c.copy(tier = it)) }
        Toggle("They accept submissions from artists", c.acceptsSubmissions) { save(c.copy(acceptsSubmissions = it)) }
        Field("Their submission instructions", c.submissionNotes, { save(c.copy(submissionNotes = it)) }, lines = 2)
        Field("Notes (what they show, who you met, openings you went to)", c.notes, { save(c.copy(notes = it)) }, lines = 3)
        if (c.website.isNotBlank()) TextButton(onClick = { Share.open(ctx, c.website) }) { Text("Open their site") }
        SavedLine()
        Status(status)

        H("Email")
        when {
            !allowed -> Panel {
                Text("Emails to this kind of venue open at Level ${Levels.outreachNeeds(c.kind)}. You are at Level $level.")
                Note("For now: go to their openings, join their mailing list, and note who you meet here. That is what makes a later email welcome. (This limit can be turned off in You, Your rules.)")
            }
            !c.acceptsSubmissions -> Panel {
                Text("They do not take unsolicited submissions, so no email.")
                Note("Build the relationship instead: openings, their mailing list, a hello in person.")
            }
            else -> {
                if (c.draftBody.isBlank() && c.status == "research") {
                    if (!G.ai.ready) Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        val (subject, body, follow) = Starter.email(c)
                        save(c.copy(draftSubject = subject, draftBody = body, followUpBody = follow, status = "drafted"))
                    }) { Text("Start the email for me") }
                    if (G.ai.ready) Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true; status = "Drafting..."
                        scope.io({ status = it; busy = false }) {
                            val (subject, body, follow) = try { G.ai.outreach(c.venue, c.notes + "\nSubmission instructions: " + c.submissionNotes, Engine.factsText(), Engine.voice(), st.website) } catch (e: Exception) { Starter.email(c) }
                            val x = c.copy(draftSubject = subject, draftBody = body, followUpBody = follow, status = "drafted"); c = x; dao.put(x); status = ""; busy = false
                        }
                    }) { Text("Draft the email and its follow-up") }
                    if (!G.ai.ready) Small("A short starter from your details, with [brackets] for the one line only you can write.")
                }
                val editable = c.status in setOf("research", "drafted")
                if (editable) {
                    Field("Subject", c.draftSubject, { save(c.copy(draftSubject = it, status = "drafted")) })
                    Field("First email", c.draftBody, { save(c.copy(draftBody = it, status = "drafted")) }, lines = 7)
                    Note("${Texts.words(c.draftBody)} words (aim under 150)")
                    Field("Follow-up, sent once after 3 to 4 weeks of silence", c.followUpBody, { save(c.copy(followUpBody = it, status = "drafted")) }, lines = 3)
                    if (c.draftBody.isNotBlank()) {
                        if (c.revisions >= limit) Note("That is ${c.revisions} rewrites. It is short and specific: it is ready.")
                        if (('[' in c.draftBody) || ('[' in c.followUpBody)) Small("Fill in the [square brackets] first.")
                        Button(enabled = c.email.contains("@") && c.draftSubject.isNotBlank() && '[' !in c.draftBody && '[' !in c.followUpBody, modifier = Modifier.fillMaxWidth(), onClick = { save(c.copy(status = "approved")) }) { Text("Approve both") }
                        if (G.ai.ready) TextButton(enabled = !busy, onClick = {
                            busy = true; status = "Rewriting..."
                            scope.io({ status = it; busy = false }) {
                                val (subject, body, follow) = G.ai.outreach(c.venue, c.notes + "\nSubmission instructions: " + c.submissionNotes, Engine.factsText(), Engine.voice(), st.website)
                                val x = c.copy(draftSubject = subject, draftBody = body, followUpBody = follow, status = "drafted", revisions = c.revisions + 1); c = x; dao.put(x); status = ""; busy = false
                            }
                        }) { Text(if (c.revisions >= limit) "One more rewrite anyway" else "Rewrite") }
                    }
                } else {
                    if (c.draftBody.isNotBlank()) Panel { Text(c.draftSubject, fontWeight = FontWeight.SemiBold); Text(c.draftBody) }
                }
                if (c.status == "approved") {
                    if (st.mailReady) Panel {
                        Text("Approved. It goes out with the next daily run, at most ${st.outreachWeeklyCap} emails a week. The follow-up is sent for you.")
                        TextButton(onClick = { save(c.copy(status = "drafted")) }) { Text("Undo") }
                    } else {
                        Button(modifier = Modifier.fillMaxWidth(), onClick = { Share.email(ctx, c.email, c.draftSubject, c.draftBody) }) { Text("Open in my mail app, filled in") }
                        OutlinedButton(onClick = { save(c.copy(status = "sent", sentAt = now(), followUpAt = now() + 24 * DAY)) }) { Text("I sent it") }
                    }
                }
                if (c.status == "sent" && !st.mailReady && c.followUpAt in 1..now() && c.followUpBody.isNotBlank()) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { Share.email(ctx, c.email, "Re: " + c.draftSubject, c.followUpBody) }) { Text("Open the follow-up in my mail app") }
                    OutlinedButton(onClick = { save(c.copy(status = "followed_up", revisitAt = now() + 180 * DAY)) }) { Text("I sent the follow-up") }
                }
                if (c.status == "followed_up") Note("Nothing more is sent. This venue comes back for a fresh look on ${fmtDate(c.revisitAt)}.")
            }
        }

        if (c.status in setOf("sent", "followed_up", "interested", "more", "not_now", "no")) {
            H("Their reply")
            if (c.lastReply.isNotBlank()) Panel { Text(c.lastReply.take(600)) }
            Field("Paste their reply" + (if (st.mailReady) " (replies to your email are picked up automatically)" else ""), reply, lines = 3)
            if (reply.value.isNotBlank()) OutlinedButton(onClick = { val t = reply.value; reply.value = ""; scope.io({ status = it }) { Engine.applyReply(c, t) } }) { Text("Log reply") }
        }
        if (c.status == "interested" || c.status == "more") {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.io({ status = it }) {
                    val ready = works.filter { Engine.documented(it) }
                    val best = ready.groupBy { it.seriesId }.maxByOrNull { it.value.size }
                    val bio = docs.filter { it.type == "bio" && it.text.isNotBlank() }.maxByOrNull { if (it.approved) 1 else 0 }?.text.orEmpty()
                    val f = Papers.proposal(ctx, st, c.venue, series.firstOrNull { it.id == best?.key }?.name.orEmpty(), Engine.pickStatement(300), bio, best?.value ?: ready)
                    withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
                }
            }) { Text("Build an exhibition proposal PDF") }
        }
        DangerButton(onClick = { scope.remove(c.venue.ifBlank { c.name.ifBlank { "the contact" } }, { dao.del(c) }, { dao.put(c) }); nav.popBackStack() }) { Text("Delete") }
    }
}
