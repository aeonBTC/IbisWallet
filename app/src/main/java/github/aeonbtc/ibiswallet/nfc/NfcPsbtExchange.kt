package github.aeonbtc.ibiswallet.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android NFC transport for PSBT signing (Coldcard-compatible).
 *
 * Export writes an NDEF message built by [NfcPsbtCodec] to the tag
 * (Coldcard waiting in `NFC Tools > Sign PSBT`). Import reads the NDEF
 * message the signer shares (`File Share` or post-sign auto-transmit)
 * and returns it as the string form for `setSignedTransactionData`.
 *
 * Runs on [Dispatchers.IO]; never logs payload bytes.
 */
object NfcPsbtExchange {
    suspend fun writePsbt(
        tag: Tag,
        psbtBytes: ByteArray,
    ): PsbtNfcWriteError? =
        withContext(Dispatchers.IO) {
            if (!NfcPsbtCodec.isExportableSize(psbtBytes)) {
                return@withContext PsbtNfcWriteError.TOO_LARGE
            }
            val messageBytes =
                try {
                    NfcPsbtCodec.buildPsbtMessage(psbtBytes)
                } catch (_: IllegalArgumentException) {
                    return@withContext PsbtNfcWriteError.TOO_LARGE
                }
            val message =
                try {
                    NdefMessage(messageBytes)
                } catch (_: Exception) {
                    return@withContext PsbtNfcWriteError.IO
                }
            val ndef = Ndef.get(tag) ?: return@withContext PsbtNfcWriteError.TAG_NOT_SUPPORTED
            try {
                ndef.connect()
                if (!ndef.isWritable) return@withContext PsbtNfcWriteError.NOT_WRITABLE
                // Fail closed when the tag cannot hold the message.
                if (ndef.maxSize > 0 && messageBytes.size > ndef.maxSize) {
                    return@withContext PsbtNfcWriteError.TOO_LARGE
                }
                ndef.writeNdefMessage(message)
                null
            } catch (e: Exception) {
                if (isTagLost(e)) PsbtNfcWriteError.TAG_LOST else PsbtNfcWriteError.IO
            } finally {
                runCatching { ndef.close() }
            }
        }

    suspend fun readPsbt(tag: Tag): Result<ReadPayload, PsbtNfcReadError> =
        withContext(Dispatchers.IO) {
            val messageBytes: ByteArray =
                try {
                    // Cached message covers most tags (incl. Coldcard Type 5);
                    // fall back to a live read when nothing is cached, and to a
                    // Type 4 IsoDep read for HCE peers (phone-to-phone PSBT
                    // share) that do not surface the NDEF technology at all.
                    readNdefBytes(tag)
                        ?: return@withContext Result.Failure(PsbtNfcReadError.EMPTY)
                } catch (e: Exception) {
                    return@withContext Result.Failure(
                        if (isTagLost(e)) PsbtNfcReadError.TAG_LOST else PsbtNfcReadError.IO,
                    )
                }
            when (val parsed = NfcPsbtCodec.parseMessage(messageBytes)) {
                is NfcPsbtCodec.ParseResult.Psbt -> {
                    val data =
                        Base64.encodeToString(parsed.bytes, Base64.NO_WRAP)
                    Result.Success(ReadPayload(data, isFinalTxn = false, parsed.checksumVerified))
                }
                is NfcPsbtCodec.ParseResult.Txn -> {
                    Result.Success(ReadPayload(parsed.rawHex, isFinalTxn = true, parsed.checksumVerified))
                }
                is NfcPsbtCodec.ParseResult.TextPayload -> {
                    val text = parsed.text.trim()
                    if (text.isBlank()) {
                        Result.Failure(PsbtNfcReadError.EMPTY)
                    } else {
                        Result.Success(ReadPayload(text, isFinalTxn = false, checksumVerified = false))
                    }
                }
                is NfcPsbtCodec.ParseResult.Failure ->
                    Result.Failure(
                        when (parsed.reason) {
                            NfcPsbtCodec.ParseFailure.CHECKSUM_MISMATCH ->
                                PsbtNfcReadError.CHECKSUM_MISMATCH
                            NfcPsbtCodec.ParseFailure.EMPTY,
                            NfcPsbtCodec.ParseFailure.NO_USABLE_RECORD,
                            -> PsbtNfcReadError.EMPTY
                            NfcPsbtCodec.ParseFailure.MALFORMED -> PsbtNfcReadError.IO
                        },
                    )
            }
        }

    data class ReadPayload(
        val data: String,
        val isFinalTxn: Boolean,
        val checksumVerified: Boolean,
    )

    /**
     * Raw NDEF message bytes from a tag: platform-cached NDEF first, live NDEF
     * read second, Type 4 IsoDep read last (HCE phone-to-phone share). Null
     * when nothing usable is present.
     */
    private fun readNdefBytes(tag: Tag): ByteArray? {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.cachedNdefMessage?.toByteArray()?.let { return it }
            ndef.connect()
            try {
                return ndef.ndefMessage?.toByteArray()
            } finally {
                runCatching { ndef.close() }
            }
        }
        return Type4NdefReader.readNdefMessage(tag)
    }

    sealed interface Result<out T, out E> {
        data class Success<T>(val value: T) : Result<T, Nothing>

        data class Failure<E>(val error: E) : Result<Nothing, E>
    }

    private fun isTagLost(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is android.nfc.TagLostException) return true
            val message = current.message.orEmpty()
            if (message.contains("Tag was lost", ignoreCase = true)) return true
            current = current.cause
        }
        return false
    }
}
