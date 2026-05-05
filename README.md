<h1 align="center">Pixels</h1>

<p align="center">
  <strong>An offline-first Android image gallery app powered by the Pexels API.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-100%25-blue?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Architecture-Clean%20Architecture-brightgreen" alt="Clean Architecture" />
  <img src="https://img.shields.io/badge/Pattern-MVVM-orange" alt="MVVM" />
</p>

<br/>

## 📱 Overview

**Pixels** is a beautiful, highly-optimized image gallery built specifically for Android. It fetches curated and searched photos from Pexels, caches them locally using Room, and displays them in a stunning, edge-to-edge staggered grid. 

The primary focus of this application is **bulletproof offline support and UX**. It aggressively caches data and utilizes progressive image loading to ensure that users have a seamless experience, regardless of their internet connection.

## ✨ Features

- **Curated & Search Feeds:** Browse the daily curated feed or search for specific photography.
- **Offline-First Architecture:** Data is securely cached for 24 hours. If the network is unavailable, the app instantly serves cached data.
- **Progressive Image Loading:** Clicking an image while offline instantly loads the cached low-res thumbnail, avoiding harsh flashes or blank screens, while attempting to fetch the high-res version in the background.
- **Immersive Detail View:** Full-screen pinch-to-zoom (via PhotoView) with an immersive UI toggle.
- **Shared Element Transitions:** Smoothly animate photos from the staggered grid into the detail view.
- **Download & Share:** Save original high-resolution photos straight to your device gallery or share them externally.
- **Smart Pagination:** Auto-skips duplicate data and handles loading states with sleek Facebook Shimmer placeholders.

## 🛠️ Tech Stack & Architecture

This project strictly adheres to **Clean Architecture** (Data, Domain, Presentation) and the **MVVM** design pattern.

- **Language:** Kotlin
- **UI:** XML, AppCompat, Material 3 (No Jetpack Compose)
- **Local Storage:** Room Database (SQLite)
- **Networking:** Volley
- **Image Loading:** Coil 3 (with custom memory-cache placeholders)
- **Asynchronous:** Kotlin Coroutines & StateFlow
- **Other Libraries:** Facebook Shimmer (Loaders), SwipeRefreshLayout, PhotoView

## 🏗️ Project Structure

```text
com.webscare.pixels/
├── data/           # Room Database, Volley Singletons, Repository Impl
├── domain/         # Clean Domain Models, Use Cases, Repository Interfaces
├── presentation/   # ViewModels, Activities, Adapters, UiState
├── di/             # Manual Service Locator (Dependency Injection)
└── util/           # Constants and Network utilities
```

## 🚀 Getting Started

To run this project locally, you will need your own Pexels API Key.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/shayann07/Pixels.git
   ```
2. **Get an API Key:**
   - Go to [Pexels API](https://www.pexels.com/api/)
   - Sign up and generate a free API key.
3. **Add the Key to the Project:**
   - Open the project in Android Studio.
   - Open (or create) the `local.properties` file in the root directory.
   - Add the following line:
     ```properties
     PEXELS_API_KEY=your_api_key_here
     ```
4. **Build and Run:**
   - Sync the project with Gradle files.
   - Hit **Run** (`Shift + F10`) to deploy to your emulator or device.

## 📝 License

This project is created for educational and portfolio purposes. Data is provided by the Pexels API.
