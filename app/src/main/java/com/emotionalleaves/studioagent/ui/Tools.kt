package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.emotionalleaves.studioagent.domain.Texts
import androidx.navigation.NavController

/**
 * Everything the app can do, grouped by what you are trying to get done, one line each.
 * For finding a tool you heard of without hunting through screens; the main tabs stay as they are.
 */
val toolGroups: List<Pair<String, List<Triple<String, String, String>>>> = listOf(
    "Making the work" to listOf(
        Triple("Start a session", "A timer, where you left off, and nothing else", "session"),
        Triple("Empty your head", "Everything at once, one line each; it is sorted and put away", "dump"),
        Triple("Foundations", "Short weeknight exercises, a body of work, and a master to study", "foundations"),
        Triple("Your pieces and series", "Fresh eyes, planning a series, is it done", "works"),
        Triple("Photograph your pieces", "Every piece without a picture, one after another; flat ones can be scanned", "photoshoot"),
        Triple("Supplies", "What is running low, and a shopping list", "supplies"),
        Triple("Your week", "Studio times that fit your shifts, days off, extra shifts and breaks", "week"),
        Triple("The year ahead", "Where steady work leads, month by month", "year")
    ),
    "Showing it" to listOf(
        Triple("Open calls", "The ones that fit you this week; one tap to apply", "calls"),
        Triple("Places near you", "Galleries, libraries and cafes that show work", "nearby"),
        Triple("People and venues", "Emails written for you, visits, studio visits", "outreach"),
        Triple("Statement, bio and CV", "Written from your own answers", "materials"),
        Triple("Ask for feedback", "Which piece is strongest, from people you trust", "feedback"),
        Triple("Your people", "Life drawing, sketch groups, art leagues near you", "people"),
        Triple("Ready-made emails", "Short letters for galleries, labs, buyers, a yes", "letters")
    ),
    "Selling it" to listOf(
        Triple("Your prices", "A rule, the strays, and raising them", "pricing"),
        Triple("A piece made for someone", "A commission from quote to hand-over", "commission"),
        Triple("Market day", "A fair table: one tap a sale, price tags, postcards, a sign, a zine", "market"),
        Triple("Teach a class", "A beginner class at a library: outline, supplies, fee, the email", "teach"),
        Triple("At the opening", "A code to scan, and selling a piece tonight", "opening")
    ),
    "Being seen" to listOf(
        Triple("When someone looks you up", "What jurors find when they search your name", "findable"),
        Triple("Studio news", "A short update to your email list", "update"),
        Triple("Is this offer real?", "Check a message for the usual schemes", "check")
    ),
    "Money and records" to listOf(
        Triple("Records", "Sales, costs, receipts, the year in a picture, taxes", "records"),
        Triple("Find anything", "Any piece, call or person, or ask a question", "search"),
        Triple("Is everything working?", "The daily check, listing sites, helper, backups", "health"),
        Triple("Words calls and galleries use", "Juried, prospectus, consignment, giclée, in plain words", "glossary")
    )
)

@Composable
fun ToolsScreen(nav: NavController) {
    val tick by st.changed.collectAsState()
    // Groups start folded to one line each, so thirty tools never arrive at once; the ones you used lately sit on top.
    var open by rememberSaveable { mutableStateOf("") }
    Screen("Everything the app can do", nav) {
        Intro("tools", "Every tool, folded into five groups by what you are trying to get done. Open one group at a time. Today still shows the one thing that matters now.")
        val all = toolGroups.flatMap { it.second }
        val recent = if (tick >= 0) st.recentTools.split(",").mapNotNull { r -> all.firstOrNull { it.third == r } } else emptyList()
        fun go(route: String) { st.recentTools = (listOf(route) + st.recentTools.split(",").filter { it.isNotBlank() && it != route }).take(3).joinToString(","); nav.navigate(route) }
        if (recent.isNotEmpty()) {
            Section("Used lately")
            recent.forEach { (name, what, route) -> IconRow(Icons.Default.Refresh, name, what) { go(route) } }
        }
        // The five groups as painted tiles, two across, each in its own neighbor of your color. One tap opens a group below them.
        Section("Pick a group")
        val shifts = listOf(-30f, -10f, 12f, 30f, 48f)
        toolGroups.withIndex().toList().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (k, g) ->
                    val (group, tools) = g
                    val isOpen = open == group
                    Box(Modifier.weight(1f).height(124.dp).clip(RoundedCornerShape(22.dp))
                        .let { if (isOpen) it.border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(22.dp)) else it }
                        .clickable(role = Role.Button, onClickLabel = if (isOpen) "Fold" else "Open") { open = if (isOpen) "" else group }) {
                        ColorField(MaterialTheme.colorScheme.primary, Modifier.matchParentSize(), key = 200L + k, shift = shifts[k % shifts.size])
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Text(group, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2)
                            Text(Texts.count(tools.size, "tool"), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        toolGroups.firstOrNull { it.first == open }?.let { (group, tools) ->
            val icon = when (group) { "Making the work" -> Icons.Default.Edit; "Showing it" -> Icons.Default.Person; "Selling it" -> Icons.Default.Star; "Being seen" -> Icons.Default.Search; else -> Icons.Default.AccountBox }
            Section(group)
            tools.forEach { (name, what, route) -> IconRow(icon, name, what) { go(route) } }
        }
    }
}
