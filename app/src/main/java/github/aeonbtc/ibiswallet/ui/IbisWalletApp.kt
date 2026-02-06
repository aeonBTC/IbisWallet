package github.aeonbtc.ibiswallet.ui

import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.navigation.Screen
import github.aeonbtc.ibiswallet.navigation.bottomNavItems
import github.aeonbtc.ibiswallet.ui.components.DrawerContent
import github.aeonbtc.ibiswallet.ui.components.DrawerItem
import github.aeonbtc.ibiswallet.ui.screens.AboutScreen
import github.aeonbtc.ibiswallet.ui.screens.AllAddressesScreen
import github.aeonbtc.ibiswallet.ui.screens.AllUtxosScreen
import github.aeonbtc.ibiswallet.ui.screens.BalanceScreen
import github.aeonbtc.ibiswallet.ui.screens.ElectrumConfigScreen
import github.aeonbtc.ibiswallet.ui.screens.ImportWalletScreen
import github.aeonbtc.ibiswallet.ui.screens.KeyMaterialInfo
import github.aeonbtc.ibiswallet.ui.screens.ManageWalletsScreen
import github.aeonbtc.ibiswallet.ui.screens.ReceiveScreen
import github.aeonbtc.ibiswallet.ui.screens.SecurityScreen
import github.aeonbtc.ibiswallet.ui.screens.SendScreen
import github.aeonbtc.ibiswallet.ui.screens.SettingsScreen
import github.aeonbtc.ibiswallet.ui.screens.WalletInfo
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.viewmodel.WalletEvent
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbisWalletApp(
    viewModel: WalletViewModel = viewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    val walletState by viewModel.walletState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val serversState by viewModel.serversState.collectAsState()
    val torState by viewModel.torState.collectAsState()
    val denomination by viewModel.denominationState.collectAsState()
    val mempoolServer by viewModel.mempoolServerState.collectAsState()
    val feeSource by viewModel.feeSourceState.collectAsState()
    val feeEstimationState by viewModel.feeEstimationState.collectAsState()
    val minFeeRate by viewModel.minFeeRate.collectAsState()
    val preSelectedUtxo by viewModel.preSelectedUtxo.collectAsState()
    val priceSource by viewModel.priceSourceState.collectAsState()
    val btcPrice by viewModel.btcPriceState.collectAsState()
    val syncingWalletId by viewModel.syncingWalletId.collectAsState()
    val sendScreenDraft by viewModel.sendScreenDraft.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    
    // Global labels version counter - bumped when labels may have changed
    // (wallet imported with backup labels, wallet switched, sync completed)
    var labelsVersion by remember { mutableIntStateOf(0) }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Check if we're on a main screen (with bottom nav)
    val isMainScreen = currentDestination?.route in listOf(
        Screen.Receive.route,
        Screen.Balance.route,
        Screen.Send.route
    )
    
    // Check if we're on a sub-screen that should show back button in main TopAppBar
    val isSubScreenWithTopBar = currentDestination?.route in listOf(
        Screen.AllAddresses.route,
        Screen.AllUtxos.route
    )
    
    // Get title for sub-screens
    val subScreenTitle = when (currentDestination?.route) {
        Screen.AllAddresses.route -> "Addresses"
        Screen.AllUtxos.route -> "UTXOs"
        else -> ""
    }
    
    // Connection status dialog state
    var showConnectionStatusDialog by remember { mutableStateOf(false) }
    val electrumConfig = viewModel.getElectrumConfig()
    
    // Connection status dialog
    if (showConnectionStatusDialog) {
        ConnectionStatusDialog(
            isConnected = uiState.isConnected,
            isConnecting = uiState.isConnecting,
            serverName = electrumConfig?.name,
            serverUrl = electrumConfig?.url,
            serverPort = electrumConfig?.port,
            useSsl = electrumConfig?.useSsl ?: false,
            isOnion = electrumConfig?.isOnionAddress() ?: false,
            lastSyncTimestamp = walletState.lastSyncTimestamp,
            onDismiss = { showConnectionStatusDialog = false },
            onConnect = {
                serversState.activeServerId?.let { viewModel.connectToServer(it) }
                showConnectionStatusDialog = false
            },
            onDisconnect = {
                viewModel.disconnect()
                showConnectionStatusDialog = false
            },
            onConfigureServer = {
                showConnectionStatusDialog = false
                navController.navigate(Screen.ElectrumConfig.route)
            }
        )
    }
    
    // Build wallet list for ManageWallets screen from all wallets in state
    val activeWalletId = walletState.activeWallet?.id
    val wallets = walletState.wallets.map { storedWallet ->
        WalletInfo(
            id = storedWallet.id,
            name = storedWallet.name,
            type = storedWallet.addressType.name.lowercase(),
            typeDescription = storedWallet.addressType.displayName,
            derivationPath = storedWallet.derivationPath,
            isActive = storedWallet.id == activeWalletId,
            isWatchOnly = storedWallet.isWatchOnly,
            lastFullSyncTime = viewModel.getLastFullSyncTime(storedWallet.id)
        )
    }
    
    // Get string resources for use in event handling
    val walletAddedMessage = stringResource(R.string.wallet_added)

    // Handle events - show notifications and navigate as needed
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletEvent.Error -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
                is WalletEvent.WalletImported -> {
                    labelsVersion++
                    Toast.makeText(context, walletAddedMessage, Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Balance.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
                is WalletEvent.TransactionSent -> {
                    Toast.makeText(context, "Transaction sent successfully", Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.WalletExported -> {
                    Toast.makeText(context, "Wallet \"${event.walletName}\" exported", Toast.LENGTH_SHORT).show()
                }
                is WalletEvent.SyncCompleted,
                is WalletEvent.WalletSwitched,
                is WalletEvent.LabelsRestored -> {
                    // Refresh labels - labels may have been restored from backup,
                    // changed due to wallet switch, or updated after sync
                    labelsVersion++
                }
                // Other success events - no notification needed, UI updates reflect the change
                is WalletEvent.Connected,
                is WalletEvent.FeeBumped,
                is WalletEvent.CpfpCreated,
                is WalletEvent.WalletDeleted,
                is WalletEvent.ServerDeleted,
                is WalletEvent.AddressGenerated -> { }
            }
        }
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onItemClick = { item ->
                    scope.launch {
                        drawerState.close()
                    }
                    when (item) {
                        DrawerItem.ManageWallets -> {
                            navController.navigate(Screen.ManageWallets.route)
                        }
                        DrawerItem.ElectrumServer -> {
                            navController.navigate(Screen.ElectrumConfig.route)
                        }
                        DrawerItem.Settings -> {
                            navController.navigate(Screen.Settings.route)
                        }
                        DrawerItem.Security -> {
                            navController.navigate(Screen.Security.route)
                        }
                        DrawerItem.About -> {
                            navController.navigate(Screen.About.route)
                        }
                    }
                }
            )
        },
        gesturesEnabled = isMainScreen
    ) {
        Scaffold(
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isMainScreen || isSubScreenWithTopBar) {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (isSubScreenWithTopBar) subScreenTitle 
                                       else walletState.activeWallet?.name ?: "Ibis Wallet",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        navigationIcon = {
                            if (isSubScreenWithTopBar) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        },
                        actions = {
                            if (isMainScreen) {
                                // Connection status indicator only on main screens
                                ConnectionStatusIndicator(
                                    isConnected = uiState.isConnected,
                                    isConnecting = uiState.isConnecting,
                                    onClick = { showConnectionStatusDialog = true }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkBackground,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { 
                            it.route == item.screen.route 
                        } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            selected = selected,
                            onClick = {
                                // Only navigate if not already on this screen
                                if (currentDestination?.route != item.screen.route) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            inclusive = false
                                        }
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BitcoinOrange,
                                selectedTextColor = BitcoinOrange,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Balance.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) }
            ) {
                composable(Screen.Receive.route) {
                    // Fetch price when entering Receive screen
                    LaunchedEffect(Unit) {
                        viewModel.fetchBtcPrice()
                    }
                    
                    ReceiveScreen(
                        walletState = walletState,
                        denomination = denomination,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        onGenerateAddress = { viewModel.getNewAddress() },
                        onSaveLabel = { address, label -> viewModel.saveAddressLabel(address, label) },
                        onShowAllAddresses = { navController.navigate(Screen.AllAddresses.route) },
                        onShowAllUtxos = { navController.navigate(Screen.AllUtxos.route) }
                    )
                }
                composable(Screen.Balance.route) {
                    // Fetch price when entering Balance screen
                    LaunchedEffect(Unit) {
                        viewModel.fetchBtcPrice()
                    }
                    
                    // Labels are refreshed when labelsVersion changes (global counter
                    // bumped on wallet import, wallet switch, sync, or manual label save)
                    val transactionLabels = remember(labelsVersion) { viewModel.getAllTransactionLabels() }
                    val addressLabels = remember(labelsVersion) { viewModel.getAllAddressLabels() }
                    
                    BalanceScreen(
                        walletState = walletState,
                        uiState = uiState,
                        denomination = denomination,
                        mempoolUrl = viewModel.getMempoolUrl(),
                        mempoolServer = viewModel.getMempoolServer(),
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        onTogglePrivacy = { viewModel.togglePrivacyMode() },
                        addressLabels = addressLabels,
                        transactionLabels = transactionLabels,
                        feeEstimationState = feeEstimationState,
                        canBumpFee = { txid -> viewModel.canBumpFee(txid) },
                        canCpfp = { txid -> viewModel.canCpfp(txid) },
                        onBumpFee = { txid, feeRate -> viewModel.bumpFee(txid, feeRate) },
                        onCpfp = { txid, feeRate -> viewModel.cpfp(txid, feeRate) },
                        onSaveTransactionLabel = { txid, label -> 
                            viewModel.saveTransactionLabel(txid, label)
                            labelsVersion++
                        },
                        onFetchTxVsize = { txid -> viewModel.fetchTransactionVsize(txid) },
                        onRefreshFees = { viewModel.fetchFeeEstimates() },
                        onSync = { viewModel.sync() },
                        onImportWallet = { navController.navigate(Screen.ImportWallet.route) }
                    )
                }
                composable(Screen.Send.route) {
                    val utxos = viewModel.getAllUtxos()
                    
                    // Collect preSelectedUtxo directly here to ensure fresh value
                    val currentPreSelectedUtxo by viewModel.preSelectedUtxo.collectAsState()
                    
                    // Get all wallet addresses for self-transfer detection
                    val allWalletAddresses = remember(walletState) {
                        val (receive, change, used) = viewModel.getAllAddresses()
                        (receive.map { it.address } + change.map { it.address } + used.map { it.address }).toSet()
                    }
                    
                    // Fetch fee estimates and price when entering Send screen
                    LaunchedEffect(Unit) {
                        viewModel.fetchFeeEstimates()
                        viewModel.fetchBtcPrice()
                    }
                    
                    SendScreen(
                        walletState = walletState,
                        uiState = uiState,
                        denomination = denomination,
                        utxos = utxos,
                        walletAddresses = allWalletAddresses,
                        feeEstimationState = feeEstimationState,
                        minFeeRate = minFeeRate,
                        preSelectedUtxo = currentPreSelectedUtxo,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        draft = sendScreenDraft,
                        onRefreshFees = { viewModel.fetchFeeEstimates() },
                        onClearPreSelectedUtxo = { viewModel.clearPreSelectedUtxo() },
                        onUpdateDraft = { draft -> viewModel.updateSendScreenDraft(draft) },
                        onSend = { address, amount, feeRate, selectedUtxos, label, isMaxSend ->
                            viewModel.sendBitcoin(address, amount, feeRate, selectedUtxos, label, isMaxSend)
                        }
                    )
                }
                composable(Screen.ManageWallets.route) {
                    ManageWalletsScreen(
                        wallets = wallets,
                        onBack = { navController.popBackStack() },
                        onImportWallet = { navController.navigate(Screen.ImportWallet.route) },
                        onViewWallet = { wallet ->
                            // Get key material from ViewModel
                            viewModel.getKeyMaterial(wallet.id)?.let { keyMaterial ->
                                KeyMaterialInfo(
                                    walletName = wallet.name,
                                    mnemonic = keyMaterial.mnemonic,
                                    extendedPublicKey = keyMaterial.extendedPublicKey,
                                    isWatchOnly = keyMaterial.isWatchOnly
                                )
                            }
                        },
                        onDeleteWallet = { wallet ->
                            viewModel.deleteWallet(wallet.id)
                        },
                        onSelectWallet = { wallet ->
                            viewModel.switchWallet(wallet.id)
                        },
                        onExportWallet = { walletId, uri, includeLabels, password ->
                            viewModel.exportWallet(walletId, uri, includeLabels, password)
                        },
                        onFullSync = { wallet ->
                            viewModel.fullSync(wallet.id)
                        },
                        syncingWalletId = syncingWalletId
                    )
                }
                composable(Screen.ImportWallet.route) {
                    ImportWalletScreen(
                        onImport = { config ->
                            viewModel.importWallet(config)
                        },
                        onImportFromBackup = { backupJson ->
                            viewModel.importFromBackup(backupJson)
                        },
                        onParseBackupFile = { uri, password ->
                            viewModel.parseBackupFile(uri, password)
                        },
                        onBack = { navController.popBackStack() },
                        isLoading = uiState.isLoading,
                        error = uiState.error
                    )
                }
                composable(Screen.ElectrumConfig.route) {
                    ElectrumConfigScreen(
                        onConnect = { config ->
                            viewModel.connectToElectrum(config)
                        },
                        onBack = { navController.popBackStack() },
                        currentConfig = viewModel.getElectrumConfig(),
                        isConnecting = uiState.isConnecting,
                        isConnected = uiState.isConnected,
                        error = uiState.error,
                        savedServers = serversState.servers,
                        activeServerId = serversState.activeServerId,
                        onSaveServer = { config -> viewModel.saveServer(config) },
                        onDeleteServer = { serverId -> viewModel.deleteServer(serverId) },
                        onConnectToServer = { serverId -> viewModel.connectToServer(serverId) },
                        onDisconnect = { viewModel.disconnect() },
                        onCancelConnection = { viewModel.cancelConnection() },
                        torState = torState,
                        isTorEnabled = viewModel.isTorEnabled(),
                        onTorEnabledChange = { enabled -> viewModel.setTorEnabled(enabled) }
                    )
                }
                composable(Screen.Settings.route) {
                    var customMempoolUrl by remember { mutableStateOf(viewModel.getCustomMempoolUrl()) }
                    
                    SettingsScreen(
                        currentDenomination = denomination,
                        onDenominationChange = { newDenomination ->
                            viewModel.setDenomination(newDenomination)
                        },
                        currentFeeSource = feeSource,
                        onFeeSourceChange = { newSource ->
                            viewModel.setFeeSource(newSource)
                        },
                        currentPriceSource = priceSource,
                        onPriceSourceChange = { newSource ->
                            viewModel.setPriceSource(newSource)
                        },
                        currentMempoolServer = mempoolServer,
                        onMempoolServerChange = { newServer ->
                            viewModel.setMempoolServer(newServer)
                        },
                        customMempoolUrl = customMempoolUrl,
                        onCustomMempoolUrlChange = { newUrl ->
                            customMempoolUrl = newUrl
                            viewModel.setCustomMempoolUrl(newUrl)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Security.route) {
                    // Use mutableState so UI recomposes when settings change
                    var securityMethod by remember { mutableStateOf(viewModel.getSecurityMethod()) }
                    var lockTiming by remember { mutableStateOf(viewModel.getLockTiming()) }
                    
                    // Check if device has biometric hardware
                    val biometricManager = androidx.biometric.BiometricManager.from(context)
                    val isBiometricAvailable = biometricManager.canAuthenticate(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                    
                    SecurityScreen(
                        currentSecurityMethod = securityMethod,
                        currentLockTiming = lockTiming,
                        isBiometricAvailable = isBiometricAvailable,
                        onSetPinCode = { pin ->
                            viewModel.savePin(pin)
                            viewModel.setSecurityMethod(SecureStorage.SecurityMethod.PIN)
                            securityMethod = SecureStorage.SecurityMethod.PIN
                        },
                        onEnableBiometric = {
                            viewModel.setSecurityMethod(SecureStorage.SecurityMethod.BIOMETRIC)
                            securityMethod = SecureStorage.SecurityMethod.BIOMETRIC
                        },
                        onDisableSecurity = {
                            viewModel.clearPin()
                            viewModel.setSecurityMethod(SecureStorage.SecurityMethod.NONE)
                            securityMethod = SecureStorage.SecurityMethod.NONE
                        },
                        onLockTimingChange = { timing ->
                            viewModel.setLockTiming(timing)
                            lockTiming = timing
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.About.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.AllAddresses.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    // Trigger recomposition after generating new address
                    var addressListVersion by remember { mutableIntStateOf(0) }
                    
                    // Fetch addresses - uses addressListVersion as key to refresh after generating
                    val addresses = remember(addressListVersion) { viewModel.getAllAddresses() }
                    
                    AllAddressesScreen(
                        receiveAddresses = addresses.first,
                        changeAddresses = addresses.second,
                        usedAddresses = addresses.third,
                        denomination = denomination,
                        privacyMode = privacyMode,
                        onGenerateReceiveAddress = {
                            val newAddress = viewModel.getNewAddressSuspend()
                            addressListVersion++
                            newAddress
                        }
                    )
                }
                composable(
                    route = Screen.AllUtxos.route,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
                ) {
                    val utxos = viewModel.getAllUtxos()
                    
                    AllUtxosScreen(
                        utxos = utxos,
                        denomination = denomination,
                        btcPrice = btcPrice,
                        privacyMode = privacyMode,
                        onFreezeUtxo = { outpoint, frozen ->
                            viewModel.setUtxoFrozen(outpoint, frozen)
                        },
                        onSendFromUtxo = { utxo ->
                            viewModel.setPreSelectedUtxo(utxo)
                            // Simple navigation to Send screen
                            navController.navigate(Screen.Send.route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * Connection status indicator shown in the app bar
 */
@Composable
private fun ConnectionStatusIndicator(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when {
        isConnecting -> BitcoinOrange
        isConnected -> SuccessGreen
        else -> ErrorRed
    }
    
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .padding(top = 4.dp, end = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
    }
}

/**
 * Dialog showing connection status details
 */
@Composable
private fun ConnectionStatusDialog(
    isConnected: Boolean,
    isConnecting: Boolean,
    serverName: String?,
    serverUrl: String?,
    serverPort: Int?,
    useSsl: Boolean,
    isOnion: Boolean,
    lastSyncTimestamp: Long?,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onConfigureServer: () -> Unit
) {
    val statusColor = when {
        isConnecting -> BitcoinOrange
        isConnected -> SuccessGreen
        else -> ErrorRed
    }
    val statusText = when {
        isConnecting -> "Connecting"
        isConnected -> "Connected"
        else -> "Disconnected"
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Status row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (serverUrl != null) {
                    // Server info card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .padding(10.dp)
                    ) {
                        // Protocol badges top-right
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Text(
                                    text = if (useSsl) "SSL" else "TCP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (isOnion) {
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Text(
                                        text = "Tor",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Name
                            if (serverName != null) {
                                Row {
                                    Text(
                                        text = "Name:  ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = serverName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            // Server
                            Row {
                                Text(
                                    text = "Server:  ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            // Port
                            Row {
                                Text(
                                    text = "Port:     ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${serverPort ?: 50001}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No server configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Connect / Disconnect row
                if (serverUrl != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isConnected || isConnecting) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = ErrorRed
                                )
                            ) {
                                Text("Disconnect")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onConnect,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SuccessGreen
                                )
                            ) {
                                Text("Connect")
                            }
                        }
                    }

                }
                
                // Change Server button
                OutlinedButton(
                    onClick = onConfigureServer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BitcoinOrange),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BitcoinOrange
                    )
                ) {
                    Text(if (serverUrl != null) "Change Server" else "Configure Server")
                }
            }
        }
    }
}
