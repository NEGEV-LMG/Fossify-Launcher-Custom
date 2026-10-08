package org.fossify.home.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.home.activities.MainActivity
import org.fossify.home.adapters.LaunchersAdapter
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.databinding.AllAppsFragmentBinding
import org.fossify.home.databinding.ItemLauncherLabelBinding
import org.fossify.home.extensions.config
import org.fossify.home.extensions.launchApp
import org.fossify.home.extensions.setupDrawerBackground
import org.fossify.home.helpers.ITEM_TYPE_ICON
import org.fossify.home.interfaces.AllAppsListener
import org.fossify.home.models.AppLauncher
import org.fossify.home.models.DrawerFolder
import org.fossify.home.models.DrawerFolderApp
import org.fossify.home.models.DrawerItem
import org.fossify.home.models.HomeScreenGridItem

class AllAppsFragment(
    context: Context,
    attributeSet: AttributeSet
) : MyFragment<AllAppsFragmentBinding>(
    context,
    attributeSet
), AllAppsListener {

    private var lastTouchCoords =
        Pair(0f, 0f)

    var touchDownY = -1

    var ignoreTouches = false

    private var launchers =
        emptyList<AppLauncher>()

    private var drawerFolders =
        emptyList<DrawerFolder>()

    private var drawerFolderApps =
        emptyList<DrawerFolderApp>()

    private var drawerFolderDialog: AlertDialog? = null

    fun closeDrawerFolderDialog() {
        drawerFolderDialog?.dismiss()
        drawerFolderDialog = null
    }

    fun showDrawerFolderAppMenu(
        launcher: AppLauncher,
        anchorView: View
    ) {
        val gridItem =
            HomeScreenGridItem(
                id = null,
                left = -1,
                top = -1,
                right = -1,
                bottom = -1,
                page = 0,
                packageName =
                    launcher.packageName,
                activityName =
                    launcher.activityName,
                title =
                    launcher.title,
                type =
                    ITEM_TYPE_ICON,
                className = "",
                widgetId = -1,
                shortcutId = "",
                icon = null,
                docked = false,
                parentId = null,
                drawable =
                    launcher.drawable
            )

        activity?.showPopupMenuForView(
            anchorView = anchorView,
            gridItem = gridItem,
            isOnAllAppsFragment = true
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(
        activity: MainActivity
    ) {
        this.activity = activity
        this.binding =
            AllAppsFragmentBinding.bind(this)

        binding.allAppsGrid.setOnTouchListener {
                _,
                event ->

            if (
                event.actionMasked ==
                MotionEvent.ACTION_UP ||
                event.actionMasked ==
                MotionEvent.ACTION_CANCEL
            ) {
                touchDownY = -1
            }

            false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupDrawerBackground()
    }

    fun onResume() {
        if (
            binding.allAppsGrid.layoutManager ==
            null ||
            binding.allAppsGrid.adapter ==
            null
        ) {
            return
        }

        val layoutManager =
            binding.allAppsGrid.layoutManager
                as MyGridLayoutManager

        if (
            layoutManager.spanCount !=
            context.config.drawerColumnCount
        ) {
            onConfigurationChanged()

            (
                binding.allAppsGrid.adapter
                    as LaunchersAdapter
                ).notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGrid.scrollToPosition(0)

        binding.allAppsFastscroller
            .resetManualScrolling()

        setupViews()

        val layoutManager =
            binding.allAppsGrid.layoutManager
                as MyGridLayoutManager

        layoutManager.spanCount =
            context.config.drawerColumnCount

        refreshDrawerItems()
    }

    override fun onInterceptTouchEvent(
        event: MotionEvent?
    ): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(
                event
            )
        }

        var shouldIntercept = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY =
                    event.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreTouches) {
                    if (
                        lastTouchCoords.first !=
                        event.x ||
                        lastTouchCoords.second !=
                        event.y
                    ) {
                        touchDownY = -1
                        return true
                    }
                }

                if (touchDownY != -1) {
                    val distance =
                        event.y.toInt() -
                            touchDownY

                    shouldIntercept =
                        distance > 0 &&
                            binding.allAppsGrid
                                .computeVerticalScrollOffset() ==
                            0

                    if (shouldIntercept) {
                        if (
                            binding.searchBar
                                .hasFocus()
                        ) {
                            activity?.hideKeyboard()
                        }

                        activity?.startHandlingTouches(
                            touchDownY
                        )

                        touchDownY = -1
                    }
                }
            }
        }

        lastTouchCoords =
            Pair(event.x, event.y)

        return shouldIntercept
    }

    fun gotLaunchers(
        appLaunchers: List<AppLauncher>
    ) {
        launchers =
            appLaunchers.sortedWith(
                compareBy(
                    {
                        it.title
                            .normalizeString()
                            .lowercase()
                    },
                    {
                        it.packageName
                    }
                )
            )

        val database =
            AppsDatabase.getInstance(context)

        val dao =
            database.DrawerFoldersDao()

        drawerFolders =
            dao.getFolders()

        drawerFolderApps =
            dao.getAllApps()

        refreshDrawerItems()
    }

    private fun refreshDrawerItems() {
        val launcherByPackage =
            launchers.associateBy {
                it.packageName
            }

        val folderPackages =
            drawerFolderApps
                .map {
                    it.packageName
                }
                .toSet()

        val apps =
            launchers
                .filterNot {
                    folderPackages.contains(
                        it.packageName
                    )
                }
                .map {
                    DrawerItem.App(it)
                }

        val folders =
            drawerFolders.map { folder ->

                val appsInFolder =
                    drawerFolderApps
                        .filter {
                            it.folderId ==
                                folder.id
                        }
                        .mapNotNull {
                            launcherByPackage[
                                it.packageName
                            ]
                        }
                        .sortedWith(
                            compareBy(
                                {
                                    it.title
                                        .normalizeString()
                                        .lowercase()
                                },
                                {
                                    it.packageName
                                }
                            )
                        )

                DrawerItem.Folder(
                    folder = folder,
                    apps = appsInFolder
                )
            }

        val items =
            (apps + folders)
                .sortedWith(
                    compareBy(
                        {
                            when (it) {
                                is DrawerItem.App ->
                                    it.launcher.title
                                        .normalizeString()
                                        .lowercase()

                                is DrawerItem.Folder ->
                                    it.folder.name
                                        .normalizeString()
                                        .lowercase()
                            }
                        },
                        {
                            when (it) {
                                is DrawerItem.App ->
                                    it.launcher.packageName

                                is DrawerItem.Folder ->
                                    it.folder.id
                                        ?.toString()
                                        ?: ""
                            }
                        }
                    )
                )

        setupAdapter(items)
    }

    private fun getAdapter() =
        binding.allAppsGrid.adapter
            as? LaunchersAdapter

    private fun setupAdapter(
        items: List<DrawerItem>
    ) {
        activity?.runOnUiThread {
            val layoutManager =
                binding.allAppsGrid.layoutManager
                    as MyGridLayoutManager

            layoutManager.spanCount =
                context.config.drawerColumnCount

            if (getAdapter() == null) {
                LaunchersAdapter(
                    activity!!,
                    this
                ) { item ->

                    when (item) {
                        is AppLauncher -> {
                            activity?.launchApp(
                                item.packageName,
                                item.activityName
                            )

                            if (
                                activity?.config
                                    ?.closeAppDrawer == true
                            ) {
                                activity?.closeAppDrawer(
                                    delayed = true
                                )
                            }

                            ignoreTouches = false
                            touchDownY = -1
                        }

                        is DrawerFolder -> {
                            openDrawerFolder(item)
                        }
                    }
                }.apply {
                    binding.allAppsGrid
                        .itemAnimator = null

                    binding.allAppsGrid
                        .adapter = this
                }
            }

            submitList(items)
        }
    }

    fun onIconHidden(
        item: HomeScreenGridItem
    ) {
        val itemToRemove =
            launchers.firstOrNull {
                it.getLauncherIdentifier() ==
                    item.getItemIdentifier()
            }

        if (itemToRemove != null) {
            val position =
                launchers.indexOfFirst {
                    it.getLauncherIdentifier() ==
                        item.getItemIdentifier()
                }

            launchers =
                launchers.toMutableList().apply {
                    removeAt(position)
                }

            refreshDrawerItems()
        }
    }

    private fun openDrawerFolder(
        folder: DrawerFolder
    ) {
        val currentActivity =
            activity ?: return

        val folderId =
            folder.id ?: return

        val folderPackages =
            drawerFolderApps
                .filter {
                    it.folderId ==
                        folderId
                }
                .map {
                    it.packageName
                }
                .toSet()

        val apps =
            launchers
                .filter {
                    folderPackages.contains(
                        it.packageName
                    )
                }
                .sortedWith(
                    compareBy(
                        {
                            it.title
                                .normalizeString()
                                .lowercase()
                        },
                        {
                            it.packageName
                        }
                    )
                )

        val recyclerView =
            RecyclerView(
                currentActivity
            ).apply {

                layoutManager =
                    GridLayoutManager(
                        currentActivity,
                        context.config.drawerColumnCount
                    )

                setHasFixedSize(true)

                itemAnimator = null

                adapter =
                    DrawerFolderAppsAdapter(
                        currentActivity,
                        this@AllAppsFragment,
                        apps
                    ) { launcher ->

                        currentActivity.launchApp(
                            launcher.packageName,
                            launcher.activityName
                        )

                        if (
                            currentActivity.config
                                .closeAppDrawer
                        ) {
                            currentActivity.closeAppDrawer(
                                delayed = true
                            )
                        }
                    }
            }

        val padding =
            (
                16 *
                    resources.displayMetrics.density
                ).toInt()

        val container =
            android.widget.LinearLayout(
                currentActivity
            ).apply {

                orientation =
                    android.widget.LinearLayout.VERTICAL

                setPadding(
                    padding,
                    0,
                    padding,
                    0
                )

                addView(
                    recyclerView,
                    android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        drawerFolderDialog =
            AlertDialog.Builder(
                currentActivity
            )
                .setTitle(folder.name)
                .setView(container)
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .create()

        drawerFolderDialog?.show()
    }

    fun setupViews() {
        if (activity == null) {
            return
        }

        binding.allAppsFastscroller
            .updateColors(
                context.getProperPrimaryColor()
            )

        binding.allAppsGrid
            .addOnScrollListener(
                object : OnScrollListener() {

                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int
                    ) {
                        if (
                            binding.searchBar.hasFocus() &&
                            dy > 0 &&
                            binding.allAppsGrid
                                .computeVerticalScrollOffset() >
                            0
                        ) {
                            activity?.hideKeyboard()
                        }
                    }
                }
            )

        setupDrawerBackground()

        getAdapter()?.updateTextColor(
            context.getProperTextColor()
        )

        binding.searchBar.beVisibleIf(
            context.config.showSearchBar
        )

        binding.searchBar.requireToolbar()
            .beGone()

        binding.searchBar.updateColors()

        binding.searchBar.setupMenu()

        binding.searchBar.onSearchTextChangedListener = {
            refreshDrawerItems()
        }

        binding.searchBar.binding.topToolbarSearch
            .setOnEditorActionListener {
                    _,
                    actionId,
                    _ ->

                if (
                    binding.searchBar
                        .getCurrentQuery()
                        .isEmpty()
                ) {
                    return@setOnEditorActionListener false
                }

                when (actionId) {
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_GO ->
                        getAdapter()
                            ?.launchFirstApp() == true

                    else ->
                        false
                }
            }
    }

    private fun showNoResultsPlaceholderIfNeeded() {
        val itemCount =
            getAdapter()?.itemCount

        binding.noResultsPlaceholder
            .beVisibleIf(
                itemCount != null &&
                    itemCount == 0
            )
    }

    override fun onAppLauncherLongPressed(
        x: Float,
        y: Float,
        appLauncher: AppLauncher
    ) {
        val gridItem =
            HomeScreenGridItem(
                id = null,
                left = -1,
                top = -1,
                right = -1,
                bottom = -1,
                page = 0,
                packageName =
                    appLauncher.packageName,
                activityName =
                    appLauncher.activityName,
                title =
                    appLauncher.title,
                type =
                    ITEM_TYPE_ICON,
                className = "",
                widgetId = -1,
                shortcutId = "",
                icon = null,
                docked = false,
                parentId = null,
                drawable =
                    appLauncher.drawable
            )

        activity?.showHomeIconMenu(
            x,
            y,
            gridItem,
            true
        )

        ignoreTouches = true

        binding.searchBar.closeSearch()
    }

    fun onBackPressed(): Boolean {
        if (
            binding.searchBar.isSearchOpen
        ) {
            binding.searchBar.closeSearch()
            return true
        }

        return false
    }

    private fun submitList(
        items: List<DrawerItem>
    ) {
        val searchQuery =
            binding.searchBar
                .getCurrentQuery()

        val filtered =
            if (searchQuery.isNotEmpty()) {
                items.filter {
                    when (it) {
                        is DrawerItem.App ->
                            it.launcher.title
                                .normalizeString()
                                .contains(
                                    searchQuery
                                        .normalizeString(),
                                    ignoreCase = true
                                )

                        is DrawerItem.Folder ->
                            it.folder.name
                                .normalizeString()
                                .contains(
                                    searchQuery
                                        .normalizeString(),
                                    ignoreCase = true
                                )
                    }
                }
            } else {
                items
            }

        getAdapter()?.submitList(
            filtered.toMutableList()
        ) {
            showNoResultsPlaceholderIfNeeded()
        }
    }
}

private class DrawerFolderAppsAdapter(
    private val activity: MainActivity,
    private val fragment: AllAppsFragment,
    private val apps: List<AppLauncher>,
    private val onAppClick: (AppLauncher) -> Unit
) : RecyclerView.Adapter<
    DrawerFolderAppsAdapter.ViewHolder
    >() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding =
            ItemLauncherLabelBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(
            apps[position]
        )
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    inner class ViewHolder(
        private val binding:
        ItemLauncherLabelBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    ) {

        fun bind(
            launcher: AppLauncher
        ) {
            binding.launcherLabel.text =
                launcher.title

            binding.launcherLabel
                .setTextColor(
                    activity.getProperTextColor()
                )

            binding.launcherLabel
                .beVisibleIf(
                    activity.config
                        .showDrawerAppLabels
                )

            binding.launcherIcon
                .setImageDrawable(
                    launcher.drawable
                )

            binding.root.setOnClickListener {
                onAppClick(launcher)
            }

            binding.root.setOnLongClickListener {
                fragment.showDrawerFolderAppMenu(
                    launcher,
                    binding.root
                )

                true
            }
        }
    }
}
