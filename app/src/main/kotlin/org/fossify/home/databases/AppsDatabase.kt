package org.fossify.home.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.fossify.home.helpers.Converters
import org.fossify.home.interfaces.AppLaunchersDao
import org.fossify.home.interfaces.DrawerFoldersDao
import org.fossify.home.interfaces.HiddenIconsDao
import org.fossify.home.interfaces.HomeScreenGridItemsDao
import org.fossify.home.models.AppLauncher
import org.fossify.home.models.DrawerFolder
import org.fossify.home.models.DrawerFolderApp
import org.fossify.home.models.HiddenIcon
import org.fossify.home.models.HomeScreenGridItem

@Database(
    entities = [
        AppLauncher::class,
        HomeScreenGridItem::class,
        HiddenIcon::class,
        DrawerFolder::class,
        DrawerFolderApp::class
    ],
    version = 6
)
@TypeConverters(Converters::class)
abstract class AppsDatabase : RoomDatabase() {

    abstract fun AppLaunchersDao(): AppLaunchersDao

    abstract fun HomeScreenGridItemsDao(): HomeScreenGridItemsDao

    abstract fun HiddenIconsDao(): HiddenIconsDao

    abstract fun DrawerFoldersDao(): DrawerFoldersDao

    companion object {
        private var db: AppsDatabase? = null

        fun getInstance(context: Context): AppsDatabase {
            if (db == null) {
                synchronized(AppsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(
                            context.applicationContext,
                            AppsDatabase::class.java,
                            "apps.db"
                        )
                            .addMigrations(MIGRATION_5_6)
                            .build()
                    }
                }
            }
            return db!!
        }
    }
}
