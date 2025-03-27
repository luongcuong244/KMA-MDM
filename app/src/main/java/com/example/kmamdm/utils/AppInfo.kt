package com.example.kmamdm.utils

import android.os.Parcel
import android.os.Parcelable

class AppInfo() : Parcelable {
    var keyCode: Int? = null
    var name: CharSequence? = null
    var packageName: String? = null
    var url: String? = null
    var iconUrl: String? = null
    var screenOrder: Int? = null
    var useKiosk: Int = 0
    var longTap: Int = 0
    var intent: String? = null

    constructor(parcel: Parcel) : this() {
        keyCode = parcel.readValue(Int::class.java.classLoader) as? Int
        name = parcel.readString()
        packageName = parcel.readString()
        url = parcel.readString()
        iconUrl = parcel.readString()
        screenOrder = parcel.readValue(Int::class.java.classLoader) as? Int
        useKiosk = parcel.readInt()
        longTap = parcel.readInt()
        intent = parcel.readString()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(keyCode)
        parcel.writeString(name?.toString())
        parcel.writeString(packageName)
        parcel.writeString(url)
        parcel.writeString(iconUrl)
        parcel.writeValue(screenOrder)
        parcel.writeInt(useKiosk)
        parcel.writeInt(longTap)
        parcel.writeString(intent)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppInfo> {
        override fun createFromParcel(parcel: Parcel): AppInfo {
            return AppInfo(parcel)
        }

        override fun newArray(size: Int): Array<AppInfo?> {
            return arrayOfNulls(size)
        }
    }
}