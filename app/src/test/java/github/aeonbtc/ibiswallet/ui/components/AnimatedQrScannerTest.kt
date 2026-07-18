package github.aeonbtc.ibiswallet.ui.components

import com.sparrowwallet.hummingbird.URDecoder
import github.aeonbtc.ibiswallet.util.Bbqr
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic

class AnimatedQrScannerTest : FunSpec({

    beforeSpec {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
    }

    test("decodes multipart zlib BBQR PSBTs from hardware wallets") {
        val psbt = ByteArray(2_400) { index -> (index % 251).toByte() }
        val parts =
            Bbqr.split(
                data = psbt,
                fileType = Bbqr.FILE_TYPE_PSBT,
                encoding = Bbqr.ENCODING_ZLIB,
                minVersion = 8,
                maxVersion = 8,
            ).parts
        require(parts.size > 1)
        val received = mutableListOf<String>()
        val joiner = Bbqr.ContinuousJoiner()

        parts.reversed().forEach { part ->
            handleScannedQrData(
                scannedText = part,
                urDecoder = URDecoder(),
                bbqrJoiner = joiner,
                onProgress = {},
                onComplete = { received += it },
            )
        }

        received shouldBe listOf(java.util.Base64.getEncoder().encodeToString(psbt))
    }

    test("decodes multipart zlib BBQR transactions from hardware wallets") {
        val tx = ByteArray(2_400) { index -> (index % 251).toByte() }
        val parts =
            Bbqr.split(
                data = tx,
                fileType = Bbqr.FILE_TYPE_TXN,
                encoding = Bbqr.ENCODING_ZLIB,
                minVersion = 8,
                maxVersion = 8,
            ).parts
        require(parts.size > 1)
        val received = mutableListOf<String>()
        val joiner = Bbqr.ContinuousJoiner()

        parts.reversed().forEach { part ->
            handleScannedQrData(
                scannedText = part,
                urDecoder = URDecoder(),
                bbqrJoiner = joiner,
                onProgress = {},
                onComplete = { received += it },
            )
        }

        received shouldBe listOf(tx.joinToString("") { byte -> "%02x".format(byte) })
    }
})
