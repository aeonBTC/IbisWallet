package github.aeonbtc.ibiswallet.ui.components

import github.aeonbtc.ibiswallet.data.local.SecureStorage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AnimatedQrCodePolicyTest : FunSpec({

    context("clampAnimatedPartIndex") {
        test("clamps stale animated indices when part count shrinks") {
            clampAnimatedPartIndex(
                partIndex = 14,
                totalParts = 11,
            ) shouldBe 10
        }

        test("returns zero when no parts are available") {
            clampAnimatedPartIndex(
                partIndex = 3,
                totalParts = 0,
            ) shouldBe 0
        }
    }

    context("resolveUrFragmentSize") {
        test("bitcoin psbt high density uses a larger fragment budget than liquid pset") {
            resolveUrFragmentSize(
                payloadSize = 2_500,
                density = SecureStorage.QrDensity.HIGH,
                exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
            ) shouldBe 340

            resolveUrFragmentSize(
                payloadSize = 2_500,
                density = SecureStorage.QrDensity.HIGH,
                exportProfile = AnimatedQrExportProfile.LIQUID_PSET,
            ) shouldBe 220
        }

        test("bitcoin psbt fragment size increases with density") {
            val payloadSize = 6_500

            val low =
                resolveUrFragmentSize(
                    payloadSize = payloadSize,
                    density = SecureStorage.QrDensity.LOW,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )
            val medium =
                resolveUrFragmentSize(
                    payloadSize = payloadSize,
                    density = SecureStorage.QrDensity.MEDIUM,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )
            val high =
                resolveUrFragmentSize(
                    payloadSize = payloadSize,
                    density = SecureStorage.QrDensity.HIGH,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )

            low shouldBe 240
            medium shouldBe 320
            high shouldBe 400
        }
    }

    context("buildAnimatedQrEncodingPlan") {
        test("reports payload size fragment size and generated part count") {
            val payload = ByteArray(2_600) { index -> (index % 251).toByte() }

            val plan =
                buildAnimatedQrEncodingPlan(
                    dataBytes = payload,
                    density = SecureStorage.QrDensity.HIGH,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )

            plan.diagnostics.payloadSizeBytes shouldBe payload.size
            plan.diagnostics.fragmentSize shouldBe 340
            plan.diagnostics.totalParts shouldBe plan.qrParts.size
            plan.diagnostics.exportProfile shouldBe AnimatedQrExportProfile.BITCOIN_PSBT
            plan.qrParts.isNotEmpty() shouldBe true
        }

        test("higher density produces fewer bitcoin psbt frames for the same payload") {
            val payload = ByteArray(6_200) { index -> (index % 251).toByte() }

            val low =
                buildAnimatedQrEncodingPlan(
                    dataBytes = payload,
                    density = SecureStorage.QrDensity.LOW,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )
            val medium =
                buildAnimatedQrEncodingPlan(
                    dataBytes = payload,
                    density = SecureStorage.QrDensity.MEDIUM,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )
            val high =
                buildAnimatedQrEncodingPlan(
                    dataBytes = payload,
                    density = SecureStorage.QrDensity.HIGH,
                    exportProfile = AnimatedQrExportProfile.BITCOIN_PSBT,
                )

            (high.diagnostics.totalParts < medium.diagnostics.totalParts) shouldBe true
            (medium.diagnostics.totalParts < low.diagnostics.totalParts) shouldBe true
        }
    }
})
