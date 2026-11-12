package com.emotionalleaves.studioagent

import android.app.Application
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
    }
}
