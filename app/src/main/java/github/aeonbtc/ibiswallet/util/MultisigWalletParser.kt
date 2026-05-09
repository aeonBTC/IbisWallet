package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.MultisigCosigner
import github.aeonbtc.ibiswallet.data.model.MultisigScriptType
import github.aeonbtc.ibiswallet.data.model.MultisigWalletConfig
import org.json.JSONArray
import org.json.JSONObject

object MultisigWalletParser {
    private val descriptorPrefixRegex = Regex("""^(wsh|sh\s*\(\s*wsh)\(""", RegexOption.IGNORE_CASE)
    private val xpubRegex = Regex("""[xyz]pub[1-9A-HJ-NP-Za-km-z]+""")
    private val extendedKeyRegex = Regex("""[xyz](?:pub|prv)[1-9A-HJ-NP-Za-km-z]+""")
    private val policyRegex = Regex("""(\d+)\s*(?:of|/)\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val keyLineRegex = Regex("""^\s*([0-9a-fA-F]{8})\s*[:=]\s*([xyz]pub[1-9A-HJ-NP-Za-km-z]+)\s*$""")

    fun looksLikeMultisig(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return false
        val lowered = trimmed.lowercase()
        return lowered.contains("sortedmulti(") ||
            lowered.contains("multi(") ||
            lowered.contains("bsms") ||
            lowered.contains("\"quorum\"") ||
            lowered.contains("\"extendedpublickeys\"") ||
            lowered.contains("\"recv_descriptor\"") ||
            lowered.contains("\"receive_descriptor\"") ||
            lowered.contains("\"descriptor\"") && lowered.contains("multi(") ||
            lowered.contains("policy:") && lowered.contains(" of ")
    }

    fun parse(input: String): MultisigWalletConfig? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("{")) {
            parseJson(trimmed)?.let { return it }
        }

        parseDescriptorInput(trimmed)?.let { return it }
        parseBsms(trimmed)?.let { return it }
        return null
    }

    fun normalizeDescriptorPair(input: String): Pair<String, String>? =
        parseDescriptorPair(input)?.let { (external, internal) ->
            normalizeDescriptor(external) to normalizeDescriptor(internal)
        }

    private fun parseJson(json: String): MultisigWalletConfig? {
        val obj = JSONObject(json)
        parseJsonDescriptor(obj)?.let { return it }
        parseCaravan(obj)?.let { return it }
        parseKeystoreJson(obj)?.let { return it }
        return null
    }

    private fun parseJsonDescriptor(obj: JSONObject): MultisigWalletConfig? {
        val external =
            firstString(
                obj,
                "recv_descriptor",
                "receive_descriptor",
                "external_descriptor",
                "descriptor",
            ) ?: return null
        if (!isMultisigDescriptor(external)) return null

        val internal =
            firstString(
                obj,
                "change_descriptor",
                "internal_descriptor",
            )
        val (externalDescriptor, internalDescriptor) =
            if (!internal.isNullOrBlank()) {
                normalizeDescriptor(external) to normalizeDescriptor(internal)
            } else {
                deriveDescriptorPair(external)
            }

        return fromDescriptors(
            externalDescriptor = externalDescriptor,
            internalDescriptor = internalDescriptor,
            name = firstString(obj, "name", "label"),
            sourceFormat = "json-descriptor",
        )
    }

    private fun parseCaravan(obj: JSONObject): MultisigWalletConfig? {
        val quorum = obj.optJSONObject("quorum") ?: return null
        val required = quorum.optInt("requiredSigners", quorum.optInt("required", 0))
        val total = quorum.optInt("totalSigners", quorum.optInt("total", 0))
        if (required <= 0 || total <= 0) return null

        val keys = obj.optJSONArray("extendedPublicKeys") ?: obj.optJSONArray("keys") ?: return null
        val cosigners = mutableListOf<MultisigCosigner>()
        for (i in 0 until keys.length()) {
            val keyObj = keys.optJSONObject(i) ?: continue
            val xpub = firstString(keyObj, "xpub", "extendedPublicKey", "bip32Xpub") ?: continue
            val fingerprint = firstString(keyObj, "xfp", "fingerprint", "masterFingerprint") ?: "00000000"
            val path = normalizePath(firstString(keyObj, "bip32Path", "path", "derivationPath") ?: "m/48'/0'/0'/2'")
            cosigners.add(
                MultisigCosigner(
                    fingerprint = fingerprint.lowercase(),
                    derivationPath = path,
                    xpub = BitcoinUtils.convertToXpub(xpub),
                    label = firstString(keyObj, "name", "label"),
                ),
            )
        }
        if (cosigners.size != total) return null

        val scriptType = scriptTypeFromJson(firstString(obj, "addressType", "scriptType", "format"))
        val sorted = obj.optBoolean("sorted", true)
        return fromCosigners(
            threshold = required,
            scriptType = scriptType,
            sorted = sorted,
            cosigners = cosigners,
            name = firstString(obj, "name", "label"),
            sourceFormat = "caravan-json",
        )
    }

    private fun parseKeystoreJson(obj: JSONObject): MultisigWalletConfig? {
        val policyText = firstString(obj, "policy", "Policy")
        val policy = policyText?.let { policyRegex.find(it) }
        val threshold = obj.optInt("threshold", policy?.groupValues?.get(1)?.toIntOrNull() ?: 0)
        val keystores = obj.optJSONArray("keystores") ?: obj.optJSONArray("cosigners") ?: return null
        val cosigners = parseJsonCosigners(keystores)
        if (threshold <= 0 || cosigners.isEmpty()) return null

        return fromCosigners(
            threshold = threshold,
            scriptType = scriptTypeFromJson(firstString(obj, "script_type", "scriptType", "format")),
            sorted = firstString(obj, "policy_type", "policyType")?.contains("sorted", ignoreCase = true) ?: true,
            cosigners = cosigners,
            name = firstString(obj, "name", "label"),
            sourceFormat = "keystore-json",
        )
    }

    private fun parseJsonCosigners(array: JSONArray): List<MultisigCosigner> {
        val cosigners = mutableListOf<MultisigCosigner>()
        for (i in 0 until array.length()) {
            val keyObj = array.optJSONObject(i) ?: continue
            val xpub = firstString(keyObj, "xpub", "ExtPubKey", "extendedPublicKey") ?: continue
            val fingerprint = firstString(keyObj, "xfp", "fingerprint", "master_fingerprint", "MasterFingerprint") ?: "00000000"
            val path = normalizePath(firstString(keyObj, "derivation", "derivationPath", "path") ?: "m/48'/0'/0'/2'")
            cosigners.add(
                MultisigCosigner(
                    fingerprint = fingerprint.lowercase(),
                    derivationPath = path,
                    xpub = BitcoinUtils.convertToXpub(xpub),
                    label = firstString(keyObj, "label", "name"),
                ),
            )
        }
        return cosigners
    }

    private fun parseDescriptorInput(input: String): MultisigWalletConfig? {
        val (external, internal) = parseDescriptorPair(input) ?: return null
        return fromDescriptors(
            externalDescriptor = normalizeDescriptor(external),
            internalDescriptor = normalizeDescriptor(internal),
            name = null,
            sourceFormat = "descriptor",
        )
    }

    private fun parseDescriptorPair(input: String): Pair<String, String>? {
        val descriptors =
            input.lineSequence()
                .map { BitcoinUtils.stripDescriptorChecksum(it).trim() }
                .filter { isMultisigDescriptor(it) }
                .toList()
        if (descriptors.isEmpty()) return null
        if (descriptors.size >= 2) {
            val external = descriptors.firstOrNull { it.contains("/0/*") } ?: descriptors[0]
            val internal = descriptors.firstOrNull { it.contains("/1/*") } ?: descriptors[1]
            return external to internal
        }
        return deriveDescriptorPair(descriptors.single())
    }

    private fun parseBsms(input: String): MultisigWalletConfig? {
        val lines = input.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val policy = lines.firstNotNullOfOrNull { line ->
            afterLabel(line, "Policy")?.let(policyRegex::find)
                ?: policyRegex.find(line)
        } ?: return null
        val threshold = policy.groupValues[1].toIntOrNull() ?: return null
        val total = policy.groupValues[2].toIntOrNull() ?: return null
        val derivation = lines.firstNotNullOfOrNull { line ->
            afterLabel(line, "Derivation")
        } ?: "m/48'/0'/0'/2'"
        val scriptType =
            scriptTypeFromJson(
                lines.firstNotNullOfOrNull { line ->
                    afterLabel(line, "Format")
                },
            )
        val name =
            lines.firstNotNullOfOrNull { line ->
                afterLabel(line, "Name")
            }
        val cosigners =
            lines.mapNotNull { line ->
                val match = keyLineRegex.find(line) ?: return@mapNotNull null
                MultisigCosigner(
                    fingerprint = match.groupValues[1].lowercase(),
                    derivationPath = normalizePath(derivation),
                    xpub = BitcoinUtils.convertToXpub(match.groupValues[2]),
                )
            }
        if (cosigners.size != total) return null

        return fromCosigners(
            threshold = threshold,
            scriptType = scriptType,
            sorted = true,
            cosigners = cosigners,
            name = name,
            sourceFormat = "bsms",
        )
    }

    private fun fromDescriptors(
        externalDescriptor: String,
        internalDescriptor: String,
        name: String?,
        sourceFormat: String,
    ): MultisigWalletConfig? {
        val threshold = extractThreshold(externalDescriptor) ?: return null
        val total = extractTotalCosigners(externalDescriptor)
        if (total < threshold || total <= 1) return null
        return MultisigWalletConfig(
            name = name,
            threshold = threshold,
            totalCosigners = total,
            scriptType = detectScriptType(externalDescriptor),
            isSorted = externalDescriptor.contains("sortedmulti(", ignoreCase = true),
            externalDescriptor = externalDescriptor,
            internalDescriptor = internalDescriptor,
            cosigners = extractCosigners(externalDescriptor),
            sourceFormat = sourceFormat,
        )
    }

    private fun fromCosigners(
        threshold: Int,
        scriptType: MultisigScriptType,
        sorted: Boolean,
        cosigners: List<MultisigCosigner>,
        name: String?,
        sourceFormat: String,
    ): MultisigWalletConfig? {
        if (threshold <= 0 || cosigners.size < threshold || cosigners.size <= 1) return null
        val functionName = if (sorted) "sortedmulti" else "multi"
        val externalKeys = cosigners.joinToString(",") { it.keyExpression(branch = 0) }
        val internalKeys = cosigners.joinToString(",") { it.keyExpression(branch = 1) }
        val externalInner = "$functionName($threshold,$externalKeys)"
        val internalInner = "$functionName($threshold,$internalKeys)"
        val external = wrapMultisig(externalInner, scriptType)
        val internal = wrapMultisig(internalInner, scriptType)
        return MultisigWalletConfig(
            name = name,
            threshold = threshold,
            totalCosigners = cosigners.size,
            scriptType = scriptType,
            isSorted = sorted,
            externalDescriptor = external,
            internalDescriptor = internal,
            cosigners = cosigners,
            sourceFormat = sourceFormat,
        )
    }

    private fun MultisigCosigner.keyExpression(branch: Int): String =
        "[${fingerprint.lowercase()}/${normalizePath(derivationPath)}]${BitcoinUtils.convertToXpub(xpub)}/$branch/*"

    private fun wrapMultisig(inner: String, scriptType: MultisigScriptType): String =
        when (scriptType) {
            MultisigScriptType.P2WSH -> "wsh($inner)"
            MultisigScriptType.P2SH_P2WSH -> "sh(wsh($inner))"
        }

    private fun deriveDescriptorPair(descriptor: String): Pair<String, String> {
        val normalized = normalizeDescriptor(descriptor)
        if (BitcoinUtils.isBip389Multipath(normalized)) {
            return normalized.replace("<1;0>", "0").replace("<0;1>", "0") to
                normalized.replace("<1;0>", "1").replace("<0;1>", "1")
        }
        return when {
            normalized.contains("/0/*") -> normalized to normalized.replace("/0/*", "/1/*")
            normalized.contains("/1/*") -> normalized.replace("/1/*", "/0/*") to normalized
            else -> {
                val keys = extendedKeyRegex.findAll(normalized).map { it.value }.toList()
                val external = keys.fold(normalized) { acc, key -> acc.replace(key, "$key/0/*") }
                val internal = keys.fold(normalized) { acc, key -> acc.replace(key, "$key/1/*") }
                external to internal
            }
        }
    }

    private fun normalizeDescriptor(descriptor: String): String {
        var normalized = BitcoinUtils.stripDescriptorChecksum(descriptor).trim()
        xpubRegex.findAll(normalized).map { it.value }.toSet().forEach { key ->
            normalized = normalized.replace(key, BitcoinUtils.convertToXpub(key))
        }
        return normalized
    }

    private fun isMultisigDescriptor(value: String): Boolean {
        val trimmed = BitcoinUtils.stripDescriptorChecksum(value).trim()
        return descriptorPrefixRegex.containsMatchIn(trimmed) &&
            (trimmed.contains("multi(", ignoreCase = true) || trimmed.contains("sortedmulti(", ignoreCase = true))
    }

    private fun extractThreshold(descriptor: String): Int? =
        Regex("""(?:sortedmulti|multi)\s*\(\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(descriptor)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun extractTotalCosigners(descriptor: String): Int =
        extendedKeyRegex.findAll(descriptor).count()

    private fun extractCosigners(descriptor: String): List<MultisigCosigner> {
        val cosignerRegex = Regex("""\[([0-9a-fA-F]{8})/([^]]+)]([xyz]pub[1-9A-HJ-NP-Za-km-z]+)""")
        return cosignerRegex.findAll(descriptor).map { match ->
            MultisigCosigner(
                fingerprint = match.groupValues[1].lowercase(),
                derivationPath = normalizePath(match.groupValues[2]),
                xpub = BitcoinUtils.convertToXpub(match.groupValues[3]),
            )
        }.toList()
    }

    private fun detectScriptType(descriptor: String): MultisigScriptType =
        if (descriptor.trim().lowercase().startsWith("sh(")) {
            MultisigScriptType.P2SH_P2WSH
        } else {
            MultisigScriptType.P2WSH
        }

    private fun scriptTypeFromJson(value: String?): MultisigScriptType {
        val normalized = value?.uppercase()?.replace("-", "_") ?: return MultisigScriptType.P2WSH
        return if (normalized.contains("P2SH") || normalized.contains("NESTED")) {
            MultisigScriptType.P2SH_P2WSH
        } else {
            MultisigScriptType.P2WSH
        }
    }

    private fun normalizePath(path: String): String =
        path.trim().removePrefix("m/").removePrefix("/")

    private fun firstString(
        obj: JSONObject,
        vararg keys: String,
    ): String? {
        for (key in keys) {
            if (obj.has(key)) {
                val value = obj.optString(key, "").trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun afterLabel(
        line: String,
        label: String,
    ): String? {
        val prefix = "$label:"
        if (!line.startsWith(prefix, ignoreCase = true)) return null
        return line.substring(prefix.length).trim().ifBlank { null }
    }
}
