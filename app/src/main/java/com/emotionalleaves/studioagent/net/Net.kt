package com.emotionalleaves.studioagent.net

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Authenticator
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/** Reads one public page as plain text. Identifies itself, one request, no crawling. */
object Fetch {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun pageText(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "StudioAgent/1.0 (personal artist tool; one page per day)").build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("Page returned ${r.code}")
            val html = r.body?.string().orEmpty()
            val doc = Jsoup.parse(html, url)
            doc.select("script, style, nav, footer, noscript").remove()
            val text = doc.body()?.text().orEmpty()
            (doc.title() + "\n" + text).trim()
        }
    }

    fun htmlToText(html: String): String = Jsoup.parse(html).text()
}

/** Writes files to a GitHub repo with a personal access token (backup and the portfolio page). */
object GitHub {
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    private fun req(url: String, token: String) = Request.Builder().url(url)
        .header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")

    /** [repo] is "owner/name". Creates or updates one file on the default branch. */
    fun putFile(repo: String, path: String, bytes: ByteArray, message: String, token: String) {
        val url = "https://api.github.com/repos/${repo.trim()}/contents/${path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }}"
        var sha: String? = null
        http.newCall(req(url, token).get().build()).execute().use { r ->
            if (r.isSuccessful) sha = try { JSONObject(r.body?.string().orEmpty()).optString("sha").ifBlank { null } } catch (e: Exception) { null }
        }
        val body = JSONObject().put("message", message).put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        sha?.let { body.put("sha", it) }
        http.newCall(req(url, token).put(body.toString().toRequestBody(json)).build()).execute().use { r ->
            if (!r.isSuccessful) throw Exception("GitHub said ${r.code}: " + r.body?.string().orEmpty().take(160))
        }
    }

    /** Turns on GitHub Pages for the repo if it is not on yet. Fails quietly; you can also do it in the repo settings. */
    fun enablePages(repo: String, token: String) {
        try {
            val body = JSONObject().put("source", JSONObject().put("branch", "main").put("path", "/"))
            http.newCall(req("https://api.github.com/repos/${repo.trim()}/pages", token).post(body.toString().toRequestBody(json)).build()).execute().close()
        } catch (e: Exception) { }
    }

    fun pagesUrl(repo: String): String {
        val owner = repo.substringBefore("/").trim(); val name = repo.substringAfter("/").trim()
        return if (name.equals("$owner.github.io", true)) "https://$name/" else "https://${owner.lowercase()}.github.io/$name/"
    }
}

data class MailMsg(val from: String, val subject: String, val text: String, val date: Long)

/** Email with an app password over SMTP and IMAP. */
object Mail {
    fun send(host: String, user: String, pass: String, to: String, subject: String, body: String, files: List<File> = emptyList()) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true"); put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", host); put("mail.smtp.port", "587")
            put("mail.smtp.connectiontimeout", "30000"); put("mail.smtp.timeout", "120000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
        })
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(user))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        msg.setSubject(subject, "UTF-8")
        val mp = MimeMultipart()
        mp.addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
        for (f in files) if (f.exists()) mp.addBodyPart(MimeBodyPart().apply { attachFile(f) })
        msg.setContent(mp)
        Transport.send(msg)
    }

    /** Newest messages in the inbox received after [since]. Read-only. */
    fun recent(host: String, user: String, pass: String, since: Long, max: Int = 40): List<MailMsg> {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps"); put("mail.imaps.connectiontimeout", "30000"); put("mail.imaps.timeout", "60000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(host, user, pass)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val count = inbox.messageCount
            if (count == 0) return emptyList()
            val msgs = inbox.getMessages(maxOf(1, count - max + 1), count)
            val out = mutableListOf<MailMsg>()
            for (m in msgs) {
                val date = (m.receivedDate ?: m.sentDate)?.time ?: 0L
                if (date <= since) continue
                val from = (m.from?.firstOrNull() as? InternetAddress)?.address ?: m.from?.firstOrNull()?.toString().orEmpty()
                out += MailMsg(from, m.subject.orEmpty(), try { textOf(m).take(20000) } catch (e: Exception) { "" }, date)
            }
            inbox.close(false)
            return out
        } finally {
            store.close()
        }
    }

    private fun textOf(p: Part): String {
        if (p.isMimeType("text/plain")) return p.content?.toString().orEmpty()
        if (p.isMimeType("text/html")) return Fetch.htmlToText(p.content?.toString().orEmpty())
        val c = p.content
        if (c is Multipart) {
            var html = ""
            for (i in 0 until c.count) {
                val bp = c.getBodyPart(i)
                if (bp.isMimeType("text/plain")) return bp.content?.toString().orEmpty()
                val t = textOf(bp)
                if (t.isNotBlank() && html.isBlank()) html = t
            }
            return html
        }
        return ""
    }
}
