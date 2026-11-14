package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G

/**
 * Is everything working? The parts that run on their own, each checked in plain words with what to do if not:
 * the daily check, the listing sites, the writing helper, notifications, backups, the calendar.
 */
@Composable
fun HealthScreen(nav: NavController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val sources by G.dao.sources().collectAsState(emptyList())
    var ai by remember { mutableStateOf("") }
    val now = now(); val day = 86_400_000L
    Screen("Is everything working?", nav) {
        Intro("health", "The parts of the app that work on their own, each checked in plain words. A cross says what to do; nothing here is urgent.")
        // The count first: how many of the parts that run on their own are fine, big, before the list says which.
        val okList = listOf(
            st.lastDaily > now - 2 * day,
            sources.any { it.enabled && it.kind == "url" && it.lastStatus.isNotBlank() && !it.lastStatus.startsWith("Could not read") },
            androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            maxOf(st.lastAutoBackup, st.lastManualBackup) > now - 35 * day,
            st.useCalendar)
        val fine = okList.count { it }
        Banner(if (fine == okList.size) "Everything is working" else "$fine of ${okList.size} parts working", "Each part checked in plain words below. A cross says what to do; nothing here is urgent.",
            key = 820L, height = 150, big = "$fine/${okList.size}", bigLabel = "fine")
        val ran = st.lastDaily
        Check(ran > now - 2 * day, if (ran == 0L) "The daily check has not run yet. It runs once a day when the phone is online." else "Daily check: last ran ${fmtDate(ran)}, ${clock(ran)}." + (if (ran < now - 2 * day) " Phones sometimes pause apps to save battery: allow Studio Agent to run in the background in your phone's battery settings." else ""))
        if (st.wifiOnly) Small("It waits for Wi-Fi and charging, as you chose.")
        // What went wrong last time, if anything, in the check's own words.
        st.lastDailyStatus.split("; ").filter { it.contains(":") }.take(4).takeIf { it.isNotEmpty() }?.let { probs -> Small("Last time, these parts did not finish (each tries again tomorrow): " + probs.joinToString("; ")) }
        val on = sources.filter { it.enabled && it.kind == "url" }
        val ok = on.count { it.lastStatus.isNotBlank() && !it.lastStatus.startsWith("Could not read") }
        Check(on.isNotEmpty() && ok > 0, if (on.isEmpty()) "No listing sites are on. Turn some on in Calls, All, Where calls come from." else "Listing sites: $ok of ${on.size} read cleanly last time." + (if (ok == 0) " If none read, the phone may have been offline; it tries again tomorrow." else ""))
        val n = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        Check(n, if (n) "Notifications are allowed." else "Notifications are off, so deadlines and studio times cannot reach you. Turn them on in your phone's settings for Studio Agent.")
        val backup = maxOf(st.lastAutoBackup, st.lastManualBackup)
        Check(backup > now - 35 * day, if (backup == 0L) "No backup yet: your work lives only on this phone. Settings, Backup, picks a folder in two taps." else "Last backup ${fmtDate(backup)}.")
        Check(st.useCalendar, if (st.useCalendar) "Studio times and deadlines go on your phone's calendar." else "Calendar is off; dates stay inside the app (Your week can still send them to another calendar).")
        // The focus app chosen for sessions is still on the phone, and the scanner for flat pieces is ready.
        if (st.focusApp.isNotBlank()) {
            val there = ctx.packageManager.getLaunchIntentForPackage(st.focusApp.substringBefore("|")) != null
            Check(there, if (there) "${st.focusApp.substringAfter("|")} opens from the session screen." else "${st.focusApp.substringAfter("|")} is no longer on this phone. Choose another in Settings, Hours and calm, or None.")
        }
        val gms = try { com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == com.google.android.gms.common.ConnectionResult.SUCCESS } catch (e: Throwable) { false }
        Check(gms, if (gms) "Scanning flat pieces and reading flyers work on this phone (Google Play services)." else "Google Play services is missing or out of date, so scanning flat pieces and reading flyers may not work. Taking photos still does.")
        Check(ai.startsWith("Working"), when { ai.isBlank() -> "Writing helper: not checked yet."; else -> ai })
        if (ai.isBlank()) TextButton(onClick = {
            ai = "Checking…"
            scope.io({ ai = "The writing helper did not answer: " + it + ". The app still works without it; try again later." }) {
                val t = G.ai.complete("Reply with the single word: ready.", "Are you there?", fast = true, maxTokens = 10)
                ai = if (t.isNotBlank()) "Working: the writing helper (${G.ai.who()}) answered." else "The writing helper gave an empty answer. Try again in a minute."
            }
        }) { Text("Check the writing helper") }
        // Space on the phone: your pictures are kept; the files made for sending (PDFs, exports) can be cleared and made again any time.
        fun size(f: java.io.File): Long = if (f.isDirectory) (f.listFiles()?.sumOf { size(it) } ?: 0L) else f.length()
        val media = remember { size(com.emotionalleaves.studioagent.media.ImageTools.mediaDir(ctx)) }
        var exports by remember { mutableStateOf(size(com.emotionalleaves.studioagent.media.ImageTools.exportDir(ctx))) }
        Small("Your pictures use ${com.emotionalleaves.studioagent.domain.Texts.fileSize(media)}. Files made for sending use ${com.emotionalleaves.studioagent.domain.Texts.fileSize(exports)}.")
        if (exports > 5_000_000) TextButton(onClick = { com.emotionalleaves.studioagent.media.ImageTools.exportDir(ctx).listFiles()?.filter { !it.name.startsWith("pack_") }?.forEach { it.deleteRecursively() }; exports = size(com.emotionalleaves.studioagent.media.ImageTools.exportDir(ctx)) }) { Text("Clear the files made for sending (they are made again when needed)") }
        Button(onClick = { nav.navigate("receipts") }) { Text("What the app did on its own") }
    }
}
