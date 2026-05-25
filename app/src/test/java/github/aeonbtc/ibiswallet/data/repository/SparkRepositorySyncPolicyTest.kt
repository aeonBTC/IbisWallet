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

    test("network recovery runs only when a Spark wallet is loaded") {
        SparkSyncPolicy.shouldForceReconnectOnNetworkChange(loadedWalletId = null) shouldBe false
        SparkSyncPolicy.shouldForceReconnectOnNetworkChange(loadedWalletId = "wallet-1") shouldBe true
    }

    test("reconnect debouncer coalesces rapid schedule requests") {
        var now = 0L
        val debouncer = SparkReconnectDebouncer(clock = { now })

        debouncer.recordScheduleRequest() shouldBe true
        debouncer.shouldRunReconnect() shouldBe false

        now += SPARK_NETWORK_RECONNECT_DEBOUNCE_MS
        debouncer.shouldRunReconnect() shouldBe true
        debouncer.markReconnectExecuted()

        now += 1_000L
        debouncer.recordScheduleRequest() shouldBe false
    }

    test("reconnect debouncer enforces minimum interval between reconnects") {
        var now = 0L
        val debouncer = SparkReconnectDebouncer(clock = { now })

        debouncer.recordScheduleRequest()
        now += SPARK_NETWORK_RECONNECT_DEBOUNCE_MS
        debouncer.shouldRunReconnect() shouldBe true
        debouncer.markReconnectExecuted()

        now += SPARK_NETWORK_RECONNECT_DEBOUNCE_MS
        debouncer.recordScheduleRequest() shouldBe false
        debouncer.shouldRunReconnect() shouldBe false

        now += SPARK_NETWORK_RECONNECT_MIN_INTERVAL_MS
        debouncer.recordScheduleRequest() shouldBe true
    }
})
