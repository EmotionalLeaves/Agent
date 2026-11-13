package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.domain.Pricing
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.work.Engine

/** All your prices in one place: the rule, what it gives each common size, the pieces that stray from it, and a raise. */
@Composable
fun PricingScreen(nav: NavController) {
    val scope = rememberCoroutineScope(); val dao = G.dao
    val works by dao.artworks().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val tick by st.changed.collectAsState()
    val rule = if (tick >= 0) st.pricePerSqIn else 0.0
    var raise by remember { mutableStateOf(10.0) }
    Screen("Your prices", nav) {
        Intro("pricing", "Buyers and galleries trust prices that hold together: the same size costs the same. Start from a simple rule, keep to it, and raise it when work sells.")
        Num("Your rule: price per square inch, " + st.currency, rule) { st.pricePerSqIn = it }
        Small("Paper and prints often start around 1 to 2 a square inch, paintings around 2 to 4. Add the frame's cost on top when it is framed.")
        Panel {
            Text("What your rule gives", fontWeight = FontWeight.SemiBold)
            Pricing.sizes.forEach { (w, h) -> Row { Text(Texts.dims(w, h), Modifier.weight(1f)); Text(money(Texts.price(w, h, rule))) } }
        }
        val strays = Pricing.strays(works, rule)
        if (strays.isNotEmpty()) {
            Section("Away from your rule")
            strays.forEach { (w, r) -> key(w.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(w.title.ifBlank { "Untitled" }); Small("${money(w.price)} now; the rule says ${money(r)} for ${Texts.dims(w.widthIn, w.heightIn)}") }
                    TextButton(onClick = { val old = w; scope.undoable("Price changed", { dao.put(w.copy(price = r)) }, { dao.put(old) }) }) { Text("Use ${money(r)}") }
                }
            } }
            Small("A piece can be priced differently on purpose (more work, a special one). These are only the ones to look at.")
        }
        val unpriced = works.filter { it.price <= 0 && !it.nfs && it.widthIn > 0 && it.heightIn > 0 && it.status !in setOf("idea", "in_progress", "sold") }
        if (unpriced.isNotEmpty()) OutlinedButton(onClick = {
            val old = unpriced
            scope.undoable("Prices set for ${Texts.count(old.size, "piece")}", { old.forEach { dao.put(it.copy(price = Texts.price(it.widthIn, it.heightIn, st.pricePerSqIn))) } }, { old.forEach { dao.put(it) } })
        }) { Text("Price the ${Texts.count(unpriced.size, "piece")} with no price, from the rule") }
        // Below cost after a gallery's share: the prints and frames already paid for, against 60 percent of the price.
        val underCost = works.filter { it.price > 0 && it.status != "sold" }.mapNotNull { w -> (-ledger.filter { it.artworkId == w.id && it.amount < 0 }.sumOf { it.amount }).takeIf { it > 0 && w.price * 0.6 < it }?.let { w to it } }
        if (underCost.isNotEmpty()) {
            Section("Below cost after a gallery's share")
            underCost.forEach { (w, spent) -> key("c" + w.id) {
                val fair = (kotlin.math.ceil(spent / 0.6 / 5.0) * 5.0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(w.title.ifBlank { "Untitled" }); Small("${money(spent)} spent on it; at ${money(w.price)} a 40 percent commission leaves ${money(w.price * 0.6)}.") }
                    TextButton(onClick = { val old = w; scope.undoable("Price changed", { dao.put(w.copy(price = fair)) }, { dao.put(old) }) }) { Text("Use ${money(fair)}") }
                }
            } }
        }
        // What your hours earn at these prices: not a rule for pricing art, but worth knowing.
        val sessions by dao.sessions().collectAsState(emptyList())
        val timed = works.filter { it.price > 0 && !it.nfs }.mapNotNull { w -> sessions.filter { it.artworkId == w.id && it.end > it.start }.sumOf { (it.end - it.start) / 3_600_000.0 }.takeIf { it >= 1 }?.let { h -> w to h } }
        if (timed.size >= 2) Panel {
            Text("What your hours earn", fontWeight = FontWeight.SemiBold)
            val perHour = timed.sumOf { it.first.price } / timed.sumOf { it.second }
            Text("About ${money(perHour)} an hour of making, across ${Texts.count(timed.size, "piece")} with sessions logged.")
            timed.sortedBy { it.first.price / it.second }.take(3).forEach { (w, h) -> Small("${w.title.ifBlank { "Untitled" }}: ${"%.0f".format(h)} hours, ${money(w.price / h)} an hour") }
            Small("Art is not priced by the hour, and early prices are low for everyone. But a piece that took three times as long as its neighbors may deserve a higher price, or a smaller size next time.")
        }
        Section("Raising prices")
        val year = java.time.LocalDate.now().year
        val sold = ledger.count { it.kind == "sale" && it.artworkId > 0 && Engine.date(it.date).year == year }
        Small(if (sold == 0) "Raise when work starts to sell: once more than half of what you show sells, 10 to 20 percent is usual. Never lower a price someone has paid." else "${Texts.count(sold, "piece")} sold this year. If more than half of what you showed sold, it is time.")
        Num("Raise by, percent", raise) { raise = it }
        val priced = works.filter { it.price > 0 && it.status != "sold" }
        if (priced.isNotEmpty() && raise > 0) OutlinedButton(onClick = {
            val old = priced; val pct = raise; val oldRule = st.pricePerSqIn
            scope.undoable("Prices raised ${"%.0f".format(pct)}%", {
                old.forEach { dao.put(it.copy(price = Pricing.raised(it.price, pct))) }
                st.pricePerSqIn = Math.round(oldRule * (1 + pct / 100) * 100) / 100.0
            }, { old.forEach { dao.put(it) }; st.pricePerSqIn = oldRule })
        }) { Text("Raise every unsold piece and the rule by ${"%.0f".format(raise)}%") }
    }
}
