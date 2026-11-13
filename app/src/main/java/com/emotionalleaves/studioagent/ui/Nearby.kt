package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.net.Places
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Papers

/** How far a place is, in the unit the phone's country uses. */
fun distanceWords(km: Double): String {
    val miles = java.util.Locale.getDefault().country in setOf("US", "GB", "LR", "MM")
    val v = if (miles) km * 0.621 else km
    val unit = if (miles) "mi" else "km"
    return if (v < 1) "under 1 $unit" else "%.0f $unit".format(v)
}

/** A place from the map, as a venue in People: what it is, how far, and the one thing to find out first. */
fun placeToContact(p: Places.Place, now: Long): Contact = Contact(
    venue = p.name, kind = p.kind, website = p.website.let { if (it.isBlank() || it.startsWith("http")) it else "https://$it" }, email = p.email,
    tier = if (p.kind in setOf("community", "campus", "nonprofit") && !p.what.startsWith("Art museum")) "now" else "stretch",
    notes = listOf(
        "${p.what}, ${distanceWords(p.km)} away." + (if (p.address.isNotBlank()) " ${p.address}." else "") + (if (p.phone.isNotBlank()) " Phone ${p.phone}." else ""),
        when {
            p.what.startsWith("Library") || p.what.startsWith("Community") -> "Many show local artists on a display wall or in cases. Ask at the desk who books it."
            p.what.startsWith("Framing") -> "Framers often hang local work and know every venue in town. Worth one visit with a print."
            p.what.startsWith("Art museum") -> "A long-term goal. Go to an opening or a talk; that is how people there get to know you."
            else -> "Look at their site or walls for local artists' shows, and whether they take submissions or have an annual open show."
        },
        "Found on OpenStreetMap."
    ).joinToString(" "),
    createdAt = now
)

@Composable
fun Nearby(nav: NavController, startWithLabs: Boolean = false) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val contacts by dao.contacts().collectAsState(emptyList())
    val works by dao.artworks().collectAsState(emptyList())
    // What a lab would be asked to print: digital pieces not yet printed and framed.
    val toPrint = works.filter { it.format == "print" && it.printStage < 5 && it.imagePath.isNotBlank() }.take(6)
    val town = rememberSaveable { mutableStateOf(listOf(st.city, st.region).filter { it.isNotBlank() }.joinToString(", ")) }
    var reach by rememberSaveable { mutableStateOf("25") }
    var labs by rememberSaveable { mutableStateOf(startWithLabs) }
    var found by remember { mutableStateOf<List<Places.Place>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val known = contacts.map { it.venue.lowercase().trim() }.toSet()
    Screen("Places near you", nav) {
        Intro("nearby", "Real galleries, art centers, libraries and community centers around you, from the free OpenStreetMap. Add the ones worth a visit to People; nothing is sent to anyone.")
        Choice("Looking for", listOf("show", "labs"), if (labs) "labs" else "show", names = { if (it == "show") "Places that show art" else "Printing and framing" }) { labs = it == "labs"; found = emptyList() }
        Field("Town or city", town)
        Choice("How far", listOf("10", "25", "50"), reach, names = { distanceWords(it.toDouble()) }) { reach = it }
        Button(enabled = !busy && town.value.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; status = "Looking on the map… this can take up to a minute."
            scope.io({ status = it; busy = false }) {
                val at = Places.locate(town.value.trim()) ?: throw Exception("That town was not found on the map. Try adding the state, like \"Springfield, Illinois\".")
                val list = Places.near(at.first, at.second, reach.toInt(), labs)
                found = list
                if (st.city.isBlank()) st.city = town.value.substringBefore(',').trim()
                status = if (list.isEmpty()) "Nothing marked on the map within ${distanceWords(reach.toDouble())}. Try further, or a bigger town nearby." else "${Texts.count(list.size, "place")} found, nearest first."
                busy = false
            }
        }) { Text(if (busy) "Looking…" else "Find places") }
        Status(status)
        val fresh = found.filter { it.name.lowercase().trim() !in known }
        val shows = fresh.filter { it.what.startsWith("Gallery") || it.what.startsWith("Art center") }
        if (!labs && shows.size > 1) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
            val now = now()
            scope.io { shows.forEachIndexed { i, p -> dao.put(placeToContact(p, now - i)) }; status = "Added ${Texts.count(shows.size, "gallery and art center", "galleries and art centers")} to People." }
        }) { Text("Add all ${shows.size} galleries and art centers") }
        found.forEach { p ->
            val added = p.name.lowercase().trim() in known
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Small(p.what + ", " + distanceWords(p.km) + (if (p.address.isNotBlank()) ". " + p.address else ""))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (added) Text("In People", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    else if (!labs) TextButton(onClick = { scope.io { dao.put(placeToContact(p, now())); status = "${p.name} added to People." } }) { Text("Add to People") }
                    // A lab is somewhere to send the print order to, not a venue: its email opens the order, ready to send.
                    if (labs && p.email.isNotBlank()) TextButton(onClick = { Share.email(ctx, p.email, "Print quote request", Papers.printOrder(st, toPrint)) }) { Text("Email them") }
                    if (labs && p.phone.isNotBlank()) TextButton(onClick = { Share.open(ctx, "tel:" + p.phone.filter { it.isDigit() || it == '+' }) }) { Text("Call") }
                    if (p.website.isNotBlank()) TextButton(onClick = { Share.open(ctx, p.website.let { if (it.startsWith("http")) it else "https://$it" }) }) { Text("Their site") }
                    TextButton(onClick = { Share.open(ctx, "geo:0,0?q=" + android.net.Uri.encode(p.name + if (p.address.isNotBlank()) ", " + p.address else ", " + town.value)) }) { Text("Map") }
                }
                Hair()
            }
        }
        if (found.isNotEmpty()) Small("Map data © OpenStreetMap contributors. Places are only what people have marked on the map, so the cafe with art on its walls may be missing: add those by hand in People.")
    }
}
