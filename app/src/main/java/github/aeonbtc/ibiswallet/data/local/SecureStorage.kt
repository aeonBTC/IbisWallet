package github.aeonbtc.ibiswallet.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.BoltzChainFundingPreview
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraft
import github.aeonbtc.ibiswallet.data.model.BoltzChainSwapDraftState
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.data.model.EstimatedSwapTerms
import github.aeonbtc.ibiswallet.data.model.LiquidSwapDetails
import github.aeonbtc.ibiswallet.data.model.LiquidSwapTxRole
import github.aeonbtc.ibiswallet.data.model.LiquidTxSource
import github.aeonbtc.ibiswallet.data.model.Layer2Provider
import github.aeonbtc.ibiswallet.data.model.LightningPaymentBackend
import github.aeonbtc.ibiswallet.data.model.PendingLightningInvoiceSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentPhase
import github.aeonbtc.ibiswallet.data.model.PendingLightningPaymentSession
import github.aeonbtc.ibiswallet.data.model.PendingLightningReceive
import github.aeonbtc.ibiswallet.data.model.PendingSwapPhase
import github.aeonbtc.ibiswallet.data.model.PendingSwapSession
import github.aeonbtc.ibiswallet.data.model.MultisigCosigner
import github.aeonbtc.ibiswallet.data.model.MultisigScriptType
import github.aeonbtc.ibiswallet.data.model.MultisigWalletConfig
import github.aeonbtc.ibiswallet.data.model.PsbtSessionStatus
import github.aeonbtc.ibiswallet.data.model.PsbtSigningSession
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.SparkPayment
import github.aeonbtc.ibiswallet.data.model.SparkUnclaimedDeposit
import github.aeonbtc.ibiswallet.data.model.SparkWalletState
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.SwapDirection
import github.aeonbtc.ibiswallet.data.model.SwapService
import github.aeonbtc.ibiswallet.data.model.WalletPolicyType
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.util.BiometricCrypto
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure storage for sensitive wallet data using Android's EncryptedSharedPreferences
 * Supports multiple wallets with unique IDs
 */
class SecureStorage private constructor(private val context: Context) {
    enum class QrDensity {
        LOW,
        MEDIUM,
        HIGH,
    }

    private val masterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val securePrefs: SharedPreferences =
        try {
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // KeyPermanentlyInvalidatedException or similar — the MasterKey was deleted
            // (e.g., by auto-wipe) but the encrypted prefs file still exists with a stale
            // Tink keyset. Delete the corrupt file and recreate from scratch.
            context.deleteSharedPreferences(SECURE_PREFS_FILE)
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry("_androidx_security_master_key_")
            } catch (_: Exception) {
            }
            val freshKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE,
                freshKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

    private val regularPrefs: SharedPreferences =
        context.getSharedPreferences(
            REGULAR_PREFS_FILE,
            Context.MODE_PRIVATE,
        )

    @Volatile private var spendSecretKey: ByteArray? = null

    private fun getPrivateString(key: String, defaultValue: String? = null): String? {
        securePrefs.getString(key, null)?.let { return it }
        val legacy = regularPrefs.getString(key, null) ?: return defaultValue
        securePrefs.edit { putString(key, legacy) }
        regularPrefs.edit { remove(key) }
        return legacy
    }

    private fun putPrivateString(
        key: String,
        value: String,
        commit: Boolean = false,
    ) {
        securePrefs.edit(commit = commit) { putString(key, value) }
        regularPrefs.edit(commit = commit) { remove(key) }
    }

    private fun putPrivateStrings(
        values: Map<String, String>,
        commit: Boolean = false,
    ) {
        if (values.isEmpty()) return
        securePrefs.edit(commit = commit) {
            values.forEach { (key, value) -> putString(key, value) }
        }
        regularPrefs.edit(commit = commit) {
            values.keys.forEach { key -> remove(key) }
        }
    }

    private fun removePrivateValue(
        key: String,
        commit: Boolean = false,
    ) {
        securePrefs.edit(commit = commit) { remove(key) }
        regularPrefs.edit(commit = commit) { remove(key) }
    }

    fun lockSpendSecretSession() {
        spendSecretKey?.fill(0)
        spendSecretKey = null
    }

    fun createSpendSecretBiometricCryptoObject(): BiometricPrompt.CryptoObject {
        val wrapped = readWrappedSecret(KEY_SPEND_MASTER_BIOMETRIC_WRAPPED)
        return if (wrapped != null) {
            BiometricCrypto.createCryptoObjectForDecryption(wrapped.iv)
        } else {
            BiometricCrypto.createCryptoObject()
        }
    }

    fun unlockSpendSecretsWithPin(pin: String) {
        spendSecretKey =
            unlockOrCreateSpendMaster(
                wrappedKey = KEY_SPEND_MASTER_PIN_WRAPPED,
                saltKey = KEY_SPEND_MASTER_PIN_SALT,
                pin = pin,
            )
        migrateLegacySpendSecrets()
    }

    fun unlockSpendSecretsWithDuressPin(pin: String) {
        spendSecretKey =
            unlockOrCreateSpendMaster(
                wrappedKey = KEY_SPEND_MASTER_DURESS_WRAPPED,
                saltKey = KEY_SPEND_MASTER_DURESS_SALT,
                pin = pin,
            )
        migrateLegacySpendSecrets()
    }

    fun unlockSpendSecretsWithBiometric(cipher: Cipher) {
        val wrapped = readWrappedSecret(KEY_SPEND_MASTER_BIOMETRIC_WRAPPED)
        val master =
            if (wrapped != null) {
                cipher.doFinal(wrapped.ciphertext)
            } else {
                val newMaster = spendSecretKey ?: randomSpendSecretKey()
                writeWrappedSecret(
                    key = KEY_SPEND_MASTER_BIOMETRIC_WRAPPED,
                    wrapped =
                        WrappedSecret(
                            iv = cipher.iv,
                            ciphertext = cipher.doFinal(newMaster),
                        ),
                )
                newMaster
            }
        spendSecretKey = master
        migrateLegacySpendSecrets()
    }

    private fun unlockOrCreateSpendMaster(
        wrappedKey: String,
        saltKey: String,
        pin: String,
    ): ByteArray {
        val wrappingKey = deriveSpendWrappingKey(pin, getOrCreateSpendMasterSalt(saltKey))
        val wrapped = readWrappedSecret(wrappedKey)
        return if (wrapped != null) {
            decryptWithRawKey(wrapped.ciphertext, wrappingKey)
        } else {
            val master = spendSecretKey ?: randomSpendSecretKey()
            writeWrappedSecret(
                wrappedKey,
                WrappedSecret(
                    iv = ByteArray(0),
                    ciphertext = encryptWithRawKey(master, wrappingKey),
                ),
            )
            master
        }
    }

    private fun getOrCreateSpendMasterSalt(key: String): ByteArray {
        securePrefs.getString(key, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        securePrefs.edit { putString(key, Base64.encodeToString(salt, Base64.NO_WRAP)) }
        return salt
    }

    private fun deriveSpendWrappingKey(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val chars = pin.toCharArray()
        val keySpec = PBEKeySpec(chars, salt, PIN_PBKDF2_ITERATIONS, PIN_HASH_LENGTH)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private data class WrappedSecret(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    private fun readWrappedSecret(key: String): WrappedSecret? {
        val raw = securePrefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(raw)
            WrappedSecret(
                iv = Base64.decode(json.optString("iv", ""), Base64.NO_WRAP),
                ciphertext = Base64.decode(json.getString("ciphertext"), Base64.NO_WRAP),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeWrappedSecret(
        key: String,
        wrapped: WrappedSecret,
    ) {
        val json =
            JSONObject()
                .put("iv", Base64.encodeToString(wrapped.iv, Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(wrapped.ciphertext, Base64.NO_WRAP))
        securePrefs.edit { putString(key, json.toString()) }
    }

    private fun randomSpendSecretKey(): ByteArray =
        ByteArray(SPEND_SECRET_KEY_BYTES).also { SecureRandom().nextBytes(it) }

    private fun randomIv(): ByteArray =
        ByteArray(SPEND_SECRET_IV_BYTES).also { SecureRandom().nextBytes(it) }

    private fun encryptWithRawKey(
        plaintext: ByteArray,
        rawKey: ByteArray,
    ): ByteArray {
        val iv = randomIv()
        val cipher = Cipher.getInstance(SPEND_SECRET_CIPHER)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(rawKey, "AES"),
            GCMParameterSpec(SPEND_SECRET_GCM_TAG_BITS, iv),
        )
        return iv + cipher.doFinal(plaintext)
    }

    private fun decryptWithRawKey(
        ciphertextWithIv: ByteArray,
        rawKey: ByteArray,
    ): ByteArray {
        val iv = ciphertextWithIv.copyOfRange(0, SPEND_SECRET_IV_BYTES)
        val ciphertext = ciphertextWithIv.copyOfRange(SPEND_SECRET_IV_BYTES, ciphertextWithIv.size)
        val cipher = Cipher.getInstance(SPEND_SECRET_CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(rawKey, "AES"),
            GCMParameterSpec(SPEND_SECRET_GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun encryptSpendSecret(value: String): String {
        val rawKey = spendSecretKey ?: return value
        return ENCRYPTED_SPEND_SECRET_PREFIX +
            Base64.encodeToString(encryptWithRawKey(value.toByteArray(Charsets.UTF_8), rawKey), Base64.NO_WRAP)
    }

    private fun getSpendSecret(key: String): String? {
        val value = getPrivateString(key, null) ?: return null
        if (!value.startsWith(ENCRYPTED_SPEND_SECRET_PREFIX)) {
            if (spendSecretKey != null) {
                securePrefs.edit { putString(key, encryptSpendSecret(value)) }
            }
            return value
        }
        val rawKey = spendSecretKey ?: return null
        val payload = Base64.decode(value.removePrefix(ENCRYPTED_SPEND_SECRET_PREFIX), Base64.NO_WRAP)
        return decryptWithRawKey(payload, rawKey).toString(Charsets.UTF_8)
    }

    private fun putSpendSecret(
        key: String,
        value: String,
    ) {
        securePrefs.edit { putString(key, encryptSpendSecret(value)) }
        regularPrefs.edit { remove(key) }
    }

    private fun removeSpendSecret(key: String) {
        removePrivateValue(key)
    }

    private fun migrateLegacySpendSecrets() {
        getWalletIds().forEach { walletId ->
            listOf(
                "${KEY_MNEMONIC_PREFIX}$walletId",
                "${KEY_PRIVATE_KEY_PREFIX}$walletId",
                "${KEY_PASSPHRASE_PREFIX}$walletId",
                "${KEY_MULTISIG_LOCAL_COSIGNER_PREFIX}$walletId",
                "${KEY_LIQUID_DESCRIPTOR_PREFIX}$walletId",
            ).forEach { key ->
                val value = securePrefs.getString(key, null)
                if (value != null && !value.startsWith(ENCRYPTED_SPEND_SECRET_PREFIX)) {
                    securePrefs.edit { putString(key, encryptSpendSecret(value)) }
                }
            }
        }
    }

    private fun migrateSpendSecretsToPlaintext() {
        val rawKey = spendSecretKey ?: return
        getWalletIds().forEach { walletId ->
            listOf(
                "${KEY_MNEMONIC_PREFIX}$walletId",
                "${KEY_PRIVATE_KEY_PREFIX}$walletId",
                "${KEY_PASSPHRASE_PREFIX}$walletId",
                "${KEY_MULTISIG_LOCAL_COSIGNER_PREFIX}$walletId",
                "${KEY_LIQUID_DESCRIPTOR_PREFIX}$walletId",
            ).forEach { key ->
                val value = securePrefs.getString(key, null)
                if (value != null && value.startsWith(ENCRYPTED_SPEND_SECRET_PREFIX)) {
                    val payload = Base64.decode(value.removePrefix(ENCRYPTED_SPEND_SECRET_PREFIX), Base64.NO_WRAP)
                    val plaintext = decryptWithRawKey(payload, rawKey).toString(Charsets.UTF_8)
                    securePrefs.edit { putString(key, plaintext) }
                }
            }
        }
        securePrefs.edit {
            remove(KEY_SPEND_MASTER_PIN_WRAPPED)
            remove(KEY_SPEND_MASTER_PIN_SALT)
            remove(KEY_SPEND_MASTER_DURESS_WRAPPED)
            remove(KEY_SPEND_MASTER_DURESS_SALT)
            remove(KEY_SPEND_MASTER_BIOMETRIC_WRAPPED)
        }
    }

    private fun getPrivateBoolean(
        key: String,
        defaultValue: Boolean = false,
    ): Boolean {
        if (securePrefs.contains(key)) return securePrefs.getBoolean(key, defaultValue)
        if (!regularPrefs.contains(key)) return defaultValue
        val legacy = regularPrefs.getBoolean(key, defaultValue)
        securePrefs.edit { putBoolean(key, legacy) }
        regularPrefs.edit { remove(key) }
        return legacy
    }

    private fun putPrivateBoolean(
        key: String,
        value: Boolean,
    ) {
        securePrefs.edit { putBoolean(key, value) }
        regularPrefs.edit { remove(key) }
    }

    private fun getPrivateLong(key: String): Long? {
        if (securePrefs.contains(key)) return securePrefs.getLong(key, -1L).takeIf { it != -1L }
        if (!regularPrefs.contains(key)) return null
        val legacy = regularPrefs.getLong(key, -1L).takeIf { it != -1L }
        if (legacy != null) {
            securePrefs.edit { putLong(key, legacy) }
        }
        regularPrefs.edit { remove(key) }
        return legacy
    }

    private fun putPrivateLongIfAbsent(
        key: String,
        value: Long,
    ) {
        if (securePrefs.contains(key)) {
            regularPrefs.edit { remove(key) }
            return
        }
        if (regularPrefs.contains(key)) {
            getPrivateLong(key)
            return
        }
        securePrefs.edit { putLong(key, value) }
    }

    private fun privateKeysWithPrefix(prefix: String): Set<String> {
        return (securePrefs.all.keys + regularPrefs.all.keys).filter { it.startsWith(prefix) }.toSet()
    }

    private fun privateStringsWithPrefix(prefix: String): Map<String, String> = privateStringsWithPrefixes(listOf(prefix))

    private fun privateStringsWithPrefixes(prefixes: Collection<String>): Map<String, String> {
        if (prefixes.isEmpty()) return emptyMap()

        val values = linkedMapOf<String, String>()
        securePrefs.all.forEach { (key, value) ->
            if (prefixes.any(key::startsWith)) {
                (value as? String)?.let { values[key] = it }
            }
        }
        regularPrefs.all.forEach { (key, value) ->
            if (key !in values && prefixes.any(key::startsWith)) {
                (value as? String)?.let { values[key] = it }
            }
        }
        return values
    }

    data class LiquidMetadataSnapshot(
        val txLabels: Map<String, String>,
        val txSources: Map<String, LiquidTxSource>,
        val txSwapDetails: Map<String, LiquidSwapDetails>,
        val txRecipients: Map<String, String>,
        val txFeeDetails: Map<String, LiquidTxFeeDetails>,
        val pendingLightningReceives: List<PendingLightningReceive>,
    )

    data class LiquidTxFeeDetails(
        val feeRate: Double,
        val vsize: Double,
    )

    // ==================== Multi-Wallet Management ====================

    /**
     * Get list of all wallet IDs
     */
    fun getWalletIds(): List<String> {
        val json = getPrivateString(KEY_WALLET_IDS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Add a wallet ID to the list
     */
    private fun addWalletId(walletId: String) {
        val ids = getWalletIds().toMutableList()
        if (!ids.contains(walletId)) {
            ids.add(walletId)
            val jsonArray = JSONArray(ids)
            putPrivateString(KEY_WALLET_IDS, jsonArray.toString())
        }
    }

    /**
     * Remove a wallet ID from the list
     */
    private fun removeWalletId(walletId: String) {
        val ids = getWalletIds().toMutableList()
        ids.remove(walletId)
        val jsonArray = JSONArray(ids)
        putPrivateString(KEY_WALLET_IDS, jsonArray.toString())
    }

    /**
     * Get the active wallet ID
     */
    fun getActiveWalletId(): String? {
        return getPrivateString(KEY_ACTIVE_WALLET_ID, null)
    }

    /**
     * Set the active wallet ID
     */
    fun setActiveWalletId(walletId: String?) {
        if (walletId != null) {
            putPrivateString(KEY_ACTIVE_WALLET_ID, walletId)
        } else {
            removePrivateValue(KEY_ACTIVE_WALLET_ID)
        }
    }

    // ==================== Wallet Metadata ====================

    /**
     * Save wallet metadata for a specific wallet
     */
    fun saveWalletMetadata(wallet: StoredWallet) {
        val walletJson =
            JSONObject().apply {
                put("id", wallet.id)
                put("name", wallet.name)
                put("addressType", wallet.addressType.name)
                put("derivationPath", wallet.derivationPath)
                put("isWatchOnly", wallet.isWatchOnly)
                put("isLocked", wallet.isLocked)
                put("network", wallet.network.name)
                put("createdAt", wallet.createdAt)
                wallet.masterFingerprint?.let { put("masterFingerprint", it) }
                put("seedFormat", wallet.seedFormat.name)
                put("gapLimit", wallet.gapLimit)
                put("policyType", wallet.policyType.name)
                wallet.multisigThreshold?.let { put("multisigThreshold", it) }
                wallet.multisigTotalCosigners?.let { put("multisigTotalCosigners", it) }
                wallet.multisigScriptType?.let { put("multisigScriptType", it.name) }
                wallet.localCosignerFingerprint?.let { put("localCosignerFingerprint", it) }
            }

        putPrivateString("${KEY_WALLET_PREFIX}${wallet.id}", walletJson.toString())

        // Add to wallet list
        addWalletId(wallet.id)

        // Set as active if no active wallet
        if (getActiveWalletId() == null) {
            setActiveWalletId(wallet.id)
        }
    }

    /**
     * Get wallet metadata for a specific wallet ID
     */
    fun getWalletMetadata(walletId: String): StoredWallet? {
        val json = getPrivateString("${KEY_WALLET_PREFIX}$walletId", null) ?: return null

        return try {
            val jsonObject = JSONObject(json)
            val addressType =
                BitcoinUtils.parseSupportedAddressType(
                    rawAddressType = jsonObject.optString("addressType", AddressType.SEGWIT.name),
                    default = AddressType.SEGWIT,
                )
            val network =
                BitcoinUtils.parseSupportedWalletNetwork(
                    rawNetwork = jsonObject.optString("network", WalletNetwork.BITCOIN.name),
                    default = WalletNetwork.BITCOIN,
                )
            val seedFormat =
                try {
                    SeedFormat.valueOf(jsonObject.optString("seedFormat", SeedFormat.BIP39.name))
                } catch (_: IllegalArgumentException) {
                    SeedFormat.BIP39
                }
            val policyType =
                try {
                    WalletPolicyType.valueOf(jsonObject.optString("policyType", WalletPolicyType.SINGLE_SIG.name))
                } catch (_: IllegalArgumentException) {
                    WalletPolicyType.SINGLE_SIG
                }
            val multisigScriptType =
                jsonObject.optString("multisigScriptType", "").takeIf { it.isNotBlank() }?.let {
                    try {
                        MultisigScriptType.valueOf(it)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }

            StoredWallet(
                id = jsonObject.getString("id"),
                name = jsonObject.optString("name", "Wallet"),
                addressType = addressType,
                derivationPath = jsonObject.optString("derivationPath", addressType.defaultPath),
                isWatchOnly = jsonObject.optBoolean("isWatchOnly", false),
                isLocked = jsonObject.optBoolean("isLocked", false),
                network = network,
                createdAt = jsonObject.optLong("createdAt", System.currentTimeMillis()),
                masterFingerprint =
                    jsonObject.optString("masterFingerprint", null.toString()).let {
                        if (it == "null" || it.isBlank()) null else it
                    },
                seedFormat = seedFormat,
                gapLimit = jsonObject.optInt("gapLimit", StoredWallet.DEFAULT_GAP_LIMIT),
                policyType = policyType,
                multisigThreshold =
                    jsonObject.takeIf { it.has("multisigThreshold") }?.optInt("multisigThreshold"),
                multisigTotalCosigners =
                    jsonObject.takeIf { it.has("multisigTotalCosigners") }?.optInt("multisigTotalCosigners"),
                multisigScriptType = multisigScriptType,
                localCosignerFingerprint =
                    jsonObject.optString("localCosignerFingerprint", "").ifBlank { null },
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get all stored wallets
     */
    fun getAllWallets(): List<StoredWallet> {
        return getWalletIds().mapNotNull { walletId ->
            try {
                getWalletMetadata(walletId)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Edit wallet metadata (name, gap limit, and optionally master fingerprint for watch-only wallets)
     */
    fun editWallet(
        walletId: String,
        newName: String,
        newGapLimit: Int,
        newFingerprint: String? = null,
    ): Boolean {
        val wallet = getWalletMetadata(walletId) ?: return false
        var updated = wallet.copy(name = newName, gapLimit = newGapLimit)
        if (wallet.isWatchOnly && newFingerprint != null) {
            updated = updated.copy(masterFingerprint = newFingerprint.ifBlank { null })
        }
        saveWalletMetadata(updated)
        return true
    }

    /**
     * Set whether a wallet requires app authentication before opening.
     */
    fun setWalletLocked(walletId: String, locked: Boolean): Boolean {
        val wallet = getWalletMetadata(walletId) ?: return false
        if (wallet.isLocked == locked) return true
        saveWalletMetadata(wallet.copy(isLocked = locked))
        return true
    }

    /**
     * Clear the locked state for all wallets.
     * Returns true if any wallet metadata changed.
     */
    fun clearWalletLocks(): Boolean {
        var updated = false
        getAllWallets().forEach { wallet ->
            if (wallet.isLocked) {
                saveWalletMetadata(wallet.copy(isLocked = false))
                updated = true
            }
        }
        return updated
    }

    /**
     * Reorder wallet IDs to the given order.
     * Only IDs that already exist in storage are kept; the order is replaced entirely.
     */
    fun reorderWalletIds(orderedIds: List<String>) {
        val existing = getWalletIds().toSet()
        // Keep only IDs that actually exist, in the requested order
        val validated = orderedIds.filter { it in existing }
        // Append any IDs that were missing from the request (shouldn't happen, but be safe)
        val missing = existing - validated.toSet()
        val finalOrder = validated + missing
        val jsonArray = JSONArray(finalOrder)
        putPrivateString(KEY_WALLET_IDS, jsonArray.toString())
    }

    /**
     * Delete wallet metadata for a specific wallet
     */
    fun deleteWalletMetadata(walletId: String) {
        removePrivateValue("${KEY_WALLET_PREFIX}$walletId")
        removeWalletId(walletId)

        // If this was the active wallet, set another as active
        if (getActiveWalletId() == walletId) {
            val remainingIds = getWalletIds()
            setActiveWalletId(remainingIds.firstOrNull())
        }
    }

    // ==================== Mnemonic/Key Storage (per wallet) ====================

    /**
     * Save mnemonic for a specific wallet
     */
    fun saveMnemonic(
        walletId: String,
        mnemonic: String,
    ) {
        putSpendSecret("${KEY_MNEMONIC_PREFIX}$walletId", mnemonic)
    }

    /**
     * Get mnemonic for a specific wallet
     */
    fun getMnemonic(walletId: String): String? {
        return getSpendSecret("${KEY_MNEMONIC_PREFIX}$walletId")
    }

    /**
     * Delete mnemonic for a specific wallet
     */
    fun deleteMnemonic(walletId: String) {
        removeSpendSecret("${KEY_MNEMONIC_PREFIX}$walletId")
    }

    // ==================== Extended Public Key (per wallet) ====================

    /**
     * Save extended key for a specific wallet
     */
    fun saveExtendedKey(
        walletId: String,
        key: String,
    ) {
        securePrefs.edit { putString("${KEY_EXTENDED_KEY_PREFIX}$walletId", key) }
    }

    /**
     * Get extended key for a specific wallet
     */
    fun getExtendedKey(walletId: String): String? {
        return securePrefs.getString("${KEY_EXTENDED_KEY_PREFIX}$walletId", null)
    }

    /**
     * Check if wallet has an extended key
     */
    fun hasExtendedKey(walletId: String): Boolean {
        return securePrefs.contains("${KEY_EXTENDED_KEY_PREFIX}$walletId")
    }

    /**
     * Delete extended key for a specific wallet
     */
    fun deleteExtendedKey(walletId: String) {
        securePrefs.edit { remove("${KEY_EXTENDED_KEY_PREFIX}$walletId") }
    }

    // ==================== Private Key Storage (per wallet - WIF format) ====================

    fun savePrivateKey(
        walletId: String,
        wif: String,
    ) {
        putSpendSecret("${KEY_PRIVATE_KEY_PREFIX}$walletId", wif)
    }

    fun getPrivateKey(walletId: String): String? {
        return getSpendSecret("${KEY_PRIVATE_KEY_PREFIX}$walletId")
    }

    fun hasPrivateKey(walletId: String): Boolean {
        return securePrefs.contains("${KEY_PRIVATE_KEY_PREFIX}$walletId")
    }

    fun deletePrivateKey(walletId: String) {
        removeSpendSecret("${KEY_PRIVATE_KEY_PREFIX}$walletId")
    }

    // ==================== Watch Address Storage (per wallet - single address) ====================

    fun saveWatchAddress(
        walletId: String,
        address: String,
    ) {
        securePrefs.edit { putString("${KEY_WATCH_ADDRESS_PREFIX}$walletId", address) }
    }

    fun getWatchAddress(walletId: String): String? {
        return securePrefs.getString("${KEY_WATCH_ADDRESS_PREFIX}$walletId", null)
    }

    fun hasWatchAddress(walletId: String): Boolean {
        return securePrefs.contains("${KEY_WATCH_ADDRESS_PREFIX}$walletId")
    }

    fun deleteWatchAddress(walletId: String) {
        securePrefs.edit { remove("${KEY_WATCH_ADDRESS_PREFIX}$walletId") }
    }

    // ==================== Multisig Wallet Storage ====================

    fun saveMultisigWalletConfig(
        walletId: String,
        config: MultisigWalletConfig,
    ) {
        putPrivateString("${KEY_MULTISIG_CONFIG_PREFIX}$walletId", multisigConfigToJson(config).toString())
    }

    fun getMultisigWalletConfig(walletId: String): MultisigWalletConfig? {
        val json = getPrivateString("${KEY_MULTISIG_CONFIG_PREFIX}$walletId", null) ?: return null
        return try {
            multisigConfigFromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun deleteMultisigWalletConfig(walletId: String) {
        removePrivateValue("${KEY_MULTISIG_CONFIG_PREFIX}$walletId")
    }

    fun saveLocalCosignerKeyMaterial(
        walletId: String,
        keyMaterial: String,
    ) {
        putSpendSecret("${KEY_MULTISIG_LOCAL_COSIGNER_PREFIX}$walletId", keyMaterial)
    }

    fun getLocalCosignerKeyMaterial(walletId: String): String? =
        getSpendSecret("${KEY_MULTISIG_LOCAL_COSIGNER_PREFIX}$walletId")

    fun deleteLocalCosignerKeyMaterial(walletId: String) {
        removeSpendSecret("${KEY_MULTISIG_LOCAL_COSIGNER_PREFIX}$walletId")
    }

    fun savePsbtSigningSession(session: PsbtSigningSession) {
        val key = psbtSigningSessionKey(session.walletId, session.id)
        putPrivateString(key, psbtSigningSessionToJson(session).toString())
    }

    fun getPsbtSigningSession(
        walletId: String,
        sessionId: String,
    ): PsbtSigningSession? {
        val json = getPrivateString(psbtSigningSessionKey(walletId, sessionId), null) ?: return null
        return try {
            psbtSigningSessionFromJson(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun getPsbtSigningSessions(walletId: String): List<PsbtSigningSession> {
        val prefix = "${KEY_PSBT_SIGNING_SESSION_PREFIX}${walletId}_"
        return privateKeysWithPrefix(prefix).mapNotNull { key ->
            getPrivateString(key, null)?.let { json ->
                try {
                    psbtSigningSessionFromJson(JSONObject(json))
                } catch (_: Exception) {
                    null
                }
            }
        }.sortedByDescending { it.updatedAt }
    }

    private fun psbtSigningSessionKey(
        walletId: String,
        sessionId: String,
    ): String = "${KEY_PSBT_SIGNING_SESSION_PREFIX}${walletId}_$sessionId"

    fun multisigConfigToJson(config: MultisigWalletConfig): JSONObject =
        JSONObject().apply {
            config.name?.let { put("name", it) }
            put("threshold", config.threshold)
            put("totalCosigners", config.totalCosigners)
            put("scriptType", config.scriptType.name)
            put("isSorted", config.isSorted)
            put("externalDescriptor", config.externalDescriptor)
            put("internalDescriptor", config.internalDescriptor)
            config.sourceFormat?.let { put("sourceFormat", it) }
            put(
                "cosigners",
                JSONArray().apply {
                    config.cosigners.forEach { cosigner ->
                        put(
                            JSONObject().apply {
                                put("fingerprint", cosigner.fingerprint)
                                put("derivationPath", cosigner.derivationPath)
                                put("xpub", cosigner.xpub)
                                cosigner.label?.let { put("label", it) }
                            },
                        )
                    }
                },
            )
        }

    fun multisigConfigFromJson(json: JSONObject): MultisigWalletConfig {
        val cosignersArray = json.optJSONArray("cosigners") ?: JSONArray()
        val cosigners =
            List(cosignersArray.length()) { index ->
                val obj = cosignersArray.getJSONObject(index)
                MultisigCosigner(
                    fingerprint = obj.getString("fingerprint"),
                    derivationPath = obj.getString("derivationPath"),
                    xpub = obj.getString("xpub"),
                    label = obj.optString("label", "").ifBlank { null },
                )
            }
        return MultisigWalletConfig(
            name = json.optString("name", "").ifBlank { null },
            threshold = json.getInt("threshold"),
            totalCosigners = json.getInt("totalCosigners"),
            scriptType = MultisigScriptType.valueOf(json.optString("scriptType", MultisigScriptType.P2WSH.name)),
            isSorted = json.optBoolean("isSorted", true),
            externalDescriptor = json.getString("externalDescriptor"),
            internalDescriptor = json.getString("internalDescriptor"),
            cosigners = cosigners,
            sourceFormat = json.optString("sourceFormat", "").ifBlank { null },
        )
    }

    private fun psbtSigningSessionToJson(session: PsbtSigningSession): JSONObject =
        JSONObject().apply {
            put("id", session.id)
            put("walletId", session.walletId)
            put("originalPsbtBase64", session.originalPsbtBase64)
            put("workingPsbtBase64", session.workingPsbtBase64)
            put("signerExportPsbtBase64", session.signerExportPsbtBase64)
            put("requiredSignatures", session.requiredSignatures)
            put("presentSignatures", session.presentSignatures)
            put("pendingLabel", session.pendingLabel)
            put("createdAt", session.createdAt)
            put("updatedAt", session.updatedAt)
            put("status", session.status.name)
            put("importedPartials", JSONArray(session.importedPartials))
        }

    private fun psbtSigningSessionFromJson(json: JSONObject): PsbtSigningSession {
        val importedPartials = json.optJSONArray("importedPartials") ?: JSONArray()
        return PsbtSigningSession(
            id = json.getString("id"),
            walletId = json.getString("walletId"),
            originalPsbtBase64 = json.getString("originalPsbtBase64"),
            workingPsbtBase64 = json.getString("workingPsbtBase64"),
            signerExportPsbtBase64 = json.getString("signerExportPsbtBase64"),
            requiredSignatures = json.getInt("requiredSignatures"),
            presentSignatures = json.optInt("presentSignatures", 0),
            importedPartials = List(importedPartials.length()) { importedPartials.getString(it) },
            pendingLabel = json.optString("pendingLabel", "").ifBlank { null },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            status = PsbtSessionStatus.valueOf(json.optString("status", PsbtSessionStatus.IN_PROGRESS.name)),
        )
    }

    // ==================== Passphrase Storage (per wallet) ====================

    /**
     * Save passphrase for a specific wallet
     */
    fun savePassphrase(
        walletId: String,
        passphrase: String,
    ) {
        putSpendSecret("${KEY_PASSPHRASE_PREFIX}$walletId", passphrase)
    }

    /**
     * Get passphrase for a specific wallet
     */
    fun getPassphrase(walletId: String): String? {
        return getSpendSecret("${KEY_PASSPHRASE_PREFIX}$walletId")
    }

    /**
     * Delete passphrase for a specific wallet
     */
    fun deletePassphrase(walletId: String) {
        removeSpendSecret("${KEY_PASSPHRASE_PREFIX}$walletId")
    }

    // ==================== Network Configuration ====================

    fun saveNetwork(network: WalletNetwork) {
        regularPrefs.edit { putString(KEY_NETWORK, network.name) }
    }

    // ==================== Electrum Server Configuration (Multi-Server) ====================

    /**
     * Get list of all saved server IDs
     */
    fun getServerIds(): List<String> {
        val json = getPrivateString(KEY_SERVER_IDS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Add a server ID to the list
     */
    private fun addServerId(serverId: String) {
        val ids = getServerIds().toMutableList()
        if (!ids.contains(serverId)) {
            ids.add(serverId)
            val jsonArray = JSONArray(ids)
            putPrivateString(KEY_SERVER_IDS, jsonArray.toString())
        }
    }

    /**
     * Remove a server ID from the list
     */
    private fun removeServerId(serverId: String) {
        val ids = getServerIds().toMutableList()
        ids.remove(serverId)
        val jsonArray = JSONArray(ids)
        putPrivateString(KEY_SERVER_IDS, jsonArray.toString())
    }

    fun reorderElectrumServerIds(orderedIds: List<String>) {
        putPrivateString(KEY_SERVER_IDS, JSONArray(orderedIds).toString())
    }

    /**
     * Get the active server ID
     */
    fun getActiveServerId(): String? {
        return getPrivateString(KEY_ACTIVE_SERVER_ID, null)
    }

    /**
     * Set the active server ID
     */
    fun setActiveServerId(serverId: String?) {
        if (serverId != null) {
            putPrivateString(KEY_ACTIVE_SERVER_ID, serverId)
        } else {
            removePrivateValue(KEY_ACTIVE_SERVER_ID)
        }
    }

    /**
     * Save an Electrum server config
     * Returns the config with a guaranteed ID (generates one if null)
     */
    fun saveElectrumServer(config: ElectrumConfig): ElectrumConfig {
        val serverId = config.id ?: java.util.UUID.randomUUID().toString()
        val savedConfig = config.copy(id = serverId)

        val serverJson =
            JSONObject().apply {
                put("id", serverId)
                put("name", savedConfig.name ?: "")
                put("url", savedConfig.url)
                put("port", savedConfig.port)
                put("useSsl", savedConfig.useSsl)
                put("useTor", savedConfig.useTor)
            }

        putPrivateString("${KEY_SERVER_PREFIX}$serverId", serverJson.toString())

        // Add to server list
        addServerId(serverId)

        return savedConfig
    }

    /**
     * Get an Electrum server config by ID
     */
    fun getElectrumServer(serverId: String): ElectrumConfig? {
        val json = getPrivateString("${KEY_SERVER_PREFIX}$serverId", null) ?: return null

        return try {
            val jsonObject = JSONObject(json)
            ElectrumConfig(
                id = jsonObject.getString("id"),
                name = jsonObject.optString("name", ""),
                url = jsonObject.getString("url"),
                port = jsonObject.optInt("port", 50001),
                useSsl = jsonObject.optBoolean("useSsl", false),
                useTor = jsonObject.optBoolean("useTor", false),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get the currently active Electrum server config
     */
    fun getActiveElectrumServer(): ElectrumConfig? {
        val activeId = getActiveServerId() ?: return null
        return getElectrumServer(activeId)
    }

    /**
     * Get all saved Electrum servers
     */
    fun getAllElectrumServers(): List<ElectrumConfig> {
        return getServerIds().mapNotNull { getElectrumServer(it) }
    }

    /**
     * Delete an Electrum server
     */
    fun deleteElectrumServer(serverId: String) {
        // Delete the stored TOFU certificate fingerprint for this server
        val config = getElectrumServer(serverId)
        if (config != null) {
            deleteServerCertFingerprint(config.cleanUrl(), config.port)
        }

        removePrivateValue("${KEY_SERVER_PREFIX}$serverId")
        removeServerId(serverId)

        // If this was the active server, clear active (do not auto-assign another)
        if (getActiveServerId() == serverId) {
            setActiveServerId(null)
        }
    }

    // ==================== Server Certificate TOFU ====================

    /**
     * Save the trusted certificate SHA-256 fingerprint for a server.
     * Keyed by host:port for persistence across server renames.
     */
    fun saveServerCertFingerprint(
        host: String,
        port: Int,
        fingerprint: String,
    ) {
        val key = "${KEY_SERVER_CERT_PREFIX}$host:$port"
        securePrefs.edit { putString(key, fingerprint) }
    }

    /**
     * Get the stored certificate fingerprint for a server.
     * Returns null if this is the first connection (no TOFU record).
     */
    fun getServerCertFingerprint(
        host: String,
        port: Int,
    ): String? {
        val key = "${KEY_SERVER_CERT_PREFIX}$host:$port"
        return securePrefs.getString(key, null)
    }

    /**
     * Delete the stored certificate fingerprint for a server.
     */
    fun deleteServerCertFingerprint(
        host: String,
        port: Int,
    ) {
        val key = "${KEY_SERVER_CERT_PREFIX}$host:$port"
        securePrefs.edit { remove(key) }
    }

    /**
     * Check if default Electrum servers have been seeded.
     * Returns false only on first app launch before any servers exist.
     */
    fun hasDefaultServersSeeded(): Boolean {
        return regularPrefs.getBoolean(KEY_DEFAULT_SERVERS_SEEDED, false)
    }

    /**
     * Mark that default Electrum servers have been seeded.
     * Called after seeding defaults on first launch.
     */
    fun setDefaultServersSeeded(seeded: Boolean) {
        regularPrefs.edit { putBoolean(KEY_DEFAULT_SERVERS_SEEDED, seeded) }
    }

    // ==================== Auto-Switch Server ====================

    /**
     * Check if auto-switch to another saved server on disconnect is enabled
     */
    fun isAutoSwitchServerEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_AUTO_SWITCH_SERVER, false)
    }

    /**
     * Set auto-switch server on disconnect state
     */
    fun setAutoSwitchServerEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_AUTO_SWITCH_SERVER, enabled) }
    }

    // ==================== User Disconnect Intent ====================

    /**
     * Returns true if the user explicitly disconnected and has not reconnected since.
     * Used to suppress auto-connect on app restart.
     */
    fun isUserDisconnected(): Boolean {
        return regularPrefs.getBoolean(KEY_USER_DISCONNECTED, false)
    }

    fun setUserDisconnected(disconnected: Boolean) {
        regularPrefs.edit { putBoolean(KEY_USER_DISCONNECTED, disconnected) }
    }

    fun isLiquidUserDisconnected(): Boolean {
        return regularPrefs.getBoolean(KEY_LIQUID_USER_DISCONNECTED, false)
    }

    fun setLiquidUserDisconnected(disconnected: Boolean) {
        regularPrefs.edit { putBoolean(KEY_LIQUID_USER_DISCONNECTED, disconnected) }
    }

    // ==================== Last Sync Time (per wallet) ====================

    fun saveLastSyncTime(
        walletId: String,
        timestamp: Long,
    ) {
        regularPrefs.edit { putLong("${KEY_LAST_SYNC_PREFIX}$walletId", timestamp) }
    }

    fun getLastSyncTime(walletId: String): Long? {
        val key = "${KEY_LAST_SYNC_PREFIX}$walletId"
        return if (regularPrefs.contains(key)) {
            regularPrefs.getLong(key, 0)
        } else {
            null
        }
    }

    // ==================== Full Sync Tracking ====================

    /**
     * Set whether a wallet needs a full sync (address discovery scan)
     * Should be true for newly imported wallets, false after successful full sync
     */
    fun setNeedsFullSync(
        walletId: String,
        needs: Boolean,
    ) {
        regularPrefs.edit { putBoolean("${KEY_NEEDS_FULL_SYNC_PREFIX}$walletId", needs) }
    }

    /**
     * Check if a wallet needs a full sync
     * Returns true by default for new wallets that haven't been synced yet
     */
    fun needsFullSync(walletId: String): Boolean {
        return regularPrefs.getBoolean("${KEY_NEEDS_FULL_SYNC_PREFIX}$walletId", true)
    }

    fun setNeedsLiquidFullSync(
        walletId: String,
        needs: Boolean,
    ) {
        regularPrefs.edit { putBoolean("${KEY_LIQUID_NEEDS_FULL_SYNC_PREFIX}$walletId", needs) }
    }

    fun needsLiquidFullSync(walletId: String): Boolean {
        return regularPrefs.getBoolean("${KEY_LIQUID_NEEDS_FULL_SYNC_PREFIX}$walletId", false)
    }

    /**
     * Save the timestamp of the last full sync for a wallet
     */
    fun saveLastFullSyncTime(
        walletId: String,
        timestamp: Long,
    ) {
        regularPrefs.edit { putLong("${KEY_LAST_FULL_SYNC_PREFIX}$walletId", timestamp) }
    }

    /**
     * Get the timestamp of the last full sync for a wallet
     * Returns null if never fully synced
     */
    fun getLastFullSyncTime(walletId: String): Long? {
        val key = "${KEY_LAST_FULL_SYNC_PREFIX}$walletId"
        return if (regularPrefs.contains(key)) {
            regularPrefs.getLong(key, 0)
        } else {
            null
        }
    }

    // ==================== Delete Wallet ====================

    /**
     * Delete all data for a specific wallet
     */
    fun deleteWallet(walletId: String) {
        // Delete secure data
        deleteMnemonic(walletId)
        deleteExtendedKey(walletId)
        deletePassphrase(walletId)
        deletePrivateKey(walletId)
        deleteWatchAddress(walletId)
        deleteMultisigWalletConfig(walletId)
        deleteLocalCosignerKeyMaterial(walletId)
        removeLiquidDescriptor(walletId)

        // Delete metadata
        deleteWalletMetadata(walletId)

        // Delete sync time and full sync flags
        regularPrefs.edit {
            remove("${KEY_LAST_SYNC_PREFIX}$walletId")
            remove("${KEY_NEEDS_FULL_SYNC_PREFIX}$walletId")
            remove("${KEY_LIQUID_NEEDS_FULL_SYNC_PREFIX}$walletId")
            remove("${KEY_LAST_FULL_SYNC_PREFIX}$walletId")
            remove("${KEY_LIQUID_ENABLED_PREFIX}$walletId")
            remove("${KEY_SPARK_ENABLED_PREFIX}$walletId")
            remove("${KEY_SPARK_ONCHAIN_DEPOSIT_ADDRESS_PREFIX}$walletId")
            remove("${KEY_LAYER2_PROVIDER_PREFIX}$walletId")
            remove("${KEY_LIQUID_WATCH_ONLY_PREFIX}$walletId")
            remove("${KEY_LIQUID_GAP_LIMIT_PREFIX}$walletId")
            remove("${KEY_ACTIVE_LAYER_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_TXIDS_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_TXIDS_BASELINE_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_LIQUID_TXIDS_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_LIQUID_TXIDS_BASELINE_PREFIX}$walletId")
            remove("${KEY_PENDING_SWAP_PREFIX}$walletId")
            remove("${KEY_BOLTZ_CHAIN_SWAP_DRAFT_PREFIX}$walletId")
            remove("${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}$walletId")
            remove("${KEY_PENDING_BOLTZ_LIGHTNING_PAYMENT_PREFIX}$walletId")
            remove(boltzSessionNextIndexKey(walletId))
            remove(liquidScriptHashStatusBlobKey(walletId))
            remove(liquidScriptHashStatusIndexKey(walletId))
            removeWalletScopedRegularPrefs(this, walletId)
        }
        securePrefs.edit {
            remove("${KEY_NOTIFIED_TXIDS_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_TXIDS_BASELINE_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_LIQUID_TXIDS_PREFIX}$walletId")
            remove("${KEY_NOTIFIED_LIQUID_TXIDS_BASELINE_PREFIX}$walletId")
            remove("${KEY_PENDING_SWAP_PREFIX}$walletId")
            remove("${KEY_BOLTZ_CHAIN_SWAP_DRAFT_PREFIX}$walletId")
            remove("${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}$walletId")
            remove("${KEY_PENDING_BOLTZ_LIGHTNING_PAYMENT_PREFIX}$walletId")
            remove(boltzSessionNextIndexKey(walletId))
            remove(liquidScriptHashStatusBlobKey(walletId))
            remove(liquidScriptHashStatusIndexKey(walletId))
            removeWalletScopedSecurePrefs(this, walletId)
        }
    }

    private fun removeWalletScopedRegularPrefs(
        editor: SharedPreferences.Editor,
        walletId: String,
    ) {
        val prefixes = walletScopedPrefixes(walletId)

        regularPrefs.all.keys
            .filter { key -> prefixes.any(key::startsWith) }
            .forEach(editor::remove)
    }

    private fun removeWalletScopedSecurePrefs(
        editor: SharedPreferences.Editor,
        walletId: String,
    ) {
        val prefixes = walletScopedPrefixes(walletId)

        securePrefs.all.keys
            .filter { key -> prefixes.any(key::startsWith) }
            .forEach(editor::remove)
    }

    /**
     * The set of `<prefix><walletId>_` and `<prefix><walletId>` keys that are
     * tied to a single wallet and must be removed on `deleteWallet`. Shared
     * between the secure and regular prefs cleanup helpers so any future
     * wallet-scoped key only has to be added in one place.
     */
    private fun walletScopedPrefixes(walletId: String): List<String> = listOf(
        "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_",
        "${KEY_LIQUID_ADDRESS_LABEL_PREFIX}${walletId}_",
        "${KEY_SPARK_ADDRESS_LABEL_PREFIX}${walletId}_",
        "${KEY_TX_LABEL_PREFIX}${walletId}_",
        "${KEY_TX_SOURCE_PREFIX}${walletId}_",
        "${KEY_TX_SWAP_DETAILS_PREFIX}${walletId}_",
        "${KEY_LIQUID_TX_LABEL_PREFIX}${walletId}_",
        "${KEY_SPARK_TX_LABEL_PREFIX}${walletId}_",
        "${KEY_SPARK_TX_SOURCE_PREFIX}${walletId}_",
        // Spark counterparty and on-chain claim metadata. Without these
        // prefixes, deleting a wallet would leave recipient identifiers
        // and pending Spark deposit overlays in encrypted prefs.
        "${KEY_SPARK_PAYMENT_RECIPIENT_PREFIX}${walletId}_",
        "${KEY_SPARK_DEPOSIT_ADDRESS_PREFIX}${walletId}_",
        "${KEY_SPARK_PENDING_DEPOSIT_PREFIX}${walletId}_",
        "${KEY_SPARK_WALLET_STATE_CACHE_PREFIX}$walletId",
        "${KEY_LIQUID_TX_SOURCE_PREFIX}${walletId}_",
        "${KEY_LIQUID_TX_SWAP_DETAILS_PREFIX}${walletId}_",
        "${KEY_LIQUID_TX_RECIPIENT_PREFIX}${walletId}_",
        "${KEY_LIQUID_TX_FEE_DETAILS_PREFIX}${walletId}_",
        "${KEY_LIQUID_SCRIPT_HASH_STATUS_ENTRY_PREFIX}${walletId}_",
        "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX}${walletId}_",
        "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX}${walletId}_",
        "${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}${walletId}_",
        "${KEY_PENDING_SWAP_SNAPSHOT_PREFIX}${walletId}_",
        "${KEY_BOLTZ_CHAIN_SWAP_SNAPSHOT_PREFIX}${walletId}_",
        "${KEY_PENDING_PSBT_LABEL_PREFIX}${walletId}_",
        "${KEY_PSBT_SIGNING_SESSION_PREFIX}${walletId}_",
        "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_",
        "${KEY_PENDING_REPLACEMENT_TX_PREFIX}${walletId}_",
        "${KEY_FROZEN_UTXOS_PREFIX}$walletId",
    )

    // ==================== Sync Batch Size ====================

    /**
     * Save the adaptive sync batch size so it persists across app restarts.
     * Avoids re-learning slow Tor connections every session.
     */
    fun saveSyncBatchSize(batchSize: Long) {
        regularPrefs.edit { putLong(KEY_SYNC_BATCH_SIZE, batchSize) }
    }

    /**
     * Get the persisted sync batch size, or null if never saved (use default).
     */
    fun getSyncBatchSize(): Long? {
        return if (regularPrefs.contains(KEY_SYNC_BATCH_SIZE)) {
            regularPrefs.getLong(KEY_SYNC_BATCH_SIZE, 500L)
        } else {
            null
        }
    }

    // ==================== Tor Settings ====================

    /**
     * Check if Tor is globally enabled
     */
    fun isTorEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_TOR_ENABLED, false)
    }

    /**
     * Set global Tor enabled state
     */
    fun setTorEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_TOR_ENABLED, enabled) }
    }

    // ==================== Display Settings ====================

    /**
     * Get the Layer 1 denomination (BTC or SATS).
     * Falls back to the legacy shared denomination key for migration.
     */
    fun getLayer1Denomination(): String {
        return regularPrefs.getString(
            KEY_LAYER1_DENOMINATION,
            regularPrefs.getString(KEY_DENOMINATION, DENOMINATION_BTC),
        ) ?: DENOMINATION_BTC
    }

    /**
     * Persist the Layer 1 denomination.
     */
    fun setLayer1Denomination(denomination: String) {
        regularPrefs.edit { putString(KEY_LAYER1_DENOMINATION, denomination) }
    }

    /**
     * Get the Layer 2 denomination (BTC or SATS).
     * Falls back to the legacy shared denomination key for migration.
     */
    fun getLayer2Denomination(): String {
        return regularPrefs.getString(
            KEY_LAYER2_DENOMINATION,
            regularPrefs.getString(KEY_DENOMINATION, DENOMINATION_BTC),
        ) ?: DENOMINATION_BTC
    }

    /**
     * Persist the Layer 2 denomination.
     */
    fun setLayer2Denomination(denomination: String) {
        regularPrefs.edit { putString(KEY_LAYER2_DENOMINATION, denomination) }
    }

    fun getAppLocale(): AppLocale {
        return AppLocale.fromStorageValue(
            regularPrefs.getString(KEY_APP_LOCALE, AppLocale.ENGLISH.storageValue),
        )
    }

    fun setAppLocale(locale: AppLocale) {
        regularPrefs.edit { putString(KEY_APP_LOCALE, locale.storageValue) }
    }

    fun getSwipeMode(): String {
        return regularPrefs.getString(KEY_SWIPE_MODE, SWIPE_MODE_DISABLED) ?: SWIPE_MODE_DISABLED
    }

    fun setSwipeMode(mode: String) {
        regularPrefs.edit { putString(KEY_SWIPE_MODE, mode) }
    }

    fun getPsbtQrDensity(): QrDensity {
        val storedValue = regularPrefs.getString(KEY_PSBT_QR_DENSITY, QrDensity.MEDIUM.name)
        return try {
            QrDensity.valueOf(storedValue ?: QrDensity.MEDIUM.name)
        } catch (_: IllegalArgumentException) {
            QrDensity.MEDIUM
        }
    }

    fun setPsbtQrDensity(density: QrDensity) {
        regularPrefs.edit { putString(KEY_PSBT_QR_DENSITY, density.name) }
    }

    fun getPsbtQrBrightness(): Float {
        val brightness = regularPrefs.getFloat(KEY_PSBT_QR_BRIGHTNESS, DEFAULT_PSBT_QR_BRIGHTNESS)
        return brightness.coerceIn(MIN_PSBT_QR_BRIGHTNESS, 1f)
    }

    fun setPsbtQrBrightness(brightness: Float) {
        regularPrefs.edit {
            putFloat(KEY_PSBT_QR_BRIGHTNESS, brightness.coerceIn(MIN_PSBT_QR_BRIGHTNESS, 1f))
        }
    }

    /**
     * Get persisted privacy mode state
     */
    fun getPrivacyMode(): Boolean {
        return regularPrefs.getBoolean(KEY_PRIVACY_MODE, false)
    }

    /**
     * Set privacy mode state
     */
    fun setPrivacyMode(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_PRIVACY_MODE, enabled) }
    }

    fun hasSeenPrivacyModeHint(): Boolean {
        return regularPrefs.getBoolean(KEY_HAS_SEEN_PRIVACY_MODE_HINT, false)
    }

    fun setHasSeenPrivacyModeHint(seen: Boolean) {
        regularPrefs.edit { putBoolean(KEY_HAS_SEEN_PRIVACY_MODE_HINT, seen) }
    }

    // ==================== Mempool Server Settings ====================

    /**
     * Get the selected mempool server option
     * Default is MEMPOOL_DISABLED
     */
    fun getMempoolServer(): String {
        return regularPrefs.getString(KEY_MEMPOOL_SERVER, MEMPOOL_DISABLED) ?: MEMPOOL_DISABLED
    }

    /**
     * Set the mempool server option
     */
    fun setMempoolServer(server: String) {
        regularPrefs.edit { putString(KEY_MEMPOOL_SERVER, server) }
    }

    /**
     * Get the full mempool URL based on selected option
     * For custom server, returns the custom URL
     * Returns empty string if disabled
     */
    fun getMempoolUrl(): String {
        return when (getMempoolServer()) {
            MEMPOOL_DISABLED -> ""
            MEMPOOL_SPACE -> "https://mempool.space"
            MEMPOOL_ONION -> "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion"
            MEMPOOL_CUSTOM -> getCustomMempoolUrl()?.trim()?.trimEnd('/').orEmpty()
            else -> ""
        }
    }

    /**
     * Get custom mempool URL (placeholder for future custom server implementation)
     */
    fun getCustomMempoolUrl(): String? {
        return getPrivateString(KEY_MEMPOOL_CUSTOM_URL, null)
    }

    /**
     * Set custom mempool URL (for future custom server implementation)
     */
    fun setCustomMempoolUrl(url: String) {
        putPrivateString(KEY_MEMPOOL_CUSTOM_URL, url)
    }

    // ==================== Liquid Explorer Settings ====================

    fun getLiquidExplorer(): String {
        return regularPrefs.getString(KEY_LIQUID_EXPLORER, LIQUID_EXPLORER_DISABLED) ?: LIQUID_EXPLORER_DISABLED
    }

    fun setLiquidExplorer(explorer: String) {
        regularPrefs.edit { putString(KEY_LIQUID_EXPLORER, explorer) }
    }

    fun getCustomLiquidExplorerUrl(): String? {
        return getPrivateString(KEY_LIQUID_EXPLORER_CUSTOM_URL, null)
    }

    fun setCustomLiquidExplorerUrl(url: String) {
        putPrivateString(KEY_LIQUID_EXPLORER_CUSTOM_URL, url)
    }

    fun getLiquidExplorerUrl(): String {
        return when (getLiquidExplorer()) {
            LIQUID_EXPLORER_DISABLED -> ""
            LIQUID_EXPLORER_LIQUID_NETWORK -> "https://liquid.network"
            LIQUID_EXPLORER_LIQUID_NETWORK_ONION ->
                "http://liquidmom47f6s3m53ebfxn47p76a6tlnxib3wp6deux7wuzotdr6cyd.onion"
            LIQUID_EXPLORER_BLOCKSTREAM -> "https://blockstream.info/liquid"
            LIQUID_EXPLORER_CUSTOM -> getCustomLiquidExplorerUrl()?.trim()?.trimEnd('/').orEmpty()
            else -> ""
        }
    }

    // ==================== Fee Estimation Settings ====================

    /**
     * Get the selected fee estimation source
     * Default is OFF (manual entry only)
     */
    fun getFeeSource(): String {
        return regularPrefs.getString(KEY_FEE_SOURCE, FEE_SOURCE_OFF) ?: FEE_SOURCE_OFF
    }

    /**
     * Set the fee estimation source
     */
    fun setFeeSource(source: String) {
        regularPrefs.edit { putString(KEY_FEE_SOURCE, source) }
    }

    /**
     * Get the full fee source URL based on selected option.
     * Returns null if fee estimation is disabled or uses Electrum.
     * Custom server is assumed to be a mempool.space-compatible instance.
     */
    fun getFeeSourceUrl(): String? {
        return when (getFeeSource()) {
            FEE_SOURCE_OFF -> null
            FEE_SOURCE_MEMPOOL -> "https://mempool.space"
            FEE_SOURCE_MEMPOOL_ONION -> "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v2oethdhhj6mlo2r6ad.onion"
            FEE_SOURCE_ELECTRUM -> null // Electrum uses the connected client, not a URL
            FEE_SOURCE_CUSTOM -> getCustomFeeSourceUrl()?.ifBlank { null }
            else -> null
        }
    }

    /**
     * Get the custom fee source server URL (mempool.space-compatible instance)
     */
    fun getCustomFeeSourceUrl(): String? {
        return getPrivateString(KEY_FEE_SOURCE_CUSTOM_URL, null)
    }

    /**
     * Set the custom fee source server URL
     */
    fun setCustomFeeSourceUrl(url: String) {
        putPrivateString(KEY_FEE_SOURCE_CUSTOM_URL, url)
    }

    // ==================== Spend Unconfirmed ====================

    /**
     * Get whether spending unconfirmed UTXOs is allowed.
     * Default is true (allow spending unconfirmed).
     */
    fun getSpendUnconfirmed(): Boolean {
        return regularPrefs.getBoolean(KEY_SPEND_UNCONFIRMED, true)
    }

    /**
     * Set whether spending unconfirmed UTXOs is allowed.
     */
    fun setSpendUnconfirmed(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_SPEND_UNCONFIRMED, enabled) }
    }

    /**
     * Get whether NFC tap-to-read/share is enabled in the app.
     * Defaults to false — user must opt in via Settings.
     */
    fun isNfcEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_NFC_ENABLED, false)
    }

    /**
     * Set whether NFC tap-to-read/share is enabled.
     */
    fun setNfcEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_NFC_ENABLED, enabled) }
    }

    /**
     * Get whether Android wallet activity notifications are enabled.
     * Defaults to false until the user explicitly opts in.
     */
    fun isWalletNotificationsEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_WALLET_NOTIFICATIONS_ENABLED, false)
    }

    /**
     * Set whether Android wallet activity notifications are enabled.
     */
    fun setWalletNotificationsEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_WALLET_NOTIFICATIONS_ENABLED, enabled) }
    }

    fun isForegroundConnectivityEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_FOREGROUND_CONNECTIVITY_ENABLED, false)
    }

    fun setForegroundConnectivityEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_FOREGROUND_CONNECTIVITY_ENABLED, enabled) }
    }

    fun isAppUpdateCheckEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_APP_UPDATE_CHECK_ENABLED, false)
    }

    fun setAppUpdateCheckEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_APP_UPDATE_CHECK_ENABLED, enabled) }
    }

    fun hasSeenAppUpdateOptInPrompt(): Boolean =
        regularPrefs.getBoolean(KEY_HAS_SEEN_APP_UPDATE_OPT_IN_PROMPT, false)

    fun setHasSeenAppUpdateOptInPrompt(seen: Boolean) {
        regularPrefs.edit { putBoolean(KEY_HAS_SEEN_APP_UPDATE_OPT_IN_PROMPT, seen) }
    }

    fun hasSeenWelcome(): Boolean = regularPrefs.getBoolean(KEY_HAS_SEEN_WELCOME, false)

    fun setHasSeenWelcome(seen: Boolean) {
        regularPrefs.edit { putBoolean(KEY_HAS_SEEN_WELCOME, seen) }
    }

    fun hasSeenLiquidEnableInfo(): Boolean = regularPrefs.getBoolean(KEY_HAS_SEEN_LIQUID_ENABLE_INFO, false)

    fun setHasSeenLiquidEnableInfo(seen: Boolean) {
        regularPrefs.edit { putBoolean(KEY_HAS_SEEN_LIQUID_ENABLE_INFO, seen) }
    }

    fun getSeenAppUpdateVersion(): String? {
        return regularPrefs.getString(KEY_SEEN_APP_UPDATE_VERSION, null)
    }

    fun setSeenAppUpdateVersion(versionName: String?) {
        regularPrefs.edit {
            if (versionName.isNullOrBlank()) {
                remove(KEY_SEEN_APP_UPDATE_VERSION)
            } else {
                putString(KEY_SEEN_APP_UPDATE_VERSION, versionName.trim())
            }
        }
    }

    // ==================== Notified Transaction IDs (per wallet) ====================

    fun getNotifiedTxids(walletId: String): Set<String> {
        val raw = getPrivateString("${KEY_NOTIFIED_TXIDS_PREFIX}$walletId", null)
            ?: return emptySet()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').toSet()
    }

    fun saveNotifiedTxids(walletId: String, txids: Set<String>) {
        putPrivateString("${KEY_NOTIFIED_TXIDS_PREFIX}$walletId", txids.joinToString(","))
    }

    fun hasNotifiedTxidsBaseline(walletId: String): Boolean {
        return getPrivateBoolean("${KEY_NOTIFIED_TXIDS_BASELINE_PREFIX}$walletId")
    }

    fun setNotifiedTxidsBaseline(walletId: String, established: Boolean) {
        putPrivateBoolean("${KEY_NOTIFIED_TXIDS_BASELINE_PREFIX}$walletId", established)
    }

    fun getNotifiedLiquidTxids(walletId: String): Set<String> {
        val raw = getPrivateString("${KEY_NOTIFIED_LIQUID_TXIDS_PREFIX}$walletId", null)
            ?: return emptySet()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').toSet()
    }

    fun saveNotifiedLiquidTxids(walletId: String, txids: Set<String>) {
        putPrivateString("${KEY_NOTIFIED_LIQUID_TXIDS_PREFIX}$walletId", txids.joinToString(","))
    }

    fun hasNotifiedLiquidTxidsBaseline(walletId: String): Boolean {
        return getPrivateBoolean("${KEY_NOTIFIED_LIQUID_TXIDS_BASELINE_PREFIX}$walletId")
    }

    fun setNotifiedLiquidTxidsBaseline(walletId: String, established: Boolean) {
        putPrivateBoolean("${KEY_NOTIFIED_LIQUID_TXIDS_BASELINE_PREFIX}$walletId", established)
    }

    // ==================== BTC/Fiat Price Settings ====================

    /**
     * Get the BTC/USD price source
     * Default is OFF (no price display)
     */
    fun getPriceSource(): String {
        return regularPrefs.getString(KEY_PRICE_SOURCE, PRICE_SOURCE_OFF) ?: PRICE_SOURCE_OFF
    }

    /**
     * Set the BTC/USD price source
     */
    fun setPriceSource(source: String) {
        regularPrefs.edit { putString(KEY_PRICE_SOURCE, source) }
    }

    fun getPriceCurrency(): String {
        return regularPrefs.getString(KEY_PRICE_CURRENCY, DEFAULT_PRICE_CURRENCY) ?: DEFAULT_PRICE_CURRENCY
    }

    fun setPriceCurrency(currencyCode: String) {
        regularPrefs.edit { putString(KEY_PRICE_CURRENCY, currencyCode) }
    }

    fun isHistoricalTxFiatEnabled(): Boolean {
        return regularPrefs.getBoolean(KEY_HISTORICAL_TX_FIAT_ENABLED, false)
    }

    fun setHistoricalTxFiatEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_HISTORICAL_TX_FIAT_ENABLED, enabled) }
    }

    // ==================== Layer 2 External Services ====================

    fun getBoltzApiSource(): String {
        return regularPrefs.getString(KEY_BOLTZ_API_SOURCE, BOLTZ_API_DISABLED) ?: BOLTZ_API_DISABLED
    }

    fun setBoltzApiSource(source: String) {
        regularPrefs.edit { putString(KEY_BOLTZ_API_SOURCE, source) }
    }

    fun getSideSwapApiSource(): String {
        return regularPrefs.getString(KEY_SIDESWAP_API_SOURCE, SIDESWAP_API_DISABLED) ?: SIDESWAP_API_DISABLED
    }

    fun setSideSwapApiSource(source: String) {
        regularPrefs.edit { putString(KEY_SIDESWAP_API_SOURCE, source) }
    }

    fun getPreferredSwapService(): SwapService {
        val storedValue = regularPrefs.getString(KEY_PREFERRED_SWAP_SERVICE, SwapService.BOLTZ.name)
        return storedValue
            ?.let { value -> SwapService.entries.firstOrNull { it.name == value } }
            ?: SwapService.BOLTZ
    }

    fun setPreferredSwapService(service: SwapService) {
        regularPrefs.edit { putString(KEY_PREFERRED_SWAP_SERVICE, service.name) }
    }

    // ==================== Address Labels ====================

    /**
     * Save a label for an address
     */
    fun saveAddressLabel(
        walletId: String,
        address: String,
        label: String,
    ) {
        val key = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        putPrivateString(key, label)
    }

    fun saveAddressLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val prefix = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_"
        putPrivateStrings(labels.mapKeys { (address, _) -> "$prefix$address" })
    }

    /**
     * Get the label for an address
     */
    fun getAddressLabel(
        walletId: String,
        address: String,
    ): String? {
        val key = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        return getPrivateString(key, null)
    }

    /**
     * Get all address labels for a wallet
     */
    fun getAllAddressLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix).mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    /**
     * Delete a label for an address
     */
    fun deleteAddressLabel(
        walletId: String,
        address: String,
    ) {
        val key = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        removePrivateValue(key)
    }

    /**
     * Save a Layer 2 Liquid label for a receive address.
     */
    fun saveLiquidAddressLabel(
        walletId: String,
        address: String,
        label: String,
    ) {
        val key = "${KEY_LIQUID_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        putPrivateString(key, label)
    }

    fun saveLiquidAddressLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val prefix = "${KEY_LIQUID_ADDRESS_LABEL_PREFIX}${walletId}_"
        putPrivateStrings(labels.mapKeys { (address, _) -> "$prefix$address" })
    }

    /**
     * Get the Layer 2 Liquid label for a receive address.
     */
    fun getLiquidAddressLabel(
        walletId: String,
        address: String,
    ): String? {
        val key = "${KEY_LIQUID_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        return getPrivateString(key, null)
    }

    /**
     * Get all Layer 2 Liquid labels for receive addresses in a wallet.
     */
    fun getAllLiquidAddressLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_LIQUID_ADDRESS_LABEL_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix).mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    /**
     * Delete a Layer 2 Liquid label for a receive address.
     */
    fun deleteLiquidAddressLabel(
        walletId: String,
        address: String,
    ) {
        val key = "${KEY_LIQUID_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        removePrivateValue(key)
    }

    fun saveSparkAddressLabel(
        walletId: String,
        address: String,
        label: String,
    ) {
        val key = "${KEY_SPARK_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        putPrivateString(key, label)
    }

    fun saveSparkAddressLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val prefix = "${KEY_SPARK_ADDRESS_LABEL_PREFIX}${walletId}_"
        putPrivateStrings(labels.filterValues { it.isNotBlank() }.mapKeys { (address, _) -> "$prefix$address" })
    }

    fun getAllSparkAddressLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_SPARK_ADDRESS_LABEL_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix).mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    // ==================== Transaction Labels ====================

    /**
     * Save a label for a transaction
     */
    fun saveTransactionLabel(
        walletId: String,
        txid: String,
        label: String,
    ) {
        val key = "${KEY_TX_LABEL_PREFIX}${walletId}_$txid"
        putPrivateString(key, label)
    }

    fun saveTransactionLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val prefix = "${KEY_TX_LABEL_PREFIX}${walletId}_"
        putPrivateStrings(labels.mapKeys { (txid, _) -> "$prefix$txid" })
    }

    fun deleteTransactionLabel(
        walletId: String,
        txid: String,
    ) {
        val key = "${KEY_TX_LABEL_PREFIX}${walletId}_$txid"
        removePrivateValue(key)
    }

    /**
     * Get the label for a transaction
     */
    fun getTransactionLabel(
        walletId: String,
        txid: String,
    ): String? {
        val key = "${KEY_TX_LABEL_PREFIX}${walletId}_$txid"
        return getPrivateString(key, null)
    }

    /**
     * Save a pending label for a PSBT (to be applied when broadcast)
     */
    fun savePendingPsbtLabel(
        walletId: String,
        psbtKey: String,
        label: String,
    ) {
        val key = "${KEY_PENDING_PSBT_LABEL_PREFIX}${walletId}_$psbtKey"
        putPrivateString(key, label)
    }

    /**
     * Get all transaction labels for a wallet
     */
    fun getAllTransactionLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_TX_LABEL_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix).mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    /**
     * Save a Layer 2 Liquid label for a transaction.
     */
    fun saveLiquidTransactionLabel(
        walletId: String,
        txid: String,
        label: String,
    ) {
        val key = "${KEY_LIQUID_TX_LABEL_PREFIX}${walletId}_$txid"
        putPrivateString(key, label)
    }

    fun saveLiquidTransactionLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val prefix = "${KEY_LIQUID_TX_LABEL_PREFIX}${walletId}_"
        putPrivateStrings(labels.mapKeys { (txid, _) -> "$prefix$txid" })
    }

    fun deleteLiquidTransactionLabel(
        walletId: String,
        txid: String,
    ) {
        val key = "${KEY_LIQUID_TX_LABEL_PREFIX}${walletId}_$txid"
        removePrivateValue(key)
    }

    /**
     * Get a Layer 2 Liquid label for a transaction.
     */
    /**
     * Get all Layer 2 Liquid transaction labels for a wallet.
     */
    fun getAllLiquidTransactionLabels(walletId: String): Map<String, String> {
        return getLiquidMetadataSnapshot(walletId).txLabels
    }

    fun saveSparkTransactionLabel(
        walletId: String,
        paymentId: String,
        label: String,
    ) {
        val key = "${KEY_SPARK_TX_LABEL_PREFIX}${walletId}_$paymentId"
        putPrivateString(key, label)
    }

    fun saveSparkTransactionLabels(
        walletId: String,
        labels: Map<String, String>,
    ) {
        val prefix = "${KEY_SPARK_TX_LABEL_PREFIX}${walletId}_"
        putPrivateStrings(labels.filterValues { it.isNotBlank() }.mapKeys { (paymentId, _) -> "$prefix$paymentId" })
    }

    fun deleteSparkTransactionLabel(
        walletId: String,
        paymentId: String,
    ) {
        val key = "${KEY_SPARK_TX_LABEL_PREFIX}${walletId}_$paymentId"
        removePrivateValue(key)
    }

    fun getAllSparkTransactionLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_SPARK_TX_LABEL_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix).mapKeys { (key, _) -> key.removePrefix(prefix) }
    }

    fun saveSparkPaymentRecipient(
        walletId: String,
        paymentId: String,
        recipient: String,
    ) {
        val key = "${KEY_SPARK_PAYMENT_RECIPIENT_PREFIX}${walletId}_$paymentId"
        putPrivateString(key, recipient)
    }

    fun getAllSparkPaymentRecipients(walletId: String): Map<String, String> {
        val prefix = "${KEY_SPARK_PAYMENT_RECIPIENT_PREFIX}${walletId}_"
        val recipients = mutableMapOf<String, String>()

        privateKeysWithPrefix(prefix).forEach { key ->
            getPrivateString(key, null)?.let { value ->
                recipients[key.removePrefix(prefix)] = value
            }
        }

        return recipients
    }

    fun saveSparkTransactionSource(
        walletId: String,
        paymentId: String,
        source: String,
    ) {
        if (source.isBlank()) return
        val key = "${KEY_SPARK_TX_SOURCE_PREFIX}${walletId}_$paymentId"
        putPrivateString(key, source)
    }

    fun saveSparkTransactionSources(
        walletId: String,
        sources: Map<String, String>,
    ) {
        val prefix = "${KEY_SPARK_TX_SOURCE_PREFIX}${walletId}_"
        putPrivateStrings(
            sources
                .filterValues { it.isNotBlank() }
                .mapKeys { (paymentId, _) -> "$prefix$paymentId" },
        )
    }

    fun getAllSparkTransactionSources(walletId: String): Map<String, String> {
        val prefix = "${KEY_SPARK_TX_SOURCE_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix)
            .mapKeys { (key, _) -> key.removePrefix(prefix) }
            .filterValues { it.isNotBlank() }
    }

    fun saveSparkDepositAddress(
        walletId: String,
        txid: String,
        address: String,
    ) {
        val key = "${KEY_SPARK_DEPOSIT_ADDRESS_PREFIX}${walletId}_$txid"
        putPrivateString(key, address)
    }

    fun getAllSparkDepositAddresses(walletId: String): Map<String, String> {
        val prefix = "${KEY_SPARK_DEPOSIT_ADDRESS_PREFIX}${walletId}_"
        val addresses = mutableMapOf<String, String>()

        privateKeysWithPrefix(prefix).forEach { key ->
            getPrivateString(key, null)?.let { value ->
                addresses[key.removePrefix(prefix)] = value
            }
        }

        return addresses
    }

    fun saveSparkPendingDeposit(
        walletId: String,
        deposit: SparkUnclaimedDeposit,
    ) {
        val key = "${KEY_SPARK_PENDING_DEPOSIT_PREFIX}${walletId}_${deposit.txid}"
        val json =
            JSONObject()
                .put("txid", deposit.txid)
                .put("vout", deposit.vout.toLong())
                .put("amountSats", deposit.amountSats)
                .put("isMature", deposit.isMature)
                .put("timestamp", deposit.timestamp ?: JSONObject.NULL)
                .put("address", deposit.address ?: JSONObject.NULL)
                .put("claimError", deposit.claimError ?: JSONObject.NULL)
        putPrivateString(key, json.toString())
    }

    fun deleteSparkPendingDeposit(
        walletId: String,
        txid: String,
    ) {
        removePrivateValue("${KEY_SPARK_PENDING_DEPOSIT_PREFIX}${walletId}_$txid")
    }

    fun getAllSparkPendingDeposits(walletId: String): List<SparkUnclaimedDeposit> {
        val prefix = "${KEY_SPARK_PENDING_DEPOSIT_PREFIX}${walletId}_"
        return privateKeysWithPrefix(prefix)
            .mapNotNull { key ->
                getPrivateString(key, null)?.let { raw ->
                    runCatching {
                        val json = JSONObject(raw)
                        SparkUnclaimedDeposit(
                            txid = json.getString("txid"),
                            vout = json.optLong("vout", 0L).toUInt(),
                            amountSats = json.optLong("amountSats", 0L),
                            isMature = json.optBoolean("isMature", false),
                            timestamp =
                                if (json.isNull("timestamp")) {
                                    null
                                } else {
                                    json.optLong("timestamp")
                                },
                            address =
                                if (json.isNull("address")) {
                                    null
                                } else {
                                    json.optString("address")
                                },
                            claimError =
                                if (json.isNull("claimError")) {
                                    null
                                } else {
                                    json.optString("claimError")
                                },
                        )
                    }.getOrNull()
                }
            }
    }

    fun saveSparkWalletStateCache(
        walletId: String,
        state: SparkWalletState,
    ) {
        val json =
            JSONObject()
                .put("walletId", walletId)
                .put("identityPubkey", state.identityPubkey ?: JSONObject.NULL)
                .put("balanceSats", state.balanceSats)
                .put("lightningAddress", state.lightningAddress ?: JSONObject.NULL)
                .put("lastSyncTimestamp", state.lastSyncTimestamp)
                .put(
                    "payments",
                    JSONArray().apply {
                        state.payments.forEach { payment ->
                            put(sparkPaymentToJson(payment))
                        }
                    },
                )
                .put(
                    "unclaimedDeposits",
                    JSONArray().apply {
                        state.unclaimedDeposits.forEach { deposit ->
                            put(sparkUnclaimedDepositToJson(deposit))
                        }
                    },
                )
        putPrivateString("${KEY_SPARK_WALLET_STATE_CACHE_PREFIX}$walletId", json.toString())
    }

    fun getSparkWalletStateCache(walletId: String): SparkWalletState? {
        val raw = getPrivateString("${KEY_SPARK_WALLET_STATE_CACHE_PREFIX}$walletId", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SparkWalletState(
                walletId = walletId,
                isInitialized = true,
                identityPubkey =
                    if (json.isNull("identityPubkey")) {
                        null
                    } else {
                        json.optString("identityPubkey")
                    },
                balanceSats = json.optLong("balanceSats", 0L),
                payments = json.optJSONArray("payments").toSparkPayments(),
                unclaimedDeposits = json.optJSONArray("unclaimedDeposits").toSparkUnclaimedDeposits(),
                lightningAddress =
                    if (json.isNull("lightningAddress")) {
                        null
                    } else {
                        json.optString("lightningAddress")
                    },
                isSyncing = false,
                lastSyncTimestamp = json.optLong("lastSyncTimestamp", 0L),
            )
        }.getOrNull()
    }

    fun clearSparkWalletStateCache(walletId: String) {
        removePrivateValue("${KEY_SPARK_WALLET_STATE_CACHE_PREFIX}$walletId")
    }

    private fun sparkPaymentToJson(payment: SparkPayment): JSONObject =
        JSONObject()
            .put("id", payment.id)
            .put("type", payment.type)
            .put("status", payment.status)
            .put("amountSats", payment.amountSats)
            .put("feeSats", payment.feeSats)
            .put("timestamp", payment.timestamp)
            .put("method", payment.method)
            .put("recipient", payment.recipient ?: JSONObject.NULL)
            .put("methodDetails", payment.methodDetails)

    private fun sparkUnclaimedDepositToJson(deposit: SparkUnclaimedDeposit): JSONObject =
        JSONObject()
            .put("txid", deposit.txid)
            .put("vout", deposit.vout.toLong())
            .put("amountSats", deposit.amountSats)
            .put("isMature", deposit.isMature)
            .put("timestamp", deposit.timestamp ?: JSONObject.NULL)
            .put("address", deposit.address ?: JSONObject.NULL)
            .put("claimError", deposit.claimError ?: JSONObject.NULL)

    private fun JSONArray?.toSparkPayments(): List<SparkPayment> {
        if (this == null) return emptyList()
        return List(length()) { index -> optJSONObject(index) }
            .mapNotNull { json ->
                json?.let {
                    SparkPayment(
                        id = it.optString("id"),
                        type = it.optString("type"),
                        status = it.optString("status"),
                        amountSats = it.optLong("amountSats", 0L),
                        feeSats = it.optLong("feeSats", 0L),
                        timestamp = it.optLong("timestamp", 0L),
                        method = it.optString("method"),
                        recipient =
                            if (it.isNull("recipient")) {
                                null
                            } else {
                                it.optString("recipient")
                            },
                        methodDetails = it.optString("methodDetails", it.optString("method")),
                    )
                }
            }
            .filter { it.id.isNotBlank() }
    }

    private fun JSONArray?.toSparkUnclaimedDeposits(): List<SparkUnclaimedDeposit> {
        if (this == null) return emptyList()
        return List(length()) { index -> optJSONObject(index) }
            .mapNotNull { json ->
                json?.let {
                    SparkUnclaimedDeposit(
                        txid = it.optString("txid"),
                        vout = it.optLong("vout", 0L).toUInt(),
                        amountSats = it.optLong("amountSats", 0L),
                        isMature = it.optBoolean("isMature", false),
                        timestamp =
                            if (it.isNull("timestamp")) {
                                null
                            } else {
                                it.optLong("timestamp")
                            },
                        address =
                            if (it.isNull("address")) {
                                null
                            } else {
                                it.optString("address")
                            },
                        claimError =
                            if (it.isNull("claimError")) {
                                null
                            } else {
                                it.optString("claimError")
                            },
                    )
                }
            }
            .filter { it.txid.isNotBlank() }
    }

    fun saveTransactionSource(
        walletId: String,
        txid: String,
        source: String,
    ) {
        if (source.isBlank()) return
        val key = "${KEY_TX_SOURCE_PREFIX}${walletId}_$txid"
        putPrivateString(key, source)
    }

    fun getAllTransactionSources(walletId: String): Map<String, String> {
        val prefix = "${KEY_TX_SOURCE_PREFIX}${walletId}_"
        return privateStringsWithPrefix(prefix)
            .mapKeys { (key, _) -> key.removePrefix(prefix) }
            .filterValues { it.isNotBlank() }
    }

    /**
     * Save internal Liquid transaction source metadata.
     */
    fun saveLiquidTransactionSource(
        walletId: String,
        txid: String,
        source: LiquidTxSource,
    ) {
        val key = "${KEY_LIQUID_TX_SOURCE_PREFIX}${walletId}_$txid"
        putPrivateString(key, source.name)
    }

    /**
     * Get all internal Liquid transaction source metadata for a wallet.
     */
    fun getAllLiquidTransactionSources(walletId: String): Map<String, LiquidTxSource> {
        return getLiquidMetadataSnapshot(walletId).txSources
    }

    fun saveLiquidTransactionRecipient(
        walletId: String,
        txid: String,
        recipientAddress: String,
    ) {
        val key = "${KEY_LIQUID_TX_RECIPIENT_PREFIX}${walletId}_$txid"
        putPrivateString(key, recipientAddress)
    }

    fun saveLiquidTransactionFeeDetails(
        walletId: String,
        txid: String,
        feeRate: Double,
        vsize: Double,
    ) {
        val key = "${KEY_LIQUID_TX_FEE_DETAILS_PREFIX}${walletId}_$txid"
        val json =
            JSONObject().apply {
                put("feeRate", feeRate)
                put("vsize", vsize)
            }
        putPrivateString(key, json.toString())
    }

    fun saveLiquidSwapDetails(
        walletId: String,
        txid: String,
        details: LiquidSwapDetails,
    ) {
        val key = "${KEY_LIQUID_TX_SWAP_DETAILS_PREFIX}${walletId}_$txid"
        val json =
            JSONObject().apply {
                put("service", details.service.name)
                put("direction", details.direction.name)
                put("swapId", details.swapId)
                put("role", details.role.name)
                put("depositAddress", details.depositAddress)
                put("receiveAddress", details.receiveAddress)
                put("refundAddress", details.refundAddress)
                put("sendAmountSats", details.sendAmountSats)
                put("expectedReceiveAmountSats", details.expectedReceiveAmountSats)
                put("paymentInput", details.paymentInput)
                put("resolvedPaymentInput", details.resolvedPaymentInput)
                put("invoice", details.invoice)
                put("status", details.status)
                put("timeoutBlockHeight", details.timeoutBlockHeight)
                put("refundPublicKey", details.refundPublicKey)
                put("claimPublicKey", details.claimPublicKey)
                put("swapTree", details.swapTree)
                put("blindingKey", details.blindingKey)
            }
        putPrivateString(key, json.toString(), commit = true)
    }

    fun saveTransactionSwapDetails(
        walletId: String,
        txid: String,
        details: LiquidSwapDetails,
    ) {
        val key = "${KEY_TX_SWAP_DETAILS_PREFIX}${walletId}_$txid"
        val json =
            JSONObject().apply {
                put("service", details.service.name)
                put("direction", details.direction.name)
                put("swapId", details.swapId)
                put("role", details.role.name)
                put("depositAddress", details.depositAddress)
                put("receiveAddress", details.receiveAddress)
                put("refundAddress", details.refundAddress)
                put("sendAmountSats", details.sendAmountSats)
                put("expectedReceiveAmountSats", details.expectedReceiveAmountSats)
                put("paymentInput", details.paymentInput)
                put("resolvedPaymentInput", details.resolvedPaymentInput)
                put("invoice", details.invoice)
                put("status", details.status)
                put("timeoutBlockHeight", details.timeoutBlockHeight)
                put("refundPublicKey", details.refundPublicKey)
                put("claimPublicKey", details.claimPublicKey)
                put("swapTree", details.swapTree)
                put("blindingKey", details.blindingKey)
            }
        putPrivateString(key, json.toString(), commit = true)
    }

    fun getAllTransactionSwapDetails(walletId: String): Map<String, LiquidSwapDetails> {
        val prefix = "${KEY_TX_SWAP_DETAILS_PREFIX}${walletId}_"
        val txSwapDetails = mutableMapOf<String, LiquidSwapDetails>()
        privateStringsWithPrefix(prefix).forEach { (key, value) ->
            val txid = key.removePrefix(prefix)
            val details = parseLiquidSwapDetails(value) ?: return@forEach
            txSwapDetails[txid] = details
        }
        return txSwapDetails
    }

    fun getAllLiquidSwapDetails(walletId: String): Map<String, LiquidSwapDetails> {
        return getLiquidMetadataSnapshot(walletId).txSwapDetails
    }

    /**
     * Persist pending Lightning-to-Liquid receive metadata until the final txid is known.
     */
    fun savePendingLightningReceive(
        walletId: String,
        swapId: String,
        claimAddress: String,
        label: String,
    ) {
        putPrivateString(
            "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX}${walletId}_$swapId",
            claimAddress,
            commit = true,
        )
        putPrivateString(
            "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX}${walletId}_$swapId",
            label,
            commit = true,
        )
    }

    /**
     * Get pending Lightning receive metadata for a specific swap.
     */
    fun getPendingLightningReceive(
        walletId: String,
        swapId: String,
    ): PendingLightningReceive? {
        val addressKey = "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX}${walletId}_$swapId"
        val claimAddress = getPrivateString(addressKey, null) ?: return null
        val labelKey = "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX}${walletId}_$swapId"
        return PendingLightningReceive(
            swapId = swapId,
            claimAddress = claimAddress,
            label = getPrivateString(labelKey, "") ?: "",
        )
    }

    /**
     * Get all pending Lightning receive metadata for a wallet.
     */
    fun getAllPendingLightningReceives(walletId: String): List<PendingLightningReceive> {
        return getLiquidMetadataSnapshot(walletId).pendingLightningReceives
    }

    private fun parseLiquidSwapDetails(raw: String): LiquidSwapDetails? {
        return runCatching {
            val json = JSONObject(raw)
            LiquidSwapDetails(
                service = SwapService.valueOf(json.getString("service")),
                direction = SwapDirection.valueOf(json.getString("direction")),
                swapId = json.getString("swapId"),
                role = LiquidSwapTxRole.valueOf(json.optString("role", LiquidSwapTxRole.FUNDING.name)),
                depositAddress = json.getString("depositAddress"),
                receiveAddress = json.optString("receiveAddress").takeIf { it.isNotBlank() },
                refundAddress = json.optString("refundAddress").takeIf { it.isNotBlank() },
                sendAmountSats = json.optLong("sendAmountSats", 0L),
                expectedReceiveAmountSats = json.optLong("expectedReceiveAmountSats", 0L),
                paymentInput = json.optString("paymentInput").takeIf { it.isNotBlank() },
                resolvedPaymentInput = json.optString("resolvedPaymentInput").takeIf { it.isNotBlank() },
                invoice = json.optString("invoice").takeIf { it.isNotBlank() },
                status = json.optString("status").takeIf { it.isNotBlank() },
                timeoutBlockHeight =
                    json.optInt("timeoutBlockHeight").takeIf {
                        !json.isNull("timeoutBlockHeight")
                    },
                refundPublicKey = json.optString("refundPublicKey").takeIf { it.isNotBlank() },
                claimPublicKey = json.optString("claimPublicKey").takeIf { it.isNotBlank() },
                swapTree = json.optString("swapTree").takeIf { it.isNotBlank() },
                blindingKey = json.optString("blindingKey").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private fun parseLiquidTxFeeDetails(raw: String): LiquidTxFeeDetails? {
        return runCatching {
            val json = JSONObject(raw)
            val feeRate = json.optDouble("feeRate", Double.NaN)
            val vsize = json.optDouble("vsize", Double.NaN)
            if (feeRate.isNaN() || vsize.isNaN() || feeRate <= 0.0 || vsize <= 0.0) {
                null
            } else {
                LiquidTxFeeDetails(
                    feeRate = feeRate,
                    vsize = vsize,
                )
            }
        }.getOrNull()
    }

    fun getLiquidMetadataSnapshot(walletId: String): LiquidMetadataSnapshot {
        val txLabelPrefix = "${KEY_LIQUID_TX_LABEL_PREFIX}${walletId}_"
        val txSourcePrefix = "${KEY_LIQUID_TX_SOURCE_PREFIX}${walletId}_"
        val txSwapPrefix = "${KEY_LIQUID_TX_SWAP_DETAILS_PREFIX}${walletId}_"
        val txRecipientPrefix = "${KEY_LIQUID_TX_RECIPIENT_PREFIX}${walletId}_"
        val txFeeDetailsPrefix = "${KEY_LIQUID_TX_FEE_DETAILS_PREFIX}${walletId}_"
        val pendingAddressPrefix = "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX}${walletId}_"
        val pendingLabelPrefix = "${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX}${walletId}_"

        val txLabels = mutableMapOf<String, String>()
        val txSources = mutableMapOf<String, LiquidTxSource>()
        val txSwapDetails = mutableMapOf<String, LiquidSwapDetails>()
        val txRecipients = mutableMapOf<String, String>()
        val txFeeDetails = mutableMapOf<String, LiquidTxFeeDetails>()
        val pendingAddresses = mutableMapOf<String, String>()
        val pendingLabels = mutableMapOf<String, String>()

        privateStringsWithPrefixes(
            listOf(
                txLabelPrefix,
                txSourcePrefix,
                txSwapPrefix,
                txRecipientPrefix,
                txFeeDetailsPrefix,
                pendingAddressPrefix,
                pendingLabelPrefix,
            ),
        ).forEach { (key, value) ->
            when {
                key.startsWith(txLabelPrefix) -> {
                    txLabels[key.removePrefix(txLabelPrefix)] = value
                }

                key.startsWith(txSourcePrefix) -> {
                    val txid = key.removePrefix(txSourcePrefix)
                    val source = runCatching { LiquidTxSource.valueOf(value) }.getOrNull() ?: return@forEach
                    txSources[txid] = source
                }

                key.startsWith(txSwapPrefix) -> {
                    val txid = key.removePrefix(txSwapPrefix)
                    val details = parseLiquidSwapDetails(value) ?: return@forEach
                    txSwapDetails[txid] = details
                }

                key.startsWith(txRecipientPrefix) -> {
                    txRecipients[key.removePrefix(txRecipientPrefix)] = value
                }

                key.startsWith(txFeeDetailsPrefix) -> {
                    val txid = key.removePrefix(txFeeDetailsPrefix)
                    val details = parseLiquidTxFeeDetails(value) ?: return@forEach
                    txFeeDetails[txid] = details
                }

                key.startsWith(pendingAddressPrefix) -> {
                    pendingAddresses[key.removePrefix(pendingAddressPrefix)] = value
                }

                key.startsWith(pendingLabelPrefix) -> {
                    pendingLabels[key.removePrefix(pendingLabelPrefix)] = value
                }
            }
        }

        return LiquidMetadataSnapshot(
            txLabels = txLabels,
            txSources = txSources,
            txSwapDetails = txSwapDetails,
            txRecipients = txRecipients,
            txFeeDetails = txFeeDetails,
            pendingLightningReceives =
                pendingAddresses.map { (swapId, claimAddress) ->
                    PendingLightningReceive(
                        swapId = swapId,
                        claimAddress = claimAddress,
                        label = pendingLabels[swapId].orEmpty(),
                    )
                },
        )
    }

    /**
     * Delete pending Lightning receive metadata for a specific swap.
     */
    fun deletePendingLightningReceive(
        walletId: String,
        swapId: String,
    ) {
        removePrivateValue("${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX}${walletId}_$swapId", commit = true)
        removePrivateValue("${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX}${walletId}_$swapId", commit = true)
    }

    /**
     * Delete any pending Lightning receive metadata that targets a claim address.
     */
    fun deletePendingLightningReceivesByClaimAddress(
        walletId: String,
        claimAddress: String,
    ) {
        val matchingSwapIds =
            getAllPendingLightningReceives(walletId)
                .filter { it.claimAddress == claimAddress }
                .map { it.swapId }

        if (matchingSwapIds.isEmpty()) return

        matchingSwapIds.forEach { swapId ->
            removePrivateValue("${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX}${walletId}_$swapId", commit = true)
            removePrivateValue("${KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX}${walletId}_$swapId", commit = true)
        }
    }

    fun savePendingSwap(
        walletId: String,
        pendingSwap: PendingSwapSession,
    ) {
        val swaps = getPendingSwaps(walletId).toMutableList()
        swaps.removeAll { it.swapId == pendingSwap.swapId }
        swaps.add(pendingSwap)
        putPrivateString(
            "${KEY_PENDING_SWAP_PREFIX}$walletId",
            JSONArray(swaps.sortedByDescending { it.createdAt }.map(::pendingSwapToJson)).toString(),
            commit = true,
        )
        val snapshotKey = pendingSwapSnapshotKey(walletId, pendingSwap.swapId)
        securePrefs.edit(commit = true) {
            if (pendingSwap.boltzSnapshot.isNullOrBlank()) {
                remove(snapshotKey)
            } else {
                putString(snapshotKey, pendingSwap.boltzSnapshot)
            }
        }
    }

    fun getPendingSwaps(walletId: String): List<PendingSwapSession> {
        val raw = getPrivateString("${KEY_PENDING_SWAP_PREFIX}$walletId", null) ?: return emptyList()
        return try {
            val array = runCatching { JSONArray(raw) }.getOrElse {
                JSONArray().put(JSONObject(raw))
            }
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    parsePendingSwap(walletId, json)?.let(::add)
                }
            }.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getPendingSwap(walletId: String, swapId: String): PendingSwapSession? {
        return getPendingSwaps(walletId).firstOrNull { it.swapId == swapId }
    }

    fun deletePendingSwap(
        walletId: String,
        swapId: String? = null,
    ) {
        if (swapId == null) {
            val swaps = getPendingSwaps(walletId)
            securePrefs.edit(commit = true) {
                swaps.forEach { remove(pendingSwapSnapshotKey(walletId, it.swapId)) }
            }
            removePrivateValue("${KEY_PENDING_SWAP_PREFIX}$walletId", commit = true)
            return
        }

        val swaps = getPendingSwaps(walletId).filterNot { it.swapId == swapId }
        if (swaps.isEmpty()) {
            removePrivateValue("${KEY_PENDING_SWAP_PREFIX}$walletId", commit = true)
        } else {
            putPrivateString(
                "${KEY_PENDING_SWAP_PREFIX}$walletId",
                JSONArray(swaps.map(::pendingSwapToJson)).toString(),
                commit = true,
            )
        }
        securePrefs.edit(commit = true) {
            remove(pendingSwapSnapshotKey(walletId, swapId))
        }
    }

    fun saveBoltzChainSwapDraft(
        walletId: String,
        draft: BoltzChainSwapDraft,
    ) {
        val drafts = getBoltzChainSwapDrafts(walletId).toMutableList()
        drafts.removeAll { it.draftId == draft.draftId }
        drafts.add(draft.copy(snapshot = null))
        putPrivateString(
            "${KEY_BOLTZ_CHAIN_SWAP_DRAFT_PREFIX}$walletId",
            JSONArray(drafts.sortedByDescending { it.updatedAt }.map(::boltzChainSwapDraftToJson)).toString(),
            commit = true,
        )
        securePrefs.edit(commit = true) {
            val key = boltzChainSwapSnapshotKey(walletId, draft.draftId)
            if (draft.snapshot.isNullOrBlank()) {
                remove(key)
            } else {
                putString(key, draft.snapshot)
            }
        }
    }

    fun getBoltzChainSwapDrafts(walletId: String): List<BoltzChainSwapDraft> {
        val raw = getPrivateString("${KEY_BOLTZ_CHAIN_SWAP_DRAFT_PREFIX}$walletId", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    parseBoltzChainSwapDraft(walletId, json)?.let(::add)
                }
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getBoltzChainSwapDraftBySwapId(
        walletId: String,
        swapId: String,
    ): BoltzChainSwapDraft? {
        return getBoltzChainSwapDrafts(walletId).firstOrNull { it.swapId == swapId }
    }

    fun getBoltzSessionNextIndex(walletId: String): Int? {
        val key = boltzSessionNextIndexKey(walletId)
        if (securePrefs.contains(key)) return securePrefs.getInt(key, -1).takeIf { it >= 0 }
        if (!regularPrefs.contains(key)) return null
        val legacy = regularPrefs.getInt(key, -1).takeIf { it >= 0 }
        securePrefs.edit(commit = true) {
            if (legacy == null) remove(key) else putInt(key, legacy)
        }
        regularPrefs.edit(commit = true) { remove(key) }
        return legacy
    }

    fun setBoltzSessionNextIndex(walletId: String, nextIndex: Int?) {
        val key = boltzSessionNextIndexKey(walletId)
        securePrefs.edit(commit = true) {
            if (nextIndex == null || nextIndex < 0) {
                remove(key)
            } else {
                putInt(key, nextIndex)
            }
        }
        regularPrefs.edit(commit = true) { remove(key) }
    }

    private fun pendingSwapToJson(pendingSwap: PendingSwapSession): JSONObject {
        return JSONObject().apply {
            put("service", pendingSwap.service.name)
            put("direction", pendingSwap.direction.name)
            put("sendAmount", pendingSwap.sendAmount)
            put("requestKey", pendingSwap.requestKey)
            put("label", pendingSwap.label)
            put("usesMaxAmount", pendingSwap.usesMaxAmount)
            put("selectedFundingOutpoints", JSONArray(pendingSwap.selectedFundingOutpoints))
            put("estimatedTerms", estimatedTermsToJson(pendingSwap.estimatedTerms))
            put("swapId", pendingSwap.swapId)
            put("depositAddress", pendingSwap.depositAddress)
            put("receiveAddress", pendingSwap.receiveAddress)
            put("refundAddress", pendingSwap.refundAddress)
            put("createdAt", pendingSwap.createdAt)
            put("reviewExpiresAt", pendingSwap.reviewExpiresAt)
            put("phase", pendingSwap.phase.name)
            put("status", pendingSwap.status)
            put("fundingLiquidFeeRate", pendingSwap.fundingLiquidFeeRate)
            put("boltzOrderAmountSats", pendingSwap.boltzOrderAmountSats)
            put("boltzVerifiedRecipientAmountSats", pendingSwap.boltzVerifiedRecipientAmountSats)
            put("boltzMaxOrderAmountVerified", pendingSwap.boltzMaxOrderAmountVerified)
            put("actualFundingFeeSats", pendingSwap.actualFundingFeeSats)
            put("actualFundingTxVBytes", pendingSwap.actualFundingTxVBytes)
            put("fundingTxid", pendingSwap.fundingTxid)
            put("settlementTxid", pendingSwap.settlementTxid)
        }
    }

    private fun parsePendingSwap(
        walletId: String,
        json: JSONObject,
    ): PendingSwapSession? {
        return runCatching {
            PendingSwapSession(
                service = SwapService.valueOf(json.getString("service")),
                direction = SwapDirection.valueOf(json.getString("direction")),
                sendAmount = json.getLong("sendAmount"),
                requestKey =
                    json.optString("requestKey", "").takeIf {
                        it.isNotBlank() && !json.isNull("requestKey")
                    },
                label =
                    json.optString("label", "").takeIf {
                        it.isNotBlank() && !json.isNull("label")
                    },
                usesMaxAmount = json.optBoolean("usesMaxAmount", false),
                selectedFundingOutpoints =
                    json.optJSONArray("selectedFundingOutpoints")?.let { array ->
                        buildList {
                            for (index in 0 until array.length()) {
                                array.optString(index)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }
                    } ?: emptyList(),
                estimatedTerms = parseEstimatedTerms(json.optJSONObject("estimatedTerms")),
                swapId = json.getString("swapId"),
                depositAddress = json.getString("depositAddress"),
                receiveAddress =
                    json.optString("receiveAddress", "").takeIf {
                        it.isNotBlank() && !json.isNull("receiveAddress")
                    },
                refundAddress =
                    json.optString("refundAddress", "").takeIf {
                        it.isNotBlank() && !json.isNull("refundAddress")
                    },
                createdAt = json.optLong("createdAt", 0L),
                reviewExpiresAt = json.optLong("reviewExpiresAt", 0L),
                phase =
                    runCatching {
                        PendingSwapPhase.valueOf(json.optString("phase", PendingSwapPhase.REVIEW.name))
                    }.getOrDefault(PendingSwapPhase.REVIEW),
                status = json.optString("status", ""),
                fundingLiquidFeeRate = json.optDouble("fundingLiquidFeeRate", 0.0),
                boltzOrderAmountSats =
                    json.optLong("boltzOrderAmountSats").takeIf {
                        !json.isNull("boltzOrderAmountSats")
                    },
                boltzVerifiedRecipientAmountSats =
                    json.optLong("boltzVerifiedRecipientAmountSats").takeIf {
                        !json.isNull("boltzVerifiedRecipientAmountSats")
                    },
                boltzMaxOrderAmountVerified = json.optBoolean("boltzMaxOrderAmountVerified", false),
                actualFundingFeeSats =
                    json.optLong("actualFundingFeeSats").takeIf {
                        !json.isNull("actualFundingFeeSats")
                    },
                actualFundingTxVBytes =
                    json.optDouble("actualFundingTxVBytes").takeIf {
                        !json.isNull("actualFundingTxVBytes")
                    },
                fundingTxid =
                    json.optString("fundingTxid", "").takeIf {
                        it.isNotBlank() && !json.isNull("fundingTxid")
                    },
                settlementTxid =
                    json.optString("settlementTxid", "").takeIf {
                        it.isNotBlank() && !json.isNull("settlementTxid")
                    },
                boltzSnapshot =
                    securePrefs.getString(
                        pendingSwapSnapshotKey(walletId, json.getString("swapId")),
                        null,
                    ),
            )
        }.getOrNull()
    }

    private fun boltzChainSwapDraftToJson(draft: BoltzChainSwapDraft): JSONObject {
        return JSONObject().apply {
            put("draftId", draft.draftId)
            put("requestKey", draft.requestKey)
            put("direction", draft.direction.name)
            put("sendAmount", draft.sendAmount)
            put("orderAmountSats", draft.orderAmountSats)
            put("maxOrderAmountVerified", draft.maxOrderAmountVerified)
            put("usesMaxAmount", draft.usesMaxAmount)
            put("selectedFundingOutpoints", JSONArray(draft.selectedFundingOutpoints))
            put("estimatedTerms", estimatedTermsToJson(draft.estimatedTerms))
            put("fundingLiquidFeeRate", draft.fundingLiquidFeeRate)
            put("bitcoinAddress", draft.bitcoinAddress)
            put("liquidAddress", draft.liquidAddress)
            put("swapId", draft.swapId)
            put("depositAddress", draft.depositAddress)
            put("receiveAddress", draft.receiveAddress)
            put("refundAddress", draft.refundAddress)
            put("state", draft.state.name)
            put("fundingPreview", fundingPreviewToJson(draft.fundingPreview))
            put("fundingTxid", draft.fundingTxid)
            put("settlementTxid", draft.settlementTxid)
            put("lastError", draft.lastError)
            put("createdAt", draft.createdAt)
            put("updatedAt", draft.updatedAt)
            put("reviewExpiresAt", draft.reviewExpiresAt)
        }
    }

    private fun parseBoltzChainSwapDraft(
        walletId: String,
        json: JSONObject,
    ): BoltzChainSwapDraft? {
        return runCatching {
            val draftId = json.getString("draftId")
            BoltzChainSwapDraft(
                draftId = draftId,
                requestKey = json.optString("requestKey", draftId),
                direction = SwapDirection.valueOf(json.getString("direction")),
                sendAmount = json.getLong("sendAmount"),
                orderAmountSats = json.optLong("orderAmountSats", json.getLong("sendAmount")),
                maxOrderAmountVerified = json.optBoolean("maxOrderAmountVerified", false),
                usesMaxAmount = json.optBoolean("usesMaxAmount", false),
                selectedFundingOutpoints =
                    json.optJSONArray("selectedFundingOutpoints")?.let { array ->
                        buildList {
                            for (index in 0 until array.length()) {
                                array.optString(index)
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }
                    } ?: emptyList(),
                estimatedTerms = parseEstimatedTerms(json.optJSONObject("estimatedTerms")),
                fundingLiquidFeeRate = json.optDouble("fundingLiquidFeeRate", 0.0),
                bitcoinAddress = json.optString("bitcoinAddress", ""),
                liquidAddress =
                    json.optString("liquidAddress", "").takeIf {
                        it.isNotBlank() && !json.isNull("liquidAddress")
                    },
                swapId =
                    json.optString("swapId", "").takeIf {
                        it.isNotBlank() && !json.isNull("swapId")
                    },
                depositAddress =
                    json.optString("depositAddress", "").takeIf {
                        it.isNotBlank() && !json.isNull("depositAddress")
                    },
                receiveAddress =
                    json.optString("receiveAddress", "").takeIf {
                        it.isNotBlank() && !json.isNull("receiveAddress")
                    },
                refundAddress =
                    json.optString("refundAddress", "").takeIf {
                        it.isNotBlank() && !json.isNull("refundAddress")
                    },
                state =
                    runCatching {
                        BoltzChainSwapDraftState.valueOf(
                            json.optString("state", BoltzChainSwapDraftState.CREATING.name),
                        )
                    }.getOrDefault(BoltzChainSwapDraftState.CREATING),
                fundingPreview = parseFundingPreview(json.optJSONObject("fundingPreview")),
                fundingTxid =
                    json.optString("fundingTxid", "").takeIf {
                        it.isNotBlank() && !json.isNull("fundingTxid")
                    },
                settlementTxid =
                    json.optString("settlementTxid", "").takeIf {
                        it.isNotBlank() && !json.isNull("settlementTxid")
                    },
                lastError =
                    json.optString("lastError", "").takeIf {
                        it.isNotBlank() && !json.isNull("lastError")
                    },
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                reviewExpiresAt = json.optLong("reviewExpiresAt", 0L),
                snapshot = securePrefs.getString(boltzChainSwapSnapshotKey(walletId, draftId), null),
            )
        }.getOrNull()
    }

    private fun estimatedTermsToJson(estimatedTerms: EstimatedSwapTerms): JSONObject {
        return JSONObject().apply {
            put("receiveAmount", estimatedTerms.receiveAmount)
            put("serviceFee", estimatedTerms.serviceFee)
            put("btcNetworkFee", estimatedTerms.btcNetworkFee)
            put("liquidNetworkFee", estimatedTerms.liquidNetworkFee)
            put("providerMinerFee", estimatedTerms.providerMinerFee)
            put("btcFeeRate", estimatedTerms.btcFeeRate)
            put("displayLiquidFeeRate", estimatedTerms.displayLiquidFeeRate)
            put("fundingNetworkFee", estimatedTerms.fundingNetworkFee)
            put("fundingFeeRate", estimatedTerms.fundingFeeRate)
            put("payoutNetworkFee", estimatedTerms.payoutNetworkFee)
            put("payoutFeeRate", estimatedTerms.payoutFeeRate)
            put("minAmount", estimatedTerms.minAmount)
            put("maxAmount", estimatedTerms.maxAmount)
            put("estimatedTime", estimatedTerms.estimatedTime)
        }
    }

    private fun parseEstimatedTerms(json: JSONObject?): EstimatedSwapTerms {
        val estimatedJson = json ?: JSONObject()
        return EstimatedSwapTerms(
            receiveAmount = estimatedJson.optLong("receiveAmount", 0L),
            serviceFee = estimatedJson.optLong("serviceFee", 0L),
            btcNetworkFee = estimatedJson.optLong("btcNetworkFee", 0L),
            liquidNetworkFee = estimatedJson.optLong("liquidNetworkFee", 0L),
            providerMinerFee = estimatedJson.optLong("providerMinerFee", 0L),
            btcFeeRate = estimatedJson.optDouble("btcFeeRate", 0.0),
            displayLiquidFeeRate = estimatedJson.optDouble("displayLiquidFeeRate", 0.0),
            fundingNetworkFee = estimatedJson.optLong("fundingNetworkFee", 0L),
            fundingFeeRate = estimatedJson.optDouble("fundingFeeRate", 0.0),
            payoutNetworkFee = estimatedJson.optLong("payoutNetworkFee", 0L),
            payoutFeeRate = estimatedJson.optDouble("payoutFeeRate", 0.0),
            minAmount = estimatedJson.optLong("minAmount", 0L),
            maxAmount = estimatedJson.optLong("maxAmount", 0L),
            estimatedTime = estimatedJson.optString("estimatedTime", ""),
        )
    }

    private fun fundingPreviewToJson(preview: BoltzChainFundingPreview?): JSONObject? {
        if (preview == null) return null
        return JSONObject().apply {
            put("feeSats", preview.feeSats)
            put("txVBytes", preview.txVBytes)
            put("effectiveFeeRate", preview.effectiveFeeRate)
            put("verifiedRecipientAmountSats", preview.verifiedRecipientAmountSats)
            put("error", preview.error)
        }
    }

    private fun parseFundingPreview(json: JSONObject?): BoltzChainFundingPreview? {
        if (json == null) return null
        return BoltzChainFundingPreview(
            feeSats = json.optLong("feeSats", 0L),
            txVBytes = json.optDouble("txVBytes", 0.0),
            effectiveFeeRate = json.optDouble("effectiveFeeRate", 0.0),
            verifiedRecipientAmountSats = json.optLong("verifiedRecipientAmountSats", 0L),
            error =
                json.optString("error", "").takeIf {
                    it.isNotBlank() && !json.isNull("error")
                },
        )
    }

    private fun pendingSwapSnapshotKey(walletId: String, swapId: String): String =
        "${KEY_PENDING_SWAP_SNAPSHOT_PREFIX}${walletId}_$swapId"

    private fun boltzChainSwapSnapshotKey(walletId: String, draftId: String): String =
        "${KEY_BOLTZ_CHAIN_SWAP_SNAPSHOT_PREFIX}${walletId}_$draftId"

    private fun boltzSessionNextIndexKey(walletId: String): String =
        "${KEY_BOLTZ_SESSION_NEXT_INDEX_PREFIX}$walletId"

    fun savePendingLightningInvoiceSession(
        walletId: String,
        session: PendingLightningInvoiceSession,
    ) {
        putPrivateString(pendingLightningInvoiceSessionKey(walletId, session.swapId), session.toJson(), commit = true)
    }

    fun getPendingLightningInvoiceSession(walletId: String): PendingLightningInvoiceSession? {
        return getPendingLightningInvoiceSessions(walletId).maxByOrNull { it.createdAt }
    }

    fun getPendingLightningInvoiceSession(
        walletId: String,
        swapId: String,
    ): PendingLightningInvoiceSession? {
        migrateLegacyPendingLightningInvoiceSession(walletId)
        return getPrivateString(pendingLightningInvoiceSessionKey(walletId, swapId), null)
            ?.let(::parsePendingLightningInvoiceSession)
    }

    fun getPendingLightningInvoiceSessions(walletId: String): List<PendingLightningInvoiceSession> {
        migrateLegacyPendingLightningInvoiceSession(walletId)
        val prefix = "${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}${walletId}_"
        return privateKeysWithPrefix(prefix)
            .mapNotNull { key -> getPrivateString(key, null)?.let(::parsePendingLightningInvoiceSession) }
            .distinctBy { it.swapId }
            .sortedByDescending { it.createdAt }
    }

    fun deletePendingLightningInvoiceSession(
        walletId: String,
        swapId: String? = null,
    ) {
        if (swapId != null) {
            removePrivateValue(pendingLightningInvoiceSessionKey(walletId, swapId), commit = true)
            return
        }
        removePrivateValue("${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}$walletId", commit = true)
        privateKeysWithPrefix("${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}${walletId}_")
            .forEach { key -> removePrivateValue(key, commit = true) }
    }

    private fun migrateLegacyPendingLightningInvoiceSession(walletId: String) {
        val legacyKey = "${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}$walletId"
        val legacyRaw = getPrivateString(legacyKey, null) ?: return
        val legacySession = parsePendingLightningInvoiceSession(legacyRaw) ?: run {
            removePrivateValue(legacyKey, commit = true)
            return
        }
        putPrivateString(pendingLightningInvoiceSessionKey(walletId, legacySession.swapId), legacySession.toJson(), commit = true)
        removePrivateValue(legacyKey, commit = true)
    }

    private fun pendingLightningInvoiceSessionKey(
        walletId: String,
        swapId: String,
    ): String = "${KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX}${walletId}_$swapId"

    private fun PendingLightningInvoiceSession.toJson(): String {
        return JSONObject().apply {
            put("swapId", swapId)
            put("snapshot", snapshot)
            put("invoice", invoice)
            put("amountSats", amountSats)
            put("createdAt", createdAt)
        }.toString()
    }

    private fun parsePendingLightningInvoiceSession(raw: String): PendingLightningInvoiceSession? {
        return try {
            val json = JSONObject(raw)
            PendingLightningInvoiceSession(
                swapId = json.getString("swapId"),
                snapshot = json.getString("snapshot"),
                invoice = json.optString("invoice"),
                amountSats = json.optLong("amountSats", 0L),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun savePendingLightningPaymentSession(
        walletId: String,
        session: PendingLightningPaymentSession,
    ) {
        val json =
            JSONObject().apply {
                put("swapId", session.swapId)
                put("backend", session.backend.name)
                put("requestKey", session.requestKey)
                put("paymentInput", session.paymentInput)
                put("lockupAddress", session.lockupAddress)
                put("lockupAmountSats", session.lockupAmountSats)
                put("refundAddress", session.refundAddress)
                put("snapshot", session.snapshot)
                put("requestedAmountSats", session.requestedAmountSats)
                put("resolvedPaymentInput", session.resolvedPaymentInput)
                put("fetchedInvoice", session.fetchedInvoice)
                put("refundPublicKey", session.refundPublicKey)
                put("paymentAmountSats", session.paymentAmountSats)
                put("swapFeeSats", session.swapFeeSats)
                put("createdAt", session.createdAt)
                put("phase", session.phase.name)
                put("status", session.status)
                put("fundingTxid", session.fundingTxid)
                put("boltzClaimPublicKey", session.boltzClaimPublicKey)
                put("timeoutBlockHeight", session.timeoutBlockHeight)
                put("swapTree", session.swapTree)
                put("blindingKey", session.blindingKey)
            }.toString()
        putPrivateString("${KEY_PENDING_BOLTZ_LIGHTNING_PAYMENT_PREFIX}$walletId", json, commit = true)
    }

    fun getPendingLightningPaymentSession(walletId: String): PendingLightningPaymentSession? {
        val raw = getPrivateString("${KEY_PENDING_BOLTZ_LIGHTNING_PAYMENT_PREFIX}$walletId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            PendingLightningPaymentSession(
                swapId = json.getString("swapId"),
                backend = runCatching {
                    LightningPaymentBackend.valueOf(
                        json.optString("backend", LightningPaymentBackend.LWK_PREPARE_PAY.name),
                    )
                }.getOrDefault(LightningPaymentBackend.LWK_PREPARE_PAY),
                requestKey = json.optString("requestKey", json.getString("swapId")),
                paymentInput = json.optString("paymentInput", json.optString("invoice")),
                lockupAddress = json.getString("lockupAddress"),
                lockupAmountSats = json.getLong("lockupAmountSats"),
                refundAddress = json.optString("refundAddress", ""),
                snapshot =
                    json.optString("snapshot", "").takeIf {
                        it.isNotBlank() && !json.isNull("snapshot")
                    },
                requestedAmountSats =
                    json.optLong("requestedAmountSats").takeIf {
                        !json.isNull("requestedAmountSats")
                    },
                resolvedPaymentInput =
                    json.optString("resolvedPaymentInput", "").takeIf {
                        it.isNotBlank() && !json.isNull("resolvedPaymentInput")
                    },
                fetchedInvoice =
                    json.optString("fetchedInvoice", "").takeIf {
                        it.isNotBlank() && !json.isNull("fetchedInvoice")
                    },
                refundPublicKey =
                    json.optString("refundPublicKey", "").takeIf {
                        it.isNotBlank() && !json.isNull("refundPublicKey")
                    },
                paymentAmountSats = json.optLong("paymentAmountSats", 0L),
                swapFeeSats = json.optLong("swapFeeSats", 0L),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                phase = runCatching {
                    PendingLightningPaymentPhase.valueOf(
                        json.optString("phase", PendingLightningPaymentPhase.PREPARED.name),
                    )
                }.getOrDefault(PendingLightningPaymentPhase.PREPARED),
                status = json.optString("status", ""),
                fundingTxid =
                    json.optString("fundingTxid", "").takeIf {
                        it.isNotBlank() && !json.isNull("fundingTxid")
                    },
                boltzClaimPublicKey =
                    json.optString("boltzClaimPublicKey", "").takeIf {
                        it.isNotBlank() && !json.isNull("boltzClaimPublicKey")
                    },
                timeoutBlockHeight =
                    json.optInt("timeoutBlockHeight", -1).takeIf {
                        it >= 0 && !json.isNull("timeoutBlockHeight")
                    },
                swapTree =
                    json.optString("swapTree", "").takeIf {
                        it.isNotBlank() && !json.isNull("swapTree")
                    },
                blindingKey =
                    json.optString("blindingKey", "").takeIf {
                        it.isNotBlank() && !json.isNull("blindingKey")
                    },
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deletePendingLightningPaymentSession(walletId: String) {
        removePrivateValue("${KEY_PENDING_BOLTZ_LIGHTNING_PAYMENT_PREFIX}$walletId", commit = true)
    }

    // ==================== Transaction First-Seen Timestamps ====================

    /**
     * Get the first-seen timestamp for a pending transaction.
     * Returns null if not stored yet.
     */
    fun getTxFirstSeen(
        walletId: String,
        txid: String,
    ): Long? {
        val key = "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_$txid"
        return getPrivateLong(key)
    }

    /**
     * Store the first-seen timestamp for a pending transaction.
     * Only writes if no value is stored yet (preserves the original first-seen time).
     */
    fun setTxFirstSeenIfAbsent(
        walletId: String,
        txid: String,
        timestamp: Long,
    ) {
        val key = "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_$txid"
        putPrivateLongIfAbsent(key, timestamp)
    }

    /**
     * Remove first-seen timestamp for a transaction (e.g. when confirmed or dropped).
     */
    fun removeTxFirstSeen(
        walletId: String,
        txid: String,
    ) {
        val key = "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_$txid"
        removePrivateValue(key)
    }

    /**
     * Persist a pending RBF replacement mapping so the superseded tx can be
     * hidden from history until the canonical wallet state catches up.
     */
    fun savePendingReplacementTransaction(
        walletId: String,
        originalTxid: String,
        replacementTxid: String,
    ) {
        val key = "${KEY_PENDING_REPLACEMENT_TX_PREFIX}${walletId}_$originalTxid"
        putPrivateString(key, replacementTxid)
    }

    /**
     * Return wallet-local pending RBF replacement mappings (old txid -> new txid).
     */
    fun getPendingReplacementTransactions(walletId: String): Map<String, String> {
        val prefix = "${KEY_PENDING_REPLACEMENT_TX_PREFIX}${walletId}_"
        return privateKeysWithPrefix(prefix)
            .associateWith { key -> getPrivateString(key, null) }
            .mapNotNull { (key, value) ->
                value?.takeIf { it.isNotBlank() }?.let { key.removePrefix(prefix) to it }
            }
            .toMap()
    }

    /**
     * Remove a pending RBF replacement mapping once it is no longer needed.
     */
    fun removePendingReplacementTransaction(
        walletId: String,
        originalTxid: String,
    ) {
        val key = "${KEY_PENDING_REPLACEMENT_TX_PREFIX}${walletId}_$originalTxid"
        removePrivateValue(key)
    }

    // ==================== Frozen UTXOs ====================

    /**
     * Freeze a UTXO (prevent it from being spent)
     */
    fun freezeUtxo(
        walletId: String,
        outpoint: String,
    ) {
        val frozen = getFrozenUtxos(walletId).toMutableSet()
        frozen.add(outpoint)
        saveFrozenUtxos(walletId, frozen)
    }

    /**
     * Unfreeze a UTXO
     */
    fun unfreezeUtxo(
        walletId: String,
        outpoint: String,
    ) {
        val frozen = getFrozenUtxos(walletId).toMutableSet()
        frozen.remove(outpoint)
        saveFrozenUtxos(walletId, frozen)
    }

    /**
     * Get all frozen UTXOs for a wallet
     */
    fun getFrozenUtxos(walletId: String): Set<String> {
        val key = "${KEY_FROZEN_UTXOS_PREFIX}$walletId"
        val json = getPrivateString(key, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Save frozen UTXOs for a wallet
     */
    private fun saveFrozenUtxos(
        walletId: String,
        outpoints: Set<String>,
    ) {
        val key = "${KEY_FROZEN_UTXOS_PREFIX}$walletId"
        val jsonArray = JSONArray(outpoints.toList())
        putPrivateString(key, jsonArray.toString())
    }

    fun setFrozenUtxosForWallet(walletId: String, outpoints: Set<String>) =
        saveFrozenUtxos(walletId, outpoints)

    // ==================== Security Settings ====================

    /**
     * Security method type
     */
    enum class SecurityMethod {
        NONE,
        PIN,
        BIOMETRIC,
    }

    /**
     * Lock timing options
     */
    enum class LockTiming(val displayName: String, val timeoutMs: Long) {
        DISABLED("Disabled", -1L),
        WHEN_MINIMIZED("When minimized", 0L),
        AFTER_1_MIN("After 1 minute", 60_000L),
        AFTER_5_MIN("After 5 minutes", 300_000L),
    }

    /**
     * Get the current security method
     */
    fun getSecurityMethod(): SecurityMethod {
        val method = securePrefs.getString(KEY_SECURITY_METHOD, SecurityMethod.NONE.name)
        return try {
            SecurityMethod.valueOf(method ?: SecurityMethod.NONE.name)
        } catch (_: IllegalArgumentException) {
            SecurityMethod.NONE
        }
    }

    /**
     * Set the security method
     */
    fun setSecurityMethod(method: SecurityMethod) {
        if (method == SecurityMethod.NONE) {
            migrateSpendSecretsToPlaintext()
            lockSpendSecretSession()
        }
        securePrefs.edit { putString(KEY_SECURITY_METHOD, method.name) }
    }

    /**
     * Save the PIN code (hashed with PBKDF2 + random salt)
     */
    fun savePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        securePrefs.edit {
            putString(KEY_PIN_CODE, Base64.encodeToString(hash, Base64.NO_WRAP))
            putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putInt(KEY_PIN_LENGTH, pin.length)
            remove(KEY_PIN_FAILED_ATTEMPTS)
            remove(KEY_PIN_LOCKOUT_UNTIL)
        }
        unlockSpendSecretsWithPin(pin)
    }

    /**
     * Verify if the provided PIN matches the stored PIN hash.
     * Implements rate limiting: locks out after MAX_PIN_ATTEMPTS with exponential backoff.
     * Returns false if locked out.
     */
    fun verifyPin(pin: String): Boolean {
        // Check lockout
        val lockoutUntil = securePrefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > 0 && System.currentTimeMillis() < lockoutUntil) {
            return false // Still locked out
        }

        val storedHash = securePrefs.getString(KEY_PIN_CODE, null) ?: return false
        val storedSaltStr = securePrefs.getString(KEY_PIN_SALT, null)

        // Migration: if no salt exists, this is an old plaintext PIN
        if (storedSaltStr == null) {
            val matches =
                java.security.MessageDigest.isEqual(
                    storedHash.toByteArray(Charsets.UTF_8),
                    pin.toByteArray(Charsets.UTF_8),
                )
            if (matches) {
                // Migrate to hashed format on successful verification
                savePin(pin)
            }
            return matches
        }

        val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPin(pin, salt)
        val storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP)

        // Constant-time comparison to prevent timing attacks
        val matches = constantTimeEquals(inputHash, storedHashBytes)

        if (matches) {
            // Reset failed attempts on success
            securePrefs.edit {
                remove(KEY_PIN_FAILED_ATTEMPTS)
                remove(KEY_PIN_LOCKOUT_UNTIL)
            }
            unlockSpendSecretsWithPin(pin)
        } else {
            // Increment failed attempts and apply lockout if needed
            val attempts = securePrefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) + 1
            securePrefs.edit { putInt(KEY_PIN_FAILED_ATTEMPTS, attempts) }

            if (attempts >= MAX_PIN_ATTEMPTS) {
                // Exponential backoff: 30s, 60s, 120s, 240s, ...
                val lockoutMs = 30_000L * (1L shl (attempts - MAX_PIN_ATTEMPTS).coerceAtMost(6))
                securePrefs.edit {
                    putLong(KEY_PIN_LOCKOUT_UNTIL, System.currentTimeMillis() + lockoutMs)
                }
            }
        }

        return matches
    }

    /**
     * Clear PIN code and reset lockout
     */
    fun clearPin() {
        securePrefs.edit {
            remove(KEY_PIN_CODE)
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_LENGTH)
            remove(KEY_PIN_FAILED_ATTEMPTS)
            remove(KEY_PIN_LOCKOUT_UNTIL)
        }
    }

    /**
     * Check if the given PIN matches the current stored PIN WITHOUT affecting
     * the failed attempt counter or lockout state. Used for duress PIN setup
     * to ensure the duress PIN differs from the real PIN.
     */
    fun pinMatchesCurrent(pin: String): Boolean {
        val storedHash = securePrefs.getString(KEY_PIN_CODE, null) ?: return false
        // Legacy: unhashed PIN — constant-time compare to prevent timing attacks
        val storedSaltStr =
            securePrefs.getString(KEY_PIN_SALT, null)
                ?: return java.security.MessageDigest.isEqual(
                    storedHash.toByteArray(Charsets.UTF_8),
                    pin.toByteArray(Charsets.UTF_8),
                )

        val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPin(pin, salt)
        val storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP)
        return constantTimeEquals(inputHash, storedHashBytes)
    }

    private fun hashPin(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val chars = pin.toCharArray()
        val keySpec = PBEKeySpec(chars, salt, PIN_PBKDF2_ITERATIONS, PIN_HASH_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        return java.security.MessageDigest.isEqual(a, b)
    }

    /**
     * Check if any security is enabled
     */
    fun isSecurityEnabled(): Boolean {
        return getSecurityMethod() != SecurityMethod.NONE
    }

    /**
     * Get the lock timing setting
     */
    fun getLockTiming(): LockTiming {
        val timing = securePrefs.getString(KEY_LOCK_TIMING, LockTiming.DISABLED.name)
        return try {
            LockTiming.valueOf(timing ?: LockTiming.DISABLED.name)
        } catch (_: IllegalArgumentException) {
            LockTiming.DISABLED
        }
    }

    /**
     * Set the lock timing setting
     */
    fun setLockTiming(timing: LockTiming) {
        securePrefs.edit { putString(KEY_LOCK_TIMING, timing.name) }
    }

    /**
     * Get the last time the app was backgrounded
     */
    fun getLastBackgroundTime(): Long {
        return regularPrefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)
    }

    /**
     * Set the last time the app was backgrounded
     */
    fun setLastBackgroundTime(time: Long) {
        regularPrefs.edit { putLong(KEY_LAST_BACKGROUND_TIME, time) }
    }

    // ==================== Screenshot Prevention ====================

    fun getDisableScreenshots(): Boolean {
        return securePrefs.getBoolean(KEY_DISABLE_SCREENSHOTS, true)
    }

    fun setDisableScreenshots(disabled: Boolean) {
        securePrefs.edit { putBoolean(KEY_DISABLE_SCREENSHOTS, disabled) }
    }

    fun getRandomizePinPad(): Boolean {
        return securePrefs.getBoolean(KEY_RANDOMIZE_PIN_PAD, false)
    }

    fun setRandomizePinPad(enabled: Boolean) {
        securePrefs.edit { putBoolean(KEY_RANDOMIZE_PIN_PAD, enabled) }
    }

    // ==================== Cloak Mode ====================

    /**
     * Check if cloak mode is enabled (app disguised as calculator)
     */
    fun isCloakModeEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_CLOAK_MODE_ENABLED, false)
    }

    /**
     * Enable or disable cloak mode
     */
    fun setCloakModeEnabled(enabled: Boolean) {
        securePrefs.edit(commit = true) { putBoolean(KEY_CLOAK_MODE_ENABLED, enabled) }
    }

    /**
     * Save the secret calculator unlock code (stored in encrypted prefs).
     * Uses commit() to survive an immediate process restart.
     */
    fun setCloakCode(code: String) {
        securePrefs.edit(commit = true) { putString(KEY_CLOAK_CODE, code) }
    }

    /**
     * Get the secret calculator unlock code
     */
    fun getCloakCode(): String? {
        return securePrefs.getString(KEY_CLOAK_CODE, null)
    }

    /**
     * Clear all cloak mode data. Uses commit() to ensure writes are flushed
     * to disk before a potential process restart.
     */
    fun clearCloakData() {
        securePrefs.edit(commit = true) {
            remove(KEY_CLOAK_MODE_ENABLED)
            remove(KEY_CLOAK_CODE)
        }
        // Schedule alias swap back to default on next launch
        setPendingIconAlias(ALIAS_DEFAULT)
    }

    /**
     * Set a pending icon alias swap (applied on next cold start to avoid process kill).
     * Uses commit() to survive an immediate process restart.
     */
    fun setPendingIconAlias(alias: String) {
        regularPrefs.edit(commit = true) { putString(KEY_CLOAK_PENDING_ALIAS, alias) }
    }

    /**
     * Get pending icon alias swap, if any
     */
    fun getPendingIconAlias(): String? {
        return regularPrefs.getString(KEY_CLOAK_PENDING_ALIAS, null)
    }

    /**
     * Clear the pending icon alias
     */
    fun clearPendingIconAlias() {
        regularPrefs.edit { remove(KEY_CLOAK_PENDING_ALIAS) }
    }

    /**
     * Get the currently active launcher alias
     */
    fun getCurrentIconAlias(): String {
        return regularPrefs.getString(KEY_CLOAK_CURRENT_ALIAS, ALIAS_DEFAULT) ?: ALIAS_DEFAULT
    }

    /**
     * Set the currently active launcher alias
     */
    fun setCurrentIconAlias(alias: String) {
        regularPrefs.edit { putString(KEY_CLOAK_CURRENT_ALIAS, alias) }
    }

    // ==================== Duress PIN / Decoy Wallet ====================

    /**
     * Save the duress PIN code (hashed with PBKDF2 + random salt, same scheme as real PIN)
     */
    fun saveDuressPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        securePrefs.edit {
            putString(KEY_DURESS_PIN_CODE, Base64.encodeToString(hash, Base64.NO_WRAP))
            putString(KEY_DURESS_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putInt(KEY_DURESS_PIN_LENGTH, pin.length)
        }
        if (spendSecretKey != null) {
            runCatching { unlockOrCreateSpendMaster(KEY_SPEND_MASTER_DURESS_WRAPPED, KEY_SPEND_MASTER_DURESS_SALT, pin) }
        }
    }

    /**
     * Verify if the provided PIN matches the stored duress PIN hash.
     * Shares lockout counters with the real PIN to prevent double-attempt attacks.
     */
    fun verifyDuressPin(
        pin: String,
        incrementFailedAttempts: Boolean = true,
    ): Boolean {
        // Check lockout (shared with real PIN)
        val lockoutUntil = securePrefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > 0 && System.currentTimeMillis() < lockoutUntil) {
            return false
        }

        val storedHash = securePrefs.getString(KEY_DURESS_PIN_CODE, null) ?: return false
        val storedSaltStr = securePrefs.getString(KEY_DURESS_PIN_SALT, null) ?: return false

        val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPin(pin, salt)
        val storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP)

        val matches = constantTimeEquals(inputHash, storedHashBytes)

        if (matches) {
            // Reset shared failed attempts on success
            securePrefs.edit {
                remove(KEY_PIN_FAILED_ATTEMPTS)
                remove(KEY_PIN_LOCKOUT_UNTIL)
            }
            unlockSpendSecretsWithDuressPin(pin)
        } else if (incrementFailedAttempts) {
            // Increment shared failed attempts and apply lockout if needed
            val attempts = securePrefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) + 1
            securePrefs.edit { putInt(KEY_PIN_FAILED_ATTEMPTS, attempts) }

            if (attempts >= MAX_PIN_ATTEMPTS) {
                val lockoutMs = 30_000L * (1L shl (attempts - MAX_PIN_ATTEMPTS).coerceAtMost(6))
                securePrefs.edit {
                    putLong(KEY_PIN_LOCKOUT_UNTIL, System.currentTimeMillis() + lockoutMs)
                }
            }
        }

        return matches
    }

    /**
     * Check if duress mode is enabled
     */
    fun isDuressEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_DURESS_ENABLED, false)
    }

    /**
     * Set whether duress mode is enabled
     */
    fun setDuressEnabled(enabled: Boolean) {
        securePrefs.edit { putBoolean(KEY_DURESS_ENABLED, enabled) }
    }

    /**
     * Get the duress (decoy) wallet ID
     */
    fun getDuressWalletId(): String? {
        return securePrefs.getString(KEY_DURESS_WALLET_ID, null)
    }

    /**
     * Set the duress (decoy) wallet ID
     */
    fun setDuressWalletId(id: String) {
        securePrefs.edit { putString(KEY_DURESS_WALLET_ID, id) }
    }

    /**
     * Get the real wallet ID (snapshot of active wallet when duress was configured)
     */
    fun getRealWalletId(): String? {
        return securePrefs.getString(KEY_REAL_WALLET_ID, null)
    }

    /**
     * Set the real wallet ID
     */
    fun setRealWalletId(id: String) {
        securePrefs.edit { putString(KEY_REAL_WALLET_ID, id) }
    }

    // ==================== Auto-Wipe ====================

    /**
     * Auto-wipe threshold options (number of failed PIN attempts before wiping all data).
     * 0 means disabled.
     */
    enum class AutoWipeThreshold(val displayName: String, val attempts: Int) {
        DISABLED("Disabled", 0),
        AFTER_1("1 attempt", 1),
        AFTER_3("3 attempts", 3),
        AFTER_5("5 attempts", 5),
        AFTER_10("10 attempts", 10),
    }

    /**
     * Get the auto-wipe threshold setting
     */
    fun getAutoWipeThreshold(): AutoWipeThreshold {
        val value = securePrefs.getString(KEY_AUTO_WIPE_THRESHOLD, AutoWipeThreshold.DISABLED.name)
        return try {
            AutoWipeThreshold.valueOf(value ?: AutoWipeThreshold.DISABLED.name)
        } catch (_: IllegalArgumentException) {
            AutoWipeThreshold.DISABLED
        }
    }

    /**
     * Set the auto-wipe threshold
     */
    fun setAutoWipeThreshold(threshold: AutoWipeThreshold) {
        securePrefs.edit { putString(KEY_AUTO_WIPE_THRESHOLD, threshold.name) }
    }

    /**
     * Get the current total number of failed PIN attempts (shared between real and duress PINs).
     */
    fun getFailedPinAttempts(): Int {
        return securePrefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)
    }

    /**
     * Check if auto-wipe should trigger based on current failed attempts.
     * Returns true if threshold is enabled and attempts have reached or exceeded it.
     */
    fun shouldAutoWipe(): Boolean {
        val threshold = getAutoWipeThreshold()
        if (threshold == AutoWipeThreshold.DISABLED) return false
        return getFailedPinAttempts() >= threshold.attempts
    }

    /**
     * Wipe all wallet data from secure and regular prefs.
     * Removes all mnemonics, keys, metadata, settings — everything.
     * Also deletes the Android Keystore MasterKey, the biometric unlock key,
     * and the underlying SharedPreferences files to eliminate forensic
     * artifacts of prior wallet use.
     *
     * Uses synchronous `commit = true` so the writes are flushed before the
     * caller terminates the process during auto-wipe.
     */
    fun wipeAllData() {
        securePrefs.edit(commit = true) { clear() }
        regularPrefs.edit(commit = true) { clear() }
        // Delete the encrypted prefs file from disk BEFORE deleting the MasterKey.
        // If only the key is deleted, the stale Tink keyset in the prefs file causes
        // KeyPermanentlyInvalidatedException on the next EncryptedSharedPreferences.create().
        try {
            context.deleteSharedPreferences(SECURE_PREFS_FILE)
        } catch (_: Exception) {
        }
        // Delete the plaintext prefs file as well. Leaving it on disk preserves
        // a structural fingerprint of prior wallet use even after clear().
        try {
            context.deleteSharedPreferences(REGULAR_PREFS_FILE)
        } catch (_: Exception) {
        }
        // Delete the MasterKey from Android Keystore to remove forensic evidence
        // that EncryptedSharedPreferences was ever used, plus the biometric
        // unlock key so the post-wipe Keystore matches a fresh install.
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry("_androidx_security_master_key_")
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    /**
     * Clear all duress data (PIN, wallet ID, real wallet ID)
     */
    fun clearDuressData() {
        securePrefs.edit {
            remove(KEY_DURESS_PIN_CODE)
            remove(KEY_DURESS_PIN_SALT)
            remove(KEY_DURESS_PIN_LENGTH)
            remove(KEY_DURESS_ENABLED)
            remove(KEY_DURESS_WALLET_ID)
            remove(KEY_REAL_WALLET_ID)
        }
    }

    companion object {
        @Volatile
        private var instance: SecureStorage? = null

        fun getInstance(context: Context): SecureStorage =
            instance ?: synchronized(this) {
                instance ?: SecureStorage(context.applicationContext).also { instance = it }
            }

        private const val SECURE_PREFS_FILE = "ibis_secure_prefs"
        private const val REGULAR_PREFS_FILE = "ibis_prefs"

        /**
         * AndroidKeyStore alias used by `MainActivity` to bind biometric unlock
         * to a cryptographic key (see [BiometricPrompt.CryptoObject]). Declared
         * here so a wipe can delete the alias even if the calling activity is
         * not currently in scope.
         */
        const val BIOMETRIC_KEY_ALIAS = "ibis_biometric_key"
        private const val KEY_SPEND_MASTER_PIN_WRAPPED = "spend_master_pin_wrapped"
        private const val KEY_SPEND_MASTER_PIN_SALT = "spend_master_pin_salt"
        private const val KEY_SPEND_MASTER_DURESS_WRAPPED = "spend_master_duress_wrapped"
        private const val KEY_SPEND_MASTER_DURESS_SALT = "spend_master_duress_salt"
        private const val KEY_SPEND_MASTER_BIOMETRIC_WRAPPED = "spend_master_biometric_wrapped"
        private const val ENCRYPTED_SPEND_SECRET_PREFIX = "enc:v1:"
        private const val SPEND_SECRET_KEY_BYTES = 32
        private const val SPEND_SECRET_IV_BYTES = 12
        private const val SPEND_SECRET_GCM_TAG_BITS = 128
        private const val SPEND_SECRET_CIPHER = "AES/GCM/NoPadding"

        // Multi-wallet keys
        private const val KEY_WALLET_IDS = "wallet_ids"
        private const val KEY_ACTIVE_WALLET_ID = "active_wallet_id"
        private const val KEY_WALLET_PREFIX = "wallet_meta_"

        // Secure keys (per wallet)
        private const val KEY_MNEMONIC_PREFIX = "wallet_mnemonic_"
        private const val KEY_EXTENDED_KEY_PREFIX = "wallet_extended_key_"
        private const val KEY_PASSPHRASE_PREFIX = "wallet_passphrase_"
        private const val KEY_PRIVATE_KEY_PREFIX = "wallet_private_key_"
        private const val KEY_WATCH_ADDRESS_PREFIX = "wallet_watch_address_"
        private const val KEY_MULTISIG_CONFIG_PREFIX = "wallet_multisig_config_"
        private const val KEY_MULTISIG_LOCAL_COSIGNER_PREFIX = "wallet_multisig_local_cosigner_"
        private const val KEY_PSBT_SIGNING_SESSION_PREFIX = "psbt_signing_session_"

        // Regular keys
        private const val KEY_NETWORK = "wallet_network"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_time_"
        private const val KEY_NEEDS_FULL_SYNC_PREFIX = "needs_full_sync_"
        private const val KEY_LIQUID_NEEDS_FULL_SYNC_PREFIX = "liquid_needs_full_sync_"
        private const val KEY_LAST_FULL_SYNC_PREFIX = "last_full_sync_time_"

        // Multi-server keys
        private const val KEY_SERVER_IDS = "server_ids"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
        private const val KEY_SERVER_PREFIX = "server_config_"
        private const val KEY_SERVER_CERT_PREFIX = "server_cert_"
        private const val KEY_DEFAULT_SERVERS_SEEDED = "default_servers_seeded"
        private const val KEY_AUTO_SWITCH_SERVER = "auto_switch_server"
        private const val KEY_USER_DISCONNECTED = "user_disconnected"
        private const val KEY_LIQUID_USER_DISCONNECTED = "liquid_user_disconnected"

        // Sync settings
        private const val KEY_SYNC_BATCH_SIZE = "sync_batch_size"

        // Tor settings
        private const val KEY_TOR_ENABLED = "tor_enabled"

        // Display settings
        private const val KEY_DENOMINATION = "denomination"
        private const val KEY_LAYER1_DENOMINATION = "layer1_denomination"
        private const val KEY_LAYER2_DENOMINATION = "layer2_denomination"
        private const val KEY_APP_LOCALE = "app_locale"
        const val DENOMINATION_BTC = "BTC"
        const val DENOMINATION_SATS = "SATS"

        // Swipe navigation
        private const val KEY_SWIPE_MODE = "swipe_mode"
        const val SWIPE_MODE_DISABLED = "DISABLED"
        const val SWIPE_MODE_SEND_RECEIVE = "SEND_RECEIVE"
        const val SWIPE_MODE_WALLETS = "WALLETS"
        const val SWIPE_MODE_LAYERS = "LAYERS"

        private const val KEY_PSBT_QR_DENSITY = "psbt_qr_density"
        private const val KEY_PSBT_QR_BRIGHTNESS = "psbt_qr_brightness"
        const val MIN_PSBT_QR_BRIGHTNESS = 0.2f
        const val DEFAULT_PSBT_QR_BRIGHTNESS = 1f

        // Mempool server settings
        private const val KEY_MEMPOOL_SERVER = "mempool_server"
        private const val KEY_MEMPOOL_CUSTOM_URL = "mempool_custom_url"
        const val MEMPOOL_DISABLED = "MEMPOOL_DISABLED"
        const val MEMPOOL_SPACE = "MEMPOOL_SPACE"
        const val MEMPOOL_ONION = "MEMPOOL_ONION"
        const val MEMPOOL_CUSTOM = "MEMPOOL_CUSTOM"

        // Layer 2 liquid explorer settings
        private const val KEY_LIQUID_EXPLORER = "liquid_explorer"
        private const val KEY_LIQUID_EXPLORER_CUSTOM_URL = "liquid_explorer_custom_url"
        const val LIQUID_EXPLORER_DISABLED = "LIQUID_EXPLORER_DISABLED"
        const val LIQUID_EXPLORER_LIQUID_NETWORK = "LIQUID_EXPLORER_LIQUID_NETWORK"
        const val LIQUID_EXPLORER_LIQUID_NETWORK_ONION = "LIQUID_EXPLORER_LIQUID_NETWORK_ONION"
        const val LIQUID_EXPLORER_BLOCKSTREAM = "LIQUID_EXPLORER_BLOCKSTREAM"
        const val LIQUID_EXPLORER_CUSTOM = "LIQUID_EXPLORER_CUSTOM"

        // Fee estimation settings
        private const val KEY_FEE_SOURCE = "fee_source"
        private const val KEY_FEE_SOURCE_CUSTOM_URL = "fee_source_custom_url"
        const val FEE_SOURCE_OFF = "OFF"
        const val FEE_SOURCE_MEMPOOL = "MEMPOOL_SPACE"
        const val FEE_SOURCE_MEMPOOL_ONION = "MEMPOOL_ONION"
        const val FEE_SOURCE_ELECTRUM = "ELECTRUM_SERVER"
        const val FEE_SOURCE_CUSTOM = "CUSTOM"

        // BTC/fiat price settings
        private const val KEY_PRICE_SOURCE = "price_source"
        private const val KEY_PRICE_CURRENCY = "price_currency"
        private const val KEY_HISTORICAL_TX_FIAT_ENABLED = "historical_tx_fiat_enabled"
        const val PRICE_SOURCE_OFF = "OFF"
        const val PRICE_SOURCE_MEMPOOL = "MEMPOOL_SPACE"
        const val PRICE_SOURCE_MEMPOOL_ONION = "MEMPOOL_ONION"
        const val PRICE_SOURCE_COINGECKO = "COINGECKO"
        const val DEFAULT_PRICE_CURRENCY = "USD"

        // Layer 2 external service settings
        private const val KEY_BOLTZ_API_SOURCE = "boltz_api_source"
        private const val KEY_SIDESWAP_API_SOURCE = "sideswap_api_source"
        private const val KEY_PREFERRED_SWAP_SERVICE = "preferred_swap_service"
        const val BOLTZ_API_DISABLED = "BOLTZ_DISABLED"
        const val BOLTZ_API_CLEARNET = "BOLTZ_CLEARNET"
        const val BOLTZ_API_TOR = "BOLTZ_TOR"
        const val SIDESWAP_API_CLEARNET = "SIDESWAP_CLEARNET"
        const val SIDESWAP_API_TOR = "SIDESWAP_TOR"
        const val SIDESWAP_API_DISABLED = "SIDESWAP_DISABLED"

        // Address labels
        private const val KEY_ADDRESS_LABEL_PREFIX = "address_label_"
        private const val KEY_LIQUID_ADDRESS_LABEL_PREFIX = "liquid_address_label_"
        private const val KEY_SPARK_ADDRESS_LABEL_PREFIX = "spark_address_label_"

        // Transaction labels
        private const val KEY_TX_LABEL_PREFIX = "tx_label_"
        private const val KEY_TX_SOURCE_PREFIX = "tx_source_"
        private const val KEY_TX_SWAP_DETAILS_PREFIX = "tx_swap_details_"
        private const val KEY_LIQUID_TX_LABEL_PREFIX = "liquid_tx_label_"
        private const val KEY_SPARK_TX_LABEL_PREFIX = "spark_tx_label_"
        private const val KEY_SPARK_TX_SOURCE_PREFIX = "spark_tx_source_"
        private const val KEY_SPARK_PAYMENT_RECIPIENT_PREFIX = "spark_payment_recipient_"
        private const val KEY_SPARK_DEPOSIT_ADDRESS_PREFIX = "spark_deposit_address_"
        private const val KEY_SPARK_PENDING_DEPOSIT_PREFIX = "spark_pending_deposit_"
        private const val KEY_SPARK_WALLET_STATE_CACHE_PREFIX = "spark_wallet_state_cache_"
        private const val KEY_LIQUID_TX_SOURCE_PREFIX = "liquid_tx_source_"
        private const val KEY_LIQUID_TX_SWAP_DETAILS_PREFIX = "liquid_tx_swap_details_"
        private const val KEY_LIQUID_TX_RECIPIENT_PREFIX = "liquid_tx_recipient_"
        private const val KEY_LIQUID_TX_FEE_DETAILS_PREFIX = "liquid_tx_fee_details_"
        private const val KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_ADDRESS_PREFIX = "pending_liquid_ln_receive_address_"
        private const val KEY_PENDING_LIQUID_LIGHTNING_RECEIVE_LABEL_PREFIX = "pending_liquid_ln_receive_label_"
        private const val KEY_PENDING_SWAP_PREFIX = "pending_swap_"
        private const val KEY_PENDING_SWAP_SNAPSHOT_PREFIX = "pending_swap_snapshot_"
        private const val KEY_BOLTZ_CHAIN_SWAP_DRAFT_PREFIX = "boltz_chain_swap_draft_"
        private const val KEY_BOLTZ_CHAIN_SWAP_SNAPSHOT_PREFIX = "boltz_chain_swap_snapshot_"
        private const val KEY_BOLTZ_SESSION_NEXT_INDEX_PREFIX = "boltz_session_next_index_"
        private const val KEY_PENDING_BOLTZ_LIGHTNING_INVOICE_PREFIX = "pending_boltz_ln_invoice_"
        private const val KEY_PENDING_BOLTZ_LIGHTNING_PAYMENT_PREFIX = "pending_boltz_ln_payment_"
        private const val KEY_LIQUID_SCRIPT_HASH_STATUS_INDEX_PREFIX = "liquid_script_hash_status_index_"
        private const val KEY_LIQUID_SCRIPT_HASH_STATUS_ENTRY_PREFIX = "liquid_script_hash_status_entry_"
        private const val LIQUID_SCRIPT_HASH_STATUS_NULL_SENTINEL = "__NULL__"

        // Pending PSBT labels (for watch-only wallet transactions awaiting signing)
        private const val KEY_PENDING_PSBT_LABEL_PREFIX = "pending_psbt_label_"

        // Transaction first-seen timestamps (for pending txs)
        private const val KEY_TX_FIRST_SEEN_PREFIX = "tx_first_seen_"
        private const val KEY_PENDING_REPLACEMENT_TX_PREFIX = "pending_replacement_tx_"

        // Spend only confirmed UTXOs
        private const val KEY_SPEND_UNCONFIRMED = "spend_unconfirmed"

        // NFC broadcasting
        private const val KEY_NFC_ENABLED = "nfc_enabled"
        private const val KEY_WALLET_NOTIFICATIONS_ENABLED = "wallet_notifications_enabled"
        private const val KEY_NOTIFIED_TXIDS_PREFIX = "notified_txids_"
        private const val KEY_NOTIFIED_TXIDS_BASELINE_PREFIX = "notified_txids_baseline_"
        private const val KEY_NOTIFIED_LIQUID_TXIDS_PREFIX = "notified_liquid_txids_"
        private const val KEY_NOTIFIED_LIQUID_TXIDS_BASELINE_PREFIX = "notified_liquid_txids_baseline_"

        // Frozen UTXOs
        private const val KEY_FROZEN_UTXOS_PREFIX = "frozen_utxos_"

        // Security settings
        private const val KEY_SECURITY_METHOD = "security_method"
        private const val KEY_PIN_CODE = "pin_code"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val PIN_PBKDF2_ITERATIONS = 150_000
        private const val PIN_HASH_LENGTH = 256
        /** Failed PIN tries (real + duress shared counter) before lockout; keep UI in sync via this constant. */
        const val MAX_PIN_ATTEMPTS = 5
        private const val KEY_LOCK_TIMING = "lock_timing"
        private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"
        private const val KEY_DISABLE_SCREENSHOTS = "disable_screenshots"
        private const val KEY_RANDOMIZE_PIN_PAD = "randomize_pin_pad"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        private const val KEY_HAS_SEEN_PRIVACY_MODE_HINT = "has_seen_privacy_mode_hint"
        private const val KEY_FOREGROUND_CONNECTIVITY_ENABLED = "foreground_connectivity_enabled"
        private const val KEY_APP_UPDATE_CHECK_ENABLED = "app_update_check_enabled"
        private const val KEY_HAS_SEEN_APP_UPDATE_OPT_IN_PROMPT = "has_seen_app_update_opt_in_prompt"
        private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"
        private const val KEY_HAS_SEEN_LIQUID_ENABLE_INFO = "has_seen_liquid_enable_info"
        private const val KEY_SEEN_APP_UPDATE_VERSION = "seen_app_update_version"

        // Auto-wipe
        private const val KEY_AUTO_WIPE_THRESHOLD = "auto_wipe_threshold"

        // Cloak mode
        private const val KEY_CLOAK_MODE_ENABLED = "cloak_mode_enabled"
        private const val KEY_CLOAK_CODE = "cloak_code"
        private const val KEY_CLOAK_PENDING_ALIAS = "cloak_pending_alias"
        private const val KEY_CLOAK_CURRENT_ALIAS = "cloak_current_alias"
        const val ALIAS_DEFAULT = ".LauncherDefault"
        const val ALIAS_CALCULATOR = ".LauncherCalculator"

        // Duress PIN / Decoy wallet
        private const val KEY_DURESS_PIN_CODE = "duress_pin_code"
        private const val KEY_DURESS_PIN_SALT = "duress_pin_salt"
        private const val KEY_DURESS_PIN_LENGTH = "duress_pin_length"
        private const val KEY_DURESS_ENABLED = "duress_enabled"
        private const val KEY_DURESS_WALLET_ID = "duress_wallet_id"
        private const val KEY_REAL_WALLET_ID = "real_wallet_id"

        // Layer 2 (Liquid)
        private const val KEY_LAYER2_ENABLED = "layer2_enabled"
        private const val KEY_SPARK_LAYER2_ENABLED = "spark_layer2_enabled"
        private const val KEY_LIQUID_ENABLED_PREFIX = "liquid_enabled_"
        private const val KEY_SPARK_ENABLED_PREFIX = "spark_enabled_"
        private const val KEY_SPARK_ONCHAIN_DEPOSIT_ADDRESS_PREFIX = "spark_onchain_deposit_address_"
        private const val KEY_LAYER2_PROVIDER_PREFIX = "layer2_provider_"
        private const val KEY_LIQUID_GAP_LIMIT_PREFIX = "liquid_gap_limit_"
        private const val KEY_ACTIVE_LAYER_PREFIX = "active_layer_"
        private const val KEY_LIQUID_SERVER_IDS = "liquid_server_ids"
        private const val KEY_ACTIVE_LIQUID_SERVER = "active_liquid_server_id"
        private const val KEY_LIQUID_SERVER_SELECTED_BY_USER = "liquid_server_selected_by_user"
        private const val KEY_LIQUID_SERVER_PREFIX = "liquid_server_"
        private const val KEY_LIQUID_DEFAULT_SERVERS_SEEDED = "liquid_default_servers_seeded"
        private const val KEY_LIQUID_DESCRIPTOR_PREFIX = "liquid_descriptor_"
        private const val KEY_LIQUID_WATCH_ONLY_PREFIX = "liquid_watch_only_"
        private const val KEY_LIQUID_SCRIPT_HASH_STATUS_PREFIX = "liquid_script_hash_status_"
    }

    // ════════════════════════════════════════════
    // Layer 2 (Liquid) persistence
    // ════════════════════════════════════════════

    /** Global Liquid toggle (legacy key name kept for backup compatibility) */
    fun isLayer2Enabled(): Boolean = regularPrefs.getBoolean(KEY_LAYER2_ENABLED, false)

    fun setLayer2Enabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_LAYER2_ENABLED, enabled) }
    }

    /** Global Spark toggle (Settings → Layer 2) */
    fun isSparkLayer2Enabled(): Boolean = regularPrefs.getBoolean(KEY_SPARK_LAYER2_ENABLED, false)

    fun setSparkLayer2Enabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean(KEY_SPARK_LAYER2_ENABLED, enabled) }
    }

    /** Per-wallet Liquid support toggle */
    fun isLiquidEnabledForWallet(walletId: String): Boolean =
        regularPrefs.getBoolean("${KEY_LIQUID_ENABLED_PREFIX}$walletId", false)

    fun setLiquidEnabledForWallet(walletId: String, enabled: Boolean) {
        regularPrefs.edit {
            putBoolean("${KEY_LIQUID_ENABLED_PREFIX}$walletId", enabled)
            if (enabled) {
                putBoolean("${KEY_SPARK_ENABLED_PREFIX}$walletId", false)
                putString("${KEY_LAYER2_PROVIDER_PREFIX}$walletId", Layer2Provider.LIQUID.name)
            } else if (getLayer2ProviderForWallet(walletId) == Layer2Provider.LIQUID) {
                putString("${KEY_LAYER2_PROVIDER_PREFIX}$walletId", Layer2Provider.NONE.name)
            }
        }
    }

    /** Per-wallet Spark support toggle */
    fun isSparkEnabledForWallet(walletId: String): Boolean =
        regularPrefs.getBoolean("${KEY_SPARK_ENABLED_PREFIX}$walletId", false)

    fun setSparkEnabledForWallet(walletId: String, enabled: Boolean) {
        regularPrefs.edit {
            putBoolean("${KEY_SPARK_ENABLED_PREFIX}$walletId", enabled)
            if (enabled) {
                putBoolean(KEY_SPARK_LAYER2_ENABLED, true)
                putBoolean("${KEY_LIQUID_ENABLED_PREFIX}$walletId", false)
                putString("${KEY_LAYER2_PROVIDER_PREFIX}$walletId", Layer2Provider.SPARK.name)
            } else if (getLayer2ProviderForWallet(walletId) == Layer2Provider.SPARK) {
                putString("${KEY_LAYER2_PROVIDER_PREFIX}$walletId", Layer2Provider.NONE.name)
            }
        }
    }

    fun getSparkOnchainDepositAddress(walletId: String): String? =
        regularPrefs.getString("${KEY_SPARK_ONCHAIN_DEPOSIT_ADDRESS_PREFIX}$walletId", null)

    fun setSparkOnchainDepositAddress(walletId: String, address: String) {
        regularPrefs.edit { putString("${KEY_SPARK_ONCHAIN_DEPOSIT_ADDRESS_PREFIX}$walletId", address) }
    }

    fun clearSparkOnchainDepositAddress(walletId: String) {
        regularPrefs.edit { remove("${KEY_SPARK_ONCHAIN_DEPOSIT_ADDRESS_PREFIX}$walletId") }
    }

    fun getLayer2ProviderForWallet(walletId: String): Layer2Provider {
        val stored = regularPrefs.getString("${KEY_LAYER2_PROVIDER_PREFIX}$walletId", null)
        val parsed = stored?.let { runCatching { Layer2Provider.valueOf(it) }.getOrNull() }
        return when {
            parsed != null && parsed != Layer2Provider.NONE -> parsed
            isSparkEnabledForWallet(walletId) -> Layer2Provider.SPARK
            isLiquidEnabledForWallet(walletId) || isLiquidWatchOnly(walletId) -> Layer2Provider.LIQUID
            else -> Layer2Provider.NONE
        }
    }

    fun setLayer2ProviderForWallet(walletId: String, provider: Layer2Provider) {
        regularPrefs.edit {
            putString("${KEY_LAYER2_PROVIDER_PREFIX}$walletId", provider.name)
            putBoolean("${KEY_LIQUID_ENABLED_PREFIX}$walletId", provider == Layer2Provider.LIQUID)
            putBoolean("${KEY_SPARK_ENABLED_PREFIX}$walletId", provider == Layer2Provider.SPARK)
            if (provider == Layer2Provider.SPARK) {
                putBoolean(KEY_SPARK_LAYER2_ENABLED, true)
            }
        }
    }

    /** Per-wallet Liquid gap limit (derivation index for fullScanToIndex) */
    fun getLiquidGapLimit(walletId: String): Int =
        regularPrefs.getInt("${KEY_LIQUID_GAP_LIMIT_PREFIX}$walletId", StoredWallet.DEFAULT_GAP_LIMIT)

    fun setLiquidGapLimit(walletId: String, gapLimit: Int) {
        regularPrefs.edit { putInt("${KEY_LIQUID_GAP_LIMIT_PREFIX}$walletId", gapLimit) }
    }

    /** Active layer per wallet (persisted across restarts) */
    fun getActiveLayer(walletId: String): String =
        regularPrefs.getString("${KEY_ACTIVE_LAYER_PREFIX}$walletId", "LAYER1") ?: "LAYER1"

    fun setActiveLayer(walletId: String, layer: String) {
        regularPrefs.edit { putString("${KEY_ACTIVE_LAYER_PREFIX}$walletId", layer) }
    }

    /** Liquid CT descriptor (stored in encrypted prefs) */
    fun getLiquidDescriptor(walletId: String): String? =
        getSpendSecret("${KEY_LIQUID_DESCRIPTOR_PREFIX}$walletId")

    fun setLiquidDescriptor(walletId: String, descriptor: String) {
        putSpendSecret("${KEY_LIQUID_DESCRIPTOR_PREFIX}$walletId", descriptor)
    }

    fun removeLiquidDescriptor(walletId: String) {
        removeSpendSecret("${KEY_LIQUID_DESCRIPTOR_PREFIX}$walletId")
    }

    /** Per-wallet Liquid watch-only flag (descriptor-only, no signer available) */
    fun isLiquidWatchOnly(walletId: String): Boolean =
        regularPrefs.getBoolean("${KEY_LIQUID_WATCH_ONLY_PREFIX}$walletId", false)

    fun setLiquidWatchOnly(walletId: String, watchOnly: Boolean) {
        regularPrefs.edit { putBoolean("${KEY_LIQUID_WATCH_ONLY_PREFIX}$walletId", watchOnly) }
    }

    /**
     * Persist Liquid Electrum script-hash statuses for smart sync.
     */
    fun saveLiquidScriptHashStatuses(
        walletId: String,
        statuses: Map<String, String?>,
    ) {
        if (statuses.isEmpty()) {
            clearLiquidScriptHashStatuses(walletId)
            return
        }

        val existing = getLiquidScriptHashStatuses(walletId)
        if (existing == statuses) return

        val removed = existing.keys - statuses.keys
        val indexJson = JSONArray(statuses.keys.toList()).toString()
        securePrefs.edit {
            removed.forEach { scriptHash ->
                remove(liquidScriptHashStatusEntryKey(walletId, scriptHash))
            }
            statuses.forEach { (scriptHash, status) ->
                if (existing[scriptHash] != status) {
                    putString(liquidScriptHashStatusEntryKey(walletId, scriptHash), encodeLiquidScriptHashStatus(status))
                }
            }
            putString(liquidScriptHashStatusIndexKey(walletId), indexJson)
            remove(liquidScriptHashStatusBlobKey(walletId))
        }
        regularPrefs.edit {
            regularPrefs.all.keys
                .filter { it.startsWith(KEY_LIQUID_SCRIPT_HASH_STATUS_ENTRY_PREFIX + walletId + "_") }
                .forEach(::remove)
            statuses.keys.forEach { scriptHash ->
                remove(liquidScriptHashStatusEntryKey(walletId, scriptHash))
            }
            remove(liquidScriptHashStatusIndexKey(walletId))
            remove(liquidScriptHashStatusBlobKey(walletId))
        }
    }

    /**
     * Load the last fully processed Liquid Electrum script-hash snapshot.
     */
    fun getLiquidScriptHashStatuses(walletId: String): Map<String, String?> {
        val indexRaw = getPrivateString(liquidScriptHashStatusIndexKey(walletId), null)
        if (indexRaw != null) {
            return try {
                val index = JSONArray(indexRaw)
                buildMap {
                    for (i in 0 until index.length()) {
                        val scriptHash = index.getString(i)
                        val encoded = getPrivateString(liquidScriptHashStatusEntryKey(walletId, scriptHash), null) ?: continue
                        put(scriptHash, decodeLiquidScriptHashStatus(encoded))
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }

        val raw = getPrivateString(liquidScriptHashStatusBlobKey(walletId), null) ?: return emptyMap()
        return parseLegacyLiquidScriptHashStatuses(raw)
    }

    fun clearLiquidScriptHashStatuses(walletId: String) {
        val indexRaw = getPrivateString(liquidScriptHashStatusIndexKey(walletId), null)
        securePrefs.edit {
            if (indexRaw != null) {
                runCatching { JSONArray(indexRaw) }
                    .getOrNull()
                    ?.let { index ->
                        for (i in 0 until index.length()) {
                            remove(liquidScriptHashStatusEntryKey(walletId, index.getString(i)))
                        }
                    }
            }
            remove(liquidScriptHashStatusIndexKey(walletId))
            remove(liquidScriptHashStatusBlobKey(walletId))
        }
        regularPrefs.edit {
            if (indexRaw != null) {
                runCatching { JSONArray(indexRaw) }
                    .getOrNull()
                    ?.let { index ->
                        for (i in 0 until index.length()) {
                            remove(liquidScriptHashStatusEntryKey(walletId, index.getString(i)))
                        }
                    }
            }
            remove(liquidScriptHashStatusIndexKey(walletId))
            remove(liquidScriptHashStatusBlobKey(walletId))
        }
    }

    private fun liquidScriptHashStatusBlobKey(walletId: String): String =
        "${KEY_LIQUID_SCRIPT_HASH_STATUS_PREFIX}$walletId"

    private fun liquidScriptHashStatusIndexKey(walletId: String): String =
        "${KEY_LIQUID_SCRIPT_HASH_STATUS_INDEX_PREFIX}$walletId"

    private fun liquidScriptHashStatusEntryKey(walletId: String, scriptHash: String): String =
        "${KEY_LIQUID_SCRIPT_HASH_STATUS_ENTRY_PREFIX}${walletId}_$scriptHash"

    private fun encodeLiquidScriptHashStatus(status: String?): String =
        status ?: LIQUID_SCRIPT_HASH_STATUS_NULL_SENTINEL

    private fun decodeLiquidScriptHashStatus(encoded: String): String? =
        if (encoded == LIQUID_SCRIPT_HASH_STATUS_NULL_SENTINEL) null else encoded

    private fun parseLegacyLiquidScriptHashStatuses(raw: String): Map<String, String?> {
        return try {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val scriptHash = keys.next()
                    put(
                        scriptHash,
                        if (json.isNull(scriptHash)) null else json.getString(scriptHash),
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Liquid server management ──

    fun getLiquidServerIds(): List<String> {
        val raw = getPrivateString(KEY_LIQUID_SERVER_IDS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    private fun saveLiquidServerIds(ids: List<String>) {
        putPrivateString(KEY_LIQUID_SERVER_IDS, ids.joinToString(","))
    }

    fun getActiveLiquidServerId(): String? =
        getPrivateString(KEY_ACTIVE_LIQUID_SERVER, null)

    fun setActiveLiquidServerId(id: String?) {
        if (id == null) {
            removePrivateValue(KEY_ACTIVE_LIQUID_SERVER)
        } else {
            putPrivateString(KEY_ACTIVE_LIQUID_SERVER, id)
        }
    }

    fun hasUserSelectedLiquidServer(): Boolean =
        regularPrefs.getBoolean(KEY_LIQUID_SERVER_SELECTED_BY_USER, false)

    fun setUserSelectedLiquidServer(selected: Boolean) {
        regularPrefs.edit { putBoolean(KEY_LIQUID_SERVER_SELECTED_BY_USER, selected) }
    }

    fun saveLiquidServer(config: github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig) {
        val json = JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("url", config.url)
            put("port", config.port)
            put("useSsl", config.useSsl)
            put("useTor", config.useTor)
        }
        putPrivateString("${KEY_LIQUID_SERVER_PREFIX}${config.id}", json.toString())
        val ids = getLiquidServerIds().toMutableList()
        if (config.id !in ids) {
            ids.add(config.id)
            saveLiquidServerIds(ids)
        }
    }

    fun getLiquidServer(id: String): github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig? {
        val raw = getPrivateString("${KEY_LIQUID_SERVER_PREFIX}$id", null) ?: return null
        return try {
            val j = JSONObject(raw)
            github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig(
                id = j.getString("id"),
                name = j.optString("name", ""),
                url = j.getString("url"),
                port = j.optInt("port", 995),
                useSsl = j.optBoolean("useSsl", true),
                useTor = j.optBoolean("useTor", false),
            )
        } catch (_: Exception) { null }
    }

    fun getAllLiquidServers(): List<github.aeonbtc.ibiswallet.data.model.LiquidElectrumConfig> =
        getLiquidServerIds().mapNotNull { getLiquidServer(it) }

    fun removeLiquidServer(id: String) {
        removePrivateValue("${KEY_LIQUID_SERVER_PREFIX}$id")
        saveLiquidServerIds(getLiquidServerIds().filter { it != id })
        if (getActiveLiquidServerId() == id) {
            setActiveLiquidServerId(null)
            setUserSelectedLiquidServer(false)
        }
    }

    fun reorderLiquidServerIds(orderedIds: List<String>) {
        saveLiquidServerIds(orderedIds)
    }

    fun isLiquidDefaultServersSeeded(): Boolean =
        regularPrefs.getBoolean(KEY_LIQUID_DEFAULT_SERVERS_SEEDED, false)

    fun setLiquidDefaultServersSeeded() {
        regularPrefs.edit { putBoolean(KEY_LIQUID_DEFAULT_SERVERS_SEEDED, true) }
    }

    /** Liquid Tor toggle (force Tor for clearnet Liquid servers) */
    fun isLiquidTorEnabled(): Boolean = regularPrefs.getBoolean("liquid_tor_enabled", false)

    fun setLiquidTorEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean("liquid_tor_enabled", enabled) }
    }

    /** Auto-switch to next Liquid server on disconnect */
    fun isLiquidAutoSwitchEnabled(): Boolean = regularPrefs.getBoolean("liquid_auto_switch", false)

    fun setLiquidAutoSwitchEnabled(enabled: Boolean) {
        regularPrefs.edit { putBoolean("liquid_auto_switch", enabled) }
    }

}
