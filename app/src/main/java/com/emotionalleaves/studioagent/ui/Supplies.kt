package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.sys.Share

/** "name|1" per line: 1 when it is running low. */
fun supplies(stored: String): List<Pair<String, Boolean>> = stored.lines().filter { it.isNotBlank() }.map { it.substringBefore("|") to (it.substringAfter("|", "0") == "1") }
fun suppliesText(list: List<Pair<String, Boolean>>): String = list.joinToString("\n") { it.first.replace("|", "/") + "|" + (if (it.second) "1" else "0") }

/**
 * What you work with, and what is running low: tap a supply when it is nearly out, and the shopping list
 * is ready to send or take to the store. A missing tube of white should never be what stops a session.
 */
@Composable
fun SuppliesScreen(nav: NavController) {
    val ctx = LocalContext.current
    val tick by st.changed.collectAsState()
    val list = if (tick >= 0) supplies(st.supplies) else emptyList()
    Screen("Supplies", nav) {
        Intro("supplies", "Tap a supply when it is running low. The shopping list is ready whenever you pass an art store, and nothing runs out in the middle of a session.")
        val low = list.filter { it.second }
        if (low.isNotEmpty()) Panel(strong = true) {
            Text("Running low: " + low.joinToString(", ") { it.first }, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { Share.text(ctx, "Art supplies to get:\n" + low.joinToString("\n") { "- " + it.first }) }) { Text("Send the shopping list") }
            TextButton(onClick = { st.supplies = suppliesText(list.map { it.first to false }) }) { Text("Bought them all") }
        }
        list.forEachIndexed { i, (name, isLow) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Toggle(name + (if (isLow) " (low)" else ""), isLow) { on -> st.supplies = suppliesText(list.mapIndexed { j, p -> if (j == i) p.first to on else p }) }
            }
        }
        if (list.isEmpty()) TextButton(onClick = { st.supplies = suppliesText(listOf("Paper or canvas", "White paint", "Your main colors", "Brushes", "Pencils and erasers", "Fixative or varnish", "Tape").map { it to false }) }) { Text("Start from a common list") }
        val add = rememberSaveable { mutableStateOf("") }
        Field("Add a supply", add)
        if (add.value.isNotBlank()) OutlinedButton(onClick = { st.supplies = suppliesText(list + (add.value.trim() to false)); add.value = "" }) { Text("Add") }
        if (list.isNotEmpty()) TextButton(onClick = { st.supplies = suppliesText(list.filter { it.second }) }) { Text("Clear all but the low ones") }
    }
}
