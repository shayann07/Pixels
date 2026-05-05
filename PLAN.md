# PLAN.md — Pixels App Development Plan

## 1. App Overview

**Pixels** is an offline-first image gallery app powered by the [Pexels API](https://www.pexels.com/api/). It fetches curated/searched photos from Pexels, caches them locally with Room, and displays them in a beautiful staggered grid. When offline, the app seamlessly shows cached content.

---

## 2. Architecture: Clean Architecture + MVVM

```
┌─────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER                  │
│  Activities / Fragments / XML Layouts / ViewModels  │
└──────────────────────────┬──────────────────────────┘
                           │ observes StateFlow
┌──────────────────────────▼──────────────────────────┐
│                    DOMAIN LAYER                      │
│         Use Cases / Repository Interfaces           │
└──────────────────────────┬──────────────────────────┘
                           │ implements
┌──────────────────────────▼──────────────────────────┐
│                     DATA LAYER                       │
│  Repository Impl / Room (Local) / Volley (Remote)   │
└─────────────────────────────────────────────────────┘
```

### Package Structure

```
com.webscare.pixels/
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── PixelsDatabase.kt          # Room database singleton
│   │   │   ├── PhotoDao.kt                # DAO for photo entity
│   │   │   └── PhotoEntity.kt             # Room entity
│   │   └── mapper/
│   │       └── PhotoEntityMapper.kt       # Entity ↔ Domain model mapping
│   ├── remote/
│   │   ├── VolleySingleton.kt             # Volley RequestQueue singleton
│   │   ├── PexelsApiService.kt            # API request methods
│   │   └── dto/
│   │       ├── PhotoResponse.kt           # JSON response DTOs
│   │       └── PexelsPageResponse.kt      # Paginated response wrapper
│   └── repository/
│       └── PhotoRepositoryImpl.kt         # Offline-first repository implementation
├── domain/
│   ├── model/
│   │   └── Photo.kt                       # Domain model (clean, no annotations)
│   ├── repository/
│   │   └── PhotoRepository.kt            # Repository interface
│   └── usecase/
│       ├── GetCuratedPhotosUseCase.kt     # Fetch curated photos
│       └── SearchPhotosUseCase.kt         # Search photos by query
├── presentation/
│   ├── main/
│   │   ├── MainActivity.kt               # Single activity host
│   │   └── MainViewModel.kt              # ViewModel for photo list state
│   ├── detail/
│   │   ├── DetailActivity.kt             # Full-screen photo viewer
│   │   └── DetailViewModel.kt
│   ├── adapter/
│   │   └── PhotoAdapter.kt               # RecyclerView adapter with DiffUtil
│   └── state/
│       └── UiState.kt                     # Sealed class: Loading/Success/Error/Empty
├── di/
│   └── ServiceLocator.kt                 # Manual DI (no Hilt — keep it lean)
└── util/
    ├── NetworkUtil.kt                     # Connectivity check helper
    └── Constants.kt                       # API key, base URL, page size
```

---

## 3. Pexels API Details

### Base URL
```
https://api.pexels.com/v1/
```

### Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/curated?page={page}&per_page={per_page}` | GET | Home feed — curated photos |
| `/v1/search?query={query}&page={page}&per_page={per_page}` | GET | Search by keyword |

### Authentication
- Header: `Authorization: YOUR_API_KEY`
- Free tier: 200 requests/hour, 20,000 requests/month
- **API Key**: `SKggq3VsYeZE69o7CJfbCkEMXZWrzJEC2BDn2viODFSiQcuuVMncXza9`

### How to Get a Pexels API Key
1. Go to **https://www.pexels.com/api/**
2. Click **"Your API Key"** (or "Get Started")
3. Sign up with email/Google — no credit card needed
4. Fill the short form: describe your app (e.g., "Offline image gallery for Android")
5. API key is issued immediately — a long alphanumeric string
6. Store it in `local.properties` (gitignored):
   ```properties
   PEXELS_API_KEY=SKggq3VsYeZE69o7CJfbCkEMXZWrzJEC2BDn2viODFSiQcuuVMncXza9
   ```
7. No OAuth, no bearer token refresh, no expiry. Key is permanent unless revoked.

### Rate Limit Strategy (200 calls/hour — Production-Ready)

**Problem**: 200 calls/hour shared across ALL users using the same key.

**Solution — Aggressive caching to minimize API calls:**

| Strategy | How It Saves Calls |
|----------|-------------------|
| **24h cache TTL** | App opens → Room has data < 24h old → show cached, skip API |
| **Fetch only on scroll** | Don't pre-fetch pages. Load page 2 only when user scrolls there |
| **15 photos per page** | 1 API call fills a full screen. Most sessions need 2-3 calls max |
| **Cache search results** | "cats" searched once → stored in Room → served from cache for 24h |
| **No duplicate fetches** | If query+page already fresh in Room → zero API calls |

**Realistic usage per user session:**
- Open app: 0 calls (cached) or 1 call (stale/first launch)
- Scroll 3 pages: 2 more calls
- Search once: 1 call
- **Total: ~1-4 calls per session**

**In-code logic before ANY API call:**
```
1. Check Room: do we have data for this query+page?
2. Check TTL: is cachedAt < 24 hours ago?
3. If YES to both → return Room data, ZERO API calls
4. If NO → call API, save to Room, return fresh data
```

**Rate limit header tracking**: Read `X-Ratelimit-Remaining` from Volley response headers. If near zero, suppress further calls and serve cache only.

### Photo Resource (verified from Postman)
```json
{
  "id": 36472088,
  "width": 4000,
  "height": 5000,
  "url": "https://www.pexels.com/photo/blue-tram-on-city-bridge-at-twilight-in-gothenburg-36472088/",
  "photographer": "Pasi Mämmelä",
  "photographer_url": "https://www.pexels.com/@pasi-mammela-2159326540",
  "photographer_id": 2159326540,
  "avg_color": "#524F62",
  "src": {
    "original": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg",
    "large2x": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940",
    "large": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&h=650&w=940",
    "medium": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&h=350",
    "small": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&h=130",
    "portrait": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&fit=crop&h=1200&w=800",
    "landscape": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&fit=crop&h=627&w=1200",
    "tiny": "https://images.pexels.com/photos/36472088/pexels-photo-36472088.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=200&w=280"
  },
  "liked": false,
  "alt": "A blue tram crosses a bridge in Gothenburg at twilight, reflecting urban lights."
}
```

**Fields we use in the app:**
| Field | Type | Usage |
|-------|------|-------|
| `id` | Int | Primary key in Room |
| `width` / `height` | Int | Aspect ratio calculation for StaggeredGrid |
| `photographer` | String | Displayed on card overlay + detail screen |
| `photographer_url` | String | Clickable link in detail screen |
| `photographer_id` | Int | Stored but not displayed (future use) |
| `avg_color` | String (hex) | Placeholder background color while image loads |
| `src.original` | String | Download functionality |
| `src.large2x` | String | Detail screen full-res display |
| `src.medium` | String | Grid thumbnails (350px height — perfect for cards) |
| `src.small` | String | Not used (too small) |
| `src.portrait` | String | Not used |
| `src.landscape` | String | Not used |
| `src.tiny` | String | Low-res blur placeholder / preload |
| `liked` | Boolean | Not used (always false for API consumers) |
| `alt` | String | Accessibility contentDescription + detail screen |

### Paginated Response (verified from Postman)

**Curated** (`GET /v1/curated?page=1&per_page=15`):
```json
{
  "page": 1,
  "per_page": 15,
  "photos": [ ...15 Photo objects... ],
  "total_results": 62545,
  "next_page": "https://api.pexels.com/v1/v1/curated?page=2&per_page=15"
}
```

**Search** (`GET /v1/search?query=nature&page=1&per_page=15`):
```json
{
  "page": 1,
  "per_page": 15,
  "photos": [ ...15 Photo objects... ],
  "total_results": 8000,
  "next_page": "https://api.pexels.com/v1/v1/search?page=2&per_page=15&query=nature"
}
```

**Single Photo** (`GET /v1/photos/{id}`) — returns flat Photo object, no wrapper.

> ⚠️ **Known Pexels API bug**: `next_page` URL contains duplicate `/v1/v1/` path segment. We do NOT use `next_page` for pagination — we construct URLs manually using `currentPage + 1`.

**Pagination fields:**
| Field | Type | Notes |
|-------|------|-------|
| `page` | Int | Current page |
| `per_page` | Int | Items per page (we use 15) |
| `total_results` | Int | Total available (curated: ~62k, search: capped at 8000) |
| `next_page` | String? | Buggy URL — ignore, build our own |
| `photos` | Array | Photo objects |

---

## 4. Data Layer Design

### 4.1 Room Entity

```kotlin
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: Int,
    val width: Int,
    val height: Int,
    val photographer: String,
    val photographerUrl: String,
    val photographerId: Int,
    val avgColor: String,
    val srcOriginal: String,
    val srcLarge2x: String,
    val srcLarge: String,
    val srcMedium: String,
    val srcSmall: String,
    val srcPortrait: String,
    val srcLandscape: String,
    val srcTiny: String,
    val alt: String,
    val query: String,          // "curated" or search term — to filter cache
    val cachedAt: Long          // System.currentTimeMillis() — for cache invalidation
)
```

### 4.2 DAO

```kotlin
@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE query = :query ORDER BY cachedAt DESC")
    suspend fun getPhotosByQuery(query: String): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM photos WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM photos WHERE query = :query")
    suspend fun getCountByQuery(query: String): Int
}
```

### 4.3 Room + Flow Decision

**We use `suspend` functions (NOT `Flow`) with Room.** Rationale:
- Our data only changes when WE explicitly fetch from the API — no background sync, no push updates
- Flow with Room is ideal for multi-source real-time data (e.g., chat apps) — overkill here
- The reactive layer lives in the **ViewModel's StateFlow**, not in Room
- Simpler, more predictable, avoids unnecessary re-emissions during inserts

**Data flow:**
```
User action → ViewModel calls UseCase →
  UseCase calls Repository → Repository checks Room (suspend) →
  If stale: calls API (suspend via coroutine wrapper) → saves to Room →
  Returns List<Photo> → ViewModel updates MutableStateFlow →
  Activity collects StateFlow and renders UI
```

---

## 5. Presentation Layer Design

### 5.1 UI Screens

#### Screen 1: Home (MainActivity)
- **Top**: Material Toolbar with app name + search icon
- **Search**: `SearchView` in toolbar — expands to full-width input
- **Body**: `StaggeredGridLayoutManager` (2 columns) in `RecyclerView`
- **Cards**: `MaterialCardView` showing:
  - Photo image (aspect-ratio preserved using `avg_color` as placeholder background)
  - Photographer name overlay (semi-transparent gradient at bottom)
- **States**:
  - Loading: `ShimmerFrameLayout` placeholder grid (shimmer effect)
  - Empty: Centered illustration + "No photos found" text
  - Error: Snackbar with retry action
  - Offline banner: Subtle top bar "Showing cached photos • Offline"
- **Pagination**: Infinite scroll — load next page when near bottom

#### Screen 2: Detail (DetailActivity)
- **Full-screen** photo with pinch-to-zoom (or viewpager swipe)
- **Bottom sheet** with:
  - Photographer name + link
  - Photo dimensions
  - Download button (save `large2x` to device gallery)
  - Share button
- **Immersive mode**: Hide system bars on tap, show on tap again

### 5.2 UI State Management

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T, val isFromCache: Boolean = false) : UiState<T>()
    data class Error(val message: String, val cachedData: Any? = null) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}
```

### 5.3 ViewModel

```kotlin
class MainViewModel(
    private val getCuratedPhotosUseCase: GetCuratedPhotosUseCase,
    private val searchPhotosUseCase: SearchPhotosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Photo>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Photo>>> = _uiState.asStateFlow()

    private var currentPage = 1
    private var currentQuery = "curated"
    private val photosList = mutableListOf<Photo>()

    fun loadCuratedPhotos(refresh: Boolean = false) { ... }
    fun searchPhotos(query: String) { ... }
    fun loadNextPage() { ... }
}
```

---

## 6. UI/UX Design Specifications

### Color Scheme (Material 3 Dynamic-like)
Using Material default theme with these customizations in `themes.xml`:

| Token | Light | Dark |
|-------|-------|------|
| colorPrimary | `#1B1B1F` | `#E4E1E6` |
| colorSurface | `#FFFBFF` | `#1B1B1F` |
| colorSurfaceVariant | `#E7E0EB` | `#49454E` |
| colorOnSurface | `#1B1B1F` | `#E6E1E6` |

### Typography
- Toolbar title: `TextAppearance.Material3.TitleLarge`
- Photographer name: `TextAppearance.Material3.LabelMedium`
- Offline banner: `TextAppearance.Material3.LabelSmall`

### Animations & Transitions
- **Shared element transition**: Photo thumbnail → detail (using `transitionName`)
- **RecyclerView item animation**: Default `DefaultItemAnimator` (subtle fade-in)
- **Shimmer loading**: Facebook shimmer library for skeleton loading

### Layout Specs
- Card corner radius: `12dp`
- Card elevation: `2dp`
- Grid spacing: `8dp`
- Content padding: `16dp`

---

## 7. Dependencies

Add to `gradle/libs.versions.toml`:

```toml
[versions]
# ...existing versions...
volley = "1.2.1"
room = "2.7.1"
coil = "3.1.0"
shimmer = "0.5.0"
lifecycle = "2.9.1"
coroutines = "1.10.2"
photoview = "2.3.0"

[libraries]
# ...existing libraries...
volley = { group = "com.android.volley", name = "volley", version.ref = "volley" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
coil = { group = "io.coil-kt.coil3", name = "coil", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
shimmer = { group = "com.facebook.shimmer", name = "shimmer", version.ref = "shimmer" }
lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
photoview = { group = "com.github.chrisbanes", name = "PhotoView", version.ref = "photoview" }

[plugins]
# ...existing plugins...
ksp = { id = "com.google.devtools.ksp", version = "2.1.21-2.0.1" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version = "2.1.21" }
```

### Why these choices:
| Library | Reason |
|---------|--------|
| **Volley** | User requirement; lightweight for single API; built-in request queue |
| **Room** | Robust offline caching with compile-time SQL verification |
| **Coil 3** | Kotlin-first, lightweight image loading; works with XML `ImageView`; disk/memory cache built-in |
| **Shimmer** | Beautiful loading placeholder matching modern app UX |
| **PhotoView** | Pinch-to-zoom for detail screen with minimal effort |
| **Coroutines** | Background threading for Room + Volley callback bridging |

---

## 8. Edge Cases & Performance Optimizations

### Network
- [ ] **Connectivity check** before API calls (avoid unnecessary Volley errors)
- [ ] **Retry with exponential backoff** on transient failures (429, 5xx)
- [ ] **Rate limit awareness**: Track `X-Ratelimit-Remaining` header; pause requests if near limit
- [ ] **Request cancellation**: Cancel pending Volley requests on Activity destroy / new search

### Caching & Storage
- [ ] **Cache invalidation**: 24-hour TTL — stale data still served offline, refreshed when online
- [ ] **Duplicate prevention**: `OnConflictStrategy.REPLACE` in Room — same photo ID overwrites
- [ ] **Bounded cache size**: Max 500 photos per query in Room; prune oldest on insert
- [ ] **Coil disk cache**: Leveraged automatically for image files (no manual file I/O needed)

### UI Performance
- [ ] **RecyclerView optimization**:
  - `setHasFixedSize(true)` where applicable
  - `RecycledViewPool` shared if multiple lists
  - Image requests canceled for recycled views (Coil handles this)
- [ ] **Aspect ratio pre-calculation**: Set `ImageView` height from `width/height` ratio BEFORE image loads (prevents layout jumps)
- [ ] **Placeholder color**: Use `avg_color` as immediate background while image loads
- [ ] **DiffUtil**: Efficient list updates without full `notifyDataSetChanged()`
- [ ] **Pagination threshold**: Load next page when 5 items from bottom

### Memory
- [ ] **Coil memory cache**: Automatic; respects app memory pressure
- [ ] **No large bitmaps in Room**: Store URLs only; let Coil manage pixel data
- [ ] **ViewModel survives config change**: No redundant API calls on rotation

### UX Edge Cases
- [ ] **Empty search results**: Show friendly "No results" illustration
- [ ] **First launch offline**: Show "Connect to internet for initial content" state
- [ ] **Slow network**: Show shimmer with timeout → fall back to cache if available
- [ ] **API key invalid/expired**: Graceful error message (not a crash)
- [ ] **Photo load failure**: Show placeholder with retry icon per card

---

## 9. Implementation Order (Step-by-Step)

### Phase 1: Project Setup
1. Add Kotlin plugin (`kotlin-android`) + KSP plugin to build files
2. Add all dependencies to version catalog + `app/build.gradle.kts`
3. Add `INTERNET` permission to `AndroidManifest.xml`
4. Create package structure directories

### Phase 2: Data Layer
5. Create `PhotoEntity` Room entity
6. Create `PhotoDao` with all queries
7. Create `PixelsDatabase` Room database class
8. Create DTOs: `PhotoDto`, `PhotoSrcDto`, `PexelsPageResponse`
9. Create `VolleySingleton`
10. Create `PexelsApiService` — Volley JSON requests with coroutine wrappers
11. Create entity ↔ domain ↔ DTO mappers
12. Create `NetworkUtil` connectivity helper
13. Create `PhotoRepository` interface (domain layer)
14. Create `PhotoRepositoryImpl` with offline-first logic

### Phase 3: Domain Layer
15. Create `Photo` domain model
16. Create `GetCuratedPhotosUseCase`
17. Create `SearchPhotosUseCase`

### Phase 4: Presentation Layer
18. Create `UiState` sealed class
19. Create `MainViewModel` with StateFlow
20. Create `ServiceLocator` for manual DI
21. Design `activity_main.xml` — toolbar + RecyclerView + shimmer overlay + offline banner
22. Create `item_photo.xml` — MaterialCardView with ImageView + photographer label
23. Create `PhotoAdapter` with DiffUtil + ViewHolder
24. Implement `MainActivity` — wire ViewModel, RecyclerView, search, pagination
25. Create `activity_detail.xml` — full-screen photo + bottom sheet info
26. Create `DetailViewModel`
27. Implement `DetailActivity` with shared element transition

### Phase 5: Polish & Edge Cases
28. Add shimmer loading layout (`placeholder_item.xml`)
29. Add empty state layout
30. Add offline banner logic (connectivity listener)
31. Implement swipe-to-refresh (`SwipeRefreshLayout`)
32. Add download-to-gallery functionality
33. Add share intent
34. Test all offline scenarios
35. Verify proper cache invalidation

### Phase 6: Final
36. Update `themes.xml` (light + dark) with Material 3 colors
37. Update `AGENTS.md` to reflect new architecture
38. Manual QA pass on both light/dark themes
39. Clean up, remove unused imports, verify ProGuard-safe

---

## 10. File-by-File Creation Checklist

| # | File | Layer |
|---|------|-------|
| 1 | `gradle/libs.versions.toml` (update) | Build |
| 2 | `app/build.gradle.kts` (update) | Build |
| 3 | `build.gradle.kts` (update — add KSP + Kotlin plugin) | Build |
| 4 | `AndroidManifest.xml` (update — permissions + DetailActivity) | Config |
| 5 | `data/local/db/PhotoEntity.kt` | Data |
| 6 | `data/local/db/PhotoDao.kt` | Data |
| 7 | `data/local/db/PixelsDatabase.kt` | Data |
| 8 | `data/local/mapper/PhotoEntityMapper.kt` | Data |
| 9 | `data/remote/VolleySingleton.kt` | Data |
| 10 | `data/remote/PexelsApiService.kt` | Data |
| 11 | `data/remote/dto/PhotoSrcDto.kt` | Data |
| 12 | `data/remote/dto/PhotoDto.kt` | Data |
| 13 | `data/remote/dto/PexelsPageResponse.kt` | Data |
| 14 | `data/repository/PhotoRepositoryImpl.kt` | Data |
| 15 | `domain/model/Photo.kt` | Domain |
| 16 | `domain/repository/PhotoRepository.kt` | Domain |
| 17 | `domain/usecase/GetCuratedPhotosUseCase.kt` | Domain |
| 18 | `domain/usecase/SearchPhotosUseCase.kt` | Domain |
| 19 | `presentation/state/UiState.kt` | Presentation |
| 20 | `presentation/main/MainViewModel.kt` | Presentation |
| 21 | `presentation/main/MainActivity.kt` | Presentation |
| 22 | `presentation/detail/DetailViewModel.kt` | Presentation |
| 23 | `presentation/detail/DetailActivity.kt` | Presentation |
| 24 | `presentation/adapter/PhotoAdapter.kt` | Presentation |
| 25 | `di/ServiceLocator.kt` | DI |
| 26 | `util/NetworkUtil.kt` | Util |
| 27 | `util/Constants.kt` | Util |
| 28 | `res/layout/activity_main.xml` | Resource |
| 29 | `res/layout/activity_detail.xml` | Resource |
| 30 | `res/layout/item_photo.xml` | Resource |
| 31 | `res/layout/placeholder_shimmer.xml` | Resource |
| 32 | `res/layout/layout_empty_state.xml` | Resource |
| 33 | `res/layout/layout_offline_banner.xml` | Resource |
| 34 | `res/values/themes.xml` (update) | Resource |
| 35 | `res/values-night/themes.xml` (update) | Resource |
| 36 | `res/values/strings.xml` (update) | Resource |
| 37 | `res/values/dimens.xml` (new) | Resource |

---

## 11. API Key Management

Store the Pexels API key in `local.properties` (gitignored):
```properties
PEXELS_API_KEY=SKggq3VsYeZE69o7CJfbCkEMXZWrzJEC2BDn2viODFSiQcuuVMncXza9
```

Access in `build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        buildConfigField("String", "PEXELS_API_KEY", "\"${project.findProperty("PEXELS_API_KEY")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}
```

Reference in code via `BuildConfig.PEXELS_API_KEY`.

---

## 12. Key Design Decisions & Rationale

| Decision | Rationale |
|----------|-----------|
| **Volley over Retrofit** | User requirement; only one API endpoint; Volley is sufficient and lightweight |
| **Coil over Glide** | Kotlin-first, smaller APK size, coroutine-native, built-in disk+memory cache |
| **Manual DI over Hilt** | Single-module app with few dependencies; avoids annotation processing overhead |
| **StaggeredGrid over Grid** | Photos have varied aspect ratios; staggered layout is visually stunning for galleries |
| **Store URLs in Room (not blobs)** | Coil handles image caching on disk; Room stays lightweight; no 2MB+ row bloat |
| **StateFlow over LiveData** | Modern, null-safe, coroutine-native; replaces LiveData in new Kotlin-first projects |
| **Pagination in ViewModel** | Survives config changes; keeps list state without re-fetching |
| **24h cache TTL** | Balance between freshness and offline availability; curated feed updates hourly anyway |

---

## 13. Testing Strategy

| Test Type | What to Test | Framework |
|-----------|-------------|-----------|
| Unit | UseCases, Mappers, Repository logic (mocked DAO/API) | JUnit 4 + Mockito |
| Unit | ViewModel state transitions | JUnit 4 + Turbine (StateFlow testing) |
| Instrumented | Room DAO operations | AndroidX Test + Room in-memory DB |
| Manual | Offline scenarios, pagination, config changes | Device/Emulator with airplane mode |

---

*This plan is ready for implementation. Each phase builds on the previous one, so code compiles at every checkpoint.*
