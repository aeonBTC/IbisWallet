package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.bitcoindevkit.Mnemonic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    onImport: (config: WalletImportConfig) -> Unit,
    onImportFromBackup: (backupJson: JSONObject) -> Unit = {},
    onParseBackupFile: suspend (uri: Uri, password: String?) -> JSONObject = { _, _ -> JSONObject() },
    onBack: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null
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
    
    val backupFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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
    
    // Determine if input is seed phrase or extended public key
    val isExtendedKey = keyMaterial.trim().let { 
        it.startsWith("xpub") || it.startsWith("ypub") || 
        it.startsWith("zpub") || it.startsWith("tpub") ||
        it.startsWith("xprv") || it.startsWith("yprv") || 
        it.startsWith("zprv") || it.startsWith("tprv")
    }
    val isWatchOnly = keyMaterial.trim().let {
        it.startsWith("xpub") || it.startsWith("ypub") || 
        it.startsWith("zpub") || it.startsWith("tpub")
    }
    
    // Validate seed phrase word count
    val wordCount = if (!isExtendedKey) {
        keyMaterial.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    } else 0
    val isValidWordCount = wordCount in listOf(12, 15, 18, 21, 24)
    
    // Validate actual BIP39 mnemonic (checksum + wordlist) only when word count is valid
    val mnemonicValidation = remember(keyMaterial) {
        if (!isExtendedKey && isValidWordCount) {
            try {
                Mnemonic.fromString(keyMaterial.trim())
                MnemonicValidation.Valid
            } catch (e: Exception) {
                MnemonicValidation.Invalid(e.message ?: "Invalid mnemonic")
            }
        } else {
            MnemonicValidation.NotChecked
        }
    }
    val isValidMnemonic = mnemonicValidation is MnemonicValidation.Valid
    
    val isValidInput = isExtendedKey || isValidMnemonic
    val canImport = walletName.isNotBlank() && keyMaterial.isNotBlank() && isValidInput && !isLoading
    
    // Get derivation path to use
    val derivationPath = if (useCustomPath && customPath.isNotBlank()) {
        customPath
    } else {
        selectedAddressType.defaultPath
    }
    
    // QR Scanner Dialog
    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { scannedText ->
                keyMaterial = scannedText
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Import Wallet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Restore from Backup card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Restore from Backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Import a wallet from an Ibis Wallet backup file",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // File picker button
                OutlinedButton(
                    onClick = { backupFilePickerLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (backupFileUri != null) BitcoinOrange.copy(alpha = 0.5f) else BorderColor
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (backupFileUri != null) BitcoinOrange else TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = backupFileName ?: "Select backup file",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Parsing indicator
                if (isParsingBackup) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BitcoinOrange,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reading backup file...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                // Backup file info
                if (backupFileUri != null && !isParsingBackup) {
                    // Encrypted backup - show password field
                    if (backupIsEncrypted == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Encrypted backup - enter password to decrypt",
                                style = MaterialTheme.typography.bodySmall,
                                color = BitcoinOrange
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = { backupPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Backup Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = if (showBackupPassword) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showBackupPassword = !showBackupPassword }) {
                                    Icon(
                                        imageVector = if (showBackupPassword) Icons.Default.Visibility
                                            else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
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
                                        backupError = if (e.message?.contains("mac", ignoreCase = true) == true ||
                                            e.message?.contains("tag", ignoreCase = true) == true ||
                                            e.message?.contains("AEADBadTagException", ignoreCase = true) == true) {
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
                            shape = RoundedCornerShape(8.dp),
                            enabled = backupPassword.isNotEmpty(),
                            border = BorderStroke(
                                1.dp,
                                if (backupPassword.isNotEmpty()) BitcoinOrange.copy(alpha = 0.5f) else BorderColor
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BitcoinOrange,
                                disabledContentColor = TextSecondary.copy(alpha = 0.5f)
                            )
                        ) {
                            Text("Decrypt")
                        }
                    }
                    
                    // Successfully parsed - show wallet info and restore button
                    if (backupParsedJson != null && backupWalletName != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = SuccessGreen.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "Backup ready to restore",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuccessGreen
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val walletObj = backupParsedJson!!.optJSONObject("wallet")
                                Text(
                                    text = "Wallet: ${walletObj?.optString("name", "Unknown")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                val addressType = walletObj?.optString("addressType", "")
                                val isWatchOnly = walletObj?.optBoolean("isWatchOnly", false) == true
                                Text(
                                    text = "Type: $addressType${if (isWatchOnly) " (Watch Only)" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                
                                val hasLabels = backupParsedJson!!.optJSONObject("labels") != null
                                if (hasLabels) {
                                    val labelsObj = backupParsedJson!!.optJSONObject("labels")
                                    val addrCount = labelsObj?.optJSONObject("addresses")?.length() ?: 0
                                    val txCount = labelsObj?.optJSONObject("transactions")?.length() ?: 0
                                    Text(
                                        text = "Labels: $addrCount address, $txCount transaction",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                backupParsedJson?.let { json ->
                                    onImportFromBackup(json)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
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
                            color = ErrorRed
                        )
                    }
                    
                    // Unencrypted backup detected
                    if (backupIsEncrypted == false && backupParsedJson == null && backupError == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unencrypted backup detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Divider with "or" text
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderColor
            )
            Text(
                text = "  or import manually  ",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderColor
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
            
            // Main card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                ) {
                    // Wallet Name
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Wallet Name", color = TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Address Type Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AddressType.entries.forEach { addressType ->
                            AddressTypeButton(
                                addressType = addressType,
                                isSelected = selectedAddressType == addressType,
                                onClick = { selectedAddressType = addressType },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Address type description
                    Text(
                        text = selectedAddressType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Seed Phrase / Extended Key Input
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = keyMaterial,
                            onValueChange = {
                                keyMaterial = if (it.trim().let { text ->
                                    text.startsWith("xpub") || text.startsWith("ypub") ||
                                        text.startsWith("zpub") || text.startsWith("tpub") ||
                                        text.startsWith("xprv") || text.startsWith("yprv") ||
                                        text.startsWith("zprv") || text.startsWith("tprv")
                                }) {
                                    it // Preserve case for extended keys (Base58 encoded)
                                } else {
                                    it.lowercase() // Lowercase for seed phrases
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            label = { Text("Seed Phrase or Extended Public Key", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = BitcoinOrange
                            )
                        )
                        IconButton(
                            onClick = { showQrScanner = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Code",
                                tint = BitcoinOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // Input validation feedback
                    if (keyMaterial.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        when {
                            isWatchOnly -> {
                                Text(
                                    text = "Watch-only wallet (extended public key detected)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal
                                )
                            }
                            isExtendedKey && !isWatchOnly -> {
                                Text(
                                    text = "Extended private key detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BitcoinOrange
                                )
                            }
                            isValidWordCount -> {
                                // Valid seed phrase
                                Text(
                                    text = "Valid BIP39 seed phrase",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen
                                )
                            }
                            wordCount < 12 -> {
                                // Still typing, show neutral feedback
                                Text(
                                    text = "$wordCount ${if (wordCount == 1) "word" else "words"} entered",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            else -> {
                                // 12+ words but invalid count (13, 14, 16, 17, 19, 20, 22, 23, 25+)
                                Text(
                                    text = "$wordCount words - Must be 12, 15, 18, 21, or 24 words",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Advanced Options Header
                    Surface(
                        onClick = { showAdvancedOptions = !showAdvancedOptions },
                        color = DarkCard,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Advanced Options",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Icon(
                                imageVector = if (showAdvancedOptions) 
                                    Icons.Default.KeyboardArrowUp 
                                else 
                                    Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showAdvancedOptions) "Collapse" else "Expand",
                                tint = TextSecondary
                            )
                        }
                    }
                    
                    // Advanced Options Content
                    AnimatedVisibility(
                        visible = showAdvancedOptions,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            HorizontalDivider(color = BorderColor)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Use BIP39 Passphrase
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = usePassphrase,
                                    onCheckedChange = { usePassphrase = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BitcoinOrange,
                                        uncheckedColor = TextSecondary
                                    ),
                                    enabled = !isExtendedKey
                                )
                                Text(
                                    text = "Use BIP39 Passphrase",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isExtendedKey) TextSecondary.copy(alpha = 0.5f) 
                                           else MaterialTheme.colorScheme.onBackground
                                )
                            }
                            
                            // Passphrase field
                            AnimatedVisibility(
                                visible = usePassphrase && !isExtendedKey,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = passphrase,
                                        onValueChange = { passphrase = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                        placeholder = {
                                            Text(
                                                "Enter passphrase",
                                                color = TextSecondary.copy(alpha = 0.5f)
                                            )
                                        },
                                        visualTransformation = if (showPassphrase) 
                                            VisualTransformation.None 
                                        else 
                                            PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                                Icon(
                                                    imageVector = if (showPassphrase) 
                                                        Icons.Default.Visibility 
                                                    else 
                                                        Icons.Default.VisibilityOff,
                                                    contentDescription = null,
                                                    tint = TextSecondary
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BitcoinOrange,
                                            unfocusedBorderColor = BorderColor,
                                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            cursorColor = BitcoinOrange
                                        )
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Use Custom Derivation Path
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useCustomPath,
                                    onCheckedChange = { useCustomPath = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BitcoinOrange,
                                        uncheckedColor = TextSecondary
                                    ),
                                    enabled = !isExtendedKey
                                )
                                Text(
                                    text = "Use Custom Derivation Path",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isExtendedKey) TextSecondary.copy(alpha = 0.5f)
                                           else MaterialTheme.colorScheme.onBackground
                                )
                            }
                            
                            // Custom path field
                            AnimatedVisibility(
                                visible = useCustomPath && !isExtendedKey,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = customPath,
                                        onValueChange = { customPath = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                        placeholder = {
                                            Text(
                                                selectedAddressType.defaultPath,
                                                color = TextSecondary.copy(alpha = 0.5f)
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BitcoinOrange,
                                            unfocusedBorderColor = BorderColor,
                                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            cursorColor = BitcoinOrange
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Default: ${selectedAddressType.defaultPath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 12.dp)
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Import button
            OutlinedButton(
                onClick = {
                    val passphraseValue = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                    val customPathValue = if (useCustomPath && customPath.isNotBlank()) customPath else null

                    val config = WalletImportConfig(
                        name = walletName.trim(),
                        keyMaterial = keyMaterial.trim(),
                        addressType = selectedAddressType,
                        passphrase = passphraseValue,
                        customDerivationPath = customPathValue,
                        network = selectedNetwork,
                        isWatchOnly = isWatchOnly
                    )
                    onImport(config)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = canImport,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BitcoinOrange,
                    disabledContentColor = TextSecondary.copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (canImport) BitcoinOrange.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = BitcoinOrange,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Import Wallet")
                }
            }
            
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AddressTypeButton(
    addressType: AddressType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) BitcoinOrange else DarkSurfaceVariant
    val contentColor = if (isSelected) DarkBackground else TextSecondary
    val borderColor = if (isSelected) BitcoinOrange else BorderColor
    
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = addressType.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
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
