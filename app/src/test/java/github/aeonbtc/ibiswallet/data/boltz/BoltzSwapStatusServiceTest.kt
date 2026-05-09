package github.aeonbtc.ibiswallet.data.boltz

import github.aeonbtc.ibiswallet.data.model.BoltzSwapUpdate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest

class BoltzSwapStatusServiceTest : FunSpec({

    test("replays the latest update to a later awaiter") {
        runTest {
            val source = FakeBoltzSwapUpdatesSource()
            val service = BoltzSwapStatusService(source)

            try {
                val first =
                    service.awaitSwapActivity(
                        swapId = "swap-1",
                        timeoutMs = 1,
                        pollStatus = { "invoice.pending" },
                    )
                first?.status shouldBe "invoice.pending"

                val replayed = service.awaitSwapActivity(swapId = "swap-1", timeoutMs = 1)
                replayed?.status shouldBe "invoice.pending"
            } finally {
                service.close()
            }
        }
    }

    test("falls back to polling when the replayed update matches the previously handled status") {
        runTest {
            val source = FakeBoltzSwapUpdatesSource()
            val service = BoltzSwapStatusService(source)

            try {
                val first =
                    service.awaitSwapActivity(
                        swapId = "swap-2",
                        timeoutMs = 1,
                        pollStatus = { "invoice.pending" },
                    )
                first?.status shouldBe "invoice.pending"

                var polled = false
                val second =
                    service.awaitSwapActivity(
                        swapId = "swap-2",
                        timeoutMs = 20,
                        previousUpdate = first,
                        pollStatus = {
                            polled = true
                            "transaction.claimed"
                        },
                    )

                polled shouldBe true
                second?.status shouldBe "transaction.claimed"
            } finally {
                service.close()
            }
        }
    }

})

private class FakeBoltzSwapUpdatesSource : BoltzSwapUpdatesSource {
    private val flows = mutableMapOf<String, MutableSharedFlow<BoltzSwapUpdate>>()
    private val subscriptions = mutableMapOf<String, Int>()

    override fun subscribeToSwapUpdates(swapId: String): Flow<BoltzSwapUpdate> {
        subscriptions[swapId] = (subscriptions[swapId] ?: 0) + 1
        return flowFor(swapId)
    }

    suspend fun emit(swapId: String, status: String, transactionId: String? = null) {
        flowFor(swapId).emit(
            BoltzSwapUpdate(
                id = swapId,
                status = status,
                transactionId = transactionId,
            ),
        )
    }

    fun subscriptionCount(swapId: String): Int = subscriptions[swapId] ?: 0

    private fun flowFor(swapId: String): MutableSharedFlow<BoltzSwapUpdate> {
        return flows.getOrPut(swapId) {
            MutableSharedFlow(extraBufferCapacity = 8)
        }
    }
}
