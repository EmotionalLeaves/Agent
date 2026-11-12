package com.emotionalleaves.studioagent

import android.app.Application
import java.io.File
import com.emotionalleaves.studioagent.ai.Ai
import com.emotionalleaves.studioagent.data.Dao
import com.emotionalleaves.studioagent.data.Db
import com.emotionalleaves.studioagent.data.Settings
import com.emotionalleaves.studioagent.sys.Notifier
import com.emotionalleaves.studioagent.work.Engine

/** The one app instance. Everything is on the phone; there is no server of ours. */
lateinit var G: App

class App : Application() {
    lateinit var db: Db
    lateinit var settings: Settings
    lateinit var ai: Ai
    val dao: Dao get() = db.dao()

    override fun onCreate() {
        super.onCreate()
        G = this
        settings = Settings(this)
        db = Db.open(this)
        ai = Ai(settings)
        Notifier.channel(this)
        try { Engine.schedule(this) } catch (e: Exception) { }
        catchCrashes()
    }

    val crashFile: File get() = File(filesDir, "last_crash.txt")

    fun versionName(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    /** If the app ever falls over, keep what happened so it can be sent in one tap next time it opens. */
    private fun catchCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashFile.writeText("Studio Agent ${versionName()}, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}\n" +
                    java.util.Date().toString() + "\n" + android.util.Log.getStackTraceString(error).take(6000))
            } catch (e: Exception) { }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Everything useful for fixing a problem, with no keys, passwords or artwork in it. */
    fun problemReport(): String = buildString {
        appendLine("Studio Agent ${versionName()}, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}")
        appendLine("AI: ${if (ai.ready) ai.who() else "off"}; email: ${if (settings.mailReady) "set" else "none"}; GitHub: ${if (settings.ghReady) "set" else "none"}")
        appendLine("Last daily check: ${settings.lastDailyStatus}")
        if (crashFile.exists()) { appendLine("Last crash:"); appendLine(crashFile.readText()) }
        appendLine("Recent activity:")
        settings.activityLines().take(15).forEach { appendLine("- " + java.util.Date(it.first) + ": " + it.second) }
    }
}
