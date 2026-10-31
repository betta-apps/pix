package io.betta.pix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import kotlin.math.ceil

/**
 * Pix's wordmark at [size] px: "pix" in Instrument Sans, and under it three yellow dashes, the guessing game's title
 * line. Each dash starts or ends at an edge of the letters (after the p's tail, at the i, at the end of the x's left
 * foot), and they sit level with the bottom of the p's tail. The positions are in ems, measured from the logo.
 */
class PixMark(context: Context, size: Float) : View(context) {
    private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Brand.font(context, 700)
        textSize = size
        letterSpacing = -0.035f
        color = Brand.INK
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YELLOW }
    private val word = Rect().also { letters.getTextBounds("pix", 0, 3, it) }
    private val tail = Rect().also { letters.getTextBounds("p", 0, 1, it) }

    init {
        contentDescription = "Pix"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(ceil(END * letters.textSize).toInt(), word.height())

    override fun onDraw(canvas: Canvas) {
        val em = letters.textSize
        val baseline = -word.top.toFloat()
        // Android puts half the letter spacing before each letter, where the logo (and CSS) put it all after.
        canvas.drawText("pix", -letters.letterSpacing / 2 * em, baseline, letters)
        val bottom = baseline + tail.bottom
        DASHES.forEach { (left, width) ->
            canvas.drawRoundRect(left * em, bottom - DASH * em, (left + width) * em, bottom, RADIUS * em, RADIUS * em, line)
        }
    }

    companion object {
        val YELLOW = Color.rgb(229, 160, 13)
        private val DASHES = listOf(0.253f to 0.321f, 0.644f to 0.348f, 1.062f to 0.311f)
        private const val DASH = 0.075f
        private const val RADIUS = 0.02f
        private const val END = 1.373f
    }
}
