package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LightningNodeConfigTorTest : StringSpec({
    "clearnet LND drops useTor" {
        LightningNodeConfig(
            type = LightningNodeConnectionType.LND_REST,
            host = "ln.example.com",
            port = 8080,
            useTor = true,
            macaroonHex = "aa",
        ).withOnionOnlyTor().useTor shouldBe false
    }

    "onion LND forces useTor" {
        LightningNodeConfig(
            type = LightningNodeConnectionType.LND_REST,
            host = "abc.onion",
            port = 8080,
            useTor = false,
            macaroonHex = "aa",
        ).withOnionOnlyTor().useTor shouldBe true
    }

    "clearnet NWC drops useTor" {
        LightningNodeConfig(
            type = LightningNodeConnectionType.NWC,
            useTor = true,
            nwcUri = "nostr+walletconnect://pk?relay=wss://relay.example&secret=ab",
        ).withOnionOnlyTor().useTor shouldBe false
    }

    "onion NWC relay forces useTor" {
        LightningNodeConfig(
            type = LightningNodeConnectionType.NWC,
            useTor = false,
            nwcUri = "nostr+walletconnect://pk?relay=wss://abc.onion&secret=ab",
        ).withOnionOnlyTor().useTor shouldBe true
    }

    "TLS on connectCandidates is the same config" {
        val config =
            LightningNodeConfig(
                type = LightningNodeConnectionType.LND_REST,
                host = "ln.example.com",
                port = 8080,
                useTls = true,
                tlsCertPem = "CERT",
                macaroonHex = "aa",
            )
        val candidates = config.connectCandidates()
        candidates.size shouldBe 1
        candidates[0] shouldBe config
    }

    "TLS off connectCandidates tries HTTPS then HTTP" {
        val config =
            LightningNodeConfig(
                type = LightningNodeConnectionType.CLN_REST,
                host = "cln.example.com",
                port = 3010,
                useTls = false,
                clnRune = "rune",
            )
        val candidates = config.connectCandidates()
        candidates.size shouldBe 2
        candidates[0].useTls shouldBe true
        candidates[0].allowInsecureTls shouldBe true
        candidates[1].useTls shouldBe false
        candidates[1].allowInsecureTls shouldBe false
    }
})
