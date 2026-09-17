package com.seblful.klas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every way **into** the current schema from a version some phone is still at.
 *
 * The chain starts at [AppDatabase.BASELINE_VERSION] — the oldest version any `klas.db` can be — and every
 * single step from there to the version `@Database` declares must be here, or Room finds no path and the
 * app either wipes the database (debug) or refuses to open it (release). `MigrationChainTest` fails the
 * build over a missing step, a stale exported schema, and this list not being handed to `addMigrations`.
 *
 * This file existed once before, as four steps from v1, and was deleted: no device had ever held any of the
 * versions it migrated (see [AppDatabase.BASELINE_VERSION] for that argument in full). What is here now is
 * not that. v5 **shipped**, in `0.3.0`, so this step runs on real phones holding real students.
 *
 * The discipline for the next one:
 *
 * - A column added here must carry the same `DEFAULT` its entity declares as `defaultValue`, or Room's
 *   schema validation fails the instant the upgrade completes — a crash on first launch after a Play
 *   update, which is the worst place to find out.
 * - The instrumented tests (`androidTest`) are what prove a step *works*, against the exported schema JSONs
 *   in `app/schemas/`. Those need a device; the unit test only proves the step is *there*.
 * - Nothing here may drop or rebuild a table lightly: `minSdk` is 24, so `ALTER TABLE … DROP COLUMN` does
 *   not exist below Android 12 and a real removal means copying the table. `SettingsEntity.showGroupsTab`
 *   is the standing example of a column left in place for exactly that reason.
 * - **A view is not recreated for you.** Room validates views against the schema after the migrations run
 *   but recreates none of them, so any step that changes a column the `classes` view projects must drop
 *   and recreate it — from [CLASSES_VIEW_SQL], never from a copy. See [MIGRATION_6_7], which is where
 *   this was learned.
 */
object Migrations {

    /**
     * v5 → v6: the settings row learns which language the app is shown in.
     *
     * `'SYSTEM'` — follow the phone — is both the honest backfill and the right default, and matches
     * `SettingsEntity.language`'s `defaultValue`. It is not `'RUSSIAN'` even though Russian is the only
     * language any v5 install has ever displayed: the tutor never chose it, and stating a preference on
     * their behalf would outlive them changing their phone.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE settings ADD COLUMN language TEXT NOT NULL DEFAULT 'SYSTEM'")
        }
    }

    /**
     * v6 → v7: a lesson learns to carry a note, and the settings row learns about the evening digest.
     *
     * Three `ADD COLUMN`s and no table rebuild, which is what keeps this safe on API 24 (see the class
     * KDoc). Every `DEFAULT` here has a twin `defaultValue` on the entity — `''`, `0` and `1260` — and
     * Room validates the pair the instant the upgrade finishes, so they are written next to each other
     * deliberately.
     *
     * The backfills are all "this install has never had the feature", which is the honest answer in each
     * case: a lesson taught before the app could hold a note has none, and a tutor who has never been
     * offered an evening digest has not asked for one. Note the `''` — an empty string literal *inside*
     * the SQL, not an empty SQL fragment, which is the way this particular default is easy to write
     * wrongly and have SQLite accept.
     *
     * **The `classes` view is recreated here, and that is not optional.** Room *validates* views after a
     * migration and never recreates them, so a view left projecting the old columns is compared against
     * the new schema and the upgrade dies with `Migration didn't properly handle: classes` — on first
     * launch after an update, with everything rolled back. This step shipped that way for one install:
     * `lessons.notes` was added to [CLASSES_VIEW_SQL] and to the table, and the view on disk still had
     * thirteen columns. The `SELECT` is that same constant rather than a copy, so the recreated view and
     * the declared one cannot differ by so much as a space.
     *
     * The discipline this adds to the list above: **a migration that changes any column the view projects
     * recreates the view.** `LessonNotesMigrationTest` reads a lesson back *through* `classes`, which is
     * what fails when this is forgotten.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE settings ADD COLUMN unfinishedDigestEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE settings ADD COLUMN unfinishedMinuteOfDay INTEGER NOT NULL DEFAULT 1260")
            db.execSQL("DROP VIEW IF EXISTS `classes`")
            db.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_SQL")
        }
    }

    /** Everything above, in the order Room should be handed it. See `di/DatabaseModule`. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7)
}
