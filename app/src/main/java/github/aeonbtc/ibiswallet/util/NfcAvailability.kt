package github.aeonbtc.ibiswallet.util

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import github.aeonbtc.ibiswallet.data.local.SecureStorage

data class NfcAvailability(
    val hasHardware: Boolean,
    val isSystemEnabled: Boolean,
    val isAppEnabled: Boolean,
    val supportsHce: Boolean,
) {
    val canRead: Boolean
        get() = hasHardware && isSystemEnabled && isAppEnabled

    val canBroadcast: Boolean
        get() = canRead && supportsHce
}

fun Context.getNfcAvailability(appNfcEnabled: Boolean = SecureStorage.getInstance(this).isNfcEnabled()): NfcAvailability {
    val adapter = runCatching { NfcAdapter.getDefaultAdapter(this) }.getOrNull()
    val hasHardware = adapter != null
    val isSystemEnabled = adapter?.isEnabled == true
    val supportsHce =
        runCatching {
            packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        }.getOrDefault(false)

    return NfcAvailability(
        hasHardware = hasHardware,
        isSystemEnabled = isSystemEnabled,
        isAppEnabled = appNfcEnabled,
        supportsHce = supportsHce,
    )
}
