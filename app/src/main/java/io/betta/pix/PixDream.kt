package io.betta.pix

import android.service.dreams.DreamService
import android.view.KeyEvent
import kotlin.concurrent.thread

/**
 * The screensaver. It never keeps the Shield awake: no wake lock and no keep-screen-on, so the Shield's own sleep
 * timer still ends it and HDMI-CEC turns the TV off. Left and right flip through the films, up and down set the
 * brightness, OK opens the film in Plex, and any other button closes it.
 */
class PixDream : DreamService() {
    private var screen: LookScreen? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        val created = LookScreen(this)
        screen = created
        setContentView(created.view)
        if (!Plex(this).signedIn) {
            created.showAny()
            // Signed in from a phone, your films take over at once.
            created.askSignIn { load(created, now = true) }
            return
        }
        created.useBackdrops(BackdropCache(this).all())
        load(created)
    }

    private fun load(created: LookScreen, now: Boolean = false) = thread {
        runCatching { Plex(this).backdrops(BackdropCache(this)) }
            // No films from Plex (only private libraries, say) is handled like not reaching it: the fallback shows.
            .onSuccess { films -> created.view.post { if (films.isEmpty()) screen?.takeIf { it.isEmpty }?.showAny() else screen?.useBackdrops(films, now) } }
            .onFailure { created.view.post { screen?.takeIf { it.isEmpty }?.showAny() } }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) screen?.stepBackdrop(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) screen?.stepBackdrop(1)
            KeyEvent.KEYCODE_DPAD_UP -> if (down) screen?.stepBrightness(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) screen?.stepBrightness(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (down) {
                screen?.openInPlex()
                finish()
            }
            else -> finish()
        }
        return true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        screen?.resume()
    }

    override fun onDreamingStopped() {
        screen?.pause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        screen?.close()
        screen = null
        super.onDetachedFromWindow()
    }
}
