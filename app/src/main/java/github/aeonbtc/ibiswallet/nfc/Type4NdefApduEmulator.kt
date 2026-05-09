package github.aeonbtc.ibiswallet.nfc

internal class Type4NdefApduEmulator(
    private val ndefMessageProvider: () -> ByteArray?,
) {
    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_FUNC_NOT_SUPPORTED = byteArrayOf(0x6A.toByte(), 0x81.toByte())

        private val NDEF_TAG_APPLICATION_ID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
        private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)
    }

    private var selectedFile = SelectedFile.NONE
    private var ndefFileBytes = byteArrayOf()

    private enum class SelectedFile { NONE, CC, NDEF }

    fun processCommandApdu(commandApdu: ByteArray): ByteArray {
        if (commandApdu.size < 4) return SW_FUNC_NOT_SUPPORTED

        return when (commandApdu[1]) {
            0xA4.toByte() -> handleSelect(commandApdu)
            0xB0.toByte() -> handleReadBinary(commandApdu)
            else -> SW_FUNC_NOT_SUPPORTED
        }
    }

    fun onDeactivated() {
        selectedFile = SelectedFile.NONE
    }

    private fun handleSelect(apdu: ByteArray): ByteArray {
        if (isSelectNdefTagApplication(apdu)) {
            selectedFile = SelectedFile.NONE
            refreshNdefFileBytes()
            return SW_OK
        }

        val fileId = parseSelectedFileId(apdu) ?: return SW_NOT_FOUND
        return when {
            fileId.contentEquals(CC_FILE_ID) -> {
                selectedFile = SelectedFile.CC
                SW_OK
            }
            fileId.contentEquals(NDEF_FILE_ID) -> {
                selectedFile = SelectedFile.NDEF
                SW_OK
            }
            else -> SW_NOT_FOUND
        }
    }

    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        val length = parseReadLength(apdu) ?: return SW_FUNC_NOT_SUPPORTED
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)

        val fileData = when (selectedFile) {
            SelectedFile.CC -> buildCapabilityContainer()
            SelectedFile.NDEF -> ndefFileBytes
            SelectedFile.NONE -> return SW_NOT_FOUND
        }

        if (offset >= fileData.size) return SW_NOT_FOUND

        val end = minOf(offset + length, fileData.size)
        return fileData.copyOfRange(offset, end) + SW_OK
    }

    private fun refreshNdefFileBytes() {
        val ndefBytes = ndefMessageProvider()
        ndefFileBytes = if (ndefBytes != null) {
            val len = ndefBytes.size
            byteArrayOf((len shr 8).toByte(), (len and 0xFF).toByte()) + ndefBytes
        } else {
            byteArrayOf(0x00, 0x00)
        }
    }

    private fun parseSelectedFileId(apdu: ByteArray): ByteArray? {
        if (apdu.size < 7 || apdu[2] != 0x00.toByte() || apdu[3] != 0x0C.toByte()) return null

        val lc = apdu[4].toInt() and 0xFF
        if (lc != 0x02) return null

        val dataEnd = 5 + lc
        if (!hasValidTrailingLe(apdu, dataEnd)) return null

        return byteArrayOf(apdu[5], apdu[6])
    }

    private fun parseReadLength(apdu: ByteArray): Int? {
        if (apdu.size == 5) {
            val length = apdu[4].toInt() and 0xFF
            return if (length == 0) 256 else length
        }

        if (apdu.size == 7 && apdu[4] == 0x00.toByte()) {
            val length = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
            return if (length == 0) 65536 else length
        }

        return null
    }

    private fun isSelectNdefTagApplication(apdu: ByteArray): Boolean {
        if (apdu.size < 5 || apdu[1] != 0xA4.toByte() || apdu[2] != 0x04.toByte()) return false

        val lc = apdu[4].toInt() and 0xFF
        if (lc != NDEF_TAG_APPLICATION_ID.size) return false

        val dataEnd = 5 + lc
        if (apdu.size < dataEnd || !hasValidTrailingLe(apdu, dataEnd)) return false

        return apdu.copyOfRange(5, dataEnd).contentEquals(NDEF_TAG_APPLICATION_ID)
    }

    private fun hasValidTrailingLe(apdu: ByteArray, dataEnd: Int): Boolean {
        val trailingBytes = apdu.size - dataEnd
        return when (trailingBytes) {
            0, 1 -> true
            3 -> apdu[dataEnd] == 0x00.toByte()
            else -> false
        }
    }

    private fun buildCapabilityContainer(): ByteArray {
        val ndefLen = ndefFileBytes.size
        val maxNdefSize = maxOf(ndefLen, 512)

        return byteArrayOf(
            0x00, 0x0F,
            0x20,
            0x00, 0xF6.toByte(),
            0x00, 0xF6.toByte(),
            0x04,
            0x06,
            0xE1.toByte(), 0x04,
            (maxNdefSize shr 8).toByte(),
            (maxNdefSize and 0xFF).toByte(),
            0x00,
            0xFF.toByte(),
        )
    }
}
