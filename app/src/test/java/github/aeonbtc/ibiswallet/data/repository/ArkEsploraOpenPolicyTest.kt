package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkEsploraOpenPolicyTest : FunSpec({

    test("keeps fastest reachable hosts and caps the list") {
        ArkEsploraOpenPolicy.orderForOpen(
            listOf(
                "https://mempool.space/api",
                "https://mempool.second.tech/api/",
                "https://mempool.emzy.de/api",
            ),
        ) shouldBe
            listOf(
                "https://mempool.space/api",
                "https://mempool.second.tech/api",
            )
    }

    test("pins a reachable preferred host ahead of a faster fallback") {
        ArkEsploraOpenPolicy.orderForOpen(
            listOf(
                "https://mempool.space/api",
                "https://mempool.second.tech/api/",
            ),
            preferred = "https://mempool.second.tech/api",
        ) shouldBe
            listOf(
                "https://mempool.second.tech/api",
                "https://mempool.space/api",
            )
    }

    test("does not notify when preferred host is the one that opened") {
        ArkEsploraOpenPolicy.shouldNotifyFallback(
            preferred = "https://mempool.second.tech/api/",
            active = "https://mempool.second.tech/api",
        ) shouldBe false
    }

    test("notifies only when a different host actually opened") {
        ArkEsploraOpenPolicy.shouldNotifyFallback(
            preferred = "https://mempool.second.tech/api",
            active = "https://mempool.space/api",
        ) shouldBe true
    }

    test("drops blanks and duplicates") {
        ArkEsploraOpenPolicy.orderForOpen(
            listOf(
                " https://mempool.space/api/ ",
                "https://mempool.space/api",
                "",
            ),
        ) shouldBe listOf("https://mempool.space/api")
    }
})
