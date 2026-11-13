package com.emotionalleaves.studioagent.work

import android.content.Context
import android.graphics.Paint
import android.text.SpannableStringBuilder
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Settings
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.media.Pdf
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Every paper document the app produces, built from catalog and CV facts only. Nothing is invented.
 * They follow the conventions galleries expect: titles of works in italics, height before width,
 * inches with centimetres, the artist's name and page number on every page, and one file name pattern.
 */
object Papers {
    private fun out(ctx: Context, name: String) = File(ImageTools.exportDir(ctx), name)

    /** US Letter where it is the custom (the Americas' letter countries and the Philippines), A4 everywhere else. */
    fun paper(country: String = Locale.getDefault().country): Pair<Int, Int> =
        if (country.uppercase() in setOf("US", "CA", "MX", "PH", "CL", "CO", "VE", "GT", "CR", "PA", "DO", "PR", "SV", "NI", "HN", "BO")) 612 to 792 else 595 to 842

    private fun pdf(ctx: Context, s: Settings, title: String, margin: Int = 60, footer: Boolean = true): Pdf {
        val (w, h) = paper()
        return if (footer) Pdf(ctx, name(s), title, w, h, margin) else Pdf(ctx, "", "", w, h, margin)
    }

    private fun last(s: Settings) = Texts.clean(s.lastName).ifBlank { Texts.clean(s.firstName) }.ifBlank { "Artist" }

    /** Lastname_What.pdf, the one naming pattern for every document. */
    fun docName(s: Settings, what: String, ext: String = "pdf") = last(s) + "_" + what + "." + ext

    private fun name(s: Settings) = s.fullName.ifBlank { "Artist" }

    fun place(s: Settings): String = listOf(s.city, s.region).filter { it.isNotBlank() }.joinToString(", ")

    fun contactLine(s: Settings): String = listOf(s.email, s.phone, s.website).filter { it.isNotBlank() }.joinToString("    ")

    /** The lines that close every email and letter. */
    fun signature(s: Settings): String = listOf(name(s), s.website, s.email, s.phone, place(s)).filter { it.isNotBlank() }.joinToString("\n")

    fun medium(a: Artwork): String = when {
        a.medium.isNotBlank() -> a.medium
        a.format == "print" -> "Archival pigment print"
        a.format == "screen" -> "Digital video"
        else -> ""
    }

    fun edition(a: Artwork): String = if (a.editionSize > 0) "Edition of ${a.editionSize}" else ""

    /** A stable catalog number: initials, year, number. JL-2026-007. */
    fun inventoryNo(s: Settings, a: Artwork): String {
        val ini = (s.firstName.take(1) + s.lastName.take(1)).uppercase().filter { it.isLetter() }.ifBlank { "A" }
        val year = if (a.year > 0) a.year else if (a.createdAt > 0) Instant.ofEpochMilli(a.createdAt).atZone(ZoneId.systemDefault()).year else LocalDate.now().year
        return "$ini-$year-${"%03d".format(a.id)}"
    }

    private fun details(a: Artwork): List<String> =
        listOf(medium(a), Texts.dims(a.widthIn, a.heightIn, a.depthIn), if (a.framed) "framed" else "", edition(a)).filter { it.isNotBlank() }

    /** Plain caption for emails and forms: Title, year, medium, size, edition. */
    fun caption(a: Artwork): String =
        (listOf(a.title.ifBlank { "Untitled" }) + listOfNotNull(a.year.takeIf { it > 0 }?.toString()) + details(a)).joinToString(", ")

    /** The same caption with the title in italics, for documents. */
    fun caption(pdf: Pdf, a: Artwork): CharSequence = SpannableStringBuilder().apply {
        append(pdf.em(a.title.ifBlank { "Untitled" }))
        if (a.year > 0) append(", ${a.year}")
        details(a).forEach { append(", $it") }
    }

    fun money(s: Settings, v: Double): String {
        val f = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = if (v % 1.0 == 0.0) 0 else 2; minimumFractionDigits = if (v % 1.0 == 0.0) 0 else 2 }
        return s.currency + f.format(v)
    }

    fun priceLine(a: Artwork): String = priceLine(com.emotionalleaves.studioagent.G.settings, a)

    fun priceLine(s: Settings, a: Artwork): String = if (a.nfs) "Not for sale" else if (a.price > 0) money(s, a.price) else "Price on request"

    // ------------------------------------------------------------ CV

    fun cv(ctx: Context, s: Settings, facts: List<Fact>): File {
        val pdf = pdf(ctx, s, "Curriculum vitae")
        pdf.text(name(s), 26f, face = Pdf.Face.DISPLAY, gap = 4f)
        val about = listOfNotNull(s.born.takeIf { it.isNotBlank() }, place(s).takeIf { it.isNotBlank() }?.let { "Lives and works in $it" })
        if (about.isNotEmpty()) pdf.text(about.joinToString(". "), 10.5f, gap = 2f)
        contactLine(s).takeIf { it.isNotBlank() }?.let { pdf.text(it, 9.5f, grey = true, gap = 2f) }
        pdf.space(16f)
        val sections = Texts.cvSections(facts)
        if (sections.isEmpty()) pdf.text("No entries yet.", 10.5f, grey = true)
        sections.forEach { (heading, lines) ->
            pdf.keep(60f)
            pdf.text(heading, 12f, face = Pdf.Face.DISPLAY, gap = 4f)
            pdf.rule()
            lines.forEach { f -> pdf.hang(if (f.year > 0) f.year.toString() else "", f.text) }
            pdf.space(12f)
        }
        return pdf.save(out(ctx, docName(s, "CV")))
    }

    // ------------------------------------------------------------ portfolio

    fun portfolio(ctx: Context, s: Settings, works: List<Artwork>, statement: String, count: Int): File {
        val pdf = pdf(ctx, s, "Portfolio").apply { coverPage = true }
        val shown = works.take(count)
        // Cover: name, years, contact. Nothing else.
        pdf.space(220f)
        pdf.text(name(s), 34f, face = Pdf.Face.DISPLAY, gap = 8f)
        val years = shown.map { it.year }.filter { it > 0 }
        pdf.text("Selected works" + (if (years.isNotEmpty()) ", " + (if (years.min() == years.max()) "${years.min()}" else "${years.min()} to ${years.max()}") else ""), 13f, gap = 4f)
        place(s).takeIf { it.isNotBlank() }?.let { pdf.text(it, 11f, grey = true, gap = 2f) }
        contactLine(s).takeIf { it.isNotBlank() }?.let { pdf.text(it, 10f, grey = true) }
        if (statement.isNotBlank()) {
            pdf.newPage()
            pdf.text("Artist statement", 16f, face = Pdf.Face.DISPLAY, gap = 10f)
            pdf.text(statement, 11.5f, gap = 8f)
        }
        shown.forEachIndexed { i, a ->
            pdf.newPage()
            pdf.image(a.imagePath, 540f)
            pdf.space(4f)
            pdf.text(caption(pdf, a), 10.5f, gap = 2f)
            pdf.text("${i + 1} of ${shown.size}    ${inventoryNo(s, a)}", 8.5f, grey = true)
        }
        return pdf.save(out(ctx, docName(s, "Portfolio")))
    }

    /**
     * Your catalogue: every piece, two to a page, with its picture, full details, inventory number, edition, where it is,
     * and its history (shows it was in, where it sold). The record a gallery or an estate would ask for.
     */
    fun catalogue(ctx: Context, s: Settings, works: List<Artwork>, history: Map<Long, List<String>>): File {
        val pdf = pdf(ctx, s, "Catalogue")
        pdf.text("Catalogue of works", 24f, face = Pdf.Face.DISPLAY, gap = 4f)
        pdf.text(name(s) + ", " + LocalDate.now().year + ". " + works.size + (if (works.size == 1) " work." else " works."), 11f, grey = true, gap = 16f)
        works.forEachIndexed { i, a ->
            if (i > 0 && i % 2 == 0) pdf.newPage()
            if (a.imagePath.isNotBlank()) pdf.image(a.imagePath, 250f)
            pdf.text(caption(pdf, a), 11f, gap = 2f)
            pdf.text(listOfNotNull(inventoryNo(s, a),
                if (a.editionSize > 1) "${a.editionUsed} of ${a.editionSize} made" else null,
                if (a.nfs) "not for sale" else if (a.price > 0) money(s, a.price) else null,
                a.location.takeIf { it.isNotBlank() && it != "Studio" }?.let { "at $it" }
            ).joinToString(". ") + ".", 9.5f, grey = true, gap = 2f)
            history[a.id].orEmpty().forEach { pdf.text(it, 9.5f, gap = 1f) }
            pdf.space(14f)
            if (i % 2 == 0 && i < works.lastIndex) pdf.rule()
        }
        return pdf.save(out(ctx, docName(s, "Catalogue")))
    }

    // ------------------------------------------------------------ image list and price list

    /**
     * Numbered list of works. The numbers match the numbered image files in an application folder,
     * so a juror can match each picture to its details.
     */
    fun imageList(ctx: Context, s: Settings, works: List<Artwork>, withPrices: Boolean, dir: File? = null, files: List<String> = emptyList(), title: String = if (withPrices) "Price list" else "Image list"): File {
        val pdf = pdf(ctx, s, title)
        pdf.text(title, 20f, face = Pdf.Face.DISPLAY, gap = 2f)
        pdf.text(name(s), 11f, grey = true, gap = 14f)
        works.forEachIndexed { i, a ->
            pdf.keep(50f)
            pdf.hang("%02d".format(i + 1), caption(pdf, a), col = 36f, gap = 2f, right = if (withPrices) priceLine(s, a) else "")
            files.getOrNull(i)?.let { pdf.text(it, 8.5f, grey = true, indent = 36f, gap = 2f) }
            pdf.text(inventoryNo(s, a), 8.5f, grey = true, indent = 36f, gap = 10f)
        }
        return pdf.save(File(dir ?: ImageTools.exportDir(ctx), docName(s, title.replace(' ', '_'))))
    }

    // ------------------------------------------------------------ wall labels

    /** Eight labels to a page, two across, with light corner marks to cut along. */
    fun labels(ctx: Context, s: Settings, works: List<Artwork>): File {
        val pdf = pdf(ctx, s, "", margin = 36, footer = false)
        val cols = 2; val rows = 4
        val cw = pdf.width / cols.toFloat()
        val ch = (pdf.pageHeight - 72) / rows.toFloat()
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBDBEBA.toInt(); strokeWidth = 0.5f }
        // One small code in each label's corner: a visitor's phone opens your page, with prices and how to buy.
        val qr = s.website.takeIf { it.isNotBlank() }?.let { com.emotionalleaves.studioagent.media.Qr.bitmap(if (it.startsWith("http")) it else "https://$it", 240) }
        works.forEachIndexed { i, a ->
            if (i % (cols * rows) == 0) pdf.newPage()
            val c = pdf.canvas()
            val col = (i % (cols * rows)) % cols; val row = (i % (cols * rows)) / cols
            val x = pdf.left + col * cw; val y = 36f + row * ch
            // Corner marks only, so nothing shows once cut.
            val m = 8f
            listOf(x to y, x + cw to y, x to y + ch, x + cw to y + ch).forEach { (px, py) ->
                c.drawLine(px - m, py, px + m, py, guide); c.drawLine(px, py - m, px, py + m, guide)
            }
            val pad = 20f
            var ty = y + pad
            fun put(t: CharSequence, size: Float, face: Pdf.Face = Pdf.Face.BODY, grey: Boolean = false, gap: Float = 3f) {
                val l = pdf.layout(t, (cw - 2 * pad).toInt(), size, face, grey)
                c.save(); c.translate(x + pad, ty); l.draw(c); c.restore(); ty += l.height + gap
            }
            put(name(s), 12f, Pdf.Face.BOLD, gap = 6f)
            put(SpannableStringBuilder(pdf.em(a.title.ifBlank { "Untitled" })).append(if (a.year > 0) ", ${a.year}" else ""), 11.5f, gap = 6f)
            details(a).forEach { put(it, 9.5f) }
            if (!a.nfs && a.price > 0) { ty += 4f; put(money(s, a.price), 10.5f) }
            ty += 2f; put(inventoryNo(s, a), 7.5f, grey = true)
            qr?.let { q -> val side = 40f; c.drawBitmap(q, null, android.graphics.RectF(x + cw - pad - side, y + ch - pad - side, x + cw - pad, y + ch - pad), Paint()) }
        }
        return pdf.save(out(ctx, docName(s, "Wall_labels")))
    }

    // ------------------------------------------------------------ certificate

    fun certificate(ctx: Context, s: Settings, a: Artwork, number: Int): File {
        val pdf = pdf(ctx, s, "", margin = 72, footer = false)
        pdf.newPage()
        val c = pdf.canvas()
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF2A2C2E.toInt(); strokeWidth = 0.8f }
        c.drawRect(40f, 40f, pdf.pageWidth - 40f, pdf.pageHeight - 40f, frame)
        c.drawRect(46f, 46f, pdf.pageWidth - 46f, pdf.pageHeight - 46f, frame.apply { strokeWidth = 0.4f })
        pdf.space(6f)
        pdf.text("Certificate of Authenticity", 24f, face = Pdf.Face.DISPLAY, center = true, gap = 18f)
        if (pdf.image(a.imagePath, 230f)) pdf.space(10f)
        pdf.row("Artist", name(s))
        pdf.row("Title", pdf.em(a.title.ifBlank { "Untitled" }))
        if (a.year > 0) pdf.row("Year", a.year.toString())
        medium(a).takeIf { it.isNotBlank() }?.let { pdf.row("Medium", it) }
        Texts.dims(a.widthIn, a.heightIn, a.depthIn).takeIf { it.isNotBlank() }?.let { pdf.row("Dimensions", it) }
        pdf.row("Edition", if (a.editionSize > 0) "$number of ${a.editionSize}" else "Unique work")
        pdf.row("Catalog number", inventoryNo(s, a))
        pdf.space(14f)
        pdf.text("I, ${name(s)}, certify that the work described above is an original work of art made by me" +
            (if (a.editionSize > 0) ", and that this is number $number in an edition limited to ${a.editionSize}." else "."), 10.5f)
        pdf.text("The artist keeps the copyright and all reproduction rights to this work.", 10.5f, grey = true)
        pdf.space(40f)
        pdf.amount("Signature  ______________________________", "Date  ________________", 10.5f)
        // Where to reach the artist years from now, if the work changes hands.
        contactLine(s).takeIf { it.isNotBlank() }?.let { pdf.space(18f); pdf.text(it, 8.5f, grey = true, center = true) }
        return pdf.save(out(ctx, docName(s, "Certificate_" + Texts.clean(a.title).ifBlank { "Untitled" } + (if (a.editionSize > 0) "_$number" else ""))))
    }

    // ------------------------------------------------------------ bill of sale

    /** A receipt for a buyer: who sold what to whom, for how much, and that copyright stays with the artist. */
    fun billOfSale(ctx: Context, s: Settings, a: Artwork, buyer: String, price: Double, number: Int, date: Long = System.currentTimeMillis()): File {
        val pdf = pdf(ctx, s, "Bill of sale")
        val day = com.emotionalleaves.studioagent.ui.fmtDate(date)
        val year = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).year
        pdf.text("Bill of sale", 24f, face = Pdf.Face.DISPLAY, gap = 14f)
        pdf.row("Number", "$year-${"%03d".format(number)}")
        pdf.row("Date", day)
        pdf.space(8f)
        pdf.row("Seller", listOf(name(s), place(s), s.email, s.phone).filter { it.isNotBlank() }.joinToString("\n"))
        pdf.row("Buyer", buyer.ifBlank { "______________________________" })
        pdf.space(10f)
        pdf.rule(dark = true)
        pdf.amount(caption(pdf, a), money(s, price))
        pdf.text(inventoryNo(s, a) + (if (a.editionSize > 0 && a.editionUsed > 0) ", print ${a.editionUsed} of ${a.editionSize}" else ""), 8.5f, grey = true)
        pdf.rule()
        pdf.amount("Total", money(s, price), 12f, Pdf.Face.BOLD)
        pdf.space(6f)
        pdf.row("Paid by", "______________________________  (cash, card, transfer, check)")
        pdf.space(14f)
        pdf.text("Received in full. Ownership of the physical work passes to the buyer on this date. " +
            "The artist keeps the copyright and reproduction rights, and may show images of the work in a portfolio with credit to the collection.", 10f)
        pdf.space(36f)
        pdf.amount("Artist  ______________________________", "Buyer  ______________________", 10.5f)
        return pdf.save(out(ctx, docName(s, "Bill_of_sale_" + Texts.clean(a.title).ifBlank { "Untitled" })))
    }

    // ------------------------------------------------------------ proposal

    fun proposal(ctx: Context, s: Settings, venue: String, seriesName: String, statement: String, bio: String, works: List<Artwork>): File {
        val pdf = pdf(ctx, s, "Exhibition proposal")
        pdf.text("Exhibition proposal", 24f, face = Pdf.Face.DISPLAY, gap = 4f)
        pdf.text(listOf(name(s), venue.takeIf { it.isNotBlank() }?.let { "for $it" }).filterNotNull().joinToString(" "), 12f, gap = 2f)
        contactLine(s).takeIf { it.isNotBlank() }?.let { pdf.text(it, 9.5f, grey = true) }
        pdf.space(14f)
        if (seriesName.isNotBlank()) { pdf.row("Body of work", SpannableStringBuilder(pdf.em(seriesName)).append(", " + Texts.count(works.size, "work"))) }
        val wall = works.sumOf { if (it.widthIn > 0) it.widthIn + 12 else 0.0 }
        if (wall > 0) pdf.row("Wall needed", "About ${"%.0f".format(Math.ceil(wall / 12))} linear feet, hung in one row with 12 in between works")
        pdf.space(8f)
        if (statement.isNotBlank()) { pdf.keep(60f); pdf.text("About the work", 13f, face = Pdf.Face.DISPLAY, gap = 4f); pdf.text(statement, 11f, gap = 10f) }
        if (bio.isNotBlank()) { pdf.keep(60f); pdf.text("About the artist", 13f, face = Pdf.Face.DISPLAY, gap = 4f); pdf.text(bio, 11f, gap = 10f) }
        // How the body of work would hang: the whole series on one wall at real sizes.
        val sized = works.filter { it.imagePath.isNotBlank() && it.widthIn > 0 && it.heightIn > 0 }
        if (sized.size > 1) com.emotionalleaves.studioagent.media.Mockup.series(sized.map { Triple(it.imagePath, it.widthIn, it.heightIn) }, true, File(ctx.cacheDir, "proposal_wall.jpg"))?.let { f ->
            pdf.keep(60f); pdf.text("As it would hang", 13f, face = Pdf.Face.DISPLAY, gap = 4f); pdf.image(f.absolutePath, 260f)
        }
        pdf.keep(60f)
        pdf.text("Works", 13f, face = Pdf.Face.DISPLAY, gap = 6f)
        works.forEachIndexed { i, a -> pdf.hang("%02d".format(i + 1), caption(pdf, a), col = 30f) }
        works.take(8).forEachIndexed { i, a -> pdf.newPage(); pdf.image(a.imagePath, 540f); pdf.space(4f); pdf.hang("%02d".format(i + 1), caption(pdf, a), col = 30f) }
        return pdf.save(out(ctx, docName(s, "Proposal_" + Texts.clean(venue).ifBlank { "Venue" })))
    }

    /** A short document of plain text with a title, used for statements and answers inside an application. */
    fun textDoc(ctx: Context, s: Settings, title: String, sections: List<Pair<String, String>>, dir: File, file: String): File {
        val pdf = pdf(ctx, s, title)
        pdf.text(title, 20f, face = Pdf.Face.DISPLAY, gap = 2f)
        pdf.text(name(s), 11f, grey = true, gap = 14f)
        sections.forEach { (h, body) ->
            if (h.isNotBlank()) { pdf.keep(50f); pdf.text(h, 11.5f, face = Pdf.Face.BOLD, gap = 4f) }
            pdf.text(body, 11f, gap = 12f)
        }
        return pdf.save(File(dir, file))
    }

    /**
     * A plain loan and consignment agreement for showing work at a library, cafe or community space:
     * who, what (each work with its price), when, the venue's share of any sale, care and insurance, and pick-up.
     * Two signature lines. Not legal advice; it puts in writing what was agreed by email.
     */
    fun agreement(ctx: Context, s: Settings, venue: String, works: List<Artwork>, dates: String, commission: Double): File {
        val pdf = pdf(ctx, s, "Exhibition agreement")
        pdf.text("Exhibition agreement", 22f, face = Pdf.Face.DISPLAY, gap = 4f)
        pdf.text("Between ${name(s).ifBlank { "the artist" }} (the artist) and ${venue.ifBlank { "the venue" }} (the venue).", 11.5f, gap = 12f)
        fun clause(h: String, body: String) { pdf.keep(60f); pdf.text(h, 11.5f, face = Pdf.Face.BOLD, gap = 2f); pdf.text(body, 11f, gap = 10f) }
        clause("Dates", dates.ifBlank { "From ______________ to ______________." })
        clause("The works", "The artist lends the works listed below for display. They remain the artist's property until sold and paid for.")
        works.forEachIndexed { i, a -> pdf.text("${i + 1}. " + caption(a) + ". " + priceLine(s, a) + ".", 10.5f, gap = 3f) }
        pdf.space(8f)
        clause("Sales", if (commission > 0) "If a work sells, the venue keeps ${"%.0f".format(commission)}% of the price and pays the artist the rest within 30 days. Prices are set by the artist." else "If a work sells, the buyer pays the artist directly. Prices are set by the artist.")
        clause("Care", "The venue hangs the works where they are not in direct sun or near heat, and does not move or alter them without asking. The venue tells the artist at once of any damage or loss.")
        clause("Insurance", "The venue's insurance covers the works while on its premises: ______ yes  ______ no. If no, the artist accepts the risk.")
        clause("Copyright", "The artist keeps the copyright. The venue may use pictures of the works only to promote this show, with the artist's name.")
        clause("Hanging and pick-up", "Delivered and hung on ______________. Collected by the artist on ______________.")
        pdf.space(18f)
        pdf.text("Artist: ________________________________   Date: __________", 11f, gap = 18f)
        pdf.text("For the venue: __________________________   Date: __________", 11f, gap = 8f)
        contactLine(s).takeIf { it.isNotBlank() }?.let { pdf.text(it, 9.5f, grey = true) }
        return pdf.save(out(ctx, docName(s, "Agreement_" + venue.filter { it.isLetterOrDigit() }.take(24))))
    }

    /** A one-page show flyer: the strongest image, the title, where and when, and the opening. Plain, so it reads from across a hall. */
    fun flyer(ctx: Context, s: Settings, title: String, venue: String, dates: String, opening: String, image: Artwork?): File {
        val pdf = pdf(ctx, s, "Flyer", margin = 54, footer = false)
        image?.imagePath?.takeIf { it.isNotBlank() }?.let { pdf.image(it, pdf.pageHeight * 0.52f) }
        pdf.text(title, 30f, face = Pdf.Face.DISPLAY, gap = 4f)
        pdf.text(name(s), 16f, gap = 14f)
        if (venue.isNotBlank()) pdf.text(venue, 14f, face = Pdf.Face.BOLD, gap = 2f)
        if (dates.isNotBlank()) pdf.text(dates, 13f, gap = 10f)
        if (opening.isNotBlank()) pdf.text("Opening: $opening. Free, all welcome.", 13f, gap = 10f)
        image?.let { pdf.text(caption(it), 9f, grey = true, gap = 4f) }
        listOf(s.website, s.email).filter { it.isNotBlank() }.forEach { pdf.text(it, 10f, grey = true, gap = 2f) }
        // A phone pointed at it opens your page: the work, the dates, how to reach you.
        if (s.website.isNotBlank()) com.emotionalleaves.studioagent.media.Qr.bitmap(s.website.let { if (it.startsWith("http")) it else "https://$it" })?.let { pdf.space(6f); pdf.bitmap(it, 84f, left = true) }
        return pdf.save(out(ctx, docName(s, "Flyer_" + title.filter { it.isLetterOrDigit() }.take(30))))
    }

    /** A sheet for a clipboard at an opening or a table: name and email lines, and the one promise that makes people sign. */
    fun signupSheet(ctx: Context, s: Settings): File {
        val pdf = pdf(ctx, s, "Studio news", footer = false)
        pdf.text("Studio news from " + name(s).ifBlank { "the artist" }, 22f, face = Pdf.Face.DISPLAY, gap = 6f)
        pdf.text("Leave your name and email to hear about new work and shows, a few times a year. Nothing else, and never shared.", 12f, gap = 4f)
        if (s.website.isNotBlank()) pdf.text(s.website, 11f, grey = true, gap = 8f) else pdf.space(14f)
        // Or scan to write by email instead of on paper.
        if (s.email.isNotBlank()) com.emotionalleaves.studioagent.media.Qr.bitmap("mailto:${s.email}?subject=" + java.net.URLEncoder.encode("Please add me to your studio news", "UTF-8").replace("+", "%20"))?.let {
            pdf.bitmap(it, 72f, left = true); pdf.text("Or scan to send me an email and be added.", 9f, grey = true, gap = 12f)
        }
        pdf.text("Name                                                          Email", 10f, grey = true, gap = 4f)
        pdf.rule(dark = true)
        repeat(17) { pdf.space(20f); pdf.rule() }
        return pdf.save(out(ctx, docName(s, "Signup_sheet")))
    }

    // ------------------------------------------------------------ texts for other people

    /** Ready-to-send request to a fine-art print lab or framer. */
    fun printOrder(s: Settings, works: List<Artwork>): String = buildString {
        appendLine("Hello,")
        appendLine()
        appendLine("I would like a quote for the prints below, with a proof of each before the full run. " +
            "I have planned for archival cotton rag paper with a matte finish; please tell me if you recommend another paper for these files.")
        appendLine()
        works.forEachIndexed { i, a ->
            appendLine("${i + 1}. ${a.title.ifBlank { "Untitled" }}")
            appendLine("   Image size: ${Texts.dims(a.widthIn, a.heightIn).ifBlank { "to confirm" }}")
            if (a.pixelW > 0) appendLine("   File: ${a.pixelW} × ${a.pixelH} px (${Texts.ppi(a.pixelW, a.pixelH, a.widthIn, a.heightIn)} ppi at this size)")
            appendLine("   Quantity: 1" + (if (a.editionSize > 0) " (edition of ${a.editionSize})" else ""))
            if (a.framed) appendLine("   Framing: simple gallery frame, white mat, wired for hanging; outer size about ${Texts.dim(a.heightIn + 6)} × ${Texts.dim(a.widthIn + 6)} in")
        }
        appendLine()
        appendLine("Could you let me know the price and how long it would take?")
        appendLine()
        appendLine("Thank you,")
        appendLine()
        append(signature(s))
    }

    private fun cell(s: String): String = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""

    /** The whole catalog as a spreadsheet file: one row per piece, the columns a registrar would expect. */
    fun inventoryCsv(works: List<Artwork>, seriesNames: Map<Long, String>, s: Settings = com.emotionalleaves.studioagent.G.settings): String = buildString {
        appendLine("catalog_no,title,year,medium,height_in,width_in,depth_in,height_cm,width_cm,framed,edition,prints_made,series,status,price,location,tags")
        works.forEach { a ->
            appendLine(listOf(inventoryNo(s, a), cell(a.title), if (a.year > 0) a.year.toString() else "", cell(medium(a)),
                Texts.dim(a.heightIn), Texts.dim(a.widthIn), if (a.depthIn > 0) Texts.dim(a.depthIn) else "",
                "%.1f".format(a.heightIn * 2.54), "%.1f".format(a.widthIn * 2.54), if (a.framed) "yes" else "no",
                if (a.editionSize > 0) a.editionSize.toString() else "unique", a.editionUsed.toString(), cell(seriesNames[a.seriesId].orEmpty()),
                a.status, if (a.nfs) "NFS" else if (a.price > 0) "%.2f".format(a.price) else "", cell(a.location), cell(a.tags)).joinToString(","))
        }
    }

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A single static page: works, statement, CV, contact. No feeds, no trackers, no scripts. */
    fun portfolioHtml(s: Settings, works: List<Pair<Artwork, String>>, statement: String, bio: String, facts: List<Fact>,
                      upcoming: List<com.emotionalleaves.studioagent.data.Opp> = emptyList(), seriesNames: Map<Long, String> = emptyMap(),
                      installs: List<Pair<String, String>> = emptyList()): String = buildString {
        val n = escape(name(s))
        val desc = escape(bio.ifBlank { "Artworks by ${name(s)}" }.take(160))
        append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>$n</title><meta name=\"description\" content=\"$desc\"><meta property=\"og:title\" content=\"$n\"><meta property=\"og:description\" content=\"$desc\">")
        works.firstOrNull()?.let { append("<meta property=\"og:image\" content=\"img/${escape(it.second)}\">") }
        // So search engines know the page is an artist's own site.
        val person = org.json.JSONObject().put("@context", "https://schema.org").put("@type", "Person").put("name", name(s)).put("jobTitle", "Artist")
        if (s.website.isNotBlank()) person.put("url", s.website)
        if (s.city.isNotBlank()) person.put("address", org.json.JSONObject().put("@type", "PostalAddress").put("addressLocality", s.city).put("addressRegion", s.region))
        append("<script type=\"application/ld+json\">" + person.toString().replace("</", "<\\/") + "</script>")
        append("<style>")
        append(":root{--ink:#2A2C2E;--pencil:#63676B;--wall:#F2F2EF;--line:#DADBD6;--link:#2B4ACB}")
        append("*{box-sizing:border-box}body{margin:0;background:var(--wall);color:var(--ink);font:17px/1.55 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif}")
        append("header,main,footer{max-width:980px;margin:0 auto;padding:0 24px}header{padding-top:56px;padding-bottom:24px}")
        append("h1{font-size:clamp(2rem,6vw,3.2rem);line-height:1.05;margin:0 0 .4em;font-weight:650;letter-spacing:-.01em}")
        append("h2{font-size:1.25rem;margin:3.5rem 0 1rem;padding-bottom:.4rem;border-bottom:1px solid var(--line);font-weight:650}")
        append("nav a{margin-right:1.2em}a{color:var(--link)}a:focus-visible{outline:2px solid var(--link);outline-offset:2px}.lede{max-width:62ch;color:var(--pencil)}")
        append("figure{margin:0 0 64px}img{display:block;max-width:100%;max-height:86vh;height:auto;margin:0 auto;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.12)}")
        append("figcaption{margin-top:12px;font-size:.92rem;color:var(--pencil);text-align:center}figcaption em{color:var(--ink)}")
        append(".text{max-width:66ch}dl{display:grid;grid-template-columns:4.5em 1fr;gap:.35em 1em;margin:0 0 1.5em}dt{color:var(--pencil)}dd{margin:0}h3{font-size:1rem;margin:1.6em 0 .5em}")
        append("footer{padding-top:48px;padding-bottom:48px;color:var(--pencil);font-size:.85rem}h3.series{font-size:1.05rem;margin:2.5rem 0 1.5rem;color:var(--pencil);font-weight:600}ul.text{padding-left:1.1em}li{margin:.3em 0}")
        append("</style></head><body><header>")
        append("<h1>$n</h1>")
        if (bio.isNotBlank()) append("<p class=\"lede\">${escape(bio)}</p>")
        append("<nav><a href=\"#work\">Work</a>")
        if (statement.isNotBlank()) append("<a href=\"#statement\">Statement</a>")
        if (facts.isNotEmpty()) append("<a href=\"#cv\">CV</a>")
        append("<a href=\"#contact\">Contact</a></nav></header><main>")
        // Shows coming up go first: it is the first thing a curator or a friend looks for.
        if (upcoming.isNotEmpty()) {
            append("<h2 id=\"soon\">Coming up</h2><ul class=\"text\">")
            upcoming.forEach { o -> append("<li><strong>${escape(o.title)}</strong>" + listOf(o.venue, o.city).filter { it.isNotBlank() }.joinToString(", ").let { if (it.isBlank()) "" else ", " + escape(it) } +
                (if (o.exhibitionDates.isNotBlank()) ". " + escape(o.exhibitionDates) else "") + "</li>") }
            append("</ul>")
        }
        append("<h2 id=\"work\">Work</h2>")
        // Works hang in their series, the way a show would group them; loose pieces after.
        val groups = works.groupBy { it.first.seriesId }.toList().sortedBy { (id, _) -> if (id == 0L || seriesNames[id].isNullOrBlank()) 1 else 0 }
        groups.forEach { (sid, list) ->
            if (groups.size > 1) seriesNames[sid]?.takeIf { it.isNotBlank() }?.let { append("<h3 class=\"series\">${escape(it)}</h3>") } ?: if (sid == 0L && groups.size > 1) append("<h3 class=\"series\">Other work</h3>") else Unit
            list.forEach { (a, file) ->
                val cap = "<em>${escape(a.title.ifBlank { "Untitled" })}</em>" + (if (a.year > 0) ", ${a.year}" else "") + details(a).joinToString("") { ", " + escape(it) }
                append("<figure><img loading=\"lazy\" src=\"img/${escape(file)}\" alt=\"${escape(a.title.ifBlank { "Untitled" })} by $n\"><figcaption>$cap</figcaption></figure>")
            }
        }
        if (installs.isNotEmpty()) {
            append("<h2 id=\"shown\">Installation views</h2>")
            installs.forEach { (file, cap) -> append("<figure><img loading=\"lazy\" src=\"img/${escape(file)}\" alt=\"Installation view${if (cap.isNotBlank()) ", " + escape(cap) else ""}\"><figcaption>${escape(cap)}</figcaption></figure>") }
        }
        if (statement.isNotBlank()) append("<h2 id=\"statement\">Statement</h2><div class=\"text\">" + statement.split(Regex("\\n\\s*\\n")).joinToString("") { "<p>${escape(it.trim()).replace("\n", "<br>")}</p>" } + "</div>")
        val sections = Texts.cvSections(facts)
        if (sections.isNotEmpty()) {
            append("<h2 id=\"cv\">CV</h2><div class=\"text\">")
            sections.forEach { (h, list) -> append("<h3>${escape(h)}</h3><dl>"); list.forEach { append("<dt>${if (it.year > 0) it.year else ""}</dt><dd>${escape(it.text)}</dd>") }; append("</dl>") }
            append("</div>")
        }
        append("<h2 id=\"contact\">Contact</h2><p>")
        if (s.email.isNotBlank()) append("<a href=\"mailto:${escape(s.email)}\">${escape(s.email)}</a><br>")
        place(s).takeIf { it.isNotBlank() }?.let { append(escape(it)) }
        append("</p></main><footer>&copy; ${LocalDate.now().year} $n. All images are the artist's; please ask before using them.</footer></body></html>")
    }
}
