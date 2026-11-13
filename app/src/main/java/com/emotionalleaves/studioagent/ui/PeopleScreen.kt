package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        People.places.forEach { p ->
            Panel {
                // The whole line is the switch, so a screen reader says what is being ticked.
                Row(androidx.compose.ui.Modifier.toggleable(p.key in been, role = androidx.compose.ui.semantics.Role.Checkbox) { on -> st.peopleTried = (if (on) been + p.key else been - p.key).joinToString(",") }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = p.key in been, onCheckedChange = null)
                    Text(p.name + if (p.key in been) " (been)" else "", fontWeight = FontWeight.SemiBold)
                }
                Text(p.what, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { Share.open(ctx, "https://www.google.com/search?q=" + URLEncoder.encode(People.query(p, st.city), "UTF-8")) }) { Text("Find one near " + st.city.ifBlank { "you" }) }
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
