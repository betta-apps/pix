package io.betta.pix

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import kotlin.concurrent.thread

/**
 * The screensaver as a screen, from Settings. Try it with the remote: left and right change the film, up and down the
 * brightness, OK opens it in Plex.
 */
class PreviewActivity : Activity() {
    private lateinit var screen: LookScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = LookScreen(this)
        setContentView(screen.view)
        if (!Plex(this).signedIn) {
            // Without Plex there's only the app's own still ([Identity.fallback]) and the sign-in.
            screen.showBackdrop(0)
            screen.askSignIn { load(now = true) }
            return
        }
        // Fades in from black with a film kept from last time while Plex loads the rest.
        screen.useBackdrops(BackdropCache(this).all())
        load()
    }

    private fun load(now: Boolean = false) = thread {
        runCatching { Plex(this).backdrops(BackdropCache(this)) }
            // No films from Plex (only private libraries, say) is handled like not reaching it: the fallback shows.
            .onSuccess { films -> runOnUiThread { if (films.isEmpty()) { if (screen.isEmpty) screen.showBackdrop(0) } else screen.useBackdrops(films, now) } }
            .onFailure { runOnUiThread { if (screen.isEmpty) screen.showBackdrop(0) } }
    }

    override fun onResume() {
        super.onResume()
        screen.resume()
    }

    override fun onPause() {
        screen.pause()
        super.onPause()
    }

    override fun onDestroy() {
        screen.close()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> screen.stepBackdrop(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> screen.stepBackdrop(1)
            KeyEvent.KEYCODE_DPAD_UP -> screen.stepBrightness(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> screen.stepBrightness(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> screen.openInPlex()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
