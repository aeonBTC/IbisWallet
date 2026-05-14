package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.security.SecureRandom
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 12

@Composable
fun LockScreen(
    securityMethod: SecureStorage.SecurityMethod,
    onPinEntered: suspend (String) -> Boolean,
    onBiometricRequest: () -> Unit,
    isBiometricAvailable: Boolean = false,
    randomizePinPad: Boolean = false,
    isDuressWithBiometric: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var pendingValidation by remember { mutableStateOf(false) }
    val pinMaxLength = MAX_PIN_LENGTH
    val incorrectPinMsg = stringResource(R.string.loc_0a18f141)
    val tooManyAttemptsMsg = stringResource(R.string.loc_364b0d65)

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
                    if (attempts >= SecureStorage.MAX_PIN_ATTEMPTS) {
                        tooManyAttemptsMsg
                    } else {
                        incorrectPinMsg
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
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text =
                    when {
                        isDuressWithBiometric -> stringResource(R.string.loc_bf4a5bdb)
                        securityMethod == SecureStorage.SecurityMethod.BIOMETRIC ->
                            stringResource(R.string.loc_cabf9832)
                        securityMethod == SecureStorage.SecurityMethod.PIN ->
                            stringResource(R.string.loc_bf4a5bdb)
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
                        .heightIn(min = 52.dp),
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
                        contentDescription = stringResource(R.string.loc_d00009b0),
                        tint = BitcoinOrange,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.loc_10459bad),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Number pad
            NumberPad(
                backspaceContentDescription = stringResource(R.string.loc_3d1100cc),
                biometricContentDescription = stringResource(R.string.loc_d00009b0),
                onNumberClick = { number ->
                    if (pin.length < pinMaxLength && !pendingValidation) {
                        val newPin = pin + number
                        pin = newPin
                        error = null

                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        error = null
                    }
                },
                onConfirmClick =
                    if (pin.length >= MIN_PIN_LENGTH && !pendingValidation) {
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
                randomizeNumbers = randomizePinPad,
            )
        }
    }
}

@Composable
private fun NumberPad(
    backspaceContentDescription: String,
    biometricContentDescription: String,
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirmClick: (() -> Unit)? = null,
    onBiometricClick: (() -> Unit)? = null,
    onClearWithBiometricClick: (() -> Unit)? = null,
    randomizeNumbers: Boolean = false,
) {
    val numbers = remember(randomizeNumbers) { buildNumberPadRows(randomizeNumbers) }

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
                        // Bottom-left: "C" (clear+biometric) button, biometric button, or empty
                        rowIndex == 3 && colIndex == 0 -> {
                            if (onClearWithBiometricClick != null) {
                                // Disguised "C" button — looks like a clear button,
                                // actually clears input and triggers biometric prompt
                                Button(
                                    onClick = onClearWithBiometricClick,
                                    modifier = Modifier.size(keypadButtonSize),
                                    shape = CircleShape,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = DarkCard,
                                            contentColor = TextSecondary,
                                        ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.loc_3dd7ffa7),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontSize = 28.sp,
                                    )
                                }
                            } else if (onBiometricClick != null) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier =
                                        Modifier
                                            .size(keypadButtonSize)
                                            .clip(CircleShape)
                                            .background(DarkCard),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = biometricContentDescription,
                                        tint = BitcoinOrange,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(keypadButtonSize))
                            }
                        }
                        // Bottom-right: confirm button (when available) stacked with backspace
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
                                    contentDescription = backspaceContentDescription,
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
                text = stringResource(R.string.loc_6d48b5d9),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun buildNumberPadRows(randomizeNumbers: Boolean): List<List<String>> {
    if (!randomizeNumbers) {
        return listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", ""),
        )
    }

    val digits =
        mutableListOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").apply {
            val random = SecureRandom()
            for (index in lastIndex downTo 1) {
                val swapIndex = random.nextInt(index + 1)
                val temp = this[index]
                this[index] = this[swapIndex]
                this[swapIndex] = temp
            }
        }

    return listOf(
        listOf(digits[0], digits[1], digits[2]),
        listOf(digits[3], digits[4], digits[5]),
        listOf(digits[6], digits[7], digits[8]),
        listOf("", digits[9], ""),
    )
}
