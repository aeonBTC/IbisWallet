package github.aeonbtc.ibiswallet.data.lightning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LndOnchainTxAddressResolveTest : StringSpec({
    "incoming receive uses our output not sender change" {
        val resolved =
            resolveLndTxAddresses(
                amountSats = 30_000L,
                outputs =
                    listOf(
                        LndTxOutput(
                            address = "bc1qsenderchange",
                            amountSats = 43_686L,
                            isOurs = false,
                        ),
                        LndTxOutput(
                            address = "bc1qourreceive",
                            amountSats = 30_000L,
                            isOurs = true,
                        ),
                    ),
                destAddressesFallback = "bc1qsenderchange",
            )

        resolved.address shouldBe "bc1qourreceive"
        resolved.addressAmountSats shouldBe 30_000L
        resolved.changeAddress.shouldBeNull()
        resolved.changeAmountSats.shouldBeNull()
    }

    "outgoing send keeps recipient and our change" {
        val resolved =
            resolveLndTxAddresses(
                amountSats = -30_365L,
                outputs =
                    listOf(
                        LndTxOutput(
                            address = "bc1qrecipient",
                            amountSats = 30_000L,
                            isOurs = false,
                        ),
                        LndTxOutput(
                            address = "bc1qourchange",
                            amountSats = 43_686L,
                            isOurs = true,
                        ),
                    ),
            )

        resolved.address shouldBe "bc1qrecipient"
        resolved.addressAmountSats shouldBe 30_000L
        resolved.changeAddress shouldBe "bc1qourchange"
        resolved.changeAmountSats shouldBe 43_686L
    }

    "outgoing falls back to dest_addresses when outputs lack external" {
        val resolved =
            resolveLndTxAddresses(
                amountSats = -10_000L,
                outputs =
                    listOf(
                        LndTxOutput(
                            address = "bc1qourchange",
                            amountSats = 5_000L,
                            isOurs = true,
                        ),
                    ),
                destAddressesFallback = "bc1qfromdest",
            )

        resolved.address shouldBe "bc1qfromdest"
        resolved.addressAmountSats shouldBe 10_000L
        resolved.changeAddress shouldBe "bc1qourchange"
        resolved.changeAmountSats shouldBe 5_000L
    }
})
