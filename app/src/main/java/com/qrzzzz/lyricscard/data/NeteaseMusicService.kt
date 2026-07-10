package com.qrzzzz.lyricscard.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class NeteaseSongSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long? = null,
    val coverUrl: String = "",
)

data class ResolvedNeteaseSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val lyrics: String,
    val coverUrl: String,
)

/** Small HTTPS-only client for the two NetEase entry points used by the editor. */
class NeteaseMusicService {
    suspend fun search(keyword: String, limit: Int = 8): List<NeteaseSongSearchResult> = withContext(Dispatchers.IO) {
        val normalized = keyword.trim()
        require(normalized.isNotEmpty()) { "请输入歌曲名或歌手" }
        require(normalized.length <= 120) { "搜索内容不能超过 120 个字符" }
        val safeLimit = limit.coerceIn(1, 20)
        val body = listOf(
            "s" to normalized,
            "limit" to safeLimit.toString(),
            "type" to "1",
            "offset" to "0",
        ).joinToString("&") { (key, value) ->
            "${encodeForm(key)}=${encodeForm(value)}"
        }.toByteArray(Charsets.UTF_8)
        val response = request(
            url = SEARCH_ENDPOINT,
            method = "POST",
            headers = NETEASE_HEADERS + mapOf(
                "Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8",
                "Cookie" to "appver=2.0.2",
            ),
            body = body,
            maxBytes = MAX_JSON_BYTES,
        )
        normalizeSearchResponse(response.decodeToString(), safeLimit)
    }

    suspend fun resolveSong(id: String): ResolvedNeteaseSong = supervisorScope {
        require(SONG_ID.matches(id)) { "网易云歌曲 ID 无效" }
        val detail = async(Dispatchers.IO) {
            val json = request(
                url = "$DETAIL_ENDPOINT?ids=[${encodeForm(id)}]",
                headers = NETEASE_HEADERS,
                maxBytes = MAX_JSON_BYTES,
            )
            normalizeDetailResponse(json.decodeToString(), id)
        }
        val lyrics = async(Dispatchers.IO) {
            runCatching {
                val json = request(
                    url = "$LYRIC_ENDPOINT?id=${encodeForm(id)}&lv=1&kv=1&tv=-1",
                    headers = NETEASE_HEADERS,
                    maxBytes = MAX_LYRIC_JSON_BYTES,
                )
                normalizeLyricsResponse(json.decodeToString())
            }.getOrDefault("")
        }
        detail.await().copy(lyrics = lyrics.await())
    }

    suspend fun resolveLink(input: String): ResolvedNeteaseSong {
        val songId = resolveSongIdFromInput(input)
        return resolveSong(songId)
    }

    suspend fun downloadCover(url: String): ByteArray = withContext(Dispatchers.IO) {
        val parsed = URI(url)
        require(parsed.scheme.equals("https", ignoreCase = true)) { "封面地址不是安全的 HTTPS 链接" }
        require(isAllowedCoverHost(parsed.host)) { "封面地址不属于网易云图片域名" }
        request(
            url = parsed.toASCIIString(),
            headers = REQUEST_HEADERS,
            maxBytes = MAX_COVER_BYTES,
        )
    }

    internal suspend fun resolveSongIdFromInput(input: String): String = withContext(Dispatchers.IO) {
        val extractedUrl = extractFirstUrl(input) ?: throw IllegalArgumentException("没有找到可用的网易云链接")
        var current = URI(extractedUrl)
        require(current.scheme.equals("https", ignoreCase = true)) { "仅支持 HTTPS 网易云链接" }
        require(isAllowedLinkHost(current.host)) { "仅支持网易云音乐链接" }
        parseSongId(extractedUrl)?.let { return@withContext it }
        repeat(MAX_REDIRECTS + 1) {
            require(current.scheme.equals("https", ignoreCase = true)) { "仅支持 HTTPS 网易云链接" }
            require(isAllowedLinkHost(current.host)) { "仅支持网易云音乐链接" }
            val connection = (current.toURL().openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                REQUEST_HEADERS.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalArgumentException("网易云短链接没有返回跳转地址")
                    current = current.resolve(location)
                    parseSongId(current.toASCIIString())?.let { return@withContext it }
                } else {
                    parseSongId(connection.url.toString())?.let { return@withContext it }
                    throw IllegalArgumentException("链接中没有找到网易云歌曲 ID")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalArgumentException("网易云短链接跳转次数过多")
    }

    internal companion object {
        private const val SEARCH_ENDPOINT = "https://music.163.com/api/search/get/web"
        private const val DETAIL_ENDPOINT = "https://music.163.com/api/song/detail"
        private const val LYRIC_ENDPOINT = "https://music.163.com/api/song/lyric"
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_JSON_BYTES = 2 * 1024 * 1024
        private const val MAX_LYRIC_JSON_BYTES = 4 * 1024 * 1024
        private const val MAX_COVER_BYTES = 25 * 1024 * 1024
        private val SONG_ID = Regex("^\\d{1,32}$")
        private val URL_PATTERN = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE)
        private val REQUEST_HEADERS = mapOf(
            "Accept" to "application/json,text/html;q=0.9,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36",
        )
        private val NETEASE_HEADERS = REQUEST_HEADERS + mapOf("Referer" to "https://music.163.com/")
        private val parser = Json { ignoreUnknownKeys = true; isLenient = false }

        internal fun extractFirstUrl(input: String): String? = URL_PATTERN.find(input.trim())
            ?.value
            ?.trimEnd('。', '，', ',', '.', '！', '!', '？', '?', '）', ')', '】', ']', '"', '\'')

        internal fun parseSongId(input: String): String? {
            val normalized = input.replace("/#/song?", "/song?", ignoreCase = true)
            return runCatching {
                val uri = URI(normalized)
                val queryId = parseQuery(uri.rawQuery)["id"]
                val fragmentId = uri.rawFragment
                    ?.substringAfter('?', missingDelimiterValue = "")
                    ?.let(::parseQuery)
                    ?.get("id")
                (queryId ?: fragmentId)?.takeIf(SONG_ID::matches)
            }.getOrNull() ?: Regex("[?&#]id=(\\d{1,32})(?:\\D|$)", RegexOption.IGNORE_CASE)
                .find(normalized)
                ?.groupValues
                ?.get(1)
        }

        internal fun normalizeSearchResponse(value: String, limit: Int): List<NeteaseSongSearchResult> {
            val root = parser.parseToJsonElement(value).jsonObject
            val songs = root.objectValue("result")?.arrayValue("songs").orEmpty()
            return songs.mapNotNull(::searchResultFromJson).take(limit.coerceIn(1, 20))
        }

        internal fun normalizeDetailResponse(value: String, id: String): ResolvedNeteaseSong {
            val root = parser.parseToJsonElement(value).jsonObject
            val song = root.arrayValue("songs").firstOrNull()?.asObject()
                ?: throw IllegalArgumentException("网易云没有返回歌曲信息")
            val title = song.stringValue("name")
            require(title.isNotBlank()) { "网易云没有返回歌曲名" }
            val album = song.objectValue("album") ?: song.objectValue("al")
            return ResolvedNeteaseSong(
                id = id,
                title = title,
                artist = artistsFrom(song).joinToString(" / "),
                album = album?.stringValue("name").orEmpty(),
                lyrics = "",
                coverUrl = album?.stringValue("picUrl").orEmpty()
                    .ifBlank { album?.stringValue("blurPicUrl").orEmpty() },
            )
        }

        internal fun normalizeLyricsResponse(value: String): String {
            val root = parser.parseToJsonElement(value).jsonObject
            val raw = root.objectValue("lrc")?.stringValue("lyric").orEmpty()
            return raw.lineSequence()
                .map { line -> line.replace(LRC_TIMESTAMPS, "").trimEnd() }
                .filterNot { line -> LRC_METADATA.containsMatchIn(line.trim()) }
                .joinToString("\n")
                .trim()
        }

        private fun searchResultFromJson(element: JsonElement): NeteaseSongSearchResult? {
            val song = element.asObject() ?: return null
            val id = song["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val title = song.stringValue("name")
            if (!SONG_ID.matches(id) || title.isBlank()) return null
            val album = song.objectValue("album") ?: song.objectValue("al")
            return NeteaseSongSearchResult(
                id = id,
                title = title,
                artist = artistsFrom(song).joinToString(" / "),
                album = album?.stringValue("name").orEmpty(),
                durationMs = song["duration"]?.jsonPrimitive?.longOrNull
                    ?: song["dt"]?.jsonPrimitive?.longOrNull,
                coverUrl = album?.stringValue("picUrl").orEmpty()
                    .ifBlank { album?.stringValue("blurPicUrl").orEmpty() },
            )
        }

        private fun artistsFrom(song: JsonObject): List<String> {
            val artists = song.arrayValue("artists").ifEmpty { song.arrayValue("ar") }
            return artists.mapNotNull { it.asObject()?.stringValue("name")?.takeIf(String::isNotBlank) }
        }

        private fun parseQuery(value: String?): Map<String, String> = value.orEmpty()
            .split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
            }
            .toMap()

        private fun isAllowedLinkHost(host: String?): Boolean {
            val value = host?.lowercase().orEmpty()
            return value == "music.163.com" || value.endsWith(".music.163.com") || value == "163cn.tv"
        }

        private fun isAllowedCoverHost(host: String?): Boolean {
            val value = host?.lowercase().orEmpty()
            return value.endsWith(".music.126.net") || value.endsWith(".music.163.com")
        }

        private fun request(
            url: String,
            method: String = "GET",
            headers: Map<String, String>,
            body: ByteArray? = null,
            maxBytes: Int,
        ): ByteArray {
            val parsed = URI(url)
            require(parsed.scheme.equals("https", ignoreCase = true)) { "仅允许 HTTPS 请求" }
            val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (body != null) {
                    doOutput = true
                    setFixedLengthStreamingMode(body.size)
                }
            }
            try {
                body?.let { connection.outputStream.use { output -> output.write(it) } }
                val status = connection.responseCode
                require(status in 200..299) { "网易云服务返回 HTTP $status" }
                val declaredLength = connection.contentLengthLong
                require(declaredLength <= 0 || declaredLength <= maxBytes) { "网易云响应超过大小限制" }
                val output = ByteArrayOutputStream(
                    declaredLength.takeIf { it in 1..maxBytes.toLong() }?.toInt() ?: DEFAULT_BUFFER_SIZE,
                )
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { "网易云响应超过大小限制" }
                        output.write(buffer, 0, read)
                    }
                }
                return output.toByteArray()
            } finally {
                connection.disconnect()
            }
        }

        private fun encodeForm(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
        private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
        private fun JsonObject.objectValue(name: String): JsonObject? = get(name) as? JsonObject
        private fun JsonObject.arrayValue(name: String): List<JsonElement> =
            runCatching { get(name)?.jsonArray?.toList().orEmpty() }.getOrDefault(emptyList())
        private fun JsonObject.stringValue(name: String): String =
            get(name)?.jsonPrimitive?.contentOrNull.orEmpty().trim()

        private val LRC_TIMESTAMPS = Regex("(?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?])+")
        private val LRC_METADATA = Regex("^\\[(ar|ti|al|by|offset):", RegexOption.IGNORE_CASE)
    }
}
