package com.qrzzzz.lyricscard.model

data class RenderSpecViolation(
    val path: String,
    val message: String,
)

class InvalidRenderSpecException(
    val violations: List<RenderSpecViolation>,
) : IllegalArgumentException(
    violations.joinToString(
        prefix = "Invalid RenderSpec: ",
        separator = "; ",
    ) { "${it.path} ${it.message}" },
)

/** Returns every structural violation so the editor can present useful diagnostics at once. */
fun RenderSpec.validate(): List<RenderSpecViolation> = buildList {
    if (schemaVersion != RenderSpec.SCHEMA_VERSION) {
        violation("schemaVersion", "must be ${RenderSpec.SCHEMA_VERSION}")
    }

    if (rendererVersion != RenderSpec.DEFAULT_RENDERER_VERSION) {
        violation("rendererVersion", "must be ${RenderSpec.DEFAULT_RENDERER_VERSION}")
    }

    validateText("song.title", song.title, MAX_METADATA_LENGTH)
    validateText("song.artist", song.artist, MAX_METADATA_LENGTH)
    validateText("song.album", song.album, MAX_METADATA_LENGTH)
    song.coverAssetId?.let { assetId ->
        if (!assetId.matches(ASSET_ID_PATTERN)) {
            violation("song.coverAssetId", "must be a logical asset ID, not a path or URI")
        }
    }

    validateText("content.lyrics", content.lyrics, MAX_LYRICS_LENGTH)
    validateText("content.translation", content.translation, MAX_LYRICS_LENGTH)
    validateText("content.instrumentalText", content.instrumentalText, MAX_METADATA_LENGTH)

    val widthRange = when (canvas.layoutMode) {
        LayoutMode.PORTRAIT -> 720..1440
        LayoutMode.LANDSCAPE -> 1080..3000
    }
    val heightRange = when (canvas.layoutMode) {
        LayoutMode.PORTRAIT -> 720..3200
        LayoutMode.LANDSCAPE -> 720..1600
    }
    if (canvas.width !in widthRange) {
        violation("canvas.width", "must be in ${widthRange.first}..${widthRange.last}")
    }
    if (canvas.height !in heightRange) {
        violation("canvas.height", "must be in ${heightRange.first}..${heightRange.last}")
    }
    if (canvas.pixelRatio !in 1..2) {
        violation("canvas.pixelRatio", "must be 1 or 2")
    }

    val allowedRatios = when (canvas.layoutMode) {
        LayoutMode.PORTRAIT -> PORTRAIT_RATIOS
        LayoutMode.LANDSCAPE -> LANDSCAPE_RATIOS
    }
    if (canvas.ratio !in allowedRatios) {
        violation("canvas.ratio", "is not valid for ${canvas.layoutMode.name.lowercase()} layout")
    }

    if (canvas.ratio != CanvasRatio.CUSTOM &&
        (canvas.width != canvas.ratio.width || canvas.height != canvas.ratio.height)
    ) {
        violation(
            "canvas",
            "preset ${canvas.ratio.name} must use ${canvas.ratio.width}x${canvas.ratio.height}",
        )
    }

    if (canvas.autoHeight &&
        (canvas.layoutMode != LayoutMode.PORTRAIT || canvas.ratio != CanvasRatio.CUSTOM)
    ) {
        violation("canvas.autoHeight", "is supported only for a custom portrait canvas")
    }

    if (content.mode == ContentMode.INSTRUMENTAL) {
        if (content.translationEnabled) {
            violation("content.translationEnabled", "must be false in instrumental mode")
        }
        if (canvas.layoutMode != LayoutMode.PORTRAIT || canvas.ratio != CanvasRatio.SQUARE) {
            violation("canvas", "must be a portrait 1:1 preset in instrumental mode")
        }
        if (canvas.autoHeight) {
            violation("canvas.autoHeight", "must be false in instrumental mode")
        }
    }

    if (typography.fontFamily.isBlank() || typography.fontFamily.length > MAX_FONT_FAMILY_LENGTH) {
        violation("typography.fontFamily", "must contain 1..$MAX_FONT_FAMILY_LENGTH characters")
    }
    if (typography.lyricSize !in 36..72) {
        violation("typography.lyricSize", "must be in 36..72")
    }
    if (!typography.lineHeight.isFinite() || typography.lineHeight !in 1.1..1.75) {
        violation("typography.lineHeight", "must be in 1.1..1.75")
    }
    if (!typography.translationScale.isFinite() || typography.translationScale !in 0.6..0.9) {
        violation("typography.translationScale", "must be in 0.6..0.9")
    }
    when (typography.textColorMode) {
        TextColorMode.CUSTOM -> {
            if (typography.customTextColor?.matches(HEX_COLOR_PATTERN) != true) {
                violation("typography.customTextColor", "must be a #RRGGBB or #RRGGBBAA color")
            }
        }

        TextColorMode.AUTO,
        TextColorMode.PRESET -> if (typography.customTextColor != null &&
            !typography.customTextColor.matches(HEX_COLOR_PATTERN)
        ) {
            violation("typography.customTextColor", "must be null or a valid hex color")
        }
    }

    validateColor("visual.palette.dominant", visual.palette.dominant)
    validateColor("visual.palette.secondary", visual.palette.secondary)
    validateColor("visual.palette.accent", visual.palette.accent)
    if (!visual.gridOpacity.isFinite() || visual.gridOpacity !in 0.0..1.0) {
        violation("visual.gridOpacity", "must be in 0.0..1.0")
    }

    validateText("branding.sharedByName", branding.sharedByName, MAX_METADATA_LENGTH)
    if (!media.coverCropScale.isFinite() || media.coverCropScale !in 1.0..2.0) {
        violation("media.coverCropScale", "must be in 1.0..2.0")
    }
}

fun RenderSpec.requireValid(): RenderSpec {
    val violations = validate()
    if (violations.isNotEmpty()) {
        throw InvalidRenderSpecException(violations)
    }
    return this
}

private fun MutableList<RenderSpecViolation>.validateText(
    path: String,
    value: String,
    maxLength: Int,
) {
    if (value.length > maxLength) {
        violation(path, "must be at most $maxLength characters")
    }
    if (value.any { it == '\u0000' }) {
        violation(path, "must not contain NUL characters")
    }
}

private fun MutableList<RenderSpecViolation>.validateColor(path: String, value: String) {
    if (!value.matches(HEX_COLOR_PATTERN)) {
        violation(path, "must be a #RRGGBB or #RRGGBBAA color")
    }
}

private fun MutableList<RenderSpecViolation>.violation(path: String, message: String) {
    add(RenderSpecViolation(path, message))
}

private val ASSET_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
private val HEX_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")
private val PORTRAIT_RATIOS = setOf(
    CanvasRatio.SQUARE,
    CanvasRatio.PORTRAIT_4_5,
    CanvasRatio.PORTRAIT_9_16,
    CanvasRatio.CUSTOM,
)
private val LANDSCAPE_RATIOS = setOf(
    CanvasRatio.LANDSCAPE_16_9,
    CanvasRatio.LANDSCAPE_21_9,
    CanvasRatio.LANDSCAPE_3_2,
    CanvasRatio.CUSTOM,
)

private const val MAX_METADATA_LENGTH = 240
private const val MAX_FONT_FAMILY_LENGTH = 200
private const val MAX_LYRICS_LENGTH = 200_000
