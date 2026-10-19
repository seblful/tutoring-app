# Database and backup

How to change the schema or the backup file without losing a tutor's data. Before a release with a
schema change, see the checklist in [releasing.md](releasing.md#per-release-checklist).

## Seams

- `SnapshotDao` is the only whole-database seam (backup/restore); its per-table methods are `protected`,
  everything else goes through the ordinary repositories.
- Backup file format (`BackupData`) is a separate type from the internal snapshot — its field names are
  JSON keys already on disk and may never be renamed. Lessons and attendance have file types of their own
  (`BackupLesson`, `BackupAttendance`): v10 dropped columns that a v8 file still carries, so those keys are
  read and never written.
- An enum's constant name is a persisted value (status, attendance, mode, settings columns, SQL literals,
  backup JSON) — renaming one is a migration (`StoredEnumNamesTest`). Entities hold the enum types; Room
  stores the name through `EnumConverters`, which parses totally, never `valueOf`: a fallback where the
  column carries no money, a named failure where it does.
- A backup's enum names are typed on the way in, so an unknown one refuses the file as it is parsed; the
  version is read first, so a newer file is refused as newer rather than as broken.

## Schema and migrations

- SQLite is the bundled driver's (`BundledSQLiteDriver`, SQLite 3.50 with androidx.sqlite 2.6.2), compiled
  into the app rather than Android's: one engine on every API level, in the JVM tests, and on iOS when the
  data layer moves there. With a driver set, Room calls only `migrate(SQLiteConnection)`, so every step
  overrides that one, and transactions go through `useWriterConnection` (`RoomTransactor`), never
  `withTransaction`. It has no ICU and no `LOCALIZED` collation, so text is ordered and matched in Kotlin,
  not SQL (see `Daos.kt`).
- `Migrations.kt` holds exactly the steps since the baseline (`AppDatabase.BASELINE_VERSION`): v5 → v10
  today. Every schema change past baseline bumps the version and ships a migration
  (`MigrationChainTest` enforces wiring; each migration also gets a device test against the exported schema).
- No views (since v10): `ClassDao` joins `lessons` and `attendance` itself, every query starting from one
  `CLASS_ROWS` constant, so a migration never has a view to rebuild. Don't add one back — Room validates
  views after a migration but never recreates them. The steps up to v9 still rebuild their own version's
  `classes` view from a frozen `SELECT`, and `MigrationChainTest` checks each against that version's schema.
- A column is dropped by rebuilding its table. The bundled SQLite has `DROP COLUMN` on every API level, but
  it refuses an indexed, foreign-key or primary-key column, and v10 was written for Android's own SQLite,
  which has none below API 31. `MIGRATION_9_10` is the pattern: rename the old table aside, create the new one with Room's DDL from the
  exported schema, copy every row by column name (ids included) and carry its `sqlite_sequence` row over,
  drop the old tables child before parent, then recreate the indices and run `PRAGMA foreign_key_check`.
  `PRAGMA foreign_keys = OFF` does nothing inside Room's migration transaction, and with keys on, dropping
  a parent that something still points at fires its `ON DELETE CASCADE`. A table that refers to a rebuilt
  one is rebuilt too, because SQLite 3.26+ re-points its foreign key at the renamed copy. `MigrationChainTest`
  checks the DDL, the copied columns, the indices and the drop order against the exported schema.
- Destructive fallback (wipe-on-unmigratable) is debug-only; release refuses to open such a database. It
  also covers a *downgrade*, so a debug build of an older schema wipes a newer debug database.

## Settings

- Settings live in Room (single row), device-local, not backed up, written under a `Mutex`.
- Platform auto-backup is off; the app's own JSON export (SAF) is the only way data moves between devices.

## Backup

- Backup = one transaction's full DB snapshot → JSON. Ids preserved, import is one transaction. Older file versions still
  import (defaults fill gaps); newer ones are refused. Restore reproduces the file as given, except that
  a v8 file's homework is converted into rows (`legacyHomework`, `MIGRATION_8_9`'s twin) and an older
  file's notes are not restored. Imported calendar event ids are discarded: they name appointments on
  the exporting device, never permission to update or delete this device's appointments.
  After a successful restore, this database's own event ids are handed back to the restored classes
  that are the same class (row id, lesson, student, start — `CalendarMirror.remirrorAfter`); only the
  ones no restored class claims are withdrawn, then the schedule is re-mirrored. So restoring this
  phone's own backup keeps past lessons in the calendar (no event is ever created for one) and does
  not re-invite students to every upcoming lesson.
  A schema change that only drops columns that were always blank (v10)
  does not bump the file version.
- `BackupData.version` is independent of the schema version — it's the only bridge out of the old
  `com.seblful.repetit` app id, which has no upgrade path to Klas.
- `BackupIntegrity` checks readability before any row is written; `BackupFailure` classifies a refusal;
  `BackupRestore` owns the whole restore act (rows + first-run flag + reminders + calendar re-mirror).
- **Not Google Drive.** SAF already covers "put the file wherever you like", Drive included, without the
  app holding a cloud credential.
