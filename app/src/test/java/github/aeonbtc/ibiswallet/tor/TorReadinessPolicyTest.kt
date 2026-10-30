package github.aeonbtc.ibiswallet.tor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TorReadinessPolicyTest : FunSpec({

    test("ready when SOCKS is reachable and a circuit is established") {
        TorReadinessPolicy.isTransportReady(
            socksReachable = true,
            circuitEstablished = true,
            bootstrapDone = false,
        ) shouldBe true
    }

    test("ready when SOCKS is reachable and bootstrap has finished") {
        TorReadinessPolicy.isTransportReady(
            socksReachable = true,
            circuitEstablished = false,
            bootstrapDone = true,
        ) shouldBe true
    }

    test("not ready when only the SOCKS listener is up without a circuit") {
        TorReadinessPolicy.isTransportReady(
            socksReachable = true,
            circuitEstablished = false,
            bootstrapDone = false,
        ) shouldBe false
    }

    test("not ready when SOCKS is unreachable even if a circuit is reported") {
        TorReadinessPolicy.isTransportReady(
            socksReachable = false,
            circuitEstablished = true,
            bootstrapDone = true,
        ) shouldBe false
    }
})
