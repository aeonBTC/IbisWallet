package github.aeonbtc.ibiswallet.util

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * System DNS with IPv4 addresses ordered first.
 *
 * Many Android networks advertise broken/idle IPv6 routes. OkHttp may pick the first
 * AAAA record and spend the full connect timeout there (often 30s) before trying A.
 * Preferring IPv4 keeps clearnet calls (mempool.space fees/price, Esplora preflight)
 * responsive while still allowing IPv6-only hosts as a fallback list.
 */
object PreferIpv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.size <= 1) return addresses
        val v4 = ArrayList<InetAddress>(addresses.size)
        val v6 = ArrayList<InetAddress>(addresses.size)
        for (address in addresses) {
            when (address) {
                is Inet4Address -> v4.add(address)
                is Inet6Address -> v6.add(address)
                else -> v4.add(address)
            }
        }
        if (v4.isEmpty() || v6.isEmpty()) return addresses
        return v4 + v6
    }
}

/** Shared clearnet Tor SOCKS "fake DNS" that forces hostname resolution at the proxy. */
object SocksProxyHostnameDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
}
