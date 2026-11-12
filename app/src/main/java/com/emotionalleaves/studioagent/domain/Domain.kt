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
    val names = listOf("Foundation", "First walls", "Regional", "Recognised", "Established")

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
    val noReasons: Map<String, Int> = emptyMap()
)

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
        if (p.blocked.any { it.isNotBlank() && o.organiser.contains(it.trim(), ignoreCase = true) }) r += "Organiser is on your blocked list"
        val fits = p.routes.isEmpty() || p.routes.any { it == "physical" || (it == "print" && o.acceptsPrints) || (it == "screen" && o.acceptsNewMedia) }
        if (!fits) r += if (p.routes == listOf("screen")) "Does not accept screen-based work" else "Does not accept prints of digital work"
        if (o.guaranteed && o.fee > 0) r += "Pay-to-show"
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
        reasons += if (o.fee <= 0.0) "Free to enter" else "Fee $${"%.0f".format(o.fee)}"

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
        val total = parts.indices.sumOf { parts[it] * w[it] } / w.sum()
        return total.roundToInt().coerceIn(0, 100) to reasons
    }
}

// ---------------------------------------------------------------- week planner

data class Slot(val start: LocalDateTime, val minutes: Int)

/** One studio slot in the artist's own weekly template. day: 1 = Monday to 7 = Sunday. */
data class TemplateSlot(val day: Int, val time: LocalTime, val minutes: Int)

object Planner {
    fun time(s: String, def: LocalTime): LocalTime =
        try { LocalTime.parse(if (s.length == 4) "0$s" else s) } catch (e: Exception) { def }

    fun days(s: String): Set<Int> = s.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** Template text looks like "2@17:00/40;6@10:00/160". */
    fun parseTemplate(s: String): List<TemplateSlot> = s.split(";").mapNotNull { part ->
        val d = part.substringBefore("@").trim().toIntOrNull()
        val m = part.substringAfter("/", "").trim().toIntOrNull()
        if (d == null || m == null || d !in 1..7 || m <= 0) null
        else TemplateSlot(d, time(part.substringAfter("@").substringBefore("/").trim(), LocalTime.of(17, 0)), m)
    }

    fun formatTemplate(l: List<TemplateSlot>): String = l.joinToString(";") { "${it.day}@${it.time}/${it.minutes}" }

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

    fun fitWord(score: Int): String = when { score >= 75 -> "Strong fit"; score >= 60 -> "Good fit"; score >= 45 -> "Fair fit"; else -> "Weak fit" }

    fun words(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotBlank() }

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
    fun fileName(lastName: String, title: String, year: Int, medium: String, wIn: Double, hIn: Double, ext: String = "jpg"): String {
        val parts = mutableListOf(clean(lastName).ifBlank { "Artist" }, clean(title).ifBlank { "Untitled" })
        if (year > 0) parts += year.toString()
        if (medium.isNotBlank()) parts += clean(medium)
        if (wIn > 0 && hIn > 0) parts += "${dim(hIn)}x${dim(wIn)}in"
        return parts.joinToString("_") + "." + ext
    }

    fun dim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)

    fun dims(wIn: Double, hIn: Double, dIn: Double = 0.0): String {
        if (wIn <= 0 || hIn <= 0) return ""
        val inches = "${dim(hIn)} x ${dim(wIn)}" + (if (dIn > 0) " x ${dim(dIn)}" else "") + " in"
        val cm = "${dim1(hIn * 2.54)} x ${dim1(wIn * 2.54)}" + (if (dIn > 0) " x ${dim1(dIn * 2.54)}" else "") + " cm"
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
        "collection" to "Collections", "press" to "Press", "publication" to "Publications", "skill" to "Skills"
    )
    val factCategories = cvOrder.map { it.first }

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

    fun cvText(name: String, contact: String, facts: List<Fact>): String =
        cv(name, contact, facts).joinToString("\n\n") { (h, l) -> (listOf(h.uppercase()) + l).joinToString("\n") }
}
