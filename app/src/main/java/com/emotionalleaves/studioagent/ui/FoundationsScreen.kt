package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.domain.Foundations

/** Twelve weeks of foundations: the week you are on, open; the rest, one line each. Ticks happen by themselves after a practice session. */
@Composable
fun FoundationsScreen(nav: NavController) {
    val tick by st.changed.collectAsState()
    val stored = if (tick >= 0) st.foundationsDone else ""
    val done = Foundations.done(stored); val current = Foundations.current(stored)
    Screen("Foundations", nav) {
        Intro("foundations", "Twelve weeks of the basics every exhibiting artist leans on, sized for weeknights: three short exercises a week. A practice session picks the next one by itself and ticks it when you save the session. Doing them badly is the point.")
        Text(if (current == null) "All twelve weeks done. That is real groundwork." else "${done.size} of ${Foundations.weeks.sumOf { it.exercises.size }} exercises done. This week: ${current.skill}.", style = MaterialTheme.typography.bodyLarge)
        if (current != null) Button(onClick = { nav.navigate("session") }) { Text("Practice the next one") }
        Foundations.weeks.forEach { wk ->
            val all = wk.exercises.all { it.id in done }
            if (wk == current) Panel(strong = true) {
                Text("Week ${wk.n}: ${wk.skill}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(wk.why)
                wk.exercises.forEach { e -> Toggle(e.text + " (${e.minutes} min)", e.id in done) { on -> st.foundationsDone = Foundations.mark(st.foundationsDone, e.id, on) } }
                Small("To check it: open the drawing's photo in Fresh eyes and look at it ${wk.check.lowercase().let { if (it == "as it is") "as it is, next to what you painted from" else "with $it" }}.")
            } else Expand("Week ${wk.n}: ${wk.skill}" + (if (all) " (done)" else "")) {
                Text(wk.why)
                wk.exercises.forEach { e -> Toggle(e.text + " (${e.minutes} min)", e.id in done) { on -> st.foundationsDone = Foundations.mark(st.foundationsDone, e.id, on) } }
            }
        }
    }
}
