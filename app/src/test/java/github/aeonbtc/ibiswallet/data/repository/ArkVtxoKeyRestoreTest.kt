package github.aeonbtc.ibiswallet.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ArkVtxoKeyRestoreTest : FunSpec({

    fun staticPeek(vararg addrs: String): suspend (Int) -> String? = { idx: Int ->
        addrs.getOrNull(idx)
    }

    test("empty known set never peeks") {
        var calls = 0
        val peek: suspend (Int) -> String? = { _: Int ->
            calls++
            "ark1x"
        }
        ArkRepository.planVtxoKeyRestore(known = emptySet(), maxIndex = 64, peek = peek) shouldBe -1
        calls shouldBe 0
    }

    test("negative maxIndex returns -1 without peeking") {
        var calls = 0
        val peek: suspend (Int) -> String? = { _: Int ->
            calls++
            "ark1x"
        }
        ArkRepository.planVtxoKeyRestore(known = setOf("ark1x"), maxIndex = -1, peek = peek) shouldBe -1
        calls shouldBe 0
    }

    test("finds highest matching index within bound") {
        val peek = staticPeek("ark1a", "ark1b", "ark1c", "ark1d", "ark1e")
        ArkRepository.planVtxoKeyRestore(
            known = setOf("ark1c"),
            maxIndex = 4,
            peek = peek,
        ) shouldBe 2
        ArkRepository.planVtxoKeyRestore(
            known = setOf("ark1a", "ark1e"),
            maxIndex = 4,
            peek = peek,
        ) shouldBe 4
    }

    test("sparse hits are all found") {
        val peek = staticPeek("ark1a", "ark1b", "ark1c", "ark1d", "ark1e", "ark1f")
        ArkRepository.planVtxoKeyRestore(
            known = setOf("ark1a", "ark1f"),
            maxIndex = 5,
            peek = peek,
        ) shouldBe 5
    }

    test("maxIndex bounds the scan") {
        var calls = 0
        val peek: suspend (Int) -> String? = { idx: Int ->
            calls++
            "ark1n$idx"
        }
        ArkRepository.planVtxoKeyRestore(
            known = setOf("ark1n2", "ark1n500"),
            maxIndex = 9,
            peek = peek,
        ) shouldBe 2
        calls shouldBe 10
    }

    test("comparison ignores case and whitespace") {
        val peek = staticPeek("ark1abc")
        ArkRepository.planVtxoKeyRestore(
            known = setOf("  ARK1ABC "),
            maxIndex = 0,
            peek = peek,
        ) shouldBe 0
    }

    test("unknown lineage returns -1") {
        val peek = staticPeek("ark1other0", "ark1other1")
        ArkRepository.planVtxoKeyRestore(
            known = setOf("ark1mine"),
            maxIndex = 1,
            peek = peek,
        ) shouldBe -1
    }

    test("null peek stops scan keeping best hit") {
        val peek: suspend (Int) -> String? = { idx: Int ->
            if (idx < 4) "ark1q$idx" else null
        }
        ArkRepository.planVtxoKeyRestore(
            known = setOf("ark1q1"),
            maxIndex = 64,
            peek = peek,
        ) shouldBe 1
    }
})
