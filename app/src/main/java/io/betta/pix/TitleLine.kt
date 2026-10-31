package io.betta.pix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.view.View
import android.widget.TextView

/**
 * The time left to guess, and a clue: a faint line on each of the title's lines that grows from the caption's corner
 * until it's as long as the title, with a gap wherever there's a space, so each word's length shows. A title on two
 * lines fills the first, then the second. Then it gives way to the title.
 */
class TitleLine(context: Context) : View(context) {
    // Timed from the clock rather than an animator, which the system's animation speed setting would scale.
    private var startedAt = 0L
    private var millis = 0L
    private var lines: List<WordLine> = emptyList()
    private var fromEnd = false
    // A start waiting for the title's first layout, called off by stop() or a newer start.
    private var waiting: Pair<TextView, Runnable>? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 243, 237, 226) }

    /** Grows over [millis] to [title]'s length, laid out as [title] lays it out, from its end when the caption is on the right. */
    fun start(title: TextView, fromEnd: Boolean, millis: Long, brightness: Float, color: Int) {
        stop()
        // Not laid out yet (the very first film): once it is, so the line breaks where the title's will.
        if (title.width == 0) {
            val later = Runnable { if (title.width > 0) start(title, fromEnd, millis, brightness, color) }
            waiting = title to later
            title.post(later)
            return
        }
        val text = title.text.toString()
        val width = title.width - title.totalPaddingLeft - title.totalPaddingRight
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, title.paint, width.coerceAtLeast(1))
            .setAlignment(if (fromEnd) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(title.includeFontPadding)
            .setLineSpacing(title.lineSpacingExtra, title.lineSpacingMultiplier)
            .setBreakStrategy(title.breakStrategy)
            .setHyphenationFrequency(title.hyphenationFrequency)
            .setMaxLines(title.maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        // Only what's shown: up to the "…" on the last line when it's cut short.
        val last = layout.lineCount - 1
        val shown = if (layout.getEllipsisCount(last) > 0) layout.getLineStart(last) + layout.getEllipsisStart(last) else text.length
        lines = wordLines(text.substring(0, shown), layout::getLineForOffset, layout::getLineEnd, layout::getPrimaryHorizontal) { title.paint.measureText(it) }
            .map { (line, words) ->
                WordLine(title.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f, words.map { (a, b) -> a + title.totalPaddingLeft to minOf(b, width.toFloat()) + title.totalPaddingLeft })
            }
        this.fromEnd = fromEnd
        this.millis = millis
        startedAt = SystemClock.uptimeMillis()
        paint.color = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
        alpha = brightness
        invalidate()
    }

    fun finish() {
        animate().alpha(0f).setDuration(800).start()
    }

    fun stop() {
        waiting?.let { (title, later) -> title.removeCallbacks(later) }
        waiting = null
        animate().cancel()
        millis = 0L
        alpha = 0f
    }

    // Only as tall as the title it lies over: left to choose, it took the whole screen and lifted the caption to the top.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        if (millis == 0L || lines.isEmpty()) return
        val progress = ((SystemClock.uptimeMillis() - startedAt).toFloat() / millis).coerceIn(0f, 1f)
        var left = lines.sumOf { (it.to - it.from).toDouble() }.toFloat() * progress
        lines.forEach { line ->
            val shown = minOf(left, line.to - line.from)
            left -= shown
            val (from, to) = if (fromEnd) line.to - shown to line.to else line.from to line.from + shown
            line.words.forEach { (start, end) ->
                val a = maxOf(start, from)
                val b = minOf(end, to)
                if (b > a) canvas.drawRect(a, line.y - THICKNESS / 2, b, line.y + THICKNESS / 2, paint)
            }
        }
        if (progress < 1f) postInvalidateOnAnimation()
    }

    /** A line of the title: where it sits down the view, and its words along it. */
    private class WordLine(val y: Float, val words: List<Pair<Float, Float>>) {
        val from = words.minOf { it.first }
        val to = words.maxOf { it.second }
    }

    private companion object {
        const val THICKNESS = 3f
    }
}

/**
 * The words of [text] by the line they fall on ([lineOf] a character's line, [lineEnd] where a line stops), each from
 * where it starts ([xOf]) for its own [measure]d length, so a space between words is a gap. A word the layout breaks
 * across lines is a piece on each. In order, the first line first.
 */
fun wordLines(text: String, lineOf: (Int) -> Int, lineEnd: (Int) -> Int, xOf: (Int) -> Float, measure: (String) -> Float): List<Pair<Int, List<Pair<Float, Float>>>> =
    Regex("\\S+").findAll(text)
        .flatMap { word ->
            generateSequence(word.range.first) { start -> lineEnd(lineOf(start)).takeIf { it <= word.range.last } }
                .map { start ->
                    val end = minOf(word.range.last + 1, lineEnd(lineOf(start)))
                    lineOf(start) to xOf(start).let { x -> x to x + measure(text.substring(start, end)) }
                }
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (line, words) -> line to words }
