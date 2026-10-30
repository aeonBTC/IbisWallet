package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArkNativeOperationsTest : FunSpec({
    test("cancellation retains the native lease until the accepted operation returns") {
        runTest {
            val operations = ArkNativeOperations()
            val owner = Any()
            val release = CompletableDeferred<Unit>()
            val job = launch { operations.run(owner) { release.await() } }
            runCurrent()
            job.cancel()
            runCurrent()
            val busyAfterCancel = operations.isBusy(owner)
            release.complete(Unit)
            job.join()
            busyAfterCancel shouldBe true
        }
    }

    test("snapshot waits for the outstanding native operation") {
        runTest {
            val operations = ArkNativeOperations()
            val owner = Any()
            val release = CompletableDeferred<Unit>()
            val operation = launch { operations.run(owner) { release.await() } }
            runCurrent()
            var copied = false
            val snapshot = launch { operations.snapshot(owner) { copied = true } }
            runCurrent()
            val copiedBeforeCompletion = copied
            release.complete(Unit)
            operation.join()
            snapshot.join()
            copiedBeforeCompletion shouldBe false
        }
    }

    test("a retired session refuses new native work") {
        runTest {
            val operations = ArkNativeOperations()
            val owner = Any()
            operations.retire(owner)
            val rejected = runCatching { operations.run(owner) { error("must not execute") } }.isFailure
            rejected shouldBe true
        }
    }

    test("a snapshot retains ownership while its file copy is running") {
        runTest {
            val operations = ArkNativeOperations()
            val owner = Any()
            val release = CompletableDeferred<Unit>()
            val snapshot = launch { operations.snapshot(owner) { release.await() } }
            runCurrent()
            val busyDuringCopy = operations.isBusy(owner)
            release.complete(Unit)
            snapshot.join()
            busyDuringCopy shouldBe true
        }
    }
})
