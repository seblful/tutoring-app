package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentMethodMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun aPaymentRecordedBeforeTheUpgradeKeepsItsMoneyAndHasNoMethod() {
        helper.createDatabase(12).use { db ->
            db.execSQL(
                "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                    "VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)",
            )
            db.execSQL(
                "INSERT INTO payments (id, studentId, dateMillis, amountMinor) VALUES (7, 1, 1750000000000, 4500)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(13, listOf(Migrations.MIGRATION_12_13))

        val row = migrated.row(
            "SELECT * FROM payments WHERE id = 7",
            missing = "the payment went missing — a migration must not recreate the table",
        )
        assertNull("not asked when it was recorded", row["method"])
        assertEquals(4500L, row["amountMinor"])
        assertEquals(1750000000000L, row["dateMillis"])
    }

    private companion object {
        const val TEST_DB = "migration-payment-method.db"
    }
}
