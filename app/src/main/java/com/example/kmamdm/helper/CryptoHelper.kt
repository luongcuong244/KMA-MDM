package com.example.kmamdm.helper

import java.security.MessageDigest
import java.util.Locale

object CryptoHelper {
    private const val MD5 = "MD5"
    private const val UTF8 = "UTF-8"

    private val hexArray = "0123456789abcdef".toCharArray()

    fun getMD5String(value: String): String {
        try {
            val md = MessageDigest.getInstance(MD5)
            md.update(value.toByteArray(charset(UTF8)))
            val digest = md.digest()

            val hexChars = CharArray(digest.size * 2)
            for (i in digest.indices) {
                val v = digest[i].toInt() and 0xFF
                hexChars[i * 2] = hexArray[v ushr 4]
                hexChars[i * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars).uppercase(Locale.getDefault())
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun getSHA1String(value: String): String {
        try {
            val md = MessageDigest.getInstance("SHA-1")
            md.update(value.toByteArray(charset(UTF8)))
            val digest = md.digest()

            val hexChars = CharArray(digest.size * 2)
            for (i in digest.indices) {
                val v = digest[i].toInt() and 0xFF
                hexChars[i * 2] = hexArray[v ushr 4]
                hexChars[i * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars).uppercase(Locale.getDefault())
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}