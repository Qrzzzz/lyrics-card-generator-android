package com.qrzzzz.lyricscard.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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

/** Stable failures that callers can map without exposing transport exception text. */
enum class NeteaseServiceError(val userMessage: String) {
    INVALID_QUERY("请输入歌曲名或歌手"),
    QUERY_TOO_LONG("搜索内容不能超过 120 个字符"),
    INVALID_SONG_ID("网易云歌曲 ID 无效"),
    INVALID_LINK("没有找到可用的网易云音乐链接"),
    UNSAFE_URL("仅支持受信任的 HTTPS 网易云音乐地址"),
    REDIRECT_NOT_ALLOWED("网易云音乐链接跳转到了不受信任的地址"),
    REDIRECT_LOOP("网易云音乐链接发生循环跳转"),
    TOO_MANY_REDIRECTS("网易云音乐链接跳转次数过多"),
    REQUEST_REJECTED("网易云音乐请求被拒绝，请稍后重试"),
    RATE_LIMITED("请求过于频繁，请稍后重试"),
    SERVICE_UNAVAILABLE("网易云音乐服务暂时不可用，请稍后重试"),
    EMPTY_RESPONSE("网易云音乐没有返回数据，请稍后重试"),
    MALFORMED_RESPONSE("网易云音乐返回了无法识别的数据"),
    RESPONSE_TOO_LARGE("网易云音乐返回的数据过大"),
    TIMEOUT("网络请求超时，请稍后重试"),
    NETWORK_UNAVAILABLE("网络连接不可用，请检查网络后重试"),
    DNS_FAILURE("无法解析网易云音乐服务地址，请检查网络后重试"),
    TLS_FAILURE("无法建立安全连接，请检查系统时间或网络后重试"),
    SONG_NOT_FOUND("网易云音乐没有返回歌曲信息"),
}

class NeteaseServiceException(
    val error: NeteaseServiceError,
) : IOException(error.userMessage)

internal fun interface NeteaseHttpsConnectionFactory {
    fun open(url: URL): HttpsURLConnection
}

/** Small HTTPS-only client for the two NetEase entry points used by the editor. */
class NeteaseMusicService internal constructor(
    private val connectionFactory: NeteaseHttpsConnectionFactory,
) {
    constructor() : this(
        NeteaseHttpsConnectionFactory { url ->
            url.openConnection() as HttpsURLConnection
        },
    )

    suspend fun search(keyword: String, limit: Int = 8): List<NeteaseSongSearchResult> =
        withContext(Dispatchers.IO) {
            val normalized = keyword.trim()
            if (normalized.isEmpty()) fail(NeteaseServiceError.INVALID_QUERY)
            if (normalized.length > 120) fail(NeteaseServiceError.QUERY_TOO_LONG)
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
                allowedHost = ::isAllowedApiHost,
            )
            parseResponse { normalizeSearchResponse(response.decodeToString(), safeLimit) }
        }

    suspend fun resolveSong(id: String): ResolvedNeteaseSong {
        if (!SONG_ID.matches(id)) fail(NeteaseServiceError.INVALID_SONG_ID)
        return supervisorScope {
            val detail = async(Dispatchers.IO) {
                val json = request(
                    url = "$DETAIL_ENDPOINT?ids=[${encodeForm(id)}]",
                    headers = NETEASE_HEADERS,
                    maxBytes = MAX_JSON_BYTES,
                    allowedHost = ::isAllowedApiHost,
                )
                parseResponse { normalizeDetailResponse(json.decodeToString(), id) }
            }
            val lyrics = async(Dispatchers.IO) {
                try {
                    val json = request(
                        url = "$LYRIC_ENDPOINT?id=${encodeForm(id)}&lv=1&kv=1&tv=-1",
                        headers = NETEASE_HEADERS,
                        maxBytes = MAX_LYRIC_JSON_BYTES,
                        allowedHost = ::isAllowedApiHost,
                    )
                    parseResponse { normalizeLyricsResponse(json.decodeToString()) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: NeteaseServiceException) {
                    // Lyrics are optional; preserve the existing metadata-only fallback.
                    ""
                }
            }
            detail.await().copy(lyrics = lyrics.await())
        }
    }

    suspend fun resolveLink(input: String): ResolvedNeteaseSong {
        val songId = resolveSongIdFromInput(input)
        return resolveSong(songId)
    }

    suspend fun downloadCover(url: String): ByteArray = withContext(Dispatchers.IO) {
        val parsed = parseTrustedUri(url, ::isAllowedCoverHost, NeteaseServiceError.UNSAFE_URL)
        request(
            url = parsed.toASCIIString(),
            headers = REQUEST_HEADERS,
            maxBytes = MAX_COVER_BYTES,
            allowedHost = ::isAllowedCoverHost,
        )
    }

    internal suspend fun resolveSongIdFromInput(input: String): String = withContext(Dispatchers.IO) {
        val extractedUrl = extractFirstUrl(input) ?: fail(NeteaseServiceError.INVALID_LINK)
        var current = parseTrustedUri(extractedUrl, ::isAllowedLinkHost, NeteaseServiceError.UNSAFE_URL)
        parseSongId(current.toASCIIString())?.let { return@withContext it }

        val visited = mutableSetOf<String>()
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!visited.add(redirectIdentity(current))) fail(NeteaseServiceError.REDIRECT_LOOP)
            val connection = openConnection(current)
            val cancellationHandle = connection.disconnectOnCancellation()
            try {
                currentCoroutineContext().ensureActive()
                connection.apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    REQUEST_HEADERS.forEach { (name, value) -> setRequestProperty(name, value) }
                }
                val status = connection.responseCode
                if (status in REDIRECT_STATUSES) {
                    if (hop == MAX_REDIRECTS) fail(NeteaseServiceError.TOO_MANY_REDIRECTS)
                    val target = resolveRedirect(current, connection.getHeaderField("Location"), ::isAllowedLinkHost)
                    current = target
                    // Validate the redirect target before accepting an ID from it.
                    parseSongId(current.toASCIIString())?.let { return@withContext it }
                } else if (status in 200..299) {
                    fail(NeteaseServiceError.INVALID_LINK)
                } else {
                    fail(errorForStatus(status))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NeteaseServiceException) {
                throw failure
            } catch (cause: IOException) {
                currentCoroutineContext().ensureActive()
                fail(errorForTransport(cause))
            } catch (_: SecurityException) {
                currentCoroutineContext().ensureActive()
                fail(NeteaseServiceError.NETWORK_UNAVAILABLE)
            } finally {
                cancellationHandle.dispose()
                connection.disconnect()
            }
        }
        fail(NeteaseServiceError.TOO_MANY_REDIRECTS)
    }

    private suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String>,
        body: ByteArray? = null,
        maxBytes: Int,
        allowedHost: (String?) -> Boolean,
    ): ByteArray {
        var current = parseTrustedUri(url, allowedHost, NeteaseServiceError.UNSAFE_URL)
        var currentMethod = method
        var currentBody = body
        val visited = mutableSetOf<String>()

        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!visited.add(redirectIdentity(current))) fail(NeteaseServiceError.REDIRECT_LOOP)
            val connection = openConnection(current)
            val cancellationHandle = connection.disconnectOnCancellation()
            try {
                currentCoroutineContext().ensureActive()
                connection.apply {
                    requestMethod = currentMethod
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (currentBody != null) {
                        doOutput = true
                        setFixedLengthStreamingMode(currentBody!!.size)
                    }
                }
                currentBody?.let { requestBody ->
                    connection.outputStream.use { output -> output.write(requestBody) }
                }

                val status = connection.responseCode
                if (status in REDIRECT_STATUSES) {
                    if (hop == MAX_REDIRECTS) fail(NeteaseServiceError.TOO_MANY_REDIRECTS)
                    current = resolveRedirect(current, connection.getHeaderField("Location"), allowedHost)
                    if (status == HttpURLConnection.HTTP_SEE_OTHER ||
                        ((status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP) &&
                            currentMethod == "POST")
                    ) {
                        currentMethod = "GET"
                        currentBody = null
                    }
                    return@repeat
                }
                if (status !in 200..299) fail(errorForStatus(status))

                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) fail(NeteaseServiceError.RESPONSE_TOO_LARGE)
                val output = ByteArrayOutputStream(
                    declaredLength.takeIf { it in 1..maxBytes.toLong() }?.toInt() ?: DEFAULT_BUFFER_SIZE,
                )
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) fail(NeteaseServiceError.RESPONSE_TOO_LARGE)
                        output.write(buffer, 0, read)
                    }
                }
                if (output.size() == 0) fail(NeteaseServiceError.EMPTY_RESPONSE)
                return output.toByteArray()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NeteaseServiceException) {
                throw failure
            } catch (cause: IOException) {
                currentCoroutineContext().ensureActive()
                fail(errorForTransport(cause))
            } catch (_: SecurityException) {
                currentCoroutineContext().ensureActive()
                fail(NeteaseServiceError.NETWORK_UNAVAILABLE)
            } finally {
                cancellationHandle.dispose()
                connection.disconnect()
            }
        }
        fail(NeteaseServiceError.TOO_MANY_REDIRECTS)
    }

    private fun openConnection(uri: URI): HttpsURLConnection = try {
        connectionFactory.open(uri.toURL())
    } catch (cause: IOException) {
        fail(errorForTransport(cause))
    } catch (_: SecurityException) {
        fail(NeteaseServiceError.NETWORK_UNAVAILABLE)
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun HttpsURLConnection.disconnectOnCancellation(): DisposableHandle =
        currentCoroutineContext().job.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true,
        ) { cause ->
            if (cause is CancellationException) disconnect()
        }

    internal companion object {
        private const val SEARCH_ENDPOINT = "https://music.163.com/api/search/get/web"
        private const val DETAIL_ENDPOINT = "https://music.163.com/api/song/detail"
        private const val LYRIC_ENDPOINT = "https://music.163.com/api/song/lyric"
        internal const val CONNECT_TIMEOUT_MS = 6_000
        internal const val READ_TIMEOUT_MS = 8_000
        internal const val MAX_REDIRECTS = 5
        internal const val MAX_JSON_BYTES = 2 * 1024 * 1024
        internal const val MAX_LYRIC_JSON_BYTES = 4 * 1024 * 1024
        internal const val MAX_COVER_BYTES = 25 * 1024 * 1024
        private val SONG_ID = Regex("^\\d{1,32}$")
        private val URL_PATTERN = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE)
        private val REDIRECT_STATUSES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
        private val REQUEST_HEADERS = mapOf(
            "Accept" to "application/json,text/html;q=0.9,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36",
        )
        private val NETEASE_HEADERS = REQUEST_HEADERS + mapOf("Referer" to "https://music.163.com/")
        private val parser = Json { ignoreUnknownKeys = true; isLenient = false }

        internal fun extractFirstUrl(input: String): String? = URL_PATTERN.find(input.trim())
            ?.value
            ?.trimEnd('。', '，', ',', '.', '；', '!', '！', '?', '？', ')', '）', ']', '"', '\'')

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
                ?: fail(NeteaseServiceError.SONG_NOT_FOUND)
            val title = song.stringValue("name")
            if (title.isBlank()) fail(NeteaseServiceError.MALFORMED_RESPONSE)
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

        private fun isAllowedApiHost(host: String?): Boolean = host.equals("music.163.com", ignoreCase = true)

        private fun isAllowedLinkHost(host: String?): Boolean {
            val value = host?.lowercase().orEmpty()
            return value == "music.163.com" || value.endsWith(".music.163.com") || value == "163cn.tv"
        }

        private fun isAllowedCoverHost(host: String?): Boolean {
            val value = host?.lowercase().orEmpty()
            return value.endsWith(".music.126.net") || value.endsWith(".music.163.com")
        }

        private fun parseTrustedUri(
            value: String,
            allowedHost: (String?) -> Boolean,
            error: NeteaseServiceError,
        ): URI {
            val uri = try {
                URI(value)
            } catch (_: Exception) {
                fail(error)
            }
            if (!uri.isAbsolute ||
                !uri.scheme.equals("https", ignoreCase = true) ||
                uri.rawUserInfo != null ||
                (uri.port != -1 && uri.port != 443) ||
                !allowedHost(uri.host)
            ) {
                fail(error)
            }
            return uri
        }

        private fun resolveRedirect(
            base: URI,
            location: String?,
            allowedHost: (String?) -> Boolean,
        ): URI {
            if (location.isNullOrBlank()) fail(NeteaseServiceError.REDIRECT_NOT_ALLOWED)
            val target = try {
                base.resolve(location)
            } catch (_: Exception) {
                fail(NeteaseServiceError.REDIRECT_NOT_ALLOWED)
            }
            return parseTrustedUri(
                target.toASCIIString(),
                allowedHost,
                NeteaseServiceError.REDIRECT_NOT_ALLOWED,
            )
        }

        private fun redirectIdentity(uri: URI): String = URI(
            uri.scheme?.lowercase(),
            null,
            uri.host?.lowercase(),
            uri.port.takeUnless { it == 443 } ?: -1,
            uri.path.orEmpty().ifEmpty { "/" },
            uri.query,
            null,
        ).normalize().toASCIIString()

        private fun errorForStatus(status: Int): NeteaseServiceError = when {
            status == 429 -> NeteaseServiceError.RATE_LIMITED
            status in 400..499 -> NeteaseServiceError.REQUEST_REJECTED
            status in 500..599 -> NeteaseServiceError.SERVICE_UNAVAILABLE
            else -> NeteaseServiceError.MALFORMED_RESPONSE
        }

        private fun errorForTransport(cause: IOException): NeteaseServiceError = when (cause) {
            is SocketTimeoutException -> NeteaseServiceError.TIMEOUT
            is UnknownHostException -> NeteaseServiceError.DNS_FAILURE
            is SSLException -> NeteaseServiceError.TLS_FAILURE
            is ConnectException, is NoRouteToHostException -> NeteaseServiceError.NETWORK_UNAVAILABLE
            else -> NeteaseServiceError.NETWORK_UNAVAILABLE
        }

        private inline fun <T> parseResponse(block: () -> T): T = try {
            block()
        } catch (failure: NeteaseServiceException) {
            throw failure
        } catch (_: Exception) {
            fail(NeteaseServiceError.MALFORMED_RESPONSE)
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

        private fun fail(error: NeteaseServiceError): Nothing = throw NeteaseServiceException(error)
    }
}
