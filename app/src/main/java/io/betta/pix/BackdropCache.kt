package io.betta.pix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/**
 * The last [KEEP] films shown, kept on the Shield so the screensaver still has your films when Plex can't be
 * reached. Each is the backdrop as Plex sent it, plus its title, year and Plex link.
 */
class BackdropCache(context: Context) {
    private val dir = File(context.filesDir, "backdrops").apply { mkdirs() }

    fun save(name: String, backdrop: Backdrops.Backdrop, bitmap: Bitmap) {
        val safe = name.replace(Regex("[^A-Za-z0-9-]"), "_")
        // Kept at the size the screensaver develops it at, not a 4K original's.
        val kept = if (bitmap.width > KEEP_WIDTH) Bitmap.createScaledBitmap(bitmap, KEEP_WIDTH, bitmap.height * KEEP_WIDTH / bitmap.width, true) else bitmap
        File(dir, "$safe.jpg").outputStream().use { kept.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        File(dir, "$safe.json").writeText(
            JSONObject().put("title", backdrop.title).put("year", backdrop.year).put("link", backdrop.link).toString(),
        )
        dir.listFiles { file -> file.extension == "jpg" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(KEEP)
            .forEach { old -> old.delete(); File(dir, "${old.nameWithoutExtension}.json").delete() }
    }

    fun all(): List<Backdrops.Backdrop> =
        dir.listFiles { file -> file.extension == "json" }.orEmpty().mapNotNull { meta ->
            val image = File(dir, "${meta.nameWithoutExtension}.jpg").takeIf { it.exists() } ?: return@mapNotNull null
            keptFilm(meta.readText()) { BitmapFactory.decodeFile(image.path) }
        }.shuffled()

    /** The film kept most recently that still opens (one cut off while saving doesn't), at 1/[sample] of its size. */
    fun latest(sample: Int): Bitmap? =
        dir.listFiles { file -> file.extension == "jpg" }.orEmpty().sortedByDescending { it.lastModified() }
            .firstNotNullOfOrNull { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = sample }) }

    private companion object {
        const val KEEP = 40
        const val KEEP_WIDTH = 2688
    }
}

/**
 * A kept film from its details ([json]), loading its still with [load]; null for details that don't read, as when the
 * TV went off while saving them, so one such file can't keep the screensaver from starting.
 */
fun keptFilm(json: String, load: () -> Bitmap): Backdrops.Backdrop? = runCatching {
    val details = JSONObject(json)
    Backdrops.Backdrop(
        title = details.getString("title"),
        year = details.optInt("year").takeIf { it > 0 },
        link = details.optString("link").ifEmpty { null },
        load = load,
    )
}.getOrNull()
