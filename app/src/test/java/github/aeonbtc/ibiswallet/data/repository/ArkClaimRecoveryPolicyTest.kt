package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkClaimRecoveryPolicyTest : FunSpec({

    fun verdict(
        accepted: Boolean = false,
        signed: Boolean = true,
        txid: Boolean = true,
        onChain: Boolean? = false,
        ids: Collection<String> = listOf("a"),
        claimable: Collection<String> = listOf("a"),
    ) = ArkClaimRecoveryPolicy.verdict(
        busy = false,
        broadcastAccepted = accepted,
        signedHexPresent = signed,
        txidPresent = txid,
        txOnChain = onChain,
        vtxoIds = ids,
        claimableIds = claimable,
    )

    test("discard is safe only when not accepted, not on-chain, and all exits claimable") {
        verdict() shouldBe ArkClaimRecoveryPolicy.Verdict.SAFE_TO_DISCARD
    }

    test("accepted claims are never discarded") {
        verdict(accepted = true) shouldBe ArkClaimRecoveryPolicy.Verdict.BROADCAST_ACCEPTED
    }

    test("on-chain claims are never discarded") {
        verdict(onChain = true) shouldBe ArkClaimRecoveryPolicy.Verdict.ON_CHAIN
    }

    test("signed bytes without a txid are unverifiable") {
        verdict(signed = true, txid = false) shouldBe
            ArkClaimRecoveryPolicy.Verdict.UNVERIFIABLE_SIGNED_TX
    }

    test("a no-longer-claimable exit blocks discard") {
        verdict(ids = listOf("a", "b"), claimable = listOf("a")) shouldBe
            ArkClaimRecoveryPolicy.Verdict.NOT_CLAIMABLE
    }

    test("no journal is reported as none") {
        verdict(ids = emptyList(), claimable = emptyList()) shouldBe
            ArkClaimRecoveryPolicy.Verdict.NONE
    }
})
