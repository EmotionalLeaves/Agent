package com.emotionalleaves.studioagent

import com.emotionalleaves.studioagent.domain.CallReader
import com.emotionalleaves.studioagent.net.Fetch
import com.emotionalleaves.studioagent.net.Listing
import com.emotionalleaves.studioagent.net.Robots
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Runs only on the build server, which can reach the internet. It tries the real call listings and the free AI services
 * and writes what happened to live.txt in the screenshots bundle. It never fails the build: the internet is not ours to test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveProbeTest {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val out = StringBuilder()
    private fun log(s: String) { out.appendLine(s); println(s) }

    private fun post(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): Pair<Int, String> = try {
        val b = Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType())).header("User-Agent", Fetch.AGENT)
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { it.code to it.body?.string().orEmpty() }
    } catch (e: Exception) { -1 to e.toString() }

    private fun get(url: String): Pair<Int, String> = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", Fetch.AGENT).build()).execute().use { it.code to it.body?.string().orEmpty() }
    } catch (e: Exception) { -1 to e.toString() }

    private fun chat(model: String) = JSONObject().put("model", model).put("max_tokens", 60).put("messages", JSONArray()
        .put(JSONObject().put("role", "system").put("content", "Reply in one short sentence."))
        .put(JSONObject().put("role", "user").put("content", "Name one famous painter.")))

    @Test fun probe() {
        Assume.assumeTrue(System.getenv("CI") == "true")
        log("Live probe, ${LocalDate.now()}")
        log("\n== Free AI with no key")
        listOf(
            "pollinations openai POST" to { post("https://text.pollinations.ai/openai", chat("openai")) },
            "pollinations GET" to { get("https://text.pollinations.ai/Name%20one%20famous%20painter%20in%20one%20sentence") },
            "pollinations gen (no key)" to { post("https://gen.pollinations.ai/v1/chat/completions", chat("openai")) },
            "llm7 models" to { get("https://api.llm7.io/v1/models") },
            "llm7 default" to { post("https://api.llm7.io/v1/chat/completions", chat("default")) },
            "llm7 fast" to { post("https://api.llm7.io/v1/chat/completions", chat("fast")) },
            "llm7 gpt-4.1-nano" to { post("https://api.llm7.io/v1/chat/completions", chat("gpt-4.1-nano")) },
            "llm7 gpt-4o-mini" to { post("https://api.llm7.io/v1/chat/completions", chat("gpt-4o-mini")) },
        ).forEach { (name, call) ->
            val t0 = System.currentTimeMillis()
            val (code, body) = call()
            log("$name: $code in ${System.currentTimeMillis() - t0} ms: ${body.replace("\n", " ").take(400)}")
        }

        log("\n== The app's own free writing help (Ai class, no key)")
        try {
            val t0 = System.currentTimeMillis()
            val a = kotlinx.coroutines.runBlocking { G.ai.complete("Reply in one short sentence.", "Name one famous painter.", fast = true, maxTokens = 200) }
            log("answered in ${System.currentTimeMillis() - t0} ms: ${a.take(200)}")
            val calls = kotlinx.coroutines.runBlocking { G.ai.extractCalls("Riverside Arts Center Winter Open. Juried exhibition for artists in Illinois. Deadline: January 15, 2027. Entry fee \$25 for up to 3 works. Submit at https://example.org/winter", "probe") }
            log("extractCalls: " + calls.joinToString { "${it.title} / ${it.deadline} / ${it.fee} / ${it.scope}" })
        } catch (e: Exception) { log("failed: $e") }

        log("\n== Starter listing sites, exactly as the daily check reads them")
        val now = System.currentTimeMillis()
        (com.emotionalleaves.studioagent.work.Engine.starterSources + listOf("ArtJobs" to "https://www.artjobs.com/open-calls", "Artshow" to "https://www.artshow.com/juriedshows/index.html")).forEach { (name, url) ->
            log("\n-- $name $url")
            try {
                log("robots allows: ${Robots.allowed(url)}")
                val doc = Fetch.html(url)
                val links = Listing.links(doc)
                log("${links.size} call-like links")
                var kept = 0
                links.take(6).forEach { l ->
                    try {
                        val page = "Link: ${l.url}\n" + Fetch.text(Fetch.html(l.url))
                        val o = com.emotionalleaves.studioagent.work.Engine.plainCall(page, name, l.text, l.url, now, doc.title())
                        if (o == null) {
                            val at = Regex("deadline|due|closes", RegexOption.IGNORE_CASE).find(page)?.range?.first ?: -1
                            log("  skipped: ${l.text.take(60)}" + (if (at >= 0) " | near deadline: '" + page.substring(at, minOf(page.length, at + 80)).replace("\n", " ") + "'" else " | no deadline word"))
                        } else {
                            kept++
                            val d = if (o.deadline > 0) java.time.Instant.ofEpochMilli(o.deadline).atZone(ZoneId.systemDefault()).toLocalDate().toString() else "none"
                            log("  CALL: '${o.title}' deadline=$d fee=${o.fee} kind=${o.kind} scope=${o.scope}")
                        }
                    } catch (e: Exception) { log("  read failed: ${l.url} $e") }
                }
                log("kept $kept of ${minOf(6, links.size)}")
            } catch (e: Exception) { log("failed: $e") }
        }
        File("build/screens").apply { mkdirs() }.resolve("live.txt").writeText(out.toString())
    }
}
