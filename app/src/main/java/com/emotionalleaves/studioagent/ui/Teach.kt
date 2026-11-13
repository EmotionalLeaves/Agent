package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.fillMaxWidth
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
        Choice("What to teach", listOf("drawing", "watercolor", "painting", "printmaking", "collage", "phone drawing"), kind, names = { it.replaceFirstChar { c -> c.uppercase() } }) { kind = it }
        Field("Name of the class", title)
        Count("How long", minutes, 45, 180, 15, " min") { minutes = it }
        Count("How many people", people, 3, 25) { people = it }
        Section("The class")
        Panel {
            Workshop.outline(kind, minutes).forEach { p -> Text("${p.minutes} min", fontWeight = FontWeight.SemiBold); Text(p.what, style = MaterialTheme.typography.bodyMedium) }
        }
        Section("What to bring")
        Panel { Workshop.supplies(kind, people).forEach { Text("• $it") } }
        val (flat, per) = Workshop.fee(minutes, people)
        Small("A fair fee: about ${money(flat)} for the class (the setting up counts), or ${money(per)} a person when you charge at the door. Supplies are a cost to keep in Records.")
        Section("Offer it")
        if (places.isNotEmpty()) Choice("To", places.map { it.id.toString() }, venueId.toString(), names = { id -> places.firstOrNull { it.id.toString() == id }?.let { it.venue.ifBlank { it.name } } ?: "" }) { venueId = it.toLong() }
        val to = places.firstOrNull { it.id == venueId }
        val body = Workshop.email(st.fullName.ifBlank { "an artist" }, to?.let { it.venue.ifBlank { it.name } } ?: "", title.value.trim(), minutes, people, st.website, money(flat))
        Panel { Text(body, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = { Share.email(ctx, to?.email ?: "", "A beginner class: " + title.value.trim(), body) }, modifier = Modifier.fillMaxWidth()) { Text(if (to != null) "Send to ${to.venue.ifBlank { to.name }}" else "Write the email") }
        OutlinedButton(onClick = { Share.text(ctx, title.value.trim() + "\n\n" + Workshop.outline(kind, minutes).joinToString("\n") { "${it.minutes} min: ${it.what}" } + "\n\nBring:\n" + Workshop.supplies(kind, people).joinToString("\n") { "- $it" }) }, modifier = Modifier.fillMaxWidth()) { Text("Share the outline and list") }
        // Once it has happened: a line on the CV, under Teaching and Talks.
        var taught by rememberSaveable { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        if (!taught) androidx.compose.material3.TextButton(onClick = {
            taught = true
            val line = "\"" + title.value.trim() + "\", a beginner class" + (to?.let { ", " + it.venue.ifBlank { it.name } } ?: "") + (st.city.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")
            scope.io { G.dao.put(com.emotionalleaves.studioagent.data.Fact(category = "teaching", year = java.time.LocalDate.now().year, text = line)) }
        }) { Text("I taught it: add it to my CV") } else Small("On your CV, under Teaching and Talks.")
        if (places.isEmpty()) Small("Libraries and community centers near you are in Places near you; any you add with an email appear here.")
    }
}
