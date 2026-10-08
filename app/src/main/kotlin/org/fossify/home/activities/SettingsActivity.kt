package org.fossify.home.activities

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.home.BuildConfig
import org.fossify.home.R
import org.fossify.home.databinding.ActivitySettingsBinding
import org.fossify.home.databases.AppsDatabase
import org.fossify.home.extensions.config
import org.fossify.home.helpers.MAX_COLUMN_COUNT
import org.fossify.home.helpers.MAX_ROW_COUNT
import org.fossify.home.helpers.MIN_COLUMN_COUNT
import org.fossify.home.helpers.MIN_ROW_COUNT
import org.fossify.home.models.DrawerFolder
import org.fossify.home.models.DrawerFolderApp
import org.fossify.home.receivers.LockDeviceAdminReceiver
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private lateinit var drawerFoldersSettingsView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(
            padBottomSystem = listOf(
                binding.settingsNestedScrollview
            )
        )

        setupMaterialScrollListener(
            binding.settingsNestedScrollview,
            binding.settingsAppbar
        )

        setupOptionsMenu()
        setupDrawerFoldersSettings()
    }

    override fun onResume() {
        super.onResume()

        setupTopAppBar(
            binding.settingsAppbar,
            NavigationIcon.Arrow
        )

        refreshMenuItems()

        setupCustomizeColors()
        setupUseEnglish()
        setupDoubleTapToLock()
        setupCloseAppDrawerOnOtherAppOpen()
        setupOpenKeyboardOnAppDrawer()
        setupDrawerColumnCount()
        setupDrawerSearchBar()
        setupShowDrawerAppLabels()
        setupHomeRowCount()
        setupHomeColumnCount()
        setupShowHomeAppLabels()
        setupLanguage()
        setupManageHiddenIcons()
        updateTextColors(binding.settingsHolder)

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsDrawerSettingsLabel,
            binding.settingsHomeScreenLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }

        drawerFoldersSettingsView.setTextColor(
            getProperTextColor()
        )
    }

    private fun setupDrawerFoldersSettings() {
        drawerFoldersSettingsView = TextView(this).apply {
            text = "ドロワーフォルダ管理"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(getProperTextColor())
            setPadding(
                dpToPx(16),
                dpToPx(16),
                dpToPx(16),
                dpToPx(16)
            )

            setOnClickListener {
                showDrawerFoldersDialog()
            }
        }

        binding.settingsHolder.addView(
            drawerFoldersSettingsView,
            0,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showDrawerFoldersDialog() {
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                AppsDatabase
                    .getInstance(this@SettingsActivity)
                    .DrawerFoldersDao()
                    .getFolders()
            }

            val items = folders.map {
                it.name
            }.toTypedArray()

            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("ドロワーフォルダ")
                .setItems(items) { _, which ->
                    editDrawerFolder(folders[which])
                }
                .setPositiveButton("＋ フォルダ作成") { _, _ ->
                    createDrawerFolder()
                }
                .setNegativeButton("閉じる", null)
                .show()
        }
    }

    private fun createDrawerFolder() {
        val editText = android.widget.EditText(this).apply {
            hint = "フォルダ名"
            setSingleLine(true)
        }

        val padding = dpToPx(24)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                padding,
                0,
                padding,
                0
            )

            addView(
                editText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("フォルダを作成")
            .setView(container)
            .setPositiveButton("作成") { _, _ ->
                val name = editText.text.toString().trim()

                if (name.isEmpty()) {
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppsDatabase
                        .getInstance(this@SettingsActivity)
                        .DrawerFoldersDao()

                    val order = dao.getFolders().size

                    dao.insertFolder(
                        DrawerFolder(
                            id = null,
                            name = name,
                            order = order
                        )
                    )
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun editDrawerFolder(folder: DrawerFolder) {
        val folderId = folder.id ?: return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val database = AppsDatabase.getInstance(
                    this@SettingsActivity
                )

                val dao = database.DrawerFoldersDao()

                val apps = database
                    .AppLaunchersDao()
                    .getAppLaunchers()
                    .sortedWith(
                        compareBy(
                            { it.title.lowercase() },
                            { it.packageName }
                        )
                    )

                val currentPackages = dao
                    .getApps(folderId)
                    .map {
                        it.packageName
                    }
                    .toSet()

                Pair(apps, currentPackages)
            }

            val apps = result.first
            val currentPackages = result.second

            val checked = BooleanArray(apps.size) {
                currentPackages.contains(
                    apps[it].packageName
                )
            }

            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(folder.name)
                .setMultiChoiceItems(
                    apps.map {
                        it.title
                    }.toTypedArray(),
                    checked
                ) { _, which, isChecked ->

                    val app = apps[which]

                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = AppsDatabase
                            .getInstance(
                                this@SettingsActivity
                            )
                            .DrawerFoldersDao()

                        if (isChecked) {
                            dao.removeAppFromAllFolders(
                                app.packageName
                            )

                            dao.insertApp(
                                DrawerFolderApp(
                                    folderId = folderId,
                                    packageName = app.packageName
                                )
                            )
                        } else {
                            dao.deleteApp(
                                DrawerFolderApp(
                                    folderId = folderId,
                                    packageName = app.packageName
                                )
                            )
                        }
                    }
                }
                .setPositiveButton("名前変更") { _, _ ->
                    renameDrawerFolder(folder)
                }
                .setNeutralButton("削除") { _, _ ->
                    deleteDrawerFolder(folder)
                }
                .setNegativeButton("閉じる", null)
                .show()
        }
    }

    private fun renameDrawerFolder(folder: DrawerFolder) {
        val folderId = folder.id ?: return

        val editText = android.widget.EditText(this).apply {
            setText(folder.name)
            setSingleLine(true)
            selectAll()
        }

        val padding = dpToPx(24)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                padding,
                0,
                padding,
                0
            )

            addView(
                editText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle("フォルダ名を変更")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = editText.text.toString().trim()

                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppsDatabase
                            .getInstance(
                                this@SettingsActivity
                            )
                            .DrawerFoldersDao()
                            .renameFolder(
                                folderId,
                                name
                            )
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun deleteDrawerFolder(folder: DrawerFolder) {
        val folderId = folder.id ?: return

        AlertDialog.Builder(this)
            .setTitle("フォルダを削除")
            .setMessage(
                "「${folder.name}」を削除しますか？\n\n" +
                    "フォルダ内のアプリはアンインストールされず、" +
                    "通常のドロワーに戻ります。"
            )
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppsDatabase
                        .getInstance(
                            this@SettingsActivity
                        )
                        .DrawerFoldersDao()

                    dao.deleteAppsFromFolder(folderId)
                    dao.deleteFolder(folderId)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.about -> launchAbout()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                else -> return@setOnMenuItemClickListener false
            }

            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible =
                resources.getBoolean(
                    org.fossify.commons.R.bool.is_google_play_build
                )
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf(
            beVisible =
                (config.wasUseEnglishToggled ||
                    Locale.getDefault().language != "en") &&
                    !isTiramisuPlus()
        )

        binding.settingsUseEnglish.isChecked =
            config.useEnglish

        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish =
                binding.settingsUseEnglish.isChecked

            exitProcess(0)
        }
    }

    private fun setupDoubleTapToLock() {
        val devicePolicyManager =
            getSystemService(DEVICE_POLICY_SERVICE)
                as DevicePolicyManager

        binding.settingsDoubleTapToLock.isChecked =
            devicePolicyManager.isAdminActive(
                ComponentName(
                    this,
                    LockDeviceAdminReceiver::class.java
                )
            )

        binding.settingsDoubleTapToLockHolder.setOnClickListener {
            val isLockDeviceAdminActive =
                devicePolicyManager.isAdminActive(
                    ComponentName(
                        this,
                        LockDeviceAdminReceiver::class.java
                    )
                )

            if (isLockDeviceAdminActive) {
                devicePolicyManager.removeActiveAdmin(
                    ComponentName(
                        this,
                        LockDeviceAdminReceiver::class.java
                    )
                )
            } else {
                val intent = Intent(
                    DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                )

                intent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(
                        this,
                        LockDeviceAdminReceiver::class.java
                    )
                )

                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(
                        R.string.lock_device_admin_hint
                    )
                )

                startActivity(intent)
            }
        }
    }

    private fun setupOpenKeyboardOnAppDrawer() {
        binding.settingsOpenKeyboardOnAppDrawerHolder
            .beVisibleIf(config.showSearchBar)

        binding.settingsOpenKeyboardOnAppDrawer.isChecked =
            config.autoShowKeyboardInAppDrawer

        binding.settingsOpenKeyboardOnAppDrawerHolder
            .setOnClickListener {
                binding.settingsOpenKeyboardOnAppDrawer.toggle()

                config.autoShowKeyboardInAppDrawer =
                    binding.settingsOpenKeyboardOnAppDrawer.isChecked
            }
    }

    private fun setupCloseAppDrawerOnOtherAppOpen() {
        binding.settingsCloseAppDrawerOnOtherApp.isChecked =
            config.closeAppDrawer

        binding.settingsCloseAppDrawerOnOtherAppHolder
            .setOnClickListener {
                binding.settingsCloseAppDrawerOnOtherApp.toggle()

                config.closeAppDrawer =
                    binding.settingsCloseAppDrawerOnOtherApp.isChecked
            }
    }

    private fun setupDrawerColumnCount() {
        val currentColumnCount =
            config.drawerColumnCount

        binding.settingsDrawerColumnCount.text =
            currentColumnCount.toString()

        binding.settingsDrawerColumnCountHolder
            .setOnClickListener {
                val items = ArrayList<RadioItem>()

                for (i in 1..MAX_COLUMN_COUNT) {
                    items.add(
                        RadioItem(
                            id = i,
                            title = resources.getQuantityString(
                                org.fossify.commons.R.plurals.column_counts,
                                i,
                                i
                            )
                        )
                    )
                }

                RadioGroupDialog(
                    this,
                    items,
                    currentColumnCount
                ) {
                    val newColumnCount = it as Int

                    if (currentColumnCount != newColumnCount) {
                        config.drawerColumnCount =
                            newColumnCount

                        setupDrawerColumnCount()
                    }
                }
            }
    }

    private fun setupDrawerSearchBar() {
        val showSearchBar =
            config.showSearchBar

        binding.settingsShowSearchBar.isChecked =
            showSearchBar

        binding.settingsDrawerSearchHolder
            .setOnClickListener {
                binding.settingsShowSearchBar.toggle()

                config.showSearchBar =
                    binding.settingsShowSearchBar.isChecked

                binding.settingsOpenKeyboardOnAppDrawerHolder
                    .beVisibleIf(config.showSearchBar)
            }
    }

    private fun setupShowDrawerAppLabels() {
        binding.settingsShowDrawerAppLabels.isChecked =
            config.showDrawerAppLabels

        binding.settingsShowDrawerAppLabelsHolder
            .setOnClickListener {
                binding.settingsShowDrawerAppLabels.toggle()

                config.showDrawerAppLabels =
                    binding.settingsShowDrawerAppLabels.isChecked
            }
    }

    private fun setupHomeRowCount() {
        val currentRowCount =
            config.homeRowCount

        binding.settingsHomeScreenRowCount.text =
            currentRowCount.toString()

        binding.settingsHomeScreenRowCountHolder
            .setOnClickListener {
                val items = ArrayList<RadioItem>()

                for (i in MIN_ROW_COUNT..MAX_ROW_COUNT) {
                    items.add(
                        RadioItem(
                            id = i,
                            title = resources.getQuantityString(
                                org.fossify.commons.R.plurals.row_counts,
                                i,
                                i
                            )
                        )
                    )
                }

                RadioGroupDialog(
                    this,
                    items,
                    currentRowCount
                ) {
                    val newRowCount = it as Int

                    if (currentRowCount != newRowCount) {
                        config.homeRowCount =
                            newRowCount

                        setupHomeRowCount()
                    }
                }
            }
    }

    private fun setupHomeColumnCount() {
        val currentColumnCount =
            config.homeColumnCount

        binding.settingsHomeScreenColumnCount.text =
            currentColumnCount.toString()

        binding.settingsHomeScreenColumnCountHolder
            .setOnClickListener {
                val items = ArrayList<RadioItem>()

                for (i in MIN_COLUMN_COUNT..MAX_COLUMN_COUNT) {
                    items.add(
                        RadioItem(
                            id = i,
                            title = resources.getQuantityString(
                                org.fossify.commons.R.plurals.column_counts,
                                i,
                                i
                            )
                        )
                    )
                }

                RadioGroupDialog(
                    this,
                    items,
                    currentColumnCount
                ) {
                    val newColumnCount = it as Int

                    if (currentColumnCount != newColumnCount) {
                        config.homeColumnCount =
                            newColumnCount

                        setupHomeColumnCount()
                    }
                }
            }
    }

    private fun setupShowHomeAppLabels() {
        binding.settingsShowHomeAppLabels.isChecked =
            config.showHomeAppLabels

        binding.settingsShowHomeAppLabelsHolder
            .setOnClickListener {
                binding.settingsShowHomeAppLabels.toggle()

                config.showHomeAppLabels =
                    binding.settingsShowHomeAppLabels.isChecked
            }
    }

    @SuppressLint("NewApi")
    private fun setupLanguage() {
        binding.settingsLanguage.text =
            Locale.getDefault().displayLanguage

        binding.settingsLanguageHolder.beVisibleIf(
            isTiramisuPlus()
        )

        binding.settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupManageHiddenIcons() {
        binding.settingsManageHiddenIconsHolder
            .setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        HiddenIconsActivity::class.java
                    )
                )
            }
    }

    private fun launchAbout() {
        val licenses = 0L
        val faqItems = ArrayList<FAQItem>()

        if (resources.getBoolean(
                org.fossify.commons.R.bool.is_google_play_build
            )
        ) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )

            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }
}
