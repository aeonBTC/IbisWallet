package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SilentPaymentTest : FunSpec({
    val address = "sp1qqgste7k9hx0qftg6qmwlkqtwuy6cycyavzmzj85c6qdfhjdpdjtdgqjuexzk6murw56suy3e0rd2cgqvycxttddwsvgxe2usfpxumr70xc9pkqwv"

    context("address parsing") {
        test("parses a v0 mainnet silent payment address") {
            val parsed = SilentPayment.parseAddress(address).getOrThrow()

            parsed.scanPublicKey.toHex() shouldBe
                "0220bcfac5b99e04ad1a06ddfb016ee13582609d60b6291e98d01a9bc9a16c96d4"
            parsed.spendPublicKey.toHex() shouldBe
                "025cc9856d6f8375350e123978daac200c260cb5b5ae83106cab90484dcd8fcf36"
        }

        test("rejects an invalid checksum") {
            SilentPayment.parseAddress(address.dropLast(1) + "x").isFailure shouldBe true
        }
    }

    context("sender vectors") {
        test("matches BIP-352 simple send vector with two P2PKH inputs") {
            val outputs =
                SilentPayment.createOutputKeys(
                    inputKeys =
                        listOf(
                            SilentPayment.InputKey(
                                outpoint = "f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16:0",
                                privateKey = "eadc78165ff1f8ea94ad7cfdc54990738a4c53f6e0507b42154201b8e5dff3b1".hexToBytes(),
                            ),
                            SilentPayment.InputKey(
                                outpoint = "a1075db55d416d3ca199f55b6084e2115b9345e16c5cf302fc80e9d5fbf5d48d:0",
                                privateKey = "93f5ed907ad5b2bdbbdcb5d9116ebc0a4e1f92f910d5260237fa45a9408aad16".hexToBytes(),
                            ),
                        ),
                    recipients = listOf(address),
                )

            outputs.map { it.xOnlyPublicKey.toHex() } shouldContainExactly
                listOf("3e9fce73d4e77a4809908e3c3a2e54ee147b9312dc5044a193d1fc85de46e3c1")
        }
    }
})

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }
