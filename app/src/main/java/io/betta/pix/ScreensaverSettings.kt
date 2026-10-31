package io.betta.pix

import android.content.Context
import kotlin.math.roundToInt

/**
 * The screensaver's timings, as chosen in Settings, and its brightness. Each timing offers a few values; [Choice] is
 * one setting's values, its default and how each reads.
 */
class ScreensaverSettings(context: Context) {
    class Choice(val key: String, val values: List<Long>, val default: Long, val describe: (Long) -> String)

    private val prefs = context.getSharedPreferences("screensaver", Context.MODE_PRIVATE)
    private val look = context.getSharedPreferences("look", Context.MODE_PRIVATE)

    /** How bright the screensaver is, from 0.2 to 1: up and down while it's on, and a row in Pix's settings. */
    var brightness: Float
        get() = look.getFloat("brightness", 0.7f)
        set(value) = look.edit().putFloat("brightness", value).apply()

    fun get(choice: Choice) = prefs.getLong(choice.key, choice.default).takeIf { it in choice.values } ?: choice.default

    fun set(choice: Choice, value: Long) = prefs.edit().putLong(choice.key, value).apply()

    /** How long each film stays. */
    val filmMillis get() = get(FILM)

    /** How long you have to guess before the title; the year comes halfway. */
    val guessMillis get() = get(GUESS)

    /** How long the title stays once shown; 0 keeps it until the next film. */
    val shownMillis get() = get(SHOWN)

    companion object {
        val FILM = Choice("film", listOf(30_000L, 60_000L, 120_000L, 300_000L), 60_000L, ::duration)
        val GUESS = Choice("guess", listOf(5_000L, 10_000L, 15_000L, 20_000L), 10_000L, ::duration)
        val SHOWN = Choice("shown", listOf(5_000L, 8_000L, 15_000L, 0L), 8_000L) { if (it == 0L) "Until the next film" else duration(it) }

        fun duration(millis: Long) = if (millis >= 60_000L) "${millis / 60_000L} min" else "${millis / 1000L} s"

        /** [brightness] [by] tenths up or down, kept to whole tenths from 20% to 100%. */
        fun stepBrightness(brightness: Float, by: Int) = ((brightness * 10).roundToInt() + by).coerceIn(2, 10) / 10f
    }
}

/** The value [step] places along from [current] in [values], wrapping round at either end. */
fun <T> cycle(values: List<T>, current: T, step: Int): T {
    val at = values.indexOf(current).takeIf { it >= 0 } ?: 0
    return values[Math.floorMod(at + step, values.size)]
}
