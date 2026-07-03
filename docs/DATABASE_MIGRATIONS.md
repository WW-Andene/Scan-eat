# Database Migration Strategy

ScanEat currently stores browser/PWA data in IndexedDB stores under the `scanneat`
database. The Kotlin runtime in this repository is a Ktor backend and does not yet
contain Room entities, DAOs, or an Android `RoomDatabase` class. This document is
the migration contract to follow before adding those native persistence classes.

## Required rules for future Room schema changes

1. Keep `exportSchema = true` on every Room database declaration.
2. Commit generated schema JSON under `schemas/` for every released version.
3. Never rename or drop user-data columns without a `Migration` that copies data
   into the replacement shape.
4. Register every migration in the database builder; destructive migration is
   allowed only for development builds or explicitly disposable caches.
5. Add a regression test that opens the oldest committed schema and migrates it
   to the newest schema.

## Initial Room template

```kotlin
@Database(
    entities = [
        ScanEntity::class,
        PendingScanEntity::class,
        MealEntity::class,
        UserSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ScanEatDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun pendingScanDao(): PendingScanDao

    companion object {
        fun build(context: Context): ScanEatDatabase = Room.databaseBuilder(
            context,
            ScanEatDatabase::class.java,
            "scaneat.db",
        )
            .addMigrations(/* Migration(1, 2), Migration(2, 3), ... */)
            .build()
    }
}
```

## Pending scan retry fields

Any native `PendingScanEntity` must preserve retry metadata equivalent to the web
queue store:

- `retryCount: Int`
- `lastError: String?`
- `nextRetryTime: Long?`
- `status: String` (`pending`, `processing`, `complete`, `failed`)

These fields prevent data loss when a photo scan is interrupted or rate-limited.
