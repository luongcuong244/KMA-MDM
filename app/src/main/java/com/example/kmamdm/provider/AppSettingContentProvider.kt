package com.example.kmamdm.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.util.Log
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.Const


class AppSettingContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor {
        val key = uri.lastPathSegment
        val cursor = MatrixCursor(arrayOf("attribute", "value", "comment"))

        val context = context ?: return cursor
        val applicationSettings = SettingsHelper.getInstance(context).getConfig()?.applicationSettings

        if (key == null || applicationSettings == null) {
            return cursor
        }

        // Lấy UID của app gọi
        val callingUid = Binder.getCallingUid()

        // Dùng UID để lấy tên gói
        val packages = getContext()!!.packageManager.getPackagesForUid(callingUid)
        val callingPackage = if (!packages.isNullOrEmpty()) packages[0] else "unknown"

        Log.d(Const.LOG_TAG, "Query from package: $callingPackage")

        for (appSetting in applicationSettings) {
            Log.d(Const.LOG_TAG, "AppSetting: ${appSetting.application.pkg} - ${appSetting.attribute} - ${appSetting.value} - ${appSetting.comment}")
            if (appSetting.application.pkg == callingPackage || callingPackage == "unknown") {
                Log.d(Const.LOG_TAG, "Adding to cursor: ${appSetting.attribute} - ${appSetting.value} - ${appSetting.comment}")
                cursor.addRow(arrayOf<Any?>(appSetting.attribute, appSetting.value, appSetting.comment))
            }
        }

        return cursor
    }

    override fun getType(p0: Uri): String? {
        return null
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        return null
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
        return -1
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        return -1
    }
}