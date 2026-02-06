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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Secure storage for sensitive wallet data using Android's EncryptedSharedPreferences
 * Supports multiple wallets with unique IDs
 */
class SecureStorage(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
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
                createdAt = jsonObject.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Get the currently active wallet metadata
     */
    @Suppress("unused")
    fun getActiveWalletMetadata(): StoredWallet? {
        val activeId = getActiveWalletId() ?: return null
        return getWalletMetadata(activeId)
    }
    
    /**
     * Get all stored wallets
     */
    fun getAllWallets(): List<StoredWallet> {
        return getWalletIds().mapNotNull { getWalletMetadata(it) }
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
     * Check if wallet has a mnemonic
     */
    fun hasMnemonic(walletId: String): Boolean {
        return securePrefs.contains("${KEY_MNEMONIC_PREFIX}${walletId}")
    }
    
    /**
     * Delete mnemonic for a specific wallet
     */
    fun deleteMnemonic(walletId: String) {
        securePrefs.edit { remove("${KEY_MNEMONIC_PREFIX}${walletId}") }
    }
    
    // Legacy single-wallet methods for backward compatibility
    @Deprecated("Use saveMnemonic(walletId, mnemonic) instead")
    fun saveMnemonic(mnemonic: String) {
        val activeId = getActiveWalletId() ?: return
        saveMnemonic(activeId, mnemonic)
    }
    
    @Deprecated("Use getMnemonic(walletId) instead")
    fun getMnemonic(): String? {
        val activeId = getActiveWalletId() ?: return null
        return getMnemonic(activeId)
    }
    
    @Deprecated("Use hasMnemonic(walletId) instead")
    fun hasMnemonic(): Boolean {
        val activeId = getActiveWalletId() ?: return false
        return hasMnemonic(activeId)
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
    
    // Legacy single-wallet methods
    @Deprecated("Use hasExtendedKey(walletId) instead")
    fun hasExtendedKey(): Boolean {
        val activeId = getActiveWalletId() ?: return false
        return hasExtendedKey(activeId)
    }
    
    @Deprecated("Use getExtendedKey(walletId) instead")
    fun getExtendedKey(): String? {
        val activeId = getActiveWalletId() ?: return null
        return getExtendedKey(activeId)
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
    
    // Legacy single-wallet methods
    @Deprecated("Use getPassphrase(walletId) instead")
    fun getPassphrase(): String? {
        val activeId = getActiveWalletId() ?: return null
        return getPassphrase(activeId)
    }
    
    // ==================== Network Configuration ====================
    
    @Suppress("unused")
    fun saveNetwork(network: WalletNetwork) {
        regularPrefs.edit { putString(KEY_NETWORK, network.name) }
    }
    
    @Suppress("unused")
    fun getNetwork(): WalletNetwork {
        val networkName = regularPrefs.getString(KEY_NETWORK, WalletNetwork.BITCOIN.name)
        return try {
            WalletNetwork.valueOf(networkName ?: WalletNetwork.BITCOIN.name)
        } catch (_: IllegalArgumentException) {
            WalletNetwork.BITCOIN
        }
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
        regularPrefs.edit { remove("${KEY_SERVER_PREFIX}${serverId}") }
        removeServerId(serverId)
        
        // If this was the active server, clear active
        if (getActiveServerId() == serverId) {
            val remainingIds = getServerIds()
            setActiveServerId(remainingIds.firstOrNull())
        }
    }
    
    /**
     * Check if any Electrum server is configured
     */
    fun hasElectrumServer(): Boolean {
        return getServerIds().isNotEmpty()
    }
    
    // Legacy methods for backward compatibility
    @Deprecated("Use saveElectrumServer instead")
    fun saveElectrumConfig(config: ElectrumConfig) {
        // Convert to new format with generated ID if needed
        val serverId = if (config.id.isNullOrBlank()) java.util.UUID.randomUUID().toString() else config.id
        val serverName = if (config.name.isNullOrBlank()) config.url else config.name
        val newConfig = config.copy(id = serverId, name = serverName)
        saveElectrumServer(newConfig)
        setActiveServerId(serverId)
    }
    
    @Deprecated("Use getActiveElectrumServer instead")
    fun getElectrumConfig(): ElectrumConfig? {
        return getActiveElectrumServer()
    }
    
    @Deprecated("Use hasElectrumServer instead")
    fun hasElectrumConfig(): Boolean {
        return hasElectrumServer()
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
    
    // Legacy single-wallet methods
    @Deprecated("Use saveLastSyncTime(walletId, timestamp) instead")
    fun saveLastSyncTime(timestamp: Long) {
        val activeId = getActiveWalletId() ?: return
        saveLastSyncTime(activeId, timestamp)
    }
    
    @Deprecated("Use getLastSyncTime(walletId) instead")
    fun getLastSyncTime(): Long? {
        val activeId = getActiveWalletId() ?: return null
        return getLastSyncTime(activeId)
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
        
        // Delete metadata
        deleteWalletMetadata(walletId)
        
        // Delete sync time and full sync flags
        regularPrefs.edit { 
            remove("${KEY_LAST_SYNC_PREFIX}${walletId}")
            remove("${KEY_NEEDS_FULL_SYNC_PREFIX}${walletId}")
            remove("${KEY_LAST_FULL_SYNC_PREFIX}${walletId}")
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
     * Check if using sats denomination
     */
    fun isUsingSats(): Boolean {
        return getDenomination() == DENOMINATION_SATS
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
        regularPrefs.edit { putString(KEY_MEMPOOL_CUSTOM_URL, url) }
    }
    
    // ==================== Fee Estimation Settings ====================
    
    /**
     * Get the selected fee estimation source
     * Default is OFF (manual entry only)
     */
    fun getFeeSource(): String {
        val source = regularPrefs.getString(KEY_FEE_SOURCE, FEE_SOURCE_OFF) ?: FEE_SOURCE_OFF
        // Migrate old onion/custom settings to OFF
        return if (source == "MEMPOOL_ONION" || source == "CUSTOM") {
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
     * Get the full fee source URL based on selected option
     * Returns null if fee estimation is disabled (OFF)
     */
    fun getFeeSourceUrl(): String? {
        return when (getFeeSource()) {
            FEE_SOURCE_OFF -> null
            FEE_SOURCE_MEMPOOL -> "https://mempool.space"
            else -> null
        }
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
        val method = regularPrefs.getString(KEY_SECURITY_METHOD, SecurityMethod.NONE.name)
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
        regularPrefs.edit { putString(KEY_SECURITY_METHOD, method.name) }
    }
    
    /**
     * Save the PIN code (encrypted)
     */
    fun savePin(pin: String) {
        securePrefs.edit { putString(KEY_PIN_CODE, pin) }
    }
    
    /**
     * Get the stored PIN code
     */
    fun getPin(): String? {
        return securePrefs.getString(KEY_PIN_CODE, null)
    }
    
    /**
     * Verify if the provided PIN matches the stored PIN
     */
    fun verifyPin(pin: String): Boolean {
        val storedPin = getPin()
        return storedPin != null && storedPin == pin
    }
    
    /**
     * Check if PIN is set
     */
    fun hasPin(): Boolean {
        return securePrefs.contains(KEY_PIN_CODE)
    }
    
    /**
     * Clear PIN code
     */
    fun clearPin() {
        securePrefs.edit { remove(KEY_PIN_CODE) }
    }
    
    /**
     * Check if biometric authentication is enabled
     */
    fun isBiometricEnabled(): Boolean {
        return getSecurityMethod() == SecurityMethod.BIOMETRIC
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
        val timing = regularPrefs.getString(KEY_LOCK_TIMING, LockTiming.DISABLED.name)
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
        regularPrefs.edit { putString(KEY_LOCK_TIMING, timing.name) }
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
    
    // ==================== Clear All Data ====================
    
    fun clearAllData() {
        securePrefs.edit { clear() }
        regularPrefs.edit { clear() }
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
        
        // Regular keys
        private const val KEY_NETWORK = "wallet_network"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_time_"
        private const val KEY_NEEDS_FULL_SYNC_PREFIX = "needs_full_sync_"
        private const val KEY_LAST_FULL_SYNC_PREFIX = "last_full_sync_time_"
        
        // Multi-server keys
        private const val KEY_SERVER_IDS = "server_ids"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
        private const val KEY_SERVER_PREFIX = "server_config_"
        
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
        const val FEE_SOURCE_OFF = "OFF"
        const val FEE_SOURCE_MEMPOOL = "MEMPOOL_SPACE"
        
        // BTC/USD price source settings
        private const val KEY_PRICE_SOURCE = "price_source"
        const val PRICE_SOURCE_OFF = "OFF"
        const val PRICE_SOURCE_MEMPOOL = "MEMPOOL_SPACE"
        const val PRICE_SOURCE_COINGECKO = "COINGECKO"
        
        // Address labels
        private const val KEY_ADDRESS_LABEL_PREFIX = "address_label_"
        
        // Transaction labels
        private const val KEY_TX_LABEL_PREFIX = "tx_label_"
        
        // Frozen UTXOs
        private const val KEY_FROZEN_UTXOS_PREFIX = "frozen_utxos_"
        
        // Security settings
        private const val KEY_SECURITY_METHOD = "security_method"
        private const val KEY_PIN_CODE = "pin_code"
        private const val KEY_LOCK_TIMING = "lock_timing"
        private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"
    }
}
