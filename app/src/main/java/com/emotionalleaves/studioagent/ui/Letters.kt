package com.emotionalleaves.studioagent.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    Letter("Ask a question about a call", "When a flyer or call page leaves out the deadline, the fee or how to enter.", "A question about your call for artists",
        "Hello [THEM],\n\nI saw your call for artists at [PLACE] and would like to enter. Could you tell me the deadline, whether there is an entry fee, and how work should be submitted?\n\nThank you,\n\n[ME]"),
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
    Letter("Reply to someone who wants a piece made for them", "When a person asks for a commission. A clear quote now saves misunderstandings later.", "Your commission: a quote",
        "Hello [THEM],\n\nThank you for asking. I would be glad to make a piece for you. Here is how I usually work:\n\n" +
            "Size and medium: [for example, 16 × 20 in, archival pigment print, framed]\nPrice: [price], including [framing / delivery]\n" +
            "Deposit: half when we agree, the rest when it is finished and you are happy with it\nTime: about [number] weeks from the deposit\n" +
            "Along the way: I will send you one sketch or draft to approve before I finish.\n\nI keep the copyright, as artists usually do; the piece itself is yours. " +
            "If that all sounds right, reply and I will send the details for the deposit.\n\n[ME]"),
    Letter("Ask an artist you admire for a little advice", "Most artists remember being asked, and many say yes to fifteen minutes.", "A question from a newer artist",
        "Hello [THEM],\n\nI have admired your work for a while, especially [one piece or show]. I am a newer artist, making [what you make] around my day job, and working toward my first shows.\n\n" +
            "Would you have fifteen minutes, by phone or over coffee, for two or three questions about how you got started showing work? If not, I completely understand, and thank you for the work either way.\n\n[ME]\n[LINK]"),
    Letter("Sign up for a portfolio review", "Art centers, colleges and some galleries hold review days where you show work and get frank advice.", "Portfolio review",
        "Hello [THEM],\n\nI would like to sign up for a portfolio review at [PLACE] if there is a place open. I am an emerging artist working in [your medium]; my work is here: [LINK]\n\nCould you tell me the dates, any fee, and what to bring?\n\nThank you,\n\n[ME]"),
    Letter("Thank someone who bought your work", "The day after a sale. A buyer thanked is a buyer who comes back.", "Thank you",
        "Hello [THEM],\n\nThank you for giving my work a home. It means a great deal to me. If you ever send a photo of it where it hangs, I would love to see it, and I will let you know when I have new work or a show.\n\nWith thanks,\n\n[ME]"),
    Letter("New work, for someone who bought before", "Six months on. People who bought once are the likeliest to buy again.", "New work",
        "Hello [THEM],\n\nI hope [PLACE] is still hanging happily. I wanted you to be among the first to see some new work: [LINK]\n\nIf anything catches your eye, just reply.\n\nWarmly,\n\n[ME]"),
    Letter("Ask about payment for a sale", "If a sale has not been paid a few weeks after a show ends.", "Payment for sold work",
        "Hello [THEM],\n\nI understand a piece of mine sold during the show at [PLACE]. Could you let me know when I can expect payment and whether you need anything from me?\n\nThank you,\n\n[ME]"),
    Letter("Ask whether the entry fee can be waived", "When a call fits but the fee is a stretch. Many smaller organizers have a quiet waiver or a reduced fee; asking costs nothing.", "Entry fee question",
        "Hello [THEM],\n\nI'm an early-career artist and would love to enter the call at [PLACE]. Is there a fee waiver or a reduced fee for artists in my position? If not, I completely understand.\n\nThank you,\n\n[ME]"),
    Letter("Arrange to get work back", "When a show has ended and the pick-up details are not clear yet.", "Collecting my work",
        "Hello [THEM],\n\nThank you again for including my work at [PLACE]. Now that the show has closed, when would be a good time to collect the pieces that did not sell? I'm free most afternoons after 4 and on weekends.\n\nThank you,\n\n[ME]"),
    Letter("Tell an organizer you made a mistake on an entry", "Only for something that changes what they see or read: the wrong image, a wrong size or price, a missing file. A small typo is fine to leave; jurors do not notice it. Organizers get these emails all the time.", "A correction to my entry",
        "Hello [THEM],\n\nI sent my entry to [PLACE] and have noticed a mistake: [what was wrong]. Could you use the corrected version below (or attached) instead? I'm sorry for the extra step.\n\nThank you,\n\n[ME]")
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
            // Someone already saved in People fills all three boxes at once.
            val known by dao.contacts().collectAsState(emptyList())
            val named = known.filter { it.kind != "subscriber" && (it.venue.isNotBlank() || it.name.isNotBlank()) }
            if (named.isNotEmpty()) Pick("Fill in from People", listOf("") + named.map { it.id.toString() }, "",
                names = { id -> named.firstOrNull { it.id.toString() == id }?.let { listOf(it.name, it.venue).filter { x -> x.isNotBlank() }.joinToString(", ") } ?: "Choose someone" }) { id ->
                named.firstOrNull { it.id.toString() == id }?.let { them.value = it.name; place.value = it.venue; to.value = it.email }
            }
            Field("Their name (leave empty if you do not know it)", them)
            Field("The place (gallery, library, lab)", place)
            Field("Their email (optional)", to)
            Panel { Text(fillLetter(l.subject, them.value, place.value), fontWeight = FontWeight.SemiBold); Text(fillLetter(l.body, them.value, place.value)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Share.email(ctx, to.value.trim(), fillLetter(l.subject, them.value, place.value), fillLetter(l.body, them.value, place.value)) }) { Text("Open in my mail app") }
                // For a contact form or a message app instead of email.
                val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                OutlinedButton(onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(fillLetter(l.body, them.value, place.value))) }) { Text("Copy the text") }
                OutlinedButton(onClick = { picked = -1 }) { Text("Choose another letter") }
            }
        }
    }
}
