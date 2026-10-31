package io.betta.pix

import android.graphics.Color
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Your films with a backdrop, the better-rated ones far more likely to come first, each loaded only when shown.
 * One with black bars fails to load, so it's skipped; each that loads is kept in [cache]. When Plex can't be
 * reached, the films in [cache] instead.
 */
fun Plex.backdrops(cache: BackdropCache): List<Backdrops.Backdrop> {
    val server = try {
        server()
    } catch (e: Exception) {
        return cache.all().ifEmpty { throw e }
    }
    return weightedOrder(movies(server)).map { movie ->
        // The Plex app (2026.20) ignores plex://server links but opens its page for a watch.plex.tv one.
        lateinit var film: Backdrops.Backdrop
        film = Backdrops.Backdrop(movie.title, movie.year, movie.page) {
            val bitmap = traced("backdrop fetch") { backdrop(server, movie, BAKE_WIDTH) }
            check(!hasBlackBars(bitmap.width, bitmap.height) { x, y -> luminance(bitmap.getPixel(x, y)) }) { "${movie.title} has black bars" }
            traced("backdrop cache") { cache.save(movie.page?.substringAfterLast('/') ?: movie.art, film, bitmap) }
            bitmap
        }
        film
    }
}

/**
 * The largest power of two a [width]-wide backdrop can be decoded at a fraction of (inSampleSize) and still be at least
 * [maxWidth] wide, so the decoder does what shrinking it can.
 */
fun backdropSample(width: Int, maxWidth: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= maxWidth) sample *= 2
    return sample
}

/** A [width] by [height] backdrop's size once shrunk to no wider than [maxWidth], keeping its shape. */
fun shrunk(width: Int, height: Int, maxWidth: Int): Pair<Int, Int> =
    if (width <= maxWidth) width to height else maxWidth to (height.toLong() * maxWidth / width).toInt()

private fun luminance(pixel: Int) = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000

/**
 * A random order where each film's chance of coming early grows steeply with its rating: an 8 is about seven times
 * as likely as a 6 to come next. Films you've watched count half again, as they're the ones you can guess.
 * Unrated films count as a 6.
 */
fun weightedOrder(movies: List<Plex.Movie>, random: Random = Random): List<Plex.Movie> =
    movies.map { movie ->
        val weight = exp(movie.rating ?: 6.0) * (if (movie.watched) 1.5 else 1.0)
        movie to ln(random.nextDouble().coerceAtLeast(1e-12)) / weight
    }.sortedByDescending { it.second }.map { it.first }
