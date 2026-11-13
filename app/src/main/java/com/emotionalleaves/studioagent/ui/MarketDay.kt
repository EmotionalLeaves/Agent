package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Market
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.work.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A day at an art market or fair, on one screen: what you brought, one big tap per sale, the running total,
 * and at the end everything goes to Records in one go. Works offline; nothing is sent anywhere.
 */
@Composable
fun MarketDay(nav: NavController) {
    val scope = rememberCoroutineScope(); val ctx = androidx.compose.ui.platform.LocalContext.current
    val tick by st.changed.collectAsState()
    val works by G.dao.artworks().collectAsState(emptyList())
    val items = remember(tick) { Market.items(st.marketItems) }
    val tally = remember(tick) { Market.tally(st.marketTally) }
    val originals = works.filter { it.status in setOf("documented", "available", "finished") && !it.nfs && it.price > 0 }
    var where by rememberSaveable { mutableStateOf("") }
    var closed by remember { mutableStateOf("") }
    Screen("Market day", nav) {
        Intro("market", "For an art market, a fair table or an open studio: what you brought, one tap per sale, the running total. At the end of the day it all goes to Records in one go.")
        if (closed.isNotBlank()) { Panel(strong = true) { Text(closed, fontWeight = FontWeight.SemiBold) }; return@Screen }
        val total = Market.total(tally, items, originals.associate { it.id to it.price })
        Panel(strong = true) {
            Text("Today: " + money(total), style = MaterialTheme.typography.headlineMedium)
            Small(Texts.count(tally.values.sum(), "sale") + (if (st.marketFee > 0) ", booth ${money(st.marketFee)}" else "") + ". A tap adds one; a long list is fine.")
        }
        if (items.isEmpty() && originals.isEmpty()) Note("Add what you are bringing below: prints, cards, small pieces.")
        items.forEach { i -> key(i.name) {
            val k = "m:" + i.name; val n = tally[k] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Text(i.name, fontWeight = FontWeight.SemiBold); Small(money(i.price) + ", " + (i.qty - n).coerceAtLeast(0) + " left" + (if (n > 0) ", $n sold" else "")) }
                if (n > 0) TextButton(onClick = { st.marketTally = Market.bump(st.marketTally, k, -1) }) { Text("Undo") }
                FilledTonalButton(onClick = { st.marketTally = Market.bump(st.marketTally, k, 1) }, enabled = n < i.qty || i.qty == 0, modifier = Modifier.heightIn(min = 56.dp)) { Text("Sold one") }
            }
        } }
        if (originals.isNotEmpty()) {
            Section("Originals")
            originals.forEach { w -> key(w.id) {
                val k = "w:${w.id}"; val sold = (tally[k] ?: 0) > 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(w.title.ifBlank { "Untitled" }, fontWeight = FontWeight.SemiBold); Small(money(w.price) + if (sold) ", sold" else "") }
                    if (sold) TextButton(onClick = { st.marketTally = Market.bump(st.marketTally, k, -1) }) { Text("Undo") }
                    else FilledTonalButton(onClick = { st.marketTally = Market.bump(st.marketTally, k, 1) }) { Text("Sold") }
                }
            } }
        }
        Expand("What you are bringing", startOpen = items.isEmpty()) {
            items.forEachIndexed { idx, i -> key("edit" + i.name) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i.name}, ${money(i.price)}, ${i.qty} brought", Modifier.weight(1f))
                    TextButton(onClick = { st.marketItems = Market.itemsText(items.filterIndexed { j, _ -> j != idx }) }) { Text("Remove") }
                }
            } }
            if (items.isEmpty()) TextButton(onClick = { st.marketItems = Market.itemsText(Market.starter) }) { Text("Start from cards and two sizes of prints") }
            val name = rememberSaveable { mutableStateOf("") }; var price by rememberSaveable { mutableStateOf(0.0) }; var qty by rememberSaveable { mutableStateOf(0) }
            Field("What it is (for example: small prints)", name)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Num("Price, " + st.currency, price) { price = it } }
                Column(Modifier.weight(1f)) { Whole("How many", qty) { qty = it } }
            }
            // Prints of one of your pieces: each sold counts against its edition.
            val editions = works.filter { it.editionSize > 0 && it.status != "sold" }
            var forPiece by rememberSaveable { mutableStateOf(0L) }
            if (editions.isNotEmpty()) Choice("Prints of a piece (counts against its edition)", listOf("0") + editions.map { it.id.toString() }, forPiece.toString(),
                names = { i -> if (i == "0") "Not prints of a piece" else editions.firstOrNull { it.id.toString() == i }?.let { w -> w.title + " (${w.editionSize - w.editionUsed} of ${w.editionSize} left)" } ?: i }) { forPiece = it.toLongOrNull() ?: 0L; if (name.value.isBlank() && forPiece > 0) name.value = "Prints of " + (editions.firstOrNull { it.id == forPiece }?.title ?: "") }
            if (name.value.isNotBlank() && price > 0) OutlinedButton(onClick = { st.marketItems = Market.itemsText(items + Market.Item(name.value.trim().replace(",", " ").replace(":", " "), price, qty, forPiece)); name.value = ""; forPiece = 0L }) { Text("Add") }
            Num("Booth or table fee, " + st.currency, st.marketFee) { st.marketFee = it }
            if (items.isNotEmpty() || originals.isNotEmpty()) OutlinedButton(onClick = { scope.io {
                val f = com.emotionalleaves.studioagent.work.Papers.priceTags(ctx, st, items.map { it.name to it.price } + originals.map { it.title.ifBlank { "Untitled" } to it.price })
                withContext(Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
            } }) { Text("Price tags to print") }
            Small("A mix sells best: something under 10, something under 30, and a few originals. Price everything, in plain view.")
        }
        Field("Where (for the records)", where, { where = it })
        if (tally.isNotEmpty()) Button(onClick = { scope.io { val t = Engine.closeMarket(where.trim()); withContext(Dispatchers.Main) { closed = "Day closed: ${money(t)} recorded in Records. What is left over is kept for next time." } } }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Close the day") }
    }
}
