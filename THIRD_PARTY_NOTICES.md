# Third-party components

The Gradle wrapper scripts and binary under `gradle/wrapper` originate from the official Gradle wrapper (9.2.1), licensed under Apache License 2.0: https://github.com/gradle/gradle/blob/v9.2.1/LICENSE . Its official distribution URL and SHA-256 are pinned in `gradle-wrapper.properties`.

Published GifSnap AARs do not shade third-party libraries. Their POM/Gradle metadata declares Kotlin, Kotlin coroutines/serialization, OkHttp, AndroidX Compose and Coil dependencies, which are resolved separately and retain their upstream licenses. Coil's animated decoder is explicitly configured for GIF and WebP support. Fixture animations in the tests are original solid-color assets generated for this SDK and contain no provider media.
