package com.rowix.gifsnap.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import com.rowix.gifsnap.client.*

enum class GifSnapTheme { System, Light, Dark, Inherit }

/**
 * Ready-to-use animated GIF/sticker picker. Put it in a container with bounded height.
 * Selection returns the original API object; the host decides whether to insert, send or dismiss.
 * Search is explicitly submitted, avoiding a network request for each keystroke.
 */
@Composable
fun GifSnapPicker(
    client: GifSnapDataSource,
    onSelect: (GifItem) -> Unit,
    modifier: Modifier = Modifier,
    theme: GifSnapTheme = GifSnapTheme.System,
    initialMediaType: MediaType = MediaType.Gifs,
) {
    val colors = when (theme) {
        GifSnapTheme.Inherit -> MaterialTheme.colorScheme
        GifSnapTheme.Dark -> darkColorScheme(primary = Color(0xFFB0DF79))
        GifSnapTheme.Light -> lightColorScheme(primary = Color(0xFF486820))
        GifSnapTheme.System -> if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFB0DF79))
            else lightColorScheme(primary = Color(0xFF486820))
    }
    MaterialTheme(colorScheme = colors) { PickerContent(client, onSelect, modifier, initialMediaType) }
}

internal fun createGifSnapImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context.applicationContext)
    // API28+ ImageDecoder plays both GIF and animated WebP, unlike a plain image decoder.
    .components { add(AnimatedImageDecoder.Factory()) }
    .diskCache(null)
    .build()

@Composable
internal fun rememberGifSnapImageLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    val loader = remember(context) { createGifSnapImageLoader(context) }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    return loader
}

@Composable
private fun PickerContent(client: GifSnapDataSource, onSelect: (GifItem) -> Unit, modifier: Modifier, initialType: MediaType) {
    val scope = rememberCoroutineScope()
    val controller = remember(client, scope) { PickerController(client, scope) }
    val state by controller.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current
    val loader = rememberGifSnapImageLoader()
    val gridState = rememberLazyGridState()
    LaunchedEffect(controller, initialType) { controller.search("", initialType) }
    LaunchedEffect(state.query, state.type) { gridState.scrollToItem(0) }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val submit = { focus.clearFocus(); controller.search(query, state.type) }
    Column(modifier.fillMaxWidth().heightIn(min = 360.dp, max = 640.dp)
        .background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Find the right GIF", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, label = { Text(if (state.type == MediaType.Gifs) "Search GIFs" else "Search stickers") },
                singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }))
            Button(onClick = { submit() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Search") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in MediaType.entries) {
                FilterChip(selected = state.type == type, onClick = { controller.search(query, type) },
                    modifier = Modifier.heightIn(min = 48.dp), label = { Text(if (type == MediaType.Gifs) "GIFs" else "Stickers") })
            }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(128.dp), state = gridState, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.id }) { item -> MediaCard(item, loader, onSelect) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        state.loading -> {
                            CircularProgressIndicator(Modifier.size(28.dp).semantics { contentDescription = "Loading GIFs" })
                            Text("Loading…", modifier = Modifier.padding(top = 8.dp))
                        }
                        state.error != null -> {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            TextButton(onClick = controller::retry) { Text("Retry") }
                        }
                        state.items.isEmpty() -> Text("No results. Try another search.",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        state.hasMore -> OutlinedButton(onClick = controller::loadMore) { Text("Load more") }
                        else -> Text("You're all caught up.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        TextButton(onClick = { uri.openUri("https://gifsnap.com/developers") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Powered by GifSnap")
        }
    }
}

@Composable
private fun MediaCard(gif: GifItem, loader: ImageLoader, onSelect: (GifItem) -> Unit) {
    var attempt by remember(gif.url, gif.previewUrl) { mutableIntStateOf(0) }
    val preview = attempt > 0 && gif.previewUrl != gif.url
    val failed = attempt > (if (gif.previewUrl != gif.url) 1 else 0)
    val title = gif.title.ifBlank { if (gif.type == "sticker") "Sticker" else "GIF" }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(role = Role.Button, onClickLabel = "Select $title") { onSelect(gif) }
        .semantics(mergeDescendants = true) {}) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (gif.width > 0 && gif.height > 0)
            (gif.width.toFloat() / gif.height).coerceIn(0.5f, 2f) else 1f), contentAlignment = Alignment.Center) {
            if (!failed) AsyncImage(model = if (preview) gif.previewUrl else gif.url, imageLoader = loader,
                contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onError = { attempt++ })
            else Text("Image unavailable", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            if (preview && !failed) Text("Preview", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.surface).padding(4.dp))
        }
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
