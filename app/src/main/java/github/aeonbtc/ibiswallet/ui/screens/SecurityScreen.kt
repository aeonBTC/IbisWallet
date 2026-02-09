package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.*

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    currentSecurityMethod: SecureStorage.SecurityMethod = SecureStorage.SecurityMethod.NONE,
    currentLockTiming: SecureStorage.LockTiming = SecureStorage.LockTiming.WHEN_MINIMIZED,
    isBiometricAvailable: Boolean = false,
    screenshotsDisabled: Boolean = false,
    onSetPinCode: (String) -> Unit = {},
    onEnableBiometric: () -> Unit = {},
    onDisableSecurity: () -> Unit = {},
    onLockTimingChange: (SecureStorage.LockTiming) -> Unit = {},
    onScreenshotsDisabledChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var showPinSetup by remember { mutableStateOf(false) }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }
    
    if (showPinSetup) {
        PinSetupScreen(
            onPinSet = { pin ->
                onSetPinCode(pin)
                showPinSetup = false
            },
            onBack = {
                showPinSetup = false
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    text = "Security",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Options
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
                        text = "App Lock",
                        style = MaterialTheme.typography.titleMedium,
                        color = BitcoinOrange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Biometric Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isBiometricAvailable) BitcoinOrange else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Biometric Unlock",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isBiometricAvailable) 
                                        MaterialTheme.colorScheme.onBackground 
                                    else 
                                        TextSecondary.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (isBiometricAvailable) 
                                        "Use fingerprint or face" 
                                    else 
                                        "Not available on this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        
                        SquareToggle(
                            checked = currentSecurityMethod == SecureStorage.SecurityMethod.BIOMETRIC,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    onEnableBiometric()
                                } else {
                                    showDisableConfirmDialog = true
                                }
                            },
                            enabled = isBiometricAvailable
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // PIN Code Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PIN Code",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "$MIN_PIN_LENGTH\u2013$MAX_PIN_LENGTH digit unlock code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        
                        SquareToggle(
                            checked = currentSecurityMethod == SecureStorage.SecurityMethod.PIN,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinSetup = true
                                } else {
                                    showDisableConfirmDialog = true
                                }
                            }
                        )
                    }
                    
                    // Lock timing dropdown (only shown when security is enabled)
                    if (currentSecurityMethod != SecureStorage.SecurityMethod.NONE) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = BorderColor
                        )
                        
                        Text(
                            text = "Lock Timing",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LockTimingDropdown(
                            currentTiming = currentLockTiming,
                            onTimingSelected = onLockTimingChange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Screenshot Prevention
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Disable Screenshots",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Blocks screenshots and app previews",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        SquareToggle(
                            checked = screenshotsDisabled,
                            onCheckedChange = onScreenshotsDisabledChange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Disable Security Confirmation Dialog
        if (showDisableConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDisableConfirmDialog = false },
                title = {
                    Text(
                        text = "Disable Security?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                text = {
                    Text(
                        text = "Anyone with access to your device will be able to open the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDisableSecurity()
                            showDisableConfirmDialog = false
                        }
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
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun PinSetupScreen(
    onPinSet: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) } // 1 = enter PIN, 2 = confirm PIN
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
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
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = if (step == 1) "Create PIN" else "Confirm PIN",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    step == 1 -> "Enter a $MIN_PIN_LENGTH\u2013$MAX_PIN_LENGTH digit PIN"
                    else -> "Enter the PIN again to confirm"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // PIN dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentPin = if (step == 1) pin else confirmPin
                val targetLength = if (step == 2) pin.length else MIN_PIN_LENGTH
                repeat(currentPin.length) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(BitcoinOrange)
                    )
                }
                // Show empty dots up to target length
                val emptyDots = (targetLength - currentPin.length).coerceAtLeast(0)
                repeat(emptyDots) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(BorderColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        textAlign = TextAlign.Center
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
                onConfirmClick = if (step == 1 && pin.length >= MIN_PIN_LENGTH) {
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
                } else null,
                confirmLabel = if (step == 1) "Next" else "Confirm"
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
    confirmLabel: String = "Confirm"
) {
    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "")
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        numbers.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
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
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        // Number buttons
                        number.isNotEmpty() -> {
                            Button(
                                onClick = { onNumberClick(number) },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkCard,
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontSize = 28.sp
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
            modifier = Modifier
                .width(240.dp)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BitcoinOrange,
                contentColor = DarkBackground,
                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                disabledContentColor = DarkBackground.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = confirmLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTimingDropdown(
    currentTiming: SecureStorage.LockTiming,
    onTimingSelected: (SecureStorage.LockTiming) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentTiming.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BitcoinOrange,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = BitcoinOrange,
                unfocusedLabelColor = TextSecondary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            shape = RoundedCornerShape(8.dp)
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .exposedDropdownSize(true)
                .background(DarkSurface)
        ) {
            SecureStorage.LockTiming.entries.forEach { timing ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = timing.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (timing == currentTiming) 
                                BitcoinOrange 
                            else 
                                MaterialTheme.colorScheme.onBackground
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
                                tint = BitcoinOrange
                            )
                        }
                    }
                )
            }
        }
    }
}
