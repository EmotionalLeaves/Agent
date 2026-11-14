package com.emotionalleaves.studioagent.ui

import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.fillMaxWidth
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
        if (closed.isNotBlank()) {
            Panel(strong = true) { Text(closed, fontWeight = FontWeight.SemiBold) }
            // The next one, while today is fresh: the day and a packing evening two days before go on your to-dos.
            var next by rememberSaveable { mutableStateOf("") }
            if (next.isBlank()) DateField("Another market coming up? Its date", "") { d ->
                next = d; val day = java.time.LocalDate.parse(d); val place = where.trim().ifBlank { "the market" }
                scope.io {
                    G.dao.put(com.emotionalleaves.studioagent.data.Evt(type = "task", title = "Market day: $place", date = Engine.millis(day.atTime(9, 0))))
                    G.dao.put(com.emotionalleaves.studioagent.data.Evt(type = "task", title = "Pack for the market (Market day has the list)", date = Engine.millis(day.minusDays(2).atTime(18, 0))))
                }
            } else Small("On your to-dos: the market, and packing two evenings before.")
            return@Screen
        }
        val total = Market.total(tally, items, originals.associate { it.id to it.price })
        // The day's total, big, on a painted field: the number that matters at a table.
        Banner(money(total), "Today. " + Texts.count(tally.values.sum(), "sale") + (if (st.marketFee > 0) ", booth ${money(st.marketFee)}" else "") + ". Tap a tile when something sells.", key = 31L, height = 170)
        if (items.isEmpty() && originals.isEmpty()) Note("Add what you are bringing below: prints, cards, small pieces.")
        // What is on the table as big tiles, two across: the whole tile is the "sold one" button, the price big on it, how many are left under.
        val cs = MaterialTheme.colorScheme
        items.withIndex().toList().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (idx, i) -> key(i.name) {
                    val k = "m:" + i.name; val n = tally[k] ?: 0
                    val gone = i.qty > 0 && n >= i.qty
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (gone) cs.surfaceContainerHigh else neighbor(cs.primary, idx * 17f - 17f, 0.40f))
                            .clickable(enabled = !gone, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Sold one") { st.marketTally = Market.bump(st.marketTally, k, 1) }
                            .padding(14.dp)) {
                            val fg = if (gone) cs.onSurfaceVariant else androidx.compose.ui.graphics.Color.White
                            Text(i.name, Modifier.align(Alignment.TopStart), style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 2)
                            Text(money(i.price), Modifier.align(Alignment.BottomStart), style = MaterialTheme.typography.headlineSmall, color = fg)
                            if (n > 0) Box(Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                                Text("$n", style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color.Black)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (gone) "All gone" else if (i.qty > 0) "${i.qty - n} left" else "Tap per sale", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            if (n > 0) TextButton(onClick = { st.marketTally = Market.bump(st.marketTally, k, -1) }) { Text("Undo") }
                        }
                    }
                } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (originals.isNotEmpty()) {
            Section("Originals")
            // Each original as its picture; a sold one gets a red dot, the way galleries mark a sale on the wall label.
            originals.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { w -> key(w.id) {
                        val k = "w:${w.id}"; val sold = (tally[k] ?: 0) > 0
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(18.dp))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = if (sold) "Undo the sale" else "Sold") { st.marketTally = Market.bump(st.marketTally, k, if (sold) -1 else 1) }) {
                                if (w.imagePath.isNotBlank() && java.io.File(w.imagePath).exists()) PathImage(w.imagePath, Modifier.matchParentSize(), 400, ContentScale.Crop)
                                else ColorField(cs.primary, Modifier.matchParentSize(), key = w.id)
                                if (sold) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFFD32F2F)))
                            }
                            Text(w.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Small(money(w.price) + if (sold) ", sold. Tap again to undo" else ". Tap the picture when it sells")
                        }
                    } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        // The table itself: ticked as it goes in the bag, cleared for the next market when the day is closed.
        Expand("Packing for the table") {
            val bits = com.emotionalleaves.studioagent.domain.HangReady.bits(st.marketPacked, 1)
            Market.packing.forEachIndexed { i, t ->
                val on = bits and (1 shl i) != 0
                Row(Modifier.fillMaxWidth().toggleable(on, role = androidx.compose.ui.semantics.Role.Checkbox) { v -> st.marketPacked = com.emotionalleaves.studioagent.domain.HangReady.set(st.marketPacked, 1, i, v) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = null); Text(t.replace("about 50", "about ${st.currency}50"))
                }
            }
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
            Small("A mix sells best: something under 10, something under 30, and a few originals. Price everything, in plain view.")
        }
        // Everything to print for the table, in one folded place.
        Expand("Things to print: tags, postcards, a sign, cards, a zine") {
            if (items.isNotEmpty() || originals.isNotEmpty()) OutlinedButton(onClick = { scope.io {
                val f = com.emotionalleaves.studioagent.work.Papers.priceTags(ctx, st, items.map { it.name to it.price } + originals.map { it.title.ifBlank { "Untitled" } to it.price })
                withContext(Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
            } }) { Text("Price tags to print") }
            val cards = originals.filter { it.imagePath.isNotBlank() }.ifEmpty { works.filter { it.imagePath.isNotBlank() && it.status != "idea" }.take(8) }
            if (cards.isNotEmpty()) OutlinedButton(onClick = { scope.io {
                val f = com.emotionalleaves.studioagent.work.Papers.postcards(ctx, st, cards.take(8))
                withContext(Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
            } }) { Text("Postcards of your pieces to print") }
            // A sign for the table, with a code to pay by (a PayPal.me, Venmo or Cash App link of yours) if you add one.
            var pay by remember { mutableStateOf(st.payLink) }
            Field("Your payment link, for a code to scan (optional)", pay, { pay = it; st.payLink = it.trim() })
            OutlinedButton(onClick = { scope.io {
                val f = com.emotionalleaves.studioagent.work.Papers.tableSign(ctx, st, st.payLink)
                withContext(Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
            } }) { Text("A sign for the table") }
            OutlinedButton(onClick = { scope.io {
                val f = com.emotionalleaves.studioagent.work.Papers.businessCards(ctx, st, cards.firstOrNull())
                withContext(Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
            } }) { Text("Business cards, ten to a sheet") }
            // A zine of six pieces from one sheet: sold for a few dollars, or given away with a purchase.
            if (cards.size >= 3) OutlinedButton(onClick = { scope.io {
                val f = com.emotionalleaves.studioagent.work.Papers.zine(ctx, st, st.fullName.ifBlank { "Selected work" }, cards)
                withContext(Dispatchers.Main) { com.emotionalleaves.studioagent.sys.Share.view(ctx, f, "application/pdf") }
            } }) { Text("A little zine of your pieces (one sheet)") }
        }
        Field("Where (for the records)", where, { where = it })
        if (tally.isNotEmpty()) Button(onClick = {
            // What sold best, said before the tally is cleared: the one thing to bring more of next time.
            val best = tally.maxByOrNull { it.value }?.let { (k, n) -> (if (k.startsWith("m:")) k.removePrefix("m:") else originals.firstOrNull { "w:${it.id}" == k }?.title?.ifBlank { "an original" } ?: "an original") to n }
            scope.io { val t = Engine.closeMarket(where.trim()); st.marketPacked = ""; withContext(Dispatchers.Main) { closed = "Day closed: ${money(t)} recorded in Records." + (best?.takeIf { it.second > 1 }?.let { " Sold best: ${it.first} (${it.second}). Bring more of those next time." } ?: "") + " What is left over is kept for next time." } } }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Close the day") }
    }
}
