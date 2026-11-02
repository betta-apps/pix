package io.betta.pix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

/**
 * Just enough of Plex for the screensaver: sign in by approving a code on your phone, find your own server, list the films
 * in its movie libraries (never one with "Private" in its name) and fetch a backdrop sized for the TV. Everything
 * here blocks, so call it off the main thread.
 */
class Plex(context: Context) {
    class Pin(val id: Long, val code: String)
    class Server(val uri: String, val token: String, val machineId: String)
    /**
     * A film or TV show. [rating] is out of 10: the audience rating Plex has for it, else the critics'; null when it
     * has neither. [page] is its page on watch.plex.tv, which the Plex app opens.
     */
    class Movie(val title: String, val year: Int?, val art: String, val rating: Double?, val watched: Boolean, val page: String? = null)

    private val prefs = context.getSharedPreferences("plex", Context.MODE_PRIVATE)
    // What Plex's sign-in page and your list of devices call this app.
    private val product = context.getString(R.string.app_name)
    private val clientId = prefs.getString("client", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("client", it).apply() }

    val signedIn get() = prefs.getString("token", null) != null

    fun signOut() {
        prefs.edit().remove("token").remove("server").apply()
        found = null
    }

    /**
     * [block] run against your server: the one already found while you've been signed in, until a request to it fails,
     * and only then looked for again ([server]: plex.tv's list, then which address answers) and [block] tried once more.
     */
    fun <T> withServer(block: (Server) -> T): T {
        val token = prefs.getString("token", null)
        found?.takeIf { it.first == token }?.second?.let { kept ->
            try {
                return block(kept)
            } catch (e: Exception) {
                // Gone, moved or no longer answering: looked for afresh below.
            }
        }
        return block(server())
    }

    /**
     * Your server, checked to answer before a change is sent to it: a change goes once, as one that timed out may still
     * have been made, so it's never run again by [withServer]'s retry.
     */
    fun liveServer(): Server = withServer { server -> server.also { check(reachable(it.uri, it.token)) { "Your Plex server isn't answering" } } }

    fun requestPin(): Pin {
        val pin = JSONObject(fetch("https://plex.tv/api/v2/pins?strong=true", method = "POST"))
        return Pin(pin.getLong("id"), pin.getString("code"))
    }

    /** Plex's sign-in page with this code filled in, for a QR code on the TV. */
    fun signInUrl(pin: Pin) =
        "https://app.plex.tv/auth#?clientID=$clientId&code=${pin.code}&context%5Bdevice%5D%5Bproduct%5D=${Uri.encode(product)}"

    /** True once you've approved the code; the token is then kept for next time. */
    fun claim(pin: Pin): Boolean {
        val result = JSONObject(fetch("https://plex.tv/api/v2/pins/${pin.id}"))
        if (result.isNull("authToken")) return false
        prefs.edit().putString("token", result.getString("authToken")).apply()
        return true
    }

    /**
     * Your own server, through the address that answered last time if it still does, else whichever answers first,
     * local ones before remote. Plex lists a server's Docker bridges too, which never answer from the TV, so the
     * addresses are tried all at once rather than waiting on each in turn.
     */
    fun server(): Server {
        val token = checkNotNull(prefs.getString("token", null)) { "Not signed in to Plex" }
        val resources = JSONArray(fetch("https://plex.tv/api/v2/resources?includeHttps=1", token))
        for (i in 0 until resources.length()) {
            val resource = resources.getJSONObject(i)
            if ("server" !in resource.optString("provides") || !resource.optBoolean("owned")) continue
            val serverToken = resource.optString("accessToken", token)
            val connections = resource.getJSONArray("connections")
            val (local, remote) = (0 until connections.length()).map { connections.getJSONObject(it) }
                .filterNot { it.optBoolean("relay") }
                .partition { it.optBoolean("local") }
                .let { (local, remote) -> local.map { it.getString("uri") } to remote.map { it.getString("uri") } }
            val last = prefs.getString("server", null)?.takeIf { it in local + remote && reachable(it, serverToken) }
            val uri = last
                ?: firstAnswering(local) { reachable(it, serverToken) }
                ?: firstAnswering(remote) { reachable(it, serverToken) }
            if (uri != null) {
                if (uri != last) prefs.edit().putString("server", uri).apply()
                return Server(uri, serverToken, resource.getString("clientIdentifier")).also { found = token to it }
            }
        }
        error("Couldn't reach your Plex server")
    }

    /** The films and TV shows in your libraries, never from one with "private" in its name. */
    fun movies(server: Server): List<Movie> {
        val sections = JSONObject(fetch("${server.uri}/library/sections", server.token))
            .getJSONObject("MediaContainer").optJSONArray("Directory") ?: JSONArray()
        return (0 until sections.length()).map { sections.getJSONObject(it) }
            .filter { it.optString("type") in KINDS && !it.optString("title").contains("private", ignoreCase = true) }
            .flatMap { section ->
                val kind = section.getString("type")
                val type = if (kind == "show") 2 else 1
                val items = JSONObject(fetch("${server.uri}/library/sections/${section.getString("key")}/all?type=$type", server.token))
                    .getJSONObject("MediaContainer").optJSONArray("Metadata") ?: JSONArray()
                (0 until items.length()).map { items.getJSONObject(it) }
                    .filter { it.has("art") }
                    .map {
                        Movie(
                            title = it.getString("title"),
                            year = it.optInt("year").takeIf { y -> y > 0 },
                            art = it.getString("art"),
                            rating = listOf("audienceRating", "rating").firstOrNull(it::has)?.let(it::getDouble),
                            // A show counts as watched once any episode has been.
                            watched = it.optInt(if (kind == "show") "viewedLeafCount" else "viewCount") > 0,
                            page = it.optString("slug").ifEmpty { null }?.let { slug -> "https://watch.plex.tv/$kind/$slug" },
                        )
                    }
            }
    }

    /**
     * The backdrop as Plex stores it (often 4K), not through Plex's resizer, which re-compresses everything at about
     * JPEG quality 75; shrunk here to no wider than [maxWidth], the most the renderer develops it at.
     */
    fun backdrop(server: Server, movie: Movie, maxWidth: Int): Bitmap {
        val connection = open("${server.uri}${movie.art}", server.token)
        try {
            check(connection.responseCode == 200) { "Plex answered ${connection.responseCode} for a backdrop" }
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
            val options = BitmapFactory.Options().apply { inSampleSize = backdropSample(bounds.outWidth, maxWidth) }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Plex sent a backdrop that isn't an image")
            val (width, height) = shrunk(decoded.width, decoded.height, maxWidth)
            if (width == decoded.width) return decoded
            return Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
        } finally {
            connection.disconnect()
        }
    }

    /** A picture from the server, scaled and cropped by Plex to fill [width] by [height], decoded at 1/[sample] of that. */
    fun image(server: Server, path: String, width: Int, height: Int, sample: Int = 1): Bitmap {
        val art = URLEncoder.encode(path, "UTF-8")
        val connection = open("${server.uri}/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=$art", server.token)
        try {
            check(connection.responseCode == 200) { "Plex answered ${connection.responseCode} for a backdrop" }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return connection.inputStream.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Plex sent a backdrop that isn't an image")
        } finally {
            connection.disconnect()
        }
    }

    private fun reachable(uri: String, token: String) = runCatching {
        val connection = open("$uri/identity", token, timeout = 3000).apply { readTimeout = 3000 }
        try { connection.responseCode == 200 } finally { connection.disconnect() }
    }.getOrDefault(false)

    internal fun fetch(url: String, token: String? = null, method: String = "GET"): String {
        val connection = open(url, token, method = method)
        try {
            check(connection.responseCode in 200..299) { "${URL(url).host} answered ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, token: String?, method: String = "GET", timeout: Int = 10_000) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeout
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Plex-Product", product)
            setRequestProperty("X-Plex-Version", "0.1.0")
            setRequestProperty("X-Plex-Device-Name", product)
            setRequestProperty("X-Plex-Client-Identifier", clientId)
            token?.let { setRequestProperty("X-Plex-Token", it) }
        }

    companion object {
        /** The library types it reads: films and TV shows. */
        val KINDS = setOf("movie", "show")

        // The server last found, and for which sign-in: one for the whole app, home screen and screensaver alike.
        @Volatile private var found: Pair<String, Server>? = null
    }
}

/** The first of [candidates] that [answers], asking them all at once so one that hangs holds up none of the others. */
fun firstAnswering(candidates: List<String>, answers: (String) -> Boolean): String? {
    if (candidates.isEmpty()) return null
    val pool = Executors.newFixedThreadPool(candidates.size)
    try {
        val done = ExecutorCompletionService<String?>(pool)
        candidates.forEach { candidate -> done.submit { candidate.takeIf(answers) } }
        repeat(candidates.size) { done.take().get()?.let { return it } }
        return null
    } finally {
        pool.shutdownNow()
    }
}
