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

/** Reads one public page as plain text. Identifies itself, asks robots.txt first, no crawling. */
object Fetch {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(true).build()
    const val AGENT = "StudioAgent/1.0 (personal artist tool; reads a page at most once a day)"

    /** [automatic] reads (the daily check) follow robots.txt; a page you open yourself is read like a browser would. */
    fun html(address: String, automatic: Boolean = true): org.jsoup.nodes.Document {
        // An address typed without https:// still works.
        val url = address.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        if (automatic && !Robots.allowed(url)) throw Exception("This site asks apps not to read that page")
        val req = Request.Builder().url(url).header("User-Agent", AGENT).header("Accept", "text/html,application/xhtml+xml").build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("Page returned ${r.code}")
            Jsoup.parse(r.body?.string().orEmpty(), r.request.url.toString())
        }
    }

    fun text(doc: org.jsoup.nodes.Document): String {
        val d = doc.clone()
        d.select("script, style, nav, footer, noscript, header, form, iframe").remove()
        // Keep line breaks between blocks, so the first line is a title and dates stay near their labels.
        d.select("br, p, h1, h2, h3, h4, li, tr, div, dt, dd").forEach { it.prepend("\n") }
        val body = d.body()?.wholeText().orEmpty().lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }.joinToString("\n")
        val h1 = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        return (listOf(h1.ifBlank { doc.title() }) + body).joinToString("\n").trim()
    }

    fun pageText(url: String): String = text(html(url, automatic = false))

    fun htmlToText(html: String): String = Jsoup.parse(html).text()

    internal fun get(url: String): String? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", AGENT).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (e: Exception) { null }
}

/** Honours a site's robots.txt: if it asks bots to stay out of a page, the app does not read it. */
object Robots {
    private val cache = mutableMapOf<String, Pair<Long, List<Pair<Boolean, String>>>>()

    fun allowed(url: String): Boolean {
        val u = try { java.net.URI(url) } catch (e: Exception) { return false }
        val host = u.scheme + "://" + u.host + (if (u.port > 0) ":${u.port}" else "")
        val now = System.currentTimeMillis()
        val rules = cache[host]?.takeIf { now - it.first < 86_400_000 }?.second ?: run {
            val r = rules(Fetch.get("$host/robots.txt").orEmpty())
            cache[host] = now to r
            r
        }
        val path = (u.rawPath ?: "/").ifBlank { "/" } + (u.rawQuery?.let { "?$it" } ?: "")
        return allowedBy(rules, path)
    }

    /** The longest matching rule decides, as search engines do; with no matching rule a page may be read. */
    fun allowedBy(rules: List<Pair<Boolean, String>>, path: String): Boolean =
        rules.filter { it.second.isNotBlank() && path.startsWith(it.second.removeSuffix("*").removeSuffix("$")) }.maxByOrNull { it.second.length }?.first ?: true

    /** The Disallow lines that apply to every bot (User-agent: *) or to this app by name. */
    fun disallowed(txt: String): List<String> = rules(txt).filter { !it.first }.map { it.second }

    /** Allow and Disallow lines for every bot or this app, in order: (allowed, path). */
    fun rules(txt: String): List<Pair<Boolean, String>> {
        val out = mutableListOf<Pair<Boolean, String>>()
        var applies = false; var inAgents = false
        txt.lines().map { it.substringBefore('#').trim() }.filter { it.isNotBlank() }.forEach { line ->
            val key = line.substringBefore(':').trim().lowercase(); val v = line.substringAfter(':', "").trim()
            when (key) {
                "user-agent" -> { if (!inAgents) applies = false; inAgents = true; if (v == "*" || v.contains("studioagent", true)) applies = true }
                "disallow" -> { inAgents = false; if (applies && v.isNotBlank()) out += false to v }
                "allow" -> { inAgents = false; if (applies && v.isNotBlank()) out += true to v }
                else -> inAgents = false
            }
        }
        return out
    }
}

/**
 * Reads a listing page (many calls on one page) without AI: finds the links that look like individual calls,
 * judged by the words and dates around each link. Each one is then opened and read on its own.
 */
object Listing {
    private val callWords = Regex("call for|open call|deadline|juried|exhibition|residency|grant|competition|submission|entries|entry|apply|fellowship|prize|award|commission|festival|art fair|show", RegexOption.IGNORE_CASE)
    private val skipWords = Regex("^(log ?in|sign ?(up|in)|register|about|contact|privacy|terms|faq|help|blog|home|cart|donate|shop|search|menu|next|previous|more|read more|learn more|view all|subscribe|newsletter|advertise|pricing|careers|jobs)$", RegexOption.IGNORE_CASE)
    // Links to lists, categories and the site's own pages, not to one call.
    private val listWords = Regex("see all|view all|browse|upcoming|ongoing|all calls|more calls|categor|publish your|post (a|your)|advertise|author|news editor|newsletter|blog|learn more about", RegexOption.IGNORE_CASE)
    private val skipHref = Regex("facebook\\.com|instagram\\.com|twitter\\.com|x\\.com/|linkedin\\.com|youtube\\.com|tiktok\\.com|pinterest\\.com|mailto:|tel:|javascript:|#$|/login|/signin|/register|/account|/cart|/privacy|/terms|\\.(pdf|jpg|png|zip)$", RegexOption.IGNORE_CASE)

    data class Link(val url: String, val text: String, val context: String, val score: Int)

    fun links(doc: org.jsoup.nodes.Document, max: Int = 30): List<Link> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Link>()
        val base = try { java.net.URI(doc.location()).host.orEmpty() } catch (e: Exception) { "" }
        for (a in doc.select("main a[href], article a[href], body a[href]")) {
            val href = a.absUrl("href").substringBefore('#')
            val text = a.text().trim()
            if (href.isBlank() || !href.startsWith("http") || href in seen) continue
            if (text.length !in 6..160 || skipWords.matches(text) || listWords.containsMatchIn(text) || skipHref.containsMatchIn(href)) continue
            // One or two words ("Deadline", "Competitions") name a category, not a call.
            if (text.split(Regex("\\s+")).size < 3) continue
            if (a.parents().any { it.tagName() in setOf("nav", "footer", "header") }) continue
            // The smallest block around the link that still reads like one listing.
            var box: org.jsoup.nodes.Element = a
            for (p in a.parents()) { if (p.text().length > 900) break; box = p; if (p.tagName() in setOf("li", "article", "tr")) break }
            val ctx = box.text().take(900)
            var score = 0
            if (callWords.containsMatchIn(text)) score += 2
            if (callWords.containsMatchIn(ctx)) score += 1
            if (com.emotionalleaves.studioagent.domain.CallReader.dates(ctx).isNotEmpty()) score += 2
            if (Regex("deadline|due|closes", RegexOption.IGNORE_CASE).containsMatchIn(ctx)) score += 1
            val host = try { java.net.URI(href).host.orEmpty() } catch (e: Exception) { "" }
            if (host == base && java.net.URI(href).path.orEmpty().length <= 1) continue
            if (score < 3) continue
            seen += href
            out += Link(href, text, ctx, score)
        }
        return out.sortedByDescending { it.score }.take(max)
    }
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
    /** The servers for the common free mail services, worked out from the address. Null when the service is unknown or does not allow app passwords. */
    fun serversFor(email: String): Pair<String, String>? = when (email.substringAfter('@', "").lowercase().trim()) {
        "gmail.com", "googlemail.com" -> "smtp.gmail.com" to "imap.gmail.com"
        "yahoo.com", "ymail.com", "rocketmail.com" -> "smtp.mail.yahoo.com" to "imap.mail.yahoo.com"
        "icloud.com", "me.com", "mac.com" -> "smtp.mail.me.com" to "imap.mail.me.com"
        "aol.com" -> "smtp.aol.com" to "imap.aol.com"
        "fastmail.com", "fastmail.fm" -> "smtp.fastmail.com" to "imap.fastmail.com"
        "zoho.com", "zohomail.com" -> "smtp.zoho.com" to "imap.zoho.com"
        else -> null
    }

    /** Outlook and Hotmail stopped letting apps sign in with a password, so sending from the app cannot work there. */
    fun noAppPasswords(email: String): Boolean = email.substringAfter('@', "").lowercase().trim() in setOf("outlook.com", "hotmail.com", "live.com", "msn.com")

    /** Tests point mail at a local server: (smtp port, imap port), plain connections. Never set in the app. */
    internal var testPorts: Pair<Int, Int>? = null

    /** Sends from your own account. Your name shows as the sender, and replies come back to you. */
    fun send(host: String, user: String, pass: String, to: String, subject: String, body: String, files: List<File> = emptyList(), fromName: String = "") {
        val test = testPorts
        val props = Properties().apply {
            put("mail.smtp.auth", "true"); put("mail.smtp.starttls.enable", if (test == null) "true" else "false")
            if (test == null) put("mail.smtp.starttls.required", "true")
            put("mail.smtp.host", host); put("mail.smtp.port", (test?.first ?: 587).toString())
            put("mail.smtp.connectiontimeout", "30000"); put("mail.smtp.timeout", "120000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
        })
        val msg = MimeMessage(session)
        val me = if (fromName.isNotBlank()) InternetAddress(user, fromName, "UTF-8") else InternetAddress(user)
        msg.setFrom(me)
        msg.replyTo = arrayOf(me)
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        msg.setSubject(subject, "UTF-8")
        msg.sentDate = java.util.Date()
        val mp = MimeMultipart()
        mp.addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
        for (f in files) if (f.exists()) mp.addBodyPart(MimeBodyPart().apply { attachFile(f); fileName = f.name })
        msg.setContent(mp)
        Transport.send(msg)
    }

    /** Newest messages in the inbox received after [since]. Read-only. */
    fun recent(host: String, user: String, pass: String, since: Long, max: Int = 40): List<MailMsg> {
        val test = testPorts
        val proto = if (test == null) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", proto); put("mail.$proto.connectiontimeout", "30000"); put("mail.$proto.timeout", "60000")
        }
        val store = Session.getInstance(props).getStore(proto)
        if (test == null) store.connect(host, user, pass) else store.connect(host, test.second, user, pass)
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
