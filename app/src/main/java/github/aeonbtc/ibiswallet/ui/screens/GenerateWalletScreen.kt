package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.model.AddressType
import github.aeonbtc.ibiswallet.data.model.WalletImportConfig
import github.aeonbtc.ibiswallet.data.model.WalletNetwork
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.WordCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateWalletScreen(
    onGenerate: (config: WalletImportConfig) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null
) {
    val context = LocalContext.current

    var walletName by remember { mutableStateOf("") }
    var selectedAddressType by remember { mutableStateOf(AddressType.SEGWIT) }
    var selectedWordCount by remember { mutableStateOf(WordCount.WORDS12) }

    // Advanced options
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var usePassphrase by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var useCustomPath by remember { mutableStateOf(false) }
    var customPath by remember { mutableStateOf("") }

    // Generation state
    var generatedMnemonic by remember { mutableStateOf<String?>(null) }
    var backedUp by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    // Reset copied state after 3 seconds
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(3000)
            copied = false
        }
    }

    // Generate mnemonic using BDK (entropy sourced from platform CSPRNG)
    fun generateMnemonic() {
        val mnemonic = Mnemonic(selectedWordCount)
        generatedMnemonic = mnemonic.toString()
        backedUp = false
        copied = false
    }

    val words = generatedMnemonic?.trim()?.split("\\s+".toRegex()) ?: emptyList()

    // Dynamically derive master fingerprint from generated mnemonic
    var derivedFingerprint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(generatedMnemonic, selectedAddressType, passphrase, usePassphrase) {
        val mnemonic = generatedMnemonic
        if (mnemonic != null) {
            derivedFingerprint = withContext(Dispatchers.Default) {
                try {
                    val mnemonicObj = Mnemonic.fromString(mnemonic)
                    val pass = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                    val secretKey = DescriptorSecretKey(Network.BITCOIN, mnemonicObj, pass)
                    val descriptor = when (selectedAddressType) {
                        AddressType.LEGACY -> Descriptor.newBip44(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
                        AddressType.NESTED_SEGWIT -> Descriptor.newBip49(secretKey, KeychainKind.EXTERNAL, Network.BITCOIN)
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
            derivedFingerprint = null
        }
    }

    val canCreate = walletName.isNotBlank() && generatedMnemonic != null && backedUp && !isLoading

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
                text = "Generate Wallet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main form card
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
                // Wallet Name
                Text(
                    text = "Wallet Name",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = walletName,
                    onValueChange = { walletName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("My Wallet", color = TextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = BitcoinOrange
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Address Type
                Text(
                    text = "Address Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddressType.entries
                        .filter { it != AddressType.NESTED_SEGWIT }
                        .forEach { addressType ->
                            GenerateAddressTypeButton(
                                addressType = addressType,
                                isSelected = selectedAddressType == addressType,
                                onClick = { selectedAddressType = addressType },
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedAddressType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Seed Phrase Length
                Text(
                    text = "Seed Phrase Length",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WordCountOption.entries.forEach { option ->
                        val isSelected = selectedWordCount == option.wordCount
                        val backgroundColor = if (isSelected) BitcoinOrange else DarkSurfaceVariant
                        val contentColor = if (isSelected) DarkBackground else TextSecondary
                        val borderColor = if (isSelected) BitcoinOrange else BorderColor

                        Surface(
                            onClick = {
                                selectedWordCount = option.wordCount
                                generatedMnemonic = null
                                backedUp = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = backgroundColor,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${selectedWordCount.toEntropy()} bits of entropy",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Advanced Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    onClick = { showAdvancedOptions = !showAdvancedOptions },
                    color = DarkCard,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced Options",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = if (showAdvancedOptions)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showAdvancedOptions) "Collapse" else "Expand",
                            tint = TextSecondary
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showAdvancedOptions,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        HorizontalDivider(color = BorderColor)

                        Spacer(modifier = Modifier.height(4.dp))

                        // Use BIP39 Passphrase
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = usePassphrase,
                                onCheckedChange = { usePassphrase = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BitcoinOrange,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Text(
                                text = "Use BIP39 Passphrase",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        AnimatedVisibility(
                            visible = usePassphrase,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    placeholder = {
                                        Text(
                                            "Enter passphrase",
                                            color = TextSecondary.copy(alpha = 0.5f)
                                        )
                                    },
                                    visualTransformation = if (showPassphrase)
                                        VisualTransformation.None
                                    else
                                        PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        autoCorrect = false,
                                        keyboardType = KeyboardType.Password
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                            Icon(
                                                imageVector = if (showPassphrase)
                                                    Icons.Default.Visibility
                                                else
                                                    Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        cursorColor = BitcoinOrange
                                    )
                                )
                            }
                        }

                        // Use Custom Derivation Path
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = useCustomPath,
                                onCheckedChange = { useCustomPath = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BitcoinOrange,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Text(
                                text = "Use Custom Derivation Path",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        AnimatedVisibility(
                            visible = useCustomPath,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customPath,
                                    onValueChange = { customPath = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    placeholder = {
                                        Text(
                                            selectedAddressType.defaultPath,
                                            color = TextSecondary.copy(alpha = 0.5f)
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        cursorColor = BitcoinOrange
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Default: ${selectedAddressType.defaultPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Generate / Seed phrase display
        if (generatedMnemonic == null) {
            Surface(
                onClick = { generateMnemonic() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = BitcoinOrange.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.3f))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "Generate Seed Phrase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BitcoinOrange
                    )
                }
            }
        } else {
            // Seed phrase display
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
                        Text(
                            text = "Your Seed Phrase",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        derivedFingerprint?.let { fp ->
                            Text(
                                text = "Fingerprint: $fp",
                                style = MaterialTheme.typography.bodySmall,
                                color = BitcoinOrange
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Word grid (2 columns)
                    val half = (words.size + 1) / 2
                    Column(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until half) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SeedWordItem(index = i + 1, word = words[i])
                                }
                                val rightIndex = i + half
                                if (rightIndex < words.size) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SeedWordItem(
                                            index = rightIndex + 1,
                                            word = words[rightIndex]
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Copy + Regenerate buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                generatedMnemonic?.let {
                                    SecureClipboard.copyAndScheduleClear(context, "Mnemonic", it)
                                    copied = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (copied) SuccessGreen else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy",
                                color = if (copied) SuccessGreen else TextSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = { generateMnemonic() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Regenerate",
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Backup confirmation checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = backedUp,
                    onCheckedChange = { backedUp = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BitcoinOrange,
                        uncheckedColor = TextSecondary
                    )
                )
                Text(
                    text = "I have backed up my seed phrase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Create Wallet button
            Button(
                onClick = {
                    val passphraseValue = if (usePassphrase && passphrase.isNotBlank()) passphrase else null
                    val customPathValue = if (useCustomPath && customPath.isNotBlank()) customPath else null
                    val config = WalletImportConfig(
                        name = walletName.trim(),
                        keyMaterial = generatedMnemonic!!,
                        addressType = selectedAddressType,
                        passphrase = passphraseValue,
                        customDerivationPath = customPathValue,
                        network = WalletNetwork.BITCOIN
                    )
                    onGenerate(config)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = canCreate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BitcoinOrange,
                    disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Wallet")
                }
            }
        }

        // Error message
        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ErrorRed.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SeedWordItem(
    index: Int,
    word: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

@Composable
private fun GenerateAddressTypeButton(
    addressType: AddressType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) BitcoinOrange else DarkSurfaceVariant
    val contentColor = if (isSelected) DarkBackground else TextSecondary
    val borderColor = if (isSelected) BitcoinOrange else BorderColor

    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = addressType.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

private enum class WordCountOption(val label: String, val wordCount: WordCount) {
    TWELVE("12 words", WordCount.WORDS12),
    TWENTY_FOUR("24 words", WordCount.WORDS24);
}

private fun WordCount.toEntropy(): Int = when (this) {
    WordCount.WORDS12 -> 128
    WordCount.WORDS15 -> 160
    WordCount.WORDS18 -> 192
    WordCount.WORDS21 -> 224
    WordCount.WORDS24 -> 256
}
