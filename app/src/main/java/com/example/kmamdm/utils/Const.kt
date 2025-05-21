package com.example.kmamdm.utils

object Const {

    object SocketAction {
        const val SOCKET_CONNECTED = "SOCKET_CONNECTED"
        const val SOCKET_DISCONNECTED = "SOCKET_DISCONNECTED"
        const val SOCKET_ERROR = "SOCKET_ERROR"
    }

    const val  REQUEST_INSTALL = 1011

    const val TASK_SUCCESS: Int = 0
    
    const val TASK_ERROR: Int = 1
    
    const val TASK_NETWORK_ERROR: Int = 2

    const val ACTION_SERVICE_STOP: String = "SERVICE_STOP"
    
    val ACTION_SHOW_LAUNCHER: String = "SHOW_LAUNCHER"
    
    val ACTION_ENABLE_SETTINGS: String = "ENABLE_SETTINGS"
    
    val ACTION_PERMISSIVE_MODE: String = "PERMISSIVE_MODE" // Temporary action
    
    val ACTION_TOGGLE_PERMISSIVE: String = "TOGGLE_PERMISSIVE" // Permanent action
    
    val ACTION_EXIT_KIOSK: String = "EXIT_KIOSK"
    
    val ACTION_STOP_CONTROL: String = "STOP_CONTROL"
    
    val ACTION_EXIT: String = "EXIT"
    
    val ACTION_HIDE_SCREEN: String = "HIDE_SCREEN"
    
    val ACTION_UPDATE_CONFIGURATION: String = "UPDATE_CONFIGURATION"
    
    val ACTION_POLICY_VIOLATION: String = "ACTION_POLICY_VIOLATION"
    
    val ACTION_ADMIN: String = "ADMIN"
    
    val ACTION_INSTALL_COMPLETE: String = "INSTALL_COMPLETE"
    
    val ACTION_DISABLE_BLOCK_WINDOW: String = "DISABLE_BLOCK_WINDOW"

    
    val EXTRA_ENABLED: String = "ENABLED"

    
    var CONNECTION_TIMEOUT: Long = 10000
    
    var LONG_POLLING_READ_TIMEOUT: Long = 300000
    
    val STATUS_OK: String = "OK"
    
    val ORIENTATION: String = "ORIENTATION"
    
    val PACKAGE_NAME: String = "PACKAGE_NAME"
    
    val POLICY_VIOLATION_CAUSE: String = "POLICY_VIOLATION_CAUSE"
    
    val RESTORED_ACTIVITY: String = "RESTORED_ACTIVITY"

    
    const val GPS_ON_REQUIRED: Int = 1
    
    const val GPS_OFF_REQUIRED: Int = 2
    
    const val MOBILE_DATA_ON_REQUIRED: Int = 3
    
    const val MOBILE_DATA_OFF_REQUIRED: Int = 4

    
    val PREFERENCES: String = "PREFERENCES"

    
    const val PREFERENCES_ON: Int = 1
    
    const val PREFERENCES_OFF: Int = 0

    
    val PREFERENCES_ADMINISTRATOR: String = "PREFERENCES_ADMINISTRATOR"
    
    val PREFERENCES_OVERLAY: String = "PREFERENCES_OVERLAY"
    
    val PREFERENCES_USAGE_STATISTICS: String = "PREFERENCES_USAGE_STATISTICS"
    
    val PREFERENCES_MANAGE_STORAGE: String = "PREFERENCES_MANAGE_STORAGE"
    
    val PREFERENCES_ACCESSIBILITY_SERVICE: String = "PREFERENCES_ACCESSIBILITY_SERVICE"
    
    val PREFERENCES_DEVICE_OWNER: String = "PREFERENCES_DEVICE_OWNER"
    
    val PREFERENCES_UNKNOWN_SOURCES: String = "PREFERENCES_UNKNOWN_SOURCES"
    
    val PREFERENCES_DISABLE_LOCATION: String = "PREFERENCES_DISABLE_LOCATION"
    
    val PREFERENCES_MIUI_PERMISSIONS: String = "PREFERENCES_MIUI_PERMISSIONS"
    
    val PREFERENCES_MIUI_DEVELOPER: String = "PREFERENCES_MIUI_DEVELOPER"
    
    val PREFERENCES_MIUI_OPTIMIZATION: String = "PREFERENCES_MIUI_OPTIMIZATION"
    
    val PREFERENCES_LOG_STRING: String = "PREFERENCES_LOG_STRING"
    
    val PREFERENCES_DATA_TOKEN: String = "PREFERENCES_DATA_TOKEN"

    
    const val MIUI_PERMISSIONS: Int = 0
    
    const val MIUI_DEVELOPER: Int = 1
    
    const val MIUI_OPTIMIZATION: Int = 2

    
    val LOG_TAG: String = "KMAMDM"

    
    const val SETTINGS_UNBLOCK_TIME: Int = 180000
    
    const val PERMISSIVE_MODE_TIME: Int = 180000

    
    val LAUNCHER_RESTARTER_PACKAGE_ID: String = "com.hmdm.emuilauncherrestarter"
    
    val LAUNCHER_RESTARTER_OLD_VERSION: String = "oldVersion"
    
    val LAUNCHER_RESTARTER_STOP: String = "stop"

    
    val SETTINGS_PACKAGE_NAME: String = "com.android.settings"
    
    val GSF_PACKAGE_NAME: String = "com.google.android.gsf"
    
    val SYSTEM_UI_PACKAGE_NAME: String = "com.android.systemui"
    
    val KIOSK_BROWSER_PACKAGE_NAME: String = "com.hmdm.kiosk"
    
    val APUPPET_PACKAGE_NAME: String = "com.hmdm.control"
    
    val APUPPET_SERVICE_CLASS_NAME: String = "com.hmdm.control.GestureDispatchService"

    
    val QR_BASE_URL_ATTR: String = "com.hmdm.BASE_URL"
    
    val QR_SECONDARY_BASE_URL_ATTR: String = "com.hmdm.SECONDARY_BASE_URL"
    
    val QR_SERVER_PROJECT_ATTR: String = "com.hmdm.SERVER_PROJECT"
    
    val QR_DEVICE_ID_ATTR: String = "DEVICE_ID"
    
    val QR_LEGACY_DEVICE_ID_ATTR: String = "ru.headwind.kiosk.DEVICE_ID"
    
    val QR_DEVICE_ID_USE_ATTR: String = "com.hmdm.DEVICE_ID_USE"
    
    val QR_CUSTOMER_ATTR: String = "com.hmdm.CUSTOMER"
    
    val QR_CONFIG_ATTR: String = "com.hmdm.CONFIG"
    
    val QR_GROUP_ATTR: String = "com.hmdm.GROUP"
    
    val QR_OPEN_WIFI_ATTR: String = "com.hmdm.OPEN_WIFI"
    
    val QR_WORK_PROFILE_ATTR: String = "com.hmdm.WORK_PROFILE"

    
    const val KIOSK_UNLOCK_CLICK_COUNT: Int = 1

    
    val INTENT_PUSH_NOTIFICATION_PREFIX: String = "com.hmdm.push."
    
    val INTENT_PUSH_NOTIFICATION_EXTRA: String = "com.hmdm.PUSH_DATA"

    
    val WORK_TAG_COMMON: String = "com.hmdm.launcher"

    
    val DEVICE_CHARGING_USB: String = "usb"
    
    val DEVICE_CHARGING_AC: String = "ac"

    
    val WIFI_STATE_FAILED: String = "failed"
    
    val WIFI_STATE_INACTIVE: String = "inactive"
    
    val WIFI_STATE_SCANNING: String = "scanning"
    
    val WIFI_STATE_DISCONNECTED: String = "disconnected"
    
    val WIFI_STATE_CONNECTING: String = "connecting"
    
    val WIFI_STATE_CONNECTED: String = "connected"

    
    val GPS_STATE_INACTIVE: String = "inactive"
    
    val GPS_STATE_LOST: String = "lost"
    
    val GPS_STATE_ACTIVE: String = "active"

    
    val MOBILE_STATE_INACTIVE: String = "inactive"
    
    val MOBILE_STATE_DISCONNECTED: String = "disconnected"
    
    val MOBILE_STATE_CONNECTED: String = "connected"

    
    val MOBILE_SIMSTATE_UNKNOWN: String = "unknown"
    
    val MOBILE_SIMSTATE_ABSENT: String = "absent"
    
    val MOBILE_SIMSTATE_PIN_REQUIRED: String = "pinRequired"
    
    val MOBILE_SIMSTATE_PUK_REQUIRED: String = "pukRequired"
    
    val MOBILE_SIMSTATE_LOCKED: String = "locked"
    
    val MOBILE_SIMSTATE_READY: String = "ready"
    
    val MOBILE_SIMSTATE_NOT_READY: String = "notReady"
    
    val MOBILE_SIMSTATE_DISABLED: String = "disabled"
    
    val MOBILE_SIMSTATE_ERROR: String = "error"
    
    val MOBILE_SIMSTATE_RESTRICTED: String = "restricted"

    
    const val LOG_ERROR: Int = 1
    
    const val LOG_WARN: Int = 2
    
    const val LOG_INFO: Int = 3
    
    const val LOG_DEBUG: Int = 4
    
    const val LOG_VERBOSE: Int = 5

    
    val PASSWORD_QUALITY_PRESENT: String = "present"
    
    val PASSWORD_QUALITY_EASY: String = "easy"
    
    val PASSWORD_QUALITY_MODERATE: String = "moderate"
    
    val PASSWORD_QUALITY_STRONG: String = "strong"

    
    val HEADER_IP_ADDRESS: String = "X-IP-Address"
    
    val HEADER_RESPONSE_SIGNATURE: String = "X-Response-Signature"

    
    const val SCREEN_ORIENTATION_PORTRAIT: Int = 1
    
    const val SCREEN_ORIENTATION_LANDSCAPE: Int = 2

    
    const val DIRECTION_LEFT: Int = 0
    
    const val DIRECTION_RIGHT: Int = 1
    
    const val DIRECTION_UP: Int = 2
    
    const val DIRECTION_DOWN: Int = 3

    
    const val DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC: Int = 300
    
    const val DEFAULT_PUSH_WORKER_KEEPALIVE_TIME_SEC: Int = 900
}