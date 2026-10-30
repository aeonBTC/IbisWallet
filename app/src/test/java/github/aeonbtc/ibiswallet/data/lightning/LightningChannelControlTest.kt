package github.aeonbtc.ibiswallet.data.lightning

import github.aeonbtc.ibiswallet.data.model.LightningNodeChannel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val TXID = "83b5a55b21255915dbc0d005230b2c026a004c839edaa716247b96b66490c66a"
private const val PEER = "026165850492521f4ac8abd9bd8088123446d126f648ca35e60f88177dc149ceb2"
private const val CHAN_ID = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"

private fun channel(
    id: String = "123",
    fundingTxid: String? = null,
    channelPoint: String? = null,
    shortChannelId: String? = null,
    remotePubkey: String = PEER,
) = LightningNodeChannel(
    id = id,
    remotePubkey = remotePubkey,
    shortChannelId = shortChannelId,
    fundingTxid = fundingTxid,
    channelPoint = channelPoint,
)

class LightningChannelControlTest : StringSpec({
    "closeOutpoint prefers channelPoint" {
        channel(id = "123", fundingTxid = TXID, channelPoint = "$TXID:1").closeOutpoint() shouldBe "$TXID:1"
    }

    "closeOutpoint accepts outpoint-form id" {
        channel(id = "$TXID:0").closeOutpoint() shouldBe "$TXID:0"
    }

    "closeOutpoint never guesses vout from funding txid alone" {
        channel(id = "123", fundingTxid = TXID).closeOutpoint() shouldBe null
    }

    "closeOutpoint rejects malformed channelPoint" {
        channel(id = "123", channelPoint = "not-an-outpoint").closeOutpoint() shouldBe null
    }

    "cln target prefers short channel id" {
        val ch = channel(id = CHAN_ID, shortChannelId = "800000x1x0")
        ClnRestClient.clnChannelTarget(ch) shouldBe "800000x1x0"
    }

    "cln target prefers channel id over peer" {
        val ch = channel(id = CHAN_ID)
        ClnRestClient.clnChannelTarget(ch) shouldBe CHAN_ID
    }

    "cln target rejects a funding txid used as id instead of guessing the peer" {
        val ch = channel(id = TXID, fundingTxid = TXID)
        ClnRestClient.clnChannelTarget(ch) shouldBe null
    }

    "cln target rejects synthetic ids instead of targeting the peer" {
        val ch = channel(id = "channel-3", remotePubkey = PEER)
        ClnRestClient.clnChannelTarget(ch) shouldBe null
    }

    "cln target is null without any usable identifier" {
        val ch = channel(id = "channel-3", remotePubkey = "")
        ClnRestClient.clnChannelTarget(ch) shouldBe null
    }
})
