package com.qrzzzz.lyricscard.model

import java.util.UUID

object ProjectTemplates {
    const val DEFAULT_BLANK_NAME: String = "未命名歌词卡"
    const val DEFAULT_SAMPLE_NAME: String = "示例·微光"

    fun blank(
        name: String = DEFAULT_BLANK_NAME,
        id: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): Project = Project(
        id = id,
        name = name,
        spec = RenderSpec(),
        createdAt = now,
        updatedAt = now,
    ).requireValid()

    fun sample(
        name: String = DEFAULT_SAMPLE_NAME,
        id: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): Project = Project(
        id = id,
        name = name,
        spec = RenderSpec(
            song = SongSpec(
                title = "微光",
                artist = "Lyrics Card",
                album = "Android Alpha",
            ),
            content = ContentSpec(
                lyrics = SAMPLE_LYRICS,
                translationEnabled = true,
                translation = SAMPLE_TRANSLATION,
            ),
            canvas = CanvasSpec(
                ratio = CanvasRatio.PORTRAIT_4_5,
                width = 1080,
                height = 1350,
                autoHeight = false,
            ),
            visual = VisualSpec(
                palette = PaletteSpec(
                    dominant = "#0F2D58",
                    secondary = "#1E66B0",
                    accent = "#9FCFEE",
                ),
                gridEnabled = true,
            ),
            visibility = VisibilitySpec(
                showCover = false,
                showSongInfo = true,
                showAlbum = true,
            ),
        ),
        createdAt = now,
        updatedAt = now,
    ).requireValid()

    private val SAMPLE_LYRICS = listOf(
        "风从城市的屋顶掠过",
        "把今天写成一首歌",
        "我们在微光里相遇",
        "也在旋律中记得彼此",
    ).joinToString("\n")

    private val SAMPLE_TRANSLATION = listOf(
        "The wind crosses the city roofs",
        "And turns today into a song",
        "We meet inside a trace of light",
        "And remember through the melody",
    ).joinToString("\n")
}
