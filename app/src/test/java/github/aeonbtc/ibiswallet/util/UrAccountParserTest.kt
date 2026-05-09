package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic

class UrAccountParserTest : FunSpec({

    beforeSpec {
        // Mock Log calls since they throw in JVM unit tests
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
    }

    // ── parseUr with unsupported type ──

    context("parseUr with unsupported type") {
        test("returns null for unknown UR type") {
            val ur = com.sparrowwallet.hummingbird.UR("unknown-type", ByteArray(10))
            val result = UrAccountParser.parseUr(
                ur,
                github.aeonbtc.ibiswallet.data.model.AddressType.SEGWIT,
            )
            result shouldBe null
        }
    }

})
