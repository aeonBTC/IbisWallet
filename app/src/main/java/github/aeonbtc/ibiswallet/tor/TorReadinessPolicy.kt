package github.aeonbtc.ibiswallet.tor

/**
 * Pure readiness decisions for a running Tor daemon.
 *
 * A listening SOCKS port is NOT sufficient to trust a Tor "ready" state. After
 * the underlying network changes (VPN toggle / route change) the daemon keeps
 * the SOCKS listener open while every circuit is dead, so a stale CONNECTED
 * would make each connection through the proxy hang until it times out. The
 * transport only counts as ready when SOCKS accepts connections AND Tor reports
 * an established circuit or a finished bootstrap.
 */
internal object TorReadinessPolicy {
    fun isTransportReady(
        socksReachable: Boolean,
        circuitEstablished: Boolean,
        bootstrapDone: Boolean,
    ): Boolean = socksReachable && (circuitEstablished || bootstrapDone)
}
