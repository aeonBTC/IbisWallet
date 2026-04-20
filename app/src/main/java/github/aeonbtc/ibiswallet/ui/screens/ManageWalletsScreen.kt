@file:Suppress("AssignedValueIsNeverRead")

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.ScrollableAlertDialog
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.Bip329LabelCounts
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class WalletInfo(
    val id: String,
    val name: String,
    val type: String,
    val typeDescription: String,
    val derivationPath: String,
    val seedFormat: SeedFormat = SeedFormat.BIP39,
    val isActive: Boolean = false,
    val isWatchOnly: Boolean = false,
    val isLocked: Boolean = false,
    val isWatchAddress: Boolean = false,
    val isPrivateKey: Boolean = false,
    val lastFullSyncTime: Long? = null,
    val masterFingerprint: String? = null,
    val gapLimit: Int = 20,
    val liquidGapLimit: Int = 20,
    val isLiquidWatchOnly: Boolean = false,
)

/**
 * Data class for key material display
 * For full wallets: mnemonic is set, xpub is derived
 * For watch-only wallets: only xpub is set
 */
data class KeyMaterialInfo(
    val walletName: String,
    val mnemonic: String?, // Seed phrase (null for watch-only, WIF, watch address)
    val extendedPublicKey: String?, // xpub/zpub (always available for HD wallets)
    val isWatchOnly: Boolean,
    val masterFingerprint: String? = null, // Master key fingerprint (8 hex chars)
    val privateKey: String? = null, // WIF private key (single-key wallets)
    val watchAddress: String? = null, // Single watched address
    val liquidDescriptor: String? = null, // Liquid CT descriptor for Liquid-enabled wallets
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletsScreen(
    wallets: List<WalletInfo>,
    onBack: () -> Unit,
    onImportWallet: () -> Unit,
    onGenerateWallet: () -> Unit,
    onViewWallet: (WalletInfo) -> KeyMaterialInfo?,
    onDeleteWallet: (WalletInfo) -> Unit,
    onSelectWallet: (WalletInfo) -> Unit,
    onExportWallet: (walletId: String, uri: Uri, includeLabels: Boolean, includeServerSettings: Boolean, password: String?) -> Unit = { _, _, _, _, _ -> },
    onEditWallet: (walletId: String, newName: String, newGapLimit: Int, newFingerprint: String?) -> Unit = { _, _, _, _ -> },
    onReorderWallets: (List<String>) -> Unit = {},
    onFullSync: (WalletInfo) -> Unit = {},
    syncingWalletId: String? = null,
    onExportBip329Labels: (walletId: String, uri: Uri, scope: Bip329LabelScope) -> Unit = { _, _, _ -> },
    onImportBip329Labels: (walletId: String, uri: Uri, scope: Bip329LabelScope) -> Unit = { _, _, _ -> },
    onImportBip329LabelsFromContent: (walletId: String, content: String, scope: Bip329LabelScope) -> Unit = { _, _, _ -> },
    onGetBip329LabelsContent: (walletId: String, scope: Bip329LabelScope) -> String = { _, _ -> "" },
    onGetLabelCounts: (walletId: String) -> Bip329LabelCounts = { Bip329LabelCounts() },
    // Layer 2 (Liquid)
    layer2Enabled: Boolean = false,
    isLiquidEnabledForWallet: (walletId: String) -> Boolean = { false },
    onSetLiquidEnabledForWallet: (walletId: String, Boolean) -> Unit = { _, _ -> },
    onEditLiquidGapLimit: (walletId: String, newGapLimit: Int) -> Unit = { _, _ -> },
    isWalletLockAvailable: Boolean = false,
    onSetWalletLocked: (walletId: String, Boolean) -> Unit = { _, _ -> },
) {
    var walletToDelete by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToView by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToSync by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToExport by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToEdit by remember { mutableStateOf<WalletInfo?>(null) }
    var walletForLabels by remember { mutableStateOf<WalletInfo?>(null) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var keyMaterialInfo by remember { mutableStateOf<KeyMaterialInfo?>(null) }
    var showKeyMaterial by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (walletToDelete != null) {
        val isWatchOnly = walletToDelete?.isWatchOnly == true
        var confirmChecked by remember { mutableStateOf(false) }
        IbisConfirmDialog(
            onDismissRequest = {
                walletToDelete = null
                confirmChecked = false
            },
            title = if (isWatchOnly) "Remove Watch-Only Wallet" else "Delete Wallet",
            confirmText = if (isWatchOnly) "Remove" else "Delete",
            confirmColor = if (isWatchOnly) BitcoinOrange else ErrorRed,
            confirmEnabled = confirmChecked,
            onConfirm = {
                walletToDelete?.let { onDeleteWallet(it) }
                walletToDelete = null
                confirmChecked = false
            },
            onDismissAction = {
                walletToDelete = null
                confirmChecked = false
            },
            body = {
                Column {
                    Text(
                        if (isWatchOnly) {
                            "Remove \"${walletToDelete?.name}\"? You can re-import it later with the same xpub."
                        } else {
                            "Are you sure you want to delete \"${walletToDelete?.name}\"? This action cannot be undone. Make sure you have backed up your recovery phrase."
                        },
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { confirmChecked = !confirmChecked }
                                .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = confirmChecked,
                            onCheckedChange = { confirmChecked = it },
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = if (isWatchOnly) BitcoinOrange else ErrorRed,
                                    uncheckedColor = TextSecondary,
                                ),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isWatchOnly) "Confirm removal" else "I understand this cannot be undone",
                            color =
                                if (confirmChecked) {
                                    if (isWatchOnly) BitcoinOrange else ErrorRed
                                } else {
                                    TextSecondary
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
        )
    }

    // Wallet settings dialog
    if (walletToEdit != null) {
        var editName by remember(walletToEdit) { mutableStateOf(walletToEdit!!.name) }
        var editGapLimit by remember(walletToEdit) { mutableStateOf(walletToEdit!!.gapLimit.toString()) }
        var editFingerprint by remember(walletToEdit) {
            mutableStateOf(walletToEdit!!.masterFingerprint ?: "")
        }
        val isWatchOnly = walletToEdit!!.isWatchOnly
        val isWatchAddress = walletToEdit!!.isWatchAddress
        val isPrivateKey = walletToEdit!!.isPrivateKey
        val showFingerprint = isWatchOnly && !isWatchAddress && !isPrivateKey
        val fingerprintRegex = remember { Regex("^[0-9a-fA-F]{0,8}$") }
        val fingerprintValid = editFingerprint.isBlank() || editFingerprint.length == 8
        val gapLimitInt = editGapLimit.toIntOrNull()
        val gapLimitValid = gapLimitInt != null && gapLimitInt in 1..10000

        val showLiquidGapLimit = layer2Enabled && isLiquidEnabledForWallet(walletToEdit!!.id)
        var editLiquidGapLimit by remember(walletToEdit) {
            mutableStateOf(walletToEdit!!.liquidGapLimit.toString())
        }
        val liquidGapLimitInt = editLiquidGapLimit.toIntOrNull()
        val liquidGapLimitValid = !showLiquidGapLimit ||
            editLiquidGapLimit.isEmpty() ||
            (liquidGapLimitInt != null && liquidGapLimitInt in 1..10000)

        val nameChanged = editName.trim().isNotBlank() && editName.trim() != walletToEdit?.name
        val gapLimitChanged = gapLimitValid && gapLimitInt != walletToEdit?.gapLimit
        val liquidGapLimitChanged = showLiquidGapLimit &&
            liquidGapLimitValid && liquidGapLimitInt != null &&
            liquidGapLimitInt != walletToEdit?.liquidGapLimit
        val fingerprintChanged =
            showFingerprint &&
                editFingerprint.trim().lowercase() != (walletToEdit?.masterFingerprint ?: "").lowercase()
        val canSave =
            editName.trim().isNotBlank() && gapLimitValid && fingerprintValid && liquidGapLimitValid &&
                (nameChanged || gapLimitChanged || liquidGapLimitChanged || fingerprintChanged)

        ScrollableAlertDialog(
            onDismissRequest = { walletToEdit = null },
            title = {
                Text(
                    "Wallet Settings",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    // Name
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    // Gap Limit
                    OutlinedTextField(
                        value = editGapLimit,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.all { it.isDigit() }) {
                                editGapLimit = value
                            }
                        },
                        label = { Text(if (showLiquidGapLimit) "Bitcoin Gap Limit" else "Gap Limit") },
                        singleLine = true,
                        isError = editGapLimit.isNotEmpty() && !gapLimitValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Fingerprint (extended key watch-only wallets only)
                    if (showFingerprint) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editFingerprint,
                            onValueChange = { value ->
                                if (value.matches(fingerprintRegex)) {
                                    editFingerprint = value
                                }
                            },
                            label = { Text("Master Fingerprint") },
                            placeholder = { Text("e.g. 73c5da0a", color = TextSecondary.copy(alpha = 0.4f)) },
                            singleLine = true,
                            isError = editFingerprint.isNotBlank() && !fingerprintValid,
                            supportingText =
                                if (editFingerprint.isNotBlank() && !fingerprintValid) {
                                    { Text("Must be 8 hex characters", color = ErrorRed) }
                                } else {
                                    {
                                        Text(
                                            "8 hex characters",
                                            color = TextSecondary.copy(alpha = 0.5f),
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

                    if (showLiquidGapLimit) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editLiquidGapLimit,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    editLiquidGapLimit = value
                                }
                            },
                            label = { Text("Liquid Gap Limit") },
                            singleLine = true,
                            isError = editLiquidGapLimit.isNotEmpty() && !liquidGapLimitValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedName = editName.trim()
                        val newGap = gapLimitInt ?: walletToEdit!!.gapLimit
                        val trimmedFp = if (showFingerprint) editFingerprint.trim().lowercase() else null
                        if (trimmedName.isNotBlank() && gapLimitValid) {
                            walletToEdit?.let { onEditWallet(it.id, trimmedName, newGap, trimmedFp) }
                        }
                        if (liquidGapLimitChanged) {
                            walletToEdit?.let { onEditLiquidGapLimit(it.id, liquidGapLimitInt) }
                        }
                        if (gapLimitChanged || liquidGapLimitChanged) {
                            walletToEdit?.let { onFullSync(it) }
                        }
                        walletToEdit = null
                    },
                    enabled = canSave,
                ) {
                    Text(
                        "Save",
                        color = if (canSave) BitcoinOrange else TextSecondary.copy(alpha = 0.4f),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToEdit = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
        )
    }

    // Warning dialog before showing seed phrase
    if (showWarningDialog && walletToView != null) {
        ScrollableAlertDialog(
            onDismissRequest = {
                showWarningDialog = false
                walletToView = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(48.dp),
                )
            },
            title = {
                Text(
                    "Security Warning",
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column {
                    Text(
                        "You are about to view sensitive information for \"${walletToView?.name}\".",
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your seed phrase is the ONLY way to recover your wallet. Anyone with access to it can steal your funds.",
                        color = ErrorRed,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Make sure no one is watching your screen and never share this information with anyone.",
                        color = TextSecondary,
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
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                ) {
                    Text("I Understand")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        walletToView = null
                    },
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
        )
    }

    // Full Sync confirmation dialog
    if (walletToSync != null) {
        IbisConfirmDialog(
            onDismissRequest = { walletToSync = null },
            title = "Full Sync",
            message = "This will rescan all addresses for transactions. Use this if missing transactions or after backup restore.",
            confirmText = "Full Sync",
            onConfirm = {
                walletToSync?.let { onFullSync(it) }
                walletToSync = null
            },
        )
    }

    // Key material display dialog
    if (showKeyMaterial && keyMaterialInfo != null) {
        KeyMaterialDialog(
            keyMaterialInfo = keyMaterialInfo!!,
            onDismiss = {
                showKeyMaterial = false
                keyMaterialInfo = null
            },
        )
    }

    // BIP 329 Labels dialog
    if (walletForLabels != null) {
        Bip329LabelsDialog(
            wallet = walletForLabels!!,
            walletSupportsLiquid = layer2Enabled &&
                walletForLabels?.isWatchOnly == false &&
                isLiquidEnabledForWallet(walletForLabels!!.id),
            onDismiss = { walletForLabels = null },
            onExportToFile = { walletId, uri, scope ->
                onExportBip329Labels(walletId, uri, scope)
                walletForLabels = null
            },
            onImportFromFile = { walletId, uri, scope ->
                onImportBip329Labels(walletId, uri, scope)
                walletForLabels = null
            },
            onImportFromQr = { walletId, content, scope ->
                onImportBip329LabelsFromContent(walletId, content, scope)
                walletForLabels = null
            },
            getLabelsContent = onGetBip329LabelsContent,
            getLabelCounts = onGetLabelCounts,
        )
    }

    // Export wallet dialog
    if (walletToExport != null) {
        ExportWalletDialog(
            wallet = walletToExport!!,
            onDismiss = { walletToExport = null },
            onExport = { uri, includeLabels, includeServerSettings, password ->
                walletToExport?.let { wallet ->
                    onExportWallet(wallet.id, uri, includeLabels, includeServerSettings, password)
                }
                walletToExport = null
            },
        )
    }

    // Local mutable copy for reordering
    val reorderableWallets = remember(wallets) { wallets.toMutableStateList() }

    // ---- Drag state ----
    val itemHeightDp = 140.dp          // approximate card height
    val spacingDp = 8.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val spacingPx = with(density) { spacingDp.toPx() }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Measured item heights for variable-height cards
    val measuredHeights = remember { mutableMapOf<Int, Float>() }

    // Track header height (buttons row + spacer) so drag hit-test starts at correct Y
    val headerSpacingPx = with(density) { 6.dp.toPx() }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()

    /** Given how far the dragged item has moved, return its new logical index. */
    fun calcTargetIndex(fromIndex: Int, offsetY: Float): Int {
        val count = reorderableWallets.size
        if (count <= 1) return fromIndex
        var accumulated = 0f
        var target = fromIndex
        if (offsetY > 0) {
            // Moving down
            for (i in fromIndex until count - 1) {
                val nextSlot = (measuredHeights[i + 1] ?: itemHeightPx) / 2f + (measuredHeights[i] ?: itemHeightPx) / 2f + spacingPx
                accumulated += nextSlot
                if (offsetY > accumulated - nextSlot / 2f) {
                    target = i + 1
                } else {
                    break
                }
            }
        } else {
            // Moving up
            for (i in fromIndex downTo 1) {
                val prevSlot = (measuredHeights[i - 1] ?: itemHeightPx) / 2f + (measuredHeights[i] ?: itemHeightPx) / 2f + spacingPx
                accumulated -= prevSlot
                if (offsetY < accumulated + prevSlot / 2f) {
                    target = i - 1
                } else {
                    break
                }
            }
        }
        return target.coerceIn(0, count - 1)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
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
                text = "Manage Wallets",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (reorderableWallets.isEmpty()) {
            // Generate + Import wallet buttons (for empty state, not scrolling)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IbisButton(
                    onClick = onGenerateWallet,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                IbisButton(
                    onClick = onImportWallet,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Empty state
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No Wallets",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add a wallet to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            // Reorderable wallet list
            val showDragHandles = reorderableWallets.size > 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (showDragHandles) {
                            Modifier.pointerInput(reorderableWallets.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        // Adjust for header content (buttons row + spacer) and scroll position
                                        val adjustedY = offset.y + scrollState.value.toFloat() - headerHeightPx
                                        if (adjustedY < 0f) return@detectDragGesturesAfterLongPress
                                        // Determine which card was long-pressed based on Y
                                        var accumulated = 0f
                                        for (i in reorderableWallets.indices) {
                                            val h = measuredHeights[i] ?: itemHeightPx
                                            if (adjustedY < accumulated + h + spacingPx) {
                                                draggedIndex = i
                                                dragOffsetY = 0f
                                                break
                                            }
                                            accumulated += h + spacingPx
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val idx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                        // Clamp offset so the card can't be dragged beyond list bounds
                                        val maxUp = -(0 until idx).sumOf {
                                            ((measuredHeights[it] ?: itemHeightPx) + spacingPx).toDouble()
                                        }.toFloat()
                                        val maxDown = (idx + 1 until reorderableWallets.size).sumOf {
                                            ((measuredHeights[it] ?: itemHeightPx) + spacingPx).toDouble()
                                        }.toFloat()
                                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(maxUp, maxDown)
                                    },
                                    onDragEnd = {
                                        val idx = draggedIndex
                                        if (idx != null) {
                                            val target = calcTargetIndex(idx, dragOffsetY)
                                            if (target != idx) {
                                                reorderableWallets.apply {
                                                    add(target, removeAt(idx))
                                                }
                                                onReorderWallets(reorderableWallets.map { it.id })
                                            }
                                        }
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, enabled = draggedIndex == null),
                ) {
                    // Generate + Import wallet buttons (inside scrollable area)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                headerHeightPx = coords.size.height.toFloat() + headerSpacingPx
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IbisButton(
                            onClick = onGenerateWallet,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }

                        IbisButton(
                            onClick = onImportWallet,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Import",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    reorderableWallets.forEachIndexed { index, wallet ->
                        val isDragging = draggedIndex == index
                        val targetIndex = draggedIndex?.let { calcTargetIndex(it, dragOffsetY) }

                        // Calculate displacement for non-dragged items
                        val displacementPx = if (!isDragging && draggedIndex != null && targetIndex != null) {
                            val dragIdx = draggedIndex!!
                            val draggedH = measuredHeights[dragIdx] ?: itemHeightPx
                            when (index) {
                                // Dragged item moved down past this item → shift up
                                in (dragIdx + 1)..targetIndex ->
                                    -(draggedH + spacingPx)
                                // Dragged item moved up past this item → shift down
                                in targetIndex until dragIdx ->
                                    draggedH + spacingPx
                                else -> 0f
                            }
                        } else {
                            0f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = if (isDragging) {
                                            dragOffsetY.roundToInt()
                                        } else {
                                            displacementPx.roundToInt()
                                        },
                                    )
                                }
                                .graphicsLayer {
                                    if (isDragging) {
                                        shadowElevation = 8f
                                        alpha = 0.95f
                                    }
                                },
                        ) {
                            WalletCard(
                                wallet = wallet,
                                onView = {
                                    walletToView = wallet
                                    showWarningDialog = true
                                },
                                onDelete = { walletToDelete = wallet },
                                onExport = { walletToExport = wallet },
                                onEdit = { walletToEdit = wallet },
                                onClick = {
                                    if (draggedIndex == null) onSelectWallet(wallet)
                                },
                                onSync = { walletToSync = wallet },
                                onLabels = { walletForLabels = wallet },
                                isSyncing = syncingWalletId == wallet.id,
                                showDragHandle = showDragHandles,
                                onMeasured = { heightPx ->
                                    measuredHeights[index] = heightPx
                                },
                                layer2Enabled = layer2Enabled,
                                isLiquidEnabled = isLiquidEnabledForWallet(wallet.id),
                                onSetLiquidEnabled = { enabled ->
                                    onSetLiquidEnabledForWallet(wallet.id, enabled)
                                },
                                isWalletLockAvailable = isWalletLockAvailable,
                                onSetWalletLocked = { locked ->
                                    onSetWalletLocked(wallet.id, locked)
                                },
                            )
                        }
                        if (index < reorderableWallets.size - 1) {
                            Spacer(modifier = Modifier.height(spacingDp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Enum for key material view type selection
 */
private enum class KeyViewType {
    SEED_PHRASE,
    EXTENDED_PUBLIC_KEY,
    PRIVATE_KEY,
    WATCH_ADDRESS,
    LIQUID_DESCRIPTOR,
}

@Composable
private fun ExportWalletDialog(
    wallet: WalletInfo,
    onDismiss: () -> Unit,
    onExport: (uri: Uri, includeLabels: Boolean, includeServerSettings: Boolean, password: String?) -> Unit,
) {
    val context = LocalContext.current
    var includeLabels by remember { mutableStateOf(true) }
    var includeServerSettings by remember { mutableStateOf(true) }
    var encryptBackup by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var exportFileName by remember { mutableStateOf<String?>(null) }
    var showExportConfirmation by remember { mutableStateOf(false) }

    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val safeName = wallet.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
    val suggestedFileName = "ibis-backup-$safeName-$dateStr.json"

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri: Uri? ->
            if (uri != null) {
                exportUri = uri
                // Query the real display name from the content resolver
                exportFileName =
                    try {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) cursor.getString(idx) else null
                            } else {
                                null
                            }
                        }
                    } catch (_: Exception) {
                        null
                    } ?: suggestedFileName
                showExportConfirmation = true
            }
        }

    val passwordsMatch = password == confirmPassword
    val passwordLongEnough = password.length >= 8
    val encryptionValid = !encryptBackup || (passwordLongEnough && passwordsMatch)
    val canCompleteExport = exportUri != null && encryptionValid
    val canChooseLocation = !encryptBackup || encryptionValid

    if (showExportConfirmation && exportUri != null) {
        Dialog(
            onDismissRequest = {
                showExportConfirmation = false
                exportUri = null
                exportFileName = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = DarkSurface,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                ) {
                    Text(
                        text = "Confirm Export",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Review the backup details before exporting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BorderColor.copy(alpha = 0.18f)),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Save location",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                                Text(
                                    text = exportFileName ?: suggestedFileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BorderColor.copy(alpha = 0.14f)),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ExportConfirmationRow(
                                label = "Wallet",
                                value = wallet.name,
                            )
                            ExportConfirmationRow(
                                label = "Labels",
                                value = if (includeLabels) "Included" else "Excluded",
                                valueColor = if (includeLabels) SuccessGreen else ErrorRed,
                            )
                            ExportConfirmationRow(
                                label = "Server settings",
                                value = if (includeServerSettings) "Included" else "Excluded",
                                valueColor = if (includeServerSettings) SuccessGreen else ErrorRed,
                            )
                            ExportConfirmationRow(
                                label = "Encryption",
                                value = if (encryptBackup) "Enabled" else "Off",
                                valueColor = if (encryptBackup) SuccessGreen else ErrorRed,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!encryptionValid) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (!passwordLongEnough) {
                                "Password must be at least 8 characters."
                            } else {
                                "Passwords do not match."
                            },
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                showExportConfirmation = false
                                exportUri = null
                                exportFileName = null
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                exportUri?.let { uri ->
                                    onExport(
                                        uri,
                                        includeLabels,
                                        includeServerSettings,
                                        if (encryptBackup) password else null,
                                    )
                                }
                            },
                            enabled = canCompleteExport,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = BitcoinOrange,
                                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                                ),
                        ) {
                            Text("Export")
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Export Wallet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Exporting \"${wallet.name}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = BorderColor,
                    thickness = 1.dp,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Include Labels toggle
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { includeLabels = !includeLabels },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Labels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Address and transaction labels",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    SquareToggle(
                        checked = includeLabels,
                        onCheckedChange = { includeLabels = it },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Include Server Settings toggle
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { includeServerSettings = !includeServerSettings },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Server Settings",
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
                        checked = includeServerSettings,
                        onCheckedChange = { includeServerSettings = it },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Encrypt Backup toggle
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { encryptBackup = !encryptBackup },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (encryptBackup) BitcoinOrange else TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Encrypt Backup",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            text = "AES-256 password protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    SquareToggle(
                        checked = encryptBackup,
                        onCheckedChange = { encryptBackup = it },
                    )
                }

                if (!encryptBackup) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = ErrorRed.copy(alpha = 0.1f),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text =
                                    if (wallet.isWatchOnly) {
                                        "This backup is not encrypted."
                                    } else {
                                        "This backup is not encrypted."
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                            )
                        }
                    }
                }

                // Password fields (animated)
                AnimatedVisibility(
                    visible = encryptBackup,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation =
                                if (showPassword) {
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
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector =
                                            if (showPassword) {
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

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm Password", color = TextSecondary) },
                            singleLine = true,
                            visualTransformation =
                                if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            keyboardOptions =
                                KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                ),
                            isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BitcoinOrange,
                                    unfocusedBorderColor = BorderColor,
                                    cursorColor = BitcoinOrange,
                                    errorBorderColor = ErrorRed,
                                ),
                        )

                        // Validation hints
                        if (password.isNotEmpty() && !passwordLongEnough) {
                            Text(
                                text = "Minimum 8 characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        } else if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                            Text(
                                text = "Passwords don't match",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Location
                Text(
                    text = "Save Location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { filePickerLauncher.launch(suggestedFileName) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    enabled = canChooseLocation,
                    shape = RoundedCornerShape(8.dp),
                    border =
                        BorderStroke(
                            1.dp,
                            if (exportUri != null) SuccessGreen.copy(alpha = 0.5f) else BorderColor,
                        ),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = if (exportUri != null) SuccessGreen else TextSecondary,
                            disabledContentColor = TextSecondary.copy(alpha = 0.5f),
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = exportFileName ?: "Choose Location",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportConfirmationRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

// ==================== BIP 329 Labels Dialog ====================

@Composable
private fun Bip329LabelsDialog(
    wallet: WalletInfo,
    walletSupportsLiquid: Boolean,
    onDismiss: () -> Unit,
    onExportToFile: (walletId: String, uri: Uri, scope: Bip329LabelScope) -> Unit,
    onImportFromFile: (walletId: String, uri: Uri, scope: Bip329LabelScope) -> Unit,
    onImportFromQr: (walletId: String, content: String, scope: Bip329LabelScope) -> Unit,
    getLabelsContent: (walletId: String, scope: Bip329LabelScope) -> String,
    getLabelCounts: (walletId: String) -> Bip329LabelCounts,
) {
    val counts = remember(wallet.id) { getLabelCounts(wallet.id) }
    var selectedScope by remember(wallet.id, walletSupportsLiquid) {
        mutableStateOf(Bip329LabelScope.BITCOIN)
    }
    val addrCount = counts.addressCount(selectedScope)
    val txCount = counts.transactionCount(selectedScope)
    val totalCount = counts.totalCount(selectedScope)

    // QR display state
    var showQrExport by remember { mutableStateOf(false) }
    var labelsBytes by remember { mutableStateOf<ByteArray?>(null) }

    // QR scanner state
    var showQrScanner by remember { mutableStateOf(false) }

    // File picker for export (.jsonl)
    val exportFilePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/jsonl"),
        ) { uri ->
            if (uri != null) {
                onExportToFile(wallet.id, uri, selectedScope)
            }
        }

    // File picker for import (.jsonl or .csv)
    val importFilePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                onImportFromFile(wallet.id, uri, selectedScope)
            }
        }

    // QR scanner dialog
    if (showQrScanner) {
        github.aeonbtc.ibiswallet.ui.components.LabelsQrScannerDialog(
            onLabelsScanned = { content ->
                showQrScanner = false
                onImportFromQr(wallet.id, content, selectedScope)
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Label Import/Export",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "\"${wallet.name}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (walletSupportsLiquid) {
                    Text(
                        text = "Scope",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Bip329LabelScope.BITCOIN to "Bitcoin",
                            Bip329LabelScope.LIQUID to "Liquid",
                            Bip329LabelScope.BOTH to "Both",
                        ).forEach { (scope, label) ->
                            val isSelected = selectedScope == scope
                            OutlinedButton(
                                onClick = { selectedScope = scope },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) {
                                        when (scope) {
                                            Bip329LabelScope.BITCOIN -> BitcoinOrange
                                            Bip329LabelScope.LIQUID, Bip329LabelScope.BOTH -> LiquidTeal
                                        }
                                    } else {
                                        BorderColor
                                    },
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isSelected) {
                                        when (scope) {
                                            Bip329LabelScope.BITCOIN -> BitcoinOrange
                                            Bip329LabelScope.LIQUID, Bip329LabelScope.BOTH -> LiquidTeal
                                        }
                                    } else {
                                        TextSecondary
                                    },
                                ),
                            ) {
                                Text(label)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Label counts
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$addrCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedScope == Bip329LabelScope.LIQUID) LiquidTeal else BitcoinOrange,
                            )
                            Text(
                                text = "Address",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$txCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedScope == Bip329LabelScope.LIQUID) LiquidTeal else BitcoinOrange,
                            )
                            Text(
                                text = "Transaction",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = when (selectedScope) {
                                    Bip329LabelScope.BITCOIN -> "Bitcoin"
                                    Bip329LabelScope.LIQUID -> "Liquid"
                                    Bip329LabelScope.BOTH -> "Combined"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Import section
                Text(
                    text = "Import",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (selectedScope == Bip329LabelScope.LIQUID) {
                        "BIP 329 (.jsonl) for Liquid labels"
                    } else {
                        "BIP 329 (.jsonl) or Electrum CSV"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            importFilePicker.launch(
                                arrayOf(
                                    "application/json",
                                    "application/jsonl",
                                    "text/plain",
                                    "text/csv",
                                    "application/octet-stream",
                                    "*/*",
                                ),
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("File")
                    }

                    OutlinedButton(
                        onClick = { showQrScanner = true },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Export section
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "BIP 329 (.jsonl)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val suffix =
                                when (selectedScope) {
                                    Bip329LabelScope.BITCOIN -> "bitcoin"
                                    Bip329LabelScope.LIQUID -> "liquid"
                                    Bip329LabelScope.BOTH -> "combined"
                                }
                            val fileName = "${wallet.name.replace(" ", "_")}_${suffix}_labels.jsonl"
                            exportFilePicker.launch(fileName)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = totalCount > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("File")
                    }

                    OutlinedButton(
                        onClick = {
                            val content = getLabelsContent(wallet.id, selectedScope)
                            if (content.isNotBlank()) {
                                labelsBytes = content.toByteArray(Charsets.UTF_8)
                                showQrExport = true
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = totalCount > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show QR")
                    }
                }

                // QR export display (animated)
                if (showQrExport && labelsBytes != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            github.aeonbtc.ibiswallet.ui.components.AnimatedQrCodeBytes(
                                data = labelsBytes!!,
                                qrSize = 240.dp,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(onClick = { showQrExport = false }) {
                                Text("Hide QR", color = TextSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Close", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun KeyMaterialDialog(
    keyMaterialInfo: KeyMaterialInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var selectedViewType by remember { mutableStateOf<KeyViewType?>(null) }
    var showQrCode by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val hasSeedPhrase = keyMaterialInfo.mnemonic != null
    val hasXpub = keyMaterialInfo.extendedPublicKey != null
    val hasPrivateKey = keyMaterialInfo.privateKey != null
    val hasWatchAddress = keyMaterialInfo.watchAddress != null
    val hasLiquidDescriptor = keyMaterialInfo.liquidDescriptor != null

    // Current key material based on selection
    val currentKeyMaterial =
        when (selectedViewType) {
            KeyViewType.SEED_PHRASE -> keyMaterialInfo.mnemonic
            KeyViewType.EXTENDED_PUBLIC_KEY -> keyMaterialInfo.extendedPublicKey
            KeyViewType.PRIVATE_KEY -> keyMaterialInfo.privateKey
            KeyViewType.WATCH_ADDRESS -> keyMaterialInfo.watchAddress
            KeyViewType.LIQUID_DESCRIPTOR ->
                keyMaterialInfo.liquidDescriptor?.let(BitcoinUtils::formatLiquidDescriptorForDisplay)
            null -> null
        }

    val title =
        when (selectedViewType) {
            KeyViewType.SEED_PHRASE -> "Seed Phrase"
            KeyViewType.EXTENDED_PUBLIC_KEY -> "Extended Public Key"
            KeyViewType.PRIVATE_KEY -> "Private Key"
            KeyViewType.WATCH_ADDRESS -> "Watched Address"
            KeyViewType.LIQUID_DESCRIPTOR -> "Liquid Descriptor"
            null -> "View Key Material"
        }

    // For seed phrases, split into words
    val words =
        if (selectedViewType == KeyViewType.SEED_PHRASE && currentKeyMaterial != null) {
            currentKeyMaterial.trim().split("\\s+".toRegex())
        } else {
            emptyList()
        }

    // Generate QR code when view type changes
    LaunchedEffect(selectedViewType, showQrCode) {
        if (selectedViewType != null && currentKeyMaterial != null) {
            qrBitmap = generateQrBitmap(currentKeyMaterial)
        }
    }

    // Reset copied state after 3 seconds
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(3000)
            copied = false
        }
    }

    // Reset copied state when view type changes
    LaunchedEffect(selectedViewType) {
        copied = false
        showQrCode = false
    }

    ScrollableAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${keyMaterialInfo.walletName} - $title",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (selectedViewType == null) {
                    // Selection state - show options to reveal
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "What would you like to reveal?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Seed Phrase button
                            IbisButton(
                                onClick = { selectedViewType = KeyViewType.SEED_PHRASE },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hasSeedPhrase,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Seed Phrase")
                            }

                            // Extended Public Key button
                            IbisButton(
                                onClick = { selectedViewType = KeyViewType.EXTENDED_PUBLIC_KEY },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hasXpub,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extended Public Key")
                            }

                            if (hasLiquidDescriptor) {
                                IbisButton(
                                    onClick = { selectedViewType = KeyViewType.LIQUID_DESCRIPTOR },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Liquid Descriptor")
                                }
                            }

                            // Private Key button (WIF wallets)
                            if (hasPrivateKey) {
                                IbisButton(
                                    onClick = { selectedViewType = KeyViewType.PRIVATE_KEY },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Private Key (WIF)")
                                }
                            }

                            // Watch Address button
                            if (hasWatchAddress) {
                                IbisButton(
                                    onClick = { selectedViewType = KeyViewType.WATCH_ADDRESS },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Watched Address")
                                }
                            }
                        }
                    }
                } else {
                    // Revealed state

                    // Master fingerprint (shown for HD wallets only, not single-address/key wallets)
                    if (keyMaterialInfo.masterFingerprint != null &&
                        selectedViewType != KeyViewType.WATCH_ADDRESS &&
                        selectedViewType != KeyViewType.PRIVATE_KEY
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Master Fingerprint",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                                Text(
                                    text = keyMaterialInfo.masterFingerprint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (showQrCode && currentKeyMaterial != null) {
                        // QR Code display
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (qrBitmap != null) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier =
                                                Modifier
                                                    .size(184.dp)
                                                    .padding(8.dp),
                                        )
                                    }
                                } else {
                                    CircularProgressIndicator(
                                        color = BitcoinOrange,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val qrSubtitle = when (selectedViewType) {
                            KeyViewType.SEED_PHRASE -> "seed phrase"
                            KeyViewType.EXTENDED_PUBLIC_KEY -> "extended public key"
                            KeyViewType.LIQUID_DESCRIPTOR -> "Liquid descriptor"
                            KeyViewType.PRIVATE_KEY -> "private key"
                            else -> "address"
                        }
                        Text(
                            text = "Scan this QR code to import the $qrSubtitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if ((selectedViewType == KeyViewType.EXTENDED_PUBLIC_KEY ||
                            selectedViewType == KeyViewType.LIQUID_DESCRIPTOR ||
                            selectedViewType == KeyViewType.PRIVATE_KEY ||
                            selectedViewType == KeyViewType.WATCH_ADDRESS) && currentKeyMaterial != null
                    ) {
                        // Extended public key, Liquid descriptor, WIF private key, or watch address - show as text
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                        ) {
                            Text(
                                text = currentKeyMaterial,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else if (selectedViewType == KeyViewType.SEED_PHRASE && words.isNotEmpty()) {
                        // Seed phrase - show as numbered word grid (2 columns)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                        ) {
                            val half = (words.size + 1) / 2
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                for (i in 0 until half) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            WordChip(index = i + 1, word = words[i])
                                        }
                                        val rightIndex = i + half
                                        if (rightIndex < words.size) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                WordChip(
                                                    index = rightIndex + 1,
                                                    word = words[rightIndex],
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Copy button
                        OutlinedButton(
                            onClick = {
                                currentKeyMaterial?.let {
                                    SecureClipboard.copyAndScheduleClear(context, "Key Material", it)
                                    copied = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (copied) BitcoinOrange else TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy",
                                color = if (copied) BitcoinOrange else TextSecondary,
                            )
                        }

                        // QR Code button
                        OutlinedButton(
                            onClick = { showQrCode = !showQrCode },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (showQrCode) BitcoinOrange else BorderColor,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = if (showQrCode) BitcoinOrange else TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "QR Code",
                                color = if (showQrCode) BitcoinOrange else TextSecondary,
                            )
                        }
                    }

                    // Back button to selection
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { selectedViewType = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Back to Selection",
                            color = TextSecondary,
                        )
                    }

                    if (copied) {
                        Text(
                            text = "Clipboard will auto-clear in 30 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
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
        containerColor = DarkSurface,
    )
}

@Composable
private fun WordChip(
    index: Int,
    word: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletCard(
    wallet: WalletInfo,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    onSync: () -> Unit = {},
    onLabels: () -> Unit = {},
    isSyncing: Boolean = false,
    showDragHandle: Boolean = false,
    onMeasured: (Float) -> Unit = {},
    layer2Enabled: Boolean = false,
    isLiquidEnabled: Boolean = false,
    onSetLiquidEnabled: (Boolean) -> Unit = {},
    isWalletLockAvailable: Boolean = false,
    onSetWalletLocked: (Boolean) -> Unit = {},
) {
    val cardColor =
        if (wallet.isActive) {
            BitcoinOrange.copy(alpha = 0.15f)
        } else {
            DarkCard
        }

    val borderColor =
        if (wallet.isActive) {
            BitcoinOrange.copy(alpha = 0.5f)
        } else {
            BorderColor
        }

    val isBip39Wallet =
        !wallet.isWatchOnly &&
            !wallet.isWatchAddress &&
            !wallet.isPrivateKey &&
            wallet.seedFormat == SeedFormat.BIP39
    val showLiquidToggle = wallet.isLiquidWatchOnly || (layer2Enabled && isBip39Wallet)
    val managementActionsEnabled = !wallet.isLocked
    val liquidToggleEnabled = !wallet.isLocked && !wallet.isLiquidWatchOnly
    val liquidChecked = wallet.isLiquidWatchOnly || isLiquidEnabled
    val lockedPrimaryTextColor = ErrorRed.copy(alpha = 0.85f)
    val lockedSecondaryTextColor = ErrorRed.copy(alpha = 0.6f)
    val actionTint =
        if (managementActionsEnabled) {
            TextSecondary
        } else {
            TextSecondary.copy(alpha = 0.35f)
        }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onMeasured(coordinates.size.height.toFloat())
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Left side: wallet info text
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = wallet.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (wallet.isLocked) lockedPrimaryTextColor else MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        when {
                            wallet.isWatchAddress || wallet.isWatchOnly -> Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = if (wallet.isWatchAddress) "Watch Address" else "Watch Only",
                                tint = if (wallet.isLocked) lockedPrimaryTextColor else BitcoinOrange,
                                modifier = Modifier.size(16.dp),
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = if (wallet.isPrivateKey) "Private Key" else "Seed Phrase",
                                tint = if (wallet.isLocked) lockedPrimaryTextColor else BitcoinOrange,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (wallet.isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BitcoinOrange.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BitcoinOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${wallet.typeDescription}  -  ${when {
                            wallet.isWatchAddress -> "Watch Address"
                            wallet.isPrivateKey -> "Private Key"
                            wallet.isWatchOnly -> "Watch Only"
                            else -> "Seed Phrase"
                        }}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                        color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (wallet.derivationPath != "single") {
                        Text(
                            text = "Derivation Path: ${wallet.derivationPath}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                            color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary.copy(alpha = 0.92f),
                        )
                    }

                    if (wallet.masterFingerprint != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fingerprint: ${wallet.masterFingerprint}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                            color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary.copy(alpha = 0.92f),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val lastSyncText =
                        if (wallet.lastFullSyncTime != null) {
                            val date = Date(wallet.lastFullSyncTime)
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            "Last full sync: ${formatter.format(date)}"
                        } else {
                            "Never fully synced"
                        }
                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary.copy(alpha = 0.92f),
                    )
                    if (isWalletLockAvailable || showLiquidToggle) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isWalletLockAvailable) {
                                Text(
                                    text = "Lock",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    color = if (wallet.isLocked) lockedPrimaryTextColor else TextSecondary.copy(alpha = 0.8f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SquareToggle(
                                    checked = wallet.isLocked,
                                    onCheckedChange = onSetWalletLocked,
                                    checkedColor = BitcoinOrange,
                                    trackWidth = 36.dp,
                                    trackHeight = 20.dp,
                                    thumbSize = 14.dp,
                                    thumbPadding = 3.dp,
                                )
                            }
                            if (isWalletLockAvailable && showLiquidToggle) {
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            if (showLiquidToggle) {
                                Text(
                                    text = "Liquid",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    color =
                                        if (liquidChecked) {
                                            LiquidTeal
                                        } else if (wallet.isLocked) {
                                            lockedSecondaryTextColor
                                        } else {
                                            TextSecondary.copy(alpha = if (liquidToggleEnabled) 0.8f else 0.55f)
                                        },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SquareToggle(
                                    checked = liquidChecked,
                                    onCheckedChange = { enabled ->
                                        if (!wallet.isLiquidWatchOnly) {
                                            onSetLiquidEnabled(enabled)
                                        }
                                    },
                                    enabled = liquidToggleEnabled,
                                    checkedColor = LiquidTeal,
                                    disabledCheckedColor = LiquidTeal,
                                    trackWidth = 36.dp,
                                    trackHeight = 20.dp,
                                    thumbSize = 14.dp,
                                    thumbPadding = 3.dp,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right side: icon buttons in two rows
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Top row: Sync, Settings, View
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .clickable(enabled = managementActionsEnabled && !isSyncing) { onSync() },
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 1.5.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Full Sync",
                                    tint = actionTint,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .clickable(enabled = managementActionsEnabled) { onEdit() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Wallet Settings",
                                tint = actionTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .clickable(enabled = managementActionsEnabled) { onView() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "View Seed Phrase",
                                tint = actionTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom row: Labels, Export, Delete
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .clickable(enabled = managementActionsEnabled) { onLabels() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Labels (BIP 329)",
                                tint = actionTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .clickable(enabled = managementActionsEnabled) { onExport() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export Wallet",
                                tint = actionTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .clickable(enabled = managementActionsEnabled) { onDelete() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = actionTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                }
            }

            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to reorder",
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 8.dp)
                            .size(18.dp),
                )
            }
        }
    }
}
