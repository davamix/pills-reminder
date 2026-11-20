# P3 — Storage: Specification

| | |
|---|---|
| **Status** | Ready, v0.2 |
| **Phase** | P3, see [Implementation plan §5](../../IMPLEMENTATION_PLAN.md#5-phases) |
| **Depends on** | P2 |
| **Branch** | `phase/p3-storage` |
| **Handover** | [HANDOVER.md](HANDOVER.md) |

This document holds everything needed to implement the phase. Working on it shouldn't require reading the full specification or plan.

## 1. Goal

The app keeps its treatments and answers on the phone. A DataStore store holds them in one JSON document, version 1. It implements P2's `TreatmentRepository`, and `AppContainer` uses it in place of the in-memory fake.

A save cut off halfway leaves the previous data whole. A file the app can't read is kept as it is and reported, and is never written over. Nothing changes on screen yet: the screens that show the data come in P6 and P7.

## 2. Requirements covered

Quoted from [SPECIFICATION.md](../../SPECIFICATION.md) v0.5.

| ID | Requirement (quoted) | What this phase does for it |
|---|---|---|
| NF-4 | "**Data is kept safe:** treatments and takes survive app restarts, phone restarts and app updates. A save interrupted halfway, for example by a flat battery, never loses or damages what was saved before. There is no backup: uninstalling the app deletes everything." | The store, with DataStore's atomic writes (§4.3.3), the damaged-file rules (§4.3.5), and the device checks of §6 on the phone. Backup stays off, as P1 set it (§5). |
| TR-9 | "Treatments can be created, edited and deleted in the app. Deleting asks for confirmation, and also deletes every take recorded for the treatment." | The storage side only. P2's `deleteTreatment()` removes the treatment and its answers from `PillsData`, and the next write replaces the file, so the file no longer holds them. P5 removes the notifications, and P6 asks for confirmation. |
| TR-10 | "Editing a treatment that has already started changes only what is still to come. Takes that are due or answered keep their state; takes not yet due are worked out again from the new values." | The storage side only. The format stores each medicine's earlier schedules (P2's `EarlierSchedule`), so the takes and their states are the same after reading the file back. |
| TR-12 | "Finished treatments stay in the app until you delete them." | The store keeps every treatment, ended or not, until it is deleted. |

### 2.1 What later phases need from P3

| Phase | Needs |
|---|---|
| P5 | The store, holding P4's reminder state as well (snoozes, alerts played). P5 adds it to the format (Q3). The receivers read the same repository from `AppContainer`. The app stays in one process (§4.3.4). |
| P6, P7 | `StoredData.Unreadable`, to tell the user (Q1). `update` can throw `IOException` when a write fails, which a screen reports as "not saved". |
| P8 | The real repository everywhere. `AppContainer` provides it from P3 on, so nothing is left to wire there. |

## 3. Scope

### In scope

- **JSON format, version 1:** serializable types of their own, the mapping to and from P2's `PillsData`, and the checks on reading (§4.3.1, §4.3.2).
- **Store:** DataStore with a serializer for that format (plan `D-4`), behind P2's `TreatmentRepository`.
- **Damaged file:** kept byte for byte, never written over, reported as `StoredData.Unreadable`, and logged without content in debug builds (§4.3.5).
- **Interrupted writes:** the previous data survives (§4.3.3).
- **`AppContainer`:** the store replaces the in-memory fake.
- **Rules for later format changes,** with a golden file per version (§4.3.6).
- **Device checks** on the emulator and the phone: closing the app, an update, a process killed while writing, a restart (§6).

### Out of scope

| What | Phase |
|---|---|
| What the screens show when the data can't be read, or a save fails (Q1) | P6, P7 |
| Reminder state in the document: snoozes and alerts played. P4 defines it, and P5 adds it to the format (Q3). | P4, P5 |
| Removing a deleted or ended treatment's notifications | P5 |
| Any way to repair, export or back up the data. There is no backup (`NF-4`, spec Q17). | Not planned |

## 4. Design

### 4.1 Packages and files owned

P3 owns the package `io.github.davamix.pillsreminder.data` in `:app`. The file names are suggestions; the types in §4.2 and the format in §4.3.1 are what other phases rely on.

```
app/src/main/kotlin/io/github/davamix/pillsreminder/data/
    PillsDocument.kt                   the serializable types of format version 1
    PillsJson.kt                       the Json configuration; encodePillsData(), decodePillsData() and their checks
    PillsDataSerializer.kt             DataStore's Serializer<StoredData>
    DataStoreTreatmentRepository.kt    TreatmentRepository on DataStore, and create()
app/src/test/kotlin/io/github/davamix/pillsreminder/data/
    StoreExample.kt                    test data: spec §4.4, and data that uses every field of the format
    PillsJsonTest.kt
    DamagedDataTest.kt
    DataStoreTreatmentRepositoryTest.kt
app/src/test/resources/data/
    pills-v1.json                      the golden file of version 1 (§4.3.6)
app/src/androidTest/kotlin/io/github/davamix/pillsreminder/data/
    DataStoreOnDeviceTest.kt           on a file in the cache, never the app's own file
    StoreDeviceCheck.kt                the steps of the device checks, skipped unless asked for (§6)
```

**Shared files** this phase changes:

- `app/build.gradle.kts`: adds `libs.androidx.datastore` and `libs.kotlinx.serialization.json` to `:app`, and turns on `buildFeatures.buildConfig` for the debug-only log.
- `app/src/main/kotlin/io/github/davamix/pillsreminder/di/AppContainer.kt`: `repository` becomes the store, with the store's coroutine scope. Its comment on who adds what is updated.
- `CLAUDE.md`, under "Code structure": the `data` package, the store's file on the device, and the rule of §4.3.6 that a field added to `PillsData` goes into the format in the same change. Under "Tests": instrumented tests that go through `MainActivity` use the real store (§5).

The version catalog doesn't change: DataStore 1.3.0 and kotlinx-serialization-json 1.13.0 are already declared (P1).

### 4.2 Interfaces and data

Signatures, in Kotlin. Names may change during implementation if the shape stays the same; record it in the handover.

**From P2** (`core.model`), unchanged:

```kotlin
sealed interface StoredData {
    data class Readable(val data: PillsData) : StoredData
    data object Unreadable : StoredData          // the stored file can't be read; nothing may write over it
}

interface TreatmentRepository {
    val data: Flow<StoredData>                   // the stored data, then every change to it
    /** Atomic. transform must be pure. Throws UnreadableDataException when Unreadable; P3 may also throw IOException. */
    suspend fun update(transform: (PillsData) -> PillsData): PillsData
}

class UnreadableDataException : Exception
```

`PillsData` holds `treatments: List<Treatment>` and `answers: Map<TakeKey, Answer>`. A `Treatment` has `id`, `name`, `notes`, `medicines` and `endedAt: LocalDateTime?`. A `Medicine` has `id`, `name`, `dose`, `instructions`, `schedule` and `earlierSchedules: List<EarlierSchedule>`. A `Schedule` has `firstTake`, `interval` (`amount`, `unit`: `HOURS`, `DAYS`, `WEEKS` or `MINUTES`) and `duration` (`Days(count)`, `Weeks(count)` or `NoEnd`). An `EarlierSchedule` has `schedule` and `until`. An `Answer` is `Taken(at)` or `Skipped`. Every time is a `LocalDateTime` in whole minutes (P2-D3).

**New in P3** (`data`):

```kotlin
/** The result of reading stored bytes. */
internal sealed interface Decoded {
    data class Readable(val data: PillsData) : Decoded
    /** [reason] says what failed, and never holds treatment content (§5). */
    data class Unreadable(val reason: String) : Decoded
}

/** Version 1 of the format, in UTF-8 (§4.3.1). */
internal fun encodePillsData(data: PillsData): ByteArray

/** Never throws: content it can't read gives Unreadable (§4.3.2). */
internal fun decodePillsData(bytes: ByteArray): Decoded

/** DataStore's serializer. A missing file reads as empty data; a file it can't read, as Unreadable (P3-D1). */
class PillsDataSerializer(
    private val onUnreadable: (reason: String) -> Unit = {},
) : Serializer<StoredData> {
    override val defaultValue: StoredData = StoredData.Readable(PillsData())
    override suspend fun readFrom(input: InputStream): StoredData
    /** Writes only Readable data, after checking that it reads back the same (P3-D3). */
    override suspend fun writeTo(t: StoredData, output: OutputStream)
}

/** P2's repository on a DataStore. */
class DataStoreTreatmentRepository(
    private val store: DataStore<StoredData>,
) : TreatmentRepository {
    companion object {
        /** One per file and process (§4.3.4). [scope] lives as long as the store. */
        fun create(
            file: File,
            scope: CoroutineScope,
            onUnreadable: (reason: String) -> Unit = {},
        ): DataStoreTreatmentRepository
    }
}
```

**In `AppContainer`:**

```kotlin
private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

val repository: TreatmentRepository =
    DataStoreTreatmentRepository.create(
        file = appContext.dataStoreFile("pills.json"),       // files/datastore/pills.json
        scope = storeScope,
        onUnreadable = { reason -> if (BuildConfig.DEBUG) Log.e("PillsStore", "Stored data unreadable: $reason") },
    )
```

### 4.3 Behaviour

#### 4.3.1 The JSON format, version 1

One JSON object, in UTF-8, written compact (no indentation). The §4.4 example, with its two 08:00 takes answered, indented here for reading:

```json
{
  "schemaVersion": 1,
  "treatments": [
    {
      "id": "example",
      "name": "Example",
      "notes": "",
      "medicines": [
        {
          "id": "amoxicillin",
          "name": "Amoxicillin",
          "dose": "1 capsule",
          "instructions": "",
          "schedule": {
            "firstTake": "2026-11-23T08:00",
            "interval": { "amount": 8, "unit": "hours" },
            "duration": { "unit": "days", "count": 7 }
          },
          "earlierSchedules": []
        },
        {
          "id": "atrovent",
          "name": "Atrovent",
          "dose": "3 puffs",
          "instructions": "",
          "schedule": {
            "firstTake": "2026-11-23T08:00",
            "interval": { "amount": 8, "unit": "hours" },
            "duration": { "unit": "days", "count": 5 }
          },
          "earlierSchedules": []
        }
      ]
    }
  ],
  "answers": [
    { "medicineId": "amoxicillin", "plannedAt": "2026-11-23T08:00", "answer": "taken", "takenAt": "2026-11-23T08:05" },
    { "medicineId": "atrovent", "plannedAt": "2026-11-23T08:00", "answer": "skipped" }
  ]
}
```

| Field | Value |
|---|---|
| `schemaVersion` | `1`. Always first. |
| `treatments` | In `PillsData`'s order. Each has `id`, `name`, `notes` (empty for none), `medicines`, in their order, and `endedAt` only when it was ended early (`TR-11`). |
| `medicines[]` | `id`, `name`, `dose`, `instructions` (empty for none), `schedule` and `earlierSchedules`, oldest first, each `{ "schedule": {…}, "until": "…" }`. |
| `schedule` | `firstTake`, `interval` and `duration`. |
| `interval` | `amount` (1 or more), and `unit`: `"minutes"`, `"hours"`, `"days"` or `"weeks"`. Minutes come only from debug builds (plan `D-15`), but every build reads them. |
| `duration` | `{ "unit": "days", "count": n }`, `{ "unit": "weeks", "count": n }`, or `{ "unit": "noEnd" }`, with no count. |
| `answers` | One per answered take, sorted by `medicineId`, then `plannedAt`, so the same data always gives the same bytes. `answer` is `"taken"`, with `takenAt`, or `"skipped"`, without it. |
| Times | Local date-times in whole minutes, as ISO-8601 text with no seconds and no zone: `2026-11-23T08:00` (`LocalDateTime.toString()` of a whole-minute value). |
| IDs | The ID's `value`, as text. |

Every field is written, except the optional ones (`endedAt`, `takenAt`, a duration's `count`), which are left out when absent. Size: about 100 bytes per answer, so a medicine with no end every 8 hours adds about 110 KB a year. DataStore rewrites the whole file on each change; at this size that is fine (plan `D-4`).

#### 4.3.2 Reading

`decodePillsData` runs these steps, and the first that fails gives `Decoded.Unreadable`:

1. **Parse** the bytes as UTF-8 JSON. An empty file, text that isn't JSON, or JSON that isn't an object fails.
2. **Version:** `schemaVersion` must be present and a whole number. Version 1 is read as below. Any other version fails, a newer one included: it comes from a later app, and reading it with this one would lose what it added (P3-D2). Migrations from older versions go here once there are any (§4.3.6).
3. **Decode strictly** into the format types: every field required except the optional ones, no unknown fields, no wrong types, no unknown units (P3-D2).
4. **Map** to `PillsData`. P2's constructors check what they check (amounts and counts of at least 1, whole-minute first takes, earlier schedules in order of `until`). The mapping also refuses:
   - a time with seconds, or one that isn't a valid local date-time;
   - `"taken"` without `takenAt`, or `"skipped"` with one; a duration of days or weeks without `count`, or `noEnd` with one;
   - two treatments with the same ID, or two medicines with the same ID, in one treatment or across treatments;
   - two answers for the same take, since a map can hold only one of them.
5. **Answers that match no take** are kept as they are. P2's `MedicineTakes` ignores them, and P2's actions don't create them.

Nothing is thrown. Every exception from these steps becomes `Unreadable(reason)`. The reason names the step and the exception's class, such as `"decode: MissingFieldException"`, but never its message (§5).

`PillsDataSerializer.readFrom` reads the whole stream, decodes it, and returns `StoredData.Readable` or `StoredData.Unreadable`. For Unreadable it calls `onUnreadable(reason)` first. A missing file never reaches `readFrom`: DataStore gives `defaultValue`, the empty data.

#### 4.3.3 Writing

- **`update` on Unreadable data** throws `UnreadableDataException` from inside DataStore's transform, so DataStore writes nothing (P3-D1).
- **An update that changes nothing** writes nothing: DataStore compares the new value with the current one. A refused action (`endTreatment` on a treatment already ended, for example) leaves the file untouched.
- **The write check** (P3-D3): `writeTo` encodes the data, decodes the bytes again, and compares the result with the data. If they differ, it throws `IllegalStateException`, with a message that names no content, before writing a byte. DataStore then keeps the old file, and `update` throws. This catches a mapping that drops a field, and a value the reader would refuse, such as a taken time with seconds.
- **Atomic replacement:** DataStore writes the new bytes to a temporary file next to the store's file, syncs it to disk, and renames it over the old file. A write cut off at any point, for example by a flat battery or a killed process, leaves the old file whole. DataStore ignores a temporary file left behind, and replaces it at the next write. T3 notes its name.
- **A write that fails,** for example on a full disk, makes `update` throw an `IOException`. The data, in memory and in the file, stays as it was.

#### 4.3.4 The repository

- `data` is DataStore's `data`. It emits `StoredData.Readable` or `StoredData.Unreadable`, then each change. It never fails for bad content. If reading the file fails with an `IOException` (a file that exists but can't be opened), `data` emits `Unreadable` and ends, and `update` throws that exception.
- `update(transform)` runs `transform` inside `DataStore.updateData`, on the latest data, one update at a time, and returns the new data. It behaves as P2's fake does, and the helpers in `core.actions` work unchanged.
- **One store per file in the process.** DataStore refuses a second store for a file while the first one's scope is active. `AppContainer` creates the only one, with a scope that lasts as long as the process. The app must stay in one process: no component may set `android:process` (P5 adds the receivers).
- **The file** is `files/datastore/pills.json` in the app's own credential-encrypted storage (plan `D-16`), so it can be read only after the phone's first unlock since its restart. The debug build's file is separate from the release build's, as the application IDs differ.
- The first read happens when `data` is first collected, or at the first `update`, on `Dispatchers.IO`.

#### 4.3.5 A file that can't be read

- `data` emits `StoredData.Unreadable`, and every `update` throws `UnreadableDataException`.
- The file stays as it is, byte for byte. Nothing in the app deletes, renames or replaces it.
- Debug builds log one line, with the reason and no content. Release builds log nothing (plan §3).
- The screens tell the user (P6, P7, Q1). The app offers no repair: the file is kept so that it can be read over adb (`phone` skill, "Read the app's private files"). Clearing the app's storage in Android's settings starts again, and deletes it.
- It stays Unreadable until the process ends. If the file is fixed or removed in the meantime, the next start reads it again.

#### 4.3.6 Later changes to the format

For the phases after P3, and written into CLAUDE.md in T4:

- **A field added to `PillsData`**, or to a type in it, goes into the format in the same change, with a test. The write check (§4.3.3) refuses to write data with that field set until it does.
- **Golden files never change.** `pills-v1.json` holds version 1 and stays as it is.
- **A change that version-1 files still decode as,** such as a new optional field, keeps version 1. Add a golden test of the new field, and keep reading `pills-v1.json`.
- **Any other change** raises `schemaVersion`. It adds a migration from the previous version, on the parsed JSON before step 3 of §4.3.2, and a new golden file. Every earlier golden file must still decode.

### 4.4 Dependencies

- **Libraries,** both already in the version catalog, now added to `:app`:
  - `androidx.datastore:datastore` 1.3.0, the typed DataStore (plan `D-4`). It brings `datastore-core`, `datastore-core-okio`, `com.squareup.okio:okio`, `androidx.annotation` and kotlinx-coroutines (from its Gradle metadata, Q5). T1 lists what really arrives.
  - `org.jetbrains.kotlinx:kotlinx-serialization-json` 1.13.0. The serialization plugin is already applied in `:app`, for the routes.
- **From P2:** `TreatmentRepository`, `StoredData`, `UnreadableDataException`, the model types, and the actions in `core.actions`. P2's handover leaves this phase one item of pending wiring: replace `InMemoryTreatmentRepository` in `AppContainer`, with a format that covers all of `PillsData`.
- **From P1:** the backup settings (`allowBackup="false"`, data extraction rules that exclude every domain), the permission allowlist check in `build`, and JUnit Jupiter in `:app`'s unit tests.

**Pitfalls that apply here** (from CLAUDE.md, P1, P2 and DataStore):

- **Two stores on one file:** DataStore throws `IllegalStateException` while another store's scope for the same file is active. A test that "restarts the app" cancels the first store's scope and waits for its job to end before creating the second.
- **Coroutine tests:** in `runTest`, `advanceUntilIdle()` ignores work that only `backgroundScope` has scheduled. Wait for a value with `repository.data.first { … }` (P2). DataStore does real file IO, so give it a real scope, such as `CoroutineScope(Dispatchers.IO + Job())`, cancelled at the end of each test, rather than the test's virtual time.
- **`android.util.Log` doesn't work in `:app`'s JVM unit tests:** the stub throws "Method … not mocked". So the store takes `onUnreadable` as a function, and only `AppContainer` calls `Log`.
- **kotlinx.serialization's messages quote the JSON input,** names and doses included ("JSON input: …"). Never log a message, nor let a decoding exception escape, since a crash writes its message to the log.
- **Test names:** JVM names can't contain `:` (nor `.` `;` `/` `\` `[` `]` `<` `>`): write times as `0800`. Instrumented names take no punctuation except `-`, `_` and `$`: no commas. A single instrumented method can't be run by name when its name has spaces, because `am instrument -e class` splits at them; run its class (CLAUDE.md).
- **The phone:** an instrumented run needs the phone unlocked, and `allow_test_start` after a fresh install, or it hangs (P1, P2). Use `instrument`, never `connectedDebugAndroidTest`, which uninstalls the app and deletes its data, the device check's included.
- **ktlint:** run `./gradlew ktlintFormat` as its own command before `build`, and wrap by hand any long line it leaves. Write Gradle's output to a file instead of piping it (CLAUDE.md).

### 4.5 Decisions made in this phase

| ID | Decision | Reason |
|---|---|---|
| P3-D1 | The DataStore holds `StoredData` itself. The serializer turns a file it can't read into `StoredData.Unreadable`, instead of throwing `CorruptionException`, and no corruption handler is set. `update` refuses inside the transform. | DataStore's `ReplaceFileCorruptionHandler` would write over the damaged file, which `NF-4` forbids. With the value in the store, `data` doesn't fail on bad content, and nothing is ever written on top of it. |
| P3-D2 | Reading is strict: unknown fields, missing fields and a newer `schemaVersion` make the file Unreadable (Q2). | After a lenient read, the next write would drop what wasn't understood, losing data silently. |
| P3-D3 | Every write is first decoded again in memory and compared with the data. A difference refuses the write. | A mapping that misses a field, or a value the reader refuses, would otherwise lose data at the write, or leave a file the next start can't read. The data is small, so the check costs a few milliseconds. |
| P3-D4 | The format has its own serializable types, mapped to and from `PillsData` (as P2-D2 decided), with a golden file per version that never changes. | The stored format is explicit, a change to a domain type can't change it silently, and the golden files prove that old files still read after an update (`NF-4`). |
| P3-D5 | `:app`'s tests have their own test data (`StoreExample`), not `:core`'s `ExampleData`. | `:app`'s tests can't see `:core`'s test sources. Moving `ExampleData` into shared test fixtures would touch files P4 works on in parallel. A later phase can share it. |
| P3-D6 | The store reports a damaged file through `onUnreadable(reason)`. `AppContainer` logs it in debug builds only, and the reason never holds content. | Plan §3: logs hold no treatment content, and release builds don't log. JVM tests can't call `android.util.Log`. |
| P3-D7 | The device checks use an instrumented class, `StoreDeviceCheck`, that does nothing unless run with `-e storeCheck <step>` (Q4). | The checks need the app's own write path and its real file. A normal suite run skips it, so it never touches the data. |

## 5. Security and privacy

Plan §3 applies. The data is health data, and this phase is where it reaches the disk. `NF-3`: "**Offline and private:** no network permission, no accounts, no analytics, no ads. Treatment data stays on the phone. Android's cloud backup is turned off."

- **Where it is stored:** only `files/datastore/pills.json`, in the app's private, credential-encrypted storage (plan `D-16`), plus DataStore's temporary file next to it while it writes. No copy goes to the cache, external storage or anywhere else. Tests use temporary folders on the PC, and the app's cache on a device.
- **Backup:** P1's `allowBackup="false"` and data extraction rules, which exclude the `file` domain among all the others, already cover the store. They don't change.
- **Logs:** one line in debug builds when the file can't be read, holding only the reason: the step and an exception class name. Never a message, a name, a dose, instructions or notes. A test checks that the reason for a damaged file holding names contains none of them.
- **Exceptions:** decoding exceptions never escape the store, since their messages quote the JSON. The write check's message names no content.
- **Deleting (`TR-9`):** once `deleteTreatment` is written, the file no longer holds the treatment, its medicines or its answers, because DataStore writes a new file and renames it over the old one. A test reads the file to confirm it. What the phone's flash storage keeps of old blocks is outside the app's reach.
- **Test data:** spec §4.4 ("Example", Amoxicillin, Atrovent), with made-up IDs, also in the golden file. The repo is public: no real treatments. On the phone, `StoreDeviceCheck` writes only the example, refuses to start if the stored data isn't empty, and removes the example at the end.
- **Instrumented tests and the real data:** from P3 on, `AppContainer.repository` is the real file. An instrumented test that goes through `MainActivity` or the container reads and writes the data the app really has, on the phone too. P3's own tests use a file in the cache, apart from `StoreDeviceCheck`. Later phases should build ViewModels with fakes, as CLAUDE.md already says, and a test through the activity must leave the data as it found it. T4 adds this to CLAUDE.md, and the handover lists it for P6, P7 and P8.
- **Dependencies:** DataStore (AndroidX) and kotlinx-serialization-json (JetBrains), from Google Maven and Maven Central. Okio, from Square, comes with DataStore, which plan §3 accepts (Q5). The permission allowlist check confirms that no permission is added. T1 also compares the merged manifests' components with `main`'s.

## 6. Testing

Unit tests are JUnit Jupiter tests in `:app` (`app/src/test`), on files in a temporary folder (`@TempDir`), and run in `./gradlew build`. Instrumented tests are JUnit 4.

| Test | Type | Covers |
|---|---|---|
| `PillsJsonTest`: `StoreExample`'s full data round-trips unchanged: two treatments, one ended; several earlier schedules, including one that holds no takes (as P2's `saveTreatment` can store); every interval unit and duration kind; Taken and Skipped; empty notes and instructions; text with accents, `½`, quotes, a backslash, a new line and an emoji. Empty data round-trips. The same data gives the same bytes, whatever the map's order. Times are written without seconds. The takes and states worked out from the decoded data match the original's at several moments. | Unit | `NF-4`, `TR-10`, `TR-12`, §4.3.1 |
| `PillsJsonTest`, golden file: `pills-v1.json` decodes to `StoreExample`'s full data, and encoding that data gives the same JSON tree as the file. | Unit | `NF-4` (app updates), §4.3.6 |
| `DamagedDataTest`: each case gives Unreadable with a reason: an empty file; not JSON; truncated JSON; a JSON array; `schemaVersion` missing, `0`, `2` or `"1"`; an unknown field; a missing field; a wrong type; an unknown unit; an amount or count of 0; days without a count, or `noEnd` with one; an invalid date; a time with seconds; `taken` without `takenAt`, or `skipped` with it; two answers for one take; a treatment ID twice; a medicine ID twice, within and across treatments; earlier schedules out of order. An answer that matches no take is kept. No reason contains the names or doses in the damaged input. | Unit | `NF-4` (damaged file), §4.3.2, §5 |
| `DataStoreTreatmentRepositoryTest`, round trip: with no file the data is empty, and no file is created until the first update; `update` returns the new data and `data` emits it; after "restarting" (a new store on the same file), the data is the same; the actions in `core.actions` (save, edit a started medicine, mark taken, end, delete) give the same data after a restart; concurrent updates lose none of one another. | Unit | `NF-4` (app restarts), `TR-10`, `TR-12`, §4.3.4 |
| `DataStoreTreatmentRepositoryTest`, deleting: after `deleteTreatment` and a restart, the file's text holds none of the treatment's or its medicines' IDs, nor its name. | Unit | `TR-9` |
| `DataStoreTreatmentRepositoryTest`, damaged file: `data` is Unreadable; `update` throws `UnreadableDataException`; the file's bytes are unchanged and no temporary file is left; `onUnreadable` is called with a reason. A file that can't be opened (a folder in its place) is Unreadable too. | Unit | `NF-4`, §4.3.5 |
| `DataStoreTreatmentRepositoryTest`, interrupted write: a serializer that writes half the bytes and then throws makes `update` throw; `data` keeps the old data; after a restart the old data reads back and the file's bytes are unchanged. A partial temporary file left by a "crash" is ignored at the restart, and the next update succeeds. | Unit | `NF-4` (interrupted save), §4.3.3 |
| `DataStoreTreatmentRepositoryTest`, write check: data that wouldn't read back the same (a taken time with seconds; a treatment ID twice) isn't written; `update` throws and the file is unchanged. A refused action leaves the file's bytes and time unchanged. | Unit | P3-D3, §4.3.3 |
| `DataStoreOnDeviceTest`: on a file in the app's cache, on the device's file system: write, restart the store, read the same data; a damaged file stays byte for byte and `update` is refused. | Instrumented (emulator and phone) | `NF-4` on Android |
| `NavigationSmokeTest` (P1) still passes: the app starts with the real store. | Instrumented (emulator and phone) | `AppContainer` |

### Manual checks

`StoreDeviceCheck` runs one step per call, chosen with `-e storeCheck <step>`. With no argument, it is skipped, so a whole-suite run never touches the data. Its steps:

- `seed`: fails unless the stored data is empty. Then it saves the §4.4 example, marks Amoxicillin's 08:00 take Taken and skips Atrovent's, as in §4.3.1. It goes through `AppContainer.repository` and the actions in `core.actions`, with a fixed clock at Monday 23 November 2026, 08:05, when both takes are Due.
- `verify`: the stored data is readable and equals what `seed` wrote, apart from the notes that `churn` changes.
- `churn`: changes the treatment's notes in a loop, for up to 60 seconds, so that the process can be killed in the middle of a write.
- `remove`: deletes the example treatment, leaving the data empty as before.

Run each with `instrument data.StoreDeviceCheck -e storeCheck <step>` (`phone` skill). Record the file's size and SHA-256 with `$adb exec-out run-as $pkg cat files/datastore/pills.json | sha256sum` before and after each event.

| # | Device | Steps | Expected |
|---|---|---|---|
| M1 | Emulator, then phone | `seed`, then `verify` | Passes. `files/datastore/pills.json` exists. |
| M2 | Emulator, then phone | Close the app (`force_stop`: P3 sets no alarms yet), then `verify` | Passes, and the hash is unchanged. |
| M3 | Emulator, then phone | Update the app: `gradle installDebug` (`adb install -r`), then `verify` | Passes, and the hash is unchanged. |
| M4 | Emulator, then phone | Start `churn` in the background, and run `$adb shell am force-stop $pkg` after a few seconds. Then `verify`. Repeat 3 times. | `verify` passes each time: the data is readable, with one of the notes `churn` wrote. |
| M5 | Emulator, then phone | Restart the device. On the phone, ask the user first, and the user unlocks it afterwards. `wait_boot`, then `verify`. | Passes, and the hash is unchanged since M4. |
| M6 | Emulator, then phone | `remove`, then check with `run-as` that the file's text holds no "Amoxicillin". | The data is empty again: the phone is left as it was found. |
| M7 | Emulator only | Uninstall the debug app, install it again, and run the suite | The data is empty: uninstalling deletes everything (`NF-4`). The phone is left out, because an uninstall resets `allow_test_start`'s permission. |

Record in the handover, for each device: the date, the Android and HyperOS versions, and each result.

## 7. Tasks

| ID | Task |
|---|---|
| T1 | Dependencies and a first measurement. Add DataStore and kotlinx-serialization-json to `:app`, and turn on `buildConfig`. Check that `build` passes, permission allowlist included, and that the merged debug and release manifests have the same permissions and components as `main`'s. List the new runtime dependencies (Q5). Confirm with a first unit test that DataStore writes, replaces and reads a file in `:app`'s JVM unit tests, on this PC (Windows) and in CI (Linux) (Q6). |
| T2 | The format: `PillsDocument`, `encodePillsData`, `decodePillsData` and their checks (§4.3.1, §4.3.2); `StoreExample`; the golden file `pills-v1.json`. `PillsJsonTest` and `DamagedDataTest`. |
| T3 | The store: `PillsDataSerializer`, with the write check, and `DataStoreTreatmentRepository` with `create()` (§4.3.3–§4.3.5). `DataStoreTreatmentRepositoryTest`. Note the name of DataStore's temporary file. |
| T4 | Wiring and the device. `AppContainer` uses the store, with the debug log. `DataStoreOnDeviceTest` and `StoreDeviceCheck`. Every instrumented test passes on the emulator and the phone. `CLAUDE.md` (§4.1). |
| T5 | The manual checks M1–M7, on the emulator, then on the phone, with the restart agreed with the user. Results in the handover. Check every row of §6 against the tests, then the pull request. |

## 8. Done criteria

- [ ] All tasks done
- [ ] `./gradlew build` passes locally and in CI, with `:app`'s new unit tests among them (the totals recorded in the handover)
- [ ] Unit tests cover every row of §6, including the plan's list for P3: a save-and-load round trip, the schema version, an interrupted write and a damaged file
- [ ] Every instrumented test passes on the emulator and the phone
- [ ] On the phone, the data survives closing the app, an app update (`adb install -r`), a process killed while writing, and a restart (M1–M6), with the results in the handover
- [ ] The merged manifests have the same permissions and components as `main`'s, and the new runtime dependencies are listed in the handover
- [ ] HANDOVER.md is up to date, and the plan's status table is updated

## 9. Open questions

None at the moment. New questions are numbered from Q7.

### Answered

The review confirmed the six draft assumptions. Q1, Q3 and Q5 went into plan v0.3 (§2.4, §3 and P5–P7 in §5.2).

| # | Question | Decision |
|---|---|---|
| Q1 | Who tells the user that the data can't be read, and what they can do then. Plan §2.4 says the app "says so". | P3 provides `StoredData.Unreadable` and a debug log line. P7's home screen shows a message, and P6's editors refuse to save, as pending wiring for them. The app offers no repair: the file is kept, to be read over adb, and clearing the app's storage in Android's settings starts again. (§4.3.5, plan §2.4) |
| Q2 | Strict reading: a file with an unknown field, or a newer `schemaVersion`, is Unreadable, rather than read with the unknown parts dropped. | Strict (P3-D2). It matters most during development: every build has `versionCode` 1, so an older build can be installed over a newer one, for example from another branch. That build then leaves the newer file untouched, and the newer build reads it again once it's back. A lenient read would have dropped the newer fields at its first write. |
| Q3 | Reminder state (P4's snoozes and alerts played) isn't in version 1. Plan §2.4 keeps it in the same document. | P5, which depends on both P3 and P4, adds it to the format, following §4.3.6. P3 leaves it as pending wiring. (Plan §2.4, P5) |
| Q4 | The device checks write the §4.4 example into the debug app's real data on the phone, through `StoreDeviceCheck`, and remove it afterwards. M5 restarts the phone once. | Agreed. The check refuses to run if the data isn't empty, and does nothing unless asked for. The restart waits for the user's go-ahead. (§6, P3-D7) |
| Q5 | DataStore 1.3.0 depends on Okio, a Square library, through `datastore-core-okio`. Plan §3 asked for "only well-known AndroidX, JetBrains and Google libraries". | Accepted: it comes with AndroidX's own library, from Maven Central, and the app doesn't use it directly. T1 records it in the handover. (Plan §3) |
| Q6 | DataStore's file storage in `:app`'s JVM unit tests: it uses the Android variant of `datastore-core`, and replaces a file by renaming over it, which can behave differently on Windows. | Assumed to work on this PC and on CI's Linux. T1 checks it first. If it doesn't, stop and ask: the store's tests could use DataStore's Okio storage, or move to instrumented tests. |

## 10. Change log

| Version | Date | Changes |
|---|---|---|
| 0.1 | 2026-11-20 | First draft |
| 0.2 | 2026-11-20 | Review: Q1–Q6 answered, confirming the draft assumptions. Q1, Q3 and Q5 went into plan v0.3. Ready. |
