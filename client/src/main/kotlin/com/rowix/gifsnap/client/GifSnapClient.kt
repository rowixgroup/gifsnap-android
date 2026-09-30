package com.rowix.gifsnap.client

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Values are preserved exactly as returned, including provider IDs and URL queries. */
data class GifItem(
    val id: String,
    val title: String,
    val url: String,
    val previewUrl: String,
    val width: Int,
    val height: Int,
    val type: String,
    val source: String? = null,
    val contentId: String? = null,
)

enum class MediaType(internal val path: String) { Gifs("gifs"), Stickers("stickers") }

data class GifPagination(
    val page: Int,
    val limit: Int,
    val total: Long,
    val hasNext: Boolean,
    val nextPage: Int?,
    val offset: Long,
)

data class GifPage(val data: List<GifItem>, val pagination: GifPagination, val query: String? = null)

enum class GifSnapErrorCode { Http, Network, Timeout, InvalidResponse }
class GifSnapException(
    val code: GifSnapErrorCode,
    message: String,
    val statusCode: Int? = null,
    /** Raw Retry-After header: seconds or HTTP date. No automatic retry is performed. */
    val retryAfter: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Injectable data source for app-specific state, previews and deterministic tests. */
interface GifSnapDataSource {
    suspend fun search(query: String, mediaType: MediaType = MediaType.Gifs, page: Int = 1, limit: Int = 24): GifPage
    suspend fun trending(mediaType: MediaType = MediaType.Gifs, page: Int = 1, limit: Int = 24): GifPage
}

/**
 * Anonymous public API client. Requests run off the main thread and cancellation cancels the HTTP call.
 * No telemetry, background requests, credentials, automatic retries, or provider fallbacks.
 */
class GifSnapClient(
    baseUrl: String = "https://gifsnap.com/api/v1",
    timeoutMillis: Long = 15_000,
) : GifSnapDataSource {
    private val base: HttpUrl = requireSafeUrl(baseUrl).also {
        require(it.query == null && it.fragment == null) { "baseUrl must not include a query or fragment" }
    }
    private val http = OkHttpClient.Builder()
        .callTimeout(timeoutMillis.also { require(it in 1..120_000) { "timeoutMillis must be between 1 and 120000" } }, TimeUnit.MILLISECONDS)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun search(query: String, mediaType: MediaType, page: Int, limit: Int): GifPage {
        require(query.isNotBlank() && query.length <= 200) { "query must contain 1–200 characters" }
        return request(mediaType, "search", query, page, limit)
    }

    override suspend fun trending(mediaType: MediaType, page: Int, limit: Int): GifPage =
        request(mediaType, "trending", null, page, limit)

    private suspend fun request(type: MediaType, operation: String, query: String?, page: Int, limit: Int): GifPage {
        require(page in 1..1_000_000) { "page must be between 1 and 1000000" }
        require(limit in 1..50) { "limit must be between 1 and 50" }
        val url = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/${type.path}/$operation")
            .addQueryParameter("page", page.toString()).addQueryParameter("limit", limit.toString())
            .apply { if (query != null) addQueryParameter("q", query) }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(GifSnapException(
                        if (e is InterruptedIOException) GifSnapErrorCode.Timeout else GifSnapErrorCode.Network,
                        if (e is InterruptedIOException) "GifSnap request timed out" else "Could not reach GifSnap", cause = e,
                    ))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw GifSnapException(GifSnapErrorCode.Http,
                                "GifSnap returned HTTP ${it.code}", it.code, it.header("Retry-After"))
                            val body = it.body ?: invalid("Missing response body")
                            if (body.contentLength() > MAX_RESPONSE_BYTES) invalid("Response exceeds 4 MiB")
                            val source = body.source()
                            source.request(MAX_RESPONSE_BYTES + 1)
                            if (source.buffer.size > MAX_RESPONSE_BYTES) invalid("Response exceeds 4 MiB")
                            parsePage(source.readUtf8())
                        }
                    }
                    if (continuation.isActive) result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { error -> continuation.resumeWithException(when (error) {
                            is GifSnapException -> error
                            is InterruptedIOException -> GifSnapException(GifSnapErrorCode.Timeout, "GifSnap request timed out", cause = error)
                            is IOException -> GifSnapException(GifSnapErrorCode.Network, "Could not read GifSnap response", cause = error)
                            else -> GifSnapException(GifSnapErrorCode.InvalidResponse, "Invalid GifSnap response", cause = error)
                        }) },
                    )
                }
            })
        }
    }
    private companion object { const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024 }
}

/** Only HTTP(S), with a real host and no credentials, whitespace, control characters or backslashes. */
internal fun requireSafeUrl(value: String): HttpUrl {
    require((value.startsWith("https://") || value.startsWith("http://")) && value.none { it.isWhitespace() || it.code <= 32 || it.code == 127 || it == '\\' }) { "Invalid media URL" }
    val parsed = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Expected an absolute HTTP(S) URL")
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "URL credentials are not allowed" }
    return parsed
}

private fun invalid(message: String): Nothing = throw GifSnapException(GifSnapErrorCode.InvalidResponse, message)
private fun JsonObject.string(name: String, optional: Boolean = false, nonEmpty: Boolean = true): String? {
    val value = this[name]
    if (optional && (value == null || value == JsonNull)) return null
    val primitive = value as? JsonPrimitive ?: invalid("Invalid $name")
    if (!primitive.isString || (nonEmpty && primitive.content.isBlank())) invalid("Invalid $name")
    return primitive.content
}
private fun JsonObject.number(name: String, minimum: Long = 0): Long {
    val value = this[name] as? JsonPrimitive ?: invalid("Invalid $name")
    val result = if (value.isString) null else value.longOrNull
    return result?.takeIf { it >= minimum } ?: invalid("Invalid $name")
}
private fun JsonObject.integer(name: String, minimum: Int = 0): Int =
    number(name, minimum.toLong()).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: invalid("Invalid $name")

internal fun parsePage(body: String): GifPage {
    val root = try { Json.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }
        ?: invalid("Response must be a JSON object")
    val items = root["data"] as? JsonArray ?: invalid("Missing data array")
    val data = items.map { element ->
        val item = element as? JsonObject ?: invalid("Invalid media item")
        val url = item.string("url")!!
        val preview = item.string("preview_url")!!
        try { requireSafeUrl(url); requireSafeUrl(preview) } catch (_: IllegalArgumentException) { invalid("Unsafe media URL") }
        val type = item.string("type")!!
        if (type != "gif" && type != "sticker") invalid("Invalid media type")
        GifItem(item.string("id")!!, item.string("title", nonEmpty = false)!!, url, preview,
            item.integer("width"), item.integer("height"), type,
            item.string("source", optional = true, nonEmpty = false), item.string("content_id", optional = true))
    }
    val pagination = root["pagination"] as? JsonObject ?: invalid("Missing pagination")
    val hasNextValue = pagination["has_next"] as? JsonPrimitive ?: invalid("Invalid has_next")
    val hasNext = if (hasNextValue.isString) null else hasNextValue.booleanOrNull
    if (hasNext == null) invalid("Invalid has_next")
    val nextPage = when (pagination["next_page"]) {
        null, JsonNull -> null
        else -> pagination.integer("next_page", 1)
    }
    val page = pagination.integer("page", 1)
    if (hasNext && (nextPage == null || nextPage <= page) || !hasNext && nextPage != null) invalid("Invalid next_page")
    val limit = pagination.integer("limit", 1)
    if (limit > 50) invalid("Invalid limit")
    return GifPage(data, GifPagination(page, limit, pagination.number("total"),
        hasNext, nextPage, pagination.number("offset")), root.string("query", optional = true, nonEmpty = false))
}

/** Presentation-only deduplication. Raw API pages and counts are not modified. First occurrence wins. */
fun distinctGifs(items: List<GifItem>): List<GifItem> {
    val ids = HashSet<String>(); val urls = HashSet<String>(); val contentIds = HashSet<String>()
    return items.filter { item ->
        val duplicate = item.id in ids || item.url in urls || (item.contentId != null && item.contentId in contentIds)
        ids.add(item.id); urls.add(item.url); item.contentId?.let(contentIds::add)
        !duplicate
    }
}
