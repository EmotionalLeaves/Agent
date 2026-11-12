package com.emotionalleaves.studioagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** status: idea, in_progress, finished, documented, available, pending, shown, sold.
 *  format: print (archival print of digital work), physical, screen, digital. */
@Entity
data class Artwork(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val year: Int = 0,
    val medium: String = "",
    val support: String = "",
    val widthIn: Double = 0.0,
    val heightIn: Double = 0.0,
    val depthIn: Double = 0.0,
    val framed: Boolean = false,
    val seriesId: Long = 0,
    val price: Double = 0.0,
    val nfs: Boolean = false,
    val status: String = "in_progress",
    val format: String = "print",
    val location: String = "Studio",
    val editionSize: Int = 0,
    val editionUsed: Int = 0,
    val pixelW: Int = 0,
    val pixelH: Int = 0,
    val imagePath: String = "",
    val originalPath: String = "",
    val detailPath: String = "",
    val notes: String = "",
    val nextStep: String = "",
    val createdAt: Long = 0,
    /** More pictures of the same piece (detail, installed view), paths joined by "|". */
    val extraImages: String = "",
    /** Your own labels, comma separated. */
    val tags: String = ""
)

@Entity
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val targetCount: Int = 10,
    val statement100: String = "",
    val statement250: String = "",
    val notes: String = "",
    val createdAt: Long = 0
)

/** kind: make, skill, light. */
@Entity
data class Sess(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artworkId: Long = 0,
    val kind: String = "make",
    val start: Long = 0,
    val end: Long = 0,
    val note: String = "",
    val nextStep: String = "",
    val photoPath: String = ""
)

@Entity
data class Idea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String = "",
    val photoPath: String = "",
    val createdAt: Long = 0,
    val done: Boolean = false
)

/** type: statement, bio, proposal, interview, voice. variant: word count or key. */
@Entity
data class Doc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "",
    val variant: String = "",
    val seriesId: Long = 0,
    val text: String = "",
    val approved: Boolean = false,
    val revisions: Int = 0,
    val updatedAt: Long = 0,
    val parkedUntil: Long = 0,
    /** For type "custom": your name for the document, its word target, and what it is for. */
    val title: String = "",
    val target: Int = 0,
    val brief: String = ""
)

/** A true line for the CV. category: education, solo, group, award, invited, grant, residency, collection, press, publication, skill. */
@Entity
data class Fact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String = "group",
    val year: Int = 0,
    val text: String = ""
)

/** status: new, yes, no, later, applied, accepted, declined, expired. scope: campus, local, online, regional, national, international. */
@Entity
data class Opp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val organiser: String = "",
    val venue: String = "",
    val city: String = "",
    val online: Boolean = false,
    val deadline: Long = 0,
    val fee: Double = 0.0,
    val prizes: String = "",
    val eligibility: String = "",
    val scope: String = "",
    val mediums: String = "",
    val minWorks: Int = 0,
    val maxWorks: Int = 0,
    val imageSpecs: String = "",
    val statementLimit: Int = 0,
    val theme: String = "",
    val jurors: String = "",
    val exhibitionDates: String = "",
    val delivery: String = "",
    val commission: Double = 0.0,
    val url: String = "",
    val email: String = "",
    val sourceName: String = "",
    val rawText: String = "",
    val guaranteed: Boolean = false,
    val rightsGrab: Boolean = false,
    val acceptsPrints: Boolean = true,
    val acceptsNewMedia: Boolean = false,
    val themeFit: Int = 50,
    val themeReason: String = "",
    val fitScore: Int = 0,
    val fitReasons: String = "",
    val status: String = "new",
    val noReason: String = "",
    val createdAt: Long = 0,
    val dedupeKey: String = "",
    /** exhibition, residency, grant, publication, fair, commission, other. */
    val kind: String = "exhibition"
)

/** status: draft, ready, sending, submitted, accepted, declined. method: email, web. */
@Entity
data class Appl(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val oppId: Long = 0,
    val workIds: String = "",
    val statement: String = "",
    val themeText: String = "",
    val answers: String = "",
    val method: String = "web",
    val emailTo: String = "",
    val emailSubject: String = "",
    val emailBody: String = "",
    val packDir: String = "",
    val proofText: String = "",
    val proofPath: String = "",
    val status: String = "draft",
    val submittedAt: Long = 0,
    val feePaid: Double = 0.0,
    val revisions: Int = 0,
    val resultText: String = "",
    val createdAt: Long = 0
)

/** kind: campus, community, artist_run, juror, nonprofit, university, curator, commercial, subscriber.
 *  status: research, drafted, approved, sent, followed_up, interested, not_now, no, more, revisit. */
@Entity
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val role: String = "",
    val venue: String = "",
    val email: String = "",
    val website: String = "",
    val kind: String = "community",
    val tier: String = "now",
    val acceptsSubmissions: Boolean = true,
    val submissionNotes: String = "",
    val notes: String = "",
    val status: String = "research",
    val draftSubject: String = "",
    val draftBody: String = "",
    val followUpBody: String = "",
    val sentAt: Long = 0,
    val followUpAt: Long = 0,
    val revisitAt: Long = 0,
    val lastReply: String = "",
    val revisions: Int = 0,
    val createdAt: Long = 0
)

/** type: studio, pack, submit, deadline, notify, delivery, pickup, opening, task. */
@Entity
data class Evt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "task",
    val title: String = "",
    val date: Long = 0,
    val endDate: Long = 0,
    val oppId: Long = 0,
    val applId: Long = 0,
    val done: Boolean = false,
    val calendarId: Long = 0
)

/** kind: fee, sale, commission, shipping, print, framing, other. Money out is negative, money in is positive. */
@Entity
data class Ledger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = "fee",
    val amount: Double = 0.0,
    val date: Long = 0,
    val note: String = "",
    val artworkId: Long = 0,
    val oppId: Long = 0
)

/** kind: url (a listing page to read) or email (a sender whose emails are call alerts). */
@Entity
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val kind: String = "url",
    val url: String = "",
    val enabled: Boolean = true,
    val lastFetched: Long = 0,
    val lastStatus: String = ""
)

@androidx.room.Dao
interface Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Artwork): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Series): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Sess): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Idea): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Doc): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Fact): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Opp): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Appl): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Contact): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Evt): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Ledger): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(x: Source): Long

    @Delete suspend fun del(x: Artwork)
    @Delete suspend fun del(x: Series)
    @Delete suspend fun del(x: Idea)
    @Delete suspend fun del(x: Doc)
    @Delete suspend fun del(x: Fact)
    @Delete suspend fun del(x: Opp)
    @Delete suspend fun del(x: Contact)
    @Delete suspend fun del(x: Evt)
    @Delete suspend fun del(x: Ledger)
    @Delete suspend fun del(x: Source)
    @Delete suspend fun del(x: Sess)
    @Delete suspend fun del(x: Appl)

    @Query("SELECT * FROM Artwork ORDER BY createdAt DESC") fun artworks(): Flow<List<Artwork>>
    @Query("SELECT * FROM Series ORDER BY createdAt DESC") fun series(): Flow<List<Series>>
    @Query("SELECT * FROM Sess ORDER BY start DESC") fun sessions(): Flow<List<Sess>>
    @Query("SELECT * FROM Idea ORDER BY createdAt DESC") fun ideas(): Flow<List<Idea>>
    @Query("SELECT * FROM Doc ORDER BY type, variant") fun docs(): Flow<List<Doc>>
    @Query("SELECT * FROM Fact ORDER BY year DESC") fun facts(): Flow<List<Fact>>
    @Query("SELECT * FROM Opp ORDER BY deadline ASC") fun opps(): Flow<List<Opp>>
    @Query("SELECT * FROM Appl ORDER BY createdAt DESC") fun appls(): Flow<List<Appl>>
    @Query("SELECT * FROM Contact ORDER BY createdAt DESC") fun contacts(): Flow<List<Contact>>
    @Query("SELECT * FROM Evt ORDER BY date ASC") fun events(): Flow<List<Evt>>
    @Query("SELECT * FROM Ledger ORDER BY date DESC") fun ledger(): Flow<List<Ledger>>
    @Query("SELECT * FROM Source ORDER BY name") fun sources(): Flow<List<Source>>

    @Query("SELECT * FROM Artwork ORDER BY createdAt DESC") suspend fun artworksNow(): List<Artwork>
    @Query("SELECT * FROM Series ORDER BY createdAt DESC") suspend fun seriesNow(): List<Series>
    @Query("SELECT * FROM Sess ORDER BY start DESC") suspend fun sessionsNow(): List<Sess>
    @Query("SELECT * FROM Idea ORDER BY createdAt DESC") suspend fun ideasNow(): List<Idea>
    @Query("SELECT * FROM Doc ORDER BY type, variant") suspend fun docsNow(): List<Doc>
    @Query("SELECT * FROM Fact ORDER BY year DESC") suspend fun factsNow(): List<Fact>
    @Query("SELECT * FROM Opp ORDER BY deadline ASC") suspend fun oppsNow(): List<Opp>
    @Query("SELECT * FROM Appl ORDER BY createdAt DESC") suspend fun applsNow(): List<Appl>
    @Query("SELECT * FROM Contact ORDER BY createdAt DESC") suspend fun contactsNow(): List<Contact>
    @Query("SELECT * FROM Evt ORDER BY date ASC") suspend fun eventsNow(): List<Evt>
    @Query("SELECT * FROM Ledger ORDER BY date DESC") suspend fun ledgerNow(): List<Ledger>
    @Query("SELECT * FROM Source ORDER BY name") suspend fun sourcesNow(): List<Source>
}

@Database(
    entities = [Artwork::class, Series::class, Sess::class, Idea::class, Doc::class, Fact::class, Opp::class,
        Appl::class, Contact::class, Evt::class, Ledger::class, Source::class],
    version = 2,
    exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao

    companion object {
        const val NAME = "studio.db"
        fun open(c: Context): Db =
            Room.databaseBuilder(c, Db::class.java, NAME).setJournalMode(JournalMode.TRUNCATE).addMigrations(MIGRATION_1_2).build()

        /** Build 2 added columns only; nothing from build 1 is dropped or rewritten. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Artwork ADD COLUMN extraImages TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Artwork ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Doc ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Doc ADD COLUMN target INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Doc ADD COLUMN brief TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Opp ADD COLUMN kind TEXT NOT NULL DEFAULT 'exhibition'")
            }
        }
    }
}
