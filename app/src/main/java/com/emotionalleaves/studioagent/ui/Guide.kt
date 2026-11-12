package com.emotionalleaves.studioagent.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        GuideStep("setup", a, "Tell the app who you are",
            "Your name goes on your CV and file names. Your work hours keep the app silent while you are at work.",
            listOf("Answer four short questions.", "Say yes or no to calendar and notifications. Both are optional and can be changed later."),
            "onboarding", "Open setup", st.onboarded),
        GuideStep("interview", a, "Do the interview",
            "Everything the app writes for you is built from these answers, so it sounds like you and never invents anything.",
            listOf("Seven questions. Talk or type.", "Rough answers are fine. Three answered is enough to start.", "Tap Save answers. You can come back and add more any time."),
            "interview", "Open the interview", docs.count { it.type == "interview" && it.text.isNotBlank() } >= 3),
        GuideStep("series", a, "Name your first series",
            "A series is a group of works that belong together. Shows and calls ask for one, and it gives you a finish line.",
            listOf("Open Studio and tap Series at the top.", "Type a working title. It can change later.", "Tap Add series."),
            "works", "Open Studio", series.isNotEmpty()),
        GuideStep("piece", a, "Add one piece with its image",
            "The catalog is what every application, label and portfolio is built from.",
            listOf("Open Studio and tap Add a piece.", "Give it a title.", "Digital work: tap Import file. Physical work: tap Photograph.", "Pick its series.", "When the piece is finished, tap Finished and documented."),
            "works", "Open Studio", works.any { it.imagePath.isNotBlank() }),
        GuideStep("session", a, "Run one session",
            "This is the part that matters most. The app only needs to be told when you start and when you stop.",
            listOf("Tap Start session on Home.", "Pick the piece, tap Start, and put the phone down.", "When you stop, write one line for next time and tap End and save."),
            "session", "Start a session", sessions.isNotEmpty()),
        GuideStep("week", a, "Make the week yours",
            "The app suggests studio times around your job. If they are not realistic, change them. You can also pause planning any time.",
            listOf("Look at the suggested times.", "Tap Change these to edit the days, times and lengths.", "Tap Save and re-plan."),
            "week", "Open your week", st.weekTemplate.isNotBlank() || events.any { it.type == "studio" } || m("week"), optional = true, manual = true),

        GuideStep("ai", b, "Add an AI key",
            "This lets the app write statements and emails and read calls out of links and flyers. It is pay-as-you-go and separate from any chat subscription. The app stops at your monthly cap (5 to start).",
            listOf("In your phone's browser, open platform.claude.com and sign in or create an account.", "Add a small amount of credit in the billing section.", "Open Settings, then API keys, and create a key.",
                "Copy it straight away. It is shown only once.", "Come back here, tap the button below, paste it into API key, and tap Test the key."),
            "settings/ai", "Open AI settings", G.ai.ready, optional = true, link = "https://platform.claude.com/settings/keys"),
        GuideStep("facts", b, "Add your CV facts",
            "Education, any past shows or publications, skills. The CV is made only from these lines.",
            listOf("Pick the kind of line.", "Type the year and the line.", "Tap Add line. Even two lines of education is a real CV at this stage."),
            "facts", "Open CV facts", facts.isNotEmpty()),
        GuideStep("statement", b, "Get your statement and approve it",
            "Nearly every call asks for one. Once approved, the app reuses it everywhere.",
            listOf("Open Materials and tap Statement, 150 words.", "Tap Write it for me (or type your own).", "Read it once. Fix anything that is not true or not you.", "When the checklist is all ticks, tap Approve."),
            "materials", "Open Materials", docs.any { it.type == "statement" && it.approved && it.text.isNotBlank() }),

        GuideStep("email", c, "Connect your email",
            "Then the app sends approved emails itself and reads replies and results for you. Skip this and it still works: it opens your mail app with everything filled in.",
            listOf("This works with Gmail. Your Google account needs 2-Step Verification turned on first.", "In your browser, open myaccount.google.com/apppasswords.", "Name it Studio Agent and create it. Google shows a 16-letter password.",
                "Come back here and tap the button below.", "Type your Gmail address and paste that password (not your normal one).", "Tap Send me a test email."),
            "settings/email", "Open email settings", st.mailReady || m("email"), optional = true, link = "https://myaccount.google.com/apppasswords"),
        GuideStep("github", c, "Back up, and publish your portfolio page",
            "A backup means a lost phone does not lose your catalog. The portfolio page is the one link you give to venues.",
            listOf("On github.com create two repositories: a private one for backup and a public one for your page.", "Open github.com/settings/personal-access-tokens/new.", "Choose Only select repositories and pick those two.",
                "Under Repository permissions, set Contents to Read and write.", "Generate the token and copy it.", "Come back here, tap the button below, and paste the token and both repository names (yourname/repository).",
                "Tap Back up to GitHub now to test it.", "For the page: tap Publish in Materials. Then, once, in the public repository open Settings, Pages, and choose the main branch."),
            "settings/github", "Open GitHub settings", (st.ghReady && (st.ghBackupRepo.isNotBlank() || st.ghPagesRepo.isNotBlank())) || m("github"), optional = true,
            link = "https://github.com/settings/personal-access-tokens/new"),

        GuideStep("call", d, "Add your first call",
            "You never need to go looking inside the app. When you see a call anywhere, hand it over.",
            listOf("In any app or browser, tap Share and choose Studio Agent.", "Tap Add as an open call.", "Or open Calls and paste the link there.", "The app reads it, checks it and ranks it. You answer Yes, No or Not this year."),
            "calls", "Open Calls", opps.isNotEmpty()),
        GuideStep("sources", d, "Have calls sent to you",
            "Listing sites will email you new calls. The app reads those emails so you do not have to.",
            listOf("Sign up for email alerts on a listing site you like.", "Open Call sources and choose email.", "Type the sender's address or domain and tap Add.", "This needs your email connected (the step above)."),
            "sources", "Open Call sources", sources.isNotEmpty() || m("sources"), optional = true),
        GuideStep("apply", d, "Send your first application",
            "The app builds the whole pack. You look once and send.",
            listOf("In Calls, tap Yes on a call.", "Check the works it picked. Swap any.", "Tap Build the pack.", "When every check is ticked, send it: by email, or with Open the entry form.", "Tap the button that locks it as done. After that there is nothing to check."),
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
    var simple by remember { mutableStateOf(st.simpleMode) }
    var hidden by remember { mutableStateOf(st.guideHidden) }
    LaunchedEffect(tick) {
        val loaded = withContext(Dispatchers.IO) { try { guideSteps(ctx) } catch (e: Exception) { emptyList() } }
        steps = loaded
        if (openId.isBlank() || loaded.firstOrNull { it.id == openId }?.done == true) openId = (loaded.firstOrNull { !it.done && !it.optional } ?: loaded.firstOrNull { !it.done })?.id.orEmpty()
    }
    Screen("Guide", nav) {
        val done = steps.count { it.done }
        Text("One step at a time. Only the open step needs you; ignore the rest until you get there.")
        if (steps.isNotEmpty()) {
            LinearProgressIndicator(progress = { done.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
            Note("$done of ${steps.size} done. Steps marked optional can be skipped for good: the app works without them.")
        }
        steps.groupBy { it.group }.forEach { (group, list) ->
            H(group)
            list.forEach { s ->
                val open = s.id == openId
                Panel(strong = open, onClick = if (open) null else ({ openId = s.id })) {
                    Text((if (s.done) "✓ " else "○ ") + s.title + (if (s.optional && !s.done) "  (optional)" else ""), fontWeight = if (open) FontWeight.SemiBold else FontWeight.Normal)
                    if (open) {
                        Text(s.why)
                        s.how.forEachIndexed { i, line -> Text("${i + 1}. $line") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nav.navigate(s.route) }) { Text(s.button) }
                            if (s.link.isNotBlank()) OutlinedButton(onClick = { Share.open(ctx, s.link) }) { Text("Open the website") }
                        }
                        if (!s.done && (s.optional || s.manual)) TextButton(onClick = { st.guideManual = st.guideManual + "," + s.id }) { Text(if (s.manual) "I have done this" else "Skip this for good") }
                    }
                }
            }
        }
        H("Layout")
        Toggle("Simple layout: fewer tabs, progress folded away", simple) { simple = it; st.simpleMode = it }
        Toggle("Hide the Guide card on Home", hidden) { hidden = it; st.guideHidden = it }
    }
}
