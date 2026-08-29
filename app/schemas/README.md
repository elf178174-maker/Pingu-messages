# Room schemas

Room exports the database schema as JSON into this directory on every build
(`room.schemaLocation` in `app/build.gradle.kts`).

Keep the generated files in version control: a schema change then shows up as a reviewable diff,
and `MigrationTestHelper` reads them from here to create a database at an older version and run the
migrations against it.

After changing an entity and bumping `PinguDatabase.VERSION`, build once, commit the new JSON file
alongside the migration you wrote for it.
