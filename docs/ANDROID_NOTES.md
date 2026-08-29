# Android platform notes

The awkward, load-bearing details of writing a default SMS app. Everything here was a decision
point in this codebase, and each section says what the platform requires, what it does *not* do for
you, and where the code lives.

---

## 1. The default SMS role

Android allows exactly one app at a time to receive `SMS_DELIVER` and to write to the telephony
provider. An app cannot grant itself the role; it can only ask, and the user decides in a system
dialog.

**Four components are required.** Missing any one of them means the app never appears in
*Settings → Default apps → SMS app*, with no error message to explain why. They are marked
`[DEFAULT SMS ROLE]` in `AndroidManifest.xml`:

| Component | Requirement |
| --- | --- |
| An activity handling `ACTION_SENDTO` for `sms:`, `smsto:`, `mms:`, `mmsto:` | `ui/MainActivity` |
| A service handling `ACTION_RESPOND_VIA_MESSAGE`, guarded by `SEND_RESPOND_VIA_MESSAGE` | `platform/sms/HeadlessSmsSendService` |
| A receiver for `SMS_DELIVER`, guarded by `BROADCAST_SMS` | `platform/sms/SmsDeliverReceiver` |
| A receiver for `WAP_PUSH_DELIVER` with mime `application/vnd.wap.mms-message`, guarded by `BROADCAST_WAP_PUSH` | `platform/mms/MmsWapPushReceiver` |

**Requesting the role changed in Android 10.** Before API 29 it was
`Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`; from API 29 it is
`RoleManager.createRequestRoleIntent(ROLE_SMS)`. Both are handled in
`platform/system/DefaultSmsAppManager`.

**The result code is unreliable.** Several OEM implementations return `RESULT_CANCELED` even when
the role was granted. `MainActivity` therefore ignores the result code and re-checks
`Telephony.Sms.getDefaultSmsPackage()`.

**Some devices cannot grant it at all**: no telephony hardware, a secondary user, or a work
profile. `createRequestIntent()` returns null there and the UI explains rather than showing a dead
button.

---

## 2. Receiving SMS: the platform does not store it for you

This is the single biggest surprise. When your app holds the role, `SMS_DELIVER` hands you the
PDUs and *nothing else happens*. If you do not write the message into `content://sms` yourself, it
exists nowhere and is gone the moment the broadcast returns.

`SmsDeliverReceiver` therefore:

1. reassembles the multipart PDUs in order (`Telephony.Sms.Intents.getMessagesFromIntent`);
2. resolves the thread with `Telephony.Threads.getOrCreateThreadId`;
3. inserts into `content://sms` with `MESSAGE_TYPE_INBOX`;
4. hands off to `IncomingMessageHandler` for mirroring, spam checks and the notification.

The receiving subscription arrives under two different extra keys depending on the platform build
(`SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX` or the older `"subscription"`), and on some builds
neither. Both are tried; `-1` means "use the default subscription".

A broadcast receiver gets roughly ten seconds before the process is killed, so all of this runs
inside `goAsync()` with an explicit timeout (`platform/BroadcastScope`). A silent timeout would
mean a dropped message; a logged one is diagnosable.

---

## 3. Sending SMS

`SmsManager.sendTextMessage` / `sendMultipartTextMessage`, with the message written to
`content://sms` in the outbox **before** it is handed to the radio, so nothing is lost if the
process dies mid-send.

**The result PendingIntents must be mutable.** The platform fills the result code and, for delivery
reports, the raw status PDU into the intent before sending it back. An immutable PendingIntent
silently discards them, which is how delivery reports quietly stop working.
`platform/PendingIntents` makes this explicit.

**Each part gets its own callback** with a distinct request code — extras are not part of
`Intent.filterEquals`, so PendingIntents that differ only by extras collide. A failure on any part
marks the message failed; success is only recorded on the last part.

**Delivery is read from the status PDU, never inferred.** GSM 03.40 TP-Status below `0x20` means
delivered; `0x20`–`0x3F` is a temporary failure and `0x40` and above is permanent. When there is no
PDU the app records nothing rather than claiming a delivery it cannot prove.

---

## 4. MMS: what the platform does and does not do

The platform provides exactly two things:
`SmsManager.sendMultimediaMessage` and `SmsManager.downloadMultimediaMessage`. They handle the APN,
the MMS proxy and the HTTP exchange. **Everything either side of that is the app's job**: building
a valid `M-Send.req`, parsing the `M-Retrieve.conf` that comes back, storing both in the telephony
provider, and acknowledging the carrier.

`data/mms/pdu/` is a from-scratch implementation of the parts of OMA-TS-MMS_ENC-V1_3 and
WAP-230-WSP that this needs: uintvars, value-lengths, encoded strings with character sets, the
well-known content-type table, multipart bodies, and a SMIL generator. It is covered by a
round-trip test, because a PDU that cannot be decoded is a message the carrier rejects and there is
no way to discover that on a device without spending money on a failed send.

**PDUs are exchanged through a file.** There is no API that takes bytes. The app writes the PDU to
its cache, exposes it through its `FileProvider`, and grants read (or write, for a download) to
`com.android.phone`. This is the documented mechanism.

**Receiving is four steps, each of which can fail on its own:**

1. a `WAP_PUSH_DELIVER` arrives with an `M-Notification.ind` — the sender, the size, an expiry and
   a URL, but no content. It is stored immediately so the conversation shows something.
2. the body is downloaded, automatically or when the user taps Download.
3. the downloaded PDU is parsed and written to the provider as a real message.
4. an `M-Acknowledge.ind` is sent so the carrier stops re-notifying.

The notification row is kept until step 3 succeeds. That is what makes "Download failed — try
again" possible instead of the message simply vanishing.

**Auto-download is a real setting with real consequences**: MMS uses mobile data and can cost money
while roaming. When it is off, the app sends an `M-NotifyResp.ind` with a *deferred* status so the
carrier holds the message, rather than silently doing nothing.

**Size limits are enforced by the carrier, not by Android.** `getCarrierConfigValues()` reports the
maximum where available; 300 kB is a common real limit and the app's conservative default. Images
are re-encoded at a decreasing scale and quality until they fit
(`platform/mms/MmsAttachmentEncoder`). Sending an untouched modern phone photo fails every time.

**Dates in `content://mms` are in seconds**, unlike `content://sms` which uses milliseconds. Mixing
them up puts every MMS in 1970.

---

## 5. The telephony provider as the source of truth

Messages live in `content://sms` and `content://mms`. That is what makes them survive a switch to
another messaging app and visible to the rest of the phone.

Room holds a **mirror** for fast queries, sorting and search, plus the app-only state SMS cannot
carry: reactions, reply links, pins, mutes, archive flags, folders, scheduled messages. The two are
separate tables so a full re-sync can replace every mirrored row without touching a user decision.

Syncing is bounded rather than "read everything every time":

| Pass | When | Scope |
| --- | --- | --- |
| `syncThreads` | On any provider change | The conversation list only |
| `syncRecentMessages` | On a full pass | The newest ~2,000 messages across all threads |
| `syncThread` | Opening a conversation, scrolling back | One thread, in depth |
| `syncSingleSms` / `syncSingleMms` | From a broadcast receiver | Exactly one message |

Deletion reconciliation only runs when the provider query was **not** truncated by its limit —
otherwise "missing from this page" would be mistaken for "deleted".

OEM variants of the provider do drop columns (`archived` and `sub_id` are the usual casualties), so
every column is read defensively with a fallback (`data/telephony/CursorUtils`).

---

## 6. Notifications

Conversation notifications need three things together: a published **shortcut**, `setShortcutId`
plus `setLocusId` on the notification, and `MessagingStyle` with a `Person` per sender. With all
three, Android gives the notification its own section in system settings, an avatar in the shade,
correct behaviour with Do Not Disturb's priority conversations, and the option to bubble.

Bubbles additionally require an activity that can be launched into its own document
(`ui/ConversationWindowActivity`, `documentLaunchMode="always"`).

**Privacy is applied when the notification is built**, not by hoping the system hides things. When
the user asks for sender-only or hidden notifications the message text is never put into the
notification at all, so it cannot leak through a watch, a car display or a screenshot.

Inline reply uses `RemoteInput` and goes through the same send path as the composer — it is written
to the provider, gets a delivery callback and appears in the thread.

---

## 7. Scheduled messages

Nothing is held in memory. Every pending message is a database row, and
`platform/scheduling/ScheduledMessageScheduler` turns those rows into alarms.

**Exact alarms are not guaranteed.** From Android 12 the user can refuse `SCHEDULE_EXACT_ALARM`,
and the system can revoke it. The app degrades honestly: without it the alarm becomes inexact and a
15-minute WorkManager sweep catches anything the system delayed. That is a few minutes of
imprecision, not a lost message, and the scheduling dialog says so *before* the user schedules.

`USE_EXACT_ALARM` is deliberately **not** declared: Play restricts it to alarm clocks and
calendars, and a messaging app does not qualify.

Alarms are cleared by a reboot and by an app update, and moved by a clock or timezone change. All
four are handled by `SystemEventReceiver`, which re-arms the queue and immediately sends anything
whose time passed while the device was off.

PendingIntents for alarms carry a distinct `data` URI per message, because extras are not part of
intent equality.

---

## 8. Permissions

Nothing is requested at startup. The SMS group arrives with the role. Contacts is asked the first
time a conversation would show a name, the microphone the first time the record button is held,
location the first time Location is tapped.

**The camera permission is not declared at all.** Photos and videos are captured through the system
camera app with `ACTION_IMAGE_CAPTURE` / `ACTION_VIDEO_CAPTURE` and a `FileProvider` target URI.
Declaring `CAMERA` would mean the intent throws until the permission is granted — worse for the
user and pointless for the app.

Media permissions are optional: they only power the in-app strip of recent photos. The system photo
picker and document picker need no permission at all and remain available.

---

## 9. SIM and dual-SIM

Dual-SIM support is entirely a matter of using the right `SmsManager`: one created for a specific
subscription id sends from that SIM. `createForSubscriptionId` from API 31, the deprecated
`getSmsManagerForSubscriptionId` below it.

`SubscriptionManager.getActiveSubscriptionInfoList()` needs `READ_PHONE_STATE`. Without it the app
works with an empty list and lets the platform choose, which is exactly right on a single-SIM
phone.

---

## 10. Blocking

Android has a system-wide blocked-number list that only the default SMS app and the default dialer
may write to (`BlockedNumberContract`). Blocking in this app therefore also blocks calls and
survives switching messaging apps.

`canCurrentUserBlockNumbers()` returns false on secondary users and work profiles. The app keeps
its own list in step and falls back to it there.

---

## 11. Storage and file sharing

Every file handed to another process goes through the `FileProvider` with a temporary, scoped
grant. No `file://` URI ever leaves the app; on API 24 and above that would throw
`FileUriExposedException`, and it would be wrong regardless.

Attachments picked from a picker are **copied into the app's cache** before being put in a draft. A
picker grant does not survive the process being killed, and a draft does; keeping only the original
URI would leave the user with a draft whose attachment can no longer be read.

"Save to Downloads" uses `MediaStore.Downloads`, so no storage permission is needed on any
supported version and the file lands where the system file manager expects it.

---

## 12. Backup

Android Auto Backup carries the DataStore settings only. The message mirror is deliberately
excluded: messages themselves live in the telephony provider, which the platform backs up
separately, and restoring a stale mirror would resurrect deleted threads.

Device-to-device transfer may carry the mirror, since it never leaves the user's hardware.

For anything the platform will not carry, Settings offers an explicit export to a file the user
chooses through the system document picker. Restoring writes SMS back into the telephony provider
(only possible while holding the role) and skips duplicates by matching sender, timestamp and body.
