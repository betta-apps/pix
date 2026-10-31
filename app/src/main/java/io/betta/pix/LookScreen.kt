package io.betta.pix

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The backdrop on screen, shared by the screensaver and the preview: which film, which look, how bright, and the
 * guessing game (the year halfway through the guess, then the title, which goes again after a while), alternating
 * between the bottom corners. The timings are the ones chosen in Settings ([ScreensaverSettings]), read as each film
 * starts.
 */
class LookScreen(private val context: Context) {
    private val prefs = context.getSharedPreferences("look", Context.MODE_PRIVATE)
    private val renderer = LookRenderer()
    private val loader = Executors.newSingleThreadExecutor()
    private var backdrops = Backdrops(context).all
    private var backdrop = 0
    // Counts the films asked for, so only the latest may go on screen.
    private var loading = 0
    private var look = prefs.getInt("look", 1).coerceIn(FilmLook.presets.indices)
    private val settings = ScreensaverSettings(context)
    private var brightness = settings.brightness
    private var corner = 0
    private var failures = 0
    private var shown: Backdrops.Backdrop? = null
    private val captionAlpha get() = brightness
    // Signed out: Plex's QR sign-in, dimmed with the rest but never too far to scan.
    private var signIn: SignInCard? = null
    private val cardAlpha get() = brightness.coerceAtLeast(0.6f)

    private val surface = GLSurfaceView(context).apply {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
    }

    private val label = TextView(context).apply {
        setTextColor(Color.argb(220, 243, 237, 226))
        textSize = 16f
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
        alpha = 0f
    }


    private val year = TextView(context).apply {
        setTextColor(Color.rgb(217, 207, 189))
        textSize = 11f
        letterSpacing = 0.2f
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
        alpha = 0f
    }

    private val title = TextView(context).apply {
        setTextColor(Color.rgb(243, 237, 226))
        textSize = 23f
        typeface = Typeface.SERIF
        setShadowLayer(16f, 0f, 0f, Color.BLACK)
        // Always room for two lines, however long the title, so the caption's height gives nothing away; longer ends in "…".
        minLines = 2
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        alpha = 0f
    }

    private val line = TitleLine(context).apply { alpha = 0f }

    private val hint = Runnable { year.animate().alpha(captionAlpha).setDuration(1500).start() }
    private val reveal = Runnable {
        line.finish()
        title.animate().alpha(captionAlpha).setDuration(2400).start()
    }
    // Once guessed, the caption goes and leaves the picture.
    private val vanish = Runnable { listOf(year, title).forEach { it.animate().alpha(0f).setDuration(2500).start() } }
    private val next = Runnable { stepBackdrop(1) }

    private val caption = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        // Full width, so where the year sits says nothing about the title; the line under it gives only its words' lengths.
        addView(year, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // The line runs where the title will be, and gives way to it.
        addView(FrameLayout(context).apply {
            addView(title, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            addView(line, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    val view = FrameLayout(context).apply {
        addView(surface)
        addView(caption, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(96, 0, 96, 80)
        })
        addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(64, 48, 0, 0)
        })
    }

    init {
        renderer.setLook(FilmLook.presets[look])
        renderer.brightness = brightness
    }

    fun showBackdrop(index: Int) {
        // Closed: a film that loaded just before, or its timer, can still ask for the next one.
        if (loader.isShutdown) return
        backdrop = Math.floorMod(index, backdrops.size)
        val chosen = backdrops[backdrop]
        // Only the latest film asked for may go on screen; one that loads late is dropped.
        val asked = ++loading
        val side = CORNERS[corner++ % CORNERS.size]
        hideCaption()
        loader.execute {
            val bitmap = runCatching { traced("backdrop load") { chosen.load() } }.getOrNull()
            val tint = bitmap?.let { tintOf(it, side) }
            view.post {
                if (asked != loading) return@post
                if (bitmap == null) {
                    // Skip straight past a film that can't be shown (black bars, say), but don't spin when none can.
                    failures++
                    view.postDelayed(next, if (failures < 5) 0 else RETRY_MILLIS)
                    return@post
                }
                failures = 0
                // Shown from a list that's since been replaced, it takes its place in the new one when it's there.
                backdrops.indexOf(chosen).takeIf { it >= 0 }?.let { backdrop = it }
                renderer.show(bitmap, ScreensaverSettings(context).filmMillis)
                shown = chosen
                val timings = ScreensaverSettings(context)
                view.postDelayed(next, timings.filmMillis)
                // Signed out there's only the app's own still (or a painting) to show, so nothing to guess.
                if (!Plex(context).signedIn) return@post
                year.text = chosen.year?.toString().orEmpty()
                title.text = chosen.title
                placeCaption(side)
                tint?.let(::colourCaption)
                val guess = timings.guessMillis
                line.start(title, side == Gravity.END, guess, captionAlpha, tint ?: Color.rgb(243, 237, 226))
                view.postDelayed(hint, guess / 2)
                view.postDelayed(reveal, guess)
                if (timings.shownMillis > 0) view.postDelayed(vanish, guess + timings.shownMillis)
            }
        }
    }

    fun stepBackdrop(by: Int) = showBackdrop(backdrop + by)

    /** Any one of the films. */
    fun showAny() = showBackdrop(Random.nextInt(backdrops.size))

    /** Opens the film on screen in the Plex app; false for a still that isn't a Plex film. */
    fun openInPlex(): Boolean {
        val link = shown?.link ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(PLEX_APP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Plays [list] from here on: at once if nothing is showing yet or [now], otherwise from the next film, uninterrupted.
     */
    fun useBackdrops(list: List<Backdrops.Backdrop>, now: Boolean = false) {
        if (list.isEmpty()) return
        backdrops = list
        if (shown == null || now) showBackdrop(0) else backdrop = -1
    }

    /** Plex's QR sign-in in the top right, until it's approved; then [onSignedIn]. */
    fun askSignIn(onSignedIn: () -> Unit) {
        val card = SignInCard(context, { cardAlpha }) { signIn = null; onSignedIn() }
        signIn = card
        view.addView(card.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, 64, 64, 0)
        })
        card.start()
    }

    /** Nothing has been shown yet. */
    val isEmpty get() = shown == null

    /** Remembered, for the screensaver and the preview alike. */
    fun stepBrightness(by: Int) {
        brightness = ScreensaverSettings.stepBrightness(brightness, by)
        settings.brightness = brightness
        renderer.brightness = brightness
        listOf(year, title).filter { it.alpha > 0f }.forEach { it.alpha = captionAlpha }
        signIn?.view?.takeIf { it.alpha > 0f }?.alpha = cardAlpha
        announce("Brightness ${(brightness * 100).roundToInt()}%")
    }


    fun resume() {
        surface.onResume()
        signIn?.paused = false
    }

    fun pause() {
        surface.onPause()
        signIn?.paused = true
    }

    fun close() {
        signIn?.stop()
        hideCaption()
        loader.shutdownNow()
    }

    /**
     * A light tint of the colour under the caption (the bottom of the picture, on its side), so the text belongs to
     * the picture: that area's hue, its most colourful parts counting most, kept pale enough to stay readable.
     */
    private fun tintOf(bitmap: Bitmap, side: Int): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 48, 27, true)
        val columns = if (side == Gravity.END) 24 until 48 else 0 until 24
        val hsv = FloatArray(3)
        var r = 0f
        var g = 0f
        var b = 0f
        var total = 0f
        for (x in columns) for (y in 16 until 27) {
            val pixel = small.getPixel(x, y)
            Color.colorToHSV(pixel, hsv)
            val weight = hsv[1] * hsv[2] + 0.02f
            r += Color.red(pixel) * weight
            g += Color.green(pixel) * weight
            b += Color.blue(pixel) * weight
            total += weight
        }
        Color.colorToHSV(Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt()), hsv)
        return Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.6f).coerceAtMost(0.22f), 0.96f))
    }

    private fun colourCaption(tint: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(tint, hsv)
        // A dark shadow in a deep shade of the same colour, strong enough to keep the text readable on any picture.
        val shadow = Color.HSVToColor(220, floatArrayOf(hsv[0], 0.5f, 0.06f))
        title.setTextColor(tint)
        title.setShadowLayer(14f, 0f, 2f, shadow)
        year.setTextColor(Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 0.85f)))
        year.setShadowLayer(10f, 0f, 1f, shadow)
    }

    private fun placeCaption(side: Int) {
        year.gravity = side
        title.gravity = side or Gravity.TOP
    }

    private fun hideCaption() {
        listOf(hint, reveal, vanish, next).forEach(view::removeCallbacks)
        line.stop()
        listOf(year, title).forEach {
            it.animate().cancel()
            it.animate().alpha(0f).setDuration(600).start()
        }
    }

    private fun announce(text: String) {
        label.text = text
        label.animate().cancel()
        label.alpha = 1f
        label.animate().alpha(0f).setStartDelay(2000).setDuration(800).start()
    }

    private companion object {
        const val PLEX_APP = "com.plexapp.android"
        const val RETRY_MILLIS = 5_000L

        // Bottom left, then bottom right, so the caption never lands where the last one was.
        val CORNERS = listOf(Gravity.START, Gravity.END)
    }
}
