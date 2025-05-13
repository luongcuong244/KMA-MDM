package com.example.kmamdm.worker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kmamdm.extension.log
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.socket.json.PushMessage
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.InstallUtils
import com.example.kmamdm.utils.SystemUtils
import com.example.kmamdm.utils.Utils
import org.json.JSONObject
import java.io.File

object PushNotificationProcessor {
    fun process(message: PushMessage, context: Context) {
        log("Got Push Message, type " + message.messageType)
        if (message.messageType == PushMessage.TYPE_CONFIG_UPDATED) {
            // Update local configuration
            ConfigUpdater.notifyConfigUpdate(context)
            // The configUpdated should be broadcasted after the configuration update is completed
            return
        } else if (message.messageType == PushMessage.TYPE_RUN_APP) {
            // Run application
            runApplication(context, message.payload?.let { JSONObject(it) })
            // Do not broadcast this message to other apps
            return
        } else if (message.messageType == PushMessage.TYPE_UNINSTALL_APP) {
            // Uninstall application
            AsyncTask.execute {
                uninstallApplication(
                    context,
                    message.payload?.let { JSONObject(it) }
                )
            }
            return
        } else if (message.messageType == PushMessage.TYPE_DELETE_FILE) {
            // Delete file
            AsyncTask.execute {
                deleteFile(
                    context,
                    message.payload?.let { JSONObject(it) }
                )
            }
            return
        } else if (message.messageType == PushMessage.TYPE_DELETE_DIR) {
            // Delete directory recursively
            AsyncTask.execute {
                deleteDir(
                    context,
                    message.payload?.let { JSONObject(it) }
                )
            }
            return
        } else if (message.messageType == PushMessage.TYPE_PURGE_DIR) {
            // Purge directory (delete all files recursively)
            AsyncTask.execute {
                purgeDir(
                    context,
                    message.payload?.let { JSONObject(it) }
                )
            }
            return
        } else if (message.messageType == PushMessage.TYPE_PERMISSIVE_MODE) {
            // Turn on permissive mode
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(Const.ACTION_PERMISSIVE_MODE))
            return
        } else if (message.messageType == PushMessage.TYPE_RUN_COMMAND) {
            // Run a command-line script
            AsyncTask.execute {
                runCommand(
                    context,
                    message.payload?.let { JSONObject(it) }
                )
            }
            return
        } else if (message.messageType == PushMessage.TYPE_REBOOT) {
            // Reboot a device
            AsyncTask.execute { reboot(context) }
            return
        } else if (message.messageType == PushMessage.TYPE_EXIT_KIOSK) {
            // Temporarily exit kiosk mode
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(Const.ACTION_EXIT_KIOSK))
            return
        } else if (message.messageType == PushMessage.TYPE_CLEAR_DOWNLOADS) {
            // Clear download history
            AsyncTask.execute { clearDownloads(context) }
            return
        } else if (message.messageType == PushMessage.TYPE_SETTINGS) {
            // Clear download history
            AsyncTask.execute {
                openSettings(
                    context,
                    message.payload?.let { JSONObject(it) }
                )
            }
            return
        }
        log("Unknown Push Message type: " + message.messageType)
    }

    private fun runApplication(context: Context, payload: JSONObject?) {
        if (payload == null) {
            return
        }
        try {
            val pkg = payload.getString("pkg")
            val action = payload.optString("action", null)
            val extras = payload.optJSONObject("extra")
            val data = payload.optString("data", null)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.setAction(action)
                try {
                    launchIntent.setData(Uri.parse(data))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (extras != null) {
                    val keys = extras.keys()
                    var key: String?
                    while (keys.hasNext()) {
                        key = keys.next() as String
                        val value = extras[key]
                        if (value is String) {
                            launchIntent.putExtra(key, value)
                        } else if (value is Int) {
                            launchIntent.putExtra(key, value)
                        } else if (value is Float) {
                            launchIntent.putExtra(key, value)
                        } else if (value is Boolean) {
                            launchIntent.putExtra(key, value)
                        }
                    }
                }

                // These magic flags are found in the source code of the default Android launcher
                // These flags preserve the app activity stack (otherwise a launch activity appears at the top which is not correct)
                launchIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uninstallApplication(context: Context, payload: JSONObject?) {
        if (payload == null) {
            log("Uninstall request failed: no package specified")
            return
        }
        if (!Utils.isDeviceOwner(context)) {
            // Require device owner for non-interactive uninstallation
            log("Uninstall request failed: no device owner")
            return
        }

        try {
            val pkg = payload.getString("pkg")
            InstallUtils.silentUninstallApplication(context, pkg)
            log("Uninstalled application: $pkg")
        } catch (e: Exception) {
            log("Uninstall request failed: " + e.message)
            e.printStackTrace()
        }
    }

    private fun deleteFile(context: Context, payload: JSONObject?) {
        if (payload == null) {
            log("File delete failed: no path specified")
            return
        }

        try {
            val path = payload.getString("path")
            val file = File(Environment.getExternalStorageDirectory(), path)
            file.delete()
            log("Deleted file: $path")
        } catch (e: Exception) {
            log("File delete failed: " + e.message)
            e.printStackTrace()
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            val childFiles = fileOrDirectory.listFiles()
            for (child in childFiles) {
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }

    private fun deleteDir(context: Context, payload: JSONObject?) {
        if (payload == null) {
            log("Directory delete failed: no path specified")
            return
        }

        try {
            val path = payload.getString("path")
            val file = File(Environment.getExternalStorageDirectory(), path)
            deleteRecursive(file)
            log("Deleted directory: $path")
        } catch (e: Exception) {
            log("Directory delete failed: " + e.message)
            e.printStackTrace()
        }
    }

    private fun purgeDir(context: Context, payload: JSONObject?) {
        if (payload == null) {
            log("Directory purge failed: no path specified")
            return
        }

        try {
            val path = payload.getString("path")
            val file = File(Environment.getExternalStorageDirectory(), path)
            if (!file.isDirectory) {
                log("Directory purge failed: not a directory: $path")
                return
            }
            val recursive = payload.optString("recursive")
            val childFiles = file.listFiles()
            for (child in childFiles) {
                if (recursive != "1") {
                    if (!child.isDirectory) {
                        child.delete()
                    }
                } else {
                    deleteRecursive(child)
                }
            }
            log("Purged directory: $path")
        } catch (e: Exception) {
            log("Directory purge failed: " + e.message)
            e.printStackTrace()
        }
    }

    private fun runCommand(context: Context, payload: JSONObject?) {
        if (payload == null) {
            log("Command failed: no command specified")
            return
        }

        try {
            val command = payload.getString("command")
            Log.d(Const.LOG_TAG, "Executing a command: $command")
            var result: String = SystemUtils.executeShellCommand(command, true)
            var msg = "Executed a command: $command"
            if (result != "") {
                if (result.length > 200) {
                    result = result.substring(0, 200) + "..."
                }
                msg += " Result: $result"
            }
            log(msg)
        } catch (e: Exception) {
            log("Command failed: " + e.message)
            e.printStackTrace()
        }
    }

    private fun reboot(context: Context) {
        log("Rebooting by a Push message")
        if (Utils.checkAdminMode(context)) {
            if (!Utils.reboot(context)) {
                log("Reboot failed")
            }
        } else {
            log("Reboot failed: no permissions")
        }
    }

    private fun clearDownloads(context: Context) {
        log("Clear download history by a Push message")
        /*val dbHelper: DatabaseHelper = DatabaseHelper.instance(context)
        val db: SQLiteDatabase = dbHelper.getWritableDatabase()
        val downloads: List<Download> = DownloadTable.selectAll(db)
        for (d in downloads) {
            val file: File = File(d.getPath())
            try {
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        DownloadTable.deleteAll(db)*/
    }

    private fun openSettings(context: Context, payload: JSONObject?) {
        if (payload == null) {
            log("Open settings failed: no action specified")
            return
        }

        try {
            val action = payload.getString("action")
            Log.d(Const.LOG_TAG, "Opening settings sheet: $action")
            val i = Intent(action)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            log("Open settings failed: " + e.message)
            e.printStackTrace()
        }
    }
}