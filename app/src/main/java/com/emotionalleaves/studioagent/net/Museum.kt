package com.emotionalleaves.studioagent.net

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Public-domain works from the Art Institute of Chicago's open collection (no account, no key), for studying
 * how others solved the problems you are working on. Only works marked public domain are used.
 */
object Museum {
    data class Piece(val id: Long, val title: String, val artist: String, val date: String, val medium: String, val imageId: String) {
        val imageUrl get() = "https://www.artic.edu/iiif/2/$imageId/full/843,/0/default.jpg"
        /** The sizes the museum's picture server documents, tried in turn. */
        val imageUrls get() = listOf("843,", "400,").map { "https://www.artic.edu/iiif/2/$imageId/full/$it/0/default.jpg" }
        val pageUrl get() = "https://www.artic.edu/artworks/$id"
        val credit get() = listOf(artist, date).filter { it.isNotBlank() }.joinToString(", ") + ". Art Institute of Chicago, public domain."
    }

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    /** What to search for, from how you work: the nearest traditional medium, so the examples are useful to copy from. */
    fun termFor(mediums: String): String {
        val m = mediums.lowercase()
        return listOf("gouache", "watercolor", "charcoal", "pastel", "etching", "woodcut", "ink", "pencil", "oil", "acrylic", "photograph").firstOrNull { m.contains(it.take(5)) }
            ?.let { if (it == "acrylic") "oil sketch" else it } ?: if (m.contains("digital") || m.contains("print")) "drawing" else "drawing"
    }

    fun parse(json: String): List<Piece> {
        val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val img = o.optString("image_id").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            if (!o.optBoolean("is_public_domain", false)) return@mapNotNull null
            Piece(o.optLong("id"), o.optString("title").ifBlank { "Untitled" }, o.optString("artist_title").takeIf { it != "null" }.orEmpty(),
                o.optString("date_display").takeIf { it != "null" }.orEmpty(), o.optString("medium_display").takeIf { it != "null" }.orEmpty(), img)
        }
    }

    fun search(term: String): List<Piece> {
        val url = "https://api.artic.edu/api/v1/artworks/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", term).addQueryParameter("limit", "100")
            .addQueryParameter("fields", "id,title,artist_title,date_display,medium_display,image_id,is_public_domain").build()
        val req = Request.Builder().url(url).header("User-Agent", Fetch.AGENT).header("AIC-User-Agent", Fetch.AGENT).build()
        return http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw Exception("The museum's collection did not answer (${r.code}).") else parse(r.body?.string().orEmpty()) }
    }

    fun download(p: Piece, dir: File): File {
        val f = File(dir.apply { mkdirs() }, "museum_${p.id}.jpg")
        if (f.length() > 0) return f
        // Asked openly, as this app; if the picture server says no, the work is opened on the museum's own page instead.
        var last = 0
        for (u in p.imageUrls) {
            val req = Request.Builder().url(u).header("User-Agent", Fetch.AGENT).header("AIC-User-Agent", Fetch.AGENT).build()
            val ok = http.newCall(req).execute().use { r -> last = r.code; if (r.isSuccessful) { f.outputStream().use { o -> r.body?.byteStream()?.copyTo(o) }; true } else false }
            if (ok && f.length() > 0) return f
        }
        throw Exception("The picture did not load ($last).")
    }
}
