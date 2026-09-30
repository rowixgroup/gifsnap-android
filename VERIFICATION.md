# Android SDK verification — 0.1.1, 30 September 2026

Version 0.1.1 removes the visible provider/source text from Compose cards. The same text is absent from the merged accessibility node because it is no longer rendered. `GifItem.source` and the API parser remain unchanged; selection still returns the original object. The “Powered by GifSnap” link remains.

This cosmetic patch uses the previously verified toolchain: Gradle 9.2.1 with the official checksum-verified wrapper, AGP 9.0.1, Kotlin/Compose compiler 2.2.10, JDK 21, compileSdk 36, Compose UI 1.9.3, Material 3 1.3.2 and Coil 3.4.0. The client supports API 23+; the picker requires API 28+ for animated WebP.

Patch verification runs the 10 client and 5 picker-state unit tests, release lint, sample compilation, Android instrumentation-test compilation and both release Maven publications. The selection instrumentation assertion now requires provider labels to be absent while checking that selected source metadata is preserved. An emulator was not restarted for this text-only change. The unchanged animated media pipeline previously passed actual three-color GIF/WebP frame-progression tests and Compose selection/error/empty-state tests on API 36.1 at 320dp width in version0.1.0.

Version 0.1.0 source and Maven artifacts remain immutable. The local Maven repository includes both versions. The 0.1.1 source archive excludes build products, caches, emulator data, local settings, logs and credentials. It retains the executable official Gradle wrapper and original synthetic GIF/WebP fixtures. Per-file SHA-256 checks and artifact hashes are generated in `dist/manifest.json`, `SHA256SUMS` and `SOURCE_SHA256SUMS`.

GitHub release availability and hosted Maven availability are separate: the Maven ZIP can be used locally, while hosted 0.1.1 URLs are verified after the website release. No Maven Central or JitPack publication is claimed. No physical-device or API 28 runtime test was performed.
