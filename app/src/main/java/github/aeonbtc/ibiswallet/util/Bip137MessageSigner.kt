package github.aeonbtc.ibiswallet.util

import github.aeonbtc.ibiswallet.data.model.AddressType
import org.bitcoinj.base.AddressParser
import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.ScriptType
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.utils.MessageVerifyUtils
import java.math.BigInteger
import java.security.SignatureException

object Bip137MessageSigner {
    private val mainnetAddressParser = AddressParser.getDefault(BitcoinNetwork.MAINNET)

    fun sign(
        privateKeyBytes: ByteArray,
        addressType: AddressType,
        message: String,
        compressed: Boolean = true,
    ): String {
        val scriptType = addressType.toBip137ScriptType()
        val key = ECKey.fromPrivate(BigInteger(1, privateKeyBytes), compressed)
        return key.signMessage(message, scriptType)
    }

    fun verify(
        address: String,
        message: String,
        signatureBase64: String,
    ): Boolean {
        return try {
            val parsedAddress = mainnetAddressParser.parseAddress(address.trim())
            MessageVerifyUtils.verifyMessage(parsedAddress, message, signatureBase64.trim())
            true
        } catch (_: SignatureException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun addressForPrivateKey(
        privateKeyBytes: ByteArray,
        addressType: AddressType,
        compressed: Boolean = true,
    ): String {
        val scriptType = addressType.toBip137ScriptType()
        val key = ECKey.fromPrivate(BigInteger(1, privateKeyBytes), compressed)
        return key.toAddress(scriptType, BitcoinNetwork.MAINNET).toString()
    }

    fun decodeWif(wif: String): DecodedWif {
        val decoded = BitcoinUtils.Base58.decodeChecked(wif.trim())
        require(decoded.isNotEmpty() && decoded[0].toInt() and 0xFF == 0x80) { "Invalid WIF network" }
        return when (decoded.size) {
            33 -> DecodedWif(privateKeyBytes = decoded.copyOfRange(1, 33), compressed = false)
            34 -> {
                require(decoded[33] == 0x01.toByte()) { "Invalid compressed WIF marker" }
                DecodedWif(privateKeyBytes = decoded.copyOfRange(1, 33), compressed = true)
            }
            else -> throw IllegalArgumentException("Invalid WIF length")
        }
    }

    private fun AddressType.toBip137ScriptType(): ScriptType =
        when (this) {
            AddressType.LEGACY -> ScriptType.P2PKH
            AddressType.SEGWIT -> ScriptType.P2WPKH
            AddressType.TAPROOT -> throw IllegalArgumentException("BIP137 does not support Taproot addresses")
        }

    data class DecodedWif(
        val privateKeyBytes: ByteArray,
        val compressed: Boolean,
    )
}
