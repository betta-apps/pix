package io.betta.pix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * What the screensaver shows without Plex: any still pushed to the app's `backdrops` folder, then the app's own
 * ([Identity.fallback]). Push with `adb push still.jpg /sdcard/Android/data/<application id>/files/backdrops/`.
 */
class Backdrops(context: Context) {
    class Backdrop(val title: String, val year: Int? = null, val link: String? = null, val load: () -> Bitmap)

    val all: List<Backdrop> = run {
        val pushed = context.getExternalFilesDir("backdrops")?.listFiles()
            ?.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name }
            .orEmpty()
            .map { file -> Backdrop(file.nameWithoutExtension) { decode(file) } }
        pushed + Identity.fallback(context)
    }

    private fun decode(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
