# SPEC.md — Pixels Technical Specification

> Source of truth for architecture, API contracts, data design, and UI specs.

---

## 1. Architecture

**Pattern**: Clean Architecture + MVVM  
**Module**: Single-module (`app/`)  
**Language**: Kotlin  
**UI Framework**: XML Views (NOT Compose)  
**DI**: Manual `ServiceLocator` (no Hilt/Dagger)

```
┌─────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER                  │
│  Activities / XML Layouts / ViewModels / Adapters   │
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
│   ├── local/db/          → Room Entity, DAO, Database
│   ├── local/mapper/      → Entity ↔ Domain mappers
│   ├── remote/            → VolleySingleton, PexelsApiService
│   ├── remote/dto/        → PhotoDto, PhotoSrcDto, PexelsPageResponse
│   └── repository/        → PhotoRepositoryImpl
├── domain/
│   ├── model/             → Photo (clean domain model)
│   ├── repository/        → PhotoRepository interface
│   └── usecase/           → GetCuratedPhotosUseCase, SearchPhotosUseCase
├── presentation/
│   ├── main/              → MainActivity, MainViewModel
│   ├── detail/            → DetailActivity, DetailViewModel
│   ├── adapter/           → PhotoAdapter (DiffUtil)
│   └── state/             → UiState sealed class
├── di/                    → ServiceLocator
└── util/                  → NetworkUtil, Constants
```

---

## 2. Pexels API Contract

### Base URL
```
https://api.pexels.com/v1/
```

### Authentication
- Header: `Authorization: <API_KEY>`
- Key stored in `local.properties` → exposed via `BuildConfig.PEXELS_API_KEY`
- Free tier: **200 requests/hour**, 20,000/month

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/curated?page={page}&per_page=15` | GET | Home feed |
| `/v1/search?query={q}&page={page}&per_page=15` | GET | Search |
| `/v1/photos/{id}` | GET | Single photo (detail) |

### Photo Resource (verified)
```json
{
  "id": 36472088,
  "width": 4000,
  "height": 5000,
  "url": "https://www.pexels.com/photo/...",
  "photographer": "Pasi Mämmelä",
  "photographer_url": "https://www.pexels.com/@pasi-mammela-2159326540",
  "photographer_id": 2159326540,
  "avg_color": "#524F62",
  "src": {
    "original": "...pexels-photo-36472088.jpeg",
    "large2x": "...?auto=compress&cs=tinysrgb&dpr=2&h=650&w=940",
    "large": "...?auto=compress&cs=tinysrgb&h=650&w=940",
    "medium": "...?auto=compress&cs=tinysrgb&h=350",
    "small": "...?auto=compress&cs=tinysrgb&h=130",
    "portrait": "...?fit=crop&h=1200&w=800",
    "landscape": "...?fit=crop&h=627&w=1200",
    "tiny": "...?dpr=1&fit=crop&h=200&w=280"
  },
  "liked": false,
  "alt": "A blue tram crosses a bridge..."
}
```

### Field Usage
| Field | Usage |
|-------|-------|
| `id` | Room PK |
| `width`/`height` | Aspect ratio for StaggeredGrid |
| `photographer` | Card overlay + detail |
| `photographer_url` | Detail screen link |
| `photographer_id` | Stored, future use |
| `avg_color` | Placeholder background |
| `src.original` | Download |
| `src.large2x` | Detail screen display |
| `src.medium` | Grid thumbnails |
| `src.tiny` | Low-res preload |
| `alt` | Accessibility / contentDescription |

### Paginated Response
```json
{
  "page": 1,
  "per_page": 15,
  "photos": [...],
  "total_results": 62545,
  "next_page": "..." // BUGGY — contains /v1/v1/ — DO NOT USE
}
```
> ⚠️ We build pagination URLs manually: `currentPage + 1`. Never use `next_page`.

### Rate Limit Strategy
- Check Room cache first (query + TTL < 24h) → skip API if fresh
- Track `X-Ratelimit-Remaining` header → suppress calls if near zero
- Typical session: 1–4 API calls total

---

## 3. Data Layer Design

### Room Entity
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
    val query: String,       // "curated" or search term
    val cachedAt: Long       // System.currentTimeMillis()
)
```

### DAO (suspend, NOT Flow)
- `getPhotosByQuery(query)` → List<PhotoEntity>
- `insertPhotos(photos)` → OnConflictStrategy.REPLACE
- `deleteByQuery(query)`
- `deleteOlderThan(timestamp)`
- `getCountByQuery(query)` → Int

### Offline-First Flow
```
1. Check Room for query+page data
2. Check TTL (< 24h?)
3. If fresh → return cached
4. If stale/missing + online → fetch API → save to Room → return
5. If offline → return stale cache + offline indicator
6. On API error → fallback to cache
```

---

## 4. UI/UX Specifications

### Screens
1. **Home (MainActivity)**: Toolbar + SearchView + StaggeredGrid RecyclerView + Shimmer + Offline banner
2. **Detail (DetailActivity)**: Full-screen photo (pinch-to-zoom) + Bottom sheet (photographer, dimensions, download, share)

### State Management
```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T, val isFromCache: Boolean = false) : UiState<T>()
    data class Error(val message: String, val cachedData: Any? = null) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}
```

### Theme
| Token | Light | Dark |
|-------|-------|------|
| colorPrimary | `#1B1B1F` | `#E4E1E6` |
| colorSurface | `#FFFBFF` | `#1B1B1F` |
| colorSurfaceVariant | `#E7E0EB` | `#49454E` |
| colorOnSurface | `#1B1B1F` | `#E6E1E6` |

### Layout Constants
- Card corner radius: `12dp`
- Card elevation: `2dp`
- Grid spacing: `8dp`
- Content padding: `16dp`
- StaggeredGrid columns: 2

### Animations
- Shared element transition (thumbnail → detail)
- Facebook Shimmer for loading skeletons
- DefaultItemAnimator for RecyclerView

---

## 5. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Volley over Retrofit | Single endpoint; user requirement; lightweight |
| Coil 3 over Glide | Kotlin-first, smaller APK, coroutine-native, built-in disk+memory cache |
| Manual DI over Hilt | Single-module, few deps; avoids annotation processing |
| StaggeredGrid over Grid | Varied aspect ratios; visually stunning for galleries |
| URLs in Room (not blobs) | Coil caches images on disk; Room stays lightweight |
| StateFlow over LiveData | Modern, null-safe, coroutine-native |
| suspend over Flow in DAO | Data changes only on explicit fetch; simpler mental model |
| 24h cache TTL | Balances freshness vs. offline availability |
| Build own pagination URLs | Pexels `next_page` has `/v1/v1/` bug |

---

## 6. Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Volley | 1.2.1 | HTTP client |
| Room | 2.7.1 | Local DB |
| Coil 3 | 3.1.0 | Image loading |
| Facebook Shimmer | 0.5.0 | Loading skeletons |
| PhotoView | 2.3.0 | Pinch-to-zoom |
| Lifecycle | 2.9.1 | ViewModel + lifecycle-aware coroutines |
| Coroutines | 1.10.2 | Async |
| KSP | 2.1.21-2.0.1 | Room annotation processor |
| Kotlin | 2.1.21 | Language |

### Build Config
- AGP: 9.2.0 (**bundles Kotlin internally** — do NOT apply `kotlin-android` plugin)
- KSP: 2.1.21-2.0.1 (requires `android.disallowKotlinSourceSets=false` in gradle.properties)
- compileSdk: release(36) { minorApiLevel = 1 }
- minSdk: 26
- targetSdk: 36
- JDK toolchain: 21
- PhotoView: hosted on JitPack (added to `settings.gradle.kts`)

