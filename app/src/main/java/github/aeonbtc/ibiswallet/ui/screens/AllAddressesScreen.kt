package github.aeonbtc.ibiswallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.data.model.KeychainType
import github.aeonbtc.ibiswallet.data.model.WalletAddress
import github.aeonbtc.ibiswallet.ui.theme.AccentGreen
import github.aeonbtc.ibiswallet.ui.theme.AccentTeal
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

private const val USED_ADDRESS_LIMIT = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAddressesScreen(
    receiveAddresses: List<WalletAddress>,
    changeAddresses: List<WalletAddress>,
    usedAddresses: List<WalletAddress>,
    denomination: String = SecureStorage.DENOMINATION_BTC,
    privacyMode: Boolean = false,
    onGenerateReceiveAddress: suspend () -> String? = { null }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAllUsed by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val tabs = listOf("Receive", "Change", "Used")
    val useSats = denomination == SecureStorage.DENOMINATION_SATS
    
    val receiveListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var scrollToAddress by remember { mutableStateOf<String?>(null) }
    var showQrForAddress by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    
    // Generate QR code when showing
    LaunchedEffect(showQrForAddress) {
        showQrForAddress?.let { address ->
            qrBitmap = generateQrCode(address)
        }
    }
    
    // QR Code Dialog
    if (showQrForAddress != null && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { 
                showQrForAddress = null
                qrBitmap = null
            },
            confirmButton = {
                TextButton(onClick = { 
                    showQrForAddress = null
                    qrBitmap = null
                }) {
                    Text("Close", color = BitcoinOrange)
                }
            },
            title = {
                Text(
                    text = "Address QR Code",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(250.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = showQrForAddress ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )
                }
            },
            containerColor = DarkCard
        )
    }
    
    // Scroll to newly generated address when list updates
    LaunchedEffect(receiveAddresses, scrollToAddress) {
        scrollToAddress?.let { targetAddress ->
            val index = receiveAddresses.indexOfFirst { it.address == targetAddress }
            if (index >= 0) {
                receiveListState.animateScrollToItem(index)
                scrollToAddress = null
            }
        }
    }
    
    // Filter used addresses by search query
    val filteredUsedAddresses = remember(usedAddresses, searchQuery) {
        if (searchQuery.isBlank()) {
            usedAddresses
        } else {
            val query = searchQuery.lowercase()
            usedAddresses.filter { addr ->
                addr.address.lowercase().contains(query) ||
                addr.label?.lowercase()?.contains(query) == true
            }
        }
    }
    
    // Apply limit on Used tab (only when not searching)
    val displayedUsedAddresses = remember(filteredUsedAddresses, showAllUsed, searchQuery) {
        if (searchQuery.isNotBlank() || showAllUsed) {
            filteredUsedAddresses
        } else {
            filteredUsedAddresses.take(USED_ADDRESS_LIMIT)
        }
    }
    
    val hasMoreUsedAddresses = searchQuery.isBlank() && 
        !showAllUsed && 
        filteredUsedAddresses.size > USED_ADDRESS_LIMIT
    
    val currentAddresses = when (selectedTab) {
        0 -> receiveAddresses
        1 -> changeAddresses
        2 -> displayedUsedAddresses
        else -> receiveAddresses
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        // Tab selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { 
                        selectedTab = index
                        // Reset show all when switching away from Used tab
                        if (index != 2) {
                            showAllUsed = false
                            searchQuery = ""
                        }
                    },
                    label = { 
                        Text(
                            text = title,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BitcoinOrange,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Search field for Used tab
        if (selectedTab == 2) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search address or label") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = DarkCard,
                    focusedContainerColor = DarkCard,
                    unfocusedBorderColor = DarkCard,
                    focusedBorderColor = BitcoinOrange
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        if (currentAddresses.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedTab == 2 && searchQuery.isNotBlank()) 
                        "No matching addresses" 
                    else 
                        "No addresses",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = if (selectedTab == 0) receiveListState else rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentAddresses) { address ->
                    AddressCard(
                        address = address,
                        useSats = useSats,
                        privacyMode = privacyMode,
                        isUsedTab = selectedTab == 2,
                        onShowQr = { showQrForAddress = address.address },
                        onCopy = { /* Handled inside AddressCard */ }
                    )
                }
                
                // Show All button for Used tab
                if (selectedTab == 2 && hasMoreUsedAddresses) {
                    item {
                        TextButton(
                            onClick = { showAllUsed = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Show All (${filteredUsedAddresses.size} total)",
                                color = BitcoinOrange
                            )
                        }
                    }
                }
                
                // Generate button on Receive tab only (inside scroll)
                if (selectedTab == 0) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val newAddress = onGenerateReceiveAddress()
                                    scrollToAddress = newAddress
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BitcoinOrange
                            ),
                            border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.5f))
                        ) {
                            Text("Generate New Address")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressCard(
    address: WalletAddress,
    useSats: Boolean,
    privacyMode: Boolean = false,
    isUsedTab: Boolean = false,
    onShowQr: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var showCopied by remember(address.address) { mutableStateOf(false) }
    
    // Auto-dismiss copy notification after 3 seconds
    LaunchedEffect(showCopied, address.address) {
        if (showCopied) {
            kotlinx.coroutines.delay(3000)
            showCopied = false
        }
    }
    
    val typeName = if (address.keychain == KeychainType.EXTERNAL) "Receive" else "Change"
    val hasBalance = address.balanceSats > 0UL
    
    // Determine card background color
    val cardColor = if (isUsedTab) {
        if (hasBalance) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
    } else {
        DarkCard
    }
    
    // Determine border color for used tab
    val borderColor = if (isUsedTab) {
        if (hasBalance) SuccessGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.5f)
    } else {
        null
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderColor?.let { BorderStroke(1.dp, it) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with index
            Text(
                text = "$typeName #${address.index + 1u}",
                style = MaterialTheme.typography.labelMedium,
                color = BitcoinOrange
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Address with copy and QR buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = address.address,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = onShowQr,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Show QR",
                        tint = TextSecondary
                    )
                }
                
                IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Address", address.address)
                        clipboardManager.setPrimaryClip(clip)
                        showCopied = true
                        onCopy()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (showCopied) BitcoinOrange else TextSecondary
                    )
                }
            }
            
            if (showCopied) {
                Text(
                    text = "Copied to clipboard!",
                    style = MaterialTheme.typography.bodySmall,
                    color = BitcoinOrange
                )
            }
            
            // Label (if exists)
            if (!address.label.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = address.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentTeal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Balance and transaction count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = if (privacyMode) HIDDEN_AMOUNT else formatAmount(address.balanceSats, useSats),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Transactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = address.transactionCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

private const val HIDDEN_AMOUNT = "****"

private fun formatAmount(sats: ULong, useSats: Boolean): String {
    return if (useSats) {
        "${NumberFormat.getNumberInstance(Locale.US).format(sats.toLong())} sats"
    } else {
        val btc = sats.toDouble() / 100_000_000.0
        "${String.format(Locale.US, "%.8f", btc)} BTC"
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.MARGIN, 1)
        }
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
