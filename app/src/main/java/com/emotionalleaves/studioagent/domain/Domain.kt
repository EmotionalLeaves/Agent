package com.emotionalleaves.studioagent.domain

import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Opp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------- career ladder

data class CareerStats(
    val bestSeriesDocumented: Int = 0,
    val statementApproved: Boolean = false,
    val exhibitions: Int = 0,
    val awardOrInvited: Boolean = false,
    val solo: Int = 0,
    val residencyOrGrant: Boolean = false
)

data class Gate(val text: String, val progress: Float)

/** Every rule the artist can loosen or tighten. Defaults are the outline's ladder. */
data class Rules(
    val gateWorks: Int = 10,
    val gateShows1: Int = 3,
    val gateShows2: Int = 8,
    /** Fee cap per entry for levels 0 to 4. Negative means no cap. */
    val feeCaps: List<Double> = listOf(0.0, 25.0, 40.0, -1.0, -1.0),
    /** 0 to 4 to set the level by hand, anything else to let the gates decide. */
    val levelOverride: Int = -1,
    val showAllLevels: Boolean = false,
    val gateOutreach: Boolean = true,
    /** Importance 1 (low) to 3 (high) of theme, career, money, odds, effort, goal. */
    val weights: List<Int> = listOf(2, 2, 2, 2, 2, 2)
)

object Levels {
    val names = listOf("Foundation", "First walls", "Regional", "Recognized", "Established")

    fun level(s: CareerStats, r: Rules = Rules()): Int = when {
        r.levelOverride in 0..4 -> r.levelOverride
        !(s.bestSeriesDocumented >= r.gateWorks && s.statementApproved) -> 0
        s.exhibitions < r.gateShows1 -> 1
        !(s.exhibitions >= r.gateShows2 && s.awardOrInvited) -> 2
        !(s.solo >= 1 && s.residencyOrGrant) -> 3
        else -> 4
    }

    fun gate(s: CareerStats, r: Rules = Rules()): Gate = when (level(s, r)) {
        0 -> {
            val want = r.gateWorks.coerceAtLeast(1)
            val n = min(s.bestSeriesDocumented, want)
            Gate(
                "$n of $want documented works in one series, statement " + (if (s.statementApproved) "approved" else "not approved yet"),
                n.toFloat() / want * 0.9f + (if (s.statementApproved) 0.1f else 0f)
            )
        }
        1 -> { val want = r.gateShows1.coerceAtLeast(1); Gate("${min(s.exhibitions, want)} of $want exhibitions on your CV", min(s.exhibitions, want).toFloat() / want) }
        2 -> { val want = r.gateShows2.coerceAtLeast(1); Gate(
            "${min(s.exhibitions, want)} of $want shows, " + (if (s.awardOrInvited) "award or invitation done" else "plus an award or an invited show"),
            min(s.exhibitions, want).toFloat() / want * 0.8f + (if (s.awardOrInvited) 0.2f else 0f)
        ) }
        3 -> Gate(
            (if (s.solo >= 1) "Solo show done" else "A solo show") + ", " + (if (s.residencyOrGrant) "residency or grant done" else "plus a residency or grant"),
            (if (s.solo >= 1) 0.5f else 0f) + (if (s.residencyOrGrant) 0.5f else 0f)
        )
        else -> Gate("Established", 1f)
    }

    /** Default fee cap per entry. */
    fun feeCap(level: Int, r: Rules = Rules()): Double {
        val c = r.feeCaps.getOrElse(level) { -1.0 }
        return if (c < 0) Double.MAX_VALUE else c
    }

    /** Highest call tier shown as a normal (non-stretch) call. */
    fun maxTier(level: Int): Int = if (level <= 0) 1 else min(level, 4)

    fun hoursAsk(level: Int): IntRange = when (level) { 0 -> 3..5; 1 -> 4..6; 2 -> 5..8; else -> 6..10 }

    fun outreachAllowed(level: Int, kind: String, r: Rules): Boolean = !r.gateOutreach || outreachAllowed(level, kind)

    fun outreachAllowed(level: Int, kind: String): Boolean = when (kind) {
        "subscriber" -> true
        "campus", "community" -> level >= 1
        "artist_run", "juror" -> level >= 2
        else -> level >= 3
    }

    fun outreachNeeds(kind: String): Int = (0..4).first { outreachAllowed(it, kind) }

    fun stats(facts: List<Fact>, bestSeriesDocumented: Int, statementApproved: Boolean) = CareerStats(
        bestSeriesDocumented = bestSeriesDocumented,
        statementApproved = statementApproved,
        exhibitions = facts.count { it.category == "solo" || it.category == "group" || it.category == "invited" },
        awardOrInvited = facts.any { it.category == "award" || it.category == "invited" },
        solo = facts.count { it.category == "solo" },
        residencyOrGrant = facts.any { it.category == "residency" || it.category == "grant" }
    )
}

// ---------------------------------------------------------------- matching

data class Profile(
    val level: Int = 0,
    /** Formats the artist shows: print, physical, screen. */
    val routes: List<String> = listOf("print"),
    val rules: Rules = Rules(),
    /** Kinds of call the artist wants to see. Empty means all. */
    val kinds: List<String> = emptyList(),
    val availableWorks: Int = 0,
    val feeBudgetLeft: Double = 0.0,
    val goals: String = "",
    val blocked: List<String> = emptyList(),
    val noReasons: Map<String, Int> = emptyMap(),
    val currency: String = "$",
    /** Organizers and venues that have shown your work: a second yes is far likelier than a first. */
    val friends: List<String> = emptyList(),
    /** Your state or region, as you typed it, for calls open only to artists in certain places. */
    val region: String = "",
    /** How far (miles) each call's town is from you, by town name in lower case, where the map knows it. */
    val milesTo: Map<String, Int> = emptyMap(),
    /** What your work is about, in your words ("kitchens, night, labor"): calls on those themes come forward. */
    val themes: List<String> = emptyList(),
    /** The farthest you will deliver or ship to, in miles (0: no limit). Online calls are never held back by it. */
    val maxMiles: Int = 0
)

/** An existing CV, pasted as text, read into CV lines: section headings set the kind, a year at the start of a line sets the year. */
object CvImport {
    private val headings = listOf(
        "solo" to Regex("solo", RegexOption.IGNORE_CASE), "group" to Regex("group|exhibition|shows?\\b", RegexOption.IGNORE_CASE),
        "education" to Regex("education|degree|training", RegexOption.IGNORE_CASE), "award" to Regex("award|prize|honou?r", RegexOption.IGNORE_CASE),
        "grant" to Regex("grant|fellowship", RegexOption.IGNORE_CASE), "residency" to Regex("residenc", RegexOption.IGNORE_CASE),
        "collection" to Regex("collection", RegexOption.IGNORE_CASE), "press" to Regex("press|review|bibliograph", RegexOption.IGNORE_CASE),
        "publication" to Regex("publication|published", RegexOption.IGNORE_CASE), "skill" to Regex("skill", RegexOption.IGNORE_CASE),
        "teaching" to Regex("teach|workshop|lecture|talks?\\b", RegexOption.IGNORE_CASE))

    fun read(text: String): List<Fact> {
        var kind = "group"
        val out = mutableListOf<Fact>()
        text.lines().map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.isNotBlank() }.forEach { line ->
            val year = Regex("^((19|20)\\d\\d)(\\s*[-–]\\s*((19|20)\\d\\d|present))?[\\s,.:]+").find(line)
            if (year == null && line.length < 40 && !line.contains(',')) {
                // A short line with no year and no comma reads as a section heading.
                headings.firstOrNull { it.second.containsMatchIn(line) }?.let { kind = it.first }
                return@forEach
            }
            val y = year?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val rest = if (year != null) line.substring(year.range.last + 1).trim() else line
            if (rest.length >= 4) out += Fact(category = kind, year = y, text = rest)
        }
        return out
    }
}

/** Where a call is open to, read from its words: "open to artists living in Ohio", "Texas residents only". */
object Eligible {
    val states = listOf("Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida", "Georgia", "Hawaii", "Idaho", "Illinois",
        "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska",
        "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island",
        "South Carolina", "South Dakota", "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming")
    private val abbr = mapOf("AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
        "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana",
        "ME" to "Maine", "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska",
        "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
        "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina", "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas",
        "UT" to "Utah", "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming")

    /** Your region as a full state name, from "TX", "Texas" or "texas". */
    fun stateOf(region: String): String? = region.trim().let { r -> abbr[r.uppercase()] ?: states.firstOrNull { it.equals(r, true) } }

    /**
     * When the call is open only to artists in named states and yours is not one of them, the places it is open to; otherwise null.
     * Only a clear limiting phrase counts (residents, living in, based in, open to artists in), so a venue's address alone never excludes you.
     */
    fun onlyFor(text: String, region: String): String? {
        val mine = stateOf(region) ?: return null
        val limit = Regex("(residents? of|resident artists of|living in|based in|reside in|residing in|open to artists (?:in|from)|artists (?:living|working|residing) in|open only to)\\s+([^.;]{0,120})", RegexOption.IGNORE_CASE)
        val named = limit.findAll(text).flatMap { m -> states.filter { st -> Regex("\\b" + st + "\\b").containsMatchIn(m.groupValues[2]) } }.toSet() +
            Regex("\\b(" + states.joinToString("|") + ") (?:residents|artists) only\\b").findAll(text).map { it.groupValues[1] }.toSet()
        if (named.isEmpty() || mine in named) return null
        return named.sorted().joinToString(", ")
    }
}

object Scoring {
    fun tier(o: Opp): Int = when (o.scope.lowercase().trim()) {
        "campus", "local", "online" -> 1
        "regional" -> 2
        "national", "international" -> 3
        else -> if (o.online) 1 else 2
    }

    fun isStretch(o: Opp, p: Profile): Boolean = tier(o) == Levels.maxTier(p.level) + 1

    fun trustFlags(o: Opp): List<String> {
        val f = mutableListOf<String>()
        if (o.guaranteed && o.fee > 0) f += "Pay-to-show: the fee buys a place, nothing is juried"
        if (o.rightsGrab) f += "Terms take broad rights to your images"
        if (o.jurors.isBlank()) f += "No named jurors"
        if (!o.online && o.venue.isBlank()) f += "No venue address"
        if (o.fee >= 50 && o.prizes.isBlank()) f += "High fee with no stated prize"
        return f
    }

    /** Hard rules. Empty list means the call can be shown. */
    fun ineligible(o: Opp, p: Profile, now: Long): List<String> {
        val r = mutableListOf<String>()
        if (o.deadline in 1..now) r += "Deadline has passed"
        if (!p.rules.showAllLevels && o.fee > Levels.feeCap(p.level, p.rules)) r += "Entry fee is over your Level ${p.level} cap"
        else if (o.fee > 0 && o.fee > p.feeBudgetLeft) r += "Over this month's fee budget"
        if (!p.rules.showAllLevels && tier(o) > Levels.maxTier(p.level) + 1) r += "Above your current level"
        if (p.kinds.isNotEmpty() && o.kind.isNotBlank() && o.kind !in p.kinds) r += "You have ${o.kind} calls turned off"
        if (o.minWorks > p.availableWorks) r += "Needs ${o.minWorks} works, you have ${p.availableWorks} ready"
        if (p.blocked.any { it.isNotBlank() && o.organiser.contains(it.trim(), ignoreCase = true) }) r += "Organizer is on your blocked list"
        if (p.maxMiles > 0 && !o.online) p.milesTo[o.city.substringBefore(",").trim().lowercase()]?.let { mi -> if (mi > p.maxMiles) r += "About $mi miles away, past your ${p.maxMiles}" }
        val fits = p.routes.isEmpty() || p.routes.any { it == "physical" || (it == "print" && o.acceptsPrints) || (it == "screen" && o.acceptsNewMedia) }
        if (!fits) r += if (p.routes == listOf("screen")) "Does not accept screen-based work" else "Does not accept prints of digital work"
        if (o.guaranteed && o.fee > 0) r += "Pay-to-show"
        Eligible.onlyFor(o.eligibility + " " + o.rawText.take(4000), p.region)?.let { r += "Open only to artists in $it" }
        return r
    }

    /** 0 to 100, with the reasons shown to the artist. */
    fun score(o: Opp, p: Profile): Pair<Int, List<String>> {
        val t = tier(o)
        val reasons = mutableListOf<String>()
        val theme = o.themeFit.coerceIn(0, 100)
        if (o.themeReason.isNotBlank()) reasons += "Theme: ${o.themeReason}" else if (theme >= 70) reasons += "Theme fits your work"

        var career = when {
            t == Levels.maxTier(p.level) -> 80
            isStretch(o, p) -> 90
            else -> 60
        }
        if (o.jurors.isNotBlank()) career = min(100, career + 10)
        if (isStretch(o, p)) reasons += "Stretch: one level above you"
        if (p.level <= 1 && t == 1) reasons += "A realistic first CV line"

        var money = if (o.fee <= 0.0) 100 else (100 - o.fee * 2).roundToInt().coerceAtLeast(20)
        if (o.prizes.isNotBlank()) money = min(100, money + 10)
        if ((p.noReasons["fee"] ?: 0) >= 3 && o.fee > 0) money = (money - 20).coerceAtLeast(0)
        reasons += if (o.fee > 0.0) "Fee ${p.currency}${"%.0f".format(o.fee)}" else if (Texts.feeStated(o)) "Free to enter" else "Fee not stated: check before paying anything"

        val odds = when (t) { 1 -> 80; 2 -> 60; else -> 40 }

        var effort = when {
            o.online -> 90
            t == 1 -> 80
            else -> 50
        }
        if ((p.noReasons["too far"] ?: 0) >= 3 && !o.online && t > 1) effort = (effort - 30).coerceAtLeast(0)
        if (o.online) reasons += "Online: nothing to ship"

        val goalWords = p.goals.lowercase().split(Regex("[^a-z]+")).filter { it.length > 3 }
        val hay = (o.title + " " + o.theme + " " + o.prizes + " " + o.eligibility).lowercase()
        val goal = if (goalWords.any { hay.contains(it) }) 80 else 50

        val base = listOf(0.30, 0.20, 0.15, 0.15, 0.10, 0.10)
        val w = base.mapIndexed { i, b -> b * p.rules.weights.getOrElse(i) { 2 }.coerceIn(1, 3) / 2.0 }
        val parts = listOf(theme, career, money, odds, effort, goal)
        var total = parts.indices.sumOf { parts[it] * w[it] } / w.sum()
        // A call on one of your own themes: the work already answers it.
        val callText = (o.title + " " + o.theme + " " + o.eligibility).lowercase()
        p.themes.map { it.trim().lowercase() }.filter { it.length > 2 }.firstOrNull { t -> Regex("\\b" + Regex.escape(t.removeSuffix("s")) + "s?\\b").containsMatchIn(callText) }?.let { t -> total += 6; reasons += "About $t: one of your themes" }
        // Near enough to drive to and hang the work yourself: the easiest kind of first show. Far ones cost shipping.
        if (!o.online) p.milesTo[o.city.substringBefore(",").trim().lowercase()]?.let { mi ->
            when {
                mi <= 30 -> { total += 8; reasons += if (mi < 2) "In your town" else "About $mi miles from you" }
                mi <= 120 -> { total += 3; reasons += "About $mi miles from you: a day trip" }
                mi > 400 -> { total -= if (p.level <= 1) 8 else 3; reasons += "About $mi miles away: shipping both ways" }
                else -> reasons += "About $mi miles from you"
            }
        }
        val known = p.friends.firstOrNull { f -> f.length > 3 && listOf(o.organiser, o.venue, o.title).any { it.contains(f, ignoreCase = true) } }
        if (known != null) { total += 12; reasons += "$known has shown your work before" }
        return total.roundToInt().coerceIn(0, 100) to reasons
    }
}

// ---------------------------------------------------------------- week planner

data class Slot(val start: LocalDateTime, val minutes: Int)

/** One studio slot in the artist's own weekly template. day: 1 = Monday to 7 = Sunday. */
data class TemplateSlot(val day: Int, val time: LocalTime, val minutes: Int)

/**
 * Ready-made frames: the standard US sizes sold at craft and big-box stores, with a mat that leaves
 * at least two inches around the image. Far cheaper than custom framing, and fine for a first show.
 */
object Frames {
    val standard = listOf(8.0 to 10.0, 11.0 to 14.0, 12.0 to 16.0, 16.0 to 20.0, 18.0 to 24.0, 20.0 to 24.0, 24.0 to 30.0, 24.0 to 36.0, 30.0 to 40.0)

    /** The smallest standard frame that fits the image with a mat of at least [minMat] inches each side, in the image's orientation. */
    fun fit(wIn: Double, hIn: Double, minMat: Double = 2.0): Pair<Double, Double>? {
        if (wIn <= 0 || hIn <= 0) return null
        val portrait = hIn >= wIn
        return standard.map { (a, b) -> if (portrait) a to b else b to a }
            .filter { (fw, fh) -> fw - wIn >= 2 * minMat && fh - hIn >= 2 * minMat }
            .minByOrNull { it.first * it.second }
    }

    /** A plain sentence: the frame to buy and the mat opening to ask for (a quarter inch smaller than the print, so it holds it). */
    fun advice(wIn: Double, hIn: Double): String {
        val f = fit(wIn, hIn) ?: return "This size is bigger than ready-made frames; a framer will make one to measure."
        val ow = wIn - 0.25; val oh = hIn - 0.25
        fun n(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.2f".format(d).trimEnd('0').trimEnd('.')
        return "A ready-made ${n(f.first)} × ${n(f.second)} in frame, with a mat cut to an opening of ${n(ow)} × ${n(oh)} in (a quarter inch smaller than the print, so it holds it)."
    }
}

object Planner {
    fun time(s: String, def: LocalTime): LocalTime = timeOrNull(s) ?: def

    /** Reads the ways people type a time: 17:00, 5:00, 5pm, 5:30 pm, 1730, 17.30, 17. Null when it is not a time. */
    fun timeOrNull(raw: String): LocalTime? {
        val s = raw.trim().lowercase().replace(" ", "").replace('.', ':')
        val m = Regex("""^(\d{1,2})(?::?(\d{2}))?(am|pm|a|p)?$""").matchEntire(s) ?: return null
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val half = m.groupValues[3]
        if (half.isNotEmpty()) { if (h !in 1..12) return null; h = h % 12 + (if (half.startsWith("p")) 12 else 0) }
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    fun days(s: String): Set<Int> = s.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** Template text looks like "2@17:00/40;6@10:00/160". */
    fun parseTemplate(s: String): List<TemplateSlot> = s.split(";").mapNotNull { part ->
        val d = part.substringBefore("@").trim().toIntOrNull()
        val m = part.substringAfter("/", "").trim().toIntOrNull()
        if (d == null || m == null || d !in 1..7 || m <= 0) null
        else TemplateSlot(d, time(part.substringAfter("@").substringBefore("/").trim(), LocalTime.of(17, 0)), m)
    }

    fun formatTemplate(l: List<TemplateSlot>): String = l.joinToString(";") { "${it.day}@${it.time}/${it.minutes}" }

    /**
     * Learns from when you really work. Over the last [weeks] weeks: a planned slot that never had a session near it,
     * and a day and hour where sessions keep happening (at least three) that the plan does not cover. Returns the slot to move
     * and where to move it, or null when the plan already matches your life.
     */
    fun betterSlot(plan: List<TemplateSlot>, sessionStarts: List<java.time.LocalDateTime>, today: LocalDate, weeks: Int = 4): Pair<TemplateSlot, TemplateSlot>? {
        val since = today.minusWeeks(weeks.toLong()).atStartOfDay()
        val recent = sessionStarts.filter { it.isAfter(since) }
        if (recent.size < 3 || plan.isEmpty()) return null
        fun near(s: TemplateSlot, t: java.time.LocalDateTime) = t.dayOfWeek.value == s.day && kotlin.math.abs(t.toLocalTime().toSecondOfDay() - s.time.toSecondOfDay()) <= 2 * 3600
        val unused = plan.firstOrNull { s -> recent.none { near(s, it) } } ?: return null
        val habit = recent.groupBy { it.dayOfWeek.value to it.hour }.filter { (k, v) -> v.size >= 3 && plan.none { p -> p.day == k.first && kotlin.math.abs(p.time.hour - k.second) <= 2 } }
            .maxByOrNull { it.value.size } ?: return null
        val minute = habit.value.map { it.minute }.average().toInt().let { if (it < 15) 0 else if (it < 45) 30 else 0 }
        val hour = habit.key.second + if (habit.value.map { it.minute }.average() >= 45) 1 else 0
        return unused to TemplateSlot(habit.key.first, LocalTime.of(hour.coerceIn(0, 23), minute), unused.minutes)
    }

    fun planFromTemplate(from: LocalDate, template: List<TemplateSlot>): List<Slot> = (0..6).flatMap { i ->
        val date = from.plusDays(i.toLong())
        template.filter { it.day == date.dayOfWeek.value }.map { Slot(date.atTime(it.time), it.minutes) }
    }.sortedBy { it.start }

    /** The suggested week, as a template the artist can then edit. */
    fun suggestedTemplate(workDays: Set<Int>, workEnd: LocalTime, bufferMin: Int, bedtime: LocalTime, weeklyHours: Int): List<TemplateSlot> =
        plan(LocalDate.of(2024, 1, 1), workDays, workEnd, bufferMin, bedtime, weeklyHours).map { TemplateSlot(it.start.dayOfWeek.value, it.start.toLocalTime(), it.minutes) }

    /**
     * Default week: short sessions on two work-day evenings after the recovery buffer,
     * then the rest of the weekly hours on days off. [from] is any day; slots cover the 7 days from it.
     */
    fun plan(from: LocalDate, workDays: Set<Int>, workEnd: LocalTime, bufferMin: Int, bedtime: LocalTime, weeklyHours: Int): List<Slot> {
        val slots = mutableListOf<Slot>()
        var left = weeklyHours * 60
        val week = (0..6).map { from.plusDays(it.toLong()) }
        val evening = roundUpHalfHour(workEnd.plusMinutes(bufferMin.toLong()))
        val windDown = bedtime.minusHours(1)
        val shortLen = 40
        val preferred = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        var shortCount = 0
        for (d in preferred) {
            if (shortCount >= 2 || left <= 120) break
            val date = week.firstOrNull { it.dayOfWeek == d && it.dayOfWeek.value in workDays } ?: continue
            if (evening.plusMinutes(shortLen.toLong()).isAfter(windDown) || evening.isBefore(workEnd)) continue
            slots += Slot(date.atTime(evening), shortLen); left -= shortLen; shortCount++
        }
        val off = week.filter { it.dayOfWeek.value !in workDays }
        for ((i, date) in off.withIndex()) {
            if (left <= 0) break
            val len = min(left, 180).coerceAtLeast(30)
            slots += Slot(date.atTime(if (i == 0) LocalTime.of(10, 0) else LocalTime.of(14, 0)), len); left -= len
        }
        return slots.sortedBy { it.start }
    }

    private fun roundUpHalfHour(t: LocalTime): LocalTime {
        val m = t.minute
        return when {
            m == 0 || m == 30 -> t.withSecond(0).withNano(0)
            m < 30 -> t.withMinute(30).withSecond(0).withNano(0)
            else -> t.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        }
    }

    /** Quiet = during work hours on work days, or from wind-down (1 hour before bed) until 7:00. */
    fun isQuiet(now: LocalDateTime, workDays: Set<Int>, workStart: LocalTime, workEnd: LocalTime, bedtime: LocalTime): Boolean {
        val t = now.toLocalTime()
        if (now.dayOfWeek.value in workDays && !t.isBefore(workStart) && t.isBefore(workEnd)) return true
        val windDown = bedtime.minusHours(1)
        val morning = LocalTime.of(7, 0)
        return if (windDown.isAfter(morning)) !t.isBefore(windDown) || t.isBefore(morning) else !t.isBefore(windDown) && t.isBefore(morning)
    }

    fun isWindDown(now: LocalDateTime, bedtime: LocalTime): Boolean {
        val t = now.toLocalTime(); val w = bedtime.minusHours(1)
        return if (w.isBefore(bedtime)) !t.isBefore(w) && t.isBefore(bedtime) else !t.isBefore(w) || t.isBefore(bedtime)
    }

    /**
     * Weeks until a series reaches its target at the artist's real pace, or null when there is nothing to go on yet.
     * [hoursPerPiece] comes from pieces already finished; [hoursPerWeek] from the last few weeks actually worked.
     */
    /**
     * The year ahead, a month at a time: about how many pieces you will have at your real pace (with the milestones
     * that open doors), calls closing, calls expected to open again, and your own dated plans. Months with nothing
     * dated get one plain focus for your level.
     */
    fun yearPlan(today: LocalDate, piecesNow: Int, piecesPerWeek: Double, level: Int, calls: List<Pair<LocalDate, String>>,
                 again: List<Pair<LocalDate, String>>, plans: List<Pair<LocalDate, String>>, months: Int = 12): List<Pair<java.time.YearMonth, List<String>>> {
        val start = java.time.YearMonth.from(today)
        var before = piecesNow.toDouble()
        return (0 until months).map { k ->
            val ym = start.plusMonths(k.toLong())
            val end = ym.atEndOfMonth()
            val weeks = java.time.temporal.ChronoUnit.DAYS.between(today, end).coerceAtLeast(0) / 7.0
            val count = piecesNow + piecesPerWeek * weeks
            val lines = mutableListOf<String>()
            if (piecesPerWeek > 0) {
                lines += "About ${count.toInt()} finished " + (if (count.toInt() == 1) "piece" else "pieces") + " by the end of the month."
                listOf(10 to "Ten pieces: a full series, and local and campus calls open to you.", 15 to "Fifteen pieces: enough to fill a small room, a first solo show.", 25 to "Twenty-five pieces: choice. Send only your strongest.")
                    .firstOrNull { (n, _) -> before < n && count >= n }?.let { lines += it.second }
            }
            before = count
            fun inMonth(l: List<Pair<LocalDate, String>>) = l.filter { java.time.YearMonth.from(it.first) == ym }.sortedBy { it.first }
            plans.let(::inMonth).let { p -> p.take(3).forEach { lines += "${it.first.dayOfMonth} ${ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)}: ${it.second}" }; if (p.size > 3) lines += "and ${p.size - 3} more of your own dates" }
            calls.let(::inMonth).let { c -> if (c.isNotEmpty()) lines += "Closing: " + c.take(3).joinToString("; ") { it.second } + (if (c.size > 3) "; and ${c.size - 3} more" else "") }
            again.let(::inMonth).let { a -> if (a.isNotEmpty()) lines += "Likely to open again: " + a.take(3).joinToString("; ") { it.second } }
            // A quiet month gets one plain focus for your level, a different one each month so the year has a shape.
            if (inMonth(calls).isEmpty() && inMonth(plans).isEmpty()) lines += "Focus: " + when {
                level <= 0 -> listOf("make, and go and look at one place near you.", "finish one piece and photograph it well.", "post one piece, and ask three people which of yours is strongest.", "go to one opening and say hello.")
                level == 1 -> listOf("one local or campus call, and one venue to write to.", "go to one opening and say hello.", "finish two pieces for the series.", "write to a library or cafe that shows local work.")
                else -> listOf("one regional call that fits, and keep making.", "write to one gallery you have visited.", "a proposal for a small solo show.", "finish the strongest piece of the series.")
            }[k % 4]
            ym to lines
        }
    }

    /** The kinds of work a page mentions showing, in plain words, most mentioned first. */
    fun mediaShown(text: String): List<String> {
        val l = text.lowercase()
        val kinds = listOf("painting" to "paint", "drawing" to "drawing|drawings|graphite|charcoal", "photography" to "photograph", "printmaking" to "printmak|prints\\b|linocut|etching|screen ?print",
            "ceramics" to "ceramic|pottery|clay", "sculpture" to "sculpt", "textiles" to "textile|fiber art|fibre|quilt|weav", "digital art" to "digital art|new media|video art", "illustration" to "illustrat", "mixed media" to "mixed media|collage")
        return kinds.map { (name, re) -> name to Regex(re).findAll(l).count() }.filter { it.second >= 2 }.sortedByDescending { it.second }.map { it.first }.take(4)
    }

    /** Whether what a venue shows meets what you make, from your mediums ("acrylic, digital" meets "painting, digital art"). */
    fun mediaMatch(shown: List<String>, mine: String): Boolean {
        val m = mine.lowercase()
        val mineKinds = mediaShown("$m $m").toMutableSet()
        if (Regex("acrylic|oil|gouache|watercolou?r|tempera").containsMatchIn(m)) mineKinds += "painting"
        if (Regex("pencil|ink|pastel|charcoal|graphite").containsMatchIn(m)) mineKinds += "drawing"
        if (Regex("archival print|giclee|digital").containsMatchIn(m)) mineKinds += "digital art"
        return shown.any { it in mineKinds }
    }

    /** "Title | what to make" lines, numbered or bulleted or not, into pairs. */
    fun pieceLines(text: String): List<Pair<String, String>> = text.lines().map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }
        .mapNotNull { l -> val parts = l.split(Regex("\\s*[|]\\s*|\\s+[-–—]\\s+|:\\s+"), limit = 2); if (parts.size == 2 && parts[0].length in 2..50 && parts[1].length > 8) parts[0].trim('"', ' ') to parts[1].trim() else null }
        .distinctBy { it.first.lowercase() }

    /**
     * Without the writing helper: the way series are built, as plain prompts. Keep the subject, change one thing.
     * [subject] is the series name or what ties it together.
     */
    fun seriesVariations(subject: String, n: Int): List<Pair<String, String>> {
        val s = subject.ifBlank { "your subject" }
        return listOf(
            "Early" to "$s first thing in the morning, in that light.",
            "Late" to "$s at night or at dusk: the same thing in the dark.",
            "Close" to "$s from very close: one small part, filling the whole picture.",
            "Far" to "$s from far away, small in a big space.",
            "Twice the size" to "$s again, twice as big as your usual size.",
            "Small" to "$s small and quick, in one sitting.",
            "Another season" to "$s in another season or another weather.",
            "From above" to "$s seen from above, or from below.",
            "Two colors" to "$s in only two colors plus white.",
            "Someone there" to "$s with a person or their trace (a coat, a cup, a shadow).",
            "Empty" to "$s with everything else taken out.",
            "Again" to "Your favorite piece in this series, made again from memory."
        ).take(n.coerceIn(1, 12))
    }

    fun weeksToFinish(done: Int, target: Int, hoursPerPiece: Double, hoursPerWeek: Double): Int? {
        if (done >= target) return 0
        if (hoursPerPiece <= 0 || hoursPerWeek <= 0) return null
        return Math.ceil((target - done) * hoursPerPiece / hoursPerWeek).toInt().coerceAtLeast(1)
    }

    /** "3 weekends left" instead of a date to calculate. */
    fun weekendsLeft(today: LocalDate, deadline: LocalDate): String {
        if (deadline.isBefore(today)) return "passed"
        if (deadline == today) return "today"
        var n = 0; var d = today.plusDays(1)
        while (!d.isAfter(deadline)) { if (d.dayOfWeek == DayOfWeek.SATURDAY) n++; d = d.plusDays(1) }
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, deadline).toInt()
        return when {
            n == 0 -> if (days == 1) "tomorrow" else "$days days left"
            n == 1 -> "1 weekend left"
            else -> "$n weekends left"
        }
    }
}

// ---------------------------------------------------------------- text and file rules

object Texts {
    val jargon = listOf(
        "liminal", "interrogates", "interrogate notions", "notions of", "juxtaposition", "dichotomy", "explores the",
        "the human condition", "blurs the line", "blurring the line", "tapestry", "delve", "testament to",
        "ever-evolving", "seeks to", "invites the viewer", "at the intersection of", "unique perspective", "passion for"
    )

    /** A piece's tiny steps. Each line is "[x] text" (done) or "[ ] text" (open); a bare line counts as open. */
    fun steps(s: String): List<Pair<Boolean, String>> = s.lines().map { it.trim() }.filter { it.isNotBlank() }.map {
        when {
            it.startsWith("[x]") -> true to it.removePrefix("[x]").trim()
            it.startsWith("[ ]") -> false to it.removePrefix("[ ]").trim()
            else -> false to it
        }
    }.filter { it.second.isNotBlank() }

    fun stepsText(l: List<Pair<Boolean, String>>): String = l.joinToString("\n") { (if (it.first) "[x] " else "[ ] ") + it.second }

    fun firstOpenStep(s: String): String = steps(s).firstOrNull { !it.first }?.second.orEmpty()

    /** How alike two questions are, 0 to 1, by the longer words they share. */
    fun similarity(a: String, b: String): Double {
        fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 3 }.toSet()
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    fun fitWord(score: Int): String = when { score >= 75 -> "Strong fit"; score >= 60 -> "Good fit"; score >= 45 -> "Fair fit"; else -> "Weak fit" }

    fun words(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    /** "Night Shift" -> "Night Shift II", "Night Shift II" -> "Night Shift III", "Study 3" -> "Study 4". */
    fun nextTitle(t: String): String {
        val s = t.trim().ifBlank { return "Untitled" }
        Regex("^(.*?)\\s+(\\d+)$").find(s)?.let { return it.groupValues[1] + " " + (it.groupValues[2].toInt() + 1) }
        val romans = listOf("II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
        Regex("^(.*?)\\s+(II|III|IV|V|VI|VII|VIII|IX)$").find(s)?.let { m -> return m.groupValues[1] + " " + romans[romans.indexOf(m.groupValues[2]) + 1] }
        return "$s II"
    }

    /** Sentences over [max] words, the start of each: long sentences are where readers (and jurors) drift off. */
    fun longSentences(s: String, max: Int = 28): List<String> = s.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.split(Regex("\\s+")).size > max }.map { it.split(Regex("\\s+")).take(6).joinToString(" ") + "..." }

    fun jargonIn(s: String, extra: List<String> = emptyList()): List<String> {
        val l = s.lowercase()
        return (jargon + extra.map { it.trim().lowercase() }.filter { it.isNotBlank() }).distinct().filter { l.contains(it) }
    }

    fun trimToWords(s: String, limit: Int): String {
        val w = s.trim().split(Regex("\\s+"))
        if (limit <= 0 || w.size <= limit) return s.trim()
        val cut = w.take(limit).joinToString(" ")
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (end > cut.length / 2) cut.substring(0, end + 1) else cut
    }

    fun clean(s: String): String = s.replace(Regex("[^A-Za-z0-9]+"), "").take(40)

    /** Lastname_Title_Year_Medium_Size.jpg */
    fun fileName(lastName: String, title: String, year: Int, medium: String, wIn: Double, hIn: Double, ext: String = "jpg", number: Int = 0): String {
        val parts = mutableListOf(clean(lastName).ifBlank { "Artist" })
        if (number > 0) parts += "%02d".format(number)
        parts += clean(title).ifBlank { "Untitled" }
        if (year > 0) parts += year.toString()
        // Short and predictable, the way calls ask for it: Lastname_01_Title_2026.jpg. Medium and size live in the image list.
        return parts.joinToString("_") + "." + ext
    }

    fun dim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)

    fun dims(wIn: Double, hIn: Double, dIn: Double = 0.0): String {
        if (wIn <= 0 || hIn <= 0) return ""
        val inches = "${dim(hIn)} \u00D7 ${dim(wIn)}" + (if (dIn > 0) " \u00D7 ${dim(dIn)}" else "") + " in"
        val cm = "${dim1(hIn * 2.54)} \u00D7 ${dim1(wIn * 2.54)}" + (if (dIn > 0) " \u00D7 ${dim1(dIn * 2.54)}" else "") + " cm"
        return "$inches ($cm)"
    }

    private fun dim1(d: Double) = "%.1f".format(d)

    /** Pixels per inch at the wanted print size; 300 is the usual target. */
    fun ppi(pixelW: Int, pixelH: Int, wIn: Double, hIn: Double): Int {
        if (pixelW <= 0 || pixelH <= 0 || wIn <= 0 || hIn <= 0) return 0
        return min(pixelW / wIn, pixelH / hIn).toInt()
    }

    /** The largest size a file prints sharply (300 ppi), rounded down to the half inch. Null when there is no file. */
    fun suggestedPrintSize(pixelW: Int, pixelH: Int): Pair<Double, Double>? {
        if (pixelW <= 0 || pixelH <= 0) return null
        val w = Math.floor(pixelW / 300.0 * 2) / 2; val h = Math.floor(pixelH / 300.0 * 2) / 2
        return if (w < 1 || h < 1) null else w to h
    }

    fun printVerdict(pixelW: Int, pixelH: Int, wIn: Double, hIn: Double): String {
        if (pixelW <= 0 || pixelH <= 0) return "No file yet"
        val maxW = pixelW / 300.0; val maxH = pixelH / 300.0
        val largest = "Largest sharp print: ${dim(maxH)} x ${dim(maxW)} in at 300 ppi."
        val p = ppi(pixelW, pixelH, wIn, hIn)
        return when {
            p == 0 -> largest
            p >= 300 -> "Print-ready at this size ($p ppi). $largest"
            p >= 200 -> "Acceptable at this size ($p ppi), sharper if printed smaller. $largest"
            else -> "Too few pixels for this size ($p ppi). $largest"
        }
    }

    private val filler = setOf("call", "calls", "open", "entry", "entries", "for", "artists", "artist", "the", "and", "annual", "submissions", "exhibition", "juried")

    private fun titleWords(t: String) = t.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.length > 2 && it !in filler }.toSet()

    /** The same call seen on two listing sites: same deadline day and mostly the same words in the title. */
    fun sameCall(aTitle: String, aDeadline: Long, bTitle: String, bDeadline: Long): Boolean {
        if (aDeadline <= 0 || bDeadline <= 0 || aDeadline / 86_400_000L != bDeadline / 86_400_000L) return false
        val a = titleWords(aTitle); val b = titleWords(bTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        return a.intersect(b).size.toDouble() / minOf(a.size, b.size) >= 0.75
    }

    fun dedupeKey(organiser: String, deadline: Long, title: String): String {
        val day = if (deadline > 0) deadline / 86_400_000L else 0
        val t = title.lowercase().replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.length > 3 }.sorted().take(4).joinToString("")
        val org = organiser.lowercase().replace(Regex("[^a-z0-9]"), "").take(12)
        return "$org|$day|$t"
    }

    fun price(wIn: Double, hIn: Double, perSqIn: Double): Double =
        if (wIn <= 0 || hIn <= 0) 0.0 else (Math.round(wIn * hIn * perSqIn / 5.0) * 5).toDouble()

    private val cvOrder = listOf(
        "education" to "Education", "solo" to "Solo Exhibitions", "group" to "Group Exhibitions",
        "invited" to "Invited Exhibitions", "award" to "Awards", "grant" to "Grants", "residency" to "Residencies",
        "teaching" to "Teaching and Talks", "collection" to "Collections", "press" to "Press", "publication" to "Publications", "skill" to "Skills"
    )
    val factCategories = cvOrder.map { it.first }

    /** The heading a CV uses for a kind of line, for example "Solo Exhibitions". */
    fun factName(key: String): String = cvOrder.firstOrNull { it.first == key }?.second ?: key.replaceFirstChar { it.uppercase() }

    /** The CV is built only from recorded facts; nothing is invented or padded. */
    fun cv(name: String, contact: String, facts: List<Fact>): List<Pair<String, List<String>>> {
        val out = mutableListOf<Pair<String, List<String>>>()
        out += name to listOfNotNull(contact.ifBlank { null })
        for ((key, heading) in cvOrder) {
            val lines = facts.filter { it.category == key }.sortedByDescending { it.year }
                .map { (if (it.year > 0) "${it.year}  " else "") + it.text }
            if (lines.isNotEmpty()) out += heading to lines
        }
        return out
    }

    /** Reads a typed number the way people type it: "$1,200", "1 200", "12,5", "16 in". Null when there is no number. */
    fun number(t: String): Double? {
        var x = t.trim().replace(Regex("[^0-9.,]"), "")
        if (x.isEmpty()) return null
        // A single comma with one or two digits after it is a decimal comma; otherwise commas group thousands.
        x = if (Regex("^\\d+,\\d{1,2}$").matches(x)) x.replace(',', '.') else x.replace(",", "")
        return x.toDoubleOrNull()
    }

    /** A web address, with or without https:// ("artcall.org/calls"), and nothing else in the text. */
    fun isLink(t: String): Boolean = Regex("^(https?://)?([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/\\S*)?$").matches(t.trim()) && !t.trim().contains('@')

    /** A quick check that an address could be an email: something@somewhere.tld, no spaces. */
    /** True when the call says outright that entry is free, or was typed in by hand; otherwise a zero fee only means none was found. */
    fun feeStated(o: Opp): Boolean = o.fee > 0 || o.rawText.isBlank() ||
        Regex("""\b(free (to enter|entry|of charge|submission)|no (entry |submission |application )?fees?|fee: ?(none|free|\$?0\b)|without (a|any) fee|no cost)""", RegexOption.IGNORE_CASE).containsMatchIn(o.rawText)

    /** True when an email domain and a web address belong to the same organization: "news.riverside.org" and "https://www.riverside.org/call". */
    fun sameSite(emailDomain: String, url: String): Boolean {
        fun base(h: String) = h.lowercase().trim().removePrefix("www.").split('.').filter { it.isNotBlank() }.takeLast(2).joinToString(".")
        val host = url.substringAfter("://", url).substringBefore('/').substringBefore(':')
        val a = base(emailDomain); val b = base(host)
        return a.contains('.') && a == b && a !in setOf("gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com", "aol.com")
    }

    fun looksLikeEmail(e: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[a-zA-Z]{2,}$").matches(e.trim())

    /** 850 KB, 1.4 MB: a file size the way people read it. */
    fun fileSize(bytes: Long): String = if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)} KB"

    /** "1 file", "3 files": a count with the right form of the word. */
    fun count(n: Int, one: String, many: String = one + "s"): String = "$n " + if (n == 1) one else many

    /** CV sections in the usual order, newest first inside each, for documents that lay the years out in a column. */
    fun cvSections(facts: List<Fact>): List<Pair<String, List<Fact>>> = cvOrder.mapNotNull { (key, heading) ->
        facts.filter { it.category == key && it.text.isNotBlank() }.sortedByDescending { it.year }.takeIf { it.isNotEmpty() }?.let { heading to it }
    }

    fun cvText(name: String, contact: String, facts: List<Fact>): String =
        cv(name, contact, facts).joinToString("\n\n") { (h, l) -> (listOf(h.uppercase()) + l).joinToString("\n") }
}


// ---------------------------------------------------------------- reading a call without AI

/** Pulls the obvious facts out of a call's text with plain pattern matching, for when there is no AI key. */
/** Asking a few people which pieces are strongest: the message, and counting their picks. */
object Feedback {
    val defaultQuestions = listOf(
        "If you could hang only one of these on your wall, which number would it be?",
        "In each one, what do you look at first?",
        "Does anything look confusing or unfinished to you?"
    )

    fun message(name: String, titles: List<String>, questions: List<String>): String =
        "Hi! I am choosing which of my pieces to send to shows, and I would love a quick, honest opinion. " +
            "The pictures are numbered:\n" + titles.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n") +
            "\n\n" + questions.filter { it.isNotBlank() }.mapIndexed { i, q -> "${i + 1}) $q" }.joinToString("\n") +
            "\n\nA few words is plenty. Thank you!" + (if (name.isNotBlank()) "\n$name" else "")

    fun votes(stored: String): Map<Long, Int> = stored.split(",").mapNotNull { p -> p.split(":").takeIf { it.size == 2 }?.let { (a, b) -> a.toLongOrNull()?.let { id -> b.toIntOrNull()?.let { id to it } } } }.toMap()

    fun addVote(stored: String, id: Long): String = (votes(stored).toMutableMap().also { it[id] = (it[id] ?: 0) + 1 }).entries.joinToString(",") { "${it.key}:${it.value}" }

    /** The order people picked, most picks first; pieces nobody picked keep their place after. */
    fun byVotes(ids: List<Long>, stored: String): List<Long> { val v = votes(stored); return ids.sortedByDescending { v[it] ?: 0 } }

    /** "No. 2 was picked by 3 of 4 people", or nothing until someone has answered. */
    fun summary(ids: List<Long>, titles: Map<Long, String>, stored: String): String {
        val v = votes(stored).filterKeys { it in ids }; val total = v.values.sum()
        if (total == 0) return ""
        val (top, n) = v.maxByOrNull { it.value }!!
        val tied = v.count { it.value == n } > 1
        return if (tied) "No clear favorite yet ($total ${if (total == 1) "answer" else "answers"}). Ask one or two more people."
        else "${titles[top] ?: "One piece"} was picked by $n of ${Texts.count(total, "person", "people")}."
    }
}

object CallReader {
    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val monthPattern = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val datePatterns = listOf(
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})"),
        Regex("$monthPattern\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})", RegexOption.IGNORE_CASE),
        Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+$monthPattern\\.?,?\\s+(\\d{4})", RegexOption.IGNORE_CASE),
        Regex("(\\d{1,2})/(\\d{1,2})/(\\d{2,4})"),
        // No year given ("Deadline: November 30"): the next time that date comes round.
        Regex("$monthPattern\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b(?!,?\\s*\\d{4})(?!:)", RegexOption.IGNORE_CASE),
        Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$monthPattern\\b(?!\\.?,?\\s*\\d{4})", RegexOption.IGNORE_CASE)
    )

    private fun month(word: String): Int = months.indexOfFirst { it.startsWith(word.lowercase().take(3)) } + 1

    /** Every date found, with where it sits in the text. US month/day order for slashed dates. */
    fun dates(text: String, today: LocalDate = LocalDate.now()): List<Pair<Int, LocalDate>> {
        val out = mutableListOf<Pair<Int, LocalDate>>()
        datePatterns.forEachIndexed { i, p ->
            p.findAll(text).forEach { m ->
                val g = m.groupValues
                val d = try {
                    when (i) {
                        0 -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                        1 -> LocalDate.of(g[3].toInt(), month(g[1]), g[2].toInt())
                        2 -> LocalDate.of(g[3].toInt(), month(g[2]), g[1].toInt())
                        4 -> LocalDate.of(today.year, month(g[1]), g[2].toInt()).let { if (it.isBefore(today)) it.plusYears(1) else it }
                        5 -> LocalDate.of(today.year, month(g[2]), g[1].toInt()).let { if (it.isBefore(today)) it.plusYears(1) else it }
                        else -> LocalDate.of(g[3].toInt().let { if (it < 100) 2000 + it else it }, g[1].toInt(), g[2].toInt())
                    }
                } catch (e: Exception) { null }
                if (d != null) out += m.range.first to d
            }
        }
        return out.sortedBy { it.first }
    }

    /** The first date within a sentence or so after any of [words], for example the delivery day in an acceptance email. */
    fun dateAfter(text: String, words: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val all = dates(text, today)
        return Regex(words, RegexOption.IGNORE_CASE).findAll(text).firstNotNullOfOrNull { k -> all.firstOrNull { it.first >= k.range.first && it.first - k.range.first < 120 }?.second }
    }

    /** The deadline: the first date after a word like "deadline", otherwise the first date still ahead. */
    fun deadline(text: String, today: LocalDate): LocalDate? {
        val all = dates(text, today)
        val key = Regex("deadline|due|closes|submit by|entries by|apply by", RegexOption.IGNORE_CASE).find(text)?.range?.first
        if (key != null) all.firstOrNull { it.first >= key && it.first - key < 160 }?.let { return it.second }
        return all.map { it.second }.firstOrNull { !it.isBefore(today) }
    }

    fun fee(text: String): Double {
        val l = text.lowercase()
        if (Regex("no (entry |application |submission )?fee|free to (enter|apply|submit)|free entry").containsMatchIn(l)) return 0.0
        // An amount, but not a prize: "$12,000" and "$1K" are not fees.
        val amount = "\\$\\s?(\\d{1,3}(?:\\.\\d{1,2})?)(?![\\d,.]*\\d|\\s?[km]\\b|\\s?(?:thousand|million))"
        val near = Regex("(?:fee|entry|entries|application)[^$]{0,60}$amount").find(l) ?: Regex("$amount[^.]{0,40}(?:fee|entry|per entry|to enter)").find(l)
        return near?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun kindOf(l: String): String? = when {
        Regex("residenc").containsMatchIn(l) -> "residency"; Regex("\\bgrants?\\b|fellowship").containsMatchIn(l) -> "grant"
        Regex("art fair|festival").containsMatchIn(l) -> "fair"; Regex("magazine|publication|journal|zine\\b").containsMatchIn(l) -> "publication"
        l.contains("commission") && l.contains("public art") -> "commission"
        Regex("exhibit|juried|open call|call for (art|entr)|show\\b|competition|prize|award|salon").containsMatchIn(l) -> "exhibition"
        else -> null
    }

    /** "Home | Winter Open 2027" and "Winter Open 2027 - Riverside Arts" read as "Winter Open 2027". */
    /** Lines that say what a flyer is, not what it is called. */
    private val genericLine = Regex("^(an? )?(open call|call for (artists|entries|submissions|art|work)|now accepting|submissions? (open|wanted)|artists wanted|words read from a photo.*|apply now|seeking artists)[!:.]*$", RegexOption.IGNORE_CASE)

    /** The line that names the call: the first real line, skipping links, "OPEN CALL" style headings, and date or fee lines. */
    /** An opening poster, read: the show's name and the day of the opening, or null when no date can be found. */
    fun openingFrom(text: String, today: LocalDate = LocalDate.now()): Pair<String, LocalDate>? {
        val d = dateAfter(text, "opening|reception|first friday|art walk|join us|opens", today) ?: dateAfter(text, "", today) ?: return null
        if (d.isBefore(today)) return null
        return titleLine(text).ifBlank { "An opening" } to d
    }

    fun titleLine(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("Link:") }
        return lines.firstOrNull { l -> !genericLine.matches(l) && !Regex("^((deadline|due( date)?|entry fee|fees?|apply|submit|how to apply)\\s*[:\\-–]|open to |eligib)", RegexOption.IGNORE_CASE).containsMatchIn(l) }
            ?: lines.firstOrNull().orEmpty()
    }

    fun cleanTitle(t: String): String {
        val parts = t.split(" | ", " – ", " — ").map { it.trim() }.filter { it.isNotBlank() && !it.equals("home", true) }
        return (parts.firstOrNull() ?: t).trim()
    }

    fun read(text: String, today: LocalDate, zone: java.time.ZoneId, sourceName: String, now: Long): Opp {
        val l = text.lowercase()
        // Flyers print bare addresses ("riversideprints.org/call"), so those count as links too.
        val url = Regex("https?://[^\\s)>\\]]+").find(text)?.value
            ?: Regex("(?<![@\\w.])(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:org|com|net|edu|art|gallery|io|co|us|ca|uk)(?:/[^\\s)>\\]]*)?", RegexOption.IGNORE_CASE).find(text)?.value?.let { "https://" + it.trimEnd('.', ',') }
            ?: ""
        val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(text)?.value.orEmpty()
        val title = cleanTitle(titleLine(text)).take(120).ifBlank { "Shared call" }
        val d = deadline(text, today)
        val deadlineMs = d?.atTime(23, 59)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: 0L
        // The kind is read from the title first, then the opening of the page, so a menu that mentions "residencies" does not decide it.
        val kind = kindOf(title.lowercase()) ?: kindOf(l.take(1200)) ?: "exhibition"
        val scope = when {
            l.contains("international") || l.contains("worldwide") -> "international"; l.contains("national") || l.contains("open to all u.s.") -> "national"
            l.contains("regional") -> "regional"; l.contains("online exhibition") || l.contains("virtual exhibition") || l.contains("online only") -> "online"
            l.contains("staff") || l.contains("campus") || l.contains("students") -> "campus"; else -> ""
        }
        val limit = Regex("(\\d{2,4})\\s*words?").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val works = Regex("up to (\\d{1,2}) (?:works|pieces|images|entries)").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return Opp(
            title = title, url = url, email = email, deadline = deadlineMs, fee = fee(text), kind = kind, scope = scope,
            online = scope == "online", statementLimit = if (l.contains("statement")) limit else 0, maxWorks = works,
            jurors = Regex("(?i:jurors?|juried by|judges?):?\\s+([A-Z][^\\n.,;]{2,60})").find(text)?.groupValues?.get(1)?.trim().orEmpty(),
            rawText = text.take(6000), sourceName = sourceName, createdAt = now, dedupeKey = Texts.dedupeKey("", deadlineMs, title),
            guaranteed = payToShow(l), rightsGrab = takesRights(l)
        )
    }

    /** Vanity-gallery signs: a place is bought, not juried. */
    fun payToShow(l: String) = Regex("guaranteed (exhibition|acceptance|placement|to be (shown|exhibited))|all (works|artists|entries) (are |will be )?(accepted|exhibited|shown)|wall (rental|fee)|hanging fee|rent (a )?wall|exhibition fee of|every (artist|entry) (is|will be) (accepted|shown)").containsMatchIn(l)

    /** Terms that take more than the right to promote the show. */
    fun takesRights(l: String) = Regex("perpetual|irrevocable|in perpetuity|transfer (of )?(all )?(copyright|rights)|assign (all )?(copyright|rights)|exclusive rights|sell (the )?images|commercial use of (the )?(images|works)").containsMatchIn(l)
}


/** A list of places pasted from anywhere (a note, a spreadsheet, an email): name, email and website picked out of each line. */
object VenueList {
    data class Row(val name: String, val email: String, val website: String)

    fun read(text: String): List<Row> = text.lines().map { it.trim().trimStart('-', '*', '•', ' ') }.filter { it.length > 2 }.mapNotNull { l ->
        val em = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+").find(l)
        val email = em?.value.orEmpty().trimEnd('.')
        // A website anywhere on the line, but not the part of the email after the @.
        val web = Regex("(https?://\\S+|www\\.\\S+|\\b[\\w-]+(?:\\.[\\w-]+)*\\.(?:org|com|net|edu|gallery|art|us)(?:/\\S*)?)").findAll(l)
            .filter { m -> em == null || m.range.first > em.range.last || m.range.last < em.range.first }.map { it.value.trimEnd('.', ',', ';', ')') }.firstOrNull().orEmpty()
        val name = l.replace(email, "").replace(web, "").split(Regex("[,;|\\t]| - | – ")).map { it.trim() }.firstOrNull { it.length > 1 && it.any { c -> c.isLetter() } }.orEmpty()
        if (name.isBlank() && email.isBlank()) null else Row(name.ifBlank { email.substringBefore("@") }, email, web)
    }
}

/** What has happened with a venue or person, in date order, read from the dates in their notes and the app's own records. */
object Timeline {
    private val iso = Regex("(\\d{4}-\\d{2}-\\d{2})")
    private val us = Regex("([A-Z][a-z]{2}), ([A-Z][a-z]{2}) (\\d{1,2}), (\\d{4})")

    fun dateIn(line: String): LocalDate? = iso.find(line)?.let { runCatching { LocalDate.parse(it.value) }.getOrNull() }
        ?: us.find(line)?.let { m -> runCatching { LocalDate.parse("${m.groupValues[4]}-${m.groupValues[2]}-${m.groupValues[3].padStart(2, '0')}", java.time.format.DateTimeFormatter.ofPattern("yyyy-MMM-dd", java.util.Locale.US)) }.getOrNull() }

    /** [notes] sentences with a date in them, plus the dated events given, newest first. */
    fun of(notes: String, events: List<Pair<LocalDate, String>>): List<Pair<LocalDate, String>> {
        val fromNotes = notes.split(Regex("(?<=[.!?])\\s+|\\n")).mapNotNull { l -> dateIn(l)?.let { it to l.trim() } }
        return (fromNotes + events).distinctBy { it.second }.sortedByDescending { it.first }
    }
}

/** Hours of making in each of the last [weeks] weeks (Monday to Sunday), oldest first. */
object HoursByWeek {
    fun of(today: LocalDate, sessions: List<Pair<LocalDate, Double>>, weeks: Int = 8): List<Double> {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        return (weeks - 1 downTo 0).map { k -> val start = monday.minusWeeks(k.toLong()); val end = start.plusDays(6); sessions.filter { !it.first.isBefore(start) && !it.first.isAfter(end) }.sumOf { it.second } }
    }
}

/** Dates as a calendar file (.ics) any calendar app can import: deadlines, show steps and studio times. */
object Ics {
    private fun stamp(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    /** Each entry: a stable id, the title, start and end (end 0 = one hour). */
    fun file(entries: List<Triple<String, String, Pair<Long, Long>>>, now: Long = System.currentTimeMillis()): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Studio Agent//EN\r\nCALSCALE:GREGORIAN\r\n")
        entries.forEach { (id, title, t) ->
            val end = if (t.second > t.first) t.second else t.first + 3_600_000L
            append("BEGIN:VEVENT\r\nUID:$id@studioagent\r\nDTSTAMP:${stamp(now)}\r\nDTSTART:${stamp(t.first)}\r\nDTEND:${stamp(end)}\r\nSUMMARY:${esc(title)}\r\nEND:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }
}

/** Money in and out by month, for a picture of the year: income and costs per month, January first. */
object MoneyByMonth {
    fun of(year: Int, entries: List<Pair<LocalDate, Double>>): List<Pair<Double, Double>> = (1..12).map { m ->
        val inMonth = entries.filter { it.first.year == year && it.first.monthValue == m }
        inMonth.filter { it.second > 0 }.sumOf { it.second } to inMonth.filter { it.second < 0 }.sumOf { -it.second }
    }
}

/** An entry in small pieces: five 15-minute steps, one per evening, spread over the days before the send date. */
object EntrySteps {
    val steps = listOf("choose the works", "check each picture and its details", "the paragraph on how it fits", "fill in the form or the email", "send it")

    /** The days for each step: work-day evenings first ([workDays] as 1 = Monday .. 7 = Sunday), never after [sendBy], never before tomorrow. */
    fun days(today: LocalDate, sendBy: LocalDate, workDays: Set<Int>): List<LocalDate> {
        val all = generateSequence(today.plusDays(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(sendBy) }.toList()
        if (all.isEmpty()) return steps.map { today }
        val evenings = all.filter { it.dayOfWeek.value in workDays }
        val pool = if (evenings.size >= steps.size) evenings else all
        return steps.indices.map { i -> pool[(i * pool.size / steps.size).coerceAtMost(pool.size - 1)] }
    }
}

/**
 * "Is it done?" A piece that has had far more sessions than your others usually take may be finished already;
 * reworking a finished piece is the commonest way work gets worse. Gentle, and only with your own numbers.
 */
object GoodEnough {
    /** Sessions a finished piece of yours usually took, or 0 until there are two finished pieces to go by. */
    fun usual(sessionsPerFinished: List<Int>): Int = if (sessionsPerFinished.size < 2) 0 else sessionsPerFinished.sorted()[sessionsPerFinished.size / 2]

    fun overdue(sessionsHere: Int, usual: Int): Boolean = usual > 0 && sessionsHere >= maxOf(usual * 2, usual + 3)
}

/** Prices that hold together: a table from your rule, the pieces that stray from it, and a raise rounded the way prices are. */
object Pricing {
    val sizes = listOf(5.0 to 7.0, 8.0 to 10.0, 9.0 to 12.0, 11.0 to 14.0, 16.0 to 20.0, 18.0 to 24.0, 24.0 to 36.0)

    /** Rounded to a price that looks chosen: to 5 under 100, to 10 under 1,000, to 50 above. */
    fun round(p: Double): Double = when { p <= 0 -> 0.0; p < 100 -> Math.round(p / 5) * 5.0; p < 1000 -> Math.round(p / 10) * 10.0; else -> Math.round(p / 50) * 50.0 }

    fun raised(p: Double, pct: Double): Double = round(p * (1 + pct / 100))

    /** Pieces whose price is more than a quarter away from what your rule gives their size. */
    fun strays(works: List<com.emotionalleaves.studioagent.data.Artwork>, perSqIn: Double): List<Pair<com.emotionalleaves.studioagent.data.Artwork, Double>> =
        works.filter { it.price > 0 && !it.nfs && it.widthIn > 0 && it.heightIn > 0 && it.status != "sold" }
            .map { it to Texts.price(it.widthIn, it.heightIn, perSqIn) }
            .filter { (w, r) -> r > 0 && kotlin.math.abs(w.price - r) / r > 0.25 }
}

/** Someone wants to see your work in person: what to do before, what to have out, what to ask, and the thank-you after. */
object StudioVisit {
    fun steps(visit: LocalDate, today: LocalDate): List<Pair<String, LocalDate>> = listOf(
        "Confirm the visit by email: day, time, address, how long (an hour is usual)" to today,
        "Choose 6 to 10 of your strongest pieces and the order to show them" to visit.minusDays(5),
        "Print your CV, statement and price list; have the portfolio on your phone" to visit.minusDays(2),
        "Clear a wall or a table so the work can be seen one piece at a time" to visit.minusDays(1),
        "The visit" to visit,
        "Send a short thank-you, with anything they asked for" to visit.plusDays(1)
    ).map { (t, d) -> t to (if (d.isBefore(today)) today else d) }

    val questions = listOf(
        "What drew you to the work?",
        "Which pieces would you choose, and why?",
        "What kind of shows or projects are you planning?",
        "Is there anyone else you think should see this work?",
        "What would be a good next step from here?"
    )

    val tips = listOf(
        "Show the pieces one at a time, strongest first. Let them look before you speak.",
        "Say what you made and why in a sentence or two; then ask what they see.",
        "It is fine not to know where the work is going. 'I am finding out' is an honest answer.",
        "If they ask about prices, say them plainly from your price list.",
        "Offer water or coffee. Keep it to about an hour."
    )
}

/** A day at a market or fair: prints and cards in multiples, originals one each, a tally, and the day's total. */
object Market {
    /** [pieceId]: prints of one of your pieces, so each one sold counts against its edition. */
    data class Item(val name: String, val price: Double, val qty: Int, val pieceId: Long = 0)

    fun items(stored: String): List<Item> = stored.lines().mapNotNull { l -> l.split("|").takeIf { it.size in 3..4 }?.let { p -> Item(p[0], p[1].toDoubleOrNull() ?: 0.0, p[2].toIntOrNull() ?: 0, p.getOrNull(3)?.toLongOrNull() ?: 0) } }.filter { it.name.isNotBlank() }
    fun itemsText(items: List<Item>): String = items.joinToString("\n") { "${it.name.replace("|", "/")}|${it.price}|${it.qty}" + (if (it.pieceId > 0) "|${it.pieceId}" else "") }

    fun tally(stored: String): Map<String, Int> = stored.split(",").mapNotNull { p -> p.substringBeforeLast(":").takeIf { it.isNotBlank() }?.let { k -> p.substringAfterLast(":").toIntOrNull()?.let { k to it } } }.toMap()
    fun bump(stored: String, key: String, by: Int): String = tally(stored).toMutableMap().also { val n = ((it[key] ?: 0) + by).coerceAtLeast(0); if (n == 0) it.remove(key) else it[key] = n }.entries.joinToString(",") { "${it.key}:${it.value}" }

    /** The day in money: what came in, minus the booth fee. Keys are "m:<name>" for multiples and "w:<id>" for originals. */
    fun total(tally: Map<String, Int>, items: List<Item>, originals: Map<Long, Double>): Double =
        tally.entries.sumOf { (k, n) -> when { k.startsWith("m:") -> (items.firstOrNull { "m:" + it.name == k }?.price ?: 0.0) * n; k.startsWith("w:") -> originals[k.removePrefix("w:").toLongOrNull()] ?: 0.0; else -> 0.0 } }

    /** The usual starting set for a first market: something under 10, something under 30, and the originals. */
    val starter = listOf(Item("Cards", 5.0, 20), Item("Small prints (5 x 7)", 15.0, 15), Item("Large prints (11 x 14)", 35.0, 8))
}

/** A show with other artists: their pieces from a plain list, the costs shared, and the note that brings them in. */
object GroupShow {
    /** "Title, medium, 12 x 16 in, $250" per line, any part left out; read into pieces for labels and the price list. */
    fun pieces(list: String): List<com.emotionalleaves.studioagent.data.Artwork> = list.lines().map { it.trim().trimStart('-', '*', '•', ' ') }.filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return@mapNotNull null
        val size = parts.firstNotNullOfOrNull { Regex("(\\d+(?:\\.\\d+)?)\\s*[x×]\\s*(\\d+(?:\\.\\d+)?)").find(it) }
        val price = parts.firstNotNullOfOrNull { p -> Regex("^\\$\\s?(\\d[\\d,]*(?:\\.\\d{2})?)$|^(\\d[\\d,]*)\\s*(usd|dollars)$", RegexOption.IGNORE_CASE).find(p)?.let { m -> (m.groupValues[1].ifBlank { m.groupValues[2] }).replace(",", "").toDoubleOrNull() } }
        val nfs = parts.any { it.equals("nfs", true) || it.equals("not for sale", true) }
        val rest = parts.drop(1).filter { p -> !(size != null && p.contains(size.value)) && !Regex("^\\$|usd|dollars|^nfs$|not for sale", RegexOption.IGNORE_CASE).containsMatchIn(p) }
        com.emotionalleaves.studioagent.data.Artwork(title = parts[0], medium = rest.firstOrNull().orEmpty(), widthIn = size?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0,
            heightIn = size?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0, price = price ?: 0.0, nfs = nfs, status = "documented")
    }

    fun share(totalCosts: Double, artists: Int): Double = if (artists <= 0) totalCosts else Math.round(totalCosts / artists * 100) / 100.0

    fun invite(you: String, title: String, venue: String, dates: String, others: Int): String =
        "Hi! I am putting on a small group show" + (if (title.isNotBlank()) ", $title," else "") + (if (venue.isNotBlank()) " at $venue" else "") + (if (dates.isNotBlank()) ", $dates" else "") + ", " +
            "and I would love to have your work in it" + (if (others > 1) " alongside ${others - 1} other artist${if (others > 2) "s" else ""}" else "") + ".\n\n" +
            "If you are in, could you send me, for two or three pieces:\n" +
            "Title, medium, size, price (or NFS), one per line, like: Night Shift, gouache on paper, 9 x 12 in, $250\n" +
            "and a good photo of each, plus two sentences about your work.\n\n" +
            "We would split the costs (printing, wine, anything the venue asks) evenly, and each keep our own sales. I will make the labels, the price list and the flyer.\n\n" +
            "Thank you!\n$you"
}

/** The physical checks before a piece leaves the studio, by what it is. Ticks are kept as bits per piece. */
object HangReady {
    fun items(format: String, framed: Boolean): List<String> = buildList {
        add("Signed (front or back)")
        if (format == "print") { add("Printed on archival paper and checked in daylight"); add("Numbered, if it is an edition"); add("Certificate of authenticity printed") }
        else add("Surface dry all the way through (and varnish cured, if varnished)")
        if (framed || format == "print") add("Frame and glazing clean, nothing loose inside")
        add("Hanging hardware on: D-rings and wire, or what the show asks for")
        add("Label on the back")
        add("Condition photo taken, front and back")
    }

    fun bits(stored: String, id: Long): Int = stored.split(",").firstOrNull { it.startsWith("$id:") }?.substringAfter(":")?.toIntOrNull() ?: 0

    fun set(stored: String, id: Long, index: Int, on: Boolean): String {
        val b = bits(stored, id).let { if (on) it or (1 shl index) else it and (1 shl index).inv() }
        val rest = stored.split(",").filter { it.isNotBlank() && !it.startsWith("$id:") }
        return (rest + (if (b != 0) listOf("$id:$b") else emptyList())).joinToString(",")
    }
}

/** Sending work to a show far away: the box, the weight a carrier will charge for, and the steps, counted back from delivery. */
object Shipping {
    /** Outside size of a double box: 2 inches of foam around the piece, then 2 more for the outer box. */
    fun box(wIn: Double, hIn: Double, dIn: Double, framed: Boolean): Triple<Int, Int, Int> {
        val d = if (dIn > 0) dIn else if (framed) 1.5 else 0.5
        fun r(x: Double) = Math.ceil(x).toInt()
        return Triple(r(maxOf(wIn, hIn) + 8), r(minOf(wIn, hIn) + 8), r(d + 8))
    }

    /** About what it weighs packed, in pounds: framed work with glazing is heavy, paper is light. */
    fun weightLb(wIn: Double, hIn: Double, framed: Boolean, canvas: Boolean): Double {
        val area = wIn * hIn
        val piece = when { framed -> 2 + area * 0.012; canvas -> 1 + area * 0.006; else -> 0.5 + area * 0.002 }
        return Math.round((piece + 3 + area * 0.004) * 10) / 10.0
    }

    /** Carriers charge the bigger of the real weight and length x width x height / 139 (inches, pounds). */
    fun billableLb(box: Triple<Int, Int, Int>, actual: Double): Int = Math.ceil(maxOf(actual, box.first * box.second * box.third / 139.0)).toInt()

    fun steps(deliver: LocalDate, today: LocalDate): List<Pair<String, LocalDate>> = listOf(
        "Get a box, foam and corner protectors" to deliver.minusDays(16),
        "Swap glass for acrylic if the piece is framed (most shows ask for it when shipped)" to deliver.minusDays(14),
        "Take condition photos, front and back" to deliver.minusDays(11),
        "Pack it: a clean sheet over the face, corners, foam, then the box in a box" to deliver.minusDays(11),
        "Ship it with tracking, insured at its price" to deliver.minusDays(10),
        "Send the venue the tracking number" to deliver.minusDays(10),
        "Check the return: a prepaid label in the box, or what the show arranges" to deliver.minusDays(10)
    ).map { (t, d) -> t to (if (d.isBefore(today)) today else d) }
}

/** A photographed receipt, read: the total, the shop, and what kind of cost it most likely is. */
object Receipts {
    data class Read(val total: Double, val shop: String, val kind: String)

    private val money = Regex("(?<![\\d.])\\$?\\s?(\\d{1,5}(?:,\\d{3})*[.,]\\d{2})(?!\\d)")

    fun read(text: String): Read {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        fun amounts(l: String) = money.findAll(l).mapNotNull { it.groupValues[1].replace(",", "").let { v -> if (v.length > 3 && v[v.length - 3] == '.') v.toDoubleOrNull() else v.replace(Regex("(\\d)(\\d{2})$"), "$1.$2").toDoubleOrNull() } }.toList()
        // The line that says total (not subtotal), or the line after it when the amount sits below; else the largest amount.
        var total = 0.0
        for ((i, l) in lines.withIndex()) {
            val low = l.lowercase()
            if (Regex("\\b(total|amount due|balance due|grand total)\\b").containsMatchIn(low) && !low.contains("subtotal") && !low.contains("sub total")) {
                val a = amounts(l).lastOrNull() ?: lines.getOrNull(i + 1)?.let { amounts(it).lastOrNull() }
                if (a != null && a > 0) { total = a; if (low.contains("grand") || low.contains("due")) break }
            }
        }
        if (total == 0.0) total = lines.flatMap { amounts(it) }.maxOrNull() ?: 0.0
        val shop = lines.firstOrNull { l -> l.count { it.isLetter() } >= 3 && !Regex("(?i)receipt|welcome|thank|store #|^\\d").containsMatchIn(l) }?.take(40).orEmpty()
        val all = text.lowercase()
        val kind = when {
            Regex("blick|michaels|jerry'?s|utrecht|hobby lobby|joann|artist ?& ?craftsman|cheap joe|plaza art|paint|canvas|brush|gesso|watercolor|acrylic|sketchbook").containsMatchIn(all) -> "materials"
            Regex("\\bups\\b|fedex(?! office)|usps|postal|shipping|uline|postage").containsMatchIn(all) -> "shipping"
            Regex("frame|framing|mat board|matboard").containsMatchIn(all) -> "framing"
            Regex("print|fedex office|staples|copies|giclee|photo lab|mpix|bay photo").containsMatchIn(all) -> "print"
            Regex("entry fee|call for|submission|jury fee").containsMatchIn(all) -> "fee"
            else -> "other"
        }
        return Read(total, shop, kind)
    }
}

/**
 * "Too good to be true?" for a message, invitation or offer. Artists starting out are targeted by a few
 * well-known schemes: the overpaying buyer, paid "features", pay-to-show dressed as an honor, and urgency.
 * Each sign found is named in plain words with what to do. Rules only, nothing sent anywhere.
 */
object Offers {
    data class Sign(val serious: Boolean, val what: String)

    fun check(text: String): List<Sign> {
        val l = text.lowercase().replace(Regex("\\s+"), " ")
        val out = mutableListOf<Sign>()
        fun has(vararg p: String) = p.any { Regex(it).containsMatchIn(l) }
        if (has("(cashier'?s|certified|personal|bank) (check|cheque)", "\\b(check|cheque) (for|of) (more|the full|an extra)", "refund (the|any) (difference|balance|excess)", "send (back|the rest|the difference)", "overpa(y|id|yment)"))
            out += Sign(true, "A check for more than the price, with the rest to be sent back, is the most common scam aimed at artists. The check bounces days later and the money you sent is gone. Only accept payment that has fully cleared, never refund an overpayment.")
        if (has("my (shipper|mover|moving company|agent|assistant)( who)? will (pick|collect|come)", "(shipper|mover|courier).{0,40}pick ?up", "(shipping|moving) company (will|to) (pick|collect)", "arrange (the )?(pick ?up|shipping) (myself|with my)"))
            out += Sign(true, "A buyer who insists on their own shipper or mover is a known pattern with overpayment scams. Ship it yourself once the payment has cleared, or meet in person.")
        if (has("gift cards?", "wire (transfer|the money)", "western union", "moneygram", "crypto|bitcoin|usdt", "zelle (me|to)"))
            out += Sign(true, "Gift cards, wires, crypto and instant transfers cannot be reversed. A real gallery or buyer will not ask for them.")
        if (has("(feature|featured|publish|publication|interview|listing|placement|promotion) (fee|cost|price|package)", "(fee|cost|price) (for|to be) (featured|published|included)", "(magazine|catalog|book|publication).{0,60}\\$\\s?\\d", "\\$\\s?\\d.{0,60}(magazine|catalog|book|publication|feature)"))
            out += Sign(true, "Paying to be in a magazine, book or \"feature\" is advertising, not recognition. Jurors and galleries do not count it.")
        if (CallReader.payToShow(l) || has("exhibition (fee|package)", "participation fee", "booth (fee|rental).{0,40}(venice|paris|miami|new york|london|basel|biennale)", "(venice|florence|paris|barcelona|london|new york|miami).{0,80}(fee|package|participation)"))
            out += Sign(true, "The fee buys the place, not a juror's choice. These shows are rarely seen by buyers or curators, and they do not count the way a juried show does.")
        if (has("(discovered|came across|found|noticed|saw) your (work|art|profile|page|portfolio|instagram)", "we (have been|were) (following|impressed)", "selected you", "you have been (chosen|selected|nominated)"))
            out += Sign(false, "Unasked-for praise that leads to an offer is how most of these begin. Real invitations usually come from people you have met or applied to.")
        if (has("(only|just) \\d+ (spots|places|spaces) (left|remaining)", "limited (spots|places|time)", "within (24|48|72) hours", "(respond|reply|confirm|pay) (today|immediately|asap|right away)", "offer (expires|ends)"))
            out += Sign(false, "Pressure to decide fast is a sales tactic. A real opportunity can wait a day while you look it up.")
        if (has("award|prize|honou?r|certificate|diploma") && has("fee|cost|\\$\\s?\\d|payment|pay "))
            out += Sign(false, "An award that costs money to receive is a product, not an honor.")
        if (has("(social security|ssn|bank account number|routing number|passport|date of birth)"))
            out += Sign(true, "Never send ID numbers or bank details to someone you do not know.")
        if (has("@(gmail|yahoo|hotmail|outlook|aol)\\.com") && has("gallery|museum|foundation|curator|magazine"))
            out += Sign(false, "A gallery or magazine writing from a free email address is worth a second look: check that the place exists and that the address is on its own website.")
        return out
    }

    fun verdict(signs: List<Sign>): String = when {
        signs.any { it.serious } -> "Be careful with this one."
        signs.size >= 2 -> "A few things to check before you answer."
        signs.size == 1 -> "Mostly fine. One thing to check."
        else -> "Nothing here matches the usual schemes. Still look the place up before sending money or work."
    }
}

// ---------------------------------------------------------------- drafts without AI

/** Builds a first draft from the artist's own interview answers and CV facts. No AI, nothing invented. */
object Drafts {
    /** The words your statement leans on most (not the small ones), as a start for your themes. */
    fun themeWords(statement: String, n: Int = 4): List<String> {
        val stop = setOf("about", "after", "again", "always", "because", "before", "being", "between", "could", "every", "first", "people", "really", "something", "their", "there", "these", "thing", "things", "those", "through", "where", "which", "while", "would", "work", "works", "making", "make", "piece", "pieces", "paint", "painting", "drawing", "draw", "artist", "other", "still", "around", "often", "never", "until", "small", "large", "little")
        return Regex("[a-z]{5,}").findAll(statement.lowercase()).map { it.value }.filter { it !in stop }
            .groupingBy { it.removeSuffix("s") }.eachCount().entries.sortedWith(compareBy({ -it.value }, { statement.lowercase().indexOf(it.key) })).map { it.key }.take(n)
    }

    /** Show titles without help: the series name, a phrase from the statement, and the strongest piece's title. */
    fun showTitles(series: String, statement: String, firstTitle: String): List<String> {
        val phrase = statement.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.let { s -> Regex("\\b(at|in|before|after|during|from) [a-z ]{3,30}", RegexOption.IGNORE_CASE).find(s)?.value }?.trim()?.replaceFirstChar { it.uppercase() }
        return listOfNotNull(series.ifBlank { null }, phrase, firstTitle.ifBlank { null }?.let { "$it and Other Pieces" }, "New Work").distinct()
    }

    /** A short profile line for Instagram and the like: what you make, where, and a hook from your own statement. Under 150 characters. */
    fun profileLine(mediums: String, city: String, statement: String): String {
        val what = mediums.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(2).joinToString(" and ").ifBlank { "art" }
        val hook = statement.split(Regex("(?<=[.!?])\\s+")).firstOrNull { it.length in 10..70 }?.trim()?.trimEnd('.').orEmpty()
        val line = "Artist" + (if (city.isNotBlank()) " in ${city.substringBefore(",").trim()}" else "") + ". " + what.replaceFirstChar { it.uppercase() } + "." + (if (hook.isNotBlank()) " $hook." else "") + " Shows and news below."
        return if (line.length <= 150) line else line.take(147).trimEnd() + "..."
    }

    /** The same, without social media: a page of your own and an email address do the job quietly. */
    val findableQuiet = listOf(
        "Your name, spelled the same way everywhere (CV, labels, online)",
        "A page with your name that shows your work (your web page, or a portfolio site)",
        "Your email address on that page",
        "Your CV and statement on that page, or ready to send",
        "Your newest strong pieces on it"
    )

    /** What someone finds when they look you up before choosing your work: the checks, in the order that matters. */
    val findable = listOf(
        "Your name, spelled the same way everywhere (CV, labels, online)",
        "A page with your name that shows your work (your web page, or a portfolio site)",
        "An Instagram (or similar) with at least nine posts of finished work",
        "A profile line that says what you make and where",
        "Your web page or email in that profile",
        "Your newest strong piece pinned or posted in the last month"
    )

    /**
     * A description of the picture for people who cannot see it: what it is, its size, its main colors and
     * light, and what it shows when your notes say. Nothing is guessed about the subject.
     */
    fun altText(title: String, medium: String, size: String, colors: List<String>, light: String, note: String): String {
        val kind = medium.ifBlank { "artwork" }.lowercase()
        val first = (if (kind.first() in "aeiou") "An " else "A ") + kind + (if (size.isNotBlank()) ", $size," else "") + (if (title.isNotBlank()) " titled $title." else ".")
        val shows = note.split(Regex("(?<=[.!?])\\s+")).firstOrNull { it.length in 12..200 }?.trim()?.let { " " + sentence(it) }.orEmpty()
        val col = if (colors.isNotEmpty()) " Mostly " + colors.take(3).let { if (it.size == 1) it[0] else it.dropLast(1).joinToString(", ") + " and " + it.last() } + (if (light.isNotBlank()) ", $light." else ".") else ""
        return (first + shows + col).trim()
    }

    /** A listing announcement: headline, what, when, where, free and open to all, a line about the artist, contact. */
    fun announcement(artist: String, title: String, venue: String, city: String, dates: String, opening: String, bio: String, media: String, count: Int, email: String, website: String): String {
        val who = artist.ifBlank { "A local artist" }
        val head = (if (title.isNotBlank()) "$title: " else "") + "new work by $who" + (if (venue.isNotBlank()) " at $venue" else "")
        val body = buildList {
            val show = "a show of " + (if (count > 0) "$count " else "") + "new " + (if (media.isNotBlank()) "$media " else "") + (if (count == 1) "work" else "works") + " by $who."
            add(if (venue.isNotBlank()) "$venue presents " + (if (title.isNotBlank()) "\"$title,\" " else "") + show else show.replaceFirstChar { it.uppercase() })
            if (dates.isNotBlank()) add("On view $dates" + (if (city.isNotBlank()) ", in $city" else "") + ".")
            if (opening.isNotBlank()) add("Opening reception $opening. Free and open to everyone.")
            else add("Free and open to everyone.")
            if (bio.isNotBlank()) add(bio.trim())
            add("For more: " + listOf(email, website).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "contact the venue" } + ".")
        }
        return head + "\n\n" + body.joinToString("\n\n")
    }

    /** Where a small show's news goes, with what each wants. Most take a short form or an email two to three weeks ahead. */
    val listingPlaces = listOf(
        "The local newspaper's arts or events calendar" to "Look for 'submit an event' on their site. Two to three weeks ahead.",
        "The alt-weekly or city magazine" to "They often run free listings; some pick a show to write about.",
        "Community or college radio" to "Many read out local events; send the announcement by email.",
        "The venue itself" to "Ask them to post it on their site and social accounts, and to their own email list.",
        "Your campus or workplace newsletter" to "Staff and student shows are exactly what they want to share.",
        "Library and coffee shop boards" to "Pin the flyer; take a photo of it for your records.",
        "An event on Facebook, Instagram or a local events site" to "One post with the picture, the date and the address.")

    fun sentence(s: String): String {
        val t = s.trim().replace(Regex("\\s+"), " ")
        if (t.isBlank()) return ""
        val cap = t.replaceFirstChar { it.uppercase() }
        return if (cap.last() in ".!?") cap else "$cap."
    }

    /** The answers in a readable order, cut to the word target at a sentence end. */
    fun statement(answers: Map<String, String>, target: Int): String {
        val order = listOf("words", "what", "why", "how", "viewer", "looking")
        val seen = mutableSetOf<String>()
        val parts = (order + answers.keys.filter { it.startsWith("own:") }).mapNotNull { k ->
            sentence(answers[k].orEmpty()).takeIf { it.isNotBlank() && seen.add(it.lowercase()) }
        }
        return Texts.trimToWords(parts.joinToString(" "), target)
    }

    fun bio(first: String, last: String, city: String, mediums: String, facts: List<Fact>, firstPerson: Boolean, target: Int): String {
        val name = "$first $last".trim().ifBlank { "The artist" }
        val who = if (firstPerson) "I am" else "$name is"
        val whose = if (firstPerson) "My" else (first.ifBlank { "Their" } + (if (first.isBlank()) "" else "'s"))
        val parts = mutableListOf("$who an artist" + (if (city.isNotBlank()) " based in $city" else "") + (if (mediums.isNotBlank()) ", working in $mediums" else "") + ".")
        facts.filter { it.category == "education" }.maxByOrNull { it.year }?.let { parts += (if (firstPerson) "I studied: " else "Education: ") + sentence(it.text) }
        val shows = facts.filter { it.category in setOf("solo", "group", "invited") }.sortedByDescending { it.year }.take(3)
        if (shows.isNotEmpty()) parts += "$whose work has been shown in " + shows.joinToString("; ") { it.text + (if (it.year > 0) " (${it.year})" else "") } + "."
        facts.filter { it.category in setOf("award", "grant", "residency") }.maxByOrNull { it.year }?.let { parts += sentence(it.text + (if (it.year > 0) " (${it.year})" else "")) }
        return Texts.trimToWords(parts.joinToString(" "), target)
    }
}


// ---------------------------------------------------------------- is a series ready to show

object Readiness {
    val printSteps = listOf(
        "File ready" to "Tap Export pictures (under Pictures and papers) and keep the file ending in _original. That is the one a lab prints from.",
        "Proof ordered" to "Order one small test print, called a proof, from a fine-art print lab. Ask for matte cotton rag paper. Ask a print lab for a quote (under Pictures and papers) writes the request for you.",
        "Proof checked" to "Look at the proof in daylight. If the colors and darks are close to your screen, it passes. If it is too dark, brighten the file a little and proof once more. Close is good enough; a print never matches a lit screen exactly.",
        "Final print made" to "Order the final at full size. When it arrives, sign and number it in pencil in the bottom margin, like 1/10.",
        "Framed and ready to hang" to "A plain frame, white mat and hanging wire is what shows expect. Many accept only wired work."
    )

    /** Objective checks. [ready] are the series' finished pieces that have an image. */
    fun series(ready: List<com.emotionalleaves.studioagent.data.Artwork>, target: Int, hasStatement: Boolean): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        out += "At least $target finished pieces with images (${ready.size} so far)" to (ready.size >= target)
        out += "Every finished piece has a title, year and size" to (ready.isNotEmpty() && ready.all { it.title.isNotBlank() && it.year > 0 && it.widthIn > 0 && it.heightIn > 0 })
        out += "Every finished piece has a price or is marked not for sale" to (ready.isNotEmpty() && ready.all { it.price > 0 || it.nfs })
        val longSides = ready.map { maxOf(it.widthIn, it.heightIn) }.filter { it > 0 }
        out += "Sizes belong together (the largest is no more than twice the smallest)" to (longSides.isNotEmpty() && longSides.max() <= longSides.min() * 2)
        val prints = ready.filter { it.format == "print" }
        if (prints.isNotEmpty()) {
            out += "Every print file is sharp enough at its size (200 ppi or more)" to prints.all { Texts.ppi(it.pixelW, it.pixelH, it.widthIn, it.heightIn) >= 200 }
            out += "Every print is printed and framed" to prints.all { it.printStage >= 5 }
        }
        out += "A statement for this series is written and approved" to hasStatement
        return out
    }
}

// ---------------------------------------------------------------- empty your head
/**
 * Everything in your head, one line each, sorted without a single decision from you: a job to do
 * (with its day when the line names one), something to buy, or an idea for the work.
 */
object Dump {
    enum class Kind(val label: String) { TASK("To do"), SUPPLY("To buy"), IDEA("Idea") }
    data class Line(val text: String, val kind: Kind, val day: LocalDate?)

    private val doing = listOf("email", "call", "text", "send", "ask", "finish", "apply", "ship", "frame", "pay", "book",
        "print", "post", "varnish", "sign", "photograph", "measure", "write", "check", "deliver", "pick up", "drop off",
        "enter", "register", "update", "reply", "answer", "return", "renew", "cancel", "order", "submit", "visit", "wrap",
        "pack", "mail", "invoice", "remind", "tell", "thank", "fix", "clean", "sort", "scan", "upload", "fill in", "follow up")
    private val buying = listOf("buy ", "get more ", "need more ", "out of ", "run out of ", "ran out of ", "almost out of ", "restock ")
    private val days = DayOfWeek.values().associateBy { it.name.lowercase() }

    fun sort(text: String, today: LocalDate = LocalDate.now()): List<Line> =
        text.lines().map { it.trim().trimStart('-', '*', '•', ' ').trim() }.filter { it.length > 1 }.map { line(it, today) }

    fun line(raw: String, today: LocalDate = LocalDate.now()): Line {
        val low = raw.lowercase()
        buying.firstOrNull { low.startsWith(it) }?.let { b ->
            return Line(raw.substring(b.length).trim().replaceFirstChar { it.uppercase() }, Kind.SUPPLY, null)
        }
        val day = dayIn(low, today)
        val isTask = doing.any { low.startsWith("$it ") || low == it } || day != null || low.startsWith("don't forget") || low.startsWith("remember to")
        val clean = raw.replace(Regex("^(don't forget to|don't forget|remember to)\\s+", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.uppercase() }
        return Line(clean, if (isTask) Kind.TASK else Kind.IDEA, day)
    }

    /** "tomorrow", "today", "tonight", a weekday ("friday", "by friday", "on friday"), or "next week" (its Monday). */
    fun dayIn(low: String, today: LocalDate): LocalDate? {
        val words = low.split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        if ("today" in words || "tonight" in words) return today
        if ("tomorrow" in words) return today.plusDays(1)
        if (low.contains("next week")) return today.plusDays((8 - today.dayOfWeek.value).toLong())
        words.firstNotNullOfOrNull { days[it] }?.let { d ->
            val ahead = ((d.value - today.dayOfWeek.value) + 7) % 7
            return today.plusDays(if (ahead == 0) 7L else ahead.toLong())
        }
        return null
    }
}

// ---------------------------------------------------------------- teaching a class
/**
 * A beginner class at a library, community center or cafe: an outline that fits the time, what to bring for the
 * number of people, a fair fee, and the short email that offers it. Teaching pays, and it puts your name in front of
 * a room of people who now care about your work.
 */
object Workshop {
    data class Part(val minutes: Int, val what: String)

    fun kindOf(medium: String): String = medium.lowercase().let { m ->
        when {
            listOf("watercolor", "watercolour").any { it in m } -> "watercolor"
            listOf("acrylic", "oil", "gouache", "paint").any { it in m } -> "painting"
            listOf("print", "lino", "block").any { it in m } && "digital" !in m && "archival" !in m -> "printmaking"
            listOf("collage", "paper").any { it in m } -> "collage"
            listOf("digital", "procreate", "ipad", "tablet").any { it in m } -> "phone drawing"
            else -> "drawing"
        }
    }

    fun title(kind: String): String = when (kind) {
        "watercolor" -> "Loose watercolor for complete beginners"
        "painting" -> "Your first small painting"
        "printmaking" -> "Carve and print a card"
        "collage" -> "Collage from what is in your recycling"
        "phone drawing" -> "Drawing on your phone"
        else -> "Drawing what is in front of you"
    }

    /** The class in parts that add up to [minutes]: a welcome, a demo, guided work, free work, a look at everyone's. */
    fun outline(kind: String, minutes: Int): List<Part> {
        val m = minutes.coerceIn(45, 180)
        val welcome = 5; val demo = (m * 0.15).toInt().coerceAtLeast(8); val share = (m * 0.12).toInt().coerceAtLeast(8)
        val guided = ((m - welcome - demo - share) * 0.55).toInt(); val free = m - welcome - demo - share - guided
        val doing = when (kind) {
            "watercolor" -> "a three-color sky, then one simple object" to "a small postcard painting of their own"
            "painting" -> "blocking in three big shapes" to "one small piece from a photo they brought"
            "printmaking" -> "carving a simple shape and inking it" to "a card or two to take home"
            "collage" -> "tearing and arranging by value" to "a small collage from a theme they choose"
            "phone drawing" -> "layers, a pencil, and tracing light and dark" to "a drawing of something on the table"
            else -> "seeing big shapes, then light and dark" to "a drawing of a still life on the table"
        }
        return listOf(Part(welcome, "Welcome, names, and what everyone hopes to make"), Part(demo, "You show it once, slowly: " + doing.first),
            Part(guided, "Everyone tries it with you, step by step"), Part(free, "Their own: " + doing.second),
            Part(share, "Everyone's on one table, one kind thing about each"))
    }

    /** What to bring for [people], plus a couple spare. */
    fun supplies(kind: String, people: Int): List<String> {
        val n = people + 2
        return when (kind) {
            "watercolor" -> listOf("$n watercolor pads or postcard blocks", "$n travel watercolor sets", "$n round brushes, size 8", "${(n + 1) / 2} water jars", "Paper towels")
            "painting" -> listOf("$n small canvas boards, 8 x 10", "Acrylics: white, black and the three primaries, shared", "$n brushes, flat and round", "${(n + 1) / 2} palettes or paper plates", "Paper towels and aprons")
            "printmaking" -> listOf("$n soft carving blocks", "${(n + 1) / 2} carving tools", "Block ink and ${(n + 2) / 3} brayers", "$n blank cards", "A wooden spoon each")
            "collage" -> listOf("A stack of magazines and colored paper", "$n glue sticks", "$n pairs of scissors", "$n card bases, 8 x 10")
            "phone drawing" -> listOf("A free drawing app everyone installs before", "A few simple objects to draw", "Optional: $n cheap styluses")
            else -> listOf("$n sketchbooks or sheets of good paper", "$n pencils (2B and 4B)", "$n kneaded erasers", "A few simple objects to draw, lit from one side")
        }
    }

    /** A fair fee: a flat rate for the time, the supplies on top. Libraries often pay flat; a cafe class can charge per person. */
    fun fee(minutes: Int, people: Int, hourly: Double = 40.0, perPersonSupplies: Double = 6.0): Pair<Double, Double> {
        val flat = (hourly * (minutes + 30) / 60.0 / 5).roundToInt() * 5.0 // the half hour to set up and clean up counts too
        val perPerson = ((flat / people.coerceAtLeast(1)) + perPersonSupplies).let { (it / 5).roundToInt() * 5.0 }.coerceAtLeast(10.0)
        return flat to perPerson
    }

    fun email(name: String, venue: String, title: String, minutes: Int, people: Int, website: String, feeText: String = ""): String =
        "Hello,\n\nI'm $name, a local artist. I'd love to teach a beginner class at ${venue.ifBlank { "your space" }}: \"$title\", " +
            "about ${if (minutes % 60 == 0) "${minutes / 60} hour${if (minutes > 60) "s" else ""}" else "$minutes minutes"}, for up to $people people, no experience needed. " +
            "I bring every supply and set up and clear away myself." + (if (feeText.isNotBlank()) " My fee for a class like this is $feeText, supplies included." else "") + "\n\nCould we find an evening or a weekend afternoon that suits you? " +
            "I'm happy to share an outline and a couple of pictures of my work" + (if (website.isNotBlank()) " (there are more at $website)" else "") + ".\n\nThank you,\n$name"
}

// ---------------------------------------------------------------- no's that count
/**
 * Counting sends, not yeses: the one number fully in your hands. Each no is a send that counts toward the year's aim,
 * and the usual rate (several no's for every yes, for nearly everyone) is said plainly so a no is not read as a verdict.
 */
object Attempts {
    fun afterNo(sent: Int, no: Int, yes: Int, aim: Int): String {
        val nth = when (no % 100) { 11, 12, 13 -> "${no}th"; else -> when (no % 10) { 1 -> "${no}st"; 2 -> "${no}nd"; 3 -> "${no}rd"; else -> "${no}th" } }
        val toGo = (aim - sent).coerceAtLeast(0)
        return "Your $nth no this year, from ${Texts.count(sent, "send")}" + (if (yes > 0) " and ${Texts.count(yes, "yes", "yeses")}" else "") + ". " +
            "Most artists hear several no's for every yes, so this is the usual road, not a verdict. " +
            (if (toGo > 0) "It counts: ${Texts.count(toGo, "send")} to go toward your $aim this year." else "You have already sent all $aim you aimed for this year. Anything more is extra.")
    }

    fun tile(sent: Int, aim: Int): String = if (sent >= aim) "sent this year; aim of $aim met" else "sent this year, aiming for $aim"
}

// ---------------------------------------------------------------- your people
/**
 * Other artists, in person: they pass on calls, swap honest feedback, and a room of people drawing makes starting easy.
 * The usual places to find them, what each is like for a beginner, and the search that finds the local one.
 */
object People {
    data class Place(val key: String, val name: String, val what: String, val search: String)

    val places = listOf(
        Place("life", "A drop-in life drawing session", "Usually a small fee at the door; bring a pad and a pencil. Nobody looks at your drawings, and two hours of drawing goes by fast.", "drop-in life drawing"),
        Place("sketch", "Urban Sketchers", "Free, any level, meets outdoors at a different spot each time and shares drawings at the end. Local groups are called chapters.", "Urban Sketchers"),
        Place("league", "Your local art league or association", "Members' shows are often open to every member, beginners included: a first show without a jury.", "art league OR art association"),
        Place("crit", "A critique group or open studio night", "A few artists showing work in progress and saying what they see. Kind ones exist; it is fine to leave one that is not.", "artist critique group OR open studio night"),
        Place("library", "An art club at a library or community center", "Free or nearly, often in the evening, and a quiet way in.", "art club library OR community center")
    )

    fun query(p: Place, city: String): String = (p.search + " " + city).trim()

    /** A short hello to an artist you met, so one meeting turns into knowing someone. */
    fun hello(them: String, where: String, me: String): String =
        "Hi ${them.ifBlank { "there" }}, it was good to draw next to you${if (where.isNotBlank()) " at $where" else ""}. " +
            "I'm trying to go most weeks. If you hear of any open calls or shows for people starting out, I'd love to know, and I'll pass on any I find. $me"
}

// ---------------------------------------------------------------- answers you have written before
/**
 * Entry forms ask the same few things in different words ("Tell us about your practice", "Describe your work").
 * An answer written once is offered again when a new form asks something close to it.
 */
object AnswerBank {
    data class Past(val question: String, val answer: String, val from: String)

    private val filler = setOf("a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "your", "you", "us", "we", "our", "is", "are", "be",
        "what", "how", "why", "please", "tell", "describe", "briefly", "about", "this", "that", "with", "do", "does", "it", "its", "if", "any", "words", "word", "max", "maximum", "up")
    private val same = mapOf("practice" to "work", "artwork" to "work", "artworks" to "work", "pieces" to "work", "piece" to "work", "works" to "work",
        "influences" to "influence", "inspiration" to "influence", "inspires" to "influence", "inspired" to "influence",
        "goals" to "goal", "hope" to "goal", "hopes" to "goal", "plans" to "goal", "plan" to "goal",
        "background" to "bio", "biography" to "bio", "yourself" to "bio", "experience" to "bio",
        "connect" to "fit", "connects" to "fit", "relate" to "fit", "relates" to "fit", "fits" to "fit", "theme" to "fit")

    fun words(q: String): Set<String> = q.lowercase().split(Regex("[^a-z]+")).filter { it.length > 1 && it !in filler }.map { same[it] ?: it }.toSet()

    fun likeness(a: String, b: String): Double {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return (x intersect y).size.toDouble() / (x union y).size
    }

    /** The closest past answer to [question], when it is close enough to be worth offering; the longest wins a tie. */
    fun best(question: String, past: List<Past>): Past? = past.filter { it.answer.isNotBlank() }
        .map { it to likeness(question, it.question) }.filter { it.second >= 0.5 }
        .maxWithOrNull(compareBy({ it.second }, { it.first.answer.length }))?.first
}

// ---------------------------------------------------------------- drop-off day
/** What to have with you when you take work to a show: the wrapping, the paperwork, and the small things that save a second trip. */
object Packing {
    fun list(pieces: Int, framed: Boolean, selfHang: Boolean): List<String> = buildList {
        add(if (pieces == 1) "The piece wrapped: plastic or glassine on the face, then bubble wrap, bubbles out" else "All $pieces pieces wrapped: plastic or glassine on the face, then bubble wrap, bubbles out")
        if (framed) add("Cardboard corners on every frame")
        add("A blanket in the car; pieces standing upright, face to face, never flat with things on top")
        add("Your list of the works with prices, two copies: one for them, one they sign for you")
        add("Their agreement, signed, or a pen to sign it there")
        add("Your phone charged: a photo of each piece as you hand it over")
        add(if (selfHang) "Tape measure, a small level, a pencil, picture hooks and a hammer" else "Asked whether you hang it yourself; if so, a tape measure, level, pencil, hooks and hammer")
        add("The pick-up date and time, on your calendar before you leave")
    }
}

// ---------------------------------------------------------------- a year of making
/** The day, once a year, that you started: what has happened since, counted plainly. */
object Anniversary {
    /** How many years ago [first] was, when [today] is its anniversary (Feb 29 is kept on Feb 28 in other years); otherwise 0. */
    fun years(first: LocalDate, today: LocalDate): Int {
        val y = today.year - first.year
        if (y < 1) return 0
        val day = if (first.monthValue == 2 && first.dayOfMonth == 29 && !today.isLeapYear) LocalDate.of(today.year, 2, 28) else first.withYear(today.year)
        return if (day == today) y else 0
    }

    fun title(years: Int): String = if (years == 1) "A year of making" else "$years years of making"

    fun line(years: Int, sessions: Int, hours: Int, pieces: Int, sends: Int): String =
        (if (years == 1) "A year ago today" else "$years years ago today") + " was your first session. Since then: " +
            listOfNotNull(Texts.count(sessions, "session"), if (hours > 0) Texts.count(hours, "hour") + " of making" else null,
                if (pieces > 0) Texts.count(pieces, "piece") else null, if (sends > 0) Texts.count(sends, "entry", "entries") + " sent" else null).joinToString(", ") +
            ". Every one of them was you showing up."
}

// ---------------------------------------------------------------- firsts
/**
 * The firsts on the way: noticed once, on the day they happen, in a sentence. Not a badge or a streak; a moment
 * to let land before the next thing.
 */
object Firsts {
    data class First(val key: String, val title: String, val line: String)

    data class Counts(val pieces: Int, val pictured: Int, val finished: Int, val sessions: Int, val hourSession: Boolean,
                      val sent: Int, val yes: Int, val sales: Int, val taught: Int, val openings: Int = 0, val venueEmails: Int = 0)

    private val all = listOf(
        First("piece", "Your first piece", "It is in the studio now, which is where every body of work starts."),
        First("picture", "Your first piece photographed", "A good picture is what a juror actually sees. That part is done for this one."),
        First("hour", "A whole hour of making", "An hour in one sitting. That is real studio time, on a working day."),
        First("finished", "Your first finished piece", "Finished is a decision, and you made it."),
        First("ten", "Ten sessions", "Ten times you sat down and started. That habit is the hard part, and you have it."),
        First("opening", "Your first opening", "You walked into a room full of art and the people who show it. That is how people start to know your face."),
        First("venue", "Your first email to a venue", "Most people never send the first one. Whatever they say, the next is easier."),
        First("sent", "Your first entry sent", "It is out in the world now. Whatever the answer, it counts toward the year."),
        First("yes", "Your first yes", "A juror chose your work for a wall. Take a moment with that one."),
        First("sale", "Your first sale", "Someone wanted to live with your work enough to pay for it."),
        First("taught", "Your first class taught", "A room of people learned something from you."))

    fun reached(c: Counts): List<First> = all.filter {
        when (it.key) {
            "piece" -> c.pieces > 0; "picture" -> c.pictured > 0; "hour" -> c.hourSession; "finished" -> c.finished > 0
            "ten" -> c.sessions >= 10; "opening" -> c.openings > 0; "venue" -> c.venueEmails > 0; "sent" -> c.sent > 0; "yes" -> c.yes > 0; "sale" -> c.sales > 0; "taught" -> c.taught > 0
            else -> false
        }
    }

    /** The newest first not yet seen ("key:date" entries in [seen]), shown all of the day it is first noticed. */
    fun toShow(reached: List<First>, seen: String, today: String): First? {
        val m = seen.split(",").filter { it.contains(":") }.associate { it.substringBefore(":") to it.substringAfter(":") }
        return reached.lastOrNull { it.key !in m } ?: reached.lastOrNull { m[it.key] == today }
    }

    fun mark(seen: String, key: String, today: String): String =
        (seen.split(",").filter { it.isNotBlank() && it.substringBefore(":") != key } + "$key:$today").joinToString(",")

    /** On a fresh install with history (a restore), every first already reached is marked seen, so nothing old is celebrated. */
    fun markAll(reached: List<First>, day: String): String = reached.joinToString(",") { "${it.key}:$day" }
}
