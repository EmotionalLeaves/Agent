package com.emotionalleaves.studioagent

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.studioagent.data.Appl
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.data.Db
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Idea
import com.emotionalleaves.studioagent.data.Ledger
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.data.Series
import com.emotionalleaves.studioagent.data.Sess
import com.emotionalleaves.studioagent.data.Source
import com.emotionalleaves.studioagent.ui.MainActivity
import com.emotionalleaves.studioagent.work.DAY
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens a build-1 database with this build and checks nothing is lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpgradeTest {
    @Test fun dataFromBuildOneSurvivesTheUpgrade() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-test.db"
        ctx.deleteDatabase(name)
        val file = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            V1Schema.create.values.forEach { raw.execSQL(it) }
            V1Schema.rows.forEach { raw.execSQL(it) }
            raw.version = 1
        }
        val db = Room.databaseBuilder(ctx, Db::class.java, name).addMigrations(Db.MIGRATION_1_2, Db.MIGRATION_2_3).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.dao()
            val work = dao.artworksNow().single()
            assertEquals("x", work.title); assertEquals("", work.extraImages); assertEquals("", work.tags); assertEquals("", work.steps); assertEquals("", work.doneWhen)
            val doc = dao.docsNow().single()
            assertEquals("x", doc.text); assertEquals(0, doc.target); assertEquals("", doc.title)
            assertEquals("exhibition", dao.oppsNow().single().kind)
            assertEquals(1, dao.seriesNow().size); assertEquals(1, dao.sessionsNow().size); assertEquals(1, dao.ideasNow().size)
            assertEquals(1, dao.factsNow().size); assertEquals(1, dao.applsNow().size); assertEquals(1, dao.contactsNow().size)
            assertEquals(1, dao.eventsNow().size); assertEquals(1, dao.ledgerNow().size); assertEquals(1, dao.sourcesNow().size)
            // and the new columns can be written
            dao.put(work.copy(tags = "blue, night", extraImages = "/a|/b"))
            assertEquals("blue, night", dao.artworksNow().single().tags)
        }
        db.close()
    }
}

/** Runs the real app (database, engine, every screen) the way a phone would, without a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val future = System.currentTimeMillis() + 40 * DAY

    private fun opens(route: String, expect: String) {
        ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java).putExtra("route", route)).use {
            try {
                compose.waitUntil(15_000) { compose.onAllNodesWithText(expect, substring = true).fetchSemanticsNodes().isNotEmpty() }
            } catch (e: Throwable) {
                val seen = try { compose.onAllNodes(isRoot()).printToString(maxDepth = 40).replace("\n", " | ").take(1500) } catch (x: Throwable) { "nothing (${x.message?.take(200)})" }
                throw AssertionError("Screen '$route' never showed \"$expect\". On screen: $seen")
            }
        }
    }

    @Test fun everyScreenOpensOnAFreshInstall() {
        ctx
        listOf(
            "home" to "Set up in 2 minutes", "onboarding" to "Only your first name is needed", "basics" to "What is an open call?", "session" to "Two-minute start", "admin" to "Nothing to approve",
            "works" to "Add a piece", "ideas" to "Park it", "materials" to "Your own documents", "interview" to "Save answers", "facts" to "Add line",
            "calls" to "Add a call", "sources" to "Run the daily check now", "outreach" to "Your email list", "records" to "Quarterly report",
            "settings" to "Your rules", "settings/you" to "Sizes in", "settings/hours" to "Change my studio times", "settings/rules" to "What matters most when ranking",
            "settings/writing" to "These apply to everything", "settings/ai" to "Test the key", "settings/email" to "Send me a test email",
            "settings/github" to "Back up to GitHub now", "settings/backup" to "Choose a backup folder", "settings/permissions" to "Allow Do Not Disturb",
            "guide" to "Tell the app who you are", "receipts" to "What the app did on its own", "settings/calm" to "Finished means finished", "done" to "Session saved", "week" to "Change these", "sessions" to "No sessions yet", "more" to "Call sources", "share" to "Park as an idea"
        ).forEach { (route, expect) -> opens(route, expect) }
    }

    @Test fun everyScreenOpensWithRealData() {
        ctx
        val s = G.settings
        s.firstName = "Jade"; s.lastName = "Leaves"; s.simpleMode = false
        val ids = runBlocking {
            val dao = G.dao
            val series = dao.put(Series(name = "Night Kitchen", createdAt = 1))
            val work = dao.put(Artwork(title = "Blue Hour", year = 2026, widthIn = 16.0, heightIn = 20.0, seriesId = series, status = "available", imagePath = "/none.jpg", tags = "blue", createdAt = 1))
            dao.put(Artwork(title = "Night Shift", status = "in_progress", seriesId = series, createdAt = 2, steps = "[x] Open the file\n[ ] Block in the big shapes", doneWhen = "the light reads as 5am"))
            val doc = dao.put(Doc(type = "statement", variant = "150", text = "I draw the kitchen at five in the morning.", approved = false))
            dao.put(Doc(type = "custom", variant = "c1", title = "Cover letter", brief = "For a staff show", target = 120))
            dao.put(Doc(type = "interview", variant = "what", text = "Drawings of the kitchen."))
            dao.put(Fact(category = "education", year = 2020, text = "BFA, State University"))
            val opp = dao.put(Opp(title = "Winter Open", organiser = "Riverside Arts", scope = "local", deadline = future, jurors = "A. Juror", venue = "Riverside", createdAt = 1))
            val draft = dao.put(Appl(oppId = opp, workIds = "$work", statement = "I draw the kitchen.", answers = Engine.answersJson(listOf("Why this show?" to "")), createdAt = 2))
            val opp2 = dao.put(Opp(title = "Staff Show", scope = "campus", status = "applied", createdAt = 1))
            val sent = dao.put(Appl(oppId = opp2, workIds = "$work", status = "submitted", submittedAt = 5, proofText = "Sent.", createdAt = 1))
            val contact = dao.put(Contact(venue = "Campus Gallery", kind = "campus", email = "g@example.edu", createdAt = 1))
            dao.put(Evt(type = "todo", title = "Buy frames"))
            dao.put(Evt(type = "studio", title = "Studio time", date = future, endDate = future + 3_600_000))
            dao.put(Ledger(kind = "print", amount = -30.0, date = System.currentTimeMillis(), note = "Proofs"))
            dao.put(Sess(artworkId = work, start = System.currentTimeMillis() - 3_600_000, end = System.currentTimeMillis(), nextStep = "Glaze the sky"))
            dao.put(Idea(text = "Fridge light series", createdAt = 1))
            dao.put(Source(name = "CaFE alerts", kind = "email", url = "callforentry.org"))
            mapOf("work" to work, "doc" to doc, "opp" to opp, "draft" to draft, "sent" to sent, "contact" to contact)
        }
        listOf(
            "home" to "Start now", "startnow" to "Now: Block in the big shapes", "receipts" to "Application: Staff Show", "admin" to "Approve statement", "works" to "Blue Hour", "work/${ids["work"]}" to "More pictures of this piece",
            "crop/${ids["work"]}" to "Drag each dot", "ideas" to "Fridge light series", "materials" to "Cover letter", "doc/${ids["doc"]}" to "Ready when these are all true",
            "facts" to "BFA, State University", "calls" to "Winter Open", "call/${ids["opp"]}" to "The basics", "appl/${ids["draft"]}" to "Questions this call asks (1)",
            "appl/${ids["sent"]}" to "Submitted. Done.", "sources" to "CaFE alerts", "outreach" to "Campus Gallery", "contact/${ids["contact"]}" to "How realistic right now",
            "records" to "Buy frames", "guide" to "of 16 done", "week" to "Coming up", "sessions" to "Glaze the sky"
        ).forEach { (route, expect) -> opens(route, expect) }
    }

    @Test fun anApplicationGoesFromYesToAccepted() = runBlocking {
        ctx
        val dao = G.dao
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"; G.settings.useCalendar = false
        val series = dao.put(Series(name = "Night Kitchen"))
        val works = (1..3).map { dao.put(Artwork(title = "Piece $it", year = 2026, widthIn = 8.0, heightIn = 10.0, seriesId = series, status = "available", imagePath = "/none$it.jpg")) }
        dao.put(Doc(type = "statement", variant = "150", text = "I draw the kitchen at five in the morning.", approved = true))
        val oppId = dao.put(Opp(title = "Winter Open", organiser = "Riverside Arts", venue = "Riverside", city = "Austin", scope = "local", deadline = future, maxWorks = 2, email = "entries@example.org"))
        val opp = dao.oppsNow().single()

        val applId = Engine.createApplication(ctx, opp)
        assertEquals(applId, Engine.createApplication(ctx, opp))
        var appl = dao.applsNow().single()
        assertEquals(2, Engine.workIds(appl).size)
        assertEquals("email", appl.method)
        assertTrue(appl.statement.isNotBlank())
        assertEquals("yes", dao.oppsNow().single().status)
        assertEquals(3, dao.eventsNow().count { it.applId == applId })
        val checks = Engine.checklist(appl, opp, dao.artworksNow(), 0.0, System.currentTimeMillis())
        assertTrue(checks.first { it.first.startsWith("Statement included") }.second)
        assertTrue(!checks.first { it.first.startsWith("Every work has an image") }.second)

        Engine.markSubmitted(appl, "Sent by hand.")
        appl = dao.applsNow().single()
        assertEquals("submitted", appl.status)
        assertEquals(2, dao.artworksNow().count { it.status == "pending" })
        assertEquals("applied", dao.oppsNow().single().status)

        Engine.reopen(appl)
        assertEquals("ready", dao.applsNow().single().status)
        assertEquals(0, dao.artworksNow().count { it.status == "pending" })
        Engine.markSubmitted(dao.applsNow().single(), "Sent again.")

        Engine.recordResult(ctx, dao.applsNow().single(), true, "")
        assertEquals("accepted", dao.applsNow().single().status)
        assertEquals(2, dao.artworksNow().count { it.status == "shown" })
        assertTrue(dao.factsNow().single().text.contains("Winter Open"))
        assertTrue(dao.eventsNow().count { it.type == "task" } >= 5)
        assertEquals(works.size, dao.artworksNow().size)
        assertEquals(oppId, opp.id)
    }

    @Test fun theWeekIsPlannedPausedAndReplanned() = runBlocking {
        ctx
        val dao = G.dao; val s = G.settings
        s.useCalendar = false
        Engine.planWeek(ctx)
        val first = dao.eventsNow().filter { it.type == "studio" }
        assertTrue(first.isNotEmpty())
        Engine.planWeek(ctx)
        assertEquals(first.size, dao.eventsNow().count { it.type == "studio" })

        s.pausedUntil = System.currentTimeMillis() + 7 * DAY
        Engine.replan(ctx)
        assertEquals(0, dao.eventsNow().count { it.type == "studio" && it.date > System.currentTimeMillis() })

        s.pausedUntil = 0
        s.weekTemplate = "1@06:00/30;2@06:00/30;3@06:00/30;4@06:00/30;5@06:00/30;6@06:00/30;7@06:00/30"
        Engine.replan(ctx)
        val own = dao.eventsNow().filter { it.type == "studio" }
        assertTrue(own.size in 6..7)
        assertTrue(own.all { it.endDate - it.date == 30 * 60_000L })
    }

    @Test fun startingIsOneTapAndStepsLeadTheWay() = runBlocking {
        ctx
        val dao = G.dao; val s = G.settings
        s.firstName = "Jade"
        dao.put(Doc(type = "interview", variant = "what", text = "Kitchen drawings."))
        dao.put(Series(name = "Night Kitchen"))
        val id = dao.put(Artwork(title = "Night Shift", status = "in_progress", nextStep = "old note", steps = "[x] Open the file\n[ ] Block in the big shapes\n[ ] Add the light"))
        assertEquals("Block in the big shapes", Engine.nextStep().title)
        Engine.quickStart(5)
        assertEquals(id, s.sessionArtwork); assertEquals("make", s.sessionKind); assertEquals(5, s.sessionGoalMin)
        assertTrue(s.sessionStart > 0)
        assertEquals("session", Engine.nextStep().route)
        val (filled, what) = Engine.fillBlanks(Artwork(id = 7, format = "print", pixelW = 4800, pixelH = 6000, status = "documented"))
        assertEquals("Untitled #7", filled.title); assertEquals(16.0, filled.widthIn, 0.0); assertEquals(20.0, filled.heightIn, 0.0)
        assertEquals(10, filled.editionSize); assertEquals(480.0, filled.price, 0.0); assertEquals(5, what.size)
        assertTrue(Engine.fillBlanks(filled).second.isEmpty())
        val other = dao.put(Artwork(title = "Second Piece", status = "in_progress", createdAt = 99))
        s.sessionStart = 0
        assertEquals(other, Engine.currentPiece(dao.artworksNow())?.id)
        s.focusWork = id
        assertEquals(id, Engine.currentPiece(dao.artworksNow())?.id)
        assertTrue(Engine.forecast(1, 10, dao.artworksNow(), dao.sessionsNow()).startsWith("Once one piece is finished"))
        s.log("first"); s.log("second")
        assertEquals("second", s.activityLines().first().second)
    }

    @Test fun theHomeCardFollowsPinsAndSnoozes() = runBlocking {
        ctx
        val s = G.settings
        assertEquals("onboarding", Engine.nextStep().route)
        s.firstName = "Jade"
        assertEquals("interview", Engine.nextStep().route)
        s.pinnedStep = "Call the framer"
        assertEquals("Call the framer", Engine.nextStep().title)
        s.pinnedStep = ""
        s.snoozeDay = java.time.LocalDate.now().toString()
        assertEquals("Nothing else needs you today", Engine.nextStep().title)
    }
}
