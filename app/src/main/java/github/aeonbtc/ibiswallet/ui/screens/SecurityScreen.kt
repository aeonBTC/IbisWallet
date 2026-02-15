package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.*
import github.aeonbtc.ibiswallet.util.QrFormatParser
import org.bitcoindevkit.Mnemonic

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    currentSecurityMethod: SecureStorage.SecurityMethod = SecureStorage.SecurityMethod.NONE,
    currentLockTiming: SecureStorage.LockTiming = SecureStorage.LockTiming.WHEN_MINIMIZED,
    isBiometricAvailable: Boolean = false,
    screenshotsDisabled: Boolean = false,
    isDuressEnabled: Boolean = false,
    isDuressMode: Boolean = false,
    hasWallet: Boolean = true,
    autoWipeThreshold: SecureStorage.AutoWipeThreshold = SecureStorage.AutoWipeThreshold.DISABLED,
    isCloakModeEnabled: Boolean = false,
    onSetPinCode: (String) -> Unit = {},
    onEnableBiometric: () -> Unit = {},
    onDisableSecurity: () -> Unit = {},
    onLockTimingChange: (SecureStorage.LockTiming) -> Unit = {},
    onScreenshotsDisabledChange: (Boolean) -> Unit = {},
    onSetupDuress: (
        pin: String,
        mnemonic: String,
        passphrase: String?,
        customDerivationPath: String?,
        addressType: AddressType,
    ) -> Unit = { _, _, _, _, _ -> },
    onDisableDuress: () -> Unit = {},
    onAutoWipeThresholdChange: (SecureStorage.AutoWipeThreshold) -> Unit = {},
    onEnableCloakMode: (code: String) -> Unit = {},
    onDisableCloakMode: () -> Unit = {},
    onPinSetupActiveChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var showPinSetup by remember { mutableStateOf(false) }
    var showDuressSetup by remember { mutableStateOf(false) }
    var showCloakSetup by remember { mutableStateOf(false) }

    // Notify parent when any PIN setup sub-screen is active
    val isPinSetupActive = showPinSetup || showDuressSetup || showCloakSetup
    LaunchedEffect(isPinSetupActive) {
        onPinSetupActiveChange(isPinSetupActive)
    }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }
    var showDisableDuressDialog by remember { mutableStateOf(false) }
    var showAutoWipeConfirmDialog by remember { mutableStateOf(false) }
    var showDisableCloakDialog by remember { mutableStateOf(false) }

    if (showCloakSetup) {
        CloakCodeSetupScreen(
            onCodeSet = { code ->
                onEnableCloakMode(code)
                showCloakSetup = false
            },
            onBack = { showCloakSetup = false },
        )
    } else if (showDuressSetup) {
        DuressSetupScreen(
            currentSecurityMethod = currentSecurityMethod,
            onDuressSet = { pin, mnemonic, passphrase, customDerivationPath, addressType ->
                onSetupDuress(pin, mnemonic, passphrase, customDerivationPath, addressType)
                showDuressSetup = false
            },
            onBack = { showDuressSetup = false },
        )
    } else if (showPinSetup) {
        PinSetupScreen(
            onPinSet = { pin ->
                onSetPinCode(pin)
                showPinSetup = false
            },
            onBack = {
                showPinSetup = false
            },
        )
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    text = "Security",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Options
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
                        text = "App Lock",
                        style = MaterialTheme.typography.titleMedium,
                        color = BitcoinOrange,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Biometric Option
                    val isBiometricEnabled = currentSecurityMethod == SecureStorage.SecurityMethod.BIOMETRIC
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = isBiometricAvailable) {
                                    if (!isBiometricEnabled) {
                                        onEnableBiometric()
                                    } else {
                                        showDisableConfirmDialog = true
                                    }
                                },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isBiometricAvailable) BitcoinOrange else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Biometric",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color =
                                        if (isBiometricAvailable) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            TextSecondary.copy(alpha = 0.5f)
                                        },
                                )
                                Text(
                                    text =
                                        if (isBiometricAvailable) {
                                            "Use fingerprint or face"
                                        } else {
                                            "Not available on this device"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }

                        SquareToggle(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    onEnableBiometric()
                                } else {
                                    showDisableConfirmDialog = true
                                }
                            },
                            enabled = isBiometricAvailable,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // PIN Code Option
                    val isPinEnabled = currentSecurityMethod == SecureStorage.SecurityMethod.PIN
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!isPinEnabled) {
                                        showPinSetup = true
                                    } else {
                                        showDisableConfirmDialog = true
                                    }
                                },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PIN code",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "$MIN_PIN_LENGTH\u2013$MAX_PIN_LENGTH digit unlock code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }

                        SquareToggle(
                            checked = isPinEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinSetup = true
                                } else {
                                    showDisableConfirmDialog = true
                                }
                            },
                        )
                    }

                    // Lock timing dropdown (only shown when security is enabled)
                    if (currentSecurityMethod != SecureStorage.SecurityMethod.NONE) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderColor,
                        )

                        Text(
                            text = "Lock Timer",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LockTimingDropdown(
                            currentTiming = currentLockTiming,
                            onTimingSelected = onLockTimingChange,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Duress PIN card
            // - Hidden entirely in duress mode (attacker shouldn't see it)
            // - Faded out when no security is enabled (with hint to enable PIN or biometric)
            // - Fully interactive when security is enabled
            if (!isDuressMode) {
                val isSecurityActive = currentSecurityMethod != SecureStorage.SecurityMethod.NONE
                val canEnableDuress = isSecurityActive && hasWallet

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
                            text = "Duress PIN",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canEnableDuress) BitcoinOrange else BitcoinOrange.copy(alpha = 0.4f),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (canEnableDuress) {
                                            Modifier.clickable {
                                                if (!isDuressEnabled) {
                                                    showDuressSetup = true
                                                } else {
                                                    showDisableDuressDialog = true
                                                }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (canEnableDuress) BitcoinOrange else TextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Duress PIN",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color =
                                            if (canEnableDuress) {
                                                MaterialTheme.colorScheme.onBackground
                                            } else {
                                                TextSecondary.copy(alpha = 0.4f)
                                            },
                                    )
                                    Text(
                                        text =
                                            if (!isSecurityActive) {
                                                "Set up PIN or biometric to enable"
                                            } else if (!hasWallet) {
                                                "Import a wallet first"
                                            } else {
                                                "Unlock Ibis into a decoy wallet"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                            if (canEnableDuress) {
                                                TextSecondary
                                            } else {
                                                TextSecondary.copy(
                                                    alpha = 0.4f,
                                                )
                                            },
                                    )
                                }
                            }

                            SquareToggle(
                                checked = isDuressEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showDuressSetup = true
                                    } else {
                                        showDisableDuressDialog = true
                                    }
                                },
                                enabled = canEnableDuress,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Auto-Wipe card
            // - Hidden in duress mode
            // - Faded when no security is enabled
            if (!isDuressMode) {
                val isSecurityActive = currentSecurityMethod != SecureStorage.SecurityMethod.NONE

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
                            text = "Auto-Wipe",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSecurityActive) BitcoinOrange else BitcoinOrange.copy(alpha = 0.4f),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint =
                                        if (isSecurityActive && autoWipeThreshold != SecureStorage.AutoWipeThreshold.DISABLED) {
                                            ErrorRed
                                        } else if (isSecurityActive) {
                                            BitcoinOrange
                                        } else {
                                            TextSecondary.copy(alpha = 0.4f)
                                        },
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Wipe after unlock attempts",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color =
                                            if (isSecurityActive) {
                                                MaterialTheme.colorScheme.onBackground
                                            } else {
                                                TextSecondary.copy(alpha = 0.4f)
                                            },
                                    )
                                    Text(
                                        text =
                                            if (isSecurityActive) {
                                                "Erase app data after failed unlock"
                                            } else {
                                                "Set up PIN code to enable"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                            if (isSecurityActive) {
                                                TextSecondary
                                            } else {
                                                TextSecondary.copy(
                                                    alpha = 0.4f,
                                                )
                                            },
                                    )
                                }
                            }

                            SquareToggle(
                                checked = autoWipeThreshold != SecureStorage.AutoWipeThreshold.DISABLED,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showAutoWipeConfirmDialog = true
                                    } else {
                                        onAutoWipeThresholdChange(SecureStorage.AutoWipeThreshold.DISABLED)
                                    }
                                },
                                enabled = isSecurityActive,
                            )
                        }

                        // Show attempt count selector when auto-wipe is enabled
                        if (autoWipeThreshold != SecureStorage.AutoWipeThreshold.DISABLED) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SecureStorage.AutoWipeThreshold.entries
                                    .filter { it != SecureStorage.AutoWipeThreshold.DISABLED }
                                    .forEach { threshold ->
                                        val isSelected = threshold == autoWipeThreshold
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) {
                                                            ErrorRed.copy(alpha = 0.15f)
                                                        } else {
                                                            Color.Transparent
                                                        },
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) ErrorRed else BorderColor,
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                    .clickable { onAutoWipeThresholdChange(threshold) }
                                                    .padding(vertical = 8.dp),
                                        ) {
                                            Text(
                                                text = "${threshold.attempts}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isSelected) ErrorRed else TextSecondary,
                                            )
                                        }
                                    }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Cloak Mode card — hidden in duress mode
            if (!isDuressMode) {
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
                            text = "Cloak Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = BitcoinOrange,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (!isCloakModeEnabled) {
                                            showCloakSetup = true
                                        } else {
                                            showDisableCloakDialog = true
                                        }
                                    },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = BitcoinOrange,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Disguise as calculator",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text =
                                            if (isCloakModeEnabled) {
                                                "App icon changes after restart"
                                            } else {
                                                "Hide Ibis behind a calculator app"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }

                            SquareToggle(
                                checked = isCloakModeEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showCloakSetup = true
                                    } else {
                                        showDisableCloakDialog = true
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Screenshot Prevention
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
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onScreenshotsDisabledChange(!screenshotsDisabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Disable Screenshots",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "Blocks screenshots and app previews",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }

                        SquareToggle(
                            checked = screenshotsDisabled,
                            onCheckedChange = onScreenshotsDisabledChange,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Disable Cloak Mode Confirmation Dialog
        if (showDisableCloakDialog) {
            AlertDialog(
                onDismissRequest = { showDisableCloakDialog = false },
                title = {
                    Text(
                        text = "Disable Cloak Mode?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                text = {
                    Text(
                        text = "The app icon will revert to Ibis Wallet after restart. You may need to re-add the homescreen shortcut.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDisableCloakMode()
                            showDisableCloakDialog = false
                        },
                    ) {
                        Text("Disable", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisableCloakDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(12.dp),
            )
        }

        // Disable Duress Confirmation Dialog
        if (showDisableDuressDialog) {
            AlertDialog(
                onDismissRequest = { showDisableDuressDialog = false },
                title = {
                    Text(
                        text = "Disable Duress PIN?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                text = {
                    Text(
                        text = "The decoy wallet will be removed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDisableDuress()
                            showDisableDuressDialog = false
                        },
                    ) {
                        Text("Disable", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisableDuressDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(12.dp),
            )
        }

        // Enable Auto-Wipe Confirmation Dialog
        if (showAutoWipeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showAutoWipeConfirmDialog = false },
                title = {
                    Text(
                        text = "Enable Auto-Wipe?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "All wallet data will be permanently erased after repeated failed unlock attempts. This cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Text(
                            text = "Make sure your seed phrases are backed up. Without a backup, your funds will be lost permanently.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onAutoWipeThresholdChange(SecureStorage.AutoWipeThreshold.AFTER_10)
                            showAutoWipeConfirmDialog = false
                        },
                    ) {
                        Text("Enable", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAutoWipeConfirmDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(12.dp),
            )
        }

        // Disable Security Confirmation Dialog
        if (showDisableConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDisableConfirmDialog = false },
                title = {
                    Text(
                        text = "Disable Security?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                text = {
                    Text(
                        text = "Anyone with access to your device will be able to open the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDisableSecurity()
                            showDisableConfirmDialog = false
                        },
                    ) {
                        Text("Disable", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisableConfirmDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun PinSetupScreen(
    onPinSet: (String) -> Unit,
    onBack: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 = enter PIN, 2 = confirm PIN
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DarkBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (step == 2) {
                        step = 1
                        confirmPin = ""
                        error = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = if (step == 1) "Create PIN" else "Confirm PIN",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    when {
                        step == 1 -> "Enter a $MIN_PIN_LENGTH\u2013$MAX_PIN_LENGTH digit PIN"
                        else -> "Enter the PIN again to confirm"
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // PIN dots indicator - only show entered digits
            val currentPin = if (step == 1) pin else confirmPin
            if (currentPin.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(currentPin.length) {
                        Box(
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(BitcoinOrange),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }

            // Error message
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Number pad
            PinNumberPad(
                onNumberClick = { number ->
                    val maxLen = if (step == 2) pin.length else MAX_PIN_LENGTH
                    val currentPin = if (step == 1) pin else confirmPin
                    if (currentPin.length < maxLen) {
                        if (step == 1) {
                            pin += number
                            error = null
                        } else {
                            confirmPin += number
                            error = null
                        }
                    }
                },
                onBackspaceClick = {
                    if (step == 1 && pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        error = null
                    } else if (step == 2 && confirmPin.isNotEmpty()) {
                        confirmPin = confirmPin.dropLast(1)
                        error = null
                    }
                },
                onConfirmClick =
                    if (step == 1 && pin.length >= MIN_PIN_LENGTH) {
                        {
                            step = 2
                            error = null
                        }
                    } else if (step == 2 && confirmPin.length == pin.length) {
                        {
                            if (pin == confirmPin) {
                                onPinSet(pin)
                            } else {
                                error = "PINs don't match, try again"
                                confirmPin = ""
                            }
                        }
                    } else {
                        null
                    },
                confirmLabel = if (step == 1) "Next" else "Confirm",
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun PinNumberPad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirmClick: (() -> Unit)? = null,
    confirmLabel: String = "Confirm",
) {
    val numbers =
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", ""),
        )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        numbers.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { colIndex, number ->
                    when {
                        // Empty space in bottom-left
                        rowIndex == 3 && colIndex == 0 -> {
                            Spacer(modifier = Modifier.size(72.dp))
                        }
                        // Backspace button in bottom-right
                        rowIndex == 3 && colIndex == 2 -> {
                            IconButton(
                                onClick = onBackspaceClick,
                                modifier =
                                    Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(DarkCard),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        // Number buttons
                        number.isNotEmpty() -> {
                            Button(
                                onClick = { onNumberClick(number) },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = DarkCard,
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                    ),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontSize = 28.sp,
                                )
                            }
                        }
                        // Empty space
                        else -> {
                            Spacer(modifier = Modifier.size(72.dp))
                        }
                    }
                }
            }
        }

        // Confirm button row below the number pad
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onConfirmClick?.invoke() },
            enabled = onConfirmClick != null,
            modifier =
                Modifier
                    .width(240.dp)
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BitcoinOrange,
                    contentColor = DarkBackground,
                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                    disabledContentColor = DarkBackground.copy(alpha = 0.5f),
                ),
        ) {
            Text(
                text = confirmLabel,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTimingDropdown(
    currentTiming: SecureStorage.LockTiming,
    onTimingSelected: (SecureStorage.LockTiming) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = currentTiming.displayName,
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BitcoinOrange,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = BitcoinOrange,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                ),
            shape = RoundedCornerShape(8.dp),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            SecureStorage.LockTiming.entries.forEach { timing ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = timing.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (timing == currentTiming) {
                                    BitcoinOrange
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                        )
                    },
                    onClick = {
                        onTimingSelected(timing)
                        expanded = false
                    },
                    leadingIcon = {
                        if (timing == currentTiming) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Sealed class for BIP39 mnemonic validation state in duress setup
 */
private sealed class DuressMnemonicValidation {
    data object NotChecked : DuressMnemonicValidation()

    data object Valid : DuressMnemonicValidation()

    data class Invalid(val error: String) : DuressMnemonicValidation()
}

/**
 * Duress PIN setup flow: create PIN -> confirm PIN -> enter seed phrase -> enable
 */
@Composable
private fun DuressSetupScreen(
    currentSecurityMethod: SecureStorage.SecurityMethod,
    onDuressSet: (
        pin: String,
        mnemonic: String,
        passphrase: String?,
        customDerivationPath: String?,
        addressType: AddressType,
    ) -> Unit,
    onBack: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 = enter PIN, 2 = confirm PIN, 3 = seed phrase
    var error by remember { mutableStateOf<String?>(null) }
    var seedPhrase by remember { mutableStateOf("") }
    // Advanced options (matching ImportWalletScreen pattern)
    var usePassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var useCustomPath by remember { mutableStateOf(false) }
    var customPath by remember { mutableStateOf("") }
    var selectedAddressType by remember { mutableStateOf(AddressType.SEGWIT) }
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }

    // BIP39 validation
    val bip39WordSet = remember { QrFormatParser.getWordlist(context).toSet() }
    val bip39PrefixSet = remember { bip39WordSet.map { it.take(4) }.toSet() }

    val words =
        remember(seedPhrase) {
            seedPhrase.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        }
    val wordCount = words.size
    val isValidWordCount = wordCount in listOf(12, 15, 18, 21, 24)

    val invalidWords =
        remember(words, bip39WordSet, bip39PrefixSet) {
            words.filter { word ->
                word.length >= 4 && word.take(4) !in bip39PrefixSet
            }
        }
    val allTypedWordsValid = invalidWords.isEmpty()
    val allWordsComplete = words.all { it in bip39WordSet }

    val mnemonicValidation =
        remember(seedPhrase, isValidWordCount, allTypedWordsValid, allWordsComplete) {
            if (!isValidWordCount || !allTypedWordsValid || !allWordsComplete) {
                DuressMnemonicValidation.NotChecked
            } else {
                try {
                    Mnemonic.fromString(seedPhrase.trim())
                    DuressMnemonicValidation.Valid
                } catch (e: Exception) {
                    DuressMnemonicValidation.Invalid(e.message ?: "Invalid mnemonic")
                }
            }
        }

    val isMnemonicValid = mnemonicValidation is DuressMnemonicValidation.Valid

    if (step <= 2) {
        // PIN entry steps (reuses the same pattern as PinSetupScreen)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        when (step) {
                            2 -> {
                                step = 1
                                confirmPin = ""
                                error = null
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (step == 1) "Create Duress PIN" else "Confirm Duress PIN",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        when (step) {
                            1 -> "Enter a $MIN_PIN_LENGTH\u2013$MAX_PIN_LENGTH digit PIN"
                            else -> "Enter the PIN again to confirm"
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )

                if (currentSecurityMethod == SecureStorage.SecurityMethod.BIOMETRIC) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "To trigger biometric unlock, press 'C' on the lock screen PIN pad",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // PIN dots
                val currentPin = if (step == 1) pin else confirmPin
                if (currentPin.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(currentPin.length) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(BitcoinOrange),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }

                // Error message
                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (error != null) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                PinNumberPad(
                    onNumberClick = { number ->
                        val maxLen = if (step == 2) pin.length else MAX_PIN_LENGTH
                        val curPin = if (step == 1) pin else confirmPin
                        if (curPin.length < maxLen) {
                            if (step == 1) {
                                pin += number
                                error = null
                            } else {
                                confirmPin += number
                                error = null
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (step == 1 && pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                            error = null
                        } else if (step == 2 && confirmPin.isNotEmpty()) {
                            confirmPin = confirmPin.dropLast(1)
                            error = null
                        }
                    },
                    onConfirmClick =
                        if (step == 1 && pin.length >= MIN_PIN_LENGTH) {
                            {
                                // Validate duress PIN != real PIN (if PIN security mode)
                                // Use pinMatchesCurrent() to avoid incrementing the failed attempt counter
                                if (currentSecurityMethod == SecureStorage.SecurityMethod.PIN &&
                                    secureStorage.pinMatchesCurrent(pin)
                                ) {
                                    error = "Must differ from your unlock PIN"
                                    pin = ""
                                } else {
                                    step = 2
                                    error = null
                                }
                            }
                        } else if (step == 2 && confirmPin.length == pin.length) {
                            {
                                if (pin == confirmPin) {
                                    step = 3
                                    error = null
                                } else {
                                    error = "PINs don't match, try again"
                                    confirmPin = ""
                                }
                            }
                        } else {
                            null
                        },
                    confirmLabel = if (step == 1) "Next" else "Next",
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    } else {
        // Step 3: Seed phrase entry with advanced options
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    step = 2
                    confirmPin = ""
                    error = null
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Decoy Wallet Seed",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter the seed phrase for the decoy wallet that will be shown when the duress PIN is used.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Address Type Selection
            Text(
                text = "Address Type",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddressType.entries
                    .filter { it != AddressType.NESTED_SEGWIT }
                    .forEach { addressType ->
                        val isSelected = selectedAddressType == addressType
                        val backgroundColor = if (isSelected) BitcoinOrange else DarkCard
                        val contentColor = if (isSelected) DarkBackground else TextSecondary
                        val borderColor = if (isSelected) BitcoinOrange else BorderColor
                        Surface(
                            onClick = { selectedAddressType = addressType },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = backgroundColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
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
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedAddressType.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = seedPhrase,
                onValueChange = { newValue ->
                    seedPhrase = newValue.lowercase()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                placeholder = {
                    Text(
                        text = "Enter 12 or 24 word BIP39 seed phrase",
                        color = TextSecondary,
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.None,
                    ),
                shape = RoundedCornerShape(8.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = BitcoinOrange,
                    ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Validation feedback
            when {
                mnemonicValidation is DuressMnemonicValidation.Valid -> {
                    Text(
                        text = "Valid BIP39 seed phrase",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                    )
                }
                mnemonicValidation is DuressMnemonicValidation.Invalid -> {
                    Text(
                        text = (mnemonicValidation as DuressMnemonicValidation.Invalid).error,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
                invalidWords.isNotEmpty() -> {
                    val display = invalidWords.take(3).joinToString(", ") { "\"$it\"" }
                    Text(
                        text = "Invalid word(s): $display",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
                isValidWordCount && allTypedWordsValid -> {
                    Text(
                        text = "$wordCount words entered",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                wordCount > 0 && wordCount !in listOf(12, 15, 18, 21, 24) && wordCount > 11 -> {
                    Text(
                        text = "$wordCount words \u2014 need 12, 15, 18, 21, or 24",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
                wordCount > 0 -> {
                    Text(
                        text = "$wordCount words entered",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced options (matching ImportWalletScreen pattern)

            // Use BIP39 Passphrase
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { usePassphrase = !usePassphrase },
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
                )
                Text(
                    text = "Use BIP39 Passphrase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Passphrase field
            AnimatedVisibility(
                visible = usePassphrase,
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
                    text = "Use Custom Derivation Path",
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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onDuressSet(
                        pin,
                        seedPhrase.trim(),
                        if (usePassphrase) passphrase.ifBlank { null } else null,
                        if (useCustomPath) customPath.ifBlank { null } else null,
                        selectedAddressType,
                    )
                },
                enabled = isMnemonicValid,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        contentColor = DarkBackground,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                        disabledContentColor = DarkBackground.copy(alpha = 0.5f),
                    ),
            ) {
                Text(
                    text = "Enable Duress PIN",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private const val MIN_CLOAK_CODE_LENGTH = 4
private const val MAX_CLOAK_CODE_LENGTH = 12

/**
 * Setup screen for the cloak mode unlock code.
 * Two-step flow: enter code -> confirm code.
 */
@Composable
private fun CloakCodeSetupScreen(
    onCodeSet: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 = enter code, 2 = confirm code
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(DarkBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (step == 2) {
                        step = 1
                        confirmCode = ""
                        error = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (step == 1) "Set Unlock Pin" else "Confirm Unlock Pin",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Enter a $MIN_CLOAK_CODE_LENGTH\u2013$MAX_CLOAK_CODE_LENGTH digit pin for cloak mode",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter this pin in the calculator app and press the '=' key to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // PIN dots indicator
            val currentCode = if (step == 1) code else confirmCode
            if (currentCode.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(currentCode.length) {
                        Box(
                            modifier =
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(BitcoinOrange),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }

            // Error message
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Number pad
            PinNumberPad(
                onNumberClick = { number ->
                    val maxLen = if (step == 2) code.length else MAX_CLOAK_CODE_LENGTH
                    val curCode = if (step == 1) code else confirmCode
                    if (curCode.length < maxLen) {
                        if (step == 1) {
                            code += number
                            error = null
                        } else {
                            confirmCode += number
                            error = null
                        }
                    }
                },
                onBackspaceClick = {
                    if (step == 1 && code.isNotEmpty()) {
                        code = code.dropLast(1)
                        error = null
                    } else if (step == 2 && confirmCode.isNotEmpty()) {
                        confirmCode = confirmCode.dropLast(1)
                        error = null
                    }
                },
                onConfirmClick =
                    if (step == 1 && code.length >= MIN_CLOAK_CODE_LENGTH) {
                        {
                            step = 2
                            error = null
                        }
                    } else if (step == 2 && confirmCode.length == code.length) {
                        {
                            if (code == confirmCode) {
                                onCodeSet(code)
                            } else {
                                error = "Pin doesn't match, try again"
                                confirmCode = ""
                            }
                        }
                    } else {
                        null
                    },
                confirmLabel = if (step == 1) "Next" else "Enable",
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
