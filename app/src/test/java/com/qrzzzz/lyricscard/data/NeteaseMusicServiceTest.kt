package com.qrzzzz.lyricscard.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun invalidInputsFailBeforeOpeningAConnection() = runTest {
        var openCount = 0
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            openCount += 1
            FakeHttpsConnection(url)
        })

        expectError(NeteaseServiceError.INVALID_QUERY) { service.search("   ") }
        expectError(NeteaseServiceError.QUERY_TOO_LONG) { service.search("x".repeat(121)) }
        expectError(NeteaseServiceError.INVALID_SONG_ID) { service.resolveSong("12-not-an-id") }
        expectError(NeteaseServiceError.UNSAFE_URL) {
            service.downloadCover("http://p1.music.126.net/cover.jpg")
        }
        expectError(NeteaseServiceError.UNSAFE_URL) {
            service.downloadCover("https://music.126.net.evil.example/cover.jpg")
        }
        expectError(NeteaseServiceError.UNSAFE_URL) {
            service.downloadCover("https://user:secret@p1.music.126.net/cover.jpg")
        }
        expectError(NeteaseServiceError.UNSAFE_URL) {
            service.resolveSongIdFromInput("https://attacker.example/song?id=123")
        }

        assertEquals(0, openCount)
    }

    @Test
    fun apiRequestsUseTimeoutsAndFollowOnlyValidatedRedirects() = runTest {
        val connections = mutableListOf<FakeHttpsConnection>()
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            FakeHttpsConnection(
                url = url,
                status = if (connections.isEmpty()) 302 else 200,
                response = if (connections.isEmpty()) ByteArray(0) else SEARCH_EMPTY_RESPONSE,
                location = if (connections.isEmpty()) "/api/search/redirected" else null,
            ).also(connections::add)
        })

        assertTrue(service.search("night").isEmpty())

        assertEquals(2, connections.size)
        assertEquals("music.163.com", connections[0].url.host)
        assertEquals("music.163.com", connections[1].url.host)
        assertEquals("POST", connections[0].requestMethod)
        assertEquals("GET", connections[1].requestMethod)
        assertTrue(connections[0].requestBody.toString(Charsets.UTF_8.name()).contains("s=night"))
        connections.forEach { connection ->
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(NeteaseMusicService.CONNECT_TIMEOUT_MS, connection.connectTimeout)
            assertEquals(NeteaseMusicService.READ_TIMEOUT_MS, connection.readTimeout)
            assertTrue(connection.disconnected.get())
        }
    }

    @Test
    fun trustedShortLinkRedirectReturnsIdWithoutOpeningTheTarget() = runTest {
        var openCount = 0
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            openCount += 1
            FakeHttpsConnection(
                url = url,
                status = 302,
                location = "https://music.163.com/song?id=24680",
            )
        })

        assertEquals("24680", service.resolveSongIdFromInput("https://163cn.tv/short"))
        assertEquals(1, openCount)
    }

    @Test
    fun redirectTargetIsValidatedBeforeItsSongIdIsAccepted() = runTest {
        var openCount = 0
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            openCount += 1
            FakeHttpsConnection(
                url = url,
                status = 302,
                location = "https://attacker.example/song?id=24680",
            )
        })

        expectError(NeteaseServiceError.REDIRECT_NOT_ALLOWED) {
            service.resolveSongIdFromInput("https://163cn.tv/short")
        }
        assertEquals(1, openCount)
    }

    @Test
    fun shortLinkLoopIsRejectedDeterministically() = runTest {
        var openCount = 0
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            openCount += 1
            FakeHttpsConnection(url = url, status = 302, location = "https://163cn.tv:443/loop")
        })

        expectError(NeteaseServiceError.REDIRECT_LOOP) {
            service.resolveSongIdFromInput("https://163cn.tv/loop")
        }
        assertEquals(1, openCount)
    }

    @Test
    fun httpFailuresMapToStableDomainErrors() = runTest {
        val cases = mapOf(
            404 to NeteaseServiceError.REQUEST_REJECTED,
            429 to NeteaseServiceError.RATE_LIMITED,
            503 to NeteaseServiceError.SERVICE_UNAVAILABLE,
        )

        cases.forEach { (status, expected) ->
            val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
                FakeHttpsConnection(url = url, status = status)
            })
            val failure = expectError(expected) { service.search("test") }
            assertEquals(expected.userMessage, failure.message)
            assertNull(failure.cause)
            assertFalse(failure.message.orEmpty().contains(status.toString()))
        }
    }

    @Test
    fun emptyAndMalformedResponsesHaveDifferentStableErrors() = runTest {
        val emptyService = serviceReturning(ByteArray(0))
        expectError(NeteaseServiceError.EMPTY_RESPONSE) { emptyService.search("test") }

        val malformedService = serviceReturning("not-json".toByteArray())
        expectError(NeteaseServiceError.MALFORMED_RESPONSE) { malformedService.search("test") }
    }

    @Test
    fun dnsTlsTimeoutAndUnavailableNetworkDoNotLeakRawExceptions() = runTest {
        val cases = listOf(
            UnknownHostException("private-query.example") to NeteaseServiceError.DNS_FAILURE,
            SSLHandshakeException("certificate details for private proxy") to NeteaseServiceError.TLS_FAILURE,
            SocketTimeoutException("socket details") to NeteaseServiceError.TIMEOUT,
            IOException("raw network stack details") to NeteaseServiceError.NETWORK_UNAVAILABLE,
        )

        cases.forEach { (transportFailure, expected) ->
            val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
                FakeHttpsConnection(url = url, responseFailure = transportFailure)
            })
            val failure = expectError(expected) { service.search("private query") }
            assertEquals(expected.userMessage, failure.message)
            assertNull(failure.cause)
            assertFalse(failure.message.orEmpty().contains(transportFailure.message.orEmpty()))
            assertFalse(failure.toString().contains("java.net"))
        }
    }

    @Test
    fun oversizedDeclaredCoverIsRejectedBeforeReadingTheBody() = runTest {
        val inputRead = AtomicBoolean(false)
        val connection = FakeHttpsConnection(
            url = URL("https://p1.music.126.net/cover.jpg"),
            declaredLength = NeteaseMusicService.MAX_COVER_BYTES.toLong() + 1,
            input = object : InputStream() {
                override fun read(): Int {
                    inputRead.set(true)
                    return -1
                }
            },
        )
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { connection })

        expectError(NeteaseServiceError.RESPONSE_TOO_LARGE) {
            service.downloadCover(connection.url.toString())
        }

        assertFalse(inputRead.get())
        assertTrue(connection.disconnected.get())
    }

    @Test
    fun chunkedResponseIsStillBoundedWhenContentLengthIsUnknown() = runTest {
        val oversized = ByteArray(NeteaseMusicService.MAX_JSON_BYTES + 1) { 'x'.code.toByte() }
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            FakeHttpsConnection(url = url, response = oversized, declaredLength = -1)
        })

        expectError(NeteaseServiceError.RESPONSE_TOO_LARGE) { service.search("test") }
    }

    @Test
    fun cancellingARequestDisconnectsTheConnectionAndStaysCancellation() = runBlocking {
        val blockingInput = BlockingInputStream()
        val connection = FakeHttpsConnection(
            url = URL("https://p1.music.126.net/cover.jpg"),
            input = blockingInput,
            declaredLength = -1,
        )
        val service = NeteaseMusicService(NeteaseHttpsConnectionFactory { connection })
        val request = async(Dispatchers.Default) {
            service.downloadCover(connection.url.toString())
        }

        assertTrue("request did not start", blockingInput.started.await(2, TimeUnit.SECONDS))
        withTimeout(2_000) { request.cancelAndJoin() }

        assertTrue(request.isCancelled)
        assertTrue(connection.disconnected.get())
        assertTrue(blockingInput.closed.get())
    }

    private fun serviceReturning(body: ByteArray): NeteaseMusicService =
        NeteaseMusicService(NeteaseHttpsConnectionFactory { url ->
            FakeHttpsConnection(url = url, response = body)
        })

    private suspend fun expectError(
        expected: NeteaseServiceError,
        block: suspend () -> Any?,
    ): NeteaseServiceException {
        try {
            block()
            fail("Expected $expected")
        } catch (failure: NeteaseServiceException) {
            assertEquals(expected, failure.error)
            return failure
        }
        throw AssertionError("Expected $expected")
    }

    private class FakeHttpsConnection(
        url: URL,
        private val status: Int = 200,
        private val response: ByteArray = ByteArray(0),
        private val location: String? = null,
        private val responseFailure: IOException? = null,
        private val declaredLength: Long = response.size.toLong(),
        private val input: InputStream? = null,
    ) : HttpsURLConnection(url) {
        val requestBody = ByteArrayOutputStream()
        val disconnected = AtomicBoolean(false)

        override fun connect() = Unit

        override fun disconnect() {
            disconnected.set(true)
            input?.close()
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseFailure?.let { throw it }
            return status
        }

        override fun getHeaderField(name: String?): String? =
            if (name.equals("Location", ignoreCase = true)) location else null

        override fun getContentLengthLong(): Long = declaredLength

        override fun getInputStream(): InputStream = input ?: ByteArrayInputStream(response)

        override fun getOutputStream(): ByteArrayOutputStream = requestBody

        override fun getCipherSuite(): String = "TLS_FAKE"

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private class BlockingInputStream : InputStream() {
        val started = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        private val released = CountDownLatch(1)

        override fun read(): Int {
            started.countDown()
            released.await()
            if (closed.get()) throw IOException("connection closed")
            return -1
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = read()

        override fun close() {
            closed.set(true)
            released.countDown()
        }
    }

    private companion object {
        val SEARCH_EMPTY_RESPONSE = """{"result":{"songs":[]}}""".toByteArray()
    }
}
