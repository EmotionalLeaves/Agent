package com.emotionalleaves.studioagent

import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.domain.CallReader
import com.emotionalleaves.studioagent.domain.CareerStats
import com.emotionalleaves.studioagent.domain.Drafts
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Profile
import com.emotionalleaves.studioagent.domain.Readiness
import com.emotionalleaves.studioagent.domain.Rules
import com.emotionalleaves.studioagent.domain.Scoring
import com.emotionalleaves.studioagent.domain.Texts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DomainTest {
    private val future = 4_000_000_000_000L
    private val now = 1_800_000_000_000L

    @Test fun levelsClimbOnlyWhenGatesAreMet() {
        assertEquals(0, Levels.level(CareerStats()))
        assertEquals(0, Levels.level(CareerStats(bestSeriesDocumented = 10)))
        assertEquals(1, Levels.level(CareerStats(bestSeriesDocumented = 10, statementApproved = true)))
        assertEquals(2, Levels.level(CareerStats(10, true, exhibitions = 3)))
        assertEquals(2, Levels.level(CareerStats(10, true, exhibitions = 8)))
        assertEquals(3, Levels.level(CareerStats(10, true, exhibitions = 8, awardOrInvited = true)))
        assertEquals(4, Levels.level(CareerStats(10, true, 8, true, solo = 1, residencyOrGrant = true)))
    }

    @Test fun gateProgressIsBetweenZeroAndOne() {
        val g = Levels.gate(CareerStats(bestSeriesDocumented = 5))
        assertTrue(g.progress > 0f && g.progress < 1f)
        assertTrue(g.text.startsWith("5 of 10"))
    }

    @Test fun levelZeroOnlySeesFreeCalls() {
        val p = Profile(level = 0)
        val free = Opp(title = "Staff show", scope = "campus", fee = 0.0, deadline = future, jurors = "A", venue = "Library")
        val paid = free.copy(fee = 10.0)
        assertTrue(Scoring.ineligible(free, p, now).isEmpty())
        assertTrue(Scoring.ineligible(paid, p, now).any { it.contains("cap") })
    }

    @Test fun payToShowAndPassedDeadlinesAreHeldBack() {
        val p = Profile(level = 2, feeBudgetLeft = 100.0, availableWorks = 5)
        val vanity = Opp(title = "Gallery showcase", scope = "regional", fee = 35.0, guaranteed = true, deadline = future)
        assertTrue(Scoring.ineligible(vanity, p, now).contains("Pay-to-show"))
        assertTrue(Scoring.trustFlags(vanity).isNotEmpty())
        val old = Opp(title = "Old", scope = "local", deadline = now - 1000)
        assertTrue(Scoring.ineligible(old, p, now).contains("Deadline has passed"))
    }

    @Test fun needsEnoughWorksAndRespectsBudget() {
        val p = Profile(level = 1, feeBudgetLeft = 10.0, availableWorks = 2)
        val o = Opp(title = "Local open", scope = "local", fee = 20.0, minWorks = 3, deadline = future)
        val why = Scoring.ineligible(o, p, now)
        assertTrue(why.any { it.contains("budget") })
        assertTrue(why.any { it.contains("Needs 3 works") })
    }

    @Test fun freeLocalCallsOutrankPaidDistantOnes() {
        val p = Profile(level = 1, feeBudgetLeft = 100.0)
        val free = Opp(title = "A", scope = "local", fee = 0.0, online = false, jurors = "J")
        val paid = Opp(title = "B", scope = "regional", fee = 35.0, online = false, jurors = "J")
        assertTrue(Scoring.score(free, p).first > Scoring.score(paid, p).first)
        assertTrue(Scoring.isStretch(paid, p))
        assertFalse(Scoring.isStretch(free, p))
    }

    @Test fun weekIsPlannedAroundWorkHours() {
        val monday = LocalDate.of(2026, 11, 9)
        val slots = Planner.plan(monday, setOf(1, 2, 3, 4, 5), LocalTime.of(15, 30), 90, LocalTime.of(22, 30), 4)
        assertEquals(240, slots.sumOf { it.minutes })
        assertEquals(2, slots.count { it.start.dayOfWeek.value <= 5 })
        slots.filter { it.start.dayOfWeek.value <= 5 }.forEach {
            assertEquals(LocalTime.of(17, 0), it.start.toLocalTime())
            assertEquals(40, it.minutes)
        }
        assertTrue(slots.any { it.start.dayOfWeek.value == 6 && it.start.toLocalTime() == LocalTime.of(10, 0) })
    }

    @Test fun quietDuringWorkAndAfterWindDown() {
        val days = setOf(1, 2, 3, 4, 5)
        val ws = LocalTime.of(7, 0); val we = LocalTime.of(15, 30); val bed = LocalTime.of(22, 30)
        assertTrue(Planner.isQuiet(LocalDateTime.of(2026, 11, 11, 10, 0), days, ws, we, bed))
        assertFalse(Planner.isQuiet(LocalDateTime.of(2026, 11, 11, 18, 0), days, ws, we, bed))
        assertTrue(Planner.isQuiet(LocalDateTime.of(2026, 11, 11, 22, 0), days, ws, we, bed))
        assertTrue(Planner.isQuiet(LocalDateTime.of(2026, 11, 12, 5, 0), days, ws, we, bed))
        assertFalse(Planner.isQuiet(LocalDateTime.of(2026, 11, 14, 10, 0), days, ws, we, bed))
    }

    @Test fun deadlinesReadAsWeekends() {
        assertEquals("3 weekends left", Planner.weekendsLeft(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 30)))
        assertEquals("tomorrow", Planner.weekendsLeft(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12)))
        assertEquals("passed", Planner.weekendsLeft(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 10)))
    }

    @Test fun fileNamesAndPrintChecks() {
        assertEquals("Leaves_BlueHour_2026.jpg", Texts.fileName("Leaves", "Blue Hour", 2026, "Archival pigment print", 16.0, 20.0))
        assertEquals(300, Texts.ppi(4800, 6000, 16.0, 20.0))
        assertTrue(Texts.printVerdict(4800, 6000, 16.0, 20.0).startsWith("Print-ready"))
        assertTrue(Texts.printVerdict(1200, 1500, 16.0, 20.0).startsWith("Too few pixels"))
        assertEquals(480.0, Texts.price(16.0, 20.0, 1.5), 0.001)
    }

    @Test fun wordRules() {
        assertEquals(4, Texts.words("  one two\nthree   four "))
        assertTrue(Texts.words(Texts.trimToWords("One two three. Four five six. Seven eight nine ten.", 7)) <= 7)
        assertTrue(Texts.jargonIn("My work explores the liminal.").contains("liminal"))
        assertTrue(Texts.jargonIn("I paint the kitchen at 5am.").isEmpty())
    }

    @Test fun rulesCanBeLoosened() {
        val stats = CareerStats(bestSeriesDocumented = 6, statementApproved = true)
        assertEquals(0, Levels.level(stats))
        assertEquals(1, Levels.level(stats, Rules(gateWorks = 6)))
        assertEquals(3, Levels.level(CareerStats(), Rules(levelOverride = 3)))
        assertEquals(15.0, Levels.feeCap(0, Rules(feeCaps = listOf(15.0, 25.0, 40.0, -1.0, -1.0))), 0.001)
        assertEquals(Double.MAX_VALUE, Levels.feeCap(3), 0.0)

        val national = Opp(title = "Big national", scope = "national", fee = 45.0, deadline = future)
        val strict = Profile(level = 0, feeBudgetLeft = 100.0)
        assertTrue(Scoring.ineligible(national, strict, now).isNotEmpty())
        assertTrue(Scoring.ineligible(national, strict.copy(rules = Rules(showAllLevels = true)), now).isEmpty())
        assertFalse(Levels.outreachAllowed(0, "commercial", Rules()))
        assertTrue(Levels.outreachAllowed(0, "commercial", Rules(gateOutreach = false)))
    }

    @Test fun formatsAndKindsFilterCalls() {
        val screenOnly = Opp(title = "Video night", scope = "local", acceptsPrints = false, acceptsNewMedia = true, deadline = future)
        assertTrue(Scoring.ineligible(screenOnly, Profile(routes = listOf("print")), now).isNotEmpty())
        assertTrue(Scoring.ineligible(screenOnly, Profile(routes = listOf("print", "screen")), now).isEmpty())
        val residency = Opp(title = "Residency", scope = "local", kind = "residency", deadline = future)
        assertTrue(Scoring.ineligible(residency, Profile(kinds = listOf("exhibition")), now).any { it.contains("turned off") })
        assertTrue(Scoring.ineligible(residency, Profile(kinds = listOf("exhibition", "residency")), now).isEmpty())
    }

    @Test fun whatMattersMostChangesTheRanking() {
        val cheapPoorFit = Opp(title = "A", scope = "local", fee = 0.0, themeFit = 20)
        val paidGreatFit = Opp(title = "B", scope = "local", fee = 40.0, themeFit = 95)
        val moneyFirst = Profile(level = 1, rules = Rules(weights = listOf(1, 2, 3, 2, 2, 2)))
        val themeFirst = Profile(level = 1, rules = Rules(weights = listOf(3, 2, 1, 2, 2, 2)))
        assertTrue(Scoring.score(cheapPoorFit, moneyFirst).first > Scoring.score(paidGreatFit, moneyFirst).first)
        assertTrue(Scoring.score(paidGreatFit, themeFirst).first > Scoring.score(cheapPoorFit, themeFirst).first)
    }

    @Test fun yourOwnWeekIsFollowedExactly() {
        val t = Planner.parseTemplate("3@18:30/45;7@09:00/120;bad;9@10:00/30")
        assertEquals(2, t.size)
        assertEquals(t, Planner.parseTemplate(Planner.formatTemplate(t)))
        val slots = Planner.planFromTemplate(LocalDate.of(2026, 11, 9), t)
        assertEquals(2, slots.size)
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 30), slots[0].start)
        assertEquals(LocalDateTime.of(2026, 11, 15, 9, 0), slots[1].start)
        val suggested = Planner.suggestedTemplate(setOf(1, 2, 3, 4, 5), LocalTime.of(15, 30), 90, LocalTime.of(22, 30), 4)
        assertEquals(240, suggested.sumOf { it.minutes })
    }

    @Test fun theSeriesForecastUsesRealPace() {
        assertEquals(0, Planner.weeksToFinish(10, 10, 5.0, 4.0))
        assertEquals(null, Planner.weeksToFinish(2, 10, 0.0, 4.0))
        assertEquals(null, Planner.weeksToFinish(2, 10, 5.0, 0.0))
        assertEquals(10, Planner.weeksToFinish(2, 10, 5.0, 4.0))
        assertEquals(1, Planner.weeksToFinish(9, 10, 1.0, 8.0))
    }

    @Test fun aCallIsReadWithoutAi() {
        val today = LocalDate.of(2026, 11, 11); val zone = java.time.ZoneId.of("America/Chicago")
        val text = "Winter Open: Small Works\nRiverside Arts invites entries from regional artists. Juried by Ana Reyes.\n" +
            "Opens October 1, 2026. Deadline: January 15, 2027. Entry fee: $25 for up to 3 works. Statement of 100 words.\n" +
            "Apply at https://riverside.example.org/call or email entries@riverside.example.org"
        val o = CallReader.read(text, today, zone, "Pasted", 1L)
        assertEquals("Winter Open: Small Works", o.title)
        assertEquals(LocalDate.of(2027, 1, 15), CallReader.deadline(text, today))
        assertEquals(25.0, o.fee, 0.0); assertEquals(3, o.maxWorks); assertEquals(100, o.statementLimit)
        assertEquals("https://riverside.example.org/call", o.url); assertEquals("entries@riverside.example.org", o.email)
        assertEquals("regional", o.scope); assertEquals("exhibition", o.kind); assertEquals("Ana Reyes", o.jurors)
        assertEquals(LocalDate.of(2027, 3, 1), CallReader.deadline("Residency applications due 3/1/27", today))
        assertEquals(LocalDate.of(2026, 12, 5), CallReader.deadline("Show runs 5 December 2026 at the library", today))
        assertEquals(null, CallReader.deadline("No dates here", today))
        // No year written: the next time the date comes round.
        val nov = LocalDate.of(2026, 11, 12)
        assertEquals(LocalDate.of(2026, 11, 30), CallReader.deadline("Deadline: November 30", nov))
        assertEquals(LocalDate.of(2027, 2, 1), CallReader.deadline("Entries due 1st February.", nov))
        assertEquals(LocalDate.of(2027, 1, 15), CallReader.deadline("Deadline January 15, 2027", nov))
        assertEquals(0.0, CallReader.fee("There is no entry fee. Prize of $500."), 0.0)
        assertEquals("residency", CallReader.read("Artist residency, international", today, zone, "x", 1L).kind)
        // Seen on real listing pages: a prize is not a fee, and a menu word does not decide the kind.
        assertEquals(0.0, CallReader.fee("The Homiens Art Prize ($12,000 Annually). Entries open to all."), 0.0)
        assertEquals(0.0, CallReader.fee("$1K Open Theme Competition. Entry is free."), 0.0)
        assertEquals(30.0, CallReader.fee("Application fee: $30. Grant of $5,000."), 0.0)
        assertEquals("exhibition", CallReader.read("Winter Juried Exhibit\nMenu: residencies, grants, jobs", today, zone, "x", 1L).kind)
        assertEquals("Creative Futures 2027", CallReader.cleanTitle("Home | Creative Futures 2027"))
        // Numbers typed the way people type them.
        assertEquals(1200.0, Texts.number("$1,200")!!, 0.0); assertEquals(12.5, Texts.number("12,5")!!, 0.0)
        assertEquals(16.0, Texts.number("16 in")!!, 0.0); assertEquals(null, Texts.number("about"))
        assertTrue(Texts.isLink("artcall.org/calls") && Texts.isLink("https://www.artjobs.com/open-calls?x=1") && !Texts.isLink("Winter Open, deadline Jan 15") && !Texts.isLink("a@b.org"))
        assertTrue(Texts.looksLikeEmail("entries@riverside.org") && !Texts.looksLikeEmail("entries@riverside") && !Texts.looksLikeEmail("a b@c.org"))
        assertEquals("the 150-word statement", com.emotionalleaves.studioagent.work.docWords("statement", "150"))
        assertEquals("the bio", com.emotionalleaves.studioagent.work.docWords("bio", "first"))
        assertEquals("1 file", Texts.count(1, "file")); assertEquals("3 files", Texts.count(3, "file"))
        // Warning signs a beginner would not know to look for, found without AI.
        assertTrue(CallReader.read("Gallery 9 Open\nAll artists are accepted. Wall fee $300. Deadline March 1, 2027", today, zone, "x", 1L).guaranteed)
        assertTrue(CallReader.read("Photo Prize\nEntrants grant us a perpetual, irrevocable licence. Deadline March 1, 2027", today, zone, "x", 1L).rightsGrab)
        val fair = CallReader.read("Winter Juried Show\nArtists grant the right to reproduce images to promote the exhibition. Deadline March 1, 2027", today, zone, "x", 1L)
        assertTrue(!fair.guaranteed && !fair.rightsGrab)
        // The same call on two listing sites is kept once; different calls on the same day are not merged.
        val day = 1_800_000_000_000L
        assertTrue(Texts.sameCall("Albuquerque Open 2027", day, "Call for Entries: Albuquerque Open 2027", day + 3600_000))
        assertTrue(!Texts.sameCall("Albuquerque Open 2027", day, "Santa Fe Small Works 2027", day))
        assertTrue(!Texts.sameCall("Albuquerque Open 2027", day, "Albuquerque Open 2027", day + 3 * 86_400_000L))
    }

    @Test fun draftsAreBuiltOnlyFromWhatTheArtistSaid() {
        val answers = mapOf("what" to "drawings of the kitchen at 5am", "why" to "It is the only quiet hour I get", "how" to "", "words" to "I draw where I work.")
        assertEquals("I draw where I work. Drawings of the kitchen at 5am. It is the only quiet hour I get.", Drafts.statement(answers, 150))
        assertTrue(Texts.words(Drafts.statement(answers, 8)) <= 8)
        assertEquals("", Drafts.statement(emptyMap(), 150))
        val facts = listOf(com.emotionalleaves.studioagent.data.Fact(category = "education", year = 2020, text = "BFA, State University"),
            com.emotionalleaves.studioagent.data.Fact(category = "group", year = 2026, text = "Winter Open, Riverside"))
        assertEquals("Jade Leaves is an artist based in Austin, working in digital prints. Education: BFA, State University. Jade's work has been shown in Winter Open, Riverside (2026).",
            Drafts.bio("Jade", "Leaves", "Austin", "digital prints", facts, false, 150))
        assertEquals("I am an artist.", Drafts.bio("Jade", "", "", "", emptyList(), true, 40))
    }

    @Test fun aSeriesIsReadyWhenEveryCheckPasses() {
        fun piece(w: Double, stage: Int = 5, price: Double = 100.0) = com.emotionalleaves.studioagent.data.Artwork(title = "A", year = 2026, widthIn = w, heightIn = w * 1.25, price = price, format = "print", pixelW = 4800, pixelH = 6000, printStage = stage)
        val good = List(3) { piece(16.0) }
        assertTrue(Readiness.series(good, 3, true).all { it.second })
        assertFalse(Readiness.series(good, 10, true).first().second)
        assertFalse(Readiness.series(good, 3, false).last().second)
        assertTrue(Readiness.series(good + piece(40.0), 3, true).any { it.first.startsWith("Sizes belong together") && !it.second })
        assertTrue(Readiness.series(good + piece(16.0, stage = 2), 3, true).any { it.first.contains("printed and framed") && !it.second })
        assertTrue(Readiness.series(good + piece(16.0, price = 0.0), 3, true).any { it.first.contains("price") && !it.second })
        assertEquals(5, Readiness.printSteps.size)
    }

    @Test fun smallStepsRoundTrip() {
        val steps = Texts.steps("[x] Open the file\n[ ] Block in shapes\nAdd the light\n\n")
        assertEquals(3, steps.size)
        assertTrue(steps[0].first); assertFalse(steps[1].first); assertFalse(steps[2].first)
        assertEquals("Block in shapes", Texts.firstOpenStep(Texts.stepsText(steps)))
        assertEquals("", Texts.firstOpenStep("[x] Only step"))
        assertEquals("Strong fit", Texts.fitWord(80)); assertEquals("Weak fit", Texts.fitWord(10))
    }

    @Test fun yourOwnBannedWordsAreCaught() {
        assertTrue(Texts.jargonIn("It was a journey.", listOf("Journey")).contains("journey"))
    }

    @Test fun sameCallFromTwoSitesIsOneCall() {
        val a = Texts.dedupeKey("Riverside Arts Center", 1_800_000_000_000L, "Winter Open: Small Works 2026")
        val b = Texts.dedupeKey("riverside arts center", 1_800_000_000_000L + 3600_000, "WINTER OPEN - small works 2026")
        assertEquals(a, b)
    }

    @Test fun timesReadTheWayPeopleTypeThem() {
        val t = { x: String -> Planner.timeOrNull(x)?.toString() }
        assertEquals("17:30", t("5:30 pm")); assertEquals("17:00", t("5pm")); assertEquals("17:30", t("1730"))
        assertEquals("09:05", t("9:05")); assertEquals("17:30", t("17.30")); assertEquals("00:00", t("12am")); assertEquals("12:00", t("12 pm"))
        assertEquals(null, t("25:00")); assertEquals(null, t("soon")); assertEquals(null, t("13pm"))
    }

    @Test fun aFlyerReadOnThePhoneGetsItsRealNameAndLink() {
        val words = "Words read from a photo of a flyer or poster:\nOPEN CALL\nRiverside Print Biennial\nDeadline: March 3, 2027\nEntry fee: \$25 for up to 3 works\nOpen to artists in Ohio\nApply: riversideprints.org/call"
        val o = com.emotionalleaves.studioagent.domain.CallReader.read(words, java.time.LocalDate.of(2027, 1, 10), java.time.ZoneOffset.UTC, "Flyer photo", 0L)
        assertEquals("Riverside Print Biennial", o.title)
        assertEquals("https://riversideprints.org/call", o.url)
        assertEquals(25.0, o.fee, 0.0)
        assertEquals("2027-03-03", java.time.Instant.ofEpochMilli(o.deadline).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())
        // A real name that happens to start like a deadline line is kept.
        assertEquals("Due North", com.emotionalleaves.studioagent.domain.CallReader.titleLine("Due North\nDeadline: May 1, 2027"))
        // An email address is not mistaken for a link.
        assertEquals("", com.emotionalleaves.studioagent.domain.CallReader.read("Send to entries@riverside.org", java.time.LocalDate.of(2027, 1, 10), java.time.ZoneOffset.UTC, "x", 0L).url)
    }

    @Test fun aResultEmailIsMatchedToTheOrganizersSite() {
        assertTrue(Texts.sameSite("news.riverside.org", "https://www.riverside.org/call"))
        assertTrue(Texts.sameSite("riverside.org", "riverside.org"))
        assertFalse(Texts.sameSite("gmail.com", "https://gmail.com"))
        assertFalse(Texts.sameSite("other.org", "https://riverside.org/call"))
        assertFalse(Texts.sameSite("riverside.org", ""))
    }

    @Test fun errorsReadAsPlainWords() {
        val off = com.emotionalleaves.studioagent.ui.plainError(java.net.UnknownHostException("Unable to resolve host \"api.anthropic.com\""))
        assertTrue(off.startsWith("No internet connection"))
        val slow = com.emotionalleaves.studioagent.ui.plainError(java.net.SocketTimeoutException("timeout"))
        assertTrue(slow.contains("too long"))
        val key = com.emotionalleaves.studioagent.ui.plainError(Exception("HTTP 401: invalid x-api-key"))
        assertTrue(key.contains("not accepted"))
        val raw = com.emotionalleaves.studioagent.ui.plainError(IllegalStateException("java.lang.IllegalStateException: closed"))
        assertTrue(raw.startsWith("Something went wrong"))
        assertEquals("Could not find a call on that page.", com.emotionalleaves.studioagent.ui.plainError(Exception("Could not find a call on that page.")))
    }

    @Test fun starterDraftsLeaveBlanks() {
        val o = com.emotionalleaves.studioagent.data.Opp(title = "Winter Open", theme = "night")
        val fit = com.emotionalleaves.studioagent.ui.Starter.fit(listOf("Night Shift", "Walk-in Cooler"), o, "I draw the kitchen at five. The light is blue.")
        assertTrue(fit.contains("\"Night Shift\" and \"Walk-in Cooler\" belong together."))
        assertTrue(fit.contains("I draw the kitchen at five."))
        assertTrue(fit.contains("["))
        assertEquals("[Your answer, in a sentence or two]", com.emotionalleaves.studioagent.ui.Starter.answer(""))
    }

    @Test fun everyScreenBelongsToOneTab() {
        val tabs = listOf("home", "works", "calls", "more")
        val expect = mapOf("home" to "home", "session" to "home", "week" to "home", "admin" to "home", "works" to "works", "work/3" to "works", "ideas" to "works",
            "calls" to "calls", "call/2" to "calls", "appl/4" to "calls", "sources" to "calls", "outreach" to "more", "contact/1" to "more", "letters" to "more",
            "more" to "more", "materials" to "more", "guide" to "more", "settings/ai" to "more", "records" to "more", "receipts" to "more", "basics" to "more", "tour" to "more")
        expect.forEach { (route, tab) -> assertEquals(route, tab, com.emotionalleaves.studioagent.ui.tabFor(route, tabs)) }
    }
}
