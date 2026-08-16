package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkSyncSpinnerTest : FunSpec({
    test("first begin turns spinner on") {
        val spinner = ArkSyncSpinner()
        spinner.begin() shouldBe true
        spinner.isHeld() shouldBe true
    }

    test("nested begin does not re-arm") {
        val spinner = ArkSyncSpinner()
        spinner.begin() shouldBe true
        spinner.begin() shouldBe false
        spinner.isHeld() shouldBe true
    }

    test("last end turns spinner off") {
        val spinner = ArkSyncSpinner()
        spinner.begin()
        spinner.begin()
        spinner.end() shouldBe false
        spinner.isHeld() shouldBe true
        spinner.end() shouldBe true
        spinner.isHeld() shouldBe false
    }

    test("end without begin stays off") {
        val spinner = ArkSyncSpinner()
        spinner.end() shouldBe true
        spinner.isHeld() shouldBe false
    }

    test("reset clears nested holders") {
        val spinner = ArkSyncSpinner()
        spinner.begin()
        spinner.begin()
        spinner.reset()
        spinner.isHeld() shouldBe false
        spinner.begin() shouldBe true
    }
})
