# Android SDK verification — 30 September 2026

The release source builds two standalone release AARs (`gifsnap-client` and `gifsnap-compose` 0.1.0), source JARs and a Maven repository with dependency metadata. It uses Gradle 9.2.1, AGP 9.0.1, Kotlin/Compose compiler 2.2.10, JDK 21, Android compile SDK 36, Compose UI 1.9.3, Material 3 1.3.2 and Coil 3.4.0. The client supports API23+, and the picker requires API28+ for animated WebP.

- **10 client tests passed:** exact string IDs/full URL queries and raw counts; optional source/identity; invalid URLs/models/pagination; safe presentation deduplication; encoded queries and four endpoint paths; HTTP429/Retry-After without retry; whole-call timeout; coroutine cancellation; invalid arguments; oversized response and redirect rejection.
- **5 picker-state tests passed:** ordered append and server pagination; retry preserves results and page; empty/duplicate-only termination; cancellation-uncooperative stale responses; disposal.
- **3 device tests passed on a dedicated Android16/API36.1 emulator:** actual GIF and animated WebP each advanced through three distinct frame colors; Compose search/selection/source attribution/dark theme; empty/error/retry/light theme. The final device run used a 320dp-wide viewport. The native decoder test is separate from Compose's test-clock rule so Android drawable scheduling remains live.
- **Release lint passed:** no errors. Available dependency-version notices remain; this is a deliberately tested compatible toolchain, not a claim that every dependency is the newest release.
- **Sample application compiled.** A separate Gradle consumer resolves the actual local Maven artifacts, not project module dependencies. The consumer was installed on the dedicated emulator and loaded public GifSnap trending media and source attribution at 320dp width.

The dependency check selected Coil3.4.0 for its response-consumption and bounded image-size fixes while retaining the verified compiler/SDK requirements. Coil3.6.x requires a newer compiler/SDK baseline and was not adopted in this release. Official sources are linked in README.

Artifacts and individual source/repository files receive SHA-256 entries in `dist/manifest.json`, `SHA256SUMS` and `SOURCE_SHA256SUMS`. The source ZIP retains executable wrapper permissions and excludes build outputs, caches, emulator data, local settings, logs and credentials. Original three-color fixture GIF/WebP files are the only bundled test media.

Boundaries: no physical-device or API28 runtime test was performed; on-device coverage is API36.1. No Maven Central or JitPack publication is claimed. Hosted Maven download and GitHub publication are release-owner steps, to be verified after upload. API quotas, provider attribution obligations and service availability are not guaranteed by the SDK. The SDK does not send messages or hidden analytics.
