package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Planner
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.work.Engine
import java.time.LocalDate

/**
 * The calls that fit, one at a time, with big buttons: yes, no, not this year, or skip. Nothing else on the screen,
 * so deciding takes a minute each and a break at work is enough for all of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecideScreen(nav: NavController) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current; val dao = G.dao
    val opps by dao.opps().collectAsState(emptyList())
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    var decided by remember { mutableStateOf(0) }
    var askWhy by remember { mutableStateOf(0L) }
    val p by androidx.compose.runtime.produceState<com.emotionalleaves.studioagent.domain.Profile?>(null, opps.size) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { Engine.profile() } catch (e: Exception) { null } } }
    val prof = p
    val list = if (prof == null) emptyList() else Engine.shortlist(opps, prof, now(), Engine.stretchOk(now()), 20).filter { it.id !in skipped }
    val o = list.firstOrNull()
    Screen("Decide, one at a time", nav) {
        Intro("decide", "The calls that fit you, one at a time. Yes starts the entry, No teaches the ranking, Not this year keeps it for next year. Skip if you are not sure.")
        if (o == null) {
            Panel(strong = true) { Text(if (decided > 0) "Done: ${Texts.count(decided, "call")} decided." else "Nothing waiting to be decided.", style = MaterialTheme.typography.titleMedium) }
            Button(onClick = { nav.navigate("calls") }) { Text("Back to Calls") }
            return@Screen
        }
        Small("${list.size} to go")
        Panel(strong = true) {
            Text(o.title, style = MaterialTheme.typography.headlineSmall)
            Small(listOf(o.venue.ifBlank { o.organiser }, o.city, if (o.online) "online" else "").filter { it.isNotBlank() }.joinToString(", "))
            Text((if (o.deadline > 0) "Closes ${fmtDate(o.deadline)} (" + Planner.weekendsLeft(LocalDate.now(), Engine.date(o.deadline)) + "). " else "") + (if (o.fee > 0) "Fee ${money(o.fee)}." else if (Texts.feeStated(o)) "Free to enter." else "Fee not stated."))
            o.fitReasons.lines().filter { it.isNotBlank() }.take(3).forEach { Small(it) }
            Engine.plainWordsCached(o).take(3).forEach { Text("• $it") }
        }
        Button(onClick = { val x = o; decided++; scope.io { val id = Engine.createApplication(ctx, x); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { skipped = skipped + x.id } } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Yes: start the entry") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { askWhy = o.id }) { Text("No") }
            OutlinedButton(onClick = { val x = o; decided++; skipped = skipped + x.id; scope.undoable("Not this year: ${x.title}", { dao.put(x.copy(status = "later")) }, { dao.put(x) }) }) { Text("Not this year") }
            TextButton(onClick = { skipped = skipped + o.id }) { Text("Skip") }
        }
        // Why not, in one tap: it teaches the ranking what to bring forward.
        if (askWhy == o.id) Choice("Why not? (one tap, it teaches the ranking)", listOf("too far", "fee", "wrong vibe", "no reason"), "", names = { mapOf("too far" to "Too far", "fee" to "The fee", "wrong vibe" to "Not my kind of show", "no reason" to "No reason")[it] ?: it }) { r ->
            val x = o; askWhy = 0L; decided++; skipped = skipped + x.id
            if (r != "no reason") st.addNoReason(r)
            scope.undoable("Said no to ${x.title}", { dao.put(x.copy(status = "no", noReason = r)); Engine.rescore() }, { dao.put(x); Engine.rescore() })
        }
        TextButton(onClick = { nav.navigate("call/${o.id}") }) { Text("See the whole call") }
    }
}
