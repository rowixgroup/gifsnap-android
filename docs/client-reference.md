# GifSnap Android: Kotlin client and HTTP response reference

This reference describes the published `com.rowix.gifsnap:gifsnap-client:0.1.2`
client. Install it from `https://gifsnap.com/sdk/android`, or use
`com.rowix.gifsnap:gifsnap-compose:0.1.2` to include the picker and client together.
The [README](../README.md) contains the complete Gradle configuration.

## Search and load the next page with Kotlin

Call client methods from an existing coroutine. `GifSnapClient()` uses
`https://gifsnap.com/api/v1` by default; no API key is currently required.
`MediaType.Gifs` selects GIFs and `MediaType.Stickers` selects stickers.

```kotlin
import com.rowix.gifsnap.client.GifPage
import com.rowix.gifsnap.client.GifSnapClient
import com.rowix.gifsnap.client.MediaType

suspend fun nextSearchPage(
    client: GifSnapClient,
    current: GifPage,
    query: String,
    mediaType: MediaType = MediaType.Gifs,
): GifPage? {
    val nextPage = current.pagination.nextPage ?: return null
    return client.search(
        query = query,
        mediaType = mediaType,
        page = nextPage,
        limit = current.pagination.limit,
    )
}
```

Request the first page with `client.search("happy cats", page = 1, limit = 24)`.
Pass the same query and media type to subsequent page requests. `trending` takes
the same media type, page and limit arguments, but no query. Request only the
next page needed by the UI; do not exhaust all pages in a background loop.
Client responses preserve order and duplicates. Presentation deduplication is a
separate concern and must not change pagination counts or lookup IDs.

Cancellation propagates as coroutine cancellation and cancels the HTTP call.
Allow it to propagate to the coroutine owner. `GifSnapException` exposes `code`,
optional `statusCode` and raw optional `retryAfter`. Error codes are `Http`,
`Network`, `Timeout` and `InvalidResponse`. Invalid arguments throw
`IllegalArgumentException`. The client performs no automatic retries. A host
retry policy should respect `Retry-After`, cancellation and a bounded attempt
count; never retry indefinitely in an immediate loop.

## Raw HTTP endpoints

The Kotlin client sends GET requests to these complete paths:

| Operation | URL | Query parameters |
| --- | --- | --- |
| Search GIFs | `https://gifsnap.com/api/v1/gifs/search` | `q`, `page`, `limit` |
| Trending GIFs | `https://gifsnap.com/api/v1/gifs/trending` | `page`, `limit` |
| Search stickers | `https://gifsnap.com/api/v1/stickers/search` | `q`, `page`, `limit` |
| Trending stickers | `https://gifsnap.com/api/v1/stickers/trending` | `page`, `limit` |

`query` and `mediaType` are Kotlin arguments, not HTTP query parameter names.
The search text is encoded as `q`; `mediaType` selects the path. The client sends
`Accept: application/json` and defaults to page 1 with limit 24.

For raw HTTP callers, omitted pagination instead defaults to page 1 and limit
25. `page` and `limit` must be positive base-10 integers; valid limits above 50
are capped at 50. Malformed or unsafe pagination and missing search text return
HTTP 400; correct the request before retrying. Service failures can return 503.
The Kotlin client validates its own arguments before making a request.

Follow `has_next` and `next_page`, keeping the query and limit unchanged. An
explicit upstream total stops continuation at the final page, including a full
final page. When no upstream total is available, `total` is an estimate based on
the offset and returned batch. An empty page always stops continuation. Keep
raw counts separate from display deduplication and stop automatic fetching on
duplicate-only batches.

## HTTP JSON fields and Kotlin properties

The HTTP response uses **snake_case** for the fields below. The SDK converts
them to **camelCase** Kotlin properties. Do not use Kotlin property names when
constructing a raw HTTP fixture or describing the service's JSON response.

| HTTP JSON field | Kotlin property |
| --- | --- |
| `pagination.has_next` | `GifPagination.hasNext` |
| `pagination.next_page` | `GifPagination.nextPage` |
| `data[].preview_url` | `GifItem.previewUrl` |
| `data[].content_id` | `GifItem.contentId` |

A complete synthetic empty search response in the **HTTP JSON format** is:

```json
{
  "data": [],
  "pagination": {
    "page": 1,
    "limit": 24,
    "total": 0,
    "has_next": false,
    "next_page": null,
    "offset": 0
  },
  "query": "happy cats"
}
```

This is a fixture illustrating the contract, not a live search result. Required
pagination fields are `page`, `limit`, `total`, `has_next` and `offset`.
`next_page` is null or absent when there is no next page; when `has_next` is true
it must be an integer greater than `page`. Nonempty `data` entries require `id`,
`title`, `url`, `preview_url`, `width`, `height` and `type`. `source` and
`content_id` are optional. Preserve full URL strings, including query strings.

The SDK code is MIT licensed. API availability, media rights and provider terms
are separate; this reference makes no SLA or media-license promise.
