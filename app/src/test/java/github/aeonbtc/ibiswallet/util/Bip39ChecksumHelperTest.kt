package github.aeonbtc.ibiswallet.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

private fun loadRealWordlist(): List<String> {
    val candidates =
        listOf(
            File("src/main/assets/bip39_english.txt"),
            File("app/src/main/assets/bip39_english.txt"),
        )
    return candidates.first { it.isFile }.readLines().map { it.trim() }.filter { it.isNotEmpty() }
}

class Bip39ChecksumHelperTest : FunSpec({

    // Minimal fake wordlist is not usable (helper requires 2048 words), so build
    // a deterministic 2048-word list. Checksum math only needs stable indices.
    val wordlist = List(2048) { index -> "word%04d".format(index) }

    context("checksumCandidates") {
        test("11 words yield 128 candidates") {
            val prefix = List(11) { wordlist[0] }
            Bip39ChecksumHelper.checksumCandidates(prefix, wordlist).size shouldBe 128
        }

        test("23 words yield 8 candidates") {
            val prefix = List(23) { wordlist[0] }
            Bip39ChecksumHelper.checksumCandidates(prefix, wordlist).size shouldBe 8
        }

        test("every candidate forms a valid mnemonic") {
            val prefix = List(11) { wordlist[it * 7 % 2048] }
            val candidates = Bip39ChecksumHelper.checksumCandidates(prefix, wordlist)
            candidates.size shouldBe 128
            candidates.forEach { candidate ->
                Bip39ChecksumHelper.isValidMnemonic(prefix + candidate, wordlist) shouldBe true
            }
        }

        test("unsupported prefix count returns empty") {
            Bip39ChecksumHelper.checksumCandidates(List(12) { wordlist[0] }, wordlist) shouldBe emptyList()
            Bip39ChecksumHelper.checksumCandidates(List(10) { wordlist[0] }, wordlist) shouldBe emptyList()
        }

        test("unknown prefix word returns empty") {
            Bip39ChecksumHelper.checksumCandidates(List(11) { "nope" }, wordlist) shouldBe emptyList()
        }

        test("non-2048 wordlist returns empty") {
            Bip39ChecksumHelper.checksumCandidates(List(11) { "word0000" }, listOf("a", "b")) shouldBe emptyList()
        }
    }

    context("isValidMnemonic") {
        test("rejects wrong length") {
            Bip39ChecksumHelper.isValidMnemonic(List(11) { wordlist[0] }, wordlist) shouldBe false
        }

        test("rejects unknown word") {
            Bip39ChecksumHelper.isValidMnemonic(List(12) { "nope" }, wordlist) shouldBe false
        }

        test("accepts helper-produced 24-word phrase") {
            val prefix = List(23) { wordlist[0] }
            val candidate = Bip39ChecksumHelper.checksumCandidates(prefix, wordlist).first()
            Bip39ChecksumHelper.isValidMnemonic(prefix + candidate, wordlist) shouldBe true
        }
    }

    context("real BIP39 English wordlist") {
        // Official BIP39 test vector: entropy 7f7f...7f
        val vector = "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" ")

        test("wordlist loads 2048 words") {
            loadRealWordlist().size shouldBe 2048
        }

        test("official vector validates") {
            Bip39ChecksumHelper.isValidMnemonic(vector, loadRealWordlist()) shouldBe true
        }

        test("all-zero entropy phrase is invalid") {
            // SHA-256(16 zero bytes) starts with 0x37, so checksum nibble 0011 != 0000
            Bip39ChecksumHelper.isValidMnemonic(List(12) { "abandon" }, loadRealWordlist()) shouldBe false
        }

        test("11x abandon completes with about among 128") {
            val candidates = Bip39ChecksumHelper.checksumCandidates(List(11) { "abandon" }, loadRealWordlist())
            candidates.size shouldBe 128
            candidates shouldContain "about"
        }

        test("vector prefix completes with yellow") {
            val candidates =
                Bip39ChecksumHelper.checksumCandidates(vector.dropLast(1), loadRealWordlist())
            candidates.size shouldBe 128
            candidates shouldContain "yellow"
        }

        test("all-zero entropy phrase validates (abandon x11 + about)") {
            val zeroEntropy = List(11) { "abandon" } + "about"
            Bip39ChecksumHelper.isValidMnemonic(zeroEntropy, loadRealWordlist()) shouldBe true
        }

        test("23x abandon yields 8 valid completions") {
            val prefix = List(23) { "abandon" }
            val candidates = Bip39ChecksumHelper.checksumCandidates(prefix, loadRealWordlist())
            candidates.size shouldBe 8
            candidates.forEach { candidate ->
                Bip39ChecksumHelper.isValidMnemonic(prefix + candidate, loadRealWordlist()) shouldBe true
            }
        }
    }

    context("generate dice entropy") {
        test("minimum rolls are 50 for 12 words and 100 for 24") {
            Bip39ChecksumHelper.minGenerateDiceRolls(12) shouldBe 50
            Bip39ChecksumHelper.minGenerateDiceRolls(24) shouldBe 100
        }

        test("validation enforces digit set and minimum") {
            Bip39ChecksumHelper.isValidDiceRolls("6".repeat(50), 50) shouldBe true
            Bip39ChecksumHelper.isValidDiceRolls("6".repeat(49), 50) shouldBe false
            Bip39ChecksumHelper.isValidDiceRolls("6".repeat(49) + "7", 50) shouldBe false
            Bip39ChecksumHelper.isValidDiceRolls(" 61616 ", 5) shouldBe true
            Bip39ChecksumHelper.isValidDiceRolls("", 50) shouldBe false
        }

        test("dice-only entropy is deterministic and sized") {
            val first = Bip39ChecksumHelper.diceOnlyEntropy("6".repeat(50), 16)
            first.size shouldBe 16
            Bip39ChecksumHelper.diceOnlyEntropy("6".repeat(50), 16) shouldBe first
            Bip39ChecksumHelper.diceOnlyEntropy("6".repeat(49) + "5", 16) shouldNotBe first
        }

        test("dice-only entropy equals SHA-256 of rolls (full and truncated)") {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest("123456".toByteArray())
            Bip39ChecksumHelper.diceOnlyEntropy("123456", 32) shouldBe digest
            Bip39ChecksumHelper.diceOnlyEntropy("123456", 16) shouldBe digest.copyOf(16)
            Bip39ChecksumHelper.diceOnlyEntropy("1 2 3 4 5 6", 32) shouldBe digest
        }

        test("diceEntropyOrNull returns null on blank and sized entropy otherwise") {
            Bip39ChecksumHelper.diceEntropyOrNull("   ", 12) shouldBe null
            Bip39ChecksumHelper.diceEntropyOrNull("", 24) shouldBe null
            val entropy12 = Bip39ChecksumHelper.diceEntropyOrNull("6".repeat(50), 12)
            entropy12!!.size shouldBe 16
            entropy12 shouldBe Bip39ChecksumHelper.diceOnlyEntropy("6".repeat(50), 16)
            Bip39ChecksumHelper.diceEntropyOrNull("6".repeat(100), 24)!!.size shouldBe 32
        }
    }

    context("diceRollsToIndex") {        test("is deterministic and in range") {
            val first = Bip39ChecksumHelper.diceRollsToIndex("63214", 128)
            first shouldNotBe null
            (first!! in 0 until 128) shouldBe true
            Bip39ChecksumHelper.diceRollsToIndex("63214", 128) shouldBe first
            Bip39ChecksumHelper.diceRollsToIndex("6 3 2 1 4", 128) shouldBe first
        }

        test("different rolls usually differ") {
            // Not guaranteed by contract, but true for SHA-256 on these inputs
            Bip39ChecksumHelper.diceRollsToIndex("11111", 128) shouldNotBe
                Bip39ChecksumHelper.diceRollsToIndex("66666", 128)
        }

        test("rejects bad input") {
            Bip39ChecksumHelper.diceRollsToIndex("6", 128) shouldBe null
            Bip39ChecksumHelper.diceRollsToIndex("6161", 128) shouldBe null
            Bip39ChecksumHelper.diceRollsToIndex("61207", 128) shouldBe null
            Bip39ChecksumHelper.diceRollsToIndex("abcdef", 128) shouldBe null
            Bip39ChecksumHelper.diceRollsToIndex("", 128) shouldBe null
            Bip39ChecksumHelper.diceRollsToIndex("61616", 0) shouldBe null
        }

        test("covers all candidate indices without bias gaps") {
            // Deterministic d6 inputs: base-6 counter mapped to digits 1-6.
            fun rollsFor(n: Int): String {
                var value = n
                val chars = CharArray(8)
                for (i in chars.indices) {
                    chars[i] = ('1' + (value % 6)).toString()[0]
                    value /= 6
                }
                return String(chars)
            }
            val seen = HashSet<Int>()
            for (n in 0 until 5000) {
                seen.add(Bip39ChecksumHelper.diceRollsToIndex(rollsFor(n), 128)!!)
            }
            seen.size shouldBe 128
        }
    }
})
