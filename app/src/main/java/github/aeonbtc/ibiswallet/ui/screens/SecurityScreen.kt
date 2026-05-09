@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.SeedFormat
import github.aeonbtc.ibiswallet.data.model.StoredWallet
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.ui.components.Bip39SuggestionRow
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.SensitiveSeedIme
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.components.sensitiveSeedKeyboardOptions
import github.aeonbtc.ibiswallet.ui.theme.*
import github.aeonbtc.ibiswallet.util.BitcoinUtils
import github.aeonbtc.ibiswallet.util.ElectrumSeedUtil
import github.aeonbtc.ibiswallet.util.QrFormatParser
import github.aeonbtc.ibiswallet.util.SecureClipboard
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.WordCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.localization.descriptionText
import github.aeonbtc.ibiswallet.ui.localization.seedVariantLabel
import github.aeonbtc.ibiswallet.ui.localization.titleText

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 12

private enum class DuressWordCountOption(val wordCount: WordCount) {
    TWELVE(WordCount.WORDS12),
    TWENTY_FOUR(WordCount.WORDS24),
}

@Composable
private fun DuressWordCountOption.labelText(): String =
    when (this) {
        DuressWordCountOption.TWELVE -> stringResource(R.string.loc_a34ad456)
        DuressWordCountOption.TWENTY_FOUR -> stringResource(R.string.loc_4e15e68f)
    }

private enum class DuressSeedSource {
    GENERATE,
    EXISTING,
}

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    currentSecurityMethod: SecureStorage.SecurityMethod = SecureStorage.SecurityMethod.NONE,
    currentLockTiming: SecureStorage.LockTiming = SecureStorage.LockTiming.WHEN_MINIMIZED,
    isBiometricAvailable: Boolean = false,
    screenshotsDisabled: Boolean = false,
    randomizePinPad: Boolean = false,
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
    onRandomizePinPadChange: (Boolean) -> Unit = {},
    onSetupDuress: (
        pin: String,
        config: WalletImportConfig,
    ) -> Unit = { _, _ -> },
    onDisableDuress: () -> Unit = {},
    onAutoWipeThresholdChange: (SecureStorage.AutoWipeThreshold) -> Unit = {},
    onEnableCloakMode: (code: String) -> Unit = {},
    onDisableCloakMode: () -> Unit = {},
    onRestartApp: () -> Unit = {},
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
    var showCloakRestartDialog by remember { mutableStateOf(false) }

    if (showCloakSetup) {
        CloakCodeSetupScreen(
            onCodeSet = { code ->
                onEnableCloakMode(code)
                showCloakSetup = false
                showCloakRestartDialog = true
            },
            onBack = { showCloakSetup = false },
        )
    } else if (showDuressSetup) {
        DuressSetupScreen(
            currentSecurityMethod = currentSecurityMethod,
            onDuressSet = { pin, config ->
                onSetupDuress(pin, config)
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
                    text = stringResource(R.string.loc_3cedb797),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        text = stringResource(R.string.loc_a06704d8),
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
                            ToggleOptionText(
                                title = stringResource(R.string.loc_922337f3),
                                subtitle =
                                    if (isBiometricAvailable) {
                                        stringResource(R.string.loc_b2481aa2)
                                    } else {
                                        stringResource(R.string.loc_3e2ca137)
                                    },
                                titleColor =
                                    if (isBiometricAvailable) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        TextSecondary.copy(alpha = 0.5f)
                                    },
                                subtitleColor =
                                    if (isBiometricAvailable) {
                                        TextSecondary
                                    } else {
                                        TextSecondary.copy(alpha = 0.5f)
                                    },
                            )
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
                            ToggleOptionText(
                                title = stringResource(R.string.loc_4355b799),
                                subtitle =
                                    stringResource(
                                        R.string.security_pin_unlock_code_format,
                                        MIN_PIN_LENGTH,
                                        MAX_PIN_LENGTH,
                                    ),
                            )
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

                    if (currentSecurityMethod != SecureStorage.SecurityMethod.NONE) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderColor,
                        )

                        Text(
                            text = stringResource(R.string.loc_599c7d5f),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LockTimingDropdown(
                            currentTiming = currentLockTiming,
                            onTimingSelected = onLockTimingChange,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderColor,
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = isPinEnabled) { onRandomizePinPadChange(!randomizePinPad) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = if (isPinEnabled) BitcoinOrange else TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                ToggleOptionText(
                                    title = stringResource(R.string.loc_6e64c96f),
                                    subtitle =
                                        if (isPinEnabled) {
                                            stringResource(R.string.security_randomize_pin_subtitle)
                                        } else {
                                            stringResource(R.string.loc_a0a0bf84)
                                        },
                                    titleColor =
                                        if (isPinEnabled) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            TextSecondary.copy(alpha = 0.5f)
                                        },
                                    subtitleColor =
                                        if (isPinEnabled) {
                                            TextSecondary
                                        } else {
                                            TextSecondary.copy(alpha = 0.5f)
                                        },
                                )
                            }

                            SquareToggle(
                                checked = randomizePinPad,
                                onCheckedChange = onRandomizePinPadChange,
                                enabled = isPinEnabled,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Duress PIN card
            // - Faded out when no security is enabled (with hint to enable PIN or biometric)
            // - Fully interactive when security is enabled
            val isSecurityActive = currentSecurityMethod != SecureStorage.SecurityMethod.NONE
            val canEnableDuress = isSecurityActive && hasWallet
            val showDuressEnabled = isDuressEnabled && !isDuressMode

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
                        text = stringResource(R.string.loc_b68140bc),
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
                                            if (!showDuressEnabled) {
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
                            ToggleOptionText(
                                title = stringResource(R.string.loc_b68140bc),
                                subtitle =
                                    if (!isSecurityActive) {
                                        stringResource(R.string.loc_a1d57483)
                                    } else if (!hasWallet) {
                                        stringResource(R.string.loc_96e7526f)
                                    } else {
                                        stringResource(R.string.loc_f6649c50)
                                    },
                                titleColor =
                                    if (canEnableDuress) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        TextSecondary.copy(alpha = 0.4f)
                                    },
                                subtitleColor =
                                    if (canEnableDuress) {
                                        TextSecondary
                                    } else {
                                        TextSecondary.copy(alpha = 0.4f)
                                    },
                            )
                        }

                        SquareToggle(
                            checked = showDuressEnabled,
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

            // Auto-Wipe card
            // - Faded when no security is enabled
            val isAutoWipeSecurityActive = currentSecurityMethod != SecureStorage.SecurityMethod.NONE

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
                        text = stringResource(R.string.loc_10bf1589),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAutoWipeSecurityActive) BitcoinOrange else BitcoinOrange.copy(alpha = 0.4f),
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
                                    if (isAutoWipeSecurityActive && autoWipeThreshold != SecureStorage.AutoWipeThreshold.DISABLED) {
                                        ErrorRed
                                    } else if (isAutoWipeSecurityActive) {
                                        BitcoinOrange
                                    } else {
                                        TextSecondary.copy(alpha = 0.4f)
                                    },
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            ToggleOptionText(
                                title = stringResource(R.string.loc_4e19a59a),
                                subtitle =
                                    if (isAutoWipeSecurityActive) {
                                        stringResource(R.string.loc_3bbee4d1)
                                    } else {
                                        stringResource(R.string.loc_560ec39d)
                                    },
                                titleColor =
                                    if (isAutoWipeSecurityActive) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        TextSecondary.copy(alpha = 0.4f)
                                    },
                                subtitleColor =
                                    if (isAutoWipeSecurityActive) {
                                        TextSecondary
                                    } else {
                                        TextSecondary.copy(alpha = 0.4f)
                                    },
                            )
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
                            enabled = isAutoWipeSecurityActive,
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

            // Cloak Mode card
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
                        text = stringResource(R.string.loc_bd14b437),
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
                            ToggleOptionText(
                                title = stringResource(R.string.loc_127261f8),
                                subtitle =
                                    if (isCloakModeEnabled) {
                                        stringResource(R.string.loc_72808e02)
                                    } else {
                                        stringResource(R.string.loc_5d688c9a)
                                    },
                            )
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
                            ToggleOptionText(
                                title = stringResource(R.string.loc_714c3012),
                                subtitle = stringResource(R.string.loc_0d6a729c),
                            )
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
            IbisConfirmDialog(
                onDismissRequest = { showDisableCloakDialog = false },
                title = stringResource(R.string.loc_3139ebdd),
                message = stringResource(R.string.loc_aa09917b),
                confirmText = stringResource(R.string.loc_80f874cd),
                confirmColor = ErrorRed,
                onConfirm = {
                    onDisableCloakMode()
                    showDisableCloakDialog = false
                    onRestartApp()
                },
            )
        }

        // Cloak Mode Restart Prompt
        if (showCloakRestartDialog) {
            IbisConfirmDialog(
                onDismissRequest = { showCloakRestartDialog = false },
                title = stringResource(R.string.loc_2a9da994),
                message = stringResource(R.string.security_cloak_restart_message),
                confirmText = stringResource(R.string.loc_28441b9b),
                onConfirm = {
                    showCloakRestartDialog = false
                    onRestartApp()
                },
                dismissText = stringResource(R.string.loc_2dc29d4d),
            )
        }

        // Disable Duress Confirmation Dialog
        if (showDisableDuressDialog) {
            IbisConfirmDialog(
                onDismissRequest = { showDisableDuressDialog = false },
                title = stringResource(R.string.loc_4b582481),
                message = stringResource(R.string.loc_cd305fb8),
                confirmText = stringResource(R.string.loc_80f874cd),
                confirmColor = ErrorRed,
                onConfirm = {
                    onDisableDuress()
                    showDisableDuressDialog = false
                },
            )
        }

        // Enable Auto-Wipe Confirmation Dialog
        if (showAutoWipeConfirmDialog) {
            IbisConfirmDialog(
                onDismissRequest = { showAutoWipeConfirmDialog = false },
                title = stringResource(R.string.loc_5e0c0f5b),
                confirmText = stringResource(R.string.loc_f40ccd04),
                confirmColor = ErrorRed,
                onConfirm = {
                    onAutoWipeThresholdChange(SecureStorage.AutoWipeThreshold.AFTER_10)
                    showAutoWipeConfirmDialog = false
                },
                body = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.loc_f7b9c073),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Text(
                            text = stringResource(R.string.loc_5ed5bb5d),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                        )
                    }
                },
            )
        }

        // Disable Security Confirmation Dialog
        if (showDisableConfirmDialog) {
            IbisConfirmDialog(
                onDismissRequest = { showDisableConfirmDialog = false },
                title = stringResource(R.string.loc_c47ba42d),
                message = stringResource(R.string.loc_38a084fc),
                confirmText = stringResource(R.string.loc_80f874cd),
                confirmColor = ErrorRed,
                onConfirm = {
                    onDisableSecurity()
                    showDisableConfirmDialog = false
                },
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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
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
                        contentDescription = stringResource(R.string.loc_cdfc6e09),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text =
                    if (step == 1) {
                        stringResource(R.string.loc_40a878b7)
                    } else {
                        stringResource(R.string.loc_c3d36da0)
                    },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    when {
                        step == 1 ->
                            stringResource(
                                R.string.security_enter_pin_range_format,
                                MIN_PIN_LENGTH,
                                MAX_PIN_LENGTH,
                            )
                        else -> stringResource(R.string.loc_c3c70203)
                    },
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

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

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Error message
            Box(
                modifier = Modifier.heightIn(min = 24.dp),
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

            Spacer(modifier = Modifier.height(24.dp))

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
                    when (step) {
                        1 ->
                            if (pin.length >= MIN_PIN_LENGTH) {
                                {
                                    step = 2
                                    error = null
                                }
                            } else {
                                null
                            }
                        2 ->
                            if (confirmPin.length == pin.length) {
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
                            }
                        else -> null
                    },
                confirmLabel = if (step == 1) "Next" else "Confirm",
            )

            Spacer(modifier = Modifier.height(24.dp))
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

    val keypadButtonSize = 72.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        numbers.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { colIndex, number ->
                    when {
                        // Empty space in bottom-left
                        rowIndex == 3 && colIndex == 0 -> {
                            Spacer(modifier = Modifier.size(keypadButtonSize))
                        }
                        // Backspace button in bottom-right
                        rowIndex == 3 && colIndex == 2 -> {
                            IconButton(
                                onClick = onBackspaceClick,
                                modifier =
                                    Modifier
                                        .size(keypadButtonSize)
                                        .clip(CircleShape)
                                        .background(DarkCard),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = stringResource(R.string.loc_3d1100cc),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        // Number buttons
                        number.isNotEmpty() -> {
                            Button(
                                onClick = { onNumberClick(number) },
                                modifier = Modifier.size(keypadButtonSize),
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
                            Spacer(modifier = Modifier.size(keypadButtonSize))
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
                    .fillMaxWidth()
                    .widthIn(max = 240.dp)
                    .heightIn(min = 48.dp),
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

@Composable
private fun lockTimingLabel(timing: SecureStorage.LockTiming): String =
    when (timing) {
        SecureStorage.LockTiming.DISABLED -> stringResource(R.string.loc_7d880cb5)
        SecureStorage.LockTiming.WHEN_MINIMIZED -> stringResource(R.string.security_lock_timing_when_minimized)
        SecureStorage.LockTiming.AFTER_1_MIN -> stringResource(R.string.security_lock_timing_after_1_min)
        SecureStorage.LockTiming.AFTER_5_MIN -> stringResource(R.string.security_lock_timing_after_5_min)
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
            value = lockTimingLabel(currentTiming),
            onValueChange = {},
            readOnly = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
                            text = lockTimingLabel(timing),
                            style = MaterialTheme.typography.titleMedium,
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
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ToggleOptionText(
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: Color = TextSecondary,
) {
    Column {
        Text(
            text = title,
            style = TextStyle(fontSize = 15.sp),
            color = titleColor,
        )
        Text(
            text = subtitle,
            style = TextStyle(fontSize = 13.sp),
            color = subtitleColor,
        )
    }
}

/**
 * Sealed class for BIP39 mnemonic validation state in duress setup
 */
private sealed class DuressMnemonicValidation {
    data object NotChecked : DuressMnemonicValidation()

    data object Valid : DuressMnemonicValidation()

    data class ValidElectrum(val seedType: ElectrumSeedUtil.ElectrumSeedType) : DuressMnemonicValidation()

    data object Invalid : DuressMnemonicValidation()
}

/**
 * Duress PIN setup flow: create PIN -> confirm PIN -> configure decoy wallet -> enable
 */
@Composable
private fun DuressSetupScreen(
    currentSecurityMethod: SecureStorage.SecurityMethod,
    onDuressSet: (
        pin: String,
        config: WalletImportConfig,
    ) -> Unit,
    onBack: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 = enter PIN, 2 = confirm PIN, 3 = seed phrase
    var error by remember { mutableStateOf<String?>(null) }
    var walletName by remember { mutableStateOf("") }
    var seedSource by remember { mutableStateOf(DuressSeedSource.GENERATE) }
    var manualSeedPhraseField by remember { mutableStateOf(TextFieldValue("")) }
    val manualSeedPhrase = manualSeedPhraseField.text
    var generatedSeedPhrase by remember { mutableStateOf<String?>(null) }
    var selectedWordCount by remember { mutableStateOf(WordCount.WORDS12) }
    var backedUpGeneratedSeed by remember { mutableStateOf(false) }
    var copiedGeneratedSeed by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    // Advanced options (matching ImportWalletScreen pattern)
    var usePassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var useCustomPath by remember { mutableStateOf(false) }
    var customPath by remember { mutableStateOf("") }
    var useCustomFingerprint by remember { mutableStateOf(false) }
    var masterFingerprint by remember { mutableStateOf("") }
    var useCustomGapLimit by remember { mutableStateOf(false) }
    var gapLimitText by remember { mutableStateOf("") }
    val advancedOptionsBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(showAdvancedOptions, "security_advanced")
    val customFingerprintBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(useCustomFingerprint, "security_fingerprint")
    val passphraseBringIntoViewRequester =
        rememberBringIntoViewRequesterOnExpand(
            usePassphrase,
            "security_passphrase",
        )
    val customGapLimitBringIntoViewRequester = rememberBringIntoViewRequesterOnExpand(useCustomGapLimit, "security_gap_limit")
    val customPathBringIntoViewRequester =
        rememberBringIntoViewRequesterOnExpand(
            useCustomPath,
            "security_path",
        )
    var selectedAddressType by remember { mutableStateOf(AddressType.SEGWIT) }
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage.getInstance(context) }

    val bip39WordList = remember { QrFormatParser.getWordlist(context) }
    val bip39WordSet = remember { bip39WordList.toSet() }
    val bip39PrefixSet = remember { bip39WordSet.map { it.take(4) }.toSet() }
    val isGenerateMode = seedSource == DuressSeedSource.GENERATE
    val trimmedImportInput = manualSeedPhrase.trim()
    val unsupportedNonMainnetReason =
        remember(manualSeedPhrase) {
            BitcoinUtils.unsupportedNonMainnetReason(trimmedImportInput)
        }
    val unsupportedNestedSegwitReason =
        remember(manualSeedPhrase) {
            BitcoinUtils.unsupportedNestedSegwitReason(trimmedImportInput)
        }
    val isExtendedKey =
        trimmedImportInput.let {
            it.startsWith("xpub") || it.startsWith("ypub") ||
                it.startsWith("zpub") || it.startsWith("upub") ||
                it.startsWith("xprv") || it.startsWith("yprv") ||
                it.startsWith("zprv")
        }
    val isOriginPrefixed =
        trimmedImportInput.startsWith("[") && trimmedImportInput.contains("]") &&
            trimmedImportInput.substringAfter("]").let {
                it.startsWith("xpub") || it.startsWith("ypub") ||
                    it.startsWith("zpub") || it.startsWith("upub")
            }
    val isOutputDescriptor =
        listOf("pkh(", "wpkh(", "tr(").any {
            trimmedImportInput.lowercase().startsWith(it)
        } && (
            trimmedImportInput.contains("xpub") ||
                trimmedImportInput.contains("zpub") ||
                trimmedImportInput.contains("xprv") ||
                trimmedImportInput.contains("zprv")
        )
    val isJsonFormat = trimmedImportInput.startsWith("{") && trimmedImportInput.endsWith("}")
    val isWatchOnlyKey = isExtendedKey || isOriginPrefixed || isOutputDescriptor || isJsonFormat
    val isWatchOnly =
        isWatchOnlyKey &&
            !trimmedImportInput.let {
                it.startsWith("xprv") || it.startsWith("yprv") ||
                    it.startsWith("zprv") ||
                    it.contains("xprv")
            }
    val isWifKey =
        remember(manualSeedPhrase) {
            val t = manualSeedPhrase.trim()
            val couldBeWif =
                (t.length == 52 && (t.startsWith("K") || t.startsWith("L"))) ||
                    (t.length == 51 && t.startsWith("5"))
            if (!couldBeWif) {
                false
            } else {
                try {
                    Descriptor("wpkh($t)", Network.BITCOIN)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    val isBitcoinAddress =
        remember(manualSeedPhrase) {
            val t = manualSeedPhrase.trim()
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
    val detectedAddressType =
        remember(manualSeedPhrase) {
            when {
                trimmedImportInput.startsWith("1") -> AddressType.LEGACY
                trimmedImportInput.startsWith("bc1q") -> AddressType.SEGWIT
                trimmedImportInput.startsWith("bc1p") -> AddressType.TAPROOT
                else -> null
            }
        }
    val bareKeyPrefix =
        if (isOriginPrefixed) {
            trimmedImportInput.substringAfter("]").take(4)
        } else {
            trimmedImportInput.take(4)
        }
    val isSegwitVersionKey = bareKeyPrefix == "zpub"
    val isLegacyDisabled = isWatchOnly && isSegwitVersionKey
    val parsedFingerprint =
        remember(manualSeedPhrase) {
            """\[([a-fA-F0-9]{8})/""".toRegex().find(trimmedImportInput)?.groupValues?.get(1)?.lowercase()
        }
    val hasEmbeddedFingerprint = parsedFingerprint != null
    val suppressImportMnemonicValidation = isWifKey || isBitcoinAddress
    val importWords =
        if (!isExtendedKey && !isWatchOnlyKey && !suppressImportMnemonicValidation) {
            trimmedImportInput.split("\\s+".toRegex()).filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    val wordCount = importWords.size
    val isValidWordCount = wordCount in listOf(12, 15, 18, 21, 24)
    val invalidWords =
        remember(manualSeedPhrase) {
            if (importWords.isNotEmpty()) {
                importWords.mapIndexedNotNull { index, word ->
                    when {
                        word.length < 4 -> null
                        word in bip39WordSet -> null
                        word.take(4) !in bip39PrefixSet -> index to word
                        else -> null
                    }
                }
            } else {
                emptyList()
            }
        }
    val seedEntryStatus: Pair<String, Color>? =
        when {
            invalidWords.isNotEmpty() -> {
                val display = invalidWords.take(3).joinToString(", ") { "\"${it.second}\"" }
                "Invalid word(s): $display" to ErrorRed
            }
            isValidWordCount -> "$wordCount words entered" to TextSecondary
            wordCount in 1..11 -> "$wordCount ${if (wordCount == 1) "word" else "words"} entered" to TextSecondary
            wordCount > 11 -> "$wordCount words — need 12, 15, 18, 21, or 24" to ErrorRed
            else -> null
        }
    val allTypedWordsValid =
        importWords.isNotEmpty() && invalidWords.isEmpty() &&
            importWords.all { it in bip39WordSet }
    val mnemonicValidation =
        remember(manualSeedPhrase, isExtendedKey, isWatchOnlyKey, isValidWordCount, allTypedWordsValid) {
            if (!isExtendedKey && !isWatchOnlyKey && !suppressImportMnemonicValidation &&
                isValidWordCount && allTypedWordsValid
            ) {
                try {
                    Mnemonic.fromString(trimmedImportInput)
                    DuressMnemonicValidation.Valid
                } catch (_: Exception) {
                    val electrumType = ElectrumSeedUtil.getElectrumSeedType(trimmedImportInput)
                    if (electrumType != null) {
                        DuressMnemonicValidation.ValidElectrum(electrumType)
                    } else {
                        DuressMnemonicValidation.Invalid
                    }
                }
            } else {
                DuressMnemonicValidation.NotChecked
            }
        }
    val isElectrumSeed = mnemonicValidation is DuressMnemonicValidation.ValidElectrum
    val electrumSeedType = (mnemonicValidation as? DuressMnemonicValidation.ValidElectrum)?.seedType
    val isValidImportMnemonic =
        mnemonicValidation is DuressMnemonicValidation.Valid ||
            mnemonicValidation is DuressMnemonicValidation.ValidElectrum
    val isValidImportInput =
        unsupportedNonMainnetReason == null &&
            unsupportedNestedSegwitReason == null &&
            (isWatchOnlyKey || isExtendedKey || isValidImportMnemonic || isWifKey || isBitcoinAddress)
    val gapLimitInt = gapLimitText.toIntOrNull()
    val gapLimitValid = !useCustomGapLimit || gapLimitText.isEmpty() || (gapLimitInt != null && gapLimitInt in 1..10000)
    val duressGapLimit =
        if (useCustomGapLimit && gapLimitText.isNotBlank()) {
            gapLimitInt?.coerceIn(1, 10000) ?: StoredWallet.DEFAULT_GAP_LIMIT
        } else {
            StoredWallet.DEFAULT_GAP_LIMIT
        }
    val canEnableDuress =
        walletName.trim().isNotBlank() &&
            gapLimitValid &&
            if (isGenerateMode) {
                generatedSeedPhrase != null && backedUpGeneratedSeed
            } else {
                isValidImportInput
            }

    LaunchedEffect(seedSource, isBitcoinAddress, detectedAddressType) {
        if (seedSource == DuressSeedSource.EXISTING &&
            isBitcoinAddress &&
            detectedAddressType != null &&
            selectedAddressType != detectedAddressType
        ) {
            selectedAddressType = detectedAddressType
        }
    }

    LaunchedEffect(seedSource, isLegacyDisabled) {
        if (seedSource == DuressSeedSource.EXISTING && isLegacyDisabled && selectedAddressType == AddressType.LEGACY) {
            selectedAddressType = AddressType.SEGWIT
        }
    }

    LaunchedEffect(seedSource, electrumSeedType) {
        if (seedSource == DuressSeedSource.EXISTING) {
            when (electrumSeedType) {
                ElectrumSeedUtil.ElectrumSeedType.STANDARD -> selectedAddressType = AddressType.LEGACY
                ElectrumSeedUtil.ElectrumSeedType.SEGWIT -> selectedAddressType = AddressType.SEGWIT
                null -> { }
            }
        }
    }

    LaunchedEffect(seedSource, isExtendedKey, isWatchOnly, hasEmbeddedFingerprint) {
        if (seedSource == DuressSeedSource.EXISTING && isExtendedKey && isWatchOnly && !hasEmbeddedFingerprint) {
            showAdvancedOptions = true
        }
    }

    LaunchedEffect(copiedGeneratedSeed) {
        if (copiedGeneratedSeed) {
            kotlinx.coroutines.delay(3000)
            copiedGeneratedSeed = false
        }
    }

    var generatedFingerprint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(generatedSeedPhrase, selectedAddressType, passphrase, usePassphrase) {
        val mnemonic = generatedSeedPhrase
        if (mnemonic != null) {
            generatedFingerprint =
                withContext(Dispatchers.Default) {
                    try {
                        val mnemonicObj = Mnemonic.fromString(mnemonic)
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
        } else {
            generatedFingerprint = null
        }
    }

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
                            contentDescription = stringResource(R.string.loc_cdfc6e09),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text =
                        if (step == 1) {
                            stringResource(R.string.loc_e6b56697)
                        } else {
                            stringResource(R.string.loc_66399ff4)
                        },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text =
                        when (step) {
                            1 ->
                                stringResource(
                                    R.string.security_enter_pin_range_format,
                                    MIN_PIN_LENGTH,
                                    MAX_PIN_LENGTH,
                                )
                            else -> stringResource(R.string.loc_c3c70203)
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )

                if (currentSecurityMethod == SecureStorage.SecurityMethod.BIOMETRIC) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.loc_e624c41d),
                        style = MaterialTheme.typography.bodyMedium,
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
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(28.dp))
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

            Spacer(modifier = Modifier.height(24.dp))

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
                        when (step) {
                            1 ->
                                if (pin.length >= MIN_PIN_LENGTH) {
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
                                } else {
                                    null
                                }
                            2 ->
                                if (confirmPin.length == pin.length) {
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
                                }
                            else -> null
                        },
                    confirmLabel = if (step == 1) "Next" else "Next",
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    } else {
        val generatedWords =
            remember(generatedSeedPhrase) {
                generatedSeedPhrase
                    ?.trim()
                    ?.split("\\s+".toRegex())
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            }

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
                        contentDescription = stringResource(R.string.loc_cdfc6e09),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.loc_ad46d69f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

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
                        text = stringResource(R.string.loc_748eece4),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.loc_d9194d2d),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DuressSeedSource.entries.forEach { option ->
                            DuressSegmentButton(
                                text =
                                    when (option) {
                                        DuressSeedSource.GENERATE -> stringResource(R.string.loc_027001ed)
                                        DuressSeedSource.EXISTING -> stringResource(R.string.loc_9ae2cb2b)
                                    },
                                isSelected = seedSource == option,
                                onClick = { seedSource = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.loc_7bc49729),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
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
                    Spacer(modifier = Modifier.height(10.dp))
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
                                if (isGenerateMode) {
                                    true
                                } else {
                                    !isElectrumSeed &&
                                        !(addressType == AddressType.LEGACY && isLegacyDisabled) &&
                                        !(isBitcoinAddress && detectedAddressType != null && addressType != detectedAddressType)
                                }
                            DuressSegmentButton(
                                text = addressType.titleText(),
                                isSelected = selectedAddressType == addressType,
                                onClick = { selectedAddressType = addressType },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedAddressType.descriptionText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (seedSource == DuressSeedSource.GENERATE) {
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
                            text = stringResource(R.string.loc_e3b008f1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DuressWordCountOption.entries.forEach { option ->
                                DuressSegmentButton(
                                    text = option.labelText(),
                                    isSelected = selectedWordCount == option.wordCount,
                                    onClick = { selectedWordCount = option.wordCount },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (generatedSeedPhrase == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    generatedSeedPhrase = Mnemonic(selectedWordCount).toString()
                                    backedUpGeneratedSeed = false
                                    copiedGeneratedSeed = false
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = BitcoinOrange,
                                        contentColor = DarkBackground,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_e3b008f1),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                                border = BorderStroke(1.dp, BorderColor),
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
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.loc_47bc3f78),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        generatedFingerprint?.let { fp ->
                                            Text(
                                                text = stringResource(R.string.common_fingerprint_format, fp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = BitcoinOrange,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    DuressSeedWordGrid(words = generatedWords)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedIconButton(
                                            onClick = {
                                                generatedSeedPhrase?.let {
                                                    SecureClipboard.copyAndScheduleClear(context, it)
                                                    copiedGeneratedSeed = true
                                                }
                                            },
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BorderColor),
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = if (copiedGeneratedSeed) "Copied" else "Copy seed phrase",
                                                    tint = if (copiedGeneratedSeed) SuccessGreen else TextSecondary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        OutlinedIconButton(
                                            onClick = {
                                                generatedSeedPhrase = Mnemonic(selectedWordCount).toString()
                                                backedUpGeneratedSeed = false
                                                copiedGeneratedSeed = false
                                            },
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BorderColor),
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = stringResource(R.string.loc_7c63b77c),
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { backedUpGeneratedSeed = !backedUpGeneratedSeed },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = backedUpGeneratedSeed,
                                    onCheckedChange = { backedUpGeneratedSeed = it },
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = BitcoinOrange,
                                            uncheckedColor = TextSecondary,
                                        ),
                                )
                                Text(
                                    text = stringResource(R.string.loc_77aacbaf),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            } else {
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.loc_9ae2cb2b),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
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
                        Spacer(modifier = Modifier.height(12.dp))
                        SensitiveSeedIme {
                            OutlinedTextField(
                                value = manualSeedPhraseField,
                                onValueChange = { input ->
                                    val trimmed = input.text.trim()
                                    val isKeyOrDescriptor =
                                        trimmed.let { text ->
                                            text.startsWith("xpub") || text.startsWith("ypub") ||
                                                text.startsWith("zpub") || text.startsWith("upub") ||
                                                text.startsWith("xprv") || text.startsWith("yprv") ||
                                                text.startsWith("zprv") ||
                                                text.startsWith("[") ||
                                                text.startsWith("{") ||
                                                listOf("pkh(", "wpkh(", "tr(", "sh(").any {
                                                    text.lowercase().startsWith(it)
                                                }
                                        }
                                    val isWifInput =
                                        trimmed.let { text ->
                                            (
                                                text.length <= 52 && text.isNotEmpty() &&
                                                    (text[0] == 'K' || text[0] == 'L' || text[0] == '5')
                                            ) && !text.contains(" ")
                                        }
                                    val isAddressInput =
                                        trimmed.let { text ->
                                            text.isNotEmpty() && !text.contains(" ") &&
                                                (text.startsWith("1") || text.startsWith("3") || text.startsWith("bc1"))
                                        }
                                    manualSeedPhraseField =
                                        if (isKeyOrDescriptor || isWifInput || isAddressInput) {
                                            input
                                        } else {
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
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.loc_16bd0da2),
                                        color = TextSecondary,
                                    )
                                },
                                keyboardOptions = sensitiveSeedKeyboardOptions(),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedBorderColor = BorderColor,
                                        cursorColor = BitcoinOrange,
                                    ),
                            )
                        }

                        Bip39SuggestionRow(
                            input = manualSeedPhrase,
                            wordlist = bip39WordList,
                            onWordSelected = { completedInput ->
                                manualSeedPhraseField =
                                    TextFieldValue(
                                        text = completedInput,
                                        selection = TextRange(completedInput.length),
                                    )
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        when {
                            unsupportedNonMainnetReason != null -> {
                                Text(
                                    text = unsupportedNonMainnetReason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorRed,
                                )
                            }
                            unsupportedNestedSegwitReason != null -> {
                                Text(
                                    text = unsupportedNestedSegwitReason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorRed,
                                )
                            }
                            isJsonFormat -> {
                                Text(
                                    text = stringResource(R.string.loc_d5fabbff),
                                    style = MaterialTheme.typography.bodyMedium,
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentTeal,
                                )
                            }
                            isOriginPrefixed -> {
                                Text(
                                    text = stringResource(R.string.loc_c8579cd6),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentTeal,
                                )
                            }
                            isWatchOnly -> {
                                Column {
                                    Text(
                                        text = stringResource(R.string.loc_9dec0f67),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AccentTeal,
                                    )
                                    if (isExtendedKey && !hasEmbeddedFingerprint) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.loc_396e66e1),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BitcoinOrange,
                                        )
                                    }
                                }
                            }
                            isWifKey -> {
                                Text(
                                    text = stringResource(R.string.loc_517ece75),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuccessGreen,
                                )
                            }
                            isBitcoinAddress -> {
                                Text(
                                    text = stringResource(R.string.loc_541ee340),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentTeal,
                                )
                            }
                            isExtendedKey && !isWatchOnly -> {
                                Text(
                                    text = stringResource(R.string.loc_85e5fba3),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BitcoinOrange,
                                )
                            }
                            mnemonicValidation is DuressMnemonicValidation.ValidElectrum -> {
                                val seedLabel = mnemonicValidation.seedType.seedVariantLabel()
                                Text(
                                    text =
                                        stringResource(
                                            R.string.common_electrum_seed_format,
                                            seedLabel,
                                            wordCount,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuccessGreen,
                                )
                            }
                            isValidImportMnemonic -> {
                                Text(
                                    text = stringResource(R.string.loc_e7f0104f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SuccessGreen,
                                )
                            }
                            mnemonicValidation is DuressMnemonicValidation.Invalid -> {
                                Text(
                                    text = stringResource(R.string.loc_964abaa1),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ErrorRed,
                                )
                            }
                        }

                        parsedFingerprint?.let { fp ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.common_fingerprint_format, fp),
                                style = MaterialTheme.typography.bodySmall,
                                color = BitcoinOrange,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    }

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

                            AnimatedVisibility(
                                visible = seedSource == DuressSeedSource.EXISTING && isWatchOnly,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column {
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

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !(seedSource == DuressSeedSource.EXISTING && isExtendedKey)) {
                                            usePassphrase = !usePassphrase
                                        },
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
                                    enabled = !(seedSource == DuressSeedSource.EXISTING && isExtendedKey),
                                )
                                Text(
                                    text = stringResource(R.string.loc_75923525),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (seedSource == DuressSeedSource.EXISTING && isExtendedKey) {
                                            TextSecondary.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                )
                            }

                            AnimatedVisibility(
                                visible = usePassphrase && !(seedSource == DuressSeedSource.EXISTING && isExtendedKey),
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

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            useCustomPath = !useCustomPath
                                        },
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

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
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

                            AnimatedVisibility(
                                visible = useCustomGapLimit,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(modifier = Modifier.bringIntoViewRequester(customGapLimitBringIntoViewRequester)) {
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                                "Default: ${StoredWallet.DEFAULT_GAP_LIMIT}. Scan limit for empty addresses (1-10000)",
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val passphraseValue = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                    val customPathValue = if (useCustomPath && customPath.isNotBlank()) customPath else null
                    val config =
                        if (seedSource == DuressSeedSource.GENERATE) {
                            WalletImportConfig(
                                name = walletName.trim(),
                                keyMaterial = generatedSeedPhrase!!,
                                addressType = selectedAddressType,
                                passphrase = passphraseValue,
                                customDerivationPath = customPathValue,
                                network = WalletNetwork.BITCOIN,
                                gapLimit = duressGapLimit,
                            )
                        } else {
                            val fingerprintValue =
                                parsedFingerprint
                                    ?: if (useCustomFingerprint && masterFingerprint.length == 8) {
                                        masterFingerprint
                                    } else if (isWatchOnly) {
                                        "00000000"
                                    } else {
                                        null
                                    }
                            val seedFormat =
                                when (electrumSeedType) {
                                    ElectrumSeedUtil.ElectrumSeedType.STANDARD -> SeedFormat.ELECTRUM_STANDARD
                                    ElectrumSeedUtil.ElectrumSeedType.SEGWIT -> SeedFormat.ELECTRUM_SEGWIT
                                    null -> SeedFormat.BIP39
                                }
                            WalletImportConfig(
                                name = walletName.trim(),
                                keyMaterial = trimmedImportInput,
                                addressType = selectedAddressType,
                                passphrase = passphraseValue,
                                customDerivationPath = customPathValue,
                                network = WalletNetwork.BITCOIN,
                                isWatchOnly = isWatchOnly || isBitcoinAddress,
                                masterFingerprint = fingerprintValue,
                                seedFormat = seedFormat,
                                gapLimit = duressGapLimit,
                            )
                        }
                    onDuressSet(pin, config)
                },
                enabled = canEnableDuress,
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
                    text = stringResource(R.string.loc_189d09c6),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DuressSegmentButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backgroundColor =
        when {
            !enabled -> DarkSurfaceVariant
            isSelected -> BitcoinOrange
            else -> DarkCard
        }
    val contentColor =
        when {
            !enabled -> TextSecondary.copy(alpha = 0.5f)
            isSelected -> DarkBackground
            else -> TextSecondary
        }
    val borderColor =
        when {
            !enabled -> BorderColor.copy(alpha = 0.5f)
            isSelected -> BitcoinOrange
            else -> BorderColor
        }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun DuressSeedWordGrid(words: List<String>) {
    val half = (words.size + 1) / 2

    Column(modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until half) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    DuressSeedWordItem(index = i + 1, word = words[i])
                }
                val rightIndex = i + half
                if (rightIndex < words.size) {
                    Box(modifier = Modifier.weight(1f)) {
                        DuressSeedWordItem(
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

@Composable
private fun DuressSeedWordItem(
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
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
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
                        contentDescription = stringResource(R.string.loc_cdfc6e09),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    if (step == 1) {
                        stringResource(R.string.loc_bbc273c5)
                    } else {
                        stringResource(R.string.loc_b02d0f6b)
                    },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.loc_1d1e82ec),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.loc_75f1a5fd),
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

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

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(28.dp))
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

            Spacer(modifier = Modifier.height(24.dp))

            // Number pad
            PinNumberPad(
                onNumberClick = { number ->
                    val maxLen = if (step == 2) code.length else MAX_CLOAK_CODE_LENGTH
                    val currentCode = if (step == 1) code else confirmCode
                    if (currentCode.length < maxLen) {
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
                    when (step) {
                        1 ->
                            if (code.length >= MIN_CLOAK_CODE_LENGTH) {
                                {
                                    step = 2
                                    error = null
                                }
                            } else {
                                null
                            }
                        2 ->
                            if (confirmCode.length == code.length) {
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
                            }
                        else -> null
                    },
                confirmLabel = if (step == 1) "Next" else "Enable",
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
