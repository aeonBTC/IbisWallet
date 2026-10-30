package github.aeonbtc.ibiswallet.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * HCE service that emulates an NFC Forum Type 4 Tag containing an NDEF message.
 * When another phone or NFC reader taps this device, it receives the NDEF payload
 * (a bitcoin: URI) as if it scanned a physical NFC tag.
 *
 * Implements the NFC Forum Type 4 Tag Operation specification:
 * 1. SELECT NDEF Tag Application (AID D2760000850101)
 * 2. SELECT Capability Container (file ID E103)
 * 3. READ BINARY on CC → returns CC bytes describing the NDEF file
 * 4. SELECT NDEF file (file ID E104)
 * 5. READ BINARY on NDEF → returns 2-byte length + NDEF message bytes
 */
class NdefHostApduService : HostApduService() {

    companion object {
        // Current NDEF payload — set by ReceiveScreen via the static setter
        @Volatile
        private var currentNdefMessage: ByteArray? = null

        // Receive payload kept only while the app is foregrounded. It is moved
        // here on pause so a backgrounded phone does not keep advertising the
        // address, and restored on resume unless the screen disposed/locked.
        @Volatile
        private var suspendedNdefMessage: ByteArray? = null

        // PSBT share payload for phone-to-phone handoff, armed explicitly from
        // a PSBT export screen. Takes precedence over the receive-URI payload
        // while armed. Never armed implicitly: HCE is promiscuous (any nearby
        // reader gets the payload), so a PSBT — which exposes the wallet's
        // inputs and outputs — is only presented while the user holds the
        // share screen, and disarmed on screen dispose, cancel, timeout, or
        // app lock.
        @Volatile
        private var psbtShareMessage: ByteArray? = null

        /**
         * Set the NDEF message to broadcast. Pass null to stop broadcasting.
         * Typically called with a Bitcoin, Liquid, or Lightning payload from the Receive screen.
         */
        fun setNdefPayload(uri: String?) {
            currentNdefMessage = uri?.let { buildNdefBytes(it) }
            if (uri == null) suspendedNdefMessage = null
            if (currentNdefMessage != null) {
                NfcRuntimeStatus.setShareReady()
            } else {
                NfcRuntimeStatus.restoreShareReadyIfPayloadPresent(psbtShareMessage != null)
            }
        }

        /** Stop advertising the receive URI while the app is not visible. */
        fun suspendNdefPayload() {
            if (currentNdefMessage != null) {
                suspendedNdefMessage = currentNdefMessage
                currentNdefMessage = null
            }
            NfcRuntimeStatus.restoreShareReadyIfPayloadPresent(psbtShareMessage != null)
        }

        /** Re-present the receive URI when the app returns to the foreground. */
        fun resumeNdefPayload() {
            val suspended = suspendedNdefMessage ?: return
            if (currentNdefMessage == null) {
                currentNdefMessage = suspended
                NfcRuntimeStatus.setShareReady()
            }
        }

        /**
         * Arm a PSBT NDEF message (built by [NfcPsbtCodec.buildShareMessage])
         * for phone-to-phone sharing. While armed, taps receive the PSBT
         * instead of any receive-URI payload. Callers must disarm via
         * [disarmPsbtShare] when the share screen disposes, the user cancels,
         * the share times out, or the app locks.
         */
        fun armPsbtShare(ndefMessageBytes: ByteArray) {
            psbtShareMessage = ndefMessageBytes
            NfcRuntimeStatus.setShareReady()
        }

        /** Stop presenting a shared PSBT. Restores receive-URI state. */
        fun disarmPsbtShare() {
            psbtShareMessage = null
            NfcRuntimeStatus.restoreShareReadyIfPayloadPresent(currentNdefMessage != null)
        }

        /** True while a PSBT share payload is armed for tapping. */
        fun isPsbtShareArmed(): Boolean = psbtShareMessage != null

        /**
         * Build raw NDEF message bytes from a supported send payload.
         */
        private fun buildNdefBytes(uri: String): ByteArray {
            val trimmed = uri.trim()
            val ndefRecord =
                if (hasSupportedUriScheme(trimmed)) {
                    NdefRecord.createUri(trimmed)
                } else {
                    createTextRecord(trimmed)
                }
            val ndefMessage = NdefMessage(arrayOf(ndefRecord))
            return ndefMessage.toByteArray()
        }

        private fun hasSupportedUriScheme(uri: String): Boolean {
            val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.US)
            return scheme in setOf("bitcoin", "lightning", "liquidnetwork", "liquid")
        }

        private fun createTextRecord(text: String): NdefRecord {
            val language = "en".toByteArray(StandardCharsets.US_ASCII)
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val payload = ByteArray(1 + language.size + textBytes.size)
            payload[0] = language.size.toByte()
            System.arraycopy(language, 0, payload, 1, language.size)
            System.arraycopy(textBytes, 0, payload, 1 + language.size, textBytes.size)
            return NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT,
                ByteArray(0),
                payload,
            )
        }

    }

    private val emulator = Type4NdefApduEmulator { psbtShareMessage ?: currentNdefMessage }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        NfcRuntimeStatus.markShareActivity()
        return emulator.processCommandApdu(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        emulator.onDeactivated()
        NfcRuntimeStatus.restoreShareReadyIfPayloadPresent(
            currentNdefMessage != null || psbtShareMessage != null,
        )
    }
}
