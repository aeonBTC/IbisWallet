package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.*

data class WalletInfo(
    val id: String,
    val name: String,
    val type: String,
    val typeDescription: String,
    val derivationPath: String,
    val isActive: Boolean = false,
    val isWatchOnly: Boolean = false,
    val lastFullSyncTime: Long? = null
)

/**
 * Data class for key material display
 * For full wallets: mnemonic is set, xpub is derived
 * For watch-only wallets: only xpub is set
 */
data class KeyMaterialInfo(
    val walletName: String,
    val mnemonic: String?,       // Seed phrase (null for watch-only)
    val extendedPublicKey: String?, // xpub/zpub (always available)
    val isWatchOnly: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletsScreen(
    wallets: List<WalletInfo>,
    onBack: () -> Unit,
    onImportWallet: () -> Unit,
    onViewWallet: (WalletInfo) -> KeyMaterialInfo?,
    onDeleteWallet: (WalletInfo) -> Unit,
    onSelectWallet: (WalletInfo) -> Unit,
    onExportWallet: (walletId: String, uri: Uri, includeLabels: Boolean, password: String?) -> Unit = { _, _, _, _ -> },
    onFullSync: (WalletInfo) -> Unit = {},
    syncingWalletId: String? = null
) {
    var walletToDelete by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToView by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToSync by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToExport by remember { mutableStateOf<WalletInfo?>(null) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var keyMaterialInfo by remember { mutableStateOf<KeyMaterialInfo?>(null) }
    var showKeyMaterial by remember { mutableStateOf(false) }
    
    // Delete confirmation dialog
    if (walletToDelete != null) {
        AlertDialog(
            onDismissRequest = { walletToDelete = null },
            title = { 
                Text(
                    "Delete Wallet",
                    color = MaterialTheme.colorScheme.onBackground
                ) 
            },
            text = { 
                Text(
                    "Are you sure you want to delete \"${walletToDelete?.name}\"? This action cannot be undone. Make sure you have backed up your recovery phrase.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        walletToDelete?.let { onDeleteWallet(it) }
                        walletToDelete = null
                    }
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
    
    // Warning dialog before showing seed phrase
    if (showWarningDialog && walletToView != null) {
        AlertDialog(
            onDismissRequest = { 
                showWarningDialog = false
                walletToView = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { 
                Text(
                    "Security Warning",
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                ) 
            },
            text = { 
                Column {
                    Text(
                        "You are about to view sensitive information for \"${walletToView?.name}\".",
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your seed phrase is the ONLY way to recover your wallet. Anyone with access to it can steal your funds.",
                        color = ErrorRed
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Make sure no one is watching your screen and never share this information with anyone.",
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog = false
                        // Get the key material
                        walletToView?.let { wallet ->
                            val info = onViewWallet(wallet)
                            if (info != null) {
                                keyMaterialInfo = info
                                showKeyMaterial = true
                            }
                        }
                        walletToView = null
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BitcoinOrangeLight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange
                    )
                ) {
                    Text("I Understand, Show Me")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showWarningDialog = false
                        walletToView = null
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
    
    // Full Sync confirmation dialog
    if (walletToSync != null) {
        AlertDialog(
            onDismissRequest = { walletToSync = null },
            title = { 
                Text(
                    "Full Sync",
                    color = MaterialTheme.colorScheme.onBackground
                ) 
            },
            text = { 
                Text(
                    "This will rescan all addresses for transactions. Use this if missing transactions or after backup restore.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        walletToSync?.let { onFullSync(it) }
                        walletToSync = null
                    }
                ) {
                    Text("Full Sync", color = BitcoinOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToSync = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
    
    // Key material display dialog
    if (showKeyMaterial && keyMaterialInfo != null) {
        KeyMaterialDialog(
            keyMaterialInfo = keyMaterialInfo!!,
            onDismiss = {
                showKeyMaterial = false
                keyMaterialInfo = null
            }
        )
    }
    
    // Export wallet dialog
    if (walletToExport != null) {
        ExportWalletDialog(
            wallet = walletToExport!!,
            onDismiss = { walletToExport = null },
            onExport = { uri, includeLabels, password ->
                walletToExport?.let { wallet ->
                    onExportWallet(wallet.id, uri, includeLabels, password)
                }
                walletToExport = null
            }
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
                text = "Manage Wallets",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Import wallet button at top
        OutlinedButton(
            onClick = onImportWallet,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BitcoinOrange
            ),
            border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Wallet")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (wallets.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Wallets",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import a wallet to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Wallet list
            wallets.forEachIndexed { index, wallet ->
                WalletCard(
                    wallet = wallet,
                    onView = { 
                        walletToView = wallet
                        showWarningDialog = true
                    },
                    onDelete = { walletToDelete = wallet },
                    onExport = { walletToExport = wallet },
                    onClick = { onSelectWallet(wallet) },
                    onSync = { walletToSync = wallet },
                    isSyncing = syncingWalletId == wallet.id
                )
                if (index < wallets.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Enum for key material view type selection
 */
private enum class KeyViewType {
    SEED_PHRASE,
    EXTENDED_PUBLIC_KEY
}

@Composable
private fun ExportWalletDialog(
    wallet: WalletInfo,
    onDismiss: () -> Unit,
    onExport: (uri: Uri, includeLabels: Boolean, password: String?) -> Unit
) {
    var includeLabels by remember { mutableStateOf(true) }
    var encryptBackup by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var exportFileName by remember { mutableStateOf<String?>(null) }
    
    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val safeName = wallet.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
    val suggestedFileName = "ibis-backup-${safeName}-${dateStr}.json"
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            exportUri = uri
            exportFileName = uri.lastPathSegment?.substringAfterLast('/') ?: suggestedFileName
        }
    }
    
    val passwordsMatch = password == confirmPassword
    val passwordLongEnough = password.length >= 8
    val encryptionValid = !encryptBackup || (passwordLongEnough && passwordsMatch)
    val canExport = exportUri != null && encryptionValid
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Export Wallet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Exporting \"${wallet.name}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Warning
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (wallet.isWatchOnly) {
                                "This backup contains your extended public key. Store it securely."
                            } else {
                                "This backup contains your seed phrase. Anyone with access to it can steal your funds. Store it securely."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Include Labels toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Labels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Address and transaction labels",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    SquareToggle(
                        checked = includeLabels,
                        onCheckedChange = { includeLabels = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Encrypt Backup toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (encryptBackup) BitcoinOrange else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Encrypt Backup",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "AES-256 password protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    SquareToggle(
                        checked = encryptBackup,
                        onCheckedChange = { encryptBackup = it }
                    )
                }
                
                // Password fields (animated)
                AnimatedVisibility(
                    visible = encryptBackup,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.Visibility
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
                        
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                                errorBorderColor = ErrorRed
                            )
                        )
                        
                        // Validation hints
                        if (password.isNotEmpty() && !passwordLongEnough) {
                            Text(
                                text = "Minimum 8 characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                            Text(
                                text = "Passwords don't match",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Save Location
                Text(
                    text = "Save Location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(suggestedFileName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (exportUri != null) SuccessGreen.copy(alpha = 0.5f) else BorderColor
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (exportUri != null) SuccessGreen else TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = exportFileName ?: "Choose location",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            exportUri?.let { uri ->
                                onExport(
                                    uri,
                                    includeLabels,
                                    if (encryptBackup) password else null
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = canExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                            disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyMaterialDialog(
    keyMaterialInfo: KeyMaterialInfo,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var selectedViewType by remember { mutableStateOf<KeyViewType?>(null) }
    var showQrCode by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val isWatchOnly = keyMaterialInfo.isWatchOnly
    val hasSeedPhrase = keyMaterialInfo.mnemonic != null
    val hasXpub = keyMaterialInfo.extendedPublicKey != null
    
    // Current key material based on selection
    val currentKeyMaterial = when (selectedViewType) {
        KeyViewType.SEED_PHRASE -> keyMaterialInfo.mnemonic
        KeyViewType.EXTENDED_PUBLIC_KEY -> keyMaterialInfo.extendedPublicKey
        null -> null
    }
    
    val title = when (selectedViewType) {
        KeyViewType.SEED_PHRASE -> "Seed Phrase"
        KeyViewType.EXTENDED_PUBLIC_KEY -> "Extended Public Key"
        null -> "View Key Material"
    }
    
    // For seed phrases, split into words
    val words = if (selectedViewType == KeyViewType.SEED_PHRASE && currentKeyMaterial != null) {
        currentKeyMaterial.trim().split("\\s+".toRegex())
    } else {
        emptyList()
    }
    
    // Generate QR code when view type changes
    LaunchedEffect(selectedViewType, showQrCode) {
        if (selectedViewType != null && currentKeyMaterial != null) {
            qrBitmap = generateQrCode(currentKeyMaterial)
        }
    }
    
    // Reset copied state when view type changes
    LaunchedEffect(selectedViewType) {
        copied = false
        showQrCode = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "${keyMaterialInfo.walletName} - $title",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (selectedViewType == null) {
                    // Selection state - show options to reveal
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "What would you like to reveal?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Seed Phrase button
                            OutlinedButton(
                                onClick = { selectedViewType = KeyViewType.SEED_PHRASE },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = hasSeedPhrase,
                                border = BorderStroke(
                                    1.dp, 
                                    if (hasSeedPhrase) BitcoinOrange.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BitcoinOrange,
                                    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seed Phrase")
                            }
                            
                            if (!hasSeedPhrase) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Not available for watch-only wallets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Extended Public Key button
                            OutlinedButton(
                                onClick = { selectedViewType = KeyViewType.EXTENDED_PUBLIC_KEY },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                enabled = hasXpub,
                                border = BorderStroke(
                                    1.dp, 
                                    if (hasXpub) BitcoinOrange.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BitcoinOrange,
                                    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extended Public Key")
                            }
                        }
                    }
                } else {
                    // Revealed state
                    if (showQrCode && currentKeyMaterial != null) {
                        // QR Code display
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier
                                                .size(184.dp)
                                                .padding(8.dp)
                                        )
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        color = BitcoinOrange,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Scan this QR code to import the ${if (selectedViewType == KeyViewType.SEED_PHRASE) "seed phrase" else "extended public key"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (selectedViewType == KeyViewType.EXTENDED_PUBLIC_KEY && currentKeyMaterial != null) {
                        // Extended public key - show as text
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Text(
                                text = currentKeyMaterial,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else if (selectedViewType == KeyViewType.SEED_PHRASE && words.isNotEmpty()) {
                        // Seed phrase - show as numbered word grid
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(words) { index, word ->
                                    WordChip(
                                        index = index + 1,
                                        word = word
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy button
                        OutlinedButton(
                            onClick = {
                                currentKeyMaterial?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    copied = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (copied) SuccessGreen else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy",
                                color = if (copied) SuccessGreen else TextSecondary
                            )
                        }
                        
                        // QR Code button
                        OutlinedButton(
                            onClick = { showQrCode = !showQrCode },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp, 
                                if (showQrCode) BitcoinOrange else BorderColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = if (showQrCode) BitcoinOrange else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showQrCode) "Text" else "QR Code",
                                color = if (showQrCode) BitcoinOrange else TextSecondary
                            )
                        }
                    }
                    
                    // Back button to selection
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = { selectedViewType = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Back to Selection",
                            color = TextSecondary
                        )
                    }
                    
                    if (copied) {
                        Text(
                            text = "Remember to clear your clipboard after use!",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BitcoinOrange)
            }
        },
        containerColor = DarkSurface
    )
}

/**
 * Generate a QR code bitmap for the given content
 */
private fun generateQrCode(content: String): Bitmap? {
    return try {
        val size = 512
        val qrCodeWriter = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size
        )
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun WordChip(
    index: Int,
    word: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurfaceVariant,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = word,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletCard(
    wallet: WalletInfo,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onClick: () -> Unit,
    onSync: () -> Unit = {},
    isSyncing: Boolean = false
) {
    val cardColor = if (wallet.isActive) {
        BitcoinOrange.copy(alpha = 0.15f)
    } else {
        DarkCard
    }
    
    val borderColor = if (wallet.isActive) {
        BitcoinOrange.copy(alpha = 0.5f)
    } else {
        BorderColor
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (wallet.isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BitcoinOrange.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = BitcoinOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Row {
                    IconButton(
                        onClick = onSync,
                        modifier = Modifier.size(40.dp),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BitcoinOrange,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Full Sync",
                                tint = TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onView,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View Seed Phrase",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export Wallet",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${wallet.typeDescription}  -  ${if (wallet.isWatchOnly) "Watch Only" else "Seed Phrase"}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Derivation Path: ${wallet.derivationPath}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.7f)
            )
            
            // Last full sync time
            val lastSyncText = if (wallet.lastFullSyncTime != null) {
                val date = Date(wallet.lastFullSyncTime)
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                "Last full sync: ${formatter.format(date)}"
            } else {
                "Never fully synced"
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = lastSyncText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.7f)
            )
        }
    }
}
