package com.example.kmamdm.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kmamdm.extension.isDisable
import com.example.kmamdm.extension.isEnable
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.Utils
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class StatusControlService : Service() {
    private var settingsHelper: SettingsHelper? = null
    private var threadPoolExecutor = ScheduledThreadPoolExecutor(1)
    private var controlDisabled = false
    private var disableControlTimer: Timer? = null

    private val ENABLE_CONTROL_DELAY: Long = 60

    private val STATUS_CHECK_INTERVAL_MS: Long = 5000

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Const.ACTION_SERVICE_STOP -> stopSelf()
                Const.ACTION_STOP_CONTROL -> disableControl()
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)

        threadPoolExecutor.shutdownNow()
        threadPoolExecutor = ScheduledThreadPoolExecutor(1)

        Log.i(Const.LOG_TAG, "StatusControlService: service stopped")

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        settingsHelper = SettingsHelper.getInstance(this)

        Log.i(Const.LOG_TAG, "StatusControlService: service started.")

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)

        val intentFilter = IntentFilter(Const.ACTION_SERVICE_STOP)
        intentFilter.addAction(Const.ACTION_STOP_CONTROL)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter)

        threadPoolExecutor.shutdownNow()

        threadPoolExecutor = ScheduledThreadPoolExecutor(1)
        threadPoolExecutor.scheduleWithFixedDelay(
            { controlStatus() },
            STATUS_CHECK_INTERVAL_MS, STATUS_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS
        )

        return START_STICKY
    }


    private fun disableControl() {
        Log.i(Const.LOG_TAG, "StatusControlService: request to disable control")

        if (disableControlTimer != null) {
            try {
                disableControlTimer!!.cancel()
            } catch (e: Exception) {
            }
            disableControlTimer = null
        }
        controlDisabled = true
        disableControlTimer = Timer()
        disableControlTimer!!.schedule(object : TimerTask() {
            override fun run() {
                controlDisabled = false
                Log.i(Const.LOG_TAG, "StatusControlService: control enabled")
            }
        }, ENABLE_CONTROL_DELAY * 1000)
        Log.i(Const.LOG_TAG, "StatusControlService: control disabled for 60 sec")
    }

    // This method is called every 5 seconds to check the status of Bluetooth, Wi-Fi, GPS, and mobile data
    // don't need permission because we are device owner
    @SuppressLint("MissingPermission")
    private fun controlStatus() {
        val config: ServerConfig? = settingsHelper?.getConfig()
        if (config == null || controlDisabled) {
            return
        }

        Log.i(Const.LOG_TAG, "StatusControlService: control status")

        if (config.bluetooth != null) {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null) {
                    val enabled = bluetoothAdapter.isEnabled
                    try {
                        if (config.bluetooth.isEnable() && !enabled) {
                            bluetoothAdapter.enable()
                        } else if (config.bluetooth.isDisable() && enabled) {
                            bluetoothAdapter.disable()
                        }
                    } catch (e: Exception) {
                        Log.d(Const.LOG_TAG, "StatusControlService: Bluetooth enable/disable failed with ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Note: SecurityException here on Mediatek
        // Looks like com.mediatek.permission.CTA_ENABLE_WIFI needs to be explicitly granted
        // or even available to system apps only
        // By now, let's just ignore this issue
        if (config.wifi != null) {
            try {
                val wifiManager =
                    this.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val enabled = wifiManager.isWifiEnabled
                if (config.wifi.isEnable() && !enabled) {
                    wifiManager.setWifiEnabled(true)
                } else if (config.wifi.isDisable() && enabled) {
                    wifiManager.setWifiEnabled(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (config.gps != null) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (config.gps.isEnable() && !enabled) {
                notifyStatusViolation(Const.GPS_ON_REQUIRED)
                return
            } else if (config.gps.isDisable() && enabled) {
                notifyStatusViolation(Const.GPS_OFF_REQUIRED)
                return
            }
        }

        if (config.mobileData != null) {
            val cm = getSystemService(CONNECTIVITY_SERVICE)
            try {
                val enabled: Boolean = Utils.isMobileDataEnabled(this)
                if (config.mobileData.isEnable() && !enabled) {
                    notifyStatusViolation(Const.MOBILE_DATA_ON_REQUIRED)
                } else if (config.mobileData.isDisable() && enabled) {
                    notifyStatusViolation(Const.MOBILE_DATA_OFF_REQUIRED)
                }
            } catch (e: Exception) {
                // Some problem access private API
            }
        }
    }

    private fun notifyStatusViolation(cause: Int) {
        Log.i(Const.LOG_TAG, "StatusControlService: notify status violation $cause")
        val intent = Intent(Const.ACTION_POLICY_VIOLATION)
        intent.putExtra(Const.POLICY_VIOLATION_CAUSE, cause)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, StatusControlService::class.java)
            context.startService(intent)
        }
    }
}