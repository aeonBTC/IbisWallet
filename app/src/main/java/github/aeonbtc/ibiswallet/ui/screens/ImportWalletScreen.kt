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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.MultisigScriptType
import github.aeonbtc.ibiswallet.data.model.MultisigWalletConfig
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
import github.aeonbtc.ibiswallet.ui.localization.descriptionText
import github.aeonbtc.ibiswallet.ui.localization.localizedTitle
import github.aeonbtc.ibiswallet.ui.localization.seedVariantLabel
import github.aeonbtc.ibiswallet.ui.localization.titleText
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
import github.aeonbtc.ibiswallet.util.InputLimits
import github.aeonbtc.ibiswallet.util.MultisigWalletParser
import github.aeonbtc.ibiswallet.util.QrFormatParser
import github.aeonbtc.ibiswallet.util.readBytesWithLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.json.JSONObject
import androidx.compose.material3.Text

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
    var showManualMultisigDialog by remember { mutableStateOf(false) }
    var manualMultisigLocalCosignerMaterial by remember { mutableStateOf<String?>(null) }
    var multisigLocalCosignerField by remember { mutableStateOf("") }
    var multisigLocalCosignerPassphrase by remember { mutableStateOf("") }
    var showMultisigLocalCosignerPassphrase by remember { mutableStateOf(false) }

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
    val backupParseFallbackError = stringResource(R.string.loc_53017e88)
    val walletConfigFileReadError = stringResource(R.string.loc_e61e3fea)
    val backupDecryptPasswordError = stringResource(R.string.loc_94b6dca8)
    val backupDecryptFallbackError = stringResource(R.string.loc_20968582)

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
                            backupError = e.message ?: backupParseFallbackError
                        }
                    } finally {
                        isParsingBackup = false
                    }
                }
            }
        }

    val walletConfigFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                backupCoroutineScope.launch {
                    try {
                        val content =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    stream.readBytesWithLimit(InputLimits.BACKUP_FILE_BYTES).toString(Charsets.UTF_8)
                                }.orEmpty()
                            }
                        keyMaterialField =
                            TextFieldValue(
                                text = content.trim(),
                                selection = TextRange(content.trim().length),
                            )
                        manualMultisigLocalCosignerMaterial = null
                        multisigLocalCosignerField = ""
                        multisigLocalCosignerPassphrase = ""
                        scannerError = null
                    } catch (_: Exception) {
                        scannerError = walletConfigFileReadError
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
        listOf("pkh(", "wpkh(", "tr(", "wsh(", "sh(wsh(").any {
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
    val multisigConfig =
        remember(keyMaterial) {
            if (MultisigWalletParser.looksLikeMultisig(trimmedInput)) {
                MultisigWalletParser.parse(trimmedInput)
            } else {
                null
            }
        }
    val isMultisigConfig = multisigConfig != null
    val importedMultisigLocalCosignerMaterial =
        remember(multisigConfig, multisigLocalCosignerField, multisigLocalCosignerPassphrase) {
            multisigConfig?.let {
                buildMultisigLocalCosignerMaterial(
                    config = it,
                    localCosignerKey = multisigLocalCosignerField,
                    localCosignerPassphrase = multisigLocalCosignerPassphrase,
                )
            }
        }
    val multisigLocalCosignerValid =
        multisigLocalCosignerField.isBlank() || importedMultisigLocalCosignerMaterial != null
    val liquidDescriptorError =
        when {
            liquidDescriptorLines.size > 1 && looksLikeLiquidDescriptorInput && normalizedLiquidDescriptor == null ->
                stringResource(R.string.loc_74b37443)
            looksLikeLiquidDescriptorInput &&
                normalizedLiquidDescriptor == null &&
                trimmedInput.endsWith(")") ->
                stringResource(R.string.loc_a8d202fe)
            else -> null
        }

    val isWatchOnlyKey =
        isExtendedKey || isOriginPrefixed || isOutputDescriptor || isJsonFormat ||
            looksLikeLiquidDescriptorInput || isMultisigConfig
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
                val moreCount = invalidWords.size - 3
                val moreSuffix =
                    if (moreCount > 0) {
                        pluralStringResource(R.plurals.import_seed_invalid_more, moreCount, moreCount)
                    } else {
                        ""
                    }
                val title =
                    pluralStringResource(
                        R.plurals.import_seed_invalid_title,
                        invalidWords.size,
                        invalidWords.size,
                    )
                stringResource(R.string.import_seed_invalid_line, title, badWords, moreSuffix) to ErrorRed
            }
            isValidWordCount -> pluralStringResource(R.plurals.import_seed_words_entered, wordCount, wordCount) to TextSecondary
            wordCount in 1..11 ->
                pluralStringResource(R.plurals.import_seed_words_entered, wordCount, wordCount) to TextSecondary
            wordCount > 11 ->
                stringResource(R.string.import_seed_wrong_word_count, wordCount) to ErrorRed
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
                        MnemonicValidation.Invalid
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
            multisigLocalCosignerValid &&
            (isWatchOnlyKey || isExtendedKey || isValidMnemonic || isWifKey || isBitcoinAddress || isLiquidCtDescriptor || isMultisigConfig)

    // Auto-generate wallet name based on input type with incremental suffix
    val addressTypeTitle = selectedAddressType.localizedTitle(context)
    val autoWalletBase =
        when {
            isMultisigConfig ->
                requireNotNull(multisigConfig).name ?: stringResource(R.string.loc_6dfe3462)
            isLiquidCtDescriptor -> stringResource(R.string.loc_c821b501)
            isBitcoinAddress ->
                stringResource(R.string.wallet_auto_name_watch_format, addressTypeTitle)
            isWifKey ->
                stringResource(R.string.wallet_auto_name_key_format, addressTypeTitle)
            else -> addressTypeTitle
        }
    val autoWalletName =
        remember(autoWalletBase, existingWalletNames) {
            val count =
                existingWalletNames.count { name ->
                    name == autoWalletBase ||
                        name.matches(Regex("${Regex.escape(autoWalletBase)}_(\\d+)"))
                }
            if (count == 0) autoWalletBase else "${autoWalletBase}_${count + 1}"
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
                    manualMultisigLocalCosignerMaterial = null
                    multisigLocalCosignerField = ""
                    multisigLocalCosignerPassphrase = ""
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
                                backupDecryptPasswordError
                            } else {
                                e.message ?: backupDecryptFallbackError
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

    if (showManualMultisigDialog) {
        ManualMultisigDialog(
            existingWalletName = walletName,
            onDismiss = { showManualMultisigDialog = false },
            onApply = { name, bsmsText, localCosignerMaterial ->
                if (walletName.isBlank() && name.isNotBlank()) {
                    walletName = name
                }
                manualMultisigLocalCosignerMaterial = localCosignerMaterial
                multisigLocalCosignerField = ""
                multisigLocalCosignerPassphrase = ""
                keyMaterialField =
                    TextFieldValue(
                        text = bsmsText,
                        selection = TextRange(bsmsText.length),
                    )
                scannerError = null
                showManualMultisigDialog = false
            },
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
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.loc_aeabc606),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Manual Import Section
        Text(
            text = stringResource(R.string.loc_7c2117d5),
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
                    text = stringResource(R.string.loc_748eece4),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.loc_6e9ddcf4),
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
                    text = stringResource(R.string.loc_3213841a),
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
                    text = selectedAddressType.descriptionText(),
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
                        text = stringResource(R.string.loc_9ae2cb2b),
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
                                manualMultisigLocalCosignerMaterial = null
                                multisigLocalCosignerField = ""
                                multisigLocalCosignerPassphrase = ""
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
                                    stringResource(R.string.loc_f986ec47),
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
                            contentDescription = stringResource(R.string.loc_59b2cdc5),
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
                            isMultisigConfig -> {
                                val multisig = requireNotNull(multisigConfig)
                                Column {
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.import_multisig_policy_line,
                                                multisig.policyLabel,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentTeal,
                                    )
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.import_wallet_cosigners_line,
                                                multisig.totalCosigners,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                            isJsonFormat -> {
                                Text(
                                    text = stringResource(R.string.loc_d5fabbff),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            looksLikeLiquidDescriptorInput -> {
                                Text(
                                    text = stringResource(R.string.loc_c43dbb42),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isOutputDescriptor -> {
                                Text(
                                    text =
                                        stringResource(
                                            if (hasEmbeddedFingerprint) {
                                                R.string.loc_26b5e032
                                            } else {
                                                R.string.loc_c329d1ef
                                            },
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isOriginPrefixed -> {
                                Text(
                                    text = stringResource(R.string.loc_c8579cd6),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isWatchOnly -> {
                                Column {
                                    Text(
                                        text = stringResource(R.string.loc_9dec0f67),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentTeal,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (isExtendedKey && !hasEmbeddedFingerprint) {
                                        Text(
                                            text = stringResource(R.string.loc_daea11b1),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }
                            }
                            isWifKey -> {
                                Text(
                                    text = stringResource(R.string.loc_517ece75),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                )
                            }
                            isBitcoinAddress -> {
                                Text(
                                    text = stringResource(R.string.loc_541ee340),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                )
                            }
                            isExtendedKey && !isWatchOnly -> {
                                Text(
                                    text = stringResource(R.string.loc_85e5fba3),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange,
                                )
                            }
                            mnemonicValidation is MnemonicValidation.ValidElectrum -> {
                                val seedLabel = mnemonicValidation.seedType.seedVariantLabel()
                                Text(
                                    text =
                                        stringResource(
                                            R.string.common_electrum_seed_format,
                                            seedLabel,
                                            wordCount,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                )
                            }
                            isValidMnemonic -> {
                                Text(
                                    text = stringResource(R.string.loc_e7f0104f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                )
                            }
                            isValidWordCount && mnemonicValidation is MnemonicValidation.Invalid -> {
                                Text(
                                    text = stringResource(R.string.loc_964abaa1),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                )
                            }
                        }
                        derivedFingerprint?.let { fp ->
                            Text(
                                text = stringResource(R.string.common_fingerprint_format, fp),
                                style = MaterialTheme.typography.bodySmall,
                                color = BitcoinOrange,
                            )
                        }
                    }
                }
            }
        }

        if (isMultisigConfig) {
            Spacer(modifier = Modifier.height(8.dp))
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
                    Text(
                        text = stringResource(R.string.loc_9589b41e),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.loc_b848af0e),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = multisigLocalCosignerField,
                        onValueChange = { multisigLocalCosignerField = it },
                        label = { Text(stringResource(R.string.loc_7a4499bf)) },
                        placeholder = {
                            Text(
                                stringResource(R.string.loc_796b4a85),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        minLines = 2,
                        isError = multisigLocalCosignerField.isNotBlank() && !multisigLocalCosignerValid,
                        supportingText = {
                            val attached =
                                manualMultisigLocalCosignerMaterial != null ||
                                    importedMultisigLocalCosignerMaterial != null ||
                                    keyMaterial.contains("prv", ignoreCase = true)
                            Text(
                                text =
                                    when {
                                        multisigLocalCosignerField.isNotBlank() && !multisigLocalCosignerValid ->
                                            stringResource(R.string.loc_c6354927)
                                        attached -> stringResource(R.string.loc_cc38eb35)
                                        else -> stringResource(R.string.loc_711aadc6)
                                    },
                                color =
                                    when {
                                        multisigLocalCosignerField.isNotBlank() && !multisigLocalCosignerValid -> ErrorRed
                                        attached -> SuccessGreen
                                        else -> TextSecondary
                                    },
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AnimatedVisibility(
                        visible = multisigLocalCosignerField.trim().split("\\s+".toRegex()).size in 12..24,
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = multisigLocalCosignerPassphrase,
                                onValueChange = { multisigLocalCosignerPassphrase = it },
                                label = { Text(stringResource(R.string.loc_d271ea89)) },
                                singleLine = true,
                                visualTransformation =
                                    if (showMultisigLocalCosignerPassphrase) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            showMultisigLocalCosignerPassphrase = !showMultisigLocalCosignerPassphrase
                                        },
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (showMultisigLocalCosignerPassphrase) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
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
                                modifier = Modifier.fillMaxWidth(),
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
                            text = stringResource(R.string.loc_20a1d916),
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
                            contentDescription =
                                if (showAdvancedOptions) {
                                    stringResource(R.string.loc_729b34d2)
                                } else {
                                    stringResource(R.string.loc_b47e7391)
                                },
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
                                        text = stringResource(R.string.loc_a7557fbf),
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
                                                    stringResource(R.string.loc_70352f41),
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
                                                text = stringResource(R.string.loc_161165a5),
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
                                text = stringResource(R.string.loc_75923525),
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
                                            stringResource(R.string.loc_e8a4f395),
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
                                text = stringResource(R.string.loc_01fca34c),
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
                                    text =
                                        stringResource(
                                            R.string.common_default_format,
                                            selectedAddressType.defaultPath,
                                        ),
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
                                text = stringResource(R.string.loc_894dedef),
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
                                            stringResource(R.string.loc_87c9f231),
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
                        multisigConfig = multisigConfig,
                        localCosignerKeyMaterial =
                            manualMultisigLocalCosignerMaterial
                                ?: importedMultisigLocalCosignerMaterial
                                ?: if (isMultisigConfig && keyMaterial.contains("prv", ignoreCase = true)) {
                                keyMaterial.trim()
                            } else {
                                null
                            },
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
                    text =
                        when {
                            isMultisigConfig -> stringResource(R.string.loc_638eb802)
                            isLiquidCtDescriptor -> stringResource(R.string.loc_a016ef65)
                            isBitcoinAddress -> stringResource(R.string.loc_fd030147)
                            isWifKey -> stringResource(R.string.loc_5ab1fed3)
                            else -> stringResource(R.string.loc_aeabc606)
                        },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.loc_255d09fd),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.loc_7999830a),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IbisButton(
                onClick = { showManualMultisigDialog = true },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.multisig_manual_option),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            IbisButton(
                onClick = { walletConfigFilePickerLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.multisig_config_file_option),
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
                text = stringResource(R.string.loc_1db77587),
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
            text = stringResource(R.string.loc_0e42bd4f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.loc_0eeb7864),
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
                text = backupFileName ?: stringResource(R.string.loc_e4783294),
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
                    text =
                        if (isParsingBackup) {
                            stringResource(R.string.loc_5bc64b9b)
                        } else {
                            stringResource(R.string.loc_ab6580da)
                        },
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
                text = stringResource(R.string.loc_1db77587),
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
            text = stringResource(R.string.loc_380cec3b),
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
                text = stringResource(R.string.loc_ba4c3466),
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
                text = addressType.titleText(),
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
    val unknownName = stringResource(R.string.loc_629b9e5b)
    val watchOnlySuffix = stringResource(R.string.loc_25d748f3)

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
                        text = stringResource(R.string.loc_0e42bd4f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = canDismiss,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.loc_d2c0aec0),
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
                                text = stringResource(R.string.loc_5bc64b9b),
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
                                    text = stringResource(R.string.loc_3de64ebf),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuccessGreen,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.loc_a422f393,
                                            walletObj?.optString("name")?.takeIf { it.isNotBlank() }
                                                ?: unknownName,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                                val addressType = walletObj?.optString("addressType", "") ?: ""
                                val isWatchOnly = walletObj?.optBoolean("isWatchOnly", false) == true
                                val resolvedAddressType =
                                    addressType.takeIf { it.isNotBlank() } ?: unknownName
                                Text(
                                    text =
                                        if (isWatchOnly) {
                                            stringResource(
                                                R.string.import_wallet_address_line_watch,
                                                resolvedAddressType,
                                                watchOnlySuffix,
                                            )
                                        } else {
                                            resolvedAddressType
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )

                                labelsObj?.let {
                                    val addressCount = it.optJSONObject("addresses")?.length() ?: 0
                                    val transactionCount = it.optJSONObject("transactions")?.length() ?: 0
                                    val addrPart =
                                        pluralStringResource(
                                            R.plurals.import_backup_label_address_count,
                                            addressCount,
                                            addressCount,
                                        )
                                    val txPart =
                                        pluralStringResource(
                                            R.plurals.import_backup_label_transaction_count,
                                            transactionCount,
                                            transactionCount,
                                        )
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.import_backup_labels_line,
                                                addrPart,
                                                txPart,
                                            ),
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
                                    val blockExplorerLabel = stringResource(R.string.loc_929a5c05)
                                    val feeSourceLabel = stringResource(R.string.loc_90799139)
                                    val liquidExplorerLabel = stringResource(R.string.loc_52640f61)
                                    val liquidConnectivityLabel = stringResource(R.string.loc_b61cfdd3)
                                    val parts = mutableListOf<String>()
                                    if (serverCount > 0) {
                                        parts.add(
                                            pluralStringResource(
                                                R.plurals.import_backup_electrum_servers,
                                                serverCount,
                                                serverCount,
                                            ),
                                        )
                                    }
                                    if (liquidServerCount > 0) {
                                        parts.add(
                                            pluralStringResource(
                                                R.plurals.import_backup_liquid_servers,
                                                liquidServerCount,
                                                liquidServerCount,
                                            ),
                                        )
                                    }
                                    if (hasExplorerUrl) {
                                        parts.add(blockExplorerLabel)
                                    }
                                    if (hasFeeUrl) {
                                        parts.add(feeSourceLabel)
                                    }
                                    if (hasLiquidExplorer) {
                                        parts.add(liquidExplorerLabel)
                                    }
                                    if (hasLiquidConnectivity) {
                                        parts.add(liquidConnectivityLabel)
                                    }
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.import_backup_server_settings_line,
                                                parts.joinToString(", "),
                                            ),
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
                                        text = stringResource(R.string.loc_82807e0f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = stringResource(R.string.loc_997e26df),
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
                                text = stringResource(R.string.loc_867cb15f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BitcoinOrange,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.loc_bf01e3a2),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = onBackupPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.loc_1cdedba2), color = TextSecondary) },
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
                                    text = stringResource(R.string.loc_7a2478a8),
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
                                text = stringResource(R.string.loc_f73683e2),
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
                        text = stringResource(R.string.loc_e18f112a),
                        color = BitcoinOrange,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(8.dp))

                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = canDismiss,
                ) {
                    Text(stringResource(R.string.loc_51bac044), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ManualMultisigDialog(
    existingWalletName: String,
    onDismiss: () -> Unit,
    onApply: (name: String, bsmsText: String, localCosignerMaterial: String?) -> Unit,
) {
    var name by remember { mutableStateOf(existingWalletName.ifBlank { "Multisig" }) }
    var threshold by remember { mutableStateOf("2") }
    var derivationPath by remember { mutableStateOf("m/48'/0'/0'/2'") }
    var localCosignerKey by remember { mutableStateOf("") }
    var localCosignerPassphrase by remember { mutableStateOf("") }
    var showLocalCosignerPassphrase by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var cosigners by remember {
        mutableStateOf(
            """
            aaaaaaaa: xpub...
            bbbbbbbb: xpub...
            cccccccc: xpub...
            """.trimIndent(),
        )
    }

    val cosignerLines =
        cosigners.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
    val thresholdInt = threshold.toIntOrNull()
    val fingerprintRegex = Regex("""^[0-9a-fA-F]{8}\s*:\s*[xyz]pub.+""")
    val hasValidCosigners = cosignerLines.size >= 2 && cosignerLines.all { it.matches(fingerprintRegex) }
    val localCosignerMaterial =
        remember(threshold, derivationPath, localCosignerKey, localCosignerPassphrase, cosigners) {
            buildManualMultisigLocalCosignerMaterial(
                threshold = thresholdInt ?: 1,
                derivationPath = derivationPath,
                cosignerLines = cosignerLines,
                localCosignerKey = localCosignerKey,
                localCosignerPassphrase = localCosignerPassphrase,
            )
        }
    val localCosignerValid = localCosignerKey.isBlank() || localCosignerMaterial != null
    val canApply =
        name.isNotBlank() &&
            thresholdInt != null &&
            thresholdInt in 1..cosignerLines.size &&
            hasValidCosigners &&
            localCosignerValid

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.loc_68a4806d),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.loc_fe11d138)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = BitcoinOrange,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.all(Char::isDigit)) {
                                threshold = value
                            }
                        },
                        label = { Text(stringResource(R.string.loc_698ba835)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = derivationPath,
                        onValueChange = { derivationPath = it },
                        label = { Text(stringResource(R.string.loc_ab662431)) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        modifier = Modifier.weight(2f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = cosigners,
                    onValueChange = { cosigners = it },
                    label = { Text(stringResource(R.string.loc_9054140b)) },
                    placeholder = { Text(stringResource(R.string.loc_2a428c88), color = TextSecondary.copy(alpha = 0.5f)) },
                    minLines = 4,
                    isError = cosigners.isNotBlank() && !hasValidCosigners,
                    supportingText = {
                        Text(
                            text = stringResource(R.string.loc_810fe008),
                            color = if (hasValidCosigners) TextSecondary else ErrorRed,
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = BitcoinOrange,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = localCosignerKey,
                    onValueChange = { localCosignerKey = it },
                    label = { Text(stringResource(R.string.loc_7db8bd23)) },
                    placeholder = {
                        Text(
                            stringResource(R.string.loc_d44eca4c),
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    minLines = 2,
                    isError = localCosignerKey.isNotBlank() && !localCosignerValid,
                    supportingText = {
                        Text(
                            text = stringResource(R.string.loc_5237cff5),
                            color = if (localCosignerValid) TextSecondary else ErrorRed,
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            cursorColor = BitcoinOrange,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(visible = localCosignerKey.trim().split("\\s+".toRegex()).size in 12..24) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = localCosignerPassphrase,
                            onValueChange = { localCosignerPassphrase = it },
                            label = { Text(stringResource(R.string.loc_d271ea89)) },
                            singleLine = true,
                            visualTransformation =
                                if (showLocalCosignerPassphrase) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            trailingIcon = {
                                IconButton(onClick = { showLocalCosignerPassphrase = !showLocalCosignerPassphrase }) {
                                    Icon(
                                        imageVector =
                                            if (showLocalCosignerPassphrase) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.loc_51bac044), color = TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            onApply(
                                name.trim(),
                                buildManualMultisigBsms(
                                    name = name.trim(),
                                    threshold = thresholdInt ?: 1,
                                    derivationPath = derivationPath.trim(),
                                    cosignerLines = cosignerLines,
                                ),
                                localCosignerMaterial,
                            )
                        },
                        enabled = canApply,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_acfc8aab),
                            color = if (canApply) BitcoinOrange else TextSecondary.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

private fun buildManualMultisigBsms(
    name: String,
    threshold: Int,
    derivationPath: String,
    cosignerLines: List<String>,
): String =
    buildString {
        appendLine("BSMS 1.0")
        appendLine("Name: $name")
        appendLine("Policy: $threshold of ${cosignerLines.size}")
        appendLine("Derivation: $derivationPath")
        appendLine("Format: P2WSH")
        cosignerLines.forEach { appendLine(it) }
    }.trimEnd()

private fun buildManualMultisigLocalCosignerMaterial(
    threshold: Int,
    derivationPath: String,
    cosignerLines: List<String>,
    localCosignerKey: String,
    localCosignerPassphrase: String,
): String? {
    val trimmedKey = localCosignerKey.trim()
    if (trimmedKey.isBlank()) return null
    if (MultisigWalletParser.looksLikeMultisig(trimmedKey)) {
        return trimmedKey
    }

    val localMatch = Regex("""^\[([0-9a-fA-F]{8})/(.+?)](xprv[1-9A-HJ-NP-Za-km-z]+)$""")
        .find(trimmedKey)
    val normalizedPath = derivationPath.trim()
    val localKey =
        if (localMatch != null) {
            ManualMultisigLocalKey(
                fingerprint = localMatch.groupValues[1].lowercase(),
                path = localMatch.groupValues[2].trim(),
                xprv = localMatch.groupValues[3].trim(),
            )
        } else {
            try {
                Mnemonic.fromString(trimmedKey)
                val seed =
                    ElectrumSeedUtil.bip39MnemonicToSeed(
                        trimmedKey,
                        localCosignerPassphrase.takeIf { it.isNotBlank() },
                    )
                ManualMultisigLocalKey(
                    fingerprint = ElectrumSeedUtil.computeMasterFingerprint(seed),
                    path = normalizedPath.removePrefix("m/").removePrefix("/"),
                    xprv = ElectrumSeedUtil.deriveXprv(seed, normalizedPath),
                )
            } catch (_: Exception) {
                return null
            }
        }
    val cosignerRegex = Regex("""^\s*([0-9a-fA-F]{8})\s*:\s*([xyz]pub[1-9A-HJ-NP-Za-km-z]+)\s*$""")
    val parsedCosigners =
        cosignerLines.map { line ->
            val match = cosignerRegex.find(line) ?: return null
            match.groupValues[1].lowercase() to match.groupValues[2]
        }
    if (parsedCosigners.none { it.first == localKey.fingerprint }) return null

    fun keyExpression(
        fingerprint: String,
        xpub: String,
        branch: Int,
    ): String =
        if (fingerprint == localKey.fingerprint) {
            "[${localKey.fingerprint}/${localKey.path}]${localKey.xprv}/$branch/*"
        } else {
            "[$fingerprint/${normalizedPath.removePrefix("m/").removePrefix("/")}]$xpub/$branch/*"
        }

    fun descriptor(branch: Int): String {
        val keys = parsedCosigners.joinToString(",") { (fingerprint, xpub) ->
            keyExpression(fingerprint, xpub, branch)
        }
        return "wsh(sortedmulti($threshold,$keys))"
    }

    return "${descriptor(0)}\n${descriptor(1)}"
}

private fun buildMultisigLocalCosignerMaterial(
    config: MultisigWalletConfig,
    localCosignerKey: String,
    localCosignerPassphrase: String,
): String? {
    val trimmedKey = localCosignerKey.trim()
    if (trimmedKey.isBlank()) return null
    if (MultisigWalletParser.looksLikeMultisig(trimmedKey)) {
        return trimmedKey.takeIf { privateMultisigDescriptorMatchesConfig(it, config) }
    }

    val originXprvMatch = Regex("""^\[([0-9a-fA-F]{8})/(.+?)](xprv[1-9A-HJ-NP-Za-km-z]+)$""")
        .find(trimmedKey)
    val localKey =
        if (originXprvMatch != null) {
            ManualMultisigLocalKey(
                fingerprint = originXprvMatch.groupValues[1].lowercase(),
                path = originXprvMatch.groupValues[2].trim(),
                xprv = originXprvMatch.groupValues[3].trim(),
            )
        } else {
            try {
                Mnemonic.fromString(trimmedKey)
                val seed =
                    ElectrumSeedUtil.bip39MnemonicToSeed(
                        trimmedKey,
                        localCosignerPassphrase.takeIf { it.isNotBlank() },
                    )
                val fingerprint = ElectrumSeedUtil.computeMasterFingerprint(seed)
                val cosigner = config.cosigners.firstOrNull { it.fingerprint.equals(fingerprint, ignoreCase = true) }
                    ?: return null
                val path = cosigner.derivationPath.removePrefix("m/").removePrefix("/")
                ManualMultisigLocalKey(
                    fingerprint = fingerprint,
                    path = path,
                    xprv = ElectrumSeedUtil.deriveXprv(seed, "m/$path"),
                )
            } catch (_: Exception) {
                return null
            }
        }
    if (config.cosigners.none { it.fingerprint.equals(localKey.fingerprint, ignoreCase = true) }) return null

    fun keyExpression(
        fingerprint: String,
        derivationPath: String,
        xpub: String,
        branch: Int,
    ): String {
        val normalizedPath = derivationPath.removePrefix("m/").removePrefix("/")
        return if (fingerprint.equals(localKey.fingerprint, ignoreCase = true)) {
            "[${localKey.fingerprint}/${localKey.path}]${localKey.xprv}/$branch/*"
        } else {
            "[${fingerprint.lowercase()}/$normalizedPath]${BitcoinUtils.convertToXpub(xpub)}/$branch/*"
        }
    }

    fun descriptor(branch: Int): String {
        val functionName = if (config.isSorted) "sortedmulti" else "multi"
        val keys = config.cosigners.joinToString(",") { cosigner ->
            keyExpression(
                fingerprint = cosigner.fingerprint,
                derivationPath = cosigner.derivationPath,
                xpub = cosigner.xpub,
                branch = branch,
            )
        }
        val inner = "$functionName(${config.threshold},$keys)"
        return when (config.scriptType) {
            MultisigScriptType.P2WSH -> "wsh($inner)"
            MultisigScriptType.P2SH_P2WSH -> "sh(wsh($inner))"
        }
    }

    return "${descriptor(0)}\n${descriptor(1)}"
}

private fun privateMultisigDescriptorMatchesConfig(
    descriptor: String,
    config: MultisigWalletConfig,
): Boolean {
    val localConfig = MultisigWalletParser.parse(descriptor) ?: return false
    if (localConfig.threshold != config.threshold) return false
    if (localConfig.totalCosigners != config.totalCosigners) return false
    if (localConfig.scriptType != config.scriptType) return false
    val expectedFingerprints = config.cosigners.map { it.fingerprint.lowercase() }.toSet()
    val localFingerprints = localConfig.cosigners.map { it.fingerprint.lowercase() }.toSet()
    return expectedFingerprints == localFingerprints
}

private data class ManualMultisigLocalKey(
    val fingerprint: String,
    val path: String,
    val xprv: String,
)

/**
 * Mnemonic validation state
 */
private sealed class MnemonicValidation {
    data object NotChecked : MnemonicValidation()

    /** Valid BIP39 seed phrase */
    data object Valid : MnemonicValidation()

    /** Valid Electrum native seed phrase */
    data class ValidElectrum(val seedType: ElectrumSeedUtil.ElectrumSeedType) : MnemonicValidation()

    data object Invalid : MnemonicValidation()
}
