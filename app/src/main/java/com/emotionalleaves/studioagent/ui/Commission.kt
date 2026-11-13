package com.emotionalleaves.studioagent.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.G
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.sys.Share
import com.emotionalleaves.studioagent.work.Engine
import com.emotionalleaves.studioagent.work.Papers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Someone wants a piece made: who, what, how big, by when. The price comes from your own rule. */
@Composable
fun CommissionStart(nav: NavController) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var brief by rememberSaveable { mutableStateOf("") }
    var wIn by rememberSaveable { mutableStateOf(0.0) }
    var hIn by rememberSaveable { mutableStateOf(0.0) }
    var price by rememberSaveable { mutableStateOf(0.0) }
    var priceTouched by rememberSaveable { mutableStateOf(false) }
    var due by rememberSaveable { mutableStateOf(LocalDate.now().plusWeeks(6).toString()) }
    var busy by remember { mutableStateOf(false) }
    Screen("A piece made for someone", nav) {
        Intro("commission", "A commission, start to finish: the quote, the deposit, one draft to show them, the final payment and the hand-over, each on your week. The price starts from your own rule for the size.")
        Field("Their name", name, { name = it })
        Field("Their email (for the quote)", email, { email = it })
        Field("Or their phone", phone, { phone = it })
        Field("What they would like (subject, colors, where it will hang)", brief, { brief = it }, lines = 3, mic = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) { Num("Width, inches", wIn) { wIn = it; if (!priceTouched) price = Engine.commissionPrice(wIn, hIn) } }
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) { Num("Height, inches", hIn) { hIn = it; if (!priceTouched) price = Engine.commissionPrice(wIn, hIn) } }
        }
        androidx.compose.runtime.key(price) { Num("Price, " + st.currency, price) { price = it; priceTouched = true } }
        if (price > 0) Small("Your rule for the size plus about a quarter for the back-and-forth a commission takes. Half is asked as a deposit before you start.")
        DateField("Promised by", due) { due = it }
        val dueDay = try { LocalDate.parse(due) } catch (e: Exception) { LocalDate.now().plusWeeks(6) }
        Small("About " + Texts.count(maxOf(1, java.time.temporal.ChronoUnit.WEEKS.between(LocalDate.now(), dueDay).toInt()), "week") + ". Most people are happy to wait; promise later rather than sooner.")
        Button(enabled = !busy && name.isNotBlank(), onClick = {
            busy = true
            scope.io({ busy = false }) {
                val id = Engine.startCommission(name.trim(), email.trim(), phone.trim(), brief.trim(), wIn, hIn, price, dueDay)
                withContext(Dispatchers.Main) { busy = false; nav.navigate("work/$id") { popUpTo("commission") { inclusive = true } } }
            }
        }) { Text(if (name.isBlank()) "Add their name to start" else "Start the commission") }
    }
}

/** On a commissioned piece: who it is for, the money, and the six steps with the one action each needs. */
@Composable
fun CommissionPanel(nav: NavController, w: Artwork) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val dao = G.dao
    val contacts by dao.contacts().collectAsState(emptyList())
    val evts by dao.events().collectAsState(emptyList())
    val ledger by dao.ledger().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val client = contacts.firstOrNull { it.id == w.clientId }
    val steps = evts.filter { it.artworkId == w.id && it.type == "task" }.sortedBy { it.date }
    val paid = ledger.filter { it.artworkId == w.id && it.kind == "sale" }.sumOf { it.amount }
    Panel(strong = true) {
        Text("Made for " + (client?.name?.ifBlank { null } ?: "a client"), fontWeight = FontWeight.SemiBold)
        Small(listOfNotNull(if (w.price > 0) "Price " + money(w.price) else null, if (paid > 0) "paid " + money(paid) else "nothing paid yet",
            if (w.dueDate > 0) "promised by " + fmtDate(w.dueDate) else null).joinToString(", ") + ".")
        steps.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = e.done, onCheckedChange = { v -> scope.io { dao.put(e.copy(done = v)) } })
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) { Text(e.title); if (!e.done) Small(fmtDate(e.date)) }
                if (!e.done) when (e.title) {
                    "Send the quote" -> TextButton(onClick = {
                        val (subj, body) = Engine.commissionQuote(w, client?.name.orEmpty())
                        if (client?.email?.isNotBlank() == true) Share.email(ctx, client.email, subj, body) else Share.text(ctx, body)
                        scope.io { dao.put(e.copy(done = true)) }
                    }) { Text("Send") }
                    "Deposit received" -> TextButton(onClick = { scope.io { Engine.commissionPaid(w, deposit = true) } }) { Text("Paid") }
                    "Show them a draft" -> {
                        // The newest progress photo, sent with a short note asking for anything to change now rather than later.
                        val draft = sessions.filter { it.artworkId == w.id && it.photoPath.isNotBlank() && java.io.File(it.photoPath).exists() }.maxByOrNull { it.start }
                        if (draft != null) TextButton(onClick = {
                            val note = "Hi ${client?.name?.ifBlank { null } ?: "there"},\n\nHere is where your piece is now. Is there anything you would like changed while it is still easy to change? If it looks right to you, I will carry on and finish it.\n\n${st.firstName}"
                            Share.files(ctx, listOf(java.io.File(draft.photoPath)), "image/jpeg", note)
                            scope.io { G.dao.put(e.copy(done = true)) }
                        }) { Text("Send the draft") } else TextButton(onClick = { nav.navigate("session") }) { Text("Work on it") }
                    }
                    "Final payment received" -> TextButton(onClick = { scope.io { Engine.commissionPaid(w, deposit = false) } }) { Text("Paid") }
                    "Hand it over" -> TextButton(onClick = { scope.io { Engine.commissionDelivered(w) } }) { Text("Done") }
                    else -> {}
                }
            }
        }
        if (steps.all { it.done } && steps.isNotEmpty()) Note("Handed over and paid. It is on your CV as a private collection. A thank-you note and a photo of it in its new home make the next commission likelier.")
        if (w.status == "sold" || steps.any { it.title == "Final payment received" && it.done }) OutlinedButton(onClick = {
            scope.io { val n = dao.ledgerNow().count { it.kind == "sale" && Engine.date(it.date).year == LocalDate.now().year }.coerceAtLeast(1)
                val f = Papers.billOfSale(ctx, st, w, client?.name.orEmpty(), w.price, n); withContext(Dispatchers.Main) { Share.view(ctx, f, "application/pdf") } }
        }) { Text("Receipt for them (bill of sale)") }
    }
}
