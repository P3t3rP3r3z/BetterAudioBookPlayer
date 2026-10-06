# Data safety and App content answers

Play Console → Policy → App content. These answers match version 1.0.0. Recheck them before any release that
adds network access, analytics, crash reporting, ads, accounts or the planned book-info lookup.

## Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not asked when you answer No above |
| Do you provide a way for users to request that their data be deleted? | Not asked when you answer No above |

Why "No" is accurate:

- The app has no `INTERNET` permission and no network libraries. Nothing is sent to a server.
- Data kept only on the device (library, positions, bookmarks, notes, settings) isn't "collected" under Play's
  definition.
- Android Auto Backup and device-to-device transfer are OS-level backup, which Play says doesn't need
  declaring ("data accessed or stored solely for device or OS-level backup & restore").
- Sharing or exporting bookmarks only happens when the user chooses it in the Android share sheet or file picker.

## Other App content declarations

| Declaration | Answer |
|---|---|
| Privacy policy | `<site>/privacy` (see README.md) |
| Ads | No, the app doesn't contain ads |
| App access | All functionality is available without special access (no login) |
| Content rating (IARC) | Category: Reference, News, or Educational → answer No to every content question. Expect Everyone / PEGI 3 |
| Target audience | 13+ age groups only (avoids Families policy requirements); not appealing to children |
| News app | No |
| COVID-19 contact tracing / status | No |
| Government app | No |
| Financial features | None |
| Health | None |
| Foreground service permissions | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — see below |

## Foreground service declaration

- **Type:** Media playback
- **Description:** "Plays the audiobook the user started while the app is in the background or the screen is
  off, with playback controls in the notification, lock screen, Bluetooth and Android Auto. The service runs
  only while a book is playing or paused with controls showing."
- **Video:** Play may ask for a short video. Record the phone screen (Quick Settings → Screen record): start a
  book, go to the home screen, lock the phone, then show the notification controls. Upload it to YouTube as
  unlisted and paste the link.

## Android Auto

Play reviews Android Auto apps separately. In Play Console → Advanced settings → Form factors, add
**Android Auto** and accept the terms. The app is a standard media app (MediaLibraryService), which is an
allowed Auto category.
