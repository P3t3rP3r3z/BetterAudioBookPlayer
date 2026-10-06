# Better Audiobook Player

A clean, modern Android audiobook player with a focus on simplicity, user experience, and reliability.

## Vision

An offline-first audiobook player that addresses the pain points of existing solutions:
- **Simple UI** — No clutter, no distractions
- **Easy file management** — Intuitive browsing of audiobook files on device storage
- **Reliable background playback** — Never drops audio when the phone goes to sleep
- **High audio quality** — Pristine playback without compression
- **No cloud dependency** — Works entirely offline
- **No subscription** — Free, forever

## Architecture

Built with modern Android best practices:
- **MVVM** — Clean separation of concerns
- **Kotlin Coroutines** — Asynchronous operations and reactive flows
- **Hilt** — Dependency injection
- **Jetpack Compose** — Modern, declarative UI
- **Media3/ExoPlayer** — Robust, high-quality audio playback
- **Room** — Local metadata persistence
- **TDD** — Comprehensive test coverage

## Project Structure

```
app/src/
├── main/
│   ├── kotlin/com/aikipunk/betteraudiobookplayer/
│   │   ├── data/          # Data layer (repositories, local DB, file access)
│   │   ├── domain/        # Domain layer (use cases, entities, interfaces)
│   │   ├── presentation/  # Presentation layer (UI, ViewModels)
│   │   ├── service/       # Background playback service
│   │   ├── MainActivity.kt
│   │   └── AudiobookPlayerApp.kt
│   └── res/               # Resources (strings, themes, etc.)
├── test/                  # Unit tests
└── androidTest/           # Integration & UI tests
```

## Tech Stack

- **Kotlin** 2.4 with KSP (Room, Hilt)
- **Jetpack Compose** — Modern UI toolkit
- **Media3/ExoPlayer** — Audio playback engine
- **Room** — Local database
- **Hilt** — Dependency injection
- **Kotlin Coroutines & Flow** — Reactive programming
- **JUnit 4 + Mockk** — Unit testing
- **Espresso** — UI testing

## Setup

### Prerequisites
- An Android Studio release that supports AGP 9.4
- Android SDK Platform 37 (compile) — the build installs it if the SDK licenses are accepted; the app targets API 36
- JDK 21 (Gradle runs on it and compiles with it; it's downloaded automatically if missing)

### Build & Run

```bash
# Clone the repo
git clone https://github.com/P3t3rP3r3z/BetterAudioBookPlayer.git
cd BetterAudioBookPlayer

# Build the project
./gradlew build

# Run on connected device or emulator
./gradlew installDebug
```

### Release Build

Release builds are minified with R8 and resource shrinking. Signing details are read from a
`keystore.properties` file in the project root, which is gitignored along with `*.jks` / `*.keystore`.
Keep the keystore and its passwords out of the repo.

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

```bash
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab for Google Play
./gradlew assembleRelease   # APK; unsigned if keystore.properties is missing
```

Store listing text, policy answers, the privacy policy site and store graphics are in
[docs/store](docs/store/README.md).

Open-source credits are generated from the dependencies at build time (AboutLibraries plugin) and
shown in Settings › About › Open-source licenses. There's nothing to update by hand when dependencies change.

### Run Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest

# All tests with coverage
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

## Development

### Branch Strategy
- `main` — Stable, release-ready code
- `working` — Active development branch

### Commit & PR Process
1. Create a feature branch from `working`
2. Follow TDD: write tests first, implement code
3. Ensure all tests pass locally
4. Open a PR with a clear description
5. Merge to `working` after review
6. Periodically merge `working` → `main` for releases

### Code Style
- Follow Kotlin conventions
- Avoid `!!` (non-null assertions) — use safe navigation (`?.`, `?: return`)
- Prefer `val` over `var`
- Keep functions small and focused
- Write self-documenting code; comment only non-obvious logic

## Features

- **Bookshelf**: import folders as books or chapters; grid/list views; filter, sort and search by title, author, narrator, publisher or year
- **Player**: chapters, speed, skip intervals, rewind on resume, bookmark markers on the timeline
- **Background playback**: media notification, lock screen, headset buttons and Android Auto
- **Voice**: "Hey Google, play Frankenstein on Better Audiobook Player" opens and plays the closest matching book (title, author or narrator); a request without a title resumes the last book
- **Bookmarks** with notes
- **Sleep timer** with shake to extend
- **Audio output picker**
- **Settings**: light/dark/system theme and dynamic color
- **Device transfer**: the bookshelf, progress and bookmarks move to a new phone with Android backup
- **Accessibility**: TalkBack labels, 48dp touch targets, text scaling

## Known Limitations
- Audio formats supported: MP3, OGG, FLAC, WAV, M4A (Media3 defaults)
- Max file size: Limited by device storage only
- Metadata parsing: Basic ID3v2 tags (v0.1 MVP)

## License

Proprietary. © 2026 Aikipunk Creations. All rights reserved. See [LICENSE](LICENSE).
Third-party open-source components are used under their own licenses (all Apache 2.0 at the time of writing),
listed in the app under Settings › About › Open-source licenses.

---

**Made by:** Aikipunk Creations  
**Current Version:** 1.0.0  
**Last Updated:** September 2026
