package github.aeonbtc.ibiswallet.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure storage for sensitive wallet data using Android's EncryptedSharedPreferences
 * Supports multiple wallets with unique IDs
 */
class SecureStorage(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val securePrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
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
        } catch (_: Exception) { }
        val freshKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            freshKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private val regularPrefs: SharedPreferences = context.getSharedPreferences(
        REGULAR_PREFS_FILE,
        Context.MODE_PRIVATE
    )
    
    // ==================== Multi-Wallet Management ====================
    
    /**
     * Get list of all wallet IDs
     */
    fun getWalletIds(): List<String> {
        val json = regularPrefs.getString(KEY_WALLET_IDS, "[]") ?: "[]"
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
            regularPrefs.edit { putString(KEY_WALLET_IDS, jsonArray.toString()) }
        }
    }
    
    /**
     * Remove a wallet ID from the list
     */
    private fun removeWalletId(walletId: String) {
        val ids = getWalletIds().toMutableList()
        ids.remove(walletId)
        val jsonArray = JSONArray(ids)
        regularPrefs.edit { putString(KEY_WALLET_IDS, jsonArray.toString()) }
    }
    
    /**
     * Get the active wallet ID
     */
    fun getActiveWalletId(): String? {
        return regularPrefs.getString(KEY_ACTIVE_WALLET_ID, null)
    }
    
    /**
     * Set the active wallet ID
     */
    fun setActiveWalletId(walletId: String?) {
        if (walletId != null) {
            regularPrefs.edit { putString(KEY_ACTIVE_WALLET_ID, walletId) }
        } else {
            regularPrefs.edit { remove(KEY_ACTIVE_WALLET_ID) }
        }
    }
    
    // ==================== Wallet Metadata ====================
    
    /**
     * Save wallet metadata for a specific wallet
     */
    fun saveWalletMetadata(wallet: StoredWallet) {
        val walletJson = JSONObject().apply {
            put("id", wallet.id)
            put("name", wallet.name)
            put("addressType", wallet.addressType.name)
            put("derivationPath", wallet.derivationPath)
            put("isWatchOnly", wallet.isWatchOnly)
            put("network", wallet.network.name)
            put("createdAt", wallet.createdAt)
            wallet.masterFingerprint?.let { put("masterFingerprint", it) }
        }
        
        regularPrefs.edit { putString("${KEY_WALLET_PREFIX}${wallet.id}", walletJson.toString()) }
        
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
        val json = regularPrefs.getString("${KEY_WALLET_PREFIX}${walletId}", null) ?: return null
        
        return try {
            val jsonObject = JSONObject(json)
            val addressType = try {
                AddressType.valueOf(jsonObject.optString("addressType", AddressType.SEGWIT.name))
            } catch (_: IllegalArgumentException) {
                AddressType.SEGWIT
            }
            val network = try {
                WalletNetwork.valueOf(jsonObject.optString("network", WalletNetwork.BITCOIN.name))
            } catch (_: IllegalArgumentException) {
                WalletNetwork.BITCOIN
            }
            
            StoredWallet(
                id = jsonObject.getString("id"),
                name = jsonObject.optString("name", "Wallet"),
                addressType = addressType,
                derivationPath = jsonObject.optString("derivationPath", addressType.defaultPath),
                isWatchOnly = jsonObject.optBoolean("isWatchOnly", false),
                network = network,
                createdAt = jsonObject.optLong("createdAt", System.currentTimeMillis()),
                masterFingerprint = jsonObject.optString("masterFingerprint", null.toString()).let {
                    if (it == "null" || it.isBlank()) null else it
                }
            )
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Get all stored wallets
     */
    fun getAllWallets(): List<StoredWallet> {
        return getWalletIds().mapNotNull { getWalletMetadata(it) }
    }
    
    /**
     * Edit wallet metadata (name and optionally master fingerprint for watch-only wallets)
     */
    fun editWallet(walletId: String, newName: String, newFingerprint: String? = null): Boolean {
        val wallet = getWalletMetadata(walletId) ?: return false
        val updated = if (wallet.isWatchOnly && newFingerprint != null) {
            wallet.copy(name = newName, masterFingerprint = newFingerprint.ifBlank { null })
        } else {
            wallet.copy(name = newName)
        }
        saveWalletMetadata(updated)
        return true
    }
    
    /**
     * Delete wallet metadata for a specific wallet
     */
    fun deleteWalletMetadata(walletId: String) {
        regularPrefs.edit { remove("${KEY_WALLET_PREFIX}${walletId}") }
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
    fun saveMnemonic(walletId: String, mnemonic: String) {
        securePrefs.edit { putString("${KEY_MNEMONIC_PREFIX}${walletId}", mnemonic) }
    }
    
    /**
     * Get mnemonic for a specific wallet
     */
    fun getMnemonic(walletId: String): String? {
        return securePrefs.getString("${KEY_MNEMONIC_PREFIX}${walletId}", null)
    }
    
    /**
     * Delete mnemonic for a specific wallet
     */
    fun deleteMnemonic(walletId: String) {
        securePrefs.edit { remove("${KEY_MNEMONIC_PREFIX}${walletId}") }
    }
    
    // ==================== Extended Public Key (per wallet) ====================
    
    /**
     * Save extended key for a specific wallet
     */
    fun saveExtendedKey(walletId: String, key: String) {
        securePrefs.edit { putString("${KEY_EXTENDED_KEY_PREFIX}${walletId}", key) }
    }
    
    /**
     * Get extended key for a specific wallet
     */
    fun getExtendedKey(walletId: String): String? {
        return securePrefs.getString("${KEY_EXTENDED_KEY_PREFIX}${walletId}", null)
    }
    
    /**
     * Check if wallet has an extended key
     */
    fun hasExtendedKey(walletId: String): Boolean {
        return securePrefs.contains("${KEY_EXTENDED_KEY_PREFIX}${walletId}")
    }
    
    /**
     * Delete extended key for a specific wallet
     */
    fun deleteExtendedKey(walletId: String) {
        securePrefs.edit { remove("${KEY_EXTENDED_KEY_PREFIX}${walletId}") }
    }
    
    // ==================== Private Key Storage (per wallet - WIF format) ====================
    
    fun savePrivateKey(walletId: String, wif: String) {
        securePrefs.edit { putString("${KEY_PRIVATE_KEY_PREFIX}${walletId}", wif) }
    }
    
    fun getPrivateKey(walletId: String): String? {
        return securePrefs.getString("${KEY_PRIVATE_KEY_PREFIX}${walletId}", null)
    }
    
    fun hasPrivateKey(walletId: String): Boolean {
        return securePrefs.contains("${KEY_PRIVATE_KEY_PREFIX}${walletId}")
    }
    
    fun deletePrivateKey(walletId: String) {
        securePrefs.edit { remove("${KEY_PRIVATE_KEY_PREFIX}${walletId}") }
    }
    
    // ==================== Watch Address Storage (per wallet - single address) ====================
    
    fun saveWatchAddress(walletId: String, address: String) {
        securePrefs.edit { putString("${KEY_WATCH_ADDRESS_PREFIX}${walletId}", address) }
    }
    
    fun getWatchAddress(walletId: String): String? {
        return securePrefs.getString("${KEY_WATCH_ADDRESS_PREFIX}${walletId}", null)
    }
    
    fun hasWatchAddress(walletId: String): Boolean {
        return securePrefs.contains("${KEY_WATCH_ADDRESS_PREFIX}${walletId}")
    }
    
    fun deleteWatchAddress(walletId: String) {
        securePrefs.edit { remove("${KEY_WATCH_ADDRESS_PREFIX}${walletId}") }
    }
    
    // ==================== Passphrase Storage (per wallet) ====================
    
    /**
     * Save passphrase for a specific wallet
     */
    fun savePassphrase(walletId: String, passphrase: String) {
        securePrefs.edit { putString("${KEY_PASSPHRASE_PREFIX}${walletId}", passphrase) }
    }
    
    /**
     * Get passphrase for a specific wallet
     */
    fun getPassphrase(walletId: String): String? {
        return securePrefs.getString("${KEY_PASSPHRASE_PREFIX}${walletId}", null)
    }
    
    /**
     * Delete passphrase for a specific wallet
     */
    fun deletePassphrase(walletId: String) {
        securePrefs.edit { remove("${KEY_PASSPHRASE_PREFIX}${walletId}") }
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
        val json = regularPrefs.getString(KEY_SERVER_IDS, "[]") ?: "[]"
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
            regularPrefs.edit { putString(KEY_SERVER_IDS, jsonArray.toString()) }
        }
    }
    
    /**
     * Remove a server ID from the list
     */
    private fun removeServerId(serverId: String) {
        val ids = getServerIds().toMutableList()
        ids.remove(serverId)
        val jsonArray = JSONArray(ids)
        regularPrefs.edit { putString(KEY_SERVER_IDS, jsonArray.toString()) }
    }
    
    /**
     * Get the active server ID
     */
    fun getActiveServerId(): String? {
        return regularPrefs.getString(KEY_ACTIVE_SERVER_ID, null)
    }
    
    /**
     * Set the active server ID
     */
    fun setActiveServerId(serverId: String?) {
        if (serverId != null) {
            regularPrefs.edit { putString(KEY_ACTIVE_SERVER_ID, serverId) }
        } else {
            regularPrefs.edit { remove(KEY_ACTIVE_SERVER_ID) }
        }
    }
    
    /**
     * Save an Electrum server config
     * Returns the config with a guaranteed ID (generates one if null)
     */
    fun saveElectrumServer(config: ElectrumConfig): ElectrumConfig {
        val serverId = config.id ?: java.util.UUID.randomUUID().toString()
        val savedConfig = config.copy(id = serverId)
        
        val serverJson = JSONObject().apply {
            put("id", serverId)
            put("name", savedConfig.name ?: "")
            put("url", savedConfig.url)
            put("port", savedConfig.port)
            put("useSsl", savedConfig.useSsl)
            put("useTor", savedConfig.useTor)
        }
        
        regularPrefs.edit { putString("${KEY_SERVER_PREFIX}${serverId}", serverJson.toString()) }
        
        // Add to server list
        addServerId(serverId)
        
        return savedConfig
    }
    
    /**
     * Get an Electrum server config by ID
     */
    fun getElectrumServer(serverId: String): ElectrumConfig? {
        val json = regularPrefs.getString("${KEY_SERVER_PREFIX}${serverId}", null) ?: return null
        
        return try {
            val jsonObject = JSONObject(json)
            ElectrumConfig(
                id = jsonObject.getString("id"),
                name = jsonObject.optString("name", ""),
                url = jsonObject.getString("url"),
                port = jsonObject.optInt("port", 50001),
                useSsl = jsonObject.optBoolean("useSsl", false),
                useTor = jsonObject.optBoolean("useTor", false)
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

        regularPrefs.edit { remove("${KEY_SERVER_PREFIX}${serverId}") }
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
    fun saveServerCertFingerprint(host: String, port: Int, fingerprint: String) {
        val key = "${KEY_SERVER_CERT_PREFIX}${host}:${port}"
        securePrefs.edit { putString(key, fingerprint) }
    }

    /**
     * Get the stored certificate fingerprint for a server.
     * Returns null if this is the first connection (no TOFU record).
     */
    fun getServerCertFingerprint(host: String, port: Int): String? {
        val key = "${KEY_SERVER_CERT_PREFIX}${host}:${port}"
        return securePrefs.getString(key, null)
    }

    /**
     * Delete the stored certificate fingerprint for a server.
     */
    fun deleteServerCertFingerprint(host: String, port: Int) {
        val key = "${KEY_SERVER_CERT_PREFIX}${host}:${port}"
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

    // ==================== Last Sync Time (per wallet) ====================
    
    fun saveLastSyncTime(walletId: String, timestamp: Long) {
        regularPrefs.edit { putLong("${KEY_LAST_SYNC_PREFIX}${walletId}", timestamp) }
    }
    
    fun getLastSyncTime(walletId: String): Long? {
        val key = "${KEY_LAST_SYNC_PREFIX}${walletId}"
        return if (regularPrefs.contains(key)) {
            regularPrefs.getLong(key, 0)
        } else null
    }
    
    // ==================== Full Sync Tracking ====================
    
    /**
     * Set whether a wallet needs a full sync (address discovery scan)
     * Should be true for newly imported wallets, false after successful full sync
     */
    fun setNeedsFullSync(walletId: String, needs: Boolean) {
        regularPrefs.edit { putBoolean("${KEY_NEEDS_FULL_SYNC_PREFIX}${walletId}", needs) }
    }
    
    /**
     * Check if a wallet needs a full sync
     * Returns true by default for new wallets that haven't been synced yet
     */
    fun needsFullSync(walletId: String): Boolean {
        return regularPrefs.getBoolean("${KEY_NEEDS_FULL_SYNC_PREFIX}${walletId}", true)
    }
    
    /**
     * Save the timestamp of the last full sync for a wallet
     */
    fun saveLastFullSyncTime(walletId: String, timestamp: Long) {
        regularPrefs.edit { putLong("${KEY_LAST_FULL_SYNC_PREFIX}${walletId}", timestamp) }
    }
    
    /**
     * Get the timestamp of the last full sync for a wallet
     * Returns null if never fully synced
     */
    fun getLastFullSyncTime(walletId: String): Long? {
        val key = "${KEY_LAST_FULL_SYNC_PREFIX}${walletId}"
        return if (regularPrefs.contains(key)) {
            regularPrefs.getLong(key, 0)
        } else null
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
        
        // Delete metadata
        deleteWalletMetadata(walletId)
        
        // Delete sync time and full sync flags
        regularPrefs.edit { 
            remove("${KEY_LAST_SYNC_PREFIX}${walletId}")
            remove("${KEY_NEEDS_FULL_SYNC_PREFIX}${walletId}")
            remove("${KEY_LAST_FULL_SYNC_PREFIX}${walletId}")
        }
    }
    
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
        } else null
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
     * Get the preferred denomination (BTC or SATS)
     * Default is BTC
     */
    fun getDenomination(): String {
        return regularPrefs.getString(KEY_DENOMINATION, DENOMINATION_BTC) ?: DENOMINATION_BTC
    }
    
    /**
     * Set the preferred denomination
     */
    fun setDenomination(denomination: String) {
        regularPrefs.edit { putString(KEY_DENOMINATION, denomination) }
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
            MEMPOOL_ONION -> "http://mempoolhqx4isw62xs7abwphsq7ldayuidyx2v7oahrz2vh6jjg6m6qd.onion"
            MEMPOOL_CUSTOM -> getCustomMempoolUrl() ?: "https://mempool.space"
            else -> ""
        }
    }
    
    /**
     * Get custom mempool URL (placeholder for future custom server implementation)
     */
    fun getCustomMempoolUrl(): String? {
        return regularPrefs.getString(KEY_MEMPOOL_CUSTOM_URL, null)
    }
    
    /**
     * Set custom mempool URL (for future custom server implementation)
     */
    fun setCustomMempoolUrl(url: String) {
        requireHttpsForClearnet(url)
        regularPrefs.edit { putString(KEY_MEMPOOL_CUSTOM_URL, url) }
    }
    
    // ==================== Fee Estimation Settings ====================
    
    /**
     * Get the selected fee estimation source
     * Default is OFF (manual entry only)
     */
    fun getFeeSource(): String {
        val source = regularPrefs.getString(KEY_FEE_SOURCE, FEE_SOURCE_OFF) ?: FEE_SOURCE_OFF
        // Migrate old onion setting to OFF (custom is now supported)
        return if (source == "MEMPOOL_ONION") {
            FEE_SOURCE_OFF
        } else {
            source
        }
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
            FEE_SOURCE_ELECTRUM -> null // Electrum uses the connected client, not a URL
            FEE_SOURCE_CUSTOM -> getCustomFeeSourceUrl()?.ifBlank { null }
            else -> null
        }
    }
    
    /**
     * Whether fee estimation should use the connected Electrum server
     */
    fun isElectrumFeeSource(): Boolean {
        return getFeeSource() == FEE_SOURCE_ELECTRUM
    }
    
    /**
     * Get the custom fee source server URL (mempool.space-compatible instance)
     */
    fun getCustomFeeSourceUrl(): String? {
        return regularPrefs.getString(KEY_FEE_SOURCE_CUSTOM_URL, null)
    }
    
    /**
     * Set the custom fee source server URL
     */
    fun setCustomFeeSourceUrl(url: String) {
        requireHttpsForClearnet(url)
        regularPrefs.edit { putString(KEY_FEE_SOURCE_CUSTOM_URL, url) }
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
    
    // ==================== BTC/USD Price Source ====================
    
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
    
    // ==================== Address Labels ====================
    
    /**
     * Save a label for an address
     */
    fun saveAddressLabel(walletId: String, address: String, label: String) {
        val key = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        regularPrefs.edit { putString(key, label) }
    }
    
    /**
     * Get the label for an address
     */
    fun getAddressLabel(walletId: String, address: String): String? {
        val key = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        return regularPrefs.getString(key, null)
    }
    
    /**
     * Get all address labels for a wallet
     */
    fun getAllAddressLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_"
        val labels = mutableMapOf<String, String>()
        
        regularPrefs.all.forEach { (key, value) ->
            if (key.startsWith(prefix) && value is String) {
                val address = key.removePrefix(prefix)
                labels[address] = value
            }
        }
        
        return labels
    }
    
    /**
     * Delete a label for an address
     */
    fun deleteAddressLabel(walletId: String, address: String) {
        val key = "${KEY_ADDRESS_LABEL_PREFIX}${walletId}_$address"
        regularPrefs.edit { remove(key) }
    }
    
    // ==================== Transaction Labels ====================
    
    /**
     * Save a label for a transaction
     */
    fun saveTransactionLabel(walletId: String, txid: String, label: String) {
        val key = "${KEY_TX_LABEL_PREFIX}${walletId}_$txid"
        regularPrefs.edit { putString(key, label) }
    }
    
    /**
     * Get the label for a transaction
     */
    fun getTransactionLabel(walletId: String, txid: String): String? {
        val key = "${KEY_TX_LABEL_PREFIX}${walletId}_$txid"
        return regularPrefs.getString(key, null)
    }
    
    /**
     * Save a pending label for a PSBT (to be applied when broadcast)
     */
    fun savePendingPsbtLabel(walletId: String, psbtKey: String, label: String) {
        val key = "${KEY_PENDING_PSBT_LABEL_PREFIX}${walletId}_$psbtKey"
        regularPrefs.edit { putString(key, label) }
    }
    
    /**
     * Get all transaction labels for a wallet
     */
    fun getAllTransactionLabels(walletId: String): Map<String, String> {
        val prefix = "${KEY_TX_LABEL_PREFIX}${walletId}_"
        val labels = mutableMapOf<String, String>()
        
        regularPrefs.all.forEach { (key, value) ->
            if (key.startsWith(prefix) && value is String) {
                val txid = key.removePrefix(prefix)
                labels[txid] = value
            }
        }
        
        return labels
    }
    
    // ==================== Transaction First-Seen Timestamps ====================
    
    /**
     * Get the first-seen timestamp for a pending transaction.
     * Returns null if not stored yet.
     */
    fun getTxFirstSeen(walletId: String, txid: String): Long? {
        val key = "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_$txid"
        val value = regularPrefs.getLong(key, -1L)
        return if (value == -1L) null else value
    }
    
    /**
     * Store the first-seen timestamp for a pending transaction.
     * Only writes if no value is stored yet (preserves the original first-seen time).
     */
    fun setTxFirstSeenIfAbsent(walletId: String, txid: String, timestamp: Long) {
        val key = "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_$txid"
        if (regularPrefs.getLong(key, -1L) == -1L) {
            regularPrefs.edit { putLong(key, timestamp) }
        }
    }
    
    /**
     * Remove first-seen timestamp for a transaction (e.g. when confirmed or dropped).
     */
    fun removeTxFirstSeen(walletId: String, txid: String) {
        val key = "${KEY_TX_FIRST_SEEN_PREFIX}${walletId}_$txid"
        regularPrefs.edit { remove(key) }
    }
    
    // ==================== Frozen UTXOs ====================
    
    /**
     * Freeze a UTXO (prevent it from being spent)
     */
    fun freezeUtxo(walletId: String, outpoint: String) {
        val frozen = getFrozenUtxos(walletId).toMutableSet()
        frozen.add(outpoint)
        saveFrozenUtxos(walletId, frozen)
    }
    
    /**
     * Unfreeze a UTXO
     */
    fun unfreezeUtxo(walletId: String, outpoint: String) {
        val frozen = getFrozenUtxos(walletId).toMutableSet()
        frozen.remove(outpoint)
        saveFrozenUtxos(walletId, frozen)
    }
    
    /**
     * Get all frozen UTXOs for a wallet
     */
    fun getFrozenUtxos(walletId: String): Set<String> {
        val key = "${KEY_FROZEN_UTXOS_PREFIX}${walletId}"
        val json = regularPrefs.getString(key, "[]") ?: "[]"
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
    private fun saveFrozenUtxos(walletId: String, outpoints: Set<String>) {
        val key = "${KEY_FROZEN_UTXOS_PREFIX}${walletId}"
        val jsonArray = JSONArray(outpoints.toList())
        regularPrefs.edit { putString(key, jsonArray.toString()) }
    }
    
    // ==================== Security Settings ====================
    
    /**
     * Security method type
     */
    enum class SecurityMethod {
        NONE,
        PIN,
        BIOMETRIC
    }
    
    /**
     * Lock timing options
     */
    enum class LockTiming(val displayName: String, val timeoutMs: Long) {
        DISABLED("Disabled", -1L),
        WHEN_MINIMIZED("When minimized", 0L),
        AFTER_1_MIN("After 1 minute", 60_000L),
        AFTER_5_MIN("After 5 minutes", 300_000L)
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
            val matches = MessageDigest.isEqual(storedHash.toByteArray(), pin.toByteArray())
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
     * Get the stored PIN length hint (for UI display of dot indicators).
     * Returns null if no PIN is set.
     */
    fun getStoredPinLength(): Int? {
        if (!hasPin()) return null
        // If we have a stored length, use it
        val length = securePrefs.getInt(KEY_PIN_LENGTH, -1)
        if (length > 0) return length
        // Legacy: unhashed PIN stored directly, length can be read
        val stored = securePrefs.getString(KEY_PIN_CODE, null) ?: return null
        if (!securePrefs.contains(KEY_PIN_SALT)) return stored.length
        return null // Unknown length for migrated PINs without stored length
    }
    
    /**
     * Check if PIN is set
     */
    fun hasPin(): Boolean {
        return securePrefs.contains(KEY_PIN_CODE)
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
        val storedSaltStr = securePrefs.getString(KEY_PIN_SALT, null)
        
        // Legacy: unhashed PIN
        if (storedSaltStr == null) {
            return MessageDigest.isEqual(storedHash.toByteArray(), pin.toByteArray())
        }
        
        val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPin(pin, salt)
        val storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP)
        return constantTimeEquals(inputHash, storedHashBytes)
    }
    
    private fun requireHttpsForClearnet(url: String) {
        val parsed = android.net.Uri.parse(url)
        val host = parsed.host ?: ""
        if (!host.endsWith(".onion") && parsed.scheme?.lowercase() != "https") {
            throw IllegalArgumentException("HTTPS is required for non-.onion URLs")
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
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
    
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
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
        securePrefs.edit { putBoolean(KEY_CLOAK_MODE_ENABLED, enabled) }
    }
    
    fun setCloakCode(code: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(code, salt)
        securePrefs.edit {
            putString(KEY_CLOAK_CODE, Base64.encodeToString(hash, Base64.NO_WRAP))
            putString(KEY_CLOAK_CODE_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
        }
    }

    fun verifyCloakCode(code: String): Boolean {
        val storedHash = securePrefs.getString(KEY_CLOAK_CODE, null) ?: return false
        val storedSaltStr = securePrefs.getString(KEY_CLOAK_CODE_SALT, null) ?: return false
        val salt = Base64.decode(storedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPin(code, salt)
        val storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP)
        return constantTimeEquals(inputHash, storedHashBytes)
    }

    fun hasCloakCode(): Boolean {
        return securePrefs.contains(KEY_CLOAK_CODE)
    }
    
    /**
     * Clear all cloak mode data
     */
    fun clearCloakData() {
        securePrefs.edit {
            remove(KEY_CLOAK_MODE_ENABLED)
            remove(KEY_CLOAK_CODE)
            remove(KEY_CLOAK_CODE_SALT)
        }
        // Schedule alias swap back to default on next launch
        setPendingIconAlias(ALIAS_DEFAULT)
    }
    
    /**
     * Set a pending icon alias swap (applied on next cold start to avoid process kill)
     */
    fun setPendingIconAlias(alias: String) {
        regularPrefs.edit { putString(KEY_CLOAK_PENDING_ALIAS, alias) }
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
    }
    
    /**
     * Verify if the provided PIN matches the stored duress PIN hash.
     * Shares lockout counters with the real PIN to prevent double-attempt attacks.
     */
    fun verifyDuressPin(pin: String, incrementFailedAttempts: Boolean = true): Boolean {
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
    
    fun getDuressPinLength(): Int? {
        if (!securePrefs.contains(KEY_DURESS_PIN_CODE)) return null
        val length = securePrefs.getInt(KEY_DURESS_PIN_LENGTH, -1)
        if (length > 0) return length
        return null
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
        AFTER_10("10 attempts", 10)
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
     * Increment the shared failed attempt counter.
     * Used by biometric failures to share the same counter as PIN failures.
     */
    fun incrementFailedAttempts() {
        val attempts = securePrefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) + 1
        securePrefs.edit { putInt(KEY_PIN_FAILED_ATTEMPTS, attempts) }
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
     * Also deletes the Android Keystore MasterKey to eliminate forensic artifacts.
     */
    fun wipeAllData() {
        securePrefs.edit { clear() }
        regularPrefs.edit { clear() }
        // Delete the encrypted prefs file from disk BEFORE deleting the MasterKey.
        // If only the key is deleted, the stale Tink keyset in the prefs file causes
        // KeyPermanentlyInvalidatedException on the next EncryptedSharedPreferences.create().
        try {
            context.deleteSharedPreferences(SECURE_PREFS_FILE)
        } catch (_: Exception) { }
        // Delete the MasterKey from Android Keystore to remove forensic evidence
        // that EncryptedSharedPreferences was ever used
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry("_androidx_security_master_key_")
        } catch (_: Exception) { }
    }
    
    /**
     * Clear all duress data (PIN, wallet ID, real wallet ID)
     */
    fun clearDuressData() {
        securePrefs.edit {
            remove(KEY_DURESS_PIN_CODE)
            remove(KEY_DURESS_PIN_SALT)
            remove(KEY_DURESS_PIN_LENGTH)  // legacy cleanup
            remove(KEY_DURESS_ENABLED)
            remove(KEY_DURESS_WALLET_ID)
            remove(KEY_REAL_WALLET_ID)
        }
    }
    
    companion object {
        private const val SECURE_PREFS_FILE = "ibis_secure_prefs"
        private const val REGULAR_PREFS_FILE = "ibis_prefs"
        
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
        
        // Regular keys
        private const val KEY_NETWORK = "wallet_network"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_time_"
        private const val KEY_NEEDS_FULL_SYNC_PREFIX = "needs_full_sync_"
        private const val KEY_LAST_FULL_SYNC_PREFIX = "last_full_sync_time_"
        
        // Multi-server keys
        private const val KEY_SERVER_IDS = "server_ids"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
        private const val KEY_SERVER_PREFIX = "server_config_"
        private const val KEY_SERVER_CERT_PREFIX = "server_cert_"
        private const val KEY_DEFAULT_SERVERS_SEEDED = "default_servers_seeded"
        
        // Sync settings
        private const val KEY_SYNC_BATCH_SIZE = "sync_batch_size"
        
        // Tor settings
        private const val KEY_TOR_ENABLED = "tor_enabled"
        
        // Display settings
        private const val KEY_DENOMINATION = "denomination"
        const val DENOMINATION_BTC = "BTC"
        const val DENOMINATION_SATS = "SATS"
        
        // Mempool server settings
        private const val KEY_MEMPOOL_SERVER = "mempool_server"
        private const val KEY_MEMPOOL_CUSTOM_URL = "mempool_custom_url"
        const val MEMPOOL_DISABLED = "MEMPOOL_DISABLED"
        const val MEMPOOL_SPACE = "MEMPOOL_SPACE"
        const val MEMPOOL_ONION = "MEMPOOL_ONION"
        const val MEMPOOL_CUSTOM = "MEMPOOL_CUSTOM"
        
        // Fee estimation settings
        private const val KEY_FEE_SOURCE = "fee_source"
        private const val KEY_FEE_SOURCE_CUSTOM_URL = "fee_source_custom_url"
        const val FEE_SOURCE_OFF = "OFF"
        const val FEE_SOURCE_MEMPOOL = "MEMPOOL_SPACE"
        const val FEE_SOURCE_ELECTRUM = "ELECTRUM_SERVER"
        const val FEE_SOURCE_CUSTOM = "CUSTOM"
        
        // BTC/USD price source settings
        private const val KEY_PRICE_SOURCE = "price_source"
        const val PRICE_SOURCE_OFF = "OFF"
        const val PRICE_SOURCE_MEMPOOL = "MEMPOOL_SPACE"
        const val PRICE_SOURCE_COINGECKO = "COINGECKO"
        
        // Address labels
        private const val KEY_ADDRESS_LABEL_PREFIX = "address_label_"
        
        // Transaction labels
        private const val KEY_TX_LABEL_PREFIX = "tx_label_"
        
        // Pending PSBT labels (for watch-only wallet transactions awaiting signing)
        private const val KEY_PENDING_PSBT_LABEL_PREFIX = "pending_psbt_label_"
        
        // Transaction first-seen timestamps (for pending txs)
        private const val KEY_TX_FIRST_SEEN_PREFIX = "tx_first_seen_"
        
        // Spend only confirmed UTXOs
        private const val KEY_SPEND_UNCONFIRMED = "spend_unconfirmed"
        
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
        private const val MAX_PIN_ATTEMPTS = 5
        private const val KEY_LOCK_TIMING = "lock_timing"
        private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"
        private const val KEY_DISABLE_SCREENSHOTS = "disable_screenshots"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        
        // Auto-wipe
        private const val KEY_AUTO_WIPE_THRESHOLD = "auto_wipe_threshold"
        
        // Cloak mode
        private const val KEY_CLOAK_MODE_ENABLED = "cloak_mode_enabled"
        private const val KEY_CLOAK_CODE = "cloak_code"
        private const val KEY_CLOAK_CODE_SALT = "cloak_code_salt"
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
    }
}
