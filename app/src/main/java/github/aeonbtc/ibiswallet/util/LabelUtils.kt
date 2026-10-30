package github.aeonbtc.ibiswallet.util

object LabelUtils {
    fun normalize(label: String?): String? =
        label
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Canonical transaction label resolution for every rail: an explicit
     * transaction label wins, an address label is the fallback. Both list and
     * detail surfaces must call this so they can never disagree.
     */
    fun resolve(
        transactionLabel: String?,
        addressLabel: String?,
    ): String? = normalize(transactionLabel) ?: normalize(addressLabel)
}
