# Database and backup

How to change the schema or the backup file without losing a tutor's data. Before a release with a
schema change, see the checklist in [releasing.md](releasing.md#per-release-checklist).

## Seams

- `SnapshotDao` is the only whole-database seam (backup/restore); its per-table methods are `protected`,
  everything else goes through the ordinary repositories.
- Backup file format (`BackupData`) is a separate type from the internal snapshot — its field names are
  JSON keys already on disk and may never be renamed.
- An enum's constant name is a persisted value (status, mode columns, SQL literals) — renaming one is a
  migration (`StoredEnumNamesTest`). Storage parses with a total function, never `valueOf`.

## Schema and migrations

- `Migrations.kt` holds exactly the steps since the baseline (`AppDatabase.BASELINE_VERSION`). Every schema change past baseline bumps the version and ships a migration
  (`MigrationChainTest` enforces wiring; each migration also gets a device test against the exported schema).
- A step that changes a column the `classes` view projects drops and recreates the view — Room validates
  views but never rebuilds them. The latest such step reads `CLASSES_VIEW_SQL`; earlier ones keep their own
  version's `SELECT` frozen, and `MigrationChainTest` runs every step against that version's exported view.
- A column is left in place rather than dropped (no `DROP COLUMN` below API 31, and a table rebuild is a
  risk to the data): `settings.showGroupsTab`, and since v9 `lessons.notes`, `lessons.homework`,
  `attendance.homeworkResult` — emptied by `MIGRATION_8_9`, written empty ever after, read by nothing.
- Destructive fallback (wipe-on-unmigratable) is debug-only; release refuses to open such a database.

## Settings

- Settings live in Room (single row), device-local, not backed up, written under a `Mutex`.
- Platform auto-backup is off; the app's own JSON export (SAF) is the only way data moves between devices.

## Backup

- Backup = full DB snapshot → JSON. Ids preserved, import is one transaction. Older file versions still
  import (defaults fill gaps); newer ones are refused. Restore reproduces the file as given, except that
  a v8 file's homework is converted into rows (`legacyHomework`, `MIGRATION_8_9`'s twin) and an older
  file's notes are not restored.
- `BackupData.version` is independent of the schema version — it's the only bridge out of the old
  `com.seblful.repetit` app id, which has no upgrade path to Klas.
- `BackupIntegrity` checks readability before any row is written; `BackupFailure` classifies a refusal;
  `BackupRestore` owns the whole restore act (rows + first-run flag + reminders + calendar re-mirror).
- **Not Google Drive.** SAF already covers "put the file wherever you like", Drive included, without the
  app holding a cloud credential.
