package com.emotionalleaves.studioagent

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.studioagent.data.Appl
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.data.Series
import com.emotionalleaves.studioagent.data.Sess
import com.emotionalleaves.studioagent.ui.MainActivity
import com.emotionalleaves.studioagent.work.DAY
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Takes a picture of each main screen at phone size, so the design can be looked at without a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val dir = File("build/screens").apply { mkdirs() }

    private fun shot(name: String, route: String, expect: String) {
        ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java).putExtra("route", route)).use { scenario ->
            try { compose.waitUntil(15_000) { compose.onAllNodesWithText(expect, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() } } catch (e: Throwable) { }
            try { compose.waitForIdle() } catch (e: Throwable) { }
            try {
                var bmp: Bitmap? = null
                var note = ""
                scenario.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    try {
                        android.view.PixelCopy.request(a.window, b, { r -> note = "pixelcopy=$r" }, android.os.Handler(android.os.Looper.getMainLooper()))
                    } catch (e: Throwable) {
                        note = "drawn: $e"; v.draw(android.graphics.Canvas(b))
                    }
                    bmp = b
                }
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                File(dir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (note.isNotBlank() && note != "pixelcopy=0") File(dir, "$name.note.txt").writeText(note)
            } catch (e: Throwable) {
                File(dir, "$name.failed.txt").writeText(e.toString())
            }
        }
    }

    @Test fun freshInstall() {
        ctx
        shot("01_home_fresh", "home", "two-minute tour")
        shot("07_tour", "tour", "From the studio to a wall")
        shot("03_guide_fresh", "guide", "Take the tour")
        shot("04_more", "more", "People and venues")
        shot("05_settings", "settings", "Your rules")
        shot("06_basics", "basics", "What is an open call?")
    }

    private fun seed(): Map<String, Long> {
        ctx
        val s = G.settings
        s.firstName = "Jade"; s.lastName = "Leaves"; s.lastReviewWeek = Engine.weekId()
        val future = System.currentTimeMillis() + 40 * DAY
        return runBlocking {
            val dao = G.dao
            val series = dao.put(Series(name = "Night Kitchen", createdAt = 1))
            val work = dao.put(Artwork(title = "Blue Hour", year = 2026, widthIn = 16.0, heightIn = 20.0, seriesId = series, status = "available", imagePath = "/none.jpg", pixelW = 4800, pixelH = 6000, price = 480.0, editionSize = 10, createdAt = 1))
            val wip = dao.put(Artwork(title = "Night Shift", status = "in_progress", seriesId = series, createdAt = 2, steps = "[x] Open the file\n[ ] Block in the big shapes\n[ ] Add the light", doneWhen = "the light reads as 5am"))
            dao.put(Artwork(title = "Walk-in Cooler", status = "documented", seriesId = series, imagePath = "/none2.jpg", createdAt = 3))
            val doc = dao.put(Doc(type = "statement", variant = "150", text = "I draw the kitchen at five in the morning, before anyone else is in. The light is blue and the steel is cold.", approved = false))
            dao.put(Doc(type = "interview", variant = "what", text = "Drawings of the kitchen."))
            dao.put(Fact(category = "education", year = 2020, text = "BFA, State University"))
            val opp = dao.put(Engine.scored(Opp(title = "Winter Open: Small Works", organiser = "Riverside Arts", city = "Austin", scope = "local", deadline = future, jurors = "A. Juror", venue = "Riverside", theme = "work made after dark", themeFit = 82, themeReason = "night kitchen drawings match", createdAt = 1), Engine.profile()))
            dao.put(Engine.scored(Opp(title = "Staff and Faculty Exhibition", organiser = "University Gallery", scope = "campus", deadline = future - 10 * DAY, venue = "Main Hall", createdAt = 2), Engine.profile()))
            val draft = dao.put(Appl(oppId = opp, workIds = "$work", statement = "I draw the kitchen at five in the morning.", createdAt = 2))
            val contact = dao.put(Contact(venue = "Campus Gallery", kind = "campus", email = "g@example.edu", createdAt = 1))
            dao.put(Evt(type = "studio", title = "Studio time", date = System.currentTimeMillis() + DAY, endDate = System.currentTimeMillis() + DAY + 2_400_000))
            dao.put(Evt(type = "deadline", title = "Deadline: Winter Open: Small Works", date = future, oppId = opp, applId = draft))
            dao.put(Sess(artworkId = wip, start = System.currentTimeMillis() - 3_600_000, end = System.currentTimeMillis() - 1_200_000, nextStep = "Block in the big shapes"))
            mapOf("work" to work, "wip" to wip, "doc" to doc, "opp" to opp, "draft" to draft, "contact" to contact)
        }
    }

    @Test fun inUse() {
        val ids = seed()
        shot("10_home", "home", "Block in the big shapes")
        shot("11_studio", "works", "Blue Hour")
        shot("12_piece", "work/${ids["wip"]}", "Small steps")
        shot("13_session_setup", "session", "Two-minute start")
        shot("14_calls", "calls", "Winter Open")
        shot("15_call", "call/${ids["opp"]}", "Details")
        shot("16_application", "appl/${ids["draft"]}", "Statement")
        shot("17_materials", "materials", "Interview")
        shot("18_document", "doc/${ids["doc"]}", "Ready when")
        shot("19_people", "outreach", "Openings this month")
        shot("20_week", "week", "Studio times")
        shot("21_guide", "guide", "done")
        shot("22_path", "path", "the five levels")
        shot("23_review", "review", "Done for the week")
        shot("24_records", "records", "To do")
        shot("25_session_running", "startnow", "Now: Block in the big shapes")
        shot("26_done", "done", "It is all stored")
    }

    /** The same screens after dark, to check nothing turns unreadable. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h740dp-night-xhdpi") fun dark() {
        val ids = seed()
        shot("30_dark_home", "home", "Start now")
        shot("31_dark_studio", "works", "Blue Hour")
        shot("32_dark_calls", "calls", "Winter Open")
        shot("33_dark_document", "doc/${ids["doc"]}", "Ready when")
        shot("34_dark_guide", "guide", "done")
        shot("35_dark_session", "startnow", "Now: Block in the big shapes")
        shot("36_dark_piece", "work/${ids["wip"]}", "Small steps")
        shot("37_dark_you", "more", "People and venues")
        shot("38_dark_tour", "tour", "From the studio to a wall")
        shot("39_dark_done", "done", "It is all stored")
        shot("49_dark_interview", "interview", "Done for now")
        shot("59_dark_hours", "settings/hours", "Your studio times themselves")
        shot("70_dark_call", "call/${ids["opp"]}", "Details")
    }

    /** Phone text size turned up, to catch anything that clips or collides. */
    @Test fun bigText() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        val ids = seed()
        shot("40_big_home", "home", "Start now")
        shot("41_big_calls", "calls", "Winter Open")
        shot("42_big_session", "startnow", "Now: Block in the big shapes")
        shot("43_big_studio", "works", "Blue Hour")
        shot("44_big_call", "call/${ids["opp"]}", "Details")
    }

    @Test fun moreScreens() {
        val ids = seed()
        shot("27_ideas", "ideas", "Fridge light")
        shot("28_receipts", "receipts", "Staff Show")
        shot("29_admin", "admin", "Approve the 150-word statement")
        shot("50_interview", "interview", "Done for now")
        shot("51_contact", "contact/${ids["contact"]}", "Details: who, where")
        shot("52_letters", "letters", "print lab")
        shot("53_sources", "sources", "daily check")
        shot("54_facts", "facts", "Year")
        shot("55_settings_you", "settings/you", "Sizes in")
        shot("56_settings_calm", "settings/calm", "Finished means finished")
        shot("57_sessions", "sessions", "Glaze")
        shot("58_review", "review", "Done for")
    }

    @Test fun settingsAndTools() {
        val ids = seed()
        shot("60_rules", "settings/rules", "What matters most when ranking")
        shot("61_writing", "settings/writing", "Standing notes")
        shot("62_ai", "settings/ai", "Use the free writing help")
        shot("63_email", "settings/email", "App password")
        shot("64_backup", "settings/backup", "Choose a backup folder")
        shot("65_permissions", "settings/permissions", "Allow Do Not Disturb")
        shot("66_hours", "settings/hours", "Your studio times themselves")
        shot("67_crop", "crop/${ids["work"]}", "Drag each dot")
        shot("68_web", "web/${ids["draft"]}", "Fill")
        shot("69_basics", "basics", "What is an open call?")
    }

    /**
     * Accessibility sweep: on every main screen, every tappable thing must have something a screen reader can say.
     * Findings go to a11y.txt beside the screenshots.
     */
    @Test fun spokenLabels() {
        val ids = seed()
        val routes = listOf("home", "works", "calls", "outreach", "more", "guide", "path", "basics", "week", "sessions", "records", "receipts",
            "materials", "interview", "facts", "letters", "sources", "admin", "review", "tour", "startnow",
            "work/${ids["wip"]}", "call/${ids["opp"]}", "appl/${ids["draft"]}", "doc/${ids["doc"]}", "contact/${ids["contact"]}",
            "settings/you", "settings/hours", "settings/rules", "settings/calm", "settings/writing", "settings/ai", "settings/email", "settings/backup", "settings/permissions")
        val out = StringBuilder()
        routes.forEach { r ->
            ActivityScenario.launch<MainActivity>(Intent(ctx, MainActivity::class.java).putExtra("route", r)).use {
                try { compose.waitUntil(8_000) { compose.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().size > 3 } } catch (e: Throwable) { }
                try { compose.waitForIdle() } catch (e: Throwable) { }
                val nodes = compose.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes()
                var bad = 0
                nodes.forEach { n ->
                    val c = n.config
                    val text = c.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ")
                    val desc = c.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ")
                    val edit = if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)) c[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text else ""
                    val label = if (c.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)) c[androidx.compose.ui.semantics.SemanticsActions.OnClick].label.orEmpty() else ""
                    if (text.isBlank() && desc.isBlank() && edit.isBlank() && label.isBlank() && !c.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText)) {
                        bad++
                        out.append("$r: unlabelled tap target at ${n.boundsInRoot}\n")
                    }
                }
                out.append("$r: ${nodes.size} tap targets, $bad without a spoken label\n")
            }
        }
        File(dir, "a11y.txt").writeText(out.toString())
        org.junit.Assert.assertFalse(out.toString(), out.contains("unlabelled"))
    }

    /** The largest text size Android offers, to catch anything that breaks. */
    @Test fun hugeText() {
        org.robolectric.RuntimeEnvironment.setFontScale(2.0f)
        seed()
        shot("45_huge_home", "home", "Start now")
        shot("46_huge_session", "startnow", "Now: Block in the big shapes")
    }

    /** The phone turned sideways. */
    @Test @Config(sdk = [34], qualifiers = "w740dp-h360dp-land-xhdpi") fun sideways() {
        seed()
        shot("47_land_home", "home", "Start now")
        shot("48_land_session", "startnow", "Now: Block in the big shapes")
    }
}
