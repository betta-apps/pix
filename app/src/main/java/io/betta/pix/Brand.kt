package io.betta.pix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** The look outside the pictures: its colours, Instrument Sans (bundled, OFL) and the logo's ring. */
object Brand {
    val INK = Color.rgb(243, 237, 226)
    val INK_DARK = Color.rgb(42, 35, 29)
    val AMBER = Color.rgb(227, 154, 82)
    val GROUND = Color.rgb(12, 9, 8)
    val GROUND_LIGHT = Color.rgb(36, 27, 21)

    private val faces = mutableMapOf<Int, Typeface>()

    /** Instrument Sans at [weight] (400 to 700). */
    fun font(context: Context, weight: Int): Typeface = faces.getOrPut(weight) {
        runCatching {
            Typeface.Builder(context.assets, "fonts/InstrumentSans.ttf").setFontVariationSettings("'wght' $weight").build()
        }.getOrElse { Typeface.create(Typeface.SANS_SERIF, weight, false) }
    }
}

/** The logo's ring: an amber arc most of the way round, the rest of the circle faint behind it. */
class LogoRing(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(60, 227, 154, 82) }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Brand.AMBER }
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.1f
        track.strokeWidth = stroke / 2
        arc.strokeWidth = stroke
        bounds.set(stroke, stroke, width - stroke, height - stroke)
        canvas.drawOval(bounds, track)
        canvas.drawArc(bounds, -90f, 290f, false, arc)
    }
}
