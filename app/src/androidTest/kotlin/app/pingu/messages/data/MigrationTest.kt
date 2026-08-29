package app.pingu.messages.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pingu.messages.data.local.Migrations
import app.pingu.messages.data.local.PinguDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Database migrations.
 *
 * At version 1 there is nothing to migrate, so this test does the two things that matter now:
 * it proves the exported schema is present and openable, and it fails the moment a future version
 * is added without a migration to go with it. That is the guard that stops an upgrade from
 * destroying somebody's reactions, drafts and block list.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PinguDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun theCurrentSchemaCanBeCreated() {
        helper.createDatabase(TEST_DATABASE, PinguDatabase.VERSION).close()
    }

    @Test
    fun everyVersionStepHasAMigration() {
        // One migration is needed for each step between 1 and the current version.
        val requiredSteps = PinguDatabase.VERSION - 1
        assertThat(Migrations.ALL.size).isEqualTo(requiredSteps)
    }

    private companion object {
        const val TEST_DATABASE = "migration-test.db"
    }
}
