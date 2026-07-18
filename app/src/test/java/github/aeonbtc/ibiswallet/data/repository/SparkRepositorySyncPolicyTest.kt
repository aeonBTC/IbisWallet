package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
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

    test("claimed deposit matcher accepts settled receive payment with same txid and amount") {
        SparkDepositClaimMatcher.matches(
            deposit = sparkDeposit(txid = "a".repeat(64), amountSats = 165_787L),
            payment = sparkReceivePayment(
                status = "COMPLETE",
                amountSats = 165_787L,
                methodDetails = "BitcoinDeposit(txid=${"a".repeat(64)})",
            ),
        ) shouldBe true
    }

    test("claimed deposit matcher rejects pending payment") {
        SparkDepositClaimMatcher.matches(
            deposit = sparkDeposit(txid = "b".repeat(64), amountSats = 165_787L),
            payment = sparkReceivePayment(
                status = "PENDING",
                amountSats = 165_787L,
                methodDetails = "BitcoinDeposit(txid=${"b".repeat(64)})",
            ),
        ) shouldBe false
    }

    test("claimed deposit matcher rejects amount mismatch") {
        SparkDepositClaimMatcher.matches(
            deposit = sparkDeposit(txid = "c".repeat(64), amountSats = 165_787L),
            payment = sparkReceivePayment(
                status = "COMPLETE",
                amountSats = 165_788L,
                methodDetails = "BitcoinDeposit(txid=${"c".repeat(64)})",
            ),
        ) shouldBe false
    }

    test("claimed deposit matcher accepts SDK unclaimed deposit already present in payment history") {
        val txid = "d".repeat(64)
        val payments = listOf(
            sparkReceivePayment(
                status = "COMPLETE",
                amountSats = 165_787L,
                methodDetails = "BitcoinDeposit(txid=$txid)",
            ),
        )

        payments.any { payment ->
            SparkDepositClaimMatcher.matches(
                deposit = sparkDeposit(txid = txid, amountSats = 165_787L),
                payment = payment,
            )
        } shouldBe true
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

private fun sparkDeposit(
    txid: String,
    amountSats: Long,
): SparkUnclaimedDeposit =
    SparkUnclaimedDeposit(
        txid = txid,
        vout = 0u,
        amountSats = amountSats,
        isMature = false,
    )

private fun sparkReceivePayment(
    status: String,
    amountSats: Long,
    methodDetails: String,
): SparkPayment =
    SparkPayment(
        id = "payment-id",
        type = "RECEIVE",
        status = status,
        amountSats = amountSats,
        feeSats = 0L,
        timestamp = 0L,
        method = "Bitcoin",
        methodDetails = methodDetails,
    )
