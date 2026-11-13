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
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Evt
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Ledger
import com.emotionalleaves.studioagent.data.Opp
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
        goals = st.goals, blocked = st.blocked.split(",").map { it.trim() }.filter { it.isNotBlank() }, noReasons = st.noReasonCounts()
    )

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
            val n = scored(o, p); if (n != o) dao.put(n)
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
    suspend fun ingest(text: String, sourceName: String, image: ByteArray? = null): Int {
        val raw = ai.extractCalls(text, sourceName, image)
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
    fun local(o: Opp, text: String): Opp {
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

    /** What to do first on a piece: its next open small step, or the line left at the end of the last session. */
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
                    "Be practical and honest; say when something depends on the call or the venue. Never invent specific organisations, prices or deadlines. " +
                    "The artist uses an app that finds open calls, writes statements from their own answers, builds application packs and sends them; mention it only when it helps. " +
                    "Stay consistent with the app's own plain answers below when they cover the question.\n\n" + known.joinToString("\n") { "Q: ${it.first}\nA: ${it.second}" }.take(6000),
                q.take(800), fast = true, maxTokens = 900).trim()
        } catch (e: Exception) { }
        if (best != null && bestScore > 0) best.first + "\n\n" + best.second
        else "The writing help did not answer just now. Try the plain answers below, or ask again in a minute."
    }

    private val plainCache = mutableMapOf<Long, List<String>>()

    fun plainWordsCached(o: Opp): List<String> = plainCache[o.id].orEmpty()

    /** Three short plain lines about a call: what it is, what they want from you, anything to watch. Empty when there is nothing to read. */
    suspend fun plainWords(o: Opp): List<String> = withContext(Dispatchers.IO) {
        val src = o.rawText.ifBlank { listOf(o.theme, o.eligibility, o.prizes, o.delivery).filter { it.isNotBlank() }.joinToString("\n") }
        if (!ai.ready || src.length < 120) return@withContext emptyList()
        val out = ai.complete(
            "You explain open calls for artists to someone new to the art world who has ADHD. ${ai.dataRule} Reply with exactly three short lines, plain words, no jargon, no headings, no bullet symbols: " +
                "1) what this is and who runs it, 2) what they want you to send, 3) one thing to watch (cost, commission, image rights, delivery, travel). " +
                "Give a date only if the text gives it, and say what that date is for (entry deadline, delivery, show dates). Do not praise or recommend.",
            "<data>\nTitle: ${o.title}\n${src.take(5000)}\n</data>", fast = true, maxTokens = 260)
        val lines = out.lines().map { it.trim().trimStart('-', '*', '•').replace(Regex("^\\d+[.)]\\s*"), "").trim() }.filter { it.length in 8..220 }.take(3)
        plainCache[o.id] = lines
        lines
    }

    /**
     * Five small, concrete steps for a piece, each small enough to start tired after work.
     * The writing help suggests them from the piece's own details; without it, a sound general sequence.
     */
    suspend fun suggestSteps(w: Artwork): List<String> {
        val plain = when (w.format) {
            "print" -> listOf("Pick the final file and check its size", "Make a small test print", "Adjust colour from the test", "Order or make the final print", "Sign and number it")
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

    suspend fun createApplication(ctx: Context, opp: Opp): Long {
        dao.applsNow().firstOrNull { it.oppId == opp.id }?.let { return it.id }
        val works = dao.artworksNow().filter { available(it) }
        val bySeries = works.groupBy { it.seriesId }.values.maxByOrNull { it.size }.orEmpty()
        val want = when { opp.maxWorks > 0 -> opp.maxWorks; opp.minWorks > 0 -> opp.minWorks; else -> 3 }
        val chosen = (bySeries + works.filter { it !in bySeries }).take(want)
        val statement = pickStatement(opp.statementLimit)
        val list = chosen.mapIndexed { i, a -> "${i + 1}. ${Papers.caption(a)}" }.joinToString("\n")
        val body = applicationEmail(opp, chosen, statement)
        val id = dao.put(
            Appl(
                oppId = opp.id, workIds = chosen.joinToString(",") { it.id.toString() }, statement = statement,
                method = if (opp.email.isNotBlank()) "email" else "web", emailTo = opp.email,
                emailSubject = "${opp.title}: submission from ${st.fullName.ifBlank { "the artist" }}", emailBody = body, createdAt = System.currentTimeMillis()
            )
        )
        dao.put(opp.copy(status = "yes"))
        if (Scoring.isStretch(opp, profile())) st.lastStretch = System.currentTimeMillis()
        if (opp.deadline > 0) {
            val now = System.currentTimeMillis()
            listOf(Triple("pack", "Pack ready: ${opp.title}", opp.deadline - 7 * DAY), Triple("submit", "Submit: ${opp.title}", opp.deadline - 3 * DAY), Triple("deadline", "Deadline: ${opp.title}", opp.deadline))
                .forEach { (type, title, at) ->
                    val calId = if (at > now && st.useCalendar && type != "pack") Cal.add(ctx, title, at - 3_600_000, at) else 0L
                    dao.put(Evt(type = type, title = title, date = at, oppId = opp.id, applId = id, calendarId = calId))
                }
        }
        return id
    }

    fun workIds(a: Appl): List<Long> = a.workIds.split(",").mapNotNull { it.trim().toLongOrNull() }

    /** Objective checks. When every line is true the pack is ready; there is nothing else to perfect. */
    fun checklist(a: Appl, o: Opp, works: List<Artwork>, feeLeft: Double, now: Long): List<Pair<String, Boolean>> {
        val chosen = works.filter { it.id in workIds(a) }
        val out = mutableListOf<Pair<String, Boolean>>()
        out += "Deadline not passed" to (o.deadline == 0L || o.deadline > now)
        if (o.minWorks > 0) out += "At least ${o.minWorks} works chosen (${chosen.size})" to (chosen.size >= o.minWorks)
        else out += "At least one work chosen (${chosen.size})" to chosen.isNotEmpty()
        if (o.maxWorks > 0) out += "No more than ${Texts.count(o.maxWorks, "work")}" to (chosen.size <= o.maxWorks)
        out += "Every work has an image" to (chosen.isNotEmpty() && chosen.all { it.imagePath.isNotBlank() && File(it.imagePath).exists() })
        out += "Every work has a title, year and size" to (chosen.isNotEmpty() && chosen.all { it.title.isNotBlank() && it.year > 0 && it.widthIn > 0 && it.heightIn > 0 })
        out += "Statement included" to a.statement.isNotBlank()
        if (o.statementLimit > 0) out += "Statement within ${o.statementLimit} words (${Texts.words(a.statement)})" to (Texts.words(a.statement) <= o.statementLimit)
        out += "No stock art jargon" to Texts.jargonIn(a.statement + " " + a.themeText, st.jargonExtra).isEmpty()
        val qa = answers(a)
        // Starter drafts made without AI leave [square brackets] for you to fill; nothing goes out with one left in.
        out += "No [blanks] left to fill" to !(a.statement + a.themeText + qa.joinToString(" ") { it.second }).contains('[')
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
        Papers.imageList(ctx, st, works, withPrices = true, dir = dir, files = names)
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
        dao.eventsNow().filter { it.applId == a.id && it.type in setOf("pack", "submit", "deadline") }.forEach { dao.put(it.copy(done = true)) }
        st.log("Application locked as sent: " + (dao.oppsNow().firstOrNull { it.id == a.oppId }?.title ?: "application") + ". " + proof)
    }

    /** Unlocks a submitted application, for the rare case it really has to change. */
    suspend fun reopen(a: Appl) {
        dao.put(a.copy(status = "ready", proofText = "Reopened on ${com.emotionalleaves.studioagent.ui.fmtDate(System.currentTimeMillis())}. Earlier note: ${a.proofText}".take(600)))
        dao.oppsNow().firstOrNull { it.id == a.oppId }?.let { dao.put(it.copy(status = "yes")) }
        dao.artworksNow().filter { it.id in workIds(a) && it.status == "pending" }.forEach { dao.put(it.copy(status = "available")) }
        dao.eventsNow().filter { it.applId == a.id && it.type in setOf("pack", "submit", "deadline") }.forEach { dao.put(it.copy(done = false)) }
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
            dao.put(Fact(category = "group", year = LocalDate.now().year, text = line))
        }
        val tasks: List<Pair<String, Long>> = (if (ai.ready && text.isNotBlank()) (try { ai.logistics(text) } catch (e: Exception) { emptyList<Pair<String, Long>>() }) else emptyList()).ifEmpty {
            listOf("Confirm you are taking part", "Sign and return the agreement", "Frame or prepare hanging hardware", "Print wall labels and price list",
                "Take condition photos before the work leaves", "Deliver or ship the work", "Go to the opening", "Pick up unsold work").map { it to 0L }
        }
        tasks.forEach { (title, at) ->
            val calId = if (at > System.currentTimeMillis() && st.useCalendar) Cal.add(ctx, "$title (${opp?.title.orEmpty()})", at - 3_600_000, at) else 0L
            dao.put(Evt(type = "task", title = title, date = at, oppId = a.oppId, applId = a.id, calendarId = calId))
        }
    }

    // ------------------------------------------------------------ outreach

    /** Four places almost every town and campus has, as blanks to fill in. Somewhere to start when you know nobody. */
    fun starterContacts(now: Long): List<Contact> = listOf(
        Contact(venue = "Your university's art gallery", kind = "campus", tier = "now", createdAt = now,
            notes = "Find out: who runs it (look for a gallery director or coordinator on its web page), whether there is a staff or community show each year, and when the next opening is. Going to one opening is the whole first step."),
        Contact(venue = "Your university library or student union", kind = "campus", tier = "now", createdAt = now - 1,
            notes = "Many have a display wall or cases and a simple request form. Ask at the front desk who books it. Staff are usually welcome."),
        Contact(venue = "Your nearest public library", kind = "community", tier = "now", createdAt = now - 2,
            notes = "Public libraries often show local artists for a month at a time. Look for 'exhibits' or 'art display' on their site, or ask at the desk."),
        Contact(venue = "A cafe or community art centre near you", kind = "community", tier = "now", createdAt = now - 3,
            notes = "Look at the walls: if there is local art with small labels, they show work. Ask who to speak to. Replace this name with the real place.")
    )

    fun sentThisWeek(contacts: List<Contact>, now: Long) = contacts.count { it.sentAt > now - 7 * DAY }

    suspend fun classify(text: String): String =
        if (ai.ready) try { ai.classifyReply(text) } catch (e: Exception) { Ai.classifyByKeywords(text) } else Ai.classifyByKeywords(text)

    suspend fun applyReply(c: Contact, text: String) {
        val status = when (classify(text)) {
            "interested", "accepted" -> "interested"; "not_now" -> "not_now"; "no", "declined" -> "no"; "more" -> "more"; else -> c.status
        }
        dao.put(c.copy(status = status, lastReply = text.take(3000)))
    }

    private suspend fun sendOutreach(now: Long) {
        if (!st.mailReady) return
        val level = level()
        var contacts = dao.contactsNow()
        for (c in contacts.filter { it.status == "approved" && it.email.contains("@") }) {
            if (sentThisWeek(contacts, now) >= st.outreachWeeklyCap) break
            if (!Levels.outreachAllowed(level, c.kind, rules()) || !c.acceptsSubmissions) continue
            try {
                Mail.send(st.smtpHost, st.mailUser, st.mailPass, c.email, c.draftSubject, c.draftBody, fromName = st.fullName)
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
            val appl = dao.applsNow().firstOrNull { a ->
                val o = opps[a.oppId]
                a.status == "submitted" && ((a.emailTo.isNotBlank() && a.emailTo.equals(m.from, true)) ||
                    (o != null && o.title.length > 6 && (m.subject + " " + m.text).contains(o.title, ignoreCase = true)))
            }
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
                val n = if (ai.ready) try { ingest(m.subject + "\n" + m.text, src.name) } catch (e: Exception) { 0 } else 0
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
        val works = dao.artworksNow().filter { documented(it) }
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
        val html = Papers.portfolioHtml(st, pairs, statement, bio, dao.factsNow())
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
            .forEach { out += Pending("Approve " + (if (it.type == "custom") it.title.ifBlank { "document" } else "${it.type} (${it.variant})"), "${Texts.words(it.text)} words", "doc/${it.id}") }
        val opps = dao.oppsNow().associateBy { it.id }
        dao.applsNow().filter { it.status == "draft" || it.status == "ready" }
            .forEach { out += Pending("Application: ${opps[it.oppId]?.title.orEmpty()}", if (it.status == "ready") "Ready to send" else "Review the pack", "appl/${it.id}") }
        dao.contactsNow().forEach { c ->
            when {
                c.status == "drafted" -> out += Pending("Approve email to ${c.venue.ifBlank { c.name }}", "First email and follow-up, one tap", "contact/${c.id}")
                c.status == "approved" && !st.mailReady -> out += Pending("Send email to ${c.venue.ifBlank { c.name }}", "Opens your mail app, filled in", "contact/${c.id}")
                c.status == "sent" && c.followUpAt in 1..now && !st.mailReady -> out += Pending("Follow up with ${c.venue.ifBlank { c.name }}", "Opens your mail app, filled in", "contact/${c.id}")
                c.status == "interested" || c.status == "more" -> out += Pending("${c.venue.ifBlank { c.name }} replied", "They want to hear more", "contact/${c.id}")
            }
        }
        return out
    }

    /** The single thing shown on the home screen. */
    suspend fun nextStep(): NextStep {
        val now = System.currentTimeMillis(); val today = LocalDate.now()
        if (st.sessionStart > 0) return NextStep("Session in progress", "Tap to go back to it.", "session")
        if (!st.onboarded) return NextStep("Start with the two-minute tour", "How the app works, a page at a time. Nothing to fill in.", "tour")
        if (st.pinnedStep.isNotBlank()) return NextStep(st.pinnedStep, "Pinned by you. Clear it below when it is done.", "home")
        if (st.snoozeDay == today.toString()) return NextStep("Nothing else needs you today", "You set today's step aside. It will be back tomorrow.", "session")
        if (st.pausedUntil > now) return NextStep("Planning is paused", "Until ${date(st.pausedUntil)}. Nothing is being counted or missed. Start a session any time you feel like it.", "session")
        val works = dao.artworksNow(); val docs = dao.docsNow(); val series = dao.seriesNow()
        val soon = dao.eventsNow().filter { it.type == "submit" && !it.done && it.date in (now - DAY)..(now + 4 * DAY) }
        val appls = dao.applsNow()
        soon.firstOrNull()?.let { e ->
            appls.firstOrNull { it.id == e.applId && it.status in setOf("draft", "ready") }?.let {
                return NextStep(e.title, Planner.weekendsLeft(today, date(e.date + 3 * DAY)) + " to the deadline. The pack is waiting for one look.", "appl/${it.id}")
            }
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
        return NextStep("Start a session", "Nothing else needs you today.", "session")
    }

    // ------------------------------------------------------------ finding calls

    /** Listing sites checked on the build server: they allow reading, show their calls without a browser, and list many US calls. */
    val starterSources = listOf(
        "ArtCall" to "https://artcall.org/calls",
        "Artwork Archive" to "https://www.artworkarchive.com/call-for-entry",
        "ArtJobs" to "https://www.artjobs.com/open-calls",
        "Artshow" to "https://www.artshow.com/juriedshows/index.html",
    )

    /** On a new install the listing sites are already there, so calls arrive with nothing to set up. */
    suspend fun seedSources() {
        if (st.sourcesSeeded) return
        val have = dao.sourcesNow().map { it.url }.toSet()
        starterSources.filter { it.second !in have }.forEach { (name, url) -> dao.put(com.emotionalleaves.studioagent.data.Source(name = name, kind = "url", url = url)) }
        st.sourcesSeeded = true
    }

    /**
     * Reads each listing page once: finds the links that look like single calls, opens up to [perSource] new ones,
     * and reads each for title, deadline, fee and the rest. Works without AI; with AI the reading is fuller.
     */
    suspend fun scanSources(now: Long, perSource: Int = 6): Int = withContext(Dispatchers.IO) {
        seedSources()
        // The phone gives a background job about ten minutes. AI reading gets the first four; after that the plain reader carries on alone.
        val started = System.currentTimeMillis()
        var total = 0
        val known = dao.oppsNow().map { it.url }.filter { it.isNotBlank() }.toMutableSet()
        for (s in dao.sourcesNow().filter { it.kind == "url" && it.enabled && it.url.isNotBlank() }) {
            val status = try {
                val doc = Fetch.html(s.url)
                val links = com.emotionalleaves.studioagent.net.Listing.links(doc).filter { it.url !in known }
                var added = 0
                for (l in links.take(perSource)) {
                    known += l.url
                    val text = try { "Link: ${l.url}\n" + Fetch.text(Fetch.html(l.url)) } catch (e: Exception) { continue }
                    added += readCall(text, s.name, l.text, l.url, now, doc.title(), useAi = System.currentTimeMillis() - started < 4 * 60_000)
                }
                if (links.isEmpty() && Fetch.text(doc).length < 400) "The page shows almost nothing without a browser, so it cannot be read"
                else if (added == 0) "Looked; nothing new" else "Added $added " + (if (added == 1) "call" else "calls")
            } catch (e: Exception) { "Could not read: ${com.emotionalleaves.studioagent.ui.plainError(e)}" }
            total += Regex("Added (\\d+)").find(status)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            dao.put(s.copy(lastFetched = now, lastStatus = status))
        }
        total
    }

    /** One call's page text into the list. AI reads it when it can; the plain reader always works. Skips calls already closed. */
    private suspend fun readCall(text: String, sourceName: String, linkText: String, url: String, now: Long, siteTitle: String = "", useAi: Boolean = true): Int {
        if (useAi && ai.ready) try { val n = ingest(text, sourceName); if (n > 0) return n } catch (e: Exception) { }
        val o = plainCall(text, sourceName, linkText, url, now, siteTitle) ?: return 0
        return addCalls(listOf(o))
    }

    /** The plain reading of one call page, or null when it is closed or does not read like a call. */
    fun plainCall(text: String, sourceName: String, linkText: String, url: String, now: Long, siteTitle: String = ""): Opp? {
        val o = local(com.emotionalleaves.studioagent.domain.CallReader.read(text, LocalDate.now(), zone, sourceName, now), text)
        // A date of today is usually the page printing today's date, and a call closing today is too late to start.
        if (o.deadline in 1 until now + DAY) return null
        val looksLikeCall = o.deadline > 0 || Regex("open call|call for|submission|apply", RegexOption.IGNORE_CASE).containsMatchIn(text.take(3000))
        if (!looksLikeCall) return null
        // The page heading is usually the best title; the link's own words stand in when the heading is missing or generic.
        val generic = o.title == "Shared call" || o.title.length >= 120 || o.title.startsWith("Link:") || o.title.length < 12 || o.title.equals(siteTitle, true)
        val title = (if (generic && linkText.length >= 12 && !linkText.startsWith("Apply", true)) linkText.removeSuffix("...").trim() else o.title).take(120)
        return o.copy(title = title, url = url, dedupeKey = Texts.dedupeKey("", o.deadline, title))
    }

    // ------------------------------------------------------------ the daily run

    suspend fun runDaily(ctx: Context): String {
        val log = mutableListOf<String>()
        val now = System.currentTimeMillis()
        suspend fun step(name: String, block: suspend () -> Unit) {
            try { block() } catch (e: Exception) { log += "$name: ${com.emotionalleaves.studioagent.ui.plainError(e)}" }
        }
        step("expire") { dao.oppsNow().filter { it.status in setOf("new", "later") && it.deadline in 1..now }.forEach { dao.put(it.copy(status = "expired")) } }
        step("plan week") { planWeek(ctx) }
        step("sources") { scanSources(now) }
        step("email") { withContext(Dispatchers.IO) { checkMail(ctx) } }
        step("outreach") { withContext(Dispatchers.IO) { sendOutreach(now) } }
        step("rescore") { rescore() }
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
        }
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
