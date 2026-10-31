package io.betta.pix

import android.graphics.Bitmap

/**
 * A smooth blur of [pixels] (ARGB, [width] by [height]): a box blur of [radius] each way, three times over, which comes
 * out close to a Gaussian. The Shield's Android has no blur of its own, and shrinking a picture and stretching it back
 * leaves it blocky rather than soft.
 */
fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    var out = pixels
    repeat(3) {
        out = pass(out, width, height, radius, horizontal = true)
        out = pass(out, width, height, radius, horizontal = false)
    }
    return out
}

/** One way across: each pixel the average of the [radius] either side of it, the edge pixels repeated beyond the edge. */
private fun pass(source: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val out = IntArray(source.size)
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val span = 2 * radius + 1
    for (line in 0 until lines) {
        fun at(i: Int) = source[if (horizontal) line * width + i.coerceIn(0, length - 1) else i.coerceIn(0, length - 1) * width + line]
        var a = 0; var r = 0; var g = 0; var b = 0
        for (i in -radius..radius) at(i).let { a += it ushr 24; r += it shr 16 and 0xFF; g += it shr 8 and 0xFF; b += it and 0xFF }
        for (i in 0 until length) {
            // Rounded, not cut down: six passes of cutting down would darken the picture.
            val half = span / 2
            out[if (horizontal) line * width + i else i * width + line] =
                ((a + half) / span shl 24) or ((r + half) / span shl 16) or ((g + half) / span shl 8) or ((b + half) / span)
            val gone = at(i - radius)
            val come = at(i + radius + 1)
            a += (come ushr 24) - (gone ushr 24)
            r += (come shr 16 and 0xFF) - (gone shr 16 and 0xFF)
            g += (come shr 8 and 0xFF) - (gone shr 8 and 0xFF)
            b += (come and 0xFF) - (gone and 0xFF)
        }
    }
    return out
}

/** This picture blurred (see [boxBlur]), at its own size. */
fun Bitmap.blurred(radius: Int): Bitmap {
    val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    return Bitmap.createBitmap(boxBlur(pixels, width, height, radius), width, height, Bitmap.Config.ARGB_8888)
}
