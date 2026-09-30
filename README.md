# GifSnap for Android

A typed Kotlin coroutine client and an animated GIF/sticker picker for Jetpack Compose. The picker provides submitted search, trending, pagination, selection, light/dark/system themes, source labels and error/retry states. It plays full GIF and animated WebP media; static previews are used only if the full media cannot load.

## Requirements

- **Picker:** Android 9 (API 28) or later. API 28 is intentional: Coil's `AnimatedImageDecoder` supports both GIF and animated WebP there.
- **Client only:** Android 6 (API 23) or later.
- Kotlin **2.2.10+**, Java bytecode target **17**, and Android `compileSdk` **36+**.
- Compose-enabled host using the Kotlin Compose compiler plugin. Tested with Gradle 9.2.1, Android Gradle Plugin 9.0.1, JDK 21, Compose UI 1.9.3, Material 3 1.3.2 and Coil 3.4.0. A sample app is included.

## Install

GifSnap distributes these artifacts through its own Maven repository, **not Maven Central or JitPack**. Add the repository in `settings.gradle.kts` alongside the standard dependency repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://gifsnap.com/sdk/android")
            content { includeGroup("com.rowix.gifsnap") }
        }
    }
}
```

Add the picker to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.rowix.gifsnap:gifsnap-compose:0.1.0")
}
```

This includes the client and animated Coil decoder transitively. For a custom UI, depend on `com.rowix.gifsnap:gifsnap-client:0.1.0` instead. An `INTERNET` permission is merged from the client library. The SDK does not enable cleartext networking or change the application's network security policy.

For an offline/local distribution, unzip `gifsnap-android-maven-0.1.0.zip` and point the same Maven repository block at the extracted `repository` directory, for example `url = uri("$rootDir/vendor/gifsnap/repository")`. Retain the `.pom` and `.module` files: a bare AAR does not declare transitive dependencies. Google/Maven Central dependencies must still be available from those repositories or your existing cache.

## Compose picker

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rowix.gifsnap.client.GifSnapClient
import com.rowix.gifsnap.compose.GifSnapPicker
import com.rowix.gifsnap.compose.GifSnapTheme

@Composable
fun MessageGifPicker(onGifSelected: (String) -> Unit) {
    val client = remember { GifSnapClient() }
    GifSnapPicker(
        client = client,
        theme = GifSnapTheme.System,
        onSelect = { gif -> onGifSelected(gif.url) },
    )
}
```

Place the picker in a container with bounded height, such as a dialog, bottom sheet or screen column. `modifier` customizes the container. `GifSnapTheme.Light`, `Dark`, `System`, and `Inherit` are supported; `Inherit` uses the host Material color scheme. `initialMediaType = MediaType.Stickers` starts in sticker mode. Keep the client with `remember` or a longer-lived owner rather than constructing it on every recomposition.

Selection returns the original `GifItem`. The host controls insertion, sending and dismissal. The SDK does not send messages, request storage access or download media to the user's gallery. Titles and source labels are displayed; the picker retains its “Powered by GifSnap” link. Returned source metadata is displayed as provided by the API, not independently verified ownership. Preserve any additional provider attribution required for your integration.

Search runs on explicit submission. Replacing a search or leaving composition cancels the old work; a generation guard also rejects stale results from custom data sources. Load more is explicit. Empty and duplicate-only pages stop pagination. Failed pagination retains already displayed items and retries the same page.

## Kotlin client

Use from a coroutine (`viewModelScope`, `lifecycleScope`, or another owner):

```kotlin
import com.rowix.gifsnap.client.GifSnapClient
import com.rowix.gifsnap.client.MediaType

val client = GifSnapClient(timeoutMillis = 15_000)
val result = client.search("happy cats", mediaType = MediaType.Gifs, page = 1, limit = 24)
val gifs = result.data
val nextPage = result.pagination.nextPage

val trendingStickers = client.trending(mediaType = MediaType.Stickers)
```

The default base URL is `https://gifsnap.com/api/v1`. Search and trending are supported for GIFs and stickers. Queries must contain 1–200 characters; limits are 1–50 and pages 1–1,000,000. Invalid arguments throw `IllegalArgumentException`. Request query parameters are encoded; media URLs, their queries, string IDs, `source`, optional opaque `contentId`, response order and raw pagination counts are preserved. The documented fields are exposed as typed properties; unknown response fields are not surfaced. The client does not deduplicate results. `distinctGifs` is an optional presentation helper used by the picker: exact ID, exact full URL or equal non-null `contentId` matches retain the first occurrence. Titles and URL directories never establish identity.

`GifSnapException` reports `code` (`Http`, `Network`, `Timeout`, `InvalidResponse`), optional `statusCode`, and the raw `retryAfter` header (seconds or HTTP date). Cancellation stays coroutine cancellation, not a wrapped API error. The default whole-call timeout is 15 seconds; allowed values are 1–120,000ms. Responses are bounded to 4 MiB and validated before use. Only absolute HTTP(S) media URLs without credentials, control characters, whitespace or backslashes are accepted; originals are never rewritten. HTTP media may be blocked by your application's normal Android security policy. Redirects and automatic HTTP retries are disabled for metadata requests. The client never sends credentials or telemetry.

The public API's current access model does not imply permanent unlimited capacity or a service-level guarantee. Handle errors and rate limits in your application. The client only requests the chosen GifSnap base URL; displaying returned media separately contacts the URL's media host through Coil. No undisclosed analytics requests are made. The picker disables its own persistent image disk cache; Coil may use an in-memory cache during its lifetime.

For custom testing or state management, implement `GifSnapDataSource` and pass it as the picker's `client`.

## Build and test

Set `ANDROID_HOME` to your installed SDK and use JDK 17+ supported by your Gradle version (the verified build uses JDK 21):

```sh
./gradlew :client:testDebugUnitTest :compose:testDebugUnitTest :sample:assembleDebug
./gradlew :compose:connectedDebugAndroidTest
./gradlew :client:publishReleasePublicationToDistributionRepository :compose:publishReleasePublicationToDistributionRepository
./gradlew -p test-consumer :app:assembleDebug
python3 scripts/package_distribution.py
```

The local Maven repository is created at `build/repository`. `test-consumer` resolves the Maven artifacts rather than project modules; `-PgifsnapRepository=https://gifsnap.com/sdk/android` checks the hosted repository after publication. Device tests use original synthetic GIF/WebP fixtures, check that both formats advance through three frame colors, and exercise selection/source attribution/search and empty/retry UI states. No test provider assets or credentials are bundled.

The source archive excludes build products, caches, emulator data, logs and local settings. Release AARs, source JARs, POM/Gradle metadata, checksums, and a Maven-layout ZIP are generated separately.

## Official implementation references

- [Coil animated GIF/WebP decoding](https://coil-kt.github.io/coil/gifs/)
- [Coil networking](https://coil-kt.github.io/coil/network/)
- [Coil version history](https://coil-kt.github.io/coil/changelog/)
- [Kotlin/Compose compiler guidance](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [AGP 9.0.1 built-in Kotlin and compatibility](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Android library Maven publishing](https://developer.android.com/build/publish-library/upload-library)

MIT licensed SDK code. Media, trademarks and API services are not licensed by this package; see `LICENSE` and `THIRD_PARTY_NOTICES.md`.
