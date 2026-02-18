@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
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
    val isActive: Boolean = false,
    val isWatchOnly: Boolean = false,
    val isWatchAddress: Boolean = false,
    val isPrivateKey: Boolean = false,
    val lastFullSyncTime: Long? = null,
    val masterFingerprint: String? = null,
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
    onEditWallet: (walletId: String, newName: String, newFingerprint: String?) -> Unit = { _, _, _ -> },
    onReorderWallets: (List<String>) -> Unit = {},
    onFullSync: (WalletInfo) -> Unit = {},
    syncingWalletId: String? = null,
) {
    var walletToDelete by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToView by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToSync by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToExport by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToEdit by remember { mutableStateOf<WalletInfo?>(null) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var keyMaterialInfo by remember { mutableStateOf<KeyMaterialInfo?>(null) }
    var showKeyMaterial by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (walletToDelete != null) {
        val isWatchOnly = walletToDelete?.isWatchOnly == true
        var confirmChecked by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                walletToDelete = null
                confirmChecked = false
            },
            title = {
                Text(
                    if (isWatchOnly) "Remove Watch-Only Wallet" else "Delete Wallet",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
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
            confirmButton = {
                TextButton(
                    onClick = {
                        walletToDelete?.let { onDeleteWallet(it) }
                        walletToDelete = null
                        confirmChecked = false
                    },
                    enabled = confirmChecked,
                ) {
                    Text(
                        if (isWatchOnly) "Remove" else "Delete",
                        color =
                            if (confirmChecked) {
                                if (isWatchOnly) BitcoinOrange else ErrorRed
                            } else {
                                TextSecondary.copy(alpha = 0.4f)
                            },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    walletToDelete = null
                    confirmChecked = false
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
        )
    }

    // Edit wallet dialog
    if (walletToEdit != null) {
        var editName by remember(walletToEdit) { mutableStateOf(walletToEdit!!.name) }
        var editFingerprint by remember(walletToEdit) {
            mutableStateOf(walletToEdit!!.masterFingerprint ?: "")
        }
        val isWatchOnly = walletToEdit!!.isWatchOnly
        val isWatchAddress = walletToEdit!!.isWatchAddress
        val fingerprintRegex = remember { Regex("^[0-9a-fA-F]{0,8}$") }
        val fingerprintValid = editFingerprint.isBlank() || editFingerprint.length == 8
        val nameChanged = editName.trim().isNotBlank() && editName.trim() != walletToEdit?.name
        val fingerprintChanged =
            isWatchOnly && !isWatchAddress &&
                editFingerprint.trim().lowercase() != (walletToEdit?.masterFingerprint ?: "").lowercase()
        val canSave = editName.trim().isNotBlank() && fingerprintValid && (nameChanged || fingerprintChanged)

        AlertDialog(
            onDismissRequest = { walletToEdit = null },
            title = {
                Text(
                    "Edit Wallet",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Wallet Name") },
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
                    if (isWatchOnly && !isWatchAddress) {
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedName = editName.trim()
                        val trimmedFp = if (isWatchOnly) editFingerprint.trim().lowercase() else null
                        if (trimmedName.isNotBlank()) {
                            walletToEdit?.let { onEditWallet(it.id, trimmedName, trimmedFp) }
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
                    Text("I Understand, Show Me")
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
        AlertDialog(
            onDismissRequest = { walletToSync = null },
            title = {
                Text(
                    "Full Sync",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
                Text(
                    "This will rescan all addresses for transactions. Use this if missing transactions or after backup restore.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        walletToSync?.let { onFullSync(it) }
                        walletToSync = null
                    },
                ) {
                    Text("Full Sync", color = BitcoinOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToSync = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
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
                Button(
                    onClick = onGenerateWallet,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                            disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                        ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                }

                Button(
                    onClick = onImportWallet,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                            disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                        ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                        text = "Generate or import a wallet to get started",
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
                        Button(
                            onClick = onGenerateWallet,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = BitcoinOrange,
                                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                                ),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
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
                        }

                        Button(
                            onClick = onImportWallet,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = BitcoinOrange,
                                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                                ),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
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
                                isSyncing = syncingWalletId == wallet.id,
                                showDragHandle = showDragHandles,
                                onMeasured = { heightPx ->
                                    measuredHeights[index] = heightPx
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
}

@Composable
private fun ExportWalletDialog(
    wallet: WalletInfo,
    onDismiss: () -> Unit,
    onExport: (uri: Uri, includeLabels: Boolean, includeServerSettings: Boolean, password: String?) -> Unit,
) {
    val context = LocalContext.current
    var includeLabels by remember { mutableStateOf(true) }
    var includeServerSettings by remember { mutableStateOf(false) }
    var encryptBackup by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var exportFileName by remember { mutableStateOf<String?>(null) }

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
            }
        }

    val passwordsMatch = password == confirmPassword
    val passwordLongEnough = password.length >= 8
    val encryptionValid = !encryptBackup || (passwordLongEnough && passwordsMatch)
    val canExport = exportUri != null && encryptionValid

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

                // Warning
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
                                    "This backup contains your extended public key. Store it securely."
                                } else {
                                    "This backup contains your seed phrase. Store it securely."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                        )
                    }
                }

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
                            text = "Electrum servers, block explorer, fee source",
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
                    shape = RoundedCornerShape(8.dp),
                    border =
                        BorderStroke(
                            1.dp,
                            if (exportUri != null) SuccessGreen.copy(alpha = 0.5f) else BorderColor,
                        ),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = if (exportUri != null) SuccessGreen else TextSecondary,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = exportFileName ?: "Choose location",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary,
                            ),
                    ) {
                        Text("Cancel")
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
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = canExport,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
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

    // Current key material based on selection
    val currentKeyMaterial =
        when (selectedViewType) {
            KeyViewType.SEED_PHRASE -> keyMaterialInfo.mnemonic
            KeyViewType.EXTENDED_PUBLIC_KEY -> keyMaterialInfo.extendedPublicKey
            KeyViewType.PRIVATE_KEY -> keyMaterialInfo.privateKey
            KeyViewType.WATCH_ADDRESS -> keyMaterialInfo.watchAddress
            null -> null
        }

    val title =
        when (selectedViewType) {
            KeyViewType.SEED_PHRASE -> "Seed Phrase"
            KeyViewType.EXTENDED_PUBLIC_KEY -> "Extended Public Key"
            KeyViewType.PRIVATE_KEY -> "Private Key"
            KeyViewType.WATCH_ADDRESS -> "Watched Address"
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
            qrBitmap = generateQrCode(currentKeyMaterial)
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

    AlertDialog(
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
                            selectedViewType == KeyViewType.PRIVATE_KEY ||
                            selectedViewType == KeyViewType.WATCH_ADDRESS) && currentKeyMaterial != null
                    ) {
                        // Extended public key, WIF private key, or watch address - show as text
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

/**
 * Generate a QR code bitmap for the given content
 */
private fun generateQrCode(content: String): Bitmap? {
    return try {
        val size = 512
        val qrCodeWriter = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix =
            qrCodeWriter.encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
            )

        val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap[x, y] =
                    if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb()
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
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
    isSyncing: Boolean = false,
    showDragHandle: Boolean = false,
    onMeasured: (Float) -> Unit = {},
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    when {
                        wallet.isWatchAddress || wallet.isWatchOnly -> Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = if (wallet.isWatchAddress) "Watch Address" else "Watch Only",
                            tint = BitcoinOrange,
                            modifier = Modifier.size(16.dp),
                        )
                        else -> Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = if (wallet.isPrivateKey) "Private Key" else "Seed Phrase",
                            tint = BitcoinOrange,
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

                Row {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                                .clickable(enabled = !isSyncing) { onSync() },
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = BitcoinOrange,
                                strokeWidth = 1.5.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Full Sync",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                                .clickable { onEdit() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Wallet",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                                .clickable { onView() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View Seed Phrase",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                                .clickable { onExport() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export Wallet",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                                .clickable { onDelete() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (wallet.derivationPath != "single") {
                Text(
                    text = "Derivation Path: ${wallet.derivationPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }

            if (wallet.masterFingerprint != null) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Fingerprint: ${wallet.masterFingerprint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }

            // Last full sync time
            val lastSyncText =
                if (wallet.lastFullSyncTime != null) {
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
                color = TextSecondary.copy(alpha = 0.7f),
            )

            if (showDragHandle) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
