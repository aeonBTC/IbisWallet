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
        relaxed.version.shouldBeGreaterThanOrEqual(6)
        relaxed.version.shouldBeLessThanOrEqual(40)
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

    test("psbt bbqrs default to uncompressed base32 for hardware wallet compatibility") {
        val payload = ByteArray(2_400) { index -> (index % 251).toByte() }

        val split =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                minVersion = 8,
                maxVersion = 12,
            )

        split.encoding shouldBe Bbqr.ENCODING_BASE32
        split.parts.size.shouldBeGreaterThanOrEqual(5)
        split.parts.all { part -> part.startsWith("B\$2P") } shouldBe true
    }

    test("multipart psbt bbqrs round trip with base32 encoding") {
        val payload = ByteArray(2_400) { index -> (index % 251).toByte() }
        val split =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                minVersion = 8,
                maxVersion = 12,
            )
        val joiner = Bbqr.ContinuousJoiner()

        split.parts.reversed().forEach { part -> joiner.addPart(part) shouldBe true }

        joiner.partsReceived shouldBe split.parts.size
        joiner.result?.data?.contentEquals(payload) shouldBe true
        joiner.result?.fileType shouldBe Bbqr.FILE_TYPE_PSBT
        joiner.result?.encoding shouldBe Bbqr.ENCODING_BASE32
    }

    test("multipart bbqrs do not emit an extra frame when the final part uses full qr capacity") {
        val payload = ByteArray(661) { index -> (index % 251).toByte() }

        val split =
            Bbqr.split(
                data = payload,
                fileType = Bbqr.FILE_TYPE_PSBT,
                encoding = Bbqr.ENCODING_BASE32,
                minVersion = 8,
                maxVersion = 8,
            )
        val joiner = Bbqr.ContinuousJoiner()

        split.parts.size shouldBe 4
        split.parts.all { part -> part.substring(4, 6) == "04" } shouldBe true
        split.parts.forEach { part -> joiner.addPart(part) shouldBe true }
        joiner.result?.data?.contentEquals(payload) shouldBe true
    }
})
