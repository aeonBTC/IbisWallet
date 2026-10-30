package github.aeonbtc.ibiswallet.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

/**
 * Builds the package-installer intents for an APK downloaded in-app and verifies
 * the download before it is handed to the installer.
 *
 * Trust for the download itself is TLS + the GitHub-host allowlist; the app is
 * not checksum-verified against a published artifact. The signature check below
 * exists so a substituted APK cannot be offered as a "fresh install" of a
 * different app on the same intent.
 */
object AppUpdateInstaller {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val SHA256 = "SHA-256"

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun canRequestInstalls(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), apkFile)
        val installBase =
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(uri)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Prefer the platform installer explicitly. An implicit ACTION_VIEW
        // APK intent can be received by any app registering for the MIME type.
        val installers =
            listOf("com.google.android.packageinstaller", "com.android.packageinstaller")
        val installer =
            installers.firstOrNull { pkg ->
                context.packageManager.resolveActivity(
                    Intent(installBase).setPackage(pkg),
                    0,
                ) != null
            }
        return if (installer != null) {
            installBase.setPackage(installer)
        } else {
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * True only when [apkFile] parses as this app's package, is signed by a
     * certificate that also signs the installed app, and is a strictly newer
     * build. Fails closed on any parse error, unknown signer, package mismatch,
     * or non-newer version.
     */
    fun verifyDownloadedApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.isFile || apkFile.length() <= 0L) return false
        val packageManager = context.packageManager
        val archive =
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                signatureFlags(),
            ) ?: return false
        if (archive.packageName != context.packageName) return false

        val archiveSigners = certificateDigests(archive)
        if (archiveSigners.isEmpty()) return false

        val installed =
            runCatching {
                packageManager.getPackageInfo(
                    context.packageName,
                    signatureFlags(),
                )
            }.getOrNull() ?: return false

        val installedSigners = certificateDigests(installed)
        if (installedSigners.none { it in archiveSigners }) return false

        return versionCode(archive) > versionCode(installed)
    }

    // GET_SIGNING_CERTIFICATES only exists on API 28+; API 26-27 need the legacy GET_SIGNATURES flag.
    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun certificateDigests(packageInfo: PackageInfo): Set<String> {
        val legacySignatures: Array<Signature>? = packageInfo.signatures
        val signatures: Array<out Signature> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                when {
                    signingInfo == null -> legacySignatures ?: emptyArray()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners ?: emptyArray()
                    else -> signingInfo.signingCertificateHistory ?: emptyArray()
                }
            } else {
                legacySignatures ?: emptyArray()
            }
        return signatures.mapNotNull { signature ->
            runCatching {
                MessageDigest.getInstance(SHA256)
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.getOrNull()
        }.toSet()
    }
}
