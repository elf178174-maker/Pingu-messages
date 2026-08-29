# Architecture

## Layers

```
ui/          Compose screens, view models, navigation, theme
   |  depends on
platform/    Android system services: receivers, transports, notifications, alarms, widget
   |  depends on
data/        Telephony providers, Room mirror, DataStore settings, repositories
   |  depends on
domain/      The types the whole app speaks in
core/        Pure Kotlin. No Android imports at all.
```

Dependencies point one way. `core/` compiles and is tested on a plain JVM, which is why the rules
that are easy to get subtly wrong — phone-number matching, GSM segment counting, entity detection,
tapback parsing, accent-insensitive search, relative time — live there rather than inside a view
model where they would only be reachable through an emulator.

---

## Data flow

```
carrier ──► SmsDeliverReceiver / MmsWapPushReceiver
                    │  writes to
                    ▼
            content://sms, content://mms          ← the source of truth
                    │  mirrored by
                    ▼
            SyncRepository ──► Room (mirror + app-only metadata)
                    │  observed as Flows
                    ▼
            Repositories ──► ViewModels ──► Compose
```

Sending runs the other way and always writes to the provider **before** handing anything to the
radio, so a message is never lost if the process dies mid-send.

### Why the telephony provider is the source of truth

Because it is the interoperable one. A message written there is visible to every other app on the
phone, survives the user switching messaging apps, and is backed up by the platform. An app that
keeps messages only in its own database is holding them hostage.

Room exists for what the provider is bad at: sorting a thousand threads by a computed order,
searching ten thousand bodies, and storing the things SMS cannot carry.

| In Room, mirrored (replaceable) | In Room, app-owned (irreplaceable) |
| --- | --- |
| `conversations`, `messages`, `attachments` | `conversation_metadata` (pin, mute, archive, block, spam, folder, custom title, SIM) |
| | `reactions`, reply links on `messages` |
| | `drafts`, `draft_attachments` |
| | `scheduled_messages`, `scheduled_attachments` |
| | `blocked_numbers`, `spam_keywords`, `folders` |

They are separate tables precisely so a full re-sync can replace every mirrored row without
touching a user decision. `MessageDao.updateMirroredColumns` names the provider-owned columns
explicitly rather than replacing the whole row, so a re-sync cannot clear a reply link.

---

## Dependency injection

`di/AppContainer.kt` is the object graph: one file, lazy properties, constructor injection
throughout.

This is a deliberate choice over Hilt for an app of this size. The graph is readable top to bottom,
construction order is explicit, there is no generated code to step through when something is null,
and the build carries one fewer annotation processor to keep in step with the Kotlin version. Every
class still receives its collaborators through its constructor, so each is constructible in a test
with fakes, and swapping in Hilt later would touch only that one file.

View models are built by `ui/PinguViewModels.kt` using `viewModelFactory { initializer { … } }`,
with screen arguments (a thread id, the message being viewed) as constructor parameters. A view
model therefore cannot exist in an ambiguous state where its argument has not arrived yet.

---

## Performance

The app is built for a phone with years of history on it.

- **Windowed message loading.** The conversation screen asks for the newest *N* messages and grows
  *N* as the user scrolls back. A thread with tens of thousands of messages costs one indexed query
  of the visible range, never a full table read. Jumping to a search result grows the window just
  enough to include the target.
- **Bounded sync.** A first run mirrors the newest ~2,000 messages rather than the entire provider;
  older messages arrive when their thread is opened.
- **One query per list, not per row.** The conversation list projection joins the mirror, the
  metadata and the draft in a single statement. Contact names come from an in-memory index
  refreshed by a content observer, instead of a `PhoneLookup` IPC per visible row.
- **Attachment metadata on demand.** Size, dimensions and duration cost an IPC and a header decode,
  so they are computed for the thread being viewed rather than during a bulk backfill.
- **Reverse-layout lazy list.** New messages appear at the bottom with no measure-and-scroll pass.
- **Image budgets.** Coil is configured with a modest memory cache and a small disk cache, because
  attachments already exist in the provider and a second full copy of every photo is wasteful on a
  phone short of space.

---

## Error handling

`domain/model/AppError` is a sealed interface with a case per failure a person can act on: no SMS
role, no SIM, no service, no mobile data for MMS, message too large, permission missing, attachment
unreadable, no app for the intent, a platform send failure with its result code.

`ui/util/ErrorMessages.kt` turns each into a specific sentence. "Something went wrong" appears only
for genuinely unexpected failures — using it everywhere teaches users that error messages are not
worth reading.

Failed messages keep their content, show why they failed, and offer a retry that goes through the
same send path.

---

## Testing

| Layer | How it is tested |
| --- | --- |
| `core/` | Plain JVM unit tests. Fast enough to run on every push. |
| `data/mms/pdu/` | A full encode/decode round trip, including binary parts, several recipients, non-Latin text and the acknowledgement PDUs. |
| `data/local/` | Instrumentation tests against an in-memory Room database: sort order, filtering, cascade deletes, search, and metadata surviving a re-sync. |
| `ui/` | Compose component tests for the pieces that carry meaning: the conversation row, draft labelling, empty states, launch. |

What is *not* tested automatically is anything that requires a carrier: an actual send, an actual
MMS retrieval. Those need a real SIM and real money. The PDU round trip is the closest a build
server can get, and it is the test that would catch the class of bug that silently breaks MMS.

---

## Adding another transport

`platform/messaging/MessageSender` decides how a message travels and owns the SMS/MMS split.
`SendRequest` and `SendResult` are transport-agnostic. Adding a third transport means implementing
its equivalent of `SmsTransport`/`MmsTransport` and extending the decision in
`MessageSender.requiresMms` — no change to the UI, the repositories or the database.

This is why the app can say honestly that it is architected for more than SMS without claiming to
support RCS today.

---

## Database migrations

`exportSchema` is on and the build writes the generated JSON to `app/schemas`; keeping it in
version control makes every schema change a reviewable diff.

There is no `fallbackToDestructiveMigration` anywhere in the project. An unhandled schema change
fails loudly in development rather than silently deleting a user's reactions, drafts, scheduled
messages and block list on update. `MigrationTest` asserts that the number of migrations matches
the number of version steps, so adding a version without a migration fails the build.

To add one:

1. change the entity and bump `PinguDatabase.VERSION`;
2. diff the two schema JSON files;
3. add a `Migration(from, to)` to `Migrations.ALL` performing the equivalent SQL.
