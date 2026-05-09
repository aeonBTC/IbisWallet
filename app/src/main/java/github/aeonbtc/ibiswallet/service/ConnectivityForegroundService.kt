package github.aeonbtc.ibiswallet.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import github.aeonbtc.ibiswallet.util.WalletNotificationHelper

class ConnectivityForegroundService : Service() {
    companion object {
        private const val ACTION_REFRESH = "github.aeonbtc.ibiswallet.action.REFRESH_CONNECTIVITY_SERVICE"

        fun startOrUpdate(context: Context) {
            val intent =
                Intent(context, ConnectivityForegroundService::class.java).apply {
                    action = ACTION_REFRESH
                }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectivityForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WalletNotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val snapshot = ConnectivityKeepAlivePolicy.currentSnapshot()
        // A queued startForegroundService() call can arrive after the policy has already
        // flipped back to "stop". Promote first, then stop if the latest snapshot no longer
        // requires the service, so Android never sees a foreground-start request without a
        // matching startForeground() call.
        startForegroundWithSnapshot(snapshot)

        if (intent?.action != ACTION_REFRESH || !snapshot.shouldRunForegroundService) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundWithSnapshot(snapshot: ConnectivityKeepAliveSnapshot) {
        val notification =
            WalletNotificationHelper.buildConnectivityForegroundNotification(
                context = this,
                snapshot = snapshot,
            )
        startForeground(WalletNotificationHelper.CONNECTIVITY_NOTIFICATION_ID, notification)
    }
}
