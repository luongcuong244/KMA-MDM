package com.example.kmamdm.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.kmamdm.extension.log
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.Const
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

        return forceConfigUpdateWork()
    }

    private fun forceConfigUpdateWork(): Result {
        val lastConfigUpdateTimestamp: Long = settingsHelper.getConfigUpdateTimestamp()
        val now = System.currentTimeMillis()
//        if (lastConfigUpdateTimestamp == 0L) {
//            settingsHelper.setConfigUpdateTimestamp(now)
//            return Result.success()
//        }
//        if (lastConfigUpdateTimestamp + CONFIG_UPDATE_INTERVAL > now) {
//            return Result.success()
//        }
        log("Forcing configuration update")
        settingsHelper.setConfigUpdateTimestamp(now)
        ConfigUpdater.forceConfigUpdate(context, null, false)
        return Result.success()
    }

    companion object {
        // Minimal interval is 15 minutes as per docs
        private const val FIRE_PERIOD_MINS: Long = 1L

        // Interval to update configuration to avoid losing device due to push failure
        const val CONFIG_UPDATE_INTERVAL: Long = 3600000L

        private const val WORK_TAG_PERIODIC = "WORK_TAG_PUSH_PERIODIC"

        fun schedule(context: Context) {
            log("Push notifications enqueued: $FIRE_PERIOD_MINS mins")
            val queryRequest: PeriodicWorkRequest =
                PeriodicWorkRequestBuilder<PushNotificationWorker>(FIRE_PERIOD_MINS, TimeUnit.MINUTES)
                    .addTag(Const.WORK_TAG_COMMON)
                    .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_TAG_PERIODIC, ExistingPeriodicWorkPolicy.REPLACE, queryRequest
            )
        }
    }
}
