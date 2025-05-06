package com.example.kmamdm.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.kmamdm.model.DeviceInfo

object DeviceUtils {
    private var deviceInfo: DeviceInfo? = null

    fun getDeviceInfo(context: Context): DeviceInfo {
        if (deviceInfo != null) {
            return deviceInfo!!
        }

        val deviceName = Build.DEVICE // e.g., "Pixel 4"
        val deviceManufacturer = Build.MANUFACTURER // e.g., "Google"
        val deviceModel = Build.MODEL // e.g., "Pixel 4"
        val deviceBrand = Build.BRAND // e.g., "google"
        val deviceProduct = Build.PRODUCT // e.g., "coral"
        val deviceSerial = getSerialNumber() // e.g., "1234567890ABCDEF"
        val deviceHardware = Build.HARDWARE // e.g., "coral"
        val deviceBuildId = Build.ID // e.g., "RQ1A.210205.004"
        val androidVersion = Build.VERSION.RELEASE // e.g., "11"
        val androidSdkVersion = Build.VERSION.SDK_INT // e.g., 30
        val imei: String = getIMEI(context) // e.g., "123456789012345"
        val cpuArch = Build.SUPPORTED_ABIS.joinToString(", ") // e.g., "arm64-v8a, armeabi-v7a"
        val cpuCores = Runtime.getRuntime().availableProcessors() // e.g., 8
        val totalRAM = Runtime.getRuntime().totalMemory() // e.g., 8GB
        val totalStorage = android.os.Environment.getExternalStorageDirectory().totalSpace // e.g., 128GB

        deviceInfo = DeviceInfo(
            deviceName = deviceName,
            deviceManufacturer = deviceManufacturer,
            deviceModel = deviceModel,
            deviceBrand = deviceBrand,
            deviceProduct = deviceProduct,
            deviceSerial = deviceSerial,
            deviceHardware = deviceHardware,
            deviceBuildId = deviceBuildId,
            androidVersion = androidVersion,
            androidSdkVersion = androidSdkVersion,
            androidId = getAndroidID(context),
            imei = imei, // Note: IMEI may not be available on all devices, especially if they don't have telephony capabilities.
            cpuArch = cpuArch,
            cpuCores = cpuCores,
            totalRAM = totalRAM,
            totalStorage = totalStorage
        )
        return deviceInfo!!
    }

    private fun getAndroidID(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getIMEI(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                telephonyManager.imei ?: "Unavailable"
            } catch (e: SecurityException) {
                "Permission denied"
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                telephonyManager.deviceId ?: "Unavailable"
            } catch (e: SecurityException) {
                "Permission denied"
            }
        }
    }

    private fun getSerialNumber(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Build.getSerial()
            } catch (e: SecurityException) {
                "Permission denied"
            }
        } else {
            @Suppress("DEPRECATION")
            Build.SERIAL
        }
    }
}