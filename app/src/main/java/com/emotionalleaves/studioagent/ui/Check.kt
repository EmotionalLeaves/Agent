package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.domain.Offers

/** Paste a message, invitation or offer: the signs of the usual schemes aimed at artists, named in plain words. */
@Composable
fun OfferCheck(nav: NavController) {
    // A flagged email arrives already pasted; it is shown once.
    val text = rememberSaveable { mutableStateOf(st.checkText.also { if (it.isNotBlank()) st.checkText = "" }) }
    Screen("Is this offer real?", nav) {
        Intro("check", "Paste an email or message: a buyer, a gallery invitation, a magazine feature, an award. It is checked on your phone for the signs of the usual schemes aimed at artists. Nothing is sent anywhere.")
        Banner("Check before you pay or reply", "Paste it in. It is checked on your phone for the signs of the usual scams.", key = 803L, height = 150)
        Field("Paste the message here", text, lines = 8)
        if (text.value.trim().length > 30) {
            val signs = Offers.check(text.value)
            Panel(strong = signs.any { it.serious }) {
                Text(Offers.verdict(signs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                signs.forEach { Text("• " + it.what, fontWeight = if (it.serious) FontWeight.SemiBold else FontWeight.Normal) }
            }
            Small("Two habits keep you safe from nearly all of it: never send money to receive money, and look the place up yourself (its own website, its street address, other artists who have shown there).")
        }
    }
}
