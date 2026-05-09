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
    ) {
        if (!canPostNotifications(context)) return

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

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID_ACTIVITY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        if (snapshot.hasAnyTorRequirement) {
            statusParts += context.getString(R.string.foreground_connectivity_status_tor)
        }

        val body = statusParts.distinct().joinToString(", ")

        return NotificationCompat.Builder(context, CHANNEL_ID_CONNECTIVITY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.foreground_connectivity_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
