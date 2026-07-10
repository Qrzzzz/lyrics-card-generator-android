package com.qrzzzz.lyricscard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The complete, versioned input consumed by the bundled renderer.
 *
 * Keep this model free of Android types and transient editor state. Serialized enum names are
 * part of the cross-platform contract and must not be changed without a schema migration.
 */
@Serializable
data class RenderSpec(
    val schemaVersion: Int = SCHEMA_VERSION,
    val rendererVersion: String = DEFAULT_RENDERER_VERSION,
    val locale: RenderLocale = RenderLocale.ZH,
    val song: SongSpec = SongSpec(),
    val content: ContentSpec = ContentSpec(),
    val canvas: CanvasSpec = CanvasSpec(),
    val typography: TypographySpec = TypographySpec(),
    val visual: VisualSpec = VisualSpec(),
    val visibility: VisibilitySpec = VisibilitySpec(),
    val branding: BrandingSpec = BrandingSpec(),
    val media: MediaSpec = MediaSpec(),
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val DEFAULT_RENDERER_VERSION: String = "android-alpha-renderer-1"
    }
}

@Serializable
data class SongSpec(
    val source: SongSource = SongSource.UNKNOWN,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val explicit: Boolean = false,
    val coverAssetId: String? = null,
)

@Serializable
data class ContentSpec(
    val mode: ContentMode = ContentMode.LYRICS,
    val lyrics: String = "",
    val translationEnabled: Boolean = false,
    val translation: String = "",
    val instrumentalText: String = "纯音乐",
)

@Serializable
data class CanvasSpec(
    val layoutMode: LayoutMode = LayoutMode.PORTRAIT,
    val ratio: CanvasRatio = CanvasRatio.CUSTOM,
    val width: Int = 1040,
    val height: Int = 1080,
    val autoHeight: Boolean = true,
    val pixelRatio: Int = 2,
)

@Serializable
data class TypographySpec(
    val fontScheme: FontScheme = FontScheme.SANS_HEAVY,
    val fontFamily: String = "Source Han Sans SC",
    val lyricSize: Int = 60,
    val lineHeight: Double = 1.4,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val translationScale: Double = 0.75,
    val twoLineTitle: Boolean = false,
    val textColorMode: TextColorMode = TextColorMode.AUTO,
    val textColorPreset: TextColorPreset = TextColorPreset.WHITE,
    val customTextColor: String? = null,
)

@Serializable
data class VisualSpec(
    val backgroundMode: BackgroundMode = BackgroundMode.PALETTE,
    val palette: PaletteSpec = PaletteSpec(),
    val gridEnabled: Boolean = false,
    val gridDensity: GridDensity = GridDensity.MEDIUM,
    val gridOpacity: Double = 0.15,
)

@Serializable
data class PaletteSpec(
    val dominant: String = "#7C3AED",
    val secondary: String = "#2563EB",
    val accent: String = "#F97316",
)

@Serializable
data class VisibilitySpec(
    val showCover: Boolean = true,
    val showSongInfo: Boolean = true,
    val showAlbum: Boolean = true,
    val showPlatformBadge: Boolean = false,
    val showSharedBy: Boolean = false,
    val showGeneratedWatermark: Boolean = false,
)

@Serializable
data class BrandingSpec(
    val sharedByName: String = "",
    val platform: SongSource = SongSource.UNKNOWN,
)

@Serializable
data class MediaSpec(
    val coverCropScale: Double = 1.0,
)

@Serializable
enum class RenderLocale {
    @SerialName("zh")
    ZH,

    @SerialName("zh-TW")
    ZH_TW,

    @SerialName("en")
    EN,

    @SerialName("fr")
    FR,

    @SerialName("ja")
    JA,

    @SerialName("es")
    ES,
}

@Serializable
enum class SongSource {
    @SerialName("unknown")
    UNKNOWN,

    @SerialName("qq")
    QQ,

    @SerialName("netease")
    NETEASE,

    @SerialName("apple")
    APPLE,

    @SerialName("spotify")
    SPOTIFY,
}

@Serializable
enum class ContentMode {
    @SerialName("lyrics")
    LYRICS,

    @SerialName("instrumental")
    INSTRUMENTAL,
}

@Serializable
enum class LayoutMode {
    @SerialName("portrait")
    PORTRAIT,

    @SerialName("landscape")
    LANDSCAPE,
}

@Serializable
enum class CanvasRatio(
    val width: Int?,
    val height: Int?,
) {
    @SerialName("1:1")
    SQUARE(1080, 1080),

    @SerialName("4:5")
    PORTRAIT_4_5(1080, 1350),

    @SerialName("9:16")
    PORTRAIT_9_16(1080, 1920),

    @SerialName("16:9")
    LANDSCAPE_16_9(1920, 1080),

    @SerialName("21:9")
    LANDSCAPE_21_9(2520, 1080),

    @SerialName("3:2")
    LANDSCAPE_3_2(1800, 1200),

    @SerialName("custom")
    CUSTOM(null, null),
}

@Serializable
enum class FontScheme {
    @SerialName("sans-heavy")
    SANS_HEAVY,

    @SerialName("serif-heavy")
    SERIF_HEAVY,

    @SerialName("system-sans")
    SYSTEM_SANS,

    @SerialName("system-serif")
    SYSTEM_SERIF,
}

@Serializable
enum class TextAlignment {
    @SerialName("left")
    LEFT,

    @SerialName("center")
    CENTER,

    @SerialName("right")
    RIGHT,
}

@Serializable
enum class TextColorMode {
    @SerialName("auto")
    AUTO,

    @SerialName("preset")
    PRESET,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class TextColorPreset {
    @SerialName("white")
    WHITE,

    @SerialName("black")
    BLACK,

    @SerialName("warmWhite")
    WARM_WHITE,

    @SerialName("cream")
    CREAM,

    @SerialName("charcoal")
    CHARCOAL,

    @SerialName("softBlue")
    SOFT_BLUE,

    @SerialName("softGold")
    SOFT_GOLD,
}

@Serializable
enum class BackgroundMode {
    @SerialName("palette")
    PALETTE,

    @SerialName("gradient")
    GRADIENT,
}

@Serializable
enum class GridDensity {
    @SerialName("sparse")
    SPARSE,

    @SerialName("medium")
    MEDIUM,

    @SerialName("dense")
    DENSE,
}

/** Shared strict codec for persisted projects and renderer messages. */
object RenderSpecJson {
    val format: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
        allowStructuredMapKeys = false
    }

    fun encode(spec: RenderSpec): String =
        format.encodeToString(RenderSpec.serializer(), spec.requireValid())

    fun decode(value: String): RenderSpec =
        format.decodeFromString(RenderSpec.serializer(), value).requireValid()
}
