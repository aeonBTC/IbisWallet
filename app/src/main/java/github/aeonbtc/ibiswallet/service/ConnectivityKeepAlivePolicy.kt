package github.aeonbtc.ibiswallet.service

import android.content.Context

data class ConnectivityKeepAliveSnapshot(
    val foregroundConnectivityEnabled: Boolean = false,
    val appInForeground: Boolean = true,
    val bitcoinConnected: Boolean = false,
    val bitcoinElectrumUsesTor: Boolean = false,
    val bitcoinExternalTorRequired: Boolean = false,
    val liquidConnected: Boolean = false,
    val liquidElectrumUsesTor: Boolean = false,
    val liquidExternalTorRequired: Boolean = false,
    val lightningConnected: Boolean = false,
    val lightningUsesTor: Boolean = false,
    val sparkConnected: Boolean = false,
) {
    val hasAnyElectrumConnection: Boolean
        get() = bitcoinConnected || liquidConnected

    val hasAnyLayer2Connection: Boolean
        get() = lightningConnected || sparkConnected

    val hasExternalTorRequirement: Boolean
        get() = bitcoinExternalTorRequired || liquidExternalTorRequired

    val hasAnyTorRequirement: Boolean
        get() =
            (bitcoinConnected && bitcoinElectrumUsesTor) ||
                (liquidConnected && liquidElectrumUsesTor) ||
                (lightningConnected && lightningUsesTor) ||
                hasExternalTorRequirement

    val shouldRunForegroundService: Boolean
        get() =
            !appInForeground &&
                foregroundConnectivityEnabled &&
                (
                    hasAnyElectrumConnection ||
                        hasAnyLayer2Connection ||
                        hasExternalTorRequirement
                )
}

object ConnectivityKeepAlivePolicy {
    private val lock = Any()

    private var foregroundConnectivityEnabled = false
    private var appInForeground = true
    private var bitcoinConnected = false
    private var bitcoinElectrumUsesTor = false
    private var bitcoinExternalTorRequired = false
    private var liquidConnected = false
    private var liquidElectrumUsesTor = false
    private var liquidExternalTorRequired = false
    private var lightningConnected = false
    private var lightningUsesTor = false
    private var sparkConnected = false

    fun updateForegroundConnectivityEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        synchronized(lock) {
            foregroundConnectivityEnabled = enabled
            syncForegroundServiceLocked(context)
        }
    }

    fun updateAppForegroundState(
        context: Context,
        isInForeground: Boolean,
    ) {
        synchronized(lock) {
            appInForeground = isInForeground
            syncForegroundServiceLocked(context)
        }
    }

    fun updateBitcoinState(
        context: Context,
        connected: Boolean,
        electrumUsesTor: Boolean,
        externalTorRequired: Boolean,
    ) {
        synchronized(lock) {
            bitcoinConnected = connected
            bitcoinElectrumUsesTor = electrumUsesTor
            bitcoinExternalTorRequired = externalTorRequired
            syncForegroundServiceLocked(context)
        }
    }

    fun updateLiquidState(
        context: Context,
        connected: Boolean,
        electrumUsesTor: Boolean,
        externalTorRequired: Boolean,
    ) {
        synchronized(lock) {
            liquidConnected = connected
            liquidElectrumUsesTor = electrumUsesTor
            liquidExternalTorRequired = externalTorRequired
            syncForegroundServiceLocked(context)
        }
    }

    fun updateLightningState(
        context: Context,
        connected: Boolean,
        usesTor: Boolean,
    ) {
        synchronized(lock) {
            lightningConnected = connected
            lightningUsesTor = usesTor
            syncForegroundServiceLocked(context)
        }
    }

    fun updateSparkState(
        context: Context,
        connected: Boolean,
    ) {
        synchronized(lock) {
            sparkConnected = connected
            syncForegroundServiceLocked(context)
        }
    }

    fun currentSnapshot(): ConnectivityKeepAliveSnapshot =
        synchronized(lock) {
            snapshotLocked()
        }

    /**
     * Returns true when the user has opted in to the background foreground
     * service that keeps Electrum / Lightning / Spark sockets alive. ViewModels
     * check this to decide whether to keep heartbeats / reconnect loops running
     * while the app is backgrounded.
     */
    fun isForegroundConnectivityEnabled(): Boolean =
        synchronized(lock) {
            foregroundConnectivityEnabled
        }

    fun hasAnyTorRequirement(): Boolean =
        synchronized(lock) {
            snapshotLocked().hasAnyTorRequirement
        }

    fun hasTorRequirementOutsideBitcoin(): Boolean =
        synchronized(lock) {
            (liquidConnected && liquidElectrumUsesTor) ||
                liquidExternalTorRequired ||
                (lightningConnected && lightningUsesTor)
        }

    fun hasTorRequirementOutsideLiquid(): Boolean =
        synchronized(lock) {
            (bitcoinConnected && bitcoinElectrumUsesTor) ||
                bitcoinExternalTorRequired ||
                (lightningConnected && lightningUsesTor)
        }

    private fun syncForegroundServiceLocked(context: Context) {
        val appContext = context.applicationContext
        if (snapshotLocked().shouldRunForegroundService) {
            ConnectivityForegroundService.startOrUpdate(appContext)
        } else {
            ConnectivityForegroundService.stop(appContext)
        }
    }

    private fun snapshotLocked(): ConnectivityKeepAliveSnapshot =
        ConnectivityKeepAliveSnapshot(
            foregroundConnectivityEnabled = foregroundConnectivityEnabled,
            appInForeground = appInForeground,
            bitcoinConnected = bitcoinConnected,
            bitcoinElectrumUsesTor = bitcoinElectrumUsesTor,
            bitcoinExternalTorRequired = bitcoinExternalTorRequired,
            liquidConnected = liquidConnected,
            liquidElectrumUsesTor = liquidElectrumUsesTor,
            liquidExternalTorRequired = liquidExternalTorRequired,
            lightningConnected = lightningConnected,
            lightningUsesTor = lightningUsesTor,
            sparkConnected = sparkConnected,
        )
}
