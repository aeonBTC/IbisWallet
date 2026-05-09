package github.aeonbtc.ibiswallet.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class LiquidAssetTest : FunSpec({

    context("LiquidAsset.resolve") {
        test("resolves known L-BTC asset ID") {
            val asset = LiquidAsset.resolve(LiquidAsset.LBTC_ASSET_ID)

            asset.ticker shouldBe "L-BTC"
            asset.name shouldBe "Liquid Bitcoin"
            asset.isPolicyAsset shouldBe true
        }

        test("resolves known USDT asset ID") {
            val asset = LiquidAsset.resolve(LiquidAsset.USDT_ASSET_ID)

            asset.ticker shouldBe "USDt"
            asset.name shouldBe "Tether USD"
            asset.isPolicyAsset shouldBe false
        }

        test("unknown asset ID gets truncated hex ticker") {
            val unknownId = "aaabbbccc111222333444555666777888999000aaabbbccc111222333444555666"
            val asset = LiquidAsset.resolve(unknownId)

            asset.ticker shouldContain "aaabbbcc"
            asset.name shouldBe "Unknown Asset"
            asset.isPolicyAsset shouldBe false
        }
    }

    context("LiquidAsset.isPolicyAsset companion") {
        test("L-BTC is policy asset") {
            LiquidAsset.isPolicyAsset(LiquidAsset.LBTC_ASSET_ID) shouldBe true
        }

        test("USDT is not policy asset") {
            LiquidAsset.isPolicyAsset(LiquidAsset.USDT_ASSET_ID) shouldBe false
        }
    }

    context("LiquidWalletState asset-aware helpers") {
        test("balanceForAsset returns correct balance") {
            val state = LiquidWalletState(
                balanceSats = 50_000L,
                assetBalances = listOf(
                    LiquidAssetBalance(LiquidAsset.LBTC, 50_000L),
                    LiquidAssetBalance(LiquidAsset.USDT, 1_000_000_000L),
                ),
            )

            state.balanceForAsset(LiquidAsset.LBTC_ASSET_ID) shouldBe 50_000L
            state.balanceForAsset(LiquidAsset.USDT_ASSET_ID) shouldBe 1_000_000_000L
        }

        test("balanceForAsset returns 0 for missing asset") {
            val state = LiquidWalletState(
                balanceSats = 50_000L,
                assetBalances = listOf(
                    LiquidAssetBalance(LiquidAsset.LBTC, 50_000L),
                ),
            )

            state.balanceForAsset(LiquidAsset.USDT_ASSET_ID) shouldBe 0L
        }

        test("usdtBalanceAmount extracts USDT balance") {
            val state = LiquidWalletState(
                assetBalances = listOf(
                    LiquidAssetBalance(LiquidAsset.LBTC, 50_000L),
                    LiquidAssetBalance(LiquidAsset.USDT, 500_000_000L),
                ),
            )

            state.usdtBalanceAmount shouldBe 500_000_000L
        }

        test("hasNonLbtcAssets detects USDT presence") {
            val stateWith = LiquidWalletState(
                assetBalances = listOf(
                    LiquidAssetBalance(LiquidAsset.LBTC, 50_000L),
                    LiquidAssetBalance(LiquidAsset.USDT, 100L),
                ),
            )
            val stateWithout = LiquidWalletState(
                assetBalances = listOf(
                    LiquidAssetBalance(LiquidAsset.LBTC, 50_000L),
                ),
            )

            stateWith.hasNonLbtcAssets shouldBe true
            stateWithout.hasNonLbtcAssets shouldBe false
        }
    }

    context("LiquidTransaction.assetDeltas") {
        test("deltaForAsset extracts specific asset delta") {
            val tx = LiquidTransaction(
                txid = "abc123",
                balanceSatoshi = -500L,
                fee = 100L,
                assetDeltas = mapOf(
                    LiquidAsset.LBTC_ASSET_ID to -500L,
                    LiquidAsset.USDT_ASSET_ID to -1_000_000_00L,
                ),
            )

            tx.deltaForAsset(LiquidAsset.LBTC_ASSET_ID) shouldBe -500L
            tx.deltaForAsset(LiquidAsset.USDT_ASSET_ID) shouldBe -1_000_000_00L
        }

        test("deltaForAsset returns 0 for missing asset") {
            val tx = LiquidTransaction(
                txid = "abc123",
                balanceSatoshi = 1000L,
                fee = 50L,
                assetDeltas = mapOf(LiquidAsset.LBTC_ASSET_ID to 1000L),
            )

            tx.deltaForAsset(LiquidAsset.USDT_ASSET_ID) shouldBe 0L
        }

        test("involvesNonLbtcAsset detects USDT transactions") {
            val usdtTx = LiquidTransaction(
                txid = "abc123",
                balanceSatoshi = -200L,
                fee = 200L,
                assetDeltas = mapOf(
                    LiquidAsset.LBTC_ASSET_ID to -200L,
                    LiquidAsset.USDT_ASSET_ID to -5_000_000_00L,
                ),
            )
            val lbtcOnlyTx = LiquidTransaction(
                txid = "def456",
                balanceSatoshi = 1000L,
                fee = 100L,
                assetDeltas = mapOf(LiquidAsset.LBTC_ASSET_ID to 1000L),
            )

            usdtTx.involvesNonLbtcAsset shouldBe true
            lbtcOnlyTx.involvesNonLbtcAsset shouldBe false
        }
    }

    context("LiquidSendPreview asset helpers") {
        test("resolvedAsset returns L-BTC for null assetId") {
            val preview = LiquidSendPreview(
                kind = LiquidSendKind.LBTC,
                recipientDisplay = "lq1...",
            )

            preview.resolvedAsset.ticker shouldBe "L-BTC"
            preview.isLbtc shouldBe true
        }

        test("resolvedAsset returns USDT for USDT assetId") {
            val preview = LiquidSendPreview(
                kind = LiquidSendKind.LIQUID_ASSET,
                assetId = LiquidAsset.USDT_ASSET_ID,
                recipientDisplay = "lq1...",
            )

            preview.resolvedAsset.ticker shouldBe "USDt"
            preview.isLbtc shouldBe false
        }
    }
})
