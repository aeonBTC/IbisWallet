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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
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
    onCancelConnection: () -> Unit = {}
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
                // Parse address:port format
                val parsed = parseServerAddress(code)
                serverUrl = parsed.first
                if (parsed.second != null) {
                    serverPort = parsed.second.toString()
                    // Auto-detect SSL based on port
                    useSsl = parsed.second == 50002
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
                onClick = { showAddServerForm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !showAddServerForm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (showAddServerForm) TextSecondary.copy(alpha = 0.5f) else BitcoinOrange
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = !showAddServerForm).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (showAddServerForm) BorderColor.copy(alpha = 0.5f) else BitcoinOrange.copy(alpha = 0.5f)
                    )
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Server")
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // 2. Current Server / Connection Status Card (always shown)
            val activeServer = savedServers.find { it.id == activeServerId }
            CurrentServerCard(
                serverName = activeServer?.displayName(),
                serverAddress = activeServer?.let { "${it.url}:${it.port}" },
                isConnecting = isConnecting,
                isConnected = isConnected,
                useSsl = activeServer?.useSsl ?: false,
                isOnion = activeServer?.isOnionAddress() ?: false,
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
                var showSavedServers by remember { mutableStateOf(false) }

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
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = BitcoinOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
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
 * Current Server Card - shows connection state and server info
 */
@Composable
private fun CurrentServerCard(
    serverName: String?,
    serverAddress: String?,
    isConnecting: Boolean,
    isConnected: Boolean,
    useSsl: Boolean,
    isOnion: Boolean,
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
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = BitcoinOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
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
                    // Connect button - status indicator that can reconnect
                    val canConnect = serverName != null && !isConnected && !isConnecting
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
                    
                    // Disconnect/Cancel button - prominent X
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Server details or empty state
            if (serverName != null && serverAddress != null) {
                val serverBorderColor = when {
                    isConnected -> SuccessGreen
                    isConnecting -> BitcoinOrange
                    else -> BorderColor
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .then(
                            Modifier.border(
                                width = 1.5.dp,
                                color = serverBorderColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = serverAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (useSsl) "SSL" else "TCP",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (isOnion) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Image(
                                    painter = painterResource(id = R.drawable.tor_icon),
                                    contentDescription = "Onion",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit server",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // No server selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
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
 * Individual saved server item - styled like ManageWalletsScreen
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
    val isSelected = isActive
    
    val cardColor = if (isSelected) {
        BitcoinOrange.copy(alpha = 0.15f)
    } else {
        DarkSurfaceVariant
    }
    
    val borderColor = if (isSelected) {
        BitcoinOrange
    } else {
        BorderColor
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Server info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val badgeText = when {
                            isConnected -> "Active"
                            else -> "Selected"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BitcoinOrange
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkBackground,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${server.url}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (server.useSsl) "SSL" else "TCP",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (server.isOnionAddress()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Image(
                            painter = painterResource(id = R.drawable.tor_icon),
                            contentDescription = "Onion",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            // Edit button
            IconButton(
                onClick = onEdit
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Delete button
            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            text = "Encrypt connection (port 50002)",
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

/**
 * Parse server address from QR code
 * Supports formats: address:port, address (defaults to null port)
 */
private fun parseServerAddress(input: String): Pair<String, Int?> {
    val trimmed = input.trim()
    
    // Check for address:port format
    val lastColonIndex = trimmed.lastIndexOf(':')
    
    if (lastColonIndex > 0) {
        val potentialPort = trimmed.substring(lastColonIndex + 1)
        val port = potentialPort.toIntOrNull()
        
        // Valid port found
        if (port != null && port in 1..65535) {
            val address = trimmed.substring(0, lastColonIndex)
            return Pair(address, port)
        }
    }
    
    // No valid port found, return address only
    return Pair(trimmed, null)
}
