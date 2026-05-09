@file:Suppress("AssignedValueIsNeverRead")

package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.model.ElectrumConfig
import github.aeonbtc.ibiswallet.tor.TorState
import github.aeonbtc.ibiswallet.ui.components.IbisConfirmDialog
import github.aeonbtc.ibiswallet.ui.components.ScrollableAlertDialog
import github.aeonbtc.ibiswallet.tor.TorStatus
import github.aeonbtc.ibiswallet.ui.components.QrScannerDialog
import github.aeonbtc.ibiswallet.ui.components.SquareToggle
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.LightningYellow
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.TorPurple
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectrumConfigScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    showHeader: Boolean = true,
    isConnecting: Boolean = false,
    isConnected: Boolean = false,
    error: String? = null,
    // Multi-server support
    savedServers: List<ElectrumConfig> = emptyList(),
    activeServerId: String? = null,
    onSaveServer: (ElectrumConfig) -> ElectrumConfig = { it },
    onDeleteServer: (String) -> Unit = {},
    onConnectToServer: (String) -> Unit = {},
    // Tor status
    torState: TorState = TorState(),
    isTorEnabled: Boolean = false,
    isActiveServerOnion: Boolean = false,
    // Disconnect support
    onDisconnect: () -> Unit = {},
    onCancelConnection: () -> Unit = {},
    // Auto-switch server on disconnect
    autoSwitchServer: Boolean = false,
    onAutoSwitchServerChange: (Boolean) -> Unit = {},
    // Reorder servers
    onReorderServers: (List<String>) -> Unit = {},
    // Server info
    serverVersion: String? = null,
    blockHeight: UInt? = null,
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
            onDismiss = { showQrScanner = false },
        )
    }

    val isValidName = serverName.isNotBlank()
    val isValidUrl = serverUrl.isNotBlank()
    val portNumber = serverPort.toIntOrNull()
    val isValidPort = portNumber != null && portNumber in 1..65535

    // Delete confirmation dialog
    serverToDelete?.let { server ->
        IbisConfirmDialog(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                serverToDelete = null
            },
            title = stringResource(R.string.loc_b94b1995),
            message = stringResource(R.string.server_delete_confirmation_format, server.displayName()),
            confirmText = stringResource(R.string.loc_3dbe79b1),
            confirmColor = ErrorRed,
            onConfirm = {
                server.id?.let { onDeleteServer(it) }
                serverToDelete = null
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showHeader) {
                // Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.loc_cdfc6e09),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.loc_aab4007b),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 1. Current Server / Connection Status Card (always shown)
            val activeServer = savedServers.find { it.id == activeServerId }
            CurrentServerCard(
                server = activeServer,
                isConnecting = isConnecting,
                isConnected = isConnected,
                error = error,
                serverVersion = serverVersion,
                blockHeight = blockHeight,
                torState = torState,
                isOnionServer = isActiveServerOnion,
                onConnect = { activeServerId?.let { onConnectToServer(it) } },
                onDisconnect = onDisconnect,
                onCancelConnection = onCancelConnection,
                onEdit =
                    activeServer?.let { server ->
                        {
                            serverToEdit = server
                            serverUrl = server.url
                            serverPort = server.port.toString()
                            serverName = server.name ?: ""
                            useSsl = server.useSsl
                            showAddServerForm = true
                        }
                    },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Tor Network Card
            TorStatusCard(
                torState = torState,
                isTorEnabled = isTorEnabled,
                isActiveServerOnion = isActiveServerOnion,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Saved Servers Section
            if (savedServers.isNotEmpty()) {
                val reorderableServers = remember(savedServers) { savedServers.toMutableStateList() }
                val showDragHandles = reorderableServers.size > 1
                val density = LocalDensity.current
                val itemHeightPx = with(density) { 56.dp.toPx() }
                val spacingPx = with(density) { 12.dp.toPx() }
                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                val measuredHeights = remember { mutableMapOf<Int, Float>() }

                fun calcTargetIndex(fromIndex: Int, offsetY: Float): Int {
                    val count = reorderableServers.size
                    if (count <= 1) return fromIndex
                    var accumulated = 0f
                    var target = fromIndex
                    if (offsetY > 0) {
                        for (i in fromIndex until count - 1) {
                            val nextSlot = (measuredHeights[i + 1] ?: itemHeightPx) / 2f +
                                (measuredHeights[i] ?: itemHeightPx) / 2f + spacingPx
                            accumulated += nextSlot
                            if (offsetY > accumulated - nextSlot / 2f) target = i + 1 else break
                        }
                    } else {
                        for (i in fromIndex downTo 1) {
                            val prevSlot = (measuredHeights[i - 1] ?: itemHeightPx) / 2f +
                                (measuredHeights[i] ?: itemHeightPx) / 2f + spacingPx
                            accumulated -= prevSlot
                            if (offsetY < accumulated + prevSlot / 2f) target = i - 1 else break
                        }
                    }
                    return target.coerceIn(0, count - 1)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = BitcoinOrange,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.loc_cc38cb35),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                        ) {
                            HorizontalDivider(color = BorderColor)

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (showDragHandles) {
                                            Modifier.pointerInput(reorderableServers.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        var accumulated = 0f
                                                        for (i in reorderableServers.indices) {
                                                            val h = measuredHeights[i] ?: itemHeightPx
                                                            if (offset.y < accumulated + h + spacingPx) {
                                                                draggedIndex = i
                                                                dragOffsetY = 0f
                                                                break
                                                            }
                                                            accumulated += h + spacingPx
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val idx = draggedIndex
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val maxUp = -(0 until idx).sumOf {
                                                            ((measuredHeights[it] ?: itemHeightPx) + spacingPx).toDouble()
                                                        }.toFloat()
                                                        val maxDown = (idx + 1 until reorderableServers.size).sumOf {
                                                            ((measuredHeights[it] ?: itemHeightPx) + spacingPx).toDouble()
                                                        }.toFloat()
                                                        dragOffsetY = (dragOffsetY + dragAmount.y)
                                                            .coerceIn(maxUp, maxDown)
                                                    },
                                                    onDragEnd = {
                                                        val idx = draggedIndex
                                                        if (idx != null) {
                                                            val target = calcTargetIndex(idx, dragOffsetY)
                                                            if (target != idx) {
                                                                reorderableServers.apply {
                                                                    add(target, removeAt(idx))
                                                                }
                                                                onReorderServers(
                                                                    reorderableServers.mapNotNull { it.id },
                                                                )
                                                            }
                                                        }
                                                        draggedIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    reorderableServers.forEachIndexed { index, server ->
                                        val isDragging = draggedIndex == index
                                        val targetIndex = draggedIndex?.let {
                                            calcTargetIndex(it, dragOffsetY)
                                        }
                                        val displacementPx = if (!isDragging && draggedIndex != null && targetIndex != null) {
                                            val dragIdx = draggedIndex!!
                                            val draggedH = measuredHeights[dragIdx] ?: itemHeightPx
                                            when (index) {
                                                in (dragIdx + 1)..targetIndex ->
                                                    -(draggedH + spacingPx)
                                                in targetIndex until dragIdx ->
                                                    draggedH + spacingPx
                                                else -> 0f
                                            }
                                        } else {
                                            0f
                                        }
                                        val isActive = server.id == activeServerId

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (isDragging) 1f else 0f)
                                                .offset {
                                                    IntOffset(
                                                        x = 0,
                                                        y = if (isDragging) {
                                                            dragOffsetY.roundToInt()
                                                        } else {
                                                            displacementPx.roundToInt()
                                                        },
                                                    )
                                                }
                                                .graphicsLayer {
                                                    if (isDragging) {
                                                        shadowElevation = 8f
                                                        alpha = 0.95f
                                                    }
                                                }
                                                .onGloballyPositioned { coords ->
                                                    measuredHeights[index] = coords.size.height.toFloat()
                                                },
                                        ) {
                                            SavedServerItem(
                                                server = server,
                                                isActive = isActive,
                                                isConnected = isConnected && isActive,
                                                onConnect = {
                                                    server.id?.let { onConnectToServer(it) }
                                                },
                                                onEdit = {
                                                    serverToEdit = server
                                                    serverUrl = server.url
                                                    serverPort = server.port.toString()
                                                    serverName = server.name ?: ""
                                                    useSsl = server.useSsl
                                                    showAddServerForm = true
                                                },
                                                onDelete = { serverToDelete = server },
                                                showDragHandle = showDragHandles,
                                                isDragging = isDragging,
                                            )
                                        }

                                        if (index < reorderableServers.size - 1) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }

                            // Auto-switch toggle
                            if (savedServers.size >= 2) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BorderColor)
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.loc_98016440),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        Text(
                                            text = stringResource(R.string.loc_e43bcbe8),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                        )
                                    }
                                    SquareToggle(
                                        checked = autoSwitchServer,
                                        onCheckedChange = onAutoSwitchServerChange,
                                    )
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

                        val config =
                            ElectrumConfig(
                                id = serverToEdit?.id,
                                url = serverUrl.trim(),
                                port = portNumber ?: 50001,
                                useSsl = useSsl,
                                name = serverName.trim(),
                                useTor = serverUrl.trim().endsWith(".onion"),
                            )
                        onSaveServer(config)

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
                    },
                )
            }

            // Bottom spacing for scroll area
            Spacer(modifier = Modifier.height(16.dp))
        }

        FloatingActionButton(
            onClick = { if (!showAddServerForm) showAddServerForm = true },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            shape = CircleShape,
            containerColor = if (showAddServerForm) BitcoinOrange.copy(alpha = 0.3f) else BitcoinOrange,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.loc_e46232fe),
            )
        }
    }
}

/**
 * Protocol badge (SSL / TCP)
 */
@Composable
private fun ProtocolBadge(useSsl: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (useSsl) LightningYellow.copy(alpha = 0.15f) else TextSecondary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (useSsl) LightningYellow.copy(alpha = 0.4f) else TextSecondary.copy(alpha = 0.3f)),
    ) {
        Text(
            text = if (useSsl) "SSL" else "TCP",
            style = MaterialTheme.typography.labelSmall,
            color = if (useSsl) LightningYellow else TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
        border = BorderStroke(1.dp, purple.copy(alpha = 0.4f)),
    ) {
        Text(
            text = stringResource(R.string.loc_c89a3806),
            style = MaterialTheme.typography.labelSmall,
            color = purple,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Labeled detail row for server info
 */
@Composable
private fun ServerDetailRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Current Server Card - shows connection state and server info
 */
@Composable
fun CurrentServerCard(
    server: ElectrumConfig?,
    isConnecting: Boolean,
    isConnected: Boolean,
    error: String? = null,
    serverVersion: String? = null,
    blockHeight: UInt? = null,
    torState: TorState = TorState(),
    isOnionServer: Boolean = false,
    headerTitle: String? = null,
    headerLeadingContent: (@Composable () -> Unit)? = null,
    headerTrailingContent: (@Composable () -> Unit)? = null,
    onConnect: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onCancelConnection: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onServerDetailsClick: (() -> Unit)? = null,
    onAddServer: (() -> Unit)? = null,
) {
    val isTorBootstrapping =
        isConnecting && isOnionServer &&
            torState.status != TorStatus.CONNECTED

    val statusColor =
        when {
            isTorBootstrapping -> TorPurple
            isConnecting -> BitcoinOrange
            isConnected -> SuccessGreen
            else -> TextSecondary
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // Header with buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (headerLeadingContent != null) {
                        headerLeadingContent()
                    } else {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = BitcoinOrange,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = headerTitle ?: stringResource(R.string.loc_8d4cd2b4),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (headerTrailingContent != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        headerTrailingContent()
                    }
                }

                // Connect/Disconnect buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val canConnect = server != null && !isConnected && !isConnecting
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color =
                                        when {
                                            canConnect -> BorderColor
                                            else -> statusColor
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .background(
                                    when {
                                        canConnect -> DarkBackground
                                        else -> statusColor.copy(alpha = 0.15f)
                                    },
                                )
                                .then(
                                    if (canConnect) {
                                        Modifier.clickable(onClick = onConnect)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = statusColor,
                                    strokeWidth = 1.5.dp,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text =
                                    when {
                                        isTorBootstrapping -> stringResource(R.string.loc_268af6fe)
                                        isConnecting -> stringResource(R.string.loc_066df953)
                                        isConnected -> stringResource(R.string.loc_98469a16)
                                        else -> stringResource(R.string.loc_bb72c083)
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                            )
                        }
                    }

                    // Disconnect/Cancel button
                    if (isConnected || isConnecting) {
                        Box(
                            modifier =
                                Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = 1.dp,
                                        color = ErrorRed,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .background(ErrorRed)
                                    .clickable(
                                        onClick = if (isConnecting) onCancelConnection else onDisconnect,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription =
                                    if (isConnecting) {
                                        stringResource(R.string.loc_51bac044)
                                    } else {
                                        stringResource(R.string.loc_4f674841)
                                    },
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Server details or empty state
            if (server != null) {
                val serverBorderColor =
                    when {
                        isConnected -> SuccessGreen
                        isConnecting -> BitcoinOrange
                        else -> BorderColor
                    }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isConnected -> SuccessGreen.copy(alpha = 0.08f)
                                    isConnecting -> BitcoinOrange.copy(alpha = 0.08f)
                                    else -> DarkSurfaceVariant
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = serverBorderColor,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .then(
                                if (onServerDetailsClick != null) {
                                    Modifier.clickable(onClick = onServerDetailsClick)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Name row with badges and edit button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_3cf29f70),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.width(72.dp),
                        )
                        Text(
                            text = server.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
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
                                contentDescription = stringResource(R.string.loc_e85c4517),
                                tint = TextSecondary,
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .clickable(onClick = onEdit),
                            )
                        }
                    }
                    // Address
                    ServerDetailRow(
                        label = stringResource(R.string.loc_77caa9b0),
                        value = server.cleanUrl(),
                        monospace = true,
                    )
                    // Port
                    ServerDetailRow(
                        label = stringResource(R.string.loc_475e06fd),
                        value = server.port.toString(),
                        monospace = true,
                    )
                    // Software (only when connected)
                    if (isConnected && serverVersion != null) {
                        ServerDetailRow(
                            label = stringResource(R.string.loc_9d53bbd5),
                            value = serverVersion,
                        )
                    }
                    // Block height (only when connected)
                    if (isConnected && blockHeight != null && blockHeight > 0u) {
                        ServerDetailRow(
                            label = stringResource(R.string.loc_c641697a),
                            value = "%,d".format(blockHeight.toInt()),
                        )
                    }
                }

                // Error message inside server card
                if (error != null && !isConnecting && !isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.loc_11c5bc85),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            } else {
                // No server — show add button
                OutlinedButton(
                    onClick = { onAddServer?.invoke() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.loc_aad3c3b2))
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
    onDelete: () -> Unit,
    showDragHandle: Boolean = false,
    isDragging: Boolean = false,
) {
    val cardColor =
        if (isActive) {
            BitcoinOrange.copy(alpha = 0.10f)
        } else {
            DarkSurfaceVariant
        }

    val borderColor =
        if (isDragging) {
            BitcoinOrange.copy(alpha = 0.6f)
        } else if (isActive) {
            BitcoinOrange
        } else {
            BorderColor
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onConnect() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.loc_00ad5135),
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = server.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isConnected) SuccessGreen else BitcoinOrange,
                        ) {
                            Text(
                                text = if (isConnected) stringResource(R.string.loc_4cb2f934) else stringResource(R.string.common_selected),
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkBackground,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${server.cleanUrl()}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurface)
                        .clickable { onEdit() },
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.loc_21077124),
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurface)
                        .clickable { onDelete() },
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.loc_3dbe79b1),
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun TorStatusCard(
    torState: TorState,
    isTorEnabled: Boolean,
    isActiveServerOnion: Boolean = false,
) {
    val isTorActive = isTorEnabled || isActiveServerOnion
    val statusColor =
        if (isTorActive) {
            when (torState.status) {
                TorStatus.CONNECTED -> TorPurple
                TorStatus.CONNECTING, TorStatus.STARTING -> TorPurple
                TorStatus.ERROR -> ErrorRed
                TorStatus.DISCONNECTED, TorStatus.STOPPING -> TextSecondary
            }
        } else {
            TextSecondary
        }

    val statusText =
        if (isTorActive) {
            when (torState.status) {
                TorStatus.CONNECTED -> stringResource(R.string.loc_4cb2f934)
                TorStatus.CONNECTING -> stringResource(R.string.loc_10c5a9ae)
                TorStatus.STARTING -> stringResource(R.string.loc_10c5a9ae)
                TorStatus.ERROR -> stringResource(R.string.loc_9c1c9375)
                TorStatus.DISCONNECTED, TorStatus.STOPPING -> stringResource(R.string.loc_51851e0f)
            }
        } else {
            stringResource(R.string.loc_51851e0f)
        }

    val accentColor = if (isTorActive) TorPurple else TextSecondary.copy(alpha = 0.4f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .align(Alignment.CenterVertically)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(accentColor),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tor_icon),
                    contentDescription = null,
                    alpha = if (isTorActive) 1f else 0.4f,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.loc_c89a3806),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.loc_d2f190c7),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = statusColor.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                }
            }
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
    onCancel: () -> Unit,
) {
    ScrollableAlertDialog(
        onDismissRequest = onCancel,
        containerColor = DarkCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = null,
                    tint = BitcoinOrange,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isEditMode) stringResource(R.string.loc_eff04021) else stringResource(R.string.loc_e46232fe),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.loc_fe11d138),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )

                OutlinedTextField(
                    value = serverName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.loc_597532ba),
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange,
                        ),
                )

                Text(
                    text = stringResource(R.string.loc_0899b645),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.loc_0db390f1),
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onScanQr) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.loc_59b2cdc5),
                                tint = BitcoinOrange,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange,
                        ),
                )

                Text(
                    text = stringResource(R.string.loc_e3a3f2f2),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = onPortChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.loc_b1b16491),
                            color = TextSecondary.copy(alpha = 0.5f),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BitcoinOrange,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = BitcoinOrange,
                        ),
                    isError = serverPort.isNotBlank() && !isValidPort,
                )

                if (serverPort.isNotBlank() && !isValidPort) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.loc_d83b7198),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSslChange(!useSsl) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.loc_a8fc5dd4),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.loc_209e3dd7),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    SquareToggle(
                        checked = useSsl,
                        onCheckedChange = onSslChange,
                    )
                }
            }
        },
        confirmButton = {
            val isEnabled = isValidName && isValidUrl && isValidPort
            Button(
                onClick = onSave,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = isEnabled,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = BitcoinOrange,
                        disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                    ),
            ) {
                Text(
                    text = if (isEditMode) stringResource(R.string.loc_9f89304e) else stringResource(R.string.loc_f55495e0),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.loc_51bac044), color = TextSecondary)
                }
            }
        },
    )
}
