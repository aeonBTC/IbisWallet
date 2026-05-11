package github.aeonbtc.ibiswallet.data.remote

import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.wire.OfferTypes

class Bolt12OfferVerifier {
    fun verifyFetchedInvoice(
        offerString: String,
        invoiceString: String,
        expectedAmountSats: Long,
    ) {
        val offer = OfferTypes.Offer.decode(offerString).get()
        val invoice = Bolt12Invoice.fromString(invoiceString).get()

        val candidateSigners =
            buildSet {
                offer.issuerId?.let(::add)
                offer.paths.orEmpty().mapTo(this) { it.nodeId }
            }
        require(candidateSigners.isNotEmpty()) { "BOLT12 offer does not expose any valid signer" }
        require(invoice.nodeId in candidateSigners) { "Fetched BOLT12 invoice does not belong to the requested offer" }
        offer.amount?.let { amountMsats ->
            val offerAmountSats = (amountMsats.toLong() + 999L) / 1000L
            require(offerAmountSats == expectedAmountSats) {
                "BOLT12 offer amount does not match the requested amount"
            }
        }
    }
}
