package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDenomination: String = SecureStorage.DENOMINATION_BTC,
    onDenominationChange: (String) -> Unit = {},
    currentFeeSource: String = SecureStorage.FEE_SOURCE_OFF,
    onFeeSourceChange: (String) -> Unit = {},
    currentPriceSource: String = SecureStorage.PRICE_SOURCE_OFF,
    onPriceSourceChange: (String) -> Unit = {},
    currentMempoolServer: String = SecureStorage.MEMPOOL_SPACE,
    onMempoolServerChange: (String) -> Unit = {},
    customMempoolUrl: String = "",
    onCustomMempoolUrlChange: (String) -> Unit = {},
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

        // Display Settings Section
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

                    // Denomination Setting
                    Text(
                        text = "Amount Denomination",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Denomination Dropdown
                    DenominationDropdown(
                        currentDenomination = currentDenomination,
                        onDenominationSelected = onDenominationChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fee Estimation Settings Section
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
                        text = "Fee Estimation",
                        style = MaterialTheme.typography.titleMedium,
                        color = BitcoinOrange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fee Source Setting
                    Text(
                        text = "Fee Rate Source",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Fee Source Dropdown
                    FeeSourceDropdown(
                        currentSource = currentFeeSource,
                        onSourceSelected = onFeeSourceChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // BTC/USD Price Settings Section
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
                        text = "BTC/USD Price",
                        style = MaterialTheme.typography.titleMedium,
                        color = BitcoinOrange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Price Source Setting
                    Text(
                        text = "Price Source",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Price Source Dropdown
                    PriceSourceDropdown(
                        currentSource = currentPriceSource,
                        onSourceSelected = onPriceSourceChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Block Explorer Settings Section
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
                        text = "Block Explorer",
                        style = MaterialTheme.typography.titleMedium,
                        color = BitcoinOrange
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Mempool Server Setting
                    Text(
                        text = "Mempool Server",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Mempool Server Dropdown
                    MempoolServerDropdown(
                        currentServer = currentMempoolServer,
                        onServerSelected = onMempoolServerChange
                    )
                    
                    // Custom URL input field (shown only when Custom Server is selected)
                    if (currentMempoolServer == SecureStorage.MEMPOOL_CUSTOM) {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Custom Server URL",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        OutlinedTextField(
                            value = customMempoolUrl,
                            onValueChange = onCustomMempoolUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "https://mempool.space or http://...onion",
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
                    }
                }
            }
            
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Data class for denomination options
 */
private data class DenominationOption(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Dropdown for selecting amount denomination
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DenominationDropdown(
    currentDenomination: String,
    onDenominationSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val denominationOptions = listOf(
        DenominationOption(
            id = SecureStorage.DENOMINATION_BTC,
            name = "BTC",
            description = "Display amounts in Bitcoin (e.g., 0.00150000)"
        ),
        DenominationOption(
            id = SecureStorage.DENOMINATION_SATS,
            name = "Sats",
            description = "Display amounts in Satoshis (e.g., 150,000)"
        )
    )
    
    val selectedOption = denominationOptions.find { it.id == currentDenomination } ?: denominationOptions.first()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedOption.name,
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
            denominationOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (option.id == currentDenomination) 
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
                        onDenominationSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentDenomination) {
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
            description = "Don't show block explorer link"
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
            description = "Configure your own mempool instance"
        )
    )
    
    val selectedOption = serverOptions.find { it.id == currentServer } ?: serverOptions.first()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedOption.name,
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
            description = "Fetch fee estimates from mempool.space"
        )
    )
    
    val selectedOption = sourceOptions.find { it.id == currentSource } ?: sourceOptions.first()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedOption.name,
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
        OutlinedTextField(
            value = selectedOption.name,
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
