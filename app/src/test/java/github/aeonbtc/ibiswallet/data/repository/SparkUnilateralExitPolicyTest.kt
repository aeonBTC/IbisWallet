package github.aeonbtc.ibiswallet.data.repository

import github.aeonbtc.ibiswallet.data.model.SparkExitBranchFunding
import github.aeonbtc.ibiswallet.data.model.SparkExitFundingUtxo
import github.aeonbtc.ibiswallet.data.model.SparkExitLiveUtxo
import github.aeonbtc.ibiswallet.data.model.SparkExitQuote
import github.aeonbtc.ibiswallet.data.model.SparkExitTx
import github.aeonbtc.ibiswallet.data.model.SparkExitTxKind
import github.aeonbtc.ibiswallet.data.model.SparkExitTxStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Traces Spark unilateral-exit guards used by the exit flow:
 * funding-script classification → weight bounds → fee clamp → quote economics.
 */
class SparkUnilateralExitPolicyTest : FunSpec({

    context("classifyFundingScript") {
        test("P2WPKH script classifies") {
            SparkUnilateralExitPolicy.classifyFundingScript(
                "0014" + "11".repeat(20),
            ) shouldBe SparkUnilateralExitPolicy.FundingScriptClass.P2WPKH
        }

        test("P2TR script classifies") {
            SparkUnilateralExitPolicy.classifyFundingScript(
                "5120" + "22".repeat(32),
            ) shouldBe SparkUnilateralExitPolicy.FundingScriptClass.P2TR
        }

        test("P2WSH script classifies") {
            SparkUnilateralExitPolicy.classifyFundingScript(
                "0020" + "33".repeat(32),
            ) shouldBe SparkUnilateralExitPolicy.FundingScriptClass.P2WSH
        }

        test("legacy P2PKH rejects") {
            SparkUnilateralExitPolicy.classifyFundingScript(
                "76a914" + "44".repeat(20) + "88ac",
            ) shouldBe null
        }

        test("P2SH (incl. nested SegWit) rejects") {
            SparkUnilateralExitPolicy.classifyFundingScript(
                "a914" + "55".repeat(20) + "87",
            ) shouldBe null
        }

        test("garbage rejects") {
            SparkUnilateralExitPolicy.classifyFundingScript("zzzz") shouldBe null
        }

        test("odd-length hex rejects") {
            SparkUnilateralExitPolicy.classifyFundingScript("001") shouldBe null
        }

        test("empty rejects") {
            SparkUnilateralExitPolicy.classifyFundingScript("") shouldBe null
        }

        test("uppercase + surrounding whitespace still classifies") {
            SparkUnilateralExitPolicy.classifyFundingScript(
                "  0014" + "AA".repeat(20) + "  ",
            ) shouldBe SparkUnilateralExitPolicy.FundingScriptClass.P2WPKH
        }

        test("right length but wrong prefix rejects") {
            // 68 hex chars starting with 0014: neither P2WSH (0020) nor P2TR (5120).
            SparkUnilateralExitPolicy.classifyFundingScript(
                "0014" + "66".repeat(32),
            ) shouldBe null
        }

        test("truncated P2WPKH rejects") {
            SparkUnilateralExitPolicy.classifyFundingScript("0014" + "11".repeat(19)) shouldBe null
        }
    }

    context("signedInputWeightFor") {
        test("P2WPKH bound covers worst-case witness") {
            // 41 non-witness bytes (164 WU) + 109 witness WU = 273 WU max.
            SparkUnilateralExitPolicy.signedInputWeightFor(
                SparkUnilateralExitPolicy.FundingScriptClass.P2WPKH,
            ) shouldBe 273L
        }

        test("P2TR keypath bound") {
            SparkUnilateralExitPolicy.signedInputWeightFor(
                SparkUnilateralExitPolicy.FundingScriptClass.P2TR,
            ) shouldBe 231L
        }

        test("P2WSH bound is the largest (script-defined witnesses)") {
            SparkUnilateralExitPolicy.signedInputWeightFor(
                SparkUnilateralExitPolicy.FundingScriptClass.P2WSH,
            ) shouldBe 1500L
        }
    }

    context("floorFeeRateSatPerVb") {
        test("zero floors to minimum") {
            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(0L) shouldBe 1L
        }

        test("negative floors to minimum") {
            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(-50L) shouldBe 1L
        }

        test("minimum passes through") {
            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(1L) shouldBe 1L
        }

        test("no ceiling: high rates pass through") {
            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(200L) shouldBe 200L
            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(1000L) shouldBe 1000L
            SparkUnilateralExitPolicy.floorFeeRateSatPerVb(100_000L) shouldBe 100_000L
        }
    }

    context("evaluateQuote") {
        test("empty selection is empty") {
            SparkUnilateralExitPolicy.evaluateQuote(
                leafCount = 0,
                recoverableValueSats = 0L,
                totalFeeSats = 0L,
            ).shouldBeInstanceOf<SparkUnilateralExitPolicy.QuoteEconomics.Empty>()
        }

        test("fee exceeding recovery is not worth it") {
            val result =
                SparkUnilateralExitPolicy.evaluateQuote(
                    leafCount = 2,
                    recoverableValueSats = 1000L,
                    totalFeeSats = 1200L,
                )
            result.shouldBeInstanceOf<SparkUnilateralExitPolicy.QuoteEconomics.NotWorthIt>()
        }

        test("fee exactly equal to recovery is not worth it (strictly above required)") {
            val result =
                SparkUnilateralExitPolicy.evaluateQuote(
                    leafCount = 1,
                    recoverableValueSats = 1200L,
                    totalFeeSats = 1200L,
                )
            result.shouldBeInstanceOf<SparkUnilateralExitPolicy.QuoteEconomics.NotWorthIt>()
        }

        test("dust margin of one sat is worth it") {
            SparkUnilateralExitPolicy.evaluateQuote(
                leafCount = 1,
                recoverableValueSats = 1201L,
                totalFeeSats = 1200L,
            ) shouldBe SparkUnilateralExitPolicy.QuoteEconomics.WorthIt
        }

        test("positive margin is worth it") {
            SparkUnilateralExitPolicy.evaluateQuote(
                leafCount = 1,
                recoverableValueSats = 5000L,
                totalFeeSats = 1200L,
            ) shouldBe SparkUnilateralExitPolicy.QuoteEconomics.WorthIt
        }
    }

    context("isQuoteCoveringBalance") {
        test("exact cover passes") {
            SparkUnilateralExitPolicy.isQuoteCoveringBalance(
                balanceSats = 24_000L,
                quotedLeafSats = 24_000L,
            ) shouldBe true
        }

        test("dust skew under the floor passes") {
            SparkUnilateralExitPolicy.isQuoteCoveringBalance(
                balanceSats = 24_000L,
                quotedLeafSats = 23_500L,
            ) shouldBe true
        }

        test("missing leaves fail") {
            SparkUnilateralExitPolicy.isQuoteCoveringBalance(
                balanceSats = 24_000L,
                quotedLeafSats = 8_000L,
            ) shouldBe false
        }

        test("spend landing mid-quote fails") {
            SparkUnilateralExitPolicy.isQuoteCoveringBalance(
                balanceSats = 8_000L,
                quotedLeafSats = 24_000L,
            ) shouldBe false
        }

        test("empty wallet with empty quote passes") {
            SparkUnilateralExitPolicy.isQuoteCoveringBalance(
                balanceSats = 0L,
                quotedLeafSats = 0L,
            ) shouldBe true
        }

        test("corrupt negative inputs do not nag") {
            SparkUnilateralExitPolicy.isQuoteCoveringBalance(
                balanceSats = -1L,
                quotedLeafSats = 8_000L,
            ) shouldBe true
        }
    }

    context("preferPerBranchFunding") {
        test("negative margin prefers per-branch") {
            SparkUnilateralExitPolicy.preferPerBranchFunding(
                recoverableValueSats = 1000L,
                totalFeeSats = 1200L,
            ) shouldBe true
        }

        test("thin margin prefers per-branch") {
            SparkUnilateralExitPolicy.preferPerBranchFunding(
                recoverableValueSats = 1000L,
                totalFeeSats = 950L,
            ) shouldBe true
        }

        test("exactly 10% margin keeps single UTXO") {
            // margin * 10 < recoverable is strict: 100 * 10 < 1000 is false.
            SparkUnilateralExitPolicy.preferPerBranchFunding(
                recoverableValueSats = 1000L,
                totalFeeSats = 900L,
            ) shouldBe false
        }

        test("just under 10% margin prefers per-branch") {
            SparkUnilateralExitPolicy.preferPerBranchFunding(
                recoverableValueSats = 1000L,
                totalFeeSats = 901L,
            ) shouldBe true
        }

        test("wide margin keeps single UTXO") {
            SparkUnilateralExitPolicy.preferPerBranchFunding(
                recoverableValueSats = 10000L,
                totalFeeSats = 1000L,
            ) shouldBe false
        }
    }

    context("requiredFundingSats") {
        test("sums per-branch amounts") {
            SparkUnilateralExitPolicy.requiredFundingSats(
                singleUtxoFundingSats = 900L,
                perBranchFunding = listOf(500L, 400L),
                usePerBranch = true,
            ) shouldBe 900L
        }

        test("single mode ignores per-branch list") {
            SparkUnilateralExitPolicy.requiredFundingSats(
                singleUtxoFundingSats = 700L,
                perBranchFunding = listOf(500L, 400L),
                usePerBranch = false,
            ) shouldBe 700L
        }

        test("empty per-branch list sums to zero") {
            SparkUnilateralExitPolicy.requiredFundingSats(
                singleUtxoFundingSats = 700L,
                perBranchFunding = emptyList(),
                usePerBranch = true,
            ) shouldBe 0L
        }
    }

    context("isFundingSelectionReady") {
        test("single mode: exactly one sufficient UTXO passes") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 1,
                selectedTotalSats = 900L,
                singleUtxoFundingSats = 900L,
                perBranchFundingSats = listOf(500L, 400L),
                usePerBranch = false,
            ) shouldBe true
        }

        test("single mode: two UTXOs never pass (fan-out takes one)") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 2,
                selectedTotalSats = 2000L,
                singleUtxoFundingSats = 900L,
                perBranchFundingSats = listOf(500L, 400L),
                usePerBranch = false,
            ) shouldBe false
        }

        test("single mode: underfunded UTXO fails") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 1,
                selectedTotalSats = 899L,
                singleUtxoFundingSats = 900L,
                perBranchFundingSats = emptyList(),
                usePerBranch = false,
            ) shouldBe false
        }

        test("per-branch mode: one UTXO per branch covering the total passes") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 2,
                selectedTotalSats = 900L,
                singleUtxoFundingSats = 950L,
                perBranchFundingSats = listOf(500L, 400L),
                usePerBranch = true,
            ) shouldBe true
        }

        test("per-branch mode: fewer UTXOs than branches fails") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 1,
                selectedTotalSats = 900L,
                singleUtxoFundingSats = 950L,
                perBranchFundingSats = listOf(500L, 400L),
                usePerBranch = true,
            ) shouldBe false
        }

        test("per-branch mode: underfunded total fails") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 2,
                selectedTotalSats = 899L,
                singleUtxoFundingSats = 950L,
                perBranchFundingSats = listOf(500L, 400L),
                usePerBranch = true,
            ) shouldBe false
        }

        test("empty selection never passes") {
            SparkUnilateralExitPolicy.isFundingSelectionReady(
                selectedCount = 0,
                selectedTotalSats = 0L,
                singleUtxoFundingSats = 0L,
                perBranchFundingSats = emptyList(),
                usePerBranch = true,
            ) shouldBe false
        }
    }

    context("isExitBuildFundingSufficient") {
        fun quote(
            recoverable: Long = 10_000L,
            totalFee: Long = 1_000L,
            singleUtxo: Long = 700L,
            perBranch: List<Long> = listOf(500L, 400L),
        ) = SparkExitQuote(
            leafIds = listOf("leaf-a", "leaf-b"),
            leaves = emptyList(),
            recoverableValueSats = recoverable,
            totalFeeSats = totalFee,
            fanoutFeeSats = 300L,
            singleUtxoFundingSats = singleUtxo,
            perBranchFunding = perBranch.mapIndexed { i, sats ->
                SparkExitBranchFunding("leaf-$i", sats)
            },
            feeRateSatPerVb = 2L,
            destination = "bc1qtest",
        )

        test("wide margin uses single-UTXO mode: one sufficient UTXO passes") {
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = quote(),
                selectedCount = 1,
                selectedTotalSats = 700L,
            ) shouldBe true
        }

        test("wide margin single mode: two UTXOs fail even when overfunded") {
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = quote(),
                selectedCount = 2,
                selectedTotalSats = 2_000L,
            ) shouldBe false
        }

        test("wide margin single mode: underfunded UTXO fails") {
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = quote(),
                selectedCount = 1,
                selectedTotalSats = 699L,
            ) shouldBe false
        }

        test("thin margin derives per-branch mode: one UTXO fails despite covering single amount") {
            // 5% margin → per-branch mode: 1 UTXO < 2 branches fails.
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = quote(recoverable = 1_000L, totalFee = 950L, singleUtxo = 900L),
                selectedCount = 1,
                selectedTotalSats = 900L,
            ) shouldBe false
        }

        test("thin margin per-branch mode: one UTXO per branch covering total passes") {
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = quote(recoverable = 1_000L, totalFee = 950L, singleUtxo = 900L),
                selectedCount = 2,
                selectedTotalSats = 900L,
            ) shouldBe true
        }

        test("empty selection never builds") {
            SparkUnilateralExitPolicy.isExitBuildFundingSufficient(
                quote = quote(),
                selectedCount = 0,
                selectedTotalSats = 0L,
            ) shouldBe false
        }
    }

    context("isPlausibleBitcoinAddress") {
        test("mainnet P2WPKH passes") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            ) shouldBe true
        }

        test("mainnet P2TR passes") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "bc1p0xlxvlhee94chsfks7cx2zmv6g6q25aztsf0hvzuqc7vsem0s2kseuz0py",
            ) shouldBe true
        }

        test("mainnet P2PKH passes") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            ) shouldBe true
        }

        test("mainnet P2SH passes") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
            ) shouldBe true
        }

        test("testnet P2WPKH passes") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
            ) shouldBe true
        }

        test("bad bech32 checksum rejects") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5",
            ) shouldBe false
        }

        test("bad base58 checksum rejects") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb",
            ) shouldBe false
        }

        test("silent payment code rejects") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "sp1qqw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4qq",
            ) shouldBe false
        }

        test("empty and garbage reject") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress("") shouldBe false
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress("   ") shouldBe false
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress("not-an-address") shouldBe false
        }

        test("surrounding whitespace is tolerated") {
            SparkUnilateralExitPolicy.isPlausibleBitcoinAddress(
                "  bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4  ",
            ) shouldBe true
        }
    }

    context("validateFundingAgainstLive") {
        val funding =
            listOf(
                SparkExitFundingUtxo(
                    txid = "aa".repeat(32),
                    vout = 0u,
                    valueSats = 50_000L,
                    scriptPubkeyHex = "0014" + "11".repeat(20),
                    signedInputWeight = 273L,
                ),
            )
        val liveOk = listOf(SparkExitLiveUtxo(outpoint = "${"aa".repeat(32)}:0", isConfirmed = true, isFrozen = false))

        test("live funding passes") {
            SparkUnilateralExitPolicy.validateFundingAgainstLive(funding, liveOk).shouldBeNull()
        }

        test("empty funding fails") {
            SparkUnilateralExitPolicy.validateFundingAgainstLive(emptyList(), liveOk).shouldNotBeNull()
        }

        test("spent funding names the outpoint") {
            val err = SparkUnilateralExitPolicy.validateFundingAgainstLive(funding, emptyList())
            err.shouldNotBeNull()
            (err.contains("aa".repeat(32))) shouldBe true
        }

        test("unconfirmed funding fails") {
            val err =
                SparkUnilateralExitPolicy.validateFundingAgainstLive(
                    funding,
                    listOf(SparkExitLiveUtxo(outpoint = "${"aa".repeat(32)}:0", isConfirmed = false, isFrozen = false)),
                )
            err.shouldNotBeNull()
        }

        test("frozen funding fails") {
            val err =
                SparkUnilateralExitPolicy.validateFundingAgainstLive(
                    funding,
                    listOf(SparkExitLiveUtxo(outpoint = "${"aa".repeat(32)}:0", isConfirmed = true, isFrozen = true)),
                )
            err.shouldNotBeNull()
        }

        test("malformed funding fails") {
            SparkUnilateralExitPolicy.validateFundingAgainstLive(
                listOf(funding.first().copy(valueSats = 0L)),
                liveOk,
            ).shouldNotBeNull()
            SparkUnilateralExitPolicy.validateFundingAgainstLive(
                listOf(funding.first().copy(txid = "")),
                liveOk,
            ).shouldNotBeNull()
        }

        test("non-SegWit script fails") {
            SparkUnilateralExitPolicy.validateFundingAgainstLive(
                listOf(funding.first().copy(scriptPubkeyHex = "76a914" + "44".repeat(20) + "88ac")),
                liveOk,
            ).shouldNotBeNull()
        }
    }

    context("broadcastReadinessOf") {
        fun tx(
            txid: String,
            dependsOn: List<String> = emptyList(),
            status: SparkExitTxStatus = SparkExitTxStatus.READY,
            spendableAtHeight: UInt? = null,
        ) = SparkExitTx(
            txid = txid,
            txHex = "hex-$txid",
            kind = SparkExitTxKind.NODE,
            dependsOn = dependsOn,
            status = status,
            spendableAtHeight = spendableAtHeight,
        )

        test("ready status broadcasts") {
            SparkUnilateralExitPolicy.broadcastReadinessOf(tx("a")) shouldBe
                SparkUnilateralExitPolicy.BroadcastReadiness.Ready
        }

        test("confirmed needs no broadcast") {
            SparkUnilateralExitPolicy.broadcastReadinessOf(tx("a", status = SparkExitTxStatus.CONFIRMED)) shouldBe
                SparkUnilateralExitPolicy.BroadcastReadiness.AlreadyConfirmed
        }

        test("waiting on dependencies blocks") {
            SparkUnilateralExitPolicy.broadcastReadinessOf(
                tx("c", dependsOn = listOf("p"), status = SparkExitTxStatus.WAITING_FOR_DEPENDENCIES),
            ) shouldBe SparkUnilateralExitPolicy.BroadcastReadiness.WaitingOnDependencies
        }

        test("waiting on timelock carries spendable height") {
            val readiness =
                SparkUnilateralExitPolicy.broadcastReadinessOf(
                    tx("c", status = SparkExitTxStatus.WAITING_FOR_TIMELOCK, spendableAtHeight = 900_000u),
                )
            readiness.shouldBeInstanceOf<SparkUnilateralExitPolicy.BroadcastReadiness.WaitingOnTimelock>()
            readiness.spendableAtHeight shouldBe 900_000u
        }

        test("unverified never broadcasts") {
            SparkUnilateralExitPolicy.broadcastReadinessOf(tx("a", status = SparkExitTxStatus.UNVERIFIED)) shouldBe
                SparkUnilateralExitPolicy.BroadcastReadiness.Unknown
        }
    }

    context("isExitComplete") {
        fun tx(status: SparkExitTxStatus) =
            SparkExitTx(
                txid = "t-${status.name}",
                txHex = "hex",
                kind = SparkExitTxKind.NODE,
                status = status,
            )

        test("empty set never completes") {
            SparkUnilateralExitPolicy.isExitComplete(emptyList()) shouldBe false
        }

        test("non-confirmed statuses never complete") {
            SparkUnilateralExitPolicy.isExitComplete(
                listOf(
                    tx(SparkExitTxStatus.READY),
                    tx(SparkExitTxStatus.WAITING_FOR_DEPENDENCIES),
                    tx(SparkExitTxStatus.WAITING_FOR_TIMELOCK),
                    tx(SparkExitTxStatus.UNVERIFIED),
                ),
            ) shouldBe false
        }

        test("all confirmed completes") {
            SparkUnilateralExitPolicy.isExitComplete(
                listOf(tx(SparkExitTxStatus.CONFIRMED), tx(SparkExitTxStatus.CONFIRMED)),
            ) shouldBe true
        }

        test("one ready blocks completion") {
            SparkUnilateralExitPolicy.isExitComplete(
                listOf(tx(SparkExitTxStatus.CONFIRMED), tx(SparkExitTxStatus.READY)),
            ) shouldBe false
        }
    }

    context("normalizeExitStateHex") {
        test("valid hex passes through lowercased") {
            SparkUnilateralExitPolicy.normalizeExitStateHex("  AABBccdd\n") shouldBe "aabbccdd"
        }

        test("blank rejects") {
            runCatching { SparkUnilateralExitPolicy.normalizeExitStateHex("   \n  ") }
                .exceptionOrNull()
                .shouldBeInstanceOf<IllegalArgumentException>()
        }

        test("odd length rejects") {
            runCatching { SparkUnilateralExitPolicy.normalizeExitStateHex("abc") }
                .exceptionOrNull()
                .shouldBeInstanceOf<IllegalArgumentException>()
        }

        test("non-hex rejects") {
            runCatching { SparkUnilateralExitPolicy.normalizeExitStateHex("zz") }
                .exceptionOrNull()
                .shouldBeInstanceOf<IllegalArgumentException>()
        }

        test("oversize rejects") {
            val big = "ab".repeat(SparkUnilateralExitPolicy.EXIT_STATE_HEX_MAX_CHARS / 2 + 1)
            runCatching { SparkUnilateralExitPolicy.normalizeExitStateHex(big) }
                .exceptionOrNull()
                .shouldBeInstanceOf<IllegalArgumentException>()
        }
    }

        context("isPlausibleClaimQuote") {
        test("consistent quote passes") {
            SparkUnilateralExitPolicy.isPlausibleClaimQuote(
                amountSats = 50_000L,
                creditSats = 49_200L,
                feeSats = 800L,
            ) shouldBe true
        }

        test("zero amount hides") {
            SparkUnilateralExitPolicy.isPlausibleClaimQuote(
                amountSats = 0L,
                creditSats = 49_200L,
                feeSats = 800L,
            ) shouldBe false
        }

        test("zero credit hides") {
            SparkUnilateralExitPolicy.isPlausibleClaimQuote(
                amountSats = 50_000L,
                creditSats = 0L,
                feeSats = 800L,
            ) shouldBe false
        }

        test("credit above amount hides") {
            SparkUnilateralExitPolicy.isPlausibleClaimQuote(
                amountSats = 50_000L,
                creditSats = 60_000L,
                feeSats = 800L,
            ) shouldBe false
        }

        test("negative fee hides") {
            SparkUnilateralExitPolicy.isPlausibleClaimQuote(
                amountSats = 50_000L,
                creditSats = 49_200L,
                feeSats = -1L,
            ) shouldBe false
        }
    }

    context("validateClaimCeiling") {        test("sane ceiling passes") {
            SparkUnilateralExitPolicy.validateClaimCeiling(
                amountSats = 50_000L,
                maxFeeSats = 800L,
            ).shouldBeNull()
        }

        test("zero ceiling fails") {
            SparkUnilateralExitPolicy.validateClaimCeiling(
                amountSats = 50_000L,
                maxFeeSats = 0L,
            ).shouldNotBeNull()
        }

        test("negative ceiling fails") {
            SparkUnilateralExitPolicy.validateClaimCeiling(
                amountSats = 50_000L,
                maxFeeSats = -5L,
            ).shouldNotBeNull()
        }

        test("fee at or above value fails") {
            SparkUnilateralExitPolicy.validateClaimCeiling(
                amountSats = 50_000L,
                maxFeeSats = 50_000L,
            ).shouldNotBeNull()
            SparkUnilateralExitPolicy.validateClaimCeiling(
                amountSats = 50_000L,
                maxFeeSats = 60_000L,
            ).shouldNotBeNull()
        }
    }

    context("quotesMatch") {        fun quote(
            leafIds: List<String> = listOf("leaf-a"),
            destination: String = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            feeRate: Long = 2L,
        ) = SparkExitQuote(
            leafIds = leafIds,
            leaves = emptyList(),
            recoverableValueSats = 10_000L,
            totalFeeSats = 1_000L,
            fanoutFeeSats = 0L,
            singleUtxoFundingSats = 700L,
            perBranchFunding = emptyList(),
            feeRateSatPerVb = feeRate,
            destination = destination,
        )

        test("identical quotes match") {
            SparkUnilateralExitPolicy.quotesMatch(quote(), quote()) shouldBe true
        }

        test("re-quote with different leaves mismatches") {
            SparkUnilateralExitPolicy.quotesMatch(quote(), quote(leafIds = listOf("leaf-b"))) shouldBe false
        }

        test("destination change mismatches") {
            SparkUnilateralExitPolicy.quotesMatch(
                quote(),
                quote(destination = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"),
            ) shouldBe false
        }

        test("fee rate change mismatches") {
            SparkUnilateralExitPolicy.quotesMatch(quote(), quote(feeRate = 3L)) shouldBe false
        }
    }
})
