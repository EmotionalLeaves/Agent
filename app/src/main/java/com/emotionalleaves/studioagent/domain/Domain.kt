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
    val noReasons: Map<String, Int> = emptyMap(),
    val currency: String = "$"
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
        reasons += if (o.fee <= 0.0) "Free to enter" else "Fee ${p.currency}${"%.0f".format(o.fee)}"

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

    /** How alike two questions are, 0 to 1, by the longer words they share. */
    fun similarity(a: String, b: String): Double {
        fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 3 }.toSet()
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

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
        "collection" to "Collections", "press" to "Press", "publication" to "Publications", "skill" to "Skills"
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
    fun cleanTitle(t: String): String {
        val parts = t.split(" | ", " – ", " — ").map { it.trim() }.filter { it.isNotBlank() && !it.equals("home", true) }
        return (parts.firstOrNull() ?: t).trim()
    }

    fun read(text: String, today: LocalDate, zone: java.time.ZoneId, sourceName: String, now: Long): Opp {
        val l = text.lowercase()
        val url = Regex("https?://[^\\s)>\\]]+").find(text)?.value.orEmpty()
        val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(text)?.value.orEmpty()
        val title = cleanTitle(text.lines().map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("Link:") }.orEmpty()).take(120).ifBlank { "Shared call" }
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


// ---------------------------------------------------------------- drafts without AI

/** Builds a first draft from the artist's own interview answers and CV facts. No AI, nothing invented. */
object Drafts {
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
