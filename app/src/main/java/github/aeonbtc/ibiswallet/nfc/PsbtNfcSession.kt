package github.aeonbtc.ibiswallet.nfc

/**
 * PSBT NFC session events routed from the reader callback to the PSBT screen.
 * Payload bytes are never carried here — only transport strings already in
 * the form accepted by `WalletViewModel.setSignedTransactionData`.
 */
enum class PsbtNfcWriteError {
    TOO_LARGE,
    TAG_NOT_SUPPORTED,
    NOT_WRITABLE,
    TAG_LOST,
    IO,
}

enum class PsbtNfcReadError {
    TAG_NOT_SUPPORTED,
    EMPTY,
    CHECKSUM_MISMATCH,
    TAG_LOST,
    IO,
}

sealed interface PsbtNfcEvent {
    data object WriteSucceeded : PsbtNfcEvent

    data class WriteFailed(val error: PsbtNfcWriteError) : PsbtNfcEvent

    data class ReadSucceeded(
        val data: String,
        val isFinalTxn: Boolean,
        val checksumVerified: Boolean,
    ) : PsbtNfcEvent

    data class ReadFailed(val error: PsbtNfcReadError) : PsbtNfcEvent
}
