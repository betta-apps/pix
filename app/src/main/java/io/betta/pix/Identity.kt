package io.betta.pix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View

/**
 * What makes this app Pix: the sign-in's logo (the wordmark), words and the yellow of its dashes,
 * and the one still it shows before it has any of your films.
 */
object Identity {
    val ACCENT = PixMark.YELLOW

    const val PITCH = "Pix turns the films and shows on your Plex server into a slow, cinematic screensaver. To see them, it " +
        "needs you to sign in to Plex."

    val POINTS = listOf(
        "It only reads your library. It never changes anything in Plex.",
        "Your sign-in stays on this TV.",
        "Private libraries are never shown.",
    )

    fun logo(context: Context): View = PixMark(context, 64f)

    /** The moon from Méliès's A Trip to the Moon (1902), from its hand-coloured print: public domain everywhere. */
    fun still(context: Context, sample: Int = 1): Bitmap =
        BitmapFactory.decodeResource(context.resources, R.drawable.trip_to_the_moon, BitmapFactory.Options().apply { inSampleSize = sample })

    fun fallback(context: Context): List<Backdrops.Backdrop> =
        listOf(Backdrops.Backdrop("A Trip to the Moon", 1902) { still(context) })
}
