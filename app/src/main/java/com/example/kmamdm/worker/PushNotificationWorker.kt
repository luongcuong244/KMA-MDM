package com.example.kmamdm.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.kmamdm.BuildConfig
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.CryptoHelper
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.utils.Const
import retrofit2.Response
import java.io.UnsupportedEncodingException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PushNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    private val settingsHelper: SettingsHelper = SettingsHelper.getInstance(context)

    // This is running in a background thread by WorkManager
    override fun doWork(): Result {
        if (settingsHelper.getConfig() == null) {
            return Result.failure()
        }

        val pushOptions: String = settingsHelper.getConfig().getPushOptions()

        return if (pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_WORKER ||
            pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_ALARM
        ) {
            // Note: MQTT client is automatically reconnected if connection is broken during launcher running,
            // and re-initializing it may cause looped errors
            // In particular, MQTT client is reconnected after turning Wi-Fi off and back on.
            // Re-connection of MQTT client at Headwind MDM startup is implemented in MainActivity
            // So by now, just request configuration update some times per day to avoid "device lost" issues
            doMqttWork()
        } else {
            // PUSH_OPTIONS_POLLING by default
            //return doPollingWork();
            // Long polling is done in a related service
            // This is just a reserve task to prevent devices from being lost just in case
            doLongPollingWork()
        }
    }

    // Query server for incoming messages each 15 minutes
    private fun doPollingWork(): Result {
        val serverService: ServerService = ServerServiceKeeper.getServerServiceInstance(context)
        val secondaryServerService: ServerService =
            ServerServiceKeeper.getSecondaryServerServiceInstance(
                context
            )
        var response: Response<PushResponse>? = null

        // Calculate request signature
        var encodedDeviceId: String = settingsHelper.getDeviceId()
        try {
            encodedDeviceId = URLEncoder.encode(encodedDeviceId, "utf8")
        } catch (e: UnsupportedEncodingException) {
        }
        val path: String =
            settingsHelper.getServerProject() + "/rest/notifications/device/" + encodedDeviceId
        var signature: String? = null
        try {
            signature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + path)
        } catch (e: Exception) {
        }

        RemoteLogger.log(context, Const.LOG_DEBUG, "Querying push notifications")
        try {
            response = serverService.queryPushNotifications
            (settingsHelper.getServerProject(), settingsHelper.getDeviceId(), signature).execute()
        } catch (e: Exception) {
            RemoteLogger.log(
                context,
                Const.LOG_WARN,
                "Failed to query push notifications: " + e.message
            )
            e.printStackTrace()
        }

        try {
            if (response == null) {
                response = secondaryServerService.queryPushNotifications
                (settingsHelper.getServerProject(), settingsHelper.getDeviceId(), signature).execute()
            }

            if (response.isSuccessful()) {
                if (Const.STATUS_OK.equals(response.body().getStatus()) && response.body()
                        .getData() != null
                ) {
                    val filteredMessages: MutableMap<String, PushMessage> =
                        HashMap<String, PushMessage>()
                    for (message in response.body().getData()) {
                        // Filter out multiple configuration update requests
                        if (!message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED) ||
                            !filteredMessages.containsKey(PushMessage.TYPE_CONFIG_UPDATED)
                        ) {
                            filteredMessages[message.getMessageType()] = message
                        }
                    }
                    for ((_, value) in filteredMessages) {
                        PushNotificationProcessor.process(value, context)
                    }
                    return Result.success()
                } else {
                    return Result.failure()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.failure()
    }

    // Periodic configuration update requests
    private fun doLongPollingWork(): Result {
        return forceConfigUpdateWork()
    }

    // Periodic configuration update requests
    private fun doMqttWork(): Result {
        if (PushNotificationMqttWrapper.getInstance().checkPingDeath(context)) {
            RemoteLogger.log(context, Const.LOG_INFO, "MQTT ping death detected, reconnecting!")
            mqttReconnect()
        }

        return forceConfigUpdateWork()
    }

    private fun forceConfigUpdateWork(): Result {
        val lastConfigUpdateTimestamp: Long = settingsHelper.getConfigUpdateTimestamp()
        val now = System.currentTimeMillis()
        if (lastConfigUpdateTimestamp == 0L) {
            settingsHelper.setConfigUpdateTimestamp(now)
            return Result.success()
        }
        if (lastConfigUpdateTimestamp + CONFIG_UPDATE_INTERVAL > now) {
            return Result.success()
        }
        RemoteLogger.log(context, Const.LOG_DEBUG, "Forcing configuration update")
        settingsHelper.setConfigUpdateTimestamp(now)
        ConfigUpdater.forceConfigUpdate(context)
        return Result.success()
    }

    // We assume we're running in the background!
    // https://stackoverflow.com/questions/57552955/is-possible-backgroundworker-dowork-in-main-thread
    private fun mqttReconnect() {
        var keepaliveTime: Int = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC
        val pushOptions: String = settingsHelper.getConfig().getPushOptions()
        val newKeepaliveTime: Int = settingsHelper.getConfig().getKeepaliveTime()
        if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
            keepaliveTime = newKeepaliveTime
        }
        try {
            PushNotificationMqttWrapper.getInstance().disconnect(context)
            Thread.sleep(5000)
            val url = URL(settingsHelper.getBaseUrl())
            PushNotificationMqttWrapper.getInstance().connect(
                context, url.host, BuildConfig.MQTT_PORT,
                pushOptions, keepaliveTime, settingsHelper.getDeviceId(), null, null
            )
        } catch (e: Exception) {
            RemoteLogger.log(context, Const.LOG_DEBUG, "Reconnection failure: " + e.message)
            e.printStackTrace()
        }
    }

    companion object {
        // Minimal interval is 15 minutes as per docs
        const val FIRE_PERIOD_MINS: Int = 15

        // Interval to update configuration to avoid losing device due to push failure
        const val CONFIG_UPDATE_INTERVAL: Long = 3600000L

        private const val WORK_TAG_PERIODIC = "com.hmdm.launcher.WORK_TAG_PUSH_PERIODIC"

        fun schedule(context: Context) {
            Log.d(Const.LOG_TAG, "Push notifications enqueued: " + FIRE_PERIOD_MINS + " mins")
            val queryRequest: PeriodicWorkRequest =
                Builder(PushNotificationWorker::class.java, FIRE_PERIOD_MINS, TimeUnit.MINUTES)
                    .addTag(Const.WORK_TAG_COMMON)
                    .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_TAG_PERIODIC, ExistingPeriodicWorkPolicy.REPLACE, queryRequest
            )
        }
    }
}
