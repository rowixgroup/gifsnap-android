package com.rowix.gifsnap.compose

import com.rowix.gifsnap.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PickerState(
    val items: List<GifItem> = emptyList(),
    val query: String = "",
    val type: MediaType = MediaType.Gifs,
    val loading: Boolean = false,
    val error: String? = null,
    val nextPage: Int = 1,
    val hasMore: Boolean = false,
    val total: Long = 0,
)

/** Owned by one picker composition; a generation token also rejects cancellation-uncooperative sources. */
internal class PickerController(private val client: GifSnapDataSource, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(PickerState())
    val state: StateFlow<PickerState> = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    fun search(query: String, type: MediaType) {
        job?.cancel(); generation++
        mutableState.value = PickerState(query = query.trim(), type = type)
        load(append = false)
    }
    fun loadMore() { if (!state.value.loading && state.value.hasMore) load(append = true) }
    fun retry() { if (!state.value.loading && state.value.error != null) load(append = state.value.items.isNotEmpty()) }
    fun cancel() { generation++; job?.cancel() }
    private fun load(append: Boolean) {
        val before = state.value
        val token = generation
        mutableState.value = before.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val page = if (before.query.isEmpty()) client.trending(before.type, before.nextPage, 24)
                    else client.search(before.query, before.type, before.nextPage, 24)
                if (token != generation) return@launch
                val items = distinctGifs((if (append) before.items else emptyList()) + page.data)
                val added = items.size > (if (append) before.items.size else 0)
                mutableState.value = before.copy(items = items, loading = false, error = null,
                    total = page.pagination.total,
                    nextPage = page.pagination.nextPage ?: before.nextPage,
                    hasMore = page.data.isNotEmpty() && added && page.pagination.hasNext &&
                        (page.pagination.nextPage ?: 0) > before.nextPage)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != generation) return@launch
                val message = when {
                    error is GifSnapException && error.statusCode == 429 ->
                        "Too many requests. Please wait before retrying." + (error.retryAfter?.let { " Retry-After: $it" } ?: "")
                    error is GifSnapException && error.code == GifSnapErrorCode.Timeout -> "Request timed out. Try again."
                    else -> "Couldn't load GIFs. Check your connection and try again."
                }
                mutableState.value = before.copy(loading = false, error = message)
            }
        }
    }
}
