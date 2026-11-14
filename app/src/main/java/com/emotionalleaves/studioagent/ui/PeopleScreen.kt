package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.domain.People
import com.emotionalleaves.studioagent.sys.Share
import java.net.URLEncoder

/** Your people: the usual places other artists gather, each one search away, and a hello for after. */
@Composable
fun PeopleScreen(nav: NavController) {
    val ctx = LocalContext.current
    val tick by st.changed.collectAsState()
    val been = if (tick >= 0) st.peopleTried.split(",").filter { it.isNotBlank() }.toSet() else emptySet()
    Screen("Your people", nav) {
        Intro("people", "Other artists pass on calls, say honestly which piece is strongest, and make starting easier just by working in the same room. One drop-in a month is plenty.")
        Banner("Your people", "Other artists pass on calls and make starting easier. One drop-in a month is plenty.", key = 823L, height = 150)
        People.places.forEach { p ->
            Panel {
                Text(p.name, fontWeight = FontWeight.SemiBold)
                Text(p.what, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { Share.open(ctx, "https://www.google.com/search?q=" + URLEncoder.encode(People.query(p, st.city), "UTF-8")) }, modifier = androidx.compose.ui.Modifier.offset(x = (-12).dp)) { Text("Find one near " + st.city.ifBlank { "you" }) }
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
                    // The whole "I've been" is the switch, so a screen reader says what is being ticked.
                    Row(androidx.compose.ui.Modifier.toggleable(p.key in been, role = androidx.compose.ui.semantics.Role.Checkbox) { on -> st.peopleTried = (if (on) been + p.key else been - p.key).joinToString(",") }.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = p.key in been, onCheckedChange = null); Text("I've been")
                    }
                }
                // Once is a visit; going again is how people start to know you. A reminder a week on, the same evening.
                if (p.key in been) {
                    var again by androidx.compose.runtime.saveable.rememberSaveable(p.key) { mutableStateOf(false) }
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    if (!again) TextButton(onClick = {
                        again = true
                        scope.io { com.emotionalleaves.studioagent.G.dao.put(com.emotionalleaves.studioagent.data.Evt(type = "task", title = "Go again: " + p.name.removePrefix("A ").removePrefix("An ").replaceFirstChar { it.uppercase() },
                            date = com.emotionalleaves.studioagent.work.Engine.millis(java.time.LocalDate.now().plusDays(7).atTime(17, 30)))) }
                    }, modifier = androidx.compose.ui.Modifier.offset(x = (-12).dp)) { Text("Remind me to go again next week") }
                    else Small("On your to-dos for a week from today.")
                }
            }
        }
        Section("After you meet someone")
        val them = rememberSaveable { mutableStateOf("") }
        val where = rememberSaveable { mutableStateOf("") }
        Field("Their name", them)
        Field("Where you met (Tuesday's life drawing)", where)
        val hello = People.hello(them.value.trim(), where.value.trim(), st.firstName)
        Panel { Text(hello, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = { Share.text(ctx, hello) }) { Text("Send it") }
    }
}
