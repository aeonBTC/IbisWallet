package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class BbqrTest : FunSpec({

    test("psbt bbqrs can target a denser capped version range") {
        val payload = ByteArray(791) { index -> (index % 251).toByte() }

        val relaxed =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                minVersion = 6,
                maxVersion = 40,
            )
        val capped =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                minVersion = 8,
                maxVersion = 14,
            )

        capped.version.shouldBeGreaterThanOrEqual(8)
        capped.version.shouldBeLessThanOrEqual(14)
        (capped.parts.size <= relaxed.parts.size) shouldBe true
    }

    test("higher psbt bbqrs density range can reduce split count") {
        val payload = ByteArray(791) { index -> (index % 251).toByte() }

        val lowDensity =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                minVersion = 8,
                maxVersion = 12,
            )
        val highDensity =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                minVersion = 10,
                maxVersion = 18,
            )

        highDensity.version.shouldBeGreaterThanOrEqual(10)
        highDensity.version.shouldBeLessThanOrEqual(18)
        (highDensity.parts.size <= lowDensity.parts.size) shouldBe true
    }
})
