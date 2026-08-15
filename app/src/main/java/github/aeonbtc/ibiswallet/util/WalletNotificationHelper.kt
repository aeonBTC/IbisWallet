package github.aeonbtc.ibiswallet.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.service.ConnectivityKeepAliveSnapshot

object WalletNotificationHelper {
    private fun isCloaked(context: Context): Boolean =
        runCatching {
            github.aeonbtc.ibiswallet.data.local.SecureStorage.getInstance(context).isCloakModeEnabled()
        }.getOrDefault(false)

    private const val CHANNEL_ID_ACTIVITY = "wallet_activity"
    private const val CHANNEL_ID_CONNECTIVITY = "foreground_connectivity"
    const val CONNECTIVITY_NOTIFICATION_ID = 42001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID_ACTIVITY,
                "Activity Alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "App activity alerts"
            }
        manager.createNotificationChannel(channel)
        val connectivityChannel =
            NotificationChannel(
                CHANNEL_ID_CONNECTIVITY,
                context.getString(R.string.foreground_connectivity_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.foreground_connectivity_channel_description)
            }
        manager.createNotificationChannel(connectivityChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun areNotificationsEnabledInSystem(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canPostNotifications(context: Context): Boolean {
        return hasNotificationPermission(context) && areNotificationsEnabledInSystem(context)
    }

    fun notifyWalletActivity(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        contentLocked: Boolean = false,
    ) {
        if (!canPostNotifications(context)) return
        if (isCloaked(context)) return

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val lockedTitle = context.getString(R.string.wallet_notification_locked_title)
        val lockedBody = context.getString(R.string.wallet_notification_locked_body)
        val displayTitle = if (contentLocked) lockedTitle else title
        val displayBody = if (contentLocked) lockedBody else body
        val publicVersion =
            NotificationCompat.Builder(context, CHANNEL_ID_ACTIVITY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(lockedTitle)
                .setContentText(lockedBody)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID_ACTIVITY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(displayTitle)
                .setContentText(displayBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Always hide sensitive body on lockscreen / sensitive surfaces
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.getOrElse { throwable ->
            if (throwable !is SecurityException) {
                throw throwable
            }
        }
    }

    fun buildConnectivityForegroundNotification(
        context: Context,
        snapshot: ConnectivityKeepAliveSnapshot,
        contentLocked: Boolean = false,
    ): Notification {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                CONNECTIVITY_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val statusParts = mutableListOf<String>()
        if (snapshot.bitcoinConnected) {
            statusParts += context.getString(R.string.foreground_connectivity_status_bitcoin)
        }
        if (snapshot.liquidConnected) {
            statusParts += context.getString(R.string.foreground_connectivity_status_liquid)
        }
        if (snapshot.lightningConnected) {
            statusParts += context.getString(R.string.foreground_connectivity_status_lightning)
        }
        if (snapshot.sparkConnected) {
            statusParts += context.getString(R.string.foreground_connectivity_status_spark)
        }
        if (snapshot.arkConnected) {
            statusParts += context.getString(R.string.foreground_connectivity_status_ark)
        }
        if (snapshot.hasAnyTorRequirement) {
            statusParts += context.getString(R.string.foreground_connectivity_status_tor)
        }

        val cloaked = isCloaked(context)
        val icon = if (cloaked) R.mipmap.ic_launcher_calculator else R.mipmap.ic_launcher
        val lockedTitle =
            if (cloaked) {
                context.getString(R.string.cloak_calculator_label)
            } else {
                context.getString(R.string.wallet_notification_locked_title)
            }
        val lockedBody =
            if (cloaked) {
                context.getString(R.string.cloak_calculator_notification_body)
            } else {
                context.getString(R.string.wallet_notification_locked_body)
            }
        val body =
            if (cloaked || contentLocked) {
                lockedBody
            } else {
                statusParts.distinct().joinToString(", ")
            }
        val title =
            if (cloaked || contentLocked) {
                lockedTitle
            } else {
                context.getString(R.string.foreground_connectivity_notification_title)
            }
        val publicVersion =
            NotificationCompat.Builder(context, CHANNEL_ID_CONNECTIVITY)
                .setSmallIcon(icon)
                .setContentTitle(lockedTitle)
                .setContentText(lockedBody)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        return NotificationCompat.Builder(context, CHANNEL_ID_CONNECTIVITY)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
    }
}
