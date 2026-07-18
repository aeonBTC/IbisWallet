package github.aeonbtc.ibiswallet.data.lightning

import github.aeonbtc.ibiswallet.data.model.LightningNodeConnectionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import java.util.Base64

class LightningNodeConnectionUriParserTest :
    StringSpec(
        {
            "parses clnrest+https umbrel style with rune and certs" {
                val uri =
                    "clnrest+https://63kckr5kiz6kfixzlcss4krklpiezrrepkag5y7ei3f4z2o3e3example.onion:2107" +
                        "?rune=7_IrillwOrf-5YNtQx9Cckvo3lv_6tR4FRvvBpR5NHjA9M%3D" +
                        "&certs=Y2xpZW50X2tleTogLS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0t"
                val result = LightningNodeConnectionUriParser.parse(uri)
                result.type shouldBe LightningNodeConnectionType.CLN_REST
                result.config.host shouldContain "onion"
                result.config.port shouldBe 2107
                result.config.useTor shouldBe true
                result.config.useTls shouldBe true
                result.config.clnRune.shouldNotBeEmpty()
                result.config.tlsCertPem.shouldNotBeEmpty()
            }

            "parses clnrest with unescaped equals padding in rune" {
                val uri =
                    "clnrest+https://example.com:3010?rune=ABC_def-ghi==&certs=eu"
                val result = LightningNodeConnectionUriParser.parse(uri)
                result.config.port shouldBe 3010
                result.config.host shouldBe "example.com"
                result.config.clnRune shouldBe "ABC_def-ghi=="
            }

            "parses percent-encoded full clnrest URL" {
                val plain =
                    "clnrest+https://node.example:2107?rune=myrune1234567890"
                val encoded =
                    java.net.URLEncoder.encode(plain, Charsets.UTF_8.name())
                val result = LightningNodeConnectionUriParser.parse(encoded)
                result.type shouldBe LightningNodeConnectionType.CLN_REST
                result.config.host shouldBe "node.example"
                result.config.port shouldBe 2107
                result.config.clnRune shouldBe "myrune1234567890"
            }

            "parses base64-wrapped clnrest URL" {
                val plain =
                    "clnrest+https://base64host.onion:2107?rune=runewrap123456789"
                val b64 = Base64.getEncoder().encodeToString(plain.toByteArray())
                val result = LightningNodeConnectionUriParser.parse(b64)
                result.config.host shouldContain "base64host"
                result.config.clnRune shouldBe "runewrap123456789"
                result.config.useTor shouldBe true
            }

            "parses json clnrest envelope" {
                val json =
                    """
                    {
                      "host": "cln.example",
                      "port": 2107,
                      "rune": "jsonrune1234567890abcd",
                      "protocol": "https",
                      "certs": "Y2E="
                    }
                    """.trimIndent()
                val result = LightningNodeConnectionUriParser.parse(json)
                result.type shouldBe LightningNodeConnectionType.CLN_REST
                result.config.host shouldBe "cln.example"
                result.config.port shouldBe 2107
                result.config.clnRune shouldBe "jsonrune1234567890abcd"
            }

            "parses lndconnect" {
                // minimal fake macaroon base64url (3 bytes)
                val uri =
                    "lndconnect://lnd.example:8080?macaroon=AQID&cert="
                val result = LightningNodeConnectionUriParser.parse(uri)
                result.type shouldBe LightningNodeConnectionType.LND_REST
                result.config.host shouldBe "lnd.example"
                result.config.port shouldBe 8080
                result.config.macaroonHex shouldBe "010203"
            }

            "parses hex macaroon param" {
                val uri =
                    "lndconnect://hexhost:8080?macaroon=aabbccdd"
                val result = LightningNodeConnectionUriParser.parse(uri)
                result.config.macaroonHex shouldBe "aabbccdd"
            }

            "rejects certs-only zeus style payload with clear error" {
                val err =
                    runCatching {
                        LightningNodeConnectionUriParser.parse(
                            "Y2xpZW50X2tleTogLS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0t",
                        )
                    }.exceptionOrNull()
                err!!.message!!.shouldContain("TLS cert")
            }

            "parses scheme-less host port rune" {
                val uri = "node.example.onion:2107?rune=abcdefg1234567890"
                val result = LightningNodeConnectionUriParser.parse(uri)
                result.type shouldBe LightningNodeConnectionType.CLN_REST
                result.config.port shouldBe 2107
                result.config.useTor shouldBe true
                result.config.clnRune shouldBe "abcdefg1234567890"
            }

            "parses html-entity ampersand in query" {
                val ampEntity = "\u0026amp;"
                val uri =
                    "clnrest+https://h.example:3010?rune=abc1234567890xyz${ampEntity}certs=ZZZ"
                val result = LightningNodeConnectionUriParser.parse(uri)
                result.config.clnRune shouldBe "abc1234567890xyz"
                result.config.tlsCertPem shouldBe "ZZZ"
            }
        },
    )
