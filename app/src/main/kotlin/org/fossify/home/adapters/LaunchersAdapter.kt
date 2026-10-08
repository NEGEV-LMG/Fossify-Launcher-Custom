package org.fossify.home.adapters

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.realScreenSize
import org.fossify.home.R
import org.fossify.home.activities.SimpleActivity
import org.fossify.home.databinding.ItemLauncherLabelBinding
import org.fossify.home.extensions.animateScale
import org.fossify.home.extensions.config
import org.fossify.home.interfaces.AllAppsListener
import org.fossify.home.models.AppLauncher
import org.fossify.home.models.DrawerItem
import kotlin.math.min

class LaunchersAdapter(
    val activity: SimpleActivity,
    val allAppsListener: AllAppsListener,
    val itemClick: (Any) -> Unit
) : ListAdapter<DrawerItem, LaunchersAdapter.ViewHolder>(
    DrawerItemDiffCallback()
), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textColor = activity.getProperTextColor()
    private var iconPadding = 0

    init {
        setHasStableIds(true)
        calculateIconWidth()
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is DrawerItem.App ->
                item.launcher
                    .getLauncherIdentifier()
                    .hashCode()
                    .toLong()

            is DrawerItem.Folder ->
                item.folder.id ?: position.toLong()
        }
    }

    fun launchFirstApp(): Boolean {
        val item = currentList.firstOrNull {
            it is DrawerItem.App
        } ?: return false

        itemClick(
            (item as DrawerItem.App).launcher
        )

        return true
    }

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

        return ViewHolder(binding.root)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bindView(
            getItem(position)
        )
    }

    override fun submitList(
        list: MutableList<DrawerItem>?
    ) {
        calculateIconWidth()
        super.submitList(list)
    }

    private fun calculateIconWidth() {
        val currentColumnCount =
            activity.config.drawerColumnCount

        val iconWidth =
            activity.realScreenSize.x /
                currentColumnCount

        iconPadding =
            (iconWidth * 0.1f).toInt()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTextColor(
        newTextColor: Int
    ) {
        if (newTextColor != textColor) {
            textColor = newTextColor
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        @SuppressLint("ClickableViewAccessibility")
        fun bindView(
            item: DrawerItem
        ): View {
            val binding =
                ItemLauncherLabelBinding.bind(
                    itemView
                )

            itemView.apply {
                binding.launcherLabel
                    .setTextColor(textColor)

                binding.launcherLabel.beVisibleIf(
                    activity.config
                        .showDrawerAppLabels
                )

                binding.launcherIcon.setPadding(
                    iconPadding,
                    iconPadding,
                    iconPadding,
                    0
                )

                when (item) {
                    is DrawerItem.App -> {
                        bindApp(
                            binding,
                            item.launcher
                        )

                        setOnClickListener {
                            itemClick(
                                item.launcher
                            )
                        }

                        setOnLongClickListener {
                            val location =
                                IntArray(2)

                            getLocationOnScreen(
                                location
                            )

                            allAppsListener
                                .onAppLauncherLongPressed(
                                    x = (
                                        location[0] +
                                            width / 2
                                        ).toFloat(),
                                    y = location[1]
                                        .toFloat(),
                                    appLauncher =
                                        item.launcher
                                )

                            true
                        }
                    }

                    is DrawerItem.Folder -> {
                        binding.launcherLabel.text =
                            item.folder.name

                        binding.launcherIcon
                            .setImageDrawable(
                                FolderPreviewDrawable(
                                    item.apps
                                )
                            )

                        binding.launcherIcon.tag =
                            true

                        setOnClickListener {
                            itemClick(
                                item.folder
                            )
                        }

                        setOnLongClickListener {
                            false
                        }
                    }
                }

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            binding.launcherIcon
                                .drawable
                                ?.alpha =
                                LAUNCHER_ALPHA_PRESSED

                            animateScale(
                                from =
                                    LAUNCHER_SCALE_NORMAL,
                                to =
                                    LAUNCHER_SCALE_PRESSED,
                                duration =
                                    LAUNCHER_SCALE_UP_DURATION
                            )
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            binding.launcherIcon
                                .drawable
                                ?.alpha =
                                LAUNCHER_ALPHA_NORMAL

                            animateScale(
                                from =
                                    LAUNCHER_SCALE_PRESSED,
                                to =
                                    LAUNCHER_SCALE_NORMAL,
                                duration =
                                    LAUNCHER_SCALE_DOWN_DURATION
                            )
                        }
                    }

                    false
                }
            }

            return itemView
        }

        private fun bindApp(
            binding: ItemLauncherLabelBinding,
            launcher: AppLauncher
        ) {
            binding.launcherLabel.text =
                launcher.title

            if (
                launcher.drawable != null &&
                binding.launcherIcon.tag == true
            ) {
                binding.launcherIcon
                    .setImageDrawable(
                        launcher.drawable
                    )
            } else {
                val placeholderDrawable =
                    activity.resources
                        .getColoredDrawableWithColor(
                            drawableId =
                                R.drawable
                                    .placeholder_drawable,
                            color =
                                launcher
                                    .thumbnailColor
                        )

                Glide.with(activity)
                    .load(launcher.drawable)
                    .placeholder(
                        placeholderDrawable
                    )
                    .diskCacheStrategy(
                        DiskCacheStrategy.RESOURCE
                    )
                    .into(
                        object :
                            DrawableImageViewTarget(
                                binding.launcherIcon
                            ) {

                            override fun
                                onResourceReady(
                                resource: Drawable,
                                transition:
                                Transition<
                                    in Drawable
                                    >?
                            ) {
                                super.onResourceReady(
                                    resource,
                                    transition
                                )

                                view.tag = true
                            }
                        }
                    )
            }
        }
    }

    override fun onChange(
        position: Int
    ): String {
        return when (
            val item =
                currentList.getOrNull(position)
        ) {
            is DrawerItem.App ->
                item.launcher.getBubbleText()

            is DrawerItem.Folder ->
                item.folder.name

            null ->
                ""
        }
    }

    companion object {
        private const val LAUNCHER_SCALE_NORMAL = 1f
        private const val LAUNCHER_SCALE_PRESSED = 1.15f
        private const val LAUNCHER_SCALE_UP_DURATION = 100L
        private const val LAUNCHER_SCALE_DOWN_DURATION = 50L
        private const val LAUNCHER_ALPHA_NORMAL = 255
        private const val LAUNCHER_ALPHA_PRESSED = 220
    }
}


/**
 * ドロワーフォルダーのプレビューアイコン。
 *
 * フォルダー内のアプリを最大4個表示する。
 *
 * 1個:
 *
 *       ●
 *
 * 2個:
 *
 *      ● ●
 *
 * 3個:
 *
 *       ●
 *      ● ●
 *
 * 4個以上:
 *
 *      ● ●
 *      ● ●
 */
private class FolderPreviewDrawable(
    private val apps: List<AppLauncher>
) : Drawable() {

    private val backgroundPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.argb(
                45,
                128,
                128,
                128
            )

            style = Paint.Style.FILL
        }

    private val borderPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.argb(
                80,
                128,
                128,
                128
            )

            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

    private val whiteBorderPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.argb(
                220,
                255,
                255,
                255
            )

            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

    override fun draw(
        canvas: Canvas
    ) {
        val bounds = bounds

        if (
            bounds.width() <= 0 ||
            bounds.height() <= 0
        ) {
            return
        }

        val centerX =
            bounds.centerX().toFloat()

        val centerY =
            bounds.centerY().toFloat()

        val radius =
            min(
                bounds.width(),
                bounds.height()
            ).toFloat() * 0.48f

        // 円形の背景
        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            backgroundPaint
        )

// 円形の薄い縁
        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            borderPaint
        )

// 視認性向上用の白枠
        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            whiteBorderPaint
        )

        val visibleApps =
            apps
                .take(4)
                .filter {
                    it.drawable != null
                }

        if (visibleApps.isEmpty()) {
            return
        }

        val iconSize =
            when (visibleApps.size) {
                1 ->
                    radius * 0.95f

                2 ->
                    radius * 0.70f

                else ->
                    radius * 0.52f
            }

        when (visibleApps.size) {
            1 -> {
                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[0],
                    centerX = centerX,
                    centerY = centerY,
                    size = iconSize,
                    folderRadius = radius
                )
            }

            2 -> {
                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[0],
                    centerX =
                        centerX -
                            radius * 0.32f,
                    centerY = centerY,
                    size = iconSize,
                    folderRadius = radius
                )

                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[1],
                    centerX =
                        centerX +
                            radius * 0.32f,
                    centerY = centerY,
                    size = iconSize,
                    folderRadius = radius
                )
            }

            3 -> {
                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[0],
                    centerX = centerX,
                    centerY =
                        centerY -
                            radius * 0.30f,
                    size = iconSize,
                    folderRadius = radius
                )

                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[1],
                    centerX =
                        centerX -
                            radius * 0.30f,
                    centerY =
                        centerY +
                            radius * 0.27f,
                    size = iconSize,
                    folderRadius = radius
                )

                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[2],
                    centerX =
                        centerX +
                            radius * 0.30f,
                    centerY =
                        centerY +
                            radius * 0.27f,
                    size = iconSize,
                    folderRadius = radius
                )
            }

            else -> {
                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[0],
                    centerX =
                        centerX -
                            radius * 0.29f,
                    centerY =
                        centerY -
                            radius * 0.29f,
                    size = iconSize,
                    folderRadius = radius
                )

                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[1],
                    centerX =
                        centerX +
                            radius * 0.29f,
                    centerY =
                        centerY -
                            radius * 0.29f,
                    size = iconSize,
                    folderRadius = radius
                )

                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[2],
                    centerX =
                        centerX -
                            radius * 0.29f,
                    centerY =
                        centerY +
                            radius * 0.29f,
                    size = iconSize,
                    folderRadius = radius
                )

                drawAppIcon(
                    canvas = canvas,
                    launcher = visibleApps[3],
                    centerX =
                        centerX +
                            radius * 0.29f,
                    centerY =
                        centerY +
                            radius * 0.29f,
                    size = iconSize,
                    folderRadius = radius
                )
            }
        }
    }

    private fun drawAppIcon(
        canvas: Canvas,
        launcher: AppLauncher,
        centerX: Float,
        centerY: Float,
        size: Float,
        folderRadius: Float
    ) {
        val drawable =
            launcher.drawable
                ?: return

        val halfSize =
            size / 2f

        val iconRect =
            RectF(
                centerX - halfSize,
                centerY - halfSize,
                centerX + halfSize,
                centerY + halfSize
            )

        val saveCount =
            canvas.save()

        // フォルダー円の内側だけ描画する
        val path =
            Path().apply {
                addCircle(
                    centerX,
                    centerY,
                    folderRadius * 0.88f,
                    Path.Direction.CW
                )
            }

        canvas.clipPath(path)

        drawable.setBounds(
            iconRect.left.toInt(),
            iconRect.top.toInt(),
            iconRect.right.toInt(),
            iconRect.bottom.toInt()
        )

        drawable.draw(canvas)

        canvas.restoreToCount(
            saveCount
        )
    }

    override fun setAlpha(
        alpha: Int
    ) {
        backgroundPaint.alpha =
            alpha * 45 / 255

        borderPaint.alpha =
            alpha * 80 / 255

        whiteBorderPaint.alpha =
            alpha * 220 / 255

        apps.forEach {
            it.drawable?.alpha =
                alpha
        }

        invalidateSelf()
    }

    override fun setColorFilter(
        colorFilter:
        android.graphics.ColorFilter?
    ) {
        backgroundPaint.colorFilter =
            colorFilter

        borderPaint.colorFilter =
            colorFilter

        apps.forEach {
            it.drawable?.colorFilter =
                colorFilter
        }

        invalidateSelf()
    }

    @Deprecated(
        "Deprecated in Android SDK"
    )
    override fun getOpacity(): Int {
        return android.graphics.PixelFormat
            .TRANSLUCENT
    }
}


private class DrawerItemDiffCallback :
    DiffUtil.ItemCallback<DrawerItem>() {

    override fun areItemsTheSame(
        oldItem: DrawerItem,
        newItem: DrawerItem
    ): Boolean {
        return when {
            oldItem is DrawerItem.App &&
                newItem is DrawerItem.App -> {
                oldItem.launcher
                    .getLauncherIdentifier() ==
                    newItem.launcher
                        .getLauncherIdentifier()
            }

            oldItem is DrawerItem.Folder &&
                newItem is DrawerItem.Folder -> {
                oldItem.folder.id ==
                    newItem.folder.id
            }

            else ->
                false
        }
    }

    override fun areContentsTheSame(
        oldItem: DrawerItem,
        newItem: DrawerItem
    ): Boolean {
        return when {
            oldItem is DrawerItem.App &&
                newItem is DrawerItem.App -> {
                oldItem.launcher.title ==
                    newItem.launcher.title &&
                    oldItem.launcher.order ==
                    newItem.launcher.order &&
                    oldItem.launcher.thumbnailColor ==
                    newItem.launcher.thumbnailColor &&
                    oldItem.launcher.drawable != null &&
                    newItem.launcher.drawable != null
            }

            oldItem is DrawerItem.Folder &&
                newItem is DrawerItem.Folder -> {
                oldItem.folder.name ==
                    newItem.folder.name &&
                    oldItem.folder.order ==
                    newItem.folder.order &&
                    oldItem.apps.map {
                        it.packageName
                    } ==
                    newItem.apps.map {
                        it.packageName
                    } &&
                    oldItem.apps.map {
                        it.drawable != null
                    } ==
                    newItem.apps.map {
                        it.drawable != null
                    }
            }

            else ->
                false
        }
    }
}
