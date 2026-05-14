@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.MultisigWalletConfig
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.WalletResult
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableAlertDialog
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrangeLight
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LiquidTeal
import github.aeonbtc.ibiswallet.ui.theme.SparkPurple
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.Bip329LabelCounts
import github.aeonbtc.ibiswallet.util.Bip329LabelScope
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isMultisig: Boolean = false,
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
    val multisigConfig: MultisigWalletConfig? = null,
    val localCosignerKeyMaterial: String? = null,
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
    onSignMessage: (walletId: String, address: String, message: String) -> WalletResult<String> =
        { _, _, _ -> WalletResult.Error("Message signing unavailable") },
    onVerifyMessage: (address: String, message: String, signature: String) -> WalletResult<Boolean> =
        { _, _, _ -> WalletResult.Error("Message verification unavailable") },
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
    liquidLayer2Enabled: Boolean = layer2Enabled,
    sparkLayer2Enabled: Boolean = layer2Enabled,
    isLiquidEnabledForWallet: (walletId: String) -> Boolean = { false },
    onSetLiquidEnabledForWallet: (walletId: String, Boolean) -> Unit = { _, _ -> },
    isSparkEnabledForWallet: (walletId: String) -> Boolean = { false },
    onSetSparkEnabledForWallet: (walletId: String, Boolean) -> Unit = { _, _ -> },
    onEditLiquidGapLimit: (walletId: String, newGapLimit: Int) -> Unit = { _, _ -> },
    isWalletLockAvailable: Boolean = false,
    onSetWalletLocked: (walletId: String, Boolean) -> Unit = { _, _ -> },
) {
    var walletToDelete by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToView by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToSync by remember { mutableStateOf<WalletInfo?>(null) }
    var walletToEdit by remember { mutableStateOf<WalletInfo?>(null) }
    var walletForLabels by remember { mutableStateOf<WalletInfo?>(null) }
    var walletForMessages by remember { mutableStateOf<WalletInfo?>(null) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var keyMaterialInfo by remember { mutableStateOf<KeyMaterialInfo?>(null) }
    var showKeyMaterial by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (walletToDelete != null) {
        val isWatchOnly = walletToDelete?.isWatchOnly == true
        val walletDeleteMessage =
            if (isWatchOnly) {
                stringResource(R.string.wallet_delete_watch_only_message, walletToDelete?.name.orEmpty())
            } else {
                stringResource(R.string.wallet_delete_irreversible_message, walletToDelete?.name.orEmpty())
            }
        val confirmCheckboxText =
            if (isWatchOnly) {
                stringResource(R.string.wallet_delete_confirm_removal_label)
            } else {
                stringResource(R.string.wallet_delete_irreversible_ack_label)
            }
        var confirmChecked by remember { mutableStateOf(false) }
        IbisConfirmDialog(
            onDismissRequest = {
                walletToDelete = null
                confirmChecked = false
            },
            title =
                stringResource(
                    if (isWatchOnly) R.string.loc_b6f847eb else R.string.loc_9f4e5e7c,
                ),
            confirmText =
                stringResource(
                    if (isWatchOnly) R.string.loc_6f2c1806 else R.string.loc_3dbe79b1,
                ),
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
                        walletDeleteMessage,
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
                            confirmCheckboxText,
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
                    stringResource(R.string.loc_4f49447d),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            text = {
                Column {
                    // Name
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
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
                            label = { Text(stringResource(R.string.loc_7b07ecad)) },
                            placeholder = { Text(stringResource(R.string.loc_aea21c85), color = TextSecondary.copy(alpha = 0.4f)) },
                            singleLine = true,
                            isError = editFingerprint.isNotBlank() && !fingerprintValid,
                            supportingText =
                                if (editFingerprint.isNotBlank() && !fingerprintValid) {
                                    { Text(stringResource(R.string.loc_39921dc5), color = ErrorRed) }
                                } else {
                                    {
                                        Text(
                                            stringResource(R.string.loc_37540690),
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
                            label = { Text(stringResource(R.string.loc_aeb83a33)) },
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
                        stringResource(R.string.loc_f55495e0),
                        color = if (canSave) BitcoinOrange else TextSecondary.copy(alpha = 0.4f),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToEdit = null }) {
                    Text(stringResource(R.string.loc_51bac044), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
        )
    }

    // Warning dialog before showing seed phrase
    if (showWarningDialog && walletToView != null) {
        val warningTitle = stringResource(R.string.loc_80575606)
        val warningIntro =
            stringResource(R.string.wallet_view_sensitive_info_message, walletToView?.name.orEmpty())
        val warningRecovery = stringResource(R.string.loc_f9a99af0)
        val warningPrivacy = stringResource(R.string.loc_79618ad9)
        val warningConfirm = stringResource(R.string.loc_f2415d81)
        val warningCancel = stringResource(R.string.loc_51bac044)
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
                    warningTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column {
                    Text(
                        warningIntro,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        warningRecovery,
                        color = ErrorRed,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        warningPrivacy,
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
                    Text(warningConfirm)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        walletToView = null
                    },
                ) {
                    Text(warningCancel, color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
        )
    }

    // Full Sync confirmation dialog
    if (walletToSync != null) {
        IbisConfirmDialog(
            onDismissRequest = { walletToSync = null },
            title = stringResource(R.string.loc_f08db23b),
            message = stringResource(R.string.loc_b5f801e0),
            confirmText = stringResource(R.string.loc_f08db23b),
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
            walletSupportsSpark = layer2Enabled &&
                walletForLabels?.isWatchOnly == false &&
                isSparkEnabledForWallet(walletForLabels!!.id),
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

    if (walletForMessages != null) {
        SignVerifyMessageDialog(
            wallet = walletForMessages!!,
            onDismiss = { walletForMessages = null },
            onSignMessage = onSignMessage,
            onVerifyMessage = onVerifyMessage,
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
                    .padding(vertical = 4.dp),
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
                text = stringResource(R.string.loc_bcb6fe62),
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
                        text = stringResource(R.string.loc_027001ed),
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
                        text = stringResource(R.string.loc_9ae2cb2b),
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
                        text = stringResource(R.string.loc_ddfe3fc8),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.loc_2311c49c),
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
                                text = stringResource(R.string.loc_027001ed),
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
                                text = stringResource(R.string.loc_9ae2cb2b),
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
                                onMessages = { walletForMessages = wallet },
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
                                liquidLayer2Enabled = liquidLayer2Enabled,
                                sparkLayer2Enabled = sparkLayer2Enabled,
                                isLiquidEnabled = isLiquidEnabledForWallet(wallet.id),
                                onSetLiquidEnabled = { enabled ->
                                    onSetLiquidEnabledForWallet(wallet.id, enabled)
                                },
                                isSparkEnabled = isSparkEnabledForWallet(wallet.id),
                                onSetSparkEnabled = { enabled ->
                                    onSetSparkEnabledForWallet(wallet.id, enabled)
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
    MULTISIG_DESCRIPTOR,
    LOCAL_COSIGNER,
}

// ==================== BIP 329 Labels Dialog ====================

@Composable
private fun Bip329ScopeChoiceButton(
    label: String,
    scope: Bip329LabelScope,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(8.dp),
        border =
            BorderStroke(
                1.dp,
                if (isSelected) {
                    when (scope) {
                        Bip329LabelScope.BITCOIN -> BitcoinOrange
                        Bip329LabelScope.LIQUID -> LiquidTeal
                        Bip329LabelScope.SPARK, Bip329LabelScope.BOTH -> SparkPurple
                    }
                } else {
                    BorderColor
                },
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor =
                    if (isSelected) {
                        when (scope) {
                            Bip329LabelScope.BITCOIN -> BitcoinOrange
                            Bip329LabelScope.LIQUID -> LiquidTeal
                            Bip329LabelScope.SPARK, Bip329LabelScope.BOTH -> SparkPurple
                        }
                    } else {
                        TextSecondary
                    },
            ),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Bip329LabelCountTally(
    count: Int,
    caption: String,
    numberColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = numberColor,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SignVerifyMessageDialog(
    wallet: WalletInfo,
    onDismiss: () -> Unit,
    onSignMessage: (walletId: String, address: String, message: String) -> WalletResult<String>,
    onVerifyMessage: (address: String, message: String, signature: String) -> WalletResult<Boolean>,
) {
    val context = LocalContext.current
    val verifySuccessText = stringResource(R.string.loc_b137008)
    val verifyFailureText = stringResource(R.string.loc_b137009)
    val scope = rememberCoroutineScope()
    val signSupported =
        !wallet.isWatchOnly &&
            !wallet.isWatchAddress &&
            !wallet.isMultisig &&
            !wallet.isLiquidWatchOnly &&
            !wallet.typeDescription.contains("Taproot", ignoreCase = true) &&
            !wallet.derivationPath.contains("86'")
    var isSignMode by remember(wallet.id) { mutableStateOf(signSupported) }
    var signAddress by remember(wallet.id) { mutableStateOf("") }
    var signMessage by remember(wallet.id) { mutableStateOf("") }
    var signature by remember(wallet.id) { mutableStateOf("") }
    var signError by remember(wallet.id) { mutableStateOf<String?>(null) }
    var verifyAddress by remember(wallet.id) { mutableStateOf("") }
    var verifyMessage by remember(wallet.id) { mutableStateOf("") }
    var verifySignature by remember(wallet.id) { mutableStateOf("") }
    var verifyResult by remember(wallet.id) { mutableStateOf<String?>(null) }
    var isWorking by remember(wallet.id) { mutableStateOf(false) }
    var showSignAddressQrScanner by remember(wallet.id) { mutableStateOf(false) }
    var showVerifyAddressQrScanner by remember(wallet.id) { mutableStateOf(false) }
    var signSignatureCopied by remember(wallet.id) { mutableStateOf(false) }

    LaunchedEffect(signSignatureCopied) {
        if (!signSignatureCopied) return@LaunchedEffect
        delay(1_500)
        signSignatureCopied = false
    }

    fun runMessageAction(action: () -> Unit) {
        if (isWorking) return
        isWorking = true
        scope.launch {
            try {
                action()
            } finally {
                isWorking = false
            }
        }
    }

    if (showSignAddressQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                signAddress =
                    try {
                        parseBip21Uri(code).address
                    } catch (_: IllegalArgumentException) {
                        // Duplicate keys in BIP21 URI — fall back to the raw
                        // input so the address validator rejects it.
                        code
                    }
                signError = null
                showSignAddressQrScanner = false
            },
            onDismiss = { showSignAddressQrScanner = false },
        )
    }

    if (showVerifyAddressQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code ->
                verifyAddress =
                    try {
                        parseBip21Uri(code).address
                    } catch (_: IllegalArgumentException) {
                        code
                    }
                verifyResult = null
                showVerifyAddressQrScanner = false
            },
            onDismiss = { showVerifyAddressQrScanner = false },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_b137001),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.loc_d2c0aec0),
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

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SignVerifyModeButton(
                        label = stringResource(R.string.loc_b137002),
                        selected = isSignMode,
                        enabled = signSupported && !isWorking,
                        onClick = { isSignMode = true },
                        modifier = Modifier.weight(1f),
                    )
                    SignVerifyModeButton(
                        label = stringResource(R.string.loc_b137003),
                        selected = !isSignMode,
                        enabled = !isWorking,
                        onClick = { isSignMode = false },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isSignMode) {
                    MessageTextField(
                        value = signAddress,
                        onValueChange = {
                            signAddress = it
                            signError = null
                        },
                        label = stringResource(R.string.loc_c2f3561d),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showSignAddressQrScanner = true }) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = stringResource(R.string.loc_59b2cdc5),
                                    tint = TextSecondary,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MessageTextField(
                        value = signMessage,
                        onValueChange = {
                            signMessage = it
                            signError = null
                        },
                        label = stringResource(R.string.loc_b137005),
                        minLines = 4,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick = {
                            runMessageAction {
                                when (val result = onSignMessage(wallet.id, signAddress, signMessage)) {
                                    is WalletResult.Success -> {
                                        signature = result.data
                                        signError = null
                                        verifyAddress = signAddress
                                        verifyMessage = signMessage
                                        verifySignature = result.data
                                    }
                                    is WalletResult.Error -> {
                                        signature = ""
                                        signError = result.message
                                    }
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        enabled = signSupported && !isWorking,
                    ) {
                        Text(stringResource(R.string.loc_b137002))
                    }

                    if (signError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(signError!!, style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }

                    if (signature.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        MessageTextField(
                            value = signature,
                            onValueChange = { signature = it },
                            label = stringResource(R.string.loc_b137007),
                            minLines = 3,
                            bottomEndIcon = {
                                IconButton(
                                    onClick = {
                                        SecureClipboard.copyAndScheduleClear(context, signature)
                                        signSignatureCopied = true
                                    },
                                    enabled = signature.isNotBlank(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.loc_ed8814bc),
                                        tint = if (signSignatureCopied) SuccessGreen else TextSecondary,
                                    )
                                }
                            },
                        )
                        if (signSignatureCopied) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.loc_ad35e265),
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen,
                            )
                        }
                    }
                } else {
                    MessageTextField(
                        value = verifyAddress,
                        onValueChange = {
                            verifyAddress = it
                            verifyResult = null
                        },
                        label = stringResource(R.string.loc_c2f3561d),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showVerifyAddressQrScanner = true }) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = stringResource(R.string.loc_59b2cdc5),
                                    tint = TextSecondary,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MessageTextField(
                        value = verifyMessage,
                        onValueChange = {
                            verifyMessage = it
                            verifyResult = null
                        },
                        label = stringResource(R.string.loc_b137005),
                        minLines = 4,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MessageTextField(
                        value = verifySignature,
                        onValueChange = {
                            verifySignature = it
                            verifyResult = null
                        },
                        label = stringResource(R.string.loc_b137007),
                        minLines = 3,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    IbisButton(
                        onClick = {
                            runMessageAction {
                                verifyResult = when (val result = onVerifyMessage(verifyAddress, verifyMessage, verifySignature)) {
                                    is WalletResult.Success ->
                                        if (result.data) {
                                            verifySuccessText
                                        } else {
                                            verifyFailureText
                                        }
                                    is WalletResult.Error -> result.message
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        enabled = !isWorking,
                    ) {
                        Text(stringResource(R.string.loc_b137003))
                    }
                    if (verifyResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = verifyResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (verifyResult == verifySuccessText) {
                                    SuccessGreen
                                } else {
                                    ErrorRed
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(8.dp))

                IbisButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SignVerifyModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border =
            BorderStroke(
                1.dp,
                when {
                    selected -> BitcoinOrange
                    else -> BorderColor
                },
            ),
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = if (selected) BitcoinOrange else TextSecondary,
                disabledContentColor = TextSecondary.copy(alpha = 0.35f),
            ),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    trailingIcon: (@Composable () -> Unit)? = null,
    bottomEndIcon: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = TextSecondary) },
            singleLine = singleLine,
            minLines = minLines,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BitcoinOrange,
                    unfocusedBorderColor = BorderColor,
                    cursorColor = BitcoinOrange,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        bottomEndIcon?.let { icon ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp),
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun Bip329LabelsDialog(
    wallet: WalletInfo,
    walletSupportsLiquid: Boolean,
    walletSupportsSpark: Boolean,
    onDismiss: () -> Unit,
    onExportToFile: (walletId: String, uri: Uri, scope: Bip329LabelScope) -> Unit,
    onImportFromFile: (walletId: String, uri: Uri, scope: Bip329LabelScope) -> Unit,
    onImportFromQr: (walletId: String, content: String, scope: Bip329LabelScope) -> Unit,
    getLabelsContent: (walletId: String, scope: Bip329LabelScope) -> String,
    getLabelCounts: (walletId: String) -> Bip329LabelCounts,
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val counts = remember(wallet.id) { getLabelCounts(wallet.id) }
    var selectedScope by remember(wallet.id, walletSupportsLiquid, walletSupportsSpark) {
        mutableStateOf(Bip329LabelScope.BITCOIN)
    }
    val bitcoinLabelCount = counts.totalCount(Bip329LabelScope.BITCOIN)
    val liquidLabelCount = counts.totalCount(Bip329LabelScope.LIQUID)
    val sparkLabelCount = counts.totalCount(Bip329LabelScope.SPARK)
    val totalCount = counts.totalCount(selectedScope)
    val canUseLiquidLabelScope = walletSupportsLiquid || liquidLabelCount > 0
    val canUseSparkLabelScope = walletSupportsSpark || sparkLabelCount > 0
    val walletSupportsLayer2Labels = canUseLiquidLabelScope || canUseSparkLabelScope

    // QR display state
    var showQrExport by remember(wallet.id, selectedScope) { mutableStateOf(false) }
    var labelsBytes by remember(wallet.id, selectedScope) { mutableStateOf<ByteArray?>(null) }

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
                        text = stringResource(R.string.loc_384d3cf0),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.loc_d2c0aec0),
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

                if (walletSupportsLayer2Labels) {
                    Text(
                        text = stringResource(R.string.loc_c16e7ad7),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Bip329ScopeChoiceButton(
                                label = stringResource(R.string.loc_197cebf2),
                                scope = Bip329LabelScope.BITCOIN,
                                isSelected = selectedScope == Bip329LabelScope.BITCOIN,
                                onClick = { selectedScope = Bip329LabelScope.BITCOIN },
                                modifier = Modifier.weight(1f),
                            )
                            Bip329ScopeChoiceButton(
                                label = stringResource(R.string.loc_22236665),
                                scope = Bip329LabelScope.LIQUID,
                                isSelected = selectedScope == Bip329LabelScope.LIQUID,
                                onClick = { selectedScope = Bip329LabelScope.LIQUID },
                                modifier = Modifier.weight(1f),
                                enabled = canUseLiquidLabelScope,
                            )
                            Bip329ScopeChoiceButton(
                                label = stringResource(R.string.loc_85f5955f),
                                scope = Bip329LabelScope.SPARK,
                                isSelected = selectedScope == Bip329LabelScope.SPARK,
                                onClick = { selectedScope = Bip329LabelScope.SPARK },
                                modifier = Modifier.weight(1f),
                                enabled = canUseSparkLabelScope,
                            )
                        }
                        Bip329ScopeChoiceButton(
                            label = stringResource(R.string.wallet_all_layers),
                            scope = Bip329LabelScope.BOTH,
                            isSelected = selectedScope == Bip329LabelScope.BOTH,
                            onClick = { selectedScope = Bip329LabelScope.BOTH },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Label counts (addr + tx records; shown as one total per network)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    if (walletSupportsLayer2Labels && selectedScope == Bip329LabelScope.BOTH) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Bip329LabelCountTally(
                                    count = bitcoinLabelCount,
                                    caption = stringResource(R.string.loc_197cebf2),
                                    numberColor = BitcoinOrange,
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Bip329LabelCountTally(
                                    count = liquidLabelCount,
                                    caption = stringResource(R.string.loc_22236665),
                                    numberColor = LiquidTeal,
                                )
                            }
                            if (canUseSparkLabelScope) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Bip329LabelCountTally(
                                        count = sparkLabelCount,
                                        caption = stringResource(R.string.loc_85f5955f),
                                        numberColor = SparkPurple,
                                    )
                                }
                            }
                        }
                    } else {
                        val count =
                            when (selectedScope) {
                                Bip329LabelScope.LIQUID -> liquidLabelCount
                                Bip329LabelScope.SPARK -> sparkLabelCount
                                else -> bitcoinLabelCount
                            }
                        val numberColor =
                            when (selectedScope) {
                                Bip329LabelScope.LIQUID -> LiquidTeal
                                Bip329LabelScope.SPARK -> SparkPurple
                                else -> BitcoinOrange
                            }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Bip329LabelCountTally(
                                count = count,
                                caption = stringResource(R.string.loc_b27d0727),
                                numberColor = numberColor,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Import section
                Text(
                    text = stringResource(R.string.loc_9ae2cb2b),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text =
                        when (selectedScope) {
                            Bip329LabelScope.LIQUID -> "BIP 329 (.jsonl) for Liquid labels"
                            Bip329LabelScope.SPARK -> "BIP 329 (.jsonl) for Spark labels"
                            else -> "BIP 329 (.jsonl) or Electrum CSV"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            mainActivity?.skipNextBackgroundLockForActivityResult()
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
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.loc_2cad992e), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { showQrScanner = true },
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.loc_60129540), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Export section
                Text(
                    text = stringResource(R.string.loc_452013a2),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.loc_60c57977),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val suffix =
                                when (selectedScope) {
                                    Bip329LabelScope.BITCOIN -> "bitcoin"
                                    Bip329LabelScope.LIQUID -> "liquid"
                                    Bip329LabelScope.SPARK -> "spark"
                                    Bip329LabelScope.BOTH -> "combined"
                                }
                            val fileName = "${wallet.name.replace(" ", "_")}_${suffix}_labels.jsonl"
                            mainActivity?.skipNextBackgroundLockForActivityResult()
                            exportFilePicker.launch(fileName)
                        },
                        modifier =
                            Modifier
                                .weight(1f)
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
                        Text(stringResource(R.string.loc_2cad992e), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                .weight(1f)
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
                        Text(stringResource(R.string.loc_98f4c3f7), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                Text(stringResource(R.string.loc_bbb17663), color = TextSecondary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                IbisButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.loc_d2c0aec0), style = MaterialTheme.typography.titleMedium)
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
    val hasMultisigDescriptor = keyMaterialInfo.multisigConfig != null
    val hasLocalCosigner = keyMaterialInfo.localCosignerKeyMaterial != null

    // Current key material based on selection
    val currentKeyMaterial =
        when (selectedViewType) {
            KeyViewType.SEED_PHRASE -> keyMaterialInfo.mnemonic
            KeyViewType.EXTENDED_PUBLIC_KEY -> keyMaterialInfo.extendedPublicKey
            KeyViewType.PRIVATE_KEY -> keyMaterialInfo.privateKey
            KeyViewType.WATCH_ADDRESS -> keyMaterialInfo.watchAddress
            KeyViewType.LIQUID_DESCRIPTOR ->
                keyMaterialInfo.liquidDescriptor?.let(BitcoinUtils::formatLiquidDescriptorForDisplay)
            KeyViewType.MULTISIG_DESCRIPTOR ->
                keyMaterialInfo.multisigConfig?.let {
                    listOf(it.externalDescriptor, it.internalDescriptor).joinToString("\n")
                }
            KeyViewType.LOCAL_COSIGNER -> keyMaterialInfo.localCosignerKeyMaterial
            null -> null
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
        qrBitmap = null
        if (showQrCode && selectedViewType != null && currentKeyMaterial != null) {
            qrBitmap =
                withContext(Dispatchers.Default) {
                    generateQrBitmap(currentKeyMaterial)
                }
        }
    }

    // Reset copied state after 3 seconds
    LaunchedEffect(copied) {
        if (copied) {
            delay(3000)
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
                stringResource(R.string.loc_0917a3e1),
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
                                text = stringResource(R.string.loc_f0f54738),
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
                                Text(stringResource(R.string.loc_24d8d452))
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
                                Text(stringResource(R.string.loc_66008e32))
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
                                    Text(stringResource(R.string.loc_bf0bb69f))
                                }
                            }

                            if (hasMultisigDescriptor) {
                                IbisButton(
                                    onClick = { selectedViewType = KeyViewType.MULTISIG_DESCRIPTOR },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.loc_3e248346))
                                }
                            }

                            if (hasLocalCosigner) {
                                IbisButton(
                                    onClick = { selectedViewType = KeyViewType.LOCAL_COSIGNER },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.loc_9589b41e))
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
                                    Text(stringResource(R.string.loc_e109877e))
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
                                    Text(stringResource(R.string.loc_8ca7adae))
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
                                    text = stringResource(R.string.loc_7b07ecad),
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
                                            contentDescription = stringResource(R.string.loc_6e2afb3f),
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

                        val localizedQrSubtitle =
                            when (selectedViewType) {
                                KeyViewType.SEED_PHRASE -> stringResource(R.string.loc_24d8d452)
                                KeyViewType.EXTENDED_PUBLIC_KEY -> stringResource(R.string.loc_66008e32)
                                KeyViewType.LIQUID_DESCRIPTOR -> stringResource(R.string.loc_bf0bb69f)
                                KeyViewType.MULTISIG_DESCRIPTOR -> stringResource(R.string.loc_3e248346)
                                KeyViewType.LOCAL_COSIGNER -> stringResource(R.string.loc_9589b41e)
                                KeyViewType.PRIVATE_KEY -> stringResource(R.string.loc_c4a5346c)
                                KeyViewType.WATCH_ADDRESS -> stringResource(R.string.loc_0d4e6f81)
                                else -> stringResource(R.string.loc_0d4e6f81)
                            }
                        Text(
                            text = stringResource(R.string.key_material_scan_qr_format, localizedQrSubtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if ((selectedViewType == KeyViewType.EXTENDED_PUBLIC_KEY ||
                            selectedViewType == KeyViewType.LIQUID_DESCRIPTOR ||
                            selectedViewType == KeyViewType.MULTISIG_DESCRIPTOR ||
                            selectedViewType == KeyViewType.LOCAL_COSIGNER ||
                            selectedViewType == KeyViewType.PRIVATE_KEY ||
                            selectedViewType == KeyViewType.WATCH_ADDRESS) && currentKeyMaterial != null
                    ) {
                        // Descriptor/key material views are shown as raw text for exact import/export.
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
                                    SecureClipboard.copyAndScheduleClear(context, it)
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
                                text = stringResource(R.string.loc_6e2afb3f),
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
                            text = stringResource(R.string.loc_ad04894c),
                            color = TextSecondary,
                        )
                    }

                    if (copied) {
                        Text(
                            text = stringResource(R.string.loc_795ed2a1),
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
                Text(stringResource(R.string.loc_d2c0aec0), color = BitcoinOrange)
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
    onMessages: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    onSync: () -> Unit = {},
    onLabels: () -> Unit = {},
    isSyncing: Boolean = false,
    showDragHandle: Boolean = false,
    onMeasured: (Float) -> Unit = {},
    layer2Enabled: Boolean = false,
    liquidLayer2Enabled: Boolean = layer2Enabled,
    sparkLayer2Enabled: Boolean = layer2Enabled,
    isLiquidEnabled: Boolean = false,
    onSetLiquidEnabled: (Boolean) -> Unit = {},
    isSparkEnabled: Boolean = false,
    onSetSparkEnabled: (Boolean) -> Unit = {},
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
    val showLiquidToggle = wallet.isLiquidWatchOnly || (liquidLayer2Enabled && isBip39Wallet)
    val showSparkToggle = sparkLayer2Enabled && isBip39Wallet
    val managementActionsEnabled = !wallet.isLocked
    val liquidToggleEnabled = !wallet.isLocked && !wallet.isLiquidWatchOnly
    val sparkToggleEnabled = !wallet.isLocked
    val liquidChecked = wallet.isLiquidWatchOnly || isLiquidEnabled
    val showWalletLockButton = isWalletLockAvailable || wallet.isLocked
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
                        if (showWalletLockButton) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Icon(
                                    imageVector = if (wallet.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription =
                                        if (wallet.isLocked) {
                                            stringResource(R.string.loc_6d48b5d9)
                                        } else {
                                            stringResource(R.string.loc_27bd3430)
                                        },
                                    tint =
                                        if (wallet.isLocked) {
                                            lockedPrimaryTextColor
                                        } else {
                                            TextSecondary.copy(alpha = if (isWalletLockAvailable) 0.8f else 0.45f)
                                        },
                                    modifier =
                                        Modifier
                                            .size(20.dp)
                                            .clickable(enabled = isWalletLockAvailable) {
                                                onSetWalletLocked(!wallet.isLocked)
                                            },
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Text(
                            text = wallet.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (wallet.isLocked) lockedPrimaryTextColor else MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        when {
                            wallet.isMultisig -> Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = stringResource(R.string.loc_6dfe3462),
                                tint = if (wallet.isLocked) lockedPrimaryTextColor else BitcoinOrange,
                                modifier = Modifier.size(16.dp),
                            )
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
                                    text = stringResource(R.string.loc_4cb2f934),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BitcoinOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val walletKind =
                        when {
                            wallet.isMultisig -> "Multisig"
                            wallet.isWatchAddress -> "Watch Address"
                            wallet.isPrivateKey -> "Private Key"
                            wallet.isWatchOnly -> "Watch Only"
                            else -> "Seed Phrase"
                        }
                    Text(
                        text = "${wallet.typeDescription} - $walletKind",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                        color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (wallet.derivationPath != "single") {
                        Text(
                            text = stringResource(R.string.common_derivation_path_format, wallet.derivationPath),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                            color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary.copy(alpha = 0.92f),
                        )
                    }

                    if (wallet.masterFingerprint != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.common_fingerprint_format,
                                    wallet.masterFingerprint,
                                ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                            color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary.copy(alpha = 0.92f),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val lastSyncFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
                    val lastSyncText =
                        if (wallet.lastFullSyncTime != null) {
                            stringResource(
                                R.string.wallet_last_full_sync_format,
                                lastSyncFormatter.format(Date(wallet.lastFullSyncTime)),
                            )
                        } else {
                            "Never fully synced"
                        }
                    Text(
                        text = lastSyncText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = if (wallet.isLocked) lockedSecondaryTextColor else TextSecondary.copy(alpha = 0.92f),
                    )
                    if (showWalletLockButton || showLiquidToggle || showSparkToggle) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showLiquidToggle) {
                                Text(
                                    text = stringResource(R.string.loc_22236665),
                                    modifier =
                                        Modifier.clickable(enabled = liquidToggleEnabled) {
                                            if (!wallet.isLiquidWatchOnly) {
                                                onSetLiquidEnabled(!liquidChecked)
                                            }
                                        },
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
                            if (showLiquidToggle && showSparkToggle) {
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            if (showSparkToggle) {
                                Text(
                                    text = stringResource(R.string.loc_85f5955f),
                                    modifier =
                                        Modifier.clickable(enabled = sparkToggleEnabled) {
                                            onSetSparkEnabled(!isSparkEnabled)
                                        },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    color =
                                        if (wallet.isLocked) {
                                            lockedSecondaryTextColor
                                        } else if (isSparkEnabled) {
                                            SparkPurple
                                        } else {
                                            TextSecondary.copy(alpha = if (sparkToggleEnabled) 0.8f else 0.55f)
                                        },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                SquareToggle(
                                    checked = isSparkEnabled,
                                    onCheckedChange = onSetSparkEnabled,
                                    enabled = sparkToggleEnabled,
                                    checkedColor = SparkPurple,
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
                                    contentDescription = stringResource(R.string.loc_f08db23b),
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
                                contentDescription = stringResource(R.string.loc_4f49447d),
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
                                imageVector = Icons.Default.Key,
                                contentDescription = stringResource(R.string.loc_85a65da2),
                                tint = actionTint,
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .rotate(90f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom row: Labels, Sign/Verify, Delete
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
                                contentDescription = stringResource(R.string.loc_2282451b),
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
                                    .clickable(enabled = managementActionsEnabled) { onMessages() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.loc_b137001),
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
                                contentDescription = stringResource(R.string.loc_3dbe79b1),
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
                    contentDescription = stringResource(R.string.loc_00ad5135),
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
