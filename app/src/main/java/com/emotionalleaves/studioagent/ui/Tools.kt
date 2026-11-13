package com.emotionalleaves.studioagent.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Everything the app can do, grouped by what you are trying to get done, one line each.
 * For finding a tool you heard of without hunting through screens; the main tabs stay as they are.
 */
val toolGroups: List<Pair<String, List<Triple<String, String, String>>>> = listOf(
    "Making the work" to listOf(
        Triple("Start a session", "A timer, where you left off, and nothing else", "session"),
        Triple("Foundations", "Twelve weeks of short exercises, and a master to study", "foundations"),
        Triple("Your pieces and series", "Fresh eyes, planning a series, is it done", "works"),
        Triple("Your week", "Studio times that fit your shifts", "week"),
        Triple("The year ahead", "Where steady work leads, month by month", "year")
    ),
    "Showing it" to listOf(
        Triple("Open calls", "The ones that fit you this week; one tap to apply", "calls"),
        Triple("Places near you", "Galleries, libraries and cafes that show work", "nearby"),
        Triple("People and venues", "Emails written for you, visits, studio visits", "outreach"),
        Triple("Statement, bio and CV", "Written from your own answers", "materials"),
        Triple("Ask for feedback", "Which piece is strongest, from people you trust", "feedback"),
        Triple("Ready-made emails", "Short letters for galleries, labs, buyers, a yes", "letters")
    ),
    "Selling it" to listOf(
        Triple("Your prices", "A rule, the strays, and raising them", "pricing"),
        Triple("A piece made for someone", "A commission from quote to hand-over", "commission"),
        Triple("Market day", "A fair table: one tap a sale, the day's total", "market"),
        Triple("At the opening", "A code to scan, and selling a piece tonight", "opening")
    ),
    "Being seen" to listOf(
        Triple("When someone looks you up", "What jurors find online, and a profile line", "findable"),
        Triple("Studio news", "A short update to your email list", "update"),
        Triple("Is this offer real?", "Check a message for the usual schemes", "check")
    ),
    "Money and records" to listOf(
        Triple("Records", "Sales, costs, receipts, the year in a picture, taxes", "records"),
        Triple("Find anything", "Any piece, call or person, or ask a question", "search")
    )
)

@Composable
fun ToolsScreen(nav: NavController) {
    Screen("Everything the app can do", nav) {
        Intro("tools", "Every tool in one list, grouped by what you are trying to get done. Today still shows the one thing that matters now.")
        toolGroups.forEach { (group, tools) ->
            Section(group)
            tools.forEach { (name, what, route) ->
                val icon = when (group) { "Making the work" -> Icons.Default.Edit; "Showing it" -> Icons.Default.Person; "Selling it" -> Icons.Default.Star; "Being seen" -> Icons.Default.Search; else -> Icons.Default.AccountBox }
                IconRow(icon, name, what) { nav.navigate(route) }
            }
        }
    }
}
