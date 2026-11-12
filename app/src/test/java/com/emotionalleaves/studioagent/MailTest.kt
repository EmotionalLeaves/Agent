package com.emotionalleaves.studioagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.studioagent.data.Appl
import com.emotionalleaves.studioagent.data.Opp
import com.emotionalleaves.studioagent.net.Mail
import com.emotionalleaves.studioagent.work.Engine
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart

/** A real mail server on the test machine: the application email really goes out, and the gallery's answer really comes back in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MailTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val smtp = ServerSetup(3025, "127.0.0.1", "smtp")
    private val imap = ServerSetup(3143, "127.0.0.1", "imap")
    private lateinit var mail: GreenMail

    @Before fun start() {
        mail = GreenMail(arrayOf(smtp, imap)).apply { start(); setUser("jade@example.com", "jade@example.com", "app-password") }
        Mail.testPorts = smtp.port to imap.port
    }

    @After fun stop() { Mail.testPorts = null; mail.stop() }

    @Test fun theApplicationGoesOutAndTheAnswerComesBack() = runBlocking {
        ctx
        val s = G.settings
        s.firstName = "Jade"; s.lastName = "Leaves"; s.freeAi = false; s.aiKey = ""
        s.mailUser = "jade@example.com"; s.mailPass = "app-password"; s.smtpHost = "127.0.0.1"; s.imapHost = "127.0.0.1"
        val dir = File(ctx.cacheDir, "pack_test").apply { mkdirs() }
        File(dir, "Leaves_01_Piece1.jpg").writeBytes(ByteArray(2000) { 1 })
        File(dir, "Leaves_CV.pdf").writeText("%PDF-1.4 test")
        val opp = G.dao.put(Opp(title = "Winter Open 2027", email = "entries@riverside.example.org", deadline = System.currentTimeMillis() + 30L * 86_400_000, createdAt = 1))
        val id = G.dao.put(Appl(oppId = opp, method = "email", status = "sending", emailTo = "entries@riverside.example.org",
            emailSubject = "Winter Open 2027: submission from Jade Leaves", emailBody = "Dear selection committee,\n\nPlease find attached.", packDir = dir.absolutePath, createdAt = 1))

        assertTrue(Engine.sendApplication(ctx, id))
        val sent = mail.receivedMessages.single()
        assertEquals("Winter Open 2027: submission from Jade Leaves", sent.subject)
        assertEquals("Jade Leaves", (sent.from.single() as InternetAddress).personal)
        val parts = sent.content as MimeMultipart
        assertEquals(listOf("Leaves_01_Piece1.jpg", "Leaves_CV.pdf"), (1 until parts.count).map { parts.getBodyPart(it).fileName }.sorted())
        val after = G.dao.applsNow().single { it.id == id }
        assertEquals("submitted", after.status)
        assertTrue(after.proofText, after.proofText.startsWith("Sent by email to entries@riverside.example.org") && after.proofText.contains("2 attachments"))

        // The gallery writes back. The daily check reads the inbox and records the result.
        s.lastMailCheck = System.currentTimeMillis() - 60_000
        GreenMailUtil.sendTextEmail("jade@example.com", "entries@riverside.example.org", "Re: Winter Open 2027",
            "Congratulations! We are pleased to let you know your work has been selected for the exhibition.", smtp)
        Engine.checkMail(ctx)
        assertEquals("accepted", G.dao.applsNow().single { it.id == id }.status)
    }

    @Test fun aWrongPasswordFailsInPlainWordsAndKeepsTheApplication() = runBlocking {
        ctx
        val s = G.settings
        s.mailUser = "jade@example.com"; s.mailPass = "wrong"; s.smtpHost = "127.0.0.1"
        val opp = G.dao.put(Opp(title = "Spring Show", createdAt = 1))
        val id = G.dao.put(Appl(oppId = opp, method = "email", status = "sending", emailTo = "a@b.org", emailSubject = "x", emailBody = "y", createdAt = 1))
        assertTrue(!Engine.sendApplication(ctx, id))
        val a = G.dao.applsNow().single { it.id == id }
        assertEquals("ready", a.status)
        assertTrue(a.proofText, a.proofText.startsWith("Email did not send:") && !a.proofText.contains("Exception"))
    }
}
