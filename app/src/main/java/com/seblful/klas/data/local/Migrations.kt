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

    /** Everything above, in the order Room should be handed it. See `di/DatabaseModule`. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6)
}
