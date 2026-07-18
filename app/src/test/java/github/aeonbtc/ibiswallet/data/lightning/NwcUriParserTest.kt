package github.aeonbtc.ibiswallet.data.lightning

import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class NwcUriParserTest :
    StringSpec(
        {
            val albySample =
                "nostr+walletconnect://400333c1d30174646b1cd9182580710f2710752371132e6a54b3a37bd9200c95" +
                    "?relay=wss://relay.getalby.com" +
                    "&relay=wss://relay2.getalby.com" +
                    "&secret=27f2a634db766bc724b3f33c0466e799bd5ead3348cc60db40957efc37b3e104"

            "parses Alby multi-relay NWC URI" {
                val parsed = NwcUriParser.parse(albySample)
                parsed.walletPubkey shouldBe "400333c1d30174646b1cd9182580710f2710752371132e6a54b3a37bd9200c95"
                parsed.secret shouldBe "27f2a634db766bc724b3f33c0466e799bd5ead3348cc60db40957efc37b3e104"
                parsed.relays shouldHaveSize 2
                parsed.relays[0] shouldBe "wss://relay.getalby.com"
                parsed.relays[1] shouldBe "wss://relay2.getalby.com"
            }

            "connection uri parser accepts Alby NWC QR payload" {
                val result = LightningNodeConnectionUriParser.parse(albySample)
                result.type shouldBe LightningNodeConnectionType.NWC
                result.config.nwcUri shouldStartWith "nostr+walletconnect://"
                result.config.isConfigured shouldBe true
            }

            "parses uppercase scheme" {
                val upper = albySample.replace("nostr+walletconnect://", "NOSTR+WALLETCONNECT://")
                val parsed = NwcUriParser.parse(upper)
                parsed.walletPubkey.length shouldBe 64
            }

            "normalize does not wipe lowercase authority" {
                val normalized = NwcUriParser.normalize(albySample)
                normalized shouldStartWith "nostr+walletconnect://400333c1"
                normalized.contains("secret=") shouldBe true
            }
        },
    )
