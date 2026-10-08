package org.fossify.home.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawer_folders")
data class DrawerFolder(
    @PrimaryKey(autoGenerate = true)
    var id: Long?,

    @ColumnInfo(name = "name")
    var name: String,

    @ColumnInfo(name = "order")
    var order: Int
) {
    constructor() : this(null, "", 0)
}
