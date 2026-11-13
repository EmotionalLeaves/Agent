package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController

/** The words calls and galleries use, each in one plain sentence or two. */
val glossary: List<Pair<String, String>> = listOf(
    "Artist proof (AP)" to "A few extra prints of an edition kept by the artist, marked AP instead of a number. Usually no more than a tenth of the edition.",
    "Artist statement" to "A short piece of writing about what your work is and why you make it, in your own plain words. Calls ask for 100 to 300 words.",
    "Body of work" to "A group of pieces that clearly belong together. Shows and many calls want one, not single pieces.",
    "Call for art (open call)" to "An invitation to send work to be considered for a show, a prize, a residency or a publication.",
    "Color temperature (light)" to "How warm or cool a light is, in kelvin. Ordinary home bulbs are warm (about 2700K), so colors mixed under them look cooler and duller in daylight. A daylight bulb, 5000K with CRI 90 or more, keeps evening colors true.",
    "Commission (gallery)" to "The share of a sale the venue keeps, often 30 to 50 percent at galleries and less at libraries and cafes. Ask before you agree.",
    "Commission (a piece)" to "A piece made to order for someone, usually with half paid up front.",
    "Condition report" to "Photos and notes of a piece's condition before it leaves and when it returns, so damage can be shown.",
    "Consignment" to "The venue holds your work to sell and pays you after a sale; you still own it until then. Get it in writing.",
    "CV (artist CV)" to "A list of your shows, education, awards and collections, newest first. Shorter and plainer than a job resume.",
    "Docent" to "A volunteer guide at a museum or gallery.",
    "Edition" to "A set number of prints of one image, each numbered (3/25). Once the edition is sold, no more are made.",
    "Entry fee" to "What a call charges to consider your work. Many good calls are free; some charge 10 to 40.",
    "Giclée" to "A high-quality inkjet print on archival paper. Many print labs make them.",
    "Hanging fee / wall rental" to "Paying for a space on a wall. A sign of a pay-to-show venue rather than a juried show.",
    "Install / de-install" to "Putting the show up and taking it down.",
    "Juried show" to "A show where a juror (or jurors) chooses the work from the entries. Getting in is a real CV line.",
    "Juror" to "The person who chooses the work for a juried show. Looking at what they make or show helps you choose what to send.",
    "Mat" to "The card border inside a frame that keeps the art off the glass.",
    "NFS" to "Not for sale. Fine to use for a piece you want to keep showing.",
    "Opening (reception)" to "The first evening of a show, free and open to anyone. Where most of the meeting happens.",
    "Plexiglass (acrylic)" to "Clear plastic used instead of glass in frames that will be shipped, because it does not shatter.",
    "Portfolio" to "Your best pieces, usually 10 to 20, in a set order, as a PDF or a web page.",
    "Prospectus" to "The full rules of a call: dates, sizes, fees, what to send, what they take.",
    "Residency" to "Time and space somewhere else to make work, sometimes with a stipend.",
    "Salon hang" to "Many pieces hung close together, floor to ceiling, rather than in a single line.",
    "Solo show / group show" to "A show of one artist's work, or of several artists together.",
    "Stipend / honorarium" to "A fixed payment for showing, speaking or taking part.",
    "Wall label" to "The small card beside each piece: your name, the title, year, medium, size and price.",
    "Wire / D-rings" to "The hanging hardware on the back of a framed piece. Many venues say which they want."
)

@Composable
fun GlossaryScreen(nav: NavController) {
    val q = rememberSaveable { mutableStateOf("") }
    Screen("Words calls and galleries use", nav) {
        Intro("glossary", "The words you will meet in calls and at galleries, each in plain language.")
        Field("Find a word", q)
        val w = q.value.trim().lowercase()
        glossary.filter { w.isBlank() || it.first.lowercase().contains(w) || it.second.lowercase().contains(w) }.forEach { (term, meaning) ->
            Text(term, fontWeight = FontWeight.SemiBold); Small(meaning)
        }
    }
}
