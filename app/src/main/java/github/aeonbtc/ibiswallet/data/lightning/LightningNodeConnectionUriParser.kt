package github.aeonbtc.ibiswallet.data.lightning

import android.util.Base64
import github.aeonbtc.ibiswallet.data.model.LightningNodeConfig
import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses connect-QR payloads used by remote Lightning node wallets.
 *
 * - LND: `lndconnect://host:port?macaroon=<b64url>&cert=<b64url DER>`
 * - CLN: `clnrest+https://host:port?rune=...&certs=...`, legacy `clnrest://...`,
 *   or Zeus/Umbrel variants
 * - NWC: `nostr+walletconnect://...`
 * - Optional JSON envelopes and percent/base64 wrapping (dense connection QRs)
 */
object LightningNodeConnectionUriParser {
    data class ParseResult(
        val config: LightningNodeConfig,
        val type: LightningNodeConnectionType,
    )

    fun parse(raw: String): ParseResult {
        val seed = raw.trim().trim('\uFEFF')
        require(seed.isNotBlank()) { "Empty QR payload" }
        // Common mis-scan: TLS cert field QR alone (Zeus / Umbrel "REST TLS Certs")
        val looksLikeCertOnly =
            seed.contains("BEGIN CERTIFICATE") ||
                seed.contains("BEGIN PRIVATE KEY") ||
                seed.contains("client_key:") ||
                seed.contains("client_cert:") ||
                seed.contains("ca_cert:") ||
                seed.startsWith("Y2xpZW50") || // base64("client")
                (
                    !seed.contains("://") &&
                        !seed.contains("clnrest", ignoreCase = true) &&
                        !seed.contains("lndconnect", ignoreCase = true) &&
                        !seed.contains("nostr", ignoreCase = true) &&
                        !seed.contains("rune=", ignoreCase = true) &&
                        !seed.contains("macaroon=", ignoreCase = true) &&
                        seed.length > 80 &&
                        seed.replace("\\s".toRegex(), "").matches(Regex("^[A-Za-z0-9+/=_\\-]+$"))
                )
        if (looksLikeCertOnly) {
            throw IllegalArgumentException(
                "This looks like TLS cert data — scan the Connection URL QR (clnrest+/lndconnect), not the certs field",
            )
        }

        val candidates = expandCandidates(seed)
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                return parseNormalized(candidate)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError
            ?: IllegalArgumentException(
                "Unsupported connection QR — expect clnrest+, lndconnect, or nostr+walletconnect",
            )
    }

    private fun expandCandidates(raw: String): List<String> {
        val seed = raw.trim().trim('\uFEFF')
        if (seed.isBlank()) return emptyList()
        val out = linkedSetOf<String>()
        fun add(value: String?) {
            val v = value?.trim()?.takeIf { it.isNotBlank() } ?: return
            out += v
            // Strip surrounding quotes
            if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith('\'') && v.endsWith('\''))) {
                out += v.substring(1, v.lastIndex).trim()
            }
        }
        add(seed)
        add(seed.replace("\\s+".toRegex(), ""))
        // HTML / copy-paste entities often found after export from web dashboards
        add(unescapeHtmlEntities(seed))
        // Full percent-decode pass (Umbrel sometimes encodes the whole URL once)
        add(percentDecodeLenient(seed))
        add(percentDecodeLenient(seed.replace("\\s+".toRegex(), "")))
        // Strip common app schemes / wrappers
        add(seed.removePrefix("zeusln:").removePrefix("ZEUSLN:"))
        add(seed.removePrefix("ibis:").removePrefix("IBIS:"))
        add(seed.removePrefix("lightning:").removePrefix("LIGHTNING:"))
        // Drop fragment junk
        if (seed.contains('#')) {
            add(seed.substringBefore('#'))
        }
        // Base64 of UTF-8 connection string (dense single-frame QRs)
        decodeUtf8Base64(seed)?.let { decoded ->
            add(decoded)
            add(percentDecodeLenient(decoded))
            add(unescapeHtmlEntities(decoded))
        }
        // JSON node / config envelopes used by some dashboards
        parseJsonEnvelope(seed)?.forEach { add(it) }
        // Schema spacing / case quirks
        add(seed.replace("clnrest+ ", "clnrest+", ignoreCase = true))
        add(seed.replace("clnrest +", "clnrest+", ignoreCase = true))
        add(seed.replace("CLNREST+", "clnrest+", ignoreCase = true))
        // Some QRs omit `+https` and for ee only path: clnrest://host:port?...
        if (seed.contains("clnrest", ignoreCase = true) && !seed.contains("clnrest+", ignoreCase = true) &&
            !seed.contains("clnrest://", ignoreCase = true)
        ) {
            // rare: "clnrest host:port?..."
            add(seed.replaceFirst(Regex("(?i)clnrest\\s+"), "clnrest://"))
        }
        return out.toList()
    }

    private fun parseJsonEnvelope(raw: String): List<String>? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        return runCatching {
            val json = JSONObject(trimmed)
            buildList {
                // Direct fields → synthetic clnrest / lndconnect URI
                val host =
                    json.optString("host")
                        .ifBlank { json.optString("Hostname") }
                        .ifBlank { json.optString("url") }
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore('/')
                val port =
                    json.optString("port").ifBlank {
                        json.optInt("port", 0).takeIf { it > 0 }?.toString().orEmpty()
                    }
                val rune =
                    json.optString("rune")
                        .ifBlank { json.optString("Rune") }
                        .ifBlank { json.optString("adminRune") }
                val macaroon =
                    json.optString("macaroon")
                        .ifBlank { json.optString("macaroonHex") }
                        .ifBlank { json.optString("adminMacaroon") }
                val certs =
                    json.optString("certs")
                        .ifBlank { json.optString("cert") }
                        .ifBlank { json.optString("tlsCert") }
                        .ifBlank { json.optString("tls") }
                val protocol =
                    json.optString("protocol").ifBlank {
                        if (json.optBoolean("useTls", true)) "https" else "http"
                    }
                val nwc =
                    json.optString("nostrWalletConnectUrl")
                        .ifBlank { json.optString("nwcUri") }
                        .ifBlank { json.optString("nwc") }
                val connectionUrl =
                    json.optString("connectionUrl")
                        .ifBlank { json.optString("connection_url") }
                        .ifBlank { json.optString("uri") }
                        .ifBlank { json.optString("url") }
                        .ifBlank { json.optString("lndconnect") }
                        .ifBlank { json.optString("clnrest") }

                if (connectionUrl.isNotBlank()) {
                    add(connectionUrl)
                }
                if (nwc.isNotBlank()) {
                    add(nwc)
                }
                if (host.isNotBlank() && rune.isNotBlank()) {
                    val p = port.ifBlank { LightningNodeConfig.DEFAULT_CLN_REST_PORT.toString() }
                    val certQ =
                        if (certs.isNotBlank()) {
                            "&certs=${percentEncodeComponent(certs)}"
                        } else {
                            ""
                        }
                    add("clnrest+$protocol://$host:$p?rune=${percentEncodeComponent(rune)}$certQ")
                }
                if (host.isNotBlank() && macaroon.isNotBlank()) {
                    val p = port.ifBlank { LightningNodeConfig.DEFAULT_LND_REST_PORT.toString() }
                    val certQ =
                        if (certs.isNotBlank()) {
                            "&cert=${percentEncodeComponent(certs)}"
                        } else {
                            ""
                        }
                    add(
                        "lndconnect://$host:$p?macaroon=${percentEncodeComponent(macaroon)}$certQ",
                    )
                }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun parseNormalized(input: String): ParseResult {
        val cleaned = unescapeHtmlEntities(input.trim())
        val lower = cleaned.lowercase()
        return when {
            lower.startsWith("nostr+walletconnect://") ||
                lower.startsWith("nostrwalletconnect://") ||
                lower.contains("nostr+walletconnect://") ||
                lower.contains("nostrwalletconnect://") -> parseNwc(cleaned)
            lower.contains("clnrest+") ||
                lower.contains("clnrest://") ||
                lower.contains("c-lightning-rest") ||
                // Umbrel-style: host:port?rune=... without scheme
                (
                    lower.contains("rune=") &&
                        !lower.contains("macaroon=") &&
                        (lower.contains("://") || hostLikeAuthorityPresent(cleaned))
                ) ->
                parseClnrest(cleaned)
            lower.contains("lndconnect://") ||
                lower.contains("lndconnect:") ||
                (
                    lower.contains("macaroon=") &&
                        (lower.contains("://") || hostLikeAuthorityPresent(cleaned))
                ) ->
                parseLndconnect(cleaned)
            lower.contains("relay=") && lower.contains("secret=") && lower.contains("://") ->
                parseNwc(cleaned)
            else ->
                throw IllegalArgumentException(
                    "Unsupported connection QR — expect clnrest+, lndconnect, or nostr+walletconnect",
                )
        }
    }

    /** True when payload looks like host:port?... even without a scheme. */
    private fun hostLikeAuthorityPresent(input: String): Boolean {
        val authority = input.substringBefore('?').substringAfter("://", missingDelimiterValue = input)
        val clean =
            authority
                .removePrefix("https://")
                .removePrefix("http://")
                .trim()
        if (clean.isBlank()) return false
        if (clean.contains(".onion", ignoreCase = true)) return true
        // domain/host:port
        val lastColon = clean.lastIndexOf(':')
        if (lastColon > 0) {
            val port = clean.substring(lastColon + 1).toIntOrNull()
            if (port != null && port in 1..65535) return true
        }
        return clean.contains('.')
    }

    private fun parseNwc(input: String): ParseResult {
        // Normalize scheme without case-sensitive substringAfter wipe bugs.
        val uri = NwcUriParser.normalize(input)
        NwcUriParser.parse(uri)
        return ParseResult(
            config =
                LightningNodeConfig(
                    type = LightningNodeConnectionType.NWC,
                    nwcUri = uri,
                    useTor = uri.contains(".onion", ignoreCase = true),
                ),
            type = LightningNodeConnectionType.NWC,
        )
    }

    private fun parseLndconnect(input: String): ParseResult {
        val afterScheme =
            when {
                input.contains("lndconnect://", ignoreCase = true) ->
                    input.substringAfter("lndconnect://").substringAfter("LNDCONNECT://")
                input.contains("lndconnect:", ignoreCase = true) ->
                    input
                        .substringAfter("lndconnect:")
                        .substringAfter("LNDCONNECT:")
                        .removePrefix("//")
                else ->
                    // Bare host the edge case from JSON (host + macaroon query already)
                    input.removePrefix("https://").removePrefix("http://")
            }.trim()
        require(afterScheme.isNotBlank()) { "Invalid lndconnect URI" }

        val queryStart = afterScheme.indexOf('?')
        val authority =
            if (queryStart >= 0) {
                afterScheme.substring(0, queryStart)
            } else {
                afterScheme
            }
        val query =
            if (queryStart >= 0) {
                afterScheme.substring(queryStart + 1)
            } else {
                ""
            }
        val params = parseQuery(query)

        val (host, port) = parseHostPort(authority)
        require(host.isNotBlank()) { "lndconnect host missing" }

        val macaroonRaw =
            firstParam(
                params,
                "macaroon",
                "macaroonHex",
                "admin_macaroon",
                "adminMacaroon",
            ) ?: throw IllegalArgumentException("lndconnect macaroon missing")
        val macaroon =
            if (macaroonRaw.matches(Regex("^[0-9a-fA-F]+$")) && macaroonRaw.length % 2 == 0) {
                macaroonRaw.lowercase()
            } else {
                decodeBase64UrlToHex(macaroonRaw)
            }
        val certRaw = firstParam(params, "cert", "certificate", "tls", "tlscert")
        val certPem = certRaw?.let { normalizeLndCert(it) }.orEmpty()
        val onion = host.endsWith(".onion", ignoreCase = true)
        val useTls = certPem.isNotBlank() || !params.containsKey("disabletls")
        return ParseResult(
            config =
                LightningNodeConfig(
                    type = LightningNodeConnectionType.LND_REST,
                    host = host,
                    port = port ?: LightningNodeConfig.DEFAULT_LND_REST_PORT,
                    useTor = onion,
                    macaroonHex = macaroon,
                    tlsCertPem = certPem,
                    useTls = useTls || onion,
                    allowInsecureTls = !useTls && !onion,
                ),
            type = LightningNodeConnectionType.LND_REST,
        )
    }

    private fun parseClnrest(input: String): ParseResult {
        var protocol = "https"
        var body: String
        val plusMatch = Regex("clnrest\\+\\s*(\\w+)\\s*://", RegexOption.IGNORE_CASE).find(input)
        when {
            plusMatch != null -> {
                protocol = plusMatch.groupValues[1].lowercase()
                body = input.substring(plusMatch.range.last + 1)
            }
            input.contains("clnrest://", ignoreCase = true) -> {
                body =
                    input
                        .substringAfter("clnrest://", "")
                        .substringAfter("CLNREST://", "")
                if (body.startsWith("http://", ignoreCase = true)) {
                    protocol = "http"
                    body = body.removePrefix("http://").removePrefix("HTTP://")
                } else if (body.startsWith("https://", ignoreCase = true)) {
                    protocol = "https"
                    body = body.removePrefix("https://").removePrefix("HTTPS://")
                }
            }
            // host:port?rune= without scheme (from JSON expand leftovers)
            else -> {
                body =
                    input
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("HTTPS://")
                        .removePrefix("HTTP://")
                protocol =
                    when {
                        input.startsWith("http://", ignoreCase = true) -> "http"
                        else -> "https"
                    }
            }
        }
        body = body.trim()
        require(body.isNotBlank()) { "Invalid clnrest URI" }

        // Prefer last '?' so URLs that embed encoded ? in earlier sections still work; stock CLN
        // only has one query delimiter after host:port.
        val queryStart = body.indexOf('?')
        val authority = if (queryStart >= 0) body.substring(0, queryStart) else body
        val query = if (queryStart >= 0) body.substring(queryStart + 1) else ""
        val params = parseQuery(query)

        val (host, port) = parseHostPort(authority)
        require(host.isNotBlank()) { "clnrest host missing" }
        val rune =
            firstParam(params, "rune", "Rune", "adminRune", "admin_rune", "token")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "clnrest rune missing — use the Connection URL QR that includes ?rune=…",
                )
        // Zeus packs client_key + client_cert + ca_cert (often space-separated labels) as base64.
        // Prefer explicit multi-part fields if present; fall back to single certs blob.
        val certsRaw =
            firstParam(
                params,
                "certs",
                "cert",
                "tls",
                "certificate",
                "tlsCert",
                "tls_cert",
                "restCerts",
            ).orEmpty().trim()
        val tlsMaterial =
            when {
                certsRaw.isBlank() -> {
                    // Some exporters put caOnly as "cacert" / "ca"
                    firstParam(params, "cacert", "ca_cert", "ca").orEmpty().trim()
                }
                else -> certsRaw // leave Zeus base64 or PEM for TlsCertMaterial
            }
        val onion = host.endsWith(".onion", ignoreCase = true)
        val useTls = protocol != "http"
        return ParseResult(
            config =
                LightningNodeConfig(
                    type = LightningNodeConnectionType.CLN_REST,
                    host = host,
                    port = port ?: LightningNodeConfig.DEFAULT_CLN_REST_PORT,
                    useTor = onion,
                    clnRune = rune,
                    tlsCertPem = tlsMaterial,
                    useTls = useTls || onion,
                    allowInsecureTls = !useTls && !onion,
                ),
            type = LightningNodeConnectionType.CLN_REST,
        )
    }

    private fun parseHostPort(authority: String): Pair<String, Int?> {
        var auth =
            authority
                .trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("HTTPS://")
                .removePrefix("HTTP://")
                .trimEnd('/')
        if (auth.isBlank()) return "" to null

        // IPv6 [host]:port
        if (auth.startsWith("[")) {
            val end = auth.indexOf(']')
            if (end > 0) {
                val host = auth.substring(1, end)
                val rest = auth.substring(end + 1)
                val port =
                    if (rest.startsWith(":")) {
                        rest.removePrefix(":").substringBefore('/').toIntOrNull()
                    } else {
                        null
                    }
                return host to port
            }
        }

        // host:port — onion/host may contain no other colons
        val colon = auth.lastIndexOf(':')
        if (colon > 0) {
            val maybePort = auth.substring(colon + 1).substringBefore('/').toIntOrNull()
            if (maybePort != null && maybePort in 1..65535) {
                return auth.substring(0, colon) to maybePort
            }
        }
        return auth.substringBefore('/') to null
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = linkedMapOf<String, String>()
        // Mutex-free split on bare & only (certs base64 may not include & after encode)
        query.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val eq = part.indexOf('=')
            if (eq < 0) {
                map[decodeQueryComponent(part)] = ""
            } else {
                val key = decodeQueryComponent(part.substring(0, eq))
                // rest after first = — preserves = padding in macaroons/runes/base64
                val value = decodeQueryComponent(part.substring(eq + 1))
                if (key.isNotBlank()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun firstParam(
        params: Map<String, String>,
        vararg keys: String,
    ): String? {
        for (key in keys) {
            params[key]?.takeIf { it.isNotBlank() }?.let { return it }
            params.entries
                .firstOrNull { it.key.equals(key, ignoreCase = true) && it.value.isNotBlank() }
                ?.value
                ?.let { return it }
        }
        return null
    }

    /**
     * Decode judiciously: keep `+` as `+` (base64 / runes) rather than space.
     * Also accept already-decoded values with raw spaces (Zeus labeled PEM bundles).
     */
    private fun decodeQueryComponent(value: String): String {
        if (value.isEmpty()) return value
        // Already looks like Zeus cert label or PEM — leave alone (may still percent-decode
        // percent sequences if present).
        val plusSafe = value.replace("+", "%2B")
        return runCatching {
            URLDecoder.decode(plusSafe, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun unescapeHtmlEntities(value: String): String {
        // Keep ampersand entities literal in source via unicode escapes so tooling
        // doesn't normalize them away while editing.
        val amp = "\u0026"
        return value
            .replace("${amp}amp;", amp, ignoreCase = true)
            .replace("${amp}#38;", amp, ignoreCase = true)
            .replace("${amp}#x26;", amp, ignoreCase = true)
    }

    private fun percentDecodeLenient(value: String): String? {
        if (!value.contains('%')) return null
        return runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    private fun percentEncodeComponent(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decodeUtf8Base64(value: String): String? {
        val compact = value.replace("\\s".toRegex(), "")
        if (compact.length < 32 || !compact.matches(Regex("^[A-Za-z0-9+/=_\\-]+$"))) return null
        val bytes =
            runCatching { decodeBase64Url(compact) }.getOrNull() ?: return null
        val text = bytes.toString(Charsets.UTF_8)
        return text.takeIf {
            it.contains("clnrest", ignoreCase = true) ||
                it.contains("lndconnect", ignoreCase = true) ||
                it.contains("nostr+walletconnect", ignoreCase = true) ||
                it.contains("rune=", ignoreCase = true) ||
                it.contains("macaroon=", ignoreCase = true) ||
                (it.startsWith("{") && it.contains("host"))
        }
    }

    private fun normalizeLndCert(value: String): String {
        val trimmed = value.trim()
        if (trimmed.contains("BEGIN CERTIFICATE")) return trimmed
        // lndconnect cert is base64url of DER
        return runCatching { base64UrlDerToPem(trimmed) }.getOrDefault(trimmed)
    }

    private fun decodeBase64UrlToHex(value: String): String {
        val bytes = decodeBase64Url(value)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    private fun base64UrlDerToPem(value: String): String {
        val der = decodeBase64Url(value)
        val standard = Base64.encodeToString(der, Base64.NO_WRAP)
        val lines = standard.chunked(64).joinToString("\n")
        return "-----BEGIN CERTIFICATE-----\n$lines\n-----END CERTIFICATE-----"
    }

    private fun decodeBase64Url(value: String): ByteArray {
        val trimmed = value.trim().replace("\\s".toRegex(), "")
        val padded =
            buildString {
                append(trimmed.replace('-', '+').replace('_', '/'))
                while (length % 4 != 0) append('=')
            }
        return runCatching {
            Base64.decode(padded, Base64.DEFAULT)
        }.recoverCatching {
            Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        }.recoverCatching {
            java.util.Base64.getUrlDecoder().decode(trimmed)
        }.getOrElse {
            throw IllegalArgumentException("Invalid base64url payload")
        }
    }
}
