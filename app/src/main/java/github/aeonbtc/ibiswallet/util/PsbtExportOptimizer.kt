package github.aeonbtc.ibiswallet.util

import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Builds a leaner signer-export PSBT by dropping `non_witness_utxo` from inputs
 * that already carry `witness_utxo`. The full PSBT is still retained elsewhere
 * so finalize/combine can restore any metadata needed for broadcast.
 */
object PsbtExportOptimizer {
    private val psbtMagic = byteArrayOf('p'.code.toByte(), 's'.code.toByte(), 'b'.code.toByte(), 't'.code.toByte(), 0xff.toByte())

    private const val globalUnsignedTxKeyType: Byte = 0x00
    private const val inputNonWitnessUtxoKeyType: Byte = 0x00
    private const val inputWitnessUtxoKeyType: Byte = 0x01

    fun trimForSignerExport(psbtBase64: String): String {
        return runCatching {
            val psbtBytes = Base64.getDecoder().decode(psbtBase64)
            val trimmedBytes = trimForSignerExport(psbtBytes)
            Base64.getEncoder().encodeToString(trimmedBytes)
        }.getOrDefault(psbtBase64)
    }

    internal fun trimForSignerExport(psbtBytes: ByteArray): ByteArray {
        val parsed = parse(psbtBytes) ?: return psbtBytes
        var removedEntries = 0
        val trimmedInputMaps =
            parsed.inputMaps.map { entries ->
                val hasWitnessUtxo = entries.any { it.keyType == inputWitnessUtxoKeyType }
                if (!hasWitnessUtxo) {
                    entries
                } else {
                    val filtered = entries.filterNot { it.keyType == inputNonWitnessUtxoKeyType }
                    removedEntries += entries.size - filtered.size
                    filtered
                }
            }

        if (removedEntries == 0) {
            return psbtBytes
        }

        return serialize(
            ParsedPsbt(
                globalEntries = parsed.globalEntries,
                inputMaps = trimmedInputMaps,
                outputMaps = parsed.outputMaps,
            ),
        )
    }

    private fun parse(psbtBytes: ByteArray): ParsedPsbt? {
        if (psbtBytes.size < psbtMagic.size || !psbtBytes.copyOfRange(0, psbtMagic.size).contentEquals(psbtMagic)) {
            return null
        }

        val reader = ByteArrayReader(psbtBytes)
        reader.readBytes(psbtMagic.size)

        val globalEntries = reader.readMap()
        val unsignedTxBytes =
            globalEntries
                .firstOrNull { it.keyType == globalUnsignedTxKeyType }
                ?.value ?: return null

        val (inputCount, outputCount) = parseUnsignedTxCounts(unsignedTxBytes)
        val inputMaps = List(inputCount) { reader.readMap() }
        val outputMaps = List(outputCount) { reader.readMap() }

        if (!reader.isAtEnd()) {
            return null
        }

        return ParsedPsbt(
            globalEntries = globalEntries,
            inputMaps = inputMaps,
            outputMaps = outputMaps,
        )
    }

    private fun parseUnsignedTxCounts(unsignedTxBytes: ByteArray): Pair<Int, Int> {
        val reader = ByteArrayReader(unsignedTxBytes)
        reader.readBytes(4) // version

        val inputCount = reader.readCompactSize()
        repeat(inputCount) {
            reader.readBytes(32 + 4)
            val scriptLength = reader.readCompactSize()
            reader.readBytes(scriptLength + 4)
        }

        val outputCount = reader.readCompactSize()
        repeat(outputCount) {
            reader.readBytes(8)
            val scriptLength = reader.readCompactSize()
            reader.readBytes(scriptLength)
        }

        reader.readBytes(4) // locktime
        if (!reader.isAtEnd()) {
            error("Unexpected trailing bytes in PSBT unsigned transaction")
        }
        return inputCount to outputCount
    }

    private fun serialize(parsed: ParsedPsbt): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(psbtMagic)
        output.writeMap(parsed.globalEntries)
        parsed.inputMaps.forEach { output.writeMap(it) }
        parsed.outputMaps.forEach { output.writeMap(it) }
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeMap(entries: List<PsbtEntry>) {
        entries.forEach { entry ->
            writeCompactSize(entry.key.size)
            write(entry.key)
            writeCompactSize(entry.value.size)
            write(entry.value)
        }
        write(0)
    }

    private fun ByteArrayOutputStream.writeCompactSize(value: Int) {
        require(value >= 0) { "CompactSize cannot be negative" }
        when {
            value < 253 -> write(value)
            value <= 0xffff -> {
                write(253)
                writeLittleEndian(value.toLong(), 2)
            }

            else -> {
                write(254)
                writeLittleEndian(value.toLong(), 4)
            }
        }
    }

    private fun ByteArrayOutputStream.writeLittleEndian(value: Long, byteCount: Int) {
        repeat(byteCount) { index ->
            write(((value shr (index * 8)) and 0xff).toInt())
        }
    }

    private data class ParsedPsbt(
        val globalEntries: List<PsbtEntry>,
        val inputMaps: List<List<PsbtEntry>>,
        val outputMaps: List<List<PsbtEntry>>,
    )

    private data class PsbtEntry(
        val key: ByteArray,
        val value: ByteArray,
    ) {
        val keyType: Byte
            get() = key.firstOrNull() ?: error("PSBT entry key cannot be empty")
    }

    private class ByteArrayReader(private val bytes: ByteArray) {
        private var offset = 0

        fun readBytes(length: Int): ByteArray {
            require(length >= 0 && offset + length <= bytes.size) { "Unexpected end of PSBT data" }
            return bytes.copyOfRange(offset, offset + length).also {
                offset += length
            }
        }

        fun readMap(): List<PsbtEntry> {
            val entries = mutableListOf<PsbtEntry>()
            while (true) {
                val keyLength = readCompactSize()
                if (keyLength == 0) {
                    return entries
                }

                val key = readBytes(keyLength)
                val valueLength = readCompactSize()
                val value = readBytes(valueLength)
                entries += PsbtEntry(key = key, value = value)
            }
        }

        fun isAtEnd(): Boolean = offset == bytes.size

        fun readCompactSize(): Int {
            val first = readUnsignedByte()
            return when {
                first < 253 -> first
                first == 253 -> readLittleEndian(2).toInt()
                first == 254 -> readLittleEndian(4).toInt()
                else -> error("CompactSize values above UInt32 are unsupported")
            }
        }

        private fun readLittleEndian(byteCount: Int): Long {
            var value = 0L
            repeat(byteCount) { index ->
                value = value or (readUnsignedByte().toLong() shl (index * 8))
            }
            return value
        }

        private fun readUnsignedByte(): Int {
            require(offset < bytes.size) { "Unexpected end of PSBT data" }
            return bytes[offset++].toInt() and 0xff
        }
    }
}
