package io.betta.pix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.concurrent.thread

/**
 * Signed out, in a corner of the screensaver: Plex's QR sign-in, so a phone can sign in without the remote (whose
 * buttons would end the screensaver). One code each time it starts, checked every few seconds until it's approved or
 * runs out; then, or when Plex gives no code, it says where to sign in instead. [onSignedIn] runs on the UI thread.
 */
class SignInCard(private val context: Context, private val alpha: () -> Float, private val onSignedIn: () -> Unit) {
    private val qr = ImageView(context)
    private val tile = FrameLayout(context).apply {
        setPadding(14, 14, 14, 14)
        background = GradientDrawable().apply { cornerRadius = 14f; setColor(Brand.INK) }
        addView(qr, FrameLayout.LayoutParams(QR_SIZE, QR_SIZE))
    }
    private val title = text("Scan to show your own films", 20f, 600)
    private val subtitle = text("and approve it on Plex's page", 16f, 400).apply { alpha = 0.75f }
    @Volatile private var stopped = false
    @Volatile var paused = false

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(24, 24, 24, 22)
        background = GradientDrawable().apply { cornerRadius = 22f; setColor(Color.argb(170, 12, 9, 8)) }
        addView(tile, LinearLayout.LayoutParams(-2, -2))
        addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 18 })
        addView(subtitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 4 })
        alpha = 0f
    }

    fun start() {
        thread {
            val plex = Plex(context)
            try {
                val pin = plex.requestPin()
                val image = qrCode(plex.signInUrl(pin), QR_SIZE)
                view.post { if (!stopped) { qr.setImageBitmap(image); view.animate().alpha(alpha()).setDuration(1200).start() } }
                val deadline = SystemClock.uptimeMillis() + CODE_MILLIS
                // Not asked while the screen is paused; a check that fails (a dropped connection) is just not approved yet.
                while (paused || !runCatching { plex.claim(pin) }.getOrDefault(false)) {
                    if (stopped) return@thread
                    if (SystemClock.uptimeMillis() > deadline) {
                        view.post(::instead)
                        return@thread
                    }
                    Thread.sleep(CHECK_MILLIS)
                }
                view.post {
                    if (stopped) return@post
                    stopped = true
                    view.animate().alpha(0f).setDuration(800).start()
                    onSignedIn()
                }
            } catch (_: Exception) {
                view.post(::instead)
            }
        }
    }

    fun stop() {
        stopped = true
    }

    /** No code to scan: where to sign in instead. */
    private fun instead() {
        if (stopped) return
        tile.visibility = View.GONE
        title.text = "Sign in to Plex in ${context.getString(R.string.app_name)}"
        subtitle.text = "to show your own films here"
        title.layoutParams = (title.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0 }
        view.animate().alpha(alpha()).setDuration(1200).start()
    }

    private fun text(s: String, px: Float, weight: Int) = TextView(context).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        setTextColor(Brand.INK)
        typeface = Brand.font(context, weight)
    }

    private companion object {
        const val QR_SIZE = 200
        const val CODE_MILLIS = 15 * 60_000L
        const val CHECK_MILLIS = 3000L
    }
}

/** A QR code for [text], [size] pixels square, dark on the cream of [Brand.INK]. */
fun qrCode(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until width) for (y in 0 until height) setPixel(x, y, if (matrix[x, y]) Brand.INK_DARK else Brand.INK)
    }
}
