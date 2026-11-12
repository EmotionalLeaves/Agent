package com.emotionalleaves.studioagent.work

import android.content.Context
import com.emotionalleaves.studioagent.data.Artwork
import com.emotionalleaves.studioagent.data.Fact
import com.emotionalleaves.studioagent.data.Settings
import com.emotionalleaves.studioagent.domain.Texts
import com.emotionalleaves.studioagent.media.ImageTools
import com.emotionalleaves.studioagent.media.Pdf
import java.io.File
import java.time.LocalDate

/** Every paper document the app produces. All built from catalog and CV facts only. */
object Papers {
    private fun out(ctx: Context, name: String) = File(ImageTools.exportDir(ctx), name)

    fun contactLine(s: Settings): String = listOf(s.city, s.email, s.website).filter { it.isNotBlank() }.joinToString("  |  ")

    fun medium(a: Artwork): String = when {
        a.medium.isNotBlank() -> a.medium
        a.format == "print" -> "Archival pigment print"
        a.format == "screen" -> "Digital video"
        else -> ""
    }

    fun caption(a: Artwork): String {
        val parts = mutableListOf(a.title.ifBlank { "Untitled" })
        if (a.year > 0) parts += a.year.toString()
        medium(a).takeIf { it.isNotBlank() }?.let { parts += it }
        Texts.dims(a.widthIn, a.heightIn, a.depthIn).takeIf { it.isNotBlank() }?.let { parts += it }
        if (a.editionSize > 0) parts += "Edition of ${a.editionSize}"
        return parts.joinToString(", ")
    }

    fun priceLine(a: Artwork): String = if (a.nfs) "NFS" else if (a.price > 0) "$" + "%.0f".format(a.price) else ""

    fun cv(ctx: Context, s: Settings, facts: List<Fact>): File {
        val pdf = Pdf()
        val sections = Texts.cv(s.fullName.ifBlank { "Artist" }, contactLine(s), facts)
        sections.forEachIndexed { i, (heading, lines) ->
            if (i == 0) { pdf.text(heading, 20f, bold = true, gap = 2f); lines.forEach { pdf.text(it, 10f, grey = true) }; pdf.space(10f) }
            else { pdf.text(heading.uppercase(), 10f, bold = true, gap = 4f); pdf.rule(); lines.forEach { pdf.text(it, 11f, gap = 3f) }; pdf.space(10f) }
        }
        return pdf.save(out(ctx, Texts.clean(s.lastName).ifBlank { "Artist" } + "_CV.pdf"))
    }

    fun portfolio(ctx: Context, s: Settings, works: List<Artwork>, statement: String, count: Int): File {
        val pdf = Pdf()
        pdf.text(s.fullName.ifBlank { "Artist" }, 22f, bold = true, gap = 2f)
        pdf.text(contactLine(s), 10f, grey = true)
        if (statement.isNotBlank()) { pdf.space(12f); pdf.text(statement, 11f) }
        works.take(count).forEach { a ->
            pdf.newPage()
            pdf.image(a.imagePath, 560f)
            pdf.text(caption(a), 10f)
        }
        return pdf.save(out(ctx, Texts.clean(s.lastName).ifBlank { "Artist" } + "_Portfolio_$count.pdf"))
    }

    fun imageList(ctx: Context, s: Settings, works: List<Artwork>, withPrices: Boolean, dir: File? = null): File {
        val pdf = Pdf()
        pdf.text(s.fullName.ifBlank { "Artist" } + ": image list", 16f, bold = true)
        works.forEachIndexed { i, a ->
            pdf.text("${i + 1}. ${caption(a)}" + (if (withPrices && priceLine(a).isNotBlank()) "  " + priceLine(a) else ""), 11f)
        }
        return pdf.save(File(dir ?: ImageTools.exportDir(ctx), Texts.clean(s.lastName).ifBlank { "Artist" } + "_ImageList.pdf"))
    }

    fun labels(ctx: Context, s: Settings, works: List<Artwork>): File {
        val pdf = Pdf()
        works.forEach { a ->
            pdf.text(s.fullName, 13f, bold = true, gap = 2f)
            pdf.text(a.title.ifBlank { "Untitled" } + (if (a.year > 0) ", ${a.year}" else ""), 12f, gap = 2f)
            pdf.text(listOf(medium(a), Texts.dims(a.widthIn, a.heightIn, a.depthIn), if (a.editionSize > 0) "Edition of ${a.editionSize}" else "").filter { it.isNotBlank() }.joinToString("\n"), 10f, gap = 2f)
            if (priceLine(a).isNotBlank()) pdf.text(priceLine(a), 11f)
            pdf.space(6f); pdf.rule(); pdf.space(6f)
        }
        return pdf.save(out(ctx, "Wall_labels.pdf"))
    }

    fun certificate(ctx: Context, s: Settings, a: Artwork, number: Int): File {
        val pdf = Pdf()
        pdf.text("Certificate of Authenticity", 20f, bold = true, gap = 14f)
        pdf.image(a.imagePath, 260f)
        pdf.space(8f)
        pdf.text("Artist: ${s.fullName}", 12f)
        pdf.text("Title: ${a.title.ifBlank { "Untitled" }}", 12f)
        if (a.year > 0) pdf.text("Year: ${a.year}", 12f)
        pdf.text("Medium: ${medium(a)}", 12f)
        Texts.dims(a.widthIn, a.heightIn).takeIf { it.isNotBlank() }?.let { pdf.text("Size: $it", 12f) }
        if (a.editionSize > 0) pdf.text("Edition: $number of ${a.editionSize}", 12f)
        pdf.space(20f)
        pdf.text("I certify that this is an original work made by me.", 11f)
        pdf.space(30f)
        pdf.text("Signed: ______________________________      Date: ${LocalDate.now()}", 11f)
        return pdf.save(out(ctx, "COA_" + Texts.clean(a.title).ifBlank { "Untitled" } + "_$number.pdf"))
    }

    fun proposal(ctx: Context, s: Settings, venue: String, seriesName: String, statement: String, bio: String, works: List<Artwork>): File {
        val pdf = Pdf()
        pdf.text("Exhibition proposal" + (if (venue.isNotBlank()) " for $venue" else ""), 18f, bold = true, gap = 2f)
        pdf.text(s.fullName + "  |  " + contactLine(s), 10f, grey = true)
        pdf.space(10f)
        if (seriesName.isNotBlank()) pdf.text("Proposed body of work: $seriesName (${works.size} works)", 12f, bold = true)
        if (statement.isNotBlank()) { pdf.text("About the work", 11f, bold = true, gap = 3f); pdf.text(statement) }
        if (bio.isNotBlank()) { pdf.space(6f); pdf.text("About the artist", 11f, bold = true, gap = 3f); pdf.text(bio) }
        pdf.space(6f)
        pdf.text("Works and space needed", 11f, bold = true, gap = 3f)
        val wall = works.sumOf { if (it.widthIn > 0) it.widthIn + 12 else 0.0 }
        if (wall > 0) pdf.text("Hung in one row with 12 in between works, the series needs about ${"%.0f".format(wall / 12)} linear feet of wall.")
        works.forEachIndexed { i, a -> pdf.text("${i + 1}. ${caption(a)}", 10f, gap = 2f) }
        works.take(6).forEach { a -> pdf.newPage(); pdf.image(a.imagePath, 560f); pdf.text(caption(a), 10f) }
        return pdf.save(out(ctx, "Proposal_" + Texts.clean(venue).ifBlank { "Venue" } + ".pdf"))
    }

    /** Ready-to-send text for a fine-art print lab and a framer. */
    fun printOrder(s: Settings, works: List<Artwork>): String = buildString {
        appendLine("Print order from ${s.fullName}")
        appendLine("Paper: archival cotton rag, matte (tell me if you recommend another for these files)")
        appendLine("Please confirm a proof before the full run.")
        appendLine()
        works.forEachIndexed { i, a ->
            appendLine("${i + 1}. ${a.title.ifBlank { "Untitled" }}")
            appendLine("   Image size: ${Texts.dims(a.widthIn, a.heightIn).ifBlank { "to confirm" }}")
            appendLine("   File: ${a.pixelW} x ${a.pixelH} px (${Texts.ppi(a.pixelW, a.pixelH, a.widthIn, a.heightIn)} ppi at this size)")
            appendLine("   Quantity: 1" + (if (a.editionSize > 0) " (edition of ${a.editionSize})" else ""))
            if (a.framed) appendLine("   Frame: simple gallery frame, white mat, wire for hanging. Outer size about ${Texts.dim(a.heightIn + 6)} x ${Texts.dim(a.widthIn + 6)} in")
        }
    }

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A single static page: works, statement, CV, contact. No feeds, no trackers. */
    fun portfolioHtml(s: Settings, works: List<Pair<Artwork, String>>, statement: String, bio: String, cvText: String): String = buildString {
        append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>${escape(s.fullName)}</title><style>")
        append("body{font-family:Georgia,serif;margin:0;color:#222;background:#faf9f6}main{max-width:900px;margin:0 auto;padding:24px}")
        append("h1{font-weight:normal;font-size:2em;margin:.5em 0}h2{font-weight:normal;font-size:1.2em;margin-top:2.5em;border-bottom:1px solid #ccc;padding-bottom:4px}")
        append("figure{margin:0 0 40px}img{max-width:100%;height:auto;display:block}figcaption{font-size:.9em;color:#555;margin-top:8px}pre{font-family:inherit;white-space:pre-wrap}a{color:#2f5d50}")
        append("</style></head><body><main>")
        append("<h1>${escape(s.fullName)}</h1>")
        if (bio.isNotBlank()) append("<p>${escape(bio)}</p>")
        append("<h2>Work</h2>")
        works.forEach { (a, file) -> append("<figure><img loading=\"lazy\" src=\"img/${escape(file)}\" alt=\"${escape(a.title)}\"><figcaption>${escape(caption(a))}</figcaption></figure>") }
        if (statement.isNotBlank()) append("<h2>Statement</h2><p>${escape(statement).replace("\n", "<br>")}</p>")
        if (cvText.isNotBlank()) append("<h2>CV</h2><pre>${escape(cvText)}</pre>")
        if (s.email.isNotBlank()) append("<h2>Contact</h2><p><a href=\"mailto:${escape(s.email)}\">${escape(s.email)}</a></p>")
        append("</main></body></html>")
    }
}
