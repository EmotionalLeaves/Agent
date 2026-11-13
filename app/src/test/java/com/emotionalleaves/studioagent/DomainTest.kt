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

    @Test fun aZeroFeeIsOnlyFreeWhenTheCallSaysSo() {
        val o = com.emotionalleaves.studioagent.data.Opp(title = "A", rawText = "Submit up to three works by May 1.")
        assertFalse(Texts.feeStated(o))
        assertTrue(Texts.feeStated(o.copy(rawText = "There is no entry fee.")))
        assertTrue(Texts.feeStated(o.copy(rawText = "Free to enter for all artists")))
        assertTrue(Texts.feeStated(o.copy(rawText = "")))
        assertTrue(Texts.feeStated(o.copy(fee = 25.0)))
    }

    @Test fun thePlanLearnsWhenYouReallyWork() {
        val today = java.time.LocalDate.of(2027, 3, 1) // a Monday
        val plan = listOf(com.emotionalleaves.studioagent.domain.TemplateSlot(3, java.time.LocalTime.of(17, 30), 40), com.emotionalleaves.studioagent.domain.TemplateSlot(6, java.time.LocalTime.of(10, 0), 90))
        // Four Saturdays at 10 (planned) and three Sundays around 7 pm (not planned); Wednesday never.
        val sat = (1..4).map { today.minusDays(2 + 7L * (it - 1)).atTime(10, 5) }
        val sun = (1..3).map { today.minusDays(1 + 7L * (it - 1)).atTime(19, 10) }
        val (from, to) = Planner.betterSlot(plan, sat + sun, today)!!
        assertEquals(3, from.day)
        assertEquals(7, to.day); assertEquals(java.time.LocalTime.of(19, 0), to.time)
        assertEquals(null, Planner.betterSlot(plan, sat, today))
    }

    @Test fun aPrintGetsTheReadyMadeFrameThatFits() {
        val f = com.emotionalleaves.studioagent.domain.Frames
        assertEquals(11.0 to 14.0, f.fit(7.0, 10.0))
        assertEquals(20.0 to 16.0, f.fit(12.0, 9.0))
        assertEquals(null, f.fit(40.0, 50.0))
        assertTrue(f.advice(8.0, 10.0).contains("12 × 16 in frame") && f.advice(8.0, 10.0).contains("7.75 × 9.75 in"))
    }

    @Test fun aCallOnlyForOtherStatesIsHeldBack() {
        val e = com.emotionalleaves.studioagent.domain.Eligible
        assertEquals("Ohio", e.onlyFor("Open to artists living in Ohio. Entry fee $25.", "TX"))
        assertEquals(null, e.onlyFor("Open to artists living in Ohio or Texas.", "Texas"))
        assertEquals(null, e.onlyFor("Riverside Gallery, 12 Main St, Columbus, Ohio. Open to all artists.", "TX"))
        assertEquals("California", e.onlyFor("California residents only.", "tx"))
        assertEquals(null, e.onlyFor("Open to artists living in Ohio.", ""))
    }

    @Test fun aPastedCvBecomesLinesUnderTheirHeadings() {
        val cv = "EDUCATION\n2018 BFA, State University, Austin\n\nSelected Group Exhibitions\n2024 Small Works, Riverside Arts, Austin\n- 2023 Night Market, Pop-up, Dallas\nAwards\n2022 Juror's Mention, Winter Open"
        val f = com.emotionalleaves.studioagent.domain.CvImport.read(cv)
        assertEquals(listOf("education", "group", "group", "award"), f.map { it.category })
        assertEquals(listOf(2018, 2024, 2023, 2022), f.map { it.year })
        assertEquals("Small Works, Riverside Arts, Austin", f[1].text)
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

    @Test fun seriesPiecesAreReadAndPlanned() {
        val p = com.emotionalleaves.studioagent.domain.Planner
        val got = p.pieceLines("1. Walk-in Cooler | The cooler door at 6am, steam on the glass.\n- Prep Table - Knives laid out before service, from above.\nHere are some ideas:\n3) Dish Pit: The sink stacked high after lunch rush.")
        assertEquals(listOf("Walk-in Cooler", "Prep Table", "Dish Pit"), got.map { it.first })
        assertTrue(got[1].second.startsWith("Knives"))
        val v = p.seriesVariations("The kitchen", 5)
        assertEquals(5, v.size); assertTrue(v[0].second.startsWith("The kitchen"))
    }

    @Test fun feedbackPicksAreCounted() {
        val F = com.emotionalleaves.studioagent.domain.Feedback
        var v = ""
        v = F.addVote(v, 7); v = F.addVote(v, 9); v = F.addVote(v, 9)
        assertEquals(mapOf(7L to 1, 9L to 2), F.votes(v))
        assertEquals(listOf(9L, 7L, 3L), F.byVotes(listOf(3L, 7L, 9L), v))
        assertEquals("Dish Pit was picked by 2 of 3 people.", F.summary(listOf(3L, 7L, 9L), mapOf(9L to "Dish Pit"), v))
        assertTrue(F.summary(listOf(7L, 9L), emptyMap(), F.addVote(v, 7)).startsWith("No clear favorite"))
        val m = F.message("Jade", listOf("Cooler", "Prep"), F.defaultQuestions)
        assertTrue(m.contains("1. Cooler\n2. Prep") && m.endsWith("Jade") && m.contains("3) Does anything"))
    }

    @Test fun theYearAheadCountsAndLists() {
        val today = java.time.LocalDate.of(2027, 1, 10)
        val plan = com.emotionalleaves.studioagent.domain.Planner.yearPlan(today, 6, 0.5, 1,
            calls = listOf(java.time.LocalDate.of(2027, 2, 14) to "Valentine Small Works"),
            again = listOf(java.time.LocalDate.of(2027, 3, 1) to "Spring Open"),
            plans = listOf(java.time.LocalDate.of(2027, 4, 20) to "Opening at Gallery 4"))
        assertEquals(12, plan.size)
        assertEquals(java.time.YearMonth.of(2027, 1), plan[0].first)
        // 6 now, half a piece a week: about 8 by late February, crossing ten in March.
        assertTrue(plan[1].second.any { it == "Closing: Valentine Small Works" })
        assertTrue(plan[2].second.any { it.startsWith("Ten pieces") } && plan[2].second.any { it == "Likely to open again: Spring Open" })
        assertTrue(plan[3].second.any { it == "20 Apr: Opening at Gallery 4" })
        assertTrue(plan[0].second.any { it.startsWith("Focus: one local") })
        assertTrue(plan.drop(4).none { l -> l.second.any { it.startsWith("Ten pieces") } })
    }

    @Test fun theUsualSchemesAreNamed() {
        val O = com.emotionalleaves.studioagent.domain.Offers
        val buyer = O.check("Hello, I came across your work and love it. I will send a cashier's check for $1,800, please refund the difference to my shipper who will pick up the painting.")
        assertTrue(buyer.count { it.serious } >= 2)
        assertEquals("Be careful with this one.", O.verdict(buyer))
        val mag = O.check("Our editors noticed your portfolio! Be featured in Art Visionaries magazine, a two-page spread for just $350. Only 3 spots left, reply within 48 hours.")
        assertTrue(mag.any { it.what.startsWith("Paying to be in a magazine") } && mag.any { it.what.startsWith("Pressure") })
        val venice = O.check("Exhibit in Venice during the Biennale! Participation fee 900 euros includes wall space and a catalog.")
        assertTrue(venice.any { it.serious })
        val fine = O.check("Hi Jade, thanks for stopping by the library gallery on Saturday. Our spring community show takes work from local artists; the form is on our website. No fee.")
        assertTrue(fine.isEmpty())
        assertTrue(O.verdict(fine).startsWith("Nothing here"))
    }

    @Test fun receiptsAreRead() {
        val R = com.emotionalleaves.studioagent.domain.Receipts
        val blick = R.read("BLICK ART MATERIALS\nStore #123 Austin TX\nGolden Heavy Body 2oz 12.99\nCanvas pad 9x12 8.49\nSUBTOTAL 21.48\nTAX 1.77\nTOTAL 23.25\nVISA 23.25")
        assertEquals(23.25, blick.total, 0.001); assertEquals("BLICK ART MATERIALS", blick.shop); assertEquals("materials", blick.kind)
        val ups = R.read("The UPS Store\nShipping 1 package\nTotal\n$ 41.30")
        assertEquals(41.30, ups.total, 0.001); assertEquals("shipping", ups.kind)
        assertEquals("framing", R.read("Main St Custom Framing\nMat board, glass\nAmount due 85.00").kind)
        assertEquals(1234.56, R.read("Print shop\nGiclee prints 1,234.56").total, 0.001)
    }

    @Test fun shippingIsWorkedOut() {
        val S = com.emotionalleaves.studioagent.domain.Shipping
        val b = S.box(16.0, 20.0, 0.0, framed = true)
        assertEquals(Triple(28, 24, 10), b)
        val lb = S.weightLb(16.0, 20.0, framed = true, canvas = false)
        assertTrue(lb in 9.0..12.0)
        assertEquals(49, S.billableLb(b, lb))
        val d = java.time.LocalDate.of(2027, 3, 20)
        val steps = S.steps(d, java.time.LocalDate.of(2027, 3, 1))
        assertEquals(d.minusDays(16), steps.first().second)
        assertTrue(steps.any { it.first.startsWith("Ship it") && it.second == d.minusDays(10) })
        // Already late: nothing is dated in the past.
        assertTrue(S.steps(d, java.time.LocalDate.of(2027, 3, 15)).all { !it.second.isBefore(java.time.LocalDate.of(2027, 3, 15)) })
    }

    @Test fun readyToHangTicks() {
        val H = com.emotionalleaves.studioagent.domain.HangReady
        assertTrue(H.items("print", false).any { it.startsWith("Certificate") })
        assertTrue(H.items("physical", false).none { it.startsWith("Frame") })
        var s = H.set("", 5, 0, true); s = H.set(s, 5, 2, true); s = H.set(s, 9, 1, true)
        assertEquals(5, H.bits(s, 5)); assertEquals(2, H.bits(s, 9)); assertEquals(0, H.bits(s, 50))
        s = H.set(s, 5, 0, false); assertEquals(4, H.bits(s, 5))
    }

    @Test fun whatAVenueShows() {
        val P = com.emotionalleaves.studioagent.domain.Planner
        val shown = P.mediaShown("Our gallery shows painting and drawing by local artists. Past shows: paintings of the river; charcoal drawings; a painting class. Photography night once.")
        assertEquals(listOf("painting", "drawing"), shown)
        assertTrue(P.mediaMatch(shown, "gouache, pencil"))
        assertFalse(P.mediaMatch(listOf("ceramics", "sculpture"), "acrylic, digital"))
        assertTrue(P.mediaMatch(listOf("digital art"), "digital, archival print"))
    }

    @Test fun foundationsMoveOn() {
        val F = com.emotionalleaves.studioagent.domain.Foundations
        assertEquals(18, F.weeks.size); assertTrue(F.weeks.all { it.exercises.size == 3 })
        assertEquals("1a", F.next("")!!.id)
        var s = F.mark("", "1a"); s = F.mark(s, "1b"); s = F.mark(s, "1c")
        assertEquals(2, F.current(s)!!.n); assertEquals("2a", F.next(s)!!.id)
        s = F.mark(s, "10a"); s = F.mark(s, "2a")
        assertEquals("1a,1b,1c,2a,10a", s)
        assertEquals("2b", F.next(s)!!.id)
        val all = F.weeks.flatMap { it.exercises }.fold("") { acc, e -> F.mark(acc, e.id) }
        assertEquals(null, F.current(all))
    }

    @Test fun aGroupShowReadsTheirList() {
        val G = com.emotionalleaves.studioagent.domain.GroupShow
        val p = G.pieces("Night Shift, gouache on paper, 9 x 12 in, $250\n- Prep Table, acrylic, 16x20, NFS\nUntitled sketch")
        assertEquals(listOf("Night Shift", "Prep Table", "Untitled sketch"), p.map { it.title })
        assertEquals("gouache on paper", p[0].medium); assertEquals(9.0, p[0].widthIn, 0.0); assertEquals(12.0, p[0].heightIn, 0.0); assertEquals(250.0, p[0].price, 0.0)
        assertTrue(p[1].nfs); assertEquals(16.0, p[1].widthIn, 0.0); assertEquals("acrylic", p[1].medium)
        assertEquals(40.0, G.share(120.0, 3), 0.001)
        assertTrue(G.invite("Jade", "Night Kitchen", "Central Library", "May 2027", 3).contains("alongside 2 other artists"))
    }

    @Test fun aMarketDayAddsUp() {
        val M = com.emotionalleaves.studioagent.domain.Market
        val items = M.starter
        assertEquals(items, M.items(M.itemsText(items)))
        var t = ""
        t = M.bump(t, "m:Cards", 1); t = M.bump(t, "m:Cards", 1); t = M.bump(t, "m:Small prints (5 x 7)", 1); t = M.bump(t, "w:12", 1)
        t = M.bump(t, "m:Cards", -1)
        assertEquals(mapOf("m:Cards" to 1, "m:Small prints (5 x 7)" to 1, "w:12" to 1), M.tally(t))
        assertEquals(5.0 + 15.0 + 300.0, M.total(M.tally(t), items, mapOf(12L to 300.0)), 0.001)
        assertTrue(M.tally(M.bump(t, "w:12", -1)).keys.none { it == "w:12" })
    }

    @Test fun pricesHoldTogether() {
        val P = com.emotionalleaves.studioagent.domain.Pricing
        assertEquals(85.0, P.round(83.0), 0.0); assertEquals(460.0, P.round(457.0), 0.0); assertEquals(1350.0, P.round(1340.0), 0.0)
        assertEquals(330.0, P.raised(300.0, 10.0), 0.0)
        val a = com.emotionalleaves.studioagent.data.Artwork(id = 1, title = "A", widthIn = 9.0, heightIn = 12.0, price = 400.0, status = "documented")
        val b = com.emotionalleaves.studioagent.data.Artwork(id = 2, title = "B", widthIn = 9.0, heightIn = 12.0, price = 160.0, status = "documented")
        val s = P.strays(listOf(a, b), 1.5)
        assertEquals(listOf(1L), s.map { it.first.id }); assertEquals(160.0, s[0].second, 0.0)
    }

    @Test fun aPictureIsDescribed() {
        val t = com.emotionalleaves.studioagent.domain.Drafts.altText("Walk-in Cooler", "Gouache on paper", "9 × 12 in", listOf("mid blue", "very light yellow", "dark gray"), "with strong darks and lights", "The cooler door at six in the morning, steam on the glass. Done in two sittings.")
        assertEquals("A gouache on paper, 9 × 12 in, titled Walk-in Cooler. The cooler door at six in the morning, steam on the glass. Mostly mid blue, very light yellow and dark gray, with strong darks and lights.", t)
        assertTrue(com.emotionalleaves.studioagent.domain.Drafts.altText("", "oil", "", emptyList(), "", "").startsWith("An oil."))
    }


    @Test fun goodEnoughUsesYourOwnNumbers() {
        val G = com.emotionalleaves.studioagent.domain.GoodEnough
        assertEquals(0, G.usual(listOf(4)))
        assertEquals(5, G.usual(listOf(3, 5, 8)))
        assertFalse(G.overdue(9, 5)); assertTrue(G.overdue(10, 5))
        assertTrue(G.overdue(5, 2)); assertFalse(G.overdue(4, 2))
        assertFalse(G.overdue(50, 0))
    }

    @Test fun aProfileLine() {
        val l = com.emotionalleaves.studioagent.domain.Drafts.profileLine("gouache, pencil, ink", "Austin, TX", "I draw the kitchen at five in the morning. It is quiet then.")
        assertEquals("Artist in Austin. Gouache and pencil. I draw the kitchen at five in the morning. Shows and news below.", l)
        assertTrue(l.length <= 150)
    }

    @Test fun anEntryInFifteenMinuteEvenings() {
        val E = com.emotionalleaves.studioagent.domain.EntrySteps
        val today = java.time.LocalDate.of(2027, 1, 4) // a Monday
        val d = E.days(today, java.time.LocalDate.of(2027, 1, 22), setOf(1, 2, 3, 4, 5))
        assertEquals(5, d.size)
        assertTrue(d.all { it.dayOfWeek.value in 1..5 && it.isAfter(today) && !it.isAfter(java.time.LocalDate.of(2027, 1, 22)) })
        assertEquals(d, d.sorted())
        // Two days left: the steps share them.
        val tight = E.days(today, today.plusDays(2), setOf(1, 2, 3, 4, 5))
        assertTrue(tight.all { it == today.plusDays(1) || it == today.plusDays(2) })
    }

    @Test fun moneyByMonth() {
        fun d(y: Int, mo: Int, day: Int) = java.time.LocalDate.of(y, mo, day)
        val m = com.emotionalleaves.studioagent.domain.MoneyByMonth.of(2027, listOf(d(2027, 3, 2) to 200.0, d(2027, 3, 9) to -35.0, d(2027, 3, 20) to 50.0, d(2026, 3, 1) to 999.0, d(2027, 12, 1) to -10.0))
        assertEquals(12, m.size)
        assertEquals(250.0 to 35.0, m[2]); assertEquals(0.0 to 10.0, m[11]); assertEquals(0.0 to 0.0, m[0])
    }

    @Test fun nearCallsComeFirst() {
        val S = com.emotionalleaves.studioagent.domain.Scoring
        val near = com.emotionalleaves.studioagent.data.Opp(title = "Near", city = "Round Rock, TX", scope = "local")
        val far = com.emotionalleaves.studioagent.data.Opp(title = "Far", city = "Portland, OR", scope = "local")
        val p = com.emotionalleaves.studioagent.domain.Profile(level = 1, milesTo = mapOf("round rock" to 20, "portland" to 1700))
        val (a, ra) = S.score(near, p); val (b, rb) = S.score(far, p)
        assertTrue(a > b)
        assertTrue(ra.contains("About 20 miles from you")); assertTrue(rb.any { it.startsWith("About 1700 miles away") })
        // Unknown towns change nothing.
        assertEquals(S.score(near, com.emotionalleaves.studioagent.domain.Profile(level = 1)).first + 8, a)
    }

    @Test fun showTitlesWithoutHelp() {
        val t = com.emotionalleaves.studioagent.domain.Drafts.showTitles("Night Kitchen", "I draw the kitchen before the first shift starts. It is quiet then.", "Walk-in Cooler")
        assertEquals(listOf("Night Kitchen", "Before the first shift starts", "Walk-in Cooler and Other Pieces", "New Work"), t)
    }

    @Test fun yourThemesComeForward() {
        val S = com.emotionalleaves.studioagent.domain.Scoring
        val o = com.emotionalleaves.studioagent.data.Opp(title = "Kitchen Stories: an open call", scope = "local")
        val plain = S.score(o, com.emotionalleaves.studioagent.domain.Profile(level = 1)).first
        val (score, why) = S.score(o, com.emotionalleaves.studioagent.domain.Profile(level = 1, themes = listOf("kitchens", "night")))
        assertEquals(plain + 6, score); assertTrue(why.contains("About kitchens: one of your themes"))
        assertEquals(plain, S.score(o, com.emotionalleaves.studioagent.domain.Profile(level = 1, themes = listOf("ocean"))).first)
    }

    @Test fun themeWordsFromAStatement() {
        val w = com.emotionalleaves.studioagent.domain.Drafts.themeWords("I paint kitchens at night. The kitchen before the shift, steam and steel, night after night. Kitchens are where I work.")
        assertEquals(listOf("kitchen", "night", "shift"), w.take(3))
    }

    @Test fun datesAsACalendarFile() {
        val ms = java.time.LocalDateTime.of(2027, 3, 1, 18, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val f = com.emotionalleaves.studioagent.domain.Ics.file(listOf(Triple("evt1", "Deliver work; Gallery 4, Austin", ms to 0L)), now = ms)
        assertTrue(f.startsWith("BEGIN:VCALENDAR\r\n") && f.endsWith("END:VCALENDAR\r\n"))
        assertTrue(f.contains("DTSTART:20270301T180000Z") && f.contains("DTEND:20270301T190000Z"))
        assertTrue(f.contains("SUMMARY:Deliver work\\; Gallery 4\\, Austin"))
    }

    @Test fun longSentencesAreFound() {
        val long = "I make paintings of the kitchen where I work at the university before the breakfast shift starts and the steam comes off the dish machine and the cooks arrive one by one and the lights come up. Short one."
        assertEquals(listOf("I make paintings of the kitchen..."), com.emotionalleaves.studioagent.domain.Texts.longSentences(long))
        assertTrue(com.emotionalleaves.studioagent.domain.Texts.longSentences("Short. Also short.").isEmpty())
    }

    @Test fun hoursByWeek() {
        val today = java.time.LocalDate.of(2027, 3, 10) // a Wednesday
        val h = com.emotionalleaves.studioagent.domain.HoursByWeek.of(today, listOf(java.time.LocalDate.of(2027, 3, 8) to 1.5, java.time.LocalDate.of(2027, 3, 7) to 2.0, java.time.LocalDate.of(2027, 1, 1) to 9.0), 3)
        assertEquals(listOf(0.0, 2.0, 1.5), h)
    }

    @Test fun theNextTitleInASeries() {
        val T = com.emotionalleaves.studioagent.domain.Texts
        assertEquals("Night Shift II", T.nextTitle("Night Shift")); assertEquals("Night Shift III", T.nextTitle("Night Shift II"))
        assertEquals("Study 4", T.nextTitle("Study 3")); assertEquals("Untitled", T.nextTitle(" "))
    }

    @Test fun aVenueTimeline() {
        val T = com.emotionalleaves.studioagent.domain.Timeline
        val t = T.of("Visited on 2027-02-03. Liked the back room.\nWrote again from Mon, Mar 1, 2027.", listOf(java.time.LocalDate.of(2027, 4, 9) to "Opening at Gallery 4"))
        assertEquals(listOf("Opening at Gallery 4", "Wrote again from Mon, Mar 1, 2027.", "Visited on 2027-02-03."), t.map { it.second })
    }

    @Test fun aPastedListOfPlaces() {
        val r = com.emotionalleaves.studioagent.domain.VenueList.read("Central Library, art@library.example.org, library.example.org\n- Bean There Cafe - www.beanthere.com\nhello@gallery4.art\n\nx")
        assertEquals(3, r.size)
        assertEquals(com.emotionalleaves.studioagent.domain.VenueList.Row("Central Library", "art@library.example.org", "library.example.org"), r[0])
        assertEquals("Bean There Cafe", r[1].name); assertEquals("www.beanthere.com", r[1].website)
        assertEquals("hello", r[2].name); assertEquals("hello@gallery4.art", r[2].email); assertEquals("", r[2].website)
    }

    @Test fun aDistanceLimitHoldsFarCallsBack() {
        val S = com.emotionalleaves.studioagent.domain.Scoring
        val far = com.emotionalleaves.studioagent.data.Opp(title = "Far", city = "Portland, OR", scope = "local")
        val p = com.emotionalleaves.studioagent.domain.Profile(level = 1, milesTo = mapOf("portland" to 1700), maxMiles = 150)
        assertTrue(S.ineligible(far, p, 0).contains("About 1700 miles away, past your 150"))
        assertTrue(S.ineligible(far.copy(online = true), p, 0).none { it.contains("miles") })
        assertTrue(S.ineligible(far, p.copy(maxMiles = 0), 0).none { it.contains("miles") })
    }

    @Test fun anOpeningPosterIsRead() {
        val today = java.time.LocalDate.of(2027, 3, 1)
        val r = com.emotionalleaves.studioagent.domain.CallReader.openingFrom("SMALL HOURS\nNew paintings by Ana Ruiz\nOpening reception: Friday, March 12, 2027, 6-8pm\nGallery 4", today)
        assertEquals(java.time.LocalDate.of(2027, 3, 12), r?.second)
        assertTrue(r!!.first.isNotBlank())
        assertEquals(null, com.emotionalleaves.studioagent.domain.CallReader.openingFrom("Opening reception: January 3, 2027", today))
    }

    @Test fun aHeadIsEmptiedIntoTheRightPlaces() {
        val D = com.emotionalleaves.studioagent.domain.Dump
        val fri = java.time.LocalDate.of(2026, 11, 13) // a Friday
        val lines = D.sort("- email the library about the wall\nbuy titanium white\nfox in the snow, all blue\nfinish the small heron by monday\ndon't forget to pay the entry fee tomorrow", fri)
        assertEquals(5, lines.size)
        assertEquals(com.emotionalleaves.studioagent.domain.Dump.Kind.TASK, lines[0].kind); assertEquals(null, lines[0].day)
        assertEquals(com.emotionalleaves.studioagent.domain.Dump.Kind.SUPPLY, lines[1].kind); assertEquals("Titanium white", lines[1].text)
        assertEquals(com.emotionalleaves.studioagent.domain.Dump.Kind.IDEA, lines[2].kind)
        assertEquals(java.time.LocalDate.of(2026, 11, 16), lines[3].day)
        assertEquals("Pay the entry fee tomorrow", lines[4].text); assertEquals(fri.plusDays(1), lines[4].day)
        assertEquals(fri.plusDays(7), D.dayIn("frame it friday", fri))
    }

    @Test fun aClassAddsUpAndIsPricedFairly() {
        val W = com.emotionalleaves.studioagent.domain.Workshop
        assertEquals("watercolor", W.kindOf("Watercolor on paper"))
        assertEquals("phone drawing", W.kindOf("digital, archival print"))
        assertEquals("drawing", W.kindOf("graphite"))
        listOf(45, 60, 90, 120, 180).forEach { m -> assertEquals(m, W.outline("drawing", m).sumOf { it.minutes }) }
        assertTrue(W.supplies("drawing", 10).first().startsWith("12 "))
        val (flat, per) = W.fee(90, 10)
        assertEquals(80.0, flat, 0.01) // two hours at 40, set-up included
        assertTrue(per >= 10.0 && per % 5.0 == 0.0)
        val mail = W.email("Jade Leaves", "Eastside Library", "Drawing what is in front of you", 90, 10, "", "$80")
        assertTrue(mail.contains("90 minutes") && mail.contains("Eastside Library") && mail.contains("$80"))
        assertTrue(W.email("J", "", "T", 120, 8, "").contains("about 2 hours"))
    }

    @Test fun aNoCountsTowardTheYear() {
        val A = com.emotionalleaves.studioagent.domain.Attempts
        val t = A.afterNo(9, 3, 1, 24)
        assertTrue(t.startsWith("Your 3rd no this year, from 9 sends and 1 yes."))
        assertTrue(t.contains("15 sends to go toward your 24"))
        assertTrue(A.afterNo(30, 11, 4, 24).startsWith("Your 11th no") && A.afterNo(30, 11, 4, 24).contains("already sent all 24"))
        assertEquals("sent this year, aiming for 24", A.tile(3, 24))
    }

    @Test fun findingYourPeople() {
        val P = com.emotionalleaves.studioagent.domain.People
        assertEquals(5, P.places.map { it.key }.toSet().size)
        assertEquals("drop-in life drawing Austin", P.query(P.places.first(), "Austin"))
        val h = P.hello("Ana", "Tuesday's life drawing", "Jade")
        assertTrue(h.startsWith("Hi Ana, it was good to draw next to you at Tuesday's life drawing.") && h.endsWith("Jade"))
        assertTrue(P.hello("", "", "Jade").startsWith("Hi there, it was good to draw next to you."))
    }

    @Test fun anAnswerWrittenOnceIsOfferedAgain() {
        val B = com.emotionalleaves.studioagent.domain.AnswerBank
        val past = listOf(
            B.Past("Tell us about your practice (100 words)", "I draw the kitchen at five in the morning.", "Winter Open"),
            B.Past("What are your influences?", "Degas's laundresses.", "Spring Show"),
            B.Past("Describe your artwork", "", "Blank one"))
        assertEquals("Winter Open", B.best("Describe your work", past)?.from)
        assertEquals("Spring Show", B.best("Who or what inspires you?", past)?.from)
        assertEquals(null, B.best("Will you need help hanging?", past))
    }
}
