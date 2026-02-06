package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
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

private const val PIN_LENGTH = 5

@Composable
fun LockScreen(
    securityMethod: SecureStorage.SecurityMethod,
    onPinEntered: (String) -> Boolean,
    onBiometricRequest: () -> Unit,
    isBiometricAvailable: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var pendingValidation by remember { mutableStateOf(false) }

    // Auto-trigger biometric on first composition if biometric is the security method
    LaunchedEffect(securityMethod) {
        if (securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && isBiometricAvailable) {
            onBiometricRequest()
        }
    }
    
    // Handle delayed PIN validation so user can see the 5th dot
    LaunchedEffect(pendingValidation) {
        if (pendingValidation) {
            delay(200)
            val success = onPinEntered(pin)
            if (!success) {
                attempts++
                error = if (attempts >= 3) {
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
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // App Icon/Title
            Text(
                text = "Ibis Wallet",
                style = MaterialTheme.typography.headlineMedium,
                color = BitcoinOrange
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (securityMethod) {
                    SecureStorage.SecurityMethod.BIOMETRIC -> "Authenticate to continue"
                    SecureStorage.SecurityMethod.PIN -> "Enter PIN to unlock"
                    SecureStorage.SecurityMethod.NONE -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // PIN dots indicator (shown for both PIN and biometric as fallback)
            if (securityMethod == SecureStorage.SecurityMethod.PIN || 
                (securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && pin.isNotEmpty())) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(PIN_LENGTH) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < pin.length) BitcoinOrange
                                    else BorderColor
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error message
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Biometric button (for biometric mode or as fallback option)
            if (securityMethod == SecureStorage.SecurityMethod.BIOMETRIC && isBiometricAvailable) {
                IconButton(
                    onClick = { onBiometricRequest() },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Use biometric",
                        tint = BitcoinOrange,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Tap to use biometric\nor enter PIN below",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Number pad
            NumberPad(
                onNumberClick = { number ->
                    if (pin.length < PIN_LENGTH && !pendingValidation) {
                        pin += number
                        error = null
                        
                        // Auto-submit when PIN is complete (with delay)
                        if (pin.length == PIN_LENGTH) {
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
                onBiometricClick = if (isBiometricAvailable && securityMethod == SecureStorage.SecurityMethod.BIOMETRIC) {
                    { onBiometricRequest() }
                } else null
            )
        }
    }
}

@Composable
private fun NumberPad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null
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
                        // Biometric button in bottom-left
                        rowIndex == 3 && colIndex == 0 -> {
                            if (onBiometricClick != null) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(DarkCard)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Biometric",
                                        tint = BitcoinOrange,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
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
    }
}
