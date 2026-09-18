package com.qrzzzz.lyricscard.renderer

import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.exportPixelRatio

/** Transient result, bound to the complete immutable request and controller revision. */
data class ConfirmedCanvasMeasurement(
    val spec: RenderSpec,
    val revision: Long,
    val size: CanvasMeasurement,
) {
    fun outputSize(multiplier: Int): CanvasMeasurement {
        val scale = exportPixelRatio(multiplier)
        return CanvasMeasurement((size.width * scale).toInt(), (size.height * scale).toInt())
    }
}
