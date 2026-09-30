package com.rowix.gifsnap.compose

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.rowix.gifsnap.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GifSnapPickerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun item(id: String) = GifItem(id, "Result $id", "file:///android_asset/animated.gif", "file:///android_asset/animated.webp", 48, 48, "gif", "Synthetic test")
    private fun page(items: List<GifItem>, number: Int = 1, more: Boolean = false) = GifPage(items, GifPagination(number, 24, 2, more, if (more) number + 1 else null, (number - 1) * 24L))

    @Test fun searchSelectionSourceAttributionAndThemeRender() {
        var selected: GifItem? = null
        val source = object : GifSnapDataSource {
            override suspend fun search(query: String, mediaType: MediaType, page: Int, limit: Int) = page(listOf(item(query)))
            override suspend fun trending(mediaType: MediaType, page: Int, limit: Int) = page(listOf(item("trending")))
        }
        compose.setContent { GifSnapPicker(source, { selected = it }, theme = GifSnapTheme.Dark) }
        compose.onNodeWithText("Search GIFs").performTextInput("cats")
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Result cats", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Result cats", substring = true).performClick()
        compose.runOnIdle { assertEquals("cats", selected?.id) }
        compose.onNodeWithText("Source: Synthetic test", substring = true).assertExists()
        compose.onNodeWithText("Powered by GifSnap").assertExists()
    }

    @Test fun emptyAndRetryStatesAreActionable() {
        var fail = true
        val source = object : GifSnapDataSource {
            override suspend fun search(query: String, mediaType: MediaType, page: Int, limit: Int) = page(emptyList())
            override suspend fun trending(mediaType: MediaType, page: Int, limit: Int): GifPage {
                if (fail) throw GifSnapException(GifSnapErrorCode.Network, "offline")
                return page(emptyList())
            }
        }
        compose.setContent { GifSnapPicker(source, {}, theme = GifSnapTheme.Light) }
        compose.onNodeWithText("Retry").assertExists()
        fail = false
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No results. Try another search.").assertExists()
    }
}
