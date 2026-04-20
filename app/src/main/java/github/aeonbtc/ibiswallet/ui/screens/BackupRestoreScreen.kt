package github.aeonbtc.ibiswallet.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.components.ScrollableAlertDialog
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupWalletEntry(
    val id: String,
    val name: String,
    val type: String,
    val isWatchOnly: Boolean,
    val hasLabels: Boolean = true,
)

data class FullBackupPreview(
    val wallets: List<BackupWalletEntry>,
    val hasServers: Boolean,
    val hasLiquidServers: Boolean,
    val hasAppSettings: Boolean,
    val exportedAt: String,
) {
    val walletNames: List<String>
        get() = wallets.map { it.name }

    val walletCount: Int
        get() = wallets.size

    val hasLabels: Boolean
        get() = wallets.any { it.hasLabels }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    wallets: List<BackupWalletEntry>,
    onBack: () -> Unit,
    onExportFullBackup:
        (
            uri: Uri,
            walletIds: List<String>,
            labelWalletIds: List<String>,
            includeServers: Boolean,
            includeAppSettings: Boolean,
            password: String?,
        ) -> Unit,
    onParseFullBackup: suspend (uri: Uri, password: String?) -> FullBackupPreview,
    onImportFullBackup:
        (
            uri: Uri,
            password: String?,
            walletIds: List<String>,
            labelWalletIds: List<String>,
            importServers: Boolean,
            importAppSettings: Boolean,
        ) -> Unit,
    isLoading: Boolean = false,
    resultMessage: String? = null,
    onClearResult: () -> Unit = {},
) {
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Backup / Restore",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (resultMessage != null) {
                ResultBanner(
                    message = resultMessage,
                    onDismiss = onClearResult,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            BackupRestoreActionCard(
                title = "Create Backup",
                description = "Create a full app backup with wallets, labels, server settings, and general app settings. Security settings are excluded.",
                actionText = "Backup",
                enabled = !isLoading,
                onClick = {
                    showRestoreDialog = false
                    showBackupDialog = true
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            BackupRestoreActionCard(
                title = "Restore Backup",
                description = "Restore a full app backup, review its contents, and choose what to restore. Security settings are excluded.",
                actionText = "Restore",
                enabled = !isLoading,
                onClick = {
                    showBackupDialog = false
                    showRestoreDialog = true
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showBackupDialog) {
        BackupDialog(
            wallets = wallets,
            onDismiss = { showBackupDialog = false },
            onExport = onExportFullBackup,
            isLoading = isLoading,
        )
    }

    if (showRestoreDialog) {
        RestoreDialog(
            onDismiss = { showRestoreDialog = false },
            onParseFullBackup = onParseFullBackup,
            onImportFullBackup = onImportFullBackup,
            isLoading = isLoading,
        )
    }
}

@Composable
private fun BackupRestoreActionCard(
    title: String,
    description: String,
    actionText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            IbisButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun BackupDialog(
    wallets: List<BackupWalletEntry>,
    onDismiss: () -> Unit,
    onExport:
        (
            uri: Uri,
            walletIds: List<String>,
            labelWalletIds: List<String>,
            includeServers: Boolean,
            includeAppSettings: Boolean,
            password: String?,
        ) -> Unit,
    isLoading: Boolean,
) {
    val context = LocalContext.current
    val selectedWallets = remember(wallets) {
        mutableStateMapOf<String, Boolean>().apply {
            wallets.forEach { wallet ->
                this[wallet.id] = true
            }
        }
    }
    val walletLabels = remember(wallets) {
        mutableStateMapOf<String, Boolean>().apply {
            wallets.forEach { wallet ->
                this[wallet.id] = true
            }
        }
    }

    var includeServers by remember { mutableStateOf(true) }
    var includeAppSettings by remember { mutableStateOf(true) }
    var encryptBackup by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }

    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val suggestedFileName = "ibis-full-backup-$dateStr.json"

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            exportUri = uri
        }
    }

    val selectedEntries = wallets.filter { selectedWallets[it.id] == true }
    val selectedIds = selectedEntries.map { it.id }
    val labelWalletIds = selectedIds.filter { walletLabels[it] == true }
    val passwordsMatch = password == confirmPassword
    val passwordLongEnough = password.length >= 8
    val encryptionValid = !encryptBackup || (passwordLongEnough && passwordsMatch)
    val canExport = exportUri != null && selectedIds.isNotEmpty() && encryptionValid && !isLoading

    ScrollableAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Backup",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column {
                Text(
                    text = "Review exactly what will be exported before saving the file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailCard(title = "Wallets") {
                    if (wallets.isEmpty()) {
                        Text(
                            text = "No wallets available",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    } else {
                        wallets.forEachIndexed { index, wallet ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            BackupWalletOptionCard(
                                title = wallet.name,
                                subtitle = wallet.type + if (wallet.isWatchOnly) " (watch-only)" else "",
                                selected = selectedWallets[wallet.id] == true,
                                labelsEnabled = walletLabels[wallet.id] == true,
                                onSelectedChange = { checked -> selectedWallets[wallet.id] = checked },
                                onLabelsChange = { checked -> walletLabels[wallet.id] = checked },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                DetailCard(title = "Settings") {
                    ToggleRow(
                        title = "Server Settings",
                        subtitle = "Electrum and Liquid server settings",
                        checked = includeServers,
                        onCheckedChange = { includeServers = it },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ToggleRow(
                        title = "General App Settings",
                        subtitle = "Notifications, external services, etc...",
                        checked = includeAppSettings,
                        onCheckedChange = { includeAppSettings = it },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                DetailCard(title = "Protection") {
                    ToggleRow(
                        title = "Encrypt Backup File",
                        subtitle = "Protect with a password",
                        checked = encryptBackup,
                        onCheckedChange = { encryptBackup = it },
                    )

                    if (encryptBackup) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation =
                                if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector =
                                            if (showPassword) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                        contentDescription = if (showPassword) "Hide" else "Show",
                                        tint = TextSecondary,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            singleLine = true,
                            visualTransformation =
                                if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        )
                        if (password.isNotEmpty() && !encryptionValid) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text =
                                    if (!passwordLongEnough) {
                                        "Min 8 characters"
                                    } else {
                                        "Passwords do not match"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { filePickerLauncher.launch(suggestedFileName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    enabled = !isLoading,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (exportUri == null) "Choose Save Location" else "Change Save Location")
                }
            }
        },
        confirmButton = {
            IbisButton(
                onClick = {
                    exportUri?.let { uri ->
                        onExport(
                            uri,
                            selectedIds,
                            labelWalletIds,
                            includeServers,
                            includeAppSettings,
                            if (encryptBackup) password else null,
                        )
                        onDismiss()
                    }
                },
                enabled = canExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Export")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun RestoreDialog(
    onDismiss: () -> Unit,
    onParseFullBackup: suspend (uri: Uri, password: String?) -> FullBackupPreview,
    onImportFullBackup:
        (
            uri: Uri,
            password: String?,
            walletIds: List<String>,
            labelWalletIds: List<String>,
            importServers: Boolean,
            importAppSettings: Boolean,
        ) -> Unit,
    isLoading: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreFileName by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FullBackupPreview?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var isParsing by remember { mutableStateOf(false) }

    val selectedWallets = remember { mutableStateMapOf<String, Boolean>() }
    val walletLabels = remember { mutableStateMapOf<String, Boolean>() }
    var importServers by remember { mutableStateOf(true) }
    var importAppSettings by remember { mutableStateOf(true) }

    fun resetWalletSelections(wallets: List<BackupWalletEntry>) {
        selectedWallets.clear()
        walletLabels.clear()
        wallets.forEach { wallet ->
            selectedWallets[wallet.id] = true
            walletLabels[wallet.id] = wallet.hasLabels
        }
    }

    fun refreshPreview(uri: Uri, passwordValue: String?) {
        isParsing = true
        parseError = null
        scope.launch {
            try {
                val result = onParseFullBackup(uri, passwordValue)
                preview = result
                needsPassword = false
                parseError = null
                resetWalletSelections(result.wallets)
                importServers = result.hasServers || result.hasLiquidServers
                importAppSettings = result.hasAppSettings
            } catch (e: Exception) {
                val message = e.message ?: "Failed to parse backup"
                preview = null
                parseError = message
                selectedWallets.clear()
                walletLabels.clear()
                needsPassword =
                    message.contains("encrypted", ignoreCase = true) ||
                        message.contains("password", ignoreCase = true)
            } finally {
                isParsing = false
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            restoreUri = uri
            restoreFileName = try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            } ?: "backup.json"
            password = ""
            showPassword = false
            preview = null
            parseError = null
            needsPassword = false
            selectedWallets.clear()
            walletLabels.clear()
            importServers = true
            importAppSettings = true
            refreshPreview(uri, null)
        }
    }

    val previewWallets = preview?.wallets.orEmpty()
    val selectedWalletIds = previewWallets.filter { selectedWallets[it.id] == true }.map { it.id }
    val labelWalletIds =
        previewWallets.filter {
            selectedWallets[it.id] == true &&
                it.hasLabels &&
                walletLabels[it.id] == true
        }.map { it.id }

    val hasRestorableContent =
        preview?.let {
            selectedWalletIds.isNotEmpty() ||
                labelWalletIds.isNotEmpty() ||
                ((it.hasServers || it.hasLiquidServers) && importServers) ||
                (it.hasAppSettings && importAppSettings)
        } == true

    ScrollableAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restore Backup",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column {
                Text(
                    text = "Select a full backup and review what will be restored. Security settings are always skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    enabled = !isLoading && !isParsing,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (restoreUri == null) "Choose Backup File" else "Change Backup File")
                }

                if (restoreFileName != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailCard(title = "Selected File") {
                        SummaryRow(
                            label = "File",
                            value = restoreFileName ?: "",
                        )
                        if (preview != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            SummaryRow(
                                label = "Exported",
                                value = preview?.exportedAt ?: "Unknown",
                            )
                        }
                        if (isParsing) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "Reading backup contents...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }

                if (needsPassword) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailCard(title = "Encrypted Backup") {
                        Text(
                            text = "This backup is encrypted. Enter the password to view its contents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Backup Password") },
                            singleLine = true,
                            visualTransformation =
                                if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector =
                                            if (showPassword) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                        contentDescription = if (showPassword) "Hide" else "Show",
                                        tint = TextSecondary,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                cursorColor = BitcoinOrange,
                            ),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                restoreUri?.let { uri ->
                                    refreshPreview(uri, password.ifEmpty { null })
                                }
                            },
                            enabled = !isLoading && !isParsing && restoreUri != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                            ),
                        ) {
                            Text("Unlock Backup")
                        }
                    }
                }

                if (parseError != null && !needsPassword) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = parseError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                if (preview != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    DetailCard(title = "Wallets") {
                        if (previewWallets.isEmpty()) {
                            Text(
                                text = "No wallets in this backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        } else {
                            previewWallets.forEachIndexed { index, wallet ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                BackupWalletOptionCard(
                                    title = wallet.name,
                                    subtitle = wallet.type + if (wallet.isWatchOnly) " (watch-only)" else "",
                                    selected = selectedWallets[wallet.id] == true,
                                    labelsEnabled = walletLabels[wallet.id] == true,
                                    onSelectedChange = { checked ->
                                        selectedWallets[wallet.id] = checked
                                    },
                                    onLabelsChange = { checked ->
                                        walletLabels[wallet.id] = checked
                                    },
                                    labelsAvailable = wallet.hasLabels,
                                    unavailableLabelsText = "No labels in backup",
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailCard(title = "Settings") {
                        ToggleRow(
                            title = "Server Settings",
                            subtitle =
                                if (preview?.hasServers == true || preview?.hasLiquidServers == true) {
                                    "Electrum and Liquid server settings"
                                } else {
                                    "Not included in this backup"
                                },
                            checked = importServers,
                            onCheckedChange = { importServers = it },
                            enabled = preview?.hasServers == true || preview?.hasLiquidServers == true,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        ToggleRow(
                            title = "General App Settings",
                            subtitle =
                                if (preview?.hasAppSettings == true) {
                                    "Notifications, external services, etc..."
                                } else {
                                    "Not included in this backup"
                                },
                            checked = importAppSettings,
                            onCheckedChange = { importAppSettings = it },
                            enabled = preview?.hasAppSettings == true,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Existing wallets with the same seed will be skipped. Security settings are not restored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        },
        confirmButton = {
            IbisButton(
                onClick = {
                    restoreUri?.let { uri ->
                        onImportFullBackup(
                            uri,
                            password.ifEmpty { null },
                            selectedWalletIds,
                            labelWalletIds,
                            importServers,
                            importAppSettings,
                        )
                        onDismiss()
                    }
                },
                enabled = preview != null && hasRestorableContent && !isLoading && !isParsing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading && !isParsing,
            ) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun BackupWalletOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    labelsEnabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onLabelsChange: (Boolean) -> Unit,
    labelsAvailable: Boolean = true,
    unavailableLabelsText: String? = null,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BorderColor.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectedChange(!selected) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                SquareToggle(
                    checked = selected,
                    onCheckedChange = onSelectedChange,
                    trackWidth = 40.dp,
                    trackHeight = 22.dp,
                    thumbSize = 16.dp,
                )
            }

            if (selected) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = BorderColor.copy(alpha = 0.45f))
                Spacer(modifier = Modifier.height(8.dp))

                if (labelsAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLabelsChange(!labelsEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Labels",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        SquareToggle(
                            checked = labelsEnabled,
                            onCheckedChange = onLabelsChange,
                            modifier = Modifier.scale(0.78f),
                        )
                    }
                } else if (unavailableLabelsText != null) {
                    Text(
                        text = unavailableLabelsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground else TextSecondary.copy(alpha = 0.5f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.5f),
            )
        }
        BackupMainToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun BackupMainToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SquareToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        trackWidth = 40.dp,
        trackHeight = 22.dp,
        thumbSize = 16.dp,
    )
}

@Composable
private fun PreviewRow(
    label: String,
    detail: String,
    available: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = available) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (available) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        TextSecondary.copy(alpha = 0.5f)
                    },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 3,
            )
        }
        if (available) {
            BackupMainToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ResultBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    val isSuccess =
        message.startsWith("Success", ignoreCase = true) ||
            message.contains("exported", ignoreCase = true) ||
            message.contains("restored", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (isSuccess) {
                    SuccessGreen.copy(alpha = 0.15f)
                } else {
                    ErrorRed.copy(alpha = 0.15f)
                },
        ),
        onClick = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isSuccess) SuccessGreen else ErrorRed,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
