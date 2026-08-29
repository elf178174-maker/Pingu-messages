# Pingu Messages

A complete, privacy-respecting SMS and MMS app for Android, built with Kotlin, Jetpack Compose and
Material 3. It replaces the messaging app on your phone: it becomes Android's default SMS app,
receives and sends real text and multimedia messages, and keeps everything on your device.

No account. No servers. No analytics. No third-party trackers.

[![Build](https://github.com/elf178174-maker/Pingu-messages/actions/workflows/build.yml/badge.svg)](https://github.com/elf178174-maker/Pingu-messages/actions/workflows/build.yml)

---

## Getting the app

You do not need Android Studio, the Android SDK, Java or Gradle. Everything is built on GitHub's
servers.

1. Open the **[Actions](https://github.com/elf178174-maker/Pingu-messages/actions)** tab of this
   repository.
2. Click the most recent successful **Build** run.
3. Scroll to **Artifacts** and download **`pingu-messages-debug-apk`** (or
   `pingu-messages-release-apk`, which is smaller and faster).
4. Open the `.apk` on your phone. Android will ask whether to allow installing apps from this
   source; allow it, then install.
5. Open Pingu Messages and tap **Set as default SMS app**.

Tagged releases (`v1.0.0`, `v1.1.0`, …) also publish the APKs to the
[Releases](https://github.com/elf178174-maker/Pingu-messages/releases) page with notes.

**Android 8.0 (API 26) or newer.** Tested against API 35.

---

## What it does

### Messaging
- Real SMS send and receive as Android's default SMS app
- Real MMS send and receive, including the PDU encoding and decoding, written from the OMA
  specification rather than a third-party library
- Group messaging, either as one MMS to everyone or as individual texts, your choice
- Long messages split into concatenated parts, with a live segment counter that tells you when a
  curly apostrophe has just turned one message into three
- Delivery reports (optional; some carriers charge for them)
- Dual-SIM: a default SIM, a SIM per conversation, or ask every time
- Quick replies from the phone app during an incoming call

### Conversations
- Pin, mute (permanently or until a time), archive, block, mark as spam
- Configurable swipe actions in both directions, including "off"
- Multi-select with bulk pin, mute, archive, read/unread, block and delete
- Custom conversation names, and custom folders for organising threads
- Unread counts, draft indicators, delivery ticks, muted and pinned markers

### Messages
- Reply with a quote that scrolls back to the original when tapped
- Reactions, stored locally and optionally sent as the plain-text tapback other messengers
  understand — and recognised when they send one to you
- Copy, forward, share, delete, save attachments, message details
- Failed messages show why and offer a retry
- Links, phone numbers, e-mail addresses and street addresses detected and tappable
- Emoji-only messages drawn large, with a built-in emoji picker

### Media
- Photos, videos, audio, documents, contact cards and location links
- Camera and video capture through the system camera app
- Hold-to-record voice messages with slide-to-cancel
- Full-screen viewer with pinch zoom, swipe between items, share, save and delete
- Images automatically resized to fit the carrier's message size limit
- Per-conversation gallery of everything shared

### Everything else
- Scheduled messages, backed by exact alarms with a WorkManager safety net, re-armed after a
  reboot, a time change or an app update
- Global search across conversations, contacts, message text and attachment names, with the
  matching words highlighted and a tap that jumps to the exact message
- High-quality notifications: conversation-aware, inline reply that really sends, mark as read,
  grouping, and optional bubbles
- Four levels of lock-screen privacy, from full content to nothing at all
- Blocked numbers synchronised with Android's system block list, plus on-device spam keywords
- Light, dark and system themes; dynamic colour; seven accents; an OLED black mode
- Home-screen widget with recent conversations, an unread badge and a compose shortcut
- Launcher shortcuts for a new message, search and recent conversations
- Optional app lock using your fingerprint or screen lock
- Local backup and restore to a file you choose, with no cloud involved

---

## What it deliberately does not do

Every messaging app for Android runs into the same walls. This one says so instead of faking its
way around them. The same list is in the app under **Settings → Features Android does not allow**.

| Feature | Why not | What Pingu Messages does instead |
| --- | --- | --- |
| **RCS / chat features** | RCS on Android is delivered through Google's Jibe infrastructure, which is not available to third-party apps. There is no public API and no legitimate way to reverse-engineer one. | Implements SMS and MMS properly. The transport layer is behind an interface, so another protocol could be added if one ever opens up. |
| **Reactions over the wire** | SMS and MMS have no reaction field. | Reactions are stored locally. Turn on *Send reactions as text* and they are also sent as the tapback other messengers use, and tapbacks other people send are folded onto the right message instead of appearing as a stray line of text. |
| **Editing a sent message** | Once a message reaches the network it cannot be changed or recalled. | You can edit a scheduled message before it sends, and edit and resend one that failed. |
| **Deleting for everyone** | Nothing can remove a message from someone else's phone. | Deleting removes the local copy, and the confirmation says exactly that. |
| **Read receipts for SMS** | The GSM standard has no read receipt for SMS. | Delivery reports are real and optional. MMS read reports are supported and shown when the other phone sends one. No app shows a "read" tick for SMS honestly. |
| **Typing indicators** | Not part of SMS or MMS. | Nothing pretends to show one. |
| **End-to-end encryption** | SMS and MMS travel through your carrier in the clear. No app can change that. | Messages live in Android's own message store, protected by the device encryption your lock screen enables. The optional app lock adds a second gate on this device. |
| **Cloud backup** | Would mean running a server and holding your messages. | Backup writes a file you choose and keep, through the system file picker. |

---

## Privacy

- The app has no network permission for its own purposes. SMS goes over the cellular network and
  MMS goes to your carrier's MMSC through the platform; nothing is sent anywhere else.
- No analytics, no crash reporting service, no advertising identifier, no third-party SDK that
  phones home.
- Contacts are read on the device to turn numbers into names. Only the identity fields and phone
  numbers are queried; nothing else in your contact list is touched.
- Permissions are requested when they are first needed, with an explanation, not in a burst at
  startup.
- Attachments are shared with other apps through a `FileProvider` with temporary, scoped grants.

Permissions the app declares and why:

| Permission | When it is used |
| --- | --- |
| `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Sending and receiving messages. Granted together with the default SMS role. |
| `READ_CONTACTS` | Showing names and photos instead of numbers. Optional. |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Labelling SIM slots on dual-SIM devices. Optional. |
| `POST_NOTIFICATIONS` | Telling you a message arrived. Optional. |
| `RECORD_AUDIO` | Only while the record button is held. |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | Only when you tap Location; read once, turned into a link, never stored. |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and below only) | Only when you tap Save on an attachment. Android 10 and later save through MediaStore and need no permission. |
| `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` | Sending scheduled messages at the time you chose, and re-arming them after a reboot. |
| `VIBRATE`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | Notification vibration, explaining MMS failures accurately, and background sync. |

The camera permission is **not** declared: photos and videos are captured through the system camera
app, so the app never holds camera access at all.

---

## Architecture

```
app/src/main/kotlin/app/pingu/messages/
├── core/            Pure Kotlin, no Android: phone numbers, GSM segment counting, entity
│                    detection, tapback parsing, search folding, relative time
├── domain/model/    The types the whole app speaks in
├── data/
│   ├── local/       Room database: entities, DAOs, migrations
│   ├── telephony/   The system SMS, MMS, threads, contacts and SIM providers
│   ├── mms/pdu/     MMS PDU encoder and decoder (OMA-TS-MMS_ENC-V1_3, WAP-230-WSP)
│   ├── preferences/ DataStore-backed settings
│   └── repository/  Sync, conversations, messages, drafts, scheduling, blocking
├── platform/        Everything that touches an Android system service: broadcast receivers,
│                    SMS and MMS transports, notifications, alarms, widget, shortcuts, backup
├── di/              The object graph, wired by hand in one readable file
└── ui/              Compose: theme, components, screens, navigation
```

**The system telephony provider is the source of truth.** Messages live in `content://sms` and
`content://mms`, which is what makes them survive switching messaging apps and visible to the rest
of the phone. Room holds a mirror for fast queries and search, plus the app-only state SMS cannot
carry — reactions, reply links, pins, mutes, folders, scheduled messages. The two are separate
tables so a full re-sync can never lose a user's decisions.

**Dependency injection is manual.** `di/AppContainer.kt` constructs everything lazily with
constructor injection. For an app this size that is a deliberate trade: the graph is one readable
file, there is no generated code to step through, and the build carries one fewer annotation
processor. Every class still takes its collaborators through its constructor, so each is testable
with fakes and swapping in Hilt later would touch only that file.

**Performance.** Conversations and messages are read through indexed queries with a growing
window, never a full table read; a thread with tens of thousands of messages costs one query of the
visible range. Contact names come from an in-memory index refreshed by a content observer rather
than an IPC per row. Attachment metadata is computed for the thread you are looking at, not during
a bulk backfill.

More detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and
[`docs/ANDROID_NOTES.md`](docs/ANDROID_NOTES.md), which documents the awkward parts of being a
default SMS app.

---

## Building

### On GitHub (recommended, nothing to install)

Push to any branch, or open the Actions tab and run **Build** manually. The workflow sets up JDK 17
and the Android SDK, runs the unit tests, builds the debug and release APKs, compiles the
instrumentation tests, and uploads the APKs as artifacts.

### Locally, if you want to

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # release APK
```

The Gradle wrapper is committed, so `./gradlew` fetches the right Gradle version itself. You need a
JDK 17 and an Android SDK with API 35.

### Signing

The release build does **not** require you to create a keystore. With no key material configured it
falls back to the standard Android debug key, so `assembleRelease` produces an APK that installs on
any device. For a Play Store build, see [`docs/SIGNING.md`](docs/SIGNING.md).

---

## Tests

| Kind | Where | Runs |
| --- | --- | --- |
| Unit tests | `app/src/test` | On every push, on the JVM. Cover phone-number matching, GSM segment counting, link and address detection, tapback parsing, quoted replies, search folding and highlighting, emoji clustering, relative time, scheduling presets, model behaviour, and a full MMS PDU encode/decode round trip. |
| Instrumentation tests | `app/src/androidTest` | Compiled on every push; run them on a device or emulator with `./gradlew connectedDebugAndroidTest`. Cover the database (sort order, filtering, cascade deletes, search, metadata surviving a re-sync), Room migrations, Compose components and app launch. |

The MMS round-trip test is the one worth knowing about: a PDU that cannot be decoded is a message
the carrier will reject, and there is no way to discover that on a device without spending money on
a failed send.

---

## Contributing and development

- Kotlin, Compose, Material 3, Room, DataStore, WorkManager, Coil, Media3.
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`, JDK 17.
- Dependency versions live in `gradle/libs.versions.toml`.
- Room exports the database schema to `app/schemas` on every build; keep the generated JSON in
  version control so a schema change is a reviewable diff. There is no
  `fallbackToDestructiveMigration` anywhere in the project.

---

## Licence

[Apache License 2.0](LICENSE).

Pingu Messages is not affiliated with Google. It does not copy, reverse-engineer or reimplement any
proprietary Google service.
