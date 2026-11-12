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
    private val secret: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            "secrets",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("secrets_fallback", Context.MODE_PRIVATE)
    }

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
    var region by S("region")
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

    // Money
    var feeBudget by D("feeBudget", 0.0)
    var outreachWeeklyCap by I("outreachWeeklyCap", 3)

    // AI
    var aiProvider by S("aiProvider", "anthropic")
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

    // GitHub
    var ghToken by S("ghToken", "", true)
    var ghBackupRepo by S("ghBackupRepo")
    var ghPagesRepo by S("ghPagesRepo")
    var ghUploaded by S("ghUploaded")

    // State
    var sessionStart by L("sessionStart", 0)
    var sessionArtwork by L("sessionArtwork", 0)
    var sessionKind by S("sessionKind", "make")
    var energyDay by S("energyDay")
    var energy by S("energy")
    var notifDay by S("notifDay")
    var notifCount by I("notifCount", 0)
    var lastStretch by L("lastStretch", 0)
    var noReasons by S("noReasons")
    var lastDaily by L("lastDaily", 0)
    var lastDailyStatus by S("lastDailyStatus")

    val fullName get() = "$firstName $lastName".trim()
    val onboarded get() = firstName.isNotBlank()
    val mailReady get() = mailUser.isNotBlank() && mailPass.isNotBlank()
    val ghReady get() = ghToken.isNotBlank()

    fun noReasonCounts(): Map<String, Int> =
        noReasons.split(",").filter { it.contains("=") }.associate { it.substringBefore("=") to (it.substringAfter("=").toIntOrNull() ?: 0) }

    fun addNoReason(r: String) {
        if (r.isBlank()) return
        val m = noReasonCounts().toMutableMap(); m[r] = (m[r] ?: 0) + 1
        noReasons = m.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

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
