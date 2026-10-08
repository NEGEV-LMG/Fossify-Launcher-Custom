package org.fossify.home.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "drawer_folder_apps",
    primaryKeys = ["folder_id", "package_name"]
)
data class DrawerFolderApp(
    @ColumnInfo(name = "folder_id")
    var folderId: Long,

    @ColumnInfo(name = "package_name")
    var packageName: String
)
