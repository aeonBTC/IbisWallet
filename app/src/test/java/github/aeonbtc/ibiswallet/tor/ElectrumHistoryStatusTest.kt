package github.aeonbtc.ibiswallet.tor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.json.JSONArray
import org.json.JSONObject

class ElectrumHistoryStatusTest : StringSpec({
    "empty history has empty status" {
        CachingElectrumProxy.electrumHistoryStatus(JSONArray()) shouldBe ""
    }

    "mismatched history does not share status" {
        val honest =
            JSONArray().put(
                JSONObject().put("tx_hash", "aa").put("height", 100),
            )
        val fake =
            JSONArray().put(
                JSONObject().put("tx_hash", "bb").put("height", 100),
            )
        CachingElectrumProxy.electrumHistoryStatus(honest) shouldNotBe
            CachingElectrumProxy.electrumHistoryStatus(fake)
    }
})
