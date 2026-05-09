package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType
import org.json.JSONObject

enum class Bip329LabelScope {
    BITCOIN,
    LIQUID,
    SPARK,
    BOTH,
}

enum class Bip329LabelNetwork(val wireValue: String) {
    BITCOIN("bitcoin"),
    LIQUID("liquid"),
    SPARK("spark"), ;

    companion object {
        fun fromWireValue(value: String?): Bip329LabelNetwork? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}

data class Bip329LabelCounts(
    val bitcoinAddressCount: Int = 0,
    val bitcoinTransactionCount: Int = 0,
    val liquidAddressCount: Int = 0,
    val liquidTransactionCount: Int = 0,
    val sparkAddressCount: Int = 0,
    val sparkTransactionCount: Int = 0,
) {
    fun addressCount(scope: Bip329LabelScope): Int =
        when (scope) {
            Bip329LabelScope.BITCOIN -> bitcoinAddressCount
            Bip329LabelScope.LIQUID -> liquidAddressCount
            Bip329LabelScope.SPARK -> sparkAddressCount
            Bip329LabelScope.BOTH -> bitcoinAddressCount + liquidAddressCount + sparkAddressCount
        }

    fun transactionCount(scope: Bip329LabelScope): Int =
        when (scope) {
            Bip329LabelScope.BITCOIN -> bitcoinTransactionCount
            Bip329LabelScope.LIQUID -> liquidTransactionCount
            Bip329LabelScope.SPARK -> sparkTransactionCount
            Bip329LabelScope.BOTH -> bitcoinTransactionCount + liquidTransactionCount + sparkTransactionCount
        }

    fun totalCount(scope: Bip329LabelScope): Int = addressCount(scope) + transactionCount(scope)
}

/**
 * BIP 329 Wallet Labels Export Format.
 * See https://github.com/bitcoin/bips/blob/master/bip-0329.mediawiki
 *
 * Supports:
 * - Export/Import of JSONL (newline-delimited JSON) label files
 * - Import of Electrum CSV history files (txid,label,...)
 *
 * Record types: tx, addr, pubkey, input, output, xpub
 * Ibis stores tx and addr labels; other types are parsed but only tx/addr are persisted.
 */
object Bip329Labels {
    data class ExportSection(
        val addressLabels: Map<String, String>,
        val transactionLabels: Map<String, String>,
        val origin: String? = null,
        val network: Bip329LabelNetwork? = null,
    )

    /** BIP 329 record types */
    enum class Type(val wireValue: String) {
        TX("tx"),
        ADDR("addr"),
        PUBKEY("pubkey"),
        INPUT("input"),
        OUTPUT("output"),
        XPUB("xpub"), ;

        companion object {
            fun fromWireValue(value: String?): Type? =
                entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
        }
    }

    /**
     * Build the BIP 329 `origin` field for a wallet.
     * Returns an abbreviated output descriptor with key origin but no actual keys.
     * Example: "wpkh([d34db33f/84'/0'/0'])"
     */
    fun buildOrigin(
        addressType: AddressType,
        fingerprint: String?,
    ): String? {
        if (fingerprint.isNullOrBlank() || fingerprint.length != 8) return null
        val fp = fingerprint.lowercase()
        val path = addressType.accountPath
        return when (addressType) {
            AddressType.LEGACY -> "pkh([$fp/$path])"
            AddressType.SEGWIT -> "wpkh([$fp/$path])"
            AddressType.TAPROOT -> "tr([$fp/$path])"
        }
    }

    fun export(
        addressLabels: Map<String, String>,
        transactionLabels: Map<String, String>,
        origin: String? = null,
        network: Bip329LabelNetwork? = null,
    ): String {
        return export(
            listOf(
                ExportSection(
                    addressLabels = addressLabels,
                    transactionLabels = transactionLabels,
                    origin = origin,
                    network = network,
                ),
            ),
        )
    }

    /**
     * Export one or more label sections to BIP 329 JSONL format.
     * When `network` is present it is emitted as an extra field for internal
     * round-trip routing between Bitcoin and Liquid namespaces.
     */
    fun export(sections: List<ExportSection>): String {
        val lines = mutableListOf<String>()

        for (section in sections) {
            for ((txid, label) in section.transactionLabels) {
                if (label.isBlank()) continue
                val json = JSONObject().apply {
                    put("type", Type.TX.wireValue)
                    put("ref", txid)
                    put("label", label)
                    if (section.origin != null) put("origin", section.origin)
                    if (section.network != null) put("network", section.network.wireValue)
                }
                lines.add(json.toString())
            }

            for ((address, label) in section.addressLabels) {
                if (label.isBlank()) continue
                val json = JSONObject().apply {
                    put("type", Type.ADDR.wireValue)
                    put("ref", address)
                    put("label", label)
                    if (section.origin != null) put("origin", section.origin)
                    if (section.network != null) put("network", section.network.wireValue)
                }
                lines.add(json.toString())
            }
        }

        return lines.joinToString("\n")
    }

    /**
     * Parse BIP 329 JSONL content or Electrum CSV and return imported labels.
     * Handles mixed content gracefully — invalid lines are counted but skipped.
     *
     * Merge semantics: imported labels overwrite existing labels for matching refs,
     * but do not delete labels not present in the import.
     */
    fun import(
        content: String,
        defaultScope: Bip329LabelScope = Bip329LabelScope.BITCOIN,
    ): ImportResult {
        val bitcoinAddressLabels = mutableMapOf<String, String>()
        val bitcoinTransactionLabels = mutableMapOf<String, String>()
        val liquidAddressLabels = mutableMapOf<String, String>()
        val liquidTransactionLabels = mutableMapOf<String, String>()
        val sparkAddressLabels = mutableMapOf<String, String>()
        val sparkTransactionLabels = mutableMapOf<String, String>()
        val outputSpendable = mutableMapOf<String, Boolean>()
        var totalLines = 0
        var errorLines = 0

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            totalLines++

            val parsed = parseBip329Line(trimmed)
            if (parsed != null) {
                when (parsed.type) {
                    Type.TX -> {
                        if (!parsed.label.isNullOrEmpty()) {
                            when (resolveNetwork(parsed, defaultScope)) {
                                Bip329LabelNetwork.BITCOIN -> bitcoinTransactionLabels[parsed.ref] = parsed.label
                                Bip329LabelNetwork.LIQUID -> liquidTransactionLabels[parsed.ref] = parsed.label
                                Bip329LabelNetwork.SPARK -> sparkTransactionLabels[parsed.ref] = parsed.label
                            }
                        }
                    }

                    Type.ADDR -> {
                        if (!parsed.label.isNullOrEmpty()) {
                            when (resolveNetwork(parsed, defaultScope)) {
                                Bip329LabelNetwork.BITCOIN -> bitcoinAddressLabels[parsed.ref] = parsed.label
                                Bip329LabelNetwork.LIQUID -> liquidAddressLabels[parsed.ref] = parsed.label
                                Bip329LabelNetwork.SPARK -> sparkAddressLabels[parsed.ref] = parsed.label
                            }
                        }
                    }

                    Type.OUTPUT -> {
                        if (parsed.spendable != null && resolveNetwork(parsed, defaultScope) == Bip329LabelNetwork.BITCOIN) {
                            outputSpendable[parsed.ref] = parsed.spendable
                        }
                    }

                    else -> Unit
                }
                continue
            }

            val csvResult = parseElectrumCsvLine(trimmed)
            if (csvResult != null) {
                when (defaultNetwork(defaultScope)) {
                    Bip329LabelNetwork.BITCOIN -> bitcoinTransactionLabels[csvResult.first] = csvResult.second
                    Bip329LabelNetwork.LIQUID -> liquidTransactionLabels[csvResult.first] = csvResult.second
                    Bip329LabelNetwork.SPARK -> sparkTransactionLabels[csvResult.first] = csvResult.second
                }
                continue
            }

            errorLines++
        }

        return ImportResult(
            bitcoinAddressLabels = bitcoinAddressLabels,
            bitcoinTransactionLabels = bitcoinTransactionLabels,
            liquidAddressLabels = liquidAddressLabels,
            liquidTransactionLabels = liquidTransactionLabels,
            sparkAddressLabels = sparkAddressLabels,
            sparkTransactionLabels = sparkTransactionLabels,
            outputSpendable = outputSpendable,
            totalLines = totalLines,
            errorLines = errorLines,
        )
    }

    private data class ParsedLabel(
        val type: Type,
        val ref: String,
        val label: String?,
        val origin: String?,
        val spendable: Boolean?,
        val network: Bip329LabelNetwork?,
    )

    private fun parseBip329Line(line: String): ParsedLabel? {
        return try {
            val json = JSONObject(line)
            val typeStr = json.optString("type", "")
            if (typeStr.isEmpty()) return null
            val type = Type.fromWireValue(typeStr) ?: return null
            val ref = json.optString("ref", "")
            if (ref.isEmpty()) return null

            ParsedLabel(
                type = type,
                ref = ref,
                label = json.optString("label").takeIf { it.isNotEmpty() && it != "null" },
                origin = json.optString("origin").takeIf { it.isNotEmpty() && it != "null" },
                spendable = if (json.has("spendable")) json.optBoolean("spendable") else null,
                network = Bip329LabelNetwork.fromWireValue(json.optString("network")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveNetwork(
        parsed: ParsedLabel,
        defaultScope: Bip329LabelScope,
    ): Bip329LabelNetwork {
        parsed.network?.let { return it }
        if (parsed.type == Type.ADDR) {
            inferAddressNetwork(parsed.ref)?.let { return it }
        }
        return defaultNetwork(defaultScope)
    }

    private fun defaultNetwork(defaultScope: Bip329LabelScope): Bip329LabelNetwork =
        when (defaultScope) {
            Bip329LabelScope.BITCOIN, Bip329LabelScope.BOTH -> Bip329LabelNetwork.BITCOIN
            Bip329LabelScope.LIQUID -> Bip329LabelNetwork.LIQUID
            Bip329LabelScope.SPARK -> Bip329LabelNetwork.SPARK
        }

    private fun inferAddressNetwork(ref: String): Bip329LabelNetwork? {
        val normalized = ref.trim().lowercase()
        return when {
            normalized.startsWith("lq1") || normalized.startsWith("ex1") ||
                normalized.startsWith("vj") || normalized.startsWith("ct") ||
                normalized.startsWith("liquidnetwork:") -> Bip329LabelNetwork.LIQUID

            normalized.startsWith("spark") || normalized.startsWith("sp1") -> Bip329LabelNetwork.SPARK

            normalized.startsWith("bc1") || normalized.startsWith("1") ||
                normalized.startsWith("3") ||
                normalized.startsWith("bitcoin:") -> Bip329LabelNetwork.BITCOIN

            else -> null
        }
    }

    /**
     * Parse an Electrum CSV line. Matches any CSV row where one column is a 64-char
     * hex string (txid) and the next column is a non-empty label.
     * Skips header rows (where the "label" column literally says "label").
     */
    private fun parseElectrumCsvLine(line: String): Pair<String, String>? {
        val parts = parseCsvLine(line)
        if (parts.size < 2) return null

        val txidIdx = parts.indexOfFirst { field ->
            field.length == 64 && field.all { c ->
                c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            }
        }
        if (txidIdx < 0) return null

        val labelIdx = txidIdx + 1
        if (labelIdx >= parts.size) return null

        val label = parts[labelIdx].trim()
        if (label.isEmpty() || label.equals("label", ignoreCase = true)) return null

        return Pair(parts[txidIdx], label)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    result.add(current.toString().trim().removeSurrounding("\""))
                    current.clear()
                } else {
                    current.append(c)
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim().removeSurrounding("\""))

        return result
    }

    data class ImportResult(
        val bitcoinAddressLabels: Map<String, String>,
        val bitcoinTransactionLabels: Map<String, String>,
        val liquidAddressLabels: Map<String, String>,
        val liquidTransactionLabels: Map<String, String>,
        val outputSpendable: Map<String, Boolean>,
        val totalLines: Int,
        val errorLines: Int,
        val sparkAddressLabels: Map<String, String> = emptyMap(),
        val sparkTransactionLabels: Map<String, String> = emptyMap(),
    ) {
        val totalBitcoinLabelsImported: Int
            get() = bitcoinAddressLabels.size + bitcoinTransactionLabels.size

        val totalLiquidLabelsImported: Int
            get() = liquidAddressLabels.size + liquidTransactionLabels.size

        val totalSparkLabelsImported: Int
            get() = sparkAddressLabels.size + sparkTransactionLabels.size

        val totalLabelsImported: Int
            get() = totalBitcoinLabelsImported + totalLiquidLabelsImported + totalSparkLabelsImported

        val isEmpty: Boolean
            get() = totalLabelsImported == 0
    }
}
