package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The year ahead, a month at a time: where your pace takes you, what closes, what comes back, and your own dates. */
@Composable
fun YearScreen(nav: NavController) {
    val plan by produceState<List<Pair<java.time.YearMonth, List<String>>>?>(null) { value = withContext(Dispatchers.IO) { Engine.yearAhead() } }
    val pace by produceState(0.0) { value = withContext(Dispatchers.IO) { Engine.piecesPerWeek() } }
    Screen("The year ahead", nav) {
        Intro("year", "A long view, to see where steady work leads. Nothing here is a deadline: it moves with your real pace, and a slow month costs nothing.")
        Small(when {
            pace <= 0 -> "Once a piece is finished with its sessions logged, the app counts how many you will have by each month."
            pace >= 1.5 -> "At your recent pace, about ${"%.0f".format(pace)} pieces a week."
            pace >= 0.75 -> "At your recent pace, about one piece a week."
            else -> "At your recent pace, about one piece every ${Math.round(1 / pace)} weeks."
        })
        val p = plan
        if (p == null) { Loading(); return@Screen }
        p.forEachIndexed { i, (ym, lines) ->
            Panel(strong = i == 0) {
                Text(ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + (if (ym.year != java.time.LocalDate.now().year) " " + ym.year else "") + (if (i == 0) " (this month)" else ""),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
