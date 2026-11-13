package com.emotionalleaves.studioagent.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.ai.Ai
import com.emotionalleaves.studioagent.data.Appl
import com.emotionalleaves.studioagent.domain.Shipping
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Ledger
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.domain.CallReader
import com.emotionalleaves.studioagent.domain.CareerStats
import com.emotionalleaves.studioagent.domain.Levels
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Profile
import com.emotionalleaves.studioagent.domain.Rules
import com.emotionalleaves.studioagent.domain.Scoring
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.net.Fetch
import com.emotionalleaves.studioagent.net.GitHub
import com.emotionalleaves.studioagent.net.Mail
import com.emotionalleaves.studioagent.sys.Cal
import com.emotionalleaves.studioagent.sys.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class NextStep(val title: String, val detail: String, val route: String)
data class Pending(val title: String, val detail: String, val route: String)

/** "the 150-word statement", "the 50-word bio": a draft named the way a person would say it. */
fun docWords(type: String, variant: String): String {
    val what = mapOf("statement" to "statement", "bio" to "bio", "series" to "series statement")[type] ?: type
    return if (variant.toIntOrNull() != null) "the $variant-word $what" else "the $what"
}

const val DAY = 86_400_000L

/** The work the app does on its own. Screens and background workers both call into here. */
object Engine {
    private val dao get() = G.dao
    private val st get() = G.settings
    private val ai get() = G.ai
    private val zone get() = ZoneId.systemDefault()

    fun millis(t: LocalDateTime): Long = t.atZone(zone).toInstant().toEpochMilli()
    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** What is left of this month's fee budget. No budget set means only your level's fee cap applies. */
    fun feeLeft(ledger: List<Ledger>): Double = if (st.feeBudget <= 0) Double.MAX_VALUE else (st.feeBudget - feesThisMonth(ledger)).coerceAtLeast(0.0)

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    // ------------------------------------------------------------ career level

    private val shownStates = setOf("documented", "available", "pending", "shown", "sold")
    fun documented(a: Artwork) = a.imagePath.isNotBlank() && a.status in shownStates
    fun available(a: Artwork) = a.imagePath.isNotBlank() && (a.status == "documented" || a.status == "available")

    fun statsOf(works: List<Artwork>, docs: List<Doc>, facts: List<Fact>): CareerStats {
        val best = works.filter { documented(it) && it.seriesId != 0L }.groupBy { it.seriesId }.values.maxOfOrNull { it.size } ?: 0
        val approved = docs.any { it.type == "statement" && it.approved && it.text.isNotBlank() }
        return Levels.stats(facts, best, approved)
    }

    fun rules(): Rules = Rules(
        gateWorks = st.gateWorks.coerceAtLeast(1), gateShows1 = st.gateShows1.coerceAtLeast(1), gateShows2 = st.gateShows2.coerceAtLeast(1),
        feeCaps = listOf(st.feeCap0, st.feeCap1, st.feeCap2, st.feeCap3, st.feeCap3),
        levelOverride = st.levelOverride, showAllLevels = st.showAllLevels, gateOutreach = st.gateOutreach,
        weights = st.weights.split(",").map { it.trim().toIntOrNull() ?: 2 }
    )

    fun levelOf(works: List<Artwork>, docs: List<Doc>, facts: List<Fact>): Int = Levels.level(statsOf(works, docs, facts), rules())

    fun profileOf(works: List<Artwork>, docs: List<Doc>, facts: List<Fact>, ledger: List<Ledger>): Profile = Profile(
        level = levelOf(works, docs, facts), routes = st.routes, rules = rules(), kinds = st.kinds,
        availableWorks = works.count { available(it) },
        currency = st.currency,
        feeBudgetLeft = feeLeft(ledger),
        goals = st.goals, blocked = st.blocked.split(",").map { it.trim() }.filter { it.isNotBlank() }, noReasons = st.noReasonCounts(),
        friends = st.friendlyVenues.split("\n").filter { it.isNotBlank() }, region = st.region
    )

    /** Remembers who said yes, so their next call is brought forward. */
    fun rememberFriend(vararg names: String) {
        val now = st.friendlyVenues.split("\n").filter { it.isNotBlank() }.toMutableList()
        names.map { it.trim() }.filter { it.length > 3 && now.none { n -> n.equals(it, true) } }.forEach { now += it }
        st.friendlyVenues = now.takeLast(60).joinToString("\n")
    }

    suspend fun stats(): CareerStats = statsOf(dao.artworksNow(), dao.docsNow(), dao.factsNow())
    suspend fun level(): Int = Levels.level(stats(), rules())

    fun feesThisMonth(ledger: List<Ledger>): Double {
        val ym = YearMonth.now()
        return ledger.filter { it.kind == "fee" && YearMonth.from(date(it.date)) == ym }.sumOf { -it.amount }
    }

    suspend fun profile(): Profile = profileOf(dao.artworksNow(), dao.docsNow(), dao.factsNow(), dao.ledgerNow())

    // ------------------------------------------------------------ facts the writer may use

    suspend fun voice(): String = dao.docsNow().firstOrNull { it.type == "voice" }?.text.orEmpty()

    suspend fun practice(): String {
        val docs = dao.docsNow()
        val statement = docs.filter { it.type == "statement" && it.text.isNotBlank() }.maxByOrNull { (if (it.approved) 1000 else 0) + it.text.length }?.text
        if (statement != null) return statement.take(1500)
        return docs.filter { it.type == "interview" && it.text.isNotBlank() }.joinToString("\n") { it.text }.take(1500)
    }

    suspend fun factsText(workIds: List<Long> = emptyList()): String {
        val docs = dao.docsNow(); val facts = dao.factsNow(); val works = dao.artworksNow(); val series = dao.seriesNow()
        return buildString {
            appendLine("Name: ${st.fullName}")
            if (st.city.isNotBlank()) appendLine("Based in: ${st.city} ${st.region}")
            appendLine("Works in: ${st.mediums}")
            docs.filter { it.type == "interview" && it.text.isNotBlank() }.forEach { appendLine("Artist said (${it.variant}): ${it.text}") }
            if (facts.isNotEmpty()) { appendLine("CV facts:"); facts.forEach { appendLine("- ${it.category} ${if (it.year > 0) it.year else ""} ${it.text}") } }
            else appendLine("CV facts: none yet. Do not mention exhibitions or awards.")
            series.forEach { s -> appendLine("Series: ${s.name}. ${s.notes}") }
            val chosen = if (workIds.isEmpty()) works.filter { documented(it) }.take(12) else works.filter { it.id in workIds }
            if (chosen.isNotEmpty()) { appendLine("Works:"); chosen.forEach { appendLine("- ${Papers.caption(it)}") } }
        }
    }

    // ------------------------------------------------------------ finding and ranking calls

    fun scored(o: Opp, p: Profile): Opp {
        val (score, reasons) = Scoring.score(o, p)
        return o.copy(fitScore = score, fitReasons = reasons.joinToString("\n"))
    }

    suspend fun rescore() {
        val p = profile()
        dao.oppsNow().filter { it.status == "new" || it.status == "later" }.forEach { o ->
            // A city added later still brings that city's calls forward.
            val n = scored(local(o, o.rawText), p); if (n != o) dao.put(n)
        }
    }

    suspend fun addCalls(found: List<Opp>): Int {
        val existing = dao.oppsNow(); val p = profile(); var added = 0
        val keys = existing.map { it.dedupeKey }.toMutableSet()
        for (o in found) {
            if (o.dedupeKey in keys) continue
            if (o.url.isNotBlank() && existing.any { it.url == o.url && it.title.equals(o.title, true) }) continue
            if (existing.any { Texts.sameCall(it.title, it.deadline, o.title, o.deadline) }) continue
            keys += o.dedupeKey
            dao.put(scored(o, p)); added++
        }
        return added
    }

    /** Text (or a flyer photo) in, call records out, read by the writing help and checked by the plain reader. */
    /**
     * A call from a photo of a flyer or poster. The words are read on the phone (no internet needed), then read like any pasted call.
     * A key of your own also gets the picture itself. Returns the call's id, and whether any words could be read.
     */
    suspend fun callFromPhoto(ctx: Context, path: String, sourceName: String): Pair<Long, Boolean> {
        val words = com.emotionalleaves.studioagent.media.Ocr.read(ctx, path).trim()
        val bytes = if (!ai.free) ImageTools.jpegBytes(path) else null
        val before = dao.oppsNow().maxOfOrNull { it.id } ?: 0L
        val text = if (words.isNotBlank()) "Words read from a photo of a flyer or poster:\n$words" else "A photo of a flyer or poster for a call for artists."
        if (ai.ready && (words.isNotBlank() || bytes != null)) try { ingest(text, sourceName, bytes) } catch (e: Exception) { }
        val id = dao.oppsNow().filter { it.id > before }.maxByOrNull { it.id }?.id
            ?: if (words.isNotBlank()) ingestPlain(words, sourceName) else return ingestPlain("Call from a flyer photo", sourceName) to false
        fillFromPage(id)
        return id to true
    }

    /** A flyer gives a web address but rarely every detail: the call's own page fills the boxes still empty. Quiet when offline. */
    suspend fun fillFromPage(id: Long) {
        val o = dao.oppsNow().firstOrNull { it.id == id } ?: return
        if (o.url.isBlank() || (o.deadline > 0 && o.fee > 0 && o.email.isNotBlank())) return
        val page = try { Fetch.pageText(o.url) } catch (e: Exception) { return }
        if (page.isBlank()) return
        val r = CallReader.read(page, LocalDate.now(), zone, o.sourceName, System.currentTimeMillis())
        dao.put(scored(o.copy(
            deadline = if (o.deadline > 0) o.deadline else r.deadline, fee = if (o.fee > 0) o.fee else r.fee,
            email = o.email.ifBlank { r.email }, maxWorks = if (o.maxWorks > 0) o.maxWorks else r.maxWorks,
            statementLimit = if (o.statementLimit > 0) o.statementLimit else r.statementLimit,
            rawText = (o.rawText + "\n\nFrom the call's page:\n" + page.take(5000)).trim()
        ), profile()))
    }

    suspend fun ingest(text: String, sourceName: String, image: ByteArray? = null, fromListing: Boolean = false): Int {
        val raw = ai.extractCalls(text, sourceName, image).map { if (fromListing && it.scope == "campus" && !mySchool(text)) it.copy(scope = "") else it }
        // A free model can miss or misread a date. When the page holds one call, the plain reader checks its work and fills gaps.
        val found = if (raw.size == 1 && text.length > 40) {
            val plain = com.emotionalleaves.studioagent.domain.CallReader.read(text, LocalDate.now(), zone, sourceName, System.currentTimeMillis())
            val o = raw[0]
            val now = System.currentTimeMillis()
            listOf(local(o.copy(
                deadline = if (plain.deadline > now && (o.deadline < now)) plain.deadline else o.deadline,
                fee = if (o.fee <= 0 && plain.fee > 0) plain.fee else o.fee,
                url = o.url.ifBlank { plain.url }, email = o.email.ifBlank { plain.email },
                statementLimit = if (o.statementLimit <= 0) plain.statementLimit else o.statementLimit,
                maxWorks = if (o.maxWorks <= 0) plain.maxWorks else o.maxWorks
            ), text))
        } else raw.map { local(it, text) }
        val open = found.filter { it.deadline == 0L || it.deadline >= System.currentTimeMillis() + DAY }
        if (open.isEmpty()) return 0
        val practice = practice()
        val withFit = open.map { o ->
            if (practice.isBlank() || o.theme.isBlank()) o
            else try { val (fit, why) = ai.themeFit(o, practice); o.copy(themeFit = fit, themeReason = why) } catch (e: Exception) { o }
        }
        return addCalls(withFit)
    }

    /** A call that names your own city or state is local, whatever else it says. */
    /** True when the text names your own university or college (as you typed it in Settings). */
    fun mySchool(text: String): Boolean {
        val name = st.school.trim()
        if (name.length < 4) return false
        return Regex("\\b" + Regex.escape(name) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text.take(6000))
    }

    fun local(o: Opp, text: String): Opp {
        // A call at your own school is the nearest door there is.
        if (mySchool(text)) return o.copy(scope = "campus", city = o.city.ifBlank { st.city })
        if (o.scope.isNotBlank() && o.scope != "regional") return o
        val head = text.take(4000)
        val near = listOf(st.city, st.region).filter { it.length > 2 }.any { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(head) }
        return if (near) o.copy(scope = if (o.scope == "regional") "regional" else "local", city = o.city.ifBlank { st.city }) else o
    }

    /** Reads a call with no AI: plain pattern matching for the deadline, fee, link and so on. The rest is left for you to fill. */
    suspend fun ingestPlain(text: String, sourceName: String): Long {
        val o = local(com.emotionalleaves.studioagent.domain.CallReader.read(text, LocalDate.now(), zone, sourceName, System.currentTimeMillis()), text)
        return dao.put(scored(o, profile()))
    }

    /** This week's short list: eligible calls at your level, best first, plus at most one stretch call a month. */
    fun shortlist(opps: List<Opp>, p: Profile, now: Long, stretchOk: Boolean, size: Int = 7): List<Opp> {
        val ok = opps.filter { it.status == "new" && Scoring.ineligible(it, p, now).isEmpty() }
        if (p.rules.showAllLevels) return ok.sortedByDescending { it.fitScore }.take(size.coerceAtLeast(1))
        val normal = ok.filter { !Scoring.isStretch(it, p) }.sortedByDescending { it.fitScore }.take(size.coerceAtLeast(1))
        val stretch = if (stretchOk) ok.filter { Scoring.isStretch(it, p) }.maxByOrNull { it.fitScore } else null
        return normal + listOfNotNull(stretch)
    }

    fun stretchOk(now: Long) = now - st.lastStretch > 30 * DAY

    // ------------------------------------------------------------ studio time

    suspend fun planWeek(ctx: Context) {
        val now = System.currentTimeMillis()
        val events = dao.eventsNow()
        events.filter { it.type == "studio" && it.date < now - 14 * DAY }.forEach { dao.del(it) }
        if (st.pausedUntil > now) return
        val template = Planner.parseTemplate(st.weekTemplate)
        val custom = template.isNotEmpty()
        val planned = events.filter { it.type == "studio" && it.date >= now - DAY }.map { date(it.date) }.toMutableSet()
        val plannedStarts = events.filter { it.type == "studio" }.map { it.date }.toSet()
        val slots = if (custom) Planner.planFromTemplate(LocalDate.now(), template) else Planner.plan(
            LocalDate.now(), Planner.days(st.workDays), Planner.time(st.workEnd, LocalTime.of(15, 30)), st.bufferMin,
            Planner.time(st.bedtime, LocalTime.of(22, 30)), st.weeklyHours
        )
        for (slot in slots) {
            var start = slot.start
            if (millis(start) < now) continue
            // Your own template is followed exactly; the suggested week keeps to one block a day and steps around clashes.
            if (custom) {
                if (millis(start) in plannedStarts) continue
                // Something already in your calendar then (a changed shift, an appointment): an hour or two later the same day, or not that day.
                if (Cal.busy(ctx, millis(start), millis(start.plusMinutes(slot.minutes.toLong())))) {
                    val bed = Planner.time(st.bedtime, LocalTime.of(22, 30))
                    val moved = listOf(60L, 120L).map { start.plusMinutes(it) }.firstOrNull { t -> !t.plusMinutes(slot.minutes.toLong()).toLocalTime().isAfter(bed) && t.toLocalDate() == start.toLocalDate() &&
                        !Cal.busy(ctx, millis(t), millis(t.plusMinutes(slot.minutes.toLong()))) } ?: continue
                    start = moved
                }
                val s = millis(start); val e = millis(start.plusMinutes(slot.minutes.toLong()))
                val calId = if (st.useCalendar) Cal.add(ctx, "Studio time", s, e, "Planned by Studio Agent from your own week.") else 0L
                dao.put(Evt(type = "studio", title = "Studio time", date = s, endDate = e, calendarId = calId))
                remind(ctx, s, now)
                continue
            }
            if (start.toLocalDate() in planned) continue
            if (st.useCalendar && Cal.busy(ctx, millis(start), millis(start.plusMinutes(slot.minutes.toLong())))) {
                start = start.plusHours(1)
                if (Cal.busy(ctx, millis(start), millis(start.plusMinutes(slot.minutes.toLong())))) continue
            }
            val s = millis(start); val e = millis(start.plusMinutes(slot.minutes.toLong()))
            val calId = if (st.useCalendar) Cal.add(ctx, "Studio time", s, e, "Planned by Studio Agent. Move it if you need to.") else 0L
            dao.put(Evt(type = "studio", title = "Studio time", date = s, endDate = e, calendarId = calId))
            planned += start.toLocalDate()
            remind(ctx, s, now)
        }
    }

    /** "Later" on a reminder: ask again in half an hour, once. */
    fun remindLater(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(30, TimeUnit.MINUTES)
            .setInputData(workDataOf("title" to "Studio time, when you are ready", "route" to "session")).build()
        try { WorkManager.getInstance(ctx).enqueueUniqueWork("studio-later", ExistingWorkPolicy.REPLACE, req) } catch (e: Exception) { }
    }

    private fun remind(ctx: Context, s: Long, now: Long) {
        val delay = s - now - 15 * 60_000
        if (delay <= 0) return
        val req = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to "Studio in 15 minutes", "route" to "session")).build()
        try { WorkManager.getInstance(ctx).enqueueUniqueWork("studio-$s", ExistingWorkPolicy.KEEP, req) } catch (e: Exception) { }
    }

    /** Throw away the studio blocks still ahead and plan again. Used after you change your week or pause. */
    suspend fun replan(ctx: Context) {
        val now = System.currentTimeMillis()
        dao.eventsNow().filter { it.type == "studio" && it.date > now }.forEach { e ->
            Cal.remove(ctx, e.calendarId)
            try { WorkManager.getInstance(ctx).cancelUniqueWork("studio-${e.date}") } catch (x: Exception) { }
            dao.del(e)
        }
        planWeek(ctx)
    }

    /**
     * When a piece is marked documented, fill in whatever the artist left blank with safe defaults,
     * and say exactly what was filled so nothing is a surprise. Returns the piece and the plain-words list.
     */
    fun fillBlanks(a: Artwork): Pair<Artwork, List<String>> {
        var w = a; val filled = mutableListOf<String>()
        if (w.title.isBlank()) { w = w.copy(title = "Untitled #${w.id}"); filled += "a working title" }
        if (w.year <= 0) { w = w.copy(year = LocalDate.now().year); filled += "the year" }
        if ((w.widthIn <= 0 || w.heightIn <= 0) && w.format == "print") Texts.suggestedPrintSize(w.pixelW, w.pixelH)?.let {
            w = w.copy(widthIn = it.first, heightIn = it.second); filled += "the size (${Texts.dim(it.second)} x ${Texts.dim(it.first)} in, the largest sharp print)"
        }
        if (w.format == "print" && w.editionSize <= 0) { w = w.copy(editionSize = 10); filled += "an edition of 10" }
        if (w.price <= 0 && !w.nfs && w.widthIn > 0 && w.heightIn > 0) {
            val p = Texts.price(w.widthIn, w.heightIn, st.pricePerSqIn)
            if (p > 0) { w = w.copy(price = p); filled += "a price of ${st.currency}${"%.0f".format(p)} from your rule" }
        }
        return w to filled
    }

    /** Turns a file name into a working title: "blue_hour-final.png" becomes "Blue hour final". */
    fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.').replace(Regex("[_\\-]+"), " ").replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.uppercase() }.take(80)

    /** Brings one existing image file in as a finished, documented piece with safe defaults filled in. */
    suspend fun importFile(ctx: Context, uri: android.net.Uri, fileName: String, seriesId: Long = 0): Long = withContext(Dispatchers.IO) {
        val id = dao.put(Artwork(status = "documented", format = if (st.routes.firstOrNull() == "screen") "screen" else "print", year = LocalDate.now().year, createdAt = System.currentTimeMillis(), seriesId = seriesId))
        val dir = ImageTools.mediaDir(ctx)
        val orig = File(dir, "work_${id}_orig.jpg"); val main = File(dir, "work_${id}_main.jpg")
        if (!ImageTools.copyUri(ctx, uri, orig)) { dao.artworksNow().firstOrNull { it.id == id }?.let { dao.del(it) }; throw Exception("Could not read $fileName") }
        val (pw, ph) = ImageTools.size(orig.absolutePath)
        // Picking the same file twice does not make two pieces.
        if (pw > 0 && dao.artworksNow().any { it.id != id && it.pixelW == pw && it.pixelH == ph && it.title == titleFromFileName(fileName) }) {
            orig.delete(); dao.artworksNow().firstOrNull { it.id == id }?.let { dao.del(it) }; throw Exception("already in Studio")
        }
        val bmp = ImageTools.decode(orig.absolutePath, 2400)
        if (bmp == null) { orig.delete(); dao.artworksNow().firstOrNull { it.id == id }?.let { dao.del(it) }; throw Exception("$fileName is not an image the app can open") }
        ImageTools.saveJpeg(bmp, main)
        val base = dao.artworksNow().first { it.id == id }.copy(title = titleFromFileName(fileName), originalPath = orig.absolutePath, imagePath = main.absolutePath, pixelW = pw, pixelH = ph)
        dao.put(fillBlanks(base).first)
        id
    }

    /** The piece to work on: this week's chosen one if it is still in progress, otherwise the most recent in progress. */
    fun currentPiece(works: List<Artwork>): Artwork? =
        works.firstOrNull { it.id == st.focusWork && it.status == "in_progress" } ?: works.firstOrNull { it.status == "in_progress" }

    /** Plain-words forecast for a series from real session history. */
    fun forecast(seriesId: Long, target: Int, works: List<Artwork>, sessions: List<com.emotionalleaves.studioagent.data.Sess>): String {
        val inSeries = works.filter { it.seriesId == seriesId }
        val done = inSeries.count { documented(it) || it.status == "finished" }
        if (done >= target) return "Target reached."
        val finishedIds = inSeries.filter { documented(it) || it.status == "finished" }.map { it.id }.toSet()
        val perPiece = finishedIds.map { id -> sessions.filter { it.artworkId == id && it.end > it.start }.sumOf { (it.end - it.start) / 3_600_000.0 } }.filter { it > 0 }
        val since = System.currentTimeMillis() - 28 * DAY
        val recent = sessions.filter { it.start >= since && it.end > it.start }.sumOf { (it.end - it.start) / 3_600_000.0 } / 4.0
        val weeks = Planner.weeksToFinish(done, target, if (perPiece.isEmpty()) 0.0 else perPiece.average(), recent)
            ?: return "Once one piece is finished with its sessions logged, the app can tell you roughly when the series will be done."
        return "At your real pace (about ${"%.1f".format(recent)} hours a week, ${"%.1f".format(perPiece.average())} a piece): about $weeks weekend" + (if (weeks == 1) "" else "s") + " to reach $target. A forecast, not a deadline."
    }

    /**
     * A plain answer to a question about the art world or the app. The writing help answers briefly and honestly;
     * without it, the closest of the app's own plain answers.
     */
    suspend fun ask(q: String, known: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val words = q.lowercase().split(Regex("[^a-z]+")).filter { it.length > 3 }.toSet()
        val best = known.maxByOrNull { (k, a) -> (k + " " + a).lowercase().split(Regex("[^a-z]+")).count { it in words } }
        val bestScore = best?.let { (k, a) -> (k + " " + a).lowercase().split(Regex("[^a-z]+")).count { it in words } } ?: 0
        if (ai.ready) try {
            return@withContext ai.complete(
                "You answer questions from an artist who is new to the art world, works a full-time day job, and has ADHD. Answer in at most five short sentences of plain words. " +
                    "Be practical and honest; say when something depends on the call or the venue. Never invent specific organizations, prices or deadlines. " +
                    "The artist uses an app that finds open calls, writes statements from their own answers, builds application packs and sends them; mention it only when it helps. " +
                    "Stay consistent with the app's own plain answers below when they cover the question.\n\n" + known.joinToString("\n") { "Q: ${it.first}\nA: ${it.second}" }.take(6000),
                q.take(800), fast = true, maxTokens = 900).let { plainText(it) }
        } catch (e: Exception) { }
        if (best != null && bestScore > 0) best.first + "\n\n" + best.second
        else "The writing help did not answer just now. Try the plain answers below, or ask again in a minute."
    }

    private val plainCache = mutableMapOf<Long, List<String>>()

    /** Writing help sometimes answers in markdown; the app shows plain sentences. */
    fun plainText(t: String): String = t.replace(Regex("\\*\\*|__|`"), "").replace(Regex("(?m)^#{1,6}\\s*"), "").replace(Regex("(?m)^\\s*[*-]\\s+"), "").trim()

    fun plainWordsCached(o: Opp): List<String> = plainCache[o.id].orEmpty()

    /** Three short plain lines about a call: what it is, what they want from you, anything to watch. Empty when there is nothing to read. */
    suspend fun plainWords(o: Opp): List<String> = withContext(Dispatchers.IO) {
        val src = o.rawText.ifBlank { listOf(o.theme, o.eligibility, o.prizes, o.delivery).filter { it.isNotBlank() }.joinToString("\n") }
        if (!ai.ready || src.length < 120) return@withContext plainFacts(o)
        val out = try { ai.complete(
            "You explain open calls for artists to someone new to the art world who has ADHD. ${ai.dataRule} Reply with exactly three short lines, plain words, no jargon, no headings, no bullet symbols: " +
                "1) what this is and who runs it, 2) what they want you to send, 3) one thing to watch (cost, commission, image rights, delivery, travel). " +
                "Give a date only if the text gives it, and say what that date is for (entry deadline, delivery, show dates). Do not praise or recommend.",
            "<data>\nTitle: ${o.title}\n${src.take(5000)}\n</data>", fast = true, maxTokens = 260) } catch (e: Exception) { return@withContext plainFacts(o) }
        val lines = plainText(out).lines().map { it.trim().trimStart('-', '*', '•').replace(Regex("^\\d+[.)]\\s*"), "").trim() }.filter { it.length in 8..220 }.take(3)
        plainCache[o.id] = lines
        lines
    }

    /** The same three lines from the call's own fields, for when the writing help is off or busy. */
    fun plainFacts(o: Opp): List<String> {
        val kind = mapOf("exhibition" to "An exhibition", "residency" to "A residency", "grant" to "A grant", "publication" to "A publication", "fair" to "An art fair", "commission" to "A commission")[o.kind] ?: "A call"
        val who = o.organiser.ifBlank { o.venue }
        val send = buildList {
            if (o.maxWorks > 0) add("up to ${Texts.count(o.maxWorks, "work")}")
            if (o.statementLimit > 0) add("a statement of up to ${o.statementLimit} words")
        }
        val watch = buildList {
            if (o.guaranteed && o.fee > 0) add("the fee buys a place; nothing is juried")
            if (o.rightsGrab) add("the terms take broad rights to your images")
            if (o.commission > 0) add("${"%.0f".format(o.commission)}% commission on sales")
            if (o.delivery.isNotBlank()) add("delivery: ${o.delivery.take(80)}")
        }
        return listOfNotNull(
            if (who.isNotBlank()) "$kind run by $who." else null,
            if (send.isNotEmpty()) "Send " + send.joinToString(" and ") + "." else null,
            if (watch.isNotEmpty()) "Watch: " + watch.joinToString("; ") + "." else null
        )
    }

    /**
     * Five small, concrete steps for a piece, each small enough to start tired after work.
     * The writing help suggests them from the piece's own details; without it, a sound general sequence.
     */
    suspend fun suggestSteps(w: Artwork): List<String> {
        val plain = when (w.format) {
            "print" -> listOf("Pick the final file and check its size", "Make a small test print", "Adjust color from the test", "Order or make the final print", "Sign and number it")
            "screen" -> listOf("Choose the exact cut or loop", "Check it plays on a borrowed screen", "Export the final file", "Write the one-line installation note", "Record a short clip for calls")
            else -> listOf("Set out materials and one reference", "Block in the big shapes", "Work the main area for one session", "Refine the details that matter most", "Compare it with the finish line")
        }
        if (!ai.ready) return plain
        return try {
            val out = ai.complete(
                "You help an artist with ADHD start work after a full-time job. Reply with exactly five lines, no numbering, no extra words. " +
                    "The lines are the steps in the order they happen, from the first marks to the finished piece. Each is one concrete action of 4 to 9 words that moves the piece forward " +
                    "and can be started in five minutes. Only the first may be preparation. Use only what the details say; do not invent subject matter.",
                "Piece: ${w.title.ifBlank { "untitled" }}. Medium: ${Papers.medium(w).ifBlank { "not stated" }}. Size: ${Texts.dims(w.widthIn, w.heightIn).ifBlank { "not stated" }}. " +
                    "Finish line: ${w.doneWhen.ifBlank { "not set" }}. Notes: ${w.notes.take(400)}", fast = true, maxTokens = 300)
            val lines = out.lines().map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim() }.filter { it.length in 4..80 }.take(5)
            if (lines.size >= 3) lines else plain
        } catch (e: Exception) { plain }
    }

    /** What to do first on a piece: its next open small step, or the line left at the end of the last session. */
    fun firstStep(a: Artwork): String = Texts.firstOpenStep(a.steps).ifBlank { a.nextStep }

    /** Starts a session straight away with sensible choices, so starting is one tap. */
    suspend fun quickStart(goalMin: Int): Long {
        val works = dao.artworksNow()
        val wiped = st.energyDay == LocalDate.now().toString() && st.energy == "wiped"
        val piece = currentPiece(works) ?: works.firstOrNull { it.status == "idea" }
        st.sessionPausedAt = 0L; st.sessionPausedTotal = 0L
        st.sessionArtwork = piece?.id ?: 0L
        st.sessionKind = if (wiped || piece == null) "light" else "make"
        st.sessionGoalMin = goalMin
        st.sessionStart = System.currentTimeMillis()
        return st.sessionStart
    }

    /** Milliseconds actually worked in the running session, with pauses taken out. */
    fun sessionElapsed(now: Long): Long {
        if (st.sessionStart == 0L) return 0
        val until = if (st.sessionPausedAt > 0) st.sessionPausedAt else now
        return (until - st.sessionStart - st.sessionPausedTotal).coerceAtLeast(0)
    }

    // ------------------------------------------------------------ custom application questions

    fun answers(a: Appl): List<Pair<String, String>> = try {
        val arr = org.json.JSONArray(a.answers.ifBlank { "[]" })
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { it.optString("q") to it.optString("a") } }
    } catch (e: Exception) { emptyList() }

    /** An answer you already wrote to the same or a very similar question in another application, if there is one. */
    fun priorAnswer(question: String, appls: List<Appl>, exceptId: Long): String =
        appls.filter { it.id != exceptId }.flatMap { answers(it) }.filter { it.second.isNotBlank() }
            .map { it to Texts.similarity(question, it.first) }.filter { it.second >= 0.5 }.maxByOrNull { it.second }?.first?.second.orEmpty()

    /** Names the week (by its Monday) so the weekly look-ahead is offered once a week. */
    fun weekId(): String = LocalDate.now().with(DayOfWeek.MONDAY).toString()

    fun answersJson(list: List<Pair<String, String>>): String {
        val arr = org.json.JSONArray()
        list.forEach { (q, a) -> arr.put(org.json.JSONObject().put("q", q).put("a", a)) }
        return arr.toString()
    }

    fun hoursThisWeek(sessions: List<com.emotionalleaves.studioagent.data.Sess>): Double {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val from = millis(monday.atStartOfDay())
        return sessions.filter { it.start >= from && it.end > it.start }.sumOf { (it.end - it.start) / 3_600_000.0 }
    }

    // ------------------------------------------------------------ applications

    suspend fun pickStatement(limit: Int): String {
        val all = dao.docsNow().filter { it.type == "statement" && it.text.isNotBlank() }
        val pool = all.filter { it.approved }.ifEmpty { all }
        val cap = if (limit > 0) limit else 150
        val fit = pool.filter { Texts.words(it.text) <= cap }.maxByOrNull { Texts.words(it.text) }
        return fit?.text ?: pool.minByOrNull { Texts.words(it.text) }?.let { Texts.trimToWords(it.text, cap) }.orEmpty()
    }

    /**
     * Everything a gallery asks for when they say "send me your materials", as one zip:
     * portfolio PDF, CV, statement and bio, an image list, and the pictures at 2000 px with your name in them.
     */
    suspend fun pressKit(ctx: Context): File = withContext(Dispatchers.IO) {
        val works = portfolioOrdered(dao.artworksNow()).take(15)
        if (works.isEmpty()) throw Exception("Mark at least one piece Ready to send first; the kit is built from those.")
        val dir = File(ctx.cacheDir, "kit").apply { deleteRecursively(); mkdirs() }
        val last = st.lastName.ifBlank { st.firstName }
        val names = works.mapIndexed { i, w -> Texts.fileName(last, w.title, w.year, Papers.medium(w), w.widthIn, w.heightIn, number = i + 1) }
        works.forEachIndexed { i, w -> ImageTools.export(w.imagePath, 2000, 2_500_000, File(dir, names[i]), st.fullName, Papers.caption(w)) }
        Papers.imageList(ctx, st, works, withPrices = true, dir = dir, files = names, title = "Image list")
        Papers.portfolio(ctx, st, works, pickStatement(150), works.size).copyTo(File(dir, Papers.docName(st, "Portfolio")), overwrite = true)
        if (dao.factsNow().isNotEmpty()) Papers.cv(ctx, st, dao.factsNow()).copyTo(File(dir, Papers.docName(st, "CV")), overwrite = true)
        val docs = dao.docsNow()
        val statement = pickStatement(300)
        val bio = docs.filter { it.type == "bio" && it.text.isNotBlank() }.sortedByDescending { if (it.approved) 1 else 0 }.firstOrNull()?.text.orEmpty()
        val sections = listOfNotNull(statement.takeIf { it.isNotBlank() }?.let { "Artist statement" to it }, bio.takeIf { it.isNotBlank() }?.let { "Bio" to it }, "Contact" to Papers.signature(st))
        Papers.textDoc(ctx, st, "Statement and bio", sections, dir, Papers.docName(st, "Statement_and_bio"))
        val zip = File(ImageTools.exportDir(ctx), Papers.docName(st, "Materials", "zip"))
        java.util.zip.ZipOutputStream(zip.outputStream()).use { z ->
            dir.listFiles()?.sortedBy { it.name }?.forEach { f -> z.putNextEntry(java.util.zip.ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }
        zip
    }

    /** Every piece's history in one line each: the shows it was in and where it sold, oldest first. */
    suspend fun workHistory(): Map<Long, List<String>> {
        val opps = dao.oppsNow().associateBy { it.id }
        val out = mutableMapOf<Long, MutableList<String>>()
        dao.applsNow().filter { it.status == "accepted" }.sortedBy { it.submittedAt }.forEach { a ->
            val o = opps[a.oppId] ?: return@forEach
            workIds(a).forEach { id -> out.getOrPut(id) { mutableListOf() } += "Shown: " + listOf(o.title, o.venue, o.city).filter { it.isNotBlank() }.joinToString(", ") + (if (o.exhibitionDates.isNotBlank()) ", ${o.exhibitionDates}" else "") }
        }
        dao.ledgerNow().filter { it.kind == "sale" && it.artworkId > 0 }.sortedBy { it.date }.forEach { l ->
            out.getOrPut(l.artworkId) { mutableListOf() } += "Sold ${com.emotionalleaves.studioagent.ui.fmtDate(l.date)}" + l.note.substringAfter(", to ", "").let { if (it.isBlank()) "" else " to $it" }
        }
        return out
    }

    /** Your three strongest pictures, sized for any inbox, for a first email to a venue. */
    private suspend fun outreachPictures(): List<File> {
        val dir = File(G.cacheDir, "outreach_mail").apply { deleteRecursively(); mkdirs() }
        return portfolioOrdered(dao.artworksNow()).take(3).mapIndexedNotNull { i, w ->
            File(dir, Texts.fileName(st.lastName.ifBlank { st.firstName }, w.title, w.year, "", 0.0, 0.0, number = i + 1))
                .takeIf { ImageTools.export(w.imagePath, 1400, 700_000, it, st.fullName, Papers.caption(w)) }
        }
    }

    /** The covering email for an application: short, formal, and naming exactly what is attached. */
    fun applicationEmail(opp: Opp, chosen: List<Artwork>, statement: String): String = buildString {
        val to = listOf(opp.organiser, opp.venue).firstOrNull { it.isNotBlank() }
        appendLine(if (to != null) "Dear $to team," else "Dear selection committee,")
        appendLine()
        appendLine("Please find attached my submission for ${opp.title.ifBlank { "your open call" }}. " +
            (if (chosen.size == 1) "It includes one work:" else "It includes ${chosen.size} works:"))
        appendLine()
        chosen.forEachIndexed { i, a -> appendLine("${i + 1}. ${Papers.caption(a)}") }
        appendLine()
        appendLine("The image files are numbered to match this list. My CV, image list" + (if (statement.isNotBlank()) " and artist statement are" else " are") + " attached as PDFs.")
        appendLine()
        appendLine("Thank you for considering my work.")
        appendLine()
        appendLine("Kind regards,")
        appendLine()
        append(Papers.signature(st))
    }

    suspend fun createApplication(ctx: Context, opp: Opp, prefer: List<Long> = emptyList()): Long {
        dao.applsNow().firstOrNull { it.oppId == opp.id }?.let { return it.id }
        val works = dao.artworksNow().filter { available(it) }
        val bySeries = works.groupBy { it.seriesId }.values.maxByOrNull { it.size }.orEmpty()
        val want = when { opp.maxWorks > 0 -> opp.maxWorks; opp.minWorks > 0 -> opp.minWorks; else -> 3 }
        var chosen = (bySeries + works.filter { it !in bySeries }).take(want)
        // A themed call: the pieces that answer the theme, not just the biggest series.
        if (ai.ready && opp.theme.isNotBlank() && works.size > want) try {
            val ids = ai.pickWorks(opp, works, dao.seriesNow().associate { it.id to it.name }, want)
            if (ids.size >= minOf(want, works.size).coerceAtLeast(1)) chosen = ids.mapNotNull { i -> works.firstOrNull { it.id == i } }
        } catch (e: Exception) { }
        // Trying again with the same pieces: they go first, as they were.
        if (prefer.isNotEmpty()) { val p = prefer.mapNotNull { i -> works.firstOrNull { it.id == i } }; if (p.isNotEmpty()) chosen = (p + chosen.filter { it !in p }).take(maxOf(want, p.size).let { if (opp.maxWorks > 0) minOf(it, opp.maxWorks) else it }) }
        val statement = pickStatement(opp.statementLimit)
        val list = chosen.mapIndexed { i, a -> "${i + 1}. ${Papers.caption(a)}" }.joinToString("\n")
        val body = applicationEmail(opp, chosen, statement)
        val id = dao.put(
            Appl(
                oppId = opp.id, workIds = chosen.joinToString(",") { it.id.toString() }, statement = statement,
                method = if (opp.email.isNotBlank()) "email" else "web", emailTo = opp.email,
                emailSubject = "${opp.title}: submission from ${st.fullName.ifBlank { "the artist" }}", emailBody = body, createdAt = System.currentTimeMillis(),
                // Grants and residencies always ask these; they are there from the start, each with a Draft button.
                answers = when (opp.kind) {
                    "grant" -> answersJson(listOf("Project description: what you will make, and why now" to "", "Budget: what the money pays for" to "", "Timeline" to ""))
                    "residency" -> answersJson(listOf("What you will work on during the residency" to "", "Why this residency, and why now" to ""))
                    "commission" -> answersJson(listOf("Your idea for this site or brief" to "", "Similar work you have made" to ""))
                    else -> ""
                }
            )
        )
        dao.put(opp.copy(status = "yes"))
        if (Scoring.isStretch(opp, profile())) st.lastStretch = System.currentTimeMillis()
        scheduleDeadline(ctx, opp, id)
        return id
    }

    /**
     * The three dated reminders for an application (pack ready, submit, deadline). Run again when the deadline is
     * typed in or changed later: the old open ones are replaced, so the dates always follow the call.
     */
    suspend fun scheduleDeadline(ctx: Context, opp: Opp, applId: Long) {
        val kinds = setOf("pack", "submit", "deadline")
        dao.eventsNow().filter { it.applId == applId && it.type in kinds && !it.done }.forEach { e ->
            if (e.calendarId > 0) try { Cal.remove(ctx, e.calendarId) } catch (x: Exception) { }
            dao.del(e)
        }
        if (opp.deadline <= 0) return
        val now = System.currentTimeMillis()
        listOf(Triple("pack", "Pack ready: ${opp.title}", opp.deadline - 7 * DAY), Triple("submit", "Submit: ${opp.title}", opp.deadline - 3 * DAY), Triple("deadline", "Deadline: ${opp.title}", opp.deadline))
            .forEach { (type, title, at) ->
                val calId = if (at > now && st.useCalendar && type != "pack") Cal.add(ctx, title, at - 3_600_000, at) else 0L
                dao.put(Evt(type = type, title = title, date = at, oppId = opp.id, applId = applId, calendarId = calId))
            }
    }

    /** After a no: the best call still open (a few days left at least) that you have not entered, to send the same pieces to. */
    suspend fun nextCallFor(after: Appl): Opp? {
        val now = System.currentTimeMillis(); val entered = dao.applsNow().map { it.oppId }.toSet()
        return dao.oppsNow().filter { it.id != after.oppId && it.id !in entered && it.status != "no" && it.deadline > now + 3 * DAY && it.sourceName != "Your own show" }
            .maxByOrNull { it.fitScore * 10 - (if (it.fee > 0) it.fee.toInt() else 0) }
    }

    /** How many more pieces a call needs than are ready for it, counting the ones already chosen. */
    fun piecesShort(a: Appl, o: Opp, works: List<Artwork>): Int {
        val need = when { o.minWorks > 0 -> o.minWorks; o.maxWorks > 0 -> minOf(o.maxWorks, 3); else -> 1 }
        val have = works.count { available(it) || it.id in workIds(a) }
        return (need - have).coerceAtLeast(0)
    }

    /**
     * Making the missing pieces in time: the pieces in progress first (newest first), then ideas, each given a
     * Saturday to be finished by, spread evenly over the weekends before the deadline (three days early).
     * Returns what was planned, in words. Saturdays with a plan already are left alone.
     */
    suspend fun planMaking(a: Appl, o: Opp): String {
        val works = dao.artworksNow(); val short = piecesShort(a, o, works)
        if (short == 0) return "Enough pieces are ready."
        val end = date(o.deadline - 3 * DAY); val today = LocalDate.now()
        val sats = generateSequence(today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY))) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
        if (sats.isEmpty()) return "There is no weekend left before this deadline. A piece you already have, or a later call, is the kinder choice."
        val already = dao.eventsNow().filter { it.applId == a.id && it.type == "task" && it.title.startsWith("Finish ") && !it.done }
        val pieces = (works.filter { it.status == "in_progress" }.sortedByDescending { it.createdAt } + works.filter { it.status in setOf("idea", "finished") }.sortedBy { if (it.status == "finished") 0 else 1 })
            .filter { w -> already.none { it.title == "Finish " + w.title.ifBlank { "Untitled #${w.id}" } } }.take(short - already.size)
        if (pieces.isEmpty() && already.isEmpty()) return "Start a piece (or add an idea) in Studio first, and the making can be planned."
        val planned = mutableListOf<String>()
        pieces.forEachIndexed { i, w ->
            val idx = (((i + 1 + already.size) * sats.size) / short - 1).coerceIn(0, sats.size - 1)
            val day = sats[idx]; val name = w.title.ifBlank { "Untitled #${w.id}" }
            dao.put(Evt(type = "task", title = "Finish $name", date = millis(day.atTime(17, 0)), oppId = o.id, applId = a.id))
            planned += name + " by " + day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US) + " " + day.monthValue + "/" + day.dayOfMonth
        }
        val tight = short > sats.size
        return (if (planned.isEmpty()) "Already planned." else "Planned: " + planned.joinToString("; ") + ".") +
            (if (pieces.size + already.size < short) " Still " + Texts.count(short - pieces.size - already.size, "piece") + " short: start one, or send fewer if the call allows." else "") +
            (if (tight) " That is more than a piece a weekend. A smaller size, or a later call, may be kinder." else "")
    }

    /** Pieces a week at your real pace: recent studio hours a week over the hours a finished piece has taken. 0 until there is a finished piece with sessions. */
    suspend fun piecesPerWeek(): Double {
        val works = dao.artworksNow(); val sessions = dao.sessionsNow().filter { it.end > it.start }
        val doneIds = works.filter { documented(it) || it.status in setOf("finished", "shown", "sold") }.map { it.id }.toSet()
        val perPiece = doneIds.map { id -> sessions.filter { it.artworkId == id }.sumOf { (it.end - it.start) / 3_600_000.0 } }.filter { it > 0 }
        if (perPiece.isEmpty()) return 0.0
        val recent = sessions.filter { it.start >= System.currentTimeMillis() - 28 * DAY }.sumOf { (it.end - it.start) / 3_600_000.0 } / 4.0
        return recent / perPiece.average()
    }

    suspend fun yearAhead(): List<Pair<java.time.YearMonth, List<String>>> {
        val now = System.currentTimeMillis(); val works = dao.artworksNow()
        val have = works.count { documented(it) || it.status in setOf("finished", "shown", "sold") }
        val calls = dao.oppsNow().filter { it.deadline > now && (it.status == "yes" || (it.status == "new" && it.fitScore >= 60)) && it.sourceName != "Your own show" }.map { date(it.deadline) to it.title }
        val evts = dao.eventsNow().filter { !it.done && it.date > now }
        val again = evts.filter { it.type == "watch" }.map { date(it.date) to it.title.removePrefix("Watch for: ") }
        val plans = evts.filter { it.type == "task" || it.type == "invite" }.map { date(it.date) to it.title }
        return Planner.yearPlan(LocalDate.now(), have, piecesPerWeek(), level(), calls, again, plans)
    }

    // ------------------------------------------------------------ selling at a show

    /** The show a piece is in right now (an accepted entry it is part of, not yet released), or null. */
    suspend fun showOf(workId: Long): Pair<Appl, Opp>? {
        val a = dao.applsNow().filter { it.status == "accepted" && workId in workIds(it) }.maxByOrNull { it.submittedAt } ?: return null
        val o = dao.oppsNow().firstOrNull { it.id == a.oppId } ?: return null
        return a to o
    }

    /**
     * Sold at a show: the sale and the venue's cut go in Records, the piece is marked sold (it stays on the wall
     * until the show ends), the buyer can join your list, and a dated step to get it to them after the show
     * goes on your week (the show's pick-up day when there is one). Returns what you receive.
     */
    suspend fun sellAtShow(w: Artwork, price: Double, buyer: String, email: String, joinList: Boolean): Double {
        val now = System.currentTimeMillis()
        val show = showOf(w.id)
        val pct = show?.second?.commission ?: 0.0
        dao.put(Ledger(kind = "sale", amount = price, date = now, note = w.title + (if (buyer.isNotBlank()) ", to $buyer" else "") + (show?.let { ", at " + it.second.title } ?: ""), artworkId = w.id, oppId = show?.second?.id ?: 0))
        if (pct > 0) dao.put(Ledger(kind = "commission", amount = -price * pct / 100, date = now, note = "Commission on ${w.title}", artworkId = w.id, oppId = show?.second?.id ?: 0))
        dao.put(if (w.editionSize > 1) w.copy(editionUsed = w.editionUsed + 1).let { x -> if (x.editionUsed >= x.editionSize) x.copy(status = "sold") else x } else w.copy(status = "sold", location = show?.second?.let { "Sold, on the wall at " + it.venue.ifBlank { it.title } } ?: "Sold"))
        if (dao.factsNow().none { it.category == "collection" && it.text == "Private collection" }) dao.put(Fact(category = "collection", year = LocalDate.now().year, text = "Private collection"))
        if (joinList && Texts.looksLikeEmail(email) && dao.contactsNow().none { it.email.equals(email, true) })
            dao.put(Contact(name = buyer, email = email, kind = "subscriber", status = "subscriber", notes = "Bought ${w.title} on ${LocalDate.now()}.", createdAt = now))
        val pickup = show?.let { (a, _) -> dao.eventsNow().filter { it.applId == a.id && it.type == "task" && Regex("pick.?up|take.?down|collect", RegexOption.IGNORE_CASE).containsMatchIn(it.title) }.maxOfOrNull { it.date } }
        val day = (pickup ?: (now + 14 * DAY)).coerceAtLeast(now + DAY)
        dao.put(Evt(type = "task", title = "Get ${w.title.ifBlank { "the piece" }} to ${buyer.ifBlank { "the buyer" }}", date = day, oppId = show?.second?.id ?: 0, applId = show?.first?.id ?: 0, artworkId = w.id))
        return price * (1 - pct / 100)
    }

    /** Shipping steps for a show, counted back from its delivery step (or two weeks out), each once. Returns how many were added. */
    suspend fun planShipping(a: Appl): Int {
        val evts = dao.eventsNow().filter { it.applId == a.id }
        val deliver = evts.firstOrNull { Regex("deliver|ship the work|drop", RegexOption.IGNORE_CASE).containsMatchIn(it.title) && it.date > 0 }?.date?.let { date(it) } ?: LocalDate.now().plusDays(17)
        var n = 0
        Shipping.steps(deliver, LocalDate.now()).forEach { (t, d) -> if (evts.none { it.title == t }) { dao.put(Evt(type = "task", title = t, date = millis(d.atTime(18, 0)), oppId = a.oppId, applId = a.id)); n++ } }
        return n
    }

    /** The market day closed: each original sold is a sale and marked sold, the multiples one sale each kind, the booth fee a cost. The tally clears. */
    suspend fun closeMarket(where: String): Double {
        val items = com.emotionalleaves.studioagent.domain.Market.items(st.marketItems)
        val tally = com.emotionalleaves.studioagent.domain.Market.tally(st.marketTally)
        val now = System.currentTimeMillis(); var total = 0.0
        val place = where.ifBlank { "a market" }
        tally.forEach { (k, n) ->
            if (k.startsWith("m:")) items.firstOrNull { "m:" + it.name == k }?.let { i -> if (i.price > 0) { dao.put(Ledger(kind = "sale", amount = i.price * n, date = now, note = "$n x ${i.name}, at $place")); total += i.price * n } }
            if (k.startsWith("w:")) dao.artworksNow().firstOrNull { it.id == k.removePrefix("w:").toLongOrNull() }?.let { w ->
                dao.put(Ledger(kind = "sale", amount = w.price, date = now, note = "${w.title}, at $place", artworkId = w.id)); total += w.price
                dao.put(w.copy(status = "sold", location = "Sold"))
            }
        }
        if (st.marketFee > 0) dao.put(Ledger(kind = "fee", amount = -st.marketFee, date = now, note = "Booth at $place"))
        // What was left over goes back in the box for next time.
        st.marketItems = com.emotionalleaves.studioagent.domain.Market.itemsText(items.map { i -> i.copy(qty = (i.qty - (tally["m:" + i.name] ?: 0)).coerceAtLeast(0)) })
        st.marketTally = ""
        if (tally.keys.any { it.startsWith("w:") } && dao.factsNow().none { it.category == "collection" && it.text == "Private collection" }) dao.put(Fact(category = "collection", year = LocalDate.now().year, text = "Private collection"))
        return total
    }

    /** The show's announcement, ready for a newspaper calendar, a radio listing or a community board. Your words, no AI. */
    suspend fun announcement(a: Appl): String {
        val o = dao.oppsNow().firstOrNull { it.id == a.oppId } ?: return ""
        val evts = dao.eventsNow().filter { it.applId == a.id }
        val opening = evts.firstOrNull { it.title.startsWith("Go to the opening", true) && it.date > 0 }?.date ?: 0L
        val bio = dao.docsNow().filter { it.type == "bio" && it.text.isNotBlank() }.sortedByDescending { if (it.approved) 1 else 0 }.minByOrNull { it.text.length }?.text.orEmpty()
        val works = dao.artworksNow().filter { it.id in workIds(a) }
        return com.emotionalleaves.studioagent.domain.Drafts.announcement(st.fullName, o.title, o.venue.ifBlank { o.organiser }, o.city, o.exhibitionDates,
            if (opening > 0) com.emotionalleaves.studioagent.ui.fmtDate(opening) + ", " + com.emotionalleaves.studioagent.ui.clock(opening) else "", bio,
            works.mapNotNull { it.medium.ifBlank { null } }.distinct().take(2).joinToString(" and "), works.size, st.email, st.website)
    }

    /** A visit planned: its steps on your week, tied to the person, each once. The contact notes say when. */
    suspend fun planVisit(c: Contact, visit: LocalDate): Int {
        val title = c.venue.ifBlank { c.name }
        val have = dao.eventsNow().filter { it.type == "task" && it.title.endsWith(" ($title)") }.map { it.title }.toSet()
        var n = 0
        com.emotionalleaves.studioagent.domain.StudioVisit.steps(visit, LocalDate.now()).forEach { (t, d) ->
            val full = "$t ($title)"
            if (full !in have) { dao.put(Evt(type = "task", title = full, date = millis(d.atTime(if (t == "The visit") 17 else 18, 0)))); n++ }
        }
        dao.contactsNow().firstOrNull { it.id == c.id }?.let { dao.put(it.copy(notes = (it.notes + "\nStudio visit planned for $visit.").trim())) }
        return n
    }

    /** A description of the piece's picture from its details and the colors actually in it (read on the phone). */
    fun altTextFor(w: Artwork): String {
        val b = if (w.imagePath.isNotBlank()) com.emotionalleaves.studioagent.media.ImageTools.decode(w.imagePath, 400) else null
        val colors = b?.let { com.emotionalleaves.studioagent.media.Look.palette(it).filter { p -> p.second > 0.08 }.map { p -> com.emotionalleaves.studioagent.media.Look.name(p.first) } }.orEmpty().distinct()
        val light = b?.let { com.emotionalleaves.studioagent.media.Look.valueNotes(it).firstOrNull() }?.let { n -> when { n.startsWith("A full range") -> "with strong darks and lights"; n.startsWith("The values sit close") -> "soft and even in tone"; else -> "" } }.orEmpty()
        return com.emotionalleaves.studioagent.domain.Drafts.altText(w.title, Papers.medium(w), if (w.widthIn > 0) Texts.dims(w.widthIn, w.heightIn) else "", colors, light, w.notes)
    }

    // ------------------------------------------------------------ commissions

    /** The steps of a piece made for someone, in order, with the day each falls on, counted from today to the promised day. */
    fun commissionSteps(today: LocalDate, due: LocalDate): List<Pair<String, LocalDate>> {
        val span = java.time.temporal.ChronoUnit.DAYS.between(today, due).coerceAtLeast(7)
        fun at(f: Double) = today.plusDays((span * f).toLong())
        return listOf(
            "Send the quote" to today,
            "Deposit received" to today.plusDays(3),
            "Show them a draft" to at(0.5),
            "Finish the piece" to due.minusDays(3).let { if (it.isBefore(today)) today else it },
            "Final payment received" to due,
            "Hand it over" to due
        )
    }

    /** Price for a commission: your own rule for the size, plus a quarter for the back-and-forth, rounded to 5. */
    fun commissionPrice(widthIn: Double, heightIn: Double): Double {
        val base = if (widthIn > 0 && heightIn > 0) Texts.price(widthIn, heightIn, st.pricePerSqIn) else 0.0
        return if (base <= 0) 0.0 else Math.round(base * 1.25 / 5.0) * 5.0
    }

    /**
     * Someone wants a piece made: the person goes in People, the piece in Studio (as an idea with its brief,
     * size, price and promised day), and its six steps on your week. Returns the piece's id.
     */
    suspend fun startCommission(name: String, email: String, phone: String, brief: String, widthIn: Double, heightIn: Double, price: Double, due: LocalDate): Long {
        val now = System.currentTimeMillis()
        val cid = dao.contactsNow().firstOrNull { email.isNotBlank() && it.email.equals(email, true) }?.id
            ?: dao.put(Contact(name = name, email = email, kind = "client", status = "client", notes = listOf(if (phone.isNotBlank()) "Phone: $phone" else "", "Commission asked for on ${LocalDate.now()}.").filter { it.isNotBlank() }.joinToString(" "), createdAt = now))
        val id = dao.put(Artwork(title = "For " + name.ifBlank { "a client" }, status = "idea", notes = brief, widthIn = widthIn, heightIn = heightIn, price = price,
            tags = "commission", format = "physical", clientId = cid, dueDate = millis(due.atTime(12, 0)), createdAt = now))
        commissionSteps(LocalDate.now(), due).forEach { (t, d) -> dao.put(Evt(type = "task", title = t, date = millis(d.atTime(18, 0)), artworkId = id)) }
        return id
    }

    /** The quote, ready to send: what, size, price, deposit, time, and the copyright line. */
    fun commissionQuote(w: Artwork, clientName: String): Pair<String, String> {
        val dep = Math.round(w.price / 2.0).toDouble()
        val subject = "Your commission: a quote"
        val body = "Hi ${clientName.ifBlank { "there" }},\n\nThank you for asking me to make this. Here is what I have in mind:\n\n" +
            (if (w.notes.isNotBlank()) "The piece: ${w.notes.trim()}\n" else "") +
            (if (w.widthIn > 0) "Size: ${Texts.dims(w.widthIn, w.heightIn)}\n" else "") +
            (if (w.price > 0) "Price: ${money(w.price)}\nDeposit: ${money(dep)} when we agree, the rest (${money(w.price - dep)}) when it is finished and you are happy with it\n" else "") +
            (if (w.dueDate > 0) "Ready by: ${date(w.dueDate)}\n" else "") +
            "\nI will show you a draft along the way, so there are no surprises. I keep the copyright, so I can show the piece in my portfolio; it is yours to keep and enjoy.\n\n" +
            "If that all sounds right, reply yes and I will send the deposit details.\n\n${st.fullName}"
        return subject to body
    }

    private fun money(d: Double) = st.currency + (if (d == Math.floor(d)) "%,.0f".format(d) else "%,.2f".format(d))

    /** Money in for a commission: the deposit or the rest. Recorded as art income, against the piece. */
    suspend fun commissionPaid(w: Artwork, deposit: Boolean) {
        val paid = dao.ledgerNow().filter { it.artworkId == w.id && it.kind == "sale" }.sumOf { it.amount }
        val amount = if (deposit) Math.round(w.price / 2.0).toDouble() else (w.price - paid).coerceAtLeast(0.0)
        if (amount > 0) dao.put(Ledger(kind = "sale", amount = amount, date = System.currentTimeMillis(), note = (if (deposit) "Deposit on " else "Final payment on ") + w.title, artworkId = w.id))
        dao.eventsNow().firstOrNull { it.artworkId == w.id && it.title == (if (deposit) "Deposit received" else "Final payment received") }?.let { dao.put(it.copy(done = true)) }
    }

    /** Handed over: the piece is sold (to a private collection on your CV), the step ticked. */
    suspend fun commissionDelivered(w: Artwork) {
        dao.put(w.copy(status = "sold", location = "Sold", year = if (w.year == 0) LocalDate.now().year else w.year))
        dao.eventsNow().filter { it.artworkId == w.id && !it.done }.forEach { dao.put(it.copy(done = true)) }
        if (dao.factsNow().none { it.category == "collection" && it.text == "Private collection" }) dao.put(Fact(category = "collection", year = LocalDate.now().year, text = "Private collection"))
    }

    fun workIds(a: Appl): List<Long> = a.workIds.split(",").mapNotNull { it.trim().toLongOrNull() }

    /** The steps of putting up your own show, counted back from the opening (days before it), then the show and the take-down. */
    /** A one-day open studio (or a table at a fair, a pop-up): fewer steps, the list invited, the sign-up sheet out. */
    fun openStudioSteps(day: LocalDate): List<Pair<String, LocalDate>> = listOf(
        "Choose the works to show and set their prices" to day.minusDays(21),
        "Tell your email list (and invite friends and coworkers)" to day.minusDays(18),
        "Print the flyer and put it up where people will see it" to day.minusDays(14),
        "Print labels, the price list and the sign-up sheet" to day.minusDays(4),
        "Set up the space and hang the work" to day.minusDays(1),
        "Go to the opening" to day,
        "Add the new names from the sign-up sheet to your email list" to day.plusDays(2),
        "Thank the people who came" to day.plusDays(3)
    )

    fun ownShowSteps(opening: LocalDate, weeks: Int): List<Pair<String, LocalDate>> = listOf(
        "Confirm the dates, wall space and hanging method with the venue in writing" to opening.minusDays(42),
        "Choose the works and the order they hang in" to opening.minusDays(35),
        "Order prints and frames (labs take one to two weeks)" to opening.minusDays(30),
        "Print the flyer and put it up where people will see it" to opening.minusDays(21),
        "Tell your email list" to opening.minusDays(18),
        "Send the news to local listings" to opening.minusDays(17),
        "Print wall labels and the price list" to opening.minusDays(7),
        "Take condition photos of each work" to opening.minusDays(3),
        "Pack the hanging kit (Help has the list)" to opening.minusDays(3),
        "Deliver and hang the work" to opening.minusDays(2),
        "Go to the opening" to opening,
        "Photograph the work on the wall for your records" to opening.plusDays(1),
        "Pick up unsold work" to opening.plusWeeks(weeks.toLong().coerceAtLeast(1)),
        "Thank the venue" to opening.plusWeeks(weeks.toLong().coerceAtLeast(1)).plusDays(2)
    )

    /**
     * A venue said yes to a show of your own. It becomes a show you got into, like any accepted call,
     * with every step dated back from the opening, so the board, Today and the calendar carry it from here.
     */
    suspend fun planOwnShow(c: com.emotionalleaves.studioagent.data.Contact, title: String, opening: LocalDate, weeks: Int, workIds: List<Long>, oneDay: Boolean = false): Long {
        val dates = if (oneDay) com.emotionalleaves.studioagent.ui.fmtDate(millis(opening.atTime(12, 0)))
            else "${com.emotionalleaves.studioagent.ui.fmtDate(millis(opening.atTime(12, 0)))} to ${com.emotionalleaves.studioagent.ui.fmtDate(millis(opening.plusWeeks(weeks.toLong()).atTime(12, 0)))}"
        val oppId = dao.put(Opp(title = title.ifBlank { "Show at ${c.venue}" }, kind = "exhibition", venue = c.venue, city = st.city, email = c.email, url = c.website,
            scope = if (c.kind == "campus") "campus" else "local", exhibitionDates = dates, status = "applied", sourceName = "Your own show", createdAt = System.currentTimeMillis()))
        val now = System.currentTimeMillis()
        val applId = dao.put(Appl(oppId = oppId, workIds = workIds.joinToString(","), status = "accepted", submittedAt = now, method = "email", emailTo = c.email, statement = pickStatement(150),
            proofText = "Agreed with ${c.venue}${if (c.name.isNotBlank()) " (" + c.name + ")" else ""} on ${com.emotionalleaves.studioagent.ui.fmtDate(now)}.", createdAt = now))
        (if (oneDay) openStudioSteps(opening) else ownShowSteps(opening, weeks)).forEach { (t, d) -> dao.put(Evt(type = "task", title = t, date = millis(d.atTime(if (t.startsWith("Go to")) 18 else 12, 0)), oppId = oppId, applId = applId)) }
        dao.artworksNow().filter { it.id in workIds && available(it) }.forEach { dao.put(it.copy(status = "shown", location = c.venue.ifBlank { it.location })) }
        dao.put(c.copy(status = "interested", notes = (c.notes + "\nShow agreed: $title, $dates.").trim()))
        rememberFriend(c.venue)
        return applId
    }

    /** The opening happened: a show of your own becomes a true CV line now, not before. */
    suspend fun openingHappened(applId: Long) {
        val a = dao.applsNow().firstOrNull { it.id == applId } ?: return
        val o = dao.oppsNow().firstOrNull { it.id == a.oppId } ?: return
        if (o.sourceName != "Your own show") return
        if (dao.factsNow().any { it.text.contains(o.title, true) }) return
        dao.put(com.emotionalleaves.studioagent.data.Fact(category = "solo", year = LocalDate.now().year, text = listOf(o.title, o.venue, o.city).filter { it.isNotBlank() }.joinToString(", ")))
    }

    /** Documented works in the order you set for your portfolio; any not yet placed follow, newest first. */
    fun portfolioOrdered(works: List<Artwork>): List<Artwork> {
        val ids = st.portfolioOrder.split(",").mapNotNull { it.trim().toLongOrNull() }
        val docd = works.filter { documented(it) }
        return ids.mapNotNull { i -> docd.firstOrNull { it.id == i } } + docd.filter { it.id !in ids }.sortedByDescending { it.createdAt }
    }

    /** The show is over and the unsold work is home: it can go out to the next call. Its CV line stays. */
    suspend fun releaseShown(applId: Long) {
        val a = dao.applsNow().firstOrNull { it.id == applId } ?: return
        dao.artworksNow().filter { it.id in workIds(a) && it.status == "shown" }.forEach { dao.put(it.copy(status = "available", location = "Studio")) }
    }

    /** Objective checks. When every line is true the pack is ready; there is nothing else to perfect. */
    fun checklist(a: Appl, o: Opp, works: List<Artwork>, feeLeft: Double, now: Long): List<Pair<String, Boolean>> {
        val chosen = works.filter { it.id in workIds(a) }
        val out = mutableListOf<Pair<String, Boolean>>()
        out += "Deadline not passed" to (o.deadline == 0L || o.deadline > now)
        if (o.minWorks > 0) out += "At least ${o.minWorks} works chosen (${chosen.size})" to (chosen.size >= o.minWorks)
        else out += "At least one work chosen (${chosen.size})" to chosen.isNotEmpty()
        if (o.maxWorks > 0) out += "No more than ${Texts.count(o.maxWorks, "work")}" to (chosen.size <= o.maxWorks)
        // A failing line names the pieces, so you know which one to open.
        fun named(ws: List<Artwork>) = if (ws.isEmpty()) "" else " (check: " + ws.take(3).joinToString(", ") { it.title.ifBlank { "Untitled #${it.id}" } } + (if (ws.size > 3) " and ${ws.size - 3} more" else "") + ")"
        val noImage = chosen.filter { !(it.imagePath.isNotBlank() && File(it.imagePath).exists()) }
        val noFacts = chosen.filter { !(it.title.isNotBlank() && it.year > 0 && it.widthIn > 0 && it.heightIn > 0) }
        out += "Every work has an image" + named(noImage) to (chosen.isNotEmpty() && noImage.isEmpty())
        out += "Every work has a title, year and size" + named(noFacts) to (chosen.isNotEmpty() && noFacts.isEmpty())
        out += "Statement included" to a.statement.isNotBlank()
        if (o.statementLimit > 0) out += "Statement within ${o.statementLimit} words (${Texts.words(a.statement)})" to (Texts.words(a.statement) <= o.statementLimit)
        out += "No stock art jargon" to Texts.jargonIn(a.statement + " " + a.themeText, st.jargonExtra).isEmpty()
        val qa = answers(a)
        // Starter drafts made without AI leave [square brackets] for you to fill; nothing goes out with one left in.
        val blanksIn = listOfNotNull("the statement".takeIf { '[' in a.statement }, "the theme text".takeIf { '[' in a.themeText },
            "an answer".takeIf { qa.any { '[' in it.second } }, "the email".takeIf { a.method == "email" && '[' in a.emailBody })
        out += "No [blanks] left to fill" + (if (blanksIn.isEmpty()) "" else " (in " + blanksIn.joinToString(", ") + ")") to blanksIn.isEmpty()
        if (qa.isNotEmpty()) out += "Every question answered (${qa.count { it.second.isNotBlank() }} of ${qa.size})" to qa.all { it.second.isNotBlank() }
        if (o.fee > 0 && feeLeft < Double.MAX_VALUE) out += "Fee of ${st.currency}${"%.0f".format(o.fee)} fits this month's budget" to (o.fee <= feeLeft)
        if (a.method == "email") out += "Submission email address known" to Texts.looksLikeEmail(a.emailTo)
        if (a.packDir.isNotBlank() && o.imageSpecs.isNotBlank()) {
            val limit = ImageTools.parseSpecs(o.imageSpecs).second / 0.95
            out += "Every picture is within the call's file size" to packFiles(a).filter { it.extension == "jpg" }.all { it.length() <= limit }
        }
        // Calls often cap the total attachment size; most mail services refuse anything over about 25 MB.
        if (a.method == "email" && a.packDir.isNotBlank()) out += "Attachments under 20 MB in total" to (packFiles(a).sumOf { it.length() } < 20L * 1024 * 1024)
        return out
    }

    /**
     * Exports images to the call's exact spec, numbered to match the image list, plus CV, image list, statement and answers.
     * Every file starts with the artist's last name, so nothing gets lost among other applicants' files.
     */
    suspend fun buildPack(ctx: Context, a: Appl, o: Opp): File = withContext(Dispatchers.IO) {
        val dir = File(ImageTools.exportDir(ctx), "pack_${a.id}").apply { deleteRecursively(); mkdirs() }
        val ids = workIds(a)
        val works = dao.artworksNow().filter { it.id in ids }.sortedBy { ids.indexOf(it.id) }
        val (px, bytes) = ImageTools.parseSpecs(o.imageSpecs)
        val names = works.mapIndexed { i, w -> Texts.fileName(st.lastName.ifBlank { st.firstName }, w.title, w.year, Papers.medium(w), w.widthIn, w.heightIn, number = i + 1) }
        works.forEachIndexed { i, w -> ImageTools.export(w.imagePath, px, bytes, File(dir, names[i]), st.fullName, Papers.caption(w)) }
        // Calls ask for an "image list": numbered like the picture files, with prices for anything for sale.
        Papers.imageList(ctx, st, works, withPrices = true, dir = dir, files = names, title = "Image list")
        Papers.cv(ctx, st, dao.factsNow()).copyTo(File(dir, Papers.docName(st, "CV")), overwrite = true)
        if (a.statement.isNotBlank() || a.themeText.isNotBlank())
            Papers.textDoc(ctx, st, "Artist statement", listOfNotNull(a.statement.takeIf { it.isNotBlank() }?.let { "" to it }, a.themeText.takeIf { it.isNotBlank() }?.let { "On the theme" to it }), dir, Papers.docName(st, "Statement"))
        val qa = answers(a).filter { it.second.isNotBlank() }
        if (qa.isNotEmpty()) Papers.textDoc(ctx, st, "Answers for ${o.title}", qa, dir, Papers.docName(st, "Answers"))
        dao.put(a.copy(packDir = dir.absolutePath))
        dir
    }

    fun packFiles(a: Appl): List<File> = if (a.packDir.isBlank()) emptyList() else File(a.packDir).listFiles()?.sortedBy { it.name }.orEmpty()

    /** Locks the application as done. After this there is nothing to check by hand. */
    suspend fun markSubmitted(a: Appl, proof: String, proofPath: String = "") {
        val now = System.currentTimeMillis()
        dao.put(a.copy(status = "submitted", submittedAt = now, proofText = proof, proofPath = proofPath.ifBlank { a.proofPath }))
        dao.oppsNow().firstOrNull { it.id == a.oppId }?.let { dao.put(it.copy(status = "applied")) }
        dao.artworksNow().filter { it.id in workIds(a) && available(it) }.forEach { dao.put(it.copy(status = "pending")) }
        dao.eventsNow().filter { it.applId == a.id && it.type in setOf("pack", "submit", "deadline") }.forEach {
            // Sent means done: the "Submit" and "Deadline" entries leave your phone's calendar too.
            if (it.calendarId > 0) try { Cal.remove(G, it.calendarId) } catch (e: Exception) { }
            dao.put(it.copy(done = true, calendarId = 0))
        }
        st.log("Application locked as sent: " + (dao.oppsNow().firstOrNull { it.id == a.oppId }?.title ?: "application") + ". " + proof)
    }

    /** Unlocks a submitted application, for the rare case it really has to change. */
    suspend fun reopen(a: Appl) {
        dao.put(a.copy(status = "ready", proofText = "Reopened on ${com.emotionalleaves.studioagent.ui.fmtDate(System.currentTimeMillis())}. Earlier note: ${a.proofText}".take(600)))
        dao.oppsNow().firstOrNull { it.id == a.oppId }?.let { dao.put(it.copy(status = "yes")); scheduleDeadline(G, it, a.id) }
        dao.artworksNow().filter { it.id in workIds(a) && it.status == "pending" }.forEach { dao.put(it.copy(status = "available")) }
        // The sent-and-done reminders give way to fresh ones (and calendar entries) for the same deadline.
        dao.eventsNow().filter { it.applId == a.id && it.type in setOf("pack", "submit", "deadline") && it.done }.forEach { dao.del(it) }
    }

    suspend fun sendApplication(ctx: Context, id: Long): Boolean {
        val a = dao.applsNow().firstOrNull { it.id == id } ?: return false
        if (a.status != "sending") return false
        return try {
            withContext(Dispatchers.IO) { Mail.send(st.smtpHost, st.mailUser, st.mailPass, a.emailTo, a.emailSubject, a.emailBody, packFiles(a), st.fullName) }
            markSubmitted(a, "Sent by email to ${a.emailTo} on ${com.emotionalleaves.studioagent.ui.fmtDate(System.currentTimeMillis())} with ${Texts.count(packFiles(a).size, "attachment")}.")
            true
        } catch (e: Exception) {
            // Never fail quietly: say so, in plain words, and open the application when tapped.
            val why = com.emotionalleaves.studioagent.ui.plainError(e)
            dao.put(a.copy(status = "ready", proofText = "Email did not send: $why"))
            try { Notifier.post(ctx, st, 400 + a.id.toInt(), "An email did not send", why + " Nothing was lost; tap to try again.", "appl/${a.id}") } catch (x: Exception) { }
            false
        }
    }

    fun scheduleGoal(ctx: Context, inMs: Long, goal: Int) {
        if (inMs <= 0) return
        val req = OneTimeWorkRequestBuilder<GoalWorker>().setInitialDelay(inMs, TimeUnit.MILLISECONDS).setInputData(workDataOf("goal" to goal, "start" to st.sessionStart)).build()
        try { WorkManager.getInstance(ctx).enqueueUniqueWork("goal", ExistingWorkPolicy.REPLACE, req) } catch (e: Exception) { }
    }

    fun cancelGoal(ctx: Context) { try { WorkManager.getInstance(ctx).cancelUniqueWork("goal") } catch (e: Exception) { } }

    fun queueSend(ctx: Context, applId: Long) {
        val req = OneTimeWorkRequestBuilder<SendWorker>().setInitialDelay(10, TimeUnit.MINUTES).setInputData(workDataOf("id" to applId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("send-$applId", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelSend(ctx: Context, applId: Long) { WorkManager.getInstance(ctx).cancelUniqueWork("send-$applId") }

    suspend fun recordResult(ctx: Context, a: Appl, accepted: Boolean, text: String) {
        if (accepted) dao.oppsNow().firstOrNull { it.id == a.oppId }?.let { rememberFriend(it.organiser, it.venue) }
        val opp = dao.oppsNow().firstOrNull { it.id == a.oppId }
        val works = dao.artworksNow().filter { it.id in workIds(a) }
        dao.put(a.copy(status = if (accepted) "accepted" else "declined", resultText = text.take(4000)))
        st.log("Result recorded for ${opp?.title ?: "an application"}: " + (if (accepted) "accepted." else "not this time. The works are free again."))
        opp?.let { dao.put(it.copy(status = if (accepted) "accepted" else "declined")) }
        if (!accepted) {
            works.filter { it.status == "pending" }.forEach { dao.put(it.copy(status = "available")) }
            return
        }
        works.forEach { dao.put(it.copy(status = "shown", location = opp?.venue?.ifBlank { opp.organiser } ?: it.location)) }
        if (opp != null) {
            val line = listOf(opp.title, opp.venue.ifBlank { opp.organiser }, opp.city).filter { it.isNotBlank() }.joinToString(", ")
            // The CV heading follows the kind of call: a residency is listed under Residencies, a grant under Grants.
            val cat = when (opp.kind) { "residency" -> "residency"; "grant" -> "grant"; "publication" -> "publication"; else -> "group" }
            dao.put(Fact(category = cat, year = LocalDate.now().year, text = line))
        }
        val tasks: List<Pair<String, Long>> = (if (ai.ready && text.isNotBlank()) (try { ai.logistics(text) } catch (e: Exception) { emptyList<Pair<String, Long>>() }) else emptyList()).ifEmpty {
            fallbackLogistics(text, LocalDate.now())
        }
        tasks.forEach { (title, at) ->
            val calId = if (at > System.currentTimeMillis() && st.useCalendar) Cal.add(ctx, "$title (${opp?.title.orEmpty()})", at - 3_600_000, at) else 0L
            dao.put(Evt(type = "task", title = title, date = at, oppId = a.oppId, applId = a.id, calendarId = calId))
        }
    }

    /**
     * The usual steps after an acceptance, dated from the email where it says when (delivery, opening, pick-up),
     * and otherwise on sensible days: confirm within two days, the agreement within a week, preparing the week before delivery.
     */
    fun fallbackLogistics(text: String, today: LocalDate): List<Pair<String, Long>> {
        fun at(d: LocalDate?, hour: Int = 18) = d?.let { millis(it.atTime(hour, 0)) } ?: 0L
        val deliver = CallReader.dateAfter(text, "deliver|drop(ped)?[- ]?off|drop your|\\bship|arrive by|install", today)
        val opening = CallReader.dateAfter(text, "opening|reception", today)
        val pickup = CallReader.dateAfter(text, "pick(ed)?[- ]?up|collect|retriev", today)
        return listOf(
            "Confirm you are taking part" to at(today.plusDays(2)),
            "Sign and return the agreement" to at(today.plusDays(7)),
            "Frame or prepare hanging hardware" to at(deliver?.minusDays(7)),
            "Print wall labels and price list" to at(deliver?.minusDays(3)),
            "Take condition photos before the work leaves" to at(deliver?.minusDays(1)),
            "Deliver or ship the work" to at(deliver, 12),
            "Go to the opening" to at(opening),
            "Pick up unsold work" to at(pickup, 12)
        )
    }

    // ------------------------------------------------------------ outreach

    /** Four places almost every town and campus has, as blanks to fill in. Somewhere to start when you know nobody. */
    fun starterContacts(now: Long): List<Contact> = listOf(
        Contact(venue = st.school.trim().takeIf { it.length > 3 }?.let { "$it art gallery" } ?: "Your university's art gallery", kind = "campus", tier = "now", createdAt = now,
            notes = "Find out: who runs it (look for a gallery director or coordinator on its web page), whether there is a staff or community show each year, and when the next opening is. Going to one opening is the whole first step."),
        Contact(venue = st.school.trim().takeIf { it.length > 3 }?.let { "$it library or student union" } ?: "Your university library or student union", kind = "campus", tier = "now", createdAt = now - 1,
            notes = "Many have a display wall or cases and a simple request form. Ask at the front desk who books it. Staff are usually welcome."),
        Contact(venue = "Your nearest public library", kind = "community", tier = "now", createdAt = now - 2,
            notes = "Public libraries often show local artists for a month at a time. Look for 'exhibits' or 'art display' on their site, or ask at the desk."),
        Contact(venue = "A cafe or community art center near you", kind = "community", tier = "now", createdAt = now - 3,
            notes = "Look at the walls: if there is local art with small labels, they show work. Ask who to speak to. Replace this name with the real place.")
    )

    fun sentThisWeek(contacts: List<Contact>, now: Long) = contacts.count { it.sentAt > now - 7 * DAY }

    suspend fun classify(text: String): String =
        if (ai.ready) try { ai.classifyReply(text) } catch (e: Exception) { Ai.classifyByKeywords(text) } else Ai.classifyByKeywords(text)

    suspend fun applyReply(c: Contact, text: String) {
        val status = when (classify(text)) {
            "interested", "accepted" -> "interested"; "not_now" -> "not_now"; "no", "declined" -> "no"; "more" -> "more"; else -> c.status
        }
        // "Not now" is not "no": the venue comes back in six months.
        dao.put(c.copy(status = status, lastReply = text.take(3000), revisitAt = if (status == "not_now") System.currentTimeMillis() + 180 * DAY else c.revisitAt))
    }

    private suspend fun sendOutreach(now: Long) {
        if (!st.mailReady) return
        val level = level()
        var contacts = dao.contactsNow()
        for (c in contacts.filter { it.status == "approved" && it.email.contains("@") }) {
            if (sentThisWeek(contacts, now) >= st.outreachWeeklyCap) break
            if (!Levels.outreachAllowed(level, c.kind, rules()) || !c.acceptsSubmissions) continue
            try {
                Mail.send(st.smtpHost, st.mailUser, st.mailPass, c.email, c.draftSubject, c.draftBody, outreachPictures(), fromName = st.fullName)
                dao.put(c.copy(status = "sent", sentAt = now, followUpAt = now + 24 * DAY))
                st.log("Email sent to ${c.venue.ifBlank { c.email }}.")
                contacts = dao.contactsNow()
            } catch (e: Exception) { }
        }
        for (c in contacts.filter { it.status == "sent" && it.followUpAt in 1..now && it.followUpBody.isNotBlank() }) {
            try {
                Mail.send(st.smtpHost, st.mailUser, st.mailPass, c.email, "Re: " + c.draftSubject, c.followUpBody, fromName = st.fullName)
                dao.put(c.copy(status = "followed_up", revisitAt = now + 180 * DAY))
                st.log("Follow-up sent to ${c.venue.ifBlank { c.email }}. Nothing more will be sent to them.")
            } catch (e: Exception) { }
        }
    }

    // ------------------------------------------------------------ email in

    internal suspend fun checkMail(ctx: Context) {
        if (!st.mailReady) return
        val now = System.currentTimeMillis()
        val since = if (st.lastMailCheck == 0L) now - 3 * DAY else st.lastMailCheck
        val msgs = Mail.recent(st.imapHost, st.mailUser, st.mailPass, since)
        if (msgs.isEmpty()) return
        val opps = dao.oppsNow().associateBy { it.id }
        val sources = dao.sourcesNow().filter { it.kind == "email" && it.enabled && it.url.isNotBlank() }
        for (m in msgs) {
            val contact = dao.contactsNow().firstOrNull { it.email.isNotBlank() && it.email.equals(m.from, true) && it.status in setOf("sent", "followed_up") }
            if (contact != null) { applyReply(contact, m.subject + "\n" + m.text); continue }
            val submitted = dao.applsNow().filter { it.status == "submitted" }
            val appl = submitted.firstOrNull { a ->
                val o = opps[a.oppId]
                (a.emailTo.isNotBlank() && a.emailTo.equals(m.from, true)) ||
                    (o != null && o.title.length > 6 && (m.subject + " " + m.text).contains(o.title, ignoreCase = true))
            }
                // Results from a web-form entry come from the organizer's own domain. Used only when it points to exactly one entry.
                ?: submitted.filter { a -> Texts.sameSite(m.from.substringAfter('@'), opps[a.oppId]?.url.orEmpty()) }.singleOrNull()
            if (appl != null) {
                when (classify(m.subject + "\n" + m.text)) {
                    "accepted" -> { recordResult(ctx, appl, true, m.text); Notifier.post(ctx, st, 300 + appl.id.toInt(), "Accepted", "${opps[appl.oppId]?.title.orEmpty()}: your checklist is ready.", "appl/${appl.id}") }
                    "declined" -> { recordResult(ctx, appl, false, m.text); Notifier.post(ctx, st, 300 + appl.id.toInt(), "Result in", "${opps[appl.oppId]?.title.orEmpty()}: not this time. The works are free for the next call.", "appl/${appl.id}") }
                    else -> { }
                }
                continue
            }
            val src = sources.firstOrNull { m.from.contains(it.url.trim(), ignoreCase = true) }
            // With AI the email is read properly; without it the plain reader still picks out title, deadline, fee and link.
            if (src != null) {
                val n = if (ai.ready) try { ingest(m.subject + "\n" + m.text, src.name, fromListing = true) } catch (e: Exception) { 0 } else 0
                if (n == 0) try { ingestPlain(m.subject + "\n" + m.text, src.name) } catch (e: Exception) { }
            }
        }
        st.lastMailCheck = msgs.maxOf { it.date }
        st.log("Checked your inbox: ${msgs.size} new messages looked at.")
    }

    // ------------------------------------------------------------ backup and portfolio page

    suspend fun backupToGitHub(ctx: Context): Int = withContext(Dispatchers.IO) {
        if (!st.ghReady || st.ghBackupRepo.isBlank()) return@withContext 0
        val done = st.ghUploaded.split("|").filter { it.isNotBlank() }.toMutableSet()
        var n = 0
        val base = ctx.filesDir
        File(base, "media").walkTopDown().filter { it.isFile && it.length() < 50L * 1024 * 1024 }.forEach { f ->
            val key = f.relativeTo(base).path + "@" + f.lastModified()
            if (key !in done) {
                GitHub.putFile(st.ghBackupRepo, "backup/" + f.relativeTo(base).path, f.readBytes(), "Backup ${f.name}", st.ghToken)
                done += key; n++
            }
        }
        val db = ctx.getDatabasePath(com.emotionalleaves.studioagent.data.Db.NAME)
        if (db.exists()) { GitHub.putFile(st.ghBackupRepo, "backup/studio.db", db.readBytes(), "Backup catalog ${LocalDate.now()}", st.ghToken); n++ }
        st.ghUploaded = done.joinToString("|")
        n
    }

    suspend fun publishPortfolio(ctx: Context): String = withContext(Dispatchers.IO) {
        if (!st.ghReady || st.ghPagesRepo.isBlank()) throw Exception("Add a GitHub token and a portfolio repo in Settings first.")
        val works = portfolioOrdered(dao.artworksNow())
        val docs = dao.docsNow()
        val statement = docs.filter { it.type == "statement" && it.approved && it.text.isNotBlank() }.maxByOrNull { it.text.length }?.text.orEmpty()
        val bio = docs.filter { it.type == "bio" && it.approved && it.text.isNotBlank() }.minByOrNull { it.text.length }?.text.orEmpty()
        val tmp = File(ctx.cacheDir, "site").apply { mkdirs() }
        val pairs = works.map { a ->
            val name = Texts.fileName(st.lastName, a.title, a.year, "", 0.0, 0.0).replace(".jpg", "_${a.id}.jpg")
            val f = File(tmp, name)
            if (ImageTools.export(a.imagePath, 1600, 900_000, f, st.fullName, Papers.caption(a))) GitHub.putFile(st.ghPagesRepo, "img/$name", f.readBytes(), "Add ${a.title}", st.ghToken)
            a to name
        }
        val upcoming = dao.applsNow().filter { it.status == "accepted" }.mapNotNull { a -> dao.oppsNow().firstOrNull { it.id == a.oppId } }
            .filter { o -> dao.eventsNow().any { e -> e.oppId == o.id && e.type == "task" && !e.done && e.title.startsWith("Pick up", true) } }
        // Installation views: up to six photos from shows you were in, captioned with the show.
        val opps = dao.oppsNow().associateBy { it.id }
        val installs = dao.applsNow().filter { it.status == "accepted" }.flatMap { a ->
            ImageTools.mediaDir(ctx).listFiles { f -> f.name.startsWith("show_${a.id}_") }.orEmpty().sortedBy { it.name }.take(2).map { f -> f to (opps[a.oppId]?.let { o -> listOf(o.title, o.venue, o.city).filter { it.isNotBlank() }.joinToString(", ") }.orEmpty()) }
        }.takeLast(6).mapNotNull { (f, cap) ->
            val name = "install_" + f.name
            val out = File(tmp, name)
            if (ImageTools.export(f.absolutePath, 1600, 900_000, out, st.fullName, "Installation view, $cap")) { GitHub.putFile(st.ghPagesRepo, "img/$name", out.readBytes(), "Add installation view", st.ghToken); name to cap } else null
        }
        val html = Papers.portfolioHtml(st, pairs, statement, bio, dao.factsNow(), upcoming, dao.seriesNow().associate { it.id to it.name }, installs)
        GitHub.putFile(st.ghPagesRepo, "index.html", html.toByteArray(), "Update portfolio", st.ghToken)
        GitHub.enablePages(st.ghPagesRepo, st.ghToken)
        val url = GitHub.pagesUrl(st.ghPagesRepo)
        if (st.website.isBlank()) st.website = url
        url
    }

    // ------------------------------------------------------------ what needs you

    suspend fun pending(): List<Pending> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Pending>()
        dao.docsNow().filter { it.type in setOf("statement", "bio", "proposal", "custom") && it.text.isNotBlank() && !it.approved && it.parkedUntil < now }
            .forEach { out += Pending("Approve " + (if (it.type == "custom") it.title.ifBlank { "the document" } else docWords(it.type, it.variant)), "${Texts.count(Texts.words(it.text), "word")}, written from your answers", "doc/${it.id}") }
        val opps = dao.oppsNow().associateBy { it.id }
        dao.applsNow().filter { it.status == "draft" || it.status == "ready" }
            .forEach { out += Pending("Application: ${opps[it.oppId]?.title.orEmpty()}", (if (it.status == "ready") "Ready to send" else "Review the pack") +
                (opps[it.oppId]?.deadline?.takeIf { d -> d > 0 }?.let { d -> ". Deadline ${com.emotionalleaves.studioagent.ui.fmtDate(d)}" } ?: ""), "appl/${it.id}") }
        dao.contactsNow().forEach { c ->
            when {
                c.status == "drafted" -> out += Pending("Approve email to ${c.venue.ifBlank { c.name }}", "First email and follow-up, one tap", "contact/${c.id}")
                c.status == "approved" && !st.mailReady -> out += Pending("Send email to ${c.venue.ifBlank { c.name }}", "Opens your mail app, filled in", "contact/${c.id}")
                c.status == "sent" && c.followUpAt in 1..now && !st.mailReady -> out += Pending("Follow up with ${c.venue.ifBlank { c.name }}", "Opens your mail app, filled in", "contact/${c.id}")
                c.status == "interested" || c.status == "more" -> out += Pending("${c.venue.ifBlank { c.name }} replied", "They want to hear more", "contact/${c.id}")
            }
        }
        // Applications come first: they are the ones with deadlines.
        return out.sortedBy { !it.route.startsWith("appl") }
    }

    /**
     * A plain summary of where things stand, for answering a question about them: today's step, the next
     * two weeks, calls closing, entries waiting, the pieces and the people. Titles and dates only, no emails.
     */
    suspend fun snapshot(): String {
        val now = System.currentTimeMillis()
        val works = dao.artworksNow(); val opps = dao.oppsNow(); val appls = dao.applsNow(); val evts = dao.eventsNow(); val people = dao.contactsNow()
        val b = StringBuilder()
        b.appendLine("Today: " + LocalDate.now() + " (" + LocalDate.now().dayOfWeek.toString().lowercase() + ")")
        if (st.firstName.isNotBlank()) b.appendLine("Artist: " + st.firstName + (if (st.city.isNotBlank()) ", " + st.city else ""))
        if (st.mediums.isNotBlank()) b.appendLine("Works in: " + st.mediums)
        if (st.goals.isNotBlank()) b.appendLine("Goal: " + st.goals)
        b.appendLine("Free time: weekday evenings and weekends (day job Monday to Friday)")
        nextStep().let { b.appendLine("Today's step in the app: " + it.title + ". " + it.detail) }
        b.appendLine("Pieces: " + works.size + " (" + works.groupBy { it.status }.entries.joinToString(", ") { it.value.size.toString() + " " + statusWord(it.key) } + ")")
        works.take(12).forEach { w -> b.appendLine("- piece: " + w.title.ifBlank { "Untitled" } + listOf(w.medium, if (w.widthIn > 0) "${w.widthIn.toInt()}x${w.heightIn.toInt()} in" else "").filter { it.isNotBlank() }.joinToString(", ", " (", ")") + ", " + statusWord(w.status)) }
        val open = opps.filter { it.status != "no" && it.deadline > now }.sortedBy { it.deadline }
        b.appendLine("Calls still open: " + open.size)
        open.sortedByDescending { it.fitScore }.take(8).sortedBy { it.deadline }.forEach { o -> b.appendLine("- call: " + o.title + ", closes " + date(o.deadline) + (if (o.fee > 0) ", fee " + o.fee.toInt() else ", free") + ", fit " + o.fitScore + "/100" + (if (o.city.isNotBlank()) ", " + o.city else if (o.online) ", online" else "")) }
        appls.filter { it.status != "withdrawn" }.take(10).forEach { a -> val o = opps.firstOrNull { it.id == a.oppId }; b.appendLine("- entry: " + (o?.title ?: "a call") + ", " + when (a.status) { "submitted", "sending" -> "submitted, waiting to hear"; "accepted" -> "accepted"; "declined" -> "not chosen"; else -> "being prepared" }) }
        evts.filter { !it.done && it.date in now..(now + 14 * DAY) }.take(12).forEach { e -> b.appendLine("- coming up " + date(e.date) + ": " + e.title) }
        val venues = people.filter { it.kind != "subscriber" }
        b.appendLine("Venues and people saved: " + venues.size + " (" + venues.count { it.status == "sent" } + " written to, " + venues.count { it.status == "interested" || it.status == "more" } + " interested); email list: " + people.count { it.kind == "subscriber" })
        return b.toString().take(6000)
    }

    private fun statusWord(s: String) = when (s) { "in_progress" -> "in progress"; "documented", "available" -> "ready to send"; "pending" -> "out on a call"; else -> s.replace('_', ' ') }

    /** The single thing shown on the home screen. */
    suspend fun nextStep(): NextStep {
        val now = System.currentTimeMillis(); val today = LocalDate.now()
        if (st.sessionStart > 0) return NextStep("Session in progress", "Tap to go back to it.", "session")
        if (!st.onboarded) return NextStep("Start with the two-minute tour", "How the app works, a page at a time. Nothing to fill in.", "tour")
        if (st.pinnedStep.isNotBlank()) return NextStep(st.pinnedStep, "Pinned by you. Clear it below when it is done.", "home")
        if (st.snoozeDay == today.toString()) return NextStep("Nothing else needs you today", "You set today's step aside. It will be back tomorrow.", "session")
        if (st.pausedUntil > now) return NextStep("Planning is paused", "Until ${date(st.pausedUntil)}. Nothing is being counted or missed. Start a session any time you feel like it.", "session")
        val works = dao.artworksNow(); val docs = dao.docsNow(); val series = dao.seriesNow()
        // Back after a gap: no catching up, no list. Five minutes on the piece you were on is the whole step.
        val lastSession = dao.sessionsNow().filter { it.end > it.start }.maxOfOrNull { it.end } ?: 0L
        if (lastSession in 1..(now - 10 * DAY) && st.welcomedBack < lastSession && dao.eventsNow().none { it.type == "submit" && !it.done && it.date in (now - DAY)..(now + 4 * DAY) }) {
            val piece = currentPiece(works)
            return NextStep("Welcome back", "Nothing was missed and nothing needs catching up. " + (piece?.let { "Five minutes on ${it.title.ifBlank { "your piece" }} is the whole step." } ?: "Five minutes of anything is the whole step."), "session")
        }
        val soon = dao.eventsNow().filter { it.type == "submit" && !it.done && it.date in (now - DAY)..(now + 4 * DAY) }
        val appls = dao.applsNow()
        soon.firstOrNull()?.let { e ->
            appls.firstOrNull { it.id == e.applId && it.status in setOf("draft", "ready") }?.let { a ->
                // Say exactly what is left, so the step is a task and not a worry.
                val o = dao.oppsNow().firstOrNull { it.id == a.oppId }
                val open = if (o == null) emptyList() else checklist(a, o, works, feeLeft(dao.ledgerNow()), now).filter { !it.second }.map { it.first.substringBefore(" (").lowercase() }
                val left = Planner.weekendsLeft(today, date(e.date + 3 * DAY)) + " to the deadline. "
                return NextStep(e.title, left + if (open.isEmpty()) "The pack is waiting for one look." else "Still to fix: " + open.take(2).joinToString("; ") + (if (open.size > 2) ", and ${open.size - 2} more." else "."), "appl/${a.id}")
            }
        }
        // A show you got into: a dated step (delivery, the opening, pick-up) in the next three days comes before anything else.
        dao.eventsNow().filter { it.type == "task" && !it.done && it.date in (now - DAY)..(now + 3 * DAY) }.minByOrNull { it.date }?.let { e ->
            val title = if (e.artworkId > 0) works.firstOrNull { it.id == e.artworkId }?.let { w -> "the commission " + w.title.replaceFirstChar { it.lowercase() } }.orEmpty()
                else dao.oppsNow().firstOrNull { it.id == e.oppId }?.title?.let { "for $it" }.orEmpty()
            val day = date(e.date); val whenWord = when (day) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; else -> if (day.isBefore(today)) "Yesterday" else day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }
            return NextStep(e.title, "$whenWord" + (if (title.isNotBlank()) ", $title" else "") + ". Tick it off when it is done.", if (e.artworkId > 0) "work/${e.artworkId}" else "appl/${e.applId}")
        }
        if ((today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) && works.isNotEmpty() && st.lastReviewWeek != weekId())
            return NextStep("Look at next week (3 minutes)", "Pick one piece and glance at your studio times. That is all of it.", "review")
        val pend = pending()
        if (today.dayOfWeek == DayOfWeek.SUNDAY && pend.isNotEmpty())
            return NextStep("Admin pass: ${pend.size} to approve", "About 10 minutes, all in one go.", "admin")
        if (st.energyDay == today.toString() && st.energy == "wiped")
            return NextStep("Light day", "Sort reference images, or a 15-minute sketch. Or nothing: the week still counts.", "session")
        if (docs.none { it.type == "interview" && it.text.isNotBlank() })
            return NextStep("Do the 10-minute interview", "Answer by voice or text once. Every statement, bio and email is written from it.", "interview")
        if (series.isEmpty()) return NextStep("Name your first series", "A working title is enough. Aim for 10 works that belong together.", "works")
        val current = currentPiece(works)
        val finished = works.firstOrNull { it.status == "finished" }
        if (finished != null) return NextStep("Document \"${finished.title.ifBlank { "Untitled" }}\"", "One photo or file import, then it counts toward your series.", "work/${finished.id}")
        if (current != null) return NextStep(
            firstStep(current).ifBlank { "Continue \"${current.title.ifBlank { "Untitled" }}\"" },
            "Two-minute start: " + (if (current.format == "physical") "set out your materials." else "open the file."), "session"
        )
        val best = works.filter { documented(it) }.groupBy { it.seriesId }.values.maxOfOrNull { it.size } ?: 0
        if (best >= 5 && docs.none { it.type == "statement" && it.text.isNotBlank() })
            return NextStep("Generate your statement", "It is written from your interview. You approve it in one tap.", "materials")
        if (best < st.gateWorks) return NextStep("Start the next piece", "$best of ${st.gateWorks} in your series so far.", "works")
        if (pend.isNotEmpty()) return NextStep("${pend.size} to approve", pend.first().title, "admin")
        // An entry with no result six weeks on: one gentle ask, at the weekend, since results do not always arrive by email.
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY)
            appls.filter { a -> a.status == "submitted" && a.submittedAt > now - 90 * DAY && run {
                    // After the call's own results day when it gave one; otherwise six weeks on.
                    val o = dao.oppsNow().firstOrNull { it.id == a.oppId }
                    val day = o?.let { com.emotionalleaves.studioagent.ui.resultsDay(it) }
                    if (day != null) today.isAfter(day.plusDays(3)) else a.submittedAt < now - 42 * DAY
                } }.minByOrNull { it.submittedAt }?.let { a ->
                val t = dao.oppsNow().firstOrNull { it.id == a.oppId }?.title.orEmpty().ifBlank { "an entry" }
                return NextStep("Heard back from $t?", "Results should be out by now. If one came, one tap records it. If not, there is nothing to do; this stops asking after three months.", "appl/${a.id}")
            }
        // An opening coming up and people who asked to hear: tell them, once, about three weeks ahead.
        dao.eventsNow().filter { it.type == "task" && !it.done && it.title.contains("opening", true) && it.date in now..(now + 24 * DAY) }.minByOrNull { it.date }?.let { e ->
            val subs = dao.contactsNow().count { it.kind == "subscriber" && Texts.looksLikeEmail(it.email) }
            if (subs > 0 && st.lastNewsletter < e.date - 30 * DAY) {
                val t = dao.oppsNow().firstOrNull { it.id == e.oppId }?.title.orEmpty().ifBlank { "your show" }
                return NextStep("Tell your email list about $t", "${Texts.count(subs, "person", "people")} asked to hear about your shows. The note is drafted; read it and send.", "update")
            }
        }
        // Six months on from a venue that did not answer or said not now: one new note, with new work, is normal and welcome.
        dao.contactsNow().filter { it.status in setOf("followed_up", "not_now") && it.revisitAt in 1..now }.minByOrNull { it.revisitAt }?.let { c ->
            return NextStep("Write to ${c.venue.ifBlank { c.name }} again", "It has been months. A short note with what is new (a finished series, a show) is how most yeses happen.", "contact/${c.id}")
        }
        // You went and looked; now a short note is natural. Only for places your level is ready for, and only with an address.
        run {
            val lvl = Levels.level(statsOf(works, docs, dao.factsNow()), rules())
            dao.contactsNow().firstOrNull { it.status == "research" && it.notes.contains("Visited on") && Texts.looksLikeEmail(it.email) && Levels.outreachAllowed(lvl, it.kind, rules()) }?.let { c ->
                return NextStep("Write to ${c.venue.ifBlank { c.name }}", "You have been there, so you can say so. Four or five sentences and three pictures; the app drafts it from your own words.", "contact/${c.id}")
            }
        }
        // Five or more pieces and no backup anywhere: one phone lost would take them all. Saturdays only, until it is on.
        if (today.dayOfWeek == DayOfWeek.SATURDAY && st.backupFolder.isBlank() && !(st.ghReady && st.ghBackupRepo.isNotBlank()) && works.count { it.imagePath.isNotBlank() } >= 5)
            return NextStep("Turn on backups", "Your catalog lives only on this phone. Two taps pick a folder, and it is saved there every day.", "settings/backup")
        // Backed up only on the phone: once a month, a copy off it (Drive from the picker) is one step.
        if (today.dayOfWeek == DayOfWeek.SUNDAY && !(st.ghReady && st.ghBackupRepo.isNotBlank()) && works.count { it.imagePath.isNotBlank() } >= 5 && now - st.lastManualBackup > 30 * DAY)
            return NextStep("Save a copy of your catalog off the phone", "Once a month: Save a backup, then pick Google Drive in the list. A lost phone then loses nothing.", "settings/backup")
        // An opening in the next few days at a place in People: go, look, say hello. That is the networking.
        dao.eventsNow().filter { it.type == "invite" && !it.done && it.date in now..(now + 3 * DAY) }.minByOrNull { it.date }?.let { e ->
            val d = date(e.date); val word = when (d) { today -> "tonight"; today.plusDays(1) -> "tomorrow"; else -> "on " + d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }
            return NextStep("${e.title} $word", "Openings are free and open to anyone. Look at the work, and say hello to whoever runs the place. Half an hour is plenty.", "outreach")
        }
        // A weekend with nothing pressing: one real place near you to go and look at. Looking is the whole step.
        if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY)
            dao.contactsNow().filter { it.status == "research" && it.revisitAt < now && it.kind != "subscriber" && it.notes.contains("OpenStreetMap") && !it.notes.contains("Visited on") }
                // The nearest one: the easiest to actually go to.
                .minByOrNull { c -> Regex("(\\d+) (mi|km) away").find(c.notes)?.groupValues?.get(1)?.toIntOrNull() ?: (if (c.notes.contains("under 1")) 0 else 999) }?.let { c ->
                    val what = c.notes.substringBefore(".").ifBlank { "A place near you" }
                    return NextStep("Go and look at ${c.venue}", "$what. Look at the walls, pick up a card, ask who books shows if it feels easy. That is the whole step.", "contact/${c.id}")
                }
        // Once a week, on a Sunday: put one finished piece where people who look you up will find it.
        if (today.dayOfWeek == DayOfWeek.SUNDAY && now - st.lastPosted > 6 * DAY) {
            val posted = st.postedIds.split(",").toSet()
            works.filter { it.imagePath.isNotBlank() && it.status in setOf("documented", "available", "shown", "sold") && it.id.toString() !in posted }.maxByOrNull { it.createdAt }?.let { w ->
                return NextStep("Post ${w.title.ifBlank { "a piece" }} online", "Jurors and venues look you up. Open Pictures and papers on the piece and tap Post it online: the picture and the words are made for you.", "work/${w.id}")
            }
        }
        // Four or more pieces and nobody asked yet: which is strongest is the question every entry turns on. A two-minute phone task.
        if (st.feedbackAskedAt == 0L && today.dayOfWeek in setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY) && works.count { documented(it) } >= 4)
            return NextStep("Ask three people which piece is strongest", "Pictures numbered, three short questions, sent by text. Their picks can set your portfolio order.", "feedback")
        // Calls that fit, once a week at most, and only when you have not looked already.
        if (now - st.lastCallsLook > 6 * DAY) {
            val fit = shortlist(dao.oppsNow(), profile(), now, stretchOk(now), st.shortlistSize)
            if (fit.isNotEmpty()) return NextStep(if (fit.size == 1) "A call fits you this week" else "${fit.size} calls fit you this week", "Yes or No on each takes a minute.", "calls")
        }
        return NextStep("Start a session", "Nothing else needs you today.", "session")
    }

    // ------------------------------------------------------------ finding calls

    /** Listing sites checked on the build server: they allow reading, show their calls without a browser, and list many US calls. */
    val starterSources = listOf(
        "ArtCall" to "https://artcall.org/calls",
        "Artwork Archive" to "https://www.artworkarchive.com/call-for-entry",
        "ArtJobs" to "https://www.artjobs.com/open-calls",
        "Artshow" to "https://www.artshow.com/juriedshows/index.html",
        // Added after the live check read them cleanly: CaFÉ is where most US juried shows take entries.
        "CaFÉ (CallForEntry)" to "https://artist.callforentry.org/festivals.php",
        "Art Deadline" to "https://artdeadline.com/",
        "ArtConnect" to "https://www.artconnect.com/opportunities",
        "ArtRabbit" to "https://www.artrabbit.com/artist-opportunities",
    )

    /**
     * The listing sites are already there, so calls arrive with nothing to set up. Sites added in a later version
     * arrive on their own too; one you removed is never put back.
     */
    suspend fun seedSources() {
        val seeded = st.seededUrls.split("\n").filter { it.isNotBlank() }.toMutableSet()
        if (seeded.isEmpty() && st.sourcesSeeded) seeded += starterSources.take(4).map { it.second }
        val have = dao.sourcesNow().map { it.url }.toSet()
        starterSources.filter { it.second !in have && it.second !in seeded }.forEach { (name, url) -> dao.put(com.emotionalleaves.studioagent.data.Source(name = name, kind = "url", url = url)) }
        st.seededUrls = (seeded + starterSources.map { it.second }).joinToString("\n")
        st.sourcesSeeded = true
    }

    /**
     * Reads each listing page once: finds the links that look like single calls, opens up to [perSource] new ones,
     * and reads each for title, deadline, fee and the rest. Works without AI; with AI the reading is fuller.
     */
    suspend fun scanSources(now: Long, perSource: Int = 8): Int = withContext(Dispatchers.IO) {
        seedSources()
        // The phone gives a background job about ten minutes. AI reading gets the first four; after that the plain reader carries on alone.
        val started = System.currentTimeMillis()
        var total = 0
        val seen = st.seenLinks.split("\n").filter { it.isNotBlank() }.toMutableList()
        val known = (dao.oppsNow().map { it.url }.filter { it.isNotBlank() } + seen).toMutableSet()
        for (s in dao.sourcesNow().filter { it.kind == "url" && it.enabled && it.url.isNotBlank() }) {
            val status = try {
                val doc = Fetch.html(s.url)
                val links = com.emotionalleaves.studioagent.net.Listing.links(doc).filter { it.url !in known }
                var added = 0
                for (l in links.take(perSource)) {
                    // Slow sites cannot push the check past the phone's limit: after eight minutes the rest wait for tomorrow.
                    if (System.currentTimeMillis() - started > 7 * 60_000) break
                    known += l.url
                    val text = try { "Link: ${l.url}\n" + Fetch.text(Fetch.html(l.url)) } catch (e: Exception) { continue }
                    val n = readCall(text, s.name, l.text, l.url, now, doc.title(), useAi = System.currentTimeMillis() - started < 4 * 60_000)
                    // A link turned down today is not opened again tomorrow, so the daily look moves on to new calls.
                    if (n == 0) seen += l.url
                    added += n
                }
                if (links.isEmpty() && Fetch.text(doc).length < 400) "The page shows almost nothing without a browser, so it cannot be read"
                else if (added == 0) (if (links.isEmpty()) "Looked; no new calls on the page" else "Read ${Texts.count(minOf(perSource, links.size), "new call")}; none still open and new") else "Added ${Texts.count(added, "call")}"
            } catch (e: Exception) { "Could not read: ${com.emotionalleaves.studioagent.ui.plainError(e)}" }
            total += Regex("Added (\\d+)").find(status)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            dao.put(s.copy(lastFetched = now, lastStatus = status))
        }
        st.seenLinks = seen.takeLast(600).joinToString("\n")
        st.lastScan = System.currentTimeMillis()
        total
    }

    /** One call's page text into the list. AI reads it when it can; the plain reader always works. Skips calls already closed. */
    private suspend fun readCall(text: String, sourceName: String, linkText: String, url: String, now: Long, siteTitle: String = "", useAi: Boolean = true): Int {
        if (useAi && ai.ready) try { val n = ingest(text, sourceName, fromListing = true); if (n > 0) return n } catch (e: Exception) { }
        val o = plainCall(text, sourceName, linkText, url, now, siteTitle) ?: return 0
        return addCalls(listOf(o))
    }

    /** The plain reading of one call page, or null when it is closed or does not read like a call. */
    fun plainCall(text: String, sourceName: String, linkText: String, url: String, now: Long, siteTitle: String = ""): Opp? {
        val read = com.emotionalleaves.studioagent.domain.CallReader.read(text, LocalDate.now(), zone, sourceName, now)
        // A campus call found on a public listing site is for some other school's students, unless it names your own city.
        val o = local(if (read.scope == "campus" && !mySchool(text)) read.copy(scope = "") else read, text)
        // A date of today is usually the page printing today's date, and a call closing today is too late to start.
        if (o.deadline in 1 until now + DAY) return null
        val looksLikeCall = o.deadline > 0 || Regex("open call|call for|submission|apply", RegexOption.IGNORE_CASE).containsMatchIn(text.take(3000))
        if (!looksLikeCall) return null
        // The page heading is usually the best title; the link's own words stand in when the heading is missing or generic.
        val generic = o.title == "Shared call" || o.title.length >= 120 || o.title.startsWith("Link:") || o.title.length < 12 || o.title.equals(siteTitle, true)
        val title = (if (generic && linkText.length >= 12 && !linkText.startsWith("Apply", true)) linkText.removeSuffix("...").trim() else o.title).take(120)
        return o.copy(title = title, url = url, dedupeKey = Texts.dedupeKey("", o.deadline, title))
    }

    /**
     * The homework on a venue, done for you: its own site is read for pages about exhibitions, display walls,
     * artist proposals or calls, and what it finds is noted on the venue with the link. Three venues a day, each once.
     */
    suspend fun researchVenues(perDay: Int = 3): Int = withContext(Dispatchers.IO) {
        val done = st.venuesRead.split(",").filter { it.isNotBlank() }.toMutableSet()
        val todo = dao.contactsNow().filter { it.kind != "subscriber" && it.website.isNotBlank() && it.id.toString() !in done && it.status in setOf("research", "drafted") }.take(perDay)
        var found = 0
        val words = Regex("exhibit|exhibition|gallery|call for (art|entr|submission)|artists?\\b|display|proposal|submit|local art|art wall|show your", RegexOption.IGNORE_CASE)
        for (c in todo) {
            done += c.id.toString()
            val doc = try { Fetch.html(c.website) } catch (e: Exception) { continue }
            val host = try { java.net.URI(doc.location()).host.orEmpty() } catch (e: Exception) { "" }
            val links = doc.select("a[href]").mapNotNull { a ->
                val t = a.text().trim(); val href = a.absUrl("href")
                if (t.length in 4..80 && words.containsMatchIn(t + " " + href) && (host.isBlank() || href.contains(host.removePrefix("www.")))) t to href else null
            }.distinctBy { it.second }.take(4)
            // What they show, against what you make: writing to a place that shows work like yours is the best use of an email.
            val shown = Planner.mediaShown(doc.text())
            val fit = if (shown.isEmpty()) "" else "They show: " + shown.joinToString(", ") + "." + (if (Planner.mediaMatch(shown, st.mediums)) " Work like yours." else " Different from yours; look at their walls before writing.")
            if (links.isNotEmpty() || fit.isNotBlank()) {
                if (links.isNotEmpty()) found++
                val note = listOf(if (links.isNotEmpty()) "Their site has: " + links.joinToString("; ") { "${it.first} (${it.second})" } else "", fit).filter { it.isNotBlank() }.joinToString("\n")
                val fresh = dao.contactsNow().firstOrNull { it.id == c.id } ?: continue
                dao.put(fresh.copy(submissionNotes = (fresh.submissionNotes + "\n" + note).trim(), notes = (fresh.notes + "\nRead their site on ${com.emotionalleaves.studioagent.ui.fmtDate(System.currentTimeMillis())}.").trim()))
            }
        }
        st.venuesRead = done.toList().takeLast(400).joinToString(",")
        found
    }

    /**
     * Openings at the places in People: each day a few venues' own pages are read for an opening or reception in the
     * next month. Each one found becomes an invitation on your calendar and Today: going and looking is how people come to know you.
     */
    suspend fun findOpenings(perDay: Int = 3): Int = withContext(Dispatchers.IO) {
        val venues = dao.contactsNow().filter { it.kind != "subscriber" && it.website.isNotBlank() && it.status != "no" }
        if (venues.isEmpty()) return@withContext 0
        val start = ((System.currentTimeMillis() / DAY) * perDay % venues.size).toInt()
        val today = LocalDate.now()
        val known = dao.eventsNow().filter { it.type == "invite" }.map { it.title + "|" + date(it.date) }.toSet()
        var n = 0
        (0 until minOf(perDay, venues.size)).map { venues[(start + it) % venues.size] }.forEach { c ->
            val text = try { Fetch.pageText(c.website) } catch (e: Exception) { return@forEach }
            val d = CallReader.dateAfter(text, "opening reception|artist reception|opening night|reception:|first friday|art walk", today) ?: return@forEach
            if (d.isBefore(today) || d.isAfter(today.plusDays(35))) return@forEach
            val title = "Opening at ${c.venue}"
            if ("$title|$d" in known) return@forEach
            dao.put(Evt(type = "invite", title = title, date = millis(d.atTime(18, 0))))
            n++
        }
        n
    }

    // ------------------------------------------------------------ the daily run

    suspend fun runDaily(ctx: Context): String {
        val log = mutableListOf<String>()
        val now = System.currentTimeMillis()
        suspend fun step(name: String, block: suspend () -> Unit) {
            try { block() } catch (e: Exception) { log += "$name: ${com.emotionalleaves.studioagent.ui.plainError(e)}" }
        }
        step("expire") {
            dao.oppsNow().filter { it.status in setOf("new", "later") && it.deadline in 1..now }.forEach { o ->
                dao.put(o.copy(status = "expired"))
                // Most calls come back every year. A good one that closed is remembered: a month before it closed last year, it is on your calendar to watch for.
                if (o.fitScore >= 60 || o.status == "later") {
                    val title = "Watch for: " + o.title.replace(Regex("\\b(19|20)\\d\\d\\b"), "").replace(Regex("\\s{2,}"), " ").trim().trimEnd(':', '-', ',')
                    val at = o.deadline + 335 * DAY
                    if (dao.eventsNow().none { it.type == "watch" && it.title == title }) dao.put(Evt(type = "watch", title = title, date = at, oppId = o.id))
                }
            }
            // Calls you never acted on are cleared three months after closing, so the lists stay quick. Anything you applied to is kept.
            val applied = dao.applsNow().map { it.oppId }.toSet()
            dao.oppsNow().filter { it.id !in applied && it.status in setOf("expired", "no") && it.deadline in 1..(now - 90 * DAY) }.forEach { dao.del(it) }
        }
        step("plan week") { planWeek(ctx) }
        // The quick steps run before the long read of the listing sites, so a slow site can never crowd them out.
        step("email") { withContext(Dispatchers.IO) { checkMail(ctx) } }
        step("outreach") { withContext(Dispatchers.IO) { sendOutreach(now) } }
        step("venues") { researchVenues() }
        step("openings") { findOpenings() }
        step("sources") { scanSources(now) }
        step("rescore") { rescore() }
        // Applications whose checks all pass get their files made overnight, so sending is one look and one tap.
        step("packs") {
            val works = dao.artworksNow(); val left = feeLeft(dao.ledgerNow())
            dao.applsNow().filter { it.submittedAt == 0L && it.status !in setOf("sending", "accepted", "declined") && (it.packDir.isBlank() || !File(it.packDir).exists()) }.forEach { a ->
                val o = dao.oppsNow().firstOrNull { it.id == a.oppId } ?: return@forEach
                if (workIds(a).isNotEmpty() && checklist(a, o, works, left, now).all { it.second }) {
                    val dir = buildPack(ctx, a, o); dao.put(a.copy(packDir = dir.absolutePath))
                }
            }
        }
        step("backup") { backupToGitHub(ctx) }
        step("folder backup") {
            if (st.backupFolder.isNotBlank()) withContext(Dispatchers.IO) { val name = com.emotionalleaves.studioagent.sys.Backup.toFolder(ctx, st); st.log("Backup saved to your folder: $name") }
        }
        step("notify") {
            val pend = pending()
            if (LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY && pend.isNotEmpty())
                Notifier.post(ctx, st, 100, "Waiting for your yes", "${pend.size} " + (if (pend.size == 1) "thing" else "things") + ", about 10 minutes. One tap each.", "admin")
            // Once a week, on Saturday, one calm line if calls fit you: the app found them, you decide.
            // If the check runs during quiet hours on Saturday, Sunday gets the notice instead; never both.
            if (LocalDate.now().dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && now - st.lastCallsNotice > 5 * DAY) {
                val fit = shortlist(dao.oppsNow(), profile(), now, stretchOk(now), st.shortlistSize)
                if (fit.isNotEmpty()) {
                    val soonest = fit.filter { it.deadline > 0 }.minByOrNull { it.deadline }
                    val sent = Notifier.post(ctx, st, 102, if (fit.size == 1) "A call fits you this week" else "${fit.size} calls fit you this week",
                        "Yes or No on each takes a minute." + (soonest?.let { " Nearest deadline: ${com.emotionalleaves.studioagent.ui.fmtDate(it.deadline)}." } ?: ""), "calls")
                    if (sent) st.lastCallsNotice = now
                }
            }
            dao.eventsNow().firstOrNull { it.type == "submit" && !it.done && it.date in (now - DAY)..(now + 2 * DAY) }?.let {
                Notifier.post(ctx, st, 101, it.title, "This one has a deadline this week.", "appl/${it.applId}")
            }
            // Sunday: the week ahead in one calm line, so Monday holds no surprises.
            if (LocalDate.now().dayOfWeek == DayOfWeek.SUNDAY && st.lastWeekAhead != weekId()) {
                val ev = dao.eventsNow().filter { !it.done && it.date in now..(now + 7 * DAY) }
                val studio = ev.count { it.type == "studio" }
                val due = ev.filter { it.type in setOf("deadline", "task", "invite") }.sortedBy { it.date }
                val parts = listOfNotNull(studio.takeIf { it > 0 }?.let { Texts.count(it, "studio time") },
                    due.firstOrNull()?.let { e -> e.title.removePrefix("Deadline: ") + " " + date(e.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) },
                    (due.size - 1).takeIf { it > 0 }?.let { "$it more dated " + if (it == 1) "thing" else "things" })
                if (parts.isNotEmpty() && Notifier.post(ctx, st, 105, "Your week ahead", parts.joinToString(", ") + ".", "week")) st.lastWeekAhead = weekId()
            }
            // A strong call that closes before the weekend notice would reach you: said once, the day it is found.
            val told = st.urgentTold.split(",").filter { it.isNotBlank() }.toMutableSet()
            shortlist(dao.oppsNow(), profile(), now, stretchOk(now), 20).filter { it.fitScore >= 75 && it.deadline in (now + DAY)..(now + 10 * DAY) && it.id.toString() !in told }
                .minByOrNull { it.deadline }?.let { o ->
                    if (Notifier.post(ctx, st, 104, "A strong fit closes soon", "${o.title}: closes ${com.emotionalleaves.studioagent.ui.fmtDate(o.deadline)}. Yes or No takes a minute.", "call/${o.id}")) {
                        told += o.id.toString(); st.urgentTold = told.toList().takeLast(50).joinToString(",")
                    }
                }
            // A show step due today or tomorrow (hang the work, the opening, pick-up): the one reminder that must not be missed.
            dao.eventsNow().filter { it.type == "task" && !it.done && it.date in now..(now + 2 * DAY) &&
                Regex("deliver|hang|opening|pick up|ship", RegexOption.IGNORE_CASE).containsMatchIn(it.title) }.minByOrNull { it.date }?.let { e ->
                val show = dao.oppsNow().firstOrNull { it.id == e.oppId }?.title.orEmpty()
                val day = if (date(e.date) == LocalDate.now()) "Today" else "Tomorrow"
                Notifier.post(ctx, st, 103, "$day: ${e.title}", show.ifBlank { "Your show" } + ". Tick it off on the show's page when it is done.", "appl/${e.applId}")
            }
        }
        // The home-screen widget starts the day on the day's step.
        try { nextStep().let { n -> st.widgetTitle = n.title; st.widgetDetail = n.detail; st.widgetRoute = n.route }; com.emotionalleaves.studioagent.sys.NextWidget.refresh(ctx) } catch (e: Exception) { }
        // Have the flyer reader ready before the first flyer photo, not after.
        com.emotionalleaves.studioagent.media.Ocr.warmUp()
        st.lastDaily = now
        st.lastDailyStatus = if (log.isEmpty()) "All steps ran" else log.joinToString("; ")
        st.log("Daily check finished. " + (if (log.isEmpty()) "Everything ran." else "Some steps could not run: " + log.joinToString("; ")))
        return st.lastDailyStatus
    }
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try { Engine.runDaily(applicationContext); Result.success() } catch (e: Exception) { Result.retry() }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val next = try { G.dao.artworksNow().firstOrNull { it.status == "in_progress" }?.let { Engine.firstStep(it) }.orEmpty() } catch (e: Exception) { "" }
        Notifier.post(applicationContext, G.settings, 50, inputData.getString("title") ?: "Studio time",
            if (next.isNotBlank()) "First small step: $next" else "One small first step is enough.", inputData.getString("route") ?: "session", choices = true)
        return Result.success()
    }
}

/** Says, once and gently, that the session's minutes are done. Nothing if the session already ended. */
class GoalWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = G.settings
        if (s.sessionStart == 0L || s.sessionStart != inputData.getLong("start", -1)) return Result.success()
        val goal = inputData.getInt("goal", 0)
        Notifier.post(applicationContext, s, 52, "That is your $goal minutes", "Stopping now is a complete session. Keep going only if you want to.", "session")
        return Result.success()
    }
}

/** Sends an approved email application after the 10-minute undo window. */
class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Engine.sendApplication(applicationContext, inputData.getLong("id", 0))
        return Result.success()
    }
}
