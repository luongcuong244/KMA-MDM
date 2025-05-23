package com.example.kmamdm.ui.adapter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ItemAppBinding
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.utils.AppInfo
import com.example.kmamdm.utils.Const

open class BaseAppListAdapter(
    private val parentActivity: Activity,
    private val appChooseListener: OnAppChooseListener,
    private val switchAdapterListener: SwitchAdapterListener
) :
    RecyclerView.Adapter<BaseAppListAdapter.ViewHolder>() {
    private var layoutInflater: LayoutInflater = LayoutInflater.from(parentActivity)
    var items: List<AppInfo>? = null
    private var shortcuts: MutableMap<Int, AppInfo>? = null // Keycode -> Application, filled in getInstalledApps()
    private var settingsHelper: SettingsHelper = SettingsHelper.getInstance(parentActivity)
    private var spanCount: Int = 0
    private var selectedItem: Int = -1
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var selectedItemBorder: GradientDrawable
    private var focused: Boolean = true

    protected fun initShortcuts() {
        shortcuts = HashMap()
        for (item in items!!) {
            if (item.keyCode != null) {
                shortcuts!![item.keyCode!!] = item
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(layoutInflater.inflate(R.layout.item_app, parent, false))
        viewHolder.binding.rootLinearLayout.setOnClickListener(onClickListener)
        viewHolder.binding.rootLinearLayout.setOnLongClickListener(onLongClickListener)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo: AppInfo = items!![position]
        holder.binding.rootLinearLayout.tag = appInfo
        holder.binding.textView.text = appInfo.name

        if (settingsHelper.getConfig()?.textColor != null && !settingsHelper.getConfig()?.textColor?.trim().equals("")) {
            try {
                holder.binding.textView.setTextColor(
                    Color.parseColor(
                        settingsHelper.getConfig()!!.textColor
                    )
                )
            } catch (e: java.lang.Exception) {
                holder.binding.textView.setTextColor(Color.WHITE)
            }
        }

        try {
            val iconScale = settingsHelper.getConfig()?.iconSize ?: 100
            val iconSize =
                parentActivity.resources.getDimensionPixelOffset(R.dimen.app_icon_size) * iconScale / 100
            holder.binding.iconCardView.layoutParams.width = iconSize
            holder.binding.iconCardView.layoutParams.height = iconSize
            if (appInfo.iconUrl != null) {
                Glide.with(parentActivity)
                    .load(appInfo.iconUrl)
                    .listener(object : RequestListener<Drawable> {
                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            holder.binding.imageView.setImageDrawable(
                                appInfo.packageName?.let {
                                    parentActivity.packageManager.getApplicationIcon(
                                        it
                                    )
                                }
                            )
                            return false
                        }
                    })
                    .into(holder.binding.imageView)
            } else {
                holder.binding.imageView.setImageDrawable(
                    appInfo.packageName?.let {
                        parentActivity.packageManager.getApplicationIcon(
                            it
                        )
                    }
                )
            }

            holder.itemView.background = if (position == selectedItem) selectedItemBorder else null
        } catch (e: Exception) {
            // Here we handle PackageManager.NameNotFoundException as well as
            // DeadObjectException (when a device is being turned off)
            e.printStackTrace()
            holder.binding.imageView.setImageResource(R.drawable.ic_android_white_50dp)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var binding: ItemAppBinding = ItemAppBinding.bind(itemView)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        layoutManager = recyclerView.layoutManager
    }

    override fun getItemCount(): Int {
        return if (items == null) 0 else items!!.size
    }

    fun setSpanCount(spanCount: Int) {
        this.spanCount = spanCount
    }

    fun setFocused(focused: Boolean) {
        selectedItem = if (focused) 0 else -1
        notifyDataSetChanged()
        if (selectedItem == 0 && layoutManager != null) {
            layoutManager!!.scrollToPosition(selectedItem)
        }
    }

    interface OnAppChooseListener {
        fun onAppChoose(resolveInfo: AppInfo)
    }

    // Let the parent know that the user wants to switch the adapter
    // Send the direction; if the parent returns true, this means
    // it switched the adapter - unfocus self
    interface SwitchAdapterListener {
        fun switchAppListAdapter(adapter: BaseAppListAdapter?, direction: Int): Boolean
    }

    protected var onClickListener: View.OnClickListener =
        View.OnClickListener { v: View ->
            chooseApp(v.tag as AppInfo)
        }

    protected var onLongClickListener: OnLongClickListener = OnLongClickListener { v: View ->
        val appInfo: AppInfo = v.tag as AppInfo
        if (appInfo.longTap === 1) {
            // Open app settings on long click
            openAppSettings(appInfo)
            return@OnLongClickListener true
        }
        false
    }

    init {
        var isDarkBackground = true
        val config: ServerConfig? = settingsHelper.getConfig()
//        if (config != null && config.getBackgroundColor() != null) {
//            try {
//                isDarkBackground =
//                    !Utils.isLightColor(Color.parseColor(config.getBackgroundColor()))
//            } catch (e: Exception) {
//            }
//        }
        selectedItemBorder = GradientDrawable()
        selectedItemBorder.setColor(0) // transparent background
        selectedItemBorder.setStroke(
            2,
            if (isDarkBackground) -0x5f000001 else -0x60000000
        ) // white or black border with some transparency
    }

    fun chooseApp(appInfo: AppInfo) {
        val launchIntent = parentActivity.packageManager.getLaunchIntentForPackage(appInfo.packageName ?: "")
        if (launchIntent != null) {
            // These magic flags are found in the source code of the default Android launcher
            // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
            launchIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            parentActivity.startActivity(launchIntent)
        }
        appChooseListener.onAppChoose(appInfo)
    }

    fun onKey(keyCode: Int): Boolean {
        val shortcutAppInfo: AppInfo? = shortcuts!![keyCode]
        if (shortcutAppInfo != null) {
            chooseApp(shortcutAppInfo)
            return true
        }
        if (!focused) {
            return false
        }

        var switchAdapterDirection = -1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (tryMoveSelection(layoutManager, 1)) {
                    return true
                } else {
                    switchAdapterDirection = Const.DIRECTION_RIGHT
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> if (tryMoveSelection(layoutManager, -1)) {
                return true
            } else {
                switchAdapterDirection = Const.DIRECTION_LEFT
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> if (tryMoveSelection(layoutManager, spanCount)) {
                return true
            } else {
                switchAdapterDirection = Const.DIRECTION_DOWN
            }

            KeyEvent.KEYCODE_DPAD_UP -> if (tryMoveSelection(layoutManager, -spanCount)) {
                return true
            } else {
                switchAdapterDirection = Const.DIRECTION_UP
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                chooseSelectedItem()
                return true
            }
        }
        if (switchAdapterListener.switchAppListAdapter(
                this,
                switchAdapterDirection
            )
        ) {
            // Adapter switch accepted, unfocus
            setFocused(false)
        }

        return false
    }

    private fun tryMoveSelection(lm: RecyclerView.LayoutManager?, offset: Int): Boolean {
        var trySelectedItem = selectedItem + offset

        if (trySelectedItem < 0) {
            trySelectedItem = 0
        }
        if (trySelectedItem >= itemCount) {
            trySelectedItem = itemCount - 1
        }

        if (trySelectedItem != selectedItem) {
            selectedItem = trySelectedItem
            notifyDataSetChanged()
            lm?.scrollToPosition(trySelectedItem)
            return true
        }

        return false
    }

    private fun chooseSelectedItem() {
        if (items == null || selectedItem < 0 || selectedItem >= itemCount) {
            return
        }
        chooseApp(items!![selectedItem])
    }

    private fun openAppSettings(appInfo: AppInfo) {
        parentActivity.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + appInfo.packageName)
            )
        )
    }
}