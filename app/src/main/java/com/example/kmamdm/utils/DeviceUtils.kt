package com.example.kmamdm.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.example.kmamdm.model.DeviceInfo
import com.example.kmamdm.socket.json.DeviceStatus

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

    fun getDeviceStatus(context: Context): DeviceStatus {
        val deviceInfo = getDeviceInfo(context)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val isCharging = batteryManager.isCharging
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryStatus = getBatteryStatus(context)

        val storageUsage = getStorageUsage()
        val ramUsage = getRamUsage()
        val isLocked = isDeviceLocked(context)
        val networkType = getNetworkType(context)
        val locationEnabled = isLocationEnabled(context)
        val location = getCurrentLocation(context)

        return DeviceStatus(
            deviceInfo = deviceInfo,
            batteryLevel = batteryLevel,
            batteryStatus = batteryStatus,
            isCharging = isCharging,
            storageUsage = storageUsage,
            ramUsage = ramUsage,
            isLocked = isLocked,
            networkType = networkType,
            locationEnabled = locationEnabled,
            location = location
        )
    }

    private fun getBatteryStatus(context: Context): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
    }

    private fun getStorageUsage(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        return used
    }

    private fun getRamUsage(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        Log.i("DeviceUtils", "Used RAM: $used")
        return used
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    private fun getNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "None"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "None"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Unknown"
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(context: Context): String {
        // Lưu ý: cần cấp quyền ACCESS_FINE_LOCATION hoặc ACCESS_COARSE_LOCATION trước khi dùng
        // Nếu không có quyền, trả về "Unknown"
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Permission denied"
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        for (provider in providers) {
            val location: Location? = locationManager.getLastKnownLocation(provider)
            if (location != null) {
                return "${location.latitude},${location.longitude}"
            }
        }
        return "Unknown"
    }
}