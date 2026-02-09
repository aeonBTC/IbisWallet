package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.tor.TorState
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.components.SquareToggleGreen
import github.aeonbtc.ibiswallet.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectrumConfigScreen(
    onConnect: (ElectrumConfig) -> Unit,
    onBack: () -> Unit,
    currentConfig: ElectrumConfig? = null,
    isConnecting: Boolean = false,
    isConnected: Boolean = false,
    error: String? = null,
    // Multi-server support
    savedServers: List<ElectrumConfig> = emptyList(),
    activeServerId: String? = null,
    onSaveServer: (ElectrumConfig) -> ElectrumConfig = { it },
    onDeleteServer: (String) -> Unit = {},
    onConnectToServer: (String) -> Unit = {},
    // Tor support
    torState: TorState = TorState(),
    isTorEnabled: Boolean = false,
    onTorEnabledChange: (Boolean) -> Unit = {},
    // Disconnect support
    onDisconnect: () -> Unit = {},
    onCancelConnection: () -> Unit = {},
    // Server info
    serverVersion: String? = null,
    blockHeight: UInt? = null
) {
    var serverUrl by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("50001") }
    var serverName by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(false) }
    var showAddServerForm by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    
    // Edit mode state
    var serverToEdit by remember { mutableStateOf<ElectrumConfig?>(null) }
    val isEditMode = serverToEdit != null
    
    // Delete confirmation dialog state
    var serverToDelete by remember { mutableStateOf<ElectrumConfig?>(null) }
    
    // QR Scanner Dialog for server address
    if (showQrScanner) {
        QrScannerDialog(
            onCodeScanned = { code: String ->
                val parsed = github.aeonbtc.ibiswallet.util.QrFormatParser.parseServerQr(code)
                serverUrl = parsed.host
                if (parsed.port != null) {
                    serverPort = parsed.port.toString()
                }
                if (parsed.ssl != null) {
                    useSsl = parsed.ssl
                } else if (parsed.port != null) {
                    useSsl = parsed.port == 50002
                }
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }
    
    val isValidName = serverName.isNotBlank()
    val isValidUrl = serverUrl.isNotBlank()
    val portNumber = serverPort.toIntOrNull()
    val isValidPort = portNumber != null && portNumber in 1..65535
    
    // Delete confirmation dialog
    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = {
                Text(
                    text = "Delete Server",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${server.displayName()}\"?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        server.id?.let { onDeleteServer(it) }
                        serverToDelete = null
                    }
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
    
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
                text = "Electrum Server",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
            // 1. Add Server Button (at top, always visible)
            OutlinedButton(
                onClick = { if (!showAddServerForm) showAddServerForm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                enabled = !showAddServerForm,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BitcoinOrange,
                    disabledContentColor = BitcoinOrange.copy(alpha = 0.4f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (!showAddServerForm) BorderColor.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Server")
            }
            
            Spacer(modifier = Modifier.height(6.dp))

            // 2. Current Server / Connection Status Card (always shown)
            val activeServer = savedServers.find { it.id == activeServerId }
            CurrentServerCard(
                server = activeServer,
                isConnecting = isConnecting,
                isConnected = isConnected,
                serverVersion = serverVersion,
                blockHeight = blockHeight,
                onConnect = { activeServerId?.let { onConnectToServer(it) } },
                onDisconnect = onDisconnect,
                onCancelConnection = onCancelConnection,
                onEdit = activeServer?.let { server ->
                    {
                        serverToEdit = server
                        serverUrl = server.url
                        serverPort = server.port.toString()
                        serverName = server.name ?: ""
                        useSsl = server.useSsl
                        showAddServerForm = true
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // 3. Tor Network Card
            TorStatusCard(
                torState = torState,
                isTorEnabled = isTorEnabled,
                onTorEnabledChange = onTorEnabledChange
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 4. Saved Servers Section
            if (savedServers.isNotEmpty()) {
                var showSavedServers by remember { mutableStateOf(activeServerId == null) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Collapsible header
                        Surface(
                            onClick = { showSavedServers = !showSavedServers },
                            color = DarkCard,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = BitcoinOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Saved Servers",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showSavedServers)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showSavedServers) "Collapse" else "Expand",
                                    tint = TextSecondary
                                )
                            }
                        }

                        // Collapsible content
                        AnimatedVisibility(
                            visible = showSavedServers,
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

                                Spacer(modifier = Modifier.height(12.dp))

                                // List of saved servers
                                savedServers.forEach { server ->
                                    val isActive = server.id == activeServerId

                                    SavedServerItem(
                                        server = server,
                                        isActive = isActive,
                                        isConnected = isConnected && isActive,
                                        onConnect = {
                                            // Auto-enable Tor if connecting to onion server
                                            if (server.isOnionAddress() && !isTorEnabled) {
                                                onTorEnabledChange(true)
                                            }
                                            server.id?.let { onConnectToServer(it) }
                                        },
                                        onEdit = {
                                            // Populate form with server values
                                            serverToEdit = server
                                            serverUrl = server.url
                                            serverPort = server.port.toString()
                                            serverName = server.name ?: ""
                                            useSsl = server.useSsl
                                            showAddServerForm = true
                                        },
                                        onDelete = { serverToDelete = server }
                                    )

                                    if (server != savedServers.last()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Server configuration dialog
            if (showAddServerForm) {
                ServerConfigDialog(
                    isEditMode = isEditMode,
                    serverName = serverName,
                    serverUrl = serverUrl,
                    serverPort = serverPort,
                    useSsl = useSsl,
                    isValidName = isValidName,
                    isValidUrl = isValidUrl,
                    isValidPort = isValidPort,
                    onNameChange = { serverName = it },
                    onUrlChange = { serverUrl = it },
                    onPortChange = { serverPort = it },
                    onScanQr = { showQrScanner = true },
                    onSslChange = { newUseSsl ->
                        useSsl = newUseSsl
                        if (newUseSsl && serverPort == "50001") {
                            serverPort = "50002"
                        } else if (!newUseSsl && serverPort == "50002") {
                            serverPort = "50001"
                        }
                    },
                    onSave = {
                        // Cancel any in-progress connection before saving
                        if (isConnecting) {
                            onCancelConnection()
                        }
                        
                        val config = ElectrumConfig(
                            id = serverToEdit?.id,
                            url = serverUrl.trim(),
                            port = portNumber ?: 50001,
                            useSsl = useSsl,
                            name = serverName.trim(),
                            useTor = serverUrl.trim().endsWith(".onion")
                        )
                        val savedConfig = onSaveServer(config)
                        
                        if (config.isOnionAddress() && !isTorEnabled) {
                            onTorEnabledChange(true)
                        }
                        
                        savedConfig.id?.let { onConnectToServer(it) }
                        
                        serverUrl = ""
                        serverPort = "50001"
                        serverName = ""
                        useSsl = false
                        serverToEdit = null
                        showAddServerForm = false
                    },
                    onCancel = {
                        showAddServerForm = false
                        serverToEdit = null
                        serverUrl = ""
                        serverPort = "50001"
                        serverName = ""
                        useSsl = false
                    }
                )
            }
            
            // Error message
            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
        // Bottom spacing for scroll area
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Protocol badge (SSL / TCP)
 */
@Composable
private fun ProtocolBadge(useSsl: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (useSsl) SuccessGreen.copy(alpha = 0.15f) else TextSecondary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (useSsl) SuccessGreen.copy(alpha = 0.4f) else TextSecondary.copy(alpha = 0.3f))
    ) {
        Text(
            text = if (useSsl) "SSL" else "TCP",
            style = MaterialTheme.typography.labelSmall,
            color = if (useSsl) SuccessGreen else TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Tor badge
 */
@Composable
private fun TorBadge() {
    val purple = androidx.compose.ui.graphics.Color(0xFF9B59B6)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = purple.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, purple.copy(alpha = 0.4f))
    ) {
        Text(
            text = "Tor",
            style = MaterialTheme.typography.labelSmall,
            color = purple,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Labeled detail row for server info
 */
@Composable
private fun ServerDetailRow(label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Current Server Card - shows connection state and server info
 */
@Composable
private fun CurrentServerCard(
    server: ElectrumConfig?,
    isConnecting: Boolean,
    isConnected: Boolean,
    serverVersion: String? = null,
    blockHeight: UInt? = null,
    onConnect: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onCancelConnection: () -> Unit = {},
    onEdit: (() -> Unit)? = null
) {
    val statusColor = when {
        isConnecting -> BitcoinOrange
        isConnected -> SuccessGreen
        else -> TextSecondary
    }
    
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
            // Header with buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Current Server",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // Connect/Disconnect buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canConnect = server != null && !isConnected && !isConnecting
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = when {
                                    canConnect -> BorderColor
                                    isConnected -> SuccessGreen
                                    isConnecting -> BitcoinOrange
                                    else -> BorderColor
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                when {
                                    canConnect -> DarkBackground
                                    else -> statusColor.copy(alpha = 0.15f)
                                }
                            )
                            .then(
                                if (canConnect) Modifier.clickable(onClick = onConnect)
                                else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = BitcoinOrange,
                                    strokeWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = when {
                                    isConnecting -> "Connecting"
                                    isConnected -> "Connected"
                                    else -> "Connect"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    
                    // Disconnect/Cancel button
                    if (isConnected || isConnecting) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = 1.dp,
                                    color = ErrorRed,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .background(ErrorRed)
                                .clickable(
                                    onClick = if (isConnecting) onCancelConnection else onDisconnect
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isConnecting) "Cancel" else "Disconnect",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Server details or empty state
            if (server != null) {
                val serverBorderColor = when {
                    isConnected -> SuccessGreen
                    isConnecting -> BitcoinOrange
                    else -> BorderColor
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isConnected -> SuccessGreen.copy(alpha = 0.08f)
                                isConnecting -> BitcoinOrange.copy(alpha = 0.08f)
                                else -> DarkSurfaceVariant
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = serverBorderColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Name row with badges and edit button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Name:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(
                            text = server.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ProtocolBadge(useSsl = server.useSsl)
                        if (server.isOnionAddress()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            TorBadge()
                        }
                        if (onEdit != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit server",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(onClick = onEdit)
                            )
                        }
                    }
                    // Address
                    ServerDetailRow(
                        label = "Address:",
                        value = server.cleanUrl(),
                        monospace = true
                    )
                    // Port
                    ServerDetailRow(
                        label = "Port:",
                        value = server.port.toString(),
                        monospace = true
                    )
                    // Software (only when connected)
                    if (isConnected && serverVersion != null) {
                        ServerDetailRow(
                            label = "Software:",
                            value = serverVersion
                        )
                    }
                    // Block height (only when connected)
                    if (isConnected && blockHeight != null && blockHeight > 0u) {
                        ServerDetailRow(
                            label = "Block:",
                            value = "%,d".format(blockHeight.toInt())
                        )
                    }
                }
            } else {
                // No server selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add a server to connect",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Individual saved server item
 */
@Composable
private fun SavedServerItem(
    server: ElectrumConfig,
    isActive: Boolean,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = if (isActive) {
        BitcoinOrange.copy(alpha = 0.10f)
    } else {
        DarkSurfaceVariant
    }
    
    val borderColor = if (isActive) {
        BitcoinOrange
    } else {
        BorderColor
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Server info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Name row with status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isConnected) SuccessGreen else BitcoinOrange
                        ) {
                            Text(
                                text = if (isConnected) "Active" else "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkBackground,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                // Address + port + badges
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${server.cleanUrl()}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ProtocolBadge(useSsl = server.useSsl)
                    if (server.isOnionAddress()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TorBadge()
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // Edit button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurface)
                    .clickable { onEdit() }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Delete button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurface)
                    .clickable { onDelete() }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Card showing Tor status and controls
 */
@Composable
private fun TorStatusCard(
    torState: TorState,
    isTorEnabled: Boolean,
    onTorEnabledChange: (Boolean) -> Unit
) {
    val statusColor = when (torState.status) {
        TorStatus.CONNECTED -> SuccessGreen
        TorStatus.CONNECTING, TorStatus.STARTING -> BitcoinOrange
        TorStatus.ERROR -> ErrorRed
        TorStatus.DISCONNECTED -> TextSecondary
    }
    
    val statusText = when (torState.status) {
        TorStatus.CONNECTED -> "Connected"
        TorStatus.CONNECTING -> "Connecting..."
        TorStatus.STARTING -> "Starting..."
        TorStatus.ERROR -> "Error"
        TorStatus.DISCONNECTED -> "Disabled"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tor_icon),
                    contentDescription = null,
                    alpha = if (isTorEnabled) 1f else 0.5f,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Enable Tor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                }
            }
                
            SquareToggleGreen(
                checked = isTorEnabled,
                onCheckedChange = onTorEnabledChange
            )
        }
    }
}

/**
 * Server Configuration Dialog - popup for adding/editing servers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConfigDialog(
    isEditMode: Boolean,
    serverName: String,
    serverUrl: String,
    serverPort: String,
    useSsl: Boolean,
    isValidName: Boolean,
    isValidUrl: Boolean,
    isValidPort: Boolean,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onSslChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = DarkCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isEditMode) "Edit Server" else "Add Server",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = serverName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "My Server",
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
                
                Text(
                    text = "Server Address",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "electrum.example.com",
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onScanQr) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Code",
                                tint = BitcoinOrange,
                                modifier = Modifier.size(20.dp)
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
                
                Text(
                    text = "Port",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = onPortChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "50001",
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BitcoinOrange,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = BitcoinOrange
                    ),
                    isError = serverPort.isNotBlank() && !isValidPort
                )
                
                if (serverPort.isNotBlank() && !isValidPort) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Port must be between 1 and 65535",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Use SSL/TLS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Encrypt connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    SquareToggle(
                        checked = useSsl,
                        onCheckedChange = onSslChange
                    )
                }
            }
        },
        confirmButton = {
            val isEnabled = isValidName && isValidUrl && isValidPort
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isEnabled) BitcoinOrange else TextSecondary.copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    1.dp, 
                    if (isEnabled) BitcoinOrange.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.5f)
                ),
                enabled = isEnabled
            ) {
                Text(
                    text = if (isEditMode) "Update & Connect" else "Save & Connect"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
