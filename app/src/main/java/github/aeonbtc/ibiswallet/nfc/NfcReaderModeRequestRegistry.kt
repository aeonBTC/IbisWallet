package github.aeonbtc.ibiswallet.nfc

internal class NfcReaderModeRequestRegistry {
    private val requesters = linkedSetOf<Any>()

    fun request(owner: Any) {
        requesters.add(owner)
    }

    fun release(owner: Any) {
        requesters.remove(owner)
    }

    fun hasActiveRequests(): Boolean = requesters.isNotEmpty()

    fun clear() {
        requesters.clear()
    }
}
