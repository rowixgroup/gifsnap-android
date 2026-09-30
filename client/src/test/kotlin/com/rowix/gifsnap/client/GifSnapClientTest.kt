package com.rowix.gifsnap.client

import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class GifSnapClientTest {
    private val row = """{"id":"klipy_9572734031877654","title":"Hello","url":"https://cdn.example/a.gif?token=one&size=full","preview_url":"https://cdn.example/a.webp?size=small","width":100,"height":80,"type":"gif","source":"klipy","content_id":"klipy:9572734031877654"}"""
    private fun response(rows: String = row, page: Int = 1, hasNext: Boolean = true, next: String = "2") =
        """{"data":[$rows],"pagination":{"page":$page,"limit":24,"total":9007199254740993,"has_next":$hasNext,"next_page":$next,"offset":0},"query":"cats"}"""

    @Test fun preservesIdsUrlsContentIdentityAndRawCounts() {
        val page = parsePage(response("$row,$row"))
        assertEquals(2, page.data.size)
        assertEquals("klipy_9572734031877654", page.data.first().id)
        assertEquals("klipy:9572734031877654", page.data.first().contentId)
        assertEquals("https://cdn.example/a.gif?token=one&size=full", page.data.first().url)
        assertEquals(9007199254740993L, page.pagination.total)
    }
    @Test fun optionalSourceAndContentIdentityAreSupported() {
        val page = parsePage(response(row.replace(",\"source\":\"klipy\",\"content_id\":\"klipy:9572734031877654\"", "")))
        assertNull(page.data.first().source); assertNull(page.data.first().contentId)
    }
    @Test fun rejectsUnsafeUrlsAndMalformedModels() {
        for (bad in listOf("javascript:alert(1)", "file:///tmp/x", "https://user:pass@example.com/x", "https://example.com/a b")) {
            assertThrows(GifSnapException::class.java) { parsePage(response(row.replace("https://cdn.example/a.gif?token=one&size=full", bad))) }
        }
        for (bad in listOf("{}", "[]", response().replace("\"width\":100", "\"width\":-1"),
            response().replace("\"content_id\":\"klipy:9572734031877654\"", "\"content_id\":\"\""),
            response().replace("\"next_page\":2", "\"next_page\":1"),
            response().replace("\"type\":\"gif\"", "\"type\":\"video\""),
            response().replace("\"limit\":24", "\"limit\":51"),
            response().replace("\"has_next\":true", "\"has_next\":false"))) {
            assertThrows(GifSnapException::class.java) { parsePage(bad) }
        }
    }
    @Test fun dedupKeepsFirstAndDoesNotMergeTitleOrQueryVariants() {
        val a = parsePage(response()).data.first()
        val alias = a.copy(id = "alias", url = "https://cdn.example/other.webp")
        val different = a.copy(id = "different", url = a.url + "&different=yes", contentId = null)
        val titleOnly = a.copy(id = "title", url = "https://cdn.example/new.gif", contentId = "klipy:other")
        assertEquals(listOf(a, different, titleOnly), distinctGifs(listOf(a, alias, different, titleOnly, a)))
    }
    @Test fun encodesQueryAndUsesAllFourEndpoints() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val client = GifSnapClient(server.url("/api/v1").toString())
            repeat(4) { server.enqueue(MockResponse().setBody(response())) }
            client.search("cats & dogs?/", MediaType.Gifs, 2, 12)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/gifs/search", request.requestUrl!!.encodedPath)
            assertEquals("cats & dogs?/", request.requestUrl!!.queryParameter("q"))
            assertEquals("2", request.requestUrl!!.queryParameter("page"))
            assertEquals("12", request.requestUrl!!.queryParameter("limit"))
            client.trending(MediaType.Gifs); assertEquals("/api/v1/gifs/trending", server.takeRequest().requestUrl!!.encodedPath)
            client.search("x", MediaType.Stickers); assertEquals("/api/v1/stickers/search", server.takeRequest().requestUrl!!.encodedPath)
            client.trending(MediaType.Stickers); assertEquals("/api/v1/stickers/trending", server.takeRequest().requestUrl!!.encodedPath)
        } finally { server.shutdown() }
    }
    @Test fun surfaces429AndDoesNotRetry() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "42"))
            val error = try { GifSnapClient(server.url("/").toString()).trending(); error("Expected failure") } catch (e: GifSnapException) { e }
            assertEquals(GifSnapErrorCode.Http, error.code); assertEquals(429, error.statusCode); assertEquals("42", error.retryAfter)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun requestTimeoutIsBounded() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val error = try { GifSnapClient(server.url("/").toString(), 100).trending(); error("Expected timeout") } catch (e: GifSnapException) { e }
            assertEquals(GifSnapErrorCode.Timeout, error.code)
        } finally { server.shutdown() }
    }
    @Test fun cancellationPropagatesWithoutSdkError() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = async { GifSnapClient(server.url("/").toString()).trending() }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun invalidArgumentsNeverIssueRequests(): Unit = runBlocking {
        val client = GifSnapClient()
        for (action in listOf<suspend () -> Unit>({ client.search(" ") }, { client.trending(page = 0) }, { client.trending(limit = 51) })) {
            try { action(); fail("Invalid arguments accepted") } catch (_: IllegalArgumentException) { }
        }
        assertThrows(IllegalArgumentException::class.java) { GifSnapClient("https://example.com?x=1") }
        assertThrows(IllegalArgumentException::class.java) { GifSnapClient(timeoutMillis = 0) }
    }
    @Test fun rejectsOversizedResponseAndRedirects() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("x".repeat(4 * 1024 * 1024 + 1)))
            val client = GifSnapClient(server.url("/").toString())
            try { client.trending(); fail("Oversized response accepted") } catch (e: GifSnapException) { assertEquals(GifSnapErrorCode.InvalidResponse, e.code) }
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.org"))
            try { client.trending(); fail("Redirect followed") } catch (e: GifSnapException) { assertEquals(302, e.statusCode) }
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
