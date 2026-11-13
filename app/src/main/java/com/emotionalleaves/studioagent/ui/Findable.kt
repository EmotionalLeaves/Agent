package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Drafts
import com.emotionalleaves.studioagent.sys.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Jurors and venues look you up before they say yes. What they should find, ticked off, and a profile line to start from. */
@Composable
fun FindableScreen(nav: NavController) {
    val ctx = LocalContext.current
    val tick by st.changed.collectAsState()
    // Without social media the checks are about your own page; their ticks are kept apart (from 100 up).
    val social = tick >= 0 && st.socialOn
    val items = if (social) Drafts.findable else Drafts.findableQuiet
    val base = if (social) 0 else 100
    val done = st.findableDone.split(",").mapNotNull { it.toIntOrNull() }.filter { it in base until base + items.size }.map { it - base }.toSet()
    val statement by produceState("") { value = withContext(Dispatchers.IO) { G.dao.docsNow().filter { it.type == "statement" && it.text.isNotBlank() }.sortedByDescending { if (it.approved) 1 else 0 }.firstOrNull()?.text.orEmpty() } }
    Screen("When someone looks you up", nav) {
        Intro("findable", "Before choosing work, most jurors and gallery staff search the artist's name. A few simple things make what they find look like a working artist.")
        Text("${done.size} of ${items.size} done", fontWeight = FontWeight.SemiBold)
        items.forEachIndexed { i, label -> Toggle(label, i in done) { on ->
            val all = st.findableDone.split(",").mapNotNull { it.toIntOrNull() }.toMutableSet(); if (on) all += base + i else all -= base + i
            st.findableDone = all.sorted().joinToString(",") } }
        if (social) {
            Section("A profile line to start from")
            val line = Drafts.profileLine(st.mediums, st.city, statement)
            Panel { Text(line) }
            TextButton(onClick = { Share.text(ctx, line) }) { Text("Copy or send it") }
        } else Small("No social media needed: a page with your name and work, and an email address, is what most jurors look for. (Settings, Hours and calm, can turn on help with posting, if you ever want it.)")
        if (st.website.isBlank()) TextButton(onClick = { nav.navigate("materials") }) { Text("Make your web page (in Statement, bio and CV)") }
        if (social) TextButton(onClick = { nav.navigate("works") }) { Text("Post a piece (open it, then Pictures and papers)") }
    }
}
