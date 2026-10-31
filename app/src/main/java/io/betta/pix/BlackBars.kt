package io.betta.pix

/**
 * True when a picture has black bars: true black reaching in from the edges, down both sides (a 4:3 still in a 16:9
 * frame), across top and bottom (letterboxed), or a wide black panel on one side (a portrait poster on black).
 * Dark scenes still have some detail in every line, so they don't count. [luminance] gives 0–255 at a pixel.
 */
fun hasBlackBars(width: Int, height: Int, luminance: (x: Int, y: Int) -> Int): Boolean {
    fun blackColumn(x: Int) = (0 until height step 8).all { luminance(x, it) <= BLACK }
    fun blackRow(y: Int) = (0 until width step 8).all { luminance(it, y) <= BLACK }

    // How far in from an edge, as a percentage, every line is black.
    fun blackFrom(size: Int, black: (Int) -> Boolean, fromEnd: Boolean) =
        (0 until 100).firstOrNull { percent ->
            val line = size * percent / 100
            !black(if (fromEnd) size - 1 - line else line)
        } ?: 100

    fun barred(size: Int, black: (Int) -> Boolean): Boolean {
        val start = blackFrom(size, black, fromEnd = false)
        val end = blackFrom(size, black, fromEnd = true)
        if (start + end >= 100) return false
        return (start >= BOTH_SIDES && end >= BOTH_SIDES) || maxOf(start, end) >= ONE_SIDE
    }

    return barred(width, ::blackColumn) || barred(height, ::blackRow)
}

// Grain and compression lift true black a little; a dark scene's detail goes well above this somewhere in a line.
private const val BLACK = 12
// Percent of the frame: a little on both sides, or a wide panel on one.
private const val BOTH_SIDES = 3
private const val ONE_SIDE = 12
