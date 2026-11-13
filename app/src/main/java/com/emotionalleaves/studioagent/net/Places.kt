package com.emotionalleaves.studioagent.net

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real places near you that show art, from OpenStreetMap: galleries, art centers, art shops, libraries, community centers.
 * Free and open, no key or account. Asked rarely (only when you tap Find), and identified, as the map's rules ask.
 */
object Places {
    data class Place(
        val name: String, val kind: String, val what: String, val website: String, val email: String, val phone: String,
        val address: String, val km: Double, val osm: String
    )

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private const val AGENT = "StudioAgent/1.0 (personal artist tool; finds local galleries on request)"
    var geocodeUrl = "https://nominatim.openstreetmap.org/search"
    var overpassUrl = "https://overpass-api.de/api/interpreter"

    /** The state the last located town is in, when the map knows it. */
    @Volatile var lastState: String = ""

    /** Latitude and longitude of a town, or null when it cannot be found. */
    fun locate(place: String): Pair<Double, Double>? {
        if (place.isBlank()) return null
        val url = "$geocodeUrl?format=json&limit=1&addressdetails=1&q=" + URLEncoder.encode(place, "UTF-8")
        val body = http.newCall(Request.Builder().url(url).header("User-Agent", AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) throw Exception("The map search returned ${r.code}")
            r.body?.string().orEmpty()
        }
        val a = JSONArray(body)
        if (a.length() == 0) return null
        val o = a.getJSONObject(0)
        lastState = o.optJSONObject("address")?.optString("state").orEmpty()
        return o.getString("lat").toDouble() to o.getString("lon").toDouble()
    }

    /** The map query: places that show or sell art, plus libraries and community centers, within [km] of a point. */
    fun query(lat: Double, lon: Double, km: Int, labs: Boolean = false): String {
        val r = km * 1000
        val around = "(around:$r,$lat,$lon)"
        // Where digital work becomes something that hangs: photo labs, printers and framers.
        if (labs) return """
            [out:json][timeout:40];
            (
              nwr["shop"="photo"]$around;
              nwr["craft"="photographic_laboratory"]$around;
              nwr["shop"="copyshop"]$around;
              nwr["craft"="printer"]$around;
              nwr["shop"="frame"]$around;
              nwr["craft"="framing"]$around;
            );
            out center tags 120;
        """.trimIndent()
        return """
            [out:json][timeout:40];
            (
              nwr["tourism"="gallery"]$around;
              nwr["amenity"="arts_centre"]$around;
              nwr["shop"="art"]$around;
              nwr["shop"="frame"]$around;
              nwr["amenity"="library"]$around;
              nwr["amenity"="community_centre"]$around;
              nwr["tourism"="museum"]["museum"="art"]$around;
            );
            out center tags 150;
        """.trimIndent()
    }

    /** Places near a point, nearest first, with names; places without a name are left out. */
    fun near(lat: Double, lon: Double, km: Int = 25, labs: Boolean = false): List<Place> {
        val body = http.newCall(Request.Builder().url(overpassUrl).header("User-Agent", AGENT)
            .post(FormBody.Builder().add("data", query(lat, lon, km, labs)).build()).build()).execute().use { r ->
            if (!r.isSuccessful) throw Exception("The map service returned ${r.code}. Try again in a few minutes.")
            r.body?.string().orEmpty()
        }
        return parse(body, lat, lon)
    }

    fun parse(json: String, lat: Double, lon: Double): List<Place> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = mutableListOf<Place>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val t = e.optJSONObject("tags") ?: continue
            val name = t.optString("name").trim()
            if (name.isBlank()) continue
            // "Arts centre" on the map also covers theaters, rehearsal rooms and music venues: not places that hang pictures.
            if (t.optString("amenity") == "arts_centre" && t.optString("tourism") != "gallery" &&
                Regex("theat(re|er)|rehearsal|dance|music|opera|playhouse|stage|ballet|symphony|cinema", RegexOption.IGNORE_CASE).containsMatchIn(name + " " + t.optString("arts_centre"))) continue
            val plat = if (e.has("lat")) e.optDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: continue
            val plon = if (e.has("lon")) e.optDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: continue
            val (kind, what) = when {
                t.optString("shop") == "photo" || t.optString("craft") == "photographic_laboratory" -> "lab" to "Photo lab"
                t.optString("shop") == "copyshop" || t.optString("craft") == "printer" -> "lab" to "Print shop"
                t.optString("craft") == "framing" -> "lab" to "Framer"
                t.optString("tourism") == "gallery" -> (if (t.optString("operator:type") in setOf("public", "community") || t.has("non_profit")) "nonprofit" else "commercial") to "Gallery"
                t.optString("tourism") == "museum" -> "nonprofit" to "Art museum"
                t.optString("amenity") == "arts_centre" -> "nonprofit" to "Art center"
                t.optString("shop") == "art" -> "commercial" to "Art shop"
                t.optString("shop") == "frame" -> "commercial" to "Framing shop (often shows local work)"
                t.optString("amenity") == "library" -> (if (t.optString("library") == "academic" || t.optString("operator").contains("Universit", true) || t.optString("operator").contains("College", true)) "campus" else "community") to "Library"
                else -> "community" to "Community center"
            }
            val address = listOf(listOf(t.optString("addr:housenumber"), t.optString("addr:street")).filter { it.isNotBlank() }.joinToString(" "), t.optString("addr:city"))
                .filter { it.isNotBlank() }.joinToString(", ")
            out += Place(name, kind, what, (t.optString("website").ifBlank { t.optString("contact:website") }).trim(),
                (t.optString("email").ifBlank { t.optString("contact:email") }).trim(), (t.optString("phone").ifBlank { t.optString("contact:phone") }).trim(),
                address, km(lat, lon, plat, plon), e.optString("type") + "/" + e.optLong("id"))
        }
        // Galleries and art centers first at each distance, and one entry per name.
        return out.distinctBy { it.name.lowercase() }.sortedWith(compareBy({ it.km.toInt() / 5 }, { if (it.what.startsWith("Gallery") || it.what.startsWith("Art")) 0 else 1 }, { it.km }))
    }

    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
