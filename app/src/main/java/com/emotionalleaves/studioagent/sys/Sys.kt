package com.emotionalleaves.studioagent.sys

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.DocumentsContract
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.emotionalleaves.studioagent.R
import com.emotionalleaves.studioagent.data.Db
import com.emotionalleaves.studioagent.data.Settings
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.ui.MainActivity
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Calm notifications: none in work hours or after wind-down, and never more than the daily cap. */
object Notifier {
    private const val CHANNEL = "calm"

    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders, calls and results", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Studio times, calls that fit, deadlines and results. At most a couple a day, never in quiet hours."
        })
        nm.createNotificationChannel(NotificationChannel(SESSION, "Session running", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A silent note while a session runs, so you can get back to it"
            setSound(null, null)
        })
    }

    private const val SESSION = "session"
    private const val SESSION_ID = 7

    /** A silent, ongoing note with the session's clock while one runs. Tap it to get back. Not counted toward the daily limit. */
    fun session(ctx: Context, start: Long, title: String) {
        if (!allowed(ctx)) return
        try {
            channel(ctx)
            val pi = PendingIntent.getActivity(ctx, SESSION_ID, Intent(ctx, MainActivity::class.java).putExtra("route", "session").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, SESSION).setSmallIcon(R.drawable.ic_stat).setContentTitle("In session")
                .setContentText(title).setWhen(start).setUsesChronometer(true).setOngoing(true).setSilent(true)
                .setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_LOW).build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(SESSION_ID, n)
        } catch (e: Exception) { }
    }

    fun sessionEnded(ctx: Context) {
        try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(SESSION_ID) } catch (e: Exception) { }
    }

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun quietNow(s: Settings): Boolean = Planner.isQuiet(
        LocalDateTime.now(), Planner.days(s.workDays), Planner.time(s.workStart, LocalTime.of(7, 0)),
        Planner.time(s.workEnd, LocalTime.of(15, 30)), Planner.time(s.bedtime, LocalTime.of(22, 30))
    )

    /** Returns true if it was shown. Dropped items are never lost: they wait on the home card and in the admin pass. */
    fun post(ctx: Context, s: Settings, id: Int, title: String, text: String, route: String = "home", choices: Boolean = false): Boolean {
        if (!allowed(ctx) || quietNow(s)) return false
        val today = LocalDate.now().toString()
        if (s.notifDay != today) { s.notifDay = today; s.notifCount = 0 }
        if (s.notifCount >= s.notifCap) return false
        val intent = Intent(ctx, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true)
        if (choices) {
            // Three honest answers to a reminder, none of them a failure.
            fun act(what: String, code: Int) = PendingIntent.getBroadcast(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(what).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            b.addAction(0, "Start", pi).addAction(0, "In 30 minutes", act("later", 61)).addAction(0, "Not today", act("skip", 62))
        }
        val n = b.build()
        return try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
            s.notifCount = s.notifCount + 1
            true
        } catch (e: Exception) { false }
    }

    fun dndGranted(ctx: Context): Boolean =
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

    fun dnd(ctx: Context, on: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        try {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        } catch (e: Exception) { }
    }
}

/** Writes studio blocks and deadlines into the phone calendar, and checks for clashes. */
object Cal {
    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun calendarId(ctx: Context): Long? {
        val proj = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            var first: Long? = null
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (c.getInt(2) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (c.getInt(1) == 1) return id
                if (first == null) first = id
            }
            return first
        }
        return null
    }

    fun busy(ctx: Context, start: Long, end: Long): Boolean {
        if (!granted(ctx)) return false
        return try {
            val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, start); ContentUris.appendId(b, end)
            ctx.contentResolver.query(b.build(), arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.ALL_DAY), null, null, null)?.use { c ->
                var clash = false
                while (c.moveToNext()) if (c.getInt(1) == 0) clash = true
                clash
            } ?: false
        } catch (e: Exception) { false }
    }

    /** Returns the calendar event id, or 0 if the calendar is not available. */
    fun add(ctx: Context, title: String, start: Long, end: Long, description: String = ""): Long {
        if (!granted(ctx)) return 0L
        return try {
            val cal = calendarId(ctx) ?: return 0L
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, if (end > start) end else start + 30 * 60_000)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L }
    }

    fun remove(ctx: Context, id: Long) {
        if (id <= 0 || !granted(ctx)) return
        try { ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) } catch (e: Exception) { }
    }
}

object Share {
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun files(ctx: Context, files: List<File>, mime: String = "*/*") {
        val existing = files.filter { it.exists() }
        if (existing.isEmpty()) return
        val i = if (existing.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri(ctx, existing[0]))
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(existing.map { uri(ctx, it) }))
        i.type = mime; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uris = existing.map { uri(ctx, it) }
        if (uris.isNotEmpty()) i.clipData = android.content.ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        start(ctx, Intent.createChooser(i, "Send").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun text(ctx: Context, text: String) =
        start(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send"))

    fun view(ctx: Context, f: File, mime: String) =
        start(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

    fun open(ctx: Context, url: String) {
        val u = if (url.startsWith("http")) url else "https://$url"
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    /** Opens the phone's mail app with everything filled in; you press Send. */
    fun email(ctx: Context, to: String, subject: String, body: String, files: List<File> = emptyList()) {
        val existing = files.filter { it.exists() }
        val i = if (existing.isEmpty()) Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        else Intent(Intent.ACTION_SEND_MULTIPLE).setType("message/rfc822").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(existing.map { uri(ctx, it) }))
        i.putExtra(Intent.EXTRA_EMAIL, arrayOf(to)).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Some mail apps only receive read access to attachments when it is carried as clip data on both intents.
        if (existing.isNotEmpty()) {
            val uris = existing.map { uri(ctx, it) }
            i.clipData = android.content.ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
        }
        start(ctx, Intent.createChooser(i, "Send email").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun start(ctx: Context, i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }
}

/** One zip with the database, your images and non-secret settings. Keys and passwords are never exported. */
object Backup {
    fun export(ctx: Context, s: Settings, dest: Uri) {
        ctx.contentResolver.openOutputStream(dest)?.use { os ->
            ZipOutputStream(os).use { zip ->
                val db = ctx.getDatabasePath(Db.NAME)
                if (db.exists()) { zip.putNextEntry(ZipEntry("db/" + Db.NAME)); db.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                val base = ctx.filesDir
                listOf("media", "exports").forEach { dir ->
                    File(base, dir).walkTopDown().filter { it.isFile }.forEach { f ->
                        zip.putNextEntry(ZipEntry("files/" + f.relativeTo(base).path)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
                val j = JSONObject(); s.exportPlain().forEach { (k, v) -> j.put(k, v) }
                zip.putNextEntry(ZipEntry("settings.json")); zip.write(j.toString().toByteArray()); zip.closeEntry()
            }
        }
    }

    /**
     * Writes today's backup into the folder you chose and keeps only the newest [keep].
     * Returns the file name. Throws with a plain message if the folder can no longer be written to.
     */
    fun toFolder(ctx: Context, s: Settings, keep: Int = 5): String {
        if (s.backupFolder.isBlank()) throw Exception("No backup folder chosen yet.")
        val tree = Uri.parse(s.backupFolder)
        val cr = ctx.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val name = "studio-agent-" + LocalDate.now() + ".zip"
        fun existing(): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) { val n = c.getString(1).orEmpty(); if (n.startsWith("studio-agent-") && n.endsWith(".zip")) out += c.getString(0) to n }
            }
            return out
        }
        existing().filter { it.second == name }.forEach { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) }
        val doc = DocumentsContract.createDocument(cr, root, "application/zip", name) ?: throw Exception("The folder would not accept a new file. Choose the folder again.")
        export(ctx, s, doc)
        existing().sortedByDescending { it.second }.drop(keep).forEach {
            try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) } catch (e: Exception) { }
        }
        s.lastAutoBackup = System.currentTimeMillis()
        return name
    }

    /** Restores a backup over the current data. The caller must close the database first and restart the app after. */
    fun restore(ctx: Context, s: Settings, src: Uri) {
        ctx.contentResolver.openInputStream(src)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name.contains("..") -> { }
                        name == "db/" + Db.NAME -> {
                            val db = ctx.getDatabasePath(Db.NAME); db.parentFile?.mkdirs()
                            db.outputStream().use { zip.copyTo(it) }
                        }
                        name.startsWith("files/") && !e.isDirectory -> {
                            val f = File(ctx.filesDir, name.removePrefix("files/")); f.parentFile?.mkdirs()
                            f.outputStream().use { zip.copyTo(it) }
                        }
                        name == "settings.json" -> {
                            val j = JSONObject(String(zip.readBytes()))
                            s.importPlain(j.keys().asSequence().associateWith { j.optString(it) })
                        }
                    }
                    e = zip.nextEntry
                }
            }
        }
    }
}

/** Handles "In 30 minutes" and "Not today" on a studio reminder. */
class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(intent.getIntExtra("id", 50))
            if (intent.action == "later") com.emotionalleaves.studioagent.work.Engine.remindLater(context)
        } catch (e: Exception) { }
    }
}

/** Quick-settings tile: one tap from anywhere to start a session. */
class SessionTile : TileService() {
    override fun onClick() {
        val i = Intent(this, MainActivity::class.java).putExtra("route", "startnow").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(i)
            }
        } catch (e: Exception) { }
    }
}

/** Home-screen widget: a single Start session button. */
class SessionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val i = Intent(context, MainActivity::class.java).putExtra("route", "startnow").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, 8, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_start)
            views.setOnClickPendingIntent(R.id.widget_button, pi)
            manager.updateAppWidget(id, views)
        }
    }
}
