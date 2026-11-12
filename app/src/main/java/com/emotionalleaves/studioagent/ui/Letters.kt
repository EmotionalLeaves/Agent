package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.emotionalleaves.studioagent.sys.Share

/** A ready-made email for a common situation. [THEM], [PLACE] and [ME] are filled in from the boxes. */
data class Letter(val title: String, val whenToUse: String, val subject: String, val body: String)

val letters = listOf(
    Letter("Ask a print lab for a quote", "Before your first print order.", "Quote for fine-art prints",
        "Hello,\n\nI am an artist looking to have digital work printed as archival pigment prints on matte cotton rag paper. Could you tell me your price for one print at about 16 \u00D7 20 inches, how you would like the file supplied, and whether you offer a small proof first?\n\nThank you,\n\n[ME]"),
    Letter("Ask how staff can show work on campus", "Your first, lowest-pressure step toward a wall.", "Showing artwork as a staff member",
        "Hello [THEM],\n\nI work at the university and I am also an artist. Does [PLACE] ever show work by staff, or is there a staff or community exhibition I could enter? If there is someone else I should ask, I would be grateful for their name.\n\nThank you,\n\n[ME]"),
    Letter("Ask a library or cafe about showing work", "Community spaces often have a wall and a simple sign-up.", "Displaying artwork at [PLACE]",
        "Hello [THEM],\n\nI am a local artist. Does [PLACE] display work by local artists, and if so how do you choose it and how far ahead do you book? I can send a few images or a link.\n\nThank you,\n\n[ME]"),
    Letter("Say yes after being accepted", "Reply within a couple of days of an acceptance.", "Confirming my participation",
        "Hello [THEM],\n\nThank you. I am glad to confirm that I will take part. Could you confirm the delivery dates and times, and whether work needs to be wired for hanging?\n\nKind regards,\n\n[ME]"),
    Letter("Ask about delivery details", "When the acceptance email leaves something unclear.", "Delivery details",
        "Hello [THEM],\n\nA quick question about delivering my work to [PLACE]: which entrance should I use, during which hours, and is there anything I should bring besides the work itself?\n\nThank you,\n\n[ME]"),
    Letter("Withdraw an application", "If a piece sold or you cannot take part after all.", "Withdrawing my entry",
        "Hello [THEM],\n\nI need to withdraw my entry. I am sorry for any inconvenience, and thank you for your time. I hope to apply again in future.\n\nKind regards,\n\n[ME]"),
    Letter("Thank you after a show", "The week a show comes down. People remember it.", "Thank you",
        "Hello [THEM],\n\nThank you for including my work at [PLACE] and for all the effort that went into the show. I would be glad to hear about future calls.\n\nKind regards,\n\n[ME]"),
    Letter("Follow up after meeting someone at an opening", "Within a few days, while they remember you.", "Good to meet you at [PLACE]",
        "Hello [THEM],\n\nIt was good to meet you at [PLACE]. I enjoyed the show. If you would ever like to see what I make, there is a short portfolio here: [LINK]\n\nKind regards,\n\n[ME]"),
    Letter("Ask about payment for a sale", "If a sale has not been paid a few weeks after a show ends.", "Payment for sold work",
        "Hello [THEM],\n\nI understand a piece of mine sold during the show at [PLACE]. Could you let me know when I can expect payment and whether you need anything from me?\n\nThank you,\n\n[ME]")
)

fun fillLetter(text: String, them: String, place: String): String =
    text.replace("Hello [THEM],", if (them.isBlank()) "Hello," else "Dear ${them.trim()},").replace("[THEM]", them.trim().ifBlank { "there" }).replace("[PLACE]", place.trim().ifBlank { "your space" })
        .replace("[ME]", if (st.fullName.isBlank()) "(your name)" else com.emotionalleaves.studioagent.work.Papers.signature(st)).replace("[LINK]", st.website.ifBlank { "(your portfolio link)" })

@Composable
fun Letters(nav: NavController) {
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf(-1) }
    val them = rememberSaveable { mutableStateOf("") }
    val place = rememberSaveable { mutableStateOf("") }
    val to = rememberSaveable { mutableStateOf("") }
    Screen("Ready-made emails", nav) {
        Intro("letters", "Short, polite emails for moments when you do not know what to say. Pick one, fill in a name, and it opens in your mail app ready to send. Nothing here goes out by itself.")
        if (picked < 0) letters.forEachIndexed { i, l -> Panel(onClick = { picked = i }) { Text(l.title, fontWeight = FontWeight.SemiBold); Small(l.whenToUse) } }
        else {
            val l = letters[picked]
            Text(l.title, fontWeight = FontWeight.SemiBold)
            Field("Their name (leave empty if you do not know it)", them)
            Field("The place (gallery, library, lab)", place)
            Field("Their email (optional)", to)
            Panel { Text(fillLetter(l.subject, them.value, place.value), fontWeight = FontWeight.SemiBold); Text(fillLetter(l.body, them.value, place.value)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Share.email(ctx, to.value.trim(), fillLetter(l.subject, them.value, place.value), fillLetter(l.body, them.value, place.value)) }) { Text("Open in my mail app") }
                OutlinedButton(onClick = { picked = -1 }) { Text("Pick another") }
            }
        }
    }
}
