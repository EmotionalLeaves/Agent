package com.emotionalleaves.studioagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.studioagent.ai.Ai
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The free writing help with no key: tries the next service when one is down, keeps only the answer, and never blocks the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreeAiTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val down = MockWebServer()
    private val up = MockWebServer()

    private fun answer(content: String) = MockResponse().setBody(JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString())

    @After fun stop() { Ai.freeOverride = null; down.shutdown(); up.shutdown() }

    @Test fun aBusyServiceIsSkippedAndTheAnswerIsCleanedUp() = runBlocking {
        ctx
        G.settings.aiKey = ""; G.settings.freeAi = true
        down.enqueue(MockResponse().setResponseCode(503))
        up.enqueue(answer("<think>The user wants a painter.</think>Frida Kahlo."))
        Ai.freeOverride = listOf(Triple(down.url("/v1").toString(), "a", "Down"), Triple(up.url("/v1").toString(), "b", "Up"))
        assertTrue(G.ai.ready && G.ai.free)
        assertEquals("Frida Kahlo.", G.ai.complete("Reply briefly.", "Name a painter.", fast = true))
        val sent = JSONObject(up.takeRequest().body.readUtf8())
        assertEquals("b", sent.getString("model"))
    }

    @Test fun aCallReadByTheFreeHelperIsCheckedAgainstThePage() = runBlocking {
        ctx
        G.settings.aiKey = ""; G.settings.freeAi = true; G.settings.city = "Austin"
        // The model wraps its JSON in a code fence and gets the year wrong; the plain reader corrects the date and fills the fee.
        up.enqueue(answer("Here you go:\n```json\n[{\"title\":\"Winter Open 2027\",\"deadline\":\"2025-01-15\",\"fee\":0,\"scope\":\"\"}]\n```"))
        Ai.freeOverride = listOf(Triple(up.url("/v1").toString(), "b", "Up"))
        val page = "Winter Open 2027\nRiverside Arts, Austin. Deadline: January 15, 2027. Entry fee: $25. Up to 3 works. Email entries@riverside.example.org"
        assertEquals(1, Engine.ingest(page, "Shared"))
        val o = G.dao.oppsNow().single { it.title == "Winter Open 2027" }
        assertEquals(25.0, o.fee, 0.0)
        assertEquals(2027, java.time.Instant.ofEpochMilli(o.deadline).atZone(java.time.ZoneId.systemDefault()).year)
        assertEquals("entries@riverside.example.org", o.email); assertEquals("local", o.scope); assertEquals(3, o.maxWorks)
    }

    @Test fun anEmailTheHelperWritesEndsWithTheRealSignature() = runBlocking {
        ctx
        G.settings.aiKey = ""; G.settings.freeAi = true
        G.settings.firstName = "Jade"; G.settings.lastName = "Leaves"; G.settings.email = "jade@example.com"; G.settings.website = ""; G.settings.phone = ""; G.settings.city = "Austin"
        val reply = JSONObject().put("subject", "Showing at Campus Gallery").put("body", "Dear Ms Reyes,\n\nI make graphite drawings of kitchens at dawn.\n\nBest,\nJade")
            .put("follow_up", "Dear Ms Reyes,\n\nA short follow-up to my note.\n\nKind regards,")
        up.enqueue(answer(reply.toString()))
        Ai.freeOverride = listOf(Triple(up.url("/v1").toString(), "b", "Up"))
        val (_, body, follow) = G.ai.outreach("Campus Gallery", "", "", "", "")
        val sig = "Kind regards,\n\nJade Leaves\njade@example.com\nAustin"
        assertTrue(body, body.endsWith("kitchens at dawn.\n\n$sig"))
        assertTrue(follow, follow.endsWith("follow-up to my note.\n\n$sig"))
    }

    @Test fun whenEveryServiceIsDownTheMessageIsPlain() = runBlocking {
        ctx
        G.settings.aiKey = ""; G.settings.freeAi = true
        down.enqueue(MockResponse().setResponseCode(429))
        Ai.freeOverride = listOf(Triple(down.url("/v1").toString(), "a", "Down"))
        val e = try { G.ai.complete("x", "y", fast = true); null } catch (e: Exception) { e }
        assertTrue(e?.message.orEmpty(), e?.message.orEmpty().startsWith("The free writing help did not answer just now"))
    }

    @Test fun anyKeyIsRecognisedByHowItStarts() {
        assertEquals("Google Gemini", Ai.detect("AIzaSyExample").name)
        assertEquals("Claude", Ai.detect("sk-ant-api03-x").name)
        assertEquals("Groq", Ai.detect("gsk_abc").name)
        assertEquals("OpenRouter", Ai.detect("sk-or-v1-abc").name)
        assertEquals("OpenAI", Ai.detect("sk-proj-abc").name)
    }
}
