package com.emotionalleaves.studioagent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Db
import com.emotionalleaves.studioagent.data.Doc
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.sys.Backup
import com.emotionalleaves.studioagent.work.DAY
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.Papers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The parts no screen test reaches: pictures, papers, backup and the AI pipeline (against a stand-in AI server). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun picture(name: String, w: Int = 800, h: Int = 600): File {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(210, 190, 160))
        c.drawRect(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.8f, Paint().apply { color = Color.rgb(40, 70, 140) })
        val f = File(ImageTools.mediaDir(ctx), name)
        ImageTools.saveJpeg(b, f)
        return f
    }

    @Test fun picturesAreStraightenedCorrectedAndExported() {
        ctx
        val f = picture("src.jpg")
        assertEquals(800 to 600, ImageTools.size(f.absolutePath))
        val small = ImageTools.decode(f.absolutePath, 400)!!
        assertTrue(maxOf(small.width, small.height) <= 400)
        val full = ImageTools.decode(f.absolutePath, 2400)!!
        val straight = ImageTools.straighten(full, floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f), 2.0)
        assertEquals(480, straight.width); assertEquals(240, straight.height)
        val centre = straight.getPixel(240, 120)
        assertTrue("the crop should be the blue rectangle", Color.blue(centre) > Color.red(centre) + 40)
        val fixed = ImageTools.autoFix(full)
        assertEquals(full.width, fixed.width)
        val out = File(ImageTools.exportDir(ctx), "out.jpg")
        assertTrue(ImageTools.export(f.absolutePath, 300, 20_000, out))
        assertTrue(out.length() in 1..20_000)
        assertEquals(300, maxOf(ImageTools.size(out.absolutePath).first, ImageTools.size(out.absolutePath).second))
        assertEquals(1920 to 4_750_000L, ImageTools.parseSpecs(""))
        assertEquals(1200 to 1_900_000L, ImageTools.parseSpecs("JPEG, 1200 px on the long side, max 2 MB"))
        assertEquals(1920 to 475_000L, ImageTools.parseSpecs("1,920 pixels on the longest side, no larger than 500 KB"))
        assertEquals(3000 to 2_850_000L, ImageTools.parseSpecs("3000 pixel images under 3 megabytes"))
        assertEquals(16.0 to 20.0, com.emotionalleaves.studioagent.domain.Texts.suggestedPrintSize(4800, 6000))
        assertEquals(null, com.emotionalleaves.studioagent.domain.Texts.suggestedPrintSize(0, 0))
    }

    // The words that go into every document and email.
    @Test fun captionsOrdersAndThePortfolioPageReadRight() {
        ctx
        val s = G.settings
        s.firstName = "Jade"; s.lastName = "Leaves"; s.email = "jade@example.com"; s.city = "Austin"
        val img = picture("work.jpg")
        val works = (1..3).map { Artwork(id = it.toLong(), title = "Piece $it", year = 2026, widthIn = 16.0, heightIn = 20.0, price = 300.0, editionSize = 5, imagePath = img.absolutePath, pixelW = 4800, pixelH = 6000) }
        val facts = listOf(Fact(category = "education", year = 2020, text = "BFA, State University"), Fact(category = "group", year = 2026, text = "Winter Open, Riverside"))
        assertEquals("Piece 1, 2026, Archival pigment print, 20 \u00D7 16 in (50.8 \u00D7 40.6 cm), Edition of 5", Papers.caption(works[0]))
        assertTrue(Papers.printOrder(s, works).contains("300 ppi"))
        val csv = Papers.inventoryCsv(works.take(1).map { it.copy(title = "Say \"hi\"", tags = "blue, night") }, mapOf(0L to ""))
        assertEquals(2, csv.trim().lines().size)
        assertTrue(csv, csv.contains("JL-2026-001,\"Say \"\"hi\"\"\",2026,\"Archival pigment print\",20,16,") && csv.contains("\"blue, night\""))
        val html = Papers.portfolioHtml(s, works.map { it to "a<b>.jpg" }, "Line one\nLine <two>", "Bio", facts)
        assertTrue(html.contains("Jade Leaves") && html.contains("Line &lt;two&gt;") && !html.contains("<two>"))
        assertTrue(html.contains("<em>Piece 1</em>, 2026") && html.contains("<dt>2020</dt><dd>BFA, State University</dd>"))
        // Covering letters and emails close with the full signature, never a bare first name.
        assertTrue(Papers.signature(s).startsWith("Jade Leaves\njade@example.com"))
        assertEquals("Leaves_02_BlueHour_2026.jpg", com.emotionalleaves.studioagent.domain.Texts.fileName("Leaves", "Blue Hour", 2026, "", 0.0, 0.0, number = 2))
        assertEquals("$1,250", Papers.money(s, 1250.0))
    }

    /** Every document a gallery sees is really written, starts like a PDF, and is named Lastname_What.pdf. */
    @Test fun documentsAreWrittenAndNamedProperly() {
        ctx
        val s = G.settings
        s.firstName = "Jade"; s.lastName = "Leaves"; s.email = "jade@example.com"; s.city = "Austin"
        val img = picture("doc.jpg")
        val works = (1..9).map { Artwork(id = it.toLong(), title = "Piece $it", year = 2026, widthIn = 16.0, heightIn = 20.0, price = 1300.0, editionSize = 5, imagePath = img.absolutePath, pixelW = 4800, pixelH = 6000) }
        val facts = (1..30).map { Fact(category = if (it % 2 == 0) "group" else "education", year = 2000 + it % 26, text = "Entry number $it, a long enough line to wrap across the column on a letter page") }
        // Off a phone the pages are drawn as pictures, kept with the screenshots so the layout can be looked at.
        val docs = File("build/screens/docs").apply { mkdirs() }
        com.emotionalleaves.studioagent.media.Pdf.previewDir = docs
        val files = try {
            listOf(Papers.cv(ctx, s, facts), Papers.portfolio(ctx, s, works, "A statement about the work, long enough to wrap onto a second line so the measure can be judged properly.", 5), Papers.imageList(ctx, s, works, true),
                Papers.labels(ctx, s, works), Papers.signupSheet(ctx, s), Papers.agreement(ctx, s, "Central Library", works.take(2), "March 1 to March 29, 2027", 20.0), Papers.catalogue(ctx, s, works.take(3), mapOf(1L to listOf("Shown: Winter Open 2027, Riverside Arts, Austin"))), Papers.certificate(ctx, s, works[0], 2), Papers.billOfSale(ctx, s, works[0], "Sam Buyer", 1300.0, 1),
                Papers.proposal(ctx, s, "Riverside Gallery", "Night", "A statement.", "A bio.", works))
        } finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        assertEquals(listOf("Leaves_CV.pdf", "Leaves_Portfolio.pdf", "Leaves_Price_list.pdf", "Leaves_Wall_labels.pdf", "Leaves_Signup_sheet.pdf", "Leaves_Agreement_CentralLibrary.pdf", "Leaves_Catalogue.pdf", "Leaves_Certificate_Piece1_2.pdf", "Leaves_Bill_of_sale_Piece1.pdf", "Leaves_Proposal_RiversideGallery.pdf"), files.map { it.name })
        files.forEach { f -> assertTrue(f.name, File(docs, f.nameWithoutExtension + "_p1.png").length() > 1000) }
        assertTrue("30 CV lines need a second page", File(docs, "Leaves_CV_p2.png").exists())
        assertTrue("9 labels need a second page", File(docs, "Leaves_Wall_labels_p2.png").exists())
    }

    /**
     * The whole point of the app, end to end, with no keys of any kind: a listing page is read, a call is found,
     * an application is made, and the pack a gallery receives is built with the right names and credit.
     */
    @Test fun aCallGoesFromAListingPageToAFinishedPackWithNoKeys() = runBlocking {
        ctx
        val s = G.settings
        s.firstName = "Jade"; s.lastName = "Leaves"; s.email = "jade@example.com"; s.city = "Austin"
        s.freeAi = false; s.aiKey = ""; s.useCalendar = false; s.sourcesSeeded = true; s.seededUrls = Engine.starterSources.joinToString("\n") { it.second }
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when (request.path) {
                "/robots.txt" -> MockResponse().setBody("User-agent: *\nDisallow: /private")
                "/calls" -> MockResponse().setBody("<html><head><title>Calls</title></head><body><nav><a href='/about'>About us and our team</a></nav><ul>" +
                    "<li><a href='/call/1'>Winter Open 2027 juried exhibition</a> Deadline January 15, 2027</li>" +
                    "<li><a href='/private/2'>Members only open call for artists</a> Deadline February 1, 2027</li>" +
                    "<li><a href='/call/3'>Last year's open call for artists</a> Deadline January 5, 2026</li></ul></body></html>")
                "/call/1" -> MockResponse().setBody("<html><body><h1>Winter Open 2027</h1><p>Riverside Arts invites entries from artists in Austin. Juried by Ana Reyes.</p>" +
                    "<p>Deadline: January 15, 2027. Entry fee: $25 for up to 3 works. Email entries@riverside.example.org</p></body></html>")
                "/call/3" -> MockResponse().setBody("<html><body><h1>Last Year's Open</h1><p>Deadline: January 5, 2026.</p></body></html>")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            G.dao.put(com.emotionalleaves.studioagent.data.Source(name = "Test listing", kind = "url", url = server.url("/calls").toString()))
            assertEquals("one open call; the closed one and the one robots.txt keeps private are left out", 1, Engine.scanSources(System.currentTimeMillis()))
            val opp = G.dao.oppsNow().single { it.title == "Winter Open 2027" }
            assertEquals(25.0, opp.fee, 0.0); assertEquals("local", opp.scope); assertEquals(3, opp.maxWorks)
            assertTrue(opp.url.endsWith("/call/1")); assertEquals("entries@riverside.example.org", opp.email)
            assertTrue(G.dao.sourcesNow().single().lastStatus.startsWith("Added 1"))

            val img = picture("pack.jpg", 1200, 900)
            (1..3).forEach { G.dao.put(Artwork(title = "Piece $it", year = 2026, widthIn = 16.0, heightIn = 12.0, status = "documented", imagePath = img.absolutePath, pixelW = 1200, pixelH = 900, price = 400.0)) }
            G.dao.put(Doc(type = "statement", variant = "150", text = "I draw the kitchen at five in the morning.", approved = true))
            G.dao.put(Fact(category = "education", year = 2020, text = "BFA, State University"))
            val id = Engine.createApplication(ctx, opp)
            val a = G.dao.applsNow().single { it.id == id }
            assertEquals("Winter Open 2027: submission from Jade Leaves", a.emailSubject)
            assertTrue(a.emailBody, a.emailBody.startsWith("Dear selection committee,") && a.emailBody.contains("1. Piece 1, 2026") && a.emailBody.trim().endsWith("Austin"))
            assertEquals("email", a.method)

            com.emotionalleaves.studioagent.media.Pdf.previewDir = File(ctx.cacheDir, "pack_preview")
            val dir = try { Engine.buildPack(ctx, a, opp) } finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
            val names = dir.listFiles()!!.map { it.name }.sorted()
            assertEquals(listOf("Leaves_01_Piece1_2026.jpg", "Leaves_02_Piece2_2026.jpg",
                "Leaves_03_Piece3_2026.jpg", "Leaves_CV.pdf", "Leaves_Image_list.pdf", "Leaves_Statement.pdf"), names)
            val exif = android.media.ExifInterface(File(dir, names[0]).absolutePath)
            assertEquals("Jade Leaves", exif.getAttribute(android.media.ExifInterface.TAG_ARTIST))
            assertTrue(exif.getAttribute(android.media.ExifInterface.TAG_COPYRIGHT).orEmpty().contains("All rights reserved"))
            assertEquals("300/1", exif.getAttribute(android.media.ExifInterface.TAG_X_RESOLUTION))
            val checks = Engine.checklist(G.dao.applsNow().single { it.id == id }, opp, G.dao.artworksNow(), Engine.feeLeft(emptyList()), System.currentTimeMillis())
            assertTrue(checks.filter { !it.second }.joinToString { it.first }, checks.all { it.second })
        } finally { server.shutdown() }
    }

    /** "In plain words" without the writing help: three lines from the call's own facts. */
    @Test fun aCallInPlainWordsWithoutTheHelper() {
        ctx
        val lines = Engine.plainFacts(Opp(title = "Winter Open", kind = "exhibition", organiser = "Riverside Arts", maxWorks = 3, statementLimit = 150, commission = 30.0, rightsGrab = true))
        assertEquals(listOf("An exhibition run by Riverside Arts.", "Send up to 3 works and a statement of up to 150 words.",
            "Watch: the terms take broad rights to your images; 30% commission on sales."), lines)
        assertEquals(emptyList<String>(), Engine.plainFacts(Opp(title = "Bare")))
    }

    /** The listing reader keeps links to single calls and drops menus, categories and "see all" links. */
    @Test fun aListingPageGivesOnlyTheCalls() {
        val html = "<html><head><title>Calls</title></head><body><nav><a href='/calls/juried'>Juried exhibitions and open calls</a></nav><main>" +
            "<a href='/cat/deadline'>Deadline</a> <a href='/all'>See all upcoming deadlines</a>" +
            "<article><a href='/call/1'>Winter Juried Exhibition 2027</a><p>Deadline: January 15, 2027</p></article>" +
            "<article><a href='/call/2'>Spring Open Call for Printmakers</a><p>Entries due March 1, 2027</p></article>" +
            "<article><a href='https://facebook.com/share'>Share this call on Facebook</a></article>" +
            "</main><footer><a href='/privacy'>Privacy policy and terms of use</a></footer></body></html>"
        val doc = org.jsoup.Jsoup.parse(html, "https://listing.example.org/calls")
        val links = com.emotionalleaves.studioagent.net.Listing.links(doc).map { it.url }.sorted()
        assertEquals(listOf("https://listing.example.org/call/1", "https://listing.example.org/call/2"), links)
    }

    /** robots.txt: only the rules for every bot (or this app) count. */
    @Test fun robotsRulesAreReadForEveryBot() {
        val txt = "User-agent: Googlebot\nDisallow: /nothing-for-google\n\nUser-agent: *\nDisallow: /private\nDisallow: /members/\nAllow: /\n\nUser-agent: BadBot\nDisallow: /"
        assertEquals(listOf("/private", "/members/"), com.emotionalleaves.studioagent.net.Robots.disallowed(txt))
        assertEquals(emptyList<String>(), com.emotionalleaves.studioagent.net.Robots.disallowed(""))
        // The longest rule wins: a site closed to bots except for its calls pages can still be read there.
        val r = com.emotionalleaves.studioagent.net.Robots.rules("User-agent: *\nDisallow: /\nAllow: /calls")
        assertTrue(com.emotionalleaves.studioagent.net.Robots.allowedBy(r, "/calls/winter-open") && !com.emotionalleaves.studioagent.net.Robots.allowedBy(r, "/members"))
    }

    @Test fun existingFilesComeInAsFinishedPieces() = runBlocking {
        ctx
        val f = picture("blue_hour-final.jpg", 3000, 2400)
        val id = Engine.importFile(ctx, Uri.fromFile(f), "blue_hour-final.jpg")
        val w = G.dao.artworksNow().single { it.id == id }
        assertEquals("Blue hour final", w.title); assertEquals("documented", w.status)
        assertEquals(3000, w.pixelW); assertEquals(10.0, w.widthIn, 0.0); assertEquals(8.0, w.heightIn, 0.0)
        assertEquals(10, w.editionSize); assertTrue(w.price > 0)
        assertTrue(File(w.imagePath).exists() && File(w.originalPath).exists())
        val bad = File(ctx.cacheDir, "notes.txt").apply { writeText("not a picture") }
        val failed = try { Engine.importFile(ctx, Uri.fromFile(bad), "notes.txt"); false } catch (e: Exception) { true }
        assertTrue(failed); assertEquals(1, G.dao.artworksNow().size)
        val callId = Engine.ingestPlain("Staff Art Show\nDeadline: " + java.time.LocalDate.now().plusDays(20) + ". No entry fee. Open to campus staff.", "Pasted")
        val call = G.dao.oppsNow().single { it.id == callId }
        assertEquals("Staff Art Show", call.title); assertEquals("campus", call.scope); assertTrue(call.deadline > System.currentTimeMillis())
    }

    @Test fun aBackupBringsEverythingBack() = runBlocking {
        ctx
        G.settings.firstName = "Jade"; G.settings.aiKey = "secret-key"
        val img = picture("keep.jpg")
        G.dao.put(Artwork(title = "Blue Hour", imagePath = img.absolutePath))
        val zip = File(ctx.cacheDir, "backup.zip")
        Backup.export(ctx, G.settings, Uri.fromFile(zip))
        assertTrue(zip.length() > 1000)
        assertTrue("keys must never be in a backup", !zip.readBytes().toString(Charsets.ISO_8859_1).contains("secret-key"))

        G.dao.artworksNow().forEach { G.dao.del(it) }
        img.delete(); G.settings.firstName = "Someone else"
        G.db.close()
        Backup.restore(ctx, G.settings, Uri.fromFile(zip))
        G.db = Db.open(ctx)
        assertEquals("Blue Hour", G.dao.artworksNow().single().title)
        assertTrue(img.exists())
        assertEquals("Jade", G.settings.firstName)
    }

    private fun reply(text: String) = MockResponse().setBody(
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", text))))
            .put("usage", JSONObject().put("prompt_tokens", 100).put("completion_tokens", 50)).toString()
    )

    @Test fun aSharedCallIsReadCheckedAndRanked() = runBlocking {
        ctx
        val server = MockWebServer(); server.start()
        try {
            val s = G.settings
            s.aiProvider = "custom"; s.aiBaseUrl = server.url("/v1").toString(); s.aiKey = "test"; s.firstName = "Jade"
            G.dao.put(Doc(type = "statement", variant = "150", text = "I draw the kitchen at five in the morning.", approved = true))
            val deadline = java.time.LocalDate.now().plusDays(30)
            server.enqueue(reply("Here you go:\n```json\n[{\"title\":\"Night Work\",\"kind\":\"exhibition\",\"organiser\":\"Riverside Arts\",\"venue\":\"Riverside\",\"city\":\"Austin\",\"online\":false," +
                "\"deadline\":\"$deadline\",\"fee\":0,\"scope\":\"local\",\"theme\":\"work made after dark\",\"jurors\":\"A. Juror\",\"max_works\":3,\"statement_word_limit\":100," +
                "\"accepts_prints\":true,\"url\":\"https://example.org/call\"}," +
                "{\"title\":\"Pay To Hang\",\"kind\":\"fair\",\"organiser\":\"Vanity Co\",\"fee\":150,\"scope\":\"international\",\"guaranteed_acceptance\":true,\"deadline\":\"$deadline\"}]\n```"))
            server.enqueue(reply("{\"fit\": 88, \"reason\": \"night kitchen drawings match\"}"))
            assertEquals(2, Engine.ingest("pretend page text", "Shared"))
            val sent = server.takeRequest()
            assertTrue(sent.path!!.endsWith("/chat/completions"))
            assertEquals("Bearer test", sent.getHeader("Authorization"))
            assertTrue("fetched text must be fenced off as data", sent.body.readUtf8().contains("<data>"))

            val opps = G.dao.oppsNow()
            val good = opps.first { it.title == "Night Work" }
            assertEquals(88, good.themeFit); assertEquals(3, good.maxWorks); assertEquals(100, good.statementLimit)
            assertTrue(good.deadline > System.currentTimeMillis() + 28 * DAY)
            val vanity = opps.first { it.title == "Pay To Hang" }
            assertEquals("fair", vanity.kind)
            val p = Engine.profile()
            val list = Engine.shortlist(opps, p, System.currentTimeMillis(), true)
            assertEquals(listOf("Night Work"), list.map { it.title })
            assertTrue(good.fitScore > vanity.fitScore)
        } finally { server.shutdown() }
    }

    @Test fun writingComesBackTrimmedAndSpendIsCounted() = runBlocking {
        ctx
        val server = MockWebServer(); server.start()
        try {
            val s = G.settings
            s.aiProvider = "custom"; s.aiBaseUrl = server.url("/v1/").toString(); s.aiKey = "test"; s.writingNotes = "mention the kitchen"
            server.enqueue(reply("  I draw the kitchen.  "))
            assertEquals("I draw the kitchen.", G.ai.write("an artist statement", 50, Engine.factsText(), ""))
            assertTrue(server.takeRequest().body.readUtf8().contains("mention the kitchen"))
            assertTrue(G.ai.spentThisMonth() > 0)
            server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":{\"message\":\"bad key\"}}"))
            val error = try { G.ai.write("x", 10, "", ""); "" } catch (e: Exception) { e.message.orEmpty() }
            assertTrue(error.contains("401") && error.contains("bad key"))
            server.enqueue(reply("accepted"))
            assertEquals("accepted", Engine.classify("Congratulations"))
            s.aiKey = ""
            assertEquals("declined", Engine.classify("We regret to inform you"))
        } finally { server.shutdown() }
    }

    @Test fun anAcceptanceEmailDatesTheStepsWithoutAnyHelp() {
        ctx
        val today = java.time.LocalDate.of(2027, 1, 20)
        val mail = "Congratulations! Please drop off your work on February 10, 2027 between 10 and 4. The opening reception is Friday, February 19, 2027 at 6pm. Unsold work can be picked up March 30, 2027."
        val steps = com.emotionalleaves.studioagent.work.Engine.fallbackLogistics(mail, today).toMap()
        fun day(t: String) = java.time.Instant.ofEpochMilli(steps.getValue(t)).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        assertEquals("2027-02-10", day("Deliver or ship the work"))
        assertEquals("2027-02-03", day("Frame or prepare hanging hardware"))
        assertEquals("2027-02-19", day("Go to the opening"))
        assertEquals("2027-03-30", day("Pick up unsold work"))
        assertEquals("2027-01-22", day("Confirm you are taking part"))
    }


    @Test fun aCallAtYourOwnSchoolIsKeptAsCampus() {
        ctx
        G.settings.school = "Riverside University"
        val page = "Link: https://x.org/c\nStaff and Student Art Show\nOpen to Riverside University staff and students. Deadline: December 1, 2099."
        val o = com.emotionalleaves.studioagent.work.Engine.plainCall(page, "ArtCall", "Staff and Student Art Show", "https://x.org/c", System.currentTimeMillis())
        assertEquals("campus", o?.scope)
        G.settings.school = ""
        val other = com.emotionalleaves.studioagent.work.Engine.plainCall(page, "ArtCall", "Staff and Student Art Show", "https://x.org/c", System.currentTimeMillis())
        assertEquals("", other?.scope)
    }


    @Test fun placesFromTheMapBecomeVenuesNearestFirst() {
        ctx
        val json = """{"elements":[
          {"type":"node","id":1,"lat":30.27,"lon":-97.74,"tags":{"amenity":"library","name":"Central Library","addr:street":"Cesar Chavez St","addr:housenumber":"710","addr:city":"Austin","website":"library.austintexas.gov"}},
          {"type":"way","id":2,"center":{"lat":30.30,"lon":-97.74},"tags":{"tourism":"gallery","name":"Big Medium","email":"info@bigmedium.org"}},
          {"type":"node","id":3,"lat":30.27,"lon":-97.74,"tags":{"shop":"art"}},
          {"type":"node","id":4,"lat":30.2701,"lon":-97.7401,"tags":{"amenity":"arts_centre","name":"Dougherty Arts Center"}}
        ]}"""
        val list = com.emotionalleaves.studioagent.net.Places.parse(json, 30.27, -97.74)
        // Within a few miles, places that show art come before the library.
        assertEquals(listOf("Dougherty Arts Center", "Big Medium", "Central Library"), list.map { it.name })
        val c = com.emotionalleaves.studioagent.ui.placeToContact(list[2], 1L)
        assertEquals("community", c.kind)
        assertEquals("https://library.austintexas.gov", c.website)
        assertTrue(c.notes.contains("710 Cesar Chavez St, Austin"))
        assertEquals("info@bigmedium.org", com.emotionalleaves.studioagent.ui.placeToContact(list[1], 1L).email)
    }


    @Test fun aStudioUpdateTellsOnlyWhatIsTrue() {
        ctx
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"
        val w = com.emotionalleaves.studioagent.data.Artwork(id = 5, title = "Blue Hour", year = 2026, medium = "Archival pigment print", widthIn = 16.0, heightIn = 20.0)
        val show = com.emotionalleaves.studioagent.data.Opp(title = "Winter Open 2027", venue = "Riverside Arts", city = "Austin", exhibitionDates = "February 19 to March 28")
        val (subject, body) = com.emotionalleaves.studioagent.ui.studioUpdate(listOf(w), listOf(show))
        assertEquals("Coming up: Winter Open 2027", subject)
        assertTrue(body.contains("Winter Open 2027, Riverside Arts, Austin. February 19 to March 28."))
        assertTrue(body.contains("Blue Hour"))
        assertTrue(body.contains("To stop these notes"))
        assertEquals("News from the studio", com.emotionalleaves.studioagent.ui.studioUpdate(emptyList(), emptyList()).first)
    }


    @Test fun aDarkFlatPhotoIsFlaggedAndAGoodOneIsNot() {
        ctx
        val dir = java.io.File(ctx.cacheDir, "q").apply { mkdirs() }
        fun save(name: String, draw: (android.graphics.Canvas) -> Unit): String {
            val b = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
            draw(android.graphics.Canvas(b))
            val f = java.io.File(dir, name); f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; return f.absolutePath
        }
        val dark = save("dark.png") { it.drawColor(0xFF141414.toInt()) }
        val busy = save("busy.png") { c -> val p = android.graphics.Paint()
            for (x in 0 until 400 step 4) for (y in 0 until 300 step 4) { p.color = if ((x / 4 + y / 4) % 2 == 0) 0xFF202020.toInt() else 0xFFC8C8C8.toInt(); c.drawRect(x.toFloat(), y.toFloat(), x + 4f, y + 4f, p) } }
        val q = com.emotionalleaves.studioagent.media.ImageTools.quality(dark)
        assertTrue(q.any { it.startsWith("It looks dark") })
        assertTrue(q.any { it.contains("blurry") })
        assertTrue(com.emotionalleaves.studioagent.media.ImageTools.quality(busy).isEmpty())
    }


    @Test fun theMaterialsKitHoldsEverythingAGalleryAsksFor() = kotlinx.coroutines.runBlocking {
        ctx
        val s = G.settings; s.firstName = "Jade"; s.lastName = "Leaves"; s.email = "jade@example.com"
        val img = picture("kit.jpg", 1600, 1200).absolutePath
        G.dao.put(com.emotionalleaves.studioagent.data.Artwork(title = "Blue Hour", year = 2026, medium = "Archival pigment print", widthIn = 16.0, heightIn = 12.0, imagePath = img, status = "documented", createdAt = 1))
        G.dao.put(com.emotionalleaves.studioagent.data.Doc(type = "statement", text = "I photograph kitchens at dawn, before the first shift.", approved = true))
        com.emotionalleaves.studioagent.media.Pdf.previewDir = File(ctx.cacheDir, "kit_preview")
        val zip = try { Engine.pressKit(ctx) } finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        val names = java.util.zip.ZipFile(zip).use { z -> z.entries().toList().map { it.name } }
        assertTrue(names.toString(), names.any { it.startsWith("Leaves_01_Blue") && it.endsWith(".jpg") })
        assertTrue(names.toString(), names.contains("Leaves_Portfolio.pdf"))
        assertTrue(names.toString(), names.contains("Leaves_Statement_and_bio.pdf"))
        assertTrue(names.toString(), names.any { it.contains("Image_list") })
    }


    @Test fun aVenuesYesBecomesADatedShowPlan() = kotlinx.coroutines.runBlocking {
        ctx
        G.settings.city = "Austin"
        val cid = G.dao.put(com.emotionalleaves.studioagent.data.Contact(venue = "Central Library", kind = "community", email = "art@library.example.org"))
        val c = G.dao.contactsNow().first { it.id == cid }
        val opening = java.time.LocalDate.now().plusWeeks(8)
        val applId = Engine.planOwnShow(c, "Before the First Shift", opening, 4, emptyList())
        val tasks = G.dao.eventsNow().filter { it.applId == applId && it.type == "task" }
        assertEquals(14, tasks.size)
        assertEquals("accepted", G.dao.applsNow().first { it.id == applId }.status)
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"; G.settings.email = "jade@example.com"
        val news = Engine.announcement(G.dao.applsNow().first { it.id == applId })
        assertTrue(news, news.startsWith("Before the First Shift: new work by Jade Leaves at Central Library"))
        assertTrue(news.contains("Free and open to everyone.") && news.contains("jade@example.com"))
        assertEquals(opening.minusDays(42), Engine.date(tasks.minOf { it.date }))
        assertTrue(G.dao.factsNow().none { it.text.contains("Before the First Shift") })
        Engine.openingHappened(applId)
        assertTrue(G.dao.factsNow().any { it.category == "solo" && it.text == "Before the First Shift, Central Library, Austin" })
        assertEquals("interested", G.dao.contactsNow().first { it.id == cid }.status)
    }


    @Test fun aPieceIsDrawnOnAWallAtItsSize() {
        ctx
        val img = picture("wall.jpg", 1200, 900).absolutePath
        val small = com.emotionalleaves.studioagent.media.Mockup.render(img, 8.0, 6.0, true, File(ctx.cacheDir, "w8.jpg"))!!
        val big = com.emotionalleaves.studioagent.media.Mockup.render(img, 40.0, 30.0, true, File(ctx.cacheDir, "w40.jpg"))!!
        assertTrue(small.length() > 1000 && big.length() > 1000)
        // The bigger print spans much more of the wall: measure between the dark frame edges across the middle.
        fun span(wIn: Double, hIn: Double): Int { val bm = com.emotionalleaves.studioagent.media.Mockup.draw(img, wIn, hIn, true)!!
            // Above the bench, the widest run between dark frame edges in any row.
            return (0 until (bm.height * 0.6).toInt() step 4).maxOf { y ->
                val dark = (0 until bm.width).filter { x -> android.graphics.Color.red(bm.getPixel(x, y)) < 80 }
                if (dark.isEmpty()) 0 else dark.last() - dark.first() } }
        assertTrue("${span(8.0, 6.0)} vs ${span(40.0, 30.0)}", span(40.0, 30.0) > span(8.0, 6.0) * 3)
        assertEquals(null, com.emotionalleaves.studioagent.media.Mockup.render(img, 0.0, 0.0, true, File(ctx.cacheDir, "w0.jpg")))
        java.io.File(ctx.cacheDir, "w40.jpg").copyTo(java.io.File("build/screens/docs/on_the_wall.jpg").apply { parentFile?.mkdirs() }, overwrite = true)
    }


    @Test fun aVenuesOwnSiteIsReadForExhibitionPages() = kotlinx.coroutines.runBlocking {
        ctx
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when (request.path) {
                "/" -> MockResponse().setBody("<html><body><a href='/hours'>Hours and parking</a> <a href='/exhibits'>Exhibit space for local artists</a> <a href='https://elsewhere.org/call'>Call for artists elsewhere</a></body></html>")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val id = G.dao.put(com.emotionalleaves.studioagent.data.Contact(venue = "Central Library", website = server.url("/").toString(), status = "research"))
            assertEquals(1, Engine.researchVenues())
            val c = G.dao.contactsNow().first { it.id == id }
            assertTrue(c.submissionNotes, c.submissionNotes.contains("Exhibit space for local artists"))
            assertTrue(!c.submissionNotes.contains("Hours") && !c.submissionNotes.contains("elsewhere"))
            assertEquals("each venue is read once", 0, Engine.researchVenues())
        } finally { server.shutdown() }
    }


    @Test fun theSnapshotForQuestionsNamesPiecesAndCalls() = kotlinx.coroutines.runBlocking {
        ctx
        G.dao.put(Artwork(title = "Walk-in Cooler", medium = "gouache", widthIn = 9.0, heightIn = 12.0, status = "documented"))
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Spring Small Works", deadline = System.currentTimeMillis() + 9 * 86_400_000L, fitScore = 80))
        val s = com.emotionalleaves.studioagent.work.Engine.snapshot()
        assertTrue(s.contains("Walk-in Cooler (gouache, 9x12 in), ready to send"))
        assertTrue(s.contains("Spring Small Works, closes"))
        assertTrue(s.contains("Today's step in the app:"))
        assertTrue(!s.contains("@"))
    }

    @Test fun afterANoTheSamePiecesGoToTheNextCall() = kotlinx.coroutines.runBlocking {
        ctx
        val day = 86_400_000L; val now = System.currentTimeMillis()
        val a = G.dao.put(Artwork(title = "First", status = "documented", imagePath = "/x/1.jpg")); val b = G.dao.put(Artwork(title = "Second", status = "documented", imagePath = "/x/2.jpg")); G.dao.put(Artwork(title = "Third", status = "documented", imagePath = "/x/3.jpg"))
        val old = G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Old", deadline = now - day))
        val no = com.emotionalleaves.studioagent.data.Appl(id = G.dao.put(com.emotionalleaves.studioagent.data.Appl(oppId = old, workIds = "$b,$a", status = "declined")), oppId = old, workIds = "$b,$a", status = "declined")
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Too soon", deadline = now + day, fitScore = 99))
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Good fit", deadline = now + 20 * day, fitScore = 70))
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Weak fit", deadline = now + 20 * day, fitScore = 20))
        val next = com.emotionalleaves.studioagent.work.Engine.nextCallFor(no)!!
        assertEquals("Good fit", next.title)
        val id = com.emotionalleaves.studioagent.work.Engine.createApplication(ctx, next, listOf(b, a))
        assertTrue(G.dao.applsNow().single { it.id == id }.workIds.startsWith("$b,$a"))
    }

    @Test fun aPostShowsTheWholePieceWithWords() {
        ctx
        val art = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(200, 20, 20)) }
        val b = com.emotionalleaves.studioagent.media.Post.draw(art, "Jade Leaves")
        assertEquals(1080, b.width); assertEquals(1350, b.height)
        // A wide piece is shown whole: red reaches near both sides, the corners stay the quiet field.
        assertTrue(android.graphics.Color.red(b.getPixel(100, 675)) > 150 && android.graphics.Color.green(b.getPixel(100, 675)) < 80)
        assertTrue(android.graphics.Color.green(b.getPixel(10, 10)) > 200)
        val words = com.emotionalleaves.studioagent.media.Post.caption("Walk-in Cooler", 2026, "Gouache on paper", "9 × 12 in", "Painted at six in the morning before the breakfast rush. More later.", "Austin, TX", available = true)
        assertTrue(words.startsWith("Walk-in Cooler (2026)"))
        assertTrue(words.contains("Painted at six in the morning before the breakfast rush."))
        assertTrue(words.contains("#gouache") && words.contains("#austinartist"))
        assertTrue(com.emotionalleaves.studioagent.media.Post.caption("Study", 2026, "oil", "", "", "", available = false, inProgress = true).startsWith("In progress: Study"))
    }

    @Test fun freshEyesViews() {
        ctx
        // Left half dark blue, right half light yellow.
        val b = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until 200) for (y in 0 until 100) b.setPixel(x, y, if (x < 100) android.graphics.Color.rgb(20, 30, 120) else android.graphics.Color.rgb(250, 240, 150))
        val L = com.emotionalleaves.studioagent.media.Look
        assertTrue("flip", L.lum(L.flip(b).getPixel(10, 50)) > 200)
        val g = L.gray(b).getPixel(150, 50); assertEquals(android.graphics.Color.red(g), android.graphics.Color.blue(g))
        val two = L.values(b, 2); assertTrue("two values " + L.lum(two.getPixel(10, 50)) + " " + L.lum(two.getPixel(190, 50)), L.lum(two.getPixel(10, 50)) < 60 && L.lum(two.getPixel(190, 50)) > 200)
        val sq = L.squint(b); assertEquals(200, sq.width); assertTrue("squint", L.lum(sq.getPixel(5, 50)) < 80)
        val pal = L.palette(b)
        assertTrue("palette share " + pal, pal.take(2).sumOf { it.second } > 0.8)
        val names = pal.take(2).map { L.name(it.first) }
        assertTrue("names $names", names.any { it.contains("blue") } && names.any { it.contains("yellow") })
        val notes = L.valueNotes(b)
        assertTrue("notes $notes", notes.first().startsWith("A full range") || notes.first().startsWith("A fair range"))
        val flat = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(120, 120, 120)) }
        assertTrue("flat", L.valueNotes(flat).first().startsWith("The values sit close together"))
        val qs = L.questions(L.valueNotes(flat))
        assertEquals(3, qs.size); assertTrue(qs.first().startsWith("Where is the darkest dark")); assertTrue(qs.all { it.endsWith("?") })
    }

    @Test fun missingPiecesGetASaturdayEach() = kotlinx.coroutines.runBlocking {
        ctx
        val now = System.currentTimeMillis(); val day = 86_400_000L
        G.dao.put(Artwork(title = "Ready one", status = "documented", imagePath = "/x/r.jpg"))
        G.dao.put(Artwork(title = "On the easel", status = "in_progress", createdAt = now))
        G.dao.put(Artwork(title = "Just an idea", status = "idea", createdAt = now - day))
        val oid = G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Needs three", minWorks = 3, deadline = now + 40 * day))
        val o = G.dao.oppsNow().single { it.id == oid }
        val aid = G.dao.put(com.emotionalleaves.studioagent.data.Appl(oppId = oid))
        val a = G.dao.applsNow().single { it.id == aid }
        val E = com.emotionalleaves.studioagent.work.Engine
        assertEquals(2, E.piecesShort(a, o, G.dao.artworksNow()))
        val said = E.planMaking(a, o)
        assertTrue(said, said.startsWith("Planned: On the easel by Sat") && said.contains("Just an idea by Sat"))
        val tasks = G.dao.eventsNow().filter { it.applId == aid && it.title.startsWith("Finish ") }
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { E.date(it.date).dayOfWeek == java.time.DayOfWeek.SATURDAY && it.date < o.deadline - 3 * day })
        // Asking again plans nothing twice.
        assertTrue(E.planMaking(a, o).startsWith("Already planned"))
    }

    @Test fun aCommissionStartToFinish() = kotlinx.coroutines.runBlocking {
        ctx
        val E = com.emotionalleaves.studioagent.work.Engine
        G.settings.pricePerSqIn = 2.0
        assertEquals(200.0, E.commissionPrice(8.0, 10.0), 0.01)
        val due = java.time.LocalDate.now().plusWeeks(6)
        val id = E.startCommission("Sam Rivera", "sam@example.com", "", "The diner on Fifth at night", 8.0, 10.0, 240.0, due)
        val w = G.dao.artworksNow().single { it.id == id }
        assertEquals("For Sam Rivera", w.title); assertEquals("idea", w.status); assertTrue(w.clientId > 0)
        assertEquals("client", G.dao.contactsNow().single { it.id == w.clientId }.kind)
        val steps = G.dao.eventsNow().filter { it.artworkId == id }.sortedBy { it.date }
        assertEquals(listOf("Send the quote", "Deposit received", "Show them a draft", "Finish the piece", "Final payment received", "Hand it over"), steps.map { it.title })
        val (subj, body) = E.commissionQuote(w, "Sam")
        assertTrue(subj.contains("quote") && body.contains("Deposit: ") && body.contains("The diner on Fifth") && body.contains("copyright"))
        E.commissionPaid(w, deposit = true)
        E.commissionPaid(w, deposit = false)
        val paid = G.dao.ledgerNow().filter { it.artworkId == id }
        assertEquals(240.0, paid.sumOf { it.amount }, 0.01); assertEquals(2, paid.size)
        E.commissionDelivered(G.dao.artworksNow().single { it.id == id })
        assertEquals("sold", G.dao.artworksNow().single { it.id == id }.status)
        assertTrue(G.dao.eventsNow().filter { it.artworkId == id }.all { it.done })
        assertTrue(G.dao.factsNow().any { it.text == "Private collection" })
    }

    @Test fun aSaleAtTheOpening() = kotlinx.coroutines.runBlocking {
        ctx
        val E = com.emotionalleaves.studioagent.work.Engine; val now = System.currentTimeMillis()
        val wid = G.dao.put(Artwork(title = "Dish Pit", status = "shown", imagePath = "/x/d.jpg", price = 300.0))
        val oid = G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Small Works", venue = "Gallery 4", commission = 30.0, deadline = now - 30 * 86_400_000L))
        val aid = G.dao.put(com.emotionalleaves.studioagent.data.Appl(oppId = oid, workIds = "$wid", status = "accepted", submittedAt = now - 40 * 86_400_000L))
        val pickup = now + 20 * 86_400_000L
        G.dao.put(com.emotionalleaves.studioagent.data.Evt(type = "task", title = "Pick up the work", date = pickup, applId = aid, oppId = oid))
        val keep = E.sellAtShow(G.dao.artworksNow().single { it.id == wid }, 300.0, "Ana", "ana@example.com", true)
        assertEquals(210.0, keep, 0.01)
        val w = G.dao.artworksNow().single { it.id == wid }
        assertEquals("sold", w.status); assertTrue(w.location.contains("Gallery 4"))
        assertEquals(90.0, -G.dao.ledgerNow().single { it.kind == "commission" }.amount, 0.01)
        assertTrue(G.dao.contactsNow().any { it.email == "ana@example.com" && it.kind == "subscriber" })
        val get = G.dao.eventsNow().single { it.title == "Get Dish Pit to Ana" }
        assertEquals(pickup, get.date); assertEquals(wid, get.artworkId)
        // A buyer thanked tomorrow, and news of new work in six months.
        assertTrue(G.dao.eventsNow().any { it.title == "Thank Ana for buying Dish Pit" })
        assertTrue(G.dao.eventsNow().any { it.title == "Send Ana news of new work" && it.date > now + 150 * 86_400_000L })
    }

    @Test fun backLabelsAreMade() {
        ctx
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"; G.settings.email = "jade@example.com"
        val docs = File("build/screens/docs").apply { mkdirs() }
        com.emotionalleaves.studioagent.media.Pdf.previewDir = docs
        val f = try { com.emotionalleaves.studioagent.work.Papers.backLabels(ctx, G.settings, (1..3).map { Artwork(id = it.toLong(), title = "Dish Pit $it", year = 2026, medium = "gouache", widthIn = 9.0, heightIn = 12.0) }) }
            finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        assertEquals("Leaves_Back_labels.pdf", f.name)
        assertTrue(File(docs, "Leaves_Back_labels_p1.png").length() > 1000)
    }

    @Test fun closingAMarketDayRecordsIt() = kotlinx.coroutines.runBlocking {
        ctx
        val M = com.emotionalleaves.studioagent.domain.Market
        val wid = G.dao.put(Artwork(title = "Little Cooler", status = "documented", price = 120.0))
        G.settings.marketItems = M.itemsText(M.starter); G.settings.marketFee = 40.0
        G.settings.marketTally = M.bump(M.bump(M.bump("", "m:Cards", 1), "m:Cards", 1), "w:$wid", 1)
        val total = com.emotionalleaves.studioagent.work.Engine.closeMarket("Eastside Market")
        assertEquals(130.0, total, 0.001)
        val l = G.dao.ledgerNow()
        assertTrue(l.any { it.note == "2 x Cards, at Eastside Market" && it.amount == 10.0 })
        assertTrue(l.any { it.kind == "fee" && it.amount == -40.0 })
        assertEquals("sold", G.dao.artworksNow().single { it.id == wid }.status)
        assertEquals(18, M.items(G.settings.marketItems).single { it.name == "Cards" }.qty)
        assertEquals("", G.settings.marketTally)
    }

    @Test fun aStudioVisitIsPlanned() = kotlinx.coroutines.runBlocking {
        ctx
        val cid = G.dao.put(com.emotionalleaves.studioagent.data.Contact(venue = "Gallery 4", status = "interested"))
        val c = G.dao.contactsNow().single { it.id == cid }
        val day = java.time.LocalDate.now().plusDays(10)
        assertEquals(6, com.emotionalleaves.studioagent.work.Engine.planVisit(c, day))
        assertEquals(0, com.emotionalleaves.studioagent.work.Engine.planVisit(c, day))
        val visit = G.dao.eventsNow().single { it.title == "The visit (Gallery 4)" }
        assertEquals(day, com.emotionalleaves.studioagent.work.Engine.date(visit.date))
        assertTrue(G.dao.contactsNow().single { it.id == cid }.notes.contains("Studio visit planned"))
    }

    @Test fun aProcessPostShowsEachStage() {
        ctx
        val colors = listOf(android.graphics.Color.RED, android.graphics.Color.GREEN, android.graphics.Color.BLUE, android.graphics.Color.BLACK)
        val arts = colors.map { c -> android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(c) } }
        val b = com.emotionalleaves.studioagent.media.Post.process(arts, "Jade Leaves")
        assertEquals(1080, b.width)
        // Two across: the first photo top left, the last bottom right.
        val first = b.getPixel(300, 400); val last = b.getPixel(780, 900)
        assertTrue(android.graphics.Color.red(first) > 200 && android.graphics.Color.green(first) < 50)
        assertTrue(android.graphics.Color.red(last) < 40 && android.graphics.Color.green(last) < 40 && android.graphics.Color.blue(last) < 40)
    }

    @Test fun aYearInOnePicture() {
        ctx
        val arts = (1..3).map { android.graphics.Bitmap.createBitmap(120, 160, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(30, 60, 160)) } }
        val b = com.emotionalleaves.studioagent.media.Post.year(arts, "My 2026 in art", listOf("40 hours of making", "6 pieces with pictures"), "Jade Leaves")
        assertEquals(1350, b.height)
        // Pictures in the top part, the quiet field under the words.
        assertTrue((0 until 810 step 10).any { y -> android.graphics.Color.blue(b.getPixel(300, y)) > 140 && android.graphics.Color.red(b.getPixel(300, y)) < 60 })
        assertEquals(0xFFF4F3EF.toInt(), b.getPixel(1070, 1340))
    }

    @Test fun museumPiecesAreReadPublicDomainOnly() {
        val M = com.emotionalleaves.studioagent.net.Museum
        val json = """{"data":[{"id":1,"title":"Study of Trees","artist_title":"Winslow Homer","date_display":"1890","medium_display":"Watercolor on paper","image_id":"abc","is_public_domain":true},
            {"id":2,"title":"Recent","artist_title":"Someone","image_id":"def","is_public_domain":false},{"id":3,"title":"No picture","image_id":null,"is_public_domain":true}]}"""
        val p = M.parse(json)
        assertEquals(listOf(1L), p.map { it.id })
        assertEquals("https://www.artic.edu/iiif/2/abc/full/843,/0/default.jpg", p[0].imageUrl)
        assertEquals("Winslow Homer, 1890. Art Institute of Chicago, public domain.", p[0].credit)
        assertEquals("gouache", M.termFor("Gouache, pencil")); assertEquals("drawing", M.termFor("digital, archival print")); assertEquals("oil sketch", M.termFor("acrylic"))
    }

    @Test fun everyToolOpensARealScreen() {
        val nav = File("src/main/java/com/emotionalleaves/studioagent/ui/MainActivity.kt").readText()
        com.emotionalleaves.studioagent.ui.toolGroups.flatMap { it.second }.forEach { (name, _, route) -> assertTrue("$name -> $route", nav.contains("composable(\"$route\")") || nav.contains("\"$route\" ->") || nav.contains("route = \"$route\"")) }
    }

    @Test fun autopilotPreparesOneStrongFreeEntry() = kotlinx.coroutines.runBlocking {
        ctx
        val now = System.currentTimeMillis(); val day = 86_400_000L
        G.dao.put(Artwork(title = "Ready", status = "documented", imagePath = "/x/r.jpg", widthIn = 9.0, heightIn = 12.0, year = 2026))
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Strong Free", scope = "local", rawText = "Free to enter. No entry fee.", fitScore = 85, deadline = now + 20 * day, status = "new"))
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Strong Paid", scope = "local", fee = 25.0, rawText = "Entry fee $25.", fitScore = 95, deadline = now + 20 * day, status = "new"))
        G.dao.put(com.emotionalleaves.studioagent.data.Opp(title = "Too Soon", scope = "local", rawText = "Free to enter.", fitScore = 99, deadline = now + 5 * day, status = "new"))
        val E = com.emotionalleaves.studioagent.work.Engine
        G.settings.lastAutopilot = 0
        val id = E.autopilot(ctx, now)
        assertTrue(id > 0)
        assertEquals("Strong Free", G.dao.oppsNow().single { o -> o.id == G.dao.applsNow().single { it.id == id }.oppId }.title)
        assertEquals("draft", G.dao.applsNow().single { it.id == id }.status)
        // Once a week at most, and never when switched off.
        assertEquals(0L, E.autopilot(ctx, now + day))
        G.settings.autopilot = false; assertEquals(0L, E.autopilot(ctx, now + 8 * day))
    }

    @Test fun priceTagsForAMarket() {
        ctx
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"
        val docs = File("build/screens/docs").apply { mkdirs() }
        com.emotionalleaves.studioagent.media.Pdf.previewDir = docs
        val f = try { com.emotionalleaves.studioagent.work.Papers.priceTags(ctx, G.settings, listOf("Cards" to 5.0, "Small prints (5 x 7)" to 15.0, "Walk-in Cooler" to 240.0)) } finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        assertEquals("Leaves_Price_tags.pdf", f.name)
        assertTrue(File(docs, "Leaves_Price_tags_p1.png").length() > 1000)
    }

    @Test fun slidesForATalk() {
        ctx
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"; G.settings.email = "jade@example.com"
        val img = picture("talk.jpg")
        val docs = File("build/screens/docs").apply { mkdirs() }
        com.emotionalleaves.studioagent.media.Pdf.previewDir = docs
        val f = try { com.emotionalleaves.studioagent.work.Papers.talkSlides(ctx, G.settings, "Night Kitchen", (1..3).map { Artwork(id = it.toLong(), title = "Piece $it", year = 2026, medium = "gouache", widthIn = 9.0, heightIn = 12.0, imagePath = img.absolutePath) }, "I draw the kitchen at five in the morning. It is quiet then.") }
            finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        assertEquals("Leaves_Talk_slides.pdf", f.name)
        // A title page, three pieces, and a thank-you page.
        assertTrue(File(docs, "Leaves_Talk_slides_p5.png").exists()); assertTrue(!File(docs, "Leaves_Talk_slides_p6.png").exists())
    }

    @Test fun aValueListForInsurance() {
        ctx
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"
        val docs = File("build/screens/docs").apply { mkdirs() }
        com.emotionalleaves.studioagent.media.Pdf.previewDir = docs
        val f = try { com.emotionalleaves.studioagent.work.Papers.valueList(ctx, G.settings, listOf(Artwork(id = 1, title = "A", price = 300.0, status = "documented", year = 2026, widthIn = 9.0, heightIn = 12.0), Artwork(id = 2, title = "B", price = 200.0, status = "sold"))) }
            finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        assertEquals("Leaves_Works_and_value.pdf", f.name)
        assertTrue(File(docs, "Leaves_Works_and_value_p1.png").length() > 1000)
    }

    @Test fun anOnlineShowInOneFile() {
        ctx
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"; G.settings.email = "jade@example.com"
        val f = com.emotionalleaves.studioagent.work.Papers.onlineShow(ctx, G.settings, "Night Kitchen", "I draw the kitchen at five.\n\nIt is quiet then.", listOf(Artwork(id = 1, title = "Walk-in <Cooler>", price = 240.0, year = 2026)))
        val html = f.readText()
        assertEquals("Leaves_Online_show_NightKitchen.html", f.name)
        assertTrue(html.contains("<h1>Night Kitchen</h1>") && html.contains("<p>It is quiet then.</p>") && html.contains("mailto:jade@example.com"))
        assertTrue(!html.contains("<Cooler>"))
    }

    @Test fun commonQuestionsAreAnsweredOnThePhone() = kotlinx.coroutines.runBlocking {
        ctx
        val E = com.emotionalleaves.studioagent.work.Engine
        G.dao.put(com.emotionalleaves.studioagent.data.Ledger(kind = "sale", amount = 300.0, date = System.currentTimeMillis()))
        G.dao.put(com.emotionalleaves.studioagent.data.Ledger(kind = "fee", amount = -25.0, date = System.currentTimeMillis()))
        val money = E.answerLocally("How much have I sold this year?")!!
        assertTrue(money, money.startsWith("This year: ${G.settings.currency}300 in, ${G.settings.currency}25 in costs"))
        assertTrue(E.answerLocally("when is my next deadline?")!!.contains("GO: calls"))
        assertTrue(E.answerLocally("what exercise is next?")!!.startsWith("Next exercise: Draw a chair"))
        assertEquals(null, E.answerLocally("Who painted the Mona Lisa?"))
    }

    @Test fun qrCodesAreDrawn() {
        ctx
        val b = com.emotionalleaves.studioagent.media.Qr.bitmap("https://jadeleaves.github.io", 300)!!
        assertEquals(300, b.width)
        // A QR code is black and white, with plenty of each.
        val dark = (0 until 300 step 5).sumOf { y -> (0 until 300 step 5).count { x -> android.graphics.Color.red(b.getPixel(x, y)) < 50 } }
        assertTrue(dark in 400..3200)
    }


    @Test fun anOpeningAtAVenueBecomesAnInvitation() = kotlinx.coroutines.runBlocking {
        ctx
        val day = java.time.LocalDate.now().plusDays(10)
        val words = day.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US))
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                if (request.path == "/") MockResponse().setBody("<html><body><h1>Gallery 4</h1><p>New show: Small Hours. Opening reception: $words, 6 to 8 pm. All welcome.</p></body></html>")
                else MockResponse().setResponseCode(404)
        }
        server.start()
        try {
            G.dao.contactsNow().forEach { G.dao.del(it) }
            G.dao.put(com.emotionalleaves.studioagent.data.Contact(venue = "Gallery 4", website = server.url("/").toString()))
            assertEquals(1, Engine.findOpenings())
            val e = G.dao.eventsNow().single { it.type == "invite" }
            assertEquals("Opening at Gallery 4", e.title)
            assertEquals(day, Engine.date(e.date))
            assertEquals("the same opening is not added twice", 0, Engine.findOpenings())
        } finally { server.shutdown() }
    }

}
