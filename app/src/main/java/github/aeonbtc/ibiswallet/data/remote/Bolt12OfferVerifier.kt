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

        // A fetched invoice is attacker-controlled until proven otherwise. Verify
        // both signatures (invoice + embedded invoice_request), then bind the
        // request's offer to the exact offer the user reviewed and check the
        // amount/expiry that will actually be paid. Checking only invoice.nodeId
        // is insufficient: nodeId is an unauthenticated TLV field.
        require(invoice.checkSignature()) { "Fetched BOLT12 invoice has an invalid signature" }
        require(invoice.invoiceRequest.isValid()) {
            "Fetched BOLT12 invoice has an invalid invoice_request"
        }
        require(invoice.invoiceRequest.offer.offerId == offer.offerId) {
            "Fetched BOLT12 invoice is for a different offer"
        }
        require(!invoice.isExpired()) { "Fetched BOLT12 invoice is expired" }

        val invoiceAmountMsat =
            invoice.amount?.msat
                ?: throw IllegalArgumentException("Fetched BOLT12 invoice has no amount")
        val invoiceAmountSats = (invoiceAmountMsat + 999L) / 1000L
        require(invoiceAmountSats == expectedAmountSats) {
            "BOLT12 invoice amount does not match the requested amount"
        }

        val candidateSigners =
            buildSet {
                offer.issuerId?.let(::add)
                offer.paths.orEmpty().mapTo(this) { it.nodeId }
            }
        require(candidateSigners.isNotEmpty()) { "BOLT12 offer does not expose any valid signer" }
        require(invoice.nodeId in candidateSigners) { "Fetched BOLT12 invoice does not belong to the requested offer" }
        offer.amount?.let { amountMsats ->
            val offerAmountSats = (amountMsats.msat + 999L) / 1000L
            require(offerAmountSats == expectedAmountSats) {
                "BOLT12 offer amount does not match the requested amount"
            }
        }
    }
}
