package com.emotionalleaves.studioagent.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController

/** The words calls and galleries use, each in one plain sentence or two. */
val glossary: List<Pair<String, String>> = listOf(
    "Archival" to "Made to last without yellowing or fading: acid-free paper, lightfast inks and paints. Calls and buyers ask for it on prints.",
    "Artist proof (AP)" to "A few extra prints of an edition kept by the artist, marked AP instead of a number. Usually no more than a tenth of the edition.",
    "Artist statement" to "A short piece of writing about what your work is and why you make it, in your own plain words. Calls ask for 100 to 300 words.",
    "Bleed" to "Extra picture past the edge of a print, trimmed off after printing so no white line shows. Labs say how much they want, often an eighth of an inch.",
    "Body of work" to "A group of pieces that clearly belong together. Shows and many calls want one, not single pieces.",
    "Call for art (open call)" to "An invitation to send work to be considered for a show, a prize, a residency or a publication.",
    "Certificate of authenticity" to "A signed page saying who made a piece, when, its size and edition number. Prints are often sold with one; the app makes them.",
    "Color temperature (light)" to "How warm or cool a light is, in kelvin. Ordinary home bulbs are warm (about 2700K), so colors mixed under them look cooler and duller in daylight. A daylight bulb, 5000K with CRI 90 or more, keeps evening colors true.",
    "Commission (a piece)" to "A piece made to order for someone, usually with half paid up front.",
    "Commission (gallery)" to "The share of a sale the venue keeps, often 30 to 50 percent at galleries and less at libraries and cafes. Ask before you agree.",
    "Condition report" to "Photos and notes of a piece's condition before it leaves and when it returns, so damage can be shown.",
    "Consignment" to "The venue holds your work to sell and pays you after a sale; you still own it until then. Get it in writing.",
    "Curator" to "The person who chooses the work for a show and decides how it hangs. A juror chooses from entries; a curator often invites.",
    "CV (artist CV)" to "A list of your shows, education, awards and collections, newest first. Shorter and plainer than a job resume.",
    "Docent" to "A volunteer guide at a museum or gallery.",
    "Edition" to "A set number of prints of one image, each numbered (3/25). Once the edition is sold, no more are made.",
    "Emerging artist" to "Usually someone early in showing their work, often with few or no solo shows. Many calls are only for emerging artists, which helps you.",
    "Entry fee" to "What a call charges to consider your work. Many good calls are free; some charge 10 to 40.",
    "Gesso" to "A primer brushed on a canvas or board so paint grips and does not soak in.",
    "Giclée" to "A high-quality inkjet print on archival paper. Many print labs make them.",
    "Hanging fee / wall rental" to "Paying for a space on a wall. A sign of a pay-to-show venue rather than a juried show.",
    "Install / de-install" to "Putting the show up and taking it down.",
    "JPEG" to "The usual picture file. Small, and fine for juries and the web; it throws away a little detail each time it is saved again.",
    "Juried show" to "A show where a juror (or jurors) chooses the work from the entries. Getting in is a real CV line.",
    "Juror" to "The person who chooses the work for a juried show. Looking at what they make or show helps you choose what to send.",
    "Lightfast" to "A color that does not fade in light. Tubes and pens are rated; the top ratings are what to use for work you sell.",
    "Mat" to "The card border inside a frame that keeps the art off the glass.",
    "Mixed media" to "More than one material in one piece, like ink and collage, or paint over a print.",
    "NFS" to "Not for sale. Fine to use for a piece you want to keep showing.",
    "Open edition" to "Prints with no set limit on how many are made. Cheaper than a limited edition and not numbered.",
    "Opening (reception)" to "The first evening of a show, free and open to anyone. Where most of the meeting happens.",
    "Plein air" to "Painting or drawing outdoors, from life, in one sitting.",
    "Plexiglass (acrylic)" to "Clear plastic used instead of glass in frames that will be shipped, because it does not shatter.",
    "Portfolio" to "Your best pieces, usually 10 to 20, in a set order, as a PDF or a web page.",
    "PPI / DPI" to "Pixels (or dots) per inch: how many pixels of a picture go into each inch of a print. About 300 looks sharp up close; under 200 can look soft. Labs often say DPI when they mean PPI.",
    "Press release" to "A one-page announcement of a show for newspapers and listing sites: what, where, when, who, and one picture.",
    "Prospectus" to "The full rules of a call: dates, sizes, fees, what to send, what they take.",
    "Residency" to "Time and space somewhere else to make work, sometimes with a stipend.",
    "Retail price" to "What a buyer pays, the same everywhere you sell. A venue's commission comes out of it, so keep your prices the same online and at a gallery.",
    "Rights (image use)" to "Who may use pictures of your work and how. You keep copyright; a call may ask to use images to promote the show. Watch for calls that ask for more than that.",
    "Salon hang" to "Many pieces hung close together, floor to ceiling, rather than in a single line.",
    "Solo show / group show" to "A show of one artist's work, or of several artists together.",
    "Stipend / honorarium" to "A fixed payment for showing, speaking or taking part.",
    "TIFF" to "A picture file that keeps every pixel exactly, with nothing thrown away. Larger than a JPEG; many print labs prefer it.",
    "Underpainting" to "A first thin layer that sets up light and dark before the color goes on.",
    "Varnish" to "A clear coat over a finished painting that protects it and evens out the shine. Oil paintings wait months before varnishing; acrylics a few days.",
    "Wall label" to "The small card beside each piece: your name, the title, year, medium, size and price.",
    "Wire / D-rings" to "The hanging hardware on the back of a framed piece. Many venues say which they want.",
    "Zine" to "A small, cheap booklet of pictures or words, often made from one folded sheet. Artists sell them for a few dollars at markets; Market day makes one of your pieces."
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
