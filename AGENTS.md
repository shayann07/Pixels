# AGENTS.md

## Project Overview

**Pixels** is an offline-first Android image gallery app (package: `com.webscare.pixels`) powered by the Pexels API. Single-module Gradle project using XML Views (NOT Jetpack Compose), Clean Architecture + MVVM, targeting `compileSdk = 36` with `minorApiLevel = 1` (AGP 9 DSL) and `minSdk = 26`.

## Architecture

- **Clean Architecture**: 3 layers — `data/` (Room + Volley), `domain/` (models + use cases), `presentation/` (Activities + ViewModels)
- **MVVM**: ViewModels expose `StateFlow<UiState<T>>` → Activities collect lifecycle-aware
- **Offline-first**: Fetch from API → cache in Room → serve from Room when offline
- **DI**: Manual `ServiceLocator` object — no Hilt/Dagger. Initialized via `ServiceLocator.init(this)` inside `MainActivity.onCreate` (no custom `Application` class)
- **UI Framework**: XML layouts + `AppCompatActivity` + Material 3 theme (NoActionBar + custom Toolbar)
- **Screens**: `MainActivity` (staggered 2-column grid, search via toolbar `SearchView`, swipe-to-refresh, offline banner driven by `ConnectivityManager.NetworkCallback`) and `DetailActivity` (shared-element transition `photo_<id>`, two-stage progressive Coil load medium→large2x, PhotoView pinch-zoom, tap-to-toggle immersive mode, `DownloadManager` save to `Pictures/Pixels/`, share intent via `share_text_template`)

## Build System

- **Gradle**: Kotlin DSL with version catalog at `gradle/libs.versions.toml`
- **AGP**: 9.2.0 — **bundles Kotlin internally** (do NOT apply `kotlin-android` plugin separately); top-level `build.gradle.kts` declares plugins with `apply false`
- **Plugins**: `com.google.devtools.ksp` only (for Room compiler)
- **Java compatibility**: `sourceCompatibility`/`targetCompatibility = JavaVersion.VERSION_11` in `app/build.gradle.kts` (no `kotlin { jvmToolchain(...) }` block)
- **KSP**: Requires `android.disallowKotlinSourceSets=false` in `gradle.properties`
- **Dependencies via catalog aliases** — always add to `libs.versions.toml` first, then `libs.*` in `app/build.gradle.kts`
- **API Key**: Stored in `local.properties` as `PEXELS_API_KEY`, exposed via `BuildConfig.PEXELS_API_KEY`
- **PhotoView**: Hosted on JitPack — repo declared in `settings.gradle.kts` (which also enforces `repositoriesMode = FAIL_ON_PROJECT_REPOS` + foojay toolchain resolver)
- **Permissions** (`AndroidManifest.xml`): `INTERNET`, `ACCESS_NETWORK_STATE`

### Key Commands

```shell
./gradlew assembleDebug       # Build debug APK
./gradlew test                # Run unit tests
./gradlew connectedAndroidTest # Run instrumented tests
```

## Key Libraries

| Library | Purpose |
|---------|---------|
| Volley 1.2.1 | HTTP requests to Pexels API via `suspendCancellableCoroutine` |
| Room 2.7.1 | Local SQLite cache for offline-first (suspend DAO, not Flow) |
| Coil 3.1.0 (+ `coil-network-okhttp`) | Image loading into XML `ImageView` with disk/memory cache |
| Coroutines 1.10.2 | Background threading, StateFlow |
| AndroidX Lifecycle 2.9.1 | `lifecycle-viewmodel-ktx`, `lifecycle-runtime-ktx` |
| AndroidX Activity-KTX 1.13.0 | Provides `enableEdgeToEdge()` |
| SwipeRefreshLayout 1.1.0 | Pull-to-refresh in `MainActivity` |
| Facebook Shimmer 0.5.0 | Loading placeholder animations |
| PhotoView 2.3.0 | Pinch-to-zoom in detail screen |

## Conventions

- **Edge-to-edge**: All Activities use `enableEdgeToEdge()` + `ViewCompat.setOnApplyWindowInsetsListener` on root `@+id/main`
- **State management**: `UiState<T>` sealed class (Loading / Success / Error / Empty) in `presentation/state/`. `Success` carries an extra `isFromCache: Boolean = false` flag used to drive the offline banner
- **Pagination**: ViewModel holds `currentPage` + accumulated list; `MainActivity` triggers next page when `lastVisibleItem >= totalItemCount - 5`. Page size `Constants.PER_PAGE = 15`
- **Dedup**: `MainViewModel` tracks seen IDs in a `HashSet<Int>`; auto-skips ahead when `consecutiveDuplicatePages` reaches **3**
- **Cache strategy**: Room stores photo URLs (not blobs); Coil handles image file caching; `Constants.CACHE_TTL_MS = 24h`. After every fetch, `PhotoDao.deleteOlderThan` + `pruneExcess` (keeps **500** rows per query) run in `PhotoRepositoryImpl`
- **Mappers**: Extension functions in `PhotoEntityMapper` object — DTO→Entity, Entity→Domain, DTO→Domain (typical flow is DTO → Entity → Domain; the direct DTO→Domain mapper exists but is currently unused)
- **JSON parsing**: Manual via `org.json.JSONObject`, encapsulated in DTO companion factories (e.g. `PexelsPageResponse.fromJson`) under `data/remote/dto/`
- **Rate limiting**: `PexelsApiService.lastRateLimitRemaining` (`@Volatile`, initialized to **200**) tracks `X-Ratelimit-Remaining`. `PhotoRepositoryImpl` falls back to cache when `lastRateLimitRemaining <= 5`
- **Volley tuning**: 15 s timeout, 2 retries, exponential backoff set on each `JsonObjectRequest` in `PexelsApiService`
- **Theme**: `Theme.Pixels` extends `Theme.Material3.DayNight.NoActionBar` with night variant
- **No `kotlinOptions`**: AGP 9.x handles JVM target via `compileOptions` only

## Adding Features

1. Domain model in `domain/model/`
2. Repository interface in `domain/repository/`
3. Use case in `domain/usecase/`
4. Implementation in `data/repository/`
5. ViewModel in `presentation/<feature>/` with `ViewModelProvider.Factory`
6. XML layout in `res/layout/`
7. Register new Activities in `AndroidManifest.xml`
8. Wire dependencies in `di/ServiceLocator.kt`

## Testing

- **Unit tests** (`app/src/test/`) and **instrumented tests** (`app/src/androidTest/`) currently contain only the Android Studio template stubs (`ExampleUnitTest`, `ExampleInstrumentedTest`). When adding real tests, target UseCases / Mappers / ViewModels (JUnit 4; add Turbine to the catalog for `StateFlow` assertions) and Room DAOs with an in-memory DB.
