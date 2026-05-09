package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Base64

class PsbtExportOptimizerTest : FunSpec({

    context("trimForSignerExport") {
        test("drops non witness utxo entries when witness utxo is present") {
            val trimmed = PsbtExportOptimizer.trimForSignerExport(oversizedPsbtBase64)
            val originalBytes = Base64.getDecoder().decode(oversizedPsbtBase64)
            val trimmedBytes = Base64.getDecoder().decode(trimmed)

            (trimmedBytes.size < originalBytes.size) shouldBe true
            inputKeyTypeCounts(oversizedPsbtBase64, 0x00) shouldBe listOf(1, 1, 1, 1)
            inputKeyTypeCounts(trimmed, 0x00) shouldBe listOf(0, 0, 0, 0)
            inputKeyTypeCounts(trimmed, 0x01) shouldBe listOf(1, 1, 1, 1)
        }

        test("trimmed signer export preserves the unsigned transaction and output maps") {
            val trimmed = PsbtExportOptimizer.trimForSignerExport(oversizedPsbtBase64)

            unsignedTxBytes(trimmed).contentEquals(unsignedTxBytes(oversizedPsbtBase64)) shouldBe true
            outputMapCount(trimmed) shouldBe outputMapCount(oversizedPsbtBase64)
        }

        test("trimming is idempotent") {
            val trimmedOnce = PsbtExportOptimizer.trimForSignerExport(oversizedPsbtBase64)
            val trimmedTwice = PsbtExportOptimizer.trimForSignerExport(trimmedOnce)

            trimmedTwice shouldBe trimmedOnce
        }
    }
})

private fun inputKeyTypeCounts(
    psbtBase64: String,
    keyType: Int,
): List<Int> {
    val psbtBytes = Base64.getDecoder().decode(psbtBase64)
    val reader = PsbtTestReader(psbtBytes)
    reader.skip(5)

    val globalMap = reader.readMap()
    val unsignedTxBytes = globalMap.first { it.first.first().toInt() and 0xff == 0x00 }.second
    val txCounts = parseUnsignedTxCounts(unsignedTxBytes)

    return List(txCounts.first) {
        reader.readMap().count { (key, _) -> (key.first().toInt() and 0xff) == keyType }
    }.also {
        repeat(txCounts.second) { reader.readMap() }
    }
}

private fun unsignedTxBytes(psbtBase64: String): ByteArray {
    val psbtBytes = Base64.getDecoder().decode(psbtBase64)
    val reader = PsbtTestReader(psbtBytes)
    reader.skip(5)
    return reader.readMap().first { it.first.first().toInt() and 0xff == 0x00 }.second
}

private fun outputMapCount(psbtBase64: String): Int {
    val psbtBytes = Base64.getDecoder().decode(psbtBase64)
    val reader = PsbtTestReader(psbtBytes)
    reader.skip(5)

    val globalMap = reader.readMap()
    val unsignedTx = globalMap.first { it.first.first().toInt() and 0xff == 0x00 }.second
    val (inputCount, outputCount) = parseUnsignedTxCounts(unsignedTx)
    repeat(inputCount) { reader.readMap() }
    repeat(outputCount) { reader.readMap() }
    return outputCount
}

private fun parseUnsignedTxCounts(unsignedTxBytes: ByteArray): Pair<Int, Int> {
    val reader = PsbtTestReader(unsignedTxBytes)
    reader.skip(4)

    val inputCount = reader.readCompactSize()
    repeat(inputCount) {
        reader.skip(32 + 4)
        val scriptLength = reader.readCompactSize()
        reader.skip(scriptLength + 4)
    }

    val outputCount = reader.readCompactSize()
    repeat(outputCount) {
        reader.skip(8)
        val scriptLength = reader.readCompactSize()
        reader.skip(scriptLength)
    }

    reader.skip(4)
    return inputCount to outputCount
}

private class PsbtTestReader(private val bytes: ByteArray) {
    private var offset = 0

    fun skip(length: Int) {
        require(length >= 0 && offset + length <= bytes.size)
        offset += length
    }

    fun readCompactSize(): Int {
        val first = readUnsignedByte()
        return when {
            first < 253 -> first
            first == 253 -> readLittleEndian(2).toInt()
            first == 254 -> readLittleEndian(4).toInt()
            else -> error("CompactSize values above UInt32 are unsupported in tests")
        }
    }

    fun readMap(): List<Pair<ByteArray, ByteArray>> {
        val entries = mutableListOf<Pair<ByteArray, ByteArray>>()
        while (true) {
            val keyLength = readCompactSize()
            if (keyLength == 0) {
                return entries
            }

            val key = readBytes(keyLength)
            val valueLength = readCompactSize()
            val value = readBytes(valueLength)
            entries += key to value
        }
    }

    private fun readBytes(length: Int): ByteArray {
        require(length >= 0 && offset + length <= bytes.size)
        return bytes.copyOfRange(offset, offset + length).also {
            offset += length
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
        require(offset < bytes.size)
        return bytes[offset++].toInt() and 0xff
    }
}

private const val oversizedPsbtBase64 =
    "cHNidP8BAM0CAAAABJyg4H09rGEes8u5CH7F8NY/Hg9GEdohXgortoKiWsi0AAAAAAD9////eT3YA6e0saaMHUJ8i8JdWV46x1NMm34Mh0pe7LVTNa8ZAAAAAP3///9gUJUTsdlR0AsmwUX/sW5vVaeSzGWMMMMOR/SQF7Op9gAAAAAA/f////3swtpouQNWrQmdqzG9VqhOoE7vwdezkxDVUyg+k7sUAAAAAAD9////ASChBwAAAAAAFgAU4nDqY0FgnBjqdXTFRuEvfhc4DGkFbA4AAAEA/QYCAgAAAAABA97WfuKrA8svHeiFITfL8pJysTdA1bs1a0C/RUs0NNA+IgAAAAD9////CJ5xVjWbtnd8ah89IKVTDcJbMrSg4u5iK+hZ1NSdUOETAAAAAP3///8DPtyMGssENyFshj2PuYlRrB3pUr8XhPd+6ppZ3hBk/AAAAAAA/f///wLeRQAAAAAAABYAFLt+BrtZ3gDJPlp7SSDGVRu/e2Xyy24VAAAAAAAWABRbeKOUpAMYTYi5c3MBx2fSbkmD4AJHMEQCIDLTTs7YTVz8oUa1I3dGIKJ+IxRl25zH6VYEfLwZKQ3mAiAu9TXwVuVD6thRr8bbxmbnWtLzlDu9MEufupPdylBwdAEhAiuY37JGYclR/qCQuz2ITMAdFa8weBv0xQFsmQMphiRZAkcwRAIgVtfaBJpIsXK2YTgdGQx18x6Q9pHIgCNrSTiZ/yOVyNECIFTZ28WFnPNkpdmIDl3wQPegLUb+gemqBs9KqfKKDBsQASEDN1fhs1xPHwvg3+FAdtaaPYYNFC3ilhYYpkqpAQKSVQMCRzBEAiBepw3INfRI+hcpSZVoJgsKQ1YzIs0qZP1hosQDiwHzngIgSkBiSGokbLeoIVZMGVDCODWRd70fHG0hvevRVNnXD4UBIQMA602s1HfJcz9vIsP4fzn6KFHUFdSP50Fs457Fz2zr5KZZDgABAR/eRQAAAAAAABYAFLt+BrtZ3gDJPlp7SSDGVRu/e2XyIgYCww2npIFP4bScuoju3J3zuxBmw4S22iTiv24f/jW+2LcYAAAAAFQAAIAAAACAAAAAgAEAAACyAAAAAAEA/SUSAgAAAAABERjq1GTKnrRKF91u99AZn28+GuYCzwU6cE3fuBgEosjtAQAAABcWABTOZ0ASHnDdBN+8co/R8wCREnGkXf////9o8+IdmrAyPH2w1hfCN3hjXAU00x9wUOK57WW6tA1zEgAAAAAA/////1mVi1bNB4UOAtejzEGdjsZi7i0EvtMVFouV39VPrUm2AQAAAAD/////D9eIKmeJu9zX0QtihTJPlk6nVv1hvlEV4Qk5p3zSZ/8AAAAAAP/////140j/5WQG7R+adNzNh7Gn71cm7Knaan2EAt+eY7C7dwAAAAAA/////yT5VVo/PrU4ciinfI3RRbV7TmuhACp5fPDsRRKHwA7zAAAAAAD/////d8nMPFA8aKHUFgLeC+smlNruS0roDApsnTuaWUxJIVAAAAAAAP/////IuNLshHfqJXOTtQiEfEZ0UHUDej+gSPk6//Pu0mlcXAAAAAAA/////5fUo4S3DYvfASS6qIZ06L/bAk5yachMGpMJuCO9A+LyAQAAAAD/////hdwXPreK7PbxdprNyROCM4SfOMrjXvFAJKUB6uYTnAkAAAAAAP////+rVxrAYZWFYGKyRJmft+TcGwsokc58rdtkreFIdw1RlAAAAAAA/////2bUhANpyD6PYKYYyGlYAbRpTSmsVqn2qexp4PPv81kRAAAAAAD/////LxJDTQtYphZONtp98k79XhWmmwFTB+0yt+a+KVfgv70AAAAAAP////9DQcJsP+mD6tzNDy909vLP4/HSfQRdXYQ2MddxpQjUjAAAAAAA/////8vmUmYYPTd5kzO+7kA+NEApsNqjYLGf3whA9Ya91sYcAAAAAAD/////lhYVjjUy1i91sl/59VEormFN7RR4m2Ej190xQbfG0cgBAAAAAP////+b9xq5zt7oEWyAq3RUDL2w/gjm1b4+7Rl71PZYiX7CWBUAAAAA/////0EvGQUAAAAAABepFEeMqu61nsmDBuLD7emNJiFZ4rlnhwG9dwAAAAAAIgAg+hvuEPtk2375tffr0qa843f9mcdbBnHxN519q3tTnZnfykoAAAAAABYAFF0mCI6q1xD2YuTjCylPWTosA/vyWvMBAAAAAAAZdqkURNlSK6I4z6TeU/DT7+UbTlUmCHGIrGa8BAAAAAAAGXapFCYXVWgy8ODKAmkCGt0TsEfds+O3iKw4QQIAAAAAABl2qRTD87lEsuVO5QR3DK7KR3UsexFyj4ispP8BAAAAAAAWABTU/ZbAuwFwTFxfogU7xftM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"
