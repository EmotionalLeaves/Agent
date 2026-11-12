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
        assertEquals(1920 to 5L * 1024 * 1024, ImageTools.parseSpecs(""))
        assertEquals(1200 to 2L * 1024 * 1024, ImageTools.parseSpecs("JPEG, 1200 px on the long side, max 2 MB"))
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
                Papers.labels(ctx, s, works), Papers.certificate(ctx, s, works[0], 2), Papers.billOfSale(ctx, s, works[0], "Sam Buyer", 1300.0, 1),
                Papers.proposal(ctx, s, "Riverside Gallery", "Night", "A statement.", "A bio.", works))
        } finally { com.emotionalleaves.studioagent.media.Pdf.previewDir = null }
        assertEquals(listOf("Leaves_CV.pdf", "Leaves_Portfolio.pdf", "Leaves_Price_list.pdf", "Leaves_Wall_labels.pdf", "Leaves_Certificate_Piece1_2.pdf", "Leaves_Bill_of_sale_Piece1.pdf", "Leaves_Proposal_RiversideGallery.pdf"), files.map { it.name })
        files.forEach { f -> assertTrue(f.name, File(docs, f.nameWithoutExtension + "_p1.png").length() > 1000) }
        assertTrue("30 CV lines need a second page", File(docs, "Leaves_CV_p2.png").exists())
        assertTrue("9 labels need a second page", File(docs, "Leaves_Wall_labels_p2.png").exists())
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
}
