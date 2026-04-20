@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.ui.components.Bip39SuggestionRow
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.ImportQrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.SensitiveSeedIme
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.components.sensitiveSeedKeyboardOptions
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
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
    onImportLiquidWatchOnly: (name: String, ctDescriptor: String, gapLimit: Int) -> Unit = { _, _, _ -> },
    onImportFromBackup: (backupJson: JSONObject, importServerSettings: Boolean) -> Unit = { _, _ -> },
    onParseBackupFile: suspend (uri: Uri, password: String?) -> JSONObject = { _, _ -> JSONObject() },
    onBack: () -> Unit,
    onSweepPrivateKey: () -> Unit = {},
    existingWalletNames: List<String> = emptyList(),
    isLoading: Boolean = false,
    error: String? = null,
) {
    var walletName by remember { mutableStateOf("") }
    var selectedAddressType by remember { mutableStateOf(AddressType.SEGWIT) }
    var keyMaterialField by remember { mutableStateOf(TextFieldValue("")) }
    val keyMaterial = keyMaterialField.text

    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }

    // Backup restore state
    var backupFileUri by remember { mutableStateOf<Uri?>(null) }
    var backupFileName by remember { mutableStateOf<String?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var showBackupPassword by remember { mutableStateOf(false) }
    var backupIsEncrypted by remember { mutableStateOf<Boolean?>(null) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var backupParsedJson by remember { mutableStateOf<JSONObject?>(null) }
    var isParsingBackup by remember { mutableStateOf(false) }
    var importServerSettings by remember { mutableStateOf(true) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    val backupCoroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val backupFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                backupFileUri = uri
                backupFileName = getDisplayNameFromUri(context, uri) ?: "backup.json"
                backupIsEncrypted = null
                backupError = null
                backupParsedJson = null
                backupPassword = ""
                showBackupRestoreDialog = true

                // Try to parse without password first to check if encrypted
                backupCoroutineScope.launch {
                    isParsingBackup = true
                    try {
                        val json = onParseBackupFile(uri, null)
                        backupIsEncrypted = false
                        backupParsedJson = json
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
    var useCustomGapLimit by remember { mutableStateOf(false) }
    var gapLimitText by remember { mutableStateOf("") }
    val advancedOptionsBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showAdvancedOptions, "import_advanced")
    val customFingerprintBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(useCustomFingerprint, "import_fingerprint")
    val passphraseBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(usePassphrase, "import_passphrase")
    val customPathBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(useCustomPath, "import_path")
    val customGapLimitBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(useCustomGapLimit, "import_gap_limit")

    // Determine if input is seed phrase, extended key, output descriptor, WIF key, or address
    val trimmedInput = keyMaterial.trim()
    val unsupportedNonMainnetReason =
        remember(keyMaterial) {
            BitcoinUtils.unsupportedNonMainnetReason(trimmedInput)
        }
    val unsupportedNestedSegwitReason =
        remember(keyMaterial) {
            BitcoinUtils.unsupportedNestedSegwitReason(trimmedInput)
        }
    val isExtendedKey =
        trimmedInput.let {
            it.startsWith("xpub") || it.startsWith("ypub") ||
                it.startsWith("zpub") || it.startsWith("upub") ||
                it.startsWith("xprv") || it.startsWith("yprv") ||
                it.startsWith("zprv")
        }
    // Also detect [fingerprint/path]xpub and output descriptor formats
    val isOriginPrefixed =
        trimmedInput.startsWith("[") && trimmedInput.contains("]") &&
            trimmedInput.substringAfter("]").let {
                it.startsWith("xpub") || it.startsWith("ypub") ||
                    it.startsWith("zpub") || it.startsWith("upub")
            }
    val isOutputDescriptor =
        listOf("pkh(", "wpkh(", "tr(").any {
            trimmedInput.lowercase().startsWith(it)
        } && (
            trimmedInput.contains("xpub") || trimmedInput.contains("zpub")
        )
    // Detect ColdCard/Specter JSON format
    val isJsonFormat = trimmedInput.startsWith("{") && trimmedInput.endsWith("}")

    val liquidDescriptorLines =
        remember(keyMaterial) {
            trimmedInput.lineSequence()
                .map { BitcoinUtils.stripDescriptorChecksum(it).trim() }
                .filter { it.isNotBlank() }
                .toList()
        }
    val looksLikeLiquidDescriptorInput =
        liquidDescriptorLines.isNotEmpty() &&
            liquidDescriptorLines.all { it.lowercase().startsWith("ct(") }
    val normalizedLiquidDescriptor =
        remember(keyMaterial) {
            BitcoinUtils.normalizeLiquidDescriptorInput(trimmedInput)
        }
    val isLiquidCtDescriptor = normalizedLiquidDescriptor != null
    val liquidDescriptorError =
        when {
            liquidDescriptorLines.size > 1 && looksLikeLiquidDescriptorInput && normalizedLiquidDescriptor == null ->
                "Invalid Liquid descriptor pair. Paste one combined ct(...) descriptor or a matching /0/* and /1/* pair."
            looksLikeLiquidDescriptorInput &&
                normalizedLiquidDescriptor == null &&
                trimmedInput.endsWith(")") ->
                "Invalid Liquid descriptor."
            else -> null
        }

    val isWatchOnlyKey =
        isExtendedKey || isOriginPrefixed || isOutputDescriptor || isJsonFormat || looksLikeLiquidDescriptorInput
    val isWatchOnly =
        isWatchOnlyKey &&
            !trimmedInput.let {
                it.startsWith("xprv") || it.startsWith("yprv") ||
                    it.startsWith("zprv") ||
                    it.contains("xprv")
            }

    // Detect WIF private key (K/L/5 for mainnet)
    val isWifKey =
        remember(keyMaterial) {
            val t = keyMaterial.trim()
            val couldBeWif =
                (t.length == 52 && (t[0] == 'K' || t[0] == 'L')) ||
                    (t.length == 51 && t[0] == '5')
            if (!couldBeWif) {
                false
            } else {
                try {
                    // Validate Base58Check via BDK descriptor parse (fast, no network)
                    Descriptor("wpkh($t)", Network.BITCOIN)
                    true
                } catch (_: Exception) {
                    false
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
                        t.startsWith("bc1q") || t.startsWith("bc1p")
                if (!looksLikeAddress) {
                    false
                } else {
                    try {
                        org.bitcoindevkit.Address(t, Network.BITCOIN)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }
        }

    // Auto-detect address type for single addresses
    val detectedAddressType =
        remember(keyMaterial) {
            val t = keyMaterial.trim()
            when {
                t.startsWith("1") -> AddressType.LEGACY
                t.startsWith("bc1q") -> AddressType.SEGWIT
                t.startsWith("bc1p") -> AddressType.TAPROOT
                else -> null
            }
        }

    // Auto-switch address type when a single address is detected
    if (isBitcoinAddress && detectedAddressType != null && selectedAddressType != detectedAddressType) {
        selectedAddressType = detectedAddressType
    }

    // Detect the key prefix for the bare key (after origin bracket if present)
    // zpub signal SegWit derivation — Legacy is inappropriate
    val bareKeyPrefix =
        if (isOriginPrefixed) {
            trimmedInput.substringAfter("]").take(4)
        } else {
            trimmedInput.take(4)
        }
    val isSegwitVersionKey = bareKeyPrefix == "zpub"
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

    val suppressMnemonicValidation = isWifKey || isBitcoinAddress

    // Validate seed phrase word count
    val words =
        if (!isExtendedKey && !isWatchOnlyKey && !suppressMnemonicValidation) {
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
    val bip39WordList = remember { QrFormatParser.getWordlist(context) }
    val bip39WordSet = remember { bip39WordList.toSet() }
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
    val seedEntryStatus: Pair<String, Color>? =
        when {
            invalidWords.isNotEmpty() -> {
                val badWords = invalidWords.take(3).joinToString(", ") { "\"${it.second}\"" }
                val suffix = if (invalidWords.size > 3) " +${invalidWords.size - 3} more" else ""
                "Invalid word${if (invalidWords.size > 1) "s" else ""}: $badWords$suffix" to ErrorRed
            }
            isValidWordCount -> "$wordCount words entered" to TextSecondary
            wordCount in 1..11 -> "$wordCount ${if (wordCount == 1) "word" else "words"} entered" to TextSecondary
            wordCount > 11 -> "$wordCount words - need 12, 15, 18, 21, or 24" to ErrorRed
            else -> null
        }

    // Check if all words with 4+ chars have valid prefixes (for enabling mnemonic check)
    val allTypedWordsValid =
        words.isNotEmpty() && invalidWords.isEmpty() &&
            words.all { it in bip39WordSet }

    // Full mnemonic validation: try BIP39 first, then Electrum native seed detection.
    // Only runs when word count is valid and every word is a complete BIP39 word.
    val mnemonicValidation =
        remember(keyMaterial) {
            if (!isExtendedKey && !suppressMnemonicValidation && isValidWordCount && allTypedWordsValid) {
                try {
                    Mnemonic.fromString(keyMaterial.trim())
                    MnemonicValidation.Valid
                } catch (_: Exception) {
                    // BIP39 checksum failed — check if it's an Electrum native seed
                    val electrumType = ElectrumSeedUtil.getElectrumSeedType(keyMaterial.trim())
                    if (electrumType != null) {
                        MnemonicValidation.ValidElectrum(electrumType)
                    } else {
                        MnemonicValidation.Invalid("Invalid checksum")
                    }
                }
            } else {
                MnemonicValidation.NotChecked
            }
        }
    val isValidMnemonic = mnemonicValidation is MnemonicValidation.Valid ||
        mnemonicValidation is MnemonicValidation.ValidElectrum

    val isValidInput =
        unsupportedNonMainnetReason == null &&
            unsupportedNestedSegwitReason == null &&
            (isWatchOnlyKey || isExtendedKey || isValidMnemonic || isWifKey || isBitcoinAddress || isLiquidCtDescriptor)

    // Auto-generate wallet name based on input type with incremental suffix
    val autoWalletName =
        remember(selectedAddressType, existingWalletNames, isWifKey, isBitcoinAddress, isLiquidCtDescriptor) {
            val base =
                when {
                    isLiquidCtDescriptor -> "Liquid_WatchOnly"
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

    // Track whether address type is locked by Electrum seed detection
    val isElectrumSeed = mnemonicValidation is MnemonicValidation.ValidElectrum
    val electrumSeedType = (mnemonicValidation as? MnemonicValidation.ValidElectrum)?.seedType

    // Auto-select the correct address type when Electrum seed is detected
    LaunchedEffect(electrumSeedType) {
        when (electrumSeedType) {
            ElectrumSeedUtil.ElectrumSeedType.STANDARD -> selectedAddressType = AddressType.LEGACY
            ElectrumSeedUtil.ElectrumSeedType.SEGWIT -> selectedAddressType = AddressType.SEGWIT
            null -> { /* no change */ }
        }
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
            // For Electrum seeds, derive fingerprint using Electrum key stretching
            isElectrumSeed -> {
                derivedFingerprint =
                    withContext(Dispatchers.Default) {
                        try {
                            val pass = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                            val seed = ElectrumSeedUtil.mnemonicToSeed(keyMaterial.trim(), pass)
                            ElectrumSeedUtil.computeMasterFingerprint(seed)
                        } catch (_: Exception) {
                            null
                        }
                    }
            }
            // For valid BIP39 seed phrases, derive from mnemonic
            mnemonicValidation is MnemonicValidation.Valid -> {
                derivedFingerprint =
                    withContext(Dispatchers.Default) {
                        try {
                            val mnemonicObj = Mnemonic.fromString(keyMaterial.trim())
                            val pass = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                            val secretKey = DescriptorSecretKey(Network.BITCOIN, mnemonicObj, pass)
                            val descriptor =
                                when (selectedAddressType) {
                                    AddressType.LEGACY -> Descriptor.newBip44(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
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
    // QR Scanner Dialog - supports single-frame, animated UR, and all key formats
    if (showQrScanner) {
        ImportQrScannerDialog(
            preferredAddressType = selectedAddressType,
            onCodeScanned = { scannedText ->
                // The ImportQrScannerDialog already handles UR decoding internally.
                // For non-UR content, run through QrFormatParser for SeedQR/CompactSeedQR.
                try {
                    val parsedKeyMaterial = QrFormatParser.parseWalletQr(context, scannedText, selectedAddressType)
                    keyMaterialField =
                        TextFieldValue(
                            text = parsedKeyMaterial,
                            selection = TextRange(parsedKeyMaterial.length),
                        )
                    scannerError = null
                    showQrScanner = false
                } catch (e: IllegalArgumentException) {
                    scannerError = e.message ?: BitcoinUtils.UNSUPPORTED_NESTED_SEGWIT_MESSAGE
                    showQrScanner = false
                }
            },
            onDismiss = { showQrScanner = false },
        )
    }

    if (showBackupRestoreDialog && backupFileUri != null) {
        BackupRestoreDialog(
            fileName = backupFileName ?: "backup.json",
            backupParsedJson = backupParsedJson,
            backupIsEncrypted = backupIsEncrypted,
            backupPassword = backupPassword,
            onBackupPasswordChange = { backupPassword = it },
            showBackupPassword = showBackupPassword,
            onToggleShowBackupPassword = { showBackupPassword = !showBackupPassword },
            backupError = backupError,
            isParsingBackup = isParsingBackup,
            importServerSettings = importServerSettings,
            onImportServerSettingsChange = { importServerSettings = it },
            isLoading = isLoading,
            onDecrypt = {
                backupCoroutineScope.launch {
                    isParsingBackup = true
                    backupError = null
                    try {
                        val json = onParseBackupFile(backupFileUri!!, backupPassword)
                        backupParsedJson = json
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
            onConfirmRestore = {
                backupParsedJson?.let { json ->
                    onImportFromBackup(json, importServerSettings)
                }
            },
            onChooseDifferentFile = {
                showBackupRestoreDialog = false
                backupFilePickerLauncher.launch(arrayOf("application/json", "*/*"))
            },
            onDismiss = { showBackupRestoreDialog = false },
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
                    text = "Wallet Details",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                    AddressType.entries.forEach { addressType ->
                        val enabled =
                            !isElectrumSeed &&
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    seedEntryStatus?.let { (text, color) ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    SensitiveSeedIme {
                        OutlinedTextField(
                            value = keyMaterialField,
                            onValueChange = { input ->
                                scannerError = null
                                val trimmed = input.text.trim()
                                val isKeyOrDescriptor =
                                    trimmed.let { text ->
                                        text.startsWith("xpub") || text.startsWith("ypub") ||
                                            text.startsWith("zpub") || text.startsWith("upub") ||
                                            text.startsWith("xprv") || text.startsWith("yprv") ||
                                            text.startsWith("zprv") ||
                                            text.startsWith("[") || // Origin-prefixed key
                                            text.startsWith("{") || // JSON format (ColdCard, Specter)
                                            listOf("pkh(", "wpkh(", "tr(", "sh(", "ct(").any {
                                                text.lowercase().startsWith(it)
                                            } // Output descriptor
                                    }
                                // WIF private keys: K/L/5 (mainnet)
                                val isWifInput =
                                    trimmed.let { text ->
                                        (
                                            text.length <= 52 && text.isNotEmpty() &&
                                                (
                                                    text[0] == 'K' || text[0] == 'L' || text[0] == '5'
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
                                                    text.startsWith("bc1")
                                            )
                                    }
                                keyMaterialField =
                                    if (isKeyOrDescriptor || isWifInput || isAddressInput) {
                                        input // Preserve case for keys/descriptors/addresses (Base58/Bech32 encoded)
                                    } else {
                                        // Lowercase for seed phrases, then expand abbreviated BIP39
                                        // words (e.g. ColdCard exports first 4 letters of each word)
                                        val normalizedInput =
                                            QrFormatParser.expandAbbreviatedMnemonic(
                                            context,
                                            input.text.lowercase(),
                                        )
                                        input.copy(
                                            text = normalizedInput,
                                            selection =
                                                TextRange(
                                                    input.selection.end.coerceAtMost(normalizedInput.length),
                                                ),
                                        )
                                    }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                            shape = RoundedCornerShape(8.dp),
                            placeholder = {
                                Text(
                                    "BIP39 seed, Electrum seed, WIF private key, xpub/zpub, descriptor, or address",
                                    color = TextSecondary.copy(alpha = 0.5f),
                                )
                            },
                            keyboardOptions = sensitiveSeedKeyboardOptions(),
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

                Bip39SuggestionRow(
                    input = keyMaterial,
                    wordlist = bip39WordList,
                    onWordSelected = { completedInput ->
                        keyMaterialField =
                            TextFieldValue(
                                text = completedInput,
                                selection = TextRange(completedInput.length),
                            )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Input validation feedback
                if (keyMaterial.isNotBlank()) {
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
                            looksLikeLiquidDescriptorInput -> {
                                Text(
                                    text = "Liquid CT descriptor detected",
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
                            mnemonicValidation is MnemonicValidation.ValidElectrum -> {
                                val seedLabel = mnemonicValidation.seedType.label
                                Text(
                                    text = "Electrum $seedLabel seed ($wordCount words)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
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
                                    text = mnemonicValidation.error,
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
                                .bringIntoViewRequester(advancedOptionsBringIntoViewRequester)
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
                                            .heightIn(min = 40.dp)
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
                                    Column(modifier = Modifier.bringIntoViewRequester(customFingerprintBringIntoViewRequester)) {
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
                                    .heightIn(min = 40.dp)
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
                                text = "BIP39 Passphrase",
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
                            Column(modifier = Modifier.bringIntoViewRequester(passphraseBringIntoViewRequester)) {
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
                                            autoCorrectEnabled = false,
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
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { useCustomPath = !useCustomPath },
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
                            )
                            Text(
                                text = "Custom Derivation Path",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        // Custom path field
                        AnimatedVisibility(
                            visible = useCustomPath,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(customPathBringIntoViewRequester)) {
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

                        // Set Custom Gap Limit
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { useCustomGapLimit = !useCustomGapLimit },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = useCustomGapLimit,
                                onCheckedChange = { useCustomGapLimit = it },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = BitcoinOrange,
                                        uncheckedColor = TextSecondary,
                                    ),
                            )
                            Text(
                                text = "Custom Gap Limit",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        // Gap limit field
                        AnimatedVisibility(
                            visible = useCustomGapLimit,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(modifier = Modifier.bringIntoViewRequester(customGapLimitBringIntoViewRequester)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val gapLimitInt = gapLimitText.toIntOrNull()
                                val gapLimitValid = gapLimitText.isEmpty() || (gapLimitInt != null && gapLimitInt in 1..10000)
                                OutlinedTextField(
                                    value = gapLimitText,
                                    onValueChange = { value ->
                                        if (value.isEmpty() || value.all { it.isDigit() }) {
                                            gapLimitText = value
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                    placeholder = {
                                        Text(
                                            "${StoredWallet.DEFAULT_GAP_LIMIT}",
                                            color = TextSecondary.copy(alpha = 0.5f),
                                        )
                                    },
                                    singleLine = true,
                                    isError = gapLimitText.isNotEmpty() && !gapLimitValid,
                                    supportingText = {
                                        Text(
                                            "Default: ${StoredWallet.DEFAULT_GAP_LIMIT}. Scan limit for empty addresses (1–10000)",
                                            color = TextSecondary.copy(alpha = 0.5f),
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    }
                }
            }
        }

        val displayError =
            unsupportedNonMainnetReason ?: unsupportedNestedSegwitReason ?: liquidDescriptorError ?: scannerError ?: error
        if (displayError != null) {
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
                    text = displayError,
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
                val finalName = walletName.trim().ifBlank { autoWalletName }
                val customGapLimit =
                    if (useCustomGapLimit && gapLimitText.isNotBlank()) {
                        gapLimitText.toIntOrNull()?.coerceIn(1, 10000)
                            ?: StoredWallet.DEFAULT_GAP_LIMIT
                    } else {
                        StoredWallet.DEFAULT_GAP_LIMIT
                    }

                if (isLiquidCtDescriptor) {
                    onImportLiquidWatchOnly(finalName, normalizedLiquidDescriptor, customGapLimit)
                    return@Button
                }

                val passphraseValue = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                val customPathValue = if (useCustomPath && customPath.isNotBlank()) customPath else null

                val fingerprintValue =
                    parsedFingerprint
                        ?: if (useCustomFingerprint && masterFingerprint.length == 8) {
                            masterFingerprint
                        } else {
                            null
                                ?: if (isWatchOnly) "00000000" else null
                        }

                val seedFormat = when (electrumSeedType) {
                    ElectrumSeedUtil.ElectrumSeedType.STANDARD -> SeedFormat.ELECTRUM_STANDARD
                    ElectrumSeedUtil.ElectrumSeedType.SEGWIT -> SeedFormat.ELECTRUM_SEGWIT
                    null -> SeedFormat.BIP39
                }

                val config =
                    WalletImportConfig(
                        name = finalName,
                        keyMaterial = keyMaterial.trim(),
                        addressType = selectedAddressType,
                        passphrase = passphraseValue,
                        customDerivationPath = customPathValue,
                        network = WalletNetwork.BITCOIN,
                        isWatchOnly = isWatchOnly || isBitcoinAddress,
                        masterFingerprint = fingerprintValue,
                        seedFormat = seedFormat,
                        gapLimit = customGapLimit,
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
                    text = when {
                        isLiquidCtDescriptor -> "Import Liquid Watch-Only"
                        isBitcoinAddress -> "Watch Address"
                        isWifKey -> "Import Key"
                        else -> "Import Wallet"
                    },
                    style = MaterialTheme.typography.titleMedium,
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
            text = "Restore Wallet File",
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
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (backupFileUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { showBackupRestoreDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isParsingBackup,
            ) {
                Text(
                    text = if (isParsingBackup) "Reading backup file..." else "Review selected backup",
                    color = BitcoinOrange,
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
            Text(
                text = "Input WIF Key",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AddressTypeButton(
    addressType: AddressType,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
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

@Composable
private fun BackupRestoreDialog(
    fileName: String,
    backupParsedJson: JSONObject?,
    backupIsEncrypted: Boolean?,
    backupPassword: String,
    onBackupPasswordChange: (String) -> Unit,
    showBackupPassword: Boolean,
    onToggleShowBackupPassword: () -> Unit,
    backupError: String?,
    isParsingBackup: Boolean,
    importServerSettings: Boolean,
    onImportServerSettingsChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onDecrypt: () -> Unit,
    onChooseDifferentFile: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canDismiss = !isParsingBackup && !isLoading
    val scrollState = rememberScrollState()
    val walletObj = backupParsedJson?.optJSONObject("wallet")
    val labelsObj = backupParsedJson?.optJSONObject("labels")
    val serverSettingsObj = backupParsedJson?.optJSONObject("serverSettings")
    val hasServerSettings = serverSettingsObj != null

    Dialog(
        onDismissRequest = {
            if (canDismiss) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = canDismiss,
                dismissOnClickOutside = canDismiss,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkCard,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Restore Wallet File",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = canDismiss,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (canDismiss) TextSecondary else TextSecondary.copy(alpha = 0.4f),
                        )
                    }
                }

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isParsingBackup -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BitcoinOrange,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Reading backup file...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    }

                    backupParsedJson != null -> {
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Name: ${walletObj?.optString("name", "Unknown")}",
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

                                labelsObj?.let {
                                    val addressCount = it.optJSONObject("addresses")?.length() ?: 0
                                    val transactionCount = it.optJSONObject("transactions")?.length() ?: 0
                                    Text(
                                        text = "Labels: $addressCount address, $transactionCount transaction",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }

                                serverSettingsObj?.let {
                                    val serverCount = it.optJSONArray("electrumServers")?.length() ?: 0
                                    val liquidServerCount = it.optJSONArray("liquidServers")?.length() ?: 0
                                    val hasExplorerUrl =
                                        !it.optJSONObject("blockExplorer")?.optString("customUrl", "").isNullOrBlank()
                                    val hasFeeUrl =
                                        !it.optJSONObject("feeSource")?.optString("customUrl", "").isNullOrBlank()
                                    val hasLiquidExplorer =
                                        it.optString("liquidExplorer", "").isNotBlank() ||
                                            !it.optString("liquidExplorerCustomUrl", "").isNullOrBlank()
                                    val hasLiquidConnectivity =
                                        it.has("liquidTorEnabled") ||
                                            it.has("liquidAutoSwitch") ||
                                            it.has("liquidServerSelectedByUser")
                                    val parts = mutableListOf<String>()
                                    if (serverCount > 0) {
                                        parts.add("$serverCount Electrum server${if (serverCount != 1) "s" else ""}")
                                    }
                                    if (liquidServerCount > 0) {
                                        parts.add("$liquidServerCount Liquid server${if (liquidServerCount != 1) "s" else ""}")
                                    }
                                    if (hasExplorerUrl) {
                                        parts.add("block explorer")
                                    }
                                    if (hasFeeUrl) {
                                        parts.add("fee source")
                                    }
                                    if (hasLiquidExplorer) {
                                        parts.add("Liquid explorer")
                                    }
                                    if (hasLiquidConnectivity) {
                                        parts.add("Liquid connectivity")
                                    }
                                    Text(
                                        text = "Server settings: ${parts.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }

                        if (hasServerSettings) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImportServerSettingsChange(!importServerSettings) }
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Import Server Settings",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = "Electrum servers and external services",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                                SquareToggle(
                                    checked = importServerSettings,
                                    onCheckedChange = onImportServerSettingsChange,
                                )
                            }
                        }
                    }

                    backupIsEncrypted == true -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Encrypted backup",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BitcoinOrange,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Enter the password to decrypt and review this backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = onBackupPasswordChange,
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
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                            trailingIcon = {
                                IconButton(onClick = onToggleShowBackupPassword) {
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
                    }

                    backupError != null -> {
                        Text(
                            text = backupError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                        )
                    }
                }

                if (backupError != null && (backupParsedJson != null || backupIsEncrypted == true)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = backupError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    backupParsedJson != null -> {
                        IbisButton(
                            onClick = onConfirmRestore,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            enabled = !isLoading,
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
                                Text(
                                    text = "Restore Wallet",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }

                    backupIsEncrypted == true -> {
                        IbisButton(
                            onClick = onDecrypt,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            enabled = backupPassword.isNotEmpty(),
                        ) {
                            Text(
                                text = "Decrypt Backup",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onChooseDifferentFile,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && !isParsingBackup,
                ) {
                    Text(
                        text = "Choose Different File",
                        color = BitcoinOrange,
                    )
                }

                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canDismiss,
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun getDisplayNameFromUri(
    context: Context,
    uri: Uri,
): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }.getOrNull()
}

/**
 * Mnemonic validation state
 */
private sealed class MnemonicValidation {
    data object NotChecked : MnemonicValidation()

    /** Valid BIP39 seed phrase */
    data object Valid : MnemonicValidation()

    /** Valid Electrum native seed phrase */
    data class ValidElectrum(val seedType: ElectrumSeedUtil.ElectrumSeedType) : MnemonicValidation()

    data class Invalid(val error: String) : MnemonicValidation()
}
