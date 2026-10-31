package io.betta.pix

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Signing in to Plex: what the app ([Identity]) does with the account, and a QR code that opens Plex's sign-in page on
 * a phone with this TV's code filled in. It waits for the approval, then goes back to wherever it was opened from. OK
 * asks for a new code when one has run out or Plex couldn't be reached.
 */
class SignInActivity : Activity() {
    private lateinit var qr: ImageView
    private lateinit var status: TextView
    private lateinit var statusDot: View
    @Volatile private var waiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Plex(this).signedIn) return finish()

        qr = ImageView(this)
        statusDot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Identity.ACCENT) } }
        status = text("", 19f, 400).apply { alpha = 0.8f }

        val about = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Identity.logo(this@SignInActivity))
            addView(text("Connect your Plex library", 46f, 600), LinearLayout.LayoutParams(-2, -2).apply { topMargin = 72 })
            addView(text(Identity.PITCH, 22f, 400).apply { alpha = 0.78f; setLineSpacing(10f, 1f) }, LinearLayout.LayoutParams(820, -2).apply { topMargin = 24 })
            Identity.POINTS.forEachIndexed { i, line -> addView(point(line), LinearLayout.LayoutParams(-2, -2).apply { topMargin = if (i == 0) 44 else 18 }) }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(44, 44, 44, 40)
            background = GradientDrawable().apply { cornerRadius = 28f; setColor(Brand.INK) }
            addView(qr, LinearLayout.LayoutParams(QR_SIZE, QR_SIZE))
            addView(text("Scan with your phone", 24f, 600).apply { setTextColor(Brand.INK_DARK) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 24 })
            addView(text("and approve it on Plex's page", 18f, 400).apply { setTextColor(Brand.INK_DARK); alpha = 0.7f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 6 })
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(card)
            addView(LinearLayout(this@SignInActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(statusDot, LinearLayout.LayoutParams(12, 12))
                addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 12 })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 32 })
        }

        setContentView(FrameLayout(this).apply {
            // Warm black, lit a little from the left like the logo.
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 1500f
                setGradientCenter(0.25f, 0.4f)
                colors = intArrayOf(Brand.GROUND_LIGHT, Brand.GROUND)
            }
            addView(about, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = 160 })
            addView(side, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.END).apply { marginEnd = 160 })
        })
        requestCode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && !waiting) {
            requestCode()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A new code and QR, then checks every 2 s for the approval until it's given or the code runs out. */
    private fun requestCode() {
        waiting = true
        show("Getting a code from Plex…", Identity.ACCENT)
        thread {
            val plex = Plex(this)
            try {
                val pin = plex.requestPin()
                val image = qrCode(plex.signInUrl(pin), QR_SIZE)
                runOnUiThread {
                    qr.setImageBitmap(image)
                    show("Waiting for you to approve it on your phone", Identity.ACCENT)
                }
                val deadline = SystemClock.uptimeMillis() + CODE_MILLIS
                while (!plex.claim(pin)) {
                    if (isDestroyed) return@thread
                    if (SystemClock.uptimeMillis() > deadline) {
                        waiting = false
                        runOnUiThread { qr.setImageDrawable(null); show("That code ran out. Press OK for a new one.", Brand.INK) }
                        return@thread
                    }
                    Thread.sleep(2000)
                }
                runOnUiThread {
                    show("You're signed in", Color.rgb(140, 196, 120))
                    qr.postDelayed({ finish() }, 1200)
                }
            } catch (e: Exception) {
                waiting = false
                runOnUiThread { show("Couldn't reach Plex. Check the TV's internet, then press OK to try again.", Brand.INK) }
            }
        }
    }

    private fun show(message: String, dot: Int) {
        status.text = message
        (statusDot.background as GradientDrawable).setColor(dot)
    }

    private fun point(line: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(View(this@SignInActivity).apply {
            background = GradientDrawable().apply { cornerRadius = 3f; setColor(Identity.ACCENT) }
        }, LinearLayout.LayoutParams(12, 12))
        addView(text(line, 21f, 400).apply { alpha = 0.9f }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 18 })
    }

    private fun text(s: String, px: Float, weight: Int) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        setTextColor(Brand.INK)
        typeface = Brand.font(this@SignInActivity, weight)
    }

    private companion object {
        const val QR_SIZE = 340
        const val CODE_MILLIS = 15 * 60_000L
    }
}
