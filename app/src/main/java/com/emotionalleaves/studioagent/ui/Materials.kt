package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.domain.Drafts
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.DAY
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.Papers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

val interviewQuestions = listOf(
    "what" to "What do you make? Describe it to someone who cannot see it.",
    "why" to "Why this work? What keeps pulling you back to it?",
    "how" to "How do you make it? Tools, steps, materials.",
    "looking" to "What are you looking at, reading or listening to that feeds it?",
    "background" to "Where did you grow up and train? Anything that shaped your eye, including jobs outside art.",
    "viewer" to "What should someone notice or feel standing in front of it?",
    "words" to "Two or three sentences about your work, the way you would say them to a friend."
)

/** A sentence to finish, for when a question feels too big to answer cold. */
val interviewStarters = mapOf(
    "what" to "Try: \"They are pictures of ___, made with ___, about this big.\" Describing one piece is enough.",
    "why" to "Try: \"I keep coming back to ___ because ___.\" \"I do not know yet, but I notice I always draw ___\" is a real answer.",
    "how" to "Try: \"I start by ___, then ___, and I know it is done when ___.\"",
    "looking" to "Anything counts: a game, a show, a street, a person at work. \"Nothing in particular\" is fine too.",
    "background" to "Try: \"I grew up in ___. I learned to make things by ___. My day job is ___ and it shows up in the work as ___.\"",
    "viewer" to "Try: \"I want someone to stop and notice ___.\" Or just say what you feel when it works.",
    "words" to "Say it out loud the way you would to a co-worker, then tap Speak instead and say it again."
)

/** type, variant, label, word target */
private val docSlots = listOf(
    listOf("statement", "50", "Statement, 50 words"), listOf("statement", "150", "Statement, 150 words"),
    listOf("statement", "300", "Statement, 300 words"), listOf("statement", "500", "Statement, 500 words"),
    listOf("bio", "50", "Bio, third person, 50 words"), listOf("bio", "150", "Bio, third person, 150 words"),
    listOf("bio", "first", "Bio, first person, short")
)

fun docTarget(d: Doc): Int = if (d.target > 0) d.target else d.variant.toIntOrNull() ?: if (d.variant == "first") 40 else 100

@Composable
fun Materials(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    val series by dao.series().collectAsState(emptyList())
    var status by remember { mutableStateOf("") }
    val answered = docs.count { it.type == "interview" && it.text.isNotBlank() }
    val newTitle = rememberSaveable { mutableStateOf("") }
    val newBrief = rememberSaveable { mutableStateOf("") }
    var newTarget by remember { mutableStateOf(150) }

    fun open(type: String, variant: String, seriesId: Long = 0) {
        scope.io {
            val existing = dao.docsNow().firstOrNull { it.type == type && it.variant == variant && it.seriesId == seriesId }
            val id = existing?.id ?: dao.put(Doc(type = type, variant = variant, seriesId = seriesId, updatedAt = now()))
            withContext(Dispatchers.Main) { nav.navigate("doc/$id") }
        }
    }

    Screen("Statement, bio and CV", nav) {
        Intro("materials", "The written things every call asks for. You answer the interview once; the app drafts each document; you approve it once and it is reused everywhere.")
        Panel(onClick = { nav.navigate("interview") }, strong = answered == 0) {
            Text("Interview", fontWeight = FontWeight.SemiBold)
            Small("$answered of ${interviewQuestions.size} answered. Everything below is written from these.")
        }
        Panel(onClick = { nav.navigate("facts") }) {
            Text("CV facts", fontWeight = FontWeight.SemiBold)
            Small("${facts.size} line" + (if (facts.size == 1) "" else "s") + ". Your CV holds only these.")
        }

        H("Statements and bios")
        // The two most calls ask for come first; the other lengths wait in a fold until a call needs one.
        val main = setOf("statement/150", "bio/50")
        docSlots.filter { "${it[0]}/${it[1]}" in main }.forEach { (type, variant, label) ->
            val d = docs.firstOrNull { it.type == type && it.variant == variant && it.seriesId == 0L }
            DocRow(label, d?.text.orEmpty(), d?.approved == true) { open(type, variant) }
        }
        Expand("Other lengths") {
            Column {
                docSlots.filter { "${it[0]}/${it[1]}" !in main }.forEach { (type, variant, label) ->
                    val d = docs.firstOrNull { it.type == type && it.variant == variant && it.seriesId == 0L }
                    DocRow(label, d?.text.orEmpty(), d?.approved == true) { open(type, variant) }
                }
            }
        }
        if (series.isNotEmpty()) H("Series statements")
        series.forEach { s ->
            val d = docs.firstOrNull { it.type == "statement" && it.variant == "100" && it.seriesId == s.id }
            DocRow("${s.name}, 100 words", d?.text.orEmpty(), d?.approved == true) { open("statement", "100", s.id) }
        }

        H("Your own documents")
        Small("A cover letter, a project description, an answer you get asked often. Any name, any length.")
        docs.filter { it.type == "custom" }.forEach { d ->
            DocRow(d.title.ifBlank { "Untitled document" }, d.text, d.approved) { nav.navigate("doc/${d.id}") }
        }
        Expand("New document") {
            Field("Name (for example: Cover letter)", newTitle)
            Field("What is it for? Who reads it? (the app writes from this)", newBrief, lines = 2)
            Whole("About how many words", newTarget) { newTarget = it }
            Button(enabled = newTitle.value.isNotBlank(), onClick = {
                val d = Doc(type = "custom", variant = "c${now()}", title = newTitle.value.trim(), brief = newBrief.value.trim(), target = newTarget.coerceAtLeast(20), updatedAt = now())
                newTitle.value = ""; newBrief.value = ""
                scope.io { val id = dao.put(d); withContext(Dispatchers.Main) { nav.navigate("doc/$id") } }
            }) { Text("Create") }
        }

        H("CV and portfolio")
        Status(status)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.io({ status = it }) { val f = Papers.cv(ctx, st, facts); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } } }) { Text("CV PDF") }
            listOf(10, 15, 20).forEach { n ->
                OutlinedButton(onClick = {
                    scope.io({ status = it }) {
                        val statement = Engine.pickStatement(150)
                        val f = Papers.portfolio(ctx, st, works.filter { Engine.documented(it) }, statement, n)
                        withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
                    }
                }) { Text("Portfolio $n") }
            }
        }
        Panel {
            Text("Portfolio web page", fontWeight = FontWeight.SemiBold)
            Note("One plain page with your documented works, approved statement, CV and email. Published to your own GitHub Pages repo. No feed, no follower counts.")
            Button(onClick = {
                status = "Publishing... this uploads each image, so it can take a minute."
                scope.io({ status = it }) { val url = Engine.publishPortfolio(ctx); status = "Published: $url (GitHub can take a few minutes to show changes)" }
            }) { Text("Publish or update") }
            if (st.website.isNotBlank()) TextButton(onClick = { Share.open(ctx, st.website) }) { Text("Open ${st.website}") }
        }
    }
}

@Composable
fun Interview(nav: NavController) {
    val scope = rememberCoroutineScope()
    val docs by dao.docs().collectAsState(emptyList())
    val answers = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(docs) {
        docs.filter { it.type == "interview" }.forEach { if (answers[it.variant] == null) answers[it.variant] = it.text }
    }
    var status by remember { mutableStateOf("") }
    val ownQuestion = rememberSaveable { mutableStateOf("") }
    Screen("Interview", nav) {
        Intro("interview", "About 10 minutes, once. Talk or type. Rough is good: your own words are what make the writing sound like you. You can stop and come back.")
        Small("${answers.values.count { it.isNotBlank() }} of ${interviewQuestions.size} answered. Rough is good; three is enough to start.")
        interviewQuestions.forEach { (k, q) ->
            key(k) {
                Field(q, answers[k].orEmpty(), { answers[k] = it }, lines = 3, mic = true)
                interviewStarters[k]?.let { Explain("Not sure what to say?", it) }
            }
        }
        answers.keys.filter { it.startsWith("own:") }.sorted().forEach { k ->
            key(k) { Field(k.removePrefix("own:"), answers[k].orEmpty(), { answers[k] = it }, lines = 3, mic = true) }
        }
        Expand("Add your own question") {
            Note("Something about your work these questions miss. Your answer is used in the writing like the others.")
            Field("Your question or topic", ownQuestion)
            OutlinedButton(enabled = ownQuestion.value.isNotBlank(), onClick = { answers["own:" + ownQuestion.value.trim().take(80)] = ""; ownQuestion.value = "" }) { Text("Add") }
        }
        Status(status)
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            scope.io({ status = it }) {
                val existing = dao.docsNow()
                (interviewQuestions.map { it.first } + answers.keys.filter { it.startsWith("own:") }).forEach { k ->
                    val old = existing.firstOrNull { it.type == "interview" && it.variant == k }
                    val text = answers[k].orEmpty().trim()
                    if (old != null || text.isNotBlank()) dao.put((old ?: Doc(type = "interview", variant = k)).copy(text = text, updatedAt = now()))
                }
                val words = answers["words"].orEmpty().trim()
                val voice = existing.firstOrNull { it.type == "voice" }
                if (words.isNotBlank() && (voice == null || !voice.text.contains(words))) dao.put((voice ?: Doc(type = "voice", variant = "samples")).copy(text = (words + "\n" + voice?.text.orEmpty()).take(2500), updatedAt = now()))
                status = "Saved."
                withContext(Dispatchers.Main) { nav.popBackStack() }
            }
        }) { Text("Save answers") }
    }
}

@Composable
fun DocScreen(nav: NavController, id: Long) {
    val buzz = rememberBuzz()
    val scope = rememberCoroutineScope()
    val docs by dao.docs().collectAsState(emptyList())
    val series by dao.series().collectAsState(emptyList())
    val loaded = docs.firstOrNull { it.id == id }
    if (loaded == null) { Screen("Document", nav) { Loading() }; return }
    var d by remember(id) { mutableStateOf(loaded) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editedHere by remember { mutableStateOf(false) }
    fun save(x: Doc) { d = x; scope.io { dao.put(x); markSaved() } }
    var unlocked by remember { mutableStateOf(false) }
    val locked = d.approved && st.lockApproved && !unlocked

    val target = docTarget(d)
    val words = Texts.words(d.text)
    val jargon = Texts.jargonIn(d.text, st.jargonExtra)
    val lengthOk = d.text.isNotBlank() && words <= target && words >= target * 0.5
    val limit = st.revisionLimit
    val seriesName = series.firstOrNull { it.id == d.seriesId }?.name.orEmpty()
    val kind = when {
        d.type == "custom" -> d.title.ifBlank { "a document" }
        d.type == "bio" && d.variant == "first" -> "a short artist bio in the first person"
        d.type == "bio" -> "an artist bio in the third person"
        d.seriesId != 0L -> "a statement for the series \"$seriesName\""
        else -> "an artist statement"
    }

    fun generate() {
        busy = true; status = "Writing..."
        scope.io({ status = it; busy = false }) {
            val extra = series.firstOrNull { it.id == d.seriesId }?.let { "About this series, in the artist's words: ${it.notes}" }.orEmpty() +
                (if (d.type == "custom") "What this document is for: ${d.brief}" else "")
            val text = G.ai.write(kind, target, Engine.factsText(), Engine.voice(), extra)
            val x = d.copy(text = Texts.trimToWords(text, target), approved = false, revisions = d.revisions + (if (d.text.isBlank()) 0 else 1), updatedAt = now())
            d = x; dao.put(x); status = ""; busy = false
        }
    }

    Screen(kind.replaceFirstChar { it.uppercase() }, nav) {
        if (d.type == "custom") {
            Field("Name", d.title, { save(d.copy(title = it)) })
            Field("What it is for", d.brief, { save(d.copy(brief = it)) }, lines = 2)
            Whole("About how many words", d.target) { n -> if (n > 0) save(d.copy(target = n)) }
        }
        if (locked) {
            Panel { Text(d.text) }
            Small("Approved and locked: ${Texts.words(d.text)} words. This is finished. The app uses exactly this.")
            TextButton(onClick = { unlocked = true }) { Text("I have a real reason to change it") }
        } else {
            Field("Text", d.text, {
                val first = !editedHere && d.text.isNotBlank()
                editedHere = true
                save(d.copy(text = it, approved = false, revisions = d.revisions + (if (first) 1 else 0), updatedAt = now()))
            }, lines = 8)
            SavedLine()
        }
        Panel {
            Text("Ready when these are all true", fontWeight = FontWeight.SemiBold)
            Check(lengthOk, "Length: $words words (aim for ${(target * 0.5).toInt()} to $target)")
                if (target > 0) LinearProgressIndicator(progress = { (words.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(start = 30.dp).height(4.dp),
                    color = if (lengthOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {})
            Check(d.text.isNotBlank() && jargon.isEmpty(), if (jargon.isEmpty()) "No stock art jargon" else "Jargon to cut: " + jargon.joinToString(", "))
            Check(true, "Written only from your interview and CV facts")
        }
        Status(status)
        if (d.approved) { if (!locked) Panel(strong = true) { Text("Approved. Editing it un-approves it.") } }
        else if (d.text.isNotBlank()) {
            if (d.revisions >= limit) Panel(strong = true) {
                Text("That is ${d.revisions} revision passes. " + (if (lengthOk && jargon.isEmpty()) "It meets every check, so it is ready as it is." else "Fix the open check above, then it is ready."))
                Note("More passes rarely make it better. Approve it, or park it for a week and look with fresh eyes.")
            }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                buzz()
                val x = d.copy(approved = true, updatedAt = now()); save(x)
                if (editedHere) scope.io {
                    val voice = dao.docsNow().firstOrNull { it.type == "voice" }
                    dao.put((voice ?: Doc(type = "voice", variant = "samples")).copy(text = (x.text.take(400) + "\n" + voice?.text.orEmpty()).take(2500), updatedAt = now()))
                }
                nav.popBackStack()
            }) { Text("Approve") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { save(d.copy(parkedUntil = now() + 7 * DAY)); nav.popBackStack() }) { Text("Park for a week") }
                TextButton(enabled = !busy, onClick = { generate() }) { Text(if (d.revisions >= limit) "One more pass anyway" else "Rewrite") }
            }
        }
        if (d.text.isBlank()) {
            if (G.ai.ready) Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { generate() }) { Text("Write it for me") }
            if (d.type == "statement" || d.type == "bio") {
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.io({ status = it }) {
                        val all = dao.docsNow(); val facts = dao.factsNow()
                        val answers = all.filter { it.type == "interview" && it.text.isNotBlank() }.associate { it.variant to it.text } +
                            (series.firstOrNull { it.id == d.seriesId }?.notes?.takeIf { it.isNotBlank() }?.let { mapOf("words" to it) } ?: emptyMap())
                        val text = if (d.type == "statement") Drafts.statement(answers, target) else Drafts.bio(st.firstName, st.lastName, st.city, st.mediums, facts, d.variant == "first", target)
                        if (text.isBlank()) throw Exception("There is nothing to build from yet. Answer a few interview questions first.")
                        val x = d.copy(text = text, approved = false, updatedAt = now()); d = x; dao.put(x)
                        status = "Built from your own answers, word for word. Tidy it into your voice, then approve."
                    }
                }) { Text(if (G.ai.ready) "Or build it from my own answers, no AI" else "Build it from my interview answers") }
            }
            if (!G.ai.ready) Note("No AI key is needed for this. With one, the app writes a smoother draft.")
        }
        if (d.type == "custom") DangerButton(onClick = { scope.remove(d.title.ifBlank { "the document" }, { dao.del(d) }, { dao.put(d) }); nav.popBackStack() }) { Text("Delete this document") }
    }
}

@Composable
fun Facts(nav: NavController) {
    val scope = rememberCoroutineScope()
    val facts by dao.facts().collectAsState(emptyList())
    var category by remember { mutableStateOf("education") }
    var year by remember { mutableStateOf(LocalDate.now().year) }
    val text = rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Fact?>(null) }
    Screen("CV facts", nav) {
        Intro("facts", "Only true, finished things. A show you applied to is not a line; a show you were in is. Accepted applications are added here automatically.")
        Explain("I have nothing to put here", "Everyone starts there. Add where you studied (or \"self-taught\"), and one or two skills or tools you really use. No exhibitions yet is normal and honest; the app adds each one as it happens, and early calls do not expect a long list.")
        Choice("Kind", Texts.factCategories, category) { category = it }
        key(editing?.id) { Whole("Year", year) { year = it } }
        Field(when (category) {
            "education" -> "Degree or course, school, city"
            "solo", "group", "invited" -> "Show title, venue, city"
            "skill" -> "Skill or tool"
            else -> "What, where"
        }, text)
        Button(enabled = text.value.isNotBlank(), onClick = {
            val t = text.value.trim(); val old = editing; text.value = ""; editing = null
            scope.io { dao.put((old ?: Fact()).copy(category = category, year = year, text = t)) }
        }) { Text(if (editing != null) "Save changes" else "Add line") }
        if (editing != null) TextButton(onClick = { editing = null; text.value = "" }) { Text("Cancel the edit") }
        Texts.factCategories.forEach { c ->
            val lines = facts.filter { it.category == c }
            if (lines.isNotEmpty()) {
                H(c.replaceFirstChar { it.uppercase() })
                lines.forEach { f ->
                    Row {
                        Text((if (f.year > 0) "${f.year}  " else "") + f.text, Modifier.weight(1f))
                        TextButton(onClick = { editing = f; category = f.category; year = f.year; text.value = f.text }) { Text("Edit") }
                        TextButton(onClick = { scope.remove("the CV line", { dao.del(f) }, { dao.put(f) }) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}


/** One piece of writing: a tick when approved, a dot when a draft waits for you, an empty ring when not written. */
@Composable
fun DocRow(label: String, text: String, approved: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }.heightIn(min = 56.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Mark(approved, current = !approved && text.isNotBlank())
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Small(when { text.isBlank() -> "Not written yet"; approved -> "Approved, ${Texts.words(text)} words"; else -> "Draft, waiting for your yes" })
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.outline)
        }
        Hair()
    }
}
