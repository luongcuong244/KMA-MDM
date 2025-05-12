package com.example.kmamdm.helper

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.kmamdm.extension.isDisable
import com.example.kmamdm.extension.isEnable
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.InstallUtils
import com.example.kmamdm.utils.Utils

object Initializer {
    fun init(context: Context) {
        Utils.lockSafeBoot(context)
        Utils.initPasswordReset(context)

        Log.d(Const.LOG_TAG, "Starting Initializer")

        InstallUtils.clearTempFiles(context)

        // DetailedInfoWorker.schedule(context)
        PushNotificationWorker.schedule(context)
    }

    fun applyEarlyNonInteractivePolicies(context: Context, config: ServerConfig) {
        if (config.bluetooth != null) {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null) {
                    val enabled = bluetoothAdapter.isEnabled
                    if (config.bluetooth.isEnable() && !enabled) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        bluetoothAdapter.enable()
                    } else if (config.bluetooth.isDisable() && enabled) {
                        bluetoothAdapter.disable()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (config.blockUSBStorage != null) {
            Utils.lockUsbStorage(config.blockUSBStorage, context)
        }

        if (config.manageScreenTimeout != null) {
            Utils.setScreenTimeoutPolicy(config.manageScreenTimeout, config.screenTimeout, context)
        }

        if (config.manageVolume != null && config.manageVolume == true && config.volumeValue != null) {
            Utils.lockVolume(false, context)
            if (!Utils.setVolume(config.volumeValue, context)) {
                Log.d(Const.LOG_TAG, "Failed to set the device volume")
            }
        }

        if (config.lockVolume != null) {
            Utils.lockVolume(config.lockVolume, context)
        }

        if (config.disableScreenCapture != null) {
            Utils.disableScreenshots(config.disableScreenCapture, context)
        }
    }
}