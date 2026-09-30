package com.rowix.gifsnap.compose

import com.rowix.gifsnap.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickerControllerTest {
    private fun item(id: String, identity: String? = null) = GifItem(id, "Same title", "https://example.com/$id.gif", "https://example.com/$id.webp", 20, 20, "gif", "test", identity)
    private fun page(data: List<GifItem>, number: Int = 1, more: Boolean = true) = GifPage(data, GifPagination(number, 24, 99, more, if (more) number + 1 else null, (number - 1) * 24L))
    private class Source(val load: suspend (String, MediaType, Int) -> GifPage) : GifSnapDataSource {
        override suspend fun search(query: String, mediaType: MediaType, page: Int, limit: Int) = load(query, mediaType, page)
        override suspend fun trending(mediaType: MediaType, page: Int, limit: Int) = load("", mediaType, page)
    }
    @Test fun appendDeduplicatesAndUsesServerNextPage() = runTest {
        val calls = mutableListOf<Int>()
        val controller = PickerController(Source { _, _, p -> calls += p; if (p == 1) page(listOf(item("a", "test:1"))).copy(pagination = GifPagination(1, 24, 99, true, 3, 0)) else page(listOf(item("alias", "test:1"), item("b")), 3, false) }, this)
        controller.search("cat", MediaType.Gifs); advanceUntilIdle(); controller.loadMore(); controller.loadMore(); advanceUntilIdle()
        assertEquals(listOf(1, 3), calls); assertEquals(listOf("a", "b"), controller.state.value.items.map { it.id }); assertFalse(controller.state.value.hasMore)
    }
    @Test fun retryKeepsItemsAndRetriesFailedPage() = runTest {
        var fail = true
        val controller = PickerController(Source { _, _, p -> if (p == 1) page(listOf(item("a"))) else if (fail) throw GifSnapException(GifSnapErrorCode.Http, "rate", 429, "10") else page(listOf(item("b")), p, false) }, this)
        controller.search("", MediaType.Gifs); advanceUntilIdle(); controller.loadMore(); advanceUntilIdle()
        assertEquals(listOf("a"), controller.state.value.items.map { it.id }); assertTrue(controller.state.value.error!!.contains("Retry-After: 10"))
        fail = false; controller.retry(); advanceUntilIdle(); assertEquals(listOf("a", "b"), controller.state.value.items.map { it.id })
    }
    @Test fun duplicateOnlyAndEmptyPagesStopPagination() = runTest {
        for (empty in listOf(false, true)) {
            val controller = PickerController(Source { _, _, p -> page(if (p == 1 || !empty) listOf(item("a")) else emptyList(), p) }, this)
            controller.search("", MediaType.Gifs); advanceUntilIdle(); controller.loadMore(); advanceUntilIdle(); assertFalse(controller.state.value.hasMore)
        }
    }
    @Test fun newSearchIgnoresLateCancellationUncooperativeSource() = runTest {
        val controller = PickerController(Source { query, _, _ -> withContext(NonCancellable) { if (query == "old") delay(1000) }; page(listOf(item(query)), more = false) }, this)
        controller.search("old", MediaType.Gifs); runCurrent(); controller.search("new", MediaType.Stickers); advanceUntilIdle()
        assertEquals(listOf("new"), controller.state.value.items.map { it.id }); assertEquals(MediaType.Stickers, controller.state.value.type)
    }
    @Test fun disposeCancelsWorkAndIgnoresLateResult() = runTest {
        val controller = PickerController(Source { _, _, _ -> withContext(NonCancellable) { delay(1000) }; page(listOf(item("late"))) }, this)
        controller.search("", MediaType.Gifs); runCurrent(); controller.cancel(); advanceUntilIdle(); assertTrue(controller.state.value.items.isEmpty())
    }
}
