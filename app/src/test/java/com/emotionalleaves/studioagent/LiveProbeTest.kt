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

        log("\n== Call listings, read without AI")
        val today = LocalDate.now()
        listOf(
            "https://artcall.org/calls",
            "https://artist.callforentry.org/festivals.php",
            "https://www.artworkarchive.com/call-for-entry",
            "https://www.artshow.com/juriedshows/index.html",
            "https://www.transartists.org/en/call",
            "https://www.artrabbit.com/artist-opportunities",
            "https://zapplication.org/participating-events.php",
            "https://www.nyfa.org/opportunities/",
            "https://www.artjobs.com/open-calls",
            "https://www.smarterentry.com/",
            "https://www.entrythingy.com/",
            "https://callforartists.com/",
            "https://www.re-title.com/opportunities/",
            "https://www.artdeadline.com/",
            "https://www.creative-capital.org/category/opportunities/",
            "https://www.artistcommunities.org/directory/residencies",
            "https://www.chicagoartistscoalition.org/opportunities",
            "https://hyperallergic.com/tag/opportunities/",
        ).forEach { url ->
            log("\n-- $url")
            try {
                log("robots allows: ${Robots.allowed(url)}")
                val doc = Fetch.html(url, automatic = false)
                val text = Fetch.text(doc)
                val links = Listing.links(doc)
                log("text ${text.length} chars, ${links.size} call-like links")
                links.take(4).forEach { log("  link [${it.score}] ${it.text.take(70)} -> ${it.url}") }
                links.take(2).forEach { l ->
                    try {
                        val page = "Link: ${l.url}\n" + Fetch.text(Fetch.html(l.url, automatic = false))
                        val o = CallReader.read(page, today, ZoneId.of("America/Chicago"), "probe", System.currentTimeMillis())
                        val d = if (o.deadline > 0) java.time.Instant.ofEpochMilli(o.deadline).atZone(ZoneId.of("America/Chicago")).toLocalDate().toString() else "none"
                        log("  read: title='${o.title.take(70)}' deadline=$d fee=${o.fee} kind=${o.kind} scope=${o.scope} words=${o.statementLimit} email=${o.email}")
                    } catch (e: Exception) { log("  read failed: $e") }
                }
            } catch (e: Exception) { log("failed: $e") }
        }
        File("build/screens").apply { mkdirs() }.resolve("live.txt").writeText(out.toString())
    }
}
