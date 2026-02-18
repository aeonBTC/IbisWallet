package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.theme.*
import kotlinx.coroutines.delay

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 12

@Composable
fun LockScreen(
    securityMethod: SecureStorage.SecurityMethod,
    onPinEntered: (String) -> Boolean,
    onBiometricRequest: () -> Unit,
    isBiometricAvailable: Boolean = false,
    storedPinLength: Int? = null,
    isDuressWithBiometric: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var pendingValidation by remember { mutableStateOf(false) }
    val pinMaxLength = storedPinLength ?: MAX_PIN_LENGTH

    // Auto-trigger biometric on first composition if biometric is the security method
    // Skip auto-trigger in duress+biometric mode (the C button is the hidden trigger)
    LaunchedEffect(securityMethod) {
        if (securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && isBiometricAvailable && !isDuressWithBiometric) {
            onBiometricRequest()
        }
    }

    // Handle delayed PIN validation so user can see the last dot
    LaunchedEffect(pendingValidation) {
        if (pendingValidation) {
            delay(5)
            val success = onPinEntered(pin)
            if (!success) {
                attempts++
                error =
                    if (attempts >= 6) {
                        "Too many attempts"
                    } else {
                        "Incorrect PIN"
                    }
                pin = ""
            }
            pendingValidation = false
        }
    }

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
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text =
                    when {
                        isDuressWithBiometric -> "Enter PIN to unlock"
                        securityMethod == SecureStorage.SecurityMethod.BIOMETRIC -> "Authenticate to continue"
                        securityMethod == SecureStorage.SecurityMethod.PIN -> "Enter PIN to unlock"
                        else -> ""
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )

            // PIN dots indicator - fixed height container to prevent layout jumps
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (pin.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(pin.length) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(BitcoinOrange),
                            )
                        }
                    }
                }
            }

            // Error message - fixed height to prevent layout jumps
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

            Spacer(modifier = Modifier.height(16.dp))

            // Biometric button (for biometric mode or as fallback option)
            // Hidden in duress+biometric mode — the C button is the secret trigger
            if (securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && isBiometricAvailable && !isDuressWithBiometric) {
                IconButton(
                    onClick = { onBiometricRequest() },
                    modifier =
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(DarkCard),
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Use biometric",
                        tint = BitcoinOrange,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Tap to use biometric\nor enter PIN below",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Number pad
            NumberPad(
                onNumberClick = { number ->
                    if (pin.length < pinMaxLength && !pendingValidation) {
                        val newPin = pin + number
                        pin = newPin
                        error = null

                        // Auto-submit when PIN reaches the stored length
                        if (storedPinLength != null && newPin.length == storedPinLength) {
                            pendingValidation = true
                        }
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        error = null
                    }
                },
                // Show Unlock button only when stored PIN length is unknown (fallback)
                onConfirmClick =
                    if (storedPinLength == null && pin.length >= MIN_PIN_LENGTH && !pendingValidation) {
                        { pendingValidation = true }
                    } else {
                        null
                    },
                onBiometricClick =
                    if (isBiometricAvailable && securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && !isDuressWithBiometric) {
                        { onBiometricRequest() }
                    } else {
                        null
                    },
                // In duress+biometric mode, the "C" button clears input and triggers biometric
                onClearWithBiometricClick =
                    if (isDuressWithBiometric && isBiometricAvailable) {
                        {
                            pin = ""
                            error = null
                            onBiometricRequest()
                        }
                    } else {
                        null
                    },
                showConfirmButton = storedPinLength == null,
            )
        }
    }
}

@Composable
private fun NumberPad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirmClick: (() -> Unit)? = null,
    onBiometricClick: (() -> Unit)? = null,
    onClearWithBiometricClick: (() -> Unit)? = null,
    showConfirmButton: Boolean = true,
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
                        // Bottom-left: "C" (clear+biometric) button, biometric button, or empty
                        rowIndex == 3 && colIndex == 0 -> {
                            if (onClearWithBiometricClick != null) {
                                // Disguised "C" button — looks like a clear button,
                                // actually clears input and triggers biometric prompt
                                Button(
                                    onClick = onClearWithBiometricClick,
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = DarkCard,
                                            contentColor = TextSecondary,
                                        ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = "C",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontSize = 28.sp,
                                    )
                                }
                            } else if (onBiometricClick != null) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier =
                                        Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(DarkCard),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Biometric",
                                        tint = BitcoinOrange,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                        // Bottom-right: confirm button (when available) stacked with backspace
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
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
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

        // Confirm button row below the number pad (hidden when auto-submit is active)
        if (showConfirmButton) {
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
                    text = "Unlock",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
