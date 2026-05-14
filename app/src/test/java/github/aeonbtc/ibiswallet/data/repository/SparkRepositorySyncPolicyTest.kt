package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SparkRepositorySyncPolicyTest : FunSpec({

    test("fresh load requests explicit Spark wallet sync when no SDK is connected") {
        SparkSyncPolicy.shouldSyncWallet(
            currentLoadedWalletId = null,
            requestedWalletId = "wallet-1",
            recoverySyncCompletedForWalletId = null,
            hasSdk = false,
        ) shouldBe true
    }

    test("same wallet skips explicit Spark wallet sync after recovery sync completed") {
        SparkSyncPolicy.shouldSyncWallet(
            currentLoadedWalletId = "wallet-1",
            requestedWalletId = "wallet-1",
            recoverySyncCompletedForWalletId = "wallet-1",
            hasSdk = true,
        ) shouldBe false
    }

    test("same wallet repeats explicit Spark wallet sync until recovery sync completed") {
        SparkSyncPolicy.shouldSyncWallet(
            currentLoadedWalletId = "wallet-1",
            requestedWalletId = "wallet-1",
            recoverySyncCompletedForWalletId = null,
            hasSdk = true,
        ) shouldBe true
    }

    test("manual refresh uses explicit Spark wallet sync") {
        SparkSyncPolicy.modeForManualRefresh() shouldBe SparkRefreshMode.ExplicitSdkSync
    }

    test("SDK event refresh only reads cached Spark state") {
        SparkSyncPolicy.modeForSdkEvent() shouldBe SparkRefreshMode.ReadCached
    }

    test("payment history pagination advances by Spark page size") {
        SparkPaymentHistoryPaging.nextOffset(100u) shouldBe 150u
    }
})
