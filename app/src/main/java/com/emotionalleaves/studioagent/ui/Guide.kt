package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Button

import com.emotionalleaves.studioagent.work.Engine

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.sys.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One step of the walkthrough. [done] is worked out from what is really in the app, not from a tick box. */
data class GuideStep(
    val id: String, val group: String, val title: String, val why: String, val how: List<String>,
    val route: String, val button: String, val done: Boolean, val optional: Boolean = false, val link: String = "", val manual: Boolean = false
)

suspend fun guideSteps(ctx: Context): List<GuideStep> {
    val works = dao.artworksNow(); val docs = dao.docsNow(); val series = dao.seriesNow(); val sessions = dao.sessionsNow()
    val facts = dao.factsNow(); val opps = dao.oppsNow(); val appls = dao.applsNow(); val sources = dao.sourcesNow(); val events = dao.eventsNow()
    val manual = st.guideManual.split(",").toSet()
    fun m(id: String) = id in manual
    val a = "Start here"; val b = "Let the app write for you"; val c = "Sending and saving"; val d = "Finding shows"; val e = "Shortcuts"
    return listOf(
        GuideStep("setup", a, "Take the tour",
            "A two-minute look at how the app works. Your name, hours and week are optional and can be set any time.",
            listOf("Take the tour. Nothing to fill in.", "If you like, set your work hours so the app stays quiet while you are at work."),
            "tour", "Take the tour", st.onboarded),
        GuideStep("interview", a, "Do the interview",
            "Everything the app writes for you is built from these answers, so it sounds like you and never invents anything.",
            listOf("Seven questions. Talk or type.", "Rough answers are fine. Three answered is enough to start.", "Answers save as you type. Tap Done for now when you stop; come back and add more any time."),
            "interview", "Open the interview", docs.count { it.type == "interview" && it.text.isNotBlank() } >= 3),
        GuideStep("series", a, "Name your first series",
            "A series is a group of works that belong together. Shows and calls ask for one, and it gives you a finish line.",
            listOf("Open Studio and tap Series at the top.", "Type a working title. It can change later.", "Tap Add this series."),
            "works", "Open Studio", series.isNotEmpty()),
        GuideStep("piece", a, "Add one piece with its image",
            "The catalog is what every application, label and portfolio is built from.",
            listOf("Open Studio and tap Add a piece.", "Give it a title.", "Digital work: tap Choose a file. Physical work: tap Take a photo.", "Pick its series (under Size, year, medium, format, series).", "When it is done and photographed, set Where it stands to Ready to send."),
            "works", "Open Studio", works.any { it.imagePath.isNotBlank() }),
        GuideStep("session", a, "Run one session",
            "This is the part that matters most. The app only needs to be told when you start and when you stop.",
            listOf("Tap Start now on Today.", "The app picks the piece. Put the phone down.", "When you stop, write one line for next time and tap End and save."),
            "session", "Start a session", sessions.isNotEmpty()),
        GuideStep("week", a, "Make the week yours",
            "The app suggests studio times around your job. If they are not realistic, change them. You can also pause planning any time.",
            listOf("Look at the suggested times.", "On Today, tap Your week, then Change studio times.", "Tap Save and re-plan."),
            "week", "Open your week", st.weekTemplate.isNotBlank() || events.any { it.type == "studio" } || m("week"), optional = true, manual = true),

        GuideStep("print", a, "Get one piece printed and framed",
            "This is the moment digital work becomes something that can hang. The app walks each step and tells you what to ask for.",
            listOf("Open a finished piece in Studio.", "Open Getting it printed.", "Do only the highlighted step, then tap Done.", "Ready-made emails (in You, People and venues) has the message to send a print lab."),
            "works", "Open Studio", works.any { it.printStage >= 5 } || m("print"), optional = true, manual = true),
        GuideStep("facts", b, "Add your CV facts",
            "Education, any past shows or publications, skills. The CV is made only from these lines.",
            listOf("Pick the kind of line.", "Type the year and the line.", "Tap Add to CV. Even two lines of education is a real CV at this stage."),
            "facts", "Open CV facts", facts.isNotEmpty()),
        GuideStep("statement", b, "Get your statement and approve it",
            "Nearly every call asks for one. Once approved, the app reuses it everywhere.",
            listOf("In You, open Statement, bio and CV, then tap Statement, 150 words.", "Tap Write it for me (or type your own).", "Read it once. Fix anything that is not true or not you.", "When the checklist is all ticks, tap Approve."),
            "materials", "Open it", docs.any { it.type == "statement" && it.approved && it.text.isNotBlank() }),

        GuideStep("email", c, "Connect your email",
            "Then the app sends approved emails itself and reads replies and results for you. Skip this and it still works: it opens your mail app with everything filled in.",
            listOf("This works with Gmail. Your Google account needs 2-Step Verification turned on first.", "In your browser, open myaccount.google.com/apppasswords.", "Name it Studio Agent and create it. Google shows a 16-letter password.",
                "Come back here and tap the button below.", "Type your Gmail address and paste that password (not your normal one).", "Tap Send me a test email."),
            "settings/email", "Open email settings", st.mailReady || m("email"), optional = true, link = "https://myaccount.google.com/apppasswords"),
        GuideStep("autobackup", c, "Turn on automatic backups",
            "Once this is on, your catalog is saved every day without you doing or checking anything.",
            listOf("Tap the button below.", "Tap Choose a backup folder and pick any folder (a new one called Studio Agent is fine).", "Tap Back up there now. You will see the file name with a tick."),
            "settings/backup", "Open backup settings", st.backupFolder.isNotBlank()),
        GuideStep("github", c, "Publish your portfolio page (and a second backup)",
            "The portfolio page is the one link you give to venues. The same setup also keeps a second backup off your phone.",
            listOf("On github.com create two repositories: a private one for backup and a public one for your page.", "Open github.com/settings/personal-access-tokens/new.", "Choose Only select repositories and pick those two.",
                "Under Repository permissions, set Contents to Read and write.", "Generate the token and copy it.", "Come back here, tap the button below, and paste the token and both repository names (yourname/repository).",
                "Tap Back up to GitHub now to test it.", "For the page: tap Publish or update in You, Statement, bio and CV. Then, once, in the public repository open Settings, Pages, and choose the main branch."),
            "settings/github", "Open GitHub settings", (st.ghReady && (st.ghBackupRepo.isNotBlank() || st.ghPagesRepo.isNotBlank())) || m("github"), optional = true,
            link = "https://github.com/settings/personal-access-tokens/new"),

        GuideStep("call", d, "Add your first call",
            "You never need to go looking inside the app. When you see a call anywhere, hand it over.",
            listOf("In any app or browser, tap Share and choose Studio Agent.", "Tap Add as an open call.", "Or open Calls and paste the link there.", "A flyer on a notice board: in Calls, tap Photo of a flyer.", "The app reads it, checks it and ranks it. You answer Yes or No."),
            "calls", "Open Calls", opps.isNotEmpty()),
        GuideStep("sources", d, "Have calls sent to you",
            "Listing sites will email you new calls. The app reads those emails so you do not have to.",
            listOf("Sign up for email alerts on a listing site you like.", "In Calls, open All, then Where calls come from, and choose Email.", "Type the sender's address or domain and tap Add source.", "This needs your email connected (the step above)."),
            "sources", "Open Call sources", sources.isNotEmpty() || m("sources"), optional = true),
        GuideStep("apply", d, "Send your first application",
            "The app builds the whole pack. You look once and send.",
            listOf("In Calls, tap Yes on a call.", "Check the works it picked. Swap any.", "Fill anything the checks ask for. When every check is ticked the pack is made by itself.", "Send it: by email, or with Fill in the entry form.", "Tap the button that locks it as done. After that there is nothing to check."),
            "calls", "Open Calls", appls.any { it.status in setOf("submitted", "accepted", "declined") }),

        GuideStep("tile", e, "Put Start session one swipe away",
            "The fewer taps between you and starting, the better.",
            listOf("Swipe down twice from the top of your phone to open quick settings.", "Tap the pencil or Edit.", "Drag Start session into your tiles.", "Or: long-press your home screen, tap Widgets, and add the Studio Agent button."),
            "home", "Back to Home", m("tile"), optional = true, manual = true)
    ).map { if (it.optional && m(it.id)) it.copy(done = true) else it }
}

@Composable
fun Guide(nav: NavController) {
    val ctx = LocalContext.current
    val tick by st.changed.collectAsState()
    var steps by remember { mutableStateOf<List<GuideStep>>(emptyList()) }
    var openId by remember { mutableStateOf("") }
    LaunchedEffect(tick) {
        val loaded = withContext(Dispatchers.IO) { try { guideSteps(ctx) } catch (e: Exception) { emptyList() } }
        steps = loaded
        if (openId.isBlank() || loaded.firstOrNull { it.id == openId }?.done == true) openId = (loaded.firstOrNull { !it.done && !it.optional } ?: loaded.firstOrNull { !it.done })?.id.orEmpty()
    }
    Screen("Guide", nav) {
        val done = steps.count { it.done }
        Intro("guide", "One step at a time. Only the open step needs you; ignore the rest until you get there. Steps marked optional can be skipped for good: the app works without them.")
        if (steps.isNotEmpty()) {
            LinearProgressIndicator(progress = { done.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth().height(6.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {})
            Small("${steps.count { it.done && !it.optional }} of ${steps.count { !it.optional }} needed steps done" + (if (steps.any { it.optional }) ". ${steps.count { it.optional }} more are optional." else ""))
        }
        steps.groupBy { it.group }.forEach { (group, list) ->
            // A finished group folds to one line, so the page shows what is left.
            if (list.all { it.done } && list.none { it.id == openId }) {
                Expand("$group: all ${list.size} done") { list.forEach { Check(true, it.title) } }
                return@forEach
            }
            H(group)
            Column {
                list.forEachIndexed { idx, s ->
                    val open = s.id == openId
                    Timeline(s.done, current = open && !s.done, last = idx == list.lastIndex, onClick = if (open) null else ({ openId = s.id })) {
                        Text(s.title, style = if (open) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            color = if (s.done && !open) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (s.optional && !s.done) Row { Tag("optional") }
                        if (open) Panel {
                            Text(s.why)
                            s.how.forEachIndexed { i, line -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp)); Text(line, Modifier.weight(1f)) } }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { nav.navigate(s.route) }) { Text(s.button) }
                                if (s.link.isNotBlank()) OutlinedButton(onClick = { Share.open(ctx, s.link) }) { Text("Open the website") }
                            }
                            if (!s.done && (s.optional || s.manual)) TextButton(onClick = { st.guideManual = st.guideManual + "," + s.id }) { Text(if (s.manual) "I have done this" else "Skip this for good") }
                        }
                    }
                }
            }
        }
        Expand("Where this is going: the five levels") { LevelsBody() }
    }
}


val basics = listOf(
    "Do I need to know how the art world works?" to "No. The app is built so you can start knowing nothing. Wherever it asks something you cannot answer, there is a \"not sure\" or \"decide for me\" choice, and it picks a safe default.",
    "What is an open call?" to "An invitation from a gallery, art center, library, magazine or fair for artists to send in work for a show. You apply by a deadline and they choose what goes in. It is the normal way to get a first exhibition.",
    "What does \"juried\" mean?" to "One or more named people, the jurors, pick the work. That is the good, normal kind. If paying a fee guarantees you a place, nobody is choosing, and it does little for you. The app holds those back.",
    "Do I have to pay to apply?" to "Sometimes. Many calls charge an entry fee and many are free. The app only shows you free ones at the start, and never pays anything for you.",
    "How does digital art end up on a wall?" to "It is printed with pigment inks on archival paper, often called an archival pigment print or giclee, and framed. Shows accept these all the time. A print lab does the printing; the app prepares the order.",
    "What is a series?" to "A group of pieces that clearly belong together: the same subject, idea or look. Shows usually want several works that hang well side by side, which is why the app aims you at about ten.",
    "What is an edition?" to "The number of prints you promise to ever make of one image, for example 10. Each is numbered, like 3/10. You cannot raise the number later, so pick one and keep it. If unsure, 10 is a common, safe choice.",
    "What size should I print?" to "As large as your file stays sharp, or smaller. The app works this out from the file and can fill it in for you. Standard frame sizes such as 8 x 10, 11 x 14 and 16 x 20 inches keep framing cheap.",
    "How much should I charge?" to "Nobody knows at first. A simple, consistent rule is enough, such as a price per square inch plus the cost of the frame. The app can fill this in from a rule, and you can mark work not for sale.",
    "What is an artist statement?" to "A short text, in your own words, about what you make and why. Almost every call asks for one. The app drafts it from your interview answers and you approve it once.",
    "What is the difference between a bio and a statement?" to "The statement is about the work. The bio is about you: where you are based, any training, any shows. Both are short.",
    "What is an artist CV?" to "A plain list of your education, exhibitions, awards and similar. A short one is completely normal when you start. The app adds a line each time you get into something.",
    "What happens if I get in?" to "You deliver or ship the framed work by a date, it hangs for a few weeks, and you collect whatever does not sell. Venues usually keep a share of any sale, called commission. The app turns the acceptance into a checklist.",
    "What if I am not chosen?" to "That is the usual result for everyone, including artists who show a lot. It is not a verdict on the work. The app frees the pieces for the next call and does not make you go over it.",
    "What is pay-to-show, or a vanity gallery?" to "A place where the fee buys you a spot instead of a juror choosing the work: \"all artists accepted\", wall rental, hanging fees. It looks like a show but counts for little on a CV and costs a lot. The app marks these calls so you can say No without wondering.",
    "What is a commission on sales?" to "The share of the price a venue keeps when your work sells there. Galleries usually take 30 to 50 percent; art centers and fairs often 20 to 30. Set your price so the part left after commission still feels fair to you.",
    "What rights do I give when I enter a call?" to "Normally only the right to show pictures of your work to promote that show, with your name on them. You keep the copyright. Be careful with words like perpetual, irrevocable, exclusive or commercial use: those take much more. The app flags them.",
    "A call shows \"Fee?\". What does that mean?" to "The call's words did not say whether there is an entry fee, so the app does not call it free. Open the call's page and look near \"how to apply\". Still unclear? On the call, tap Ask them by email: a short polite question opens ready to send. Never pay before you know what the fee buys.",
    "What do I do at my own opening?" to "Arrive on time, find the person who runs the space and say thank you, then stand near your work for a while. When someone looks at it, a simple \"Hi, this one is mine\" is enough; most people then ask one question. Take a photo of the work on the wall for your records. Leaving after an hour is normal.",
    "Someone wants me to make a piece for them. What do I do?" to "Start from your price rule (the same size costs the same as your other work), then add about a quarter for the back-and-forth a commission takes. Ask for half as a deposit before you start and the rest when it is done, show them one draft along the way, and keep the copyright. In People, Ready-made emails has a quote you can fill in and send.",
    "When should I raise my prices?" to "When work sells steadily: as a rule of thumb, when more than half of what you show sells within a year, or after a strong show. Raise by about 10 to 20 percent at a time, and never lower prices for work already sold; buyers count on that. Change your price rule in Settings and every piece follows.",
    "How do artists get a gallery to represent them?" to "Slowly, and mostly through people: group shows, openings, artists who show there already, and a body of work that holds together. Galleries rarely take cold submissions; look for the line on their site that says whether they do. A few years of shows, a clear series and a mailing list of people who buy are what make the conversation possible.",
    "What do I bring on hanging day?" to "A small kit: a tape measure, a pencil, a level (a phone app works), a hammer and picture hooks or the venue's hanging system, painter's tape, a soft cloth for the glass, your wall labels and price list, and your phone for photos. Hang the center of each piece about 57 inches (145 cm) from the floor; that is the height most galleries use.",
    "How do I get a framed piece ready to hang?" to "Two D-rings screwed into the back of the frame, a third of the way down each side, with picture wire between them, loose enough to rise about two inches when pulled up. Ready-made frames often come with a sawtooth hanger instead, which is fine for a small piece. Put your name, title and contact on a label on the back.",
    "How do I ship a framed piece?" to "Wrap the frame in glassine or plain paper (never bubble wrap against the surface), add corner protectors, then bubble wrap, then a box about 3 inches bigger on every side, filled so nothing moves. Write FRAGILE and THIS SIDE UP, insure it for its price, and photograph it before it is sealed. Many calls prefer hand delivery when you are local; ask.",
    "What is an opening?" to "The free first evening of a show, open to anyone. People look at the work and talk. Going to openings, even just to look, is how people in local art slowly come to know your face. Nobody expects you to network.",
    "Where do the calls come from?" to "Once a day the app reads eight public listing sites (CaFÉ, ArtCall, Artwork Archive, Art Deadline, ArtConnect, ArtRabbit, ArtJobs and Artshow), opens each new call and picks out the title, deadline and fee. It skips calls already closed and sites that ask apps to stay out. You can add more sites, or email alerts, under Calls, All, Where calls come from. Anything you see yourself, share it into the app.",
    "Can I show where I work or study?" to "Often, yes. Many universities have a staff or community show, a library display wall, or a student union space that staff can book. Put your school in You, Settings, Your details: its own calls are then kept for you, and two of the starter venues in People become its gallery and its library. Ask at the gallery or library desk who books the walls.",
    "I saw a call on a flyer. How do I add it?" to "Open Calls and tap Photo of a flyer. Hold the phone square to the flyer so all the words are in the picture. The words are read on your phone, and the name, deadline, fee and web address are picked out. Check those against the flyer; anything it missed, type in. A photo you already took works the same way: tap A saved photo.",
    "Is there a quicker way to start?" to "Yes, three. Long-press the app icon and tap Start now. Add the Start now widget to your home screen. Or add the Start now tile to the quick settings you pull down from the top of the screen; a Park an idea tile sits beside it for ideas that arrive at work. Each starts the timer on the right piece straight away. The Next step widget goes further: it shows today's one step on your home screen, so you know what to do without opening the app.",
    "What are the levels in this app?" to "A way to keep the next step small. Level 0 is making your first series. Each level opens slightly bigger opportunities. You can ignore them, or set your level yourself in You, Settings, Your rules."
)

/** Everything about the app itself in one place: the tour, plain answers, and what the app is. */
@Composable
fun Basics(nav: NavController) {
    Screen("Help", nav) {
        OutlinedButton(onClick = { nav.navigate("tour") }, modifier = Modifier.fillMaxWidth()) { Text("Take the two-minute tour again") }
        Section("Ask anything")
        val scope = rememberCoroutineScope()
        val question = rememberSaveable { mutableStateOf("") }
        var reply by remember { mutableStateOf("") }
        var asking by remember { mutableStateOf(false) }
        Field("A question about shows, calls or this app", question, lines = 2)
        if (question.value.isNotBlank()) Button(enabled = !asking, onClick = {
            asking = true; reply = ""
            val q = question.value.trim()
            scope.io({ reply = it; asking = false }) { reply = Engine.ask(q, basics); asking = false }
        }) { Text(if (asking) "Thinking…" else "Ask") }
        // The answer can be selected and copied, to keep or to send to someone.
        if (reply.isNotBlank()) Panel { androidx.compose.foundation.text.selection.SelectionContainer { Text(reply) } }
        Section("Plain answers")
        Small("Tap any question. None of this is a test, and you can use the whole app without reading it.")
        basics.forEach { (q, a) -> Expand(q) { Text(a) } }
        Section("About this app")
        AboutBody()
    }
}


@Composable
fun PathScreen(nav: NavController) = Guide(nav)

/** The five levels as one timeline, with the gate to the next one. Shown folded at the foot of the Guide. */
@Composable
fun LevelsBody() {
    val works by dao.artworks().collectAsState(emptyList())
    val docs by dao.docs().collectAsState(emptyList())
    val facts by dao.facts().collectAsState(emptyList())
    val rules = com.emotionalleaves.studioagent.work.Engine.rules()
    val stats = com.emotionalleaves.studioagent.work.Engine.statsOf(works, docs, facts)
    val level = com.emotionalleaves.studioagent.domain.Levels.level(stats, rules)
    val gate = com.emotionalleaves.studioagent.domain.Levels.gate(stats, rules)
    val steps = listOf(
        Triple("Foundation", "Make your first series, photograph or import it, write the statement.", "Opens Level 1: ${rules.gateWorks} finished pieces with images in one series, and an approved statement."),
        Triple("First walls", "Free and low-fee local shows: campus, library, cafe, community center, online.", "Opens Level 2: ${rules.gateShows1} exhibitions on your CV."),
        Triple("Regional", "Regional juried shows with named jurors, artist-run spaces, small grants.", "Opens Level 3: ${rules.gateShows2} shows, plus an award or an invited show."),
        Triple("Recognized", "A solo show, residencies, competitive national calls, curators who know your work.", "Opens Level 4: a solo show, plus a residency or grant."),
        Triple("Established", "Gallery representation, museum group shows, major grants.", "")
    )
    run {
        Small("Five levels, one small step at a time. You only ever need to think about the one you are on.")
        Column {
            steps.forEachIndexed { i, (name, what, opens) ->
                Timeline(i < level, current = i == level, last = i == steps.lastIndex) {
                    Text("Level $i: $name", style = if (i == level) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        color = if (i > level) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (i == level) Row { Tag("you are here", strong = true) }
                    Text(what, color = if (i == level) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (i == level && level < 4) Panel {
                        LinearProgressIndicator(progress = { gate.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {})
                        Text("So far: ${gate.text}")
                        if (opens.isNotBlank()) Small(opens)
                    } else if (opens.isNotBlank() && i > level) Small(opens)
                }
            }
        }
        Small("Rough timing for someone with a full-time job: months for the first two levels, years for the rest. The gates decide, not the calendar. You can change the gates or set your level yourself in Settings, Your rules.")
    }
}
