package org.fossify.home.models

sealed class DrawerItem {
    data class App(
        val launcher: AppLauncher
    ) : DrawerItem()

    data class Folder(
        val folder: DrawerFolder,
        val apps: List<AppLauncher>
    ) : DrawerItem()
}
