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
        feeBudgetLeft = (st.feeBudget - feesThisMonth(ledger)).coerceAtLeast(0.0),
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
            keys += o.dedupeKey
            dao.put(scored(o, p)); added++
        }
        return added
    }

    /** Text (or a flyer photo) in, call records out. Needs an AI key. */
    suspend fun ingest(text: String, sourceName: String, image: ByteArray? = null): Int {
        val found = ai.extractCalls(text, sourceName, image)
        val practice = practice()
        val withFit = found.map { o ->
            if (practice.isBlank() || o.theme.isBlank()) o
            else try { val (fit, why) = ai.themeFit(o, practice); o.copy(themeFit = fit, themeReason = why) } catch (e: Exception) { o }
        }
        return addCalls(withFit)
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

    /** What to do first on a piece: its next open small step, or the line left at the end of the last session. */
    fun firstStep(a: Artwork): String = Texts.firstOpenStep(a.steps).ifBlank { a.nextStep }

    /** Starts a session straight away with sensible choices, so starting is one tap. */
    suspend fun quickStart(goalMin: Int): Long {
        val works = dao.artworksNow()
        val wiped = st.energyDay == LocalDate.now().toString() && st.energy == "wiped"
        val piece = works.firstOrNull { it.status == "in_progress" } ?: works.firstOrNull { it.status == "idea" }
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

    suspend fun createApplication(ctx: Context, opp: Opp): Long {
        dao.applsNow().firstOrNull { it.oppId == opp.id }?.let { return it.id }
        val works = dao.artworksNow().filter { available(it) }
        val bySeries = works.groupBy { it.seriesId }.values.maxByOrNull { it.size }.orEmpty()
        val want = when { opp.maxWorks > 0 -> opp.maxWorks; opp.minWorks > 0 -> opp.minWorks; else -> 3 }
        val chosen = (bySeries + works.filter { it !in bySeries }).take(want)
        val statement = pickStatement(opp.statementLimit)
        val list = chosen.mapIndexed { i, a -> "${i + 1}. ${Papers.caption(a)}" }.joinToString("\n")
        val body = "Hello,\n\nPlease find my submission for ${opp.title} attached.\n\n$list\n\n" +
            (if (statement.isNotBlank()) "Artist statement:\n$statement\n\n" else "") + "Thank you for your time.\n\n${st.fullName}\n${st.email}\n${st.website}".trim()
        val id = dao.put(
            Appl(
                oppId = opp.id, workIds = chosen.joinToString(",") { it.id.toString() }, statement = statement,
                method = if (opp.email.isNotBlank()) "email" else "web", emailTo = opp.email,
                emailSubject = "Submission: ${opp.title} / ${st.fullName}", emailBody = body, createdAt = System.currentTimeMillis()
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
        if (o.maxWorks > 0) out += "No more than ${o.maxWorks} works" to (chosen.size <= o.maxWorks)
        out += "Every work has an image" to (chosen.isNotEmpty() && chosen.all { it.imagePath.isNotBlank() && File(it.imagePath).exists() })
        out += "Every work has a title, year and size" to (chosen.isNotEmpty() && chosen.all { it.title.isNotBlank() && it.year > 0 && it.widthIn > 0 && it.heightIn > 0 })
        out += "Statement included" to a.statement.isNotBlank()
        if (o.statementLimit > 0) out += "Statement within ${o.statementLimit} words (${Texts.words(a.statement)})" to (Texts.words(a.statement) <= o.statementLimit)
        out += "No stock art jargon" to Texts.jargonIn(a.statement + " " + a.themeText, st.jargonExtra).isEmpty()
        val qa = answers(a)
        if (qa.isNotEmpty()) out += "Every question answered (${qa.count { it.second.isNotBlank() }} of ${qa.size})" to qa.all { it.second.isNotBlank() }
        if (o.fee > 0) out += "Fee of $${"%.0f".format(o.fee)} fits this month's budget" to (o.fee <= feeLeft)
        if (a.method == "email") out += "Submission email address known" to a.emailTo.contains("@")
        return out
    }

    /** Exports images to the call's exact spec and names, plus CV, image list and statement. */
    suspend fun buildPack(ctx: Context, a: Appl, o: Opp): File = withContext(Dispatchers.IO) {
        val dir = File(ImageTools.exportDir(ctx), "pack_${a.id}").apply { deleteRecursively(); mkdirs() }
        val works = dao.artworksNow().filter { it.id in workIds(a) }
        val (px, bytes) = ImageTools.parseSpecs(o.imageSpecs)
        works.forEach { w ->
            ImageTools.export(w.imagePath, px, bytes, File(dir, Texts.fileName(st.lastName, w.title, w.year, Papers.medium(w), w.widthIn, w.heightIn)))
        }
        Papers.imageList(ctx, st, works, withPrices = true, dir = dir)
        Papers.cv(ctx, st, dao.factsNow()).copyTo(File(dir, Texts.clean(st.lastName).ifBlank { "Artist" } + "_CV.pdf"), overwrite = true)
        File(dir, "Statement.txt").writeText(a.statement + (if (a.themeText.isNotBlank()) "\n\n" + a.themeText else ""))
        val qa = answers(a)
        if (qa.isNotEmpty()) File(dir, "Answers.txt").writeText(qa.joinToString("\n\n") { (q, ans) -> "$q\n$ans" })
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
        dao.put(a.copy(status = "ready", proofText = "Reopened on ${LocalDate.now()}. Earlier note: ${a.proofText}".take(600)))
        dao.oppsNow().firstOrNull { it.id == a.oppId }?.let { dao.put(it.copy(status = "yes")) }
        dao.artworksNow().filter { it.id in workIds(a) && it.status == "pending" }.forEach { dao.put(it.copy(status = "available")) }
        dao.eventsNow().filter { it.applId == a.id && it.type in setOf("pack", "submit", "deadline") }.forEach { dao.put(it.copy(done = false)) }
    }

    suspend fun sendApplication(ctx: Context, id: Long): Boolean {
        val a = dao.applsNow().firstOrNull { it.id == id } ?: return false
        if (a.status != "sending") return false
        return try {
            withContext(Dispatchers.IO) { Mail.send(st.smtpHost, st.mailUser, st.mailPass, a.emailTo, a.emailSubject, a.emailBody, packFiles(a)) }
            markSubmitted(a, "Sent by email to ${a.emailTo} on ${LocalDate.now()} with ${packFiles(a).size} attachments.")
            true
        } catch (e: Exception) {
            dao.put(a.copy(status = "ready", proofText = "Email did not send: ${e.message}"))
            false
        }
    }

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
                Mail.send(st.smtpHost, st.mailUser, st.mailPass, c.email, c.draftSubject, c.draftBody)
                dao.put(c.copy(status = "sent", sentAt = now, followUpAt = now + 24 * DAY))
                st.log("Email sent to ${c.venue.ifBlank { c.email }}.")
                contacts = dao.contactsNow()
            } catch (e: Exception) { }
        }
        for (c in contacts.filter { it.status == "sent" && it.followUpAt in 1..now && it.followUpBody.isNotBlank() }) {
            try {
                Mail.send(st.smtpHost, st.mailUser, st.mailPass, c.email, "Re: " + c.draftSubject, c.followUpBody)
                dao.put(c.copy(status = "followed_up", revisitAt = now + 180 * DAY))
                st.log("Follow-up sent to ${c.venue.ifBlank { c.email }}. Nothing more will be sent to them.")
            } catch (e: Exception) { }
        }
    }

    // ------------------------------------------------------------ email in

    private suspend fun checkMail(ctx: Context) {
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
            if (src != null && ai.ready) try { ingest(m.subject + "\n" + m.text, src.name) } catch (e: Exception) { }
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
            if (ImageTools.export(a.imagePath, 1600, 900_000, f)) GitHub.putFile(st.ghPagesRepo, "img/$name", f.readBytes(), "Add ${a.title}", st.ghToken)
            a to name
        }
        val html = Papers.portfolioHtml(st, pairs, statement, bio, Texts.cvText("", "", dao.factsNow()).trim())
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
        if (!st.onboarded) return NextStep("Set up in 2 minutes", "Your name, your week and how you want to show work.", "onboarding")
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
        val pend = pending()
        if (today.dayOfWeek == DayOfWeek.SUNDAY && pend.isNotEmpty())
            return NextStep("Admin pass: ${pend.size} to approve", "About 10 minutes, all in one go.", "admin")
        if (st.energyDay == today.toString() && st.energy == "wiped")
            return NextStep("Light day", "Sort reference images, or a 15-minute sketch. Or nothing: the week still counts.", "session")
        if (docs.none { it.type == "interview" && it.text.isNotBlank() })
            return NextStep("Do the 10-minute interview", "Answer by voice or text once. Every statement, bio and email is written from it.", "interview")
        if (series.isEmpty()) return NextStep("Name your first series", "A working title is enough. Aim for 10 works that belong together.", "works")
        val current = works.firstOrNull { it.status == "in_progress" }
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

    // ------------------------------------------------------------ the daily run

    suspend fun runDaily(ctx: Context): String {
        val log = mutableListOf<String>()
        val now = System.currentTimeMillis()
        suspend fun step(name: String, block: suspend () -> Unit) {
            try { block() } catch (e: Exception) { log += "$name: ${e.message}" }
        }
        step("expire") { dao.oppsNow().filter { it.status in setOf("new", "later") && it.deadline in 1..now }.forEach { dao.put(it.copy(status = "expired")) } }
        step("plan week") { planWeek(ctx) }
        step("sources") {
            if (ai.ready) dao.sourcesNow().filter { it.kind == "url" && it.enabled && it.url.isNotBlank() }.forEach { s ->
                val status = try {
                    val text = withContext(Dispatchers.IO) { Fetch.pageText(s.url) }
                    if (text.length < 400) "Page had almost no readable text (it may load its content with scripts)" else "Added ${ingest(text, s.name)} calls"
                } catch (e: Exception) { "Could not read: ${e.message}" }
                dao.put(s.copy(lastFetched = now, lastStatus = status))
            }
        }
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
                Notifier.post(ctx, st, 100, "Admin pass", "${pend.size} things to approve, about 10 minutes.", "admin")
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

/** Sends an approved email application after the 10-minute undo window. */
class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Engine.sendApplication(applicationContext, inputData.getLong("id", 0))
        return Result.success()
    }
}
