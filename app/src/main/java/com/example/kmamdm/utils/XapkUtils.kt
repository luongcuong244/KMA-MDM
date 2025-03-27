package com.example.kmamdm.utils

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.os.Build
import android.util.Log
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.zip.ZipFile

object XapkUtils {
    fun extract(context: Context, xapk: File): List<File>? {
        // Here we presume that xapk file name ends with .xapk
        try {
            val extractDir = xapk.name.substring(0, xapk.name.length - 5)
            val extractDirFile = File(context.getExternalFilesDir(null), extractDir)
            if (extractDirFile.isDirectory) {
                FileUtils.deleteDirectory(extractDirFile)
            } else if (extractDirFile.exists()) {
                extractDirFile.delete()
            }
            extractDirFile.mkdirs()

            val result: MutableList<File> = LinkedList()

            val zipFile = ZipFile(xapk)
            val entries = zipFile.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".apk")) {
                    val inputStream = zipFile.getInputStream(entry)
                    val resultFile = File(extractDirFile, entry.name)
                    val outputStream = FileOutputStream(resultFile)
                    IOUtils.copy(inputStream, outputStream)
                    inputStream.close()
                    outputStream.close()
                    result.add(resultFile)
                }
            }
            zipFile.close()
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun install(
        context: Context,
        files: List<File>?,
        packageName: String?,
        errorHandler: InstallUtils.InstallErrorHandler?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        if (files == null) {
            if (errorHandler != null) {
                errorHandler.onInstallError(null)
            }
            return
        }
        var totalSize: Long = 0
        for (file in files) {
            totalSize += file.length()
        }

        try {
            Log.i(Const.LOG_TAG, "Installing XAPK $packageName")
            val packageInstaller = context.packageManager.packageInstaller
            val params = SessionParams(
                SessionParams.MODE_FULL_INSTALL
            )
            if (packageName != null) {
                params.setAppPackageName(packageName)
            }
            params.setSize(totalSize)
            val sessionId = packageInstaller.createSession(params)

            for (file in files) {
                addFileToSession(sessionId, file, packageInstaller)
            }

            val session = packageInstaller.openSession(sessionId)
            session.commit(InstallUtils.createIntentSender(context, sessionId, packageName))
            session.close()
            Log.i(Const.LOG_TAG, "Installation session committed")
        } catch (e: Exception) {
            if (errorHandler != null) {
                errorHandler.onInstallError(e.message)
            }
        }
    }

    @Throws(IOException::class)
    private fun addFileToSession(sessionId: Int, file: File, packageInstaller: PackageInstaller) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        val `in` = FileInputStream(file)
        // set params
        val session = packageInstaller.openSession(sessionId)
        val out = session.openWrite(file.name, 0, file.length())
        val buffer = ByteArray(65536)
        var c: Int
        while ((`in`.read(buffer).also { c = it }) != -1) {
            out.write(buffer, 0, c)
        }
        session.fsync(out)
        `in`.close()
        out.close()
        session.close()
    }
}