package org.fossify.home.interfaces

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.home.models.DrawerFolder
import org.fossify.home.models.DrawerFolderApp

@Dao
interface DrawerFoldersDao {

    @Query("SELECT * FROM drawer_folders ORDER BY `order`, id")
    fun getFolders(): List<DrawerFolder>

    @Query("SELECT * FROM drawer_folder_apps WHERE folder_id = :folderId")
    fun getApps(folderId: Long): List<DrawerFolderApp>

    @Query("SELECT * FROM drawer_folder_apps")
    fun getAllApps(): List<DrawerFolderApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolder(folder: DrawerFolder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApp(app: DrawerFolderApp)

    @Delete
    fun deleteApp(app: DrawerFolderApp)

    @Query("UPDATE drawer_folders SET name = :name WHERE id = :folderId")
    fun renameFolder(folderId: Long, name: String)

    @Query("DELETE FROM drawer_folder_apps WHERE folder_id = :folderId")
    fun deleteAppsFromFolder(folderId: Long)

    @Query("DELETE FROM drawer_folders WHERE id = :folderId")
    fun deleteFolder(folderId: Long)

    @Query("DELETE FROM drawer_folder_apps WHERE package_name = :packageName")
    fun removeAppFromAllFolders(packageName: String)
}
