package io.github.vkardin.koly.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vkardin.koly.core.data.AppInfo
import io.github.vkardin.koly.core.data.AppUpdater
import io.github.vkardin.koly.core.data.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** The update feed: `latest.json` of the `android-latest` release. */
@Serializable
internal data class LatestRelease(
    val version: String,
    val versionCode: Int,
    val apk: String,
    val sha256: String,
    val size: Long,
    val minSdk: Int = 26,
)

private const val FEED = "https://github.com/vkardin/koly/releases/download/android-latest/latest.json"
private const val RELEASES = "https://github.com/vkardin/koly/releases/download/"
private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
private val STORES = setOf("com.android.vending", "com.google.android.feedback")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Updates an app installed from the project's GitHub releases.
 *
 * The APK is downloaded only from this project's releases, checked against the SHA-256 in the
 * feed, and installed with the system installer, which also checks that it is signed with
 * the same key as the installed app. The user confirms the install in the system dialog.
 */
@Singleton
class ReleaseUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val appInfo: AppInfo,
) : AppUpdater {
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var latest: LatestRelease? = null

    override val supported: Boolean by lazy { installer() !in STORES }

    private fun installer(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (_: Exception) {
        null
    }

    override suspend fun check(force: Boolean) {
        if (!supported) return
        val busy = _state.value
        if (busy is UpdateState.Checking || busy is UpdateState.Downloading || busy is UpdateState.Installing) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked_at", 0) < CHECK_EVERY_MS) {
            // Recently checked: show what was found then, without the network.
            prefs.getString("feed", null)?.let { cached -> runCatching { offer(json.decodeFromString<LatestRelease>(cached)) } }
            return
        }
        _state.value = UpdateState.Checking
        try {
            val body = withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(FEED).header("Accept", "application/json").build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    r.body.string()
                }
            }
            val release = json.decodeFromString<LatestRelease>(body)
            prefs.edit().putLong("checked_at", now).putString("feed", body).apply()
            offer(release)
        } catch (e: Exception) {
            _state.value = if (force) UpdateState.Failed(e.message ?: e.javaClass.simpleName) else UpdateState.Idle
        }
    }

    private fun offer(release: LatestRelease) {
        val valid = release.apk.startsWith(RELEASES) && release.sha256.matches(Regex("[0-9a-f]{64}"))
        if (valid && release.versionCode > appInfo.versionCode && Build.VERSION.SDK_INT >= release.minSdk) {
            latest = release
            _state.value = UpdateState.Available(release.version, release.size)
        } else {
            latest = null
            _state.value = UpdateState.UpToDate
        }
    }

    override suspend fun install() {
        val release = latest ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release.version)
            return
        }
        try {
            val apk = download(release)
            _state.value = UpdateState.Installing(release.version)
            withContext(Dispatchers.IO) { commit(apk) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun download(release: LatestRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != "koly-${release.version}.apk" }?.forEach { it.delete() }
        val file = File(dir, "koly-${release.version}.apk")
        if (file.exists() && sha256(file) == release.sha256) return@withContext file
        _state.value = UpdateState.Downloading(release.version, 0f)
        client.newCall(Request.Builder().url(release.apk).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 } ?: release.size
            val tmp = File(dir, file.name + ".part")
            r.body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = (done * 100 / total.coerceAtLeast(1)).toInt()
                        if (pct != shown) {
                            shown = pct
                            _state.value = UpdateState.Downloading(release.version, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (sha256(tmp) != release.sha256) {
                tmp.delete()
                throw IOException("checksum mismatch")
            }
            tmp.renameTo(file)
        }
        file
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("koly.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    /** Called by [InstallReceiver] when the system installer reports back. */
    internal fun onInstallResult(success: Boolean, message: String?) {
        _state.value = if (success) UpdateState.UpToDate else UpdateState.Failed(message ?: "install failed")
    }

    override fun postpone(version: String) {
        prefs.edit().putString("postponed", version).putLong("postponed_at", System.currentTimeMillis()).apply()
    }

    override fun isPostponed(version: String): Boolean =
        prefs.getString("postponed", null) == version &&
            System.currentTimeMillis() - prefs.getLong("postponed_at", 0) < 3 * 24 * 60 * 60 * 1000L

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
