package app.pingu.messages.data.local

import androidx.room.migration.Migration

/**
 * Room migrations.
 *
 * The database is at version 1, so there is nothing to migrate yet and [ALL] is empty. It exists
 * anyway, wired into [PinguDatabase.build], so adding a schema change is a two-step, reviewable
 * process rather than a scramble:
 *
 *  1. change the entity and bump [PinguDatabase.VERSION];
 *  2. add a `Migration(from, to)` here that performs the equivalent SQL, and add it to [ALL].
 *
 * The committed schema JSON under `app/schemas` makes step 2 mechanical: diff the two versions and
 * write the `ALTER TABLE` statements. `MigrationTest` in the instrumentation source set runs every
 * migration against a real database created from the previous schema.
 *
 * What must never appear in this file, or in the builder, is `fallbackToDestructiveMigration`:
 * dropping the tables on upgrade would delete the user's local reactions, drafts, scheduled
 * messages and block list.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
