package com.emotionalleaves.studioagent

import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.domain.CallReader
import com.emotionalleaves.studioagent.domain.CareerStats
import com.emotionalleaves.studioagent.domain.Drafts
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Profile
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
        assertEquals("Leaves_BlueHour_2026_Archivalpigmentprint_20x16in.jpg", Texts.fileName("Leaves", "Blue Hour", 2026, "Archival pigment print", 16.0, 20.0))
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
        assertEquals(0.0, CallReader.fee("There is no entry fee. Prize of $500."), 0.0)
        assertEquals("residency", CallReader.read("Artist residency, international", today, zone, "x", 1L).kind)
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
}
