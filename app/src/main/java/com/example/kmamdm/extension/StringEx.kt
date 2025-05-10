package com.example.kmamdm.extension

fun String.isEnable(): Boolean {
    return this == "Enabled"
}

fun String.isDisable(): Boolean {
    return this == "Disabled"
}