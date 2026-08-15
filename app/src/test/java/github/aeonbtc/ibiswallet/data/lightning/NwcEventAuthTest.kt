package github.aeonbtc.ibiswallet.data.lightning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.json.JSONObject

class NwcEventAuthTest :
    StringSpec(
        {
            "rejects missing tags" {
                val event =
                    JSONObject()
                        .put("kind", 23195)
                        .put("pubkey", "aa".repeat(32))
                        .put("created_at", System.currentTimeMillis() / 1000L)
                        .put("content", "cipher")
                        .put("id", "bb".repeat(32))
                        .put("sig", "cc".repeat(64))
                NwcClient.isAuthenticNwcResponse(event, "aa".repeat(32), "dd".repeat(32)) shouldBe false
            }

            "rejects mismatched pubkey" {
                val event =
                    JSONObject()
                        .put("kind", 23195)
                        .put("pubkey", "aa".repeat(32))
                        .put("created_at", System.currentTimeMillis() / 1000L)
                        .put("tags", JSONArray().put(JSONArray().put("e").put("dd".repeat(32))))
                        .put("content", "cipher")
                        .put("id", "bb".repeat(32))
                        .put("sig", "cc".repeat(64))
                NwcClient.isAuthenticNwcResponse(event, "ee".repeat(32), "dd".repeat(32)) shouldBe false
            }

            "preimage must hash to invoice payment hash" {
                val preimage = "11".repeat(32)
                val digest =
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(preimage.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                NwcClient.preimageMatchesInvoice(preimage, digest) shouldBe true
                NwcClient.preimageMatchesInvoice(preimage, "00".repeat(32)) shouldBe false
            }
        },
    )
