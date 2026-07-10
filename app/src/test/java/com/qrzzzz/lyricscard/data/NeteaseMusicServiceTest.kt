package com.qrzzzz.lyricscard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseMusicServiceTest {
    @Test
    fun extractsDirectAndSharedSongLinks() {
        assertEquals("123456", NeteaseMusicService.parseSongId("https://music.163.com/song?id=123456"))
        assertEquals("42", NeteaseMusicService.parseSongId("https://music.163.com/#/song?id=42"))
        assertNull(NeteaseMusicService.parseSongId("https://music.163.com/playlist?id=bad"))
        assertEquals(
            "https://music.163.com/song?id=9876",
            NeteaseMusicService.extractFirstUrl("分享歌曲：测试 https://music.163.com/song?id=9876 （来自网易云音乐）"),
        )
    }

    @Test
    fun normalizesSearchResultsAndLimit() {
        val json = """
            {
              "result": {
                "songs": [
                  {
                    "id": 101,
                    "name": "第一首",
                    "artists": [{"name": "歌手甲"}, {"name": "歌手乙"}],
                    "album": {"name": "专辑一", "picUrl": "https://p1.music.126.net/a.jpg"},
                    "duration": 234000
                  },
                  {"id": 102, "name": "第二首", "artists": [], "album": {"name": "专辑二"}}
                ]
              }
            }
        """.trimIndent()

        val results = NeteaseMusicService.normalizeSearchResponse(json, 1)

        assertEquals(1, results.size)
        assertEquals("101", results.single().id)
        assertEquals("第一首", results.single().title)
        assertEquals("歌手甲 / 歌手乙", results.single().artist)
        assertEquals("专辑一", results.single().album)
        assertEquals(234000L, results.single().durationMs)
    }

    @Test
    fun normalizesDetailAcrossLegacyFieldNames() {
        val json = """
            {
              "songs": [{
                "name": "夜航",
                "ar": [{"name": "某位歌手"}],
                "al": {"name": "远方", "picUrl": "https://p1.music.126.net/cover.jpg"}
              }]
            }
        """.trimIndent()

        val song = NeteaseMusicService.normalizeDetailResponse(json, "7788")

        assertEquals("7788", song.id)
        assertEquals("夜航", song.title)
        assertEquals("某位歌手", song.artist)
        assertEquals("远方", song.album)
        assertEquals("https://p1.music.126.net/cover.jpg", song.coverUrl)
    }

    @Test
    fun removesLrcTimestampsAndMetadata() {
        val json = """
            {"lrc":{"lyric":"[ar:歌手]\n[00:01.00]第一行\n[00:02.20][00:03.30]第二行  \n"}}
        """.trimIndent()

        assertEquals("第一行\n第二行", NeteaseMusicService.normalizeLyricsResponse(json))
    }
}
