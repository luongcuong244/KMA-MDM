package com.example.kmamdm.utils

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object InstallUtils {

    fun areVersionsEqual(v1: String, c1: Int, v2: String, c2: Int): Boolean {
        if (c2 != 0) {
            // If version code is present, let's compare version codes instead of names
            return c1 == c2
        }

        // Compare only digits (in Android 9 EMUI on Huawei Honor 8A, getPackageInfo doesn't get letters!)
        val v1d = v1.replace("[^\\d.]".toRegex(), "")
        val v2d = v2.replace("[^\\d.]".toRegex(), "")
        return v1d == v2d
    }

    @Throws(Exception::class)
    fun downloadFile(context: Context, strUrl: String, progressHandler: DownloadProgress): File {
        var tempFile = File(context.getExternalFilesDir(null), getFileName(strUrl))
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            try {
                tempFile.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()

                tempFile = File.createTempFile(getFileName(strUrl), "temp")
            }

            val url = URL(strUrl)

            val connection: HttpURLConnection
            if (url.protocol.lowercase(Locale.getDefault()) == "https") {
                connection = url.openConnection() as HttpsURLConnection
                connection.hostnameVerifier = DO_NOT_VERIFY
            } else {
                connection = url.openConnection() as HttpURLConnection
            }
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.connectTimeout = Const.CONNECTION_TIMEOUT.toInt()
            connection.readTimeout = Const.CONNECTION_TIMEOUT.toInt()
            connection.connect()

            if (connection.responseCode != 200) {
                throw Exception("Bad server response for " + strUrl + ": " + connection.responseCode)
            }

            val lengthOfFile = connection.contentLength

            progressHandler.onDownloadProgress(0, lengthOfFile.toLong(), 0)

            val `is` = connection.inputStream
            val dis = DataInputStream(`is`)

            val buffer = ByteArray(1024)
            var length: Int
            var total: Long = 0

            val fos = FileOutputStream(tempFile)
            while ((dis.read(buffer).also { length = it }) > 0) {
                total += length.toLong()
                progressHandler.onDownloadProgress(
                    ((total * 100.0f) / lengthOfFile).toInt(),
                    lengthOfFile.toLong(),
                    total
                )
                fos.write(buffer, 0, length)
            }
            fos.flush()
            fos.close()

            dis.close()
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        return tempFile
    }

    private fun getFileName(strUrl: String): String {
        val slashIndex = strUrl.lastIndexOf("/")
        return if (slashIndex >= 0) strUrl.substring(slashIndex) else strUrl
    }

    fun silentInstallApplication(
        context: Context,
        file: File,
        packageName: String,
        errorHandler: InstallErrorHandler
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        if (!Utils.canInstallPackages(context)) {
            return
        }

        if (file.name.endsWith(".xapk")) {
            val files: List<File>? = XapkUtils.extract(context, file)
            XapkUtils.install(context, files, packageName, errorHandler)
            return
        }

        try {
            Log.i(Const.LOG_TAG, "Installing $packageName")
            val `in` = FileInputStream(file)
            val packageInstaller = context.packageManager.packageInstaller
            val params = SessionParams(
                SessionParams.MODE_FULL_INSTALL
            )
            params.setAppPackageName(packageName)
            // set params
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            val out = session.openWrite("COSU", 0, -1)
            val buffer = ByteArray(65536)
            var c: Int
            while ((`in`.read(buffer).also { c = it }) != -1) {
                out.write(buffer, 0, c)
            }
            session.fsync(out)
            `in`.close()
            out.close()

            session.commit(createIntentSender(context, sessionId, packageName))
            Log.i(Const.LOG_TAG, "Installation session committed")
        } catch (e: Exception) {
            Log.w(Const.LOG_TAG, "PackageInstaller error: " + e.message)
            e.printStackTrace()
            errorHandler.onInstallError(e.message)
        }
    }

    fun createIntentSender(context: Context?, sessionId: Int, packageName: String?): IntentSender {
        val intent: Intent = Intent(Const.ACTION_INSTALL_COMPLETE)
        if (packageName != null) {
            intent.putExtra(Const.PACKAGE_NAME, packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        )
        return pendingIntent.intentSender
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun silentUninstallApplication(context: Context, packageName: String) {
        val packageInstaller = context.packageManager.packageInstaller
        try {
            packageInstaller.uninstall(packageName, createIntentSender(context, 0, null))
        } catch (e: Exception) {
            // If we're trying to remove an unexistent app, it causes an exception so just ignore it
        }
    }

    fun requestInstallApplication(
        context: Context,
        file: File,
        errorHandler: InstallErrorHandler?
    ) {
        if (file.name.endsWith(".xapk")) {
            XapkUtils.install(context, XapkUtils.extract(context, file), null, errorHandler)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".provider",
                file
            )
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            // Let's set Intent.FLAG_ACTIVITY_NEW_TASK here
            // Some devices report:
            // android.util.AndroidRuntimeException
            // Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag. Is this really what you want?
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val apkUri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun requestUninstallApplication(context: Context, packageName: String) {
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        // Let's set Intent.FLAG_ACTIVITY_NEW_TASK here
        // Some devices report:
        // android.util.AndroidRuntimeException
        // Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag. Is this really what you want?
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPackageInstallerStatusMessage(status: Int): String {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> return "PENDING_USER_ACTION"
            PackageInstaller.STATUS_SUCCESS -> return "SUCCESS"
            PackageInstaller.STATUS_FAILURE -> return "FAILURE_UNKNOWN"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> return "BLOCKED"
            PackageInstaller.STATUS_FAILURE_ABORTED -> return "ABORTED"
            PackageInstaller.STATUS_FAILURE_INVALID -> return "INVALID"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> return "CONFLICT"
            PackageInstaller.STATUS_FAILURE_STORAGE -> return "STORAGE"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> return "INCOMPATIBLE"
        }
        return "UNKNOWN"
    }

    // always verify the host - dont check for certificate
    val DO_NOT_VERIFY: HostnameVerifier =
        HostnameVerifier { hostname, session -> true }

    /**
     * Trust every server - dont check for any certificate
     * This should be called at the app start if TRUST_ANY_CERTIFICATE is set to true
     */
    fun initUnsafeTrustManager() {
        // Create a trust manager that does not validate certificate chains
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }

            @Throws(CertificateException::class)
            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
            }
        })

        // Install the all-trusting trust manager
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection
                .setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteTempApk(file: File) {
        try {
            if (file.name.endsWith(".xapk")) {
                // For XAPK, we need to remove the directory with the same name
                val path = file.absolutePath
                val directory = File(path.substring(0, path.length - 5))
                if (directory.exists()) {
                    deleteRecursive(directory)
                }
            }
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) for (child in fileOrDirectory.listFiles()) deleteRecursive(
            child
        )

        fileOrDirectory.delete()
    }

    fun clearTempFiles(context: Context) {
        try {
            val filesDir = context.getExternalFilesDir(null)
            for (child in filesDir!!.listFiles()) {
                if (child.name.equals("MqttConnection", ignoreCase = true)) {
                    // These are names which should be kept here
                    continue
                }
                if (child.isDirectory) {
                    deleteRecursive(child)
                } else {
                    child.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    interface DownloadProgress {
        fun onDownloadProgress(progress: Int, total: Long, current: Long)
    }

    interface InstallErrorHandler {
        fun onInstallError(msg: String?)
    }
}