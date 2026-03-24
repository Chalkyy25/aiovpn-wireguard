/*
 * Refactored for AIO VPN backend updater
 */
package com.wireguard.android.updater

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.aiovpn.auth.AuthGateActivity
import com.aiovpn.auth.DeviceTokenStore
import com.wireguard.android.Application
import com.aiovpn.app.BuildConfig
import com.wireguard.android.util.UserKnobs
import com.wireguard.android.util.applicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.InvalidParameterException
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Updater {
    private const val TAG = "AIOVPN/Updater"
    private const val LATEST_URL = "https://panel.aiovpn.co.uk/api/app/latest"

    private val updaterScope = CoroutineScope(Job() + Dispatchers.IO)

    private data class Sha256Digest(val bytes: ByteArray) {
        companion object {
            fun fromHex(hex: String): Sha256Digest {
                if (hex.length != 64) {
                    throw InvalidParameterException("SHA256 hash must be 64 hex chars")
                }
                val out = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                return Sha256Digest(out)
            }
        }
    }

    private data class UpdateInfo(
        val id: Long,
        val versionCode: Long,
        val versionName: String,
        val mandatory: Boolean,
        val releaseNotes: String?,
        val sha256: Sha256Digest,
        val apkUrl: String
    )

    sealed class Progress {
        object Complete : Progress()
        object Rechecking : Progress()
        class Downloading(val bytesDownloaded: ULong, val bytesTotal: ULong) : Progress()
        object Installing : Progress()

        class Available(
            val versionName: String,
            val versionCode: Long,
            val mandatory: Boolean,
            val releaseNotes: String?
        ) : Progress() {
            fun update() {
                applicationScope.launch {
                    UserKnobs.setUpdaterNewerVersionConsented(versionCode.toString())
                }
            }
        }

        class NeedsUserIntervention(val intent: Intent, private val sessionId: Int) : Progress() {
            private suspend fun installerActive(): Boolean {
                if (mutableState.value != this@NeedsUserIntervention) return true
                return try {
                    Application.get().packageManager.packageInstaller
                        .getSessionInfo(sessionId)
                        ?.isActive == true
                } catch (_: SecurityException) {
                    true
                }
            }

            fun markAsDone() {
                applicationScope.launch {
                    if (installerActive()) return@launch
                    delay(7.seconds)
                    if (installerActive()) return@launch
                    emitProgress(Failure(Exception("Install ignored by user")))
                }
            }
        }

        class Failure(val error: Throwable) : Progress() {
            fun retry() {
                updaterScope.launch {
                    downloadAndUpdateWrapErrors()
                }
            }
        }

        class Corrupt(val downloadUrl: String?) : Progress()
    }

    private val mutableState = MutableStateFlow<Progress>(Progress.Complete)
    val state = mutableState.asStateFlow()

    fun checkNow() {
        updaterScope.launch {
            try {
                emitProgress(Progress.Rechecking, force = true)

                val context = Application.get()
                val currentVersionCode = currentVersionCode(context)
                val update = checkForUpdates()

                if (update != null && update.versionCode > currentVersionCode) {
                    emitProgress(
                        Progress.Available(
                            versionName = update.versionName,
                            versionCode = update.versionCode,
                            mandatory = update.mandatory,
                            releaseNotes = update.releaseNotes
                        ),
                        force = true
                    )
                } else {
                    emitProgress(Progress.Complete, force = true)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Manual update check failed", e)
                emitProgress(Progress.Failure(e), force = true)
            }
        }
    }

    private suspend fun emitProgress(progress: Progress, force: Boolean = false) {
        if (force || mutableState.value::class.java != progress.javaClass) {
            mutableState.emit(progress)
        }
    }

    private fun installer(context: Context): String = try {
        val packageName = context.packageName
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName ?: ""
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName) ?: ""
        }
    } catch (_: Throwable) {
        ""
    }

    fun installerIsGooglePlay(context: Context): Boolean {
        return installer(context) == "com.android.vending"
    }

    private fun currentVersionCode(context: Context): Long {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
    }

    private suspend fun requireDeviceToken(): String {
        return DeviceTokenStore(Application.get())
            .getDeviceToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Missing device token")
    }

    private fun validateApkUrl(apkUrl: String) {
        val url = URL(apkUrl)
        val allowedHosts = setOf("panel.aiovpn.co.uk", "aiovpn.co.uk")
        if (url.protocol != "https" || url.host !in allowedHosts) {
            throw SecurityException("Untrusted APK URL: $apkUrl")
        }
    }

    private suspend fun checkForUpdates(): UpdateInfo? {
        val deviceToken = requireDeviceToken()
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("User-Agent", Application.USER_AGENT)
            connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            connection.setRequestProperty("X-App-Version-Code", BuildConfig.VERSION_CODE.toString())
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Update check failed: ${connection.responseCode} ${connection.responseMessage}")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val update = UpdateInfo(
                id = json.getLong("id"),
                versionCode = json.getLong("version_code"),
                versionName = json.getString("version_name"),
                mandatory = json.optBoolean("mandatory", false),
                releaseNotes = json.optString("release_notes", null)?.takeIf { it.isNotBlank() },
                sha256 = Sha256Digest.fromHex(json.getString("sha256")),
                apkUrl = json.getString("apk_url")
            )

            validateApkUrl(update.apkUrl)
            return update
        } catch (e: Exception) {
            throw IOException("Invalid update metadata from server", e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadAndUpdate() = withContext(Dispatchers.IO) {
        val context = Application.get().applicationContext
        val currentVersionCode = currentVersionCode(context)

        val receiver = InstallReceiver()
        val pendingIntent = withContext(Dispatchers.Main) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(receiver.broadcastAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            PendingIntent.getBroadcast(
                context,
                0,
                Intent(receiver.broadcastAction).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        emitProgress(Progress.Rechecking)

        val update = checkForUpdates()
        if (update == null || update.versionCode <= currentVersionCode) {
            emitProgress(Progress.Complete)
            return@withContext
        }

        emitProgress(Progress.Downloading(0UL, 0UL), true)

        val deviceToken = requireDeviceToken()
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("User-Agent", Application.USER_AGENT)
            connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            connection.setRequestProperty("X-App-Version-Code", BuildConfig.VERSION_CODE.toString())
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Update APK fetch failed: ${connection.responseCode} ${connection.responseMessage}")
            }

            var downloadedByteLen: ULong = 0UL
            val totalByteLen = connection.contentLengthLong.takeIf { it > 0 }?.toULong() ?: 0UL
            val buffer = ByteArray(32 * 1024)
            val digest = MessageDigest.getInstance("SHA-256")

            emitProgress(Progress.Downloading(downloadedByteLen, totalByteLen), true)

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            params.setAppPackageName(context.packageName)

            val session = installer.openSession(installer.createSession(params))
            var sessionFailure = true

            try {
                val installDest = session.openWrite("aiovpn-update-${update.versionCode}", 0, -1)

                installDest.use { dest ->
                    connection.inputStream.use { src ->
                        while (true) {
                            val readLen = src.read(buffer)
                            if (readLen <= 0) break

                            digest.update(buffer, 0, readLen)
                            dest.write(buffer, 0, readLen)

                            downloadedByteLen += readLen.toUInt()
                            emitProgress(Progress.Downloading(downloadedByteLen, totalByteLen), true)

                            if (downloadedByteLen >= 500UL * 1024UL * 1024UL) {
                                throw IOException("Update APK too large")
                            }
                        }
                    }

                    session.fsync(dest)
                }

                emitProgress(Progress.Installing)

                val actualHash = digest.digest()
                if (!actualHash.contentEquals(update.sha256.bytes)) {
                    throw SecurityException("Downloaded APK SHA256 mismatch")
                }

                sessionFailure = false
            } finally {
                if (sessionFailure) {
                    session.abandon()
                    session.close()
                }
            }

            session.commit(pendingIntent.intentSender)
            session.close()
        } finally {
            connection.disconnect()
        }
    }

    private var updating = false

    private suspend fun downloadAndUpdateWrapErrors() {
        if (updating) return
        updating = true

        try {
            downloadAndUpdate()
        } catch (e: Throwable) {
            Log.e(TAG, "Update failure", e)
            emitProgress(Progress.Failure(e), force = true)
        }

        updating = false
    }

    private class InstallReceiver : BroadcastReceiver() {
        val broadcastAction = UUID.randomUUID().toString()

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != broadcastAction) return

            when (val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE_INVALID
            )) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)
                    val userIntervention = IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_INTENT,
                        Intent::class.java
                    ) ?: return

                    applicationScope.launch {
                        emitProgress(Progress.NeedsUserIntervention(userIntervention, sessionId), force = true)
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    applicationScope.launch {
                        emitProgress(Progress.Complete, force = true)
                    }
                    context.applicationContext.unregisterReceiver(this)
                }

                else -> {
                    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)

                    try {
                        context.applicationContext.packageManager.packageInstaller.abandonSession(sessionId)
                    } catch (_: SecurityException) {
                    }

                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: "Installation error $status"

                    applicationScope.launch {
                        val error = Exception(message)
                        Log.e(TAG, "Install failure", error)
                        emitProgress(Progress.Failure(error), force = true)
                    }

                    context.applicationContext.unregisterReceiver(this)
                }
            }
        }
    }

    fun monitorForUpdates() {
        if (BuildConfig.DEBUG) return

        val context = Application.get()
        val installedBy = installer(context)

        if (installerIsGooglePlay(context)) return

        if (BuildConfig.BUILD_TYPE == "googleplay") {
            if (installedBy.isNotEmpty()) {
                applicationScope.launch {
                    emitProgress(Progress.Corrupt(null), force = true)
                }
            }
            return
        }

        val hasInstallPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        }.requestedPermissions?.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES) == true

        if (!hasInstallPermission) {
            if (installedBy.isNotEmpty()) {
                updaterScope.launch {
                    val update = try {
                        checkForUpdates()
                    } catch (_: Throwable) {
                        null
                    }
                    emitProgress(Progress.Corrupt(update?.apkUrl), force = true)
                }
            }
            return
        }

        updaterScope.launch {
            val currentVersionCode = currentVersionCode(context)
            val seenVersion = UserKnobs.updaterNewerVersionSeen.firstOrNull()?.toLongOrNull() ?: 0L

            if (seenVersion > currentVersionCode) {
                return@launch
            }

            var waitTime = 15
            while (true) {
                try {
                    val update = checkForUpdates()
                    if (update != null && update.versionCode > currentVersionCode) {
                        Log.i(TAG, "Update available: ${update.versionName} (${update.versionCode})")
                        UserKnobs.setUpdaterNewerVersionSeen(update.versionCode.toString())

                        if (update.mandatory) {
                            UserKnobs.setUpdaterNewerVersionConsented(update.versionCode.toString())
                        } else {
                            emitProgress(
                                Progress.Available(
                                    versionName = update.versionName,
                                    versionCode = update.versionCode,
                                    mandatory = update.mandatory,
                                    releaseNotes = update.releaseNotes
                                ),
                                force = true
                            )
                        }
                        return@launch
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error checking for updates", e)
                }

                delay(waitTime.minutes)
                waitTime = 45
            }
        }

        UserKnobs.updaterNewerVersionSeen.onEach { seen ->
            val currentVersionCode = currentVersionCode(context)
            val seenVersion = seen?.toLongOrNull() ?: 0L
            val consentedVersion = UserKnobs.updaterNewerVersionConsented.firstOrNull()?.toLongOrNull() ?: 0L

            if (seenVersion > currentVersionCode && consentedVersion <= currentVersionCode) {
                val update = try {
                    checkForUpdates()
                } catch (_: Throwable) {
                    null
                }

                if (update != null && update.versionCode > currentVersionCode) {
                    emitProgress(
                        Progress.Available(
                            versionName = update.versionName,
                            versionCode = update.versionCode,
                            mandatory = update.mandatory,
                            releaseNotes = update.releaseNotes
                        ),
                        force = true
                    )
                }
            }
        }.launchIn(applicationScope)

        UserKnobs.updaterNewerVersionConsented.onEach { consented ->
            val currentVersionCode = currentVersionCode(context)
            val consentedVersion = consented?.toLongOrNull() ?: 0L

            if (consentedVersion > currentVersionCode) {
                updaterScope.launch {
                    downloadAndUpdateWrapErrors()
                }
            }
        }.launchIn(applicationScope)
    }

    class AppUpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
            if (installer(context) != context.packageName) return

            val start = Intent(context, AuthGateActivity::class.java)
            start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(start)
        }
    }
}