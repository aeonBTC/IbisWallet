package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class Bip329LabelsTest : FunSpec({

    // ── buildOrigin ──

    context("buildOrigin") {
        test("SEGWIT produces wpkh descriptor") {
            Bip329Labels.buildOrigin(AddressType.SEGWIT, "d34db33f") shouldBe
                "wpkh([d34db33f/84'/0'/0'])"
        }

        test("LEGACY produces pkh descriptor") {
            Bip329Labels.buildOrigin(AddressType.LEGACY, "AABBCCDD") shouldBe
                "pkh([aabbccdd/44'/0'/0'])"
        }

        test("TAPROOT produces tr descriptor") {
            Bip329Labels.buildOrigin(AddressType.TAPROOT, "AABBCCDD") shouldBe
                "tr([aabbccdd/86'/0'/0'])"
        }

        test("returns null for null fingerprint") {
            Bip329Labels.buildOrigin(AddressType.SEGWIT, null) shouldBe null
        }

        test("returns null for blank fingerprint") {
            Bip329Labels.buildOrigin(AddressType.SEGWIT, "  ") shouldBe null
        }

        test("returns null for wrong-length fingerprint") {
            Bip329Labels.buildOrigin(AddressType.SEGWIT, "1234") shouldBe null
            Bip329Labels.buildOrigin(AddressType.SEGWIT, "123456789") shouldBe null
        }
    }

    // ── export ──

    context("export") {
        test("exports tx labels as JSONL") {
            val content = Bip329Labels.export(
                addressLabels = emptyMap(),
                transactionLabels = mapOf("txid1" to "Payment to Alice"),
            )
            val json = org.json.JSONObject(content.trim())
            json.getString("type") shouldBe "tx"
            json.getString("ref") shouldBe "txid1"
            json.getString("label") shouldBe "Payment to Alice"
        }

        test("exports address labels as JSONL") {
            val content = Bip329Labels.export(
                addressLabels = mapOf("bc1qtest" to "My Address"),
                transactionLabels = emptyMap(),
            )
            val json = org.json.JSONObject(content.trim())
            json.getString("type") shouldBe "addr"
            json.getString("ref") shouldBe "bc1qtest"
            json.getString("label") shouldBe "My Address"
        }

        test("tx labels come before address labels") {
            val content = Bip329Labels.export(
                addressLabels = mapOf("addr1" to "Addr Label"),
                transactionLabels = mapOf("txid1" to "Tx Label"),
            )
            val lines = content.lines()
            lines.size shouldBe 2
            org.json.JSONObject(lines[0]).getString("type") shouldBe "tx"
            org.json.JSONObject(lines[1]).getString("type") shouldBe "addr"
        }

        test("includes origin field when provided") {
            val content = Bip329Labels.export(
                addressLabels = emptyMap(),
                transactionLabels = mapOf("txid1" to "Label"),
                origin = "wpkh([d34db33f/84'/0'/0'])",
            )
            val json = org.json.JSONObject(content.trim())
            json.getString("origin") shouldBe "wpkh([d34db33f/84'/0'/0'])"
        }

        test("includes spark network field when provided") {
            val content = Bip329Labels.export(
                addressLabels = mapOf("sparkaddress1" to "Spark receive"),
                transactionLabels = mapOf("sparkpayment1" to "Spark payment"),
                network = Bip329LabelNetwork.SPARK,
            )
            val lines = content.lines()
            org.json.JSONObject(lines[0]).getString("network") shouldBe "spark"
            org.json.JSONObject(lines[1]).getString("network") shouldBe "spark"
        }

        test("omits origin field when null") {
            val content = Bip329Labels.export(
                addressLabels = emptyMap(),
                transactionLabels = mapOf("txid1" to "Label"),
                origin = null,
            )
            val json = org.json.JSONObject(content.trim())
            json.has("origin") shouldBe false
        }

        test("skips blank labels") {
            val content = Bip329Labels.export(
                addressLabels = mapOf("addr1" to "  "),
                transactionLabels = mapOf("txid1" to ""),
            )
            content shouldBe ""
        }

        test("empty maps produce empty string") {
            Bip329Labels.export(emptyMap(), emptyMap()) shouldBe ""
        }
    }

    // ── import (JSONL) ──

    context("import JSONL") {
        test("parses tx labels") {
            val content = """{"type":"tx","ref":"txid1","label":"Payment"}"""
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels shouldContainExactly mapOf("txid1" to "Payment")
            result.bitcoinAddressLabels.size shouldBe 0
        }

        test("parses addr labels") {
            val content = """{"type":"addr","ref":"bc1qtest","label":"Savings"}"""
            val result = Bip329Labels.import(content)
            result.bitcoinAddressLabels shouldContainExactly mapOf("bc1qtest" to "Savings")
        }

        test("parses mixed tx and addr labels") {
            val content = """
                {"type":"tx","ref":"txid1","label":"Payment"}
                {"type":"addr","ref":"addr1","label":"Cold Storage"}
            """.trimIndent()
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels.size shouldBe 1
            result.bitcoinAddressLabels.size shouldBe 1
            result.totalLabelsImported shouldBe 2
        }

        test("skips empty labels") {
            val content = """{"type":"tx","ref":"txid1","label":""}"""
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels.size shouldBe 0
        }

        test("skips blank lines") {
            val content = "\n\n{\"type\":\"tx\",\"ref\":\"txid1\",\"label\":\"Test\"}\n\n"
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels.size shouldBe 1
            result.totalLines shouldBe 1
        }

        test("counts error lines for malformed JSON") {
            val content = """
                {"type":"tx","ref":"txid1","label":"Good"}
                not-json-at-all
                {"type":"tx","ref":"txid2","label":"Also Good"}
            """.trimIndent()
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels.size shouldBe 2
            result.errorLines shouldBe 1
        }

        test("ignores unknown types") {
            val content = """{"type":"pubkey","ref":"02abc","label":"My Key"}"""
            val result = Bip329Labels.import(content)
            result.totalLabelsImported shouldBe 0
            result.errorLines shouldBe 0
        }

        test("parses output spendable flag") {
            val content = """{"type":"output","ref":"txid:0","spendable":false}"""
            val result = Bip329Labels.import(content)
            result.outputSpendable["txid:0"] shouldBe false
        }

        test("routes spark network labels to spark maps") {
            val content = """
                {"type":"tx","ref":"sparkpayment1","label":"Spark send","network":"spark"}
                {"type":"addr","ref":"sparkaddress1","label":"Spark receive","network":"spark"}
            """.trimIndent()
            val result = Bip329Labels.import(content)

            result.sparkTransactionLabels shouldContainExactly mapOf("sparkpayment1" to "Spark send")
            result.sparkAddressLabels shouldContainExactly mapOf("sparkaddress1" to "Spark receive")
            result.totalSparkLabelsImported shouldBe 2
        }

        test("default spark scope routes unlabeled network records to spark") {
            val content = """{"type":"tx","ref":"sparkpayment1","label":"Spark send"}"""
            val result = Bip329Labels.import(content, Bip329LabelScope.SPARK)

            result.sparkTransactionLabels shouldContainExactly mapOf("sparkpayment1" to "Spark send")
            result.bitcoinTransactionLabels.size shouldBe 0
        }
    }

    // ── import (Electrum CSV) ──

    context("import Electrum CSV") {
        test("parses txid,label CSV format") {
            val txid = "a" .repeat(64) // valid 64-char hex
            val content = "$txid,Payment to Bob,0.001,2024-01-01"
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels[txid] shouldBe "Payment to Bob"
        }

        test("skips CSV header row") {
            val txid = "b".repeat(64)
            val content = """
                transaction_hash,label,value,date
                $txid,My Payment,0.5,2024-06-15
            """.trimIndent()
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels.size shouldBe 1
            result.bitcoinTransactionLabels[txid] shouldBe "My Payment"
        }

        test("handles quoted CSV fields") {
            val txid = "c".repeat(64)
            val content = """"$txid","Payment, with comma",0.001"""
            val result = Bip329Labels.import(content)
            result.bitcoinTransactionLabels[txid] shouldBe "Payment, with comma"
        }
    }

    // ── round-trip ──

    context("round-trip export then import") {
        test("preserves all labels") {
            val addrLabels = mapOf("bc1qaddr1" to "Savings", "bc1qaddr2" to "Exchange")
            val txLabels = mapOf("txid1" to "Deposit", "txid2" to "Withdrawal")

            val exported = Bip329Labels.export(addrLabels, txLabels)
            val result = Bip329Labels.import(exported)

            result.bitcoinAddressLabels shouldContainExactly addrLabels
            result.bitcoinTransactionLabels shouldContainExactly txLabels
            result.errorLines shouldBe 0
        }
    }
})
