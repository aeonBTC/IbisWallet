package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.ImportQrScannerDialog
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.QrFormatParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    onImport: (config: WalletImportConfig) -> Unit,
    onImportFromBackup: (backupJson: JSONObject) -> Unit = {},
    onParseBackupFile: suspend (uri: Uri, password: String?) -> JSONObject = { _, _ -> JSONObject() },
    onBack: () -> Unit,
    onSweepPrivateKey: () -> Unit = {},
    existingWalletNames: List<String> = emptyList(),
    isLoading: Boolean = false,
    error: String? = null,
) {
    var walletName by remember { mutableStateOf("") }
    var selectedAddressType by remember { mutableStateOf(AddressType.SEGWIT) }
    var keyMaterial by remember { mutableStateOf("") }
    var selectedNetwork by remember { mutableStateOf(WalletNetwork.BITCOIN) }

    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }

    // Backup restore state
    var backupFileUri by remember { mutableStateOf<Uri?>(null) }
    var backupFileName by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var showBackupPassword by remember { mutableStateOf(false) }
    var backupIsEncrypted by remember { mutableStateOf<Boolean?>(null) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var backupParsedJson by remember { mutableStateOf<JSONObject?>(null) }
    var backupWalletName by remember { mutableStateOf<String?>(null) }
    var isParsingBackup by remember { mutableStateOf(false) }
    val backupCoroutineScope = rememberCoroutineScope()

    val backupFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                backupFileUri = uri
                backupFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "backup.json"
                backupIsEncrypted = null
                backupError = null
                backupParsedJson = null
                backupWalletName = null
                backupPassword = ""

                // Try to parse without password first to check if encrypted
                backupCoroutineScope.launch {
                    isParsingBackup = true
                    try {
                        val json = onParseBackupFile(uri, null)
                        backupIsEncrypted = false
                        backupParsedJson = json
                        backupWalletName = json.optJSONObject("wallet")?.optString("name")
                        backupError = null
                    } catch (e: Exception) {
                        if (e.message?.contains("encrypted", ignoreCase = true) == true) {
                            backupIsEncrypted = true
                            backupError = null
                        } else {
                            backupError = e.message ?: "Failed to read backup file"
                        }
                    } finally {
                        isParsingBackup = false
                    }
                }
            }
        }

    // Advanced options
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var usePassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var useCustomPath by remember { mutableStateOf(false) }
    var customPath by remember { mutableStateOf("") }
    var useCustomFingerprint by remember { mutableStateOf(false) }
    var masterFingerprint by remember { mutableStateOf("") }

    // Determine if input is seed phrase, extended key, output descriptor, WIF key, or address
    val trimmedInput = keyMaterial.trim()
    val isExtendedKey =
        trimmedInput.let {
            it.startsWith("xpub") || it.startsWith("ypub") ||
                it.startsWith("zpub") || it.startsWith("tpub") ||
                it.startsWith("vpub") || it.startsWith("upub") ||
                it.startsWith("xprv") || it.startsWith("yprv") ||
                it.startsWith("zprv") || it.startsWith("tprv")
        }
    // Also detect [fingerprint/path]xpub and output descriptor formats
    val isOriginPrefixed =
        trimmedInput.startsWith("[") && trimmedInput.contains("]") &&
            trimmedInput.substringAfter("]").let {
                it.startsWith("xpub") || it.startsWith("ypub") ||
                    it.startsWith("zpub") || it.startsWith("tpub") ||
                    it.startsWith("vpub") || it.startsWith("upub")
            }
    val isOutputDescriptor =
        listOf("pkh(", "wpkh(", "tr(", "sh(wpkh(", "sh(").any {
            trimmedInput.lowercase().startsWith(it)
        } && (
            trimmedInput.contains("xpub") || trimmedInput.contains("tpub") ||
                trimmedInput.contains("ypub") || trimmedInput.contains("zpub") ||
                trimmedInput.contains("vpub") || trimmedInput.contains("upub")
        )
    // Detect ColdCard/Specter JSON format
    val isJsonFormat = trimmedInput.startsWith("{") && trimmedInput.endsWith("}")

    val isWatchOnlyKey = isExtendedKey || isOriginPrefixed || isOutputDescriptor || isJsonFormat
    val isWatchOnly =
        isWatchOnlyKey &&
            !trimmedInput.let {
                it.startsWith("xprv") || it.startsWith("yprv") ||
                    it.startsWith("zprv") || it.startsWith("tprv") ||
                    it.contains("xprv") || it.contains("tprv")
            }

    // Detect WIF private key (K/L/5 for mainnet, c/9 for testnet)
    val isWifKey =
        remember(keyMaterial) {
            val t = keyMaterial.trim()
            val couldBeWif =
                (t.length == 52 && (t[0] == 'K' || t[0] == 'L' || t[0] == 'c')) ||
                    (t.length == 51 && (t[0] == '5' || t[0] == '9'))
            if (!couldBeWif) {
                false
            } else {
                try {
                    // Validate Base58Check via BDK descriptor parse (fast, no network)
                    Descriptor("wpkh($t)", Network.BITCOIN)
                    true
                } catch (_: Exception) {
                    try {
                        Descriptor("wpkh($t)", Network.TESTNET)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }
        }

    // Detect single Bitcoin address (1..., 3..., bc1q..., bc1p...)
    val isBitcoinAddress =
        remember(keyMaterial) {
            val t = keyMaterial.trim()
            if (t.isBlank() || t.contains(" ")) {
                false
            } else {
                val looksLikeAddress =
                    t.startsWith("1") || t.startsWith("3") ||
                        t.startsWith("bc1q") || t.startsWith("bc1p") ||
                        t.startsWith("tb1q") || t.startsWith("tb1p") ||
                        t.startsWith("m") || t.startsWith("n") || t.startsWith("2")
                if (!looksLikeAddress) {
                    false
                } else {
                    try {
                        org.bitcoindevkit.Address(t, Network.BITCOIN)
                        true
                    } catch (_: Exception) {
                        try {
                            org.bitcoindevkit.Address(t, Network.TESTNET)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
            }
        }

    // Auto-detect address type for single addresses
    val detectedAddressType =
        remember(keyMaterial) {
            val t = keyMaterial.trim()
            when {
                t.startsWith("1") || t.startsWith("m") || t.startsWith("n") -> AddressType.LEGACY
                t.startsWith("3") || t.startsWith("2") -> AddressType.NESTED_SEGWIT
                t.startsWith("bc1q") || t.startsWith("tb1q") -> AddressType.SEGWIT
                t.startsWith("bc1p") || t.startsWith("tb1p") -> AddressType.TAPROOT
                else -> null
            }
        }

    // Auto-switch address type when a single address is detected
    if (isBitcoinAddress && detectedAddressType != null && selectedAddressType != detectedAddressType) {
        selectedAddressType = detectedAddressType
    }

    // Detect the key prefix for the bare key (after origin bracket if present)
    // zpub/ypub/vpub/upub signal SegWit derivation — Legacy is inappropriate
    val bareKeyPrefix =
        if (isOriginPrefixed) {
            trimmedInput.substringAfter("]").take(4)
        } else {
            trimmedInput.take(4)
        }
    val isSegwitVersionKey = bareKeyPrefix in listOf("zpub", "ypub", "vpub", "upub")
    val isLegacyDisabled = isWatchOnly && isSegwitVersionKey

    // Auto-switch away from Legacy if a SegWit-versioned key is entered
    if (isLegacyDisabled && selectedAddressType == AddressType.LEGACY) {
        selectedAddressType = AddressType.SEGWIT
    }

    // Auto-extract fingerprint from origin-prefixed or descriptor input
    val parsedFingerprint =
        remember(keyMaterial) {
            """\[([a-fA-F0-9]{8})/""".toRegex().find(trimmedInput)?.groupValues?.get(1)?.lowercase()
        }
    val hasEmbeddedFingerprint = parsedFingerprint != null

    // Auto-open advanced options when bare xpub/zpub has no master fingerprint
    LaunchedEffect(isExtendedKey, isWatchOnly, hasEmbeddedFingerprint) {
        if (isExtendedKey && isWatchOnly && !hasEmbeddedFingerprint) {
            showAdvancedOptions = true
        }
    }

    val context = LocalContext.current

    // Validate seed phrase word count
    val words =
        if (!isExtendedKey && !isWatchOnlyKey) {
            keyMaterial.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    val wordCount = words.size
    val isValidWordCount = wordCount in listOf(12, 15, 18, 21, 24)

    // Validate words against BIP39 wordlist using the 4-character prefix rule.
    // BIP39 guarantees the first 4 chars uniquely identify each word, so we can
    // check validity as soon as 4 characters are typed — no need to wait for space.
    // Words with <4 chars are skipped (still being typed).
    val bip39WordSet = remember { QrFormatParser.getWordlist(context).toSet() }
    val bip39PrefixSet = remember { bip39WordSet.map { it.take(4) }.toSet() }
    val invalidWords =
        remember(keyMaterial) {
            if (words.isNotEmpty()) {
                words.mapIndexedNotNull { index, word ->
                    when {
                        word.length < 4 -> null // Still typing, don't flag yet
                        word in bip39WordSet -> null // Exact match
                        word.take(4) !in bip39PrefixSet -> index to word // No BIP39 word starts with these 4 chars
                        else -> null // Prefix matches a valid word, user may still be typing
                    }
                }
            } else {
                emptyList()
            }
        }

    // Check if all words with 4+ chars have valid prefixes (for enabling mnemonic check)
    val allTypedWordsValid =
        words.isNotEmpty() && invalidWords.isEmpty() &&
            words.all { it in bip39WordSet }

    // Full BIP39 mnemonic validation (wordlist + checksum) via BDK.
    // Only runs when word count is valid and every word is a complete BIP39 word.
    val mnemonicValidation =
        remember(keyMaterial) {
            if (!isExtendedKey && isValidWordCount && allTypedWordsValid) {
                try {
                    Mnemonic.fromString(keyMaterial.trim())
                    MnemonicValidation.Valid
                } catch (e: Exception) {
                    MnemonicValidation.Invalid(e.message ?: "Invalid checksum")
                }
            } else {
                MnemonicValidation.NotChecked
            }
        }
    val isValidMnemonic = mnemonicValidation is MnemonicValidation.Valid

    val isValidInput = isWatchOnlyKey || isExtendedKey || isValidMnemonic || isWifKey || isBitcoinAddress

    // Auto-generate wallet name based on input type with incremental suffix
    val autoWalletName =
        remember(selectedAddressType, existingWalletNames, isWifKey, isBitcoinAddress) {
            val base =
                when {
                    isBitcoinAddress -> "Watch_${selectedAddressType.displayName}"
                    isWifKey -> "Key_${selectedAddressType.displayName}"
                    else -> selectedAddressType.displayName
                }
            val count =
                existingWalletNames.count { name ->
                    name == base || name.matches(Regex("${Regex.escape(base)}_(\\d+)"))
                }
            if (count == 0) base else "${base}_${count + 1}"
        }

    val canImport = keyMaterial.isNotBlank() && isValidInput && !isLoading

    // Dynamically derive master fingerprint
    var derivedFingerprint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(keyMaterial, selectedAddressType, passphrase, usePassphrase, isValidMnemonic, parsedFingerprint) {
        when {
            // For xpubs/descriptors with embedded fingerprint, use the parsed value
            parsedFingerprint != null -> {
                derivedFingerprint = parsedFingerprint
            }
            // For valid seed phrases, derive from mnemonic
            isValidMnemonic -> {
                derivedFingerprint =
                    withContext(Dispatchers.Default) {
                        try {
                            val mnemonicObj = Mnemonic.fromString(keyMaterial.trim())
                            val pass = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                            val secretKey = DescriptorSecretKey(Network.BITCOIN, mnemonicObj, pass)
                            val descriptor =
                                when (selectedAddressType) {
                                    AddressType.LEGACY -> Descriptor.newBip44(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
                                    AddressType.NESTED_SEGWIT -> Descriptor.newBip49(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
                                    AddressType.SEGWIT -> Descriptor.newBip84(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
                                    AddressType.TAPROOT -> Descriptor.newBip86(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
                                }
                            """\[([a-fA-F0-9]{8})/""".toRegex()
                                .find(descriptor.toString())?.groupValues?.get(1)?.lowercase()
                        } catch (_: Exception) {
                            null
                        }
                    }
            }
            else -> {
                derivedFingerprint = null
            }
        }
    }

    // Get derivation path to use
    val derivationPath =
        if (useCustomPath && customPath.isNotBlank()) {
            customPath
        } else {
            selectedAddressType.defaultPath
        }

    // QR Scanner Dialog - supports single-frame, animated UR, and all key formats
    if (showQrScanner) {
        ImportQrScannerDialog(
            preferredAddressType = selectedAddressType,
            onCodeScanned = { scannedText ->
                // The ImportQrScannerDialog already handles UR decoding internally.
                // For non-UR content, run through QrFormatParser for SeedQR/CompactSeedQR.
                keyMaterial = QrFormatParser.parseWalletQr(context, scannedText, selectedAddressType)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Import Wallet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Manual Import Section
        Text(
            text = "Manual Import",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Wallet Name
                Text(
                    text = "Wallet Name",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = walletName,
                    onValueChange = { walletName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(autoWalletName, color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange,
                        ),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Address Type Selection
                Text(
                    text = "Address Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddressType.entries
                        .filter { it != AddressType.NESTED_SEGWIT }
                        .forEach { addressType ->
                            val enabled =
                                !(addressType == AddressType.LEGACY && isLegacyDisabled) &&
                                    !(isBitcoinAddress && detectedAddressType != null && addressType != detectedAddressType)
                            AddressTypeButton(
                                addressType = addressType,
                                isSelected = selectedAddressType == addressType,
                                enabled = enabled,
                                onClick = { selectedAddressType = addressType },
                                modifier = Modifier.weight(1f),
                            )
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Address type description
                Text(
                    text = selectedAddressType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Seed Phrase / Extended Key / WIF / Address Input
                Text(
                    text = "Seed Phrase, Key, or Address",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = keyMaterial,
                        onValueChange = { input ->
                            val trimmed = input.trim()
                            val isKeyOrDescriptor =
                                trimmed.let { text ->
                                    text.startsWith("xpub") || text.startsWith("ypub") ||
                                        text.startsWith("zpub") || text.startsWith("tpub") ||
                                        text.startsWith("vpub") || text.startsWith("upub") ||
                                        text.startsWith("xprv") || text.startsWith("yprv") ||
                                        text.startsWith("zprv") || text.startsWith("tprv") ||
                                        text.startsWith("[") || // Origin-prefixed key
                                        text.startsWith("{") || // JSON format (ColdCard, Specter)
                                        listOf("pkh(", "wpkh(", "tr(", "sh(").any {
                                            text.lowercase().startsWith(it)
                                        } // Output descriptor
                                }
                            // WIF private keys: K/L/5 (mainnet), c/9 (testnet)
                            val isWifInput =
                                trimmed.let { text ->
                                    (
                                        text.length <= 52 && text.isNotEmpty() &&
                                            (
                                                text[0] == 'K' || text[0] == 'L' || text[0] == '5' ||
                                                    text[0] == 'c' || text[0] == '9'
                                            )
                                    ) &&
                                        !text.contains(" ")
                                }
                            // Bitcoin addresses (Base58/Bech32)
                            val isAddressInput =
                                trimmed.let { text ->
                                    text.isNotEmpty() && !text.contains(" ") &&
                                        (
                                            text.startsWith("1") || text.startsWith("3") ||
                                                text.startsWith("bc1") || text.startsWith("tb1") ||
                                                text.startsWith("m") || text.startsWith("n") ||
                                                text.startsWith("2")
                                        )
                                }
                            keyMaterial =
                                if (isKeyOrDescriptor || isWifInput || isAddressInput) {
                                    input // Preserve case for keys/descriptors/addresses (Base58/Bech32 encoded)
                                } else {
                                    input.lowercase() // Lowercase for seed phrases
                                }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        shape = RoundedCornerShape(8.dp),
                        placeholder = {
                            Text(
                                "Seed, xpub/zpub, WIF key, or address",
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        keyboardOptions = KeyboardOptions(autoCorrect = false),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = BitcoinOrange,
                            ),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { showQrScanner = true },
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = BitcoinOrange,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Input validation feedback
                if (keyMaterial.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            isJsonFormat -> {
                                Text(
                                    text = "JSON wallet export detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isOutputDescriptor -> {
                                Text(
                                    text =
                                        if (hasEmbeddedFingerprint) {
                                            "Output descriptor with key origin"
                                        } else {
                                            "Output descriptor detected"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isOriginPrefixed -> {
                                Text(
                                    text = "Watch-only with key origin",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isWatchOnly -> {
                                Column {
                                    Text(
                                        text = "Watch-only wallet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentTeal,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (isExtendedKey && !hasEmbeddedFingerprint) {
                                        Text(
                                            text = "Some hardware wallets do not support PSBT signing without a master fingerprint — your hardware wallet should provide one alongside the xpub/zpub.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }
                            }
                            isWifKey -> {
                                Text(
                                    text = "WIF private key detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                )
                            }
                            isBitcoinAddress -> {
                                Text(
                                    text = "Watch address (${detectedAddressType?.displayName ?: ""})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isExtendedKey && !isWatchOnly -> {
                                Text(
                                    text = "Extended private key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                            isValidMnemonic -> {
                                Text(
                                    text = "Valid BIP39 seed phrase",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                )
                            }
                            isValidWordCount && mnemonicValidation is MnemonicValidation.Invalid -> {
                                Text(
                                    text = (mnemonicValidation as MnemonicValidation.Invalid).error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                )
                            }
                            invalidWords.isNotEmpty() -> {
                                val badWords = invalidWords.take(3).joinToString(", ") { "\"${it.second}\"" }
                                val suffix = if (invalidWords.size > 3) " +${invalidWords.size - 3} more" else ""
                                Text(
                                    text = "Invalid word${if (invalidWords.size > 1) "s" else ""}: $badWords$suffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                )
                            }
                            isValidWordCount && invalidWords.isEmpty() -> {
                                // Valid count, no bad words, but last word still being typed
                                Text(
                                    text = "$wordCount words entered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            wordCount < 12 -> {
                                Text(
                                    text = "$wordCount ${if (wordCount == 1) "word" else "words"} entered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            else -> {
                                Text(
                                    text = "$wordCount words - need 12, 15, 18, 21, or 24",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                )
                            }
                        }
                        derivedFingerprint?.let { fp ->
                            Text(
                                text = "Fingerprint: $fp",
                                style = MaterialTheme.typography.bodySmall,
                                color = BitcoinOrange,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Advanced Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Advanced Options Header
                Surface(
                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                    color = DarkCard,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Advanced Options",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Icon(
                            imageVector =
                                if (showAdvancedOptions) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                            contentDescription = if (showAdvancedOptions) "Collapse" else "Expand",
                            tint = TextSecondary,
                        )
                    }
                }

                // Advanced Options Content
                AnimatedVisibility(
                    visible = showAdvancedOptions,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                    ) {
                        HorizontalDivider(color = BorderColor)

                        Spacer(modifier = Modifier.height(4.dp))

                        // Origin info warning + Master Fingerprint (only for watch-only)
                        AnimatedVisibility(
                            visible = isWatchOnly,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                // Show warning when bare xpub is entered without origin info
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { useCustomFingerprint = !useCustomFingerprint },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = useCustomFingerprint,
                                        onCheckedChange = { useCustomFingerprint = it },
                                        colors =
                                            CheckboxDefaults.colors(
                                                checkedColor = BitcoinOrange,
                                                uncheckedColor = TextSecondary,
                                            ),
                                    )
                                    Text(
                                        text = "Set Master Fingerprint",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }

                                AnimatedVisibility(
                                    visible = useCustomFingerprint,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = masterFingerprint,
                                            onValueChange = { input ->
                                                masterFingerprint =
                                                    input.filter {
                                                        it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
                                                    }.take(8).lowercase()
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp),
                                            placeholder = {
                                                Text(
                                                    "00000000",
                                                    color = TextSecondary.copy(alpha = 0.5f),
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors =
                                                OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = BitcoinOrange,
                                                    unfocusedBorderColor = BorderColor,
                                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                                    cursorColor = BitcoinOrange,
                                                ),
                                            isError = masterFingerprint.isNotEmpty() && masterFingerprint.length != 8,
                                        )
                                        if (masterFingerprint.isNotEmpty() && masterFingerprint.length != 8) {
                                            Text(
                                                text = "Must be exactly 8 hex characters",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ErrorRed,
                                                modifier = Modifier.padding(start = 12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Use BIP39 Passphrase
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isExtendedKey) { usePassphrase = !usePassphrase },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = usePassphrase,
                                onCheckedChange = { usePassphrase = it },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = BitcoinOrange,
                                        uncheckedColor = TextSecondary,
                                    ),
                                enabled = !isExtendedKey,
                            )
                            Text(
                                text = "Use BIP39 Passphrase",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (isExtendedKey) {
                                        TextSecondary.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                            )
                        }

                        // Passphrase field
                        AnimatedVisibility(
                            visible = usePassphrase && !isExtendedKey,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                    placeholder = {
                                        Text(
                                            "Enter passphrase",
                                            color = TextSecondary.copy(alpha = 0.5f),
                                        )
                                    },
                                    visualTransformation =
                                        if (showPassphrase) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            autoCorrect = false,
                                            keyboardType = KeyboardType.Password,
                                        ),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                            Icon(
                                                imageVector =
                                                    if (showPassphrase) {
                                                        Icons.Default.Visibility
                                                    } else {
                                                        Icons.Default.VisibilityOff
                                                    },
                                                contentDescription = null,
                                                tint = TextSecondary,
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BitcoinOrange,
                                            unfocusedBorderColor = BorderColor,
                                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            cursorColor = BitcoinOrange,
                                        ),
                                )
                            }
                        }

                        // Use Custom Derivation Path
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isExtendedKey) { useCustomPath = !useCustomPath },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = useCustomPath,
                                onCheckedChange = { useCustomPath = it },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = BitcoinOrange,
                                        uncheckedColor = TextSecondary,
                                    ),
                                enabled = !isExtendedKey,
                            )
                            Text(
                                text = "Use Custom Derivation Path",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (isExtendedKey) {
                                        TextSecondary.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                            )
                        }

                        // Custom path field
                        AnimatedVisibility(
                            visible = useCustomPath && !isExtendedKey,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customPath,
                                    onValueChange = { customPath = it },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                    placeholder = {
                                        Text(
                                            selectedAddressType.defaultPath,
                                            color = TextSecondary.copy(alpha = 0.5f),
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BitcoinOrange,
                                            unfocusedBorderColor = BorderColor,
                                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            cursorColor = BitcoinOrange,
                                        ),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Default: ${selectedAddressType.defaultPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.1f),
                    ),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Import button
        Button(
            onClick = {
                val passphraseValue = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                val customPathValue = if (useCustomPath && customPath.isNotBlank()) customPath else null

                // Use embedded fingerprint if available, then user-provided, then default
                val fingerprintValue =
                    parsedFingerprint
                        ?: if (useCustomFingerprint && masterFingerprint.length == 8) {
                            masterFingerprint
                        } else {
                            null
                                ?: if (isWatchOnly) "00000000" else null
                        }

                val finalName = walletName.trim().ifBlank { autoWalletName }
                val config =
                    WalletImportConfig(
                        name = finalName,
                        keyMaterial = keyMaterial.trim(),
                        addressType = selectedAddressType,
                        passphrase = passphraseValue,
                        customDerivationPath = customPathValue,
                        network = selectedNetwork,
                        isWatchOnly = isWatchOnly || isBitcoinAddress,
                        masterFingerprint = fingerprintValue,
                    )
                onImport(config)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = canImport,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitcoinOrange,
                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    when {
                        isBitcoinAddress -> "Watch Address"
                        isWifKey -> "Import Key"
                        else -> "Import Wallet"
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderColor,
            )
            Text(
                text = "or",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderColor,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Restore from Backup Section
        Text(
            text = "Restore from Backup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Import an Ibis wallet backup file",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        IbisButton(
            onClick = { backupFilePickerLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = backupFileName ?: "Select backup file",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Parsing indicator
        if (isParsingBackup) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = BitcoinOrange,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reading backup file...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        // Backup file info and restore flow
        if (backupFileUri != null && !isParsingBackup) {
            Spacer(modifier = Modifier.height(8.dp))

            // Encrypted backup - show password field
            if (backupIsEncrypted == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Encrypted backup - enter password to decrypt",
                        style = MaterialTheme.typography.bodySmall,
                        color = BitcoinOrange,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backup Password", color = TextSecondary) },
                    singleLine = true,
                    visualTransformation =
                        if (showBackupPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrect = false,
                            keyboardType = KeyboardType.Password,
                        ),
                    trailingIcon = {
                        IconButton(onClick = { showBackupPassword = !showBackupPassword }) {
                            Icon(
                                imageVector =
                                    if (showBackupPassword) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = BitcoinOrange,
                        ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                IbisButton(
                    onClick = {
                        backupCoroutineScope.launch {
                            isParsingBackup = true
                            backupError = null
                            try {
                                val json = onParseBackupFile(backupFileUri!!, backupPassword)
                                backupParsedJson = json
                                backupWalletName = json.optJSONObject("wallet")?.optString("name")
                                backupError = null
                            } catch (e: Exception) {
                                backupError =
                                    if (e.message?.contains("mac", ignoreCase = true) == true ||
                                        e.message?.contains("tag", ignoreCase = true) == true ||
                                        e.message?.contains("AEADBadTagException", ignoreCase = true) == true
                                    ) {
                                        "Incorrect password"
                                    } else {
                                        e.message ?: "Decryption failed"
                                    }
                                backupParsedJson = null
                            } finally {
                                isParsingBackup = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = backupPassword.isNotEmpty(),
                ) {
                    Text("Decrypt")
                }
            }

            // Successfully parsed - show wallet info and restore button
            if (backupParsedJson != null && backupWalletName != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = SuccessGreen.copy(alpha = 0.1f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            text = "Backup ready to restore",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SuccessGreen,
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val walletObj = backupParsedJson!!.optJSONObject("wallet")
                        Text(
                            text = "Wallet: ${walletObj?.optString("name", "Unknown")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        val addressType = walletObj?.optString("addressType", "")
                        val isWatchOnly = walletObj?.optBoolean("isWatchOnly", false) == true
                        Text(
                            text = "Type: $addressType${if (isWatchOnly) " (Watch Only)" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        val hasLabels = backupParsedJson!!.optJSONObject("labels") != null
                        if (hasLabels) {
                            val labelsObj = backupParsedJson!!.optJSONObject("labels")
                            val addrCount = labelsObj?.optJSONObject("addresses")?.length() ?: 0
                            val txCount = labelsObj?.optJSONObject("transactions")?.length() ?: 0
                            Text(
                                text = "Labels: $addrCount address, $txCount transaction",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        backupParsedJson?.let { json ->
                            onImportFromBackup(json)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                            disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                        ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Wallet")
                    }
                }
            }

            // Error display
            if (backupError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = backupError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                )
            }

            // Unencrypted backup detected
            if (backupIsEncrypted == false && backupParsedJson == null && backupError == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unencrypted backup detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderColor,
            )
            Text(
                text = "or",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderColor,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sweep Section
        Text(
            text = "Sweep Private Key",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        IbisButton(
            onClick = onSweepPrivateKey,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Input private key")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AddressTypeButton(
    addressType: AddressType,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when {
            !enabled -> DarkSurfaceVariant.copy(alpha = 0.4f)
            isSelected -> BitcoinOrange
            else -> DarkSurfaceVariant
        }
    val contentColor =
        when {
            !enabled -> TextSecondary.copy(alpha = 0.3f)
            isSelected -> DarkBackground
            else -> TextSecondary
        }
    val borderColor =
        when {
            !enabled -> BorderColor.copy(alpha = 0.3f)
            isSelected -> BitcoinOrange
            else -> BorderColor
        }

    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = addressType.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

/**
 * Mnemonic validation state
 */
private sealed class MnemonicValidation {
    data object NotChecked : MnemonicValidation()

    data object Valid : MnemonicValidation()

    data class Invalid(val error: String) : MnemonicValidation()
}
