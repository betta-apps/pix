package io.betta.pix

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * What Pix's icon opens: the Plex sign-in, the screensaver's timings and brightness (left and right change them), a
 * preview, and the way to choose Pix as the screensaver, which only the TV's own settings can do. Signed out on a first
 * run, it goes straight to signing in.
 */
class PixActivity : Activity() {
    private lateinit var plexCard: LinearLayout
    private lateinit var dreamNote: TextView
    private lateinit var backdrop: ImageView
    // Counts the stills asked for, so only the latest is put up.
    private var stillAsked = 0
    // Each setting row's redraw, so coming back shows a value changed meanwhile (brightness, in the preview).
    private val rowRefreshes = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backdrop = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        plexCard = LinearLayout(this)
        dreamNote = text("Pix can't choose itself. This opens your TV's settings, where you choose Pix for Plex as the screen saver.", 18f, 400).apply { alpha = 0.65f }
        val timings = ScreensaverSettings(this)
        val rows = listOf(
            "Each film" to ScreensaverSettings.FILM,
            "Time to guess" to ScreensaverSettings.GUESS,
            "Title stays" to ScreensaverSettings.SHOWN,
        ).map { (label, choice) ->
            valueRow(label, { choice.describe(timings.get(choice)) }) { step -> timings.set(choice, cycle(choice.values, timings.get(choice), step)) }
        } + valueRow("Brightness", { "${(timings.brightness * 100).roundToInt()}%" }) { step ->
            timings.brightness = ScreensaverSettings.stepBrightness(timings.brightness, step)
        }
        val preview = button("Preview") { startActivity(Intent(this, PreviewActivity::class.java)) }
        val choose = button("Set as screensaver") { openDreamSettings() }
        // Down from the last row is Preview, the nearest button, and Right from the last button stays put, rather than
        // whichever the TV finds by distance (Set as screensaver, and the Sign in button above).
        listOf(rows.last(), preview, choose).forEach { it.id = View.generateViewId() }
        rows.last().nextFocusDownId = preview.id
        preview.nextFocusUpId = rows.last().id
        choose.nextFocusUpId = rows.last().id
        choose.nextFocusRightId = choose.id
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(PixMark(this@PixActivity, 56f), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = 40 })
            addView(plexCard, LinearLayout.LayoutParams(ROW_WIDTH, -2).apply { bottomMargin = 36 })
            addView(text("Screensaver", 21f, 600).apply { alpha = 0.7f }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(28, 0, 0, 12) })
            rows.forEach { addView(it) }
            addView(LinearLayout(this@PixActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                addView(preview)
                addView(choose, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 20 })
            }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 32, 0, 16) })
            addView(dreamNote, LinearLayout.LayoutParams(ROW_WIDTH, -2))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Brand.GROUND)
            addView(backdrop, FrameLayout.LayoutParams(-1, -1))
            addView(View(this@PixActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.argb(220, 10, 8, 6), Color.argb(140, 10, 8, 6)))
            }, FrameLayout.LayoutParams(-1, -1))
            addView(column, FrameLayout.LayoutParams(-2, -2).apply { setMargins(120, 96, 0, 0) })
            addView(text("Pix ${packageManager.getPackageInfo(packageName, 0).versionName} · Not affiliated with Plex, Inc.", 16f, 400).apply { alpha = 0.5f },
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM).apply { setMargins(120, 0, 0, 56) })
        })
        showPlex()
        plexCard.getFocusables(View.FOCUS_FORWARD).firstOrNull()?.requestFocus()
        if (savedInstanceState == null && !Plex(this).signedIn) startActivity(Intent(this, SignInActivity::class.java))
    }

    // Shown again whenever Pix comes back into view, so it shows a sign-in, a brightness or a film kept meanwhile.
    override fun onResume() {
        super.onResume()
        showPlex()
        rowRefreshes.forEach { it() }
        showStill()
    }

    /** Behind it all, the film the screensaver kept last, blurred; Pix's own still until it has kept one, or signed out. */
    private fun showStill() {
        val asked = ++stillAsked
        val signedIn = Plex(this).signedIn
        thread {
            val kept = if (signedIn) BackdropCache(this).latest(sample = 4) else null
            val still = (kept ?: Identity.still(this, sample = 4)).blurred(2)
            runOnUiThread { if (asked == stillAsked && !isDestroyed) backdrop.setImageBitmap(still) }
        }
    }

    private fun showPlex() {
        val plex = Plex(this)
        val focused = plexCard.findFocus() != null
        plexCard.removeAllViews()
        plexCard.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply { cornerRadius = 20f; setColor(Color.argb(150, 28, 24, 21)); setStroke(2, Color.argb(30, 255, 255, 255)) }
            addView(LinearLayout(this@PixActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Plex", 23f, 600))
                addView(text(if (plex.signedIn) "Signed in. Pix only reads your library." else "Sign in to see your films and shows.", 18f, 400).apply { alpha = 0.7f })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(if (plex.signedIn) button("Sign out") { plex.signOut(); showPlex(); showStill() }
            else button("Sign in") { startActivity(Intent(this@PixActivity, SignInActivity::class.java)) })
        }
        if (focused) plexCard.getFocusables(View.FOCUS_FORWARD).firstOrNull()?.requestFocus()
    }

    /**
     * The TV's screen saver settings where it lets an app open them, else its Settings, with the way on from there in
     * the note (which shows beside Settings' panel). Android TV on the Shield opens only the second.
     */
    private fun openDreamSettings() {
        if (open(Settings.ACTION_DREAM_SETTINGS)) return
        dreamNote.text = if (open(Settings.ACTION_SETTINGS)) "In Settings: Device Preferences, then Screen saver, then choose Pix for Plex."
        else "This TV doesn't open its settings for apps. In its Settings, choose Pix for Plex under Device Preferences, then Screen saver."
    }

    private fun open(action: String) = try {
        startActivity(Intent(action))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    /** A setting: its name, and its value in a pill that left and right (or OK, which steps on) change. */
    private fun valueRow(label: String, value: () -> String, change: (step: Int) -> Unit): View {
        val valueView = text(value(), 18f, 500).apply { setPadding(22, 8, 22, 8); background = GradientDrawable().apply { cornerRadius = 22f } }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            setPadding(28, 18, 20, 18)
            background = GradientDrawable().apply { cornerRadius = 16f; setColor(Color.TRANSPARENT) }
            addView(text(label, 21f, 500), LinearLayout.LayoutParams(0, -2, 1f))
            addView(valueView)
            layoutParams = LinearLayout.LayoutParams(ROW_WIDTH, -2).apply { bottomMargin = 6 }
            fun refresh() {
                valueView.text = if (isFocused) "‹   ${value()}   ›" else value()
                (valueView.background as GradientDrawable).apply {
                    setColor(if (isFocused) Brand.INK else Color.TRANSPARENT)
                    setStroke(2, if (isFocused) Brand.INK else Color.argb(90, 255, 255, 255))
                }
                valueView.setTextColor(if (isFocused) Brand.GROUND else Brand.INK)
            }
            refresh()
            rowRefreshes += ::refresh
            setOnFocusChangeListener { _, focused ->
                (background as GradientDrawable).setColor(if (focused) Color.argb(46, 255, 255, 255) else Color.TRANSPARENT)
                refresh()
            }
            setOnClickListener { change(1); refresh() }
            setOnKeyListener { _, keyCode, event ->
                val step = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@setOnKeyListener false
                }
                if (event.action == KeyEvent.ACTION_DOWN) { change(step); refresh() }
                true
            }
        }
    }

    private fun button(label: String, onClick: () -> Unit) = text(label, 20f, 600).apply {
        setPadding(32, 14, 32, 14)
        isFocusable = true
        background = GradientDrawable().apply { cornerRadius = 30f; setStroke(2, Brand.INK) }
        setOnFocusChangeListener { v, focused ->
            (background as GradientDrawable).setColor(if (focused) Brand.INK else Color.TRANSPARENT)
            setTextColor(if (focused) Brand.GROUND else Brand.INK)
            v.animate().scaleX(if (focused) 1.05f else 1f).scaleY(if (focused) 1.05f else 1f).setDuration(150).start()
        }
        setOnClickListener { onClick() }
    }

    private fun text(s: String, px: Float, weight: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        setTextColor(Brand.INK)
        typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)
    }

    companion object {
        const val ROW_WIDTH = 1000
    }
}
