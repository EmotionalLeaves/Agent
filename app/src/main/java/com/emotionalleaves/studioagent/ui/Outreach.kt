package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
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

/** What to expect from each kind of place, said once on its page: how they book, and the usual first step. */
fun kindTip(k: String): String = when (k) {
    "campus" -> "Campus galleries and libraries often run a staff or community show each year and book wall space a term ahead. Ask the gallery coordinator or the front desk."
    "community" -> "Libraries, cafes and community centers usually book local artists for a month at a time, two to six months ahead. A short email with three pictures is the usual first step."
    "artist_run" -> "Artist-run spaces are run by artists, often volunteers. Going to their openings first matters more than a perfect email."
    "nonprofit" -> "Nonprofit art centers often have an annual open or members' show, and calls for proposals. Their website's Opportunities page is the place to look."
    "university" -> "University galleries mostly show invited or faculty work; their open calls are usually for students. Worth knowing, rarely a first show."
    "commercial" -> "Commercial galleries sell work and usually find artists through shows and introductions, not cold emails. Go to openings; apply only if their site invites submissions."
    "curator" -> "Curators pick work for shows. A short, specific note about why your work fits something they have done works best."
    "juror" -> "Jurors choose work for a call. You rarely write to them directly; going to their talks is the usual way to meet."
    else -> ""
}

/** A kind of venue in plain words. */
fun kindWords(k: String): String = mapOf("campus" to "On your campus", "community" to "Community space", "artist_run" to "Artist-run space", "juror" to "Juror",
    "nonprofit" to "Nonprofit art center", "university" to "University gallery", "curator" to "Curator", "commercial" to "Commercial gallery", "client" to "Commission client")[k] ?: k.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun statusWords(c: Contact): String = when (c.status) {
    "research" -> if (c.email.isBlank()) "Researched, no email address yet" else "Researched, no email written yet"
    "drafted" -> "Draft waiting for your yes"
    "approved" -> "Approved, waiting to send"
    "sent" -> "Sent ${fmtDate(c.sentAt)}"
    "followed_up" -> "Followed up. Revisit ${fmtDate(c.revisitAt)}"
    "interested" -> "Interested"
    "more" -> "Asked for more"
    "not_now" -> "Not now"
    "no" -> "Said no"
    "subscriber" -> "On your email list"
    "client" -> "Commission client"
    else -> c.status.replace('_', ' ').replaceFirstChar { it.uppercase() }
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
    Screen("People and venues", nav) {
        Intro("people", "Venues and people worth knowing, and what has passed between you. The app drafts short, personal emails and remembers to follow up once. Nothing is sent without your yes.")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Small("At level $level")
            Text(when (level) {
                0 -> "No emails yet. Just go to one opening a month and look at the work."
                1 -> "Campus galleries, the art department, libraries, cafés and community art centers. No cold emails to commercial galleries."
                2 -> "Artist-run spaces, plus jurors and organizers of shows that accepted you."
                else -> "Curators, university and nonprofit galleries, then commercial galleries whose program fits your work."
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
            // Openings the daily check found at your venues: one tap when you went.
            events.filter { it.type == "invite" && !it.done && it.date > now() - DAY }.sortedBy { it.date }.take(4).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(e.title); Small(fmtDate(e.date)) }
                    TextButton(onClick = { scope.io {
                        dao.put(e.copy(done = true))
                        dao.put(Evt(type = "opening", title = e.title.removePrefix("Opening at "), date = now(), done = true))
                    } }) { Text("I went") }
                }
            }
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
                }) { Text("Log the opening") }
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
        // A Saturday out: up to four places you have not visited yet, as one route in the maps app.
        val toVisit = contacts.filter { it.kind in setOf("commercial", "nonprofit", "artist_run", "community", "campus") && it.status == "research" && !it.notes.contains("Visited on") && !it.venue.startsWith("Your ") && it.venue.isNotBlank() }.take(4)
        if (toVisit.size >= 2) OutlinedButton(onClick = {
            fun q(v: String) = android.net.Uri.encode(v + if (st.city.isNotBlank()) ", " + st.city else "")
            val url = "https://www.google.com/maps/dir/?api=1&travelmode=walking&destination=" + q(toVisit.last().venue) +
                "&waypoints=" + toVisit.dropLast(1).joinToString("%7C") { q(it.venue) }
            Share.open(ctx, url)
        }, modifier = Modifier.fillMaxWidth()) { Text("A walk past ${toVisit.size} places you have not seen yet") }
        // The quickest way to a real list: what the map knows is near you.
        Button(onClick = { nav.navigate("nearby") }, modifier = Modifier.fillMaxWidth()) { Text("Find places near me") }
        TextButton(onClick = { nav.navigate("check") }) { Text("Got an offer or a buyer that seems too good? Check it") }
        H("Add a venue or person")
        Field("Their website", url)
        Status(status)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!G.ai.ready && url.value.isNotBlank()) Button(enabled = !busy, onClick = {
                busy = true; status = "Reading their site…"
                scope.io({ status = it; busy = false }) {
                    val u = url.value.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    val id = dao.put(Starter.venue(u, try { Fetch.pageText(u) } catch (e: Exception) { "" }, now()))
                    status = ""; busy = false
                    withContext(Dispatchers.Main) { url.value = ""; nav.navigate("contact/$id") }
                }
            }) { Text("Look it up") }
            if (G.ai.ready && url.value.isNotBlank()) Button(enabled = !busy, onClick = {
                busy = true; status = "Reading their site…"
                scope.io({ status = it; busy = false }) {
                    val u = url.value.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    val page = try { Fetch.pageText(u) } catch (e: Exception) { "" }
                    val j = try { G.ai.venueResearch(page) } catch (e: Exception) { null }
                    // What the helper leaves blank, the plain reader fills: the page's own email address and name.
                    val plain = Starter.venue(u, page, now())
                    val id = if (j == null) dao.put(plain) else dao.put(Contact(
                        venue = j.optString("venue").ifBlank { plain.venue }, name = j.optString("contact_name"), email = j.optString("email").ifBlank { plain.email }, website = u,
                        kind = j.optString("kind").takeIf { it in contactKinds } ?: "community", acceptsSubmissions = j.optBoolean("accepts_submissions", true),
                        submissionNotes = j.optString("submission_notes"), notes = j.optString("notes"), createdAt = now()
                    ))
                    status = ""; busy = false
                    withContext(Dispatchers.Main) { url.value = ""; nav.navigate("contact/$id") }
                }
            }) { Text("Look it up") }
            OutlinedButton(onClick = { scope.io { val id = dao.put(Contact(website = url.value.trim(), createdAt = now())); withContext(Dispatchers.Main) { nav.navigate("contact/$id") } } }) { Text("Add by hand") }
        }

        // Places you can write to now come first; within them, the ones waiting on you.
        val people = contacts.filter { it.kind != "subscriber" }.sortedWith(compareBy<Contact>({ !Levels.outreachAllowed(level, it.kind, Engine.rules()) }, { it.status !in setOf("research", "drafted", "interested", "more") },
            // Then places that show work like yours.
            { !it.submissionNotes.contains("Work like yours.") },
            // Then nearest first, for places from the map.
            { Regex("""(\d+) (mi|km) away""").find(it.notes)?.groupValues?.get(1)?.toIntOrNull() ?: (if (it.notes.contains("under 1")) 0 else 999) }))
        if (people.isNotEmpty()) H("Venues and people")
        // A long list from the map: find by name, or show one kind at a time; long lists are drawn 25 at a time.
        val findPeople = rememberSaveable { mutableStateOf("") }
        var kindShown by rememberSaveable { mutableStateOf("all") }
        var peopleLimit by rememberSaveable { mutableStateOf(25) }
        if (people.size > 10) {
            Field("Find a venue or person", findPeople)
            Choice("", listOf("all") + contactKinds.filter { k -> people.any { it.kind == k } }, kindShown, names = { if (it == "all") "All" else kindWords(it) }) { kindShown = it }
        }
        val q = findPeople.value.trim().lowercase()
        val shownPeople = people.filter { c -> (kindShown == "all" || c.kind == kindShown) && (q.isBlank() || listOf(c.venue, c.name, c.notes, c.email).any { it.lowercase().contains(q) }) }
        if (people.isNotEmpty() && shownPeople.isEmpty()) Small("No venue matches that.")
        if (shownPeople.size > peopleLimit) Small("The ${peopleLimit} you can write to first are shown.")
        shownPeople.take(peopleLimit).forEach { c ->
            Panel(onClick = { nav.navigate("contact/${c.id}") }) {
                Text(c.venue.ifBlank { c.name.ifBlank { "New contact" } }, fontWeight = FontWeight.SemiBold)
                // Places from the map say how far they are.
                val far = Regex("""(\d+ (mi|km)|under 1 (mi|km)) away""").find(c.notes)?.value
                Small(kindWords(c.kind) + (if (far != null) ", $far" else "") + ", " + statusWords(c).replaceFirstChar { it.lowercase() })
                if (c.submissionNotes.contains("Work like yours.")) Tag("Shows work like yours", strong = true)
                if (!Levels.outreachAllowed(level, c.kind, Engine.rules())) Note("Emails to this kind of venue open at Level ${Levels.outreachNeeds(c.kind)}.")
            }
        }
        if (shownPeople.size > peopleLimit) OutlinedButton(onClick = { peopleLimit += 25 }, modifier = Modifier.fillMaxWidth()) { Text("Show ${minOf(25, shownPeople.size - peopleLimit)} more") }

        H("Ready-made emails")
        IconRow(Icons.Default.Email, "Print lab, gallery, library", "Short emails you can send as they are") { nav.navigate("letters") }

        // The people who have bought or commissioned work: the likeliest to buy again, so they get their own short list.
        val collectors = contacts.filter { it.kind == "client" || (it.kind == "subscriber" && it.notes.contains("Bought ")) }
        if (collectors.isNotEmpty()) {
            H("People who own your work")
            collectors.forEach { c ->
                val bought = Regex("Bought (.+?) on ").findAll(c.notes).map { it.groupValues[1] }.toList()
                IconRow(Icons.Default.Person, c.name.ifBlank { c.email }, if (c.kind == "client") "Commissioned a piece" else if (bought.isNotEmpty()) "Bought " + bought.joinToString(", ") else "Bought your work") { nav.navigate("contact/${c.id}") }
            }
            val mails = collectors.map { it.email }.filter { Texts.looksLikeEmail(it) }
            if (mails.isNotEmpty()) TextButton(onClick = {
                Share.email(ctx, "", "New work", fillLetter(letters.first { it.title == "New work, for someone who bought before" }.body, "", "the piece"), bcc = mails)
            }) { Text("Tell them about new work (${mails.size})") }
        }
        H("Your email list")
        val subs = contacts.filter { it.kind == "subscriber" }
        Note("${subs.size} people who asked to hear about your shows. Collected in person, never bought, never from social media.")
        Field("Name", subName)
        Field("Email", subEmail)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = Texts.looksLikeEmail(subEmail.value), onClick = {
                val c = Contact(name = subName.value.trim(), email = subEmail.value.trim(), kind = "subscriber", status = "subscriber", createdAt = now())
                subName.value = ""; subEmail.value = ""; scope.io { dao.put(c) }
            }) { Text("Add to my email list") }
            if (subs.isNotEmpty()) OutlinedButton(onClick = { Share.text(ctx, subs.joinToString(", ") { it.email }) }) { Text("Copy all addresses") }
        }
        // The paper sign-up sheet, photographed: the emails on it are read on the phone and added to the list.
        var sheetStatus by remember { mutableStateOf("") }
        fun readSheet(path: String) {
            sheetStatus = "Reading the sheet…"
            scope.io({ sheetStatus = it }) {
                val text = com.emotionalleaves.studioagent.media.Ocr.read(ctx, path)
                val known = dao.contactsNow().map { it.email.lowercase() }.toMutableSet()
                var added = 0
                text.lines().forEach { line ->
                    Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(line.replace(" @", "@").replace("@ ", "@"))?.let { m ->
                        val mail = m.value.trimEnd('.', ',').lowercase()
                        if (mail !in known) {
                            known += mail; added++
                            dao.put(Contact(name = line.substring(0, m.range.first).trim().trim(',', '-', ' '), email = mail, kind = "subscriber", status = "subscriber", notes = "From the sign-up sheet, ${fmtDate(now())}.", createdAt = now()))
                        }
                    }
                }
                sheetStatus = if (text.isBlank()) "No words could be read from that photo. Try again in good light, straight on." else if (added == 0) "No new email addresses found on the sheet." else "Added ${Texts.count(added, "person", "people")} from the sheet. Check the spelling of each below; handwriting is hard to read."
            }
        }
        val sheetFile = remember { java.io.File(ctx.cacheDir, "signup_sheet.jpg") }
        val sheetShot = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok -> if (ok) readSheet(sheetFile.absolutePath) }
        OutlinedButton(onClick = { try { sheetShot.launch(Share.uri(ctx, sheetFile)) } catch (e: Exception) { sheetStatus = "The camera did not open." } }) { Text("Add from a photo of the sign-up sheet") }
        if (sheetStatus.isNotBlank()) Small(sheetStatus)
        // The list itself, to check a spelling or remove someone who asked.
        if (subs.isNotEmpty()) Expand("See the list (${subs.size})", startOpen = sheetStatus.startsWith("Added")) {
            subs.sortedByDescending { it.createdAt }.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(c.name.ifBlank { c.email }); if (c.name.isNotBlank()) Small(c.email) }
                    TextButton(onClick = { nav.navigate("contact/${c.id}") }) { Text("Fix") }
                    RemoveButton("${c.email} from your list") { scope.remove("${c.email} from your list", { dao.del(c) }, { dao.put(c) }) }
                }
            }
        }
        if (subs.isNotEmpty()) Button(onClick = { nav.navigate("update") }, modifier = Modifier.fillMaxWidth()) { Text("Write a studio update to them") }
        // The list grows in person: a sheet on a clipboard at an opening, a table, a studio visit.
        OutlinedButton(onClick = { scope.io { val f = Papers.signupSheet(ctx, st); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("Print a sign-up sheet") }
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
        if (c.email.isNotBlank() || c.website.isNotBlank()) Small(listOf(c.name, c.email, c.website).filter { it.isNotBlank() }.joinToString("\n"))
        // The email is the point of this page; the details fold away once the basics are in.
        Expand("Details: who, where, how they take work", startOpen = c.venue.isBlank() || c.email.isBlank()) {
        Field("Venue", c.venue, { save(c.copy(venue = it)) })
        Field("Person", c.name, { save(c.copy(name = it)) })
        Field("Email", c.email, { save(c.copy(email = it.trim())) })
        if (c.email.isNotBlank() && !Texts.looksLikeEmail(c.email)) Small("That does not look like a full email address yet (name@place.org).")
        Field("Website", c.website, { save(c.copy(website = it.trim())) })
        Choice("Kind of place", contactKinds, c.kind, names = ::kindWords) { save(c.copy(kind = it)) }
        Choice("How realistic right now", listOf("now", "stretch", "long"), c.tier, names = { mapOf("now" to "Now", "stretch" to "A stretch", "long" to "Long shot")[it] ?: it }) { save(c.copy(tier = it)) }
        Toggle("They accept submissions from artists", c.acceptsSubmissions) { save(c.copy(acceptsSubmissions = it)) }
        Field("Their submission instructions", c.submissionNotes, { save(c.copy(submissionNotes = it)) }, lines = 2)
        Field("Notes (what they show, who you met, openings you went to)", c.notes, { save(c.copy(notes = it)) }, lines = 3)
        }
        // What the daily check found on their own site: the pages to read before writing to them.
        val pages = Regex("""([^;:]+?) \((https?://[^)\s]+)\)""").findAll(c.submissionNotes.lines().filter { it.startsWith("Their site has:") }.joinToString(";").removePrefix("Their site has:")).map { it.groupValues[1].trim() to it.groupValues[2] }.toList()
        if (pages.isNotEmpty()) Panel {
            Text("On their site", fontWeight = FontWeight.SemiBold)
            Small("Pages about showing work there, found by the daily check. Read these before writing.")
            pages.forEach { (t, u) -> TextButton(onClick = { Share.open(ctx, u) }, modifier = Modifier.offset(x = (-12).dp)) { Text(t) } }
        }
        kindTip(c.kind).takeIf { it.isNotBlank() }?.let { Small(it) }
        // They want to see the work in person: the visit planned, what to have out, and what to ask.
        if (c.status in setOf("more", "interested") || c.notes.contains("Studio visit")) Expand("They want to see the work in person") {
            var day by rememberSaveable(c.id) { mutableStateOf(java.time.LocalDate.now().plusDays(7).toString()) }
            var planned by remember(c.id) { mutableStateOf("") }
            DateField("The visit", day) { day = it }
            if (planned.isBlank()) TextButton(onClick = { scope.io({ planned = it }) {
                val n = Engine.planVisit(c, try { java.time.LocalDate.parse(day) } catch (e: Exception) { java.time.LocalDate.now().plusDays(7) })
                planned = if (n == 0) "Already on your week." else "${Texts.count(n, "step")} on your week, from confirming it to the thank-you."
            } }) { Text("Put the visit on my week") } else Small(planned)
            OutlinedButton(onClick = { nav.navigate("order") }) { Text("Choose what to show (portfolio order)") }
            Section("While they are there")
            com.emotionalleaves.studioagent.domain.StudioVisit.tips.forEach { Text("• $it") }
            Section("Good questions to ask them")
            com.emotionalleaves.studioagent.domain.StudioVisit.questions.forEach { Text("• $it") }
        }
        // They asked for more: everything they could want in one attachment, with a short note.
        if (c.status in setOf("more", "interested") && Texts.looksLikeEmail(c.email)) OutlinedButton(onClick = {
            scope.io {
                val kit = Engine.pressKit(ctx)
                val body = "Hello" + (if (c.name.isNotBlank()) " ${c.name.substringBefore(' ')}" else "") + ",\n\nThank you for getting back to me. Attached are my portfolio, CV, statement and bio, " +
                    "an image list with sizes and prices, and the pictures themselves. I would be glad to send anything else, or to bring work by.\n\n" + Papers.signature(st)
                withContext(Dispatchers.Main) { Share.email(ctx, c.email, "My materials, as promised", body, listOf(kit)) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Reply with all my materials attached") }
        // Time to try again: a fresh draft, and the old exchange stays in the notes.
        if (c.status in setOf("followed_up", "not_now") && c.revisitAt in 1..now()) OutlinedButton(onClick = {
            save(c.copy(status = "research", draftSubject = "", draftBody = "", followUpBody = "", revisitAt = 0, notes = (c.notes + "\nWrote again from ${fmtDate(now())}.").trim()))
        }, modifier = Modifier.fillMaxWidth()) { Text("Write to them again") }
        // They said yes to a show of your own: one screen turns it into a dated plan.
        if (c.status in setOf("sent", "followed_up", "interested", "more", "research")) OutlinedButton(onClick = { nav.navigate("plan/${c.id}") }, modifier = Modifier.fillMaxWidth()) { Text("They said yes to a show: plan it") }
        // A visit is real progress: log it in one tap, and the weekend suggestion moves on to the next place.
        if (c.status == "research") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { save(c.copy(notes = (c.notes + "\nVisited on ${fmtDate(now())}.").trim(), revisitAt = now() + 120 * DAY)) }) { Text("I went and looked") }
            TextButton(onClick = { save(c.copy(revisitAt = now() + 21 * DAY)) }) { Text("Not this weekend") }
        }
        // Their site is the usual next look, so it stays outside the folded details.
        Row {
            if (c.website.isNotBlank()) TextButton(onClick = { Share.open(ctx, c.website.let { if (it.startsWith("http")) it else "https://$it" }) }) { Text("Open their site") }
            // Going there is the first step for most places: directions in the phone's maps app.
            if (c.venue.isNotBlank() && !c.venue.startsWith("Your ")) TextButton(onClick = { Share.open(ctx, "geo:0,0?q=" + android.net.Uri.encode(c.venue + ", " + st.city)) }) { Text("Directions") }
        }
        SavedLine()
        Status(status)

        H("Email")
        when {
            !allowed -> Panel {
                Text("Emails to this kind of venue open at Level ${Levels.outreachNeeds(c.kind)}. You are at Level $level.")
                Note("For now: go to their openings, join their mailing list, and note who you meet here. That is what makes a later email welcome. (This limit can be turned off in You, Settings, Your rules.)")
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
                        busy = true; status = "Drafting…"
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
                        if (!Texts.looksLikeEmail(c.email)) Small("Add their email address under Details first.")
                        if (c.draftSubject.isBlank()) Small("Give it a subject line first.")
                        Button(enabled = Texts.looksLikeEmail(c.email) && c.draftSubject.isNotBlank() && '[' !in c.draftBody && '[' !in c.followUpBody, modifier = Modifier.fillMaxWidth(), onClick = { save(c.copy(status = "approved")) }) { Text("Approve the email and follow-up") }
                        if (G.ai.ready) TextButton(enabled = !busy, onClick = {
                            busy = true; status = "Rewriting…"
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
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            // Three pictures go with it, small enough for any inbox: venues decide from the work.
                            scope.io {
                                val dir = java.io.File(ctx.cacheDir, "outreach").apply { deleteRecursively(); mkdirs() }
                                val pics = Engine.portfolioOrdered(dao.artworksNow()).take(3).mapIndexedNotNull { i, w ->
                                    java.io.File(dir, Texts.fileName(st.lastName.ifBlank { st.firstName }, w.title, w.year, "", 0.0, 0.0, number = i + 1))
                                        .takeIf { com.emotionalleaves.studioagent.media.ImageTools.export(w.imagePath, 1400, 700_000, it, st.fullName, Papers.caption(w)) } }
                                withContext(Dispatchers.Main) { Share.email(ctx, c.email, c.draftSubject, c.draftBody, pics) }
                            }
                        }) { Text("Open in my mail app, with three pictures") }
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
            if (reply.value.isNotBlank()) OutlinedButton(onClick = { val t = reply.value; reply.value = ""; scope.io({ status = it }) { Engine.applyReply(c, t) } }) { Text("Save their reply") }
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
        DangerButton(onClick = { scope.remove(c.venue.ifBlank { c.name.ifBlank { "the contact" } }, { dao.del(c) }, { dao.put(c) }); nav.popBackStack() }) { Text("Delete this contact") }
    }
}

/** What is worth telling the list: new finished work and shows coming up. Plain facts only, nothing invented. */
fun studioUpdate(newWorks: List<com.emotionalleaves.studioagent.data.Artwork>, shows: List<com.emotionalleaves.studioagent.data.Opp>): Pair<String, String> {
    val subject = when {
        shows.isNotEmpty() -> "Coming up: " + shows.first().title
        newWorks.isNotEmpty() -> "New work from the studio"
        else -> "News from the studio"
    }
    val body = buildString {
        appendLine("Hello,"); appendLine()
        appendLine("A short note from the studio, since you asked to hear about my shows.")
        if (shows.isNotEmpty()) {
            appendLine(); appendLine(if (shows.size == 1) "Coming up" else "Coming up:")
            shows.forEach { o -> appendLine("- " + o.title + listOf(o.venue, o.city).filter { it.isNotBlank() }.joinToString(", ").let { if (it.isBlank()) "" else ", $it" } +
                (if (o.exhibitionDates.isNotBlank()) ". ${o.exhibitionDates}" else "") + ".") }
            appendLine("The opening is free and open to anyone. I would be glad to see you there.")
        }
        if (newWorks.isNotEmpty()) {
            appendLine(); appendLine("New work" + (if (newWorks.size > 1) ", attached:" else ", attached:"))
            newWorks.forEach { appendLine("- " + Papers.caption(it)) }
        }
        appendLine(); appendLine("Thank you for following along. To stop these notes, just reply and say so.")
        appendLine(); append(Papers.signature(st))
    }
    return subject to body
}

@Composable
fun UpdateScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by dao.contacts().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val opps by dao.opps().collectAsState(emptyList())
    val appls by dao.appls().collectAsState(emptyList())
    val subs = contacts.filter { it.kind == "subscriber" && Texts.looksLikeEmail(it.email) }
    val since = if (st.lastNewsletter > 0) st.lastNewsletter else now() - 90 * DAY
    val fresh = works.filter { Engine.documented(it) && it.createdAt >= since }.sortedByDescending { it.createdAt }.take(3)
    val shows = appls.filter { it.status == "accepted" }.mapNotNull { a -> opps.firstOrNull { it.id == a.oppId } }.filter { it.deadline == 0L || it.deadline > now() - 120 * DAY }
    val draft = remember(fresh.size, shows.size) { studioUpdate(fresh, shows) }
    val subject = rememberSaveable(draft.first) { mutableStateOf(draft.first) }
    val body = rememberSaveable(draft.second) { mutableStateOf(draft.second) }
    Screen("Studio update", nav) {
        Intro("update", "A short email to the people on your list: new work and where to see it. A few times a year is plenty.")
        if (st.lastNewsletter > 0) Small("Last one went out ${fmtDate(st.lastNewsletter)}.")
        if (fresh.isEmpty() && shows.isEmpty()) Panel(strong = true) { Text("There is nothing new to tell yet. It is fine to wait until there is a show or a finished piece; that is what people signed up for.") }
        Field("Subject", subject)
        Field("Message", body, lines = 12)
        if (fresh.isNotEmpty()) Small("${Texts.count(fresh.size, "picture")} attached, made small for email: " + fresh.joinToString(", ") { it.title.ifBlank { "Untitled" } } + ".")
        Button(enabled = subs.isNotEmpty() && body.value.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
            scope.io({ }) {
                val dir = java.io.File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
                val files = fresh.mapIndexedNotNull { i, w -> java.io.File(dir, Texts.fileName(st.lastName.ifBlank { st.firstName }, w.title, w.year, "", 0.0, 0.0, number = i + 1))
                    .takeIf { com.emotionalleaves.studioagent.media.ImageTools.export(w.imagePath, 1600, 900_000, it, st.fullName, Papers.caption(w)) } }
                withContext(Dispatchers.Main) {
                    // To yourself, with the list as blind copies: nobody sees anyone else's address.
                    Share.email(ctx, st.email, subject.value.trim(), body.value.trim(), files, subs.map { it.email })
                }
            }
        }) { Text("Open it in my mail app (${Texts.count(subs.size, "person", "people")}, blind copied)") }
        OutlinedButton(onClick = { st.lastNewsletter = now(); nav.popBackStack() }) { Text("I sent it") }
    }
}

/** A venue agreed to show your work: the title, the opening date, how long it hangs, and which pieces. Everything else is planned from that. */
@Composable
fun PlanShow(nav: NavController, contactId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by dao.contacts().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val c = contacts.firstOrNull { it.id == contactId }
    if (c == null) { Screen("Plan the show", nav) { Loading() }; return }
    val title = rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf(java.time.LocalDate.now().plusWeeks(7).with(java.time.DayOfWeek.FRIDAY).toString()) }
    var weeks by rememberSaveable { mutableStateOf(4) }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var status by remember { mutableStateOf("") }
    val ready = works.filter { Engine.available(it) }
    Screen("Plan the show", nav) {
        Intro("planshow", "A show of your own at ${c.venue.ifBlank { "this venue" }}. Give it a name and an opening date; every step is dated back from there and lands on Today when it is due.")
        var oneDay by rememberSaveable { mutableStateOf(false) }
        Choice("What kind", listOf("show", "day"), if (oneDay) "day" else "show", names = { if (it == "show") "A show that hangs for weeks" else "One day: open studio or pop-up" }) { oneDay = it == "day" }
        Field("Title (for example: Before the First Shift)", title)
        DateField(if (oneDay) "The day" else "Opening day", opening) { opening = it }
        if (!oneDay) Count("How long it hangs", weeks, 1, 16, 1, "weeks") { weeks = it }
        Section("Which pieces")
        if (ready.isEmpty()) Small("No pieces are marked Ready to send yet. You can plan now and choose them later on the show's page.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ready.forEach { w -> androidx.compose.material3.FilterChip(selected = w.id in picked, onClick = { if (w.id in picked) picked.remove(w.id) else picked.add(w.id) }, label = { Text(w.title.ifBlank { "Untitled #${w.id}" }) }) }
        }
        val day = try { java.time.LocalDate.parse(opening) } catch (e: Exception) { null }
        if (day != null) {
            Section("The plan")
            (if (oneDay) Engine.openStudioSteps(day) else Engine.ownShowSteps(day, weeks)).forEach { (t, d) -> Row { Text(fmtDate(Engine.millis(d.atTime(12, 0))), Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(t, Modifier.weight(1f)) } }
            if (day.isBefore(java.time.LocalDate.now().plusDays(21))) Small("That is soon. The early steps are already due; do them in order and skip what does not apply.")
        }
        Status(status)
        Button(enabled = title.value.isNotBlank() && day != null, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.io({ status = it }) {
                val id = Engine.planOwnShow(c, title.value.trim(), day!!, weeks, picked.toList(), oneDay)
                withContext(Dispatchers.Main) { nav.navigate("appl/$id") { popUpTo("outreach") } }
            }
        }) { Text("Make the plan") }
    }
}

/**
 * For your phone at an opening or a table: a big code people scan to join your list or see your page,
 * and a two-box form to add someone who would rather say their email out loud.
 */
@Composable
fun AtTheOpening(nav: NavController) {
    val scope = rememberCoroutineScope()
    val name = rememberSaveable { mutableStateOf("") }
    val mail = rememberSaveable { mutableStateOf("") }
    var added by remember { mutableStateOf(0) }
    var showPage by rememberSaveable { mutableStateOf(false) }
    val join = if (st.email.isNotBlank()) "mailto:${st.email}?subject=" + java.net.URLEncoder.encode("Please add me to your studio news", "UTF-8").replace("+", "%20") else ""
    val page = st.website.let { if (it.isBlank() || it.startsWith("http")) it else "https://$it" }
    val code = if (showPage && page.isNotBlank()) page else join.ifBlank { page }
    val bmp = remember(code) { if (code.isBlank()) null else com.emotionalleaves.studioagent.media.Qr.bitmap(code, 700)?.asImageBitmap() }
    Screen("At the opening", nav) {
        Text(if (code == page) "Scan to see my work" else "Scan to hear about my next show", style = MaterialTheme.typography.headlineSmall)
        if (bmp != null) androidx.compose.foundation.Image(bmp, "Code to scan", Modifier.fillMaxWidth().aspectRatio(1f).background(androidx.compose.ui.graphics.Color.White).padding(16.dp))
        else Small("Add your email in You, Settings, Your details, and the code appears here.")
        if (join.isNotBlank() && page.isNotBlank()) TextButton(onClick = { showPage = !showPage }) { Text(if (showPage) "Show the sign-up code" else "Show the code for my page") }
        Section("Or add them here")
        Field("Name", name)
        Field("Email", mail)
        Button(enabled = Texts.looksLikeEmail(mail.value), onClick = {
            val c = Contact(name = name.value.trim(), email = mail.value.trim(), kind = "subscriber", status = "subscriber", notes = "Signed up at an opening, ${fmtDate(now())}.", createdAt = now())
            name.value = ""; mail.value = ""; added++
            scope.io { if (dao.contactsNow().none { it.email.equals(c.email, true) }) dao.put(c) }
        }) { Text("Add to my list") }
        if (added > 0) Small("${Texts.count(added, "person", "people")} added tonight.")
        SellingTonight()
        Expand("Bring with you") {
            listOf("The sign-up sheet and two pens", "The price list (and a few copies)", "Cards or small flyers with your name and page", "Red dot stickers for sold work",
                "Your phone, charged, for the code and for photos", "Water, and something to eat before").forEach { Text("• $it") }
        }
    }
}

/** Someone wants to buy a piece tonight: the price, their name, a receipt, a red dot, and the step to get it to them later. */
@Composable
fun SellingTonight() {
    val ctx = androidx.compose.ui.platform.LocalContext.current; val scope = rememberCoroutineScope()
    val works by dao.artworks().collectAsState(emptyList())
    val appls by dao.appls().collectAsState(emptyList())
    val inShows = appls.filter { it.status == "accepted" }.flatMap { Engine.workIds(it) }.toSet()
    val atShow = works.filter { it.id in inShows && it.status in setOf("shown", "pending", "documented", "available") }
    val pieces = atShow.ifEmpty { works.filter { it.status in setOf("documented", "available", "shown") && !it.nfs } }
    var selling by rememberSaveable { mutableStateOf(0L) }
    var done by remember { mutableStateOf<Triple<Long, Double, String>?>(null) }
    if (pieces.isEmpty()) return
    Section(if (atShow.isNotEmpty()) "If someone wants to buy" else "If someone wants to buy a piece")
    done?.let { (id, keep, who) ->
        val w = works.firstOrNull { it.id == id }
        Panel(strong = true) {
            Text("Sold: " + (w?.title ?: "the piece"), fontWeight = FontWeight.SemiBold)
            Text("Put a red dot on its label. You receive ${money(keep)}. It stays on the wall until the show ends; getting it to $who after is on your week.")
            if (w != null) androidx.compose.material3.OutlinedButton(onClick = { scope.io { val n = dao.ledgerNow().count { it.kind == "sale" && Engine.date(it.date).year == java.time.LocalDate.now().year }.coerceAtLeast(1)
                val f = Papers.billOfSale(ctx, st, w, who, dao.ledgerNow().firstOrNull { it.artworkId == w.id && it.kind == "sale" }?.amount ?: w.price, n)
                withContext(Dispatchers.Main) { Share.files(ctx, listOf(f), "application/pdf") } } }) { Text("Send them the receipt") }
            TextButton(onClick = { done = null }) { Text("Done") }
        }
    }
    pieces.filter { it.status != "sold" }.forEach { w -> androidx.compose.runtime.key(w.id) {
        if (selling != w.id) IconRow(androidx.compose.material.icons.Icons.Default.Edit, w.title.ifBlank { "Untitled" }, if (w.price > 0) money(w.price) else "No price set") { selling = w.id }
        else Panel {
            val buyer = rememberSaveable(w.id) { mutableStateOf("") }; val mail = rememberSaveable(w.id) { mutableStateOf("") }
            var price by rememberSaveable(w.id) { mutableStateOf(w.price) }
            var join by rememberSaveable(w.id) { mutableStateOf(true) }
            Text(w.title.ifBlank { "Untitled" }, fontWeight = FontWeight.SemiBold)
            Num("Price, " + st.currency, price) { price = it }
            Small("Your price is your price. If they ask for less, \"that is the price, and I think it is a fair one\" is a complete answer.")
            Field("Their name", buyer); Field("Their email (for the receipt)", mail)
            if (Texts.looksLikeEmail(mail.value)) Toggle("They would like to hear about my next show", join) { join = it }
            Row {
                Button(enabled = price > 0, onClick = {
                    val who = buyer.value.trim(); val m = mail.value.trim(); val p = price
                    scope.io { val keep = Engine.sellAtShow(dao.artworksNow().first { it.id == w.id }, p, who, m, join); withContext(Dispatchers.Main) { done = Triple(w.id, keep, who.ifBlank { "the buyer" }); selling = 0L } }
                }) { Text("Sold") }
                TextButton(onClick = { selling = 0L }) { Text("Not now") }
            }
        }
    } }
    Small("Ask how they would like to pay before the night: many venues take the payment and pay you after; otherwise a card reader app or a payment link on your phone works.")
}
