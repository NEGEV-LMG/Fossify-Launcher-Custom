package org.fossify.home.databases

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS drawer_folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                `order` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS drawer_folder_apps (
                folder_id INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                PRIMARY KEY(folder_id, package_name),
                FOREIGN KEY(folder_id)
                    REFERENCES drawer_folders(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_drawer_folder_apps_package_name
            ON drawer_folder_apps(package_name)
            """.trimIndent()
        )
    }
}
