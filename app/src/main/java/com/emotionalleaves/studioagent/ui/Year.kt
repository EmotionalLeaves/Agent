package com.emotionalleaves.studioagent.ui

import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.G
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
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
        // The long view, big: how many pieces a year of steady work adds up to at your pace.
        val yearPieces = Math.round(pace * 52).toInt()
        Banner(if (pace > 0) "About $yearPieces pieces by this time next year" else "Twelve months, at your pace", "A plan that moves with you. A slow month costs nothing.", key = 91L, height = 160)
        Small(when {
            pace <= 0 -> "Once a piece is finished with its sessions logged, the app counts how many you will have by each month."
            pace >= 1.5 -> "At your recent pace, about ${"%.0f".format(pace)} pieces a week."
            pace >= 0.75 -> "At your recent pace, about one piece a week."
            else -> "At your recent pace, about one piece every ${Math.round(1 / pace)} weeks."
        })
        // One goal for this month, in your words; telling one friend makes it far likelier to happen.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val thisMonth = java.time.YearMonth.now().toString()
        var goal by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(if (st.monthGoalMonth == thisMonth) st.monthGoal else "") }
        Panel {
            Text("This month's one goal", fontWeight = FontWeight.SemiBold)
            Field("For example: finish two pieces, or enter one call", goal, { goal = it; st.monthGoal = it; st.monthGoalMonth = thisMonth }, mic = true)
            if (goal.isNotBlank()) androidx.compose.material3.TextButton(onClick = { com.emotionalleaves.studioagent.sys.Share.text(ctx, "My art goal for " + java.time.LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + ": " + goal.trim() + ". Ask me how it went at the end of the month?") }) { Text("Tell a friend") }
        }
        // The year so far, as pictures: every piece started this year, five across. Proof, not a score.
        val works by G.dao.artworks().collectAsState(emptyList())
        val yr = java.time.LocalDate.now().year
        val made = works.filter { (it.year == yr || (it.year == 0 && it.createdAt > 0 && Engine.date(it.createdAt).year == yr)) && it.status != "idea" }.sortedBy { it.createdAt }
        if (made.isNotEmpty()) {
            Section("Made in $yr: " + Texts.count(made.size, "piece"))
            made.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { w -> PathImage(w.imagePath, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)), 300, ContentScale.Crop, w.title.ifBlank { "Untitled" }) }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val p = plan
        if (p == null) { Loading(); return@Screen }
        Section("Month by month")
        // Each month on its own block: the month's short name big in a neighbor of your color, what it holds beside it.
        p.forEachIndexed { i, (ym, lines) ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.size(width = 58.dp, height = 58.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (i == 0) MaterialTheme.colorScheme.primary else neighbor(MaterialTheme.colorScheme.primary, i * 9f - 30f, 0.86f, 0.35f)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                    if (ym.year != yr) Text("${ym.year}", style = MaterialTheme.typography.labelSmall, color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (i == 0) Text("This month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
