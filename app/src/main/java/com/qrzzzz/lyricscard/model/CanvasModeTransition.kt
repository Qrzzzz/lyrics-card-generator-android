package com.qrzzzz.lyricscard.model

/** Explicit boundary conversion for old automatic requests; measured sizes are never persisted. */
fun RenderSpec.withLegalManualHeight(): RenderSpec =
    if (canvas.layoutMode == LayoutMode.PORTRAIT && canvas.ratio == CanvasRatio.CUSTOM && !canvas.autoHeight) {
        copy(canvas = canvas.copy(height = canvas.height.coerceIn(720, 3200)))
    } else this
