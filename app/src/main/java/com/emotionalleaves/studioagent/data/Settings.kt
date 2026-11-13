package com.emotionalleaves.studioagent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KProperty

/** Plain settings in normal preferences; keys, tokens and passwords in encrypted preferences. */
class Settings(ctx: Context) {
    val plain: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    // Keys and passwords are only unlocked when first needed, so opening the app does not wait on the phone's key store.
    private val secret: SharedPreferences by lazy { try {
        EncryptedSharedPreferences.create(
            "secrets",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("secrets_fallback", Context.MODE_PRIVATE)
    } }

    /** Bumped on every write so screens can recompute. */
    val changed = MutableStateFlow(0)

    inner class S(private val key: String, private val def: String = "", private val isSecret: Boolean = false) {
        operator fun getValue(t: Any?, p: KProperty<*>): String =
            (if (isSecret) secret else plain).getString(key, def) ?: def

        operator fun setValue(t: Any?, p: KProperty<*>, v: String) {
            (if (isSecret) secret else plain).edit().putString(key, v).apply(); changed.value++
        }
    }

    inner class I(private val key: String, private val def: Int = 0) {
        operator fun getValue(t: Any?, p: KProperty<*>): Int = plain.getInt(key, def)
        operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { plain.edit().putInt(key, v).apply(); changed.value++ }
    }

    inner class L(private val key: String, private val def: Long = 0) {
        operator fun getValue(t: Any?, p: KProperty<*>): Long = plain.getLong(key, def)
        operator fun setValue(t: Any?, p: KProperty<*>, v: Long) { plain.edit().putLong(key, v).apply(); changed.value++ }
    }

    inner class D(private val key: String, private val def: Double = 0.0) {
        operator fun getValue(t: Any?, p: KProperty<*>): Double = plain.getString(key, null)?.toDoubleOrNull() ?: def
        operator fun setValue(t: Any?, p: KProperty<*>, v: Double) { plain.edit().putString(key, v.toString()).apply(); changed.value++ }
    }

    inner class B(private val key: String, private val def: Boolean = false) {
        operator fun getValue(t: Any?, p: KProperty<*>): Boolean = plain.getBoolean(key, def)
        operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) { plain.edit().putBoolean(key, v).apply(); changed.value++ }
    }

    // You
    var firstName by S("firstName")
    var lastName by S("lastName")
    var email by S("email")
    var phone by S("phone")
    var website by S("website")
    var city by S("city")
    /** A university or college you work or study at: its campus calls are open to you, so they are kept. */
    var school by S("school")
    var region by S("region")
    /** Optional CV line, for example "b. 1998, Chicago". */
    var born by S("born")
    var mediums by S("mediums", "digital, archival print")
    var route by S("route", "print")
    var goals by S("goals", "first group show")
    var blocked by S("blocked")
    var pricePerSqIn by D("pricePerSqIn", 1.5)

    // Your week
    var workDays by S("workDays", "1,2,3,4,5")
    var workStart by S("workStart", "07:00")
    var workEnd by S("workEnd", "15:30")
    var bufferMin by I("bufferMin", 90)
    var bedtime by S("bedtime", "22:30")
    var weeklyHours by I("weeklyHours", 4)
    var notifCap by I("notifCap", 2)
    var revisionLimit by I("revisionLimit", 2)
    var dndInSession by B("dndInSession", true)
    var useCalendar by B("useCalendar", true)

    // Your own week and rules
    var weekTemplate by S("weekTemplate")
    var pausedUntil by L("pausedUntil", 0)
    var pinnedStep by S("pinnedStep")
    var snoozeDay by S("snoozeDay")
    var levelOverride by I("levelOverride", -1)
    var gateWorks by I("gateWorks", 10)
    var gateShows1 by I("gateShows1", 3)
    var gateShows2 by I("gateShows2", 8)
    var feeCap0 by D("feeCap0", 0.0)
    var feeCap1 by D("feeCap1", 25.0)
    var feeCap2 by D("feeCap2", 40.0)
    var feeCap3 by D("feeCap3", -1.0)
    var showAllLevels by B("showAllLevels", false)
    var gateOutreach by B("gateOutreach", true)
    var weights by S("weights", "2,2,2,2,2,2")
    var shortlistSize by I("shortlistSize", 7)
    var callKinds by S("callKinds", "exhibition,residency,grant,publication,fair,commission,other")
    var units by S("units", "in")
    var currency by S("currency", "$")
    var writingNotes by S("writingNotes")
    var extraJargon by S("extraJargon")
    var aiBaseUrl by S("aiBaseUrl", "https://api.openai.com/v1")
    var shortText by B("shortText", false)
    var fitWords by B("fitWords", true)
    var lockApproved by B("lockApproved", true)
    var defaultSessionMin by I("defaultSessionMin", 40)
    var sessionGoalMin by I("sessionGoalMin", 0)
    var activity by S("activity")
    var seenIntros by S("seenIntros")
    var guideManual by S("guideManual")
    var sessionPausedAt by L("sessionPausedAt", 0)
    var sessionPausedTotal by L("sessionPausedTotal", 0)

    // Money
    var feeBudget by D("feeBudget", 0.0)
    var outreachWeeklyCap by I("outreachWeeklyCap", 3)

    // AI
    var aiProvider by S("aiProvider", "anthropic")
    /** Use the free, no-account writing help when there is no key of your own. */
    var freeAi by B("freeAi", true)
    var sourcesSeeded by B("sourcesSeeded", false)
    var aiKey by S("aiKey", "", true)
    var aiModelWrite by S("aiModelWrite", "claude-sonnet-5-5")
    var aiModelFast by S("aiModelFast", "claude-haiku-5-5")
    var aiCapUsd by D("aiCapUsd", 5.0)
    var aiSpendMonth by S("aiSpendMonth")
    var aiSpendUsd by D("aiSpendUsd", 0.0)

    // Email
    var mailUser by S("mailUser")
    var mailPass by S("mailPass", "", true)
    var smtpHost by S("smtpHost", "smtp.gmail.com")
    var imapHost by S("imapHost", "imap.gmail.com")
    var lastMailCheck by L("lastMailCheck", 0)
    var lastCallsNotice by L("lastCallsNotice", 0)
    var lastScan by L("lastScan", 0)
    var lastCallsLook by L("lastCallsLook", 0)

    // GitHub
    var ghToken by S("ghToken", "", true)
    var ghBackupRepo by S("ghBackupRepo")
    var ghPagesRepo by S("ghPagesRepo")
    var ghUploaded by S("ghUploaded")
    /** Listing links already opened and turned down (closed, or not a call), newest last, so each day reaches new ones. */
    var seenLinks by S("seenLinks")
    /** Listing sites the app has put in at some point, so one you removed is not added back. */
    var seededUrls by S("seededUrls")
    var focusWork by L("focusWork", 0)
    var keepAwake by B("keepAwake", true)
    var lastReviewWeek by S("lastReviewWeek")
    var backupFolder by S("backupFolder")
    var lastAutoBackup by L("lastAutoBackup", 0)

    // State
    var sessionStart by L("sessionStart", 0)
    var sessionArtwork by L("sessionArtwork", 0)
    var sessionKind by S("sessionKind", "make")
    var energyDay by S("energyDay")
    var energy by S("energy")
    var notifDay by S("notifDay")
    var notifCount by I("notifCount", 0)
    var lastStretch by L("lastStretch", 0)
    /** When the last studio update went to your email list. */
    var lastNewsletter by L("lastNewsletter", 0)
    /** The next step as the home-screen widget shows it. */
    var widgetTitle by S("widgetTitle")
    /** Strong, soon-closing calls already mentioned once, so none is announced twice. */
    var urgentTold by S("urgentTold")
    /** Venues whose own websites have been read once for exhibition pages. */
    var venuesRead by S("venuesRead")
    /** Brown noise during sessions, on by your choice. */
    var sessionNoise by B("sessionNoise", false)
    /** Organizers and venues that have shown your work. */
    var friendlyVenues by S("friendlyVenues")
    /** The exercise a practice session is on. */
    var practiceText by S("practiceText")
    /** The order works appear in your portfolio, PDF and web page alike: ids, strongest first. */
    var portfolioOrder by S("portfolioOrder")
    /** The week the Sunday look ahead was last sent. */
    var lastWeekAhead by S("lastWeekAhead")
    /** When a backup file was last saved by hand (for example to Google Drive). */
    var lastManualBackup by L("lastManualBackup", 0)
    var widgetDetail by S("widgetDetail")
    var widgetRoute by S("widgetRoute")
    var noReasons by S("noReasons")
    var lastDaily by L("lastDaily", 0)
    var lastDailyStatus by S("lastDailyStatus")

    val routes: List<String> get() = route.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val kinds: List<String> get() = callKinds.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val jargonExtra: List<String> get() = extraJargon.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val fullName get() = "$firstName $lastName".trim()
    /** Set when the tour ends. The app is fully usable from then on, name or no name. */
    var setupDone by B("setupDone", false)
    var tourSeen by B("tourSeen", false)
    val onboarded get() = setupDone || firstName.isNotBlank()
    val mailReady get() = mailUser.isNotBlank() && mailPass.isNotBlank()
    val ghReady get() = ghToken.isNotBlank()

    fun noReasonCounts(): Map<String, Int> =
        noReasons.split(",").filter { it.contains("=") }.associate { it.substringBefore("=") to (it.substringAfter("=").toIntOrNull() ?: 0) }

    fun addNoReason(r: String) {
        if (r.isBlank()) return
        val m = noReasonCounts().toMutableMap(); m[r] = (m[r] ?: 0) + 1
        noReasons = m.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    /** A plain record of what the app did, newest first, so nothing has to be checked by hand. */
    fun log(text: String) {
        val line = System.currentTimeMillis().toString() + "|" + text.replace("\n", " ").take(200)
        activity = (listOf(line) + activity.split("\n").filter { it.isNotBlank() }).take(60).joinToString("\n")
    }

    fun activityLines(): List<Pair<Long, String>> =
        activity.split("\n").filter { it.contains("|") }.map { (it.substringBefore("|").toLongOrNull() ?: 0L) to it.substringAfter("|") }

    /** Non-secret settings, for the backup file. */
    fun exportPlain(): Map<String, String> = plain.all.mapValues { "${it.value?.javaClass?.simpleName}:${it.value}" }

    fun importPlain(m: Map<String, String>) {
        val e = plain.edit()
        m.forEach { (k, tv) ->
            val t = tv.substringBefore(":"); val v = tv.substringAfter(":")
            when (t) {
                "Integer" -> v.toIntOrNull()?.let { e.putInt(k, it) }
                "Long" -> v.toLongOrNull()?.let { e.putLong(k, it) }
                "Boolean" -> e.putBoolean(k, v == "true")
                "String" -> e.putString(k, v)
            }
        }
        e.apply(); changed.value++
    }
}
