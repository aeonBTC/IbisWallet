package github.aeonbtc.ibiswallet.util

/**
 * Shared validation for the Silent Payments scan-start height chosen in the
 * receive disclosure popup and the Manage Wallets settings dialog.
 */
internal object SpScanStartPolicy {
    sealed interface Selection {
        /** Subscribe from the current chain tip (only offered when known). */
        data object Tip : Selection

        /** Subscribe from an explicit block height. */
        data class Height(val value: Int) : Selection

        /** Input is blank or outside [minHeight, tipHeight]. */
        data object Invalid : Selection
    }

    fun parseCustomHeight(
        input: String,
        minHeight: Int,
        tipHeight: Int?,
    ): Int? {
        val height = input.trim().toIntOrNull() ?: return null
        if (height < minHeight) return null
        if (tipHeight != null && height > tipHeight) return null
        return height
    }

    fun resolveSelection(
        useTip: Boolean,
        customInput: String,
        minHeight: Int,
        tipHeight: Int?,
    ): Selection {
        if (useTip) return Selection.Tip
        val parsed = parseCustomHeight(customInput, minHeight, tipHeight)
        return if (parsed != null) Selection.Height(parsed) else Selection.Invalid
    }
}
