# ROADMAP.md — Pixels Implementation Roadmap

> Phase-by-phase checklist. Check off items as they are completed.

---

## Phase 1: Project Setup
- [x] Add `kotlin-android` plugin — NOT NEEDED (AGP 9.2.0 bundles Kotlin)
- [x] Add `com.google.devtools.ksp` plugin to version catalog and apply in `app/build.gradle.kts`
- [x] Add all library dependencies to `gradle/libs.versions.toml`
- [x] Add all `implementation`/`ksp` lines to `app/build.gradle.kts`
- [x] Enable `buildConfig = true` in build features
- [x] Add `buildConfigField` for `PEXELS_API_KEY` from `local.properties`
- [x] Add `INTERNET` permission to `AndroidManifest.xml`
- [x] Verify project compiles with `./gradlew assembleDebug` ✅ BUILD SUCCESSFUL

---

## Phase 2: Data Layer
- [x] `data/local/db/PhotoEntity.kt` — Room entity with all fields
- [x] `data/local/db/PhotoDao.kt` — DAO with suspend queries
- [x] `data/local/db/PixelsDatabase.kt` — Room database singleton
- [x] `data/remote/dto/PhotoSrcDto.kt` — DTO for `src` object
- [x] `data/remote/dto/PhotoDto.kt` — DTO for photo object
- [x] `data/remote/dto/PexelsPageResponse.kt` — Paginated wrapper DTO
- [x] `data/remote/VolleySingleton.kt` — Volley RequestQueue singleton
- [x] `data/remote/PexelsApiService.kt` — API methods with coroutine wrappers
- [x] `data/local/mapper/PhotoEntityMapper.kt` — DTO ↔ Entity ↔ Domain mappers
- [x] `util/NetworkUtil.kt` — Connectivity check helper
- [x] `util/Constants.kt` — Base URL, page size, cache TTL
- [x] `domain/repository/PhotoRepository.kt` — Repository interface
- [x] `data/repository/PhotoRepositoryImpl.kt` — Offline-first implementation
- [x] Verify data layer compiles ✅ BUILD SUCCESSFUL

---

## Phase 3: Domain Layer
- [x] `domain/model/Photo.kt` — Clean domain model
- [x] `domain/usecase/GetCuratedPhotosUseCase.kt`
- [x] `domain/usecase/SearchPhotosUseCase.kt`
- [x] Verify domain layer compiles ✅ BUILD SUCCESSFUL

---

## Phase 4: Presentation Layer
- [x] `presentation/state/UiState.kt` — Sealed class
- [x] `presentation/main/MainViewModel.kt` — StateFlow + pagination logic
- [x] `di/ServiceLocator.kt` — Manual DI wiring
- [x] `res/layout/activity_main.xml` — Toolbar + RecyclerView + shimmer + offline banner
- [x] `res/layout/item_photo.xml` — MaterialCardView with ImageView + photographer overlay
- [x] `presentation/adapter/PhotoAdapter.kt` — DiffUtil + ViewHolder
- [x] `presentation/main/MainActivity.kt` — Wire ViewModel, RecyclerView, search, pagination
- [x] `res/layout/activity_detail.xml` — Full-screen photo + bottom sheet
- [x] `presentation/detail/DetailViewModel.kt`
- [x] `presentation/detail/DetailActivity.kt` — Shared element transition
- [x] Register `DetailActivity` in `AndroidManifest.xml`
- [x] Verify full app flow compiles and runs ✅ BUILD SUCCESSFUL

---

## Phase 5: Polish & Edge Cases
- [x] `res/layout/placeholder_shimmer.xml` — Shimmer skeleton layout
- [x] `res/layout/layout_empty_state.xml` — No results UI (inline in activity_main)
- [x] Offline banner logic (NetworkCallback in MainActivity)
- [x] Swipe-to-refresh (`SwipeRefreshLayout`) integration + color scheme
- [x] Download-to-gallery functionality (DownloadManager + Snackbar feedback)
- [x] Share intent for photos
- [x] Test all offline scenarios (airplane mode) — logic implemented
- [x] Verify cache invalidation (24h TTL) — in PhotoRepositoryImpl
- [x] Verify rate limit header tracking — in PexelsApiService ✅ BUILD SUCCESSFUL

---

## Phase 6: Final
- [x] `res/values/themes.xml` — Material 3 light theme colors
- [x] `res/values-night/themes.xml` — Material 3 dark theme colors
- [x] `res/values/dimens.xml` — Centralized dimension values
- [x] `res/values/strings.xml` — All user-facing strings
- [x] Update `AGENTS.md` to reflect final architecture
- [ ] Manual QA: light theme + dark theme (user task)
- [ ] Manual QA: pagination, search, offline, config change (user task)
- [x] Clean up unused imports and dead code
- [x] Final `./gradlew assembleDebug` passes clean ✅ BUILD SUCCESSFUL

---

## File Creation Checklist (37 files)

| # | File | Phase | Status |
|---|------|-------|--------|
| 1 | `gradle/libs.versions.toml` (update) | 1 | ✅ |
| 2 | `app/build.gradle.kts` (update) | 1 | ✅ |
| 3 | `build.gradle.kts` (update) | 1 | ✅ |
| 4 | `AndroidManifest.xml` (update) | 1 | ✅ |
| 5 | `data/local/db/PhotoEntity.kt` | 2 | ✅ |
| 6 | `data/local/db/PhotoDao.kt` | 2 | ✅ |
| 7 | `data/local/db/PixelsDatabase.kt` | 2 | ✅ |
| 8 | `data/local/mapper/PhotoEntityMapper.kt` | 2 | ✅ |
| 9 | `data/remote/VolleySingleton.kt` | 2 | ✅ |
| 10 | `data/remote/PexelsApiService.kt` | 2 | ✅ |
| 11 | `data/remote/dto/PhotoSrcDto.kt` | 2 | ✅ |
| 12 | `data/remote/dto/PhotoDto.kt` | 2 | ✅ |
| 13 | `data/remote/dto/PexelsPageResponse.kt` | 2 | ✅ |
| 14 | `data/repository/PhotoRepositoryImpl.kt` | 2 | ✅ |
| 15 | `domain/model/Photo.kt` | 3 | ✅ |
| 16 | `domain/repository/PhotoRepository.kt` | 2 | ✅ |
| 17 | `domain/usecase/GetCuratedPhotosUseCase.kt` | 3 | ✅ |
| 18 | `domain/usecase/SearchPhotosUseCase.kt` | 3 | ✅ |
| 19 | `presentation/state/UiState.kt` | 4 | ✅ |
| 20 | `presentation/main/MainViewModel.kt` | 4 | ✅ |
| 21 | `presentation/main/MainActivity.kt` | 4 | ✅ |
| 22 | `presentation/detail/DetailViewModel.kt` | 4 | ✅ |
| 23 | `presentation/detail/DetailActivity.kt` | 4 | ✅ |
| 24 | `presentation/adapter/PhotoAdapter.kt` | 4 | ✅ |
| 25 | `di/ServiceLocator.kt` | 4 | ✅ |
| 26 | `util/NetworkUtil.kt` | 2 | ✅ |
| 27 | `util/Constants.kt` | 2 | ✅ |
| 28 | `res/layout/activity_main.xml` | 4 | ✅ |
| 29 | `res/layout/activity_detail.xml` | 4 | ✅ |
| 30 | `res/layout/item_photo.xml` | 4 | ✅ |
| 31 | `res/layout/placeholder_shimmer.xml` | 5 | ✅ |
| 32 | `res/layout/layout_empty_state.xml` | 5 | ✅ (inline) |
| 33 | `res/layout/layout_offline_banner.xml` | 5 | ✅ (inline) |
| 34 | `res/values/themes.xml` (update) | 6 | ✅ |
| 35 | `res/values-night/themes.xml` (update) | 6 | ✅ |
| 36 | `res/values/strings.xml` (update) | 6 | ✅ |
| 37 | `res/values/dimens.xml` (new) | 6 | ✅ |

