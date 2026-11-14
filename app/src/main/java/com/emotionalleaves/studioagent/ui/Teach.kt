package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Workshop
import com.emotionalleaves.studioagent.sys.Share

/**
 * Teach a beginner class: an outline that fits the time, what to bring, a fair fee, and the email that offers it to a
 * library, community center or cafe. Paid, local, and a room full of people who now know your work.
 */
@Composable
fun TeachScreen(nav: NavController) {
    val ctx = LocalContext.current
    val contacts by G.dao.contacts().collectAsState(emptyList())
    var kind by rememberSaveable { mutableStateOf(Workshop.kindOf(st.mediums)) }
    var minutes by rememberSaveable { mutableIntStateOf(90) }
    var people by rememberSaveable { mutableIntStateOf(10) }
    val title = rememberSaveable(kind) { mutableStateOf(Workshop.title(kind)) }
    var venueId by rememberSaveable { mutableStateOf(0L) }
    val places = contacts.filter { it.kind in setOf("community", "nonprofit", "university", "campus") && it.email.isNotBlank() }
    Screen("Teach a class", nav) {
        Intro("teach", "A beginner class is paid, local, and puts your work in front of a room of people who now care about it. You do not need to be an expert: you only need to be a few steps ahead, and kind.")
        Banner("Paid to share the basics", "Local, beginner level, and a room of people who now care about your work.", key = 815L, height = 150)
        Choice("What to teach", listOf("drawing", "watercolor", "painting", "printmaking", "collage", "phone drawing"), kind, names = { it.replaceFirstChar { c -> c.uppercase() } }) { kind = it }
        Field("Name of the class", title)
        Count("How long", minutes, 45, 180, 15, " min") { minutes = it }
        Count("How many people", people, 3, 25) { people = it }
        Section("The class")
        Panel {
            // Minutes in a column of their own, so the class reads down like a running order.
            Workshop.outline(kind, minutes).forEach { p ->
                androidx.compose.foundation.layout.Row {
                    Text("${p.minutes} min", Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
                    Text(p.what, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Section("What to bring")
        Panel { Workshop.supplies(kind, people).forEach { Text("• $it") } }
        val (flat, per) = Workshop.fee(minutes, people)
        Small("A fair fee: about ${money(flat)} for the class (the setting up counts), or ${money(per)} a person when you charge at the door. Supplies are a cost to keep in Records.")
        Section("Offer it")
        // Where you already work: staff wellness and enrichment programs look for exactly this, and you know the building.
        if (st.school.isNotBlank()) Panel {
            Text("At ${st.school}", fontWeight = FontWeight.SemiBold)
            Text("Many universities run staff wellness or enrichment sessions. A lunchtime drawing class for staff is an easy first class to teach: the people already know you, and the room is down the hall. Ask HR or the wellness office who runs them.", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = {
                Share.email(ctx, "", "A lunchtime drawing class for staff", Workshop.email(st.fullName.ifBlank { "an artist" }, st.school + " staff", title.value.trim(), minutes.coerceAtMost(60), people, st.website).replace("I'm ${st.fullName.ifBlank { "an artist" }}, a local artist.", "I'm ${st.fullName.ifBlank { "a member of staff" }}; I work here" + (if (st.job.isNotBlank()) " as a ${st.job.trim()}" else "") + ", and I make art."))
            }) { Text("Write to the wellness office") }
        }
        if (places.isNotEmpty()) Choice("To", places.map { it.id.toString() }, venueId.toString(), names = { id -> places.firstOrNull { it.id.toString() == id }?.let { it.venue.ifBlank { it.name } } ?: "" }) { venueId = it.toLong() }
        val to = places.firstOrNull { it.id == venueId }
        val body = Workshop.email(st.fullName.ifBlank { "an artist" }, to?.let { it.venue.ifBlank { it.name } } ?: "", title.value.trim(), minutes, people, st.website, money(flat))
        Panel { Text(body, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = { Share.email(ctx, to?.email ?: "", "A beginner class: " + title.value.trim(), body) }, modifier = Modifier.fillMaxWidth()) { Text(if (to != null) "Send to ${to.venue.ifBlank { to.name }}" else "Write the email") }
        val scopeH = androidx.compose.runtime.rememberCoroutineScope()
        OutlinedButton(onClick = { scopeH.io {
            val f = com.emotionalleaves.studioagent.work.Papers.classHandout(ctx, st, title.value.trim(), Workshop.outline(kind, minutes).map { it.minutes to it.what }, Workshop.homework(kind))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { Share.view(ctx, f, "application/pdf") }
        } }, modifier = Modifier.fillMaxWidth()) { Text("A handout for the people who come (PDF)") }
        OutlinedButton(onClick = { Share.text(ctx, title.value.trim() + "\n\n" + Workshop.outline(kind, minutes).joinToString("\n") { "${it.minutes} min: ${it.what}" } + "\n\nBring:\n" + Workshop.supplies(kind, people).joinToString("\n") { "- $it" }) }, modifier = Modifier.fillMaxWidth()) { Text("Share the outline and list") }
        // Once a date is agreed: the class and its shopping go on your week, and the supplies on your list.
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var booked by rememberSaveable { mutableStateOf("") }
        if (booked.isBlank()) DateField("Booked? The date of the class", "") { d ->
            val day = java.time.LocalDate.parse(d); val t = title.value.trim()
            scope.io {
                G.dao.put(com.emotionalleaves.studioagent.data.Evt(type = "task", title = "Teach: $t", date = com.emotionalleaves.studioagent.work.Engine.millis(day.atTime(18, 0))))
                G.dao.put(com.emotionalleaves.studioagent.data.Evt(type = "task", title = "Buy the supplies for \"$t\"", date = com.emotionalleaves.studioagent.work.Engine.millis(day.minusDays(3).atTime(18, 0))))
                val have = supplies(st.supplies)
                st.supplies = suppliesText(have + Workshop.supplies(kind, people).filter { n -> have.none { it.first.equals(n, true) } }.map { it to true })
            }
            booked = d
        } else Small("Booked for ${fmtDate(com.emotionalleaves.studioagent.work.Engine.millis(java.time.LocalDate.parse(booked).atTime(18, 0)))}: the class and a shopping day three days before are on your to-dos, and the supplies are on your list.")
        // Once it has happened: a line on the CV, under Teaching and Talks.
        var taught by rememberSaveable { mutableStateOf(false) }
        var paid by rememberSaveable { mutableStateOf(0.0) }
        if (!taught) Num("What you were paid for it, if anything (" + st.currency + ")", paid) { paid = it }
        if (!taught) androidx.compose.material3.TextButton(onClick = {
            taught = true
            val fee = paid
            if (fee > 0) scope.io { G.dao.put(com.emotionalleaves.studioagent.data.Ledger(kind = "teaching", amount = fee, date = System.currentTimeMillis(), note = "Class: " + title.value.trim())) }
            val line = "\"" + title.value.trim() + "\", a beginner class" + (to?.let { ", " + it.venue.ifBlank { it.name } } ?: "") + (st.city.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")
            scope.io { G.dao.put(com.emotionalleaves.studioagent.data.Fact(category = "teaching", year = java.time.LocalDate.now().year, text = line)) }
        }) { Text("I taught it: add it to my CV") } else Small("On your CV, under Teaching and Talks" + (if (paid > 0) ", and the fee is in Records." else "."))
        if (places.isEmpty()) Small("Libraries and community centers near you are in Places near you; any you add with an email appear here.")
    }
}
