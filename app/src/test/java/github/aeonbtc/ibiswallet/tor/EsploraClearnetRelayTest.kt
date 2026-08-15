package github.aeonbtc.ibiswallet.tor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EsploraClearnetRelayTest : FunSpec({

    test("parses default Second Esplora") {
        val target = EsploraClearnetRelay.parse("https://mempool.second.tech/api")
        target shouldNotBe null
        target!!.host shouldBe "mempool.second.tech"
        target.port shouldBe 443
        target.useTls shouldBe true
        target.pathPrefix shouldBe "/api"
        target.loopback shouldBe false
    }

    test("parses custom port and path") {
        val target = EsploraClearnetRelay.parse("http://esplora.example:3000/api/")
        target shouldNotBe null
        target!!.port shouldBe 3000
        target.useTls shouldBe false
        target.pathPrefix shouldBe "/api"
    }

    test("skips onion and loopback") {
        EsploraClearnetRelay.parse(
            "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion/api",
        ) shouldBe null
        EsploraClearnetRelay.fromUrl("http://127.0.0.1:3002/api") shouldBe null
    }
})
