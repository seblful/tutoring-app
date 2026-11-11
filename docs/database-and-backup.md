# Database and backup

Preserve on-device data and existing backup files when changing storage. Before shipping, use the
[release checklist](releasing.md#per-release-checklist).

## Persistence contracts

- `SnapshotDao` alone exposes whole-database reads/replacement; per-table methods are protected.
  Ordinary writes go through repositories.
- `BackupData` is independent of the internal snapshot. JSON keys already on disk must not be renamed.
  `BackupLesson`/`BackupAttendance` retain obsolete fields for reading old files, never writing them.
- Enum constant names are persisted in Room, SQL and backup JSON: renaming one requires migration
  (`StoredEnumNamesTest`). `EnumConverters` parses unknown names with a fallback only for non-money
  columns; money-bearing columns fail explicitly. Backup parsing refuses unknown enum names, but checks
  version first so a newer file is reported as newer rather than broken.

## Schema changes

- `BundledSQLiteDriver` supplies the same engine on every API and in JVM tests; versions live in the
  [catalog](../gradle/libs.versions.toml). Migrations override `migrate(SQLiteConnection)`; transactions
  use `useWriterConnection` through `RoomTransactor`, never `withTransaction`. There is no ICU or
  `LOCALIZED` collation: sort/match text in Kotlin.
- Bump `AppDatabase`'s schema version and add a migration for every change past `BASELINE_VERSION`.
  `MigrationChainTest` checks wiring; each migration needs a device test against exported schemas.
  `SupportedUpgradeTest` additionally checks every supported old schema through the current production
  factory with populated related rows; extend its cases when a new schema ships.
  Room sources, migration registration and exported schemas belong to `:shared`; Android device tests
  read `shared/schemas/` as assets. Platform database builders share `buildDatabase`'s configuration.
  Hosts use `openDatabase` and own the returned `KlasDatabase`; shared bindings own the DAO wiring.
- Keep queries view-free: `ClassDao` joins through `CLASS_ROWS`. Room validates views after migration
  without recreating them. Pre-v10 steps retain frozen view definitions checked against their schemas.
- **Rebuild tables carefully** using `MIGRATION_9_10` as the pattern: rename aside, create from exported
  Room DDL, copy named columns and IDs, carry `sqlite_sequence`, drop children before parents, recreate
  indices and run `PRAGMA foreign_key_check`. `DROP COLUMN` cannot remove indexed/FK/primary-key columns.
  `PRAGMA foreign_keys = OFF` has no effect inside Room's migration transaction; dropping a referenced
  parent with keys enabled fires `ON DELETE CASCADE`. Rebuild referencing tables too, because SQLite
  re-points their foreign keys at the renamed table. `MigrationChainTest` checks DDL, copies and order.
- Destructive migration fallback is **debug-only**, including downgrades: opening newer debug data with
  an older schema can wipe it. Release refuses an unmigratable database.

## Backup and restore

- Settings are a device-local Room row, written under a `Mutex` and excluded from backup — the guide's
  seen tips and hidden card (v14, `tipsSeen`, `firstStepsHidden`) included: a restore marks the guide done
  instead of carrying another phone's. Two kinds of setting do travel, in the file's `preferences`
  (`BackupPreferences`, backup v11): what the rows mean — currency, default lesson length, default group rate,
  auto-mark taught — which a restore applies, since «489» in the wrong currency is wrong data; and whether
  the file's phone had reminders, calendar sync and a weekly copy on, which no file can carry, so the phone it
  lands on asks for exactly those on Today (`phoneSetup`, schema v15; `PhoneSetup.UNKNOWN` for an older file).
  `earlyUser` (schema v17) travels too, so an early user's new phone stays free: a file without the field
  predates Klas Pro and grants it. What the store owns (`proOwned`), whether its ending is still to be said
  (`proEnded`) and when setup finished (`setupAtMillis`) stay on their phone.
  Undoing a restore puts this phone's own preferences back and leaves what it is asked for. The last backup (`lastExportAt`) is device-local too, but a restore
  moves it to the restored file's own date (or the restore's, for a file too old to carry one), never
  earlier: the data is then that file's, so «No backup yet» would be false. Undoing a restore leaves it, the
  kept copy being the app's, not a file the tutor holds. Android
  automatic backup is disabled; manual JSON export through SAF moves tutoring data between devices.
  SAF supports cloud destinations such as Drive without app-held cloud credentials. The export screen
  says which settings go with the file and which stay with the phone.
- Export reads one transactional snapshot. Import **replaces the database, never merges**, preserves
  IDs and commits in one transaction. `BackupIntegrity` checks readability before writes (including a
  lesson start within the years 1900–9999, which the all-time report draws a bar for each of);
  `BackupFailure` classifies refusals, and rows that break a database constraint are a broken file, not an
  unavailable one; `BackupRestore` owns rows, first-run state and mirror refresh.
- Older file versions import with defaults; newer ones are refused. v8 homework becomes rows through
  `legacyHomework`; old lesson notes are not restored. v10 added a payment's method, bumped because an
  older build would drop it on import; v11 added `preferences` for the same reason — an older build would
  restore every amount in its own currency. Dropping always-blank columns need not bump the file version. `BackupData.version` is independent of schema version and also supports transfer from
  the old `com.seblful.repetit` app ID, which cannot upgrade in place.
- JSON must explicitly contain `version`, `subjects` and `students`, including empty arrays in an empty
  backup. Missing envelope fields are malformed; defaults for later tables preserve older exports.
- Discard imported calendar event IDs: they belong to the exporting device. `remirrorAfter` reuses this
  device's IDs only for matching restored classes (row ID, lesson, student, start), withdraws unclaimed
  events and re-mirrors. This preserves past events and avoids re-inviting upcoming students when
  restoring this phone's own backup; past events are never newly created.
- A restore is asked about only after the file is read: `BackupManager.preview` parses it as the import
  will (a refused file is refused before the question) and the dialog names its date and counts.
  Exports carry an optional `exportedAtMillis`; older files lack it and older builds ignore it, so it
  needed no version bump. A date after now is a wrong clock: a restore counts from now instead, and a
  stored one counts as no backup (`AutoBackup.isDue`/`isStale`), so it cannot hold the weekly copy off.
- Before replacing anything, `BackupRestore` encodes what is on the phone and, once the import has
  succeeded, keeps it through `RestoreSnapshots` (Android: one file in `filesDir`, outside platform
  backup). Backup offers «Undo the last import» until the next import replaces that copy. An empty
  phone keeps nothing; a refused file leaves the previous copy as it was, but a restore whose copy could
  not be saved clears the previous one rather than offer older data. Undoing replaces everything,
  including what was added since, so it asks first.
- The weekly copy (`AutoBackup`) writes into a SAF folder the tutor picks (`settings.autoBackupFolder`, a
  persisted tree grant). A daily worker writes once a week has passed since the last export of any kind,
  so a manual export counts. Copies are named `klas-auto-backup-<day>.json`, apart from manual exports,
  and only the newest `KEEP` of those are kept. A folder that can no longer be written sets
  `autoBackupFailed` for the Backup page instead of retrying: a lost grant needs the folder picked again.
  No foreground service: WorkManager may run it late, never early. The Backup page warns after 14 days
  without a copy (`AutoBackup.isStale`), and so does Today — there only once there is an active student,
  so a fresh install is not told to back up nothing.
- A manual export can be sealed with a password (`SealedBackup`): PBKDF2-HMAC-SHA256 (iterations kept
  per file, capped on read) and AES-256-GCM, through the `BackupCipher` port. `BackupManager.parse`
  refuses a sealed file as `PasswordNeeded`, so the restore flow asks for the password first; a wrong
  one fails the GCM tag and reads as `WrongPassword`, not as a broken file. A lost password is a lost
  backup, and the export dialog says so. The password is never saved, but that one was asked for is, so
  a picker answer that arrives after the process was put away asks again instead of writing plain JSON.
  Weekly copies are not sealed: that would mean storing the
  password on the phone they are meant to outlive.
- `BackupExports` stamps each snapshot once and records that timestamp after writing the file, before
  reporting success. Slower older exports cannot move history backwards. Manual exports run in the
  application's scope so leaving Backup does not interrupt file writing or history recording.
