# Better Audiobook Player — Session Handoff

_Last saved: 2026-10-01 (after cleanup). Kept local only (listed in `.git/info/exclude`, not committed)._

## Resume
- **Copilot CLI session ID:** `94c7ea46-d4ca-46ec-8d29-37bda2d68324`
  - Resume with `copilot --resume` and pick this session, or start fresh and say:
    "Read SESSION_HANDOFF.md in ~/BetterAudioBookPlayer and continue."
- **Repo:** `~/BetterAudioBookPlayer`, branch `working`
- **Package:** `com.aikipunk.betteraudiobookplayer`
- **Current version:** 1.0.1 (versionCode 2)

## Where things stand
| Item | Status |
|------|--------|
| 1.0.0 (vc1) | Uploaded to the Play **Closed testing** track; it was in review on 9/29 |
| 1.0.1 (vc2) | Built, lint clean (0 errors), 211 tests pass. Uploading it as a new closed-testing release over 1.0.0 was in progress |
| Watch Pause fix | Confirmed working on the Pixel Watch (commit `bf7258c`) |
| Privacy policy site | GitHub repo created; Pages URL **not yet recorded** |
| 14-day closed test | Starts after a release is approved **and** 12+ testers have opted in |

### Store files
- `~/Desktop/BetterAudiobookPlayer-store/BetterAudiobookPlayer-1.0.1-vc2.aab` ← upload this one
- `~/Desktop/BetterAudiobookPlayer-store/BetterAudiobookPlayer-1.0.0-vc1.aab`
- `~/Desktop/BetterAudiobookPlayer-store/graphics/icon-512.png` (store graphics)
- Listing / data safety answers: `docs/store/listing.md`, `docs/store/data-safety.md`
- Privacy site source: `docs/store/site/`

## Recent commits
- `43d7a01` Cleanup: dead use cases/Shapes removed, Gradle daemon on JDK 21
- `bf7258c` Watch and other untrusted controllers can use play/pause/seek; bump to 1.0.1
- `769c53d` Voice search ("play <book> on Better Audiobook Player")
- `b6102de` Play Store listing, privacy policy site, graphics

## Uncommitted / loose ends
None. Working tree clean. The debug-symbols line was reverted (no effect without the NDK; the Play warning is harmless). Crash logs, old screenshots and `app/release/` were deleted.

## Next steps
1. Finish the Play Console upload of 1.0.1:
   - Closed testing → Manage track → Create new release → upload the vc2 AAB → remove vc1 → notes "Fixes Pause from Wear OS watch media controls" → Next → Save
   - Then Publishing overview → Send changes for review
2. Paste the privacy-policy Pages URL into Play Console. Replace the `<site>/privacy` placeholder in `docs/store/listing.md`, `data-safety.md` and README.
3. Get 12+ testers opted in. Keep them for 14 days, then apply for production.
4. Bump to 1.0.2 (vc3) when there's something to ship.

## Open todos
- Verify on device: save/restore position, rewind, bookmarks, Library long-press notes/remove
- Verify the bookshelf on device: hero/resume, grid/list, sort, filters, search, covers
- Release-build end-to-end check on the phone
- Book info lookup (Open Library / Google Books; Goodreads API is closed) — deferred
- Alexa casting for a paid tier — blocked, idea only

## Device / tooling notes
- **Phone:** Pixel 11 Pro `67070DLKX004U5`. It has the **debug** build installed, so use `./gradlew :app:installDebug`; a release APK fails with signature mismatch.
- **Watch:** Pixel Watch 5. Wireless debugging is **off** (kept off for battery). Re-pair only if needed; the port changes each time.
- **Watch controls background:** the Wear companion is an *untrusted* Media3 controller. The policy is in `service/ControllerCommands.kt`. When our app is paused and Spotify is playing, the watch switches to Spotify by design.
- **Build:** `./gradlew :app:lintRelease :app:testDebugUnitTest :app:bundleRelease`. Lint runs before every AAB.
- The Gradle daemon runs on JDK 21 (`gradle/gradle-daemon-jvm.properties`). JBR 17.0.9 crashed on Apple Silicon and left hs_err logs.
- If Gradle reports "Timeout waiting to lock journal cache", Android Studio's daemon is holding the lock; wait and retry.
- **Version:** `app/build.gradle.kts` lines ~22–23. Each Play upload needs a higher versionCode.
- Kover stays at 0.9.10 (0.9.11 won't resolve).
- `gh` CLI is logged into github.kp.org only. Use the github.com web UI for the personal privacy repo.
