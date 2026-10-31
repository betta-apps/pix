package io.betta.pix

/**
 * How a backdrop is "printed": grain is fixed to the still, like a photographic print, and scaled by brightness
 * so it shows most in the mid-tones. [grainSize] is in 1080p pixels.
 */
data class FilmLook(
    val name: String,
    val grain: Float,
    val grainSize: Float,
    val softness: Float,
    val halation: Float,
    val vignette: Float,
    val warmth: Float,
) {
    companion object {
        val presets = listOf(
            FilmLook("Clean", grain = 0f, grainSize = 1f, softness = 0.1f, halation = 0f, vignette = 0.15f, warmth = 0f),
            FilmLook("Fine print", grain = 0.175f, grainSize = 0.9f, softness = 0.175f, halation = 0.1f, vignette = 0.25f, warmth = 0.075f),
            FilmLook("35mm", grain = 0.6f, grainSize = 1f, softness = 0.35f, halation = 0.3f, vignette = 0.45f, warmth = 0.25f),
            FilmLook("16mm", grain = 0.9f, grainSize = 1.7f, softness = 0.5f, halation = 0.4f, vignette = 0.55f, warmth = 0.3f),
        )
    }
}
