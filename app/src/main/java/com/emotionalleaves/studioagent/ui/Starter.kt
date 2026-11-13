package com.emotionalleaves.studioagent.ui

import com.emotionalleaves.studioagent.data.Contact
import com.emotionalleaves.studioagent.data.Opp

/**
 * Starter drafts made without any AI key. They use only what you have told the app,
 * and leave [square brackets] where only you can say it. Nothing with a bracket left in can be sent.
 */
object Starter {
    fun sentences(t: String, n: Int): String =
        Regex("(?<=[.!?])\\s+").split(t.trim()).filter { it.isNotBlank() }.take(n).joinToString(" ")

    fun fit(titles: List<String>, o: Opp, statement: String): String {
        val list = when (titles.size) {
            0 -> ""
            1 -> "\"${titles[0]}\""
            else -> titles.dropLast(1).joinToString(", ") { "\"$it\"" } + " and \"${titles.last()}\""
        }
        val about = sentences(statement, 1)
        val theme = o.theme.ifBlank { o.title }.ifBlank { "this call" }
        return listOf(
            if (titles.size > 1) "$list belong together." else if (titles.size == 1) "$list is the piece I am sending." else "",
            about,
            "I chose ${if (titles.size > 1) "them" else "it"} for $theme because [one plain sentence on the link you see]."
        ).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun answer(statement: String): String = sentences(statement, 2).ifBlank { "[Your answer, in a sentence or two]" }

    fun email(c: Contact): Triple<String, String, String> {
        val me = listOf(st.firstName, st.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "[your name]" }
        val first = st.firstName.ifBlank { "[your name]" }
        val where = if (st.city.isNotBlank()) ", based in ${st.city}" else ""
        val make = st.mediums.ifBlank { "[what you make, in a few words]" }
        val hello = when { c.name.isNotBlank() -> "Dear ${c.name}"; c.venue.isNotBlank() -> "Dear ${c.venue} team"; else -> "Hello" }
        val sign = if (st.fullName.isBlank()) first else com.emotionalleaves.studioagent.work.Papers.signature(st)
        val body = "$hello,\n\nI am $me, an artist$where. I make $make.\n\n" +
            "[One sentence on something specific you saw at ${c.venue.ifBlank { "their space" }}, or why your work suits it.]\n\n" +
            (if (st.website.isNotBlank()) "You can see the work at ${st.website}.\n\n" else "") +
            "Would you be open to me sending a few images?\n\nKind regards,\n\n$sign"
        val follow = "$hello,\n\nI am following up once on my note from a few weeks ago about showing my work. If it is not a fit, no reply is needed.\n\nKind regards,\n\n$sign"
        return Triple("Introducing my work: $me", body, follow)
    }

    /** What can be read off a venue's page with no AI: an email address and a name from the web address. */
    fun venue(url: String, pageText: String, now: Long): Contact {
        val host = try { java.net.URI(url).host.orEmpty() } catch (e: Exception) { "" }.removePrefix("www.")
        val name = host.substringBefore('.').split('-', '_').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(pageText)?.value.orEmpty()
        return Contact(venue = name, email = email, website = url, kind = "community", notes = "Read from their website. Check the name and email.", createdAt = now)
    }
}
