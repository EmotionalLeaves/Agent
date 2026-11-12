package com.emotionalleaves.studioagent

import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.domain.CareerStats
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Profile
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

    @Test fun sameCallFromTwoSitesIsOneCall() {
        val a = Texts.dedupeKey("Riverside Arts Center", 1_800_000_000_000L, "Winter Open: Small Works 2026")
        val b = Texts.dedupeKey("riverside arts center", 1_800_000_000_000L + 3600_000, "WINTER OPEN - small works 2026")
        assertEquals(a, b)
    }
}
