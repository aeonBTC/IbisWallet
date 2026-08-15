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
