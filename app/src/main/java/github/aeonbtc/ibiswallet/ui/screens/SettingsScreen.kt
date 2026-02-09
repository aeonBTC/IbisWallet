package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDenomination: String = SecureStorage.DENOMINATION_BTC,
    onDenominationChange: (String) -> Unit = {},
    spendUnconfirmed: Boolean = true,
    onSpendUnconfirmedChange: (Boolean) -> Unit = {},
    currentFeeSource: String = SecureStorage.FEE_SOURCE_OFF,
    onFeeSourceChange: (String) -> Unit = {},
    customFeeSourceUrl: String = "",
    onCustomFeeSourceUrlSave: (String) -> Unit = {},
    currentPriceSource: String = SecureStorage.PRICE_SOURCE_OFF,
    onPriceSourceChange: (String) -> Unit = {},
    currentMempoolServer: String = SecureStorage.MEMPOOL_SPACE,
    onMempoolServerChange: (String) -> Unit = {},
    customMempoolUrl: String = "",
    onCustomMempoolUrlSave: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
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
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Card 1: Appearance & Display ──
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
                    text = "Display",
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSats = currentDenomination == SecureStorage.DENOMINATION_SATS
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSats) "Sats" else "BTC",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isSats) "Amounts shown in satoshis" else "Amounts shown in bitcoin",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SquareToggle(
                        checked = isSats,
                        onCheckedChange = { useSats ->
                            onDenominationChange(
                                if (useSats) SecureStorage.DENOMINATION_SATS
                                else SecureStorage.DENOMINATION_BTC
                            )
                        },
                        checkedColor = TextSecondary,
                        uncheckedColor = TextSecondary.copy(alpha = 0.3f),
                        uncheckedBorderColor = TextSecondary,
                        uncheckedThumbColor = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Card 2: Transaction Settings ──
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
                    text = "Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Spend Unconfirmed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Allow spending unconfirmed UTXOs",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    SquareToggle(
                        checked = spendUnconfirmed,
                        onCheckedChange = onSpendUnconfirmedChange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Card 3: External Services ──
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
                    text = "External Services",
                    style = MaterialTheme.typography.titleMedium,
                    color = BitcoinOrange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Fee Rate Source
                Text(
                    text = "Fee Rate Source",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                FeeSourceDropdown(
                    currentSource = currentFeeSource,
                    onSourceSelected = onFeeSourceChange
                )

                // Custom fee server URL field (shown only when Custom is selected)
                if (currentFeeSource == SecureStorage.FEE_SOURCE_CUSTOM) {
                    Spacer(modifier = Modifier.height(6.dp))

                    var feeUrlDraft by remember(customFeeSourceUrl) {
                        mutableStateOf(customFeeSourceUrl)
                    }
                    var feeUrlError by remember { mutableStateOf<String?>(null) }
                    var feeUrlSaved by remember { mutableStateOf<String?>(null) }

                    CompactTextFieldWithSave(
                        value = feeUrlDraft,
                        onValueChange = {
                            feeUrlDraft = it
                            feeUrlError = null
                            feeUrlSaved = null
                        },
                        onSave = {
                            val error = validateServerUrl(feeUrlDraft)
                            if (error != null) {
                                feeUrlError = error
                                feeUrlSaved = null
                            } else {
                                feeUrlError = null
                                onCustomFeeSourceUrlSave(feeUrlDraft)
                                feeUrlSaved = "Server saved"
                            }
                        },
                        placeholder = "http://192.168... or http://...onion",
                        errorMessage = feeUrlError,
                        successMessage = feeUrlSaved,
                        modifier = Modifier.padding(start = 24.dp)
                    )

                    if (feeUrlDraft.contains(".onion")) {
                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Tor will automatically be enabled to access this server",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 24.dp),
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Block Explorer
                Text(
                    text = "Block Explorer",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                MempoolServerDropdown(
                    currentServer = currentMempoolServer,
                    onServerSelected = onMempoolServerChange
                )

                // Custom URL input field (shown only when Custom Server is selected)
                if (currentMempoolServer == SecureStorage.MEMPOOL_CUSTOM) {
                    Spacer(modifier = Modifier.height(6.dp))

                    var mempoolUrlDraft by remember(customMempoolUrl) {
                        mutableStateOf(customMempoolUrl)
                    }
                    var mempoolUrlError by remember { mutableStateOf<String?>(null) }
                    var mempoolUrlSaved by remember { mutableStateOf<String?>(null) }

                    CompactTextFieldWithSave(
                        value = mempoolUrlDraft,
                        onValueChange = {
                            mempoolUrlDraft = it
                            mempoolUrlError = null
                            mempoolUrlSaved = null
                        },
                        onSave = {
                            val error = validateServerUrl(mempoolUrlDraft)
                            if (error != null) {
                                mempoolUrlError = error
                                mempoolUrlSaved = null
                            } else {
                                mempoolUrlError = null
                                onCustomMempoolUrlSave(mempoolUrlDraft)
                                mempoolUrlSaved = "Server saved"
                            }
                        },
                        placeholder = "http://192.168... or http://...onion",
                        errorMessage = mempoolUrlError,
                        successMessage = mempoolUrlSaved,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BTC/USD Price Source
                Text(
                    text = "USD Price Source",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                PriceSourceDropdown(
                    currentSource = currentPriceSource,
                    onSourceSelected = onPriceSourceChange
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Data class for mempool server options
 */
private data class MempoolServerOption(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Dropdown for selecting mempool server
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MempoolServerDropdown(
    currentServer: String,
    onServerSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val serverOptions = listOf(
        MempoolServerOption(
            id = SecureStorage.MEMPOOL_DISABLED,
            name = "Disabled",
            description = "Don't show block explorer links"
        ),
        MempoolServerOption(
            id = SecureStorage.MEMPOOL_SPACE,
            name = "mempool.space",
            description = "Clearnet server (HTTPS)"
        ),
        MempoolServerOption(
            id = SecureStorage.MEMPOOL_ONION,
            name = "mempool.space (Onion)",
            description = "Onion address (requires Tor Browser)"
        ),
        MempoolServerOption(
            id = SecureStorage.MEMPOOL_CUSTOM,
            name = "Custom Server",
            description = "Configure custom mempool instance"
        )
    )
    
    val selectedOption = serverOptions.find { it.id == currentServer } ?: serverOptions.first()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .exposedDropdownSize(true)
                .background(DarkSurface)
        ) {
            serverOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (option.id == currentServer) 
                                    BitcoinOrange 
                                else 
                                    MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    },
                    onClick = {
                        onServerSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentServer) {
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

/**
 * Data class for fee source options
 */
private data class FeeSourceOption(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Dropdown for selecting fee estimation source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeeSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val sourceOptions = listOf(
        FeeSourceOption(
            id = SecureStorage.FEE_SOURCE_OFF,
            name = "Disabled",
            description = "Manual fee entry only"
        ),
        FeeSourceOption(
            id = SecureStorage.FEE_SOURCE_MEMPOOL,
            name = "mempool.space",
            description = "Fetch from mempool.space"
        ),
        FeeSourceOption(
            id = SecureStorage.FEE_SOURCE_ELECTRUM,
            name = "Electrum Server",
            description = "Fetch from connected Electrum server"
        ),
        FeeSourceOption(
            id = SecureStorage.FEE_SOURCE_CUSTOM,
            name = "Custom Server",
            description = "Fetch from custom mempool instance"
        )
    )
    
    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .exposedDropdownSize(true)
                .background(DarkSurface)
        ) {
            sourceOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (option.id == currentSource) 
                                    BitcoinOrange 
                                else 
                                    MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
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

/**
 * Data class for price source options
 */
private data class PriceSourceOption(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Dropdown for selecting BTC/USD price source
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceSourceDropdown(
    currentSource: String,
    onSourceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val sourceOptions = listOf(
        PriceSourceOption(
            id = SecureStorage.PRICE_SOURCE_OFF,
            name = "Disabled",
            description = "Don't show USD values"
        ),
        PriceSourceOption(
            id = SecureStorage.PRICE_SOURCE_MEMPOOL,
            name = "mempool.space",
            description = "Fetch price from mempool.space"
        ),
        PriceSourceOption(
            id = SecureStorage.PRICE_SOURCE_COINGECKO,
            name = "CoinGecko",
            description = "Fetch price from CoinGecko"
        )
    )
    
    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            modifier = Modifier.menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .exposedDropdownSize(true)
                .background(DarkSurface)
        ) {
            sourceOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (option.id == currentSource) 
                                    BitcoinOrange 
                                else 
                                    MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    },
                    onClick = {
                        onSourceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentSource) {
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

/**
 * Compact read-only dropdown field with proper text alignment (no clipping).
 */
@Composable
private fun CompactDropdownField(
    value: String,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (expanded) BitcoinOrange else BorderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = TextStyle(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Validates a server URL. Returns an error message if invalid, null if valid.
 */
private fun validateServerUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return "URL cannot be empty"
    val lower = trimmed.lowercase()
    val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
    val isOnion = lower.contains(".onion")
    if (!hasScheme && !isOnion) return "URL must start with http:// or https://"
    return null
}

/**
 * Compact editable text field with a right-aligned Save button and optional error message.
 */
@Composable
private fun CompactTextFieldWithSave(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    placeholder: String,
    errorMessage: String? = null,
    successMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        errorMessage != null -> ErrorRed
        successMessage != null -> SuccessGreen
        else -> BorderColor
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(BitcoinOrange),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(fontSize = 13.sp),
                                color = TextSecondary.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Text(
                text = "Save",
                style = TextStyle(fontSize = 13.sp),
                color = BitcoinOrange,
                modifier = Modifier
                    .clickable(onClick = onSave)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        } else if (successMessage != null) {
            Text(
                text = successMessage,
                style = MaterialTheme.typography.bodySmall,
                color = SuccessGreen,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
