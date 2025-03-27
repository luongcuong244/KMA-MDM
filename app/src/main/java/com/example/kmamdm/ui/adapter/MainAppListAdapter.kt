package com.example.kmamdm.ui.adapter

import android.app.Activity

class MainAppListAdapter(
    parentActivity: Activity,
    appChooseListener: OnAppChooseListener,
    switchAdapterListener: SwitchAdapterListener
) : BaseAppListAdapter(parentActivity, appChooseListener, switchAdapterListener) {
    init {
        items = AppShortcutManager.instance!!.getInstalledApps(parentActivity, false)
        initShortcuts()
    }
}